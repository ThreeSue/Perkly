<template>
	<div class="store-workspace" :class="{ 'is-directory': !props.embedded }">
	<StoreDirectoryHeader v-if="!props.embedded" scope-label="当前商家" :scope-name="selectedMerchantName">
		<template #metrics>
		<div class="directory-counts" role="group" aria-label="按门店状态筛选">
			<button v-for="metric in metrics" :key="metric.status" type="button"
				:aria-pressed="filters.status === metric.status" @click="selectStatus(metric.status)">
				<span>{{ metric.label }}</span>
				<strong>{{ metric.value ?? '—' }}</strong>
			</button>
		</div>
		</template>
		<template #actions><div ref="createSlot" /></template>
	</StoreDirectoryHeader>
	<ManagementShell
		ref="directory"
		class="store-directory"
		title="门店信息"
		:all-hint="summaryRows ? `${summaryRows.length} 家门店` : '查看所有商家的门店'"
		:sidebar="!props.embedded && props.merchantId == null"
		:items="merchantChoices"
		:scope-id="selectedMerchant"
		:scope-name="selectedMerchantName"
		:metrics="metrics"
		@scope-change="selectMerchant"
	>
		<cl-crud ref="Crud">
			<Teleport v-if="!props.embedded && createSlot" :to="createSlot">
				<cl-add-btn class="store-create" :disabled="selectedMerchantDisabled">{{ t('新增门店') }}</cl-add-btn>
			</Teleport>
			<cl-row
				><div class="management-toolbar store-toolbar">
					<div class="store-toolbar-top">
						<el-input v-model="filters.keyword" clearable aria-label="搜索门店"
							:prefix-icon="Search" placeholder="搜索门店名称、电话或地址" @keyup.enter="search" @clear="search" />
						<el-button @click="search">搜索</el-button>
						<el-button @click="resetFilters">重置</el-button>
						<cl-multi-delete-btn v-if="Table?.selection?.length" />
						<el-dropdown trigger="click" @command="listAction">
							<el-button>更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
							<template #dropdown>
								<el-dropdown-menu>
									<el-dropdown-item command="refresh">刷新列表</el-dropdown-item>
									<el-dropdown-item v-if="canMap" command="map">门店分布地图</el-dropdown-item>
								</el-dropdown-menu>
							</template>
						</el-dropdown>
						<cl-add-btn v-if="props.embedded" :disabled="selectedMerchantDisabled">{{ t('新增门店') }}</cl-add-btn>
					</div>
					<div class="store-toolbar-filters">
					<el-radio-group v-model="filters.status" class="store-status-tabs" aria-label="营业状态" @change="search">
						<el-radio-button value="ALL">全部</el-radio-button
						><el-radio-button :value="1">营业</el-radio-button
						><el-radio-button :value="2">停业</el-radio-button
						><el-radio-button :value="0">禁用</el-radio-button
						><el-radio-button :value="3">关闭</el-radio-button>
					</el-radio-group>
					<el-select
						v-model="filters.categoryId"
						aria-label="门店类型"
						clearable
						placeholder="全部门店类型"
						@change="search"
						><el-option
							v-for="item in categories"
							:key="item.id"
							:label="item.name"
							:value="item.id"
					/></el-select>
					</div>
				</div></cl-row>
			<el-alert v-if="statsError || selectedMerchantDisabled" :title="statsError || '所选商家已停用，暂不能新增门店。'" type="warning" :closable="false" />

			<cl-row>
				<cl-table ref="Table">
					<template #empty>
						<el-empty :image-size="54" :description="listError || (hasFilters ? '没有符合筛选条件的门店' : '当前商家范围内暂无门店')">
							<el-button v-if="listError" @click="reloadList">重新加载</el-button>
							<el-button v-else-if="hasFilters" @click="resetFilters">清除筛选</el-button>
						</el-empty>
					</template>
					<template #column-name="{ scope }"
						><div class="store-name-cell">
							<el-avatar v-if="props.embedded" shape="square" :src="scope.row.cover" :size="36">{{
								scope.row.name?.slice(0, 1)
							}}</el-avatar>
							<div style="min-width: 0">
								<div class="cell-main">
									<button v-if="service.merchant.store._permission.info" type="button" class="store-name-link" @click="detail?.open(scope.row)">
										{{ displayStoreName(scope.row) }}
									</button>
									<span v-else>{{ displayStoreName(scope.row) }}</span>
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
								v-if="canManageLocation || user.info?.username === 'admin' || service.merchant.store._permission.delete"
								trigger="click"
								@command="command => storeAction(command, scope.row)"
								><el-button link>更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button
								><template #dropdown
									><el-dropdown-menu
										><el-dropdown-item
											v-if="canManageLocation"
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
						><el-tag size="small" round effect="light" class="store-status" :type="statusTag(scope.row.status)"><i aria-hidden="true" />{{
							['禁用', '营业', '停业', '关闭'][scope.row.status] || '未知'
						}}</el-tag></template
					>
					<template #column-contact="{ scope }">
						<div class="store-contact">
							<div>{{ scope.row.phone || '—' }}</div>
							<div>{{ hasCoordinates(scope.row) ? '已定位' : '待补充位置' }}</div>
						</div>
					</template>
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
	</div>
