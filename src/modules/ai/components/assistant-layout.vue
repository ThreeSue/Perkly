<template>
	<div class="assistant-layout" :class="{ 'with-assistant': open }">
		<div class="assistant-main"><slot /></div>
		<div class="assistant-side" :inert="!open" :aria-hidden="!open">
			<CouponAssistant v-if="visited" :visible="open" :merchant-id="merchantId" :coupon-id="couponId" :selected-ids="selectedIds" @close="emit('close')" @saved="emit('saved')" @edit="(coupon, step) => emit('edit', coupon, step)" />
		</div>
	</div>
</template>

<script setup lang="ts">
import CouponAssistant from './coupon-assistant.vue';
import { ref, watch } from 'vue';
const props = defineProps<{ open: boolean; merchantId?: number; couponId?: number; selectedIds?: number[] }>();
const emit = defineEmits<{ close: []; saved: []; edit: [coupon: Record<string, any>, step: number] }>();
const visited = ref(props.open);
watch(() => props.open, open => { if (open) visited.value = true; });
</script>

<style scoped>
.assistant-layout {
	position: relative;
	display: grid;
	grid-template-columns: minmax(0, 1fr) 0px;
	height: 100%;
	min-height: 0;
	container-type: inline-size;
	--assistant-duration: 300ms;
	--assistant-ease: cubic-bezier(.22, .61, .36, 1);
	transition: grid-template-columns var(--assistant-duration) var(--assistant-ease);
}
.assistant-layout.with-assistant {
	--assistant-duration: 300ms;
	--assistant-ease: cubic-bezier(.22, .61, .36, 1);
	grid-template-columns: minmax(0, 1fr) calc(clamp(244px, 22%, 288px) + 8px);
}
.assistant-main { min-width: 0; min-height: 0; height: 100%; overflow: auto; }
.assistant-side {
	position: relative;
	min-width: 0;
	min-height: 0;
	overflow: clip;
	opacity: 0;
	visibility: hidden;
	transition: opacity var(--assistant-duration) var(--assistant-ease),
		visibility 0s linear var(--assistant-duration);
}
.assistant-side > * {
	position: absolute;
	inset: 0 0 0 auto;
	width: clamp(244px, 22cqw, 288px);
}
.with-assistant .assistant-side { opacity: 1; visibility: visible; transition-delay: 0s; }
@container (max-width: 900px) {
	.assistant-main { grid-column: 1 / -1; }
	.assistant-side {
		position: absolute;
		inset: 0 0 0 auto;
		z-index: 5;
		width: min(288px, 90%);
	}
	.assistant-side > * {
		width: 100%;
		transform: translateX(16px);
		transition: transform var(--assistant-duration) var(--assistant-ease);
	}
	.with-assistant .assistant-side > * { transform: translateX(0); }
}
@media (prefers-reduced-motion: reduce) {
	.assistant-layout, .assistant-side, .assistant-side > * { transition: none; }
}
</style>
