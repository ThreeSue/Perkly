<template>
	<ManagementShell
		register
		:title="purchase ? '购券订单' : '消费与核销账单'"
		:description="purchase ? '查询购券记录与模拟支付结果' : '查询到店消费、线下收款与核销记录'"
	>
		<ManagementState v-if="!canPage" title="当前账号没有查询权限" />
		<template v-else>
			<div class="trade-register">
				<el-form v-if="!summaryOnly" inline class="record-filters" @submit.prevent="search">
					<slot name="merchant-filter" :form="filters" :change="merchantChanged" />
					<el-form-item v-if="!purchase && loadStores" label="门店"
						><el-select
							v-model="filters.storeId"
							filterable
							remote
							clearable
							remote-show-suffix
							:remote-method="searchStores"
							:loading="storesLoading"
							placeholder="搜索门店名称"
							@visible-change="open => open && searchStores('')"
							><el-option
								v-for="s in stores"
								:key="s.id"
								:label="s.name"
								:value="s.id" /></el-select
					></el-form-item>
					<el-form-item label="状态"
						><el-select v-model="filters.status" clearable placeholder="全部状态"
							><el-option
								v-for="s in states"
								:key="s.value"
								:label="s.label"
								:value="s.value" /></el-select
					></el-form-item>
					<el-form-item :label="purchase ? '订单号' : '账单号'"
						><el-input
							v-model="filters.orderNo"
							maxlength="40"
							clearable
							placeholder="输入完整单号"
					/></el-form-item>
					<el-form-item label="创建时间"
						><el-date-picker
							v-model="range"
							type="datetimerange"
							value-format="YYYY-MM-DD HH:mm:ss"
							start-placeholder="开始时间"
							end-placeholder="结束时间"
					/></el-form-item>
					<el-form-item
						><el-button type="primary" native-type="submit">搜索</el-button
						><el-button @click="reset">重置</el-button
						><el-button text :loading="busy" @click="load"
							>刷新</el-button
						></el-form-item
					>
				</el-form>
				<div v-else class="management-toolbar">
					<el-button :loading="busy" @click="load">刷新</el-button>
					<span class="filter-label">金额为模拟支付记录，仅供查询。</span>
				</div>
				<ManagementState
					v-if="error"
					kind="error"
					title="记录加载失败"
					:description="error"
					@retry="load"
				/>
				<el-table
					v-else
					v-loading="busy"
					class="management-table"
					:data="rows"
					row-key="id"
					height="100%"
				>
					<el-table-column
						:label="
							summaryOnly
								? '订单编号'
								: purchase
									? '订单与优惠券'
									: '消费账单与优惠券'
						"
						min-width="230"
					>
						<template #default="{ row }">
							<div v-if="!summaryOnly" class="cell-main" :title="couponName(row)">
								{{ couponName(row) }}
							</div>
							<div
								:class="summaryOnly ? 'cell-main' : 'cell-sub'"
								:title="row.orderNo"
							>
								{{ row.orderNo }}
							</div>
						</template>
					</el-table-column>
					<el-table-column
						v-if="!summaryOnly"
						:label="purchase ? '购买人' : '消费门店'"
						min-width="150"
					>
						<template #default="{ row }">
							<div
								class="cell-main"
								:title="purchase ? row.customerName : row.storeName"
							>
								{{ readable(purchase ? row.customerName : row.storeName) }}
							</div>
							<div class="cell-sub" :title="row.merchantName">
								{{ readable(row.merchantName) }}
							</div>
						</template>
					</el-table-column>
					<el-table-column
						:label="purchase ? '模拟购券金额' : '消费金额'"
						width="132"
						align="right"
					>
						<template #default="{ row }">
							<strong class="trade-amount">{{
								money(purchase ? row.totalAmount : row.consumptionAmount, !purchase)
							}}</strong>
						</template>
					</el-table-column>
					<el-table-column label="状态" width="100">
						<template #default="{ row }">
							<el-tag :type="statusTone(row.status)" effect="light">{{
								row.statusText || '未知状态'
							}}</el-tag>
						</template>
					</el-table-column>
					<el-table-column v-if="summaryOnly" label="模拟支付" min-width="115"
						><template #default="{ row }">{{
							paymentStatus(row.paymentStatus)
						}}</template></el-table-column
					>
					<el-table-column label="创建时间" width="172"
						><template #default="{ row }">{{
							dateText(row.createdAt)
						}}</template></el-table-column
					>
					<el-table-column
						v-if="canInfo"
						label="操作"
						width="100"
						fixed="right"
						align="right"
					>
						<template #default="{ row }">
							<el-button link type="primary" @click="open(row)">查看详情</el-button>
						</template>
					</el-table-column>
					<template #empty>
						<ManagementState
							v-if="!busy"
							:title="searched ? '没有匹配的记录' : '暂无记录'"
							:description="
								searched ? '调整筛选条件后再试。' : '有新的业务记录后将在这里显示。'
							"
						>
							<el-button v-if="searched" @click="reset">清除筛选</el-button>
						</ManagementState>
					</template>
				</el-table>
				<div class="management-pagination" v-if="!error && total > 0">
					<el-pagination
						:current-page="page"
						:page-size="size"
						:page-sizes="[10, 20, 50, 100]"
						:total="total"
						layout="total, sizes, prev, pager, next"
						@update:current-page="changePage"
						@update:page-size="changeSize"
					/>
				</div>
			</div>
		</template>
		<el-drawer
			v-model="visible"
			:title="purchase ? '购券订单详情' : '消费与核销详情'"
			size="min(720px, 100vw)"
			append-to-body
			destroy-on-close
			@close="detailRevision++"
		>
			<el-skeleton v-if="detailLoading" :rows="6" animated aria-label="正在加载详情" />
			<ManagementState
				v-else-if="detailError"
				kind="error"
				title="详情加载失败"
				:description="detailError"
				@retry="activeRow && open(activeRow)"
			/>
			<div v-else-if="detail" class="trade-detail">
				<div class="trade-detail-heading">
					<div>
						<span>{{ purchase ? '模拟购券金额' : '消费金额' }}</span>
						<strong>{{
							money(
								purchase ? detail.totalAmount : detail.consumptionAmount,
								!purchase
							)
						}}</strong>
					</div>
					<el-tag :type="statusTone(detail.status)">{{
						detail.statusText || '未知状态'
					}}</el-tag>
				</div>
				<h3>{{ purchase ? '订单资料' : '账单资料' }}</h3>
				<dl class="trade-facts">
					<div
						v-for="field in detailFields"
						:key="field.label"
						:class="{ 'trade-reference': field.reference }"
					>
						<dt>{{ field.label }}</dt>
						<dd>{{ field.value }}</dd>
					</div>
				</dl>
				<template v-if="purchase"
					><h3>购券明细</h3>
					<el-table :data="items(detail)" border
						><el-table-column
							prop="couponName"
							label="券名称"
							show-overflow-tooltip
						/><el-table-column
							prop="quantity"
							label="数量"
							width="80"
						/><el-table-column label="单价"
							><template #default="s">{{
								money(s.row.unitPrice)
							}}</template></el-table-column
						><el-table-column label="小计"
							><template #default="s">{{
								money(s.row.subtotal)
							}}</template></el-table-column
						></el-table
					></template
				>
				<template v-else-if="contentItems(detail).length"
					><h3>发券时的套餐／服务内容</h3>
					<el-table :data="contentItems(detail)" border
						><el-table-column
							prop="name"
							label="项目"
							show-overflow-tooltip /><el-table-column
							prop="quantity"
							label="数量"
							width="80" /></el-table
				></template>
				<h3>状态过程</h3>
				<el-timeline
					><el-timeline-item :timestamp="dateText(detail.createdAt)"
						>{{ purchase ? '创建购券订单' : '开立消费账单'
						}}<span v-if="!purchase">
							· {{ readable(detail.operatorName) }}</span
						></el-timeline-item
					><el-timeline-item
						v-if="purchase && detail.paidAt"
						:timestamp="dateText(detail.paidAt)"
						type="success"
						>模拟支付完成</el-timeline-item
					><el-timeline-item
						v-if="!purchase && detail.completedAt"
						:timestamp="dateText(detail.completedAt)"
						type="success"
						>确认完成并核销 · {{ readable(detail.confirmedByName) }}</el-timeline-item
					><el-timeline-item v-if="!detail.paidAt && !detail.completedAt"
						>当前状态：{{
							detail.statusText
						}}（未提供其他状态变更时间）</el-timeline-item
					></el-timeline
				>
			</div>
			<template #footer><el-button @click="visible = false">关闭</el-button></template>
		</el-drawer>
	</ManagementShell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import ManagementShell from '/$/workspace/components/management-shell.vue';
