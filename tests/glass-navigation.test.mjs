import test from "node:test";
import assert from "node:assert/strict";
import {
  glassGeometry,
  glassSpringFrame,
} from "../packages/shared/src/glass-navigation.js";
import { supportsNavRefraction } from "../packages/shared/h5/nav-refraction.js";

test("iPhone模拟UA不会关闭真实Chromium内核的H5折射", () => {
  const iPhone =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1";
  const oldCheck = /Chrome|Chromium|Edg\//.test(iPhone);
  assert.equal(oldCheck, false, "复现旧UA判断误判iPhone模拟模式");
  assert.equal(
    supportsNavRefraction({
      userAgent: iPhone,
      chromiumRuntime: true,
      backdropFilter: true,
    }),
    true,
  );
  assert.equal(
    supportsNavRefraction({
      userAgent: iPhone,
      brands: [{ brand: "Chromium" }],
      backdropFilter: true,
    }),
    true,
  );
  assert.equal(
    supportsNavRefraction({
      userAgent: "Chrome/130.0 Mobile Safari/537.36",
      backdropFilter: true,
    }),
    true,
  );
});

test("真实WebKit/Firefox和缺少背板滤镜的环境继续安全回退", () => {
  for (const userAgent of [
    "Version/17.0 Mobile Safari/604.1",
    "CriOS/130 Mobile Safari/604.1",
    "EdgiOS/130 Mobile Safari/604.1",
    "Firefox/130.0",
  ]) {
    assert.equal(
      supportsNavRefraction({ userAgent, backdropFilter: true }),
      false,
    );
  }
  assert.equal(
    supportsNavRefraction({ chromiumRuntime: true, backdropFilter: false }),
    false,
  );
});

test("玻璃滑块按实际容器尺寸定位，三个触点中心映射到三个导航", () => {
  for (const width of [272, 342, 370]) {
    const bounds = { left: 24, top: 740, width, height: 64 };
    for (let index = 0; index < 3; index++) {
      const x = bounds.left + 7 + ((width - 14) * (index + 0.5)) / 3;
      const result = glassGeometry(x, 772, bounds, 3);
      assert.equal(result.index, index);
      assert.ok(Math.abs(result.progress - index) < 0.00001);
    }
  }
});

test("拖出两端不越界，快速拖动形变受限，停止后恢复", () => {
  const bounds = { left: 24, top: 740, width: 342, height: 64 };
  for (const [x, expected] of [
    [-1000, 0],
    [2000, 2],
  ]) {
    const result = glassGeometry(x, -100, bounds, 3, 100);
    assert.equal(result.progress, expected);
    assert.ok(result.stretch <= 1.19);
    assert.ok(result.squash >= 0.887);
    assert.ok(Math.abs(result.tilt) <= 2.2);
  }
  const idle = glassGeometry(190, 772, bounds, 3);
  assert.equal(idle.stretch, 1);
  assert.equal(idle.squash, 1);
  assert.equal(idle.tilt, 0);
});

test("容器尚未测量时计算有界且无 NaN", () => {
  const result = glassGeometry(
    0,
    0,
    { left: 0, top: 0, width: 0, height: 0 },
    3,
  );
  assert.ok(Object.values(result).every(Number.isFinite));
});

test("果冻弹簧带惯性越过目标并回弹，有限帧内精确静止", () => {
  for (const initialVelocity of [0, 3, -3]) {
    let position = 0;
    let velocity = initialVelocity;
    let overshot = false;
    let frame;
    for (let step = 0; step < 100; step++) {
      frame = glassSpringFrame(position, velocity, 1, 0.016);
      position = frame.position;
      velocity = frame.velocity;
      overshot ||= position > 1;
      assert.ok(frame.stretch >= 0.945 && frame.stretch <= 1.09);
      if (frame.settled) break;
    }
    assert.equal(overshot, true);
    assert.equal(frame.settled, true);
    assert.equal(position, 1);
    assert.equal(velocity, 0);
    assert.equal(frame.stretch, 1);
  }
});

