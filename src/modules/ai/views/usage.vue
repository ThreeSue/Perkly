<template>
	<ManagementShell
		class="usage-page"
		title="AI 使用统计"
		description="查看各项 AI 功能的使用者、调用次数和模型用量。按北京时间统计上线后的新记录。"
	>
		<el-result v-if="!allowed" icon="warning" title="无权查看 AI 使用统计" />
		<div v-else class="usage-content" v-loading="loading">
			<form class="usage-filters" @submit.prevent="search">
				<el-radio-group v-model="days" aria-label="统计范围" @change="search">
					<el-radio-button :value="7">最近 7 天</el-radio-button>
					<el-radio-button :value="30">最近 30 天</el-radio-button>
				</el-radio-group>
				<el-select v-model="feature" clearable placeholder="全部功能" aria-label="AI 功能" @change="search">
					<el-option v-for="(label, key) in features" :key="key" :label="label" :value="key" />
				</el-select>
				<el-select v-model="actorType" clearable placeholder="全部使用者类型" aria-label="使用者类型" @change="changeActorType">
					<el-option v-for="(label, key) in actorTypes" :key="key" :label="label" :value="key" />
				</el-select>
				<el-input
					v-model="keyword" clearable :maxlength="100" aria-label="使用者名称或登录账号"
					placeholder="搜索名称或登录账号" @clear="search"
				/>
				<el-button type="primary" native-type="submit">查询</el-button>
				<el-button @click="reset">重置</el-button>
				<el-tag v-if="selectedActor" closable @close="clearActor">
					当前使用者 · {{ selectedActor.label }}
				</el-tag>
			</form>

			<el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
			<template v-else-if="data">
				<dl class="usage-totals">
					<div v-for="metric in metrics" :key="metric.key">
						<dt>{{ metric.label }}</dt>
						<dd>{{ formatMetric(totals, metric.key, imageCalls) }}</dd>
					</div>
				</dl>

				<section class="usage-trend" aria-labelledby="ai-usage-trend">
					<h3 id="ai-usage-trend">每日使用趋势</h3>
					<el-empty v-if="!data.trend.length" :image-size="64" description="该范围内暂无 AI 使用记录" />
					<v-chart v-else class="usage-chart" :option="chart" autoresize />
				</section>

				<section class="usage-details" aria-label="用量分布和使用明细">
					<el-tabs v-model="activeTab">
						<el-tab-pane label="按功能统计" name="features">
							<el-table :data="overview" empty-text="该范围内暂无功能用量">
								<el-table-column label="AI 功能" min-width="155">
									<template #default="{ row }">
										<el-button link type="primary" @click="openFeature(row.groupKey)">
											{{ featureLabel(row.groupKey) }}
										</el-button>
									</template>
								</el-table-column>
								<el-table-column v-for="metric in metrics" :key="metric.key" :label="metric.label" align="right" min-width="105">
									<template #default="{ row }">{{ formatMetric(row, metric.key, row.groupKey === 'image' ? row.calls : 0) }}</template>
								</el-table-column>
								<el-table-column label="调用失败" prop="failures" align="right" min-width="95" />
							</el-table>
						</el-tab-pane>
						<el-tab-pane label="使用明细" name="records">
							<el-table :data="data.list" row-key="id" empty-text="该范围内暂无使用明细">
								<el-table-column label="时间" min-width="160">
									<template #default="{ row }">{{ row.time.replace('T', ' ').slice(0, 19) }}</template>
								</el-table-column>
								<el-table-column label="使用者" min-width="170">
									<template #default="{ row }">
										<el-button v-if="row.actorId" link type="primary" @click="openActor(row)">
											{{ row.actorName }}
										</el-button>
										<span v-else>{{ row.actorName }}</span>
										<small class="usage-meta">{{ actorTypes[row.actorType] || row.actorType }}{{ row.accountName ? ' · ' + row.accountName : '' }}</small>
									</template>
								</el-table-column>
								<el-table-column label="AI 功能 / 模型" min-width="185">
									<template #default="{ row }">
										{{ featureLabel(row.feature) }}
										<small v-if="row.model" class="usage-meta">{{ row.model }}</small>
									</template>
								</el-table-column>
								<el-table-column label="事件" min-width="105">
									<template #default="{ row }">
										<el-tag v-if="row.event === 'FAILED'" type="danger" effect="plain">调用失败</el-tag>
										<span v-else>{{ row.event === 'REQUEST' ? '功能受理' : '模型返回' }}</span>
									</template>
								</el-table-column>
								<el-table-column label="输入 Token" align="right" min-width="105">
									<template #default="{ row }">{{ row.feature === 'image' || row.event === 'REQUEST' ? '—' : format(row.inputTokens) }}</template>
								</el-table-column>
								<el-table-column label="输出 Token" align="right" min-width="105">
									<template #default="{ row }">{{ row.feature === 'image' || row.event === 'REQUEST' ? '—' : format(row.outputTokens) }}</template>
								</el-table-column>
								<el-table-column label="生成图数" align="right" min-width="90">
									<template #default="{ row }">{{ row.feature === 'image' && row.event !== 'REQUEST' ? format(row.images) : '—' }}</template>
								</el-table-column>
							</el-table>
							<el-pagination v-if="data.total > pageSize" class="usage-pagination" layout="total, prev, pager, next"
								:total="data.total" :page-size="pageSize" :current-page="page" @current-change="changePage" />
						</el-tab-pane>
					</el-tabs>
					<p class="usage-meta usage-note">
						一次功能受理可能产生多次模型调用。Token 合计来自模型返回的用量。
						<span v-if="totals.missingUsage">本范围有 {{ format(totals.missingUsage) }} 次文本调用缺少完整用量。</span>
					</p>
				</section>
			</template>
		</div>
	</ManagementShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import type { EChartsOption } from 'echarts';
