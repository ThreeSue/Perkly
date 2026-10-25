import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";

const read = (path) =>
  readFileSync(new URL("../" + path, import.meta.url), "utf8");

// Execute the shipped controller under each target, replacing platform boundaries only.
function forPlatform(source, platform) {
  const flags = new Set(platform === "MP" ? ["MP", "MP-WEIXIN"] : [platform]);
  const active = [true];
  return source
    .split("\n")
    .filter((line) => {
      const directive = line.match(/^\s*\/\/\s*#(ifdef|ifndef|endif)\s*(.*)$/);
      if (!directive) return active.at(-1);
      if (directive[1] === "endif") active.pop();
      else {
        const matches = directive[2]
          .split("||")
          .some((flag) => flags.has(flag.trim()));
        active.push(
          active.at(-1) && (directive[1] === "ifdef" ? matches : !matches),
        );
      }
      return false;
    })
    .join("\n");
}

async function controller(app, platform) {
  const source = stripTypeScriptTypes(
    forPlatform(read(`apps/${app}/.cool/theme/index.ts`), platform),
  ).replace(/^import[^;]+;\s*/gm, "");
  const fixture = `
    const computed = getter => ({ get value() { return getter(); } });
    const nextTick = callback => callback();
    const uniTheme = ${read(`apps/${app}/theme.json`)};
    let routeStyle = null;
    export const setRouteStyle = value => routeStyle = value;
    const router = { route: () => ({ style: routeStyle }) };
    const ctx = { color: {}, tabBar: { list: [] } };
    const isNull = value => value == null;
    export const listeners = [], nativeThemes = [], properties = {}, classes = new Set(['dark']);
    const style = { setProperty(key, value) { properties[key] = value; } };
    const document = { documentElement: { classList: { remove: name => classes.delete(name), toggle(name, on) { if (on) classes.add(name); else classes.delete(name); } }, style }, body: { style }, querySelector: () => null };
    const window = { addEventListener: (...args) => listeners.push(args), parent: { postMessage() {} } };
    const uni = {
      getAppBaseInfo: () => ({ hostTheme: 'dark', appTheme: 'auto' }),
      getSystemInfoSync: () => ({ osTheme: 'dark' }),
      onHostThemeChange: handler => listeners.push(handler),
      onOsThemeChange: handler => listeners.push(handler),
      onAppThemeChange: handler => listeners.push(handler),
      setAppTheme: options => { nativeThemes.push(options.theme); options.success?.(); }
    };
  `;
  return import(
    "data:text/javascript;base64," +
      Buffer.from(fixture + source).toString("base64")
  );
}

for (const app of ["client", "merchant"]) {
  for (const platform of ["MP", "H5", "APP"]) {
    test(`${app}/${platform}: dark device, legacy toggles and auto requests stay light`, async () => {
      const theme = await controller(app, platform);
      theme.initTheme();
      theme.setTheme("dark");
      theme.toggleTheme();
      theme.setIsAuto();
      assert.equal(theme.theme.value, "light");
      assert.equal(theme.isDark.value, false);
      assert.equal(theme.isAuto.value, false);
      assert.deepEqual(
        theme.listeners,
        [],
        "system and parent-window events cannot re-enable dark mode",
      );
      const light = JSON.parse(read(`apps/${app}/theme.json`)).light;
      assert.equal(theme.getConfig("navTextStyle"), light.navTextStyle);
      assert.equal(theme.getConfig("bgContentColor"), light.bgContentColor);
      theme.setRouteStyle({ navigationBarBackgroundColor: "#eeeeee" });
      assert.equal(
        theme.getConfig("navBgColor"),
        "#eeeeee",
        "page-owned light colors remain authoritative",
      );
      if (platform === "H5") {
        assert.equal(theme.classes.has("dark"), false);
        assert.equal(theme.properties["color-scheme"], "only light");
      }
      if (platform === "APP")
        assert.ok(theme.nativeThemes.every((value) => value === "light"));
    });
  }

  test(`${app}: platform shell and Cool pages expose no dark-mode entry`, () => {
    const manifest = JSON.parse(read(`apps/${app}/manifest.json`));
    assert.equal(manifest["mp-weixin"].darkmode, false);
    assert.equal(manifest.web.darkmode, false);
    assert.equal("dark" in JSON.parse(read(`apps/${app}/theme.json`)), false);
    assert.doesNotMatch(
      read(`apps/${app}/pages.json`),
      /"@(?:nav|bg|tab)\w+"/,
      "native colors must not depend on dark-mode variable resolution",
    );
    assert.match(
      read(`apps/${app}/index.html`),
      /name="color-scheme" content="only light"/,
    );
    assert.doesNotMatch(
      read(`apps/${app}/uni_modules/cool-ui/components/cl-page/cl-page.uvue`),
      /<theme>|\.\/theme\.uvue/,
    );
    const settings =
      app === "client" ? "pages/set/index.uvue" : "pages/set/general.uvue";
    assert.doesNotMatch(
      read(`apps/${app}/${settings}`),
      /深色模式|toggleTheme|changeDarkMode/,
    );
  });
}
