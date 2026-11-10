<template>
	<ManagementShell register
		:title="isPlatform ? '商家功能授权' : '门店功能授权'"
		eyebrow="逐级功能授权"
		description="选择授权对象后配置可开放功能，原下级设置在上级恢复授权后继续生效"
		sidebar
		:scope-label="isPlatform ? '商家' : '门店'"
		:items="subjects"
		:scope-id="subjectId"
		:all-label="isPlatform ? '选择商家' : '选择门店'"
		all-hint="请选择一个对象配置功能"
		:scope-name="selectedName"
		:metrics="metrics"
		@scope-change="selectSubject"
	>
		<el-skeleton v-if="loadingSubjects || busy" :rows="7" animated aria-label="正在加载授权" />
		<ManagementState
			v-else-if="error"
			kind="error"
			title="授权信息加载失败"
			:description="error"
			@retry="subjectsReady ? load() : loadSubjects()"
		/>
		<ManagementState
			v-else-if="!subjectId"
			:title="subjects.length ? '请选择授权对象' : '暂无可授权的对象'"
			:description="subjects.length ? '从商家或门店列表中选择一项，查看和调整该对象的授权范围。' : undefined"
		/>
		<section v-else-if="loaded" class="grant-workbench">
			<header class="grant-heading">
				<div>
					<h3>可开放功能</h3>
					<p>这里设置可用功能的上限。员工实际能使用哪些功能，还取决于岗位、个人设置和数据范围。</p>
				</div>
				<el-button :disabled="saving" @click="refresh">刷新授权</el-button>
			</header>
			<PermissionPicker v-model="menuIds" :menus="menus" :disabled="saving" />
			<footer class="grant-footer">
				<div class="grant-selection" aria-live="polite">
					<strong>已选 {{ menuIds.length }} 项功能</strong>
					<span v-if="retained">含 {{ retained }} 项暂未开放的原配置</span>
					<el-tag v-if="dirty" type="warning">有未保存的调整</el-tag>
				</div>
				<div class="grant-actions">
					<el-button :disabled="!dirty || saving" @click="restore">撤销本次调整</el-button>
					<el-button type="primary" :disabled="!dirty" :loading="saving" @click="save">保存授权</el-button>
				</div>
			</footer>
		</section>
	</ManagementShell>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';
import { isPlatform } from '/@/workspace';
import { workspaceApi } from '../api';
import ManagementState from '../components/management-state.vue';
import ManagementShell from '../components/management-shell.vue';
import PermissionPicker from '../components/permission-picker.vue';
import {
	useManagementState,
	useRequestRevision,
	positiveId
} from '../composables/management-state';
const { state: scope, selectMerchant } = useManagementState('grants', {
	merchantId: undefined as number | undefined
});
const storeId = ref<number>(),
	subjects = ref<any[]>([]),
	menus = ref<any[]>([]),
	menuIds = ref<number[]>([]),
	original = ref<number[]>([]);
const loadingSubjects = ref(false),
	subjectsReady = ref(false),
	busy = ref(false),
	saving = ref(false),
	loaded = ref(false),
	error = ref(''),
	revision = useRequestRevision();
const subjectId = computed(() => (isPlatform ? scope.merchantId : storeId.value)),
	subjectType = isPlatform ? 'MERCHANT' : 'STORE';
