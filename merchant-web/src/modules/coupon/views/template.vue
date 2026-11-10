<template>
	<AssistantLayout
		:open="assistantOpen"
		:merchant-id="assistantMerchant"
		:coupon-id="assistantCoupon"
		:selected-ids="assistantSelection"
		@close="assistantOpen = false"
		@saved="Crud?.refresh()"
		@edit="(coupon, step) => { prepareCouponStep(step); Crud?.rowEdit(coupon); }"
	>
		<section class="coupon-workspace">
			<cl-crud ref="Crud">
				<StoreDirectoryHeader
					class="coupon-toolbar"
					title="优惠券管理"
					scope-label="券面与发放"
					scope-name="发布审核"
				>
					<template #actions>
						<div class="coupon-tools">
							<cl-refresh-btn />
							<cl-multi-delete-btn />
							<cl-add-btn class="store-create" />
							<AssistantEntry :active="assistantOpen" @click="assistantOpen = !assistantOpen" />
						</div>
					</template>
				</StoreDirectoryHeader>
				<form class="coupon-filters" aria-label="优惠券筛选" @submit.prevent="applyFilters">
					<el-input
						v-model="filters.keyWord"
						aria-label="券名称"
						placeholder="搜索券名称"
						clearable
						class="filter-keyword"
					/>
					<MerchantSelect
						v-model="filters.merchantId"
						class="filter-merchant"
						placeholder="全部商家"
						aria-label="所属商家"
					/>
					<div class="filter-secondary">
						<el-select
							v-model="filters.type"
							aria-label="券类型"
							placeholder="全部券类型"
							clearable
							><el-option
								v-for="(label, value) in typeNames"
								:key="value"
								:label="label"
								:value="value"
						/></el-select>
						<el-select
							v-model="filters.status"
							aria-label="模板状态"
							placeholder="全部模板状态"
							clearable
							><el-option
								v-for="(label, value) in statusNames"
								:key="value"
								:label="label"
								:value="Number(value)"
						/></el-select>
					</div>
					<el-popover trigger="click" width="260" placement="bottom-end">
						<template #reference
							><el-button class="filter-more"
								>更多筛选{{
									filters.type || filters.status !== undefined ? ' · 已设置' : ''
								}}</el-button
							></template
						>
						<div class="filter-popover">
							<label>券类型</label
							><el-select v-model="filters.type" placeholder="全部券类型" clearable
								><el-option
									v-for="(label, value) in typeNames"
									:key="value"
									:label="label"
									:value="value" /></el-select
							><label>模板状态</label
							><el-select
								v-model="filters.status"
								placeholder="全部模板状态"
								clearable
								><el-option
									v-for="(label, value) in statusNames"
									:key="value"
									:label="label"
									:value="Number(value)"
							/></el-select>
						</div>
					</el-popover>
					<el-button @click="resetFilters">重置</el-button
					><el-button type="primary" native-type="submit">搜索</el-button>
				</form>

				<cl-row class="coupon-table-row">
					<!-- 数据表格 -->
					<cl-table :border="false" ref="Table" @selection-change="setAssistantSelection">
						<template #column-name="{ scope }">
							<div class="coupon-identity">
								<CouponCover
									:src="scope.row.cover"
									:name="scope.row.name"
									:badge="coverBadge(scope.row)"
									:type-name="typeNames[scope.row.type]"
									:subtitle="ruleText(scope.row)"
								/>
								<div class="coupon-identity-copy">
									<button class="coupon-name" @click="openDetails(scope.row)">
										{{ scope.row.name }}
									</button>
									<div class="coupon-meta">
										<span>{{ typeNames[scope.row.type] || '优惠券' }}</span
										><span v-if="scope.row.status !== 1">{{
											statusNames[String(scope.row.status)]
										}}</span>
									</div>
								</div>
							</div>
						</template>
						<template #column-publication="{ scope }"
							><div class="coupon-status">
								<el-tag
									size="small"
									:type="
										scope.row.publication?.publishStatus === 1
											? 'success'
											: 'info'
									"
									>{{
										['未上架', '已上架', '已下架'][
											scope.row.publication?.publishStatus ?? 0
										]
									}}</el-tag
								><span>{{ auditText(scope.row) }}</span>
							</div></template
						>
						<template #column-salePrice="{ scope }"
							><strong class="coupon-price">{{
								scope.row.salePrice == null
									? '—'
									: Number(scope.row.salePrice) === 0
										? '免费领取'
										: '¥' + numberText(scope.row.salePrice, true)
							}}</strong></template
						>
						<template #column-rules="{ scope }"
							><div class="coupon-rule">{{ ruleText(scope.row) }}</div>
							<div class="coupon-secondary">{{ scopeText(scope.row) }}</div></template
						>
						<template #column-stock="{ scope }"
							><div>
								{{
									scope.row.issueLimit == null
										? '不限量'
										: Math.max(
												0,
												Number(scope.row.issueLimit) -
													Number(scope.row.issuedCount || 0)
											) + ' 张'
								}}
							</div>
							<div class="coupon-secondary">
								已发放 {{ numberText(scope.row.issuedCount) }} 张
							</div></template
						>
						<template #slot-coupon-actions="{ scope }">
							<div class="coupon-row-actions">
								<el-tooltip content="编辑优惠券"
									><el-button
										v-permission="service.coupon.template.permission.update"
										:icon="Edit"
										circle
										plain
										size="small"
										type="primary"
										aria-label="编辑优惠券"
										@click="Crud?.rowEdit(scope.row)" /></el-tooltip
								><el-tooltip content="删除优惠券"
									><el-button
										v-permission="service.coupon.template.permission.delete"
										:icon="Delete"
										circle
										plain
										size="small"
										type="danger"
										aria-label="删除优惠券"
										@click="Crud?.rowDelete(scope.row)"
								/></el-tooltip>
							</div>
						</template>
						<template #column-publicationActions="{ scope }">
							<div
								class="publication-actions"
								v-permission="service.coupon.template.permission.update"
							>
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
										>撤回审核</el-button
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

				<el-drawer
					v-model="detailVisible"
					title="优惠券资料"
					size="min(640px, 100vw)"
					append-to-body
					@closed="
						detailRequest++;
						detailLoading = false;
					"
				>
					<el-skeleton v-if="detailLoading" :rows="8" animated />
					<el-result
						v-else-if="detailError"
						icon="warning"
						title="暂时无法加载"
						:sub-title="detailError"
						><template #extra
							><el-button @click="detailTarget && openDetails(detailTarget)"
								>重新加载</el-button
							></template
						></el-result
					>
					<template v-else-if="detailCoupon">
						<h2 class="detail-title">{{ detailCoupon.name }}</h2>
						<el-descriptions :column="1" border>
							<el-descriptions-item label="所属商家">{{
								detailCoupon.merchantName || '—'
							}}</el-descriptions-item>
							<el-descriptions-item label="券类型">{{
								typeNames[detailCoupon.type || '']
							}}</el-descriptions-item>
							<el-descriptions-item label="模板状态">{{
								statusNames[String(detailCoupon.status)]
							}}</el-descriptions-item>
							<el-descriptions-item label="平台审核">{{
								auditText(detailCoupon)
							}}</el-descriptions-item>
							<el-descriptions-item label="优惠规则">{{
								ruleText(detailCoupon)
							}}</el-descriptions-item>
							<el-descriptions-item label="适用门店">{{
								scopeText(detailCoupon)
							}}</el-descriptions-item>
							<el-descriptions-item label="有效期">{{
								validityText(detailCoupon)
							}}</el-descriptions-item>
							<el-descriptions-item label="发行总量">{{
								detailCoupon.issueLimit == null
									? '不限量'
									: numberText(detailCoupon.issueLimit) + ' 张'
							}}</el-descriptions-item>
							<el-descriptions-item label="已发放">{{
								numberText(detailCoupon.issuedCount) + ' 张'
							}}</el-descriptions-item>
							<el-descriptions-item label="每人限领">{{
								detailCoupon.perCustomerLimit + ' 张'
							}}</el-descriptions-item>
							<el-descriptions-item label="同享优惠">{{
								detailCoupon.allowCombine === 1 ? '允许' : '不允许'
							}}</el-descriptions-item>
							<el-descriptions-item label="领取开始">{{
								detailCoupon.saleStartTime
									? dateText(detailCoupon.saleStartTime)
									: '不限'
							}}</el-descriptions-item>
							<el-descriptions-item label="领取结束">{{
								detailCoupon.saleEndTime
									? dateText(detailCoupon.saleEndTime)
									: '不限'
							}}</el-descriptions-item>
							<el-descriptions-item label="使用说明">{{
								detailCoupon.description || '—'
							}}</el-descriptions-item>
							<el-descriptions-item label="创建时间">{{
								dateText(detailCoupon.createTime)
							}}</el-descriptions-item>
							<el-descriptions-item label="更新时间">{{
								dateText(detailCoupon.updateTime)
							}}</el-descriptions-item>
						</el-descriptions>
						<el-table
							v-if="detailCoupon.contentItems?.length"
							:data="detailCoupon.contentItems"
							><el-table-column prop="name" label="套餐／服务内容" /><el-table-column
								prop="quantity"
								label="数量"
								width="80" /><el-table-column
								prop="itemDescription"
								label="项目说明"
						/></el-table>
					</template>
					<template #footer
						><el-button @click="detailVisible = false">关闭</el-button></template
					>
				</el-drawer>
				<!-- 新增、编辑 -->
				<cl-upsert ref="Upsert">
                    <template #prepend="{ scope }">
                        <CouponEditorHeading :draft="scope" :type-name="typeNames[scope.type]" :step="couponStep" @update:step="goToCouponStep" />
                    </template>
                    <template #append>
                        <CouponEditorNavigation :step="couponStep" @update:step="goToCouponStep" />
                    </template>
					<template #slot-cover="{ scope }"
						><CoverField
							v-model="scope.cover"
							v-model:material-id="scope.coverMaterialId"
							:ai-draft="scope"
					/></template>
					<template #slot-edit-merchant="{ scope }">
						<el-input v-if="scope.id" :model-value="scope.merchantName" readonly />
						<MerchantSelect
							v-else
							v-model="scope.merchantId"
							@selected="merchant => scope.merchantName = merchant?.name"
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
									Number(store.status) !== 1 &&
									!originalStoreIds.includes(store.id!)
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
						<CouponContentEditor
							v-model="scope.contentItems"
							:merchant-id="scope.merchantId"
						/>
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
								>{{
									numberText(reviewCoupon.salePrice, true)
								}}
								元</el-descriptions-item
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
								reviewCoupon.saleStartTime
									? dateText(reviewCoupon.saleStartTime)
									: '不限'
							}}</el-descriptions-item>
							<el-descriptions-item label="领取结束">{{
								reviewCoupon.saleEndTime
									? dateText(reviewCoupon.saleEndTime)
									: '不限'
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
								{{ reviewCoupon.publication?.submittedVersion }}
								版</el-descriptions-item
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
		</section>
	</AssistantLayout>
</template>

<script lang="ts" setup>
import CouponEditorHeading from '/@/modules/coupon/components/coupon-editor-heading.vue';
import CouponEditorNavigation from '/@/modules/coupon/components/coupon-editor-navigation.vue';
import { couponFieldStep, useCouponEditorFlow } from '/@/modules/coupon/components/coupon-editor-flow';
const { couponStep, goToCouponStep, prepareCouponStep, resetCouponStep } = useCouponEditorFlow();
import AssistantLayout from '/$/ai/components/assistant-layout.vue';
import AssistantEntry from '/$/ai/components/assistant-entry.vue';
const assistantOpen = ref(false);
const assistantMerchant = ref<number>();
const assistantCoupon = ref<number>();
const assistantSelection = ref<number[]>([]);
function setAssistantSelection(rows: { id: number }[]) {
	assistantSelection.value = rows.map(row => row.id);
	assistantCoupon.value = rows.length === 1 ? rows[0].id : undefined;
}
defineOptions({
	name: 'coupon-template'
});

import CoverField from '/$/space/components/cover-field.vue';
import { useCrud, useTable, useUpsert } from '@cool-vue/crud';
import { useCool } from '/@/cool';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { h, ref, reactive } from 'vue';
import { Edit, Delete } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox, ElTooltip } from 'element-plus';
import CouponCover from '../components/coupon-cover.vue';
import CouponContentEditor from '../components/coupon-content-editor.vue';
import MerchantSelect from '../../merchant/components/merchant-select.vue';
import StoreDirectoryHeader from '../../merchant/components/store-directory-header.vue';

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
	dialog: { width: 'min(1120px, 96vw)', height: 'min(720px, 76dvh)', controls: ['close'] },
	props: { labelPosition: 'top', onValidate(prop: string, valid: boolean) { if (!valid) goToCouponStep(couponFieldStep(prop)); } },
	onOpened(data) {
		resetCouponStep();
		assistantOpen.value = false;
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
	items: [{ span: 24, props: { class: 'coupon-editor-fields' }, component: { name: 'el-col', props: { span: 24 } }, children: [
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
		{ prop: 'coverMaterialId', hidden: true },
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
		props: { class: `coupon-fields coupon-fields--${couponFieldStep(item.prop)}` },
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
	})) }]
});

