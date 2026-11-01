<template>
	<ManagementShell
		title="商家信息"
		eyebrow="商家经营管理"
		description="集中查看商家资料，快速进入门店、账号和授权管理"
		scope-name="平台商家"
		:metrics="metrics"
	>
		<cl-crud ref="Crud">
			<cl-row>
				<!-- 刷新按钮 -->
				<cl-refresh-btn />
				<!-- 新增按钮 -->
				<cl-add-btn />

				<cl-flex1 />
			</cl-row>
			<cl-row
				><div class="management-toolbar">
					<span class="filter-label">经营状态</span
					><el-radio-group v-model="quickStatus" @change="chooseStatus"
						><el-radio-button value="ALL">全部</el-radio-button
						><el-radio-button :value="1">启用</el-radio-button
						><el-radio-button :value="0">停用</el-radio-button></el-radio-group
					>
					<p class="management-caption">
						顶部统计为全平台商家；入驻申请在“审核管理”处理。{{ summaryError }}
					</p>
				</div></cl-row
			>
			<cl-row>
				<cl-search ref="Search" reset-btn>
					<template #slot-search-category="{ scope }">
						<el-cascader
							v-model="scope.categoryId"
							:options="searchCategoryOptions"
							:props="{ emitPath: false, checkStrictly: true }"
							filterable
							clearable
							placeholder="全部商家分类"
							style="width: 240px"
						/>
					</template>
				</cl-search>
			</cl-row>

			<cl-row>
				<!-- 数据表格 -->
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
								scope.row.name?.slice(0, 1)
							}}</el-avatar>
							<div style="min-width: 0">
								<div class="cell-main">{{ scope.row.name }}</div>
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
							link
							type="primary"
							@click="Detail?.open(scope.row.id, 'stores')"
							>{{ scope.row.storeCount ?? '—' }} 家门店</el-button
						></template
					>
					<template #column-status="{ scope }"
						><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{
							scope.row.status === 1 ? '启用' : '停用'
						}}</el-tag></template
					>
					<template #column-categoryId="{ scope }">
						{{ categoryName(scope.row.categoryId) }}
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
		</cl-crud>
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
	</ManagementShell>
</template>

<script lang="ts" setup>
defineOptions({
	name: 'merchant-info'
});

import { useCrud, useTable, useUpsert, useSearch } from '@cool-vue/crud';
import { useCool } from '/@/cool';
import { useI18n } from 'vue-i18n';
import { computed, ref, watch } from 'vue';
import ManagementShell from '/$/workspace/components/management-shell.vue';
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
const quickStatus = ref<string | number>('ALL'),
	summaryRows = ref<any[] | null>(null),
	summaryError = ref(''),
	summaryRevision = useRequestRevision();
const metrics = computed(() => [
	{ label: '商家总数', value: summaryRows.value?.length },
	{ label: '启用', value: summaryRows.value?.filter(m => m.status === 1).length },
	{ label: '停用', value: summaryRows.value?.filter(m => m.status === 0).length }
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
function chooseStatus() {
	Search.value?.setForm('status', quickStatus.value === 'ALL' ? undefined : quickStatus.value);
	Crud.value?.refresh({
		page: 1,
		status: quickStatus.value === 'ALL' ? null : quickStatus.value
	});
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
	props: { showOverflowTooltip: true },
	columns: [
		{ label: t('商家'), prop: 'name', minWidth: 220 },

		{ label: t('联系信息'), prop: 'contactName', minWidth: 170 },

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
		{ label: '操作', prop: 'actions', width: 185, fixed: 'right' }
	]
});

// cl-search
const Search = useSearch({
	items: [
		{
			label: '名称 / 电话',
			prop: 'keyWord',
			component: {
				name: 'el-input',
				props: { clearable: true, placeholder: '商家名称 / 联系电话', maxlength: 100 },
				style: { width: '180px' }
			}
		},
		{ label: t('商家分类'), prop: 'categoryId', component: { name: 'slot-search-category' } },
		{
			label: t('状态'),
			hidden: true,
			prop: 'status',
			component: {
				name: 'el-select',
				props: { clearable: true, placeholder: '全部状态' },
				options: [
					{ label: t('启用'), value: 1 },
					{ label: t('停用'), value: 0 }
				],
				style: { width: '140px' }
			}
		}
	],
	onSearch(data, { next }) {
		quickStatus.value = data.status ?? 'ALL';
		next({
			keyWord: data.keyWord?.trim() || null,
			categoryId: categoryFilter(data.categoryId),
			status: data.status ?? null
		});
	}
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
	Search.value?.setForm('keyWord', undefined);
	quickStatus.value = 'ALL';
	Search.value?.setForm('status', undefined);
	Search.value?.setForm('categoryId', id);
	Crud.value?.refresh({ page: 1, keyWord: null, status: null, categoryId: categoryFilter(id) });
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
.merchant-row-actions {
	display: flex;
	gap: 12px;
	align-items: center;
	justify-content: center;
}
.merchant-row-actions .el-button {
	margin: 0;
}
.merchant-cell {
	display: flex;
	gap: 12px;
	align-items: center;
}
.merchant-cell .el-avatar {
	background: var(--el-color-primary-light-9);
	color: var(--el-color-primary);
	flex: none;
}
</style>
