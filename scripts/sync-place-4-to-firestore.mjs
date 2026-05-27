/**
 * One-place Firestore update (places/4) from public/static/data.js.
 * - Overwrites lat/lon (and any other fields present in the place doc) via REST PATCH.
 * - Preserves reviews/ratings/wishlist because those are separate collections.
 */
import { readFileSync } from 'fs';
import vm from 'vm';
import { join } from 'path';

const API_KEY = 'AIzaSyAvio-c2lwo5IHN1RSn8JV-E_-iL133w-U';
const PROJECT = 'belagavi-tourism-planner';
const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents`;

const dataPath = join(process.cwd(), '../public/static/data.js');
const sandbox = { window: {} };
vm.runInNewContext(readFileSync(dataPath, 'utf8'), sandbox);

const place = sandbox.window.allPlacesData?.find(p => p.id === 4);
if (!place) {
  console.error('Could not find place id=4 in data.js');
  process.exit(1);
}

function toFirestoreValue(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === 'string') return { stringValue: v };
  if (typeof v === 'boolean') return { booleanValue: v };
  if (typeof v === 'number') {
    return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  }
  if (Array.isArray(v)) return { arrayValue: { values: v.map(toFirestoreValue) } };
  if (typeof v === 'object') {
    const fields = {};
    for (const [k, val] of Object.entries(v)) fields[k] = toFirestoreValue(val);
    return { mapValue: { fields } };
  }
  return { stringValue: String(v) };
}

function toFirestoreFields(obj) {
  const fields = {};
  for (const [k, v] of Object.entries(obj)) fields[k] = toFirestoreValue(v);
  return fields;
}

const docPath = `places/4`;

async function patchPlace() {
  const url = `${BASE}/${docPath}?key=${API_KEY}`;
  const res = await fetch(url, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ fields: toFirestoreFields(place) }),
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(`PATCH failed (${res.status}): ${err}`);
  }
}

async function getPlace() {
  const url = `${BASE}/${docPath}?key=${API_KEY}`;
  const res = await fetch(url);
  if (!res.ok) return null;
  const data = await res.json();
  const out = {};
  for (const [k, v] of Object.entries(data.fields || {})) {
    if (v.doubleValue !== undefined) out[k] = v.doubleValue;
    else if (v.integerValue !== undefined) out[k] = Number(v.integerValue);
    else if (v.stringValue !== undefined) out[k] = v.stringValue;
  }
  return out;
}

console.log('Updating Firestore places/4 from local data.js...');
await patchPlace();

console.log('Verifying lat/lon in Firestore...');
const d = await getPlace();
const ok = d && d.lat === place.lat && d.lon === place.lon;
if (!ok) {
  console.error(`FAIL: Firestore has lat/lon=${d?.lat}, ${d?.lon} expected ${place.lat}, ${place.lon}`);
  process.exit(1);
}

console.log(`OK: places/4 lat=${place.lat}, lon=${place.lon}`);
process.exit(0);