import ManagementState from '/$/workspace/components/management-state.vue';
import {
	money,
	readable,
	items,
	contentItems,
	couponTypes,
	purchaseStates,
	consumptionStates,
	filterPayload,
	type RecordRow
} from '../record-model';

const props = withDefaults(
	defineProps<{
		purchase?: boolean;
		summaryOnly?: boolean;
		pageApi: (p: RecordRow) => Promise<any>;
		infoApi: (id: any) => Promise<RecordRow>;
		canPage?: boolean;
		canInfo?: boolean;
		loadStores?: (keyword: string, merchantId?: number) => Promise<any[]>;
	}>(),
	{ canPage: true, canInfo: true }
);
const filters = reactive<RecordRow>({});
const range = ref<string[]>([]);
const states = computed(() => (props.purchase ? purchaseStates : consumptionStates));
const stores = ref<any[]>([]),
	storesLoading = ref(false);
let storeRevision = 0,
	storeTimer: ReturnType<typeof setTimeout> | undefined;
function searchStores(word: string) {
	clearTimeout(storeTimer);
	const rev = ++storeRevision;
	storesLoading.value = true;
	storeTimer = setTimeout(async () => {
		try {
			const list = await props.loadStores?.(word, filters.merchantId);
			if (rev === storeRevision) stores.value = list || [];
		} catch (e: any) {
			if (rev === storeRevision) ElMessage.error(e.message || '门店选项加载失败');
		} finally {
			if (rev === storeRevision) storesLoading.value = false;
		}
	}, 350);
}
function merchantChanged() {
	filters.storeId = undefined;
	stores.value = [];
	storeRevision++;
	clearTimeout(storeTimer);
	storesLoading.value = false;
}
onBeforeUnmount(() => {
	storeRevision++;
	detailRevision++;
	listRevision++;
	clearTimeout(storeTimer);
});
const visible = ref(false),
	detailLoading = ref(false),
	detailError = ref(''),
	detail = ref<RecordRow>();
