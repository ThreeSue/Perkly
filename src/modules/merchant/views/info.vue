<template>
	<cl-crud ref="Crud" padding="0" class="merchant-directory-crud">
		<WorkbenchLayout title="商家信息" context="查找平台商家，查看资料与门店，并进入账号管理。" class="merchant-directory">
			<template #actions><cl-add-btn class="merchant-add">新增商家</cl-add-btn></template>
			<template #metrics>
				<button
					v-for="item in metrics"
					:key="item.status"
					type="button"
					class="workbench-banner-metric merchant-stat"
					:aria-pressed="quickStatus === item.status"
					@click="chooseStatus(item.status)"
				><span>{{ item.label }}</span><strong>{{ item.value ?? '—' }}</strong></button>
			</template>
			<form class="merchant-filters" aria-label="筛选商家" @submit.prevent="applyFilters">
				<el-input v-model="keyword" :prefix-icon="SearchIcon" maxlength="100" clearable placeholder="商家名称 / 联系电话" aria-label="商家名称或联系电话" />
				<el-cascader
					v-model="filterCategory"
					:options="searchCategoryOptions"
					:props="{ emitPath: false, checkStrictly: true }"
					filterable clearable
					placeholder="全部商家分类"
					aria-label="商家分类"
				/>
				<el-button type="primary" native-type="submit">搜索</el-button>
				<el-button @click="clearFilters">重置</el-button>
				<span class="merchant-toolbar-spacer" />
				<span v-if="summaryError" class="merchant-summary-error" role="status">{{ summaryError }}</span>
				<cl-refresh-btn />
			</form>
			<div class="merchant-results">
				<cl-table ref="Table">
					<template #column-actions="{ scope }"
						><div class="merchant-row-actions">
							<el-button
								v-if="service.merchant.info._permission.info"
								link
								type="primary"
								@click="Detail?.open(scope.row.id)"
								>查看</el-button
							><el-button
								v-if="canAccounts"
								link
								type="primary"
								@click="
									router.push({
										path: '/workspace-admin/accounts',
										query: { merchantId: scope.row.id }
									})
								"
								>账号</el-button
							>
						</div></template
					>
					<template #column-name="{ scope }"
						><div class="merchant-cell">
							<el-avatar shape="square" :src="scope.row.logo" :size="38">{{
								Array.from(scope.row.name || '商')[0]
							}}</el-avatar>
							<div style="min-width: 0">
								<button
									v-if="service.merchant.info._permission.info"
									type="button"
									class="merchant-name workbench-link"
									:title="scope.row.name"
									@click="Detail?.open(scope.row.id)"
								>{{ scope.row.name }}</button>
								<strong v-else class="merchant-name" :title="scope.row.name">{{ scope.row.name }}</strong>
								<div class="cell-sub">{{ categoryName(scope.row.categoryId) }}</div>
							</div>
						</div></template
					>
					<template #column-contactName="{ scope }"
						><div>{{ scope.row.contactName || '未填写联系人' }}</div>
						<div class="cell-sub">
							{{ scope.row.contactPhone || '未填写电话' }}
						</div></template
					>
					<template #column-storeCount="{ scope }"
						><el-button
							v-if="service.merchant.info._permission.info && service.merchant.store._permission.page"
							link
							type="primary"
							@click="Detail?.open(scope.row.id, 'stores')"
							>{{ scope.row.storeCount ?? '—' }} 家门店</el-button>
						<span v-else>{{ scope.row.storeCount ?? '—' }} 家门店</span></template
					>
					<template #column-status="{ scope }"
						><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{
							scope.row.status === 1 ? '启用' : '停用'
						}}</el-tag></template
					>

				</cl-table>
			</div>
			<div class="merchant-pagination"><cl-pagination /></div>
			<cl-upsert ref="Upsert">
				<template #slot-category="{ scope }">
					<el-cascader
						v-model="scope.categoryId"
						:options="categoryOptions"
						:props="{ emitPath: false, checkStrictly: true }"
						filterable
						clearable
						placeholder="请选择所属商家分类"
						style="width: 100%"
					/>
				</template>
			</cl-upsert>
		<MerchantDetail
			ref="Detail"
			:category-name="categoryName"
			:category-options="categoryOptions"
			@changed="
				() => {
					Crud?.refresh();
					loadSummary();
				}
			"
		/>

		</WorkbenchLayout>
	</cl-crud>
