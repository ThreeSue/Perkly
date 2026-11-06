<template>
	<cl-crud ref="Crud">
		<cl-row>
			<el-alert
				title="启用模板后，还需提交审核、审核通过并上架，用户端才会展示该优惠券。"
				type="info"
				:closable="false"
				show-icon
			/>
		</cl-row>
		<cl-row>
			<!-- 刷新按钮 -->
			<cl-refresh-btn />
			<!-- 新增按钮 -->
			<cl-add-btn />
			<!-- 删除按钮 -->
			<cl-multi-delete-btn />
			<cl-flex1 />
		</cl-row>

		<cl-row>
			<cl-search ref="Search" reset-btn>
				<template #slot-search-merchant="{ scope }">
					<MerchantSelect v-model="scope.merchantId" style="width: 190px" />
				</template>
			</cl-search>
		</cl-row>

		<cl-row>
			<!-- 数据表格 -->
			<cl-table ref="Table">
				<template #column-type="{ scope }">
					<el-tag
						class="coupon-type-tag"
						effect="dark"
						:color="typeColors[scope.row.type] || '#64748b'"
					>
						{{ typeNames[scope.row.type] || '未知类型' }}
					</el-tag>
				</template>
				<template #column-cover="{ scope }">
					<CouponCover
						:src="scope.row.cover"
						:name="scope.row.name"
						:badge="coverBadge(scope.row)"
						:type-name="typeNames[scope.row.type] || '优惠券'"
						:subtitle="ruleText(scope.row)"
					/>
				</template>
				<template #column-publicationActions="{ scope }">
					<div v-permission="service.coupon.template.permission.update">
						<el-button
							v-if="canSubmit(scope.row)"
							link
							type="primary"
							:disabled="!!publicationBusy"
							@click="publicationAction(scope.row, 'submitAudit')"
							>提交审核</el-button
						>
						<template v-if="scope.row.publication?.auditStatus === 1">
							<el-button
								link
								type="primary"
								:disabled="!!publicationBusy"
								@click="openReview(scope.row)"
								>审核</el-button
							>
							<el-button
								link
								:disabled="!!publicationBusy"
								@click="publicationAction(scope.row, 'withdrawAudit')"
								>撤回</el-button
							>
						</template>
						<el-button
							v-if="canPublish(scope.row)"
							link
							type="success"
							:disabled="!!publicationBusy"
							@click="publicationAction(scope.row, 'publish')"
							>上架</el-button
						>
						<el-button
							v-if="scope.row.publication?.publishStatus === 1"
							link
							type="danger"
							:disabled="!!publicationBusy"
							@click="publicationAction(scope.row, 'unpublish')"
							>下架</el-button
						>
					</div>
				</template>
			</cl-table>
		</cl-row>

		<cl-row>
			<cl-flex1 />
			<!-- 分页控件 -->
			<cl-pagination />
		</cl-row>

		<!-- 新增、编辑 -->
		<cl-upsert ref="Upsert">
			<template #slot-cover="{ scope }"><CoverField v-model="scope.cover" /></template>
			<template #slot-edit-merchant="{ scope }">
				<el-input v-if="scope.id" :model-value="scope.merchantName" readonly />
				<MerchantSelect
					v-else
					v-model="scope.merchantId"
					style="width: 100%"
					@change="
						id => {
							scope.storeIds = [];
							scope.contentItems = [];
							loadStores(id);
						}
					"
				/>
			</template>
			<template #slot-stores="{ scope }">
				<el-select
					v-model="scope.storeIds"
					multiple
					filterable
					:loading="storesLoading"
					:disabled="!scope.merchantId || storesFailed"
					placeholder="选择该商家的适用门店"
					style="width: 100%"
				>
					<el-option
						v-for="store in storeOptions"
						:key="store.id"
						:value="store.id!"
						:label="`${store.name}${Number(store.status) === 1 ? '' : '（停用）'}`"
						:disabled="
							Number(store.status) !== 1 && !originalStoreIds.includes(store.id!)
						"
					/>
				</el-select>
				<div v-if="storesFailed" class="form-hint">
					门店加载失败，请<el-button
						link
						type="primary"
						@click="loadStores(scope.merchantId)"
						>重试</el-button
					>
				</div>
			</template>
			<template #slot-issue-limit="{ scope }">
				<el-switch
					:model-value="scope.issueLimit == null"
					active-text="不限量"
					@update:model-value="
						value =>
							(scope.issueLimit = value
								? null
								: Math.max(Number(scope.issuedCount) || 0, 1))
					"
				/>
				<el-input-number
					v-if="scope.issueLimit != null"
					v-model="scope.issueLimit"
					:min="Math.max(Number(scope.issuedCount) || 0, 1)"
					:max="2147483647"
					:precision="0"
					controls-position="right"
					style="margin-left: 12px; width: 150px"
				/>
			</template>
			<template #slot-content-items="{ scope }">
				<CouponContentEditor v-model="scope.contentItems" :merchant-id="scope.merchantId" />
			</template>
		</cl-upsert>
		<el-dialog
			v-model="reviewVisible"
			title="审核平台优惠券"
			width="720px"
			:close-on-click-modal="false"
		>
			<template v-if="reviewCoupon">
				<el-descriptions :column="2" border>
					<el-descriptions-item label="券名称">{{
						reviewCoupon.name
					}}</el-descriptions-item>
					<el-descriptions-item label="所属商家">{{
						reviewCoupon.merchantName
					}}</el-descriptions-item>
					<el-descriptions-item label="类型">{{
						typeNames[reviewCoupon.type || '']
					}}</el-descriptions-item>
					<el-descriptions-item label="售价"
						>{{ numberText(reviewCoupon.salePrice, true) }} 元</el-descriptions-item
					>
					<el-descriptions-item label="优惠规则" :span="2">{{
						ruleText(reviewCoupon)
					}}</el-descriptions-item>
					<el-descriptions-item label="适用门店" :span="2">{{
						scopeText(reviewCoupon)
					}}</el-descriptions-item>
					<el-descriptions-item label="有效期" :span="2">{{
						validityText(reviewCoupon)
					}}</el-descriptions-item>
					<el-descriptions-item label="领取开始">{{
						reviewCoupon.saleStartTime ? dateText(reviewCoupon.saleStartTime) : '不限'
					}}</el-descriptions-item>
					<el-descriptions-item label="领取结束">{{
						reviewCoupon.saleEndTime ? dateText(reviewCoupon.saleEndTime) : '不限'
					}}</el-descriptions-item>
					<el-descriptions-item label="发行总量">{{
						reviewCoupon.issueLimit == null
							? '不限量'
							: numberText(reviewCoupon.issueLimit)
					}}</el-descriptions-item>
					<el-descriptions-item label="每人限领"
						>{{ reviewCoupon.perCustomerLimit }} 张</el-descriptions-item
					>
					<el-descriptions-item label="同享优惠">{{
						reviewCoupon.allowCombine === 1 ? '允许' : '不允许'
					}}</el-descriptions-item>
					<el-descriptions-item label="审核版本"
						>第
						{{ reviewCoupon.publication?.submittedVersion }} 版</el-descriptions-item
					>
					<el-descriptions-item label="使用说明" :span="2">{{
						reviewCoupon.description || '—'
					}}</el-descriptions-item>
					<el-descriptions-item v-if="reviewCoupon.cover" label="封面" :span="2"
						><el-image
							:src="reviewCoupon.cover"
							fit="contain"
							style="width: 120px; height: 90px"
					/></el-descriptions-item>
				</el-descriptions>
				<el-table
					v-if="reviewCoupon.contentItems?.length"
					:data="reviewCoupon.contentItems"
					style="margin-top: 16px"
				>
					<el-table-column prop="name" label="套餐／服务内容" />
					<el-table-column prop="quantity" label="数量" width="80" />
					<el-table-column prop="itemDescription" label="项目说明" />
				</el-table>
			</template>
			<template #footer>
				<el-button @click="reviewVisible = false">取消</el-button>
				<el-button
					type="danger"
					:disabled="!!publicationBusy"
					@click="reviewCoupon && publicationAction(reviewCoupon, 'audit', false)"
					>驳回</el-button
				>
				<el-button
					type="primary"
					:disabled="!!publicationBusy"
					@click="reviewCoupon && publicationAction(reviewCoupon, 'audit', true)"
					>审核通过</el-button
				>
			</template>
		</el-dialog>
	</cl-crud>
