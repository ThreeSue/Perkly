<template>
	<AssistantLayout :open="kind === 'coupon' && assistantOpen" :coupon-id="assistantCoupon" :selected-ids="assistantSelection" @close="assistantOpen = false" @saved="Crud?.refresh()" @edit="(coupon, step) => { prepareCouponStep(step); Crud?.rowEdit(coupon); }">
	<div style="height: 100%" :class="{ 'coupon-workspace': kind === 'coupon', 'business-stores': kind === 'store' }">
		<cl-crud ref="Crud" :padding="kind === 'store' ? '0' : undefined">
			<StoreDirectoryHeader v-if="kind === 'store'" :scope-label="identity === 'STORE' ? '当前门店' : '当前商家'" :scope-name="storeScopeName">
				<template v-if="can('store:edit') && owner" #actions>
					<cl-add-btn class="store-create">新增门店</cl-add-btn>
				</template>
			</StoreDirectoryHeader>
			<cl-row class="business-actions"
				><cl-refresh-btn /><cl-add-btn
					v-if="
						kind === 'coupon' && can('coupon:create')
					" /><cl-multi-delete-btn
					v-if="
						can(kind === 'store' ? 'store:delete' : 'coupon:delete')
					" /><cl-flex1 />
				<cl-search v-if="kind === 'coupon'" ref="Search" class="coupon-toolbar-search" reset-btn />
				<cl-search v-if="kind === 'store'" ref="Search" class="store-toolbar-search" reset-btn />
				<AssistantEntry v-if="kind === 'coupon'" :active="assistantOpen" @click="assistantOpen = !assistantOpen" />
			</cl-row>
			<cl-row class="business-table">
				<cl-table ref="Table" @selection-change="setAssistantSelection">
					<template #column-cover="{ scope }">
						<CouponCover
							v-if="kind === 'coupon'"
							:src="scope.row.cover"
							:name="scope.row.name"
							:badge="coverBadge(scope.row)"
							:type-name="typeNames[scope.row.type]"
							:subtitle="ruleText(scope.row)"
						/>
						<el-image
							v-else-if="scope.row.cover"
							:src="scope.row.cover"
							:preview-src-list="[scope.row.cover]"
							preview-teleported
							fit="cover"
							style="width: 64px; height: 44px; border-radius: 6px"
							><template #error><CoverPlaceholder /></template></el-image
						><CoverPlaceholder v-else />
					</template>
					<template #column-name="{ scope }">
						<div v-if="kind === 'coupon'" class="coupon-identity">
							<CouponCover :src="scope.row.cover" :name="scope.row.name"
								:badge="coverBadge(scope.row)" :type-name="typeNames[scope.row.type]" />
							<div>
								<strong>{{ scope.row.name }}</strong>
								<div class="coupon-labels">
									<small :data-coupon-type="scope.row.type">{{ typeNames[scope.row.type] || '优惠券' }}</small>
									<small v-if="scope.row.status === 0 || scope.row.status === 2" class="coupon-label-muted">
										{{ scope.row.status === 0 ? '草稿' : '停用' }}
									</small>
								</div>
							</div>
						</div>
						<div v-else class="store-identity">
							<el-image v-if="scope.row.cover" :src="scope.row.cover" :preview-src-list="[scope.row.cover]" preview-teleported fit="cover" class="store-cover">
								<template #error><CoverPlaceholder /></template>
							</el-image>
							<div>
								<strong>{{ scope.row.name }}</strong>
								<small :title="scope.row.address">{{ scope.row.categoryName || '门店' }} · {{ scope.row.address || '未填写地址' }}</small>
							</div>
						</div>
					</template>
					<template #column-storeStatus="{ scope }">
						<el-tag size="small" class="store-status" :type="scope.row.status === 1 ? 'success' : scope.row.status === 2 ? 'warning' : scope.row.status === 0 ? 'danger' : 'info'" effect="light" round>
							<i aria-hidden="true" />
							{{ ['禁用', '营业', '停业', '关闭'][scope.row.status] || '未知' }}
						</el-tag>
					</template>
					<template #column-storeContact="{ scope }">
						<div class="store-contact">
							<span>{{ scope.row.phone || '—' }}</span>
							<small>{{ scope.row.businessHours || '未填写营业时间' }}</small>
						</div>
					</template>
					<template #column-storeActions="{ scope }">
						<div class="store-actions">
							<el-button v-if="can('store:edit')" link type="primary" @click="Crud?.rowEdit(scope.row)">编辑</el-button>
							<el-dropdown v-if="can('store:edit') || can('account:manage') || can('store:delete')" trigger="click" @command="command => storeAction(command, scope.row)">
								<el-button link>更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
								<template #dropdown>
									<el-dropdown-menu>
										<el-dropdown-item v-if="can('store:edit')" command="location">门店位置</el-dropdown-item>
										<el-dropdown-item v-if="can('account:manage')" command="accounts">账号管理</el-dropdown-item>
										<el-dropdown-item v-if="can('store:delete')" command="delete">删除</el-dropdown-item>
									</el-dropdown-menu>
								</template>
							</el-dropdown>
						</div>
					</template>
					<template #column-couponApproval="{ scope }">
						<div class="coupon-release">
							<span class="coupon-release-pill" :data-publish-status="scope.row.publication?.publishStatus">
								{{ ['未上架', '已上架', '已下架'][scope.row.publication?.publishStatus ?? 0] ?? '—' }}
							</span>
							<el-button link type="primary" title="查看审批流程" @click.stop="openCouponApproval(scope.row)">
								{{ couponAuditLabel(scope.row) }} <span aria-hidden="true">↗</span>
							</el-button>
						</div>
					</template>
					<template #column-salePrice="{ scope }">
						<span class="coupon-price-value" :class="{ free: Number(scope.row.salePrice) === 0 }">
							{{ Number(scope.row.salePrice) === 0 ? '免费领取' : `¥${money(scope.row.salePrice)}` }}
						</span>
					</template>
					<template #column-couponStock="{ scope }">
						<span v-if="scope.row.issueLimit == null" class="coupon-unlimited">不限量</span>
						<div v-else class="coupon-stock">
							<span><strong>{{ remainingCouponStock(scope.row) }}</strong> / {{ scope.row.issueLimit }} 张</span>
							<div class="coupon-stock-track" aria-hidden="true">
								<span :style="{ width: Math.min(100, remainingCouponStock(scope.row) / Math.max(1, scope.row.issueLimit) * 100) + '%' }" />
							</div>
						</div>
					</template>
					<template #slot-coupon-actions="{ scope }">
						<div class="coupon-row-actions">
							<el-tooltip v-if="can('coupon:update') && Crud?.getPermission('update')" content="编辑">
								<el-button :icon="Edit" circle size="small" type="primary" plain
									aria-label="编辑优惠券" @click.stop="Crud?.rowEdit(scope.row)" />
							</el-tooltip>
							<el-tooltip v-if="can('coupon:delete') && Crud?.getPermission('delete')" content="删除">
								<el-button :icon="Delete" circle size="small" type="danger" plain
									aria-label="删除优惠券" @click.stop="Crud?.rowDelete(scope.row)" />
							</el-tooltip>
						</div>
					</template>
					<template #column-publicationActions="{ scope }">
						<div class="coupon-publication-actions">
						<el-button
							v-if="
								can('coupon:submitAudit') &&
								scope.row.status === 1 &&
								scope.row.publication?.auditStatus !== 1 &&
								scope.row.publication?.publishStatus !== 1 &&
								!approvedCurrentCouponVersion(scope.row)
							"
							plain
							size="small"
							type="primary"
							:disabled="publicationBusy"
							@click="publication(scope.row, 'submitAudit')"
							>提交审核</el-button
						>
						<el-button
							v-if="can('coupon:withdrawAudit') && scope.row.publication?.auditStatus === 1"
							plain
							size="small"
							:disabled="publicationBusy"
							@click="publication(scope.row, 'withdrawAudit')"
							>撤回审核</el-button
						>
						<el-button
							v-if="
								can('coupon:publish') &&
								scope.row.status === 1 &&
								approvedCurrentCouponVersion(scope.row) &&
								scope.row.publication?.publishStatus !== 1
							"
							plain
							size="small"
							type="success"
							:disabled="publicationBusy"
							@click="publication(scope.row, 'publish')"
							>上架</el-button
						>
						<el-button
							v-if="can('coupon:unpublish') && scope.row.publication?.publishStatus === 1"
							plain
							size="small"
							type="danger"
							:disabled="publicationBusy"
							@click="publication(scope.row, 'unpublish')"
							>下架</el-button
					>
						</div>
					</template>
				</cl-table>
			</cl-row>
			<cl-row class="business-pagination"><cl-flex1 /><cl-pagination /></cl-row>
			<cl-upsert ref="Upsert">
				<template v-if="kind === 'coupon'" #prepend="{ scope }">
					<CouponEditorHeading :draft="scope" :type-name="typeNames[scope.type]" :step="couponStep" @update:step="goToCouponStep" />
				</template>
				<template #slot-price="{ scope }">
					<div class="coupon-price">
						<el-radio-group aria-label="领取方式" :model-value="paid" @change="value => { paid = !!value; scope.salePrice = paid ? undefined : 0 }">
							<el-radio-button :value="false">免费领取</el-radio-button>
							<el-radio-button :value="true">付费购买</el-radio-button>
						</el-radio-group>
						<label v-if="paid" class="coupon-sale-price">
							<span>售价（元）</span>
							<el-input-number v-model="scope.salePrice" aria-label="售价（元）"
								:min="0.01" :precision="2" :controls="false" placeholder="请输入售价" />
						</label>
					</div>
				</template>
				<template v-if="kind === 'coupon'" #append>
					<CouponEditorNavigation :step="couponStep" @update:step="goToCouponStep" />
				</template>
				<template #slot-valid-days="{ scope }">
					<div class="coupon-number-field">
					<el-input-number v-model="scope.validDays" :min="1" :precision="0" controls-position="right" />
					<div class="coupon-presets">
						<el-button v-for="days in [7, 15, 30, 90]" :key="days" size="small"
							:type="scope.validDays === days ? 'primary' : 'default'" plain
							@click="scope.validDays = days">{{ days }} 天</el-button>
					</div>
					</div>
				</template>
				<template #slot-min-spend="{ scope }">
					<div class="coupon-number-field">
						<el-input-number
							:model-value="scope.minSpend || undefined"
							:min="scope.type === 'FULL_REDUCTION' ? 0.01 : 0" :precision="2"
							controls-position="right"
							:placeholder="scope.type === 'FULL_REDUCTION' ? '请输入消费门槛' : '无门槛'"
							@update:model-value="value => scope.minSpend = value ?? 0" />
						<div v-if="scope.type !== 'FULL_REDUCTION'" class="coupon-presets">
							<el-button size="small" plain :type="scope.minSpend > 0 ? 'default' : 'primary'"
								@click="scope.minSpend = 0">无门槛</el-button>
						</div>
					</div>
				</template>
				<template #slot-discount-rate="{ scope }">
					<div class="coupon-number-field">
						<el-input-number v-model="scope.discountRate" :min="0.01" :max="9.99"
							:precision="2" :step="0.1" controls-position="right" placeholder="例如 8.5" />
						<div class="coupon-presets">
							<el-button v-for="rate in [5, 7, 8, 8.5, 9, 9.5]" :key="rate" size="small" plain
								:type="Number(scope.discountRate) === rate ? 'primary' : 'default'"
								@click="scope.discountRate = rate">{{ rate }} 折</el-button>
						</div>
					</div>
				</template>

				<template #slot-cover="{ scope }"
					><MerchantImageUpload
						:key="uploadEpoch"
						v-model="scope.cover"
						v-model:material-id="scope.coverMaterialId"
						:can-browse="can('material:view')"
						:can-manage="can('material:manage')"
						:ai-draft="kind === 'coupon' ? scope : undefined"
						@busy-change="uploadBusy = $event"
				/></template>
				<template #slot-location="{ scope }"
					><div class="location-field">
						<el-tag
							:type="
								validCoordinate(scope.longitude, scope.latitude)
									? 'success'
									: 'warning'
							"
							>{{
								validCoordinate(scope.longitude, scope.latitude)
									? '已设置位置（GCJ-02）'
									: '待补充位置'
							}}</el-tag
						><span v-if="validCoordinate(scope.longitude, scope.latitude)"
							>经度 {{ scope.longitude }}，纬度 {{ scope.latitude }}</span
						><el-button @click="pickFormLocation(scope)">地图选点 / 核对位置</el-button
						><span>选点后需保存门店资料才会写入</span>
					</div></template
				>
				<template #slot-category="{ scope }"
					><el-select
						v-model="scope.categoryId"
						filterable
						placeholder="请选择门店类型"
						:loading="optionsLoading"
						style="width: 100%"
						><el-option
							v-for="category in categories"
							:key="category.id"
							:label="category.name"
							:value="category.id" /></el-select
				></template>
				<template #slot-stores="{ scope }"
					><div class="coupon-store-selection"><el-select
						v-model="scope.storeIds"
						multiple
						collapse-tags
						collapse-tags-tooltip
						filterable
						:loading="optionsLoading"
						placeholder="选择适用门店"
						style="width: 100%"
						><el-option
							v-for="store in storeOptions"
							:key="store.id"
							:label="store.name + (store.status === 1 ? '' : '（停用）')"
							:value="store.id"
							:disabled="
								store.status !== 1 && !originalStoreIds.includes(store.id)
							" /></el-select
					/>
					<span v-if="coversAllAvailableStores(scope.storeIds)" class="coupon-store-summary">
						已选当前全部可选营业门店
					</span>
				</div></template>
				<template #slot-issue-limit="{ scope }">
					<div class="coupon-number-field">
					<el-switch
						:model-value="scope.issueLimit == null"
						active-text="不限量"
						@update:model-value="
							value =>
								(scope.issueLimit = value
									? null
									: Math.max(scope.issuedCount || 0, 1))
						" /><el-input-number
						v-if="scope.issueLimit != null"
						v-model="scope.issueLimit"
						:min="Math.max(scope.issuedCount || 0, 1)"
						:precision="0"
						controls-position="right"
					/>
					<div v-if="scope.issueLimit != null" class="coupon-presets">
						<el-button v-for="quantity in [50, 100, 500]" :key="quantity" size="small"
							:type="scope.issueLimit === quantity ? 'primary' : 'default'" plain
							:disabled="quantity < (scope.issuedCount || 0)"
							@click="scope.issueLimit = quantity">{{ quantity }} 张</el-button>
					</div>
					</div>
				</template>
				<template #slot-content="{ scope }">
					<div class="content-list">
					<div
						v-for="(item, index) in scope.contentItems || []"
						:key="index"
						class="content-row"
					>
						<el-input
							v-model="item.name"
							placeholder="例如：招牌烧烤套餐"
							:aria-label="`第 ${index + 1} 项名称`"
							:disabled="!!item.productId"
						/><el-select v-model="item.type" :aria-label="`第 ${index + 1} 项类型`" :disabled="!!item.productId"
							><el-option label="商品" value="GOODS" /><el-option
								label="服务"
								value="SERVICE" /><el-option
								label="体验"
								value="EXPERIENCE" /></el-select
						><el-input-number
							v-model="item.quantity"
							:aria-label="`第 ${index + 1} 项数量`"
							:min="1"
							:precision="0"
							controls-position="right"
						/><el-button link type="danger" @click="scope.contentItems.splice(index, 1)"
							>移除</el-button
						>
					</div>
					<el-button
						@click="
							(scope.contentItems ||= []).push({
								name: '',
								type: 'SERVICE',
								quantity: 1
							})
						"
						>添加内容项目</el-button
					>
					</div>
				</template>
			</cl-upsert>
		</cl-crud>
		<el-drawer v-if="kind === 'coupon'" v-model="couponApprovalVisible" append-to-body
			title="优惠券审批流程" size="min(640px, 96vw)">
			<div v-loading="couponApprovalLoading" class="coupon-approval">
				<div class="coupon-approval-title">
					<span>优惠券</span>
					<h3>{{ couponApprovalName }}</h3>
				</div>
				<el-alert v-if="couponApprovalError" :title="couponApprovalError" type="error" :closable="false" />
				<ApprovalFlow v-else-if="couponApprovalDetail" :detail="couponApprovalDetail" type="COUPON" />
				<el-empty v-else-if="!couponApprovalLoading" description="提交审核后，可在这里查看处理进度和结果。" />
			</div>
		</el-drawer>
		<cl-dialog
			v-model="accountVisible"
			:title="(accountStore?.name || '') + ' · 门店账号管理'"
			width="1100px"
			height="70vh"
			><StoreAccounts
				v-if="accountVisible && accountStore"
				:key="accountStore.id"
				:store-id="accountStore.id"
				:store-name="accountStore.name"
		/></cl-dialog>
		<StoreLocationPicker v-if="kind === 'store'" ref="LocationPicker" />
	</div>
	</AssistantLayout>
