const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const ts = require('typescript');
const filename = path.join(__dirname, '../../src/modules/merchant/components/merchant-scope.ts');
const compiled = ts.transpileModule(fs.readFileSync(filename, 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } });
const exported = {};
vm.runInNewContext(compiled.outputText, { exports: exported });
const clone = v => JSON.parse(JSON.stringify(v));
function service(merchantId) {
  const base = Object.fromEntries(['page','list','info','add','update','delete'].map(action => [action, async data => clone(data)]));
  return exported.createMerchantService(base, () => merchantId, () => {});
}
test('all stores request omits null keyword/status/category and overrides merchant scope', async () => {
  assert.deepEqual(await service(1113).page({page:1,merchantId:7,keyWord:null,name:null,status:null,categoryId:null}), {page:1,merchantId:1113});
});
test('disabled status zero and real keyword are preserved', async () => {
  assert.deepEqual(await service(1113).page({status:0,keyWord:'金山'}), {status:0,keyWord:'金山',merchantId:1113});
});
test('all merchants list has no synthetic null condition', async () => {
  assert.deepEqual(await service(undefined).list({merchantId:null,status:null,keyWord:''}), {});
});
test('write operations retain explicit null values used to clear fields', async () => {
  assert.deepEqual(await service(1113).update({id:1,cover:null}), {id:1,cover:null,merchantId:1113});
});