</template>

<script lang="ts" setup>
defineOptions({
	name: 'coupon-template'
});

import CoverField from '/$/space/components/cover-field.vue';
import { useCrud, useTable, useUpsert, useSearch } from '@cool-vue/crud';
import { useCool } from '/@/cool';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { h, ref } from 'vue';
import { ElMessage, ElMessageBox, ElTooltip } from 'element-plus';
import CouponCover from '../components/coupon-cover.vue';
import CouponContentEditor from '../components/coupon-content-editor.vue';
import MerchantSelect from '../../merchant/components/merchant-select.vue';

const { service } = useCool();
const { t } = useI18n();

type CouponRow = Eps.CouponTemplateEntity & {
	merchantName?: string;
	storeNames?: string[];
	publication?: {
		auditStatus: number;
		publishStatus: number;
		submittedVersion?: number;
		publishedVersion?: number;
	};
};
const publicationBusy = ref(false);
const reviewVisible = ref(false);
const reviewCoupon = ref<CouponRow>();
function approvedCurrent(row: CouponRow) {
	return (
		row.publication?.auditStatus === 2 && row.publication.submittedVersion === row.ruleVersion
	);
}
function canSubmit(row: CouponRow) {
	return (
		row.status === 1 &&
		row.publication?.publishStatus !== 1 &&
		row.publication?.auditStatus !== 1 &&
		!approvedCurrent(row)
	);
}
function canPublish(row: CouponRow) {
	return row.status === 1 && row.publication?.publishStatus !== 1 && approvedCurrent(row);
}
function auditText(row: CouponRow) {
	if (
		row.publication?.submittedVersion != null &&
		row.publication.submittedVersion !== row.ruleVersion
	)
		return '规则已变更，需重新审核';
	return ['未提交', '待审核', '审核通过', '已驳回'][row.publication?.auditStatus ?? 0] || '未知';
}
async function openReview(row: CouponRow) {
	publicationBusy.value = true;
	try {
		reviewCoupon.value = await service.coupon.template.info({ id: row.id });
		reviewVisible.value = true;
	} catch (err: any) {
		ElMessage.error(err.message || '审核内容加载失败');
	} finally {
		publicationBusy.value = false;
	}
}
async function publicationAction(row: CouponRow, action: string, approved?: boolean) {
	if (publicationBusy.value) return;
	const label =
		action === 'audit'
			? approved
				? '审核通过'
				: '驳回'
			: {
					submitAudit: '提交审核',
					withdrawAudit: '撤回审核',
					publish: '上架',
					unpublish: '下架'
				}[action] || action;
	publicationBusy.value = true;
	try {
		let reason: string | undefined;
		if (action === 'audit' && approved === false) {
			const result = await ElMessageBox.prompt(
				'请填写驳回原因，商家将按此修改后重新提交',
				'审批意见',
				{
					inputType: 'textarea',
					inputValidator: value =>
						(!!value?.trim() && value.trim().length <= 500) || '请填写1至500字的原因'
				}
			);
			reason = result.value.trim();
		}
		await ElMessageBox.confirm(
			`确认${label}“${row.name}”？${action === 'publish' ? '上架后将展示在用户端。' : ''}`,
			'优惠券发布',
			{ type: 'warning' }
		);
		await service.coupon.template.request({
			url: `/${action}`,
			method: 'POST',
			data: {
				id: row.id,
				version: row.ruleVersion,
				...(action === 'audit' ? { approved, reason } : {})
			}
		});
		ElMessage.success(`${label}成功`);
		reviewVisible.value = false;
		Crud.value?.refresh();
	} catch (err: any) {
		if (err !== 'cancel' && err !== 'close')
			ElMessage.error(err.message || `${label}失败，请刷新后重试`);
	} finally {
		publicationBusy.value = false;
	}
}
const typeNames: Record<string, string> = {
	FULL_REDUCTION: '满减券',
	DISCOUNT: '折扣券',
	CASH: '代金券',
	PACKAGE: '套餐券',
	EXPERIENCE: '体验券',
	EXCHANGE: '兑换券'
};
const statusNames: Record<string, string> = { 0: '草稿', 1: '启用', 2: '停用' };
const typeColors: Record<string, string> = {
	FULL_REDUCTION: '#c44f42',
	DISCOUNT: '#3478b8',
	CASH: '#a66b14',
	PACKAGE: '#8056ad',
	EXPERIENCE: '#247f73',
	EXCHANGE: '#b34b7b'
};