</template>

<script setup lang="ts">
import CoverPlaceholder from '/$/space/components/cover-placeholder.vue';
import AssistantLayout from '/$/ai/components/assistant-layout.vue';
import AssistantEntry from '/$/ai/components/assistant-entry.vue';
import { ref } from 'vue';
import { useBase } from '/$/base';
import { useRouter } from 'vue-router';
import { identity } from '/@/workspace';
const { menu, user } = useBase();
const router = useRouter();
const can = (permission: string) => menu.perms.includes(permission);
const owner = (user.info as any)?.owner === true;
import StoreAccounts from './store-accounts.vue';
import StoreLocationPicker from './store-location-picker.vue';
import { validCoordinate, updateStoreLocation } from '../map/store-map';
import { useCrud, useTable, useSearch, useUpsert } from '@cool-vue/crud';
import { ArrowDown, Delete, Edit } from '@element-plus/icons-vue';
import StoreDirectoryHeader from '/$/merchant/components/store-directory-header.vue';
import { workspaceApi } from '/$/workspace/api';
import ApprovalFlow from '/$/approval/components/approval-flow.vue';
import type { ApprovalDetail } from '/$/approval/utils/flow';
import { ElMessage, ElMessageBox } from 'element-plus';
import { merchantRequest } from '/@/merchant-api';
import { createMerchantCrudService, type MerchantResource } from '../merchant-service';
import CouponCover from './coupon-cover.vue';
import CouponEditorHeading from "/@/modules/coupon/components/coupon-editor-heading.vue";
import CouponEditorNavigation from "/@/modules/coupon/components/coupon-editor-navigation.vue";
import { couponFieldStep, useCouponEditorFlow } from "/@/modules/coupon/components/coupon-editor-flow";
const { couponStep, goToCouponStep, prepareCouponStep, resetCouponStep } = useCouponEditorFlow();
const paid = ref(false);
import MerchantImageUpload from './merchant-image-upload.vue';
const uploadBusy = ref(false),
	uploadEpoch = ref(0);

