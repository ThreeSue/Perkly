<template>
	<div
		class="management-state"
		:class="`is-${kind}`"
		:role="kind === 'error' ? 'alert' : 'status'"
	>
		<el-icon class="state-icon" :size="22" aria-hidden="true">
			<Warning v-if="kind === 'error'" />
			<FolderOpened v-else />
		</el-icon>
		<strong>{{ title }}</strong>
		<p v-if="description">{{ description }}</p>
		<div v-if="$slots.default || kind === 'error'" class="state-actions">
			<slot><el-button @click="$emit('retry')">重试</el-button></slot>
		</div>
	</div>
</template>

<script setup lang="ts">
import { FolderOpened, Warning } from '@element-plus/icons-vue';

withDefaults(defineProps<{ kind?: 'empty' | 'error'; title: string; description?: string }>(), {
	kind: 'empty'
});
defineEmits<{ retry: [] }>();
</script>

<style scoped>
.management-state {
	box-sizing: border-box;
	width: 100%;
	min-height: 164px;
	display: grid;
	justify-items: center;
	align-content: center;
	gap: 8px;
	padding: 20px 16px;
	white-space: normal;
	line-height: 1.5;
	text-align: center;
	color: var(--el-text-color-primary);
}
.state-icon {
	width: 40px;
	height: 40px;
	border-radius: 8px;
	background: var(--workspace-neutral);
	color: var(--el-text-color-regular);
}
.is-error .state-icon {
	background: var(--el-color-danger-light-9);
	color: var(--el-color-danger);
}
.management-state p {
	max-width: 54ch;
	margin: 0;
	overflow-wrap: anywhere;
	font-size: 13px;
	color: var(--el-text-color-secondary);
}
.state-actions {
	margin-top: 4px;
}
</style>