</template>

<script lang="ts" setup>
defineOptions({ name: 'store-manager' });

import CoverPlaceholder from '/$/space/components/cover-placeholder.vue';
import CoverField from '/$/space/components/cover-field.vue';
import { ref, computed, onActivated, watch, nextTick } from 'vue';
import { ArrowDown, Search } from '@element-plus/icons-vue';
import ManagementShell from '/$/workspace/components/management-shell.vue';
import { useManagementState, useRequestRevision } from '/$/workspace/composables/management-state';
import LocationPicker from './location-picker.vue';
import StoreDetail from './store-editor-detail.vue';
import StoreDirectoryHeader from './store-directory-header.vue';
const detail = ref<InstanceType<typeof StoreDetail>>();
const directory = ref<InstanceType<typeof ManagementShell>>();
import { hasCoordinates, type LocationValue } from '../map/model';
import { mapPermission } from '../map/permission';
import StoreAccounts from './store-accounts.vue';
import { useBase } from '/$/base';
import MerchantSelect from './merchant-select.vue';
import { phoneRule } from './validation';
import { createMerchantService } from './merchant-scope';
import { ElMessage } from 'element-plus';
import { useCrud, useTable, useUpsert } from '@cool-vue/crud';
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
	summaryRows = ref<any[] | null>(null),
	statsError = ref('');
const statsRevision = useRequestRevision();
const listRevision = useRequestRevision();
const listError = ref('');
const hasFilters = computed(() => Boolean(filters.keyword.trim()) || filters.status !== 'ALL' || filters.categoryId != null);
const selectedMerchant = computed(() => props.merchantId ?? filters.merchantId);
const statsRows = computed(() => selectedMerchant.value == null
	? summaryRows.value
	: summaryRows.value?.filter(row => Number(row.merchantId) === selectedMerchant.value));
