import { verifyIdToken } from './auth';
import { destroy, isAllowedAsset, RESOURCE_TYPES } from './cloudinary';
import { HttpError, type Env } from './env';
import { sendFcm } from './fcm';
import { Firestore } from './firestore';
import { getAccessToken, parseServiceAccount } from './google';
import { liveKitToken } from './livekit';
import { notificationText } from './text';

/*
 * Nook Worker. Endpoints (all POST, all require "Authorization: Bearer <Firebase ID token>"):
 *   /notify          { chatId }                 push "Ali sent you a photo" to the other members
 *   /call            { callId, action }         ring / cancel the callee (full-screen call)
 *   /livekit-token   { callId }                 LiveKit join token for a call you're in
 *   /media/delete    { scope, assets[] }        delete Cloudinary assets you're allowed to touch
 * Cron: deletes expired disappearing messages + their media.
 *
 * PRIVACY: we never read, forward or log message content. Logs carry ids and counts only.
 */

interface Ctx {
  env: Env;
  uid: string;
  fs: Firestore;
  accessToken: string;
}

const MESSAGE_TYPES = new Set(['text', 'image', 'file', 'voice', 'gif', 'sticker']);

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === 'GET' && url.pathname === '/') return json({ ok: true, service: 'nook-worker' });
    if (request.method !== 'POST') return json({ error: 'method not allowed' }, 405);
    try {
      const uid = await verifyIdToken(request.headers.get('authorization'), env.FIREBASE_PROJECT_ID);
      const sa = parseServiceAccount(env.FIREBASE_SERVICE_ACCOUNT);
      const accessToken = await getAccessToken(sa);
      const ctx: Ctx = { env, uid, fs: new Firestore(env.FIREBASE_PROJECT_ID, accessToken), accessToken };
      const body = (await request.json().catch(() => ({}))) as Record<string, unknown>;
      switch (url.pathname) {
        case '/notify': return json(await notify(ctx, id(body.chatId)));
        case '/call': return json(await call(ctx, id(body.callId), str(body.action)));
        case '/livekit-token': return json(await token(ctx, id(body.callId)));
        case '/media/delete': return json(await deleteMedia(ctx, id(body.scope), body.assets));
        default: return json({ error: 'not found' }, 404);
      }
    } catch (e) {
      if (e instanceof HttpError) return json({ error: e.message }, e.status);
      console.error('worker error', (e as Error).message);
      return json({ error: 'internal' }, 500);
    }
  },

  async scheduled(_event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(sweepExpired(env));
  },
};

// ---------------- handlers ----------------

async function notify(ctx: Ctx, chatId: string) {
  const chat = await ctx.fs.get(`chats/${chatId}`);
  const members = strings(chat?.members);
  if (!chat || !members.includes(ctx.uid)) throw new HttpError(403, 'not a member');
  const last = (chat.lastMessage ?? {}) as Record<string, unknown>;
  // The chat doc is written in the same batch as the message, so it tells us the type safely.
  if (last.senderId !== ctx.uid) throw new HttpError(409, 'stale');
  const type = str(last.type);
  if (!MESSAGE_TYPES.has(type)) return { sent: 0 };

  const sender = await ctx.fs.get(`users/${ctx.uid}`);
  const senderName = str(sender?.displayName) || str(sender?.username) || 'Someone';
  const isGroup = chat.type === 'group';
  const { title, body } = notificationText({ senderName, messageType: type, isGroup, groupName: str(chat.name) });

  let sent = 0;
  await Promise.all(
    members.filter((m) => m !== ctx.uid).map(async (member) => {
      const settings = await ctx.fs.get(`users/${member}/private/settings`);
      if (!settings) return;
      if (settings.notificationsEnabled === false) return;
      if (strings(settings.mutedChats).includes(chatId)) return;
      if (strings(settings.blocked).includes(ctx.uid)) return;
      sent += await pushAll(ctx, member, strings(settings.fcmTokens), { type: 'message', chatId, title, body });
    }),
  );
  console.log('notify', { chatId, recipients: members.length - 1, sent });
  return { sent };
}

async function call(ctx: Ctx, callId: string, action: string) {
  const c = await ctx.fs.get(`calls/${callId}`);
  if (!c || c.callerId !== ctx.uid) throw new HttpError(403, 'not your call');
  const callee = str(c.calleeId);
  const settings = await ctx.fs.get(`users/${callee}/private/settings`);
  if (!settings || strings(settings.blocked).includes(ctx.uid)) return { sent: 0 };
  const tokens = strings(settings.fcmTokens);
  if (action === 'cancel') {
    return { sent: await pushAll(ctx, callee, tokens, { type: 'call_cancel', callId }, 60) };
  }
  if (c.status !== 'ringing') return { sent: 0 };
  const caller = await ctx.fs.get(`users/${ctx.uid}`);
  const callerName = str(caller?.displayName) || str(caller?.username) || 'Someone';
  const sent = await pushAll(ctx, callee, tokens, { type: 'call', callId, callerName, callType: str(c.type) || 'voice' }, 45);
  console.log('call ring', { callId, sent });
  return { sent };
}

