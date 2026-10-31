<template>
    <el-button v-if="allowed" :disabled="disabled" @click="open">调整顺序</el-button>
    <el-dialog v-model="visible" title="调整显示顺序" width="min(680px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" :before-close="close">
        <el-alert title="拖动调整同级顺序，也可用上下按钮；不分页展示完整范围，保存后生效。不会改变上下级归属。" type="info" :closable="false" />
        <el-alert v-if="error" :title="error" type="error" :closable="false" class="notice" />
        <el-scrollbar v-loading="loading" max-height="55vh" class="order-list">
            <el-tree :data="tree" :props="{ label: 'name' }" node-key="id" default-expand-all :expand-on-click-node="false" :draggable="!saving && !loading" :allow-drag="canDrag" :allow-drop="canDrop" @node-drop="dirty = true">
                <template #default="{data}">
                    <div class="order-row"><span class="handle">⠿</span><span class="label">{{ data.name || '未命名项目' }}</span>
                        <el-button link :disabled="saving || !canMove(data, -1)" :aria-label="`上移${data.name}`" @click.stop="move(data, -1)">上移</el-button>
                        <el-button link :disabled="saving || !canMove(data, 1)" :aria-label="`下移${data.name}`" @click.stop="move(data, 1)">下移</el-button>
                    </div>
                </template>
            </el-tree>
            <el-empty v-if="!loading && !tree.length" description="暂无可排序数据" :image-size="64" />
        </el-scrollbar>
        <template #footer><el-button :disabled="saving" @click="load">重置顺序</el-button><el-button :disabled="saving" @click="close()">取消</el-button><el-button type="primary" :disabled="loading || !dirty" :loading="saving" @click="save">保存顺序</el-button></template>
    </el-dialog>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { buildOrderTree, orderChanges, orderSignature, siblingList, sameOrderParent, type OrderRow } from './order-model';
const props = withDefaults(defineProps<{
    service: any; field?: string; descending?: boolean; params?: Record<string, unknown>; disabled?: boolean; protectRoot?: boolean;
}>(), { field: 'orderNum', descending: false, disabled: false, protectRoot: false });
const emit = defineEmits<{ saved: [] }>();
const visible = ref(false), loading = ref(false), saving = ref(false), dirty = ref(false), error = ref('');
const tree = ref<OrderRow[]>([]);
let original: OrderRow[] = [], scope: Record<string, unknown> = {}, generation = 0, alive = true;
const allowed = computed(() => !!props.service?._permission?.list && !!props.service?._permission?.update);
async function load() {
    if (saving.value) return;
    const round = ++generation; loading.value = true; error.value = ''; dirty.value = false; tree.value = [];
    try {
        const rows = await props.service.list(scope);
        if (!alive || !visible.value || round !== generation) return;
        if (!Array.isArray(rows)) throw new Error('未取得完整列表，请重试');
        original = rows.map(row => ({ ...row })); tree.value = buildOrderTree(rows, props.field, props.descending);
    } catch (cause: any) { if (round === generation && alive) { original = []; error.value = cause.message || '加载失败，请重试'; } }
    finally { if (round === generation && alive) loading.value = false; }
}
function open() { if (!allowed.value) return; scope = { ...props.params }; visible.value = true; void load(); }
async function close(next?: () => void) {
    if (saving.value) return;
    if (dirty.value) { try { await ElMessageBox.confirm('尚未保存排序，确定放弃本次调整？', '放弃调整', { type: 'warning' }); } catch { return; } }
    generation++; visible.value = false; dirty.value = false; next?.();
}
function isProtected(row: OrderRow) { return props.protectRoot && sameOrderParent(row, { id: 0, parentId: null }); }
function canDrag(node: any) { return !saving.value && !loading.value && !isProtected(node.data); }
function canDrop(source: any, target: any, type: string) { return type !== 'inner' && canDrag(source) && sameOrderParent(source.data, target.data); }
function canMove(row: OrderRow, step: number) {
    if (saving.value || loading.value || isProtected(row)) return false;
    const list = siblingList(tree.value, row.id), index = list.findIndex(item => String(item.id) === String(row.id));
    return index >= 0 && index + step >= 0 && index + step < list.length && sameOrderParent(row, list[index + step]);
}
function move(row: OrderRow, step: number) {
    if (!canMove(row, step)) return;
    const list = siblingList(tree.value, row.id), index = list.findIndex(item => String(item.id) === String(row.id));
    [list[index], list[index + step]] = [list[index + step], list[index]]; dirty.value = true;
}
async function save() {
    if (saving.value || loading.value || !dirty.value) return;
    saving.value = true; error.value = '';
    try {
        const latest = await props.service.list(scope);
        if (orderSignature(latest, props.field) !== orderSignature(original, props.field)) throw new Error('数据或排序已被其他操作修改，请重置顺序后重新调整');
        const changes = orderChanges(tree.value, original, props.field, props.descending, props.protectRoot);
        for (const change of changes) await props.service.update(change);
        dirty.value = false; visible.value = false; ElMessage.success('排序已保存'); emit('saved');
    } catch (cause: any) {
        // 各模块使用已有update权限及字段合并接口。失败不误报全部成功，强制重新读取实际顺序。
        dirty.value = false; emit('saved');
        saving.value = false; await load();
        error.value = `${cause?.message || '保存失败'}。若已有部分写入，请按当前实际顺序重新调整。`;
    } finally { saving.value = false; }
}
onBeforeUnmount(() => { alive = false; generation++; });
</script>
<style scoped>
.notice { margin-top:12px; }.order-list { margin-top:16px; }.order-row { display:flex; align-items:center; gap:10px; width:100%; padding-right:8px; box-sizing:border-box; min-width:0; }.label { flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.handle { cursor:grab; color:var(--el-text-color-secondary); font-size:20px; }
:deep(.el-tree-node__content) { height:40px; border-bottom:1px solid var(--el-border-color-lighter); }
</style>