const selectedName = computed(
	() => subjects.value.find(s => s.id === subjectId.value)?.name || '未选择'
);
const signature = (ids: number[]) => [...ids].sort((a, b) => a - b).join(',');
const dirty = computed(
	() => loaded.value && signature(menuIds.value) !== signature(original.value)
);
const retained = computed(
	() => menuIds.value.filter(id => !menus.value.some(m => m.id === id)).length
);
const metrics = computed(() => [
	{
		label: '可配置页面',
		value: loaded.value ? menus.value.filter(m => m.type === 1).length : undefined
	},
	{ label: '已选功能', value: loaded.value ? menuIds.value.length : undefined }
]);
async function mayDiscard() {
	if (saving.value) return false;
	if (!dirty.value) return true;
	try {
		await ElMessageBox.confirm('授权调整尚未保存，是否放弃本次修改？', '未保存的授权', {
			confirmButtonText: '放弃修改',
			cancelButtonText: '继续编辑',
			type: 'warning'
		});
		return true;
	} catch {
		return false;
	}
}
async function selectSubject(id?: number) {
	if (id === subjectId.value || !(await mayDiscard())) return;
	loaded.value = false;
	if (isPlatform) selectMerchant(id);
	else storeId.value = id;
}
watch(subjectId, () => {
	void load();
});
async function load() {
	const round = revision.next();
	loaded.value = false;
	error.value = '';
	menus.value = [];
	menuIds.value = [];
	original.value = [];
	if (!subjectId.value) {
		busy.value = false;
		return;
	}
	busy.value = true;
	try {
		const r = await workspaceApi(
			`grants?subjectType=${subjectType}&subjectId=${subjectId.value}`
		);
		if (!revision.current(round)) return;
		menus.value = r.availableMenus;
		menuIds.value = [...r.menuIds];
		original.value = [...r.menuIds];
		loaded.value = true;
	} catch (e: any) {
		if (revision.current(round)) error.value = e.message;
	} finally {
		if (revision.current(round)) busy.value = false;
	}
}
async function refresh() {
	if (await mayDiscard()) await load();
}
function restore() {
	menuIds.value = [...original.value];
}
async function save() {
	if (saving.value || !loaded.value || !subjectId.value) return;
	const id = subjectId.value,
		values = [...menuIds.value],
		old = [...original.value];
	try {
		const added = values.filter(x => !old.includes(x)).length,
			removed = old.filter(x => !values.includes(x)).length;
		await ElMessageBox.confirm(
			`为“${selectedName.value}”新增 ${added} 项、收回 ${removed} 项功能。收回后立即限制使用，下级原配置保留。确认保存？`,
			'确认功能授权',
			{ type: 'warning' }
		);
		if (id !== subjectId.value) return;
		saving.value = true;
		await workspaceApi('grants/save', { subjectType, subjectId: id, menuIds: values });
		original.value = [...values];
		ElMessage.success('授权已保存');
	} catch (e: any) {
		if (e?.message) ElMessage.error(e.message);
	} finally {
		saving.value = false;
	}
}
onBeforeRouteLeave(async () => await mayDiscard());
onBeforeRouteUpdate(async (to, from) => {
	if (to.query.merchantId !== from.query.merchantId) return await mayDiscard();
	return true;
});
async function loadSubjects() {
	loadingSubjects.value = true;
	error.value = '';
	try {
		subjects.value = await workspaceApi(isPlatform ? 'merchants' : 'stores');
		subjectsReady.value = true;
		await load();
	} catch (e: any) {
		error.value = e.message;
	} finally {
		loadingSubjects.value = false;
	}
}
onMounted(loadSubjects);
</script>
<style scoped>
.grant-workbench {
	display: flex;
	flex-direction: column;
	min-height: 100%;
	padding: 0 12px;
}
.grant-heading,
.grant-footer {
	display: flex;
	align-items: center;
	justify-content: space-between;
	flex-wrap: wrap;
	gap: 12px;
	padding-block: 12px;
}
.grant-heading h3 {
	margin: 0;
	font-size: 16px;
	color: var(--workspace-ink);
}
.grant-heading p {
	margin: 4px 0 0;
	color: var(--el-text-color-secondary);
	font-size: 13px;
	line-height: 1.55;
}
.grant-footer {
	position: sticky;
	bottom: 0;
	z-index: 1;
	margin-top: auto;
	border-top: 1px solid var(--el-border-color-lighter);
	background: var(--el-bg-color);
	padding-inline: 12px;
}
.grant-selection,
.grant-actions {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 8px;
	font-size: 13px;
}
.grant-selection span {
	color: var(--el-text-color-secondary);
}
.grant-actions .el-button {
	margin: 0;
}
</style>
