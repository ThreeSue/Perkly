const assert=require('node:assert/strict');
const fs=require('node:fs');const vm=require('node:vm');const ts=require('typescript');
const source=fs.readFileSync('src/modules/merchant/map/store-map.ts','utf8');
const code=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
const moduleObject={exports:{}};const requests=[];
vm.runInNewContext(code,{module:moduleObject,exports:moduleObject.exports,URLSearchParams,require:()=>({merchantRequest:(path,data)=>{requests.push({path,data});return Promise.resolve({});}})});
const api=moduleObject.exports;
assert.equal(api.validCoordinate(0,0),true);assert.equal(api.validCoordinate(0,26),true);assert.equal(api.validCoordinate(119,0),true);
for(const [lng,lat] of [[null,1],[1,null],['',0],[undefined,0],[181,0],[0,-91],[NaN,1],[Infinity,1]])assert.equal(api.validCoordinate(lng,lat),false);
assert.equal(api.drawable({located:false,longitude:0,latitude:0}),false);assert.equal(api.drawable({located:true,longitude:0,latitude:0}),true);
(async()=>{
 await api.fetchStoreMap({keyword:'  咖啡  ',status:0,storeTypeId:2,merchantId:999,page:1,size:20});
 assert.equal(requests[0].path,'workspace/store/map');assert.deepEqual(JSON.parse(JSON.stringify(requests[0].data)),{keyword:'咖啡',status:0,storeTypeId:2});
 await api.mapQuery('suggest',{keyword:'咖啡 & 茶',region:'福州'});assert.equal(new URL('https://test/'+requests[1].path).searchParams.get('keyword'),'咖啡 & 茶');
 await api.updateStoreLocation(4,{address:'核对地址',longitude:0,latitude:0});assert.equal(requests[2].path,'workspace/store/location/update');assert.equal(requests[2].data.longitude,0);
 console.log('PASS: coordinate zero/null/range, located flag, full-map endpoint, filter whitelist, query encoding, merchant location payload');
})().catch(()=>{console.error('Map contract check failed');process.exitCode=1;});
