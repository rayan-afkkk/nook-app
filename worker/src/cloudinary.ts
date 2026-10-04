/** Signed Cloudinary Admin "destroy" (the unsigned upload preset can't delete). */

export async function sha1Hex(input: string): Promise<string> {
  const buf = await crypto.subtle.digest('SHA-1', new TextEncoder().encode(input));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/** Cloudinary signature: sorted params joined with &, then the API secret appended, SHA-1. */
export async function sign(params: Record<string, string | number>, apiSecret: string): Promise<string> {
  const toSign = Object.keys(params)
    .sort()
    .map((k) => `${k}=${params[k]}`)
    .join('&');
  return sha1Hex(toSign + apiSecret);
}

export const RESOURCE_TYPES = new Set(['image', 'video', 'raw']);

/** Only assets inside the folder the caller is allowed to touch. */
export function isAllowedAsset(publicId: string, prefix: string): boolean {
  return publicId.startsWith(prefix) && !publicId.includes('..');
}

export async function destroy(
  cloud: string,
  apiKey: string,
  apiSecret: string,
  publicId: string,
  resourceType: string,
): Promise<boolean> {
  if (!RESOURCE_TYPES.has(resourceType)) return false;
  const timestamp = Math.floor(Date.now() / 1000);
  const signature = await sign({ public_id: publicId, timestamp }, apiSecret);
  const body = new URLSearchParams({ public_id: publicId, timestamp: String(timestamp), api_key: apiKey, signature });
  const res = await fetch(`https://api.cloudinary.com/v1_1/${cloud}/${resourceType}/destroy`, { method: 'POST', body });
  return res.ok;
}
