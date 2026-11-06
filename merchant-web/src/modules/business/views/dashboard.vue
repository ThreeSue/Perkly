<template>
	<div class="dashboard" v-loading="busy">
		<header>
			<div>
				<p>券管家 · {{ identityName }}</p>
				<h1>{{ me.storeName || me.merchantName || '工作台' }}</h1>
				<p>欢迎回来，{{ me.name }} · {{ me.roleName }}</p>
			</div>
			<el-button @click="load">刷新</el-button>
		</header>
		<el-alert v-if="error" :title="error" type="error" :closable="false" /><el-alert
			v-if="identity === 'MERCHANT' && !me.defaultStoreId"
			title="未设置默认门店时按账号授权范围查看数据；核销前请选择本次消费门店。"
			:closable="false"
		/>
		<div class="cards">
			<el-card v-for="card in cards" :key="card.kind" shadow="hover" @click="go(card.path)"
				><p>{{ card.label }}</p>
				<strong>{{ card.total ?? '—' }}</strong></el-card
			>
		</div>
		<el-card shadow="never"
			><template #header>已授权功能</template>
			<div class="links">
				<el-button v-for="m in links" :key="m.id" @click="router.push(m.router)">{{
					m.name
				}}</el-button>
			</div></el-card
		>
	</div>
</template>
<script setup lang="ts">
import { onMounted, ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useBase } from '/$/base';
import { workspaceApi } from '/$/workspace/api';
import { merchantRequest } from '/@/merchant-api';
import { identity, identityName } from '/@/workspace';
const { menu } = useBase(),
	router = useRouter();
const me = ref<any>({}),
	busy = ref(false),
	error = ref(''),
	cards = ref<any[]>([]);
const links = computed(
	() =>
		menu.all.filter(
			(m: any) => m.type === 1 && !!m.router && m.perms !== 'dashboard:view'
		) as Array<any>
);
function go(path: string) {
	router.push(`/${identity === 'STORE' ? 'store' : 'merchant'}/${path}`);
}
async function load() {
	busy.value = true;
	error.value = '';
	try {
		me.value = await workspaceApi('me');
		const specs = [
			{ permission: 'store:view', kind: 'store', label: '可查看的门店', path: 'stores' },
			{ permission: 'coupon:view', kind: 'coupon', label: '可查看的优惠券', path: 'coupons' },
			{
				permission: 'purchase:view',
				kind: 'order',
				label: '可查看的购券订单',
				path: 'orders'
			}
		].filter(c => me.value.permissions.includes(c.permission));
		cards.value = await Promise.all(
			specs.map(async c => {
				const p = await merchantRequest(`workspace/${c.kind}/page`, { page: 1, size: 1 });
				return { ...c, total: p.totalRow };
			})
		);
	} catch (e: any) {
		error.value = e.message;
	} finally {
		busy.value = false;
	}
}
onMounted(load);
</script>
<style scoped>
.dashboard {
	padding: 24px;
	height: 100%;
	overflow: auto;
	box-sizing: border-box;
}
header {
	display: flex;
	justify-content: space-between;
	align-items: center;
}
.cards {
	display: flex;
	gap: 20px;
	margin: 24px 0;
	flex-wrap: wrap;
}
.cards .el-card {
	min-width: 200px;
	cursor: pointer;
}
.cards strong {
	font-size: 32px;
}
.links {
	display: flex;
	flex-wrap: wrap;
	gap: 12px;
}
.links .el-button {
	margin: 0;
}
.el-alert {
	margin: 16px 0;
}
</style>
