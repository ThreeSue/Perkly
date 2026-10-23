import { ref } from "vue";
import { glassSpringFrame } from "../../../packages/shared/src/glass-navigation.js";
// One in-memory motion per app. It survives tab page changes, never persists business data.
const progress = ref(0);
const stretch = ref(1);
const squash = ref(1);
const tilt = ref(0);
const expansion = ref(0);
// #ifdef H5
let expansionTimer: ReturnType<typeof setTimeout> | null = null;
let expansionVelocity = 0;
// #endif
function expandTo(targetSize: number) {
	// #ifdef H5
	if (expansionTimer !== null) clearTimeout(expansionTimer);
	if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
		expansion.value = 0;
		expansionVelocity = 0;
		expansionTimer = null;
		return;
	}
	let last = Date.now();
	const started = last;
	function tickExpansion() {
		const now = Date.now();
		const frame = glassSpringFrame(
			expansion.value,
			expansionVelocity,
			targetSize,
			(now - last) / 1000
		);
		expansion.value = frame.position;
		expansionVelocity = frame.velocity;
		last = now;
		if (frame.settled || now - started > 650) {
			expansion.value = targetSize;
			expansionVelocity = 0;
			expansionTimer = null;
			return;
		}
		expansionTimer = setTimeout(tickExpansion, 16);
	}
	tickExpansion();
	// #endif
}
let initialized = false;
let target = 0;
let velocity = 0;
let frameTimer: ReturnType<typeof setTimeout> | null = null;
let previousAt = 0;
let startedAt = 0;
function stopSpring() {
	if (frameTimer !== null) {
		clearTimeout(frameTimer);
		frameTimer = null;
	}
}
function shapeReset() {
	stretch.value = 1;
	squash.value = 1;
	tilt.value = 0;
}
function align(index: number) {
	if (!initialized || target !== index) {
		stopSpring();
		target = index;
		progress.value = index;
		velocity = 0;
		shapeReset();
		initialized = true;
	}
}
function springTo(index: number, initialVelocity = 0) {
	stopSpring();
	target = index;
	velocity = Math.max(-5, Math.min(5, initialVelocity));
	// #ifdef H5
	if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
		progress.value = index;
		shapeReset();
		return;
	}
	// #endif
	previousAt = Date.now();
	startedAt = previousAt;
	function tick() {
		const now = Date.now();
		const frame = glassSpringFrame(progress.value, velocity, target, (now - previousAt) / 1000);
		progress.value = frame.position;
		velocity = frame.velocity;
		stretch.value = frame.stretch;
		squash.value = frame.squash;
		tilt.value = frame.tilt;
		previousAt = now;
		if (frame.settled || now - startedAt > 900) {
			progress.value = target;
			shapeReset();
			frameTimer = null;
			return;
		}
		let delay = 32;
		// #ifdef H5
		delay = 16;
		// #endif
		frameTimer = setTimeout(tick, delay);
	}
	tick();
}
export function useNavGlass(index: number) {
	align(index);
	return {
		progress,
		stretch,
		squash,
		tilt,
		expansion,
		expandTo,
		align,
		springTo,
		stopSpring,
		shapeReset
	};
}
