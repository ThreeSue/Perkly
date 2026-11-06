import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import ts from 'typescript';
import { parse, compileScript } from '@vue/compiler-sfc';
import * as Vue from 'vue';

test('无权分配项禁用且批量清除和勾选事件保留其原配置', () => {
 const text = readFileSync(new URL('../src/modules/workspace/components/permission-picker.vue', import.meta.url), 'utf8');
 const {descriptor} = parse(text);
 const script = compileScript(descriptor, {id:'picker'}).content;
 const {outputText} = ts.transpileModule(script, {compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}});
 const exports = {};
 new Function('require','exports',outputText)(name => name === 'vue' ? {...Vue, watch() {}} : {}, exports);
 const emitted = [];
 const state = exports.default.setup({
  modelValue:[1,2,99],
  menus:[{id:1,name:'可分配'},{id:2,name:'不可分配'},{id:3,name:'另一不可分配'}],
  unavailableIds:[2,3]
 }, {expose(){},emit:(_event,ids)=>emitted.push(ids)});
 assert.equal(state.nodes.value[0].disabled, false);
 assert.equal(state.nodes.value[1].disabled, true);
 assert.equal(state.nodes.value[2].disabled, true);
 state.changed(null,{checkedKeys:[1,3]});
 assert.deepEqual(emitted.pop(), [2,99,1]);
 state.clearVisible();
 assert.deepEqual(emitted.pop(), [2,99]);
});
