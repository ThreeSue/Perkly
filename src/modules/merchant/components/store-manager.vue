<template>
	<ManagementShell
		title="门店信息"
		eyebrow="门店管理"
		description="按商家集中管理门店资料、位置与营业状态"
		:sidebar="!props.embedded && props.merchantId == null"
		:items="merchants"
		:scope-id="selectedMerchant"
		:scope-name="selectedMerchantName"
		:metrics="metrics"
		@scope-change="selectMerchant"
	>
		<cl-crud ref="Crud">
			<cl-row
				><div class="management-toolbar">
					<cl-add-btn
						:disabled="selectedMerchantDisabled"
					/><cl-refresh-btn /><cl-multi-delete-btn />
					<el-button v-if="canMap" @click="openMap">门店分布地图</el-button>
					<span class="toolbar-spacer" />
					<el-input
						v-model="filters.keyword"
						clearable
						placeholder="门店名称 / 电话 / 地址"
						@keyup.enter="search"
					/>
					<el-button type="primary" @click="search">搜索</el-button
					><el-button @click="resetFilters">重置</el-button>
				</div></cl-row
			>
			<cl-row
				><div class="management-toolbar">
					<span class="filter-label">营业状态</span>
					<el-radio-group v-model="filters.status" @change="search">
						<el-radio-button value="ALL">全部</el-radio-button
						><el-radio-button :value="1">营业</el-radio-button
						><el-radio-button :value="2">停业</el-radio-button
						><el-radio-button :value="0">禁用</el-radio-button
						><el-radio-button :value="3">关闭</el-radio-button>
					</el-radio-group>
					<el-select
						v-model="filters.categoryId"
						clearable
						placeholder="全部门店类型"
						@change="search"
						><el-option
							v-for="item in categories"
							:key="item.id"
							:label="item.name"
							:value="item.id"
					/></el-select></div
			></cl-row>
			<cl-row
				><p class="management-caption">
					统计为当前商家范围内全部门店，不随下方搜索和状态筛选变化。{{
						selectedMerchantDisabled ? '所选商家已停用，暂不能新增门店。' : ''
					}}{{ statsError }}
				</p></cl-row
			>

			<cl-row>
				<cl-table ref="Table">
					<template #column-name="{ scope }"
						><div class="store-name-cell">
							<el-avatar shape="square" :src="scope.row.cover" :size="36">{{
								scope.row.name?.slice(0, 1)
							}}</el-avatar>
							<div style="min-width: 0">
								<div class="cell-main">
									{{ scope.row.name }}
									<el-tag
										v-if="isDefaultStore(scope.row)"
										size="small"
										effect="plain"
										>默认</el-tag
									>
								</div>
								<div class="cell-sub" :title="scope.row.address">
									{{ scope.row.categoryName || '门店' }} ·
									{{ scope.row.address || '未填写地址' }}
								</div>
							</div>
						</div></template
					>
					<template #column-actions="{ scope }"
						><div class="store-actions">
							<el-button
								v-if="service.merchant.store._permission.info"
								link
								type="primary"
								@click="detail?.open(scope.row)"
								>查看</el-button
							><el-dropdown
								trigger="click"
								@command="command => storeAction(command, scope.row)"
								><el-button link>更多 ▾</el-button
								><template #dropdown
									><el-dropdown-menu
										><el-dropdown-item
											v-if="mapPermission('store:location:update')"
											command="location"
											>位置管理</el-dropdown-item
										><el-dropdown-item
											v-if="user.info?.username === 'admin'"
											command="accounts"
											>账号管理</el-dropdown-item
										><el-dropdown-item
											v-if="service.merchant.store._permission.delete"
											command="delete"
											divided
											>删除空门店</el-dropdown-item
										></el-dropdown-menu
									></template
								></el-dropdown
							>
						</div></template
					>
					<template #column-status="{ scope }"
						><el-tag :type="statusTag(scope.row.status)">{{
							['禁用', '营业', '停业', '关闭'][scope.row.status] || '未知'
						}}</el-tag></template
					>
					<template #column-cover="{ scope }">
						<el-image
							v-if="scope.row.cover"
							:src="scope.row.cover"
							:preview-src-list="[scope.row.cover]"
							preview-teleported
							fit="cover"
							style="width: 64px; height: 44px; border-radius: 6px"
						>
							<template #error><CoverPlaceholder /></template>
						</el-image>
						<CoverPlaceholder v-else />
					</template>
				</cl-table>
			</cl-row>

			<cl-row>
				<cl-flex1 />
				<cl-pagination />
			</cl-row>

			<cl-upsert ref="Upsert">
			<template #slot-cover="{ scope }"><CoverField v-model="scope.cover" /></template>
				<template #slot-location="{ scope }">
					<div class="store-location-field">
						<el-input
							v-model="scope.address"
							clearable
							maxlength="500"
							placeholder="请填写详细地址，并核对地图位置"
						/>
						<el-button @click="formLocation?.open(scope)">地图选址</el-button>
						<el-text size="small" type="info">{{
							hasCoordinates(scope)
								? `经度 ${scope.longitude}，纬度 ${scope.latitude}（GCJ-02）`
								: '尚未定位；历史门店可稍后补录，不会自动生成坐标。'
						}}</el-text>
					</div>
				</template>
				<template #slot-merchant="{ scope }">
					<el-input
						v-if="props.merchantId != null"
						:model-value="props.merchantName"
						readonly
					/>
					<MerchantSelect
						v-else
						v-model="scope.merchantId"
						active-only
						style="width: 100%"
					/>
				</template>
				<template #slot-category="{ scope }">
					<el-select
						v-model="scope.categoryId"
						filterable
						clearable
						placeholder="请选择门店类型"
						style="width: 100%"
					>
						<el-option
							v-for="item in categoriesFor(scope.categoryId)"
							:key="item.id"
							:label="pathOf(item)"
							:value="item.id"
						/>
					</el-select>
				</template>
			</cl-upsert>
		</cl-crud>
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
		<StoreDetail ref="detail" :categories="categories" :merchant-name="props.merchantName" :embedded="props.embedded" @changed="locationSaved" />
		<LocationPicker ref="formLocation" @confirmed="applyLocation" />
		<LocationPicker ref="locationEditor" persist @saved="locationSaved" />
	</ManagementShell>
