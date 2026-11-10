<template>
	<ManagementShell register
		title="岗位管理"
		eyebrow="岗位与功能"
		description="岗位决定员工默认可以使用哪些功能；员工的权限可在员工账号中单独调整"
		:sidebar="isPlatform"
		:items="merchants"
		:scope-id="merchantId"
		:scope-name="scopeName"
		:metrics="metrics"
		all-hint="系统预设岗位与各商家的自定义岗位"
		@scope-change="selectMerchant"
	>
		<el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
		<div class="management-toolbar">
			<el-button type="primary" @click="open()">新增岗位</el-button
			><el-button :loading="busy" @click="load">刷新</el-button>
			<span class="filter-label">类型</span
			><el-radio-group v-model="builtinFilter" @change="page = 1"
				><el-radio-button value="ALL">全部</el-radio-button
				><el-radio-button value="BUILTIN">系统预设岗位</el-radio-button
				><el-radio-button value="CUSTOM">自定义岗位</el-radio-button></el-radio-group
			>
			<span class="toolbar-spacer" /><el-input
				v-model="keyword"
				clearable
				placeholder="搜索岗位名称 / 标识"
				@keyup.enter="load"
			/><el-button type="primary" @click="load">搜索</el-button
			><el-button @click="resetFilters">重置</el-button>
		</div>
		<div class="management-toolbar">
			<el-select v-model="filterType" clearable placeholder="全部适用账号" @change="page = 1"
				><el-option label="商家账号" value="MERCHANT" /><el-option
					label="门店账号"
					value="STORE"
			/></el-select>
			<p class="management-caption">
				系统预设岗位由平台提供；自定义岗位由商家创建。选择商家后可查看该商家可用的岗位。
			</p>
		</div>
		<el-table class="management-table" :height="registerHeight" :data="pageRows" v-loading="busy" :empty-text="error ? '列表暂不可用' : '没有匹配的记录'">
			<el-table-column label="岗位" min-width="160"
				><template #default="{ row }"
					><div class="cell-main">{{ row.name }}</div>
					<div class="cell-sub" :title="row.remark || row.label">
						{{ row.remark || row.label }}
					</div></template
				></el-table-column
			>
			<el-table-column v-if="isPlatform && !merchantId" label="所属商家" min-width="150"
				><template #default="{ row }">{{
					row.builtin ? '全商家共享' : row.merchantName || '所属商家不可用'
				}}</template></el-table-column
			>
			<el-table-column label="类型 / 适用账号" min-width="125"
				><template #default="{ row }"
					><el-tag
						:type="row.accountType === 'MERCHANT' ? 'warning' : 'success'"
						effect="plain"
						>{{ row.accountType === 'MERCHANT' ? '商家账号' : '门店账号' }}</el-tag
					>
					<div class="cell-sub">
						{{ row.builtin ? '系统预设岗位' : '自定义岗位' }}
					</div></template
				></el-table-column
			>
			<el-table-column label="工作范围" min-width="150"
				><template #default="{ row }">{{ applicableText(row) }}</template></el-table-column
			>
			<el-table-column label="状态" width="85"
				><template #default="{ row }"
					><el-tag :type="row.status === 1 ? 'success' : 'info'">{{
						row.status === 1 ? '启用' : '停用'
					}}</el-tag></template
				></el-table-column
			>
			<el-table-column label="使用人数" width="100"
				><template #default="{ row }"
					><el-button link type="primary" @click="viewAccounts(row)"
						>{{ row.accountCount }} 人</el-button
					></template
				></el-table-column
			>

			<el-table-column label="操作" width="145" fixed="right" align="right"
				><template #default="{ row }"
					><el-button link @click="open(row, row.builtin && !isPlatform)"
						>{{ row.builtin && !isPlatform ? '查看权限' : '编辑' }}</el-button
					><el-button link type="danger" :disabled="row.builtin" @click="remove(row)"
						>删除</el-button
					></template
				></el-table-column
			>
		</el-table>
		<div class="management-pagination">
			<el-pagination
				v-model:current-page="page"
				v-model:page-size="size"
				:total="filteredRows.length"
				:page-sizes="[10, 20, 50]"
				layout="total, sizes, prev, pager, next"
				@size-change="page = 1"
			/>
		</div>
		<el-drawer
			append-to-body
			class="workspace-editor"
			v-model="visible"
			:title="readOnly ? '查看岗位权限' : '岗位与权限'"
			size="min(880px,100vw)"
			:close-on-click-modal="false"
			:before-close="closeForm"
			><el-alert v-if="readOnly" title="系统预设岗位由平台维护；如需调整个人权限，请前往员工账号。" type="info" :closable="false" show-icon /><el-form class="workspace-editor-form" label-position="top" :disabled="readOnly"
				><section class="workspace-editor-section"><h3 class="workspace-section-heading">基本信息</h3>
				<el-form-item v-if="isPlatform && !form.builtin" label="所属商家"
					><el-select v-model="form.merchantId" filterable @change="loadStores"
						><el-option
							v-for="m in merchants"
							:key="m.id"
							:value="m.id"
							:label="m.name" /></el-select></el-form-item
				><el-form-item label="岗位名称"
					><el-input v-model="form.name" maxlength="50" /></el-form-item
				><el-collapse
					><el-collapse-item title="高级设置：岗位标识（已自动生成）" name="identifier"
						><el-form-item label="岗位标识"
							><el-input
								v-model="form.label"
								:disabled="!!form.id"
								maxlength="40" /></el-form-item></el-collapse-item
				></el-collapse>
				</section><section class="workspace-editor-section"><h3 class="workspace-section-heading">适用范围</h3>
				<el-form-item label="账号类型"
					><el-radio-group
						v-model="form.accountType"
						@change="
							() => {
								form.menuIds = [];
								form.grantMenuIds = [];
							}
						"
						:disabled="!!form.id || identity === 'STORE'"
						><el-radio value="MERCHANT">商家账号</el-radio
						><el-radio value="STORE">门店账号</el-radio></el-radio-group
					></el-form-item
				><el-form-item
					v-if="form.accountType === 'STORE' && !form.builtin"
					label="可分配给哪些门店（不选则可分配给授权范围内的门店）"
					><el-select v-model="form.applicableStoreIds" multiple filterable
						><el-option
							v-for="s in stores"
							:key="s.id"
							:value="s.id"
							:label="s.name" /></el-select></el-form-item
				><el-form-item label="状态"
					><el-switch
						v-model="form.status"
						:active-value="1"
						:inactive-value="0" /></el-form-item
				><el-form-item label="备注"
					><el-input v-model="form.remark" maxlength="255"
				/></el-form-item>
				</section></el-form>
			<section class="workspace-editor-section is-permissions"><h3 class="workspace-section-heading">功能权限</h3>
				<el-tabs
					><el-tab-pane label="岗位可使用的页面与操作"
						><PermissionPicker v-model="form.menuIds" :menus="available" :disabled="readOnly" /></el-tab-pane
					><el-tab-pane label="允许向下级授予的功能"
						><el-alert
							title="授予范围独立于本人使用权限，且始终受上级授权限制。"
							:closable="false" /><PermissionPicker
							v-model="form.grantMenuIds"
							:menus="available" :disabled="readOnly" /></el-tab-pane></el-tabs></section>
			<PermissionPreview :menus="available" :selected-ids="form.menuIds || []" :account-name="form.name" />
			<template #footer
				><el-button @click="closeForm()">取消</el-button
				><el-button v-if="!readOnly" type="primary" :loading="busy" @click="save"
					>保存岗位</el-button
				></template
			></el-drawer
		>
	</ManagementShell>
