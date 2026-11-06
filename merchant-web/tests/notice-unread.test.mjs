import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import ts from 'typescript';
import { createPinia, setActivePinia, defineStore } from 'pinia';
import { ref } from 'vue';
import * as Vue from 'vue';

const source = readFileSync(new URL('../src/modules/workspace/stores/notices.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS }
});

function fixture(request) {
  setActivePinia(createPinia());
  const user = { token: 'manager-token' };
  const exports = {};
  new Function('require', 'exports', outputText)(name => {
    if (name === 'pinia') return { defineStore };
    if (name === 'vue') return { ref };
    if (name === '/$/base/store/user') return { useUserStore: () => user };
    if (name === '../api') return { workspaceApi: request };
    throw new Error(name);
  }, exports);
  return { store: exports.useNoticeStore(), user };
}

test('未读状态可以从有通知更新为全部已读', async () => {
  let count = 3;
  const { store } = fixture(async path => {
    assert.equal(path, 'notices/unread');
    return count;
  });
  await store.refresh();
  assert.equal(store.unread, 3);
  count = 0;
  await store.refresh();
  assert.equal(store.unread, 0);
});

test('阅读后的最新结果不会被较早的轮询响应覆盖', async () => {
  const pending = [];
  const { store } = fixture(() => new Promise(resolve => pending.push(resolve)));
  const poll = store.refresh();
  const afterReading = store.refresh();
  pending[1](0);
  await afterReading;
  pending[0](4);
  await poll;
  assert.equal(store.unread, 0);
});

test('换账号和退出时丢弃原账号尚未完成的请求', async () => {
  const pending = [];
  const { store, user } = fixture(() => new Promise(resolve => pending.push(resolve)));
  const oldRequest = store.refresh();
  user.token = 'staff-token';
  store.reset();
  const currentRequest = store.refresh();
  pending[1](2);
  await currentRequest;
  pending[0](9);
  await oldRequest;
  assert.equal(store.unread, 2);
  const logoutRequest = store.refresh();
  user.token = '';
  store.reset();
  pending[2](8);
  await logoutRequest;
  assert.equal(store.unread, 0);
});

test('未登录不请求接口，临时断网保留当前红点', async () => {
  let calls = 0;
  const { store, user } = fixture(async () => {
    calls++;
    if (calls > 1) throw new Error('offline');
    return 1;
  });
  await store.refresh();
  await store.refresh();
  assert.equal(store.unread, 1);
  user.token = '';
  await store.refresh();
  assert.equal(calls, 2);
  assert.equal(store.unread, 0);
});

const menuSource = readFileSync(new URL('../src/modules/base/pages/main/components/bmenu.tsx', import.meta.url), 'utf8');
const menuCode = ts.transpileModule(menuSource, {
  compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS,
    jsx: ts.JsxEmit.React, jsxFactory: 'h'
  }
}).outputText;

function sidebar(list, notices) {
  const exports = {};
  new Function('require', 'exports', menuCode)(name => {
    if (name === 'vue') return { ...Vue, watch() {} };
    if (name === '/$/base') return { useBase: () => ({ menu: { list }, app: { isFold: false } }) };
    if (name === '/@/cool') return { useCool: () => ({ route: { path: '/' }, browser: { isMini: false }, refs: {}, setRefs() {} }) };
    if (name === 'lodash-es') return { debounce: value => value };
    if (name === '/$/workspace/stores/notices') return { useNoticeStore: () => notices };
    throw new Error(name);
  }, exports);
  return exports.default.setup({ keyWord: '' });
}

function descendants(node) {
  if (!node || typeof node !== 'object') return [];
  const children = Array.isArray(node.children) ? node.children : node.children?.default?.() || [];
  return [node, ...children.flat(Infinity).flatMap(descendants)];
}

test('没有业务菜单授权的账号也有收件箱入口，图标上的红点随未读数更新', () => {
  const notices = { unread: 2 };
  const render = sidebar([], notices);
  let nodes = descendants(render());
  assert.equal(nodes.filter(node => node.type === 'el-menu-item' && node.props.index === '/my/notices').length, 1);
  assert.equal(nodes.filter(node => node.props?.class === 'b-menu__notice-dot').length, 1);
  notices.unread = 0;
  nodes = descendants(render());
  assert.equal(nodes.filter(node => node.props?.class === 'b-menu__notice-dot').length, 0);
});

test('已有授权通知菜单时复用原菜单，避免重复入口', () => {
  const render = sidebar([{
    id: 9, type: 1, path: '/workspace-admin/notices', icon: 'icon-notice',
    isShow: true, meta: { label: '站内通知' }
  }], { unread: 1 });
  const nodes = descendants(render());
  assert.equal(nodes.filter(node => node.type === 'el-menu-item').length, 1);
  assert.equal(nodes.find(node => node.type === 'el-menu-item').props.index, '/workspace-admin/notices');
  assert.equal(nodes.filter(node => node.props?.class === 'b-menu__notice-dot').length, 1);
});

test('通知被设为首页时仍识别原路由并显示红点', () => {
  const render = sidebar([{
    id: 9, type: 1, path: '/', router: '/workspace-admin/notices',
    icon: 'icon-notice', isShow: true, meta: { label: '站内通知', isHome: true }
  }], { unread: 1 });
  const nodes = descendants(render());
  assert.equal(nodes.filter(node => node.type === 'el-menu-item').length, 1);
  assert.equal(nodes.filter(node => node.props?.class === 'b-menu__notice-dot').length, 1);
});

test('普通菜单和通知菜单的图标保持为直接子节点，避免徽标包裹破坏对齐', () => {
  const render = sidebar([{
    id: 1, type: 1, path: '/', icon: 'icon-home', isShow: true,
    meta: { label: '工作台' }
  }], { unread: 2 });
  const nodes = descendants(render());
  for (const item of nodes.filter(node => node.type === 'el-menu-item')) {
    const children = Array.isArray(item.children) ? item.children : item.children.default();
    assert.equal(children[0].type, 'cl-svg');
    assert.equal(children[1].type, 'span');
  }
  assert.equal(nodes.filter(node => node.type === 'el-badge').length, 0);
});
