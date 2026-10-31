<template>
	<div class="account">
		<h2>商家账号</h2>
		<el-descriptions border :column="1">
			<el-descriptions-item label="登录账号">{{ account.username }}</el-descriptions-item>
			<el-descriptions-item label="姓名">{{ account.name }}</el-descriptions-item>
			<el-descriptions-item label="所属商家">{{ account.merchantName }}</el-descriptions-item>
			<el-descriptions-item label="角色">商家管理员</el-descriptions-item>
		</el-descriptions>
		<p>账号开通、停用及商家归属由平台运营维护。</p>
	</div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { merchantRequest } from '/@/merchant-api';
defineOptions({ name: 'my-info' });
const account = ref<any>({});
onMounted(async () => {
	try { account.value = await merchantRequest('workspace/me'); }
	catch (error: any) { ElMessage.error(error.message); }
});
</script>
<style scoped>.account { padding: 28px; background: var(--el-bg-color); height: 100%; } p { margin-top: 20px; color: var(--el-text-color-secondary); }</style>
