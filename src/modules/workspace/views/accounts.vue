<template>
	<ManagementShell register
		title="账号管理"
		eyebrow="账号与数据范围"
		description="一号一岗位，菜单权限与可查看的数据范围分别设置"
		:sidebar="isPlatform"
		:items="merchants"
		:scope-id="merchantId"
		:scope-name="scopeName"
		:metrics="metrics"
		@scope-change="selectMerchant"
	>
		<el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
		<div class="management-toolbar">
			<el-button type="primary" @click="open()">新增账号</el-button
			><el-button :loading="busy" @click="load">刷新</el-button
			><span class="filter-label">状态</span
			><el-radio-group v-model="statusFilter" @change="page = 1"
				><el-radio-button value="ALL">全部</el-radio-button
				><el-radio-button :value="1">启用</el-radio-button
				><el-radio-button :value="0">停用</el-radio-button></el-radio-group
			><span class="toolbar-spacer" /><el-input
				v-model="keyword"
				clearable
				placeholder="姓名 / 登录账号"
				@keyup.enter="load"
			/><el-button type="primary" @click="load">搜索</el-button
			><el-button @click="resetFilters">重置</el-button>
		</div>
		<div class="management-toolbar">
			<el-select v-model="accountType" clearable placeholder="全部账号类型" @change="load"
				><el-option label="商家账号" value="MERCHANT" /><el-option
					label="门店账号"
					value="STORE" /></el-select
			><el-select
				v-model="filterStore"
				clearable
				filterable
				placeholder="全部门店"
				@change="page = 1"
				><el-option
					v-for="s in filterStores"
					:key="s.id"
					:value="s.id"
					:label="s.name" /></el-select
			><el-select
				v-model="filterRole"
				clearable
				filterable
				placeholder="全部岗位"
				@change="page = 1"
				><el-option v-for="r in filterRoles" :key="r.id" :value="r.id" :label="r.name"
			/></el-select>
		</div>
		<p class="management-caption">
			停用账号会终止原登录，历史记录保留。选择门店、岗位和状态后筛选当前查询结果。
		</p>
		<el-table class="management-table" :height="registerHeight" :data="pageRows" v-loading="busy" :empty-text="error ? '列表暂不可用' : '没有匹配的记录'">
			<el-table-column label="账号" min-width="165"
				><template #default="{ row }"
					><div class="cell-main">{{ row.name }}</div>
					<div class="cell-sub">{{ row.username }}</div></template
				></el-table-column
			>
			<el-table-column
				v-if="isPlatform && !merchantId"
				prop="merchantName"
				label="所属商家"
				min-width="150"
			/>
			<el-table-column label="身份 / 岗位" min-width="140"
				><template #default="{ row }"
					><div>{{ row.roleName }}</div>
					<div class="cell-sub">
						{{ row.accountType === 'STORE' ? '门店账号' : '商家账号' }}
					</div></template
				></el-table-column
			>
			<el-table-column label="所属门店" min-width="150"
				><template #default="{ row }">{{
					row.accountType === 'STORE' ? row.storeName || '绑定异常' : '商家范围'
				}}</template></el-table-column
			>
			<el-table-column label="可查看数据" min-width="170"
				><template #default="{ row }">{{ scopeText(row) }}</template></el-table-column
			>
			<el-table-column label="状态" width="90"
				><template #default="{ row }"
					><el-tag :type="row.status === 1 ? 'success' : 'info'">{{
						row.status === 1 ? '启用' : '停用'
					}}</el-tag></template
				></el-table-column
			>
			<el-table-column label="操作" width="145" fixed="right" align="right"
				><template #default="{ row }"
					><el-button
						link
						type="primary"
						:disabled="row.id === currentId"
						@click="open(row)"
						>编辑</el-button
					><el-button
						link
						:disabled="row.id === currentId"
						:type="row.status === 1 ? 'danger' : 'primary'"
						@click="state(row)"
						>{{ row.status === 1 ? '停用' : '启用' }}</el-button
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
			title="账号设置"
			size="min(900px,100vw)"
			:close-on-click-modal="false"
			:before-close="closeForm"
			><el-form class="workspace-editor-form" label-position="top"
				><section class="workspace-editor-section"><h3 class="workspace-section-heading">登录资料</h3>
				<el-form-item label="登录账号（新建使用手机号，已有账号不可修改）"
					><el-input
						v-model="form.username"
						:disabled="!!form.id"
						maxlength="40" /></el-form-item
				><el-form-item label="姓名"
					><el-input v-model="form.name" maxlength="50" /></el-form-item
				><el-form-item :label="form.id ? '重置密码（留空保持不变）' : '初始密码'"
					><el-input
						v-model="form.password"
						type="password"
						show-password
						maxlength="64"
						autocomplete="new-password"
				/></el-form-item>
				</section><section class="workspace-editor-section"><h3 class="workspace-section-heading">账号归属与可查看数据</h3>
				<el-form-item v-if="isPlatform" label="所属商家"
					><el-select
						v-model="form.merchantId"
						:disabled="!!form.id"
						filterable
						@change="ownership"
						><el-option
							v-for="m in merchants"
							:key="m.id"
							:value="m.id"
							:label="m.name" /></el-select></el-form-item
				><el-form-item label="账号类型"
					><el-radio-group
						v-model="form.accountType"
						:disabled="!!form.id || identity === 'STORE'"
						@change="ownership"
						><el-radio value="MERCHANT">商家账号</el-radio
						><el-radio value="STORE">门店账号</el-radio></el-radio-group
					></el-form-item
				><el-form-item v-if="form.accountType === 'STORE'" label="唯一所属门店"
					><el-select
						v-model="form.storeId"
						filterable
						:disabled="identity === 'STORE'"
						@change="loadRoles"
						><el-option
							v-for="s in stores"
							:key="s.id"
							:label="s.name"
							:value="s.id" /></el-select></el-form-item
				><el-form-item label="可查看数据"
					><el-select v-model="form.storeScope"
						><template v-if="form.accountType === 'MERCHANT'"
							><el-option
								label="本商家全部门店"
								value="ALL"
								v-if="canAll" /><el-option
								label="老板指定的门店"
								value="SELECTED" /></template
						><template v-else
							><el-option label="所属门店的记录" value="STORE" /><el-option
								label="仅本人经办记录"
								value="SELF" /></template></el-select></el-form-item
				><el-form-item
					v-if="form.storeScope === 'SELECTED'"
					label="指定门店（未勾选时无门店数据）"
					><el-select v-model="form.storeIds" multiple filterable
						><el-option
							v-for="s in stores"
							:key="s.id"
							:label="s.name"
							:value="s.id" /></el-select
				></el-form-item>
				</section><section class="workspace-editor-section is-permissions"><h3 class="workspace-section-heading">岗位及此账号的权限</h3>
				<el-form-item label="岗位（仅一个）"
					><el-select v-model="form.roleId" filterable
						><el-option
							v-for="r in roles"
							:key="r.id"
							:label="r.name"
							:value="r.id" /></el-select></el-form-item
				><el-alert
					v-if="form.id && originalRole !== form.roleId"
					title="岗位发生变更。保存前会展示权限变化并要求确认；账号和历史记录保持不变。"
					:closable="false" /><el-form-item label="账号状态"
					><el-switch v-model="form.status" :active-value="1" :inactive-value="0"
				/></el-form-item>
				<div class="management-summary">
					<div>
						<strong>当前有效功能</strong>
						<p class="management-caption">
							选择岗位或调整范围后自动刷新，最终以保存时的服务端校验为准。
						</p>
						<el-tag
							v-for="m in preview.filter(m => m.type !== 0)"
							:key="m.id"
							style="margin: 3px"
							>{{ m.name }}</el-tag
						><span v-if="!preview.length">暂未选择岗位或无可用功能</span>
						<p v-if="suppressed.length" class="management-caption">
							上级暂未开放：{{ suppressed.map(m => m.name).join('、') }}
						</p>
					</div>
				</div>