const props = defineProps<{ kind: MerchantResource }>();
const kind = props.kind;
const assistantOpen = ref(false);
const assistantCoupon = ref<number>();
const assistantSelection = ref<number[]>([]);
function setAssistantSelection(rows: { id: number }[]) {
	assistantSelection.value = rows.map(row => row.id);
	assistantCoupon.value = rows.length === 1 ? rows[0].id : undefined;
}
const storeScopeName = ref(identity === 'STORE' ? '本门店' : '本商家');
const categories = ref<any[]>([]),
	storeOptions = ref<any[]>([]),
	originalStoreIds = ref<number[]>([]);
const optionsLoading = ref(false),
	optionsFailed = ref(false),
	publicationBusy = ref(false);
const typeNames: Record<string, string> = {
	FULL_REDUCTION: '满减券',
	DISCOUNT: '折扣券',
	CASH: '代金券',
	PACKAGE: '套餐券',
	EXPERIENCE: '体验券',
	EXCHANGE: '兑换券'
};
const contentTypes = ['PACKAGE', 'EXPERIENCE', 'EXCHANGE'];
const money = (value: any) => Number(value || 0).toFixed(2);
const numberText = (value: any) =>
	value == null ? '—' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 });
function coverBadge(row: any) {
	if (row.type === 'CASH') return `${numberText(row.deductAmount)}元`;
	if (row.type === 'DISCOUNT') return `${numberText(row.discountRate)}折`;
	if (row.type === 'FULL_REDUCTION') return `减${numberText(row.discountAmount)}元`;
	return typeNames[row.type] || '优惠券';
}
function ruleText(row: any) {
	if (row.type === 'FULL_REDUCTION')
		return `满${numberText(row.minSpend)}元减${numberText(row.discountAmount)}元`;
	const threshold = row.minSpend > 0 ? `满${numberText(row.minSpend)}元可用，` : '';
	if (row.type === 'CASH') return `${threshold}抵扣${numberText(row.deductAmount)}元`;
	if (row.type === 'DISCOUNT')
		return `${threshold}${numberText(row.discountRate)}折${row.maxDiscount == null ? '' : `，最高优惠${numberText(row.maxDiscount)}元`}`;
	return row.description || '内容待配置';
}
function coversAllAvailableStores(storeIds?: number[]) {
	if (!storeIds?.length || optionsLoading.value || optionsFailed.value) return false;
	const activeStores = storeOptions.value.filter(store => store.status === 1);
	const selectedIds = new Set(storeIds.map(String));
	return activeStores.length === selectedIds.size &&
		activeStores.every(store => selectedIds.has(String(store.id)));
}

