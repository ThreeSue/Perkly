<template>
	<div class="dashboard" v-loading="loading">
		<header><div><p class="eyebrow">券管家 · 商家中心</p><h1>{{ account.merchantName || '商家工作台' }}</h1><p>欢迎回来，{{ account.name || '商家管理员' }}</p></div><el-button @click="load">刷新数据</el-button></header>
		<el-alert v-if="failure" :title="failure" type="error" :closable="false" show-icon />
		<div class="cards">
			<button v-for="item in summaries" :key="item.path" class="card" @click="router.push(item.path)"><span>{{ item.label }}</span><strong>{{ item.value ?? '—' }}</strong><small>查看明细 →</small></button>
		</div>
		<el-card shadow="never"><template #header>常用功能</template><div class="shortcuts"><el-button type="primary" @click="router.push('/merchant/stores')">门店管理</el-button><el-button @click="router.push('/merchant/coupons')">优惠券管理</el-button><el-button @click="router.push('/merchant/orders')">购券订单</el-button><el-button @click="router.push('/merchant/profile')">商家资料</el-button></div></el-card>
		<el-card class="notice" shadow="never"><template #header>业务说明</template><p>优惠券发布到平台前需要提交运营审核，审核通过后可上架。</p><p>购券订单采用模拟支付，页面金额不代表实际收款或结算。上方数量为当前商家全部数据的实时总数。</p></el-card>
	</div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { merchantRequest } from '/@/merchant-api';
defineOptions({ name: 'merchant-dashboard' });
const router = useRouter();
const account = ref<any>({}), loading = ref(false), failure = ref('');
const summaries = ref<{ label: string; value: number | null; path: string; kind: string }[]>([
	{ label: '门店总数', value: null, path: '/merchant/stores', kind: 'store' },
	{ label: '优惠券模板', value: null, path: '/merchant/coupons', kind: 'coupon' },
	{ label: '购券订单', value: null, path: '/merchant/orders', kind: 'order' }
]);
async function load() {
	loading.value = true; failure.value = '';
	try {
		const [me, ...pages] = await Promise.all([merchantRequest('workspace/me'), ...summaries.value.map(item => merchantRequest(`workspace/${item.kind}/page`, { page: 1, size: 1 }))]);
		account.value = me;
		pages.forEach((result, index) => { summaries.value[index].value = result.totalRow; });
	} catch (error: any) { failure.value = error.message || '加载失败，请重试'; summaries.value.forEach(item => { item.value = null; }); }
	finally { loading.value = false; }
}
onMounted(load);
</script>
<style scoped>
.dashboard { height:100%; overflow:auto; box-sizing:border-box; padding:24px; } header { display:flex; justify-content:space-between; align-items:center; gap:16px; margin-bottom:24px; } h1 { font-size:28px; margin:8px 0; } p { color:var(--el-text-color-secondary); line-height:1.8; } .eyebrow { font-size:13px; letter-spacing:2px; } .cards { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:20px; margin:24px 0; } .card { cursor:pointer; text-align:left; padding:24px; border:1px solid var(--el-border-color-lighter); border-radius:8px; background:var(--el-bg-color); color:var(--el-text-color-primary); } .card:hover { border-color:var(--el-color-primary); } .card span,.card small { display:block; color:var(--el-text-color-secondary); } .card strong { display:block; font-size:34px; margin:18px 0; } .shortcuts { display:flex; flex-wrap:wrap; gap:12px; } .shortcuts .el-button { margin:0; } .notice { margin-top:20px; } @media(max-width:700px) { .cards { grid-template-columns:1fr; } .dashboard { padding:16px; } h1 { font-size:22px; } }
</style>
