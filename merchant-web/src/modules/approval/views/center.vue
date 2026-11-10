<template>
	<WorkbenchLayout :title="copy.title" :context="copy.context" class="approval-workbench">
		<template #actions>
			<el-button class="workbench-refresh" :loading="loading" @click="load"
				>刷新队列</el-button
			>
		</template>

		<section class="workbench-panel approval-queue" aria-label="审批队列">
			<header class="queue-controls">
				<div class="queue-filters" role="group" aria-label="审批状态">
					<button
						v-for="filter in filters"
						:key="filter.value"
						type="button"
						:aria-pressed="status === filter.value"
						@click="filterBy(filter.value)"
					>
						{{ filter.label }}
					</button>
				</div>
				<form class="queue-search" @submit.prevent="search">
					<el-input
						v-model="keyword"
						clearable
						maxlength="100"
						:aria-label="copy.search"
						:placeholder="copy.search"
						@clear="search"
					/>
					<el-button type="primary" native-type="submit">搜索</el-button>
					<el-button :disabled="!hasFilters && !keyword" @click="reset">重置</el-button>
				</form>
			</header>
			<div class="queue-results" v-loading="loading" :aria-busy="loading">
				<ManagementState
					v-if="error"
					kind="error"
					title="审批队列加载失败"
					:description="error"
					@retry="load"
				/>
				<el-table v-else :data="rows" height="100%" class="approval-table">
					<el-table-column :label="copy.identity" min-width="220">
						<template #default="{ row }">
							<button type="button" class="queue-title" @click="open(row)">
								{{ row.title || '未命名申请' }}
							</button>
							<span v-if="type === 'COUPON'" class="queue-secondary">{{
								row.merchantName || '历史商家未记录'
							}}</span>
						</template>
					</el-table-column>
					<el-table-column label="审批状态" width="104">
						<template #default="{ row }"
							><el-tag round effect="light" :type="tagType(row.status)">{{
								statusNames[row.status] || '未提交'
							}}</el-tag></template
						>
					</el-table-column>
					<el-table-column v-if="type === 'COUPON'" label="上架状态" width="96">
						<template #default="{ row }">{{
							publishNames[row.publishStatus ?? 0] || '—'
						}}</template>
					</el-table-column>
					<el-table-column label="提交信息" min-width="170">
						<template #default="{ row }">
							<span>{{ row.submittedBy || '历史未记录' }}</span>
							<span class="queue-secondary">{{ date(row.submittedAt) }}</span>
						</template>
					</el-table-column>
					<el-table-column label="操作" fixed="right" width="96" align="right">
						<template #default="{ row }"
							><el-button link type="primary" @click="open(row)"
								>查看详情</el-button
							></template
						>
					</el-table-column>
					<template #empty>
						<ManagementState
							v-if="!loading"
							:title="hasFilters ? '没有匹配的申请' : '暂无审批申请'"
							:description="
								hasFilters
									? '可以更换关键词或状态后再查找。'
									: '收到提交的申请后，将在此处显示。'
							"
						>
							<el-button v-if="hasFilters" @click="reset">清除筛选</el-button>
						</ManagementState>
					</template>
				</el-table>
			</div>
			<footer v-if="!error" class="queue-footer">
				<el-pagination
					:current-page="page"
					:page-size="20"
					:total="total"
					:disabled="loading"
					layout="total, prev, pager, next"
					@current-change="changePage"
				/>
			</footer>
		</section>

		<el-drawer
			v-model="visible"
			:title="copy.drawer"
			size="min(760px, 100vw)"
			:close-on-click-modal="!reviewing"
			:close-on-press-escape="!reviewing"
			:show-close="!reviewing"
			destroy-on-close
			@close="detailRevision++"
		>
			<div class="approval-detail" v-loading="detailLoading" :aria-busy="detailLoading">
				<ManagementState
					v-if="detailError"
					kind="error"
					title="审批详情加载失败"
					:description="detailError"
					@retry="loadDetail"
				/>
				<template v-else-if="detail">
					<header class="application-heading">
						<h2>{{ detail.item.title || '未命名申请' }}</h2>
						<el-tag round effect="light" :type="tagType(detail.item.status)">{{
							statusNames[detail.item.status] || '—'
						}}</el-tag>
						<dl>
							<div v-if="type === 'COUPON'">
								<dt>商家</dt>
								<dd>{{ detail.item.merchantName || '历史未记录' }}</dd>
							</div>
							<div>
								<dt>提交人</dt>
								<dd>{{ detail.item.submittedBy || '历史未记录' }}</dd>
							</div>
							<div>
								<dt>提交版本</dt>
								<dd>第 {{ detail.item.version }} 版／轮</dd>
							</div>
							<div>
								<dt>提交时间</dt>
								<dd>{{ date(detail.item.submittedAt) }}</dd>
							</div>
						</dl>
					</header>
					<el-tabs v-model="detailTab" class="approval-tabs">
						<el-tab-pane label="提交资料" name="snapshot">
							<dl class="snapshot-grid">
								<div
									v-for="field in snapshotFields"
									:key="field.key"
									:class="{ 'snapshot-wide': field.wide }"
								>
									<dt>{{ field.label }}</dt>
									<dd>{{ snapshotValue(field.key) }}</dd>
								</div>
							</dl>
							<el-table
								v-if="detail.snapshot.contentItems?.length"
								:data="detail.snapshot.contentItems"
								size="small"
							>
								<el-table-column prop="name" label="内容项目" />
								<el-table-column prop="quantity" label="数量" width="80" />
								<el-table-column prop="itemDescription" label="规格说明" />
							</el-table>
						</el-tab-pane>
						<el-tab-pane label="流转记录" name="history"
							><ApprovalFlow :detail="detail" :type="type"
						/></el-tab-pane>
					</el-tabs>
				</template>
			</div>
			<template #footer>
				<div v-if="canReview" class="review-note">
					<label for="approval-reason">审批意见</label>
					<el-input
						id="approval-reason"
						v-model="reason"
						type="textarea"
						:rows="2"
						maxlength="500"
						show-word-limit
						resize="none"
						:disabled="reviewing"
						placeholder="通过可选填，驳回时请说明原因"
					/>
				</div>
				<div class="review-actions">
					<el-button :disabled="reviewing" @click="visible = false">关闭</el-button>
					<template v-if="canReview">
						<el-button type="danger" plain :disabled="reviewing" @click="review(false)"
							>驳回</el-button
						>
						<el-button type="primary" :loading="reviewing" @click="review(true)"
							>审核通过</el-button
						>
					</template>
				</div>
			</template>
		</el-drawer>
	</WorkbenchLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useCool } from '/@/cool';
