const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
const source = fs.readFileSync('src/cool/utils/storage.ts', 'utf8')
  .replace("import store from 'store';", '')
  .replace("import { identity } from '/@/workspace';", '')
  .replace('export default', 'globalThis.storage =');
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
function harness(identity, values) {
  const store = { get: k => values.get(k), set: (k, v) => values.set(k, v), remove: k => values.delete(k), each: cb => [...values].forEach(([k, v]) => cb(v, k)) };
  const context = vm.createContext({ identity, store, Date });
  vm.runInContext(compiled, context);
  return context.storage;
}
test('three identities isolate tokens, menus, user and selected-store data', () => {
  const values = new Map();
  const a = harness('PLATFORM', values), b = harness('MERCHANT', values), c = harness('STORE', values);
  for (const key of ['token', 'refreshToken', 'userInfo', 'base.menuGroup', 'selectedStore']) {
    a.set(key, 'platform'); b.set(key, 'merchant'); c.set(key, 'store');
    assert.equal(a.get(key), 'platform'); assert.equal(b.get(key), 'merchant'); assert.equal(c.get(key), 'store');
    assert.equal(a.info()[key], 'platform'); assert.equal(b.info()[key], 'merchant');
  }
});
test('logout and clear-all affect only current identity including expiration keys', () => {
  const values = new Map();const a = harness('PLATFORM', values), b = harness('MERCHANT', values), c = harness('STORE', values);
  a.set('token', 'platform', 60);b.set('token', 'merchant', 60);c.set('token', 'store', 60);
  b.remove('token');assert.equal(a.get('token'), 'platform');assert.equal(c.get('token'), 'store');assert.equal(b.getExpiration('token'), undefined);
  b.set('userInfo', 'merchant');b.clearAll();assert.equal(Object.keys(b.info()).length, 0);assert.equal(a.get('token'), 'platform');assert.equal(c.get('token'), 'store');
  a.clearAll();assert.equal(c.get('token'), 'store');
});
test('platform inventory never exposes another identity namespace', () => {
  const values = new Map([['token', 'platform'], ['workspace:STORE:token', 'store']]);
  assert.deepEqual(Object.keys(harness('PLATFORM', values).info()), ['token']);
});
