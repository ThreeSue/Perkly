<template>
	<CatalogWorkbench title="首页广告" description="管理广告图片、展示时间和跳转目标，发布前可预览展示结果。">
	<el-empty v-if="!allowed('page')" description="当前账号没有首页广告查看权限" />
	<cl-crud v-else ref="Crud">
		<cl-row
			><cl-refresh-btn /><cl-add-btn /><cl-multi-delete-btn />
			<OrderManager
				:service="bannerOrderService"
				field="sort"
				button-text="调整排序"
				save-blocked-reason="当前可以预览排序。批量保存接口接通后才能提交调整。"
			/>
			<cl-flex1 /><el-button
				@click="openPreview"
				>预览展示中的广告</el-button
			></cl-row
		>
		<cl-row><cl-search ref="Search" reset-btn /></cl-row>
		<cl-row
			><cl-table ref="Table">
				<template #column-imageUrl="{ scope }"
					><el-image
						:src="imageSource(scope.row.imageUrl)"
						fit="cover"
						class="banner-thumbnail"
						:preview-src-list="[imageSource(scope.row.imageUrl)]"
						preview-teleported
						><template #error>图片加载失败</template></el-image
					></template
				>
				<template #column-targetName="{ scope }"
					><div>{{ typeName(scope.row.jumpType) }}</div>
					<span class="banner-target" :title="scope.row.targetName">{{
						scope.row.targetName
					}}</span></template
				>
				<template #column-status="{ scope }"
					><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{
						scope.row.status === 1 ? '启用' : '停用'
					}}</el-tag></template
				>
				<template #column-displayStateText="{ scope }"
					><el-tag
						:type="
							scope.row.displayStateText === '展示中'
								? 'success'
								: scope.row.displayStateText === '目标不可用'
									? 'danger'
									: 'info'
						"
						>{{ scope.row.displayStateText }}</el-tag
					></template
				>
				<template #column-schedule="{ scope }"
					><div>{{ scope.row.startTime || '不限开始时间' }}</div>
					<div>{{ scope.row.endTime || '不限结束时间' }}</div></template
				>
				<template #column-statusActions="{ scope }"
					><el-button
						v-if="allowed('status')"
						link
						:type="scope.row.status === 1 ? 'warning' : 'success'"
						:disabled="statusBusy"
						@click="toggle(scope.row)"
						>{{ scope.row.status === 1 ? '停用' : '启用' }}</el-button
					></template
				>
			</cl-table></cl-row
		>
		<cl-row><cl-flex1 /><cl-pagination /></cl-row>
		<cl-upsert ref="Upsert">
			<template #slot-jumpType="{ scope }"
				><el-select v-model="scope.jumpType" style="width: 100%" @change="changeType(scope)"
					><el-option
						v-for="item in jumpTypes"
						:key="item.value"
						:label="item.label"
						:value="item.value" /></el-select
			></template>
			<template #slot-target="{ scope }">
				<el-input
					v-if="scope.jumpType === 'WEB'"
					v-model="scope.jumpTarget"
					maxlength="1000"
					placeholder="https://example.com/activity"
				/>
				<div v-else class="banner-target-field">
					<el-select
						:model-value="scope.jumpTarget || undefined"
						@update:model-value="
							value => (scope.jumpTarget = value ? String(value) : null)
						"
						filterable
						remote
						clearable
						:remote-method="searchTargets"
						:loading="targetsLoading"
						placeholder="输入目标名称搜索"
						@visible-change="visible => visible && fetchTargets(true)"
						style="width: 100%"
					>
						<el-option
							v-for="item in options"
							:key="item.id"
							:label="
								item.name + (item.merchantName ? ' · ' + item.merchantName : '')
							"
							:value="String(item.id)"
						/>
						<template #footer
							><el-button
								v-if="hasMoreTargets"
								text
								:loading="targetsLoading"
								@click="fetchTargets(false)"
								>加载更多目标</el-button
							></template
						>
					</el-select>
					<span class="banner-hint"
						>仅列出可公开展示的目标；目标下架后广告会自动停止展示。</span
					>
					<span v-if="targetsError" class="banner-error"
						>{{ targetsError }}
						<el-button link @click="fetchTargets(true)">重试</el-button></span
					>
				</div>
			</template>
		</cl-upsert>
		<el-dialog v-model="previewVisible" title="当前有效广告预览" width="450px" destroy-on-close>
			<div v-loading="previewLoading">
				<el-alert
					v-if="previewError"
					:title="previewError"
					type="error"
					:closable="false"
				/>
				<el-empty
					v-else-if="!previewLoading && !previewRows.length"
					description="当前没有符合展示条件的广告"
				/>
				<el-carousel
					v-if="previewRows.length"
					height="190px"
					:interval="4000"
					indicator-position="outside"
					><el-carousel-item v-for="item in previewRows" :key="item.id"
						><el-image
							:src="imageSource(item.imageUrl)"
							fit="cover"
							class="banner-preview-image"
							><template #error>图片加载失败</template></el-image
						></el-carousel-item
					></el-carousel
				>
				<p class="banner-hint">
					按实际公开接口结果预览，最多10张；预览不会跳转或修改广告。
				</p>
			</div>
		</el-dialog>
	</cl-crud>
	</CatalogWorkbench>