import ManagementShell from '/$/workspace/components/management-shell.vue';
import { featureApi } from '/$/workspace/feature-api';
import { useMenuStore } from '/$/base/store/menu';
import { isPlatform } from '/@/workspace';

const features: Record<string, string> = {
	user: '用户端助手',
	merchant: '核销端助手',
	admin: '管理端 AI 助手',
	support: '门店 AI 自动接待',
	supportDraft: '门店 AI 回复建议',
	image: 'AI 生图',
	profile: '用户画像分析',
	riskPreprocess: '风险 AI 预处理',
	riskScan: '风险巡检'
};

const actorTypes: Record<string, string> = {
	APP: '普通用户', WORKSPACE: '商家或门店账号', PLATFORM: '运营账号', STORE: '门店自动接待', SYSTEM: '系统任务'
};

type Counts = { requests: number; calls: number; failures: number; inputTokens: number; outputTokens: number; images: number; missingUsage: number };
type Group = Counts & { groupKey: string };
type Detail = {
	id: string; time: string; feature: string; actorType: string; actorId: string | null; actorName: string;
	accountName?: string; model?: string; event: 'REQUEST' | 'RETURNED' | 'FAILED';
	inputTokens: number | null; outputTokens: number | null; images: number;
};
type Statistics = { trend: Group[]; features: Group[]; list: Detail[]; total: number };

const metrics = [
	{ key: 'requests', label: '功能使用次数' },
	{ key: 'calls', label: '模型调用次数' },
	{ key: 'inputTokens', label: '输入 Token' },
	{ key: 'outputTokens', label: '输出 Token' },
	{ key: 'images', label: '生成图数' }
] as const;
const menu = useMenuStore();
const allowed = computed(() => isPlatform && menu.perms.some(permission =>
	typeof permission === 'string' && permission.split(',').some(value => value.trim() === 'ai:manage')));
const days = ref(7);
const feature = ref('');
const actorType = ref('');
const keyword = ref('');
const selectedActor = ref<{ id: string; label: string }>();
const data = ref<Statistics>();
const loading = ref(false);
const error = ref('');
const activeTab = ref<'features' | 'records'>('features');
const page = ref(1);
const pageSize = 20;
let requestId = 0;

const number = new Intl.NumberFormat('zh-CN');
const format = (value?: number | null) => value == null ? '未返回' : number.format(value);
const featureLabel = (key: string) => features[key] || key;
const overview = computed(() => [...(data.value?.features ?? [])].sort((a, b) =>
	b.calls - a.calls || b.requests - a.requests));
const imageCalls = computed(() => overview.value.find(row => row.groupKey === 'image')?.calls ?? 0);
const totals = computed(() => {
	const counts: Counts = { requests: 0, calls: 0, failures: 0, inputTokens: 0, outputTokens: 0, images: 0, missingUsage: 0 };
	for (const row of overview.value) {
		for (const key of Object.keys(counts) as (keyof Counts)[]) counts[key] += row[key];
	}
	return counts;
});

