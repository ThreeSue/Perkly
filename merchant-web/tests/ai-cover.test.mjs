import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import ts from 'typescript';
import { parse, compileScript } from '@vue/compiler-sfc';
import * as Vue from 'vue';

const read = name => readFileSync(new URL('../src/modules/ai/' + name, import.meta.url), 'utf8');
function load(code, dependencies) {
	const { outputText } = ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } });
	const exports = {};
	new Function('require', 'exports', outputText)(id => dependencies[id] ?? {}, exports);
	return exports;
}
const domain = load(read('api.ts'), {});
const sessions = new Map();
globalThis.sessionStorage = { getItem: k => sessions.get(k) ?? null, setItem: (k, v) => sessions.set(k, v), removeItem: k => sessions.delete(k) };
function picker(form, api, changes = [], perms = [], platform = true) {
	const { descriptor } = parse(read('components/cover-picker.vue'));
	const script = compileScript(descriptor, { id: 'ai-cover-test' }).content;
	return load(script, {
		vue: { ...Vue, onBeforeUnmount() {}, watch() {} },
		'/$/base': { useBase: () => ({ user: { info: { id: 99, username: platform ? 'admin' : 'staff', merchantId: 1 } }, menu: { perms } }) },
		'/@/workspace': { isPlatform: platform, identity: platform ? 'PLATFORM' : 'STORE' },
		'../api': { ...domain, imageApi: api }
	}).default.setup({ draft: form }, { expose() {}, emit: (event, value) => changes.push({ event, value }) });
}
const form = () => ({ id: 12, merchantId: 1, name: '当前未保存咖啡券', type: 'EXPERIENCE', scopeType: 0, contentItems: [{ name: '手冲咖啡', quantity: 1 }], cover: '/original.png' });
test('空历史和已结束任务停止轮询，处理中静默刷新，失败停止重试', async () => {
	sessions.clear(); const api = mockApi(), vm = picker(form(), api);
	try {
		await vm.open(); assert.equal(vm.timer, undefined);
		api.rows.push({ id: 1, status: 'RUNNING' });
		await vm.refresh(); assert.ok(vm.timer);
		const originalPage = api.page;
		let resolve;
		api.page = () => new Promise(done => { resolve = done; });
		const refreshing = vm.refresh(true);
		assert.equal(vm.loading.value, false);
		resolve({ list: [{ id: 1, status: 'STORED' }], total: 1 });
		await refreshing; assert.equal(vm.timer, undefined);
		api.page = originalPage;
		api.rows[0].status = 'RUNNING'; await vm.refresh(); assert.ok(vm.timer);
		api.page = async () => { throw new Error('网络异常'); };
		await vm.refresh(true); assert.equal(vm.timer, undefined);
	} finally { vm.stopPolling(); }
});
function mockApi() {
	const rows = [], calls = [];
	return {
		rows, calls,
		async prompt(draft) { calls.push({ kind: 'prompt', draft }); return { prompt: `仅展示实际内容：${draft.contentItems[0].name}`, merchantName: '青禾咖啡', storeNames: ['大学城店'] }; },
		async quota() { return { enabled: true, keyConfigured: true, models: ['qwen-image-3.0'], remaining: 10, limit: 10, used: rows.length }; },
		async page() { return { list: [...rows].reverse(), total: rows.length }; },
		async create(input) { calls.push({ kind: 'create', input }); let row = rows.find(r => r.requestId === input.requestId); if (!row) { row = { ...input, id: rows.length + 1, merchantId: 1, materialId: rows.length + 100, url: '/generated.png', status: 'STORED' }; rows.push(row); } return row; },
		async select(merchantId, couponId, id) { calls.push({ kind: 'select', merchantId, couponId, id }); return rows.find(r => r.id === id); }
	};
}
test('当前未保存表单直接生成提示词；所有候选保留，关闭或生成失败不更换原封面', async () => {
	sessions.clear(); const original = form(), api = mockApi(), changes = [];
	const vm = picker(original, api, changes);
	try {
		await vm.open(); assert.equal(api.calls[0].draft.name, original.name);
		await vm.generate('GENERATE'); assert.equal(api.rows.length, 1); assert.equal(changes.length, 0); assert.equal(original.cover, '/original.png');
		vm.visible.value = false; vm.stopPolling();
		await vm.open(); assert.equal(vm.rows.value.length, 1); assert.equal(changes.length, 0);
		await vm.select(api.rows[0]); assert.equal(changes.length, 1); assert.equal(changes[0].value.url, '/generated.png');
		assert.equal(api.calls.find(c => c.kind === 'select').couponId, 12);
		assert.equal(original.cover, '/original.png', '只有父表单接收选用事件才能改封面');
	} finally { vm.visible.value = false; vm.stopPolling(); }
});
test('完善图片传所选原图任务；新生成不携带旧图，两个版本均保留', async () => {
	sessions.clear(); const api = mockApi(), vm = picker(form(), api);
	try {
		await vm.open(); await vm.generate('GENERATE'); const parent = api.rows[0]; vm.chooseParent(parent);
		vm.prompt.value = '保留咖啡杯，将背景改为淡绿色'; await vm.generate('EDIT');
		const edit = api.calls.filter(c => c.kind === 'create')[1].input;
		assert.equal(edit.mode, 'EDIT'); assert.equal(edit.parentTaskId, parent.id); assert.equal(api.rows.length, 2);
		await vm.generate('GENERATE'); assert.equal(api.calls.filter(c => c.kind === 'create')[2].input.parentTaskId, undefined);
		assert.equal(api.rows.length, 3);
	} finally { vm.visible.value = false; vm.stopPolling(); }
});
test('提交结果未知时重开窗口核对同一requestId和原始正文，不另发生成请求', async () => {
	sessions.clear(); const api = mockApi(), originalCreate = api.create; let first = true;
	api.create = async input => { const result = await originalCreate(input); if (first) { first = false; throw new Error('network disconnected'); } return result; };
	const vm = picker(form(), api);
	try {
		await vm.open(); await vm.generate('GENERATE'); assert.ok(vm.pending.value);
		const saved = JSON.stringify(vm.pending.value); vm.prompt.value = '这里的修改不应改变上次请求';
		await vm.resolvePending(); assert.equal(vm.pending.value, undefined);
		const inputs = api.calls.filter(c => c.kind === 'create').map(c => JSON.stringify(c.input));
		assert.deepEqual(inputs, [saved, saved]); assert.equal(api.rows.length, 1);
	} finally { vm.visible.value = false; vm.stopPolling(); }
});
test('缺少AI或优惠券编辑权限不开放；明确业务拒绝后可修正表单重试', async () => {
	for (const perms of [[], ['coupon:create','material:view'], ['ai:image:generate','material:view']]) {
		const vm = picker({ ...form(), id: undefined }, mockApi(), [], perms, false); assert.equal(vm.allowed.value, false);
	}
	sessions.clear(); const api = mockApi(), changes = []; api.create = async () => { throw { business: true, code: 429, message: '今日额度已用完' }; };
	const vm = picker(form(), api, changes);
	try { await vm.open(); await vm.generate('GENERATE'); assert.equal(vm.pending.value, undefined); assert.match(vm.error.value, /额度/); assert.deepEqual(changes, []); }
	finally { vm.visible.value = false; vm.stopPolling(); }
});
test('上下文白名单排除旧封面和客户隐私，非商品券不带历史隐藏商品行', () => {
	const draft = domain.coverDraft({ ...form(), type: 'CASH', phone: '13800000000', password: 'private' });
	assert.deepEqual(draft.contentItems, []); assert.equal(draft.cover, undefined); assert.equal(draft.phone, undefined); assert.equal(draft.password, undefined);
});
