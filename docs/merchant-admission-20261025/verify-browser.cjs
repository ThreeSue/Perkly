const { chromium } = require(
  process.env.MERCHANT_REVIEW_PLAYWRIGHT ||
    "C:/Users/Eric/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright",
);
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
(async () => {
  const browser = await chromium.launch({ channel: "msedge", headless: true });
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    reducedMotion: "reduce",
    colorScheme: "dark",
  });
  const p = await context.newPage(),
    errors = [],
    writes = [],
    evidence = [];
  p.setDefaultTimeout(15000);
  p.on("pageerror", (e) => errors.push(e.message));
  p.on("request", (r) => {
    if (["POST", "PUT", "PATCH", "DELETE"].includes(r.method()))
      writes.push(r.url());
  });
  const button = (name) => p.getByRole("button", { name, exact: true });
  const field = (name) => p.locator('input[id^="auth-' + name + '-"]:visible');
  const route = async (name) => {
    await p.waitForURL((u) =>
      name === "welcome"
        ? ["#/", "#/pages/business/welcome"].includes(u.hash)
        : u.hash === "#/pages/business/" + name,
    );
    await p.waitForTimeout(450);
  };
  const confirm = () => p.locator(".cl-confirm:visible");
  const disabled = async (selector) =>
    p
      .locator(selector)
      .evaluate(
        (n) =>
          n.getAttribute("aria-disabled") === "true" ||
          n.getAttribute("disabled") === "true" ||
          n.disabled === true ||
          n.classList.contains("uni-button-disabled") ||
          n.classList.contains("cl-button--disabled"),
      );
  async function snap(name) {
    const metrics = await p.evaluate(() => ({
      width: innerWidth,
      height: innerHeight,
      overflow: document.documentElement.scrollWidth > innerWidth,
      dark: document.querySelectorAll(".is-dark").length,
      progressHeight:
        document.querySelector(".admission-progress")?.getBoundingClientRect()
          .height ?? null,
    }));
    assert.equal(metrics.overflow, false, name + " overflow");
    assert.equal(metrics.dark, 0);
    await p.screenshot({
      path: path.join(__dirname, name + ".png"),
      fullPage: true,
    });
    evidence.push({ name, ...metrics });
  }
  async function details() {
    if (!(await p.locator(".admission-expanded-body:visible").count()))
      await p.locator(".admission-trigger").click();
    await button("申请详情").click();
    await route("application");
  }
  async function tools() {
    await button("开发测试：模拟平台审核 展开").click();
  }
  async function logout() {
    await p.locator('.floating-nav-item[aria-label="我的"]').click();
    await route("profile");
    await button("退出登录").click();
    await confirm().waitFor();
    await confirm()
      .getByRole("button", { name: "退出登录", exact: true })
      .click();
    await route("welcome");
  }
  try {
    await p.goto("http://localhost:9902/");
    await route("welcome");
    await button("申请商家入驻").click();
    await route("onboard");
    await field("merchantName").fill("审核测试咖啡");
    await field("storeName").fill("园区测试门店");
    await button("使用示例位置测试").click();
    await button("下一步，设置账号").click();
    await field("phone").fill("13800000101");
    await field("password").fill("TestOnly!2026");
    await field("confirmation").fill("TestOnly!2026");
    await p.getByRole("button", { name: /完成入驻|提交入驻申请/ }).click();
    await route("home");
    await p.getByText("等待平台审核", { exact: true }).waitFor();
    assert.equal(await disabled(".mono-scan"), true);
    await snap("home-pending-collapsed");
    await p.locator(".admission-trigger").press("Enter");
    await p.locator(".admission-expanded-body").waitFor();
    assert.equal(
      await p.locator(".admission-trigger").getAttribute("aria-expanded"),
      "true",
    );
    await snap("home-pending-expanded");
    const height = await p
      .locator(".admission-progress")
      .evaluate((n) => n.getBoundingClientRect().height);
    assert.ok(height >= 844 * 0.45);
    await p.evaluate(() => {
      location.hash = "/pages/business/create?approved=true";
    });
    await route("home");
    assert.equal(await disabled(".mono-scan"), true);
    await details();
    await tools();
    await button("模拟审核驳回").click();
    await p.getByText("请填写需要修改的资料及原因", { exact: true }).waitFor();
    const reason =
      "请补充清晰的门店名称并核对营业地址。测试长文案用于确认小屏可以完整阅读，不截断具体修改要求。";
    await field("reviewReason").fill(reason);
    await button("模拟审核驳回").click();
    await p.getByText("入驻资料需修改", { exact: true }).first().waitFor();
    await snap("application-rejected");
    await p.goBack();
    await route("home");
    await confirm().waitFor();
    await snap("rejection-notice");
    await confirm().getByRole("button", { name: "确定", exact: true }).click();
    await confirm().waitFor({ state: "hidden" });
    assert.equal(await p.locator(".admission-progress:visible").count(), 1);
    await button("修改资料").click();
    await route("application");
    await button("修改资料并重新提交").click();
    await field("merchantName").fill("审核测试咖啡修订");
    await button("取消修改").click();
    await confirm().waitFor();
    await confirm()
      .getByRole("button", { name: "继续修改", exact: true })
      .click();
    assert.equal(await field("merchantName").inputValue(), "审核测试咖啡修订");
    await button("重新提交审核").click();
    await p.getByText("等待平台审核", { exact: true }).waitFor();
    assert.equal(await p.locator(".admission-history-row").count(), 3);
    await snap("application-resubmitted");
    await tools();
    await button("模拟审核通过").click();
    await p.getByText("入驻审核通过", { exact: true }).first().waitFor();
    // While outside home, approval is observable in the store but no home reminder is shown.
    assert.equal(await confirm().count(), 0);
    await p.goBack();
    await route("home");
    await confirm().waitFor();
    assert.equal(
      await disabled(".mono-scan"),
      false,
      "approval must unlock before reminder acknowledgment",
    );
    await snap("approval-notice-before-confirm");
    // Escape dismisses only this reminder; it must not acknowledge or revoke permissions.
    await confirm()
      .getByRole("button", { name: "确定", exact: true })
      .press("Escape");
    await confirm().waitFor({ state: "hidden" });
    assert.equal(await p.locator(".admission-progress:visible").count(), 1);
    assert.equal(await disabled(".mono-scan"), false);
    await logout();
    await button("已有账号，去登录").click();
    await route("login");
    await field("phone").fill("13800000101");
    await field("password").fill("TestOnly!2026");
    await button("登录，进入工作台").click();
    await route("home");
    await confirm().waitFor();
    await snap("approval-after-relogin");
    await confirm()
      .getByRole("button", { name: "确定", exact: true })
      .press("Enter");
    await confirm().waitFor({ state: "hidden" });
    await p.locator(".admission-progress").waitFor({ state: "hidden" });
    await snap("home-approved-confirmed");
    await p.locator('.floating-nav-item[aria-label="我的"]').click();
    await route("profile");
    await p.getByText("入驻申请与审核记录", { exact: true }).click();
    await route("application");
    assert.equal(await p.locator(".admission-history-row").count(), 4);
    await p.setViewportSize({ width: 320, height: 740 });
    await snap("application-history-narrow");
    await p.goBack();
    await route("profile");
    await p.locator('.floating-nav-item[aria-label="工作台"]').click();
    await route("home");
    assert.equal(await confirm().count(), 0);
    assert.equal(await p.locator(".admission-progress:visible").count(), 0);
    await snap("home-approved-narrow");
    assert.deepEqual(errors, []);
    assert.deepEqual(writes, []);
    fs.writeFileSync(
      path.join(__dirname, "browser-results.json"),
      JSON.stringify(
        { passed: true, evidence, pageErrors: errors, writeRequests: writes },
        null,
        2,
      ),
    );
    console.log(
      JSON.stringify({
        passed: true,
        screenshots: evidence.length,
        pageErrors: errors,
        writeRequests: writes,
      }),
    );
  } catch (error) {
    await p.screenshot({
      path: path.join(__dirname, "failure.png"),
      fullPage: true,
    });
    console.error("URL", p.url());
    console.error((await p.locator("body").innerText()).slice(-2200));
    throw error;
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
