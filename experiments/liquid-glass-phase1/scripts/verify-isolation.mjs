import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const lab = fileURLToPath(new URL('..', import.meta.url));
const root = resolve(lab, '../..');
const records = JSON.parse((await readFile(resolve(lab, 'formal-files-baseline.json'), 'utf8')).replace(/^\uFEFF/, ''));
// Explicit later user request: implement only merchant content UI. Keep the
// original baseline intact; navigation/shared/dependency changes remain failures.
const allowedUiPaths = process.argv.includes('--allow-merchant-ui') ? new Set([
  'apps/merchant/components/business-shell.uvue',
  'apps/merchant/pages.json',
  'apps/merchant/pages/business/home.uvue',
  'apps/merchant/pages/business/profile.uvue',
  'apps/merchant/pages/business/records.uvue',
  'apps/merchant/styles/merchant.scss'
]) : new Set();
const changed = [];
for (const record of records) {
  try {
    const hash = createHash('sha256').update(await readFile(resolve(root, record.path))).digest('hex').toUpperCase();
    if (hash !== record.sha256) changed.push(record.path);
  } catch { changed.push(record.path); }
}
const authorizedUiChanges = changed.filter(path => allowedUiPaths.has(path));
const unexpectedChanges = changed.filter(path => !allowedUiPaths.has(path));
console.log(JSON.stringify({ filesCompared: records.length, authorizedUiChanges, unexpectedChanges }, null, 2));
if (unexpectedChanges.length) process.exitCode = 1;
