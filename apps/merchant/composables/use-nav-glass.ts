import { computed, nextTick, ref, type Ref } from "vue";
import { glassSpringFrame } from "../../../packages/shared/src/glass-navigation.js";

const SPEED_DECAY_SECONDS = 0.18;
const NATIVE_MAX_MS = 1400;

// 本端共用运动状态跨tab续接；H5用帧循环，小程序优先批量渲染。
const progress = ref(0),
	stretch = ref(1),
	squash = ref(1),
	tilt = ref(0),
	speed = ref(0),
	expansion = ref(0);
let initialized = false;
let target = 0,
	velocity = 0,
	expansionTarget = 0,
	expansionVelocity = 0;
let moving = false,
	resizing = false,
	damping = false;
let startedAt = 0,
	expansionStartedAt = 0,
	previousAt = 0;
let frame: number | null = null;

export type NavPose = {
	progress: number;
	stretch: number;
	squash: number;
	tilt: number;
	expansion: number;
	speed: number;
};
type NativeFrame = NavPose & {
	velocity: number;
	expansionVelocity: number;
	moving: boolean;
	resizing: boolean;
	damping: boolean;
};
type NativeRenderer = (poses: NavPose[], duration: number) => void;
let nativeMode = false;
let nativeOwner: NativeRenderer | null = null;
let nativeQueued = false;
let nativeTimer: ReturnType<typeof setTimeout> | null = null;
let nativePlan: { frames: NativeFrame[]; started: number; duration: number } | null = null;
function pose(): NavPose {
	return {
		progress: progress.value,
		stretch: stretch.value,
		squash: squash.value,
		tilt: tilt.value,
		speed: speed.value,
		expansion: expansion.value
	};
}
function applyPose(value: NavPose) {
	progress.value = value.progress;
	stretch.value = value.stretch;
	squash.value = value.squash;
	tilt.value = value.tilt;
	speed.value = value.speed;
	expansion.value = value.expansion;
}
function clearNativeTimer() {
	if (nativeTimer !== null) clearTimeout(nativeTimer);
	nativeTimer = null;
}
// Sample a running render-layer animation only on interruption/page hand-off.
// There is no recurring logic-layer timer and no per-frame reactive update.
function sampleNative() {
	if (nativePlan === null) return;
	const plan = nativePlan;
	const elapsed = Math.max(0, Math.min(plan.duration, Date.now() - plan.started));
	const cursor = plan.duration === 0 ? 0 : (elapsed / plan.duration) * (plan.frames.length - 1);
	const first = plan.frames[Math.floor(cursor)];
	const last = plan.frames[Math.min(plan.frames.length - 1, Math.ceil(cursor))];
	const mix = cursor - Math.floor(cursor);
	applyPose({
		progress: first.progress + (last.progress - first.progress) * mix,
		stretch: first.stretch + (last.stretch - first.stretch) * mix,
		squash: first.squash + (last.squash - first.squash) * mix,
		tilt: first.tilt + (last.tilt - first.tilt) * mix,
		speed: first.speed + (last.speed - first.speed) * mix,
		expansion: first.expansion + (last.expansion - first.expansion) * mix
	});
	velocity = first.velocity + (last.velocity - first.velocity) * mix;
	expansionVelocity =
		first.expansionVelocity + (last.expansionVelocity - first.expansionVelocity) * mix;
	moving = elapsed < plan.duration && first.moving;
	resizing = elapsed < plan.duration && first.resizing;
	damping = elapsed < plan.duration && first.damping;
	nativePlan = null;
	clearNativeTimer();
}
function renderNative() {
	sampleNative();
	let state: NativeFrame = { ...pose(), velocity, expansionVelocity, moving, resizing, damping };
	const frames: NativeFrame[] = [state];
	const now = Date.now();
	for (
		let elapsed = 16;
		elapsed <= NATIVE_MAX_MS && (state.moving || state.resizing || state.damping);
		elapsed += 16
	) {
		const next = { ...state };
		if (state.damping && !state.moving) {
			next.speed = state.speed * Math.exp(-0.016 / SPEED_DECAY_SECONDS);
			next.tilt = state.tilt * Math.exp(-0.016 / SPEED_DECAY_SECONDS);
			if (Math.abs(next.speed) < 0.01) {
				next.speed = 0;
				next.tilt = 0;
				next.damping = false;
			}
		}
		if (state.moving) {
			const step = glassSpringFrame(state.progress, state.velocity, target, 0.016);
			next.progress = step.position;
			next.velocity = step.velocity;
			next.speed = step.velocity;
			next.stretch = step.stretch;
			next.squash = step.squash;
			next.tilt = step.tilt;
			if (step.settled || now + elapsed - startedAt >= 900) {
				next.progress = target;
				next.velocity = 0;
				next.speed = 0;
				next.moving = false;
				next.stretch = 1;
				next.squash = 1;
				next.tilt = 0;
			}
		}
		if (state.resizing) {
			const step = glassSpringFrame(
				state.expansion,
				state.expansionVelocity,
				expansionTarget,
				0.016
			);
			next.expansion = step.position;
			next.expansionVelocity = step.velocity;
			if (step.settled || now + elapsed - expansionStartedAt >= 650) {
				next.expansion = expansionTarget;
				next.expansionVelocity = 0;
				next.resizing = false;
			}
		}
		frames.push(next);
		state = next;
	}
	const duration = (frames.length - 1) * 16;
	nativePlan = duration > 0 ? { frames, started: now, duration } : null;
	if (nativeOwner === null) return;
	nativeOwner(frames, duration);
	if (duration > 0) {
		const plan = nativePlan;
		nativeTimer = setTimeout(() => {
			if (nativePlan !== plan) return;
			nativeTimer = null;
			applyPose(state);
			velocity = state.velocity;
			expansionVelocity = state.expansionVelocity;
			moving = state.moving;
			resizing = state.resizing;
			damping = state.damping;
			nativePlan = null;
			// WXS/transition may end without keeping the last frame; write it back
			// explicitly so the lens cannot remain stuck between two tabs.
			const owner = nativeOwner;
			if (owner !== null) owner([state], 0);
		}, duration);
	}
}
function queueNative() {
	if (nativeQueued) return;
	nativeQueued = true;
	nextTick(() => {
		nativeQueued = false;
		if (!nativeMode) return;
		renderNative();
	});
}

