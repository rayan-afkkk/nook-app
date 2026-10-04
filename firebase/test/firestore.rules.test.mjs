import { test, before, after, beforeEach } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  doc, getDoc, setDoc, updateDoc, deleteDoc, writeBatch, runTransaction, serverTimestamp,
  arrayUnion, arrayRemove, deleteField, Timestamp, collection, query, where, getDocs, collectionGroup, orderBy,
} from 'firebase/firestore';
import { makeEnv, ALICE, BOB, CAROL, dmId } from './helpers.mjs';

let env;
before(async () => { env = await makeEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });

const db = (uid) => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();

async function seed(fn) {
  await env.withSecurityRulesDisabled(async (ctx) => { await fn(ctx.firestore()); });
}

async function seedUsers() {
  await seed(async (f) => {
    for (const [uid, name] of [[ALICE, 'alice'], [BOB, 'bob'], [CAROL, 'carol']]) {
      await setDoc(doc(f, 'users', uid), { uid, username: name, displayName: name, photoUrl: null });
      await setDoc(doc(f, 'usernames', name), { uid });
    }
  });
}

async function seedDm() {
  const id = dmId(ALICE, BOB);
  await seed(async (f) => {
    await setDoc(doc(f, 'chats', id), {
      type: 'direct', members: [ALICE, BOB].sort(), createdBy: ALICE, disappearing: 'off', lastRead: {},
    });
  });
  return id;
}

async function seedGroup() {
  await seed(async (f) => {
    await setDoc(doc(f, 'chats', 'g1'), {
      type: 'group', name: 'The Boys', members: [ALICE, BOB], admins: [ALICE], createdBy: ALICE, disappearing: 'off', lastRead: {},
    });
  });
  return 'g1';
}

const message = (sender, extra = {}) => ({
  senderId: sender, type: 'text', text: 'hi', reactions: {}, hiddenFor: [],
  createdAt: serverTimestamp(), clientCreatedAt: Date.now(), expireAt: null, forwarded: false, ...extra,
});

// ---------------- users & usernames ----------------

async function claim(f, uid, name) {
  return runTransaction(f, async (tx) => {
    tx.set(doc(f, 'usernames', name), { uid, createdAt: serverTimestamp() });
    tx.set(doc(f, 'users', uid), { uid, username: name, displayName: 'Al', photoUrl: null, createdAt: serverTimestamp() });
  });
}

test('a user can create a profile while claiming a free username', async () => {
  await assertSucceeds(claim(db(ALICE), ALICE, 'alice'));
});

test('a taken username cannot be claimed by someone else', async () => {
  await assertSucceeds(claim(db(ALICE), ALICE, 'alice'));
  await assertFails(claim(db(BOB), BOB, 'alice'));
});

test('invalid usernames are rejected', async () => {
  await assertFails(claim(db(ALICE), ALICE, 'Al'));
  await assertFails(claim(db(ALICE), ALICE, '.alice'));
  await assertFails(claim(db(ALICE), ALICE, 'ALICE'));
});

test('a username cannot be changed or a second one claimed', async () => {
  await assertSucceeds(claim(db(ALICE), ALICE, 'alice'));
  await assertFails(updateDoc(doc(db(ALICE), 'users', ALICE), { username: 'alice2' }));
  await assertFails(setDoc(doc(db(ALICE), 'usernames', 'alice2'), { uid: ALICE }));
});

test('users can only edit their own profile, and only safe fields', async () => {
  await seedUsers();
  await assertSucceeds(updateDoc(doc(db(ALICE), 'users', ALICE), { displayName: 'Alice A' }));
  await assertFails(updateDoc(doc(db(BOB), 'users', ALICE), { displayName: 'pwned' }));
  await assertFails(updateDoc(doc(db(ALICE), 'users', ALICE), { uid: BOB }));
});

test('profiles need sign-in to read', async () => {
  await seedUsers();
  await assertFails(getDoc(doc(anon(), 'users', ALICE)));
  await assertSucceeds(getDoc(doc(db(BOB), 'users', ALICE)));
});