function scopeText(row: any) {
	if (row.scopeType === 0) return '全部营业门店';
	if (coversAllAvailableStores(row.storeIds)) return '当前全部可选营业门店';
	return row.storeNames?.join('、') || '指定门店';
}
const LocationPicker = ref<InstanceType<typeof StoreLocationPicker>>();
const locationFingerprint = ref('');
function locationSignature(data: any) {
	return `${(data.address || '').trim()}|${Number(data.longitude)}|${Number(data.latitude)}`;
}
function pickFormLocation(data: any) {
	LocationPicker.value?.open(
		{ address: data.address, longitude: data.longitude, latitude: data.latitude },
		location => {
			Upsert.value?.setForm('address', location.address);
			Upsert.value?.setForm('longitude', location.longitude);
			Upsert.value?.setForm('latitude', location.latitude);
			locationFingerprint.value = locationSignature(location);
		}
	);
}
async function editStoreLocation(row: any) {
	try {
		const data = await merchantRequest('workspace/store/info?id=' + row.id);
		LocationPicker.value?.open(
			{ address: data.address, longitude: data.longitude, latitude: data.latitude },
			async location => {
				await updateStoreLocation(row.id, location);
				await Crud.value?.refresh();
			},
			'确认保存位置'
		);
	} catch (error: any) {
		ElMessage.error(error.message || '门店资料加载失败');
	}
}
const accountVisible = ref(false);
const accountStore = ref<{ id: number; name: string }>();
function manageAccounts(row: any) {
	router.push({
		path: identity === 'STORE' ? '/store/accounts' : '/merchant/accounts',
		query: { storeId: row.id }
	});
}
function storeAction(command: string, row: any) {
	if (command === 'location') void editStoreLocation(row);
	if (command === 'accounts') manageAccounts(row);
	if (command === 'delete') Crud.value?.rowDelete(row);
}
const couponApprovalVisible = ref(false);
const couponApprovalLoading = ref(false);
const couponApprovalName = ref('');
const couponApprovalError = ref('');
const couponApprovalDetail = ref<ApprovalDetail>();
let couponApprovalRequest = 0;

function approvedCurrentCouponVersion(row: any) {
	return row.ruleVersion != null &&
		row.publication?.auditStatus === 2 &&
		row.publication.submittedVersion === row.ruleVersion;
}

