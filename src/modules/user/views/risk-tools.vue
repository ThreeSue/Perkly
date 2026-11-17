<template>
	<CatalogWorkbench v-if="page" :title="page.label" :description="page.description" class="risk-tools">
		<el-result v-if="!allowed" icon="warning" title="无权访问当前风险管控页面" />
		<div v-else class="risk-tools__content" :class="{ 'risk-tools__content--words': page.key === 'words' }">
			<el-tabs v-if="page.key === 'words'" v-model="wordTab" class="risk-tools__tabs">
				<el-tab-pane label="词库管理" name="library">
					<RiskWordLibrary :manage="manage" />
				</el-tab-pane>
				<el-tab-pane label="检测设置" name="settings" lazy>
					<RiskControls :manage="manage" />
					<details v-if="manage" class="risk-schedule-settings" @toggle="scheduleOpen=($event.target as HTMLDetailsElement).open">
						<summary>高级设置 · 定时巡检</summary>
						<RiskSchedule v-if="scheduleOpen" />
					</details>
				</el-tab-pane>
				<el-tab-pane v-if="manage" label="词库重扫" name="rescan" lazy>
					<RiskRescan :manage="manage" />
				</el-tab-pane>
			</el-tabs>
			<RiskAudit v-else />
		</div>
	</CatalogWorkbench>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { checkPerm } from '/@/modules/base/utils/permission';
import CatalogWorkbench from '/@/modules/workspace/components/catalog-workbench.vue';
import { isPlatform } from '/@/workspace';
import RiskAudit from '../components/risk-audit.vue';
import RiskControls from '../components/risk-controls.vue';
import RiskRescan from '../components/risk-rescan.vue';
import RiskSchedule from '../components/risk-schedule.vue';
import RiskWordLibrary from '../components/risk-word-library.vue';
import type { RiskToolPage } from '../risk-navigation';

const route = useRoute();
const page = computed(() => route.meta.riskTool as RiskToolPage | undefined);
const canView = computed(() => isPlatform && checkPerm('search:risk:view'));
const manage = computed(() => canView.value && checkPerm('search:risk:manage'));
const allowed = canView;
const wordTab = ref<'library' | 'settings' | 'rescan'>('library');
const scheduleOpen = ref(false);
watch(() => [route.query.wordId, route.query.word], () => {
	if (route.query.wordId || route.query.word) wordTab.value = 'library';
});
</script>

<style scoped>
.risk-tools :deep(.catalog-surface) {
	overflow: auto;
}
.risk-tools__content {
	padding: 20px 24px;
}
.risk-schedule-settings { border-top: 1px solid var(--el-border-color-light); padding-block: 14px; }
.risk-schedule-settings summary { cursor: pointer; font-weight: 600; }
.risk-tools__content--words {
	box-sizing: border-box;
	height: 100%;
	padding-block: 8px 0;
}
.risk-tools__tabs {
	display: flex;
	flex-direction: column;
	height: 100%;
}
.risk-tools__tabs :deep(.el-tabs__header) {
	order: -1;
	flex: none;
	margin-bottom: 0;
}
.risk-tools__tabs :deep(.el-tabs__content) {
	flex: 1;
	min-height: 0;
}
.risk-tools__tabs :deep(.el-tab-pane) {
	box-sizing: border-box;
	height: 100%;
	overflow: auto;
	padding: 12px 0 20px;
}
@media (max-width: 700px) {
	.risk-tools__content {
		padding: 12px;
	}
	.risk-tools__content--words {
		padding-inline: 12px;
		padding-block: 8px 0;
	}
}
</style>
