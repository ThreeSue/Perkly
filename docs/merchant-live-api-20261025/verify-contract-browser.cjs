// 离线契约/UI回归：所有 /dev/merchant 请求在此脚本拦截，不写真实后端。
const { chromium } = require(process.env.PERKLY_PLAYWRIGHT);
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
(async () => {
 const browser = await chromium.launch({ channel: "msedge", headless: true });
 const context = await browser.newContext({ viewport: { width: 390, height: 844 }, colorScheme: "dark", reducedMotion: "reduce" });
 const page = await context.newPage(), errors = [], calls = [], screens = [];
 page.setDefaultTimeout(15000); page.on("pageerror", (error) => errors.push(error.message));
 const staff = { id: 1, username: "contract_manager", name: "契约回归店长", merchantId: 2, merchantName: "接口回归商家", storeId: 3, storeName: "接口回归门店", role: "MANAGER", roleText: "店长", canViewStoreRecords: true };
 const content = [{ productId: 501, name: "契约回归套餐项目", quantity: 2, description: "双份服务" }];
 let orders = [], nextId = 10, dropConfirm = true;
 const button = (name) => page.getByRole("button", { name, exact: true });
 const field = (name) => page.locator('input[id^="auth-' + name + '-"]:visible');
 const snap = async (name) => { await page.screenshot({ path: path.join(__dirname, name + ".png") }); screens.push(name); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false); };
 await page.route("**/dev/merchant/**", async (route) => {
  const request = route.request(), endpoint = new URL(request.url()).pathname.replace("/dev", ""), input = request.postDataJSON() || {};
  calls.push({ endpoint, method: request.method(), input, authorization: request.headers().authorization ? "MERCHANT" : "NONE" });
  const reply = (data, code = 1000, message = "success") => route.fulfill({ contentType: "application/json", body: JSON.stringify({ code, message, data }) });
  const orderFor = () => orders.find((order) => String(order.id) === String(input.orderId || new URL(request.url()).searchParams.get("id")));
  if (endpoint.endsWith("/captcha")) return reply({ captchaId: "contract_image", data: "data:image/svg+xml;base64," + Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="150" height="45"><rect width="150" height="45" fill="#eee"/><text x="22" y="32" font-size="26">A7KD</text></svg>').toString("base64") });
  if (endpoint.endsWith("/login")) return reply({ token: "contract_merchant_token", expire: 7200, refreshToken: "contract_refresh", refreshExpire: 604800 });
  if (endpoint.endsWith("/me")) return reply(staff);
  if (endpoint.endsWith("/logout")) return reply(null);
  if (endpoint.endsWith("/inspect")) { const isContent = input.code.startsWith("b"); return reply({ customerCouponId: isContent ? 43 : 42, couponName: isContent ? "接口回归套餐券" : "接口回归满减券", merchantName: staff.merchantName, storeId: 3, storeName: staff.storeName, type: isContent ? "PACKAGE" : "FULL_REDUCTION", typeText: isContent ? "套餐券" : "满减券", requiresConsumptionAmount: !isContent, nextAction: isContent ? "CONFIRM_CONTENT" : "ENTER_BILL", description: "仅用于离线契约回归", validFrom: "2026-10-01 00:00:00", validTo: "2026-12-31 23:59:59", minSpend: isContent ? null : 10000, contents: isContent ? content : [], rule: { contentItems: isContent ? content : [] }, confirmed: false }); }
  if (endpoint.endsWith("/prepare") || endpoint.endsWith("/redeemContent")) {
   const existing = orders.find((order) => order.requestId === input.requestId); if (existing) return reply(existing);
   const isContent = endpoint.endsWith("/redeemContent"), id = nextId++;
   const order = { id, orderNo: "XF_CONTRACT_" + id, merchantId: 2, merchantName: staff.merchantName, storeId: 3, storeName: staff.storeName, operatorId: 1, operatorName: staff.name, customerCouponId: isContent ? 43 : 42, couponName: isContent ? "接口回归套餐券" : "接口回归满减券", couponType: isContent ? "PACKAGE" : "FULL_REDUCTION", couponSnapshot: { contentItems: isContent ? content : [] }, status: isContent ? "COMPLETED" : "PENDING", statusText: isContent ? "已完成" : "待结算", consumptionAmount: isContent ? null : input.consumptionAmount, discountAmount: isContent ? 0 : 2000, payableAmount: isContent ? 0 : input.consumptionAmount - 2000, receivedAmount: isContent ? 0 : null, paymentMode: isContent ? "NONE" : null, remark: input.remark, version: 1, redemptionId: isContent ? id + 100 : null, createdAt: "2026-10-25 12:00:00", requestId: input.requestId };
   orders.unshift(order); return reply(order);
  }
  if (endpoint.endsWith("/confirm")) { const order = orderFor(); Object.assign(order, { status: "COMPLETED", statusText: "已完成", receivedAmount: input.receivedAmount, paymentMode: "OFFLINE", redemptionId: order.id + 100, completedAt: "2026-10-25 12:05:00", version: order.version + 1 }); if (dropConfirm) { dropConfirm = false; return route.abort("failed"); } return reply(order); }
  if (endpoint.includes("/consumption/info")) return reply(orderFor());
  if (endpoint.endsWith("/consumption/page")) { const list = orders.filter((order) => (!input.status || order.status === input.status) && (!input.orderNo || order.orderNo === input.orderNo)); return reply({ list, pagination: { page: 1, size: 20, total: list.length } }); }
  if (endpoint.endsWith("/redemption/page")) { const list = orders.filter((order) => order.status === "COMPLETED").map((order) => ({ id: order.redemptionId, customerCouponId: order.customerCouponId, couponName: order.couponName, storeId: 3, storeName: staff.storeName, operatorId: 1, operatorName: staff.name, consumptionAmount: order.consumptionAmount, discountAmount: order.discountAmount, redeemedAt: "2026-10-25 12:05:00", consumptionOrderId: order.id, orderNo: order.orderNo })); return reply({ list, pagination: { page: 1, size: 20, total: list.length } }); }
  throw new Error("Unexpected endpoint " + endpoint);
 });
 try {
  await page.goto("http://localhost:9902/#/", { waitUntil: "domcontentloaded", timeout: 45000 });
  await page.getByRole("button", { name: "申请商家入驻", exact: true }).waitFor();
  await page.getByText("我已有账号，去登录", { exact: true }).click();
  await field("username").waitFor(); await snap("login-contract");
  await field("username").fill(staff.username); await field("password").fill("ContractOnly!2026"); await field("verifyCode").fill("A7KD"); await button("登录，进入工作台").click();
  await page.waitForURL("**/#/pages/business/home"); await page.getByText(staff.storeName, { exact: true }).waitFor(); await snap("home-contract");
  await button("手动输入券码").click(); await field("couponCode").fill("a".repeat(64)); await button("识别并使用优惠券").click();
  await field("consumptionAmount").fill("150.00"); await snap("amount-contract"); await button("计算优惠并开单").click();
  await page.getByText("XF_CONTRACT_10", { exact: true }).waitFor(); assert.equal(await button("确认已收款并核销").getAttribute("aria-disabled"), "true"); await snap("bill-contract");
  await page.locator(".redemption-received").click(); await button("确认已收款并核销").click(); await page.getByText("结果未确认。请先查询原操作，不要重复收款或更换券码。", { exact: true }).waitFor();
  await button("查询最新账单结果").click(); await page.getByText("核销成功", { exact: true }).waitFor(); await snap("recovered-result-contract"); await button("完成").click();
  assert.equal(calls.filter((call) => call.endpoint.endsWith("/confirm")).length, 1);
  await button("手动输入券码").click(); await field("couponCode").fill("b".repeat(64)); await button("识别并使用优惠券").click(); await page.getByText("契约回归套餐项目 × 2", { exact: true }).waitFor(); await snap("content-contract");
  await button("确认使用并核销").click(); await page.getByText("核销成功", { exact: true }).waitFor(); await button("完成").click();
  assert.ok(page.url().endsWith("/pages/business/home"));
  await page.locator('.floating-nav-item[aria-label="核销记录"]').click(); await page.getByText("XF_CONTRACT_10", { exact: false }).first().waitFor(); await snap("records-contract");
  assert.ok(calls.filter((call) => call.endpoint.endsWith("/redemption/page")).every((call) => call.input.scope === "store"));
  await button("消费账单").click(); await page.getByText("XF_CONTRACT_11", { exact: true }).waitFor(); await snap("orders-contract");
  await page.locator('.floating-nav-item[aria-label="我的"]').click(); await page.getByText(staff.merchantName, { exact: true }).waitFor(); await page.setViewportSize({ width: 320, height: 740 });
  await page.evaluate(() => { document.scrollingElement.scrollTop = document.scrollingElement.scrollHeight; }); await snap("profile-narrow-contract");
  const nativeAfter = await page.locator(".mono-feature").first().evaluate((node) => getComputedStyle(node, "::after").borderTopWidth); assert.equal(nativeAfter, "0px");
  await page.reload(); await page.getByText(staff.merchantName, { exact: true }).waitFor();
  const stored = await page.evaluate(() => JSON.stringify({ ...localStorage })); assert.ok(!stored.includes("ContractOnly!2026")); assert.ok(!stored.includes("a".repeat(64))); assert.ok(!stored.includes(staff.merchantName));
  assert.deepEqual(errors, []); assert.ok(calls.filter((call) => call.endpoint.endsWith("/captcha") || call.endpoint.endsWith("/login")).every((call) => call.authorization === "NONE"));
  fs.writeFileSync(path.join(__dirname, "browser-contract-results.json"), JSON.stringify({ passed: true, mode: "offline intercepted contract fixtures; no real backend writes", screens, errors, requests: calls.map(({ endpoint, method, authorization }) => ({ endpoint, method, authorization })) }, null, 2));
  console.log(JSON.stringify({ passed: true, screens: screens.length, errors }));
 } catch (error) { await page.screenshot({ path: path.join(__dirname, "failure.png") }); console.error(page.url(), (await page.locator("body").innerText()).slice(-2200)); throw error; }
 finally { await browser.close(); }
})().catch((error) => { console.error(error); process.exit(1); });
