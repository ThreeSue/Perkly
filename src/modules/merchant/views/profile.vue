<template>
	<div class="profile" v-loading="loading">
		<header><div><h1>商家资料</h1><p>查看当前商家的基本资料与联系信息</p></div><el-button @click="load">刷新</el-button></header>
		<el-alert v-if="failure" :title="failure" type="error" :closable="false" show-icon />
		<el-card v-else shadow="never"><el-descriptions :column="1" border><el-descriptions-item label="商家名称">{{ profile.name || '—' }}</el-descriptions-item><el-descriptions-item label="品牌 Logo"><el-image v-if="profile.logo" :src="profile.logo" fit="contain" style="width:80px;height:80px" /><span v-else>未设置</span></el-descriptions-item><el-descriptions-item label="商家简介">{{ profile.introduction || '未填写' }}</el-descriptions-item><el-descriptions-item label="联系人">{{ profile.contactName || '未填写' }}</el-descriptions-item><el-descriptions-item label="联系电话">{{ profile.contactPhone || '未填写' }}</el-descriptions-item><el-descriptions-item label="联系地址">{{ profile.address || '未填写' }}</el-descriptions-item><el-descriptions-item label="商家状态"><el-tag :type="profile.status === 1 ? 'success' : 'info'">{{ profile.status === 1 ? '启用' : '停用' }}</el-tag></el-descriptions-item></el-descriptions><p class="hint">商家主体资料变更请联系平台运营。</p></el-card>
	</div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { merchantRequest } from '/@/merchant-api';
defineOptions({ name: 'merchant-profile' });
const profile = ref<any>({}), loading = ref(false), failure = ref('');
async function load() {
	loading.value = true; failure.value = '';
	try { profile.value = await merchantRequest('workspace/profile'); }
	catch (error: any) { failure.value = error.message || '加载失败，请重试'; }
	finally { loading.value = false; }
}
onMounted(load);
</script>
<style scoped>.profile { padding:24px; box-sizing:border-box; height:100%; overflow:auto; } header { display:flex; justify-content:space-between; align-items:center; margin-bottom:24px; } h1 { margin:0 0 12px; font-size:24px; } p { color:var(--el-text-color-secondary); } .hint { margin-top:20px; }</style>