let detailRevision = 0;
const activeRow = ref<RecordRow>();
async function open(row: RecordRow) {
	activeRow.value = row;
	const rev = ++detailRevision;
	visible.value = true;
	detailLoading.value = true;
	detail.value = undefined;
	detailError.value = '';
	try {
		const result = await props.infoApi(row.id);
		if (rev === detailRevision) detail.value = result;
	} catch (e: any) {
		if (rev === detailRevision) detailError.value = e.message || '详情加载失败，请重试';
	} finally {
		if (rev === detailRevision) detailLoading.value = false;
	}
}
const rows = ref<RecordRow[]>([]);
const page = ref(1);
const size = ref(20);
const total = ref(0);
const busy = ref(false);
const error = ref('');
let query: RecordRow = {};
let listRevision = 0;
const searched = ref(false);

async function load() {
	if (!props.canPage) return;
	const rev = ++listRevision;
	busy.value = true;
	error.value = '';
	try {
		const data = await props.pageApi({ page: page.value, size: size.value, ...query });
		if (rev !== listRevision) return;
		rows.value = data.list ?? data.records ?? [];
		total.value = Number(
			data.pagination?.total ?? data.total ?? data.totalRow ?? rows.value.length
		);
	} catch (e: any) {
		if (rev === listRevision) error.value = e?.message || '请重试或检查网络连接。';
	} finally {
		if (rev === listRevision) busy.value = false;
	}
}

function search() {
	query = filterPayload(filters, range.value);
	searched.value = Object.keys(query).length > 0;
	changePage(1);
}

function changePage(value: number) {
	page.value = value;
	void load();
}

function changeSize(value: number) {
	size.value = value;
	changePage(1);
}

function reset() {
	Object.keys(filters).forEach(key => delete filters[key]);
	range.value = [];
	merchantChanged();
	search();
}

function couponName(row: RecordRow) {
	return (
		(props.purchase
			? items(row)
					.map(i => i.couponName)
					.filter(Boolean)
					.join('、')
			: row.couponName) || '未记录券名称'
	);
}

function statusTone(status: string) {
	if (status === 'PAID' || status === 'COMPLETED') return 'success';
	if (status === 'PENDING') return 'warning';
	return status === 'FAILED' ? 'danger' : 'info';
}

onMounted(load);
watch(
	() => props.canPage,
	allowed => {
		if (allowed) void load();
		else listRevision++;
	}
);