test("挂起后大时间步与快速反向切换不会造成弹簧发散", () => {
  let position = 0;
  let velocity = 0;
  for (let step = 0; step < 200; step++) {
    const frame = glassSpringFrame(
      position,
      velocity,
      step < 10 ? 2 : 0,
      step === 10 ? 8 : 0.032,
    );
    position = frame.position;
    velocity = frame.velocity;
    assert.ok(Number.isFinite(position) && Math.abs(position) < 3);
  }
  assert.equal(position, 0);
});

// Execute the production component controller with only platform/lifecycle boundaries replaced.
for (const app of ["client", "merchant"]) {
  test(app + "导航点击从原位置起弹、拖动跟手且合成tap不重复导航", async () => {
    const { readFileSync } = await import("node:fs");
    const { stripTypeScriptTypes } = await import("node:module");
    const { parse } = await import("@vue/compiler-sfc");
    const source = readFileSync(
      new URL(
        `../apps/${app}/components/${app}-floating-nav.uvue`,
        import.meta.url,
      ),
      "utf8",
    );
    const script = stripTypeScriptTypes(
      parse(source).descriptor.scriptSetup.content,
    ).replace(/^import[\s\S]*?;\s*/gm, "");
    const mathUrl = new URL(
      "../packages/shared/src/glass-navigation.js",
      import.meta.url,
    ).href;
    const fixture = `
   import { glassGeometry } from ${JSON.stringify(mathUrl)};
   const ref = value => ({ value });
   export const watchers = [];
   const watch = (sources, callback) => watchers.push({ sources, callback });
   const computed = getter => ({ get value() { return getter(); } });
   const defineProps = () => ({ active: "home" });
   const isDark = ref(false), getCurrentInstance = () => ({ proxy: {} });
   export const hooks = {}, ticks = [], mounts = [];
   const onMounted = fn => hooks.mounted = fn, onUnmounted = fn => hooks.unmounted = fn, onShow = fn => hooks.show = fn, onResize = () => {}, onHide = fn => hooks.hide = fn, nextTick = fn => ticks.push(fn);
   const document = { getElementById: () => ({}) };
   const window = { matchMedia: () => ({ matches: false }), addEventListener() {}, removeEventListener() {} };
   const mountNavRefraction = (host, ready) => { const renderer = { ready, updates: [], update(...values) { this.updates.push(values); }, destroy() { ready(false); } }; mounts.push(renderer); return renderer; };
   export const springCalls = [], navigation = [], timers = [];
   const setTimeout = fn => { timers.push(fn); return timers.length; }, clearTimeout = () => {};
   const useNavGlass = () => ({ progress: ref(0), stretch: ref(1), squash: ref(1), tilt: ref(0), expansion: ref(0), expandTo() {}, align() {}, stopSpring() {}, shapeReset() {}, springTo(index, velocity) { springCalls.push({ index, velocity }); } });
   const selector = { in() { return this; }, select() { return this; }, boundingClientRect(fn) { this.callback = fn; return this; }, exec() { this.callback({ left: 24, top: 700, width: 342, height: 64 }); } };
   const uni = { createSelectorQuery: () => selector, hideTabBar() {}, switchTab: options => navigation.push(options.url) };
  `;
    const url =
      "data:text/javascript;base64," +
      Buffer.from(
        fixture +
          script +
          "\nexport { measure, begin, update, release, tap, progress, opticalReady, expansion, tilt };\n",
      ).toString("base64");
    const controller = await import(url);
    controller.measure();
    controller.begin(195, 732);
    assert.equal(controller.progress.value, 0, "按下新项不能瞬移透镜");
    controller.release();
    assert.equal(controller.springCalls.at(-1).index, 1);
    assert.equal(controller.springCalls.at(-1).velocity, 0);
    controller.tap(1);
    assert.equal(controller.springCalls.length, 1, "释放后的合成tap去重");
    controller.timers.pop()();
    assert.match(
      controller.navigation[0],
      app === "client" ? /index\/template$/ : /business\/records$/,
    );
    controller.begin(86, 732);
    controller.update(300, 732, true);
    assert.ok(controller.progress.value > 1.7);
    controller.release();
    assert.equal(controller.springCalls.at(-1).index, 2);
    assert.ok(controller.springCalls.at(-1).velocity > 0);

    // A page hidden before Vue's next tick must not install an invisible renderer.
    controller.hooks.show();
    controller.hooks.hide();
    controller.ticks.shift()();
    assert.equal(controller.mounts.length, 0);
    controller.hooks.show();
    controller.ticks.shift()();
    controller.mounts[0].ready(true);
    assert.equal(controller.opticalReady.value, true);
    controller.hooks.hide();
    assert.equal(controller.opticalReady.value, false);
    controller.hooks.show();
    controller.ticks.shift()();
    controller.mounts[1].ready(true);
    controller.mounts[0].ready(false);
    assert.equal(
      controller.opticalReady.value,
      true,
      "旧渲染器不能关闭新页面折射",
    );
    controller.expansion.value = 0.8;
    controller.tilt.value = 1.2;
    const watcher = controller.watchers[0];
    assert.ok(watcher.sources.includes(controller.expansion));
    assert.ok(watcher.sources.includes(controller.tilt));
    watcher.callback();
    assert.deepEqual(controller.mounts[1].updates.at(-1).slice(3), [0.8, 1.2]);
    controller.hooks.unmounted();
    assert.equal(controller.opticalReady.value, false);
  });
}