<el-form-item label="账号可用功能"><div style="width:100%"><p class="management-caption">勾选即可访问并展示。灰色功能超出可分配范围，无法调整；已生效权限可在预览中查看。</p><PermissionPicker v-model="selectedMenuIds" :menus="available" :unavailable-ids="unavailableIds" /><PermissionPreview :menus="available" :selected-ids="selectedMenuIds" :account-name="form.name" /></div></el-form-item></section></el-form
			><template #footer
				><el-button @click="closeForm()">取消</el-button
				><el-button type="primary" :loading="busy" @click="save"
					>保存账号</el-button
				></template
			></el-drawer
		>
	</ManagementShell>
</template>
<script setup lang="ts">
import { computed, ref, onMounted, watch, toRef, onBeforeUnmount } from 'vue';
import ManagementShell from '../components/management-shell.vue';
import {
	useManagementState,
	useRequestRevision,
	positiveId
} from '../composables/management-state';
import { ElMessage, ElMessageBox } from 'element-plus';
import { workspaceApi } from '../api';
import { useRoute } from 'vue-router';
const route = useRoute();
import { identity, isPlatform } from '/@/workspace';
import PermissionPicker from '../components/permission-picker.vue';
import PermissionPreview from '../components/permission-preview.vue';
import { useUnsavedForm } from '../composables/unsaved-form';
import { useRegisterHeight } from '../composables/register-height';
const registerHeight = useRegisterHeight();
const rows = ref<any[]>([]),
	merchants = ref<any[]>([]),
	stores = ref<any[]>([]),
	roles = ref<any[]>([]),
	catalog = ref<any[]>([]),
	keyword = ref(''),
	accountType = ref(''),
	busy = ref(false),
	error = ref(''),
	visible = ref(false),
	form = ref<any>({}),
	context = ref<any>({}),
	originalRole = ref<number>(),
	originalName = ref(''),
	originalPreview = ref<string[]>([]);
