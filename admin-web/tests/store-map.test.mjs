import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import {test} from 'node:test';
import ts from 'typescript';
import {parse,compileScript} from '@vue/compiler-sfc';
import * as Vue from 'vue';

function commonJs(source,require=()=>{throw new Error('Unexpected dependency');},context={}) {
	const {outputText}=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}});
	const exports={};new Function('require','exports',...Object.keys(context),outputText)(require,exports,...Object.values(context));return exports;
}
const model=commonJs(readFileSync(new URL('../src/modules/merchant/map/model.ts',import.meta.url),'utf8'));
const {descriptor}=parse(readFileSync(new URL('../src/modules/merchant/components/location-picker.vue',import.meta.url),'utf8'));
const pickerSource=compileScript(descriptor,{id:'picker-test'}).content;
function deferred(){let resolve,reject;const promise=new Promise((ok,no)=>{resolve=ok;reject=no;});return {promise,resolve,reject};}
function picker(persist=false,overrides={}){
	const writes=[],events=[],cleanup=[];
	const api={suggest:async()=>({list:[]}),reverse:async(lng,lat)=>({address:'解析后的地址',longitude:lng,latitude:lat}),geocode:async()=>({address:'解析后的地址',longitude:119,latitude:26}),update:async(...args)=>{writes.push(args);return true;},...overrides};
	const module=commonJs(pickerSource,name=>{
		if(name==='vue')return {...Vue,onBeforeUnmount:cb=>cleanup.push(cb)};
		if(name==='element-plus')return {ElMessage:{success(){}}};
		if(name.endsWith('tencent-map.vue'))return {};
		if(name.endsWith('/model'))return model;
		if(name.endsWith('/api'))return {storeMapApi:api};
		if(name.endsWith('/permission'))return {mapPermission:()=>true};
		throw new Error('Unexpected dependency '+name);
	});
	let exposed;
	const state=module.default.setup({persist},{expose:value=>exposed=value,emit:(...args)=>events.push(args)});
	return {state,open:exposed.open,writes,events,dispose:()=>cleanup.forEach(fn=>fn())};
}

