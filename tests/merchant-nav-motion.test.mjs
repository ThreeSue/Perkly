import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes, createRequire } from "node:module";
import { pathToFileURL } from "node:url";
const source = readFileSync(
  new URL("../apps/merchant/composables/use-nav-glass.ts", import.meta.url),
  "utf8",
);
const requireMerchant = createRequire(
  new URL("../apps/merchant/package.json", import.meta.url),
);
const vue = pathToFileURL(requireMerchant.resolve("vue")).href;
const math = new URL(
  "../packages/shared/src/glass-navigation.js",
  import.meta.url,
).href;
function conditional(text, h5) {
  let enabled = true;
  const stack = [];
  return text
    .split("\n")
    .filter((line) => {
      const start = line.match(/\/\/ #(ifdef|ifndef) H5/);
      if (start) {
        stack.push(enabled);
        enabled = enabled && (start[1] === "ifdef" ? h5 : !h5);
        return false;
      }
      if (/\/\/ #endif/.test(line)) {
        enabled = stack.pop();
        return false;
      }
      return enabled;
    })
    .join("\n");
}
async function controller(h5, reduce = false) {
  const body = stripTypeScriptTypes(conditional(source, h5))
    .replace('"vue"', JSON.stringify(vue))
    .replace(
      '"../../../packages/shared/src/glass-navigation.js"',
      JSON.stringify(math),
    );
  const fixture = `
    let now = 1000, sequence = 0;
    export const jobs = new Map(), delays = [];
    const Date = {now:()=>now};
    const setTimeout = (fn,delay) => {delays.push(delay);const id=++sequence;jobs.set(id,{fn,due:now+delay});return id};
    const clearTimeout = id => jobs.delete(id);
    const window = {matchMedia:()=>({matches:${reduce}}),requestAnimationFrame:fn=>setTimeout(fn,16),cancelAnimationFrame:clearTimeout};
    export function advance(ms=16) {now+=ms;const pending=[...jobs];for(const [id,job] of pending){if(job.due<=now){jobs.delete(id);job.fn()}}}
    export const visible = (value=true)=>ref(value);
    export const flush = ()=>nextTick();
  `;
  return import(
    "data:text/javascript;base64," +
      Buffer.from(fixture + body + "\n// " + Math.random()).toString("base64")
  );
}
for (const h5 of [false, true]) {
  test(
    (h5 ? "H5" : "微信") + "位置和按压共用一条16ms帧循环，交互结束彻底停算",
    async () => {
      const c = await controller(h5),
        nav = c.useNavGlass(0);
      nav.expandTo(1);
      nav.springTo(2, 3);
      assert.equal(c.jobs.size, 1);
      let grew = false,
        travelled = false;
      for (let i = 0; i < 70; i++) {
        c.advance();
        grew ||= nav.expansion.value > 0.9;
        travelled ||= nav.progress.value > 1;
        assert.ok(c.jobs.size <= 1, "不能并行创建位置/按压定时器");
      }
      assert.ok(grew && travelled);
      assert.equal(nav.progress.value, 2);
      assert.equal(nav.expansion.value, 1);
      assert.equal(c.jobs.size, 0);
      nav.expandTo(0);
      nav.expandTo(0);
      nav.springTo(0);
      assert.equal(c.jobs.size, 1);
      for (let i = 0; i < 70; i++) c.advance();
      assert.equal(nav.progress.value, 0);
      assert.equal(nav.expansion.value, 0);
      assert.equal(c.jobs.size, 0);
      assert.ok(c.delays.every((ms) => ms === 16));
    },
  );
  test(
    (h5 ? "H5" : "微信") + "隐藏tab冻结绘制值，重新显示续接共享运动而不瞬移",
    async () => {
      const c = await controller(h5),
        a = c.visible(),
        b = c.visible(false);
      const home = c.useNavGlass(0, a),
        cached = c.useNavGlass(0, b);
      const hidden = cached.progress.value;
      home.springTo(1);
      for (let i = 0; i < 5; i++) c.advance();
      assert.ok(home.progress.value > 0);
      assert.equal(cached.progress.value, hidden);
      a.value = false;
      const frozen = home.progress.value;
      b.value = true;
      cached.align(1);
      assert.ok(cached.progress.value > 0 && cached.progress.value < 1);
      c.advance();
      assert.equal(home.progress.value, frozen);
      assert.ok(cached.progress.value > frozen);
      for (let i = 0; i < 70; i++) c.advance();
      assert.equal(cached.progress.value, 1);
      assert.equal(home.progress.value, frozen);
      assert.equal(c.jobs.size, 0);
    },
  );
}
test("减少动态效果时直接归位，不创建动画帧", async () => {
  const c = await controller(true, true),
    nav = c.useNavGlass(0);
  nav.expandTo(1);
  nav.springTo(2, 5);
  assert.equal(nav.progress.value, 2);
  assert.equal(nav.expansion.value, 0);
  assert.equal(c.jobs.size, 0);
});

test("小程序弹簧一次提交关键帧，不再逐16ms更新逻辑层", async () => {
  const c = await controller(false),
    nav = c.useNavGlass(0),
    rendered = [];
  nav.attachNative((poses, duration) => rendered.push({ poses, duration }));
  await c.flush();
  rendered.length = 0;
  nav.expandTo(0);
  nav.springTo(2, 3);
  await c.flush();
  assert.equal(rendered.length, 1);
  const batch = rendered[0];
  assert.ok(batch.duration > 16 && batch.duration <= 1400);
  assert.ok(
    batch.poses.some((p) => p.progress > 2),
    "保留过冲回弹而非线性平移",
  );
  assert.equal(batch.poses.at(-1).progress, 2);
  assert.equal(c.jobs.size, 1, "仅有收尾通知，无循环帧任务");
  for (let i = 0; i < 8; i++) c.advance();
  assert.equal(rendered.length, 1);
  assert.equal(nav.progress.value, 0, "渲染层播放时不逐帧写响应式位置");
  c.advance(1000);
  assert.equal(nav.progress.value, 2);
  assert.equal(nav.speed.value, 0);
  assert.equal(c.jobs.size, 0);
});

test("原生动画跨Tab和快速打断续接当前位置，隐藏页不保留帧任务", async () => {
  const c = await controller(false),
    home = c.useNavGlass(0),
    first = [],
    second = [];
  const a = (poses, duration) => first.push({ poses, duration });
  const b = (poses, duration) => second.push({ poses, duration });
  home.attachNative(a);
  home.springTo(1);
  await c.flush();
  c.advance(96);
  home.detachNative(a);
  assert.equal(c.jobs.size, 0);
  const cached = c.useNavGlass(1);
  cached.attachNative(b);
  await c.flush();
  const start = second.at(-1).poses[0].progress;
  assert.ok(start > 0 && start < 1);
  home.detachNative(a);
  assert.equal(c.jobs.size, 1, "旧页面迟到隐藏不能取消新页面动画");
  c.advance(32);
  cached.stopSpring();
  const interrupted = cached.progress.value;
  cached.progress.value = 0.4;
  cached.speed.value = 5;
  await c.flush();
  assert.ok(interrupted > start);
  assert.equal(second.at(-1).duration, 0);
  assert.equal(second.at(-1).poses[0].progress, 0.4);
  cached.decaySpeed();
  await c.flush();
  assert.equal(second.at(-1).poses.at(-1).speed, 0);
  cached.springTo(0, -3);
  await c.flush();
  c.advance(1000);
  assert.equal(cached.progress.value, 0);
  assert.equal(c.jobs.size, 0);
});

test("手指停住后速度平滑衰减，大小和折射恢复基础值", async () => {
  const { merchantLensResponse } = await import(
    "../packages/shared/src/glass-navigation.js"
  );
  const base = merchantLensResponse(0),
    slow = merchantLensResponse(0.4),
    fast = merchantLensResponse(-0.8);
  assert.ok(
    Math.abs(fast.scaleY - base.scaleY - 2 * (slow.scaleY - base.scaleY)) <
      1e-10,
  );
  assert.ok(
    Math.abs(
      fast.refraction -
        base.refraction -
        2 * (slow.refraction - base.refraction),
    ) < 1e-10,
  );
  assert.deepEqual(merchantLensResponse(-0.8), merchantLensResponse(0.8));
  assert.deepEqual(merchantLensResponse(NaN), base);
  assert.deepEqual(merchantLensResponse(Infinity), base);
  assert.ok(Math.abs(merchantLensResponse(100).scaleY - 1.824) < 1e-12);
  assert.ok(merchantLensResponse(0.4).scaleY > 1.25);
  const c = await controller(true),
    nav = c.useNavGlass(0);
  nav.speed.value = 5;
  nav.decaySpeed();
  c.advance();
  assert.ok(nav.speed.value > 0 && nav.speed.value < 5);
  for (let i = 0; i < 120; i++) c.advance();
  assert.equal(nav.speed.value, 0);
  assert.equal(c.jobs.size, 0);
});

test("视觉速度上升即时、下降缓慢，避免减速时透镜高度弹跳", async () => {
  const { smoothLensSpeed } = await import(
    "../packages/shared/src/glass-navigation.js"
  );
  assert.equal(smoothLensSpeed(0, 3, 0.016), 3);
  const step = smoothLensSpeed(3, 0.5, 0.016);
  assert.ok(step > 0.5 && step < 3);
  let value = 3;
  for (let i = 0; i < 80; i++) value = smoothLensSpeed(value, 0, 0.016);
  assert.ok(value < 0.01);
  assert.equal(smoothLensSpeed(NaN, 1, 0.016), 1);
});

test("原生元素不可用时仍可从当前位置回退到响应式动画", async () => {
  const c = await controller(false),
    nav = c.useNavGlass(0);
  nav.attachNative(() => {});
  nav.springTo(1);
  await c.flush();
  c.advance(96);
  nav.useReactive();
  const sampled = nav.progress.value;
  assert.ok(sampled > 0 && sampled < 1);
  for (let i = 0; i < 70; i++) c.advance();
  assert.equal(nav.progress.value, 1);
  assert.equal(nav.speed.value, 0);
  assert.equal(c.jobs.size, 0);
});
