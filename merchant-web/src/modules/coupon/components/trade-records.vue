<template>
	<el-empty v-if="!canPage" description="当前账号没有查询权限" />
	<cl-crud v-else ref="Crud">
		<cl-row
			><el-alert
				:title="
					purchase
						? '购券订单仅供查询，金额为模拟支付金额，不代表真实收款或结算。'
						: '此处为到店用券消费账单；已完成账单代表成功核销，线下补款由店员确认。仅查询，不修改核销结果。'
				"
				type="info"
				:closable="false"
				show-icon
		/></cl-row>
		<cl-row><cl-refresh-btn /><cl-flex1 /></cl-row>
		<cl-row
			><el-form inline class="record-filters" @submit.prevent="search">
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
						@keyup.enter="search"
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
					><el-button @click="reset">重置</el-button></el-form-item
				>
			</el-form></cl-row
		>
		<cl-row
			><cl-table ref="Table">
				<template #column-coupon="{ scope }"
					><span>{{
						purchase
							? items(scope.row)
									.map(i => i.couponName)
									.join('、') || '—'
							: scope.row.couponName
					}}</span></template
				>
				<template #column-status="{ scope }"
					><el-tag
						:type="
							['PAID', 'COMPLETED'].includes(scope.row.status)
								? 'success'
								: scope.row.status === 'PENDING'
									? 'warning'
									: 'info'
						"
						>{{ scope.row.statusText || '未知状态' }}</el-tag
					></template
				>
				<template #column-couponType="{ scope }"
					><el-tag effect="dark" :color="colors[scope.row.couponType] || '#64748b'">{{
						couponTypes[scope.row.couponType] || '未知类型'
					}}</el-tag></template
				>
				<template v-for="f in amountFields" :key="f" #[`column-${f}`]="{ scope }">{{
					money(scope.row[f], !purchase)
				}}</template>
			</cl-table></cl-row
		>
		<cl-row><cl-flex1 /><cl-pagination /></cl-row>
	</cl-crud>
	<cl-dialog
		v-model="visible"
		:title="purchase ? '购券订单详情' : '消费与核销详情'"
		width="900px"
		@closed="detailRevision++"
	>
		<div v-loading="detailLoading">
			<el-alert
				v-if="detailError"
				:title="detailError"
				type="error"
				:closable="false"
			/><template v-if="detail">
				<el-descriptions :column="2" border
					><el-descriptions-item v-for="f in detailFields" :key="f.label" :label="f.label"
						><div class="detail-value" :title="f.value">
							{{ f.value }}
						</div></el-descriptions-item
					></el-descriptions
				>
				<template v-if="purchase"
					><h4>购券明细</h4>
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
					><h4>发券时的套餐／服务内容</h4>
					<el-table :data="contentItems(detail)" border
						><el-table-column
							prop="name"
							label="项目"
							show-overflow-tooltip /><el-table-column
							prop="quantity"
							label="数量"
							width="80" /></el-table
				></template>
				<h4>状态过程</h4>
				<el-timeline
					><el-timeline-item :timestamp="readable(detail.createdAt)"
						>{{ purchase ? '创建购券订单' : '开立消费账单'
						}}<span v-if="!purchase">
							· {{ readable(detail.operatorName) }}</span
						></el-timeline-item
					><el-timeline-item
						v-if="purchase && detail.paidAt"
						:timestamp="readable(detail.paidAt)"
						type="success"
						>模拟支付完成</el-timeline-item
					><el-timeline-item
						v-if="!purchase && detail.completedAt"
						:timestamp="readable(detail.completedAt)"
						type="success"
						>确认完成并核销 · {{ readable(detail.confirmedByName) }}</el-timeline-item
					><el-timeline-item v-if="!detail.paidAt && !detail.completedAt"
						>当前状态：{{
							detail.statusText
						}}（未提供其他状态变更时间）</el-timeline-item
					></el-timeline
				>
			</template>
		</div>
	</cl-dialog>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue';
import { useCrud, useTable } from '@cool-vue/crud';
import { ElMessage } from 'element-plus';
import {
	money,
	readable,
	items,
	contentItems,
	couponTypes,
	colors,
	purchaseStates,
	consumptionStates,
	filterPayload,
	type RecordRow
} from '../record-model';
const props = withDefaults(
	defineProps<{
		purchase?: boolean;
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
	clearTimeout(storeTimer);
});
const visible = ref(false),
	detailLoading = ref(false),
	detailError = ref(''),
	detail = ref<RecordRow>();
