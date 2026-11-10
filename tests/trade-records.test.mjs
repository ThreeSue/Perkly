import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import {test} from 'node:test';
import ts from 'typescript';
import {parse,compileScript} from '@vue/compiler-sfc';
import * as Vue from 'vue';
function js(source,require=()=>{throw Error('Unexpected dependency');}){const {outputText}=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}});const exports={};new Function('require','exports',outputText)(require,exports);return exports;}
const model=js(readFileSync(new URL('../src/modules/business/record-model.ts',import.meta.url),'utf8'));
const {descriptor}=parse(readFileSync(new URL('../src/modules/business/components/trade-records.vue',import.meta.url),'utf8'));
const script=compileScript(descriptor,{id:'records-test'}).content;
function harness(overrides={}){
 const requests=[],cleanup=[],mounted=[];
 const props=Vue.reactive({purchase:false,canPage:true,canInfo:true,pageApi:async p=>{requests.push(p);return {list:[],pagination:{page:1,size:20,total:0}};},infoApi:async id=>({id}),...overrides});
 const module=js(script,name=>{
  if(name==='vue')return {...Vue,onMounted:fn=>mounted.push(fn),onBeforeUnmount:fn=>cleanup.push(fn)};
  if(name==='element-plus')return {ElMessage:{error(){}}};
  if(name==='../record-model')return model;
  if(name.startsWith('/$/workspace/components/'))return {};
  throw Error(name);
 });
 const scope=Vue.effectScope();
 const state=scope.run(()=>module.default.setup(props,{expose(){}}));
 return {state,requests,start:()=>mounted[0](),dispose:()=>{scope.stop();cleanup.forEach(fn=>fn());}};
}
test('元和分的金额独立转换，缺失值不伪装成零',()=>{assert.equal(model.money(9.9),'9.90 元');assert.equal(model.money(16500,true),'165.00 元');assert.equal(model.money(0,true),'0.00 元');assert.equal(model.money(null,true),'—');assert.equal(model.money('invalid'),'—');});
test('筛选提交正确字段，清空日期或状态不传无效参数',()=>{assert.deepEqual(model.filterPayload({orderNo:' XF123 ',status:'COMPLETED',storeId:3},['2026-10-01 00:00:00','2026-10-28 23:59:59']),{orderNo:'XF123',status:'COMPLETED',storeId:3,startTime:'2026-10-01 00:00:00',endTime:'2026-10-28 23:59:59'});assert.deepEqual(model.filterPayload({orderNo:' ',status:''},null),{});});
test('无分页权限不发初始请求，详情列受独立权限控制',async()=>{
 const h=harness({canPage:false,canInfo:false});
 await h.start();
 assert.equal(h.requests.length,0);
 assert.match(descriptor.template.content,/<el-table-column\s+v-if="canInfo"\s+label="操作"/);
 h.dispose();
});
test('搜索回到第一页，重置清除所有筛选条件',()=>{
 const h=harness();
 h.state.changePage(3);
 h.state.filters.merchantId=2;
 h.state.filters.storeId=4;
 h.state.filters.status='PENDING';
 h.state.range.value=['2026-10-01 00:00:00','2026-10-02 00:00:00'];
 h.state.search();
 assert.equal(h.requests.at(-1).page,1);
 assert.equal(h.requests.at(-1).storeId,4);
 h.state.reset();
 assert.deepEqual(h.requests.at(-1),{page:1,size:20});
 h.dispose();
});
test('商家变化清除门店筛选并取消未发出的远程搜索',async()=>{let calls=0;const h=harness({loadStores:async()=>{calls++;return [];}});h.state.filters.storeId=4;h.state.searchStores('old');h.state.merchantChanged();await new Promise(r=>setTimeout(r,380));assert.equal(calls,0);assert.equal(h.state.filters.storeId,undefined);h.dispose();});
test('详情乱序返回不会覆盖后选订单，失败不残留旧详情',async()=>{let resolveFirst;const first=new Promise(r=>resolveFirst=r);const h=harness({infoApi:id=>id===1?first:id===2?Promise.resolve({id:2}):Promise.reject(Error('加载失败'))});const pending=h.state.open({id:1});await h.state.open({id:2});resolveFirst({id:1});await pending;assert.equal(h.state.detail.value.id,2);await h.state.open({id:3});assert.equal(h.state.detail.value,undefined);assert.equal(h.state.detailError.value,'加载失败');h.dispose();});
test('运营与商家门店复用同一张交易表及详情组件',()=>{
 for(const path of ['coupon/views/purchases.vue','coupon/views/consumption.vue','business/views/consumption.vue','business/views/orders.vue']){
  const source=readFileSync(new URL('../src/modules/'+path,import.meta.url),'utf8');
  assert.match(source,/import TradeRecords from '(?:\/\$\/business|\.\.)\/components\/trade-records\.vue'/);
 }
});
test('过期分页响应不会覆盖新结果，接口错误可重试',async()=>{
 let finishOld;
 let count=0;
 const h=harness({pageApi:async()=>{
  count++;
  if(count===1)return new Promise(resolve=>{finishOld=resolve;});
  if(count===2)throw Error('请求失败');
  return {records:[{id:3}],totalRow:41};
 }});
 const pending=h.start();
 await h.state.load();
 assert.equal(h.state.error.value,'请求失败');
 await h.state.load();
 finishOld({list:[{id:1}],total:1});
 await pending;
 assert.deepEqual(h.state.rows.value,[{id:3}]);
 assert.equal(h.state.total.value,41);
 assert.equal(h.state.error.value,'');
 h.dispose();
});