function cancelFrame() {
	if (frame === null) return;
	// #ifdef H5
	window.cancelAnimationFrame(frame);
	// #endif
	// #ifndef H5
	clearTimeout(frame);
	// #endif
	frame = null;
}
function queueFrame() {
	if (nativeMode) {
		queueNative();
		return;
	}
	if (frame !== null || (!moving && !resizing && !damping)) return;
	// #ifdef H5
	frame = window.requestAnimationFrame(tick);
	// #endif
	// #ifndef H5
	frame = setTimeout(tick, 16);
	// #endif
}
function wake() {
	if (frame === null) previousAt = Date.now();
	queueFrame();
}
function shapeReset() {
	speed.value = 0;
	stretch.value = 1;
	squash.value = 1;
	tilt.value = 0;
}
function tick() {
	frame = null;
	const now = Date.now(),
		elapsed = (now - previousAt) / 1000;
	previousAt = now;
	if (damping && !moving) {
		speed.value *= Math.exp(-Math.min(0.032, elapsed) / SPEED_DECAY_SECONDS);
		tilt.value *= Math.exp(-Math.min(0.032, elapsed) / SPEED_DECAY_SECONDS);
		if (Math.abs(speed.value) < 0.01) {
			speed.value = 0;
			tilt.value = 0;
			damping = false;
		}
	}
	if (moving) {
		const next = glassSpringFrame(progress.value, velocity, target, elapsed);
		progress.value = next.position;
		velocity = next.velocity;
		speed.value = next.velocity;
		stretch.value = next.stretch;
		squash.value = next.squash;
		tilt.value = next.tilt;
		if (next.settled || now - startedAt > 900) {
			progress.value = target;
			velocity = 0;
			moving = false;
			shapeReset();
		}
	}
	if (resizing) {
		const next = glassSpringFrame(expansion.value, expansionVelocity, expansionTarget, elapsed);
		expansion.value = next.position;
		expansionVelocity = next.velocity;
		if (next.settled || now - expansionStartedAt > 650) {
			expansion.value = expansionTarget;
			expansionVelocity = 0;
			resizing = false;
		}
	}
	queueFrame();
}
function expandTo(targetSize: number) {
	if (nativeMode) sampleNative();
	// #ifdef H5
	if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
		expansion.value = 0;
		expansionVelocity = 0;
		resizing = false;
		if (!moving) cancelFrame();
		return;
	}
	// #endif
	if (expansionTarget === targetSize && (resizing || expansion.value === targetSize)) return;
	expansionTarget = targetSize;
	expansionStartedAt = Date.now();
	resizing = true;
	wake();
}
function stopSpring() {
	if (nativeMode) sampleNative();
	moving = false;
	damping = false;
	velocity = 0;
	speed.value = 0;
	if (!resizing) cancelFrame();
	if (nativeMode) queueNative();
}
function align(index: number) {
	if (!initialized || target !== index) {
		stopSpring();
		target = index;
		progress.value = index;
		shapeReset();
		initialized = true;
	} else if (!moving && Math.abs(progress.value - index) > 0.001) {
		// A cached page can mount with the same target while the shared value is
		// between tabs; snap it back instead of leaving the lens stranded.
		progress.value = index;
		shapeReset();
	}
}
function springTo(index: number, initialVelocity = 0) {
	if (nativeMode) sampleNative();
	target = index;
	damping = false;
	velocity = Math.max(-5, Math.min(5, initialVelocity));
	speed.value = velocity;
	// #ifdef H5
	if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
		progress.value = index;
		moving = false;
		shapeReset();
		if (!resizing) cancelFrame();
		return;
	}
	// #endif
	startedAt = Date.now();
	moving = true;
	wake();
}
function decaySpeed() {
	if (nativeMode) sampleNative();
	if (moving || Math.abs(speed.value) < 0.01) return;
	// Keep the damping tail bounded so it always settles within the keyframe cap.
	speed.value = Math.max(-6, Math.min(6, speed.value));
	damping = true;
	wake();
}