import dayjs from 'dayjs';
import WorkbenchLayout from '/@/modules/workspace/components/workbench-layout.vue';
import ManagementState from '/@/modules/workspace/components/management-state.vue';
import ApprovalFlow from '../components/approval-flow.vue';
import type { ApprovalItem as Item, ApprovalDetail as Detail } from '../utils/flow';
defineOptions({ name: 'approval-center' });
const props = withDefaults(defineProps<{ approvalType?: 'COUPON' | 'MERCHANT' }>(), {
	approvalType: 'COUPON'
});
const { service } = useCool();
const type = computed(() => props.approvalType);
const copy = computed(() =>
	type.value === 'COUPON'
		? {
				title: '优惠券发布审核',
				context: '核对商家提交的券面与使用规则。审核通过后，由商家自行上架。',
				identity: '优惠券 / 商家',
				search: '搜索券名称或商家名称',
				drawer: '优惠券发布申请'
			}
		: {
				title: '商家入驻审核',
				context: '核对商家资质和经营资料。审核通过后，将开通商家经营账号。',
				identity: '申请商家',
				search: '搜索申请商家名称',
				drawer: '商家入驻申请'
			}
);
const filters = [
	{ value: -1, label: '全部申请' },
	{ value: 1, label: '待审核' },
	{ value: 2, label: '已通过' },
	{ value: 3, label: '已驳回' }
];
const status = ref(-1);
const appliedKeyword = ref('');
const hasFilters = computed(() => status.value !== -1 || Boolean(appliedKeyword.value));
const publishNames = ['未上架', '已上架', '已下架'];
const keyword = ref('');
const page = ref(1);
const total = ref(0);
const rows = ref<Item[]>([]);
const loading = ref(false);
const error = ref('');
const visible = ref(false);
const detail = ref<Detail>();
const detailTab = ref('snapshot');
const detailId = ref<number>();
const canReview = computed(() => detail.value?.item.status === 1 && detail.value.canReview);
const detailLoading = ref(false);
const detailError = ref('');
const reviewing = ref(false);
const reason = ref('');
let revision = 0;
let detailRevision = 0;
const statusNames: Record<number, string> = { 0: '已撤回', 1: '待审核', 2: '已通过', 3: '已驳回' };
function date(value?: string) {
	return value && dayjs(value).isValid()
		? dayjs(value).format('YYYY-MM-DD HH:mm:ss')
		: '历史未记录';
}
function tagType(value: number): 'warning' | 'success' | 'danger' | 'info' {
	return ({ 1: 'warning', 2: 'success', 3: 'danger' } as const)[value] || 'info';
}
function message(err: any) {
	return err?.message || '请求失败，请检查后端是否已重启及账号权限';
}
async function load() {
	const current = ++revision;
	loading.value = true;
	error.value = '';
	try {
		const result = await service.request({
			url: 'admin/approval/center/page',
			method: 'GET',
			params: {
				type: type.value,
				status: status.value === -1 ? undefined : status.value,
				keyword: appliedKeyword.value,
				page: page.value,
				size: 20
			}
		});
		if (current === revision) {
			rows.value = result.list;
			total.value = result.pagination.total;
		}
	} catch (err) {
		if (current === revision) {
			rows.value = [];
			total.value = 0;
			error.value = message(err);
		}
	} finally {
		if (current === revision) loading.value = false;
	}
}
function search() {
	appliedKeyword.value = keyword.value.trim();
	page.value = 1;
	void load();
}
function filterBy(value: number) {
	if (status.value === value) return;
	status.value = value;
	page.value = 1;
	void load();
}
function changePage(value: number) {
	if (page.value === value) return;
	page.value = value;
	void load();
}
function reset() {
	status.value = -1;
	keyword.value = '';
	search();
}
function open(row: Item) {
	detailId.value = row.id;
	detailTab.value = 'snapshot';
	reason.value = '';
	visible.value = true;
	void loadDetail();
}
async function loadDetail() {
	const id = detailId.value;
	if (id == null || !visible.value) return;
	const current = ++detailRevision;
	detail.value = undefined;
	detailError.value = '';
	detailLoading.value = true;
	try {
		const result = await service.request({
			url: 'admin/approval/center/info',
			method: 'GET',
			params: { type: type.value, id }
		});
		if (current === detailRevision) detail.value = result;
	} catch (err) {
		if (current === detailRevision) detailError.value = message(err);
	} finally {
		if (current === detailRevision) detailLoading.value = false;
	}
}
async function review(approved: boolean) {
	if (reviewing.value || !canReview.value || !detail.value) return;
	if (!approved && !reason.value.trim()) {
		ElMessage.warning('请填写驳回原因');
		return;
	}
	const item = detail.value.item;
	const currentType = type.value;
	reviewing.value = true;
	try {
		await ElMessageBox.confirm(
			`确认${approved ? '通过' : '驳回'}“${item.title}”？${approved && currentType === 'MERCHANT' ? '通过后将开通商家经营账号。' : ''}`,
			'审批确认',
			{ type: 'warning' }
		);
		await service.request({
			url: 'admin/approval/center/review',
			method: 'POST',
			data: {
				type: currentType,
				id: item.id,
				version: item.version,
				approved,
				reason: reason.value.trim() || undefined
			}
		});
		ElMessage.success('审批已处理');
		visible.value = false;
		await load();
	} catch (err: any) {
		if (err !== 'cancel' && err !== 'close') ElMessage.error(message(err));
	} finally {
		reviewing.value = false;
	}
}
const snapshotFields = computed(() =>
	type.value === 'MERCHANT'
		? [
				{ key: 'name', label: '商家名称' },
				{ key: 'categoryName', label: '商家分类' },
				{ key: 'contactName', label: '联系人' },
				{ key: 'contactPhone', label: '联系电话' },
				{ key: 'licenseNumber', label: '营业执照号' },
				{ key: 'address', label: '经营地址', wide: true },
				{ key: 'introduction', label: '商家介绍', wide: true },
				{ key: 'licenseUrl', label: '资质资料地址', wide: true }
			]
		: [
				{ key: 'name', label: '券名称' },
				{ key: 'type', label: '券类型' },
				{ key: 'salePrice', label: '售价（元）' },
				{ key: 'minSpend', label: '最低消费（元）' },
				{ key: 'discountAmount', label: '满减金额（元）' },
				{ key: 'deductAmount', label: '抵扣金额（元）' },
				{ key: 'discountRate', label: '折扣数' },
				{ key: 'maxDiscount', label: '最高优惠（元）' },
				{ key: 'validityType', label: '有效期方式' },
				{ key: 'validDays', label: '有效天数' },
				{ key: 'validFrom', label: '有效期开始' },
				{ key: 'validTo', label: '有效期结束' },
				{ key: 'storeNames', label: '适用门店', wide: true },
				{ key: 'description', label: '使用说明', wide: true }
			]
);
function snapshotValue(key: string) {
	const value = detail.value?.snapshot[key];
	if (value == null || value === '') return '—';
	if (key === 'type')
		return (
			(
				{
					FULL_REDUCTION: '满减券',
					DISCOUNT: '折扣券',
					CASH: '代金券',
					PACKAGE: '套餐券',
					EXPERIENCE: '体验券',
					EXCHANGE: '兑换券'
				} as Record<string, string>
			)[value] || '未知类型'
		);
	if (key === 'validityType') return value === 'FIXED' ? '固定日期' : '发券后按天有效';
	if (key === 'storeNames') return value.length ? value.join('、') : '商家全部营业门店';
	if (key === 'validFrom' || key === 'validTo') return date(value);
	return String(value);
}
onMounted(load);
</script>