test("拖动中即有可见形变，速度加快增强，反向倾斜反转", () => {
  const bounds = { left: 24, top: 740, width: 342, height: 64 };
  const slow = glassGeometry(180, 770, bounds, 3, 0.15);
  const fast = glassGeometry(180, 770, bounds, 3, 1.2);
  const reverse = glassGeometry(180, 770, bounds, 3, -1.2);
  assert.ok(slow.stretch > 1.06);
  assert.ok(fast.stretch > slow.stretch && fast.squash < slow.squash);
  assert.equal(fast.stretch, reverse.stretch);
  assert.equal(fast.tilt, -reverse.tilt);
});

test("折射场边缘有位移，中心和外部保持中性，不制造透明缺口", async () => {
  const { capsuleRefractionMap } = await import(
    "../packages/shared/src/glass-refraction.js"
  );
  for (const [w, h] of [
    [272, 62],
    [368, 62],
    [118, 48],
    [1, 1],
  ]) {
    const map = capsuleRefractionMap(w, h);
    assert.equal(map.pixels.length, map.width * map.height * 4);
    let nonNeutral = 0;
    for (let i = 0; i < map.pixels.length; i += 4) {
      assert.equal(map.pixels[i + 3], 255);
      if (map.pixels[i] !== 128 || map.pixels[i + 1] !== 128) nonNeutral++;
    }
    assert.equal(map.pixels[0], 128);
    const center =
      ((24 + Math.floor(h / 2)) * map.width + 24 + Math.floor(w / 2)) * 4;
    assert.equal(map.pixels[center], 128);
    assert.equal(map.pixels[center + 1], 128);
    if (w > 1) assert.ok(nonNeutral > 0);
  }
});

test("折射区域跟随实际选中项中心及果冻缩放，不随形变偏移中心", async () => {
  const { refractionLensBounds } = await import(
    "../packages/shared/src/glass-refraction.js"
  );
  for (const width of [270, 326, 368]) {
    for (const progress of [0, 0.6, 1, 2]) {
      const rect = refractionLensBounds(width, 62, progress, 1.18, 0.89);
      assert.ok(
        Math.abs(
          rect.x + rect.width / 2 - (7 + ((width - 14) / 3) * (progress + 0.5)),
        ) < 0.00001,
      );
      assert.equal(rect.y + rect.height / 2, 31);
      assert.ok(rect.scaleX <= 1.25 && rect.scaleY >= 0.8);
    }
  }
});

test("按住透镜纵向超过外壳、横向收窄，松手恢复原几何", async () => {
  const { refractionLensBounds } = await import(
    "../packages/shared/src/glass-refraction.js"
  );
  const idle = refractionLensBounds(326, 62, 1, 1, 1, 0);
  const held = refractionLensBounds(326, 62, 1, 1, 1, 1);
  assert.ok(held.width < idle.width);
  assert.ok(held.y < 0 && held.y + held.height > 62);
  assert.ok(held.y > -12 && held.y + held.height < 74);
  assert.equal(held.x + held.width / 2, idle.x + idle.width / 2);
  const moving = refractionLensBounds(326, 62, 1.6, 1.18, 0.89, 1);
  assert.ok(moving.y < 0 && moving.height > 62);
});