// 缓存页面冻结最后一次绘制值，隐藏后不再订阅每一帧的共享状态。
function presented(source: Ref<number>, visible: Ref<boolean>) {
	let last = source.value;
	return computed({
		get() {
			if (visible.value) last = source.value;
			return last;
		},
		set(value: number) {
			damping = false;
			if (nativeMode) {
				// First drag sample cancels the native timeline; following properties
				// in this same touch batch are sent as one direct render-layer style.
				sampleNative();
				moving = false;
				resizing = false;
				expansion.value = expansionTarget;
				queueNative();
			}
			source.value = value;
		}
	});
}
export function useNavGlass(index: number, visible: Ref<boolean> = ref(true)) {
	align(index);
	return {
		progress: presented(progress, visible),
		stretch: presented(stretch, visible),
		squash: presented(squash, visible),
		tilt: presented(tilt, visible),
		speed: presented(speed, visible),
		expansion: presented(expansion, visible),
		expandTo,
		align,
		springTo,
		stopSpring,
		shapeReset,
		decaySpeed,
		attachNative(renderer: NativeRenderer) {
			cancelFrame();
			nativeMode = true;
			nativeOwner = renderer;
			queueNative();
		},
		useReactive() {
			sampleNative();
			nativeOwner = null;
			nativeMode = false;
			queueFrame();
		},
		detachNative(renderer: NativeRenderer) {
			if (nativeOwner !== renderer) return;
			nativeOwner = null;
			clearNativeTimer();
		}
	};
}