test('零经纬度合法；空值、单边缺失和越界不可绘制',()=>{
	assert.equal(model.drawable({located:true,longitude:0,latitude:0}),true);
	for(const point of [{located:false,longitude:119,latitude:26},{located:true,longitude:null,latitude:26},{located:true,longitude:'',latitude:26},{located:true,longitude:181,latitude:26},{located:true,longitude:0,latitude:91},{located:true,longitude:false,latitude:26}])assert.equal(model.drawable(point),false);
});
test('全量地图结果超过100家仍全部参与绘制判断',()=>{
	const rows=Array.from({length:257},(_,id)=>({id,located:id%2===0,longitude:id%2===0?0:null,latitude:26}));
	assert.equal(rows.filter(model.drawable).length,129);assert.equal(rows.filter(p=>!model.drawable(p)).length,128);
});
test('筛选保留停用状态0，保存校验地址与坐标并限制精度',()=>{
	assert.deepEqual(model.mapFilters({keyword:' 咖啡 ',status:0}),{keyword:'咖啡',status:0,merchantId:undefined,storeTypeId:undefined});
	assert.deepEqual(model.locationPayload({address:' 门店1号 ',longitude:0,latitude:26.123456789}),{address:'门店1号',longitude:0,latitude:26.1234568});
	assert.throws(()=>model.locationPayload({address:'',longitude:0,latitude:0}));
	assert.throws(()=>model.locationPayload({address:'地址',longitude:undefined,latitude:26}));
});
test('HTTP封装使用运营前缀、全量接口且不附加普通分页参数',async()=>{
	const calls=[];
	const api=commonJs(readFileSync(new URL('../src/modules/merchant/map/api.ts',import.meta.url),'utf8'),name=>name==='/@/cool'?{service:{request:async options=>{calls.push(options);return {};}}}:model).storeMapApi;
	await api.list({status:0});await api.suggest(' 咖啡 ','福州');await api.geocode('详细地址');await api.reverse(0,0);await api.update(42,{address:'已核对地址',longitude:0,latitude:26});
	assert.deepEqual(calls.map(c=>c.url),['admin/merchant/store/map','admin/merchant/map/suggest','admin/merchant/map/geocode','admin/merchant/map/reverseGeocode','admin/merchant/store/location/update']);
	assert.equal('page' in calls[0].data,false);assert.equal(calls[0].data.status,0);assert.deepEqual(calls[3].params,{longitude:0,latitude:0});assert.equal(calls[4].data.id,42);
});
test('打开、搜索预览、选择地点与取消不写入数据库',async()=>{
	const p=picker(true);p.open({id:1,address:'旧地址'});await Vue.nextTick();
	assert.equal(p.state.position.value,undefined);
	p.state.choose({address:'新地址',longitude:0,latitude:26});await Vue.nextTick();
	p.state.visible.value=false;p.state.invalidate();assert.equal(p.writes.length,0);p.dispose();
});
test('新增或编辑表单选址确认只回填，不调用位置补录接口',async()=>{
	const p=picker(false);p.open({id:1,address:'旧地址'});await Vue.nextTick();p.state.choose({address:'已核对地址',longitude:0,latitude:0});p.state.confirmed.value=true;
	await p.state.confirm();assert.equal(p.writes.length,0);assert.deepEqual(p.events,[['confirmed',{address:'已核对地址',longitude:0,latitude:0}]]);p.dispose();
});
test('已有门店仅人工核对后保存一次；保存失败保留选点供重试',async()=>{
	let attempts=0;const p=picker(true,{update:async()=>{if(++attempts===1)throw new Error('保存失败测试');return true;}});
	p.open({id:4,address:'旧地址'});await Vue.nextTick();p.state.choose({address:'新地址',longitude:119,latitude:26});
	await p.state.confirm();assert.equal(attempts,0);p.state.confirmed.value=true;await p.state.confirm();assert.equal(p.state.visible.value,true);assert.match(p.state.saveError.value,/保存失败/);assert.equal(p.state.position.value.longitude,119);
	await p.state.confirm();assert.equal(attempts,2);assert.equal(p.state.visible.value,false);p.dispose();
});
test('较早的逆解析不能覆盖后选的位置和地址',async()=>{
	const a=deferred(),b=deferred();let count=0;const p=picker(false,{reverse:()=>++count===1?a.promise:b.promise});p.open({address:'旧地址'});await Vue.nextTick();
	const first=p.state.pick({longitude:118,latitude:25});const second=p.state.pick({longitude:119,latitude:26});
	b.resolve({address:'后选地址'});await second;a.resolve({address:'过期地址'});await first;
	assert.equal(p.state.address.value,'后选地址');assert.equal(p.state.position.value.longitude,119);p.dispose();
});
test('关闭弹窗后地址解析结果失效，手工编辑也会使旧解析失效',async()=>{
	const request=deferred();const p=picker(false,{geocode:()=>request.promise});p.open({address:'旧地址'});await Vue.nextTick();
	const action=p.state.parseAddress();p.state.address.value='手工修改地址';request.resolve({address:'旧解析',longitude:119,latitude:26});await action;
	assert.equal(p.state.position.value,undefined);assert.equal(p.state.address.value,'手工修改地址');assert.equal(p.state.resolving.value,false);p.dispose();
});
test('地点搜索防抖并丢弃输入变化前的旧结果',async()=>{
	const a=deferred();let requests=0;const p=picker(false,{suggest:()=>{requests++;return a.promise;}});p.open({address:''});await Vue.nextTick();
	p.state.keyword.value='青';p.state.keyword.value='青禾';await Vue.nextTick();await new Promise(ok=>setTimeout(ok,430));assert.equal(requests,1);
	p.state.keyword.value='火锅';await Vue.nextTick();a.resolve({list:[{title:'过期青禾结果',longitude:119,latitude:26,address:'旧地址'}]});await new Promise(ok=>setTimeout(ok,5));assert.equal(p.state.places.value.length,0);p.dispose();
});