const { state: filters, selectMerchant } = useManagementState('accounts', {
	merchantId: undefined as number | undefined,
	storeId: positiveId(route.query.storeId),
	roleId: positiveId(route.query.roleId),
	status: 'ALL' as string | number
});
const merchantId = toRef(filters, 'merchantId'),
	filterStore = toRef(filters, 'storeId'),
	filterRole = toRef(filters, 'roleId'),
	statusFilter = toRef(filters, 'status');
const page = ref(1),
	size = ref(20),
	requestRevision = useRequestRevision(),
	previewRevision = useRequestRevision();
let ready = false;
const scopeName = computed(
	() =>
		merchants.value.find(m => m.id === merchantId.value)?.name ||
		(merchantId.value
			? '所选商家'
			: isPlatform
				? '全部商家'
				: context.value.merchantName || context.value.storeName || '当前授权范围')
);
const metrics = computed(() => [
	{ label: '查询账号', value: busy.value ? undefined : rows.value.length },
	{
		label: '启用',
		value: busy.value ? undefined : rows.value.filter(r => r.status === 1).length
	},
	{ label: '停用', value: busy.value ? undefined : rows.value.filter(r => r.status === 0).length }
]);
function scopeText(row: any) {
	return row.accountType === 'STORE'
		? row.storeScope === 'SELF'
			? '本店 · 仅本人经办'
			: '本店全部记录'
		: row.storeScope === 'ALL'
			? '本商家全部门店'
			: `指定 ${(row.storeIds || []).length} 家门店`;
}
function resetFilters() {
	keyword.value = '';
	accountType.value = '';
	filterStore.value = undefined;
	filterRole.value = undefined;
	statusFilter.value = 'ALL';
	void load();
}
watch(merchantId, () => {
	if (ready) {
		// Preserve explicit filters from a role/account navigation; manual scope changes clear them.
		const routeMerchant = positiveId(route.query.merchantId);
		const fromRoute = route.query.merchantId !== undefined && routeMerchant === merchantId.value;
		filterStore.value = fromRoute ? positiveId(route.query.storeId) : undefined;
		filterRole.value = fromRoute ? positiveId(route.query.roleId) : undefined;
		void load();
	}
});
const { markClean, close: closeForm } = useUnsavedForm(form, visible, busy);
const filterRoles = computed(() =>
	Array.from(
		new Map(
			rows.value
				.filter(r => r.roleId)
				.map(r => [r.roleId, { id: r.roleId, name: r.roleName }])
		).values()
	)
);
const filterStores = computed(() =>
	Array.from(
		new Map(
			rows.value
				.filter(r => r.storeId)
				.map(r => [r.storeId, { id: r.storeId, name: r.storeName }])
		).values()
	)
);
const filteredRows = computed(() =>
	rows.value.filter(
		r =>
			(filterRole.value == null || r.roleId === filterRole.value) &&
			(statusFilter.value === 'ALL' || r.status === statusFilter.value) &&
			(filterStore.value == null ||
				r.storeId === filterStore.value ||
				r.storeIds?.includes(filterStore.value) ||
				r.storeScope === 'ALL')
	)
);
const pageRows = computed(() =>
	filteredRows.value.slice((page.value - 1) * size.value, page.value * size.value)
);
watch(filteredRows, () => {
	if (page.value > Math.max(1, Math.ceil(filteredRows.value.length / size.value))) page.value = 1;
});
const currentId = computed(() => context.value.id),
	canAll = computed(() => isPlatform || context.value.owner === true),
	available = computed(() =>
		catalog.value.filter(
			m => m.accountType === 'SHARED' || m.accountType === form.value.accountType
		)
	);
