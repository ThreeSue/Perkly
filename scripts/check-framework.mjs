import { readFileSync, existsSync, readdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import assert from "node:assert/strict";
import { parse, compileScript, compileTemplate } from "@vue/compiler-sfc";
const root = fileURLToPath(new URL("../", import.meta.url));
const apps = [
  "client",
  "merchant",
  "platform-admin",
  "merchant-independent",
  "verification",
];
let count = 0;
for (const app of apps) {
  const base = path.join(root, "apps", app);
  const pages = JSON.parse(readFileSync(path.join(base, "pages.json"), "utf8"));
  const manifest = JSON.parse(
    readFileSync(path.join(base, "manifest.json"), "utf8"),
  );
  assert.equal(manifest.appid, "", "DCloud AppID 保持空白");
  assert.equal(manifest["mp-weixin"].appid, "", "微信 AppID 保持空白");
  assert.deepEqual(
    JSON.parse(readFileSync(path.join(base, "platformConfig.json"), "utf8"))
      .targets,
    ["WEB", "MP-WEIXIN"],
  );
  assert.equal(
    pages.pages[0].path,
    app === "client"
      ? "pages/index/home"
      : app === "merchant"
        ? "pages/business/welcome"
        : "pages/business/home",
  );
  for (const page of pages.pages)
    assert.ok(
      existsSync(path.join(base, page.path + ".uvue")),
      app + " 路由文件不存在 " + page.path,
    );
  for (const tab of pages.tabBar.list)
    assert.ok(pages.pages.some((p) => p.path === tab.pagePath));
  assert.ok(
    !readFileSync(path.join(base, "config/proxy.ts"), "utf8").includes(
      "show.cool-admin.com",
    ),
  );
  const folders =
    app === "client"
      ? ["pages/index", "pages/discover", "components"]
      : ["pages/business", "components"];
  for (const folder of folders)
    for (const name of readdirSync(path.join(base, folder))) {
      if (
        !name.endsWith(".uvue") ||
        (app === "client" &&
          folder === "pages/index" &&
          name !== "home.uvue") ||
        (folder === "components" &&
          name !== "business-shell.uvue" &&
          !(app === "client" && name.startsWith("client-")) &&
          !(app === "merchant" && name.startsWith("merchant-")))
      )
        continue;
      const filename = path.join(base, folder, name),
        source = readFileSync(filename, "utf8");
      const { descriptor, errors } = parse(source, { filename });
      assert.equal(errors.length, 0, String(errors));
      const script = compileScript(descriptor, { id: app + name });
      const result = compileTemplate({
        id: app + name,
        source: descriptor.template.content,
        filename,
        compilerOptions: { bindingMetadata: script.bindings },
      });
      assert.equal(result.errors.length, 0, String(result.errors));
      count++;
    }
  if (app !== "client")
    assert.equal(
      readFileSync(path.join(base, "styles/business.scss"), "utf8"),
      readFileSync(
        path.join(root, "packages/shared/styles/business.scss"),
        "utf8",
      ),
      "五端业务主题发生偏移",
    );
}
console.log(
  `五端结构、路由、平台标识、共享样式和 ${count} 个业务 SFC 语法检查通过。`,
);
console.log("此检查不替代 HBuilderX 的 H5 / 微信小程序实际编译。");
