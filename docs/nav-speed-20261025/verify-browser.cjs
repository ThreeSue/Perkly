// Browser-only transport fixtures; no mock account code is added to the app.
const {
  chromium,
} = require("C:/Users/Eric/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
(async () => {
  const browser = await chromium.launch({ channel: "msedge", headless: true });
  const errors = [],
    requests = [],
    evidence = [];
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  page.setDefaultTimeout(15000);
  page.on("pageerror", (e) => errors.push(e.message));
  await page.route("**/merchant/**", async (route) => {
    const u = new URL(route.request().url());
    if (!/\/merchant\/(auth|staff|redemption|consumption)\//.test(u.pathname))
      return route.continue();
    const endpoint = u.pathname.slice(u.pathname.indexOf("/merchant/"));
    requests.push({ endpoint, method: route.request().method() });
    let data;
    if (endpoint === "/merchant/auth/captcha")
      data = {
        captchaId: "nav-fixture",
        data:
          "data:image/svg+xml;base64," +
          Buffer.from(
            '<svg xmlns="http://www.w3.org/2000/svg" width="120" height="40"><text x="12" y="28">TEST</text></svg>',
          ).toString("base64"),
      };
    else if (endpoint === "/merchant/auth/login")
      data = {
        token: "nav-fixture-only",
        refreshToken: "nav-refresh-only",
        expire: 3600,
        refreshExpire: 7200,
      };
    else if (endpoint === "/merchant/staff/me")
      data = {
        id: 1,
        username: "nav-fixture",
        name: "导航测试",
        merchantId: 1,
        merchantName: "导航测试",
        storeId: 1,
        storeName: "导航测试店",
        role: "MANAGER",
        roleText: "店长",
        canViewStoreRecords: true,
      };
    else if (route.request().method() === "GET" || endpoint.endsWith("/page"))
      data = { list: [], pagination: { page: 1, size: 20, total: 0 } };
    else throw new Error("Unexpected write: " + endpoint);
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ code: 1000, data }),
    });
  });
  try {
    await page.goto(
      (process.env.MERCHANT_H5_URL || "http://127.0.0.1:9902") +
        "/#/pages/business/login",
      { waitUntil: "domcontentloaded" },
    );
    for (const [name, value] of [
      ["username", "nav-fixture"],
      ["password", "NavFixtureOnly!2026"],
      ["verifyCode", "TEST"],
    ])
      await page.locator('input[id^="auth-' + name + '-"]:visible').fill(value);
    await page
      .getByRole("button", { name: "登录，进入工作台", exact: true })
      .click();
    await page.waitForURL("**/#/pages/business/home");
    await page.locator(".floating-nav.has-refraction:visible").waitFor();
    await page.waitForTimeout(600);
    const nav = page.locator(".floating-nav:visible");
    const box = await nav.boundingBox();
    const y = box.y + box.height / 2;
    async function sample(name) {
      const m = await nav.evaluate((el) => {
        const lens = el.querySelector(".floating-nav-lens"),
          rect = lens.getBoundingClientRect(),
          style = getComputedStyle(lens);
        const svg = document.querySelector(
          'svg[data-nav-refraction-owner="' + el.id + '"]',
        );
        return {
          transform: style.transform,
          lens: {
            x: rect.x,
            y: rect.y,
            width: rect.width,
            height: rect.height,
          },
          scales: [
            ...svg.querySelectorAll('feDisplacementMap[result^="warp-"]'),
          ].map((n) => Number(n.getAttribute("scale"))),
          paintedRims: el.querySelectorAll(".floating-nav-prism").length,
          pseudoContent: getComputedStyle(lens, "::before").content,
        };
      });
      assert.equal(m.paintedRims, 0);
      assert.equal(m.pseudoContent, "none");
      evidence.push({ name, ...m });
      await page.screenshot({
        path: path.join(__dirname, name + ".png"),
        clip: { x: 12, y: box.y - 20, width: 366, height: box.height + 40 },
      });
      return m;
    }
    await page.mouse.move(box.x + box.width / 6, y);
    await page.mouse.down();
    await page.waitForTimeout(350);
    const idle = await sample("held-still");
    // 30px in ~200ms, then 45px in a single native mouse event.
    await page.mouse.move(box.x + box.width / 6 + 5, y);
    await page.waitForTimeout(180);
    await page.mouse.move(box.x + box.width / 6 + 35, y);
    await page.waitForTimeout(20);
    const slow = await sample("slow");
    await page.mouse.move(box.x + box.width / 6 + 40, y);
    await page.waitForTimeout(20);
    await page.mouse.move(box.x + box.width / 6 + 130, y);
    await page.waitForTimeout(20);
    const fast = await sample("fast");
    assert.ok(fast.lens.height > idle.lens.height + 2);
    assert.ok(fast.scales[1] > idle.scales[1] + 1);
    await page.waitForTimeout(600);
    const stopped = await sample("stopped");
    assert.ok(stopped.lens.height < fast.lens.height);
    assert.ok(Math.abs(stopped.scales[1] - 24) < 0.1);
    await page.mouse.up();
    await page.waitForTimeout(950);
    const timings = [];
    for (const [label, route] of [
      ["我的", "profile"],
      ["工作台", "home"],
      ["核销记录", "records"],
      ["工作台", "home"],
    ]) {
      const start = Date.now();
      await page
        .locator('.floating-nav:visible [aria-label="' + label + '"]')
        .click();
      await page.waitForURL("**/#/pages/business/" + route);
      timings.push(Date.now() - start);
      await page.waitForTimeout(700);
    }
    await page.setViewportSize({ width: 320, height: 740 });
    await page.waitForTimeout(200);
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth > innerWidth,
      ),
      false,
    );
    assert.deepEqual(errors, []);
    const businessWrites = requests.filter(
      (r) =>
        r.method !== "GET" &&
        r.endpoint !== "/merchant/auth/login" &&
        !r.endpoint.endsWith("/page"),
    );
    assert.deepEqual(businessWrites, []);
    fs.writeFileSync(
      path.join(__dirname, "browser-results.json"),
      JSON.stringify(
        {
          passed: true,
          evidence,
          timings,
          pageErrors: errors,
          businessWrites,
          mockedTransport: true,
        },
        null,
        2,
      ),
    );
    console.log(
      JSON.stringify({
        passed: true,
        evidence: evidence.map((e) => ({
          name: e.name,
          height: e.lens.height,
          strength: e.scales[1],
        })),
        timings,
      }),
    );
  } catch (e) {
    await page.screenshot({ path: path.join(__dirname, "failure.png") });
    console.error("url", page.url());
    console.error((await page.locator("body").innerText()).slice(-1600));
    throw e;
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
