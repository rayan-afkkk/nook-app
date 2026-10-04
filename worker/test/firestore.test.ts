import { describe, expect, it } from 'vitest';
import { decodeFields } from '../src/firestore';

describe('decodeFields', () => {
  it('decodes nested Firestore REST values', () => {
    const out = decodeFields({
      members: { arrayValue: { values: [{ stringValue: 'a' }, { stringValue: 'b' }] } },
      lastMessage: { mapValue: { fields: { senderId: { stringValue: 'a' }, type: { stringValue: 'image' } } } },
      notificationsEnabled: { booleanValue: false },
      count: { integerValue: '3' },
      at: { timestampValue: '2026-01-01T00:00:00Z' },
      empty: { arrayValue: {} },
      nothing: { nullValue: null },
    });
    expect(out).toEqual({
      members: ['a', 'b'],
      lastMessage: { senderId: 'a', type: 'image' },
      notificationsEnabled: false,
      count: 3,
      at: Date.parse('2026-01-01T00:00:00Z'),
      empty: [],
      nothing: null,
    });
  });
});
