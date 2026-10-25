import { readFile,writeFile,mkdir,copyFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
const root=fileURLToPath(new URL('..',import.meta.url));
for(const module of ['motion','renderer']){
 const text=await readFile(resolve(root,'shared',module+'.js'),'utf8');
 const exports=[...text.matchAll(/export (?:const|function) (\w+)/g)].map(m=>m[1]);
 const output=text.replace(/export /g,'')+'\nmodule.exports = { '+exports.join(', ')+' };\n';
 for(const folder of ['wechat/components/probe/generated','uni-app-x/wxcomponents/probe/generated']){
  await mkdir(resolve(root,folder),{recursive:true});await writeFile(resolve(root,folder,module+'.js'),output);
 }
}
for(const name of ['index.js','index.wxml','index.wxss','index.json']){
 const path=resolve(root,'wechat/components/probe',name);
 await copyFile(path,resolve(root,'uni-app-x/wxcomponents/probe',name));
}
console.log('Native probe and uni-app x custom component generated from shared core.');
