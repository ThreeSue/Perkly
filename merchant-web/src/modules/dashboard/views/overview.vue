<template>
	<el-scrollbar>
		<main class="operations-overview" v-loading="loading">
			<header class="overview-header">
				<div>
					<h2>运营概览</h2>
					<p>查看平台用户、商家、门店与优惠券的当前情况</p>
				</div>
				<div class="refresh">
					<span v-if="data"
						>更新于 {{ dayjs(data.updatedAt).format('MM-DD HH:mm:ss') }}</span
					><el-button :loading="loading" @click="load">刷新数据</el-button>
				</div>
			</header>
			<el-alert
				v-if="error"
				:title="error"
				type="error"
				:closable="false"
				show-icon
				class="error-alert"
			/>
			<template v-if="data">
				<section class="metrics">
					<article v-for="card in cards" :key="card.label" class="metric panel">
						<div class="metric-label">{{ card.label }}</div>
						<strong>{{ number(card.value) }}</strong>
						<p>{{ card.note }}</p>
					</article>
				</section>
				<section class="panel workflow">
					<div class="section-header">
						<div>
							<h3>优惠券运营</h3>
							<p>
								共 {{ number(data.counts.coupons) }} 个券模板，停用
								{{ number(data.counts.disabledCoupons) }} 个
							</p>
						</div>
						<el-button type="primary" plain @click="router.push('/coupon/template')"
							>前往优惠券管理</el-button
						>
					</div>
					<div class="workflow-grid">
						<div v-for="item in workflow" :key="item.label">
							<span>{{ item.label }}</span
							><strong :style="{ color: item.color }">{{
								number(item.value)
							}}</strong>
						</div>
					</div>
				</section>
				<section class="charts">
					<article class="panel">
						<div class="section-header">
							<div>
								<h3>近14天新增趋势</h3>
								<p>按创建日期统计，无新增的日期显示为0</p>
							</div>
						</div>
						<v-chart :option="trendOption" autoresize class="chart" />
					</article>
					<article class="panel">
						<div class="section-header">
							<div>
								<h3>券类型分布</h3>
								<p>统计全部券模板，包含草稿和停用模板</p>
							</div>
						</div>
						<v-chart
							v-if="data.counts.coupons"
							:option="typeOption"
							autoresize
							class="chart"
						/><el-empty v-else description="暂无优惠券模板" />
					</article>
				</section>
				<footer class="source-note">
					数据来自当前业务数据库（含已录入的演示数据）。交易与核销统计将在对应业务接入后展示。
				</footer>
			</template>
			<el-empty
				v-else-if="!loading"
				description="暂未获取到业务数据，请确认后端已更新并重试"
			/>
		</main>
	</el-scrollbar>
</template>