const preview = ref<any[]>([]),
	suppressed = ref<any[]>([]);
const targetAssignable = ref<number[] | undefined>();
const unavailableIds = computed(() => {
 const granted = new Set<number>((context.value.grantMenuIds || []).map(Number));
 const target = targetAssignable.value && new Set(targetAssignable.value);
 return available.value.filter(m => (!isPlatform && !granted.has(Number(m.id))) ||
  (target && !target.has(Number(m.id)))).map(m => Number(m.id));
});
const selectedMenuIds = computed<number[]>({
 get: () => preview.value.map(m => Number(m.id)),
 set: ids => {
  const locked = new Set(unavailableIds.value);
  const editable = available.value.filter(m => !locked.has(Number(m.id)));
  const selected = new Set(ids.map(Number).filter(id => !locked.has(id)));
  form.value.allowMenuIds = [...(form.value.allowMenuIds || []).filter((id: number) => locked.has(Number(id))), ...selected];
  form.value.denyMenuIds = [...(form.value.denyMenuIds || []).filter((id: number) => locked.has(Number(id))), ...editable.filter(m => !selected.has(Number(m.id))).map(m => Number(m.id))];
  preview.value = [...preview.value.filter(m => locked.has(Number(m.id))), ...editable.filter(m => selected.has(Number(m.id)))];
 }
});
async function loadPreview() {
	if (!form.value.roleId) {
		preview.value = [];
		suppressed.value = [];
		return;
	}
	const p = { ...form.value };
	delete p.password;
	const revision = previewRevision.next();
	const result = await workspaceApi('accounts/preview', p);
	if (previewRevision.current(revision)) {
		targetAssignable.value = result.assignableMenuIds?.map(Number);
		preview.value = result.effectiveMenus;
		suppressed.value = result.suppressedMenus;
	}
}

