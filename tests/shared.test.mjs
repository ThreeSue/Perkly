import test from "node:test";
import assert from "node:assert/strict";
import {
  createMockService,
  validateVoucherCode,
} from "../packages/shared/src/index.js";

test("五端的演示身份独立，保护的操作需要登录", async () => {
  const staff = createMockService("verification");
  const client = createMockService("client");
  await client.login("顾客甲");
  await assert.rejects(staff.preview("QGJ20260001"), /登录/);
  await assert.rejects(client.verify("QGJ20260001"), /不能核销/);
});
test("商家独立端只能查看和领取对应商家优惠券", async () => {
  const brand = createMockService("merchantIndependent", "m001");
  await brand.login("顾客");
  const result = await brand.list("coupons");
  assert.ok(result.items.every((item) => item.merchantId === "m001"));
  await assert.rejects(brand.claim("c002"), /不属于/);
  await assert.rejects(brand.list("users"), /不属于当前应用/);
});
test("核销拒绝跨商家券，重复点击不会写出两条核销记录", async () => {
  const staff = createMockService("verification", "m001");
  await staff.login("店员");
  await assert.rejects(staff.preview("QGJ20260002"), /不属于/);
  const results = await Promise.allSettled([
    staff.verify("QGJ20260001"),
    staff.verify("QGJ20260001"),
  ]);
  assert.equal(results.filter((r) => r.status === "fulfilled").length, 1);
  assert.equal((await staff.list("records")).total, 1);
  assert.equal((await staff.preview("QGJ20260001")).status, "已核销");
});
test("重复领券不增加示例券，券码格式处理一致", async () => {
  assert.equal(validateVoucherCode(" qgj20260001 "), true);
  assert.equal(validateVoucherCode("a/b"), false);
  const client = createMockService("client");
  await client.login("顾客");
  const before = await client.list("wallet");
  await client.claim("c001");
  await client.claim("c001");
  assert.equal((await client.list("wallet")).total, before.total);
});