function couponAuditLabel(row: any) {
	if (row.publication?.auditStatus === 2 && !approvedCurrentCouponVersion(row)) return '需重新审核';
	return ['未提交', '待审核', '审核通过', '驳回'][row.publication?.auditStatus ?? 0] ?? '查看流程';
}

function remainingCouponStock(row: any) {
	return Math.max(0, row.issueLimit - (row.issuedCount || 0));
}

async function openCouponApproval(row: any) {
	const requestId = ++couponApprovalRequest;
	couponApprovalName.value = row.name;
	couponApprovalDetail.value = undefined;
	couponApprovalError.value = '';
	couponApprovalVisible.value = true;
	couponApprovalLoading.value = true;
	try {
		const result = await merchantRequest(
			'workspace/coupon/info?includeApproval=true&id=' + encodeURIComponent(row.id)
		);
		if (requestId !== couponApprovalRequest) return;
		if (!Object.prototype.hasOwnProperty.call(result ?? {}, 'approval')) {
			couponApprovalError.value = '当前后端版本暂不支持查看审批流程，请更新后端后重试';
			return;
		}
		couponApprovalDetail.value = result.approval ?? undefined;
	} catch (error) {
		if (requestId === couponApprovalRequest)
			couponApprovalError.value =
				(error as { message?: string })?.message || '审批流程加载失败，请重试';
	} finally {
		if (requestId === couponApprovalRequest) couponApprovalLoading.value = false;
	}
}

const columns: any[] =
	kind === 'coupon'
		? [
				{ type: 'selection', fixed: 'left' },
				{ label: '优惠券', prop: 'name', minWidth: 220, align: 'left' },
				{ label: '优惠摘要', minWidth: 160, formatter: ruleText, align: 'left' },
				{ label: '售价', prop: 'salePrice', width: 105 },
				{ label: '剩余库存', prop: 'couponStock', width: 120 },
				{ label: '适用门店', minWidth: 150, formatter: scopeText },
				{
					label: '发布状态',
					prop: 'couponApproval',
					width: 132,
					showOverflowTooltip: false
				},
				{ label: '发布操作', prop: 'publicationActions', width: 136, fixed: 'right', showOverflowTooltip: false },
				{ type: 'op', width: 88, buttons: [{ name: 'slot-coupon-actions' }] }
			]
		: [
					{ type: 'selection', fixed: 'left', width: 44 },
					{ label: '门店名称', prop: 'name', minWidth: 240, align: 'left', showOverflowTooltip: false },
					{ label: '营业状态', prop: 'storeStatus', width: 100, align: 'left' },
					{ label: '联系方式', prop: 'storeContact', minWidth: 150, align: 'left', showOverflowTooltip: false },
					{ label: '操作', prop: 'storeActions', width: 130, align: 'right', showOverflowTooltip: false }
				];
