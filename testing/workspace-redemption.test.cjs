const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
const { parse } = require('@vue/compiler-sfc');
const { ref } = require('vue');
const script = parse(fs.readFileSync('src/modules/workspace/views/redemption.vue', 'utf8')).descriptor.scriptSetup.content.replace(/^import .*?;\r?\n/gm, '');
const code = ts.transpileModule(script, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
function harness(request) {
  const values = new Map();
  const context = vm.createContext({ ref, onMounted() {}, useRouter: () => ({ push: async () => {} }), merchantRequest: request, workspaceApi: async () => ({}), identity: 'STORE', storage: { set: (k, v) => values.set(k, v), get: k => values.get(k), remove: k => values.delete(k) }, ElMessage: { success() {} }, ElMessageBox: { confirm: async () => {} }, crypto: require('node:crypto') });
  vm.runInContext(code + '\nglobalThis.state={code,storeId,inspection,order,amount,remark,uncertain,requestId,error};', context);
  context.state.inspection.value = { requiresConsumptionAmount: true };
  context.state.code.value = 'coupon';context.state.storeId.value = 1;context.state.amount.value = '100.00';context.state.requestId.value = 'same-request';
  return context;
}
test('uncertain network result retains the original idempotency key and retries the same request', async () => {
  const calls=[];let fail=true;
  const h=harness(async(path,body)=>{calls.push({path,body});if(fail)throw new Error('timeout');return {id:4,orderNo:'order-1',status:'PENDING'};});
  await h.prepare();assert.equal(h.state.uncertain.value,true);assert.equal(h.state.requestId.value,'same-request');
  fail=false;await h.prepare();assert.equal(h.state.uncertain.value,false);assert.equal(h.state.order.value.id,4);assert.equal(calls[0].body.requestId,calls[1].body.requestId);assert.equal(calls[0].body.consumptionAmount,10000);
});
test('definitive business validation failure allows fixing the amount instead of trapping the form', async () => {
  const h=harness(async()=>{throw {business:true,message:'未满足使用门槛'};});
  await h.prepare();assert.equal(h.state.uncertain.value,false);assert.notEqual(h.state.requestId.value,'same-request');assert.equal(h.state.error.value,'未满足使用门槛');
});
test('content coupons use content redemption without inventing a consumption amount', async () => {
  let call;const h=harness(async(path,body)=>{call={path,body};return {id:5,status:'COMPLETED'};});
  h.state.inspection.value={requiresConsumptionAmount:false};await h.prepare();assert.equal(call.path,'consumption/redeemContent');assert.equal(call.body.consumptionAmount,null);assert.equal(h.state.order.value.status,'COMPLETED');
});
test('invalid monetary input never reaches the backend', async () => {
  let calls=0;const h=harness(async()=>{calls++;});h.state.amount.value='1.001';await h.prepare();assert.equal(calls,0);assert.match(h.state.error.value,/金额/);
});