const filters = reactive<{ keyWord: string; merchantId?: number; type?: string; status?: number }>({
	keyWord: ''
});
function applyFilters() {
	assistantMerchant.value = filters.merchantId ?? undefined;
	Crud.value?.refresh({
		page: 1,
		keyWord: filters.keyWord.trim() || undefined,
		merchantId: filters.merchantId ?? undefined,
		type: filters.type || undefined,
		status: typeof filters.status === 'number' ? filters.status : undefined
	});
}
function resetFilters() {
	Object.assign(filters, {
		keyWord: '',
		merchantId: undefined,
		type: undefined,
		status: undefined
	});
	applyFilters();
}
const detailVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref('');
const detailCoupon = ref<CouponRow>();
let detailRequest = 0;
let detailTarget: CouponRow | undefined;
async function openDetails(row: CouponRow) {
	detailTarget = row;
	const request = ++detailRequest;
	detailVisible.value = true;
	detailLoading.value = true;
	detailError.value = '';
	detailCoupon.value = undefined;
	try {
		const data = await service.coupon.template.info({ id: row.id });
		if (request === detailRequest) detailCoupon.value = data;
	} catch (error: any) {
		if (request === detailRequest) detailError.value = error.message || '资料加载失败，请重试';
	} finally {
		if (request === detailRequest) detailLoading.value = false;
	}
}
// 聚合相关信息，详细规则保留在只读资料抽屉中。
const Table = useTable({
	props: { showOverflowTooltip: true, border: false },
	columns: [
		{ type: 'selection', width: 44 },
		{ label: '优惠券', prop: 'name', minWidth: 250, align: 'left' },
		{ label: '所属商家', prop: 'merchantName', minWidth: 140, align: 'left' },
		{ label: '优惠与适用范围', prop: 'rules', minWidth: 210, align: 'left' },
		{ label: '售价', prop: 'salePrice', minWidth: 100, align: 'right' },
		{ label: '剩余库存', prop: 'stock', minWidth: 110, align: 'right' },
		{ label: '发布状态', prop: 'publication', minWidth: 170, align: 'left' },
		{
			label: '发布操作',
			prop: 'publicationActions',
			fixed: 'right',
			width: 170,
			showOverflowTooltip: false
		},
		{
			type: 'op',
			width: 96,
			showOverflowTooltip: false,
			buttons: [{ name: 'slot-coupon-actions' }]
		}
	]
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
.coupon-workspace {
	height: 100%;
	min-height: 0;
	padding: 0;
	box-sizing: border-box;
	container-type: inline-size;
	color: var(--el-text-color-primary);
}
.coupon-workspace > :deep(.cl-crud) {
	height: 100%;
	min-height: 0;
	display: flex;
	flex-direction: column;
	padding: 0 !important;
	overflow: hidden;
	background: transparent;
}
.coupon-toolbar {
	flex: none;
	margin-bottom: 16px;
}
.coupon-tools {
	display: flex;
	align-items: center;
	gap: 8px;
	flex-shrink: 0;
}
.coupon-tools :deep(.el-button) {
	margin: 0;
}
.coupon-tools :deep(.el-button:not(.store-create):not(.is-disabled)) {
	--el-button-bg-color: transparent;
	--el-button-border-color: #ffffff70;
	--el-button-text-color: #ffffff;
	--el-button-hover-bg-color: #ffffff20;
	--el-button-hover-border-color: #ffffffb0;
	--el-button-hover-text-color: #ffffff;
}
.coupon-tools :deep(.el-button[aria-pressed='true']) {
	--el-button-bg-color: #ffffff26;
	box-shadow: inset 0 -2px #ffcf55;
}
.coupon-filters {
	display: flex;
	flex-wrap: nowrap;
	align-items: center;
	gap: 8px;
	flex: none;
	padding: 12px;
	background: var(--el-bg-color);
	border-bottom: 1px solid var(--el-border-color-lighter);
}
.filter-keyword {
	flex: 1;
	min-width: 100px;
	max-width: 300px;
}
.filter-merchant {
	width: 190px !important;
	flex: 0 1 190px;
	min-width: 120px;
}
.filter-secondary {
	display: flex;
	gap: 8px;
	flex: 0 1 288px;
	min-width: 240px;
}
.filter-secondary .el-select {
	min-width: 0;
	flex: 1;
}
.coupon-filters > .el-button {
	margin: 0;
	flex-shrink: 0;
}
.coupon-filters .filter-more {
	display: none;
}
.filter-popover {
	display: grid;
	gap: 8px;
}
.filter-popover label {
	margin-top: 4px;
	font-size: 13px;
	color: var(--el-text-color-regular);
}
.coupon-table-row {
	flex: 1 !important;
	min-height: 0;
	overflow: hidden;
	margin: 0 !important;
}
.coupon-table-row :deep(.cl-table) {
	height: 100%;
}
.coupon-workspace :deep(.el-table th.el-table__cell) {
	background: var(--workspace-selected, #eaf0ff);
	color: var(--el-text-color-primary);
	font-weight: 600;
}
.coupon-workspace :deep(.el-table td.el-table__cell) {
	padding-block: 8px;
}
.coupon-workspace :deep(.cl-row--last) {
	flex: none;
	padding: 12px;
	margin: 0;
	background: var(--el-bg-color);
}
.coupon-identity {
	display: flex;
	gap: 12px;
	align-items: center;
	min-width: 0;
}
.coupon-identity :deep(.coupon-cover) {
	width: 48px;
	height: 56px;
	flex: none;
}
.coupon-identity :deep(.cover-badge) {
	display: none;
}
.coupon-identity-copy {
	min-width: 0;
}
.coupon-name {
	border: 0;
	padding: 0;
	background: none;
	text-align: left;
	font: inherit;
	font-weight: 650;
	color: var(--el-text-color-primary);
	white-space: normal;
	cursor: pointer;
}
.coupon-name:hover {
	color: var(--el-color-primary);
}
.coupon-name:focus-visible {
	outline: 2px solid var(--el-color-primary);
	outline-offset: 3px;
}
.coupon-meta {
	display: flex;
	gap: 8px;
	font-size: 12px;
	color: var(--el-text-color-regular);
	margin-top: 4px;
}
.coupon-meta span:first-child {
	color: var(--el-color-primary);
}
.coupon-status {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	gap: 5px;
	white-space: normal;
	font-size: 12px;
	line-height: 1.5;
}
.coupon-secondary {
	font-size: 12px;
	color: var(--el-text-color-regular);
	overflow: hidden;
	text-overflow: ellipsis;
	margin-top: 4px;
}
.coupon-price {
	font-variant-numeric: tabular-nums;
	font-weight: 650;
}
.publication-actions {
	display: flex;
	flex-wrap: wrap;
	justify-content: center;
	gap: 8px;
}
.publication-actions :deep(.el-button) {
	margin: 0;
}
/* 操作单元格不能继承文本省略容器的固定高度。 */
.coupon-workspace :deep(td .cell:has(.coupon-row-actions)) {
    height: auto !important;
    min-height: 28px;
    overflow: visible;
    display: flex;
    align-items: center;
    justify-content: center;
}
.coupon-row-actions {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 8px;
}
.coupon-row-actions :deep(.el-button) {
	margin: 0;
}
.detail-title {
	margin: 0 0 20px;
	font-size: 22px;
	overflow-wrap: anywhere;
}
@container (max-width: 920px) {
	.filter-secondary {
		display: none;
	}
	.coupon-filters .filter-more {
		display: inline-flex;
	}
}
@container (max-width: 600px) {
	.coupon-tools {
		gap: 4px;
	}
	.coupon-tools :deep(.el-button) {
		padding-inline: 8px;
	}
	.coupon-toolbar :deep(.directory-title) {
		padding: 16px;
	}
	.coupon-toolbar :deep(.directory-title h1) {
		font-size: 24px;
	}
	.coupon-filters {
		gap: 6px;
		padding-inline: 8px;
		overflow-x: auto;
	}
	.coupon-filters .el-button {
		padding-inline: 10px;
	}
}
</style>
