const {
  chromium,
} = require("C:/Users/Eric/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

(async () => {
  const browser = await chromium.launch({ channel: "msedge", headless: true });
  const errors = [];
  const writes = [];
  const evidence = [];
  try {
    const page = await browser.newPage({
      viewport: { width: 390, height: 844 },
      colorScheme: "dark",
    });
    page.setDefaultTimeout(10000);
    page.on("pageerror", (error) => errors.push(error.message));
    page.on("request", (request) => {
      if (["POST", "PUT", "PATCH", "DELETE"].includes(request.method()))
        writes.push(request.url());
    });
    const button = (name) => page.getByText(name, { exact: true });
    const loginLink = () => page.locator(".merchant-text-action");
    const welcome = async () => {
      await page.waitForURL((url) =>
        ["#/", "#/pages/business/welcome"].includes(url.hash),
      );
      await button("申请商家入驻").waitFor();
    };
    const login = async () => {
      await page.waitForURL("**/#/pages/business/login");
      await page
        .getByText(
          "登录店长或员工账号，进入获授权的门店。员工账号由商家后台开通。",
          { exact: true },
        )
        .waitFor();
    };
    const snapshot = async (name) => {
      const metrics = await page.evaluate(() => {
        const action = document.querySelector(".merchant-text-action");
        const bounds = action.getBoundingClientRect();
        return {
          width: innerWidth,
          horizontalOverflow: document.documentElement.scrollWidth > innerWidth,
          primaryButtons: [
            ...document.querySelectorAll(".merchant-auth-button"),
          ].filter((element) => element.getBoundingClientRect().height > 0)
            .length,
          secondaryButtons: [
            ...document.querySelectorAll(".merchant-auth-button--secondary"),
          ].filter((element) => element.getBoundingClientRect().height > 0)
            .length,
          loginText: action.innerText.trim(),
          loginTargetHeight: bounds.height,
          background: getComputedStyle(
            document.querySelector(".merchant-auth-button"),
          ).backgroundColor,
        };
      });
      assert.equal(
        metrics.horizontalOverflow,
        false,
        `${name}: horizontal overflow`,
      );
      assert.equal(
        metrics.primaryButtons,
        1,
        `${name}: expected one main application button`,
      );
      assert.equal(
        metrics.secondaryButtons,
        0,
        `${name}: secondary login button remains`,
      );
      assert.equal(
        metrics.loginText,
        "我已有账号，去登录",
        `${name}: login text`,
      );
      assert.ok(
        metrics.loginTargetHeight >= 44,
        `${name}: login target is too small`,
      );
      evidence.push({ name, ...metrics });
      await page.screenshot({
        path: path.join(__dirname, `${name}.png`),
        fullPage: true,
      });
    };

    await page.goto("http://localhost:9902/#/pages/business/welcome");
    await welcome();
    await snapshot("welcome");
    await loginLink().click();
    await login();
    await button("返回上一步").click();
    await welcome();
    await page.setViewportSize({ width: 320, height: 740 });
    await snapshot("welcome-narrow");
    assert.deepEqual(errors, []);
    assert.deepEqual(writes, []);
    const result = {
      passed: true,
      evidence,
      pageErrors: errors,
      writeRequests: writes,
    };
    fs.writeFileSync(
      path.join(__dirname, "browser-results.json"),
      JSON.stringify(result, null, 2),
    );
    console.log(
      JSON.stringify({
        passed: true,
        evidence,
        pageErrors: errors,
        writeRequests: writes,
      }),
    );
  } catch (error) {
    console.error(
      "URL",
      "see page state in browser failure capture if created",
    );
    throw error;
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