</template>

<script lang="ts" setup>
defineOptions({
	name: 'merchant-info'
});

import { useCrud, useTable, useUpsert } from '@cool-vue/crud';
import { useCool } from '/@/cool';
import { useI18n } from 'vue-i18n';
import { computed, ref, watch } from 'vue';
import { Search as SearchIcon } from '@element-plus/icons-vue';
import WorkbenchLayout from '/$/workspace/components/workbench-layout.vue';
import { useBase } from '/$/base';
import { useRequestRevision } from '/$/workspace/composables/management-state';
import { phoneRule } from '../components/validation';
import MerchantDetail from '../components/merchant-detail.vue';
const Detail = ref<InstanceType<typeof MerchantDetail>>();
import { ElMessage, type CascaderOption } from 'element-plus';

const { service, route, router } = useCool();
const { user, menu } = useBase();
const canAccounts = computed(
	() => user.info?.username === 'admin' || menu.perms.includes('workspace:administration:manage')
);
const { t } = useI18n();
const pagePath = route.path;
let ready = false;
type Status = 'ALL' | 0 | 1;
const quickStatus = ref<Status>('ALL'),
	keyword = ref(''),
	filterCategory = ref<number>(),

	summaryRows = ref<any[] | null>(null),
	summaryError = ref(''),
	summaryRevision = useRequestRevision();
const metrics = computed(() => [
	{ status: 'ALL' as const, label: '商家总数', value: summaryRows.value?.length },
	{ status: 1 as const, label: '启用', value: summaryRows.value?.filter(m => m.status === 1).length },
	{ status: 0 as const, label: '停用', value: summaryRows.value?.filter(m => m.status === 0).length }
]);
async function loadSummary() {
	const revision = summaryRevision.next();
	summaryError.value = '';
	try {
		const rows = await service.merchant.info.list({});
		if (summaryRevision.current(revision)) summaryRows.value = rows;
	} catch {
		if (summaryRevision.current(revision)) {
			summaryRows.value = null;
			summaryError.value = '统计暂不可用，可刷新重试。';
		}
	}
}
function chooseStatus(status: Status) {
	quickStatus.value = status;
	applyFilters();
}
function applyFilters() {
	Crud.value?.refresh({
		page: 1,
		keyWord: keyword.value.trim() || null,
		categoryId: categoryFilter(filterCategory.value),
		status: quickStatus.value === 'ALL' ? null : quickStatus.value
	});
}
function clearFilters() {
	keyword.value = '';
	filterCategory.value = undefined;
	chooseStatus('ALL');
}

interface Category {
	id: number;
	parentId?: number | null;
	name: string;
	status: number;
	sort?: number;
}
const categories = ref<Category[]>([]);
const categoryMap = computed(() => new Map(categories.value.map(item => [String(item.id), item])));
function buildCategoryOptions(disableInactive: boolean) {
	const nodes = new Map<string, CascaderOption>();
	const roots: CascaderOption[] = [];
	const ordered = [...categories.value].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0));
	for (const item of ordered) {
		nodes.set(String(item.id), {
			value: item.id,
			label: item.name,
			disabled: disableInactive && item.status !== 1
		});
	}
	for (const item of ordered) {
		const node = nodes.get(String(item.id))!;
		const parent = nodes.get(String(item.parentId));
		if (parent && parent !== node) {
			(parent.children ??= []).push(node);
		} else {
			roots.push(node);
		}
	}
	return roots;
}
const categoryOptions = computed(() => buildCategoryOptions(true));
const searchCategoryOptions = computed(() => buildCategoryOptions(false));

function categoryName(id: number) {
	const names: string[] = [];
	const visited = new Set<string>();
	let item = categoryMap.value.get(String(id));
	while (item && !visited.has(String(item.id))) {
		visited.add(String(item.id));
		names.unshift(item.name);
		item = categoryMap.value.get(String(item.parentId));
	}
	return names.join(' / ') || '分类不可用';
}

