import { describe, expect, it } from 'vitest';
import { jwtVerify } from 'jose';
import { liveKitToken } from '../src/livekit';

describe('liveKitToken', () => {
  it('grants exactly one room to one identity', async () => {
    const jwt = await liveKitToken({ apiKey: 'APIkey', apiSecret: 'secret-secret-secret-secret', identity: 'u1', name: 'Ali', room: 'call_c1' });
    const { payload } = await jwtVerify(jwt, new TextEncoder().encode('secret-secret-secret-secret'), { issuer: 'APIkey' });
    expect(payload.sub).toBe('u1');
    expect(payload.name).toBe('Ali');
    expect(payload.video).toMatchObject({ room: 'call_c1', roomJoin: true, canPublish: true, canSubscribe: true });
    expect((payload.exp ?? 0) - (payload.iat ?? 0)).toBe(7200);
  });
});
