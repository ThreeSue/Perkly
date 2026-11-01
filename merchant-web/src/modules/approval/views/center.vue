<template>
	<div class="approval-center">
		<el-alert
			:title="
				type === 'COUPON'
					? '优惠券审核通过后，仍需商家自行上架。'
					: '商家入驻申请审核通过后，将开通商家经营账号。'
			"
			type="info"
			:closable="false"
			show-icon
		/>
		<div class="toolbar">
			<el-select v-model="status" aria-label="审批状态" style="width: 160px"
				><el-option label="全部状态" :value="-1" /><el-option
					label="待我审批"
					:value="1" /><el-option label="已通过" :value="2" /><el-option
					label="已驳回"
					:value="3"
			/></el-select>
			<el-input
				v-model="keyword"
				clearable
				maxlength="100"
				:placeholder="type === 'COUPON' ? '搜索券名称或商家名称' : '搜索申请商家名称'"
				style="width: 260px"
				@keyup.enter="search"
			/>
			<el-button type="primary" @click="search">搜索</el-button
			><el-button @click="reset">重置</el-button><el-button @click="load">刷新</el-button>
		</div>
		<el-alert v-if="error" :title="error" type="error" :closable="false" />
		<el-table
			v-loading="loading"
			:data="rows"
			border
			stripe
			class="approval-table"
			:show-overflow-tooltip="true"
		>
			<el-table-column
				prop="title"
				:label="type === 'COUPON' ? '券名称' : '申请商家'"
				min-width="200"
			/>
			<el-table-column
				v-if="type === 'COUPON'"
				prop="merchantName"
				label="所属商家"
				min-width="160"
			/>
			<el-table-column prop="submittedBy" label="提交人" min-width="130" />
			<el-table-column label="提交时间" min-width="170"
				><template #default="{ row }">{{
					date(row.submittedAt)
				}}</template></el-table-column
			>
			<el-table-column label="审批状态" width="110"
				><template #default="{ row }"
					><el-tag :type="tagType(row.status)">{{
						statusNames[row.status] || '未提交'
					}}</el-tag></template
				></el-table-column
			>
			<el-table-column v-if="type === 'COUPON'" label="上架状态" width="110"
				><template #default="{ row }">{{
					['未上架', '已上架', '已下架'][row.publishStatus ?? 0]
				}}</template></el-table-column
			>
			<el-table-column label="操作" fixed="right" width="120"
				><template #default="{ row }"
					><el-button link type="primary" @click="open(row)"
						>审批详情</el-button
					></template
				></el-table-column
			>
		</el-table>
		<el-pagination
			v-model:current-page="page"
			:page-size="20"
			:total="total"
			layout="total, prev, pager, next"
			@current-change="load"
		/>
		<el-drawer
			v-model="visible"
			title="审批详情与流转记录"
			size="min(860px, 100vw)"
			direction="rtl"
			:close-on-click-modal="!reviewing"
			:close-on-press-escape="!reviewing"
			:show-close="!reviewing"
			destroy-on-close
			@closed="detailRevision++"
		>
			<div v-loading="detailLoading" class="approval-detail">
				<el-alert v-if="detailError" :title="detailError" type="error" :closable="false" />
				<template v-if="detail">
					<header class="application-heading">
						<div>
							<span class="application-kind">{{
								type === 'COUPON' ? '优惠券发布申请' : '商家入驻申请'
							}}</span>
							<h2>{{ detail.item.title }}</h2>
						</div>
						<el-tag :type="tagType(detail.item.status)" size="large">{{
							statusNames[detail.item.status] || '已撤回'
						}}</el-tag>
					</header>
					<el-descriptions :column="2" border>
						<el-descriptions-item label="事项">{{
							detail.item.title
						}}</el-descriptions-item
						><el-descriptions-item label="商家">{{
							detail.item.merchantName
						}}</el-descriptions-item>
						<el-descriptions-item label="提交人">{{
							detail.item.submittedBy
						}}</el-descriptions-item
						><el-descriptions-item label="审批状态">{{
							statusNames[detail.item.status] || '已撤回'
						}}</el-descriptions-item>
						<el-descriptions-item label="提交版本"
							>第 {{ detail.item.version }} 版／轮</el-descriptions-item
						><el-descriptions-item label="提交时间">{{
							date(detail.item.submittedAt)
						}}</el-descriptions-item>
					</el-descriptions>
					<approval-flow :detail="detail" :type="type" />
					<h3>提交资料快照</h3>
					<el-descriptions :column="2" border>
						<el-descriptions-item
							v-for="field in snapshotFields"
							:key="field.key"
							:label="field.label"
							:span="field.wide ? 2 : 1"
							>{{ snapshotValue(field.key) }}</el-descriptions-item
						>
					</el-descriptions>
					<el-table
						v-if="detail.snapshot.contentItems?.length"
						:data="detail.snapshot.contentItems"
						size="small"
						border
						:show-overflow-tooltip="true"
						style="margin-top: 12px"
						><el-table-column prop="name" label="内容项目" /><el-table-column
							prop="quantity"
							label="数量"
							width="80" /><el-table-column prop="itemDescription" label="规格说明"
					/></el-table>
					<template v-if="detail.item.status === 1 && detail.canReview">
						<h3>审批意见</h3>
						<el-input
							v-model="reason"
							type="textarea"
							:rows="3"
							maxlength="500"
							show-word-limit
							placeholder="通过可选填；驳回必须说明原因，申请人将按意见修改"
						/>
					</template>
				</template>
			</div>
			<template #footer
				><el-button :disabled="reviewing" @click="visible = false">关闭</el-button
				><template v-if="detail?.item.status === 1 && detail.canReview"
					><el-button
						type="danger"
						:loading="reviewing"
						:disabled="reviewing"
						@click="review(false)"
						>驳回</el-button
					><el-button
						type="success"
						:loading="reviewing"
						:disabled="reviewing"
						@click="review(true)"
						>审核通过</el-button
					></template
				></template
			>
		</el-drawer>
	</div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useCool } from '/@/cool';
