import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync, existsSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
import { parse, compileScript, compileTemplate } from "@vue/compiler-sfc";

const read = (path) =>
  readFileSync(new URL("../" + path, import.meta.url), "utf8");
async function load(path, prelude) {
  const js = stripTypeScriptTypes(read(path)).replace(
    /^import[\s\S]*?;\s*/gm,
    "",
  );
  return import(
    "data:text/javascript;base64," +
      Buffer.from(prelude + "\n" + js).toString("base64")
  );
}
const api = await load(
  "apps/client/api/trade.ts",
  `
export const user = { token: 'customer', sessionVersion: { value: 1 } };
const useStore = () => ({ user });
const config = { baseUrl: 'https://api.example.test' };
let binary = new Uint8Array([137,80,78,71,13,10,26,10]).buffer;
export const binaryCalls = [];
export function binaryReply(value) { binary = value; }
const uni = { request(input) { binaryCalls.push(input); input.success({ statusCode: 200, data: binary }); }, arrayBufferToBase64(value) { return Buffer.from(value).toString('base64'); } };
export const calls = [];
let handler = async () => ({ id: 1 });
export function respond(fn) { calls.length = 0; handler = fn; }
const request = (input) => { calls.push(input); return handler(input); };
`,
);

test("领购只提交契约字段；同一操作重试沿用requestId，不传客户端金额", async () => {
  api.respond(async () => ({ id: 201 }));
  const input = {
    couponTemplateId: 101,
    quantity: 2,
    requestId: api.createPurchaseRequestId(),
    totalAmount: 1,
    customerId: 9,
  };
  assert.match(input.requestId, /^[A-Za-z0-9_-]{8,64}$/);
  await api.createOrder(input);
  await api.createOrder(input);
  assert.deepEqual(api.calls[0].data, {
    couponTemplateId: 101,
    quantity: 2,
    requestId: input.requestId,
  });
  assert.deepEqual(api.calls[0], api.calls[1]);
  await api.claimCoupon(input);
  assert.equal(api.calls[2].url, "/app/order/claim");
  assert.throws(() => api.createOrder({ ...input, quantity: 21 }), /1至20/);
  assert.throws(
    () => api.createOrder({ ...input, couponTemplateId: "NaN" }),
    /ID无效/,
  );
});
test("模拟支付与取消使用订单ID；持券详情使用持券ID", async () => {
  api.respond(async () => ({ id: 201 }));
  await api.simulatePay(201);
  await api.cancelOrder(201);
  await api.getWalletCoupon(401);
  await api.listOrders(2, "PAID");
  assert.deepEqual(api.calls[0].data, { id: 201, result: "SUCCESS" });
  assert.deepEqual(api.calls[1].data, { id: 201 });
  assert.equal(api.calls[2].url, "/app/coupon/wallet/info?id=401");
  assert.deepEqual(api.calls[3].data, { page: 2, size: 20, status: "PAID" });
});
test("未登录不请求；切换账号后的私有响应被拒绝", async () => {
  api.user.token = null;
  api.respond(async () => ({}));
  await assert.rejects(api.getOrder(201), /请先登录/);
  assert.equal(api.calls.length, 0);
  api.user.token = "customer";
  let finish;
  api.respond(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const pending = api.getWalletCoupon(401);
  api.user.sessionVersion.value++;
  finish({ id: 401 });
  await assert.rejects(pending, /身份已变化/);
});

test("备用二维码接口原样鉴权，校验PNG签名并正确展示JSON业务错误", async () => {
  api.user.token = "customer";
  api.respond(async () => ({ id: 401 }));
  assert.match(await api.getWalletQrImage(401), /^data:image\/png;base64,/);
  assert.equal(api.binaryCalls[0].header.Authorization, "customer");
  assert.equal(
    api.binaryCalls[0].url,
    "https://api.example.test/app/coupon/wallet/qrcode?id=401",
  );
  const errorBytes = Buffer.from(
    JSON.stringify({ code: 1001, message: "持券已失效" }),
  );
  api.binaryReply(
    errorBytes.buffer.slice(
      errorBytes.byteOffset,
      errorBytes.byteOffset + errorBytes.byteLength,
    ),
  );
  await assert.rejects(api.getWalletQrImage(401), /持券已失效/);
});

const pages = await load(
  "apps/client/composables/use-private-page.ts",
  `
const ref = value => ({ value });
const computed = getter => ({ get value() { return getter(); } });
const watch = () => {}; const onUnmounted = () => {};
const user = { token: 'customer', sessionVersion: { value: 1 } };
const useStore = () => ({ user });
const getApiErrorMessage = (error, fallback) => error.message || fallback;
`,
);
test("分页切换忽略旧响应；加载更多失败保留已加载记录和页码", async () => {
  let finish;
  const stale = pages.usePrivatePage(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const pending = stale.load(true);
  stale.clear();
  finish({ list: [{ id: 1 }], pagination: { page: 1, total: 1, size: 20 } });
  await pending;
  assert.deepEqual(stale.items.value, []);
  let fail = false;
  const requested = [];
  const list = pages.usePrivatePage(async (page) => {
    requested.push(page);
    if (fail) throw new Error("断网");
    return { list: [{ id: page }], pagination: { page, size: 20, total: 3 } };
  });
  await list.load(true);
  fail = true;
  await list.load(false);
  assert.equal(list.items.value.length, 1);
  assert.equal(list.error.value, "断网");
  fail = false;
  await list.load(false);
  assert.deepEqual(requested, [1, 2, 2]);
  assert.equal(list.items.value.length, 2);
});

const network = await load(
  "apps/client/.cool/service/index.ts",
  `
const isDev = false, ignoreTokens = [], config = { baseUrl: '' }, locale = { value: 'zh-cn' };
const t = value => value; const isNull = value => value == null;
const isObject = value => value != null && typeof value === 'object'; const parse = value => value;
export const user = { token: 'customer', sessionVersion: { value: 1 }, logout() { this.token = null; this.sessionVersion.value++; }, refreshToken: async () => { throw new Error('刷新失败'); } };
const useStore = () => ({ user });
export const storage = { bothExpired: false, isExpired(key) { return key === 'token' || this.bothExpired; } };
const uni = { request() { throw new Error('过期时不应直接发请求'); } };
`,
);
test("刷新失败和双Token过期都结束请求，不遗留永久等待队列", async () => {
  const result = await Promise.allSettled([
    network.request({ url: "/app/order/page" }),
    network.request({ url: "/app/coupon/wallet/page" }),
  ]);
  assert.ok(result.every((item) => item.status === "rejected"));
  network.user.token = "new-session";
  network.storage.bothExpired = true;
  await assert.rejects(network.request({ url: "/app/order/page" }), (error) =>
    error.message.includes("登录已失效"),
  );
});

test("新增路由存在，券包及订单页面通过Vue脚本和模板编译", () => {
  const config = JSON.parse(read("apps/client/pages.json"));
  const routes = [
    ...config.pages.map((page) => page.path),
    ...config.subPackages.flatMap((pack) =>
      pack.pages.map((page) => pack.root + "/" + page.path),
    ),
  ];
  for (const route of routes.filter(
    (route) =>
      route.startsWith("pages/trade/") || route === "pages/index/template",
  )) {
    assert.ok(
      existsSync(new URL("../apps/client/" + route + ".uvue", import.meta.url)),
    );
    const filename = "apps/client/" + route + ".uvue";
    const { descriptor, errors } = parse(read(filename), { filename });
    assert.deepEqual(errors, []);
    const script = compileScript(descriptor, { id: route });
    const result = compileTemplate({
      source: descriptor.template.content,
      filename,
      id: route,
      compilerOptions: { bindingMetadata: script.bindings },
    });
    assert.deepEqual(result.errors, []);
  }
});