</template>

<script lang="ts" setup>
defineOptions({ name: 'store-manager' });

import CoverPlaceholder from '/$/space/components/cover-placeholder.vue';
import CoverField from '/$/space/components/cover-field.vue';
import { ref, computed, onActivated, watch } from 'vue';
import ManagementShell from '/$/workspace/components/management-shell.vue';
import { useManagementState, useRequestRevision } from '/$/workspace/composables/management-state';
import LocationPicker from './location-picker.vue';
import StoreDetail from './store-editor-detail.vue';
const detail = ref<InstanceType<typeof StoreDetail>>();
import { hasCoordinates, type LocationValue } from '../map/model';
import { mapPermission } from '../map/permission';
import StoreAccounts from './store-accounts.vue';
import { useBase } from '/$/base';
import MerchantSelect from './merchant-select.vue';
import { phoneRule } from './validation';
import { createMerchantService } from './merchant-scope';
import { ElMessage } from 'element-plus';
import { useCrud, useTable, useUpsert, useSearch } from '@cool-vue/crud';
import { useCool } from '/@/cool';
import { useI18n } from 'vue-i18n';

const props = defineProps<{
	merchantId?: number;
	merchantName?: string;
	embedded?: boolean;
	defaultStoreId?: number;
}>();
const emit = defineEmits<{ changed: [] }>();
const { service, router } = useCool();
const { state: filters, selectMerchant: selectScope } = useManagementState(
	props.embedded ? 'embedded-stores' : 'stores',
	{
		merchantId: undefined as number | undefined,
		keyword: '',
		status: 'ALL' as string | number,
		categoryId: undefined as number | undefined
	}
);
const merchants = ref<any[]>([]),
	statsRows = ref<any[] | null>(null),
	statsError = ref('');
