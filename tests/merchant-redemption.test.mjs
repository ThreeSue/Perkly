import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
const source = readFileSync(
  new URL("../apps/merchant/utils/merchant-redemption.ts", import.meta.url),
  "utf8",
);
const { createRedemptionFlow, initialRedemptionState, moneyToCents } =
  await import(
    "data:text/javascript;base64," +
      Buffer.from(stripTypeScriptTypes(source)).toString("base64")
  );
const code = "a".repeat(64);
const order = (fields = {}) => ({
  id: 12,
  orderNo: "XF_test_12",
  storeId: 3,
  customerCouponId: 42,
  version: 1,
  status: "PENDING",
  statusText: "待结算",
  consumptionAmount: 15000,
  discountAmount: 2000,
  payableAmount: 13000,
  remark: "",
  redemptionId: null,
  ...fields,
});
const inspection = (type = "CASH") => {
  const amount = ["CASH", "FULL_REDUCTION", "DISCOUNT"].includes(type);
  return {
    customerCouponId: 42,
    storeId: 3,
    type,
    requiresConsumptionAmount: amount,
    nextAction: amount ? "ENTER_BILL" : "CONFIRM_CONTENT",
  };
};
const uncertain = () =>
  Object.assign(new Error("结果未知"), { uncertain: true });
