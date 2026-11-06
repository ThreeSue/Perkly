<template>
	<div class="permission-picker">
		<div class="permission-tools">
			<el-input v-model="keyword" clearable placeholder="搜索菜单或操作名称" /><el-button
				@click="expandAll = !expandAll"
				>{{ expandAll ? '收起' : '展开' }}</el-button
			><el-button :disabled="disabled" @click="clearVisible">清除此目录选择</el-button>
		</div>
		<div class="permission-note">
			<span
				>已选择 <strong>{{ modelValue?.length || 0 }}</strong> 项</span
			><span>页面与操作分别勾选；搜索不会清除原选择</span>
		</div>
		<el-tree
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
				><div class="permission-node" :class="{ 'is-unavailable': data.unavailable }" :title="data.unavailable ? '超出当前账号或上级授权范围，无权分配' : ''">
					<span>{{ data.name }}</span
					><el-tag v-if="data.type === 2" size="small" type="info" effect="plain"
						>操作</el-tag
					><el-tag v-else-if="data.type === 0" size="small" effect="plain">目录</el-tag
					><el-tag v-if="data.unavailable" size="small" type="info">无权分配</el-tag>
				</div></template
			>
		</el-tree>
		<el-empty v-if="!nodes.length" description="当前没有可配置的功能" :image-size="52" />
	</div>
</template>
<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { ElTree } from 'element-plus';
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
	const shown = new Set(props.menus.filter(m => !props.unavailableIds?.includes(m.id)).map(m => m.id));
	emit('update:modelValue', [
		...new Set([...(props.modelValue || []).filter(id => !shown.has(id)), ...value.checkedKeys.filter(id => shown.has(id))])
	]);
}
function clearVisible() {
	if (props.disabled) return;
	const shown = new Set(props.menus.filter(m => !props.unavailableIds?.includes(m.id)).map(m => m.id));
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
	min-width: 160px;
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
	color: var(--el-color-primary);
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
.permission-node > span {
	overflow: hidden;
	text-overflow: ellipsis;
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