const Table = useTable({
	height: kind === 'coupon' ? '100%' : undefined,
	emptyText: kind === 'coupon' ? '暂无符合条件的优惠券' : kind === 'store' ? '没有符合条件的门店' : undefined,
	props: { showOverflowTooltip: true },
	columns
});
const Search = useSearch({
	items: [
		{
			label: kind === 'coupon' ? '券名称' : '门店名称',
			prop: 'keyWord',
			component: {
				name: 'el-input',
				props: {
					clearable: true,
					maxlength: 100,
					placeholder: kind === 'coupon' ? '输入券名称搜索' : '输入门店名称搜索'
				},
				style: { width: '190px' }
			}
		}
	]
});
const textInput = { name: 'el-input', props: { clearable: true, maxlength: 100 } };
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
const integerInput = {
	name: 'el-input-number',
	props: {
		min: 1,
		max: 2147483647,
		precision: 0,
		controlsPosition: 'right',
		style: { width: '100%' }
	}
};
const field = (label: string, prop: string, component: any, extra: any = {}) => ({
	label,
	prop,
	component,
	span: 12,
	...extra
});
const select = (options: any[]) => ({ name: 'el-select', options });
const storeItems: any[] = [
	field('门店名称', 'name', textInput, { required: true }),
	field('门店类型', 'categoryId', { name: 'slot-category' }, { required: true }),
	field('联系电话', 'phone', { name: 'el-input', props: { maxlength: 30 } }, { required: true }),
	field(
		'营业状态',
		'status',
		select([
			{ label: '营业', value: 1 },
			{ label: '停业', value: 2 },
			{ label: '关闭', value: 3 }
		]),
		{ required: true }
	),
	field(
		'详细地址',
		'address',
		{ name: 'el-input', props: { maxlength: 500 } },
		{ required: true, span: 24 }
	),
	field('门店位置', '_locationPicker', { name: 'slot-location' }, { span: 24 }),
	field('门店封面', 'cover', { name: 'slot-cover' }, { span: 24 }),
	{ prop: 'coverMaterialId', hidden: true },
	field(
		'营业时间',
		'businessHours',
		{ name: 'el-input', props: { maxlength: 4000, placeholder: '例如周一至周日 09:00–22:00' } },
		{ span: 24 }
	)
];
const couponItems: any[] = [
	field('券名称', 'name', textInput, { required: true }),
	field(
		'券类型',
		'type',
		select(Object.entries(typeNames).map(([value, label]) => ({ value, label }))),
		{ required: true }
	),
	field(
		'模板状态',
		'status',
		select([
			{ label: '草稿', value: 0 },
			{ label: '启用', value: 1 },
			{ label: '停用', value: 2 }
		]),
		{ required: true }
	),
	field('优惠券封面', 'cover', { name: 'slot-cover' }, { span: 24 }),
	{ prop: 'coverMaterialId', hidden: true },
	field(
		'使用说明',
		'description',
		{
			name: 'el-input',
			props: { type: 'textarea', rows: 3, maxlength: 2000, showWordLimit: true }
		},
		{ span: 24 }
	),
	field(
		'套餐／服务内容',
		'contentItems',
		{ name: 'slot-content' },
		{ span: 24, hidden: ({ scope }: any) => !contentTypes.includes(scope.type) }
	),
	field('领取方式', 'salePrice', { name: 'slot-price' }, {
		rules: {
			validator: (_rule: unknown, value: unknown, callback: (error?: Error) => void) => {
				callback(paid.value && !(Number(value) > 0) ? new Error('请输入大于 0 元的售价') : undefined);
			}
		}
	}),
	field('最低消费（元）', 'minSpend', { name: 'slot-min-spend' }, {
		hidden: ({ scope }: any) => contentTypes.includes(scope.type)
	}),
	field('减免金额（元）', 'discountAmount', moneyInput, {
		required: true,
		hidden: ({ scope }: any) => scope.type !== 'FULL_REDUCTION'
	}),
	field('抵扣金额（元）', 'deductAmount', moneyInput, {
		required: true,
		hidden: ({ scope }: any) => scope.type !== 'CASH'
	}),
	field(
		'折扣数',
		'discountRate',
		{ name: 'slot-discount-rate' },
		{ required: true, hidden: ({ scope }: any) => scope.type !== 'DISCOUNT' }
	),
	field('优惠上限（元）', 'maxDiscount', moneyInput, {
		hidden: ({ scope }: any) => scope.type !== 'DISCOUNT'
	}),
	field(
		'适用门店',
		'scopeType',
		select([
			{ label: '全部营业门店', value: 0 },
			{ label: '指定门店', value: 1 }
		]),
		{ required: true }
	),
	field(
		'选择门店',
		'storeIds',
		{ name: 'slot-stores' },
		{ required: true, span: 24, hidden: ({ scope }: any) => scope.scopeType !== 1 }
	),
	field(
		'有效期方式',
		'validityType',
		select([
			{ label: '发券后按天有效', value: 'AFTER_ISSUE' },
			{ label: '固定起止日期', value: 'FIXED' }
		]),
		{ required: true }
	),
	field('有效天数', 'validDays', { name: 'slot-valid-days' }, {
		required: true,
		hidden: ({ scope }: any) => scope.validityType !== 'AFTER_ISSUE'
	}),
	field('有效期开始', 'validFrom', dateInput, {
		required: true,
		hidden: ({ scope }: any) => scope.validityType !== 'FIXED'
	}),
	field('有效期结束', 'validTo', dateInput, {
		required: true,
		hidden: ({ scope }: any) => scope.validityType !== 'FIXED'
	}),
	field('发行总量', 'issueLimit', { name: 'slot-issue-limit' }, { span: 24 }),
	field('每人限领（张）', 'perCustomerLimit', integerInput, { required: true }),
	field(
		'同享其他优惠',
		'allowCombine',
		select([
			{ label: '不允许', value: 0 },
			{ label: '允许', value: 1 }
		]),
		{ required: true }
	),
	field('领取开始时间', 'saleStartTime', dateInput),
	field('领取结束时间', 'saleEndTime', dateInput)
];
for (const item of couponItems) {
	const step = couponFieldStep(item.prop);
	item.props = { ...item.props, class: `coupon-fields coupon-fields--${step}` };
}
const Upsert = useUpsert({
	dialog: kind === 'coupon'
		? { width: 'min(1120px, 96vw)', height: 'min(720px, 76dvh)', controls: ['close'] }
		: { width: '860px', height: '65vh' },
	props: {
		labelPosition: 'top',
		onValidate(prop: string, valid: boolean) {
			if (!valid && kind === 'coupon') {
				const step = couponFieldStep(prop);
				couponStep.value = step;
			}
		}
	},
	items: kind === 'store' ? storeItems : [{
		span: 24,
		props: { class: 'coupon-editor-fields' },
		component: { name: 'el-col', props: { span: 24 } },
		children: couponItems
	}],
	onOpened(data) {
		assistantOpen.value = false;
		resetCouponStep();
		paid.value = Number(data.salePrice) > 0;
		uploadEpoch.value++;
		uploadBusy.value = false;
		const defaults =
			kind === 'store'
				? { status: 1 }
				: {
						type: 'CASH',
						status: 0,
						salePrice: 0,
						minSpend: 0,
						deductAmount: 10,
						scopeType: 0,
						storeIds: [],
						contentItems: [],
						validityType: 'AFTER_ISSUE',
						validDays: 30,
						perCustomerLimit: 1,
						allowCombine: 0
					};
		if (!data.id)
			Object.entries(defaults).forEach(([key, value]) => {
				if (data[key] == null) Upsert.value?.setForm(key, value);
			});
		if (kind === 'store') {
			locationFingerprint.value = validCoordinate(data.longitude, data.latitude)
				? locationSignature(data)
				: '';
			if (!data.id) {
				Upsert.value?.setForm('longitude', null);
				Upsert.value?.setForm('latitude', null);
			}
		}
		originalStoreIds.value = [...(data.storeIds || [])];
		void loadOptions();
	},
	onSubmit(data, { next, done }) {
		if (uploadBusy.value) {
			ElMessage.warning('图片正在上传，请上传完成后再保存');
			done();
			return;
		}
		if (optionsLoading.value || optionsFailed.value) {
			ElMessage.warning('门店或类型选项尚未加载完成，请关闭后重试');
			done();
			return;
		}
		const payload = { ...data };
		if (kind === 'store') {
			const hasLongitude =
				data.longitude !== null && data.longitude !== undefined && data.longitude !== '';
			const hasLatitude =
				data.latitude !== null && data.latitude !== undefined && data.latitude !== '';
			if (hasLongitude || hasLatitude) {
				if (!validCoordinate(data.longitude, data.latitude)) {
					ElMessage.warning('门店坐标不完整或超出范围，请通过地图补录位置');
					done();
					return;
				}
				if (locationSignature(data) !== locationFingerprint.value) {
					ElMessage.warning('地址已变更，请通过地图重新核对位置后保存');
					done();
					return;
				}
				payload.longitude = Number(data.longitude);
				payload.latitude = Number(data.latitude);
			}
			delete payload._locationPicker;
		}
		if (kind === 'coupon') {
			if (paid.value && !(Number(data.salePrice) > 0)) {
				couponStep.value = 1;
				ElMessage.warning('请输入大于 0 元的售价');
				done();
				return;
			}
			payload.salePrice = paid.value ? data.salePrice : 0;
			payload.contentItems = contentTypes.includes(data.type) ? data.contentItems || [] : [];
			if (data.scopeType === 0) payload.storeIds = [];
			for (const key of [
				'cover',
				'description',
				'minSpend',
				'discountAmount',
				'deductAmount',
				'discountRate',
				'maxDiscount',
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
			if (data.validityType === 'FIXED') payload.validDays = null;
			else {
				payload.validFrom = null;
				payload.validTo = null;
			}
		}
		for (const key of [
			'merchantId',
			'merchantName',
			'categoryName',
			'storeNames',
			'issuedCount',
			'ruleVersion',
			'publication',
			'createTime',
			'updateTime',
			'queryWrapper'
		])
			delete payload[key];
		next(payload);
	}
});
const Crud = useCrud({ service: createMerchantCrudService(kind) }, app => {
	app.refresh();
	if (kind === 'coupon') void loadOptions(false);
	if (kind === 'store') {
		void workspaceApi('me').then(scope => {
			const name = identity === 'STORE' ? scope?.storeName : scope?.merchantName;
			if (name) storeScopeName.value = name;
		}).catch(() => {
			// Scope is already enforced by the store API.
		});
	}
});
async function loadOptions(showError = true) {
	if (optionsLoading.value) return;
	optionsLoading.value = true;
	optionsFailed.value = false;
	try {
		if (kind === 'store')
			categories.value = await merchantRequest('workspace/category/options');
		else storeOptions.value = await merchantRequest('workspace/store/options');
	} catch (error: any) {
		optionsFailed.value = true;
		if (showError) ElMessage.error(error.message || '选项加载失败');
	} finally {
		optionsLoading.value = false;
	}
}
const publicationLabels = {
	submitAudit: '提交审核',
	withdrawAudit: '撤回审核',
	publish: '上架',
	unpublish: '下架'
};

async function publication(row: any, action: keyof typeof publicationLabels) {
	if (publicationBusy.value) return;
	const label = publicationLabels[action];
	publicationBusy.value = true;
	try {
		await ElMessageBox.confirm(`确认对「${row.name}」执行${label}？`, label, {
			confirmButtonText: label,
			cancelButtonText: '取消'
		});
		await merchantRequest('workspace/coupon/publication', {
			id: row.id,
			version: row.ruleVersion,
			action
		});
		ElMessage.success(`${label}成功`);
		await Crud.value?.refresh();
	} catch (error: any) {
		if (error?.message) ElMessage.error(error.message);
	} finally {
		publicationBusy.value = false;
	}
}
</script>


<style scoped>
.coupon-workspace { padding: 0; overflow: hidden; background: var(--el-fill-color-light); box-sizing: border-box; }
.coupon-workspace > :deep(.cl-crud) {
	display: flex;
	flex-direction: column;
	overflow: hidden;
}
.coupon-workspace :is(.business-actions, .business-pagination) { flex: 0 0 auto; }
.coupon-workspace .business-actions,
.coupon-workspace .business-table { background: var(--el-bg-color); padding: 0 12px; margin: 0; }
.coupon-workspace .business-actions { align-items: center; padding-top: 12px; padding-bottom: 8px; }
.coupon-workspace .business-actions :deep(.el-button) { border-radius: 8px; }
.coupon-workspace .coupon-toolbar-search { margin-inline-start: auto; max-width: 100%; }
.business-stores {
	box-sizing: border-box;
	padding: 18px 22px 0;
	background: var(--workspace-canvas);
}
html.dark .business-stores { background: var(--el-bg-color-page); }
.business-stores > :deep(.cl-crud) { background: transparent; }
.business-stores .business-actions {
	align-items: center;
	flex-wrap: wrap;
	gap: 8px;
	padding: 12px 14px;
	margin: 4px 0 0;
}
.business-stores .store-toolbar-search { margin-inline-start: auto; max-width: 100%; }
.business-stores :deep(th.el-table__cell) {
	background: color-mix(in srgb, var(--el-fill-color-light) 78%, var(--el-color-primary) 2%, var(--el-bg-color));
	color: var(--el-text-color-regular);
	font-weight: 600;
}
.business-stores :deep(.el-table--border .el-table__cell) { border-right: 0; }
.business-stores :deep(.el-table--border::before),
.business-stores :deep(.el-table--border::after),
.business-stores :deep(.el-table__border-left-patch) { display: none; }
.business-stores :deep(.el-pagination) { flex-wrap: wrap; row-gap: 6px; }
.store-identity { display: flex; align-items: center; gap: 10px; }
.store-identity > div { min-width: 0; }
.store-identity strong { display: block; font-size: 15px; line-height: 1.4; overflow-wrap: anywhere; }
.store-identity small, .store-contact small { display: block; color: var(--el-text-color-regular); font-size: 12px; line-height: 1.5; }
.store-identity small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.store-identity .store-cover { width: 38px; height: 38px; flex: none; border-radius: 6px; }
.store-contact { font-size: 12px; font-variant-numeric: tabular-nums; }
.store-actions { display: flex; justify-content: flex-end; align-items: center; gap: 8px; }
.store-status { border: 0; }
.store-status i {
	display: inline-block;
	width: 5px;
	height: 5px;
	margin-inline-end: 5px;
	border-radius: 50%;
	background: currentColor;
	vertical-align: 2px;
}
@media (max-width: 850px) {
	.business-stores { padding: 8px; }
	.business-stores .store-toolbar-search { margin-inline-start: 0; }
}
.coupon-workspace .business-table {
	flex: 1 1 0;
	min-height: 0;
	flex-wrap: nowrap;
	overflow: hidden;
}
.coupon-workspace .business-table :deep(.el-table) { max-height: none !important; margin-bottom: 0; }
.coupon-workspace .business-pagination {
	padding: 10px 12px 2px;
	background: var(--el-bg-color);
}
.coupon-workspace .business-pagination :deep(.el-pagination) { flex-wrap: wrap; row-gap: 6px; }
.coupon-workspace :deep(.el-table) {
	border: 1px solid var(--el-border-color-lighter);
	border-radius: 14px;
	--el-table-border-color: var(--el-border-color-extra-light);
	--el-table-row-hover-bg-color: var(--el-color-primary-light-9);
}
.coupon-workspace :deep(.el-table .el-table__cell) { border-right: 0; }
.coupon-workspace :deep(.el-table::after),
.coupon-workspace :deep(.el-table__border-left-patch) { display: none; }
.coupon-workspace :deep(.el-table th.el-table__cell) {
	padding: 12px 0;
	font-size: 12px;
	font-weight: 650;
	background-color: var(--workspace-table-band) !important;
	color: #fff !important;
	border-bottom-color: var(--workspace-table-band);
}
.coupon-workspace :deep(.el-table td.el-table__cell) { padding: 12px 0; }
.coupon-workspace :deep(.el-table__body tr:nth-child(even) > td) {
	background-color: color-mix(in srgb, var(--el-color-primary) 3%, var(--el-bg-color));
}
.coupon-workspace :deep(.el-table__body tr:hover > td),
.coupon-workspace :deep(.el-table__body tr.hover-row > td) {
	background-color: var(--el-color-primary-light-9);
}
.coupon-identity { display: flex; align-items: center; gap: 12px; white-space: normal; }
.coupon-identity :deep(.coupon-cover) { flex: 0 0 46px; width: 46px; height: 46px; border-radius: 10px; }
.coupon-identity > div { min-width: 0; text-align: left; overflow-wrap: anywhere; }
.coupon-identity strong { display: block; font-size: 14px; color: var(--el-text-color-primary); }
.coupon-labels { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 4px; }
.coupon-release { display: grid; justify-items: center; gap: 4px; }
.coupon-release .el-button { padding: 0; font-size: 12px; }
.coupon-release .el-button span span { margin-inline-start: 3px; }
.coupon-release-pill, .coupon-unlimited {
	padding: 2px 8px; border-radius: 6px; font-size: 12px;
	background: var(--el-fill-color); color: var(--el-text-color-secondary);
}
.coupon-release-pill[data-publish-status="1"] {
	background: var(--el-color-success-light-8);
	color: var(--el-color-success-dark-2);
}
.coupon-release-pill[data-publish-status="2"] {
	background: var(--el-color-warning-light-8);
	color: var(--el-color-warning-dark-2);
}
.coupon-price-value { font-weight: 700; font-variant-numeric: tabular-nums; color: var(--el-text-color-primary); }
.coupon-price-value.free { font-size: 12px; color: var(--el-color-success-dark-2); }
.coupon-stock { display: grid; gap: 5px; padding: 0 5px; color: var(--el-text-color-secondary); font-size: 12px; }
.coupon-stock strong { color: var(--el-text-color-primary); font-variant-numeric: tabular-nums; }
.coupon-stock-track { height: 3px; border-radius: 4px; overflow: hidden; background: var(--el-fill-color); }
.coupon-stock-track span { display: block; height: 100%; border-radius: inherit; background: var(--workspace-accent); }
.coupon-row-actions { display: flex; justify-content: center; gap: 6px; }
.coupon-row-actions .el-button + .el-button { margin-left: 0; }
.coupon-publication-actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 4px 8px; }
.coupon-publication-actions .el-button { min-width: 76px; border-radius: 7px; font-weight: 600; }
.coupon-publication-actions .el-button + .el-button { margin-left: 0; }
.coupon-identity small {
	--coupon-accent: var(--el-color-primary);
	padding: 0 7px;
	border-radius: 5px;
	background: color-mix(in srgb, var(--coupon-accent) 15%, var(--el-bg-color));
	color: color-mix(in srgb, var(--coupon-accent) 78%, var(--el-text-color-primary));
}
.coupon-identity [data-coupon-type="FULL_REDUCTION"] { --coupon-accent: #df5537; }
.coupon-identity [data-coupon-type="DISCOUNT"] { --coupon-accent: #8652d7; }
.coupon-identity [data-coupon-type="CASH"] { --coupon-accent: #bf7c18; }
.coupon-identity [data-coupon-type="PACKAGE"] { --coupon-accent: #326ae0; }
.coupon-identity [data-coupon-type="EXCHANGE"] { --coupon-accent: #168b80; }
.coupon-identity .coupon-label-muted { background: var(--el-fill-color); color: var(--el-text-color-secondary); }
.coupon-number-field { display: grid; gap: 8px; justify-items: start; width: 100%; }
.coupon-store-selection { width: 100%; }
.coupon-store-summary { color: var(--el-color-primary); font-size: 12px; }
.coupon-number-field .el-input-number { width: 156px; max-width: 100%; }
.coupon-presets { display: flex; flex-wrap: wrap; gap: 6px; }
.coupon-presets .el-button + .el-button { margin-left: 0; }
.coupon-price { display: grid; gap: 10px; justify-items: start; }
.coupon-sale-price { display: grid; gap: 4px; line-height: 1.4; }

.location-field {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	gap: 10px;
	font-size: 12px;
	color: var(--el-text-color-secondary);
}
.content-list {
	display: flex;
	flex-direction: column;
	align-items: stretch;
	width: 100%;
	min-width: 0;
}
.content-list > .el-button {
	align-self: flex-start;
}
.content-row {
	display: flex;
	align-items: center;
	gap: 8px;
	margin-bottom: 12px;
}
.coupon-approval {
	min-height: 160px;
}
.coupon-approval-title {
	margin-bottom: 20px;
	padding: 14px 16px;
	border-radius: 10px;
	background: var(--el-fill-color-light);
}
.coupon-approval-title span {
	color: var(--el-text-color-secondary);
	font-size: 12px;
}
.coupon-approval-title h3 {
	margin: 4px 0 0;
	overflow-wrap: anywhere;
}
.content-row > .el-input {
	flex: 1;
	min-width: 100px;
}
.content-row > .el-select {
	width: 100px;
	flex: none;
}
.content-row .el-input-number {
	flex: none;
	width: 120px;
}
@media (max-width: 760px) {
	.content-row {
		flex-wrap: wrap;
	}
}
</style>
