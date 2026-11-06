<template>
	<ManagementShell title="活动详情与参与设置" description="查看通知关联活动与当前参与范围">
		<ActivityParticipation v-if="id > 0" :id="id" />
		<el-empty v-else description="请从活动通知进入" />
	</ManagementShell>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import ManagementShell from '../components/management-shell.vue';
import ActivityParticipation from '../components/activity-participation.vue';
const route = useRoute();
const id = computed(() => Number(route.query.id));
</script>