<style scoped>
.approval-workbench {
	display: flex;
	flex-direction: column;
	gap: 16px;
}
.approval-workbench :deep(.workbench-banner) {
	flex: none;
}
.approval-queue {
	display: grid;
	flex: 1;
	grid-template-rows: auto minmax(0, 1fr) auto;
	min-height: 330px;
	overflow: hidden;
}
.queue-controls,
.queue-search,
.queue-filters,
.queue-footer {
	display: flex;
	align-items: center;
	gap: 8px;
}
.queue-controls {
	justify-content: space-between;
	flex-wrap: wrap;
	padding: 12px 16px;
	border-block-end: 1px solid var(--el-border-color-lighter);
}
.queue-filters {
	flex-wrap: wrap;
}
.queue-filters button,
.queue-title {
	appearance: none;
	border: 0;
	font: inherit;
	cursor: pointer;
}
.queue-filters button {
	border-radius: 6px;
	padding: 7px 10px;
	background: transparent;
	color: var(--el-text-color-regular);
	font-size: 13px;
}
.queue-filters button:hover {
	background: var(--workspace-neutral);
}
.queue-filters button[aria-pressed='true'] {
	background: var(--workspace-accent-soft);
	color: var(--workspace-accent-text);
	font-weight: 650;
}
.queue-search {
	flex: 0 1 370px;
	max-width: 100%;
}
.queue-search .el-input {
	min-width: 90px;
}
.queue-search .el-button,
.review-actions .el-button {
	margin-inline-start: 0;
}
.queue-results {
	min-height: 0;
}
.approval-table {
	--el-table-header-bg-color: var(--workspace-neutral);
	--el-table-row-hover-bg-color: var(--workspace-accent-soft);
}
.approval-table :deep(th.el-table__cell) {
	color: var(--el-text-color-regular);
}
.queue-title {
	padding: 0;
	background: transparent;
	color: var(--el-text-color-primary);
	font-weight: 650;
	line-height: 1.5;
	text-align: left;
	overflow-wrap: anywhere;
}
.queue-title:hover {
	color: var(--workspace-accent-text);
	text-decoration: underline;
}
.queue-title:focus-visible,
.queue-filters button:focus-visible {
	outline: 2px solid var(--workspace-accent-text);
	outline-offset: 2px;
}
.queue-secondary {
	display: block;
	margin-top: 2px;
	font-size: 12px;
	color: var(--el-text-color-regular);
}
.queue-footer {
	justify-content: flex-end;
	padding: 10px 16px;
	border-block-start: 1px solid var(--el-border-color-lighter);
}
.approval-detail {
	min-height: 160px;
}
.application-heading {
	display: grid;
	grid-template-columns: minmax(0, 1fr) auto;
	align-items: start;
	gap: 12px;
	padding: 16px 18px;
	border-radius: 8px;
	background: var(--workspace-accent-soft);
}
.application-heading h2 {
	margin: 0;
	font-size: 22px;
	line-height: 1.35;
	overflow-wrap: anywhere;
}
.application-heading dl,
.snapshot-grid {
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 12px 16px;
	margin: 0;
}
.application-heading dl {
	grid-column: 1 / -1;
}
.approval-tabs {
	margin-top: 12px;
}
.snapshot-grid {
	gap: 0 20px;
}
.snapshot-grid > div {
	padding: 10px 0;
	border-block-end: 1px solid var(--el-border-color-lighter);
}
.snapshot-wide {
	grid-column: 1 / -1;
}
.approval-detail dt,
.review-note label {
	color: var(--el-text-color-regular);
	font-size: 12px;
}
.approval-detail dd {
	margin: 3px 0 0;
	overflow-wrap: anywhere;
	white-space: pre-wrap;
}
.review-note {
	display: grid;
	gap: 6px;
	margin-bottom: 12px;
	text-align: start;
}
.review-actions {
	display: flex;
	flex-wrap: wrap;
	justify-content: flex-end;
	gap: 8px;
}
</style>
