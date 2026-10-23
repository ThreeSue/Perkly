// Isolated rendering experiment. No product data, dependencies or business routes.
const scene = document.querySelector("#refracted");
const lens = scene.querySelector(".lens");
const filter = document.querySelector("#lens-warp");
const image = document.querySelector("#displacement-image");
const displacement = document.querySelector("#displacement");
const status = document.querySelector("#status");
const enabled = document.querySelector("#enabled");
const strength = document.querySelector("#strength");
const sample = [
  "REAL DOM · 实时文字",
  "0123456789 / ABCDE",
  "直线经过曲面时弯曲",
  "REFRACTION / 折射",
  "清晰字形 · 无模糊",
  "0123456789 / ABCDE",
  "滚动背景，再观察边缘",
  "REAL DOM · 实时文字",
  "0123456789 / ABCDE",
  "实时 HTML，不是截图",
];
for (const backdrop of document.querySelectorAll(".backdrop")) {
  for (const text of sample) {
    const line = document.createElement("p");
    line.className = "sample-line";
    line.textContent = text;
    backdrop.append(line);
  }
}

// A capsule signed-distance field supplies the curved rim's normal.
// Store X/Y pixel offsets in R/G; B is unused. Neutral gray means no displacement.
// Padding covers all possible samples; otherwise the rim samples transparent pixels.
function rebuildMap() {
  const w = Math.round(lens.offsetWidth);
  const h = Math.round(lens.offsetHeight);
  const pad = 32;
  const width = w + pad * 2;
  const height = h + pad * 2;
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");
  const pixels = ctx.createImageData(width, height);
  const radius = h / 2;
  const segment = (w - h) / 2;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const px = x - pad - w / 2;
      const py = y - pad - h / 2;
      const qx = Math.max(Math.abs(px) - segment, 0);
      const length = Math.hypot(qx, py);
      const depth = radius - length;
      const rim =
        depth >= 0 && depth < 28 ? Math.sin((depth / 28) * Math.PI) * 0.18 : 0;
      const nx = length > 0 ? (Math.sign(px) * qx) / length : 0;
      const ny = length > 0 ? py / length : 0;
      const i = (y * width + x) * 4;
      pixels.data[i] = Math.round(127.5 + nx * rim * 255);
      pixels.data[i + 1] = Math.round(127.5 + ny * rim * 255);
      pixels.data[i + 2] = 128;
      pixels.data[i + 3] = 255;
    }
  }
  ctx.putImageData(pixels, 0, 0);
  for (const node of [filter, image]) {
    node.setAttribute("x", -pad);
    node.setAttribute("y", -pad);
    node.setAttribute("width", width);
    node.setAttribute("height", height);
  }
  image.setAttribute("href", canvas.toDataURL());
  status.textContent = `位移场 ${width} × ${height} · 仅尺寸变化时重建 · 背景实时由浏览器合成`;
}

let dragging = false;
let offsetX = 0;
let offsetY = 0;
const clamp = (v, min, max) => Math.max(min, Math.min(max, v));
function moveTo(x, y) {
  lens.style.left =
    clamp(x, 2, scene.clientWidth - lens.offsetWidth - 2) + "px";
  lens.style.top =
    clamp(y, 2, scene.clientHeight - lens.offsetHeight - 2) + "px";
}
lens.addEventListener("pointerdown", (event) => {
  dragging = true;
  offsetX = event.clientX - lens.getBoundingClientRect().left;
  offsetY = event.clientY - lens.getBoundingClientRect().top;
  lens.setPointerCapture(event.pointerId);
});
lens.addEventListener("pointermove", (event) => {
  if (!dragging) return;
  const rect = scene.getBoundingClientRect();
  moveTo(
    event.clientX - rect.left - offsetX,
    event.clientY - rect.top - offsetY,
  );
});
for (const name of ["pointerup", "pointercancel", "lostpointercapture"]) {
  lens.addEventListener(name, () => {
    dragging = false;
  });
}
lens.addEventListener("keydown", (event) => {
  const moves = {
    ArrowLeft: [-12, 0],
    ArrowRight: [12, 0],
    ArrowUp: [0, -12],
    ArrowDown: [0, 12],
  };
  const delta = moves[event.key];
  if (!delta) return;
  event.preventDefault();
  moveTo(lens.offsetLeft + delta[0], lens.offsetTop + delta[1]);
});
enabled.addEventListener("change", () => {
  lens.dataset.enabled = String(enabled.checked);
});
strength.addEventListener("input", () => {
  document.querySelector("#strength-value").value = strength.value;
  displacement.setAttribute("scale", strength.value);
});
document.querySelector("#reset").addEventListener("click", () => {
  lens.style.left = "10%";
  lens.style.top = "";
});
new ResizeObserver(rebuildMap).observe(lens);
window.addEventListener("resize", () => {
  moveTo(lens.offsetLeft, lens.offsetTop);
});