function formatMetric(row: Counts, key: (typeof metrics)[number]['key'], imageCount: number) {
	if (key === 'inputTokens' || key === 'outputTokens') {
		const textCalls = row.calls - imageCount;
		if (!textCalls) return '—';
		if (row.missingUsage >= textCalls && row[key] === 0) return '未返回';
	}
	return format(row[key]);
}
const chart = computed<EChartsOption>(() => {
	const byDay = new Map(data.value?.trend.map(row => [row.groupKey, row]));
	const dateParts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' });
	const today = new Date(dateParts.format(new Date()) + 'T00:00:00Z').getTime();
	const dates = Array.from({ length: days.value }, (_, index) =>
		new Date(today - (days.value - 1 - index) * 86_400_000).toISOString().slice(0, 10));
	return {
		animation: false,
		color: ['#446de0', '#1b987e'],
		legend: { bottom: 0, textStyle: { color: 'inherit' } },
		grid: { left: 18, top: 18, right: 24, bottom: 39, containLabel: true },
		tooltip: { trigger: 'axis', renderMode: 'richText' },
		xAxis: { type: 'category', data: dates.map(date => date.slice(5)), axisLabel: { hideOverlap: true, color: 'inherit' } },
		yAxis: { type: 'value', minInterval: 1, axisLabel: { color: 'inherit' }, splitLine: { lineStyle: { color: '#7f8da333' } } },
		aria: { enabled: true },
		series: [
			{ name: '功能使用次数', type: 'bar', barMaxWidth: 18, data: dates.map(date => byDay.get(date)?.requests ?? 0) },
			{ name: '模型调用次数', type: 'line', symbolSize: 6, data: dates.map(date => byDay.get(date)?.calls ?? 0) }
		]
	};
});

async function load() {
	const id = ++requestId;
	const params = new URLSearchParams({ days: String(days.value), pageNumber: String(page.value), pageSize: String(pageSize) });
	if (feature.value) params.set('feature', feature.value);
	if (actorType.value) params.set('actorType', actorType.value);
	if (selectedActor.value) params.set('actorId', selectedActor.value.id);
	if (keyword.value.trim()) params.set('actorKeyword', keyword.value.trim());
	loading.value = true;
	error.value = '';
	try {
		const response: Statistics = await featureApi('ai/console/statistics?' + params);
		if (id === requestId) data.value = response;
	} catch {
		if (id === requestId) error.value = 'AI 使用统计读取失败，请重新查询。';
	} finally {
		if (id === requestId) loading.value = false;
	}
}

function search() {
	page.value = 1;
	if (allowed.value) void load();
}

function changeActorType() {
	selectedActor.value = undefined;
	search();
}

function clearActor() {
	selectedActor.value = undefined;
	actorType.value = '';
	search();
}

function reset() {
	days.value = 7;
	feature.value = '';
	actorType.value = '';
	keyword.value = '';
	selectedActor.value = undefined;
	search();
}

function openFeature(key: string) {
	feature.value = key;
	activeTab.value = 'records';
	search();
}

function openActor(row: Detail) {
	if (!row.actorId) return;
	actorType.value = row.actorType;
	selectedActor.value = { id: row.actorId, label: row.accountName || row.actorName };
	keyword.value = '';
	search();
}

function changePage(next: number) {
	page.value = next;
	void load();
}

onMounted(search);
watch(allowed, value => {
	if (value) search();
	else {
		requestId++;
		data.value = undefined;
		loading.value = false;
	}
});
</script>

<style scoped>
.usage-page :deep(.management-body) {
	display: block;
	overflow: auto;
}

.usage-content {
	padding: 20px 24px;
	color: var(--el-text-color-primary);
	font-variant-numeric: tabular-nums;
}

.usage-filters {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	gap: 8px 12px;
	margin-bottom: 18px;
}

.usage-filters :deep(.el-select),
.usage-filters :deep(.el-input) {
	width: min(185px, 100%);
}

.usage-filters :deep(.el-button + .el-button) {
	margin-left: 0;
}

.usage-totals {
	display: grid;
	grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
	gap: 14px;
	margin: 0;
	padding: 14px 0 18px;
	border-block: 1px solid var(--el-border-color-lighter);
}

.usage-totals dt,
.usage-meta {
	color: var(--el-text-color-secondary);
	font-size: 13px;
}

.usage-totals dd {
	margin: 4px 0 0;
	font-size: 26px;
	font-weight: 600;
	overflow-wrap: anywhere;
}

.usage-trend {
	padding-top: 18px;
}

.usage-trend h3 {
	margin: 0;
	font-size: 16px;
}

.usage-chart {
	height: 208px;
	color: var(--el-text-color-regular);
}

.usage-details {
	margin-top: 14px;
}

.usage-meta {
	display: block;
	overflow-wrap: anywhere;
}

.usage-note {
	margin-top: 12px;
	line-height: 1.5;
}

.usage-pagination {
	justify-content: flex-end;
	margin-top: 16px;
}

@media (max-width: 700px) {
	.usage-content {
		padding: 14px 12px;
	}
}
</style>
