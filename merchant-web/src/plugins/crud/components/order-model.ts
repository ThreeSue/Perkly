export interface OrderRow { id: string | number; parentId?: string | number | null; name?: string; children?: OrderRow[]; [key: string]: any }
const key = (id: unknown) => id == null || String(id) === '0' ? '' : String(id);
export const sameOrderParent = (a: OrderRow, b: OrderRow) => key(a.parentId) === key(b.parentId);
export function buildOrderTree(rows: OrderRow[], field: string, descending: boolean) {
    const nodes = new Map<string, OrderRow>();
    for (const row of rows) {
        if (row.id == null || nodes.has(String(row.id))) throw new Error('列表标识缺失或重复，不能排序');
        nodes.set(String(row.id), { ...row, children: [] });
    }
    const roots: OrderRow[] = [];
    for (const node of nodes.values()) {
        const visited = new Set<string>([String(node.id)]); let parent = nodes.get(key(node.parentId));
        while (parent) { if (visited.has(String(parent.id))) throw new Error('上下级数据存在循环，不能排序'); visited.add(String(parent.id)); parent = nodes.get(key(parent.parentId)); }
        const direct = nodes.get(key(node.parentId)); if (direct) direct.children!.push(node); else roots.push(node);
    }
    function sort(list: OrderRow[]) {
        list.sort((a,b) => (Number(a[field] || 0) - Number(b[field] || 0)) * (descending ? -1 : 1) || String(a.id).localeCompare(String(b.id), undefined, {numeric:true}));
        list.forEach(row => sort(row.children!));
    }
    sort(roots); return roots;
}
export function siblingList(tree: OrderRow[], id: string | number): OrderRow[] {
    if (tree.some(row => String(row.id) === String(id))) return tree;
    for (const row of tree) { const found = siblingList(row.children || [], id); if (found.length) return found; }
    return [];
}
export function orderSignature(rows: OrderRow[], field: string) {
    if (!Array.isArray(rows)) throw new Error('列表响应异常');
    return JSON.stringify(rows.map(row => [String(row.id), key(row.parentId), row[field] ?? null]).sort((a,b) => String(a[0]).localeCompare(String(b[0]))));
}
export function orderChanges(tree: OrderRow[], original: OrderRow[], field: string, descending: boolean, protectRoot = false) {
    const before = new Map(original.map(row => [String(row.id),row])), seen = new Set<string>(), changes: any[] = [];
    function visit(list: OrderRow[]) {
        list.forEach(row => {
            const old = before.get(String(row.id));
            if (!old || seen.has(String(row.id)) || !sameOrderParent(row,old)) throw new Error('排序范围或归属异常，请重新加载');
            seen.add(String(row.id));
            const group = list.filter(item => sameOrderParent(item,row));
            const initial = original.filter(item => sameOrderParent(item,row)).sort((a,b) => (Number(a[field] || 0)-Number(b[field] || 0))*(descending ? -1 : 1) || String(a.id).localeCompare(String(b.id),undefined,{numeric:true}));
            const moved = group.map(item => String(item.id)).join(',') !== initial.map(item => String(item.id)).join(',');
            const index = group.findIndex(item => String(item.id) === String(row.id));
            const value = descending ? group.length - index : index + 1;
            if (moved && !(protectRoot && !key(row.parentId)) && Number(old[field]) !== value) changes.push({ id: old.id, [field]: value });
            visit(row.children || []);
        });
    }
    visit(tree); if (seen.size !== original.length) throw new Error('排序列表不完整，请重新加载');
    return changes;
}
