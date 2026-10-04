export type SendResult = 'ok' | 'unregistered' | 'error';

/**
 * FCM HTTP v1, data-only + high priority so the app can render its own content-free
 * notification (or a full-screen call). Values must be strings.
 */
export async function sendFcm(
  projectId: string,
  accessToken: string,
  token: string,
  data: Record<string, string>,
  ttlSeconds = 3600,
): Promise<SendResult> {
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: 'POST',
    headers: { authorization: `Bearer ${accessToken}`, 'content-type': 'application/json' },
    body: JSON.stringify({
      message: { token, data, android: { priority: 'HIGH', ttl: `${ttlSeconds}s` } },
    }),
  });
  if (res.ok) return 'ok';
  if (res.status === 404) return 'unregistered';
  if (res.status === 400) {
    const body = await res.text();
    if (body.includes('UNREGISTERED') || body.includes('registration token is not a valid')) return 'unregistered';
  }
  return 'error';
}
