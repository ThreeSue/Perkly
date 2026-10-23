export function capsuleRefractionMap(
  width: number,
  height: number,
  padding?: number,
): {
  width: number;
  height: number;
  pixels: Uint8ClampedArray;
  padding: number;
};
export function refractionLensBounds(
  width: number,
  height: number,
  progress: number,
  stretch: number,
  squash: number,
  expansion?: number,
): {
  x: number;
  y: number;
  width: number;
  height: number;
  scaleX: number;
  scaleY: number;
};