function categoryFilter(id?: number | null) {
	if (id == null) return null;
	const ids = new Set<number>([id]);
	const pending = [id];
	while (pending.length) {
		const parentId = pending.pop();
		for (const item of categories.value) {
			if (item.parentId === parentId && !ids.has(item.id)) {
				ids.add(item.id);
				pending.push(item.id);
			}
		}
	}
	return [...ids];
}

// cl-upsert
const Upsert = useUpsert({
	onOpen() {
		Upsert.value?.setProps('licenseNumber', { disabled: false });
	},
	onOpened(data) {
		Upsert.value?.setProps('licenseNumber', { disabled: !!data.licenseNumber });
	},
	onClose(action, done) {
		done();
		if (action === 'save') void Detail.value?.refresh();
	},
	items: [
		{
			label: '营业执照号（首次填写后锁定）',
			prop: 'licenseNumber',
			component: { name: 'el-input', props: { maxlength: 18 } },
			span: 24
		},
		{
			label: t('商家名称'),
			prop: 'name',
			component: { name: 'el-input', props: { clearable: true } },
			span: 12,
			required: true
		},
		{
			label: t('选择所属商家分类'),
			prop: 'categoryId',
			component: { name: 'slot-category' },
			span: 12,
			required: true
		},
		{
			label: t('商家Logo'),
			prop: 'logo',
			component: {
				name: 'cl-upload',
				props: { type: 'image', accept: 'image/*', limit: 1, limitSize: 5 }
			},
			span: 12
		},
		{
			label: t('联系人姓名'),
			prop: 'contactName',
			component: { name: 'el-input', props: { clearable: true } },
			span: 12,
			required: true
		},
		{
			label: t('联系电话'),
			prop: 'contactPhone',
			rules: [{ required: true, message: '请输入联系电话', trigger: 'blur' }, phoneRule],
			component: { name: 'el-input', props: { clearable: true } },
			span: 12
		},
		{
			label: t('省市区'),
			prop: 'region',
			span: 24,
			hook: {
				bind(_value, { form }) {
					return [form.province, form.city, form.district].filter(Boolean);
				},
				submit(value, { form, prop }) {
					const [province, city, district] = value || [];
					Object.assign(form, {
						province: province ?? '',
						city: city ?? '',
						district: district ?? ''
					});
					form[prop] = undefined;
					return undefined;
				}
			},
			component: { name: 'cl-distpicker' }
		},
		{
			label: t('详细地址'),
			prop: 'address',
			component: { name: 'el-input', props: { clearable: true } },
			span: 12
		},
		{
			label: t('商家简介'),
			prop: 'introduction',
			component: { name: 'el-input', props: { clearable: true } },
			span: 12
		},
		{
			label: t('状态'),
			prop: 'status',
			component: {
				name: 'el-select',
				options: [
					{ label: t('启用'), value: 1 },
					{ label: t('停用'), value: 0 }
				]
			},
			span: 12,
			required: true
		},
		{
			label: t('平台内部备注'),
			prop: 'remark',
			component: { name: 'el-input', props: { clearable: true } },
			span: 12
		}
	]
});

// cl-table
const Table = useTable({
	height: '100%',
	emptyText: '没有匹配的商家，请调整筛选条件。',
	props: { showOverflowTooltip: true },
	columns: [
		{ label: t('商家'), prop: 'name', minWidth: 250, align: 'left', showOverflowTooltip: false },

		{ label: t('联系信息'), prop: 'contactName', minWidth: 165, align: 'left' },

		{
			label: t('门店数'),
			prop: 'storeCount',
			minWidth: 90,
			formatter: row => (row.storeCount == null ? '—' : String(row.storeCount))
		},
		{
			label: t('状态'),
			prop: 'status',
			minWidth: 90,
			formatter: row => (Number(row.status) === 1 ? t('启用') : t('停用'))
		},
		{
			label: t('创建时间'),
			prop: 'createTime',
			minWidth: 170,
			sortable: 'desc',
			component: { name: 'cl-date-text' }
		},
		{ label: '操作', prop: 'actions', width: 125, fixed: 'right', align: 'right' }
	]
});

