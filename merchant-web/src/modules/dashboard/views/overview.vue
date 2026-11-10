<template>
	<WorkbenchLayout
		title="运营概览"
		:context="data ? '平台经营数据 · 更新于 ' + dayjs(data.updatedAt).format('MM-DD HH:mm:ss') : '平台经营数据'"
	>
		<template #actions>
			<el-button @click="$router.push('/my/assistant')">AI 助手</el-button>
			<el-button class="workbench-refresh" :icon="Refresh" :loading="loading" @click="load">刷新数据</el-button>
		</template>
		<template v-if="cards.length" #metrics>
			<div v-for="card in cards" :key="card.label" class="workbench-banner-metric">
				<span>{{ card.label }}</span><strong>{{ number(card.value) }}</strong><small>{{ card.note }}</small>
			</div>
		</template>
		<div v-if="error && data" class="workbench-inline-alert">
			<el-alert :title="error" type="error" :closable="false" show-icon />
		</div>
		<div v-if="data" class="workbench-grid operations-grid">
			<section class="workbench-panel review-queue" aria-labelledby="review-heading">
				<div class="workbench-section-head">
					<div><h2 id="review-heading">优惠券待办</h2><p>券模板共 {{ number(data.counts.coupons) }} 个</p></div>
					<router-link v-if="couponPath" :to="couponPath" class="workbench-link queue-text-link">优惠券管理<el-icon aria-hidden="true"><ArrowRight /></el-icon></router-link>
				</div>
				<component :is="reviewPath ? RouterLink : 'div'" :to="reviewPath" class="workbench-link review-lead">
					<span><strong>待审核</strong><small>商家提交后等待平台处理</small></span>
					<strong class="review-count">{{ number(data.counts.pendingCoupons) }}</strong>
					<span v-if="reviewPath" class="review-action">进入审核<el-icon aria-hidden="true"><ArrowRight /></el-icon></span>
				</component>
				<dl class="queue-list">
					<div v-for="item in workflow" :key="item.label" class="queue-row">
						<dt><i :class="'queue-dot is-' + item.tone" aria-hidden="true" />{{ item.label }}</dt>
						<dd>{{ number(item.value) }}</dd>
					</div>
				</dl>
			</section>
			<div class="workbench-stack operations-analysis">
				<section class="workbench-panel" aria-labelledby="trend-heading">
					<div class="workbench-section-head">
						<div><h2 id="trend-heading">新增趋势</h2><p>近 14 天 · 按创建日期统计</p></div>
						<div class="trend-switch" role="group" aria-label="选择趋势指标">
							<button type="button" :aria-pressed="selectedSeries === 'all'" @click="selectedSeries = 'all'">全部</button>
							<button v-for="item in series" :key="item.key" type="button" :aria-pressed="selectedSeries === item.key" @click="selectedSeries = item.key">
								<i :style="{ background: item.color }" aria-hidden="true" />{{ item.label }}
							</button>
						</div>
					</div>
					<v-chart v-if="data.trends.length" :option="trendOption" autoresize class="workbench-chart" aria-label="近十四天新增趋势图" />
					<ManagementState v-else title="暂无趋势数据" />
				</section>
				<section class="workbench-panel" aria-labelledby="distribution-heading">
					<div class="workbench-section-head">
						<div><h2 id="distribution-heading">券类型分布</h2><p>包含草稿与停用模板</p></div>
					</div>
					<ul v-if="distribution.length" class="distribution-list">
						<li v-for="item in distribution" :key="item.type">
							<span>{{ item.name }}</span>
							<div class="distribution-track" aria-hidden="true"><i :style="{ width: item.percent + '%', background: item.color }" /></div>
							<strong>{{ number(item.total) }}</strong>
						</li>
					</ul>
					<ManagementState v-else title="暂无优惠券模板" />
				</section>
			</div>
		</div>
		<el-skeleton v-else-if="loading" class="overview-skeleton" :rows="7" animated aria-label="正在加载运营数据" />
		<ManagementState v-else-if="error" kind="error" title="运营数据加载失败" :description="error" @retry="load" />
		<ManagementState v-else title="暂无经营数据" />
	</WorkbenchLayout>
</template>

