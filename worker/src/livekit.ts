import { SignJWT } from 'jose';

/** LiveKit access token (HS256) allowing one identity to join one room. */
export async function liveKitToken(opts: {
  apiKey: string;
  apiSecret: string;
  identity: string;
  name: string;
  room: string;
  ttlSeconds?: number;
}): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  return new SignJWT({
    name: opts.name,
    video: { room: opts.room, roomJoin: true, canPublish: true, canSubscribe: true, canPublishData: true },
  })
    .setProtectedHeader({ alg: 'HS256', typ: 'JWT' })
    .setIssuer(opts.apiKey)
    .setSubject(opts.identity)
    .setNotBefore(now - 10)
    .setIssuedAt(now)
    .setExpirationTime(now + (opts.ttlSeconds ?? 2 * 3600))
    .setJti(`${opts.identity}-${now}`)
    .sign(new TextEncoder().encode(opts.apiSecret));
}
