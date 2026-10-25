// Perkly phase-one experiment. Platform-independent, time-based spring; no DOM/CSS.
export const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
export function createMotion(width = 360) {
  const m = { width, x: width / 8, vx: 0, stretch: 0, vs: 0, lift: 0, vl: 0,
    target: width / 8, pressed: false, selected: 0, origin: 0, previous: 0, lastInput: 0, velocity: 0 };
  return m;
}
export function nearestTab(m, x) { return clamp(Math.floor(x / (m.width / 4)), 0, 3); }
export function selectTab(m, index) {
  m.selected = clamp(index, 0, 3); m.target = (m.selected + .5) * m.width / 4;
}
export function press(m, x, time) {
  m.origin = m.selected; m.pressed = true; m.previous = x; m.lastInput = time; m.velocity = 0;
  m.target = clamp(x, m.width / 8, m.width * 7 / 8);
}
export function move(m, x, time) {
  if (!m.pressed) return;
  m.velocity = clamp((x - m.previous) / Math.max(8, time - m.lastInput) * 1000, -2200, 2200);
  m.target = clamp(x, m.width / 8 - 10, m.width * 7 / 8 + 10);
  m.previous = x; m.lastInput = time;
}
export function release(m, x, cancel = false) {
  m.pressed = false; m.velocity = 0;
  selectTab(m, cancel ? m.origin : nearestTab(m, x));
}
function spring(x, v, target, dt, stiffness, damping) {
  v += ((target - x) * stiffness - v * damping) * dt;
  return [x + v * dt, v];
}
export function step(m, seconds, reduce = false) {
  if (reduce) { m.x = m.target; m.vx = 0; m.stretch = m.vs = m.lift = m.vl = 0; return false; }
  const total = clamp(seconds, 0, .064), count = Math.max(1, Math.ceil(total / .008)), dt = total / count;
  for (let n = 0; n < count; n++) {
    [m.x, m.vx] = spring(m.x, m.vx, m.target, dt, 440, 30);
    const targetStretch = m.pressed ? Math.min(.3, Math.abs(m.velocity) / 6500 + Math.abs(m.target - m.x) / m.width * .35) : 0;
    [m.stretch, m.vs] = spring(m.stretch, m.vs, targetStretch, dt, 520, 28);
    [m.lift, m.vl] = spring(m.lift, m.vl, m.pressed ? 1 : 0, dt, 430, 26);
  }
  const active = Math.abs(m.x - m.target) > .02 || Math.abs(m.vx) > .04 || Math.abs(m.stretch) > .001 || Math.abs(m.vs) > .003 || Math.abs(m.lift - (m.pressed ? 1 : 0)) > .001 || Math.abs(m.vl) > .003;
  if (!active) { m.x = m.target; m.vx = m.vs = m.vl = 0; m.stretch = 0; m.lift = m.pressed ? 1 : 0; }
  return active;
}
export function geometry(m) {
  return { x: m.x, width: m.width / 4 * .88 * (1 + m.lift * .04 + m.stretch),
    height: 52 * (1 + m.lift * .2 - Math.min(.13, m.stretch * .3)) };
}
