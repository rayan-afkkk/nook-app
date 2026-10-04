import { createRemoteJWKSet, jwtVerify } from 'jose';
import { HttpError } from './env';

// Google's public keys for Firebase ID tokens (cached by jose between requests in an isolate).
const JWKS = createRemoteJWKSet(
  new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'),
);

/** Verifies a Firebase ID token and returns the caller's uid. */
export async function verifyIdToken(header: string | null, projectId: string): Promise<string> {
  if (!header?.startsWith('Bearer ')) throw new HttpError(401, 'missing token');
  const token = header.slice('Bearer '.length).trim();
  try {
    const { payload } = await jwtVerify(token, JWKS, {
      issuer: `https://securetoken.google.com/${projectId}`,
      audience: projectId,
      algorithms: ['RS256'],
    });
    if (typeof payload.sub !== 'string' || payload.sub.length === 0) throw new Error('no sub');
    return payload.sub;
  } catch {
    throw new HttpError(401, 'invalid token');
  }
}
