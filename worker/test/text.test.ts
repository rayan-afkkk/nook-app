import { describe, expect, it } from 'vitest';
import { notificationText } from '../src/text';

describe('notificationText', () => {
  it('says who and what kind in a DM, never content', () => {
    expect(notificationText({ senderName: 'Ali Khan', messageType: 'text', isGroup: false }))
      .toEqual({ title: 'Nook', body: 'Ali sent you a message' });
    expect(notificationText({ senderName: 'Ali', messageType: 'image', isGroup: false }).body).toBe('Ali sent you a photo');
    expect(notificationText({ senderName: 'Ali', messageType: 'voice', isGroup: false }).body).toBe('Ali sent you a voice message');
  });

  it('uses the group name for groups', () => {
    expect(notificationText({ senderName: 'Ali', messageType: 'gif', isGroup: true, groupName: 'The Boys' }))
      .toEqual({ title: 'The Boys', body: 'New message in The Boys' });
  });

  it('falls back safely', () => {
    expect(notificationText({ senderName: '  ', messageType: 'weird', isGroup: false }).body).toBe('Someone sent you a message');
    expect(notificationText({ senderName: 'A', messageType: 'text', isGroup: true, groupName: '' }).body).toBe('New message in your group');
  });
});
