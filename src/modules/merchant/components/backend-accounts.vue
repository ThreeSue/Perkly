<template>
	<div class="account-layout">
		<aside>
			<div class="tree-heading">
				<strong>商家与门店</strong><el-button text @click="loadGroups">刷新</el-button>
			</div>
			<div class="tree-filters">
				<el-tree-select
					v-model="categoryId"
					:data="categoryOptions"
					:disabled="groupsLoading || !categorySupported"
					:props="{ label: 'name', value: 'id', children: 'children' }"
					node-key="id"
					check-strictly
					clearable
					filterable
					placeholder="全部商家分类"
					aria-label="商家分类"
					@change="changeCategory"
				/>
				<el-input v-model="treeWord" clearable placeholder="搜索商家或门店" />
				<div class="tree-summary">{{ visibleGroups.length }} 家商家 · 展开查看门店</div>
				<el-text v-if="!groupsLoading && !categorySupported" type="warning" size="small"
					>重启后端后可使用分类筛选</el-text
				>
			</div>
			<el-scrollbar class="tree-scroll"
				><el-tree
					ref="tree"
					:data="nodes"
					node-key="key"
					:props="{ label: 'label', children: 'children' }"
					:filter-node-method="filterNode"
					highlight-current
					:current-node-key="selected.key"
					:expand-on-click-node="false"
					:indent="22"
					empty-text="此分类下暂无商家"
					@node-click="selectGroup"
					><template #default="{ data }"
						><div class="group-node" :title="data.label">
							<el-icon
								><Shop v-if="data.storeId" /><OfficeBuilding
									v-else-if="data.merchantId" /><FolderOpened v-else /></el-icon
							><span class="node-label">{{ data.label }}</span
							><span v-if="data.children?.length" class="node-count"
								>{{ data.children.length }} 店</span
							>
						</div></template
					></el-tree
				></el-scrollbar
			>
		</aside>
		<main>
			<el-alert
				title="商家管理员由入驻审核开通；店长、员工归属唯一门店，不分配平台运营权限。"
				type="info"
				:closable="false"
				show-icon
			/>
			<div class="toolbar">
				<strong>{{ selected.label }}</strong
				><el-button @click="load">刷新</el-button>
				<el-button
					v-if="selected.storeId"
					type="primary"
					@click="manageStore(selected.storeId, selected.label)"
					>管理本店账号 / 新增员工</el-button
				>
			</div>
			<div class="toolbar">
				<el-select
					v-model="filters.role"
					clearable
					placeholder="全部身份"
					style="width: 160px"
					aria-label="账号身份"
					><el-option
						v-for="(label, value) in roles"
						:key="value"
						:label="label"
						:value="value"
				/></el-select>
				<el-select
					v-model="filters.status"
					clearable
					placeholder="全部状态"
					style="width: 140px"
					aria-label="账号状态"
					><el-option label="启用" :value="1" /><el-option label="停用" :value="0"
				/></el-select>
				<el-input
					v-model="filters.keyWord"
					clearable
					maxlength="100"
					placeholder="搜索登录账号或姓名"
					style="width: 240px"
					@keyup.enter="search"
				/>
				<el-button type="primary" @click="search">搜索</el-button
				><el-button @click="reset">重置</el-button>
			</div>
			<el-table v-loading="loading" :data="rows" border height="100%" show-overflow-tooltip>
				<el-table-column prop="username" label="登录账号" min-width="160" /><el-table-column
					prop="name"
					label="姓名"
					min-width="120"
				/>
				<el-table-column label="账号身份" width="140"
					><template #default="{ row }"
						><el-tag
							:type="
								row.role === 'OWNER'
									? 'primary'
									: row.role === 'MANAGER'
										? 'warning'
										: 'info'
							"
							>{{ roles[row.role] || '未知身份' }}</el-tag
						></template
					></el-table-column
				>
				<el-table-column
					prop="merchantName"
					label="所属商家"
					min-width="170"
				/><el-table-column prop="storeName" label="管理范围 / 门店" min-width="170" />
				<el-table-column label="状态" width="90"
					><template #default="{ row }"
						><el-tag :type="row.status === 1 ? 'success' : 'danger'">{{
							row.status === 1 ? '启用' : '停用'
						}}</el-tag></template
					></el-table-column
				>
				<el-table-column label="操作" width="240" fixed="right"
					><template #default="{ row }">
						<template v-if="row.role === 'OWNER'"
							><el-button text type="primary" @click="editOwner(row)"
								>编辑 / 状态</el-button
							><el-button
								text
								type="warning"
								:disabled="busy"
								@click="resetOwner(row)"
								>重置密码</el-button
							></template
						>
						<el-button
							v-else-if="row.bindingValid && row.storeId"
							text
							type="primary"
							@click="manageStore(row.storeId, row.storeName)"
							>管理门店账号</el-button
						>
						<el-tag v-else type="danger">授权异常，需核对</el-tag>
					</template></el-table-column
				>
			</el-table>
			<el-pagination
				v-model:current-page="page"
				:page-size="20"
				:total="total"
				layout="total, prev, pager, next"
				@current-change="load"
			/>
		</main>
		<cl-dialog
			v-model="storeVisible"
			:title="storeName + ' · 门店账号管理'"
			width="1100px"
			height="70vh"
			@closed="load"
			><StoreAccounts
				v-if="storeVisible && storeId"
				:key="storeId"
				:store-id="storeId"
				:store-name="storeName"
		/></cl-dialog>
		<el-dialog v-model="ownerVisible" title="编辑商家管理员" width="480px">
			<el-form label-width="90px" @submit.prevent="saveOwner">
				<el-form-item label="登录账号"
					><el-input :model-value="owner.username" readonly /></el-form-item
				><el-form-item label="所属商家"
					><el-input :model-value="owner.merchantName" readonly
				/></el-form-item>
				<el-form-item label="姓名" required
					><el-input v-model="owner.name" maxlength="50"
				/></el-form-item>
				<el-form-item label="账号状态"
					><el-radio-group v-model="owner.status"
						><el-radio :value="1">启用</el-radio
						><el-radio :value="0">停用</el-radio></el-radio-group
					></el-form-item
				>
				<el-text type="info"
					>变更状态会使原登录失效。商家归属和账号身份不可在此修改。</el-text
				>
			</el-form>
			<template #footer
				><el-button @click="ownerVisible = false">取消</el-button
				><el-button type="primary" :loading="busy" @click="saveOwner"
					>保存</el-button
				></template
			>
		</el-dialog>
	</div>
