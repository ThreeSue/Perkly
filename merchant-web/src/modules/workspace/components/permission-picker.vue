<template>
	<div class="permission-picker">
		<div class="permission-tools">
			<el-input
				v-model="keyword"
				clearable
				aria-label="搜索菜单或操作名称"
				placeholder="搜索菜单或操作名称"
			/><el-button @click="expandAll = !expandAll">{{
				expandAll ? '收起' : '展开'
			}}</el-button
			><el-button v-if="!disabled" :disabled="!hasEditableSelection" @click="clearVisible"
				>清除可分配项</el-button
			>
		</div>
		<div class="permission-note" aria-live="polite">
			<span
				>已选择 <strong>{{ modelValue?.length || 0 }}</strong> 项</span
			><span>页面与操作分别勾选；搜索不会清除原选择</span>
		</div>
		<el-tree
			v-show="!emptyTitle"
			ref="tree"
			:key="expandAll ? 'expanded' : 'collapsed'"
			:data="nodes"
			node-key="id"
			:props="{ label: 'name', children: 'children', disabled: 'disabled' }"
			show-checkbox
			check-strictly
			:default-expand-all="expandAll"
			:default-checked-keys="checked"
			:filter-node-method="filterNode"
			:expand-on-click-node="false"
			@check="changed"
		>
			<template #default="{ data }"
				><div
					class="permission-node"
					:class="{ 'is-unavailable': data.unavailable }"
					:title="data.unavailable ? '超出当前账号或上级授权范围，无权分配' : ''"
				>
					<span>{{ data.name }}</span
					><el-tag v-if="data.type === 2" size="small" type="info" effect="plain"
						>操作</el-tag
					><el-tag v-else-if="data.type === 0" size="small" effect="plain">目录</el-tag
					><el-tag v-if="data.unavailable" size="small" type="info">无权分配</el-tag>
				</div></template
			>
		</el-tree>
		<ManagementState v-if="emptyTitle" :title="emptyTitle">
			<el-button v-if="keyword.trim()" @click="keyword = ''">清除搜索</el-button>
		</ManagementState>
	</div>
</template>
<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { ElTree } from 'element-plus';
import ManagementState from './management-state.vue';
const props = defineProps<{
	modelValue?: number[];
	menus: any[];
	disabled?: boolean;
	unavailableIds?: number[];
}>();
const emit = defineEmits<{ 'update:modelValue': [ids: number[]] }>();
const keyword = ref(''),
	expandAll = ref(true),
	tree = ref<InstanceType<typeof ElTree>>();
const checked = computed(() => props.modelValue || []);
const editableIds = computed(
	() => new Set(props.menus.filter(m => !props.unavailableIds?.includes(m.id)).map(m => m.id))
);
const hasEditableSelection = computed(() => checked.value.some(id => editableIds.value.has(id)));
const emptyTitle = computed(() => {
	if (!props.menus.length) return '当前没有可配置的功能';
	return props.menus.some(menu => filterNode(keyword.value.trim(), menu)) ? '' : '没有匹配的功能';
});
const nodes = computed(() => {
	const byId = new Map<number, any>();
	const ordered = [...props.menus].sort((a, b) => (a.orderNum || 0) - (b.orderNum || 0));
	ordered.forEach(m =>
		byId.set(m.id, {
			...m,
			disabled: props.disabled || props.unavailableIds?.includes(m.id),
			unavailable: props.unavailableIds?.includes(m.id),
			children: []
		})
	);
	const roots: any[] = [];
	for (const m of ordered) {
		const node = byId.get(m.id);
		const parent = byId.get(m.parentId);
		if (parent && parent !== node && !wouldCycle(m.id, m.parentId, byId))
			parent.children.push(node);
		else roots.push(node);
	}
	return roots;
});
function wouldCycle(id: number, parent: number, rows: Map<number, any>) {
	const seen = new Set([id]);
	let p: number | undefined = parent;
	while (p) {
		if (seen.has(p)) return true;
		seen.add(p);
		p = rows.get(p)?.parentId;
	}
	return false;
}
function filterNode(value: string, data: any) {
	return !value || `${data.name} ${data.perms || ''}`.toLowerCase().includes(value.toLowerCase());
}
function changed(_data: any, value: { checkedKeys: number[] }) {
	if (props.disabled) return;
	// Preserve stored IDs outside the catalog: parent restrictions may temporarily hide them.
	const shown = editableIds.value;
	emit('update:modelValue', [
		...new Set([
			...(props.modelValue || []).filter(id => !shown.has(id)),
			...value.checkedKeys.filter(id => shown.has(id))
		])
	]);
}
function clearVisible() {
	if (props.disabled) return;
	const shown = editableIds.value;
	emit(
		'update:modelValue',
		(props.modelValue || []).filter(id => !shown.has(id))
	);
}
watch(
	[checked, nodes, keyword, expandAll],
	async () => {
		await nextTick();
		tree.value?.setCheckedKeys(checked.value);
		tree.value?.filter(keyword.value.trim());
	},
	{ immediate: true }
);
</script>
<style scoped>
.permission-picker {
	border: 1px solid var(--el-border-color-lighter);
	border-radius: 10px;
	padding: 14px;
}
.permission-tools {
	display: flex;
	align-items: center;
	gap: 8px;
	flex-wrap: wrap;
}
.permission-tools .el-input {
	flex: 1;
	min-width: min(160px, 100%);
}
.permission-tools .el-button {
	margin: 0;
}
.permission-note {
	display: flex;
	justify-content: space-between;
	gap: 12px;
	flex-wrap: wrap;
	color: var(--el-text-color-secondary);
	font-size: 12px;
	margin: 14px 0;
}
.permission-note strong {
	color: var(--workspace-accent-text);
}
.permission-node {
	display: flex;
	align-items: center;
	gap: 10px;
	min-width: 0;
}
.permission-node.is-unavailable {
	color: var(--el-text-color-disabled);
	cursor: not-allowed;
}
.permission-node > span:first-child {
	overflow: hidden;
	text-overflow: ellipsis;
}
.permission-node > .el-tag {
	flex-shrink: 0;
}
.permission-picker :deep(.el-tree-node__content) {
	min-height: 36px;
	height: auto;
	padding-right: 8px;
}
.permission-picker :deep(.el-tree) {
	max-height: 370px;
	overflow: auto;
}
</style>
