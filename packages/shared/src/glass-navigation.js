// Pure geometry: shared by H5 and mini program; no DOM, timers or platform API.
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
