<template>
	<CatalogWorkbench title="商家分类" description="维护分类层级、前台展示和排序，按分类查看商家。">
	<cl-crud ref="Crud">
		<cl-row>
			<!-- 刷新按钮 -->
			<cl-refresh-btn />
			<!-- 新增按钮 -->
			<cl-add-btn />
			<!-- 删除按钮 -->
			<cl-multi-delete-btn />
			<OrderManager :service="service.merchant.category" field="sort" @saved="Crud?.refresh()" />
			<cl-flex1 />
		</cl-row>
		<cl-row><cl-search ref="Search" reset-btn /></cl-row>

		<cl-row>
			<!-- 数据表格 -->
			<cl-table ref="Table" />
		</cl-row>

		<!-- 新增、编辑 -->
		<cl-upsert ref="Upsert">
			<template #slot-parent="{ scope }">
				<el-select v-model="scope.parentId" clearable filterable placeholder="顶级分类可留空" style="width: 100%">
					<el-option v-for="item in parentOptions(scope.id)" :key="item.id" :label="item.name" :value="item.id" />
				</el-select>
			</template>
		</cl-upsert>
	</cl-crud>
	</CatalogWorkbench>
</template>

<script lang="ts" setup>
import CatalogWorkbench from '/@/modules/workspace/components/catalog-workbench.vue';
defineOptions({
	name: "merchant-category",
});

import { useCrud, useTable, useUpsert, useSearch } from "@cool-vue/crud";
import { useCool } from "/@/cool";
import { useI18n } from "vue-i18n";
import { Plugins } from "/#/crud";
import { useBase } from "/$/base";
import { ElMessage } from "element-plus";
import { ref } from "vue";
import OrderManager from '/#/crud/components/order-manager.vue';
const parentRows = ref<Eps.MerchantCategoryEntity[]>([]);
function parentOptions(currentId?: number) {
	if (currentId != null && parentRows.value.some(item => item.parentId === currentId)) return [];
	return parentRows.value.filter(item => item.parentId == null && item.id !== currentId);
}

const { service, router } = useCool();
const { menu } = useBase();
const { t } = useI18n();

function viewMerchants(category: { id: number }) {
	const target = menu.routes.find(item => item.viewPath?.replace(/^\//, "") === "modules/merchant/views/info.vue");
	if (!target?.path) {
		ElMessage.warning("当前账号没有可用的商家列表菜单，请联系管理员配置");
		return;
	}
	void router.push({ path: target.path, query: { categoryId: String(category.id) } });
}

// cl-upsert
const Upsert = useUpsert({
	async onOpened(data) {
		if (!data.id) { data.status ??= 1; data.showOnFront ??= 1; data.sort ??= 0; }
		try { parentRows.value = await service.merchant.category.list(); }
		catch { parentRows.value = []; ElMessage.error("上级分类加载失败，请重试"); }
	},
	items: [
		{
			label: t("分类名称"),
			prop: "name",
			component: { name: "el-input", props: { clearable: true } },
			span: 12,
			required: true,
		},
		{
			label: t("选择上级分类，顶级分类为空"),
			prop: "parentId",
			component: { name: "slot-parent" },
			span: 12,
		},
		{
			label: t("分类图标"),
			prop: "icon",
			component: { name: "cl-upload", props: { type: "image", accept: "image/*", limit: 1, limitSize: 5 } },
			span: 12,
		},
		{
			label: t("排序值，越小越靠前"),
			prop: "sort",
			hidden: true,
			component: { name: "el-input-number", props: { min: 0, precision: 0 } },
			span: 12,
			required: true,
		},
		{
			label: t("前台展示"),
			prop: "showOnFront",
			component: { name: "el-select", options: [{ label: t("显示"), value: 1 }, { label: t("隐藏"), value: 0 }] },
			span: 12,
			required: true,
		},
		{
			label: t("状态"),
			prop: "status",
			component: { name: "el-select", options: [{ label: t("启用"), value: 1 }, { label: t("停用"), value: 0 }] },
			span: 12,
			required: true,
		},
	],
});

// cl-table
const Table = useTable({
	props: { showOverflowTooltip: true },
	columns: [
		{ type: "selection" },
		{ label: t("分类名称"), prop: "name", minWidth: 120, align: "left" },
		{ label: t("分类图标地址"), prop: "icon", minWidth: 100, component: { name: "cl-image", props: { size: 36, fit: "contain" } } },
		{ label: t("排序值，越小越靠前"), prop: "sort", minWidth: 120 },
		{ label: t("前台展示"), prop: "showOnFront", minWidth: 100, formatter: row => Number(row.showOnFront) === 1 ? t("显示") : t("隐藏") },
		{ label: t("状态"), prop: "status", minWidth: 90, formatter: row => Number(row.status) === 1 ? t("启用") : t("停用") },
		{
			label: t("创建时间"),
			prop: "createTime",
			minWidth: 170,
			sortable: "desc",
			component: { name: "cl-date-text" },
		},
		{
			label: t("更新时间"),
			prop: "updateTime",
			minWidth: 170,
			sortable: "custom",
			component: { name: "cl-date-text" },
		},
		{
			type: "op",
			width: 260,
			buttons: [
				{
					label: t("查看商家"),
					hidden: !service.merchant.info._permission?.page,
					onClick: ({ scope }) => viewMerchants(scope.row),
				},
				"edit",
				"delete",
			],
		},
	],
	plugins: [Plugins.Table.toTree()],
});

// cl-search
const Search = useSearch({
	items: [
		{ label: t("分类名称"), prop: "name", component: { name: "el-input", props: { clearable: true, placeholder: "输入分类名称", maxlength: 50 }, style: { width: "180px" } } },
		{ label: t("状态"), prop: "status", component: { name: "el-select", props: { clearable: true, placeholder: "全部状态" }, options: [{ label: t("启用"), value: 1 }, { label: t("停用"), value: 0 }], style: { width: "140px" } } },
		{ label: t("前台展示"), prop: "showOnFront", component: { name: "el-select", props: { clearable: true, placeholder: "全部" }, options: [{ label: t("显示"), value: 1 }, { label: t("隐藏"), value: 0 }], style: { width: "140px" } } },
	],
	onSearch(data, { next }) {
		next({ name: data.name?.trim() || null, status: data.status ?? null, showOnFront: data.showOnFront ?? null });
	},
});

// cl-crud
const Crud = useCrud(
	{
		service: service.merchant.category,
	},
	(app) => {
		app.refresh();
	},
);

</script>