function numberText(value: number | string | null | undefined, fixed = false) {
	if (value == null || value === '' || !Number.isFinite(Number(value))) return '—';
	return Number(value).toLocaleString('zh-CN', {
		minimumFractionDigits: fixed ? 2 : 0,
		maximumFractionDigits: 2
	});
}

function dateText(value?: string | Date | null) {
	return value && dayjs(value).isValid() ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—';
}

function coverBadge(row: CouponRow) {
	if (row.type === 'CASH') return `${numberText(row.deductAmount)}元`;
	if (row.type === 'DISCOUNT') return `${numberText(row.discountRate)}折`;
	if (row.type === 'FULL_REDUCTION') return `减${numberText(row.discountAmount)}元`;
	return { PACKAGE: '套餐', EXPERIENCE: '体验', EXCHANGE: '兑换' }[row.type || ''] || '优惠券';
}

function ruleText(row: CouponRow) {
	if (row.type === 'FULL_REDUCTION')
		return `满${numberText(row.minSpend)}元减${numberText(row.discountAmount)}元`;
	if (row.type === 'DISCOUNT') {
		const threshold =
			row.minSpend != null && Number(row.minSpend) > 0
				? `满${numberText(row.minSpend)}元可用，`
				: '';
		const maximum = row.maxDiscount == null ? '' : `，最高优惠${numberText(row.maxDiscount)}元`;
		return `${threshold}${numberText(row.discountRate)}折${maximum}`;
	}
	if (row.type === 'CASH') {
		const threshold =
			row.minSpend != null && Number(row.minSpend) > 0
				? `满${numberText(row.minSpend)}元可用，`
				: '';
		return `${threshold}抵扣${numberText(row.deductAmount)}元`;
	}
	return row.description?.trim() || '内容待配置';
}

