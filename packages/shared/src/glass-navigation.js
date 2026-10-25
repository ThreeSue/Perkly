// Pure geometry: shared by H5 and mini program; no DOM, timers or platform API.
// Merchant-only: px/ms, linear moving height and displacement, with a safe cap.
export function merchantLensResponse(velocity, expansion = 0) {
  const speed = Number.isFinite(velocity)
    ? Math.min(1.2, Math.abs(velocity))
    : 0;
  const press = Number.isFinite(expansion)
    ? Math.max(0, Math.min(1.08, expansion))
    : 0;
  return {
    speed,
    scaleX: 1 - 0.07 * speed,
    scaleY: 1.08 + 0.62 * speed + 0.06 * press,
    refraction: 24 + 22 * speed,
  };
}
// Visual speed envelope: rise immediately, fall with a 180ms time constant.
// Keeps the lens from snapping back when the finger only slows slightly.
export function smoothLensSpeed(previous, target, elapsed = 0.016) {
  const prior = Number.isFinite(previous) ? previous : 0;
  const next = Number.isFinite(target) ? target : 0;
  if (Math.abs(next) >= Math.abs(prior)) return next;
  const dt = Number.isFinite(elapsed)
    ? Math.max(0.001, Math.min(0.05, elapsed))
    : 0.016;
  return next + (prior - next) * Math.exp(-dt / 0.18);
}
export function glassGeometry(x, y, bounds, count, velocity = 0) {
  const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
  const width = Math.max(1, bounds.width - 14);
  const localX = clamp(x - bounds.left - 7, 0, width);
  const progress = clamp((localX / width) * count - 0.5, 0, count - 1);
  const speed = clamp(Math.abs(velocity), 0, 2);
  return {
    progress,
    index: Math.round(progress),
    stretch: 1 + (speed / (speed + 0.35)) * 0.22,
    squash: 1 - (speed / (speed + 0.35)) * 0.132,
    tilt: clamp(velocity * 1.1, -2.2, 2.2),
  };
}

// A bounded, under-damped spring. Elapsed time is capped after app suspension.
export function glassSpringFrame(position, velocity, target, elapsed) {
  const dt = Math.max(0.001, Math.min(0.032, elapsed));
  const acceleration = (target - position) * 380 - velocity * 25;
  const nextVelocity = velocity + acceleration * dt;
  const nextPosition = position + nextVelocity * dt;
  const settled =
    Math.abs(nextPosition - target) < 0.001 && Math.abs(nextVelocity) < 0.02;
  const deformation = Math.max(-0.055, Math.min(0.09, -acceleration / 2500));
  return {
    position: settled ? target : nextPosition,
    velocity: settled ? 0 : nextVelocity,
    stretch: settled ? 1 : 1 + deformation,
    squash: settled ? 1 : 1 - deformation * 0.7,
    tilt: settled ? 0 : Math.max(-1.2, Math.min(1.2, nextVelocity * 0.22)),
    settled,
  };
}
