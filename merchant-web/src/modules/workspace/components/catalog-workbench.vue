<template>
	<WorkbenchLayout :title="title" :context="description" class="catalog-workbench">
		<section class="workbench-panel catalog-surface" :aria-label="title">
			<slot />
		</section>
	</WorkbenchLayout>
</template>

<script setup lang="ts">
import WorkbenchLayout from './workbench-layout.vue';
defineProps<{ title: string; description?: string }>();
</script>

<style scoped>
.catalog-workbench {
	display: flex;
	flex-direction: column;
	gap: 16px;
}
.catalog-workbench :deep(.workbench-banner) {
	flex: none;
}
.catalog-surface {
	flex: 1;
	min-height: 280px;
	overflow: hidden;
}
.catalog-surface > :deep(.cl-crud) {
	padding: 14px 16px 8px;
	background: var(--el-bg-color);
}
.catalog-surface > :deep(.cl-crud > .cl-row:not(.cl-row--last)) {
	flex-wrap: wrap;
}
.catalog-surface :deep(.cl-table th.el-table__cell) {
	background: var(--workspace-neutral) !important;
}
.catalog-surface :deep(.catalog-name) {
	font-weight: 650;
	color: var(--el-text-color-primary);
}
@media (max-width: 620px) {
	.catalog-surface > :deep(.cl-crud) {
		padding-inline: 10px;
	}
}
</style>