function scopeText(row: CouponRow) {
	if (Number(row.scopeType) === 0 && row.scopeType != null) return '商家全部营业门店';
	if (Number(row.scopeType) === 1)
		return row.storeNames?.length ? row.storeNames.join('、') : '指定门店（待配置）';
	return '未设置';
}

function validityText(row: CouponRow) {
	if (row.validityType === 'AFTER_ISSUE') return `发券后${numberText(row.validDays)}天有效`;
	if (row.validityType === 'FIXED')
		return `${dateText(row.validFrom)} 至 ${dateText(row.validTo)}`;
	return '未设置';
}

const storeOptions = ref<Eps.MerchantStoreEntity[]>([]);
const originalStoreIds = ref<number[]>([]);
const storesLoading = ref(false);
const storesFailed = ref(false);
let storeRequest = 0;
async function loadStores(merchantId?: number) {
	const request = ++storeRequest;
	storeOptions.value = [];
	storesFailed.value = false;
	storesLoading.value = !!merchantId;
	if (!merchantId) return;
	try {
		const rows = await service.merchant.store.list({ merchantId });
		if (request === storeRequest) storeOptions.value = rows;
	} catch {
		if (request === storeRequest) {
			storesFailed.value = true;
			ElMessage.error('适用门店加载失败，请重试');
		}
	} finally {
		if (request === storeRequest) storesLoading.value = false;
	}
}
const moneyInput = {
	name: 'el-input-number',
	props: {
		min: 0,
		max: 99999999.99,
		precision: 2,
		controlsPosition: 'right',
		style: { width: '100%' }
	}
};
const dateInput = {
	name: 'el-date-picker',
	props: {
		type: 'datetime',
		valueFormat: 'YYYY-MM-DD HH:mm:ss',
		clearable: true,
		style: { width: '100%' }
	}
};
const contentTypes = ['PACKAGE', 'EXPERIENCE', 'EXCHANGE'];
const Upsert = useUpsert({
	dialog: { width: '860px', height: '65vh' },
	props: { labelPosition: 'top' },
	onOpened(data) {
		if (!data.id) {
			const defaults = {
				type: 'FULL_REDUCTION',
				salePrice: 0,
				scopeType: 0,
				validityType: 'AFTER_ISSUE',
				validDays: 30,
				perCustomerLimit: 1,
				allowCombine: 0,
				status: 0
			};
			for (const [key, value] of Object.entries(defaults)) {
				if (data[key] == null) Upsert.value?.setForm(key, value);
			}
		}
		originalStoreIds.value = [...(data.storeIds || [])];
		void loadStores(data.merchantId);
	},
	onSubmit(data, { next, done }) {
		const fail = (message: string) => {
			ElMessage.warning(message);
			done();
		};
		if (data.scopeType === 1 && (storesFailed.value || storesLoading.value))
			return fail('请等待门店加载完成后再保存');
		if (data.scopeType === 1 && !data.storeIds?.length) return fail('请至少选择一家适用门店');
		if (
			data.type === 'FULL_REDUCTION' &&
			(!(data.minSpend > 0) ||
				!(data.discountAmount > 0) ||
				data.discountAmount > data.minSpend)
		)
			return fail('满减券的门槛和减免金额须大于0，减免金额不能超过门槛');
		if (data.type === 'DISCOUNT' && !(data.discountRate > 0 && data.discountRate < 10))
			return fail('折扣数须大于0且小于10，例如8.8');
		if (data.type === 'DISCOUNT' && data.maxDiscount != null && !(data.maxDiscount > 0))
			return fail('优惠上限须大于0，不限制时请留空');
		if (data.type === 'CASH' && !(data.deductAmount > 0)) return fail('抵扣金额须大于0');
		if (
			data.validityType === 'FIXED' &&
			(!data.validFrom ||
				!data.validTo ||
				!dayjs(data.validTo).isAfter(dayjs(data.validFrom)))
		)
			return fail('请设置完整有效期，结束时间须晚于开始时间');
		if (
			data.saleStartTime &&
			data.saleEndTime &&
			!dayjs(data.saleEndTime).isAfter(dayjs(data.saleStartTime))
		)
			return fail('领取结束时间须晚于开始时间');
		const payload = { ...data };
		payload.contentItems = contentTypes.includes(data.type) ? data.contentItems || [] : [];
		if (contentTypes.includes(data.type) && data.status === 1 && !payload.contentItems.length)
			return fail('请添加至少一个内容项目后再启用');
		if (
			payload.contentItems.some(
				item =>
					(!item.productId && (!item.name?.trim() || !item.type)) ||
					!Number.isInteger(item.quantity) ||
					item.quantity < 1
			)
		)
			return fail('请填写完整的项目名称、类型和正整数数量');
		for (const key of [
			'cover',
			'description',
			'minSpend',
			'discountAmount',
			'deductAmount',
			'maxDiscount',
			'discountRate',
			'issueLimit',
			'saleStartTime',
			'saleEndTime',
			'validFrom',
			'validTo',
			'validDays'
		])
			payload[key] = data[key] ?? null;
		if (data.type !== 'FULL_REDUCTION') payload.discountAmount = null;
		if (data.type !== 'CASH') payload.deductAmount = null;
		if (data.type !== 'DISCOUNT') {
			payload.discountRate = null;
			payload.maxDiscount = null;
		}
		if (contentTypes.includes(data.type)) payload.minSpend = null;
		if (data.validityType === 'AFTER_ISSUE') {
			payload.validFrom = null;
			payload.validTo = null;
		} else payload.validDays = null;
		if (data.scopeType === 0) payload.storeIds = [];
		for (const key of [
			'issuedCount',
			'ruleVersion',
			'merchantName',
			'storeNames',
			'createTime',
			'updateTime'
		])
			delete payload[key];
		next(payload);
	},
	items: [
		{
			type: 'tabs' as const,
			props: {
				labels: [
					{ label: '基本信息', value: 'base' },
					{ label: '优惠与适用规则', value: 'rules' },
					{ label: '领取设置', value: 'issue' }
				]
			}
		},
		{
			label: '所属商家',
			prop: 'merchantId',
			component: { name: 'slot-edit-merchant' },
			span: 12,
			required: true,
			tips: '创建后不能更换所属商家'
		},
		{
			label: '券名称',
			prop: 'name',
			component: {
				name: 'el-input',
				props: { clearable: true, maxlength: 100, placeholder: '例如：周末烧烤满120减20' }
			},
			span: 12,
			required: true
		},
		{
			label: '券类型',
			prop: 'type',
			component: {
				name: 'el-select',
				options: Object.entries(typeNames).map(([value, label]) => ({ value, label }))
			},
			span: 12,
			required: true
		},
		{
			label: '模板状态',
			prop: 'status',
			component: {
				name: 'el-select',
				options: Object.entries(statusNames).map(([value, label]) => ({
					value: Number(value),
					label
				}))
			},
			span: 12,
			required: true
		},
		{
			label: '套餐／服务内容',
			prop: 'contentItems',
			component: { name: 'slot-content-items' },
			span: 24,
			hidden: ({ scope }) => !contentTypes.includes(scope.type)
		},
		{
			label: '券封面',
			prop: 'cover',
			component: { name: 'slot-cover' },
			span: 24
		},
		{
			label: '使用说明',
			prop: 'description',
			component: {
				name: 'el-input',
				props: {
					type: 'textarea',
					rows: 3,
					maxlength: 2000,
					showWordLimit: true,
					placeholder: '填写使用规则、限制或包含内容'
				}
			},
			span: 24
		},
		{
			label: '售价（元）',
			prop: 'salePrice',
			component: moneyInput,
			span: 12,
			required: true,
			tips: '0元表示免费领取'
		},
		{
			label: '最低消费（元）',
			prop: 'minSpend',
			component: moneyInput,
			span: 12,
			hidden: ({ scope }) => contentTypes.includes(scope.type),
			tips: '满减券必填且大于0；折扣券和代金券留空或0元表示无门槛'
		},
		{
			label: '减免金额（元）',
			prop: 'discountAmount',
			component: moneyInput,
			span: 12,
			required: true,
			hidden: ({ scope }) => scope.type !== 'FULL_REDUCTION'
		},
		{
			label: '抵扣金额（元）',
			prop: 'deductAmount',
			component: moneyInput,
			span: 12,
			required: true,
			hidden: ({ scope }) => scope.type !== 'CASH'
		},
		{
			label: '折扣数',
			prop: 'discountRate',
			component: {
				name: 'el-input-number',
				props: {
					min: 0.01,
					max: 9.99,
					precision: 2,
					step: 0.1,
					controlsPosition: 'right',
					style: { width: '100%' }
				}
			},
			span: 12,
			required: true,
			hidden: ({ scope }) => scope.type !== 'DISCOUNT',
			tips: '例如8.8表示8.8折'
		},
		{
			label: '优惠上限（元）',
			prop: 'maxDiscount',
			component: moneyInput,
			span: 12,
			hidden: ({ scope }) => scope.type !== 'DISCOUNT',
			tips: '留空表示不限最高优惠，填写时须大于0'
		},
		{
			label: '适用门店',
			prop: 'scopeType',
			component: {
				name: 'el-select',
				options: [
					{ label: '商家全部营业门店', value: 0 },
					{ label: '指定门店', value: 1 }
				]
			},
			span: 12,
			required: true
		},
		{
			label: '选择门店',
			prop: 'storeIds',
			component: { name: 'slot-stores' },
			span: 24,
			required: true,
			hidden: ({ scope }) => scope.scopeType !== 1
		},
		{
			label: '有效期方式',
			prop: 'validityType',
			component: {
				name: 'el-select',
				options: [
					{ label: '发券后按天有效', value: 'AFTER_ISSUE' },
					{ label: '固定起止日期', value: 'FIXED' }
				]
			},
			span: 12,
			required: true
		},
		{
			label: '有效天数',
			prop: 'validDays',
			component: {
				name: 'el-input-number',
				props: { min: 1, max: 2147483647, precision: 0, controlsPosition: 'right' }
			},
			span: 12,
			required: true,
			hidden: ({ scope }) => scope.validityType !== 'AFTER_ISSUE'
		},
		{
			label: '有效期开始',
			prop: 'validFrom',
			component: dateInput,
			span: 12,
			required: true,
			hidden: ({ scope }) => scope.validityType !== 'FIXED'
		},
		{
			label: '有效期结束',
			prop: 'validTo',
			component: dateInput,
			span: 12,
			required: true,
			hidden: ({ scope }) => scope.validityType !== 'FIXED'
		},
		{
			label: '发行总量',
			prop: 'issueLimit',
			component: { name: 'slot-issue-limit' },
			span: 24
		},
		{
			label: '每人限领（张）',
			prop: 'perCustomerLimit',
			component: {
				name: 'el-input-number',
				props: { min: 1, max: 2147483647, precision: 0, controlsPosition: 'right' }
			},
			span: 12,
			required: true
		},
		{
			label: '同享其他优惠',
			prop: 'allowCombine',
			component: {
				name: 'el-select',
				options: [
					{ label: '不允许', value: 0 },
					{ label: '允许', value: 1 }
				]
			},
			span: 12,
			required: true
		},
		{
			label: '领取开始时间',
			prop: 'saleStartTime',
			component: dateInput,
			span: 12,
			tips: '留空表示不限制开始时间'
		},
		{
			label: '领取结束时间',
			prop: 'saleEndTime',
			component: dateInput,
			span: 12,
			tips: '留空表示不限制结束时间'
		}
	].map(item => ({
		...item,
		group:
			item.type === 'tabs'
				? undefined
				: ['merchantId', 'name', 'type', 'status', 'cover', 'description'].includes(
							item.prop || ''
					  ) || !item.prop
					? 'base'
					: [
								'issueLimit',
								'perCustomerLimit',
								'allowCombine',
								'saleStartTime',
								'saleEndTime'
						  ].includes(item.prop)
						? 'issue'
						: 'rules',
		...(item.tips
			? {
					renderLabel: () =>
						h(
							ElTooltip,
							{ content: item.tips, placement: 'top' },
							{ default: () => h('span', `${item.label} ⓘ`) }
						)
				}
			: {})
	}))
});