</template>
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { toRef } from 'vue';
import ManagementShell from '../components/management-shell.vue';
import { useManagementState, useRequestRevision } from '../composables/management-state';
import { ElMessage, ElMessageBox } from 'element-plus';
import { workspaceApi } from '../api';
import { useRouter } from 'vue-router';
const router = useRouter();
function viewAccounts(row: any) {
	router.push({
		path: isPlatform
			? '/workspace-admin/accounts'
			: identity === 'STORE'
				? '/store/accounts'
				: '/merchant/accounts',
		query: { roleId: row.id, merchantId: merchantId.value ?? row.merchantId ?? '' }
	});
}
import { identity, isPlatform } from '/@/workspace';
import PermissionPicker from '../components/permission-picker.vue';
import PermissionPreview from '../components/permission-preview.vue';
import { useUnsavedForm } from '../composables/unsaved-form';
import { useCool } from '/@/cool';
import { useRegisterHeight } from '../composables/register-height';
const registerHeight = useRegisterHeight();
const { service } = useCool();
const rows = ref<any[]>([]),
	merchants = ref<any[]>([]),
	stores = ref<any[]>([]),
	catalog = ref<any[]>([]),
	keyword = ref(''),
	busy = ref(false),
	error = ref(''),
	visible = ref(false),
	readOnly = ref(false),
	form = ref<any>({});
const { state: filters, selectMerchant } = useManagementState('roles', {
	merchantId: undefined as number | undefined,
	type: '',
	builtin: 'ALL'
});
const merchantId = toRef(filters, 'merchantId'),
	filterType = toRef(filters, 'type'),
	builtinFilter = toRef(filters, 'builtin');
const page = ref(1),
	size = ref(20),
	storeOptions = ref<any[]>([]);
