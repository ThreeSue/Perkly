const { chromium } = require(process.env.PERKLY_PLAYWRIGHT || "playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
(async () => {
  const browser = await chromium.launch({ channel: "msedge", headless: true });
  const evidence = [],
    errors = [],
    writes = [];
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    colorScheme: "dark",
    reducedMotion: "reduce",
  });
  const p = await context.newPage();
  p.setDefaultTimeout(30000);
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
    await p.waitForTimeout(300);
  };
  async function snap(name) {
    if (name.includes("profile")) {
      await p.evaluate(() => {
        const scroller = document.scrollingElement;
        if (scroller) scroller.scrollTop = scroller.scrollHeight;
      });
      await p.waitForTimeout(100);
      const footer = await p.locator(".mono-profile-bottom").boundingBox();
      const nav = await p.locator(".floating-nav").boundingBox();
      assert.ok(footer && nav, name + " profile footer and navigation visible");
      assert.ok(
        footer.y + footer.height <= nav.y - 8,
        name + " profile actions stay above floating navigation",
      );
      await p.screenshot({
        path: path.join(__dirname, name + "-bottom-visible.png"),
      });
    }
    const metrics = await p.evaluate(() => ({
      width: innerWidth,
      overflow: document.documentElement.scrollWidth > innerWidth,
      scheme: getComputedStyle(document.documentElement).colorScheme,
      darkComponents: document.querySelectorAll(".is-dark").length,
      buttons: [...document.querySelectorAll(".merchant-auth-button")]
        .filter((n) => n.getBoundingClientRect().height > 0)
        .map((n) => ({
          height: n.getBoundingClientRect().height,
          background: getComputedStyle(n).backgroundColor,
        })),
    }));
    assert.equal(metrics.overflow, false, name + " overflow");
    assert.equal(metrics.darkComponents, 0);
    assert.match(metrics.scheme, /light/);
    for (const b of metrics.buttons)
      assert.ok(b.height >= 52, name + " button target");
    evidence.push({ name, ...metrics });
    await p.screenshot({
      path: path.join(__dirname, name + ".png"),
      fullPage: true,
    });
  }
  async function logout() {
    await p.locator('.floating-nav-item[aria-label="我的"]').click();
    await route("profile");
    await button("退出登录").click();
    await p.locator(".cl-confirm:visible").waitFor();
    await p
      .locator(".cl-confirm:visible")
      .getByRole("button", { name: "退出登录", exact: true })
      .click();
    await route("welcome");
    assert.equal(await p.locator(".merchant-page:visible").count(), 0);
  }
  try {
    await p.goto("http://localhost:9902/#/pages/business/profile", {
      waitUntil: "domcontentloaded",
      timeout: 45000,
    });
    await route("welcome");
    assert.equal(
      await p.getByRole("button", { name: /凭邀请|邀请码/ }).count(),
      0,
    );
    await snap("welcome");
    await p.locator(".merchant-text-action").click();
    await route("login");
    assert.equal(
      await p.getByRole("button", { name: /邀请码|员工注册/ }).count(),
      0,
    );
    await snap("login");
    await button("登录，进入工作台").click();
    await p.getByText("请输入 11 位大陆手机号", { exact: true }).waitFor();
    await p.waitForFunction(() =>
      document.activeElement?.id.startsWith("auth-phone-"),
    );
    assert.equal(await field("phone").getAttribute("aria-invalid"), "true");
    await button("返回上一步").click();
    await route("welcome");
    await button("申请商家入驻").click();
    await route("onboard");
    await button("下一步，设置账号").click();
    await p.getByText("请输入 2–40 个字的商家名称", { exact: true }).waitFor();
    await field("merchantName").fill("拾光测试咖啡");
    await field("storeName").fill("园区测试店");
    await button("使用示例位置测试").click();
    await snap("onboard-store");
    await button("下一步，设置账号").click();
    await field("phone").fill("13800000001");
    await field("password").fill("TestOnly!2026");
    await field("confirmation").fill("Different!2026");
    await button("提交入驻申请").click();
    await p.getByText("两次输入的密码不一致", { exact: true }).waitFor();
    await button("显示密码").first().press("Enter");
    assert.equal(await field("password").getAttribute("type"), "text");
    await button("隐藏密码").first().press("Space");
    assert.equal(await field("password").getAttribute("type"), "password");
    await button("返回上一步").click();
    assert.equal(await field("merchantName").inputValue(), "拾光测试咖啡");
    await button("返回上一步").click();
    await p.locator(".cl-confirm:visible").waitFor();
    await p
      .locator(".cl-confirm:visible")
      .getByRole("button", { name: "继续填写", exact: true })
      .click();
    await button("下一步，设置账号").click();
    await field("confirmation").fill("TestOnly!2026");
    await snap("onboard-account");
    const commit = button("提交入驻申请");
    const before = await commit.boundingBox();
    await commit.click();
    await p.locator(".cl-button--loading").waitFor();
    const during = await commit.boundingBox();
    assert.ok(Math.abs(before.width - during.width) < 1);
    assert.ok(Math.abs(before.height - during.height) < 1);
    // A cold request for profile resumes after login.
    await route("profile");
    await snap("manager-profile");
    assert.ok(
      (await p.locator(".mono-identity").innerText()).includes("拾光测试咖啡"),
    );
    await p.getByText("入驻申请与审核记录", { exact: true }).click();
    await route("application");
    await button("开发测试：模拟平台审核 展开").click();
    await button("模拟审核通过").click();
    await p.getByText("入驻审核通过", { exact: true }).first().waitFor();
    await p.goBack();
    await route("profile");
    await p.locator('.floating-nav-item[aria-label="工作台"]').click();
    await route("home");
    const approvedNotice = p.getByRole("dialog", { name: "入驻审核通过" });
    await approvedNotice.waitFor();
    await approvedNotice
      .getByRole("button", { name: "确定", exact: true })
      .click();
    await approvedNotice.waitFor({ state: "hidden" });
    await p.locator('.floating-nav-item[aria-label="我的"]').click();
    await route("profile");
    assert.equal(await p.getByText("员工管理", { exact: true }).count(), 0);
    await p.locator(".mono-feature").filter({ hasText: "账号与权限" }).click();
    await p.getByRole("dialog").waitFor();
    assert.ok(!(await p.getByRole("dialog").innerText()).includes("邀请"));
    await p
      .getByRole("dialog")
      .getByRole("button", { name: "知道了", exact: true })
      .click();
    for (const routeName of ["staff", "join"]) {
      await p.evaluate((name) => {
        uni.navigateTo({ url: "/pages/business/" + name, fail() {} });
      }, routeName);
      await route("home");
      assert.equal(
        await p.locator(".merchant-team-content:visible").count(),
        0,
      );
    }
    await logout();
    await p.locator(".merchant-text-action").click();
    await route("login");
    await field("phone").fill("13800000001");
    await field("password").fill("WrongOnly!2026");
    await button("登录，进入工作台").click();
    await p
      .getByText("账号或密码不正确，请重新输入", { exact: true })
      .waitFor();
    assert.equal(await field("phone").inputValue(), "13800000001");
    assert.equal(await field("password").inputValue(), "");
    await snap("login-error");
    await field("password").fill("TestOnly!2026");
    await button("登录，进入工作台").press("Enter");
    await route("home");
    await p.locator('.floating-nav-item[aria-label="我的"]').click();
    await route("profile");
    assert.equal(await p.getByText("员工管理", { exact: true }).count(), 0);
    await snap("approved-profile");
    await p.setViewportSize({ width: 320, height: 740 });
    await snap("profile-narrow");
    await logout();
    await snap("welcome-narrow");
    await button("申请商家入驻").click();
    await route("onboard");
    await snap("onboard-narrow");
    // Actual reload clears this runtime's isolated service and session.
    await p.goto("http://localhost:9902/#/pages/business/home");
    await route("welcome");
    const persisted = await p.evaluate(() =>
      JSON.stringify({
        local: { ...localStorage },
        session: { ...sessionStorage },
      }),
    );
    assert.ok(!persisted.includes("TestOnly!2026"));
    assert.ok(!persisted.includes("13800000001"));
    assert.deepEqual(writes, []);
    assert.deepEqual(errors, []);
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
        screens: evidence.length,
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
    console.error((await p.locator("body").innerText()).slice(-2500));
    throw error;
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
