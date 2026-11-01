import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import ts from 'typescript';
import { parse, compileScript } from '@vue/compiler-sfc';
import * as Vue from 'vue';

function moduleOf(source, require = () => { throw new Error('Unexpected dependency'); }) {
  const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } });
  const exports = {};
  new Function('require', 'exports', outputText)(require, exports);
  return exports;
}
const model = moduleOf(readFileSync(new URL('../src/plugins/crud/components/order-model.ts', import.meta.url), 'utf8'));
const { descriptor } = parse(readFileSync(new URL('../src/plugins/crud/components/order-manager.vue', import.meta.url), 'utf8'));
const source = compileScript(descriptor, { id: 'order-test' }).content;
const rows = () => [{ id: 1, name: '甲', sort: 10 }, { id: 2, name: '乙', sort: 20 }, { id: 3, name: '丙', sort: 30 }];
function manager(overrides = {}, props = {}) {
  let stored = rows(); const writes = [], events = [], messages = [];
  const service = { _permission: { list: true, update: true }, list: async () => structuredClone(stored), update: async patch => { writes.push(patch); Object.assign(stored.find(row => row.id === patch.id), patch); }, ...overrides };
  const component = moduleOf(source, name => {
    if (name === 'vue') return { ...Vue, onBeforeUnmount() {} };
    if (name === 'element-plus') return { ElMessage: { success: text => messages.push(text) }, ElMessageBox: { confirm: async () => true } };
    if (name === './order-model') return model;
    throw new Error(name);
  });
  const state = component.default.setup({ service, field: 'sort', descending: false, disabled: false, protectRoot: false, ...props }, { expose() {}, emit: (...args) => events.push(args) });
  return { state, writes, events, messages, setStored: data => { stored = data; } };
}
async function open(state) { state.open(); await new Promise(resolve => setImmediate(resolve)); }

test('只改动发生重排的同级组，保留其他分支和业务字段', () => {
  const original = [...rows(), { id: 4, parentId: 1, sort: 50, name: '子项' }];
  const tree = model.buildOrderTree(original, 'sort', false);
  [tree[0], tree[1]] = [tree[1], tree[0]];
  assert.deepEqual(model.orderChanges(tree, original, 'sort', false), [{ id: 2, sort: 1 }, { id: 1, sort: 2 }, { id: 3, sort: 3 }]);
});
test('降序模块保存后仍按用户指定的先后显示', () => {
  const original = rows(); const tree = model.buildOrderTree(original, 'sort', true);
  [tree[0], tree[1]] = [tree[1], tree[0]];
  const changes = model.orderChanges(tree, original, 'sort', true);
  const persisted = original.map(row => ({ ...row, ...changes.find(patch => patch.id === row.id) }));
  assert.deepEqual(model.buildOrderTree(persisted, 'sort', true).map(row => row.id), [2, 3, 1]);
});
test('拒绝循环、重复ID、遗漏和跨父级修改', () => {
  assert.throws(() => model.buildOrderTree([{ id: 1, parentId: 2 }, { id: 2, parentId: 1 }], 'sort', false));
  assert.throws(() => model.buildOrderTree([{ id: 1 }, { id: 1 }], 'sort', false));
  assert.throws(() => model.orderChanges([], rows(), 'sort', false));
  assert.throws(() => model.orderChanges([{ id: 1, parentId: 2 }], [{ id: 1 }], 'sort', false));
});
test('不移动不产生写入，根部门支持字符串0保护', () => {
  assert.deepEqual(model.orderChanges(model.buildOrderTree(rows(), 'sort', false), rows(), 'sort', false), []);
  const original = [{ id: 1, parentId: '0', sort: 10 }, { id: 2, parentId: 0, sort: 20 }];
  assert.deepEqual(model.orderChanges([...model.buildOrderTree(original, 'sort', false)].reverse(), original, 'sort', false, true), []);
});
test('调整后明确保存才写入，成功关闭并通知父页面刷新', async () => {
  const { state, writes, events, messages } = manager(); await open(state);
  state.move(state.tree.value[1], -1); assert.equal(writes.length, 0);
  await state.save(); assert.equal(writes.length, 3); assert.equal(state.visible.value, false);
  assert.deepEqual(events, [['saved']]); assert.equal(messages.length, 1);
});
test('并发排序变化阻止覆盖，并重新读取实际顺序', async () => {
  const { state, writes, setStored, messages } = manager(); await open(state);
  state.move(state.tree.value[1], -1); setStored(rows().map(row => ({ ...row, sort: row.sort + 1 })));
  await state.save(); assert.equal(writes.length, 0); assert.equal(messages.length, 0);
  assert.match(state.error.value, /其他操作修改/); assert.equal(state.dirty.value, false);
});
test('中途失败不提示成功，重新加载服务器已写入的数据', async () => {
  const persisted = rows(); let count = 0;
  const { state, messages } = manager({ list: async () => structuredClone(persisted), update: async patch => { if (++count === 2) throw new Error('网络中断'); Object.assign(persisted.find(row => row.id === patch.id), patch); } });
  await open(state); state.move(state.tree.value[1], -1); await state.save();
  assert.equal(messages.length, 0); assert.match(state.error.value, /网络中断/); assert.equal(state.saving.value, false);
  assert.equal(state.tree.value[0].id, 2);
});
test('权限不足不能打开；树节点不能跨父级或作为子节点拖入', async () => {
  const { state } = manager({ _permission: { list: true, update: false } }); await open(state);
  assert.equal(state.visible.value, false);
  assert.equal(state.canDrop({ data: { id: 1, parentId: 3 } }, { data: { id: 2, parentId: 4 } }, 'before'), false);
  assert.equal(state.canDrop({ data: { id: 1 } }, { data: { id: 2 } }, 'inner'), false);
});