let detailRevision = 0;
async function open(row: RecordRow) {
	const rev = ++detailRevision;
	visible.value = true;
	detailLoading.value = true;
	detail.value = undefined;
	detailError.value = '';
	try {
		const result = await props.infoApi(row.id);
		if (rev === detailRevision) detail.value = result;
	} catch (e: any) {
		if (rev === detailRevision) detailError.value = e.message || '详情加载失败，请关闭后重试';
	} finally {
		if (rev === detailRevision) detailLoading.value = false;
	}
}
const amountFields = computed(() =>
	props.purchase
		? ['totalAmount']
		: ['consumptionAmount', 'discountAmount', 'payableAmount', 'receivedAmount']
);
const Table = useTable({
	columns: [
		{
			label: props.purchase ? '购券订单号' : '消费账单号',
			prop: 'orderNo',
			minWidth: 230,
			showOverflowTooltip: true
		},
		{ label: '所属商家', prop: 'merchantName', minWidth: 150, showOverflowTooltip: true },
		...(props.purchase
			? [
					{
						label: '用户昵称',
						prop: 'customerName',
						minWidth: 120,
						showOverflowTooltip: true
					}
				]
			: [{ label: '消费门店', prop: 'storeName', minWidth: 160, showOverflowTooltip: true }]),
		{ label: '券名称', prop: 'coupon', minWidth: 180, showOverflowTooltip: true },
		...(!props.purchase
			? [
					{ label: '券类型', prop: 'couponType', width: 105 },
					{
						label: '开单人员',
						prop: 'operatorName',
						minWidth: 110,
						showOverflowTooltip: true
					}
				]
			: []),
		{ label: '状态', prop: 'status', width: 100 },
		...(props.purchase
			? [{ label: '模拟购券金额', prop: 'totalAmount', width: 150 }]
			: [
					{ label: '消费金额', prop: 'consumptionAmount', width: 125 },
					{ label: '优惠金额', prop: 'discountAmount', width: 125 },
					{ label: '应补金额', prop: 'payableAmount', width: 125 },
					{ label: '确认收款', prop: 'receivedAmount', width: 125 }
				]),
		{ label: '创建时间', prop: 'createdAt', width: 180 },
		{
			type: 'op',
			width: 105,
			buttons: props.canInfo
				? [
						{
							label: '查看详情',
							type: 'primary',
							onClick: ({ scope }: any) => open(scope.row)
						}
					]
				: []
		}
	]
});
const Crud = useCrud(
	{
		service: {
			namespace: 'trade-readonly',
			permission: {},
			_permission: {
				page: true,
				info: props.canInfo,
				add: false,
				update: false,
				delete: false
			},
			page: (p: any) => props.pageApi({ ...p, ...filterPayload(filters, range.value) })
		}
	},
	app => {
		if (props.canPage) app.refresh();
	}
);
function search() {
	Crud.value?.refresh({ page: 1 });
}
function reset() {
	Object.keys(filters).forEach(k => delete filters[k]);
	range.value = [];
	merchantChanged();
	search();
}
const detailFields = computed(() => {
	const d = detail.value || {};
	const pairs: any[] = props.purchase
		? [
				['订单编号', d.orderNo],
				['所属商家', d.merchantName],
				['用户昵称', d.customerName],
				['订单状态', d.statusText],
				['模拟购券金额', money(d.totalAmount)],
				[
					'支付状态',
					(
						{
							UNPAID: '未支付',
							SUCCESS: '模拟支付成功',
							FAILED: '模拟支付失败'
						} as Record<string, string>
					)[d.paymentStatus] || '未知状态'
				],
				['渠道', d.channel === 'PLATFORM' ? '平台渠道' : '其他渠道'],
				['失效时间', d.expiresAt]
			]
		: [
				['账单编号', d.orderNo],
				['所属商家', d.merchantName],
				['消费门店', d.storeName],
				['券名称', d.couponName],
				['券类型', couponTypes[d.couponType] || '未知类型'],
				['账单状态', d.statusText],
				['消费金额', money(d.consumptionAmount, true)],
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
	return pairs.map(([label, value]) => ({ label, value: readable(value) }));
});
</script>
<style scoped>
.record-filters {
	padding-top: 8px;
}
.record-filters :deep(.el-select) {
	width: 185px;
}
.record-filters :deep(.el-input) {
	width: 210px;
}
.record-filters :deep(.el-date-editor) {
	max-width: 380px;
}
.detail-value {
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	max-width: 320px;
}
h4 {
	margin: 20px 0 12px;
}
:deep(.el-table .cell) {
	white-space: nowrap;
}
:deep(.el-table .el-tag) {
	border: 0;
}
</style>
