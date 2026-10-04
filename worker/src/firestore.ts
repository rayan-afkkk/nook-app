/** Minimal Firestore REST client (admin credentials bypass security rules, so we check membership ourselves). */

export type FsValue =
  | { nullValue: null }
  | { booleanValue: boolean }
  | { integerValue: string }
  | { doubleValue: number }
  | { timestampValue: string }
  | { stringValue: string }
  | { arrayValue: { values?: FsValue[] } }
  | { mapValue: { fields?: Record<string, FsValue> } }
  | { referenceValue: string }
  | { bytesValue: string }
  | { geoPointValue: { latitude: number; longitude: number } };

export interface FsDocument {
  name: string;
  fields?: Record<string, FsValue>;
}

/** Converts a Firestore REST value to plain JS (timestamps → epoch millis). */
export function decodeValue(v: FsValue): unknown {
  if ('nullValue' in v) return null;
  if ('booleanValue' in v) return v.booleanValue;
  if ('integerValue' in v) return Number(v.integerValue);
  if ('doubleValue' in v) return v.doubleValue;
  if ('timestampValue' in v) return Date.parse(v.timestampValue);
  if ('stringValue' in v) return v.stringValue;
  if ('arrayValue' in v) return (v.arrayValue.values ?? []).map(decodeValue);
  if ('mapValue' in v) return decodeFields(v.mapValue.fields ?? {});
  if ('referenceValue' in v) return v.referenceValue;
  if ('bytesValue' in v) return v.bytesValue;
  if ('geoPointValue' in v) return v.geoPointValue;
  return undefined;
}

export function decodeFields(fields: Record<string, FsValue>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(fields)) out[k] = decodeValue(v);
  return out;
}

export class Firestore {
  private readonly root: string;
  constructor(private projectId: string, private accessToken: string) {
    this.root = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents`;
  }

  private headers() {
    return { authorization: `Bearer ${this.accessToken}`, 'content-type': 'application/json' };
  }

  async get(path: string): Promise<Record<string, unknown> | null> {
    const res = await fetch(`${this.root}/${path}`, { headers: this.headers() });
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`firestore get failed (${res.status})`);
    const doc = (await res.json()) as FsDocument;
    return decodeFields(doc.fields ?? {});
  }

  async delete(path: string): Promise<void> {
    const res = await fetch(`${this.root}/${path}`, { method: 'DELETE', headers: this.headers() });
    if (!res.ok && res.status !== 404) throw new Error(`firestore delete failed (${res.status})`);
  }

  /** Removes values from an array field without reading first. */
  async arrayRemove(path: string, field: string, values: string[]): Promise<void> {
    const res = await fetch(`${this.root.replace(/\/documents$/, '')}/documents:commit`, {
      method: 'POST',
      headers: this.headers(),
      body: JSON.stringify({
        writes: [{
          transform: {
            document: `projects/${this.projectId}/databases/(default)/documents/${path}`,
            fieldTransforms: [{ fieldPath: field, removeAllFromArray: { values: values.map((s) => ({ stringValue: s })) } }],
          },
        }],
      }),
    });
    if (!res.ok) throw new Error(`firestore commit failed (${res.status})`);
  }

  /** Collection-group query for messages whose expireAt is in the past. Returns [docPath, data]. */
  async expiredMessages(nowIso: string, limit: number): Promise<Array<[string, Record<string, unknown>]>> {
    const res = await fetch(`${this.root}:runQuery`, {
      method: 'POST',
      headers: this.headers(),
      body: JSON.stringify({
        structuredQuery: {
          from: [{ collectionId: 'messages', allDescendants: true }],
          where: { fieldFilter: { field: { fieldPath: 'expireAt' }, op: 'LESS_THAN', value: { timestampValue: nowIso } } },
          limit,
        },
      }),
    });
    if (!res.ok) throw new Error(`firestore query failed (${res.status})`);
    const rows = (await res.json()) as Array<{ document?: FsDocument }>;
    const prefix = `projects/${this.projectId}/databases/(default)/documents/`;
    return rows
      .filter((r): r is { document: FsDocument } => !!r.document)
      .map((r) => [r.document.name.slice(prefix.length), decodeFields(r.document.fields ?? {})]);
  }
}