const canvasDescriptor=parse(readFileSync(new URL('../src/modules/merchant/components/tencent-map.vue',import.meta.url),'utf8')).descriptor;
const canvasSource=compileScript(canvasDescriptor,{id:'canvas-test'}).content;
async function canvas(editable=false,points=[]){
	const layers=[],events=[],cleanup=[];
	class EventSource{handlers={};on(name,handler){this.handlers[name]=handler;}off(name){delete this.handlers[name];}destroy(){this.destroyed=true;}}
	class LatLng{constructor(lat,lng){this.lat=lat;this.lng=lng;}getLat(){return this.lat;}getLng(){return this.lng;}}
	class MapSdk extends EventSource{constructor(_container,options){super();Object.assign(this,options);layers.push(this);}setCenter(p){this.center=p;}setZoom(n){this.zoom=n;}getZoom(){return this.zoom;}getCenter(){return this.center;}fitBounds(bounds){this.bounds=bounds;}}
	class Marker extends EventSource{constructor(options){super();Object.assign(this,options);layers.push(this);}setMap(map){this.map=map;}setGeometries(rows){this.geometries=rows;}setStyles(styles){this.styles=styles;}}
	class Cluster extends Marker{getClusters(){return this.clusters || [];}}
	class Editor extends EventSource{constructor(options){super();Object.assign(this,options);layers.push(this);}select(ids){this.selected=ids;}}
	const sdk={Map:MapSdk,LatLng,MarkerStyle:class{constructor(options){Object.assign(this,options);}},MultiMarker:Marker,MarkerCluster:Cluster,LatLngBounds:class{points=[];extend(p){this.points.push(p);}},tools:{GeometryEditor:Editor,constants:{EDITOR_ACTION:{INTERACT:'interact'}}}};
	const props=Vue.reactive({editable,points,selectedId:undefined,position:undefined});
	const module=commonJs(canvasSource,name=>{
		if(name==='vue')return {...Vue,onMounted(){},onActivated(){},onDeactivated:cb=>cleanup.push(cb),onBeforeUnmount:cb=>cleanup.push(cb)};
		if(name.endsWith('/model'))return model;
		if(name.endsWith('/sdk'))return {loadTencentMap:async()=>sdk,markerIcon:(color,text='')=>color+':'+text};
		throw new Error(name);
	});
	const state=module.default.setup(props,{expose(){},emit:(...args)=>events.push(args)});state.container.value={};await state.init();
	return {props,state,layers,events,sdk,dispose:()=>cleanup.forEach(fn=>fn())};
}
test('真实地图适配层全量传递有效点，聚合单点按营业状态着色，点击联动列表',async()=>{
	const points=[{id:1,status:1,located:true,longitude:0,latitude:0},{id:2,status:0,located:true,longitude:119,latitude:26},{id:3,status:1,located:false,longitude:119,latitude:26}];
	const c=await canvas(false,points),cluster=c.layers.find(x=>x instanceof c.sdk.MarkerCluster),marker=c.layers.find(x=>x.id==='store-points');
	assert.equal(cluster.geometries.length,2);assert.equal(cluster.geometries[0].position.getLng(),0);
	cluster.clusters=cluster.geometries.map(p=>({center:p.position,geometries:[p]}));cluster.handlers.cluster_changed();
	assert.deepEqual(marker.geometries.map(p=>p.styleId),['open','closed']);
	marker.handlers.click({geometry:marker.geometries[1]});assert.ok(c.events.some(e=>e[0]==='select'&&e[1]===2));
	c.props.selectedId=2;await Vue.nextTick();assert.equal(c.layers.find(x=>x.id==='store-selected').geometries[0].properties.storeId,2);c.dispose();assert.equal(cluster.destroyed,true);
});
test('多个密集点生成数量聚合标记，点击展开其范围',async()=>{
	const c=await canvas(false,[{id:1,status:1,located:true,longitude:119,latitude:26},{id:2,status:0,located:true,longitude:119.001,latitude:26}]);
	const cluster=c.layers.find(x=>x instanceof c.sdk.MarkerCluster),marker=c.layers.find(x=>x.id==='store-points'),bounds={testBounds:true};
	cluster.clusters=[{center:cluster.geometries[0].position,geometries:cluster.geometries,bounds}];cluster.handlers.cluster_changed();
	assert.equal(marker.geometries.length,1);assert.match(marker.styles['cluster-0'].src,/:2$/);
	marker.handlers.click({geometry:marker.geometries[0]});assert.equal(c.layers[0].bounds,bounds);c.dispose();
});
test('地图点击和拖动编辑完成只产生选点事件，空坐标不产生草稿点',async()=>{
	const c=await canvas(true),editor=c.layers.find(x=>x instanceof c.sdk.tools.GeometryEditor),marker=c.layers.find(x=>x.id==='store-points');
	assert.deepEqual(marker.geometries,[]);c.layers[0].handlers.click({latLng:new c.sdk.LatLng(26,0)});
	assert.ok(c.events.some(e=>e[0]==='pick'&&e[1].longitude===0));
	c.props.position={longitude:119,latitude:26};await Vue.nextTick();assert.deepEqual(editor.selected,['draft']);
	editor.handlers.adjust_complete({position:new c.sdk.LatLng(27,120)});assert.deepEqual(c.events.at(-1),['pick',{longitude:120,latitude:27}]);c.dispose();assert.equal(editor.destroyed,true);
});
test('SDK加载合并并发请求；失败信息不泄露Key且支持重试',async()=>{
	const scripts=[],timers=new Map();let timerId=0;
	const fakeWindow={setTimeout:cb=>{timers.set(++timerId,cb);return timerId;},clearTimeout:id=>timers.delete(id)};
	const fakeDocument={createElement:()=>({remove(){this.removed=true;}}),head:{appendChild:script=>scripts.push(script)}};
	const source=readFileSync(new URL('../src/modules/merchant/map/sdk.ts',import.meta.url),'utf8').replace('import.meta.env.VITE_TENCENT_MAP_KEY',JSON.stringify('fixture-public-web-key'));
	const sdk=commonJs(source,undefined,{window:fakeWindow,document:fakeDocument});
	const a=sdk.loadTencentMap(),b=sdk.loadTencentMap();assert.equal(a,b);assert.equal(scripts.length,1);
	const rejection=assert.rejects(a,e=>!e.message.includes('fixture-public-web-key')&&/加载失败/.test(e.message));scripts[0].onerror();await rejection;
	const next=sdk.loadTencentMap();assert.equal(scripts.length,2);fakeWindow.TMap={Map:class{},MarkerCluster:class{},tools:{GeometryEditor:class{}}};scripts[1].onload();assert.equal(await next,fakeWindow.TMap);
});

test('详情接口缺少展示名称时保留地图返回的商家与类型名称', async () => {
 const {descriptor}=parse(readFileSync(new URL('../src/modules/merchant/components/store-detail.vue',import.meta.url),'utf8'));
 const source=compileScript(descriptor,{id:'detail-test'}).content;
 const module=commonJs(source,name=>name==='vue'?Vue:{useCool:()=>({service:{merchant:{store:{info:async()=>({id:1,merchantName:null,categoryName:'',phone:'123'})}}}})});
 let exposed;
 const state=module.default.setup({}, {expose:value=>exposed=value});
 await exposed.open({id:1,name:'门店',merchantName:'商家名称',storeTypeName:'直营店',located:true});
 assert.equal(state.data.value.merchantName,'商家名称');
 assert.equal(state.data.value.storeTypeName,'直营店');
 assert.equal(state.data.value.phone,'123');
});