// cl-table：仅转换显示内容，保留原始编码和关联标识供接口使用。
const Table = useTable({
	props: { showOverflowTooltip: true },
	columns: [
		{ type: 'selection', fixed: 'left' },
		{
			label: t('券封面'),
			prop: 'cover',
			width: 86,
			fixed: 'left'
		},
		{ label: t('券名称'), prop: 'name', minWidth: 210 },
		{ label: '平台审核', prop: 'publication', minWidth: 180, formatter: auditText },
		{
			label: '平台上架',
			minWidth: 100,
			formatter: (row: CouponRow) =>
				['未上架', '已上架', '已下架'][row.publication?.publishStatus ?? 0] || '未知'
		},
		{
			label: t('所属商家'),
			prop: 'merchantName',
			minWidth: 160,
			formatter: (row: CouponRow) => row.merchantName || '商家资料未加载'
		},
		{
			label: t('券类型'),
			prop: 'type',
			minWidth: 100
		},
		{ label: t('优惠内容'), minWidth: 230, formatter: ruleText },
		{
			label: t('售价'),
			prop: 'salePrice',
			minWidth: 110,
			formatter: (row: CouponRow) =>
				row.salePrice != null && Number(row.salePrice) === 0
					? '免费'
					: row.salePrice == null
						? '—'
						: `${numberText(row.salePrice, true)} 元`
		},
		{ label: t('适用门店'), prop: 'scopeType', minWidth: 220, formatter: scopeText },
		{ label: t('有效期'), prop: 'validityType', minWidth: 280, formatter: validityText },
		{
			label: t('发行总量'),
			prop: 'issueLimit',
			minWidth: 110,
			formatter: (row: CouponRow) =>
				row.issueLimit == null ? '不限量' : `${numberText(row.issueLimit)} 张`
		},
		{
			label: t('已发放'),
			prop: 'issuedCount',
			minWidth: 100,
			formatter: (row: CouponRow) => `${numberText(row.issuedCount)} 张`
		},
		{
			label: t('每人限领'),
			prop: 'perCustomerLimit',
			minWidth: 100,
			formatter: (row: CouponRow) => `${numberText(row.perCustomerLimit)} 张`
		},
		{
			label: t('同享其他优惠'),
			prop: 'allowCombine',
			minWidth: 120,
			formatter: (row: CouponRow) =>
				row.allowCombine == null
					? '未设置'
					: { 0: '不允许', 1: '允许' }[Number(row.allowCombine)] || '未知'
		},
		{
			label: t('状态'),
			prop: 'status',
			minWidth: 90,
			formatter: (row: CouponRow) => statusNames[String(row.status)] || '未知状态'
		},
		{
			label: t('使用说明'),
			prop: 'description',
			minWidth: 220,
			formatter: (row: CouponRow) => row.description || '—'
		},
		{
			label: t('领取购买开始'),
			prop: 'saleStartTime',
			minWidth: 170,
			sortable: 'custom',
			formatter: (row: CouponRow) =>
				row.saleStartTime ? dateText(row.saleStartTime) : '不限开始时间'
		},
		{
			label: t('领取购买结束'),
			prop: 'saleEndTime',
			minWidth: 170,
			sortable: 'custom',
			formatter: (row: CouponRow) =>
				row.saleEndTime ? dateText(row.saleEndTime) : '不限结束时间'
		},
		{
			label: t('创建时间'),
			prop: 'createTime',
			minWidth: 170,
			sortable: 'desc',
			formatter: (row: CouponRow) => dateText(row.createTime)
		},
		{
			label: t('更新时间'),
			prop: 'updateTime',
			minWidth: 170,
			sortable: 'custom',
			formatter: (row: CouponRow) => dateText(row.updateTime)
		},
		{ label: '发布操作', prop: 'publicationActions', fixed: 'right', width: 180 },
		{ type: 'op', width: 140, buttons: ['edit', 'delete'] }
	]
});

