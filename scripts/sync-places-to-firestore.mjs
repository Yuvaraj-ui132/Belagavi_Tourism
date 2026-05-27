/**
 * Sync places/1..29 via Firestore REST API (no npm). Requires rules to allow writes.
 */
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import vm from 'vm';

const API_KEY = 'AIzaSyAvio-c2lwo5IHN1RSn8JV-E_-iL133w-U';
const PROJECT = 'belagavi-tourism-planner';
const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents`;

const __dirname = dirname(fileURLToPath(import.meta.url));
const sandbox = { window: {} };
vm.runInNewContext(readFileSync(join(__dirname, '../public/static/data.js'), 'utf8'), sandbox);
const places = sandbox.window.allPlacesData;

function toFirestoreValue(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === 'string') return { stringValue: v };
  if (typeof v === 'boolean') return { booleanValue: v };
  if (typeof v === 'number') {
    return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  }
  if (Array.isArray(v)) {
    return { arrayValue: { values: v.map(toFirestoreValue) } };
  }
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

async function setPlaceDoc(place) {
  const url = `${BASE}/places/${place.id}?key=${API_KEY}`;
  const res = await fetch(url, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ fields: toFirestoreFields(place) }),
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(`places/${place.id} ${res.status}: ${err}`);
  }
  return res.json();
}

async function getPlaceDoc(id) {
  const url = `${BASE}/places/${id}?key=${API_KEY}`;
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

const EXPECTED = {
  1: [15.8590, 74.5126], 5: [16.1181, 74.8263], 6: [16.1927, 74.7775],
  8: [15.6564, 74.3226], 11: [15.8594, 74.5129], 12: [15.8437, 74.5129],
  16: [15.8578, 74.5163], 17: [15.8468, 74.5039], 23: [15.8958, 74.5529],
  26: [15.8190, 74.3072], 27: [15.8134, 74.5714], 28: [15.7540, 75.1600],
  29: [15.7571, 74.5267],
};

console.log(`Syncing ${places.length} places via Firestore REST...`);
for (const place of places) {
  await setPlaceDoc(place);
  console.log(`  OK places/${place.id} ${place.name}`);
}

console.log('\nVerifying...');
let ok = true;
for (const [id, [lat, lon]] of Object.entries(EXPECTED)) {
  const d = await getPlaceDoc(id);
  if (!d || d.lat !== lat || d.lon !== lon) {
    console.error(`  FAIL places/${id}: ${d?.lat}, ${d?.lon}`);
    ok = false;
  } else {
    console.log(`  OK places/${id}: ${lat}, ${lon}`);
  }
}
process.exit(ok ? 0 : 1);