import dayjs from 'dayjs';
import ApprovalFlow from '../components/approval-flow.vue';
import type { ApprovalItem as Item, ApprovalDetail as Detail } from '../utils/flow';
defineOptions({ name: 'approval-center' });
const props = withDefaults(defineProps<{ approvalType?: 'COUPON' | 'MERCHANT' }>(), {
	approvalType: 'COUPON'
});
const { service } = useCool();
const type = computed(() => props.approvalType);
const status = ref(-1);
const keyword = ref('');
const page = ref(1);
const total = ref(0);
const rows = ref<Item[]>([]);
const loading = ref(false);
const error = ref('');
const visible = ref(false);
const detail = ref<Detail>();
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
				keyword: keyword.value.trim(),
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
	page.value = 1;
	void load();
}
function reset() {
	status.value = -1;
	keyword.value = '';
	search();
}
async function open(row: Item) {
	const current = ++detailRevision;
	visible.value = true;
	detail.value = undefined;
	detailError.value = '';
	reason.value = '';
	detailLoading.value = true;
	try {
		const result = await service.request({
			url: 'admin/approval/center/info',
			method: 'GET',
			params: { type: type.value, id: row.id }
		});
		if (current === detailRevision) detail.value = result;
	} catch (err) {
		if (current === detailRevision) detailError.value = message(err);
	} finally {
		if (current === detailRevision) detailLoading.value = false;
	}
}
async function review(approved: boolean) {
	if (reviewing.value || !detail.value) return;
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
.approval-center {
	padding: 18px;
	background: var(--el-bg-color);
	height: 100%;
	box-sizing: border-box;
	overflow: auto;
}
.toolbar {
	display: flex;
	gap: 12px;
	align-items: center;
	flex-wrap: wrap;
	margin: 12px 0 18px;
}
.approval-table {
	width: 100%;
	margin: 12px 0 18px;
}
.el-pagination {
	justify-content: flex-end;
}
h3 {
	font-size: 15px;
	margin: 22px 0 14px;
}
.approval-detail {
	min-height: 160px;
}
.application-heading {
	display: flex;
	justify-content: space-between;
	align-items: flex-start;
	gap: 16px;
	margin: 0 0 22px;
}
.application-kind {
	font-size: 12px;
	color: var(--el-text-color-secondary);
}
.application-heading h2 {
	margin: 8px 0 0;
	font-size: 21px;
	line-height: 1.5;
	overflow-wrap: anywhere;
}
.approval-detail :deep(.el-descriptions__content) {
	overflow-wrap: anywhere;
}
</style>