const merchantChoices = computed(() => {
	if (!summaryRows.value || props.embedded) return merchants.value;
	const counts = new Map<number, number>();
	for (const row of summaryRows.value) {
		const id = Number(row.merchantId);
		counts.set(id, (counts.get(id) ?? 0) + 1);
	}
	return merchants.value.map(merchant => ({ ...merchant, hint: `${counts.get(Number(merchant.id)) ?? 0} 家门店` }));
});
function displayStoreName(row: { name: string; merchantName?: string }) {
	if (props.embedded || selectedMerchant.value != null || !row.merchantName || row.name.includes(row.merchantName)) return row.name;
	return `${row.merchantName} · ${row.name}`;
}
const createSlot = ref<HTMLElement>();
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
	{ label: '门店总数', status: 'ALL', value: statsRows.value?.length },
	{ label: '营业', status: 1, value: statsRows.value?.filter(s => s.status === 1).length },
	{ label: '停业', status: 2, value: statsRows.value?.filter(s => s.status === 2).length },
	{
		label: '禁用 / 关闭',
		status: 'INACTIVE',
		value: statsRows.value?.filter(s => s.status === 0 || s.status === 3).length
	}
]);
function selectStatus(status: string | number) {
	filters.status = status;
	search();
}
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
	const status = filters.status === 'INACTIVE' ? [0, 3] : filters.status;
	void Crud.value?.refresh({
		page: 1,
		merchantId: selectedMerchant.value ?? null,
		keyWord: filters.keyword.trim() || null,
		name: null,
		status: status === 'ALL' ? null : status,
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
	statsError.value = '';
	try {
		const rows = await service.merchant.store.list(
			!props.embedded || selectedMerchant.value == null
				? {}
				: {
						merchantId: selectedMerchant.value
					}
		);
		if (statsRevision.current(revision)) summaryRows.value = rows;
	} catch {
		if (statsRevision.current(revision))
			statsError.value = '统计暂不可用，可继续查看列表并刷新重试。';
	}
}
watch(selectedMerchant, () => {
	if (ready) {
		clearFilters();
		search();
		if (props.embedded) void loadSummary();
	}
});
const canMap = computed(() => mapPermission('store:map'));
const canManageLocation = computed(() => mapPermission('store:location:update'));
function reloadList() {
	void Crud.value?.refresh();
	void loadSummary();
	if (!props.embedded) void loadMerchants();
}
function listAction(command: string) {
	if (command === 'map') openMap();
	if (command === 'refresh') reloadList();
}
const formLocation = ref<InstanceType<typeof LocationPicker>>();
const locationEditor = ref<InstanceType<typeof LocationPicker>>();
function applyLocation(value: LocationValue) {
	Upsert.value?.setForm('address', value.address);
	Upsert.value?.setForm('longitude', value.longitude);
	Upsert.value?.setForm('latitude', value.latitude);
}
async function locationSaved() {
	const getScroll = () => (directory.value?.$el as HTMLElement | undefined)
		?.querySelector('.el-table__body-wrapper .el-scrollbar__wrap');
	const scroll = getScroll();
	const top = scroll?.scrollTop ?? 0;
	void loadSummary();
	emit('changed');
	await Crud.value?.refresh();
	await nextTick();
	getScroll()?.scrollTo({ top });
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
			minWidth: 270,
			align: 'left'
		},
		{
			label: t('营业状态'),
			prop: 'status',
			minWidth: 92,
			align: 'left',
			formatter: (row: Store) => ['禁用', '营业', '停业', '关闭'][row.status] || '未知'
		},
		{ label: t('联系方式'), prop: 'contact', minWidth: 190, align: 'left' },
		{ label: '操作', prop: 'actions', width: 126, align: 'right', fixed: 'right' }
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
async function loadMerchants() {
	try {
		merchants.value = await service.merchant.info.list({});
	} catch {
		ElMessage.error('商家列表加载失败，可在「更多」中刷新重试');
	}
}
const Crud = useCrud({
	service: scopedService,
	async onRefresh(params, { render }) {
		const request = listRevision.next();
		listError.value = '';
		try {
			const result = await scopedService.page(params);
			if (listRevision.current(request)) render(result);
		} catch {
			if (listRevision.current(request)) {
				listError.value = '门店列表加载失败';
				render([], { total: 0, page: Number(params.page) || 1, size: Number(params.size) || 20 });
			}
		}
	}
}, async () => {
	await Promise.all([loadOptions(), props.embedded ? undefined : loadMerchants()]);
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
.store-workspace {
	height: 100%;
	min-height: 0;
}
.store-workspace.is-directory {
	display: flex;
	flex-direction: column;
	gap: 16px;
	padding: 0;
	box-sizing: border-box;
	background: color-mix(in srgb, var(--el-bg-color-page) 94%, #3156bc);
	color: var(--el-text-color-primary);
}
.is-directory .store-directory { flex: 1; height: auto; }
.is-directory :deep(.management-heading) { display: none; }
.is-directory .store-directory { gap: 14px; background: transparent; }
.is-directory .store-directory :deep(.management-sidebar) {
	--selector-rule: color-mix(in srgb, var(--el-border-color) 88%, var(--el-color-primary));
	background: color-mix(in srgb, var(--el-bg-color) 96%, var(--el-color-primary));
	border-color: var(--selector-rule);
	border-radius: 8px;
}
.is-directory :deep(.management-sidebar > .scope-list) {
	gap: 3px;
	padding-block-start: 8px;
	border-block-start: 1px solid var(--selector-rule);
}
@media (max-width: 850px) {
	.is-directory :deep(.management-sidebar > .scope-list) {
		padding-block-start: 0;
		border-block-start: 0;
		padding-inline-start: 8px;
		border-inline-start: 1px solid var(--selector-rule);
	}
}
.is-directory :deep(.management-body) {
	padding: 0;
	border: 0;
	border-radius: 0;
	background: transparent;
}
.is-directory :deep(.management-body > .cl-crud) {
	padding: 0;
	background-color: transparent;
}
.is-directory :deep(.management-sidebar .scope-avatar) { border-radius: 4px; }
.is-directory :deep(.scope-option) { border-radius: 6px; }
.is-directory :deep(.scope-option:not(.active) .scope-avatar) { background: var(--el-bg-color); }
.is-directory :deep(.scope-heading strong) { font-size: 15px; font-weight: 700; }
.is-directory :deep(.scope-option .scope-copy) { flex: 1; gap: 2px; }
.is-directory :deep(.scope-option .scope-copy strong) { font-size: 13px; font-weight: 650; }
.is-directory :deep(.scope-option .scope-copy small) { font-variant-numeric: tabular-nums; }
.is-directory :deep(.scope-option.active .scope-avatar) { background: var(--el-color-primary); color: #fff; border-radius: 6px; }
.is-directory :deep(.scope-option.active .scope-copy strong) { color: var(--el-color-primary); font-weight: 750; }
.is-directory :deep(.scope-option.active::after) {
	content: '';
	flex: 0 0 5px;
	height: 9px;
	margin: 0 5px 3px 0;
	border: solid var(--el-color-primary);
	border-width: 0 2px 2px 0;
	transform: rotate(45deg);
}
.store-directory {
	--directory-surface: color-mix(in srgb, var(--el-fill-color-light) 78%, var(--el-color-primary) 2%, var(--el-bg-color));
	--directory-rule: color-mix(in srgb, var(--el-border-color-lighter) 85%, var(--el-bg-color));
	background: var(--directory-surface);
}
.store-directory :deep(.management-heading),
.store-directory :deep(.management-sidebar),
.store-directory :deep(.management-body) {
	border-color: var(--directory-rule);
}
.store-directory :deep(.management-heading) {
	padding: 8px 12px;
}
.store-directory :deep(.heading-copy p),
.store-directory :deep(.scope-footer) {
	display: none;
}
.store-directory :deep(.heading-scope) {
	background: var(--el-fill-color-light);
	color: var(--el-text-color-regular);
}
.store-directory :deep(.management-metrics) {
	gap: 0;
}
.store-directory :deep(.management-metric) {
	flex-direction: row;
	align-items: baseline;
	gap: 7px;
	min-width: 0;
	padding: 3px 10px;
	border: 0;
	border-radius: 0;
	background: transparent;
}
.store-directory :deep(.management-metric + .management-metric) {
	border-inline-start: 1px solid var(--el-border-color-lighter);
}
.store-directory :deep(.management-metric strong) {
	color: var(--el-text-color-primary);
	font-size: 16px;
}
.store-directory :deep(.scope-option) {
	min-height: 44px;
	padding-block: 5px;
}
.store-directory :deep(.scope-avatar) {
	border-radius: 6px;
	line-height: 1;
}
.store-directory :deep(.scope-option:not(.active) .scope-avatar) {
	background: var(--el-fill-color);
	color: var(--el-text-color-regular);
}
.store-directory :deep(.scope-option.active) {
	background: var(--el-color-primary-light-9);
	border-color: var(--el-color-primary-light-7);
}
.store-directory :deep(.el-table__cell) {
	padding-block: 7px;
}
.store-directory :deep(td.store-phone) {
	font-size: 12px;
	color: var(--el-text-color-regular);
	font-variant-numeric: tabular-nums;
}
.store-directory .store-name-cell .cell-main {
	font-size: 15px;
	font-weight: 700;
}
.store-directory :deep(.el-table th.el-table__cell) {
	background: var(--directory-surface);
	color: var(--el-text-color-regular);
	font-weight: 600;
}
.store-toolbar {
	width: 100%;
	display: grid;
	gap: 6px;
	margin-bottom: 0;
}
.is-directory .store-toolbar {
	display: flex;
	justify-content: space-between;
	flex-wrap: wrap;
	gap: 8px 14px;
	padding: 12px 14px;
	box-sizing: border-box;
}
.is-directory .store-toolbar-top :deep(.el-input) { width: min(230px, 100%); }
.is-directory .store-toolbar-top { flex: 1 1 440px; }
.is-directory .store-toolbar-filters { padding: 0; border: 0; }
.is-directory .store-status-tabs { gap: 5px; flex-wrap: wrap; }
.is-directory .store-status-tabs :deep(.el-radio-button__inner) {
	padding: 7px 9px;
	border: 0;
	border-radius: 6px;
	background: var(--el-fill-color-light);
}
.is-directory .store-status-tabs :deep(.el-radio-button.is-active .el-radio-button__inner) {
	background: #254dc8;
	color: white;
}
.is-directory :deep(.el-table) {
	--el-table-border: 1px solid var(--el-border-color-lighter);
}
.is-directory :deep(.el-table--border .el-table__cell) { border-right: 0; }
.is-directory :deep(.el-table--border::before),
.is-directory :deep(.el-table--border::after),
.is-directory :deep(.el-table__border-left-patch) { display: none; }
.store-contact { font-size: 12px; font-variant-numeric: tabular-nums; }
.store-contact > div + div { color: var(--el-text-color-regular); }
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
	.store-workspace.is-directory { padding: 0; }
}
.store-toolbar-top,
.store-toolbar-filters { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.store-toolbar-top :deep(.el-button) { margin-left: 0; }
.store-toolbar-filters { padding-top: 4px; border-bottom: 1px solid var(--el-border-color-lighter); }
.store-toolbar-top :deep(.el-input) { width: min(320px, 100%); }
.store-toolbar :deep(.el-select) { width: 136px; }
.store-status-tabs :deep(.el-radio-button__inner) {
	padding: 10px 13px;
	border: 0;
	border-bottom: 2px solid transparent;
	border-radius: 0;
	background: transparent;
	box-shadow: none;
}
.store-status-tabs :deep(.el-radio-button.is-active .el-radio-button__inner) {
	border-bottom-color: var(--el-color-primary);
	background: transparent;
	color: var(--el-color-primary);
	font-weight: 700;
}

.store-name-link {
	padding: 0;
	border: 0;
	background: transparent;
	color: inherit;
	font: inherit;
	font-weight: 700;
	text-align: left;
	cursor: pointer;
}
.store-name-link:hover { text-decoration: underline; text-underline-offset: 3px; }
.store-name-link:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.store-name-cell {
	display: flex;
	align-items: center;
	gap: 10px;
}
.store-name-cell .el-avatar {
	background: var(--el-fill-color);
	color: var(--el-text-color-regular);
	flex: none;
}
.store-actions {
	display: flex;
	align-items: center;
	justify-content: flex-end;
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
