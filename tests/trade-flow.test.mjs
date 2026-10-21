import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
import { parse } from "@vue/compiler-sfc";

// 只在Node测试中替换网络与生命周期，生产页面仍调用真实后端。
async function pageScript(path, fixture, exported) {
  const source = readFileSync(
    new URL("../apps/client/" + path, import.meta.url),
    "utf8",
  );
  const { descriptor } = parse(source);
  const script = stripTypeScriptTypes(descriptor.scriptSetup.content).replace(
    /^import[\s\S]*?;\s*/gm,
    "",
  );
  return import(
    "data:text/javascript;base64," +
      Buffer.from(
        fixture + "\n" + script + "\nexport { " + exported + " };\n",
      ).toString("base64")
  );
}

const purchase = await pageScript(
  "pages/discover/coupon.uvue",
  `
const ref = value => ({ value });
const computed = getter => ({ get value() { return getter(); } });
const isDark = false, callbacks = [], hides = [];
const watch = (_value, callback) => callbacks.push(callback);
const onLoad = () => {}, onUnmounted = () => {}, onHide = callback => hides.push(callback);
const fixtureUser = { token: 'test-customer', sessionVersion: { value: 1 } };
const useStore = () => ({ user: fixtureUser });
export const navigation = [], calls = [];
const router = { to(path) { navigation.push(path); } };
const uni = { setNavigationBarTitle() {} };
let sequence = 0, reply = async () => ({ id: 201 });
const createPurchaseRequestId = () => 'purchase_test_' + (++sequence);
const createOrder = async input => { calls.push(input); return reply(input); };
const claimCoupon = createOrder;
const getCoupon = async () => null;
const formatMoney = value => String(value);
const getApiErrorMessage = (error, fallback) => error.message || fallback;
export function respond(handler) { reply = handler; }
export function reset() {
 fixtureUser.sessionVersion.value++; callbacks.forEach(callback => callback());
 coupon.value = { id: 101, salePrice: 990, saleState: 'AVAILABLE' };
 quantity.value = 1; calls.length = 0; navigation.length = 0;
}
export function hide() { hides.forEach(callback => callback()); }
`,
  "purchase, newPurchase, purchaseKey, uncertain, createdOrderId, busy, quantity, purchaseError",
);

test("领购提交中拦截重复点击，断网后重试复用原操作，成功后返回只查看原订单", async () => {
  purchase.reset();
  let fail;
  purchase.respond(
    () =>
      new Promise((_resolve, reject) => {
        fail = reject;
      }),
  );
  const pending = purchase.purchase();
  await purchase.purchase();
  assert.equal(purchase.calls.length, 1);
  const originalKey = purchase.purchaseKey.value;
  fail(new Error("网络断开"));
  await pending;
  assert.equal(purchase.uncertain.value, true);
  assert.equal(purchase.purchaseKey.value, originalKey);
  purchase.respond(async () => ({ id: 201 }));
  await purchase.purchase();
  assert.equal(purchase.calls[1].requestId, originalKey);
  assert.equal(purchase.createdOrderId.value, "201");
  await purchase.purchase();
  assert.equal(purchase.calls.length, 2);
  assert.equal(purchase.navigation.at(-1), "/pages/trade/order?id=201");
});

test("业务拒绝保留错误，离页后的未知结果不跳转，重进仍能幂等查询原操作", async () => {
  purchase.reset();
  purchase.respond(async () => {
    throw { code: 1001, message: "超过限领数量" };
  });
  await purchase.purchase();
  assert.equal(purchase.purchaseKey.value, "");
  assert.equal(purchase.uncertain.value, false);
  assert.match(purchase.purchaseError.value, /超过限领数量/);
  let finish;
  purchase.respond(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const pending = purchase.purchase(),
    key = purchase.purchaseKey.value;
  purchase.hide();
  finish({ id: 202 });
  await pending;
  assert.equal(purchase.navigation.length, 0);
  assert.equal(purchase.purchaseKey.value, key);
  purchase.respond(async () => ({ id: 202 }));
  await purchase.purchase();
  assert.equal(purchase.calls.at(-1).requestId, key);
});

const order = await pageScript(
  "pages/trade/order.uvue",
  `
const ref = value => ({ value }), isDark = false;
const fixtureUser = { sessionVersion: { value: 1 } }, useStore = () => ({ user: fixtureUser });
const router = { to() {} }, hides = [], onHide = callback => hides.push(callback);
let action = async () => ({ id: 201, orderStatus: 'PAID' });
let authoritative = async () => ({ id: 201, orderStatus: 'PENDING' });
export const calls = [];
const simulatePay = id => { calls.push(['pay', id]); return action(); };
const cancelOrder = id => { calls.push(['cancel', id]); return action(); };
const getOrder = async () => null;
const detail = { value: ref({ id: 201, orderStatus: 'PENDING' }), loading: ref(false), error: ref(''), loggedIn: ref(true), load: async () => { detail.value.value = await authoritative(); } };
const usePrivateDetail = () => detail;
const formatMoney = value => String(value);
const getApiErrorMessage = (error, fallback) => error.message || fallback;
export function respond(handler) { action = handler; }
export function refreshWith(handler) { authoritative = handler; }
export function reset() { hides.forEach(callback => callback()); detail.value.value = { id: 201, orderStatus: 'PENDING' }; calls.length = 0; }
export function hide() { hides.forEach(callback => callback()); }
`,
  "mutate, refresh, order, busy, uncertain, actionError",
);

test("支付结果不明时阻止支付与取消，刷新后只接受服务端订单状态", async () => {
  order.reset();
  order.respond(async () => {
    throw new Error("请求超时");
  });
  await order.mutate("pay");
  assert.equal(order.order.value.orderStatus, "PENDING");
  assert.equal(order.uncertain.value, true);
  await order.mutate("pay");
  await order.mutate("cancel");
  assert.equal(order.calls.length, 1);
  order.refreshWith(async () => ({
    id: 201,
    orderStatus: "PAID",
    couponIds: [401],
  }));
  await order.refresh();
  assert.equal(order.order.value.orderStatus, "PAID");
  assert.equal(order.uncertain.value, false);
  await order.mutate("cancel");
  assert.equal(order.calls.length, 1);
});

test("支付与取消互斥，离开订单后忽略迟到的成功响应", async () => {
  order.reset();
  let finish;
  order.respond(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const pending = order.mutate("pay");
  await order.mutate("cancel");
  assert.deepEqual(order.calls, [["pay", 201]]);
  order.hide();
  finish({ id: 201, orderStatus: "PAID" });
  await pending;
  assert.equal(order.order.value.orderStatus, "PENDING");
  assert.equal(order.busy.value, false);
});
