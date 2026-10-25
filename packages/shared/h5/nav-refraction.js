// H5-only renderer. Import inside an H5 conditional block; no uni APIs.
import {
  capsuleRefractionMap,
  refractionLensBounds,
} from "../src/glass-refraction.js";
import { merchantLensResponse } from "../src/glass-navigation.js";

const NS = "http://www.w3.org/2000/svg";
const OWNER = "data-nav-refraction-owner";
const DISPOSE = Symbol.for("qgj.nav-refraction.dispose");
let sequence = 0;
const renderers = new Set();
// Vite can replace this adapter while page components are still transitioning.
// Retire old SVG definitions before the module sequence starts over.
if (import.meta.hot) {
  import.meta.hot.dispose(() => {
    for (const renderer of renderers) renderer.destroy();
  });
}
// Device emulation can replace the UA without changing the engine. Prefer runtime
// evidence so Chromium emulating an iPhone is not mistaken for real WebKit.
export function supportsNavRefraction({
  userAgent = "",
  brands = [],
  chromiumRuntime = false,
  backdropFilter = false,
} = {}) {
  const chromium =
    chromiumRuntime ||
    brands.some(({ brand }) =>
      /Chromium|Google Chrome|Microsoft Edge/.test(brand),
    ) ||
    (/Chrome|Chromium|Edg\//.test(userAgent) &&
      !/CriOS|EdgiOS/.test(userAgent));
  return chromium && backdropFilter;
}
export function mountNavRefraction(
  host,
  ready,
  { chromaticSpread = 2.4, speedResponsive = false } = {},
) {
  const dispersion = Number.isFinite(chromaticSpread)
    ? Math.max(0, Math.min(12, chromaticSpread))
    : 2.4;
  if (
    !supportsNavRefraction({
      userAgent: navigator.userAgent,
      brands: navigator.userAgentData?.brands,
      chromiumRuntime:
        typeof window.chrome === "object" && window.chrome !== null,
      backdropFilter: CSS.supports("backdrop-filter", 'url("#nav-test")'),
    })
  ) {
    return null;
  }
  const plane = host.querySelector(".floating-nav-refraction");
  if (!plane) return null;
  // HBuilder hot reload can recreate a page without unloading its old adapter.
  // The owned SVG keeps its disposer across module instances, including observers.
  for (const previous of document.querySelectorAll(`svg[${OWNER}]`)) {
    if (previous.getAttribute(OWNER) === host.id) previous[DISPOSE]?.();
  }
  let id;
  do {
    id = "nav-refraction-" + ++sequence;
  } while (document.getElementById(id));
  const svg = document.createElementNS(NS, "svg");
  svg.setAttribute("width", "0");
  svg.setAttribute("height", "0");
  svg.setAttribute("aria-hidden", "true");
  svg.setAttribute(OWNER, host.id);
  svg.style.cssText =
    "position:fixed;pointer-events:none;width:0;height:0;overflow:hidden";
  const defs = document.createElementNS(NS, "defs");
  // Union of the shell and held lens; the aperture can extend above/below the bar.
  const clip = document.createElementNS(NS, "clipPath");
  clip.setAttribute("id", id + "-clip");
  clip.setAttribute("clipPathUnits", "userSpaceOnUse");
  const outerClip = document.createElementNS(NS, "rect");
  const innerClip = document.createElementNS(NS, "rect");
  clip.append(outerClip, innerClip);
  defs.append(clip);
  const filter = document.createElementNS(NS, "filter");
  filter.setAttribute("id", id);
  filter.setAttribute("filterUnits", "userSpaceOnUse");
  filter.setAttribute("primitiveUnits", "userSpaceOnUse");
  filter.setAttribute("color-interpolation-filters", "sRGB");
  function primitive(tag, attributes) {
    const node = document.createElementNS(NS, tag);
    for (const [key, value] of Object.entries(attributes))
      node.setAttribute(key, String(value));
    filter.append(node);
    return node;
  }
  const outerImage = primitive("feImage", {
    result: "outer-map",
    preserveAspectRatio: "none",
  });
  primitive("feDisplacementMap", {
    in: "SourceGraphic",
    in2: "outer-map",
    scale: 32,
    xChannelSelector: "R",
    yChannelSelector: "G",
    result: "shell",
  });
  primitive("feFlood", {
    "flood-color": "rgb(128,128,128)",
    result: "neutral",
  });
  const innerImage = primitive("feImage", {
    result: "lens-map",
    preserveAspectRatio: "none",
  });
  primitive("feBlend", {
    in: "lens-map",
    in2: "neutral",
    mode: "normal",
    result: "selection-map",
  });
  // Split real background channels at the curved rim. The caller chooses the
  // optical dispersion; the neutral centre retains zero spectral displacement.
  const innerWarps = [];
  for (let channel = 0; channel < 3; channel++) {
    innerWarps.push(
      primitive("feDisplacementMap", {
        in: "shell",
        in2: "selection-map",
        scale: 24,
        xChannelSelector: "R",
        yChannelSelector: "G",
        result: "warp-" + channel,
      }),
    );
    const values = new Array(20).fill(0);
    values[channel * 5 + channel] = 1;
    values[18] = 1;
    primitive("feColorMatrix", {
      in: "warp-" + channel,
      type: "matrix",
      values: values.join(" "),
      result: "channel-" + channel,
    });
  }
  primitive("feBlend", {
    in: "channel-0",
    in2: "channel-1",
    mode: "screen",
    result: "red-green",
  });
  primitive("feBlend", { in: "red-green", in2: "channel-2", mode: "screen" });
  defs.append(filter);
  svg.append(defs);
  document.body.append(svg);
  let width = 0,
    height = 0,
    disposed = false,
    loaded = false,
    generation = 0;
  let current = {
    progress: 0,
    stretch: 1,
    squash: 1,
    expansion: 0,
    tilt: 0,
    speed: 0,
  };
  const preference = window.matchMedia(
    "(prefers-reduced-transparency: reduce), (forced-colors: active)",
  );
  function applyPreference() {
    const enabled = loaded && !disposed && !preference.matches;
    plane.style.backdropFilter = enabled ? `url("#${id}")` : "none";
    plane.style.webkitBackdropFilter = enabled ? `url("#${id}")` : "none";
    plane.style.clipPath = enabled ? `url("#${id}-clip")` : "none";
    plane.style.visibility = enabled ? "visible" : "hidden";
    ready(enabled);
  }
  function bitmap(w, h) {
    const map = capsuleRefractionMap(w, h);
    const canvas = document.createElement("canvas");
    canvas.width = map.width;
    canvas.height = map.height;
    const context = canvas.getContext("2d");
    if (!context) throw new Error("Canvas 2D unavailable");
    const pixels = context.createImageData(map.width, map.height);
    pixels.data.set(map.pixels);
    context.putImageData(pixels, 0, 0);
    return { ...map, url: canvas.toDataURL() };
  }
  function position() {
    if (width <= 0 || height <= 0) return;
    const rect = refractionLensBounds(
      width,
      height,
      current.progress,
      current.stretch,
      current.squash,
      current.expansion,
    );
    const response = merchantLensResponse(current.speed, current.expansion);
    if (speedResponsive) {
      const slot = (width - 14) / 3;
      const h = height - 14;
      rect.scaleX = response.scaleX;
      rect.scaleY = response.scaleY;
      rect.width = slot * rect.scaleX;
      rect.height = h * rect.scaleY;
      rect.x = 7 + slot * current.progress + (slot - rect.width) / 2;
      rect.y = 7 + (h - rect.height) / 2;
    }
    innerImage.setAttribute("x", String(24 + rect.x - 24 * rect.scaleX));
    innerImage.setAttribute("y", String(24 + rect.y - 24 * rect.scaleY));
    innerImage.setAttribute("width", String(rect.width + 48 * rect.scaleX));
    innerImage.setAttribute("height", String(rect.height + 48 * rect.scaleY));
    innerClip.setAttribute("x", String(24 + rect.x));
    innerClip.setAttribute("y", String(24 + rect.y));
    innerClip.setAttribute("width", String(rect.width));
    innerClip.setAttribute("height", String(rect.height));
    innerClip.setAttribute("rx", String(((height - 14) * rect.scaleX) / 2));
    innerClip.setAttribute("ry", String(((height - 14) * rect.scaleY) / 2));
    innerClip.setAttribute(
      "transform",
      `rotate(${current.tilt},${24 + rect.x + rect.width / 2},${24 + rect.y + rect.height / 2})`,
    );
    const strength = speedResponsive
      ? response.refraction
      : 24 + Math.abs(rect.scaleX - 1) * 24;
    innerWarps.forEach((warp, channel) =>
      warp.setAttribute("scale", String(strength + (1 - channel) * dispersion)),
    );
  }
  function resize() {
    const w = Math.round(host.clientWidth),
      h = Math.round(host.clientHeight);
    if (w <= 0 || h <= 0 || (w === width && h === height)) return;
    width = w;
    height = h;
    const token = ++generation;
    try {
      const outer = bitmap(width, height);
      const inner = bitmap(
        Math.max(1, (width - 14) / 3),
        Math.max(1, height - 14),
      );
      for (const node of [filter, outerImage]) {
        node.setAttribute("x", "0");
        node.setAttribute("y", "0");
        node.setAttribute("width", String(outer.width));
        node.setAttribute("height", String(outer.height));
      }
      outerClip.setAttribute("x", "24");
      outerClip.setAttribute("y", "24");
      outerClip.setAttribute("width", String(width));
      outerClip.setAttribute("height", String(height));
      outerClip.setAttribute("rx", String(height / 2));
      // Decode before enabling, so a pending map never replaces the readable fallback.
      Promise.all(
        [outer.url, inner.url].map(
          (url) =>
            new Promise((resolve, reject) => {
              const probe = new Image();
              probe.onload = resolve;
              probe.onerror = reject;
              probe.src = url;
            }),
        ),
      )
        .then(() => {
          if (disposed || token !== generation) return;
          outerImage.setAttribute("href", outer.url);
          innerImage.setAttribute("href", inner.url);
          position();
          loaded = true;
          applyPreference();
        })
        .catch(() => {
          if (disposed || token !== generation) return;
          loaded = false;
          applyPreference();
        });
    } catch {
      loaded = false;
      applyPreference();
    }
  }
  const observer = new ResizeObserver(resize);
  observer.observe(host);
  preference.addEventListener("change", applyPreference);
  resize();
  const renderer = {
    update(progress, stretch, squash, expansion = 0, tilt = 0, speed = 0) {
      current = { progress, stretch, squash, expansion, tilt, speed };
      position();
    },
    destroy() {
      if (disposed) return;
      disposed = true;
      generation++;
      observer.disconnect();
      preference.removeEventListener("change", applyPreference);
      applyPreference();
      svg.remove();
      renderers.delete(renderer);
    },
  };
  svg[DISPOSE] = () => renderer.destroy();
  renderers.add(renderer);
  return renderer;
}