<script setup lang="ts">
import { computed, onActivated, onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { ArrowRight, Refresh } from '@element-plus/icons-vue';
import { useBase } from '/$/base';
import WorkbenchLayout from '/$/workspace/components/workbench-layout.vue';
import ManagementState from '/$/workspace/components/management-state.vue';
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
const { service } = useCool();
const { menu } = useBase();
function canVisit(path: string) {
	return menu.all.some(item => item.type === 1 && (item.router || item.path) === path);
}
const reviewPath = computed(() => canVisit('/approval/coupon') ? '/approval/coupon' : undefined);
const couponPath = computed(() => canVisit('/coupon/template') ? '/coupon/template' : undefined);
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
				{ label: '正式用户', value: c.users, note: '已绑定手机号' },
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
					note: '已上架且可展示'
				}
			]
		: [];
});
const workflow = computed(() => {
	const c = data.value?.counts;
	return c ? [
		{ label: '审核通过待上架', value: c.approvedUnpublishedCoupons, tone: 'success' },
		{ label: '已驳回', value: c.rejectedCoupons, tone: 'danger' },
		{ label: '草稿', value: c.draftCoupons, tone: 'neutral' },
		{ label: '停用模板', value: c.disabledCoupons, tone: 'neutral' }
	] : [];
});
const series = [
	{ key: 'users', label: '用户', color: '#254DC8' },
	{ key: 'merchants', label: '商家', color: '#0F6B68' },
	{ key: 'stores', label: '门店', color: '#7457A5' },
	{ key: 'coupons', label: '券模板', color: '#A84418' }
] as const;
const selectedSeries = ref<(typeof series)[number]['key'] | 'all'>('all');
const trendOption = computed(() => {
	const visible = series.filter(item => selectedSeries.value === 'all' || item.key === selectedSeries.value);
	return {
		color: visible.map(item => item.color),
		tooltip: { trigger: 'axis', confine: true },
		grid: { top: 14, left: 16, right: 18, bottom: 12, containLabel: true },
		xAxis: {
			type: 'category',
			boundaryGap: false,
			data: data.value?.trends.map(day => day.date.slice(5)) || [],
			axisLabel: { color: textColor.value, hideOverlap: true },
			axisTick: { show: false },
			axisLine: { lineStyle: { color: dark.value ? '#464b54' : '#dce1e9' } }
		},
		yAxis: {
			type: 'value',
			minInterval: 1,
			axisLabel: { color: textColor.value },
			splitLine: { lineStyle: { color: dark.value ? '#353840' : '#e8ebf1' } }
		},
		series: visible.map(item => ({
			name: item.label,
			type: 'line',
			symbol: 'circle',
			symbolSize: 6,
			lineStyle: { width: 2 },
			data: data.value?.trends.map(day => day[item.key]) || []
		}))
	};
});
const types: Record<string, { name: string; color: string }> = {
	FULL_REDUCTION: { name: '满减券', color: '#c44f42' },
	DISCOUNT: { name: '折扣券', color: '#3478b8' },
	CASH: { name: '代金券', color: '#a66b14' },
	PACKAGE: { name: '套餐券', color: '#8056ad' },
	EXPERIENCE: { name: '体验券', color: '#247f73' },
	EXCHANGE: { name: '兑换券', color: '#b34b7b' }
};
const distribution = computed(() => (data.value?.couponTypes || [])
	.map(item => ({
		...item,
		name: types[item.type]?.name || '其他类型',
		color: types[item.type]?.color || '#7b8494',
		percent: data.value?.counts.coupons ? (item.total / data.value.counts.coupons) * 100 : 0
	}))
	.sort((a, b) => b.total - a.total)
);
</script>

<style scoped>
.operations-grid { grid-template-columns: minmax(285px, 1fr) minmax(0, 2fr); }
.queue-text-link, .review-action {
	display: flex;
	align-items: center;
	gap: 5px;
	font-size: 13px;
	font-weight: 650;
	color: var(--workspace-accent-text);
	text-decoration: none;
}
.queue-text-link:hover { text-decoration: underline; text-underline-offset: 3px; }
.review-lead {
	display: grid;
	grid-template-columns: minmax(0, 1fr) auto;
	align-items: center;
	gap: 8px 14px;
	margin: 1px 16px 9px;
	padding: 16px;
	border-radius: 7px;
	background: var(--workspace-selected);
	color: var(--workspace-ink);
	text-decoration: none;
}
a.review-lead:hover { background: var(--el-color-primary-light-8); }
.review-lead span > strong { display: block; font-size: 18px; }
.review-lead small { display: block; margin-top: 4px; color: var(--el-text-color-regular); font-size: 12px; }
.review-count { color: var(--el-color-warning); font-size: 36px; line-height: 1; }
.review-action { grid-column: 1 / -1; padding-top: 6px; }
.queue-list { margin: 0 16px; padding-bottom: 5px; }
.queue-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 51px; border-block-start: 1px solid var(--el-border-color-lighter); }
.queue-row dt { display: flex; align-items: center; gap: 8px; color: var(--el-text-color-regular); }
.queue-row dd { margin: 0; font-size: 18px; font-weight: 650; }
.queue-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--el-text-color-secondary); }
.queue-dot.is-success { background: var(--el-color-success); }
.queue-dot.is-danger { background: var(--el-color-danger); }
.trend-switch { display: flex; flex-wrap: wrap; gap: 4px; }
.trend-switch button {
	display: flex;
	align-items: center;
	gap: 5px;
	border: 0;
	border-radius: 5px;
	padding: 5px 8px;
	background: var(--el-fill-color-light);
	color: var(--el-text-color-regular);
	font: inherit;
	font-size: 12px;
	cursor: pointer;
}
.trend-switch button[aria-pressed="true"] { color: var(--workspace-accent-text); background: var(--workspace-selected); font-weight: 700; }
.trend-switch button:hover { box-shadow: inset 0 0 0 1px var(--el-border-color); }
.trend-switch button:focus-visible { outline: 2px solid var(--workspace-accent-text); outline-offset: 1px; }
.trend-switch i { width: 7px; height: 7px; border-radius: 50%; }
.workbench-chart { height: 236px; margin: 0 8px 8px; }
.distribution-list { margin: 0; padding: 0 18px 14px; list-style: none; }
.distribution-list li { display: grid; grid-template-columns: 52px minmax(0, 1fr) 26px; align-items: center; gap: 10px; min-height: 24px; font-size: 12px; }
.distribution-list li > strong { text-align: right; }
.distribution-track { height: 5px; background: var(--el-fill-color-light); border-radius: 4px; }
.distribution-track i { display: block; height: 100%; border-radius: inherit; }
.overview-skeleton { box-sizing: border-box; padding: 20px; }
@media (max-width: 960px) {
	.operations-grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