async function token(ctx: Ctx, callId: string) {
  const c = await ctx.fs.get(`calls/${callId}`);
  if (!c || !strings(c.members).includes(ctx.uid)) throw new HttpError(403, 'not in this call');
  if (c.status !== 'ringing' && c.status !== 'accepted') throw new HttpError(410, 'call is over');
  const me = await ctx.fs.get(`users/${ctx.uid}`);
  const jwt = await liveKitToken({
    apiKey: ctx.env.LIVEKIT_API_KEY,
    apiSecret: ctx.env.LIVEKIT_API_SECRET,
    identity: ctx.uid,
    name: str(me?.displayName) || str(me?.username) || 'Nook',
    room: `call_${callId}`,
  });
  return { token: jwt };
}

async function deleteMedia(ctx: Ctx, scope: string, assetsRaw: unknown) {
  let prefix: string;
  if (scope === 'avatar') {
    prefix = `nook/avatars/${ctx.uid}/`;
  } else {
    const chat = await ctx.fs.get(`chats/${scope}`);
    if (!chat || !strings(chat.members).includes(ctx.uid)) throw new HttpError(403, 'not a member');
    prefix = `nook/chats/${scope}/`;
  }
  const assets = Array.isArray(assetsRaw) ? assetsRaw.slice(0, 50) : [];
  let deleted = 0;
  for (const a of assets) {
    const publicId = str((a as Record<string, unknown>)?.publicId);
    const resourceType = str((a as Record<string, unknown>)?.resourceType) || 'image';
    if (!isAllowedAsset(publicId, prefix) || !RESOURCE_TYPES.has(resourceType)) continue;
    if (await destroy(ctx.env.CLOUDINARY_CLOUD_NAME, ctx.env.CLOUDINARY_API_KEY, ctx.env.CLOUDINARY_API_SECRET, publicId, resourceType)) deleted++;
  }
  return { deleted };
}

/** Cron sweep: expired disappearing messages, plus their media. */
async function sweepExpired(env: Env) {
  try {
    const sa = parseServiceAccount(env.FIREBASE_SERVICE_ACCOUNT);
    const fs = new Firestore(env.FIREBASE_PROJECT_ID, await getAccessToken(sa));
    const rows = await fs.expiredMessages(new Date().toISOString(), 200);
    let media = 0;
    for (const [path, data] of rows) {
      const chatId = path.split('/')[1] ?? '';
      const m = (data.media ?? null) as Record<string, unknown> | null;
      const publicId = str(m?.publicId);
      if (publicId && isAllowedAsset(publicId, `nook/chats/${chatId}/`) && env.CLOUDINARY_API_SECRET) {
        if (await destroy(env.CLOUDINARY_CLOUD_NAME, env.CLOUDINARY_API_KEY, env.CLOUDINARY_API_SECRET, publicId, str(m?.resourceType) || 'image')) media++;
      }
      await fs.delete(path);
    }
    console.log('sweep', { messages: rows.length, media });
  } catch (e) {
    console.error('sweep failed', (e as Error).message);
  }
}

// ---------------- helpers ----------------

async function pushAll(ctx: Ctx, uid: string, tokens: string[], data: Record<string, string>, ttl = 3600): Promise<number> {
  let ok = 0;
  const dead: string[] = [];
  for (const t of tokens.slice(0, 10)) {
    const r = await sendFcm(ctx.env.FIREBASE_PROJECT_ID, ctx.accessToken, t, data, ttl);
    if (r === 'ok') ok++;
    else if (r === 'unregistered') dead.push(t);
  }
  if (dead.length) await ctx.fs.arrayRemove(`users/${uid}/private/settings`, 'fcmTokens', dead).catch(() => undefined);
  return ok;
}

/** Document ids we accept in paths: no slashes, no traversal. */
export function id(v: unknown): string {
  const s = str(v);
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(s)) throw new HttpError(400, 'bad id');
  return s;
}

function str(v: unknown): string {
  return typeof v === 'string' ? v : '';
}

function strings(v: unknown): string[] {
  return Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : [];
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), { status, headers: { 'content-type': 'application/json' } });
}