const statsRevision = useRequestRevision();
const selectedMerchant = computed(() => props.merchantId ?? filters.merchantId);
const selectedMerchantDisabled = computed(
	() => merchants.value.find(m => m.id === selectedMerchant.value)?.status === 0
);
function isDefaultStore(row: any) {
	return (
		(props.defaultStoreId ||
			merchants.value.find(m => m.id === row.merchantId)?.defaultStoreId) === row.id
	);
}
const selectedMerchantName = computed(
	() =>
		props.merchantName ||
		merchants.value.find(m => m.id === selectedMerchant.value)?.name ||
		(selectedMerchant.value ? '所选商家' : '全部商家')
);
const metrics = computed(() => [
	{ label: '当前门店', value: statsRows.value?.length },
	{ label: '营业中', value: statsRows.value?.filter(s => s.status === 1).length },
	{ label: '已停业', value: statsRows.value?.filter(s => s.status === 2).length },
	{
		label: '禁用 / 关闭',
		value: statsRows.value?.filter(s => s.status === 0 || s.status === 3).length
	}
]);
function statusTag(status: number) {
	return status === 1
		? ('success' as const)
		: status === 2
			? ('warning' as const)
			: status === 0
				? ('danger' as const)
				: ('info' as const);
}
function selectMerchant(id?: number) {
	const sameMerchant = id === selectedMerchant.value;
	clearFilters();
	selectScope(id);
	if (sameMerchant) search();
}
function search() {
	void Crud.value?.refresh({
		page: 1,
		merchantId: selectedMerchant.value ?? null,
		keyWord: filters.keyword.trim() || null,
		name: null,
		status: filters.status === 'ALL' ? null : filters.status,
		categoryId: filters.categoryId ?? null
	});
}
function clearFilters() {
	filters.keyword = '';
	filters.status = 'ALL';
	filters.categoryId = undefined;
}
function resetFilters() {
	clearFilters();
	search();
}
function openMap() {
	router.push({ path: '/merchant/storeMap', query: { merchantId: selectedMerchant.value } });
}
async function loadSummary() {
	const revision = statsRevision.next();
	statsRows.value = null;
	statsError.value = '';
	try {
		const rows = await service.merchant.store.list(
			selectedMerchant.value == null
				? {}
				: {
						merchantId: selectedMerchant.value
					}
		);
		if (statsRevision.current(revision)) statsRows.value = rows;
	} catch {
		if (statsRevision.current(revision))
			statsError.value = '统计暂不可用，可继续查看列表并刷新重试。';
	}
}
watch(selectedMerchant, () => {
	if (ready) {
		clearFilters();
		search();
		void loadSummary();
	}
});
const canMap = computed(() => mapPermission('store:map'));
const formLocation = ref<InstanceType<typeof LocationPicker>>();
const locationEditor = ref<InstanceType<typeof LocationPicker>>();
function applyLocation(value: LocationValue) {
	Upsert.value?.setForm('address', value.address);
	Upsert.value?.setForm('longitude', value.longitude);
	Upsert.value?.setForm('latitude', value.latitude);
}
function locationSaved() {
	void Crud.value?.refresh();
	void loadSummary();
	emit('changed');
}
const { user } = useBase();
const accountVisible = ref(false);
const accountStore = ref<{ id: number; name: string }>();
function storeAction(command: string, row: any) {
	if (command === 'location') locationEditor.value?.open(row);
	if (command === 'accounts') manageAccounts(row);
	if (command === 'delete') Crud.value?.rowDelete(row);
}
function manageAccounts(row: any) {
	router.push({
		path: '/workspace-admin/accounts',
		query: { storeId: row.id, merchantId: row.merchantId }
	});
}
const { t } = useI18n();

interface StoreCategory {
	id: number;
	name: string;
	status: number;
}

interface Store {
	merchantName?: string;
	categoryName?: string;
	merchantId: number;
	categoryId: number;
	status: number;
}

const categories = ref<StoreCategory[]>([]);

function categoriesFor(currentId?: number) {
	return categories.value.filter(item => Number(item.status) === 1 || item.id === currentId);
}

function pathOf(item: StoreCategory) {
	return item.name;
}