const scopeName = computed(
	() =>
		merchants.value.find(m => m.id === merchantId.value)?.name ||
		(merchantId.value ? '所选商家' : isPlatform ? '全部商家' : '当前授权范围')
);
const metrics = computed(() => [
	{ label: '岗位总数', value: busy.value ? undefined : rows.value.length },
	{ label: '系统预设岗位', value: busy.value ? undefined : rows.value.filter(r => r.builtin).length },
	{ label: '自定义岗位', value: busy.value ? undefined : rows.value.filter(r => !r.builtin).length }
]);
function applicableText(row: any) {
	if (row.accountType !== 'STORE') return row.label === 'ws_owner' ? '整个商家' : '按账号授权的门店';
	const ids = row.applicableStoreIds || [];
	return ids.length
		? ids.every((id: number) => storeOptions.value.some(s => s.id === id))
			? ids.map((id: number) => storeOptions.value.find(s => s.id === id)?.name).join('、')
			: `指定 ${ids.length} 家门店`
		: '所在门店';
}
function resetFilters() {
	keyword.value = '';
	filterType.value = '';
	builtinFilter.value = 'ALL';
	void load();
}
const requestRevision = useRequestRevision();
let ready = false;
watch(merchantId, () => {
	if (ready) void load();
});
const { markClean, close: closeForm } = useUnsavedForm(form, visible, busy);
const filteredRows = computed(() =>
	rows.value.filter(
		r =>
			(!filterType.value || r.accountType === filterType.value) &&
			(builtinFilter.value === 'ALL' || r.builtin === (builtinFilter.value === 'BUILTIN'))
	)
);
const pageRows = computed(() =>
	filteredRows.value.slice((page.value - 1) * size.value, page.value * size.value)
);
watch(filteredRows, () => {
	if (page.value > Math.max(1, Math.ceil(filteredRows.value.length / size.value))) page.value = 1;
});
const available = computed(() =>
	catalog.value.filter(
		m => m.accountType === 'SHARED' || m.accountType === form.value.accountType
	)
);
async function load() {
	const revision = requestRevision.next();
	busy.value = true;
	error.value = '';
	rows.value = [];
	try {
		const result = await workspaceApi('roles/list', {
			merchantId: merchantId.value,
			keyword: keyword.value || null
		});
		if (requestRevision.current(revision)) {
			rows.value = result;
			page.value = 1;
		}
	} catch (e: any) {
		if (requestRevision.current(revision)) error.value = e.message;
	} finally {
		if (requestRevision.current(revision)) busy.value = false;
	}
}
async function loadStores() {
	stores.value =
		form.value.merchantId || !isPlatform
			? await workspaceApi(
					`stores${form.value.merchantId ? '?merchantId=' + form.value.merchantId : ''}`
				)
			: [];
}
async function open(row?: any, readonly = false) {
	readOnly.value = readonly;
	form.value = row
		? JSON.parse(JSON.stringify(row))
		: {
				merchantId: merchantId.value,
				name: '',
				label: 'role_' + crypto.randomUUID().replace(/-/g, '').slice(0, 16),
				accountType: identity === 'STORE' ? 'STORE' : 'MERCHANT',
				status: 1,
				menuIds: [],
				grantMenuIds: [],
				applicableStoreIds: []
			};
	try {
		await loadStores();
		markClean();
		visible.value = true;
	} catch (e: any) {
		error.value = e.message;
	}
}
async function save() {
	busy.value = true;
	try {
		if (
			!form.value.name?.trim() ||
			(isPlatform && !form.value.builtin && !form.value.merchantId)
		) {
			ElMessage.warning('请填写岗位名称并选择所属商家');
			return;
		}
		if (form.value.builtin)
			await ElMessageBox.confirm(
				form.value.globalAccountCount == null
					? '这是所有商家共用的内置岗位，保存将影响所有使用该岗位的账号。确认修改？'
					: `这是所有商家共用的内置岗位，保存将影响全平台使用该岗位的 ${form.value.globalAccountCount} 个账号。确认修改？`,
				'修改共享内置岗位'
			);
		await workspaceApi('roles/save', form.value);
		visible.value = false;
		ElMessage.success('岗位已保存');
		await load();
	} catch (e: any) {
		if (e?.message) ElMessage.error(e.message);
	} finally {
		busy.value = false;
	}
}
async function remove(row: any) {
	try {
		await ElMessageBox.confirm(
			`确认删除岗位“${row.name}”？仍有账号使用时将禁止删除。`,
			'删除岗位'
		);
		await workspaceApi('roles/delete', { id: row.id });
		await load();
	} catch (e: any) {
		if (e?.message) ElMessage.error(e.message);
	}
}
onMounted(async () => {
	try {
		catalog.value = await workspaceApi('catalog');
		if (isPlatform) merchants.value = await workspaceApi('merchants');
		try {
			storeOptions.value = isPlatform
				? await service.merchant.store.list({})
				: await workspaceApi('stores');
		} catch {
			/* do not invent store names */
		}
		ready = true;
		await load();
	} catch (e: any) {
		error.value = e.message;
	}
});
</script>
<style scoped>
.role-section {
	margin: 24px 0 18px;
	padding-bottom: 10px;
	border-bottom: 1px solid var(--el-border-color-lighter);
	font-size: 15px;
}
.workspace-page {
	padding: 24px;
	height: 100%;
	overflow: auto;
	box-sizing: border-box;
}
.toolbar {
	display: flex;
	gap: 12px;
	margin: 20px 0;
}
.toolbar .el-input,
.toolbar .el-select {
	width: 260px;
}
.el-form .el-select {
	width: 100%;
}
</style>