</template>

<script setup lang="ts">
import CatalogWorkbench from '/@/modules/workspace/components/catalog-workbench.vue';
import { computed, onBeforeUnmount, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useCrud, useTable, useUpsert, useSearch } from '@cool-vue/crud';
import { useBase, checkPerm } from '/$/base';
import OrderManager from '/#/crud/components/order-manager.vue';
import { config } from '/@/config';
import {
	bannerApi,
	emptyBanner,
	bannerPayload,
	jumpTypes,
	type Banner,
	type TargetOption
} from '../banner-api';
defineOptions({ name: 'home-banner' });
const { user } = useBase();
const allowed = (action: string) =>
	user.info?.username === 'admin' || checkPerm('home:banner:' + action);
const permissions = Object.fromEntries(
	['page', 'info', 'add', 'update', 'delete'].map(action => [action, 'home:banner:' + action])
);
const permissionFlags = Object.fromEntries(
	['page', 'info', 'add', 'update', 'delete'].map(action => [action, allowed(action)])
);
permissionFlags.update = allowed('update') && allowed('info');
// 适配标准CRUD服务，沿用框架的分页、表单、错误提示和按钮权限。
const crudService = {
	namespace: 'admin/home/banner',
	permission: permissions,
	_permission: permissionFlags,
	page: (data: any = {}) =>
		bannerApi.page({ ...data, page: data.page ?? 1, size: data.size ?? 20 }),
	info: ({ id }: { id: number }) => bannerApi.info(id),
	add: (data: Banner) => bannerApi.save(data),
	update: (data: Banner) => bannerApi.save(data),
	delete: ({ ids }: { ids: number[] }) => bannerApi.delete(ids)
};
const bannerOrderService = {
	_permission: {
		get list() { return allowed('page'); },
		get update() { return allowed('update'); }
	},
	async list() {
		const size = 100;
		const first = await bannerApi.page({ page: 1, size });
		const { total } = first.pagination;
		const rows = [...first.list];
		for (let page = 2; (page - 1) * size < total; page++) {
			const result = await bannerApi.page({ page, size });
			if (result.pagination.total !== total) {
				throw new Error('广告列表已变化，请重新加载排序');
			}
			rows.push(...result.list);
		}
		if (rows.length !== total || rows.some(row => row.id == null) ||
			new Set(rows.map(row => String(row.id))).size !== total) {
			throw new Error('未取得完整广告列表，请重新加载排序');
		}
		return rows.map(row => ({ id: row.id!, name: row.title, sort: row.sort }));
	}
};
const options = ref<TargetOption[]>([]),
	targetsLoading = ref(false),
	targetsError = ref('');
const targetKeyword = ref(''),
	targetPage = ref(1),
	targetTotal = ref(0),
	targetLoadedCount = ref(0);
const hasMoreTargets = computed(() => targetLoadedCount.value < targetTotal.value);
const activeForm = ref<Banner>();
const statusBusy = ref(false),
	previewVisible = ref(false),
	previewLoading = ref(false),
	previewError = ref(''),
	previewRows = ref<Banner[]>([]);
let targetRevision = 0,
	previewRevision = 0,
	targetTimer: ReturnType<typeof setTimeout> | undefined;
