/**
 * Notification copy. Deliberately content-free: it says WHO and WHAT KIND, never what was said.
 *   DM:    "Ali sent you a message" / "Ali sent you a photo"
 *   Group: "New message in The Boys"
 */
const KIND: Record<string, string> = {
  text: 'a message',
  image: 'a photo',
  file: 'a file',
  voice: 'a voice message',
  gif: 'a GIF',
  sticker: 'a sticker',
};

export function notificationText(opts: {
  senderName: string;
  messageType: string;
  isGroup: boolean;
  groupName?: string | null;
}): { title: string; body: string } {
  const sender = firstName(opts.senderName) || 'Someone';
  if (opts.isGroup) {
    const group = (opts.groupName ?? '').trim() || 'your group';
    return { title: group, body: `New message in ${group}` };
  }
  return { title: 'Nook', body: `${sender} sent you ${KIND[opts.messageType] ?? 'a message'}` };
}

export function firstName(name: string): string {
  return name.trim().split(/\s+/)[0] ?? '';
}