const defer = () => {
  let resolve;
  const promise = new Promise((r) => {
    resolve = r;
  });
  return { promise, resolve };
};
function setup(overrides = {}) {
  const calls = [],
    state = initialRedemptionState();
  let version = 1;
  const api = {
    inspectCoupon: async () => inspection(),
    prepareConsumption: async () => order(),
    reviseConsumption: async () => order({ version: 2, payableAmount: 14000 }),
    confirmConsumption: async () =>
      order({ status: "COMPLETED", redemptionId: 88 }),
    redeemContent: async () =>
      order({
        status: "COMPLETED",
        redemptionId: 88,
        consumptionAmount: null,
        payableAmount: 0,
      }),
    cancelConsumption: async () => order({ status: "CANCELLED" }),
    getConsumption: async () => order(),
    listConsumptions: async () => ({ list: [] }),
    ...overrides,
  };
  for (const [key, action] of Object.entries(api))
    api[key] = async (input) => {
      calls.push({ key, input });
      return action(input);
    };
  const flow = createRedemptionFlow(api, state, () => ({
    version,
    storeId: "3",
  }));
  flow.open();
  state.code = code;
  return {
    flow,
    state,
    calls,
    switchAccount() {
      version++;
      flow.reset();
    },
  };
}
test("金额按十进制转整数分，拒绝多余小数、浮点格式、0与超限", () => {
  assert.equal(moneyToCents("150.01"), 15001);
  assert.equal(moneyToCents("0.1"), 10);
  assert.equal(moneyToCents("99999999.99"), 9999999999);
  for (const value of [
    "0",
    "-1",
    "1.001",
    "1e2",
    " 1",
    "1.",
    "100000000",
    "NaN",
  ])
    assert.throws(() => moneyToCents(value));
});
test("六类券按服务端类型和动作分流，拒绝未知类型或归属不符", async () => {
  for (const type of [
    "FULL_REDUCTION",
    "DISCOUNT",
    "CASH",
    "PACKAGE",
    "EXPERIENCE",
    "EXCHANGE",
  ]) {
    const env = setup({ inspectCoupon: async () => inspection(type) });
    await env.flow.inspect();
    assert.equal(
      env.state.stage,
      ["FULL_REDUCTION", "DISCOUNT", "CASH"].includes(type)
        ? "amount"
        : "content",
    );
  }
  for (const response of [
    { ...inspection(), storeId: 9 },
    inspection("UNKNOWN"),
    { ...inspection(), nextAction: "CONFIRM_CONTENT" },
  ]) {
    const env = setup({ inspectCoupon: async () => response });
    await env.flow.inspect();
    assert.equal(env.state.inspection, null);
    assert.ok(env.state.error);
  }
});
test("金额开单使用整数分，优惠取服务端；修改使用最新版本并重置收款确认", async () => {
  const env = setup({
    prepareConsumption: async () => order({ remark: "3号桌" }),
  });
  await env.flow.inspect();
  env.state.amount = "150.00";
  env.state.remark = "3号桌";
  await env.flow.prepare();
  const prepare = env.calls.find(
    (call) => call.key === "prepareConsumption",
  ).input;
  assert.equal(prepare.consumptionAmount, 15000);
  assert.equal(prepare.remark, "3号桌");
  assert.match(prepare.requestId, /^[A-Za-z0-9_-]{8,64}$/);
  assert.equal(env.state.order.discountAmount, 2000);
  await env.flow.confirm();
  assert.equal(
    env.calls.filter((call) => call.key === "confirmConsumption").length,
    0,
  );
  env.state.received = true;
  env.flow.edit();
  env.state.amount = "160.00";
  await env.flow.prepare();
  assert.deepEqual(
    env.calls.find((call) => call.key === "reviseConsumption").input,
    { orderId: 12, version: 1, consumptionAmount: 16000, remark: "3号桌" },
  );
  assert.equal(env.state.received, false);
  env.state.received = true;
  await env.flow.confirm();
  assert.deepEqual(
    env.calls.find((call) => call.key === "confirmConsumption").input,
    { orderId: 12, version: 2, receivedAmount: 14000 },
  );
  assert.equal(env.state.order.status, "COMPLETED");
});
test("重复点击开单只发送一次；结果未知重试保留requestId，不能改金额复用", async () => {
  const pending = defer();
  let attempts = 0;
  const env = setup({
    prepareConsumption: async () => {
      attempts++;
      if (attempts === 1) {
        await pending.promise;
        throw uncertain();
      }
      return order();
    },
  });
  await env.flow.inspect();
  env.state.amount = "150";
  const first = env.flow.prepare();
  await env.flow.prepare();
  assert.equal(attempts, 1);
  pending.resolve();
  await first;
  assert.equal(env.state.uncertain, true);
  env.state.amount = "151";
  await env.flow.prepare();
  assert.equal(attempts, 1);
  env.state.amount = "150";
  await env.flow.prepare();
  const calls = env.calls.filter((call) => call.key === "prepareConsumption");
  assert.equal(calls.length, 2);
  assert.equal(calls[0].input.requestId, calls[1].input.requestId);
});
test("开单超时可反查原账单，确认超时先查结果，不重复确认或收款", async () => {
  let completed = false;
  const env = setup({
    prepareConsumption: async () => {
      throw uncertain();
    },
    listConsumptions: async () => ({ list: [order()] }),
    confirmConsumption: async () => {
      completed = true;
      throw uncertain();
    },
    getConsumption: async () =>
      order(completed ? { status: "COMPLETED", redemptionId: 88 } : {}),
  });
  await env.flow.inspect();
  env.state.amount = "150";
  await env.flow.prepare();
  await env.flow.recover();
  assert.equal(env.state.order.id, 12);
  assert.equal(env.state.uncertain, false);
  env.state.received = true;
  await env.flow.confirm();
  await env.flow.confirm();
  assert.equal(
    env.calls.filter((call) => call.key === "confirmConsumption").length,
    1,
  );
  assert.equal(env.state.uncertain, true);
  await env.flow.recover();
  assert.equal(env.state.order.status, "COMPLETED");
  assert.equal(env.state.uncertain, false);
});
test("内容券不传消费金额；未知结果重试同一兑换，完成以服务端状态和核销ID为准", async () => {
  let attempts = 0;
  const env = setup({
    inspectCoupon: async () => inspection("PACKAGE"),
    redeemContent: async () => {
      if (++attempts === 1) throw uncertain();
      return order({
        status: "COMPLETED",
        redemptionId: 88,
        consumptionAmount: null,
        payableAmount: 0,
      });
    },
  });
  await env.flow.inspect();
  await env.flow.redeem();
  await env.flow.redeem();
  const calls = env.calls.filter((call) => call.key === "redeemContent");
  assert.deepEqual(calls[0].input, calls[1].input);
  assert.equal("consumptionAmount" in calls[0].input, false);
  assert.equal(env.state.order.status, "COMPLETED");
});
test("业务拒绝保留输入，取消不冒充核销；新一笔使用新requestId", async () => {
  const refused = setup({
    prepareConsumption: async () => {
      throw new Error("未达到使用门槛");
    },
  });
  await refused.flow.inspect();
  refused.state.amount = "99.00";
  await refused.flow.prepare();
  assert.equal(refused.state.amount, "99.00");
  assert.equal(refused.state.order, null);
  assert.equal(refused.state.uncertain, false);
  assert.match(refused.state.error, /门槛/);
  const env = setup();
  await env.flow.inspect();
  env.state.amount = "150";
  await env.flow.prepare();
  await env.flow.cancel();
  assert.equal(env.state.order.status, "CANCELLED");
  assert.equal(env.state.order.redemptionId, null);
  env.flow.open();
  env.state.code = code;
  await env.flow.inspect();
  env.state.amount = "150";
  await env.flow.prepare();
  const requests = env.calls.filter(
    (call) => call.key === "prepareConsumption",
  );
  assert.notEqual(requests[0].input.requestId, requests[1].input.requestId);
});
test("退出、换号、收起页面后旧响应不展示；其他门店账单不能进入收款确认", async () => {
  const pending = defer();
  const env = setup({ prepareConsumption: async () => pending.promise });
  await env.flow.inspect();
  env.state.amount = "150";
  const request = env.flow.prepare();
  env.switchAccount();
  pending.resolve(order());
  await request;
  assert.equal(env.state.order, null);
  assert.equal(env.state.visible, false);
  assert.equal(env.state.code, "");
  const foreign = setup({ getConsumption: async () => order({ storeId: 9 }) });
  await foreign.flow.resume(12);
  assert.equal(foreign.state.order, null);
  assert.ok(foreign.state.error);
});
