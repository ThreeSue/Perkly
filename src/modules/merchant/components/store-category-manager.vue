<template>
	<CatalogWorkbench
		title="门店类型"
		description="平台统一维护门店类型。停用后不可新选，已关联门店保留原类型。"
	>
		<cl-crud ref="Crud">
			<cl-row
				><cl-add-btn /><cl-refresh-btn /><cl-multi-delete-btn /><OrderManager
					:service="service.merchant.storeCategory"
					field="sort"
					@saved="Crud?.refresh()" /><cl-flex1 /><cl-search ref="Search" reset-btn
			/></cl-row>
			<cl-row><cl-table ref="Table" /></cl-row>
			<cl-row><cl-flex1 /><cl-pagination /></cl-row>
			<cl-upsert ref="Upsert" />
		</cl-crud>
	</CatalogWorkbench>
</template>

<script lang="ts" setup>
import CatalogWorkbench from '/@/modules/workspace/components/catalog-workbench.vue';
import { useCrud, useTable, useSearch, useUpsert } from '@cool-vue/crud';
import { useCool } from '/@/cool';
import OrderManager from '/#/crud/components/order-manager.vue';

defineOptions({ name: 'store-category-manager' });
const { service } = useCool();
const statuses = [
	{ label: '启用', value: 1, type: 'success' },
	{ label: '停用', value: 0, type: 'info' }
];
const Table = useTable({
	columns: [
		{ type: 'selection' },
		{
			label: '门店类型',
			prop: 'name',
			className: 'catalog-name',
			minWidth: 160,
			showOverflowTooltip: true
		},
		{ label: '排序', prop: 'sort', width: 100, sortable: 'asc' },
		{ label: '状态', prop: 'status', width: 100, dict: statuses },
		{ label: '关联门店数', prop: 'storeCount', minWidth: 120 },
		{
			label: '创建时间',
			prop: 'createTime',
			minWidth: 170,
			component: { name: 'cl-date-text' },
			showOverflowTooltip: true
		},
		{
			label: '更新时间',
			prop: 'updateTime',
			minWidth: 170,
			component: { name: 'cl-date-text' },
			showOverflowTooltip: true
		},
		{ type: 'op', fixed: 'right', width: 180, buttons: ['edit', 'delete'] }
	]
});
const Search = useSearch({
	items: [
		{
			label: '类型名称',
			prop: 'keyWord',
			component: {
				name: 'el-input',
				props: { placeholder: '输入类型名称', clearable: true, maxlength: 50 },
				style: { width: '200px' }
			}
		},
		{
			label: '状态',
			prop: 'status',
			component: {
				name: 'el-select',
				props: { placeholder: '全部状态', clearable: true },
				options: statuses,
				style: { width: '150px' }
			}
		}
	]
});
const Upsert = useUpsert({
	dialog: { width: '560px' },
	onOpened(data) {
		if (!data.id) {
			data.sort ??= 0;
			data.status ??= 1;
		}
	},
	items: [
		{
			label: '类型名称',
			prop: 'name',
			required: true,
			component: {
				name: 'el-input',
				props: { maxlength: 50, clearable: true, placeholder: '例如：社区店' }
			}
		},
		{
			label: '排序',
			prop: 'sort',
			hidden: true,
			required: true,
			component: {
				name: 'el-input-number',
				props: { min: 0, max: 2147483647, precision: 0, 'controls-position': 'right' }
			},
			span: 12
		},
		{
			label: '状态',
			prop: 'status',
			required: true,
			component: { name: 'el-select', options: statuses },
			span: 12
		}
	]
});
const Crud = useCrud({ service: service.merchant.storeCategory }, app =>
	app.refresh({ size: 20, order: 'sort', sort: 'asc' })
);
</script>