// cl-search
const Search = useSearch({
	items: [
		{
			label: t('券名称'),
			prop: 'keyWord',
			component: {
				name: 'el-input',
				props: { clearable: true, maxlength: 100, placeholder: '输入券名称搜索' },
				style: { width: '190px' }
			}
		},
		{
			label: t('所属商家'),
			prop: 'merchantId',
			component: { name: 'slot-search-merchant' }
		},
		{
			label: t('券类型'),
			prop: 'type',
			component: {
				name: 'el-select',
				props: { clearable: true, placeholder: '全部类型' },
				options: Object.entries(typeNames).map(([value, label]) => ({
					label: t(label),
					value
				})),
				style: { width: '140px' }
			}
		},
		{
			label: t('模板状态'),
			prop: 'status',
			component: {
				name: 'el-select',
				props: { clearable: true, placeholder: '全部状态' },
				options: Object.entries(statusNames).map(([value, label]) => ({
					label: t(label),
					value: Number(value)
				})),
				style: { width: '140px' }
			}
		}
	],
	onSearch(data, { next }) {
		next({
			// 空条件不发送给后端，避免框架将显式null解析为字段为空的查询。
			keyWord: data.keyWord?.trim() || undefined,
			merchantId: data.merchantId ?? undefined,
			type: data.type || undefined,
			status: data.status ?? undefined
		});
	}
});

// cl-crud
const Crud = useCrud(
	{
		service: service.coupon.template
	},
	app => {
		app.refresh();
	}
);
</script>

<style scoped>
.coupon-type-tag {
	border: none;
	color: #fff;
	white-space: nowrap;
}
</style>
