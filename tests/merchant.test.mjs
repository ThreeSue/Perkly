import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
import { createRequire } from "node:module";
import { pathToFileURL } from "node:url";
const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const source = read("../apps/merchant/api/index.ts");
const api = await import(
  "data:text/javascript;base64," +
    Buffer.from(stripTypeScriptTypes(source)).toString("base64")
);
const recordPolicy = await import(
  "data:text/javascript;base64," +
    Buffer.from(
      stripTypeScriptTypes(read("../apps/merchant/utils/merchant-records.ts")),
    ).toString("base64")
);
const policyUrl =
  "data:text/javascript;base64," +
  Buffer.from(
    stripTypeScriptTypes(read("../apps/merchant/utils/merchant-records.ts")),
  ).toString("base64");
const merchantRequire = createRequire(
  new URL("../apps/merchant/package.json", import.meta.url),
);
const sessionSource = stripTypeScriptTypes(
  read("../apps/merchant/stores/session.ts"),
)
  .replace(
    '"vue"',
    JSON.stringify(pathToFileURL(merchantRequire.resolve("vue")).href),
  )
  .replace('"@/utils/merchant-records"', JSON.stringify(policyUrl));
const session = await import(
  "data:text/javascript;base64," + Buffer.from(sessionSource).toString("base64")
);

const context = (role = "employee") => ({
  operatorId: "employee-a",
  merchantId: "merchant-a",
  storeId: "store-a",
  stores: [
    { storeId: "store-a", merchantId: "merchant-a", role },
    { storeId: "store-b", merchantId: "merchant-a", role: "manager" },
  ],
});

test("员工始终限定本人；店长直接查看当前门店全店，不带操作员筛选", () => {
  assert.deepEqual(recordPolicy.resolveMerchantRecordAccess(context()), {
    scope: "self",
    merchantId: "merchant-a",
    storeId: "store-a",
    operatorId: "employee-a",
  });
  assert.deepEqual(
    recordPolicy.resolveMerchantRecordAccess(context("manager")),
    {
      scope: "store",
      merchantId: "merchant-a",
      storeId: "store-a",
      operatorId: null,
    },
  );
  const switched = context();
  switched.storeId = "store-b";
  assert.equal(
    recordPolicy.resolveMerchantRecordAccess(switched).scope,
    "store",
  );
});

test("未登录、跨商家、未授权门店、未知或冲突角色都不能取得记录权限", () => {
  assert.equal(recordPolicy.resolveMerchantRecordAccess(null), null);
  for (const invalid of [
    { ...context(), operatorId: " " },
    { ...context(), merchantId: "merchant-b" },
    { ...context(), storeId: "store-unknown" },
    { ...context(), stores: [] },
    context("OWNER"),
    {
      ...context(),
      stores: [...context().stores, context("manager").stores[0]],
    },
  ])
    assert.equal(recordPolicy.resolveMerchantRecordAccess(invalid), null);
});

test("日期遵循北京时间，近7天/30天含今天且正确跨月跨年", () => {
  assert.equal(
    recordPolicy.shanghaiDate(Date.parse("2026-10-22T16:00:00Z")),
    "2026-10-23",
  );
  assert.equal(
    recordPolicy.shanghaiDate(Date.parse("2026-10-22T15:59:59Z")),
    "2026-10-22",
  );
  assert.deepEqual(
    recordPolicy.createRecordDateShortcuts("2026-01-03").map((x) => x.value),
    [
      ["2026-01-03", "2026-01-03"],
      ["2025-12-28", "2026-01-03"],
      ["2025-12-05", "2026-01-03"],
    ],
  );
  assert.deepEqual(
    recordPolicy.createRecordDateShortcuts("2024-03-01")[1].value,
    ["2024-02-24", "2024-03-01"],
  );
});

test("会话默认拒绝，认证上下文不受调用方后续修改影响，换号/清除立即撤回权限", () => {
  assert.equal(session.merchantRecordAccess.value, null);
  const employee = context();
  session.applyVerifiedMerchantContext(employee);
  employee.stores[0].role = "manager";
  assert.equal(session.merchantRecordAccess.value.scope, "self");
  session.applyVerifiedMerchantContext(context("manager"));
  assert.equal(session.merchantRecordAccess.value.scope, "store");
  session.clearMerchantSession();
  assert.equal(session.merchantRecordAccess.value, null);
  assert.equal(session.merchantSessionReady.value, false);
  session.applyVerifiedMerchantContext({ ...context(), storeId: "unknown" });
  assert.equal(session.merchantRecordAccess.value, null);
});
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
