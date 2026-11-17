<template>
	<div class="app-views">
		<router-view v-slot="{ Component }">
			<transition
				:name="app.info.router.transition || 'none'"
				:css="!!app.info.router.transition && app.info.router.transition !== 'none'"
			>
				<keep-alive :key="key" :include="caches">
					<component :is="Component" />
				</keep-alive>
			</transition>
		</router-view>
	</div>
</template>

<script lang="ts" setup>
defineOptions({
	name: 'app-views'
});

import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useBase } from '/$/base';
import { useCool } from '/@/cool';

const { mitt } = useCool();
const { process, app } = useBase();

// 缓存数
const key = ref(1);

// 缓存列表
const caches = computed(() => {
	return process.list
		.filter(e => e.meta?.keepAlive)
		.map(e => {
			return e.path.substring(1, e.path.length).replace(/\//g, '-');
		});
});

// 刷新页面
function refresh() {
	key.value += 1;
}

onMounted(() => {
	mitt.on('view.refresh', refresh);
});

onUnmounted(() => {
	mitt.off('view.refresh');
});
</script>

<style lang="scss" scoped>
.app-views {
	flex: 1;
	overflow: hidden;
	// The shared assistant layout owns the outer gutter for both columns.
	min-width: 0;
	min-height: 0;
	margin: 0;
	width: 100%;
	box-sizing: border-box;
	border-radius: 6px;
	position: relative;

	.slide-enter-active {
		transition: opacity 320ms ease-out, transform 320ms cubic-bezier(0.22, 0.61, 0.36, 1);
	}

	.slide-leave-active {
		// Only the incoming page participates in layout and toolbar discovery.
		display: none;
	}

	.slide-enter-to {
		transform: translate3d(0, 0, 0);
		opacity: 1;
	}

	.slide-enter-from {
		transform: translate3d(-16px, 0, 0);
		opacity: 0;
	}

	@media (prefers-reduced-motion: reduce) {
		.slide-enter-active { transition: none; }
		.slide-enter-from { transform: none; opacity: 1; }
	}
}
</style>