test('private settings and tokens are owner-only', async () => {
  await assertSucceeds(setDoc(doc(db(ALICE), 'users', ALICE, 'private', 'settings'), { blocked: [] }));
  await assertFails(getDoc(doc(db(BOB), 'users', ALICE, 'private', 'settings')));
  await assertSucceeds(setDoc(doc(db(ALICE), 'users', ALICE, 'tokens', 't1'), { token: 't1' }));
  await assertFails(getDoc(doc(db(BOB), 'users', ALICE, 'tokens', 't1')));
});

// ---------------- chats ----------------

test('a DM can be created only with its deterministic id and by a member', async () => {
  const data = { type: 'direct', members: [ALICE, BOB].sort(), createdBy: ALICE, disappearing: 'off', lastRead: {} };
  await assertFails(setDoc(doc(db(ALICE), 'chats', 'dm_wrong'), data));
  await assertFails(setDoc(doc(db(CAROL), 'chats', dmId(ALICE, BOB)), { ...data, createdBy: CAROL }));
  await assertSucceeds(setDoc(doc(db(ALICE), 'chats', dmId(ALICE, BOB)), data));
});

test('you can check whether your own DM exists, but not other people\'s', async () => {
  await assertSucceeds(getDoc(doc(db(ALICE), 'chats', dmId(ALICE, BOB))));
  await assertFails(getDoc(doc(db(CAROL), 'chats', dmId(ALICE, BOB))));
});

test('only members can read a chat and its messages', async () => {
  const id = await seedDm();
  await assertSucceeds(getDoc(doc(db(BOB), 'chats', id)));
  await assertFails(getDoc(doc(db(CAROL), 'chats', id)));
  await assertFails(getDocs(collection(db(CAROL), 'chats', id, 'messages')));
  await assertSucceeds(getDocs(query(collection(db(ALICE), 'chats'), where('members', 'array-contains', ALICE))));
});

test('members can send messages; outsiders and spoofers cannot', async () => {
  const id = await seedDm();
  await assertSucceeds(setDoc(doc(db(ALICE), 'chats', id, 'messages', 'm1'), message(ALICE)));
  await assertFails(setDoc(doc(db(CAROL), 'chats', id, 'messages', 'm2'), message(CAROL)));
  await assertFails(setDoc(doc(db(ALICE), 'chats', id, 'messages', 'm3'), message(BOB)));
  await assertFails(setDoc(doc(db(ALICE), 'chats', id, 'messages', 'm4'), message(ALICE, { reactions: { [BOB]: '❤️' } })));
});

test('sending updates the chat preview and read receipt in one batch', async () => {
  const id = await seedDm();
  const f = db(ALICE);
  const b = writeBatch(f);
  b.set(doc(f, 'chats', id, 'messages', 'm1'), message(ALICE));
  b.update(doc(f, 'chats', id), {
    lastMessage: { id: 'm1', senderId: ALICE, type: 'text', preview: 'hi' },
    lastMessageAt: serverTimestamp(),
    [`lastRead.${ALICE}`]: serverTimestamp(),
  });
  await assertSucceeds(b.commit());
});

test('members cannot be swapped out of a DM', async () => {
  const id = await seedDm();
  await assertFails(updateDoc(doc(db(ALICE), 'chats', id), { members: [ALICE, CAROL] }));
});

test('reactions: only your own key; delete-for-me: only yourself', async () => {
  const id = await seedDm();
  await seed(async (f) => { await setDoc(doc(f, 'chats', id, 'messages', 'm1'), { ...message(ALICE), createdAt: Timestamp.now() }); });
  await assertSucceeds(updateDoc(doc(db(BOB), 'chats', id, 'messages', 'm1'), { [`reactions.${BOB}`]: '😂' }));
  await assertFails(updateDoc(doc(db(BOB), 'chats', id, 'messages', 'm1'), { [`reactions.${ALICE}`]: '😂' }));
  await assertSucceeds(updateDoc(doc(db(BOB), 'chats', id, 'messages', 'm1'), { [`reactions.${BOB}`]: deleteField() }));
  await assertSucceeds(updateDoc(doc(db(BOB), 'chats', id, 'messages', 'm1'), { hiddenFor: arrayUnion(BOB) }));
  await assertFails(updateDoc(doc(db(BOB), 'chats', id, 'messages', 'm1'), { hiddenFor: arrayUnion(ALICE) }));
  await assertFails(updateDoc(doc(db(BOB), 'chats', id, 'messages', 'm1'), { text: 'edited' }));
});

