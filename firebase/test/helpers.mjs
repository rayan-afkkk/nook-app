import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';

const here = dirname(fileURLToPath(import.meta.url));

export async function makeEnv() {
  return initializeTestEnvironment({
    projectId: 'demo-nook',
    firestore: { rules: readFileSync(join(here, '..', 'firestore.rules'), 'utf8') },
    database: { rules: readFileSync(join(here, '..', 'database.rules.json'), 'utf8') },
  });
}

export const ALICE = 'aliceUid0000000000000000001';
export const BOB = 'bobUid000000000000000000002';
export const CAROL = 'carolUid0000000000000000003';
export const dmId = (a, b) => 'dm_' + [a, b].sort().join('_');