</template>
<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { Shop, OfficeBuilding, FolderOpened } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox, type ElTree } from 'element-plus';
import { useCool } from '/@/cool';
import StoreAccounts from './store-accounts.vue';
type Account = {
	id: number;
	username: string;
	name: string;
	role: string;
	status: number;
	merchantId: number;
	merchantName: string;
	storeId?: number;
	storeName: string;
	bindingValid: boolean;
};
type Group = {
	merchantId: number;
	name: string;
	stores: { id: number; name: string }[];
	categoryId?: number;
};
type Category = { id: number; name: string; parentId?: number; children?: Category[] };
type Node = {
	key: string;
	label: string;
	merchantId?: number;
	storeId?: number;
	children?: Node[];
};
const { service } = useCool();
const api = (action: string, data?: unknown): Promise<any> =>
	service.request({
		url: 'admin/merchant/backendAccount/' + action,
		method: data === undefined ? 'GET' : 'POST',
		data
	});
const roles: Record<string, string> = { OWNER: '商家管理员', MANAGER: '店长', STAFF: '核销员工' };
const tree = ref<InstanceType<typeof ElTree>>(),
	treeWord = ref(''),
	groups = ref<Group[]>([]);
const categoryId = ref<number>();
const groupsLoading = ref(true);
const categorySupported = computed(() => groups.value.every(g => g.categoryId != null));
const categories = ref<Category[]>([]);
const categoryOptions = computed(() => {
	const items = categories.value.map(c => ({ ...c, children: [] as Category[] }));
	const lookup = new Map(items.map(c => [c.id, c]));
	const roots: Category[] = [];
	for (const item of items) {
		const parent = item.parentId ? lookup.get(item.parentId) : undefined;
		if (parent && parent.id !== item.id) parent.children.push(item);
		else roots.push(item);
	}
	return roots;
});
const categoryIds = computed(() => {
	if (!categoryId.value) return undefined;
	const ids = new Set<number>([categoryId.value]);
	let changed = true;
	while (changed) {
		changed = false;
		for (const c of categories.value)
			if (c.parentId && ids.has(c.parentId) && !ids.has(c.id)) {
				ids.add(c.id);
				changed = true;
			}
	}
	return ids;
});
const visibleGroups = computed(() =>
	groups.value.filter(
		g => !categoryIds.value || (g.categoryId != null && categoryIds.value.has(g.categoryId))
	)
);
const nodes = computed<Node[]>(() => [
	{ key: 'all', label: categoryId.value ? '当前分类全部账号' : '全部商家账号' },
	...visibleGroups.value.map(m => ({
		key: 'merchant-' + m.merchantId,
		label: m.name,
		merchantId: m.merchantId,
		children: m.stores.map(s => ({
			key: 'store-' + s.id,
			label: s.name,
			merchantId: m.merchantId,
			storeId: s.id
		}))
	}))
]);
const selected = ref<Node>({ key: 'all', label: '全部商家账号' });
const filters = ref<{ role?: string; status?: number; keyWord: string }>({ keyWord: '' });
const rows = ref<Account[]>([]),
	total = ref(0),
	page = ref(1),
	loading = ref(false),
	busy = ref(false);
