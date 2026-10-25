const {
  chromium,
} = require("C:/Users/Eric/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright");
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");

(async () => {
  const browser = await chromium.launch({ channel: "msedge", headless: true });
  const evidence = [];
  try {
    for (const fallback of [false, true]) {
      const label = fallback ? "fallback" : "h5";
      const context = await browser.newContext({
        viewport: { width: fallback ? 320 : 390, height: 844 },
        colorScheme: "dark",
      });
      if (fallback)
        await context.addInitScript(() => {
          const supports = CSS.supports.bind(CSS);
          CSS.supports = (...args) =>
            args[0] === "backdrop-filter" && String(args[1]).includes("url(")
              ? false
              : supports(...args);
        });
      const page = await context.newPage();
      const errors = [];
      const writes = [];
      page.on("pageerror", (error) => errors.push(error.message));
      page.on("request", (request) => {
        if (["POST", "PUT", "PATCH", "DELETE"].includes(request.method()))
          writes.push(request.url());
      });
      page.setDefaultTimeout(15000);
      const button = (name) => page.getByRole("button", { name, exact: true });
      const field = (name) =>
        page.locator('input[id^="auth-' + name + '-"]:visible');
      const route = async (name) => {
        await page.waitForURL("**/#/pages/business/" + name);
        await page.waitForTimeout(300);
      };
      await page.goto("http://localhost:9902/#/pages/business/welcome");
      await button("申请商家入驻").click();
      await field("merchantName").fill("棱彩材质测试");
      await button("使用示例位置测试").click();
      await button("下一步，设置账号").click();
      await field("phone").fill("13800000041");
      await field("password").fill("PrismTestOnly!2026");
      await field("confirmation").fill("PrismTestOnly!2026");
      await button("提交入驻申请").click();
      await route("home");
      await page.locator('.floating-nav:visible [aria-label="我的"]').click();
      await route("profile");
      await page.getByText("入驻申请与审核记录", { exact: true }).click();
      await route("application");
      await button("开发测试：模拟平台审核 展开").click();
      await button("模拟审核通过").click();
      await page.getByText("入驻审核通过", { exact: true }).first().waitFor();
      await page.goBack();
      await route("profile");
      await page.locator('.floating-nav:visible [aria-label="工作台"]').click();
      await route("home");
      const notice = page.getByRole("dialog", { name: "入驻审核通过" });
      await notice.getByRole("button", { name: "确定", exact: true }).click();
      await notice.waitFor({ state: "hidden" });
      await page.waitForTimeout(900);
      const nav = page.locator(".floating-nav:visible");
      const box = await nav.boundingBox();
      const clip = {
        x: 12,
        y: box.y - 16,
        width: (fallback ? 320 : 390) - 24,
        height: box.height + 32,
      };
      async function snap(state) {
        await page.screenshot({
          path: path.join(__dirname, label + "-" + state + ".png"),
          fullPage: true,
        });
        await page.screenshot({
          path: path.join(__dirname, label + "-" + state + "-nav.png"),
          clip,
        });
        const metrics = await nav.evaluate((element) => {
          const rim = element.querySelector(".floating-nav-prism");
          const lens = element.querySelector(".floating-nav-lens");
          const bounds = lens.getBoundingClientRect();
          const clipping = [];
          for (let node = lens.parentElement; node; node = node.parentElement) {
            const rect = node.getBoundingClientRect();
            if (
              ["hidden", "clip", "auto", "scroll"].includes(
                getComputedStyle(node).overflowY,
              ) &&
              (bounds.top < rect.top - 1 || bounds.bottom > rect.bottom + 1)
            )
              clipping.push(node.className);
          }
          const defs = document.querySelector(
            'svg[data-nav-refraction-owner="' + element.id + '"]',
          );
          return {
            optical: element.classList.contains("has-refraction"),
            rimOpacity: getComputedStyle(rim).opacity,
            rimWidth: rim.getBoundingClientRect().width,
            clipping,
            lens: {
              x: bounds.x,
              y: bounds.y,
              width: bounds.width,
              height: bounds.height,
            },
            channelScales: defs
              ? [
                  ...defs.querySelectorAll(
                    'feDisplacementMap[result^="warp-"]',
                  ),
                ].map((node) => Number(node.getAttribute("scale")))
              : [],
          };
        });
        assert.equal(metrics.optical, !fallback);
        assert.ok(metrics.rimWidth > 60);
        assert.deepEqual(metrics.clipping, []);
        if (!fallback)
          assert.ok(
            Math.abs(
              metrics.channelScales[0] - metrics.channelScales[1] - 7.2,
            ) < 0.001,
          );
        evidence.push({ label, state, ...metrics });
      }
      await snap("idle");
      await page.mouse.move(box.x + box.width / 6, box.y + box.height / 2);
      await page.mouse.down();
      await page.waitForTimeout(250);
      await snap("pressed");
      assert.equal(evidence.at(-1).rimOpacity, "1");
      await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, {
        steps: 10,
      });
      await page.waitForTimeout(80);
      await snap("drag");
      await page.mouse.move(
        box.x + (box.width * 5) / 6,
        box.y + box.height / 2,
        { steps: 8 },
      );
      await page.mouse.up();
      await route("profile");
      await page.waitForTimeout(900);
      assert.equal(
        await page.locator(".floating-nav:visible .floating-nav-prism").count(),
        1,
      );
      await page.emulateMedia({ forcedColors: "active" });
      await page.waitForTimeout(200);
      assert.equal(
        await page
          .locator(".floating-nav:visible .floating-nav-prism")
          .evaluate((node) => getComputedStyle(node).display),
        "none",
      );
      assert.deepEqual(errors, []);
      assert.deepEqual(writes, []);
      await context.close();
    }
    fs.writeFileSync(
      path.join(__dirname, "browser-results.json"),
      JSON.stringify(
        {
          passed: true,
          evidence,
          pageErrors: [],
          writeRequests: [],
          note: "fallback is an H5 material preview with optical support disabled; it is not a WeChat runtime measurement.",
        },
        null,
        2,
      ),
    );
    console.log(JSON.stringify({ passed: true, states: evidence.length }));
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
