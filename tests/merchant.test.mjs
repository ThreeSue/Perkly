import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const source = read("../apps/merchant/api/index.ts");
const api = await import(
  "data:text/javascript;base64," +
    Buffer.from(stripTypeScriptTypes(source)).toString("base64")
);
test("接口缺失时所有商家能力明确拒绝，不制造空结果或成功状态", async () => {
  assert.equal(Object.keys(api.merchantCapabilities).length, 13);
  for (const capability of Object.keys(api.merchantCapabilities)) {
    await assert.rejects(api.requestMerchantCapability(capability), {
      message: api.merchantCapabilities[capability].reason,
    });
  }
  assert.doesNotMatch(
    source,
    /mockService|fetch\(|uni\.request|Storage|localStorage|sessionStorage/,
  );
});
test("商家端不再开放演示身份，路由覆盖功能图且不混入模板功能", () => {
  assert.match(
    read("../apps/merchant/config/business.ts"),
    /mockEnabled: false/,
  );
  assert.doesNotMatch(
    read("../apps/merchant/stores/session.ts"),
    /api\.login|DemoSession|mockService|setStorage/,
  );
  const pages = JSON.parse(read("../apps/merchant/pages.json"));
  const names = pages.pages.map((item) => item.path.split("/").at(-1));
  assert.deepEqual(names, [
    "home",
    "records",
    "profile",
    "login",
    "verify",
    "coupons",
    "create",
    "audits",
    "help",
    "feedback",
  ]);
  assert.deepEqual(
    pages.tabBar.list.map((item) => item.text),
    ["工作台", "核销记录", "我的"],
  );
});
