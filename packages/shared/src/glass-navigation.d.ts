export type GlassBounds = {
  left: number;
  top: number;
  width: number;
  height: number;
};
export type GlassGeometry = {
  progress: number;
  index: number;
  stretch: number;
  squash: number;
  tilt: number;
};
export function glassGeometry(
  x: number,
  y: number,
  bounds: GlassBounds,
  count: number,
  velocity?: number,
): GlassGeometry;
export function glassSpringFrame(
  position: number,
  velocity: number,
  target: number,
  elapsed: number,
): {
  position: number;
  velocity: number;
  stretch: number;
  squash: number;
  tilt: number;
  settled: boolean;
};
