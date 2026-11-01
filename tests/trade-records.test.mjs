import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import {test} from 'node:test';
import ts from 'typescript';
import {parse,compileScript} from '@vue/compiler-sfc';
import * as Vue from 'vue';
function js(source,require=()=>{throw Error('Unexpected dependency');}){const {outputText}=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}});const exports={};new Function('require','exports',outputText)(require,exports);return exports;}
const model=js(readFileSync(new URL('../src/modules/coupon/record-model.ts',import.meta.url),'utf8'));
const {descriptor}=parse(readFileSync(new URL('../src/modules/coupon/components/trade-records.vue',import.meta.url),'utf8'));
const script=compileScript(descriptor,{id:'records-test'}).content;
function harness(overrides={}){
 const refreshes=[],requests=[],cleanup=[];let service,columns;
 const props={purchase:false,canPage:true,canInfo:true,pageApi:async p=>{requests.push(p);return {list:[],pagination:{page:1,size:20,total:0}};},infoApi:async id=>({id}),...overrides};
 const module=js(script,name=>{
  if(name==='vue')return {...Vue,onBeforeUnmount:fn=>cleanup.push(fn)};
  if(name==='@cool-vue/crud')return {useTable:options=>{columns=options.columns;return Vue.ref(options);},useCrud:(options,ready)=>{service=options.service;const app={refresh:p=>refreshes.push(p)};ready(app);return Vue.ref(app);}};
  if(name==='element-plus')return {ElMessage:{error(){}}};
  if(name==='../record-model')return model;
  throw Error(name);
 });
 const state=module.default.setup(props,{expose(){}});
 return {state,service,columns,refreshes,requests,dispose:()=>cleanup.forEach(fn=>fn())};
}
test('元和分的金额独立转换，缺失值不伪装成零',()=>{assert.equal(model.money(9.9),'9.90 元');assert.equal(model.money(16500,true),'165.00 元');assert.equal(model.money(0,true),'0.00 元');assert.equal(model.money(null,true),'—');assert.equal(model.money('invalid'),'—');});
test('筛选提交正确字段，清空日期或状态不传无效参数',()=>{assert.deepEqual(model.filterPayload({orderNo:' XF123 ',status:'COMPLETED',storeId:3},['2026-10-01 00:00:00','2026-10-28 23:59:59']),{orderNo:'XF123',status:'COMPLETED',storeId:3,startTime:'2026-10-01 00:00:00',endTime:'2026-10-28 23:59:59'});assert.deepEqual(model.filterPayload({orderNo:' ',status:''},null),{});});
test('无分页权限不发初始请求，无详情权限不出现操作按钮',()=>{const h=harness({canPage:false,canInfo:false});assert.equal(h.refreshes.length,0);assert.deepEqual(h.columns.find(c=>c.type==='op').buttons,[]);assert.equal(h.columns.some(c=>['id','merchantId','storeId','couponCode'].includes(c.prop)),false);h.dispose();});
test('搜索重置到第一页；重置后不残留商家门店和日期条件',async()=>{const h=harness();h.state.filters.merchantId=2;h.state.filters.storeId=4;h.state.filters.status='PENDING';h.state.range.value=['2026-10-01 00:00:00','2026-10-02 00:00:00'];h.state.search();assert.deepEqual(h.refreshes.at(-1),{page:1});await h.service.page({page:1,size:20});assert.equal(h.requests[0].storeId,4);h.state.reset();await h.service.page({page:1,size:20});assert.deepEqual(h.requests.at(-1),{page:1,size:20});h.dispose();});
test('商家变化清除门店筛选并取消未发出的远程搜索',async()=>{let calls=0;const h=harness({loadStores:async()=>{calls++;return [];}});h.state.filters.storeId=4;h.state.searchStores('old');h.state.merchantChanged();await new Promise(r=>setTimeout(r,380));assert.equal(calls,0);assert.equal(h.state.filters.storeId,undefined);h.dispose();});
test('详情乱序返回不会覆盖后选订单，失败不残留旧详情',async()=>{let resolveFirst;const first=new Promise(r=>resolveFirst=r);const h=harness({infoApi:id=>id===1?first:id===2?Promise.resolve({id:2}):Promise.reject(Error('加载失败'))});const pending=h.state.open({id:1});await h.state.open({id:2});resolveFirst({id:1});await pending;assert.equal(h.state.detail.value.id,2);await h.state.open({id:3});assert.equal(h.state.detail.value,undefined);assert.equal(h.state.detailError.value,'加载失败');h.dispose();});
test('两端的金额规则和查询组件保持一致',()=>{const merchant=new URL('../../merchant-web/cool-admin-vue-8.x-merchant/src/modules/merchant/',import.meta.url);assert.equal(readFileSync(new URL('record-model.ts',merchant),'utf8'),readFileSync(new URL('../src/modules/coupon/record-model.ts',import.meta.url),'utf8'));assert.equal(readFileSync(new URL('components/trade-records.vue',merchant),'utf8'),readFileSync(new URL('../src/modules/coupon/components/trade-records.vue',import.meta.url),'utf8'));});
