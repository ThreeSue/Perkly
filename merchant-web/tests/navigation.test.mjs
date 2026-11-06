import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import assert from 'node:assert/strict';
import ts from 'typescript';
const source=readFileSync(new URL('../src/modules/base/utils/navigation.ts',import.meta.url),'utf8');
const {outputText}=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}});
const exports={};new Function('exports',outputText)(exports);
const build=exports.buildNavigation;
const page=(id,path)=>({id,path,router:path,name:path,meta:{label:path},type:1,isShow:true});
const flatten=rows=>rows.flatMap(row=>[row,...flatten(row.children||[])]);
test('商家按业务分组，空分组隐藏，页面权限与原输入保持不变',()=>{
 const rows=[page(1,'/merchant/dashboard'),page(2,'/merchant/accounts'),page(3,'/merchant/roles'),page(4,'/merchant/wallet')];
 const before=JSON.stringify(rows),result=build(rows,'MERCHANT');
 assert.deepEqual(result.map(r=>r.name),['/merchant/dashboard','订单与账单','员工与权限']);
 assert.deepEqual(flatten(result).filter(r=>r.type===1).map(r=>r.id).sort(),[1,2,3,4]);
 assert.equal(JSON.stringify(rows),before);
 assert.equal(build([],'MERCHANT').length,0);
});
test('运营系统使用三级目录，隐藏项与权限按钮不进入导航',()=>{
 const hidden={...page(8,'/sys/param'),isShow:false};
 const result=build([page(1,'/'),page(2,'/sys/menu'),page(3,'/sys/role'),hidden,{id:9,type:2,name:'操作'}],'PLATFORM');
 const sys=result.find(r=>r.name==='系统设置');
 assert.equal(sys.children.length,2);
 assert.equal(sys.children.find(r=>r.name==='菜单与参数').children[0].id,2);
 assert.deepEqual(flatten(result).filter(r=>r.type===1).map(r=>r.id).sort(),[1,2,3]);
});
test('门店保留核销快捷入口，标准化store路径，未知扩展目录继续可访问',()=>{
 const result=build([page(1,'/store/redemption'),page(2,'/store/wallet'),{id:7,type:0,name:'扩展',isShow:true},{...page(8,'/custom/page'),parentId:7}],'STORE');
 assert.equal(result[0].router,'/store/redemption');
 assert.equal(result.find(r=>r.name==='订单与账单').children[0].router,'/store/wallet');
 assert.equal(result.find(r=>r.name==='扩展').children[0].id,8);
});
test('不可见父目录下的页面不因重新分组而显示',()=>{
 const rows=[{id:7,type:0,isShow:false},{...page(8,'/merchant/accounts'),parentId:7}];
 assert.equal(build(rows,'MERCHANT').length,0);
});