<script setup lang="ts">
import { computed, onActivated, onMounted, ref } from 'vue';
import { useDark } from '@vueuse/core';
import { useCool } from '/@/cool';
import dayjs from 'dayjs';
defineOptions({ name: 'operations-overview' });
interface Overview {
	counts: {
		users: number;
		merchants: number;
		activeMerchants: number;
		stores: number;
		activeStores: number;
		coupons: number;
		publishedCoupons: number;
		pendingCoupons: number;
		draftCoupons: number;
		rejectedCoupons: number;
		approvedUnpublishedCoupons: number;
		disabledCoupons: number;
	};
	couponTypes: { type: string; total: number }[];
	trends: { date: string; users: number; merchants: number; stores: number; coupons: number }[];
	updatedAt: string;
}
const { service, router } = useCool();
const data = ref<Overview>();
const loading = ref(false);
const error = ref('');
const dark = useDark();
const textColor = computed(() => (dark.value ? '#cfd3dc' : '#606266'));
function number(value: number) {
	return Number(value).toLocaleString('zh-CN');
}
async function load() {
	if (loading.value) return;
	loading.value = true;
	error.value = '';
	try {
		data.value = await service.request({
			url: 'admin/dashboard/overview/info',
			method: 'GET'
		});
	} catch (err) {
		data.value = undefined;
		error.value = `看板数据加载失败：${err instanceof Error ? err.message : '请检查后端服务及运营账号权限'}`;
	} finally {
		loading.value = false;
	}
}
onMounted(load);
onActivated(() => {
	if (data.value) void load();
});
const cards = computed(() => {
	const c = data.value?.counts;
	return c
		? [
				{ label: '正式用户', value: c.users, note: '已绑定手机号，排除历史及注销账号' },
				{
					label: '商家总数',
					value: c.merchants,
					note: `其中启用 ${number(c.activeMerchants)} 家`
				},
				{
					label: '门店总数',
					value: c.stores,
					note: `其中营业 ${number(c.activeStores)} 家`
				},
				{
					label: '平台可展示券',
					value: c.publishedCoupons,
					note: '审核通过、上架且商家门店等条件有效'
				}
			]
		: [];
});
const workflow = computed(() => {
	const c = data.value?.counts;
	return c
		? [
				{ label: '草稿', value: c.draftCoupons, color: '#7b8494' },
				{ label: '待审核', value: c.pendingCoupons, color: '#b47a18' },
				{ label: '已驳回', value: c.rejectedCoupons, color: '#c44f42' },
				{ label: '审核通过待上架', value: c.approvedUnpublishedCoupons, color: '#247f73' }
			]
		: [];
});
const trendOption = computed(() => ({
	color: ['#3478b8', '#247f73', '#8056ad', '#c4802c'],
	tooltip: { trigger: 'axis' },
	legend: { bottom: 0, textStyle: { color: textColor.value } },
	grid: { top: 25, left: 40, right: 20, bottom: 65, containLabel: true },
	xAxis: {
		type: 'category',
		boundaryGap: false,
		data: data.value?.trends.map(day => day.date.slice(5)) || [],
		axisLabel: { color: textColor.value }
	},
	yAxis: {
		type: 'value',
		minInterval: 1,
		axisLabel: { color: textColor.value },
		splitLine: { lineStyle: { color: dark.value ? '#353840' : '#eef0f4' } }
	},
	series: (['users', 'merchants', 'stores', 'coupons'] as const).map((key, index) => ({
		name: ['正式用户', '商家', '门店', '券模板'][index],
		type: 'line',
		smooth: false,
		data: data.value?.trends.map(day => day[key]) || []
	}))
}));
const types: Record<string, { name: string; color: string }> = {
	FULL_REDUCTION: { name: '满减券', color: '#c44f42' },
	DISCOUNT: { name: '折扣券', color: '#3478b8' },
	CASH: { name: '代金券', color: '#a66b14' },
	PACKAGE: { name: '套餐券', color: '#8056ad' },
	EXPERIENCE: { name: '体验券', color: '#247f73' },
	EXCHANGE: { name: '兑换券', color: '#b34b7b' }
};
const typeOption = computed(() => ({
	tooltip: { trigger: 'item' },
	legend: { bottom: 0, textStyle: { color: textColor.value } },
	series: [
		{
			type: 'pie',
			radius: ['40%', '65%'],
			center: ['50%', '43%'],
			label: { color: textColor.value },
			data: (data.value?.couponTypes || []).map(item => ({
				name: types[item.type]?.name || '其他类型',
				value: item.total,
				itemStyle: { color: types[item.type]?.color || '#7b8494' }
			}))
		}
	]
}));
</script>

<style scoped>
.operations-overview {
	padding: 20px;
	color: var(--el-text-color-primary);
}
.overview-header,
.section-header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	gap: 16px;
}
h2 {
	margin: 0 0 8px;
	font-size: 24px;
}
h3 {
	margin: 0;
	font-size: 16px;
}
p,
.source-note {
	color: var(--el-text-color-secondary);
	font-size: 13px;
	line-height: 1.6;
	margin: 6px 0 0;
}
.refresh {
	display: flex;
	align-items: center;
	gap: 14px;
	font-size: 12px;
	color: var(--el-text-color-secondary);
}
.metrics {
	display: grid;
	grid-template-columns: repeat(4, minmax(0, 1fr));
	gap: 16px;
	margin-top: 22px;
}
.panel {
	background: var(--el-bg-color);
	border: 1px solid var(--el-border-color-light);
	border-radius: 10px;
	padding: 22px;
	min-width: 0;
}
.metric-label {
	color: var(--el-text-color-regular);
	font-size: 14px;
}
.metric strong {
	display: block;
	font-size: 34px;
	margin: 12px 0;
	font-weight: 600;
}
.workflow {
	margin-top: 16px;
}
.workflow-grid {
	display: grid;
	grid-template-columns: repeat(4, minmax(0, 1fr));
	gap: 12px;
	margin-top: 22px;
}
.workflow-grid > div {
	display: flex;
	justify-content: space-between;
	align-items: center;
	gap: 8px;
	padding: 14px;
	background: var(--el-fill-color-light);
	border-radius: 6px;
	font-size: 14px;
}
.workflow-grid strong {
	font-size: 24px;
}
.charts {
	display: grid;
	grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
	gap: 16px;
	margin-top: 16px;
}
.chart {
	height: 330px;
	width: 100%;
	margin-top: 12px;
}
.source-note {
	margin-top: 16px;
}
.error-alert {
	margin-top: 20px;
}
@media (max-width: 1100px) {
	.metrics,
	.workflow-grid {
		grid-template-columns: repeat(2, minmax(0, 1fr));
	}
	.charts {
		grid-template-columns: 1fr;
	}
}
@media (max-width: 600px) {
	.operations-overview {
		padding: 12px;
	}
	.overview-header,
	.section-header {
		align-items: flex-start;
		flex-direction: column;
	}
	.metrics,
	.workflow-grid {
		grid-template-columns: 1fr;
	}
	.panel {
		padding: 16px;
	}
}
</style>
