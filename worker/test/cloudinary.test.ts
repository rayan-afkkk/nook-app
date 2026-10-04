import { describe, expect, it } from 'vitest';
import { isAllowedAsset, sign } from '../src/cloudinary';

describe('cloudinary', () => {
  it('signs sorted params + secret with SHA-1 (matches an independent python hashlib digest)', async () => {
    const sig = await sign({ timestamp: 1700000000, public_id: 'nook/chats/abc/x1' }, 'shh-secret');
    expect(sig).toBe('91d4f3795f6ec868785e8ee9d7245d01a18926db');
  });

  it('only allows assets inside the caller\'s folder', () => {
    expect(isAllowedAsset('nook/chats/abc/photo1', 'nook/chats/abc/')).toBe(true);
    expect(isAllowedAsset('nook/chats/xyz/photo1', 'nook/chats/abc/')).toBe(false);
    expect(isAllowedAsset('nook/chats/abc/../xyz/p', 'nook/chats/abc/')).toBe(false);
    expect(isAllowedAsset('nook/avatars/u2/a', 'nook/avatars/u1/')).toBe(false);
  });
});
