const { chromium } = require(
  process.env.PERKLY_THEME_PLAYWRIGHT || "playwright",
);
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

(async () => {
  const browser = await chromium.launch({ channel: "msedge", headless: true });
  const evidence = [];
  try {
    for (const target of [
      {
        app: "client",
        port: 9900,
        routes: [
          ["home", "/pages/index/home", ".home-page"],
          ["login", "/pages/user/login", ".auth-content"],
        ],
      },
      {
        app: "merchant",
        port: 9902,
        routes: [
          ["home", "/pages/business/home", ".merchant-page"],
          ["profile", "/pages/business/profile", ".merchant-page"],
        ],
      },
    ]) {
      const context = await browser.newContext({
        viewport: { width: 390, height: 844 },
        colorScheme: "dark",
        reducedMotion: "reduce",
      });
      const page = await context.newPage();
      const errors = [];
      page.on("pageerror", (error) => errors.push(error.message));
      for (const [name, route, selector] of target.routes) {
        await page.emulateMedia({ colorScheme: "dark" });
        await page.goto(`http://localhost:${target.port}/#${route}`, {
          waitUntil: "domcontentloaded",
        });
        await page
          .locator(selector)
          .waitFor({ state: "visible", timeout: 90000 });
        await page.waitForFunction(() => {
          const scheme = getComputedStyle(
            document.documentElement,
          ).colorScheme.split(" ");
          return (
            scheme.includes("light") &&
            scheme.includes("only") &&
            !scheme.includes("dark")
          );
        });
        await page.waitForTimeout(300);
        const sample = () =>
          page.evaluate((selector) => {
            const node = document.querySelector(selector);
            const nav = document.querySelector(".floating-nav");
            return {
              darkSystem: matchMedia("(prefers-color-scheme: dark)").matches,
              darkClass: document.documentElement.classList.contains("dark"),
              darkComponents: document.querySelectorAll(".is-dark").length,
              colorScheme: getComputedStyle(document.documentElement)
                .colorScheme,
              background: getComputedStyle(node).backgroundColor,
              color: getComputedStyle(node).color,
              navBackground: nav ? getComputedStyle(nav).backgroundColor : null,
              themeButtons: document.querySelectorAll(".theme-set").length,
            };
          }, selector);
        const dark = await sample();
        assert.equal(dark.darkSystem, true);
        assert.equal(dark.darkClass, false);
        assert.equal(dark.darkComponents, 0);
        assert.equal(dark.themeButtons, 0);
        await page.evaluate(() =>
          window.postMessage({ type: "theme-change", isDark: true }, "*"),
        );
        await page.waitForTimeout(100);
        assert.deepEqual(
          await sample(),
          dark,
          "parent message cannot activate the old theme path",
        );
        await page.screenshot({
          path: path.join(__dirname, `${target.app}-${name}-dark-device.png`),
          fullPage: true,
        });
        await page.emulateMedia({ colorScheme: "light" });
        await page.waitForTimeout(100);
        const light = await sample();
        assert.equal(light.darkSystem, false);
        assert.deepEqual(
          { ...light, darkSystem: true },
          dark,
          "system change preserves rendered colors",
        );
        await page.emulateMedia({ colorScheme: "dark" });
        await page.waitForTimeout(100);
        assert.deepEqual(await sample(), dark);
        evidence.push({
          app: target.app,
          route,
          dark,
          light,
          parentMessageIgnored: true,
        });
      }
      assert.deepEqual(errors, [], "no application JavaScript errors");
      await context.close();
    }
    fs.writeFileSync(
      path.join(__dirname, "browser-results.json"),
      JSON.stringify(evidence, null, 2),
    );
    console.log(
      JSON.stringify({
        passed: evidence.length,
        scenarios:
          "dark startup, live light/dark change, legacy parent message",
        output: __dirname,
      }),
    );
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
