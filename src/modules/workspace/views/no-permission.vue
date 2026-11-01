<template>
	<el-empty description="账号已开通，等待上级分配页面权限"
		><el-button type="primary" @click="refresh">刷新授权</el-button></el-empty
	>
</template>
<script setup lang="ts">
function refresh() {
	window.location.reload();
}
</script>