const paymentStatus = (status: string) =>
	(
		({ UNPAID: '未支付', SUCCESS: '模拟支付成功', FAILED: '模拟支付失败' }) as Record<
			string,
			string
		>
	)[status] || '未知状态';

const dateText = (value: unknown) => readable(value).replace(/^(\d{4}-\d{2}-\d{2})T/, '$1 ');

const detailFields = computed(() => {
	const d = detail.value || {};
	const pairs: any[] = props.purchase
		? [
				['订单编号', d.orderNo, true],
				...(props.summaryOnly
					? []
					: [
							['所属商家', d.merchantName],
							['用户昵称', d.customerName]
						]),
				['支付状态', paymentStatus(d.paymentStatus)],
				['渠道', d.channel === 'PLATFORM' ? '平台渠道' : '其他渠道'],
				...(props.summaryOnly ? [] : [['失效时间', dateText(d.expiresAt)]])
			]
		: [
				['账单编号', d.orderNo, true],
				['所属商家', d.merchantName],
				['消费门店', d.storeName],
				['券名称', d.couponName],
				['券类型', couponTypes[d.couponType] || '未知类型'],
				['优惠金额', money(d.discountAmount, true)],
				['应补金额', money(d.payableAmount, true)],
				['确认收款', money(d.receivedAmount, true)],
				['开单人员', d.operatorName],
				['确认人员', d.confirmedByName],
				['核销情况', d.redemptionId ? '已关联核销记录' : '尚未核销'],
				[
					'结算方式',
					d.paymentMode === 'OFFLINE'
						? '线下收款'
						: d.paymentMode === 'NONE'
							? '无需补款'
							: d.status === 'PENDING'
								? '尚未结算'
								: '未记录'
				],
				['备注', d.remark]
			];
	return pairs.map(([label, value, reference]) => ({ label, value: readable(value), reference }));
});
</script>

<style scoped>
.trade-register {
	display: grid;
	grid-template-rows: auto minmax(180px, 1fr) auto;
	gap: 12px;
	height: 100%;
	min-width: 0;
}
.record-filters {
	display: flex;
	flex-wrap: wrap;
	gap: 8px 16px;
	padding-block: 6px 10px;
}
.record-filters :deep(.el-form-item) {
	margin: 0;
	max-width: 100%;
}
.record-filters :deep(.el-form-item__content) {
	min-width: 0;
}
.record-filters :deep(.el-select),
.record-filters :deep(.el-input) {
	width: 168px;
	max-width: 100%;
}
.record-filters :deep(.el-date-editor) {
	width: min(338px, 100%);
}
.trade-register .management-pagination {
	padding: 0;
}
.trade-register .management-pagination :deep(.el-pagination) {
	flex-wrap: wrap;
}
.trade-register .cell-main {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}
.trade-amount {
	font-variant-numeric: tabular-nums;
	color: var(--workspace-ink);
}
.trade-detail-heading {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16px;
	padding: 16px;
	background: var(--workspace-neutral);
	border-radius: 8px;
}
.trade-detail-heading span {
	color: var(--el-text-color-secondary);
}
.trade-detail-heading strong {
	display: block;
	margin-top: 4px;
	font-size: 24px;
	font-variant-numeric: tabular-nums;
	color: var(--workspace-ink);
}
.trade-detail h3 {
	margin: 22px 0 10px;
	font-size: 15px;
}
.trade-facts {
	display: grid;
	grid-template-columns: repeat(auto-fit, minmax(min(200px, 100%), 1fr));
	margin: 0;
	border-block-start: 1px solid var(--el-border-color-lighter);
}
.trade-facts > div {
	padding: 10px 14px 10px 0;
	border-block-end: 1px solid var(--el-border-color-lighter);
}
.trade-facts .trade-reference {
	grid-column: 1 / -1;
}
.trade-facts dt {
	color: var(--el-text-color-secondary);
	font-size: 12px;
}
.trade-facts dd {
	margin: 4px 0 0;
	overflow-wrap: anywhere;
}
.trade-detail :deep(.el-timeline) {
	padding-inline-start: 6px;
}
@media (max-width: 850px) {
	.trade-register {
		grid-template-rows: auto minmax(260px, 50vh) auto;
	}
}
@media (max-width: 520px) {
	.record-filters :deep(.el-form-item) {
		width: 100%;
	}
	.record-filters :deep(.el-select),
	.record-filters :deep(.el-input) {
		width: 100%;
	}
}
</style>