test('unsend: only the sender; expired messages: any member', async () => {
  const id = await seedDm();
  await seed(async (f) => {
    await setDoc(doc(f, 'chats', id, 'messages', 'm1'), { ...message(ALICE), createdAt: Timestamp.now() });
    await setDoc(doc(f, 'chats', id, 'messages', 'old'), {
      ...message(ALICE), createdAt: Timestamp.now(), expireAt: Timestamp.fromMillis(Date.now() - 60_000),
    });
  });
  await assertFails(deleteDoc(doc(db(BOB), 'chats', id, 'messages', 'm1')));
  await assertSucceeds(deleteDoc(doc(db(ALICE), 'chats', id, 'messages', 'm1')));
  await assertSucceeds(deleteDoc(doc(db(BOB), 'chats', id, 'messages', 'old')));
  await assertFails(deleteDoc(doc(db(CAROL), 'chats', id, 'messages', 'old')));
});

test('groups: members can add people and leave, but not remove others', async () => {
  const id = await seedGroup();
  await assertSucceeds(updateDoc(doc(db(BOB), 'chats', id), { members: arrayUnion(CAROL) }));
  await assertFails(updateDoc(doc(db(BOB), 'chats', id), { members: arrayRemove(ALICE) }));
  await assertSucceeds(updateDoc(doc(db(BOB), 'chats', id), { members: arrayRemove(BOB), [`lastRead.${BOB}`]: deleteField() }));
  await assertFails(getDoc(doc(db(BOB), 'chats', id)));
});

test('only your own messages are visible to the account-deletion collection-group query', async () => {
  const id = await seedDm();
  await seed(async (f) => { await setDoc(doc(f, 'chats', id, 'messages', 'm1'), { ...message(ALICE), createdAt: Timestamp.now() }); });
  await assertSucceeds(getDocs(query(collectionGroup(db(ALICE), 'messages'), where('senderId', '==', ALICE))));
  await assertFails(getDocs(query(collectionGroup(db(CAROL), 'messages'), where('senderId', '==', ALICE))));
});

// ---------------- calls & sticker packs ----------------

test('calls can only be placed to someone you share a chat with', async () => {
  const id = await seedDm();
  const call = (caller, callee, chatId) => ({
    chatId, callerId: caller, calleeId: callee, members: [caller, callee], type: 'voice', status: 'ringing', createdAt: serverTimestamp(),
  });
  await assertSucceeds(setDoc(doc(db(ALICE), 'calls', 'c1'), call(ALICE, BOB, id)));
  await assertFails(setDoc(doc(db(CAROL), 'calls', 'c2'), call(CAROL, BOB, id)));
  await assertSucceeds(updateDoc(doc(db(BOB), 'calls', 'c1'), { status: 'accepted', acceptedAt: serverTimestamp() }));
  await assertFails(getDoc(doc(db(CAROL), 'calls', 'c1')));
  await assertSucceeds(getDocs(query(collection(db(ALICE), 'calls'), where('members', 'array-contains', ALICE), orderBy('createdAt', 'desc'))));
});

test('sticker packs are shared with exactly the group members', async () => {
  const id = await seedGroup();
  const pack = { chatId: id, name: 'Us', createdBy: ALICE, members: [ALICE, BOB], stickers: [], createdAt: serverTimestamp() };
  await assertSucceeds(setDoc(doc(db(ALICE), 'stickerPacks', 'p1'), pack));
  await assertFails(setDoc(doc(db(CAROL), 'stickerPacks', 'p2'), { ...pack, createdBy: CAROL, members: [ALICE, BOB, CAROL] }));
  await assertSucceeds(updateDoc(doc(db(BOB), 'stickerPacks', 'p1'), { stickers: arrayUnion({ url: 'https://x', publicId: 'p' }) }));
  await assertFails(getDoc(doc(db(CAROL), 'stickerPacks', 'p1')));
});