const Upsert = useUpsert({
	onSubmit(data, { next }) {
		// Cool form removes hidden fields from submitted data; carry the chosen coordinates explicitly.
		data.longitude = Upsert.value?.getForm('longitude');
		data.latitude = Upsert.value?.getForm('latitude');
		void next(data).catch(() => {});
	},
	onOpened(data) {
		void loadOptions();
		if (!data.id && selectedMerchant.value != null) data.merchantId = selectedMerchant.value;
		if (!data.id) {
			data.status ??= 1;
		}
	},
	items: [
		{
			label: t('所属商家'),
			prop: 'merchantId',
			component: { name: 'slot-merchant' },
			span: 12,
			required: true
		},
		{
			label: t('门店类型'),
			prop: 'categoryId',
			component: { name: 'slot-category' },
			span: 12,
			required: true
		},
		{
			label: t('门店名称'),
			prop: 'name',
			component: { name: 'el-input', props: { clearable: true, maxlength: 100 } },
			span: 12,
			required: true
		},
		{
			label: t('门店封面'),
			prop: 'cover',
			component: { name: 'slot-cover' },
			span: 24
		},
		{
			label: t('详细地址与位置'),
			prop: 'address',
			component: { name: 'slot-location' },
			span: 24,
			required: true
		},
		{ prop: 'longitude', hidden: true, component: { name: 'el-input-number' } },
		{ prop: 'latitude', hidden: true, component: { name: 'el-input-number' } },
		{
			label: t('联系电话'),
			prop: 'phone',
			rules: [{ required: true, message: '请输入门店联系电话', trigger: 'blur' }, phoneRule],
			component: { name: 'el-input', props: { clearable: true, maxlength: 30 } },
			span: 12
		},
		{
			label: t('营业时间'),
			prop: 'businessHours',
			component: {
				name: 'el-input',
				props: {
					type: 'textarea',
					rows: 3,
					maxlength: 4000,
					placeholder: '例如：周一至周日 10:00-22:00'
				}
			},
			span: 24
		},
		{
			label: t('状态'),
			prop: 'status',
			component: {
				name: 'el-select',
				options: [
					{ label: t('营业'), value: 1 },
					{ label: t('禁用'), value: 0 },
					{ label: t('停业'), value: 2 },
					{ label: t('关闭'), value: 3 }
				]
			},
			span: 12
		}
	]
});

const Table = useTable({
	autoHeight: !props.embedded,
	props: { showOverflowTooltip: true },
	columns: [
		{ type: 'selection' },

		{
			label: t('门店名称'),
			prop: 'name',
			minWidth: 230
		},
		{
			label: t('所属商家'),
			prop: 'merchantId',
			minWidth: 160,
			hidden: computed(() => selectedMerchant.value != null),
			formatter: (row: Store) => row.merchantName || '商家名称加载失败'
		},
		{ label: t('联系电话'), prop: 'phone', minWidth: 130 },

		{
			label: '位置状态',
			prop: 'locationState',
			minWidth: 120,
			formatter: (row: any) => (hasCoordinates(row) ? '已定位' : '待补充位置')
		},

		{
			label: t('状态'),
			prop: 'status',
			minWidth: 90,
			formatter: (row: Store) => ['禁用', '营业', '停业', '关闭'][row.status] || '未知'
		},
		{ label: '操作', prop: 'actions', width: 175, fixed: 'right' }
	]
});

let optionsRevision = 0;
async function loadOptions() {
	const current = ++optionsRevision;
	try {
		const rows = await service.merchant.storeCategory.list({});
		if (current === optionsRevision) categories.value = rows as unknown as StoreCategory[];
	} catch {
		if (current === optionsRevision) ElMessage.error('门店类型加载失败，请重试');
	}
}
let ready = false;
const scopedService = createMerchantService(
	service.merchant.store,
	() => selectedMerchant.value,
	() => {
		emit('changed');
		void loadOptions();
		void loadSummary();
	}
);
const Crud = useCrud({ service: scopedService }, async app => {
	try {
		await loadOptions();
	} catch {
		ElMessage.error('商家或门店类型加载失败，请重试');
	}
	if (!props.embedded) {
		try {
			merchants.value = await service.merchant.info.list({});
		} catch {
			ElMessage.error('商家列表加载失败，可刷新重试');
		}
	}
	search();
	void loadSummary();
	ready = true;
});
onActivated(() => {
	if (ready)
		void loadOptions()
			.then(() => {
				void Crud.value?.refresh();
				void loadSummary();
			})
			.catch(() => ElMessage.error('关联数据刷新失败，请重试'));
});
</script>
<style scoped>
.store-name-cell {
	display: flex;
	align-items: center;
	gap: 10px;
}
.store-name-cell .el-avatar {
	background: var(--el-color-primary-light-9);
	color: var(--el-color-primary);
	flex: none;
}
.store-actions {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 10px;
}
.store-actions .el-button {
	margin: 0;
}
.store-location-field {
	display: flex;
	flex-wrap: wrap;
	gap: 10px;
	width: 100%;
}
.store-location-field > .el-input {
	flex: 1;
	min-width: 220px;
}
.store-location-field > .el-text {
	width: 100%;
}
</style>