const storeVisible = ref(false),
	storeId = ref<number>(),
	storeName = ref(''),
	ownerVisible = ref(false),
	owner = ref<Partial<Account>>({});
let revision = 0;
function filterNode(value: string, data: Record<string, any>) {
	const node = data as Node;
	return (
		!value || node.label.includes(value) || !!node.children?.some(c => c.label.includes(value))
	);
}
watch(treeWord, value => tree.value?.filter(value));
async function loadGroups() {
	groupsLoading.value = true;
	try {
		const [data, types] = await Promise.all([api('groups'), service.merchant.category.list()]);
		groups.value = data;
		categories.value = types as unknown as Category[];
		if (!categorySupported.value && categoryId.value) {
			categoryId.value = undefined;
			selected.value = { key: 'all', label: '全部商家账号' };
			search();
		}
		await nextTick();
		tree.value?.filter(treeWord.value);
	} catch (e: any) {
		ElMessage.error(e.message || '商家门店加载失败');
	} finally {
		groupsLoading.value = false;
	}
}
async function load() {
	const current = ++revision;
	loading.value = true;
	try {
		const result = await api('page', {
			categoryId: categoryId.value || undefined,
			merchantId: selected.value.merchantId,
			storeId: selected.value.storeId,
			...filters.value,
			role: filters.value.role || undefined,
			status: typeof filters.value.status === 'number' ? filters.value.status : undefined,
			page: page.value,
			size: 20
		});
		if (current !== revision) return;
		rows.value = result.list;
		total.value = result.pagination.total;
	} catch (e: any) {
		if (current === revision) {
			rows.value = [];
			total.value = 0;
			ElMessage.error(e.message || '账号加载失败');
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
	filters.value = { keyWord: '' };
	search();
}
function selectGroup(node: Node) {
	selected.value = node;
	search();
}
async function changeCategory() {
	selected.value = { key: 'all', label: categoryId.value ? '当前分类全部账号' : '全部商家账号' };
	await nextTick();
	tree.value?.filter(treeWord.value);
	search();
}
function manageStore(id: number, name: string) {
	storeId.value = id;
	storeName.value = name;
	storeVisible.value = true;
}
function editOwner(row: Account) {
	owner.value = { ...row };
	ownerVisible.value = true;
}
async function saveOwner() {
	if (busy.value) return;
	if (!owner.value.name?.trim()) {
		ElMessage.warning('请输入姓名');
		return;
	}
	busy.value = true;
	try {
		await api('saveOwner', {
			id: owner.value.id,
			name: owner.value.name,
			status: owner.value.status
		});
		ownerVisible.value = false;
		ElMessage.success('管理员资料已保存');
		await load();
	} catch (e: any) {
		ElMessage.error(e.message || '保存失败');
	} finally {
		busy.value = false;
	}
}
async function resetOwner(row: Account) {
	if (busy.value) return;
	try {
		await ElMessageBox.confirm(
			`确认将“${row.name}（${row.username}）”的密码重置为123456？原登录立即失效。`,
			'重置商家管理员密码',
			{ type: 'warning' }
		);
		busy.value = true;
		await api('resetOwner', { id: row.id });
		ElMessage.success('密码已重置为123456');
	} catch (e: any) {
		if (e?.message) ElMessage.error(e.message);
	} finally {
		busy.value = false;
	}
}
onMounted(() => {
	void loadGroups();
	void load();
});
</script>
<style scoped>
.account-layout {
	height: 100%;
	display: flex;
	gap: 12px;
}
aside {
	width: 300px;
	flex-shrink: 0;
	min-height: 0;
	display: flex;
	flex-direction: column;
	background: var(--el-bg-color);
}
.tree-heading {
	height: 40px;
	padding: 0 10px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	border-bottom: 1px solid var(--el-border-color-extra-light);
	flex-shrink: 0;
}
.tree-filters {
	padding: 14px 15px 8px;
	display: grid;
	gap: 10px;
}
.tree-filters :deep(.el-select) {
	width: 100%;
}
.tree-summary {
	font-size: 12px;
	color: var(--el-text-color-secondary);
}
.tree-scroll {
	flex: 1;
	min-height: 0;
	padding: 0 10px 10px;
}
.group-node {
	display: flex;
	align-items: center;
	gap: 8px;
	min-width: 0;
	flex: 1;
	padding-right: 10px;
}
.group-node .el-icon {
	flex-shrink: 0;
	color: var(--el-text-color-secondary);
}
.node-label {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	flex: 1;
}
.node-count {
	font-size: 12px;
	color: var(--el-text-color-secondary);
	flex-shrink: 0;
}
aside :deep(.el-tree-node__content) {
	height: 38px;
	margin: 3px 0;
	border-radius: 4px;
	font-size: 14px;
}
aside :deep(.el-tree-node.is-current > .el-tree-node__content) {
	background: var(--el-color-primary-light-9);
	color: var(--el-color-primary);
}
aside :deep(.el-tree-node.is-current > .el-tree-node__content .el-icon) {
	color: var(--el-color-primary);
}
.toolbar {
	display: flex;
	align-items: center;
	gap: 10px;
	margin-bottom: 12px;
}
aside :deep(.el-tree) {
	margin-top: 0;
}
aside :deep(.el-tree-node__label) {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}
main {
	flex: 1;
	min-width: 0;
	padding: 15px;
	background: var(--el-bg-color);
	display: flex;
	flex-direction: column;
}
main > :deep(.el-alert) {
	margin-bottom: 12px;
	flex-shrink: 0;
}
.toolbar {
	flex-wrap: wrap;
	flex-shrink: 0;
}
main > :deep(.el-table) {
	flex: 1;
	min-height: 180px;
}
main > :deep(.el-pagination) {
	margin-top: 12px;
	justify-content: flex-end;
}
@media (max-width: 1000px) {
	aside {
		width: 240px;
	}
}
</style>
