import { describe, expect, it } from 'vitest';
import { id } from '../src/index';

describe('id()', () => {
  it('accepts Firestore-style ids and rejects path tricks', () => {
    expect(id('dm_abc_DEF')).toBe('dm_abc_DEF');
    expect(() => id('chats/x')).toThrow();
    expect(() => id('../x')).toThrow();
    expect(() => id('')).toThrow();
    expect(() => id(42)).toThrow();
  });
});