async function load() {
	const revision = requestRevision.next();
	busy.value = true;
	error.value = '';
	rows.value = [];
	try {
		const result = await workspaceApi('accounts/list', {
			merchantId: merchantId.value,
			keyword: keyword.value || null,
			accountType: accountType.value || null
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
async function loadRoles() {
	if (isPlatform && !form.value.merchantId) {
		roles.value = [];
		return;
	}
	roles.value = await workspaceApi(
		`roles/options?accountType=${form.value.accountType}${form.value.merchantId ? '&merchantId=' + form.value.merchantId : ''}${form.value.storeId ? '&storeId=' + form.value.storeId : ''}`
	);
}
async function ownership() {
	targetAssignable.value = undefined;
	try {
		form.value.roleId = undefined;
		form.value.allowMenuIds = [];
		form.value.denyMenuIds = [];
		form.value.storeIds = [];
		form.value.storeScope =
			form.value.accountType === 'STORE' ? 'SELF' : canAll.value ? 'ALL' : 'SELECTED';
		stores.value =
			!isPlatform || form.value.merchantId
				? await workspaceApi(
						`stores${form.value.merchantId ? '?merchantId=' + form.value.merchantId : ''}`
					)
				: [];
		form.value.storeId = identity === 'STORE' ? context.value.storeId : undefined;
		await loadRoles();
	} catch (e: any) {
		ElMessage.error(e.message);
	}
}
async function open(row?: any) {
	targetAssignable.value = undefined;
	form.value = row
		? { ...JSON.parse(JSON.stringify(row)), password: '' }
		: {
				merchantId:
					merchantId.value ||
					rows.value.find(r => r.storeId === filterStore.value)?.merchantId ||
					context.value.merchantId,
				name: '',
				username: '',
				password: '',
				accountType: identity === 'STORE' || filterStore.value ? 'STORE' : 'MERCHANT',
				status: 1,
				storeScope:
					identity === 'STORE' || filterStore.value
						? 'SELF'
						: canAll.value
							? 'ALL'
							: 'SELECTED',
				storeIds: [],
				allowMenuIds: [],
				denyMenuIds: [],
				storeId: identity === 'STORE' ? context.value.storeId : filterStore.value
			};
	originalRole.value = row?.roleId;
	originalName.value = row?.roleName || '';
	try {
		if (!isPlatform) context.value = await workspaceApi('me');
		stores.value =
			!isPlatform || form.value.merchantId
				? await workspaceApi(
						`stores${form.value.merchantId ? '?merchantId=' + form.value.merchantId : ''}`
					)
				: [];
		await loadRoles();
		await loadPreview();
		originalPreview.value = preview.value.map(m => m.name);
		markClean();
		visible.value = true;
	} catch (e: any) {
		ElMessage.error(e.message);
	}
}
async function save() {
	if (
		!form.value.name?.trim() ||
		!form.value.username ||
		!form.value.roleId ||
		(isPlatform && !form.value.merchantId)
	) {
		ElMessage.warning('请填写姓名、登录账号，选择所属商家和岗位');
		return;
	}
	if (form.value.accountType === 'STORE' && !form.value.storeId) {
		ElMessage.warning('请选择唯一所属门店');
		return;
	}
	if (
		!form.value.id &&
		(!/^1\d{10}$/.test(form.value.username) ||
			!form.value.password ||
			form.value.password.length < 6)
	) {
		ElMessage.warning('新账号使用11位手机号，初始密码至少6位');
		return;
	}
	try {
		await loadPreview();
		if (form.value.id && originalRole.value !== form.value.roleId) {
			const next = roles.value.find(r => r.id === form.value.roleId)?.name || '未选择';
			await ElMessageBox.confirm(
				`岗位：${originalName.value} → ${next}\n原权限：${originalPreview.value.join('、') || '无'}\n调整后：${preview.value.map(m => m.name).join('、') || '无'}\n账号可用功能以本页勾选结果为准。确认提交？`,
				'确认岗位与权限变化',
				{ confirmButtonText: '确认调整' }
			);
		}
		busy.value = true;
		await workspaceApi('accounts/save', form.value);
		visible.value = false;
		ElMessage.success('账号已保存，权限将自动更新；修改密码或身份状态需重新登录');
		await load();
	} catch (e: any) {
		if (e?.message) ElMessage.error(e.message);
	} finally {
		busy.value = false;
	}
}
async function state(row: any) {
	try {
		await ElMessageBox.confirm(
			`${row.status === 1 ? '停用' : '启用'}“${row.name}”的登录账号？历史订单、账单和核销记录会保留。`,
			'账号状态'
		);
		await workspaceApi('accounts/status', { id: row.id, status: row.status === 1 ? 0 : 1 });
		await load();
	} catch (e: any) {
		if (e?.message) ElMessage.error(e.message);
	}
}
watch(
	() => [route.query.roleId, route.query.storeId],
	() => {
		if (!route.path.endsWith('/accounts')) return;
		filterRole.value = positiveId(route.query.roleId);
		filterStore.value = positiveId(route.query.storeId);
		if (filterRole.value != null) {
			keyword.value = '';
			accountType.value = '';
			statusFilter.value = 'ALL';
			if (ready) void load();
		}
		page.value = 1;
	}
);
let previewTimer: ReturnType<typeof setTimeout> | undefined;
watch(
	() => [
		form.value.roleId,
		form.value.storeId,
		form.value.storeScope,
		form.value.storeIds,
		form.value.allowMenuIds,
		form.value.denyMenuIds
	],
	() => {
		if (!visible.value) return;
		clearTimeout(previewTimer);
		previewTimer = setTimeout(() => {
			void loadPreview().catch((e: any) => ElMessage.error(e.message || '权限预览加载失败'));
		}, 250);
	},
	{ deep: true }
);
onBeforeUnmount(() => clearTimeout(previewTimer));
onMounted(async () => {
	try {
		if (!isPlatform) context.value = await workspaceApi('me');
		catalog.value = await workspaceApi('catalog');
		if (isPlatform) merchants.value = await workspaceApi('merchants');
		if (positiveId(route.query.roleId) != null) statusFilter.value = 'ALL';
		ready = true;
		await load();
	} catch (e: any) {
		error.value = e.message;
	}
});
</script>
<style scoped>
.workspace-page {
	padding: 24px;
	height: 100%;
	overflow: auto;
	box-sizing: border-box;
}
.toolbar {
	display: flex;
	flex-wrap: wrap;
	gap: 12px;
	margin: 20px 0;
}
.toolbar .el-select,
.toolbar .el-input {
	width: 220px;
}
.el-form .el-select {
	width: 100%;
}
h3 {
	margin: 28px 0 20px;
	border-bottom: 1px solid var(--el-border-color);
	padding-bottom: 12px;
}
</style>
