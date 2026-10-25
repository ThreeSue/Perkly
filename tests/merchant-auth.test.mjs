import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
const read = (file) =>
  readFileSync(new URL("../apps/merchant/" + file, import.meta.url), "utf8");
let serial = 0;
const moduleUrl = (source) =>
  "data:text/javascript;base64," +
  Buffer.from(stripTypeScriptTypes(source, { mode: "transform" })).toString(
    "base64",
  ) +
  "#" +
  serial++;
const ok = (data) => ({ statusCode: 200, data: { code: 1000, data } });
const tokens = {
  token: "merchant_access",
  expire: 120,
  refreshToken: "merchant_refresh",
  refreshExpire: 600,
};
const defer = () => {
  let resolve;
  const promise = new Promise((r) => {
    resolve = r;
  });
  return { promise, resolve };
};
async function setup(handler) {
  const calls = [],
    storage = new Map();
  globalThis.uni = {
    getStorageSync: (key) => storage.get(key),
    setStorageSync: (key, value) => storage.set(key, value),
    removeStorageSync: (key) => storage.delete(key),
    request(options) {
      calls.push(options);
      Promise.resolve()
        .then(() => handler(options))
        .then(
          (response) => options.success(response),
          () => options.fail({ errMsg: "transport failed" }),
        );
    },
  };
  const httpUrl = moduleUrl(
    read("api/merchant-http.ts").replace(
      '"@/config"',
      JSON.stringify(
        moduleUrl('export const config = { baseUrl: "http://backend.test" };'),
      ),
    ),
  );
  return {
    http: await import(httpUrl),
    calls,
    storage,
    api: (file) =>
      import(
        moduleUrl(
          read(file).replace('"@/api/merchant-http"', JSON.stringify(httpUrl)),
        )
      ),
  };
}
test("真实商家认证与消费API按契约发送；公共认证不带旧令牌，账单不注入范围或客户端优惠", async () => {
  const env = await setup(() => ok(tokens));
  env.http.saveMerchantCredentials(tokens);
  const auth = await env.api("api/merchant-auth.ts"),
    api = await env.api("api/merchant-consumption.ts");
  await auth.getMerchantCaptcha();
  await auth.loginMerchant({
    username: "staff",
    password: "secret",
    captchaId: "image",
    verifyCode: "ABCD",
  });
  await auth.getMerchantStaff();
  await api.inspectCoupon("a".repeat(64));
  await api.prepareConsumption({
    code: "a".repeat(64),
    consumptionAmount: 15000,
    remark: "3号桌",
    requestId: "same_request_1",
  });
  await api.confirmConsumption({
    orderId: 12,
    version: 2,
    receivedAmount: 13000,
  });
  await api.listConsumptions({ status: "PENDING" });
  await api.listRedemptions(2, "store");
  for (const call of env.calls.slice(0, 2))
    assert.equal(call.header.Authorization, undefined);
  for (const call of env.calls.slice(2))
    assert.equal(call.header.Authorization, tokens.token);
  assert.deepEqual(env.calls[1].data, {
    username: "staff",
    password: "secret",
    captchaId: "image",
    verifyCode: "ABCD",
  });
  assert.deepEqual(env.calls[4].data, {
    code: "a".repeat(64),
    consumptionAmount: 15000,
    remark: "3号桌",
    requestId: "same_request_1",
  });
  assert.deepEqual(env.calls[5].data, {
    orderId: 12,
    version: 2,
    receivedAmount: 13000,
  });
  assert.deepEqual(env.calls[6].data, { page: 1, size: 20, status: "PENDING" });
  assert.deepEqual(env.calls[7].data, { page: 2, size: 20, scope: "store" });
  assert.equal(env.storage.size, 1);
  assert.ok(!JSON.stringify([...env.storage.values()]).includes("staff"));
});
test("并发401共用一次刷新，迟到的旧Token401不会重复轮换", async () => {
  const late = defer(),
    rotated = defer();
  let refreshes = 0;
  const env = await setup(async (call) => {
    if (call.url.endsWith("refreshToken")) {
      refreshes++;
      rotated.resolve();
      return ok({
        ...tokens,
        token: "rotated",
        refreshToken: "rotated_refresh",
      });
    }
    if (call.header.Authorization === tokens.token) {
      if (call.url.endsWith("late")) {
        await rotated.promise;
        await late.promise;
      }
      return { statusCode: 401, data: { code: 401, message: "expired" } };
    }
    return ok("verified");
  });
  env.http.saveMerchantCredentials(tokens);
  const slow = env.http.merchantRequest("/late"),
    fast = env.http.merchantRequest("/first");
  assert.equal(await fast, "verified");
  late.resolve();
  assert.equal(await slow, "verified");
  assert.equal(refreshes, 1);
  const call = env.calls.find((item) => item.url.endsWith("refreshToken"));
  assert.equal(call.header.Authorization, undefined);
  assert.deepEqual(call.data, { refreshToken: tokens.refreshToken });
});
test("403不刷新；刷新失败结束等待并清除凭证，不出现无限队列", async () => {
  let refreshes = 0,
    expired = 0;
  const env = await setup((call) => {
    if (call.url.endsWith("refreshToken")) {
      refreshes++;
      return {
        statusCode: 401,
        data: { code: 401, message: "invalid session" },
      };
    }
    return {
      statusCode: call.url.endsWith("forbidden") ? 403 : 401,
      data: {
        code: call.url.endsWith("forbidden") ? 403 : 401,
        message: "denied",
      },
    };
  });
  env.http.saveMerchantCredentials(tokens);
  env.http.onMerchantSessionExpired(() => expired++);
  await assert.rejects(env.http.merchantRequest("/forbidden"), /denied/);
  assert.equal(refreshes, 0);
  await assert.rejects(env.http.merchantRequest("/private"), /invalid session/);
  assert.equal(refreshes, 1);
  assert.equal(expired, 1);
  assert.equal(env.http.hasMerchantCredentials(), false);
  assert.equal(env.storage.size, 0);
});
test("退出或换号后旧响应不能继续消费，新账号不会被旧401清除", async () => {
  const pending = defer();
  const env = await setup(() => pending.promise);
  env.http.saveMerchantCredentials(tokens);
  const request = env.http.merchantRequest("/private");
  await Promise.resolve();
  env.http.clearMerchantCredentials();
  env.http.saveMerchantCredentials({ ...tokens, token: "new_account" });
  pending.resolve({ statusCode: 401, data: { code: 401 } });
  await assert.rejects(request, /状态已改变/);
  assert.equal(env.http.hasMerchantCredentials(), true);
  assert.equal(env.calls.length, 1);
});
test("传输失败和写请求500标记结果未知，业务拒绝保留后端错误，不伪造成功", async () => {
  const env = await setup((call) => {
    if (call.url.endsWith("network")) throw new Error("offline");
    if (call.url.endsWith("server")) return { statusCode: 500, data: null };
    return { statusCode: 200, data: { code: 1001, message: "优惠券已使用" } };
  });
  env.http.saveMerchantCredentials(tokens);
  for (const path of ["/network", "/server"])
    await assert.rejects(
      env.http.merchantRequest(path, "POST", {}),
      (error) => error.uncertain === true,
    );
  await assert.rejects(
    env.http.merchantRequest("/business", "POST", {}),
    (error) => error.message === "优惠券已使用" && !error.uncertain,
  );
});
test("拒绝OWNER、未知角色和门店授权异常，入驻/核销路径按新旧规则分流", async () => {
  const { verifiedStaff } = await import(
    moduleUrl(read("utils/merchant-staff.ts"))
  );
  const { merchantRouteAccess } = await import(
    moduleUrl(read("utils/merchant-access.ts"))
  );
  const staff = {
    id: 1,
    merchantId: 2,
    storeId: 3,
    role: "STAFF",
    canViewStoreRecords: false,
  };
  assert.equal(verifiedStaff(staff).role, "STAFF");
  for (const value of [
    { ...staff, role: "OWNER" },
    { ...staff, storeId: null },
    { ...staff, role: "MANAGER" },
  ])
    assert.throws(() => verifiedStaff(value));
  for (const page of ["verify", "staff", "join", "create"])
    assert.equal(
      merchantRouteAccess("/pages/business/" + page, true, true, true),
      "unavailable",
    );
  assert.equal(
    merchantRouteAccess("/pages/business/onboard", false, false, false),
    "public",
  );
  assert.equal(
    merchantRouteAccess("/pages/business/application", true, false, false),
    "allowed",
  );
  assert.equal(
    merchantRouteAccess("/pages/business/application", false, false, false),
    "login",
  );
  assert.equal(
    merchantRouteAccess("/pages/business/records", true, false, false),
    "review",
  );
  assert.equal(merchantRouteAccess("/pages/business/home", false), "login");
});
