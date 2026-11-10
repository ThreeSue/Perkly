<template>
	<WorkbenchLayout title="活动详情与参与设置" context="查看通知关联活动与当前参与范围">
		<section class="workbench-panel activity-inbox-content">
			<ActivityParticipation v-if="id > 0" :id="id" />
			<ManagementState
				v-else
				title="请选择一条活动通知"
				description="从站内通知打开具体活动，即可查看规则与参与范围。"
			/>
		</section>
	</WorkbenchLayout>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import WorkbenchLayout from '../components/workbench-layout.vue';
import ManagementState from '../components/management-state.vue';
import ActivityParticipation from '../components/activity-participation.vue';
const route = useRoute();
const id = computed(() => Number(route.query.id));
</script>

<style scoped>
.activity-inbox-content {
	margin-block-start: 16px;
	padding: 18px;
}
</style>
