<template>
	<WorkbenchLayout title="等待页面授权" context="账号已开通">
		<section class="workbench-panel no-access-state">
			<ManagementState
				title="暂无可访问的页面"
				description="请联系上级管理员分配页面权限，授权后可在此刷新。"
			>
				<el-button type="primary" @click="refresh">刷新授权</el-button>
			</ManagementState>
		</section>
	</WorkbenchLayout>
</template>

<script setup lang="ts">
import WorkbenchLayout from '../components/workbench-layout.vue';
import ManagementState from '../components/management-state.vue';
function refresh() {
	window.location.reload();
}
</script>

<style scoped>
.no-access-state {
	margin-block-start: 16px;
}
</style>