// Cached menu pages must also receive a newly selected category on re-entry.
function applyCategoryLink() {
	const value = route.query.categoryId;
	if (typeof value !== 'string') return false;
	const id = Number(value);
	if (!Number.isSafeInteger(id) || id <= 0 || !categories.value.some(item => item.id === id)) {
		ElMessage.warning('该商家分类不存在或已被删除，请重新选择分类');
		return false;
	}
	keyword.value = '';
	quickStatus.value = 'ALL';
	filterCategory.value = id;
	applyFilters();
	return true;
}
watch(
	() => route.fullPath,
	async path => {
		if (!ready || route.path !== pagePath || typeof route.query.categoryId !== 'string') return;
		try {
			const rows = await service.merchant.category.list();
			if (route.fullPath !== path) return;
			categories.value = rows as unknown as Category[];
			applyCategoryLink();
		} catch {
			ElMessage.error('分类加载失败，请刷新页面重试');
		}
	}
);

// cl-crud
const Crud = useCrud(
	{
		service: service.merchant.info,
		onRefresh(params, { next }) {
			void loadSummary();
			next(params);
		}
	},
	async app => {
		try {
			categories.value = (await service.merchant.category.list()) as unknown as Category[];
		} catch {
			ElMessage.error('商家分类加载失败，请刷新页面重试');
		}
		ready = true;
		void loadSummary();
		if (!applyCategoryLink()) app.refresh();
	}
);
</script>

<style scoped>
.merchant-directory-crud { background: transparent; }
.merchant-directory {
	display: flex;
	flex-direction: column;
	min-height: 0;
	gap: 12px;
}
.merchant-directory :deep(.workbench-banner) { flex: none; }
.merchant-stat {
	border: 0;
	border-inline-end: 1px solid #ffffff29;
	background: transparent;
	font: inherit;
	text-align: start;
	cursor: pointer;
}
.merchant-stat:hover { background: #ffffff13; }
.merchant-stat[aria-pressed="true"] { background: #ffffff13; box-shadow: inset 0 -3px #ffcf55; }
.merchant-stat:focus-visible { outline: 2px solid white; outline-offset: -4px; }
.merchant-directory :deep(.merchant-add) {
	--el-button-bg-color: #ffcf55;
	--el-button-border-color: #ffcf55;
	--el-button-text-color: #19223a;
	--el-button-hover-bg-color: #ffe08f;
	--el-button-hover-border-color: #ffe08f;
	--el-button-hover-text-color: #19223a;
	font-weight: 700;
}
.merchant-filters { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.merchant-filters .el-input { width: min(260px, 100%); }
.merchant-filters .el-cascader { width: min(205px, 100%); }
.merchant-filters .el-button { margin: 0; }
.merchant-toolbar-spacer { flex: 1; }
.merchant-summary-error { color: var(--el-text-color-regular); font-size: 12px; }
.merchant-results { flex: 1 1 0; min-height: 175px; overflow: hidden; border-radius: 7px; }
.merchant-results :deep(.cl-table) { height: 100%; }
.merchant-results :deep(.el-table) { --el-table-header-bg-color: var(--workspace-neutral); --el-table-row-hover-bg-color: var(--workspace-selected); }
.merchant-pagination { display: flex; justify-content: flex-end; }
.merchant-pagination :deep(.el-pagination) { flex-wrap: wrap; row-gap: 6px; }
.merchant-row-actions { display: flex; gap: 9px; align-items: center; justify-content: flex-end; }
.merchant-row-actions .el-button { margin: 0; }
.merchant-cell { display: flex; gap: 11px; align-items: center; }
.merchant-cell .el-avatar { background: var(--workspace-selected); color: var(--workspace-accent-text); flex: none; }
.merchant-name {
	display: block;
	max-width: 100%;
	overflow: hidden;
	padding: 0;
	border: 0;
	background: transparent;
	text-align: start;
	text-overflow: ellipsis;
	white-space: nowrap;
	color: var(--workspace-ink);
	font: inherit;
	font-weight: 700;
}
button.merchant-name { cursor: pointer; }
button.merchant-name:hover { color: var(--workspace-accent-text); text-decoration: underline; text-underline-offset: 3px; }
.merchant-cell .cell-sub { margin-top: 3px; }
@media (max-width: 590px) {
	.merchant-toolbar-spacer { display: none; }
	.merchant-results { flex-basis: 330px; }
}
</style>