function typeName(type: string) {
	return jumpTypes.find(item => item.value === type)?.label || type;
}
function imageSource(url: string) {
	return url?.startsWith('/') ? config.baseUrl + url : url;
}
function resetTargets() {
	targetRevision++;
	clearTimeout(targetTimer);
	options.value = [];
	targetsError.value = '';
	targetsLoading.value = false;
	targetKeyword.value = '';
	targetTotal.value = 0;
	targetLoadedCount.value = 0;
}
function changeType(scope: Banner) {
	scope.jumpTarget = null;
	resetTargets();
}
function searchTargets(keyword: string) {
	targetKeyword.value = keyword;
	clearTimeout(targetTimer);
	targetRevision++;
	targetTimer = setTimeout(() => void fetchTargets(true), 350);
}
async function fetchTargets(first: boolean) {
	const type = activeForm.value?.jumpType;
	if (!type || !['STORE', 'COUPON'].includes(type) || !allowed('targetOptions')) return;
	const revision = ++targetRevision,
		nextPage = first ? 1 : targetPage.value + 1;
	targetsLoading.value = true;
	targetsError.value = '';
	try {
		const result = await bannerApi.targets(type, targetKeyword.value.trim(), nextPage);
		if (revision !== targetRevision || type !== activeForm.value?.jumpType) return;
		const keep = first
			? options.value.filter(item => String(item.id) === activeForm.value?.jumpTarget)
			: options.value;
		options.value = Array.from(
			new Map([...keep, ...result.list].map(item => [item.id, item])).values()
		);
		targetPage.value = nextPage;
		targetTotal.value = result.pagination.total;
		targetLoadedCount.value = first
			? result.list.length
			: targetLoadedCount.value + result.list.length;
	} catch (e: any) {
		if (revision === targetRevision) targetsError.value = e.message || '目标加载失败';
	} finally {
		if (revision === targetRevision) targetsLoading.value = false;
	}
}
const Upsert = useUpsert({
	dialog: { width: '720px' },
	onOpened(data) {
		resetTargets();
		if (!data.id) Object.assign(data, emptyBanner());
		activeForm.value = data;
		if (data.id && data.jumpTarget && ['STORE', 'COUPON'].includes(data.jumpType))
			options.value = [
				{ id: Number(data.jumpTarget), name: data.targetName || '原跳转目标' }
			];
	},
	onClosed() {
		resetTargets();
		activeForm.value = undefined;
	},
	onSubmit(data, { next, done }) {
		if (data.startTime && data.endTime && data.startTime >= data.endTime) {
			ElMessage.warning('结束时间必须晚于开始时间');
			done();
			return;
		}
		if (data.jumpType !== 'NONE' && !String(data.jumpTarget ?? '').trim()) {
			ElMessage.warning('请选择或填写跳转目标');
			done();
			return;
		}
		next(bannerPayload(data as Banner));
	},
	items: [
		{
			label: '广告名称',
			prop: 'title',
			required: true,
			component: {
				name: 'el-input',
				props: {
					maxlength: 100,
					showWordLimit: true,
					placeholder: '填写便于运营辨认的名称'
				}
			},
			span: 24
		},
		{
			label: '轮播图片',
			prop: 'imageUrl',
			required: true,
			component: {
				name: 'cl-upload',
				props: { type: 'image', accept: 'image/*', limit: 1, limitSize: 5 }
			},
			span: 24
		},
		{
			label: '跳转类型',
			prop: 'jumpType',
			required: true,
			component: { name: 'slot-jumpType' },
			span: 24
		},
		{
			label: '跳转目标',
			prop: 'jumpTarget',
			component: { name: 'slot-target' },
			hidden: ({ scope }) => scope.jumpType === 'NONE',
			span: 24
		},
		{
			label: '启停状态',
			prop: 'status',
			required: true,
			component: {
				name: 'el-select',
				options: [
					{ label: '停用', value: 0 },
					{ label: '启用', value: 1 }
				]
			},
			span: 12
		},
		{
			label: '开始时间（北京时间）',
			prop: 'startTime',
			component: {
				name: 'el-date-picker',
				props: {
					type: 'datetime',
					valueFormat: 'YYYY-MM-DD HH:mm:ss',
					clearable: true,
					placeholder: '留空表示不限'
				}
			},
			span: 12
		},
		{
			label: '结束时间（北京时间）',
			prop: 'endTime',
			component: {
				name: 'el-date-picker',
				props: {
					type: 'datetime',
					valueFormat: 'YYYY-MM-DD HH:mm:ss',
					clearable: true,
					placeholder: '留空表示不限'
				}
			},
			span: 12
		}
	]
});
const Table = useTable({
	props: { showOverflowTooltip: true },
	columns: [
		{ type: 'selection' },
		{ label: '广告图片', prop: 'imageUrl', width: 170 },
		{ label: '广告名称', prop: 'title', minWidth: 160 },
		{ label: '跳转内容', prop: 'targetName', minWidth: 180 },
		{ label: '排序', prop: 'sort', width: 75 },
		{ label: '启停', prop: 'status', width: 85 },
		{ label: '展示状态', prop: 'displayStateText', width: 120 },
		{ label: '展示时间（北京时间）', prop: 'schedule', minWidth: 190 },
		{ label: '上下架', prop: 'statusActions', width: 85, hidden: !allowed('status') },
		{ type: 'op', width: 150, buttons: ['edit', 'delete'] }
	]
});
const Search = useSearch({
	items: [
		{
			label: '广告名称',
			prop: 'keyword',
			component: {
				name: 'el-input',
				props: { clearable: true, maxlength: 100, placeholder: '输入广告名称' }
			},
			style: { width: '180px' }
		},
		{
			label: '启停状态',
			prop: 'status',
			component: {
				name: 'el-select',
				props: { clearable: true, placeholder: '全部' },
				options: [
					{ label: '启用', value: 1 },
					{ label: '停用', value: 0 }
				]
			},
			style: { width: '140px' }
		},
		{
			label: '跳转类型',
			prop: 'jumpType',
			component: {
				name: 'el-select',
				props: { clearable: true, placeholder: '全部' },
				options: jumpTypes.map(item => ({ label: item.label, value: item.value }))
			},
			style: { width: '160px' }
		}
	],
	onSearch(data, { next }) {
		next({
			keyword: data.keyword?.trim() || '',
			status: data.status ?? null,
			jumpType: data.jumpType || null
		});
	}
});
const Crud = useCrud({ service: crudService }, app => app.refresh());
async function toggle(row: Banner) {
	if (!allowed('status') || statusBusy.value || !row.id) return;
	try {
		await ElMessageBox.confirm(
			row.status === 1
				? '停用后将不再展示该广告，确认停用？'
				: '启用后将在指定时间内展示该广告，确认启用？',
			'广告上下架',
			{ type: 'warning' }
		);
	} catch {
		return;
	}
	statusBusy.value = true;
	try {
		await bannerApi.status(row.id, row.status === 1 ? 0 : 1);
		ElMessage.success('状态已更新');
		await Crud.value?.refresh();
	} catch (e: any) {
		ElMessage.error(e.message || '状态更新失败');
	} finally {
		statusBusy.value = false;
	}
}
async function openPreview() {
	previewVisible.value = true;
	previewLoading.value = true;
	previewError.value = '';
	previewRows.value = [];
	const revision = ++previewRevision;
	try {
		const result = await bannerApi.preview();
		if (revision === previewRevision) previewRows.value = result;
	} catch (e: any) {
		if (revision === previewRevision) previewError.value = e.message || '预览加载失败';
	} finally {
		if (revision === previewRevision) previewLoading.value = false;
	}
}
onBeforeUnmount(() => {
	resetTargets();
	previewRevision++;
});
</script>
<style scoped lang="scss">
.banner-thumbnail {
	width: 140px;
	height: 66px;
	border-radius: 6px;
	background: var(--el-fill-color-light);
}
.banner-target {
	display: block;
	color: var(--el-text-color-secondary);
	font-size: 12px;
	max-width: 240px;
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
}
.banner-target-field {
	width: 100%;
	display: flex;
	flex-direction: column;
	gap: 8px;
}
.banner-hint {
	color: var(--el-text-color-secondary);
	font-size: 12px;
	line-height: 1.6;
}
.banner-error {
	color: var(--el-color-danger);
}
.banner-preview-image {
	width: 100%;
	height: 190px;
}
</style>
