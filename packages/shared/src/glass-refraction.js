// Platform-free displacement fields. R/G encode X/Y offsets; no blur or lighting.
export function capsuleRefractionMap(width, height, padding = 24) {
  const w = Math.max(1, Math.round(width));
  const h = Math.max(1, Math.round(height));
  const pad = Math.max(0, Math.round(padding));
  const mapWidth = w + pad * 2;
  const mapHeight = h + pad * 2;
  const pixels = new Uint8ClampedArray(mapWidth * mapHeight * 4);
  const radius = Math.min(w, h) / 2;
  const segmentX = Math.max(0, (w - h) / 2);
  const segmentY = Math.max(0, (h - w) / 2);
  const band = Math.min(18, radius * 0.8);
  for (let y = 0; y < mapHeight; y++) {
    for (let x = 0; x < mapWidth; x++) {
      const px = x - pad - (w - 1) / 2;
      const py = y - pad - (h - 1) / 2;
      const qx = Math.max(0, Math.abs(px) - segmentX);
      const qy = Math.max(0, Math.abs(py) - segmentY);
      const length = Math.hypot(qx, qy);
      const depth = radius - length;
      const rim =
        depth >= 0 && depth < band
          ? Math.sin((depth / band) * Math.PI) * 0.18
          : 0;
      const i = (y * mapWidth + x) * 4;
      pixels[i] = Math.round(
        127.5 + (length > 0 ? (Math.sign(px) * qx) / length : 0) * rim * 255,
      );
      pixels[i + 1] = Math.round(
        127.5 + (length > 0 ? (Math.sign(py) * qy) / length : 0) * rim * 255,
      );
      pixels[i + 2] = 128;
      pixels[i + 3] = 255;
    }
  }
  return { width: mapWidth, height: mapHeight, pixels, padding: pad };
}

export function refractionLensBounds(
  width,
  height,
  progress,
  stretch,
  squash,
  expansion = 0,
) {
  const slot = Math.max(1, (width - 14) / 3);
  const h = Math.max(1, height - 14);
  const amount = Math.max(0, Math.min(1.08, expansion));
  const sx = Math.max(0.8, Math.min(1.25, stretch)) * (1 - 0.12 * amount);
  const sy = Math.max(0.8, Math.min(1.15, squash)) + 0.58 * amount;
  return {
    x: 7 + slot * progress + (slot * (1 - sx)) / 2,
    y: 7 + (h * (1 - sy)) / 2,
    width: slot * sx,
    height: h * sy,
    scaleX: sx,
    scaleY: sy,
  };
}
