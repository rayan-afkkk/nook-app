import { test, before, after, beforeEach } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { ref, set, get, update, remove, serverTimestamp } from 'firebase/database';
import { makeEnv, ALICE, BOB, CAROL, dmId } from './helpers.mjs';

let env;
before(async () => { env = await makeEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearDatabase(); });

const rtdb = (uid) => env.authenticatedContext(uid).database();

test('presence: write your own, read anyone (signed in)', async () => {
  await assertSucceeds(set(ref(rtdb(ALICE), `presence/${ALICE}`), { online: true, lastSeen: serverTimestamp() }));
  await assertFails(set(ref(rtdb(BOB), `presence/${ALICE}`), { online: false, lastSeen: 1 }));
  await assertSucceeds(get(ref(rtdb(BOB), `presence/${ALICE}`)));
  await assertFails(get(ref(env.unauthenticatedContext().database(), `presence/${ALICE}`)));
});

test('membership mirror: DM ids must include you; members can add, anyone can leave', async () => {
  const id = dmId(ALICE, BOB);
  await assertFails(update(ref(rtdb(CAROL), `chatMembers/${id}`), { [CAROL]: true, [ALICE]: true }));
  await assertSucceeds(update(ref(rtdb(ALICE), `chatMembers/${id}`), { [ALICE]: true, [BOB]: true }));
  await assertSucceeds(update(ref(rtdb(ALICE), 'chatMembers/g1'), { [ALICE]: true, [BOB]: true }));
  await assertSucceeds(update(ref(rtdb(BOB), 'chatMembers/g1'), { [CAROL]: true }));
  await assertFails(remove(ref(rtdb(BOB), `chatMembers/g1/${ALICE}`)));
  await assertSucceeds(remove(ref(rtdb(BOB), `chatMembers/g1/${BOB}`)));
});

test('typing: only chat members can read or write', async () => {
  const id = dmId(ALICE, BOB);
  await env.withSecurityRulesDisabled(async (ctx) => {
    await set(ref(ctx.database(), `chatMembers/${id}`), { [ALICE]: true, [BOB]: true });
  });
  await assertSucceeds(set(ref(rtdb(ALICE), `typing/${id}/${ALICE}`), serverTimestamp()));
  await assertFails(set(ref(rtdb(ALICE), `typing/${id}/${BOB}`), serverTimestamp()));
  await assertFails(set(ref(rtdb(CAROL), `typing/${id}/${CAROL}`), serverTimestamp()));
  await assertSucceeds(get(ref(rtdb(BOB), `typing/${id}`)));
  await assertFails(get(ref(rtdb(CAROL), `typing/${id}`)));
});
