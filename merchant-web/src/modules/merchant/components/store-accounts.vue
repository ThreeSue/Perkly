<template>
	<cl-crud ref="Crud">
		<cl-row
			><el-alert
				title="每个店长/员工账号只能绑定一家门店。停用、转店、变更角色或重置密码后，原登录立即失效。重置密码为123456。"
				type="info"
				:closable="false"
				show-icon
		/></cl-row>
		<cl-row><cl-refresh-btn /><cl-add-btn /><cl-flex1 /></cl-row>
		<cl-row><cl-search ref="Search" reset-btn /></cl-row>
		<cl-row
			><cl-table ref="Table"
				><template #column-actions="{ scope }"
					><el-button size="small" :disabled="busy" @click="Upsert?.edit(scope.row)"
						>编辑/绑定门店</el-button
					><el-button
						size="small"
						:type="scope.row.status === 1 ? 'danger' : 'success'"
						plain
						:disabled="busy"
						@click="action(scope.row, 'status')"
						>{{ scope.row.status === 1 ? '停用' : '启用' }}</el-button
					><el-button
						size="small"
						type="warning"
						plain
						:disabled="busy"
						@click="action(scope.row, 'resetPassword')"
						>重置密码</el-button
					></template
				></cl-table
			></cl-row
		>
		<cl-row><cl-flex1 /><cl-pagination /></cl-row>
		<cl-upsert ref="Upsert"
			><template #slot-username="{ scope }"
				><el-input
					v-model="scope.username"
					:readonly="editing"
					maxlength="40"
					placeholder="4至40位字母、数字或下划线" /></template
			><template #slot-store="{ scope }"
				><el-select
					v-model="scope.targetStoreId"
					filterable
					:loading="optionsLoading"
					style="width: 100%"
					><el-option
						v-for="item in stores"
						:key="item.id"
						:value="item.id"
						:label="
							item.name + (item.status === 1 ? '' : '（门店停用）')
						" /></el-select></template
		></cl-upsert>
	</cl-crud>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useCrud, useTable, useSearch, useUpsert } from '@cool-vue/crud';
import { merchantRequest } from '/@/merchant-api';
const props = defineProps<{ storeId: number; storeName: string }>();
const api = (action: string, data?: unknown) =>
	merchantRequest('workspace/storeAccount/' + action, data);
const editing = ref(false);
const busy = ref(false),
	optionsLoading = ref(false),
	optionsFailed = ref(false);
const stores = ref<{ id: number; name: string; status: number }[]>([]);
async function loadStores() {
	optionsLoading.value = true;
	optionsFailed.value = false;
	try {
		stores.value = await api('options?storeId=' + props.storeId);
	} catch (error: any) {
		optionsFailed.value = true;
		ElMessage.error(error.message || '门店加载失败，请关闭后重试');
	} finally {
		optionsLoading.value = false;
	}
}
const Search = useSearch({
	items: [
		{
			label: '账号/姓名',
			prop: 'keyWord',
			component: {
				name: 'el-input',
				props: { clearable: true, placeholder: '输入账号或姓名', maxlength: 100 }
			}
		}
	]
});
const Table = useTable({
	autoHeight: false,
	props: { maxHeight: 360, showOverflowTooltip: true },
	columns: [
		{ label: '账号', prop: 'username', minWidth: 160 },
		{ label: '姓名', prop: 'name', minWidth: 120 },
		{
			label: '角色',
			prop: 'role',
			width: 90,
			formatter: row => (row.role === 'MANAGER' ? '店长' : '员工')
		},
		{ label: '唯一所属门店', prop: 'storeName', minWidth: 190 },
		{
			label: '状态',
			prop: 'status',
			width: 90,
			formatter: row => (row.status === 1 ? '启用' : '停用')
		},
		{ label: '操作', prop: 'actions', width: 360, fixed: 'right' }
	]
});
const Upsert = useUpsert({
	dialog: { width: '650px' },
	props: { labelPosition: 'top' },
	items: [
		{
			label: '登录账号',
			prop: 'username',
			required: true,
			component: { name: 'slot-username' },
			rules: [{ pattern: /^[A-Za-z0-9_]{4,40}$/, message: '账号需4至40位字母、数字或下划线' }]
		},
		{
			label: '姓名',
			prop: 'name',
			required: true,
			component: { name: 'el-input', props: { maxlength: 50 } }
		},
		{
			label: '角色',
			prop: 'role',
			required: true,
			value: 'STAFF',
			component: {
				name: 'el-select',
				options: [
					{ label: '店长', value: 'MANAGER' },
					{ label: '员工', value: 'STAFF' }
				]
			}
		},
		{
			label: '唯一所属门店',
			prop: 'targetStoreId',
			required: true,
			component: { name: 'slot-store' }
		},
		{
			label: '账号状态',
			prop: 'status',
			required: true,
			value: 1,
			component: {
				name: 'el-select',
				options: [
					{ label: '启用', value: 1 },
					{ label: '停用', value: 0 }
				]
			}
		},
		{
			label: '初始密码',
			prop: 'password',
			value: '123456',
			required: true,
			hidden: ({ scope }: any) => !!scope.id,
			component: {
				name: 'el-input',
				props: { type: 'password', showPassword: true, maxlength: 64 }
			},
			rules: [{ min: 6, max: 64, message: '密码需6至64位' }]
		}
	],
	onOpened(data) {
        editing.value = !!data.id;
		Upsert.value?.setForm('targetStoreId', data.storeId || props.storeId);
		void loadStores();
	},
	onSubmit(data, { next, done }) {
		if (optionsLoading.value || optionsFailed.value) {
			ElMessage.warning('门店选项未加载完成，请关闭后重试');
			done();
			return;
		}
		next({
			id: data.id,
			storeId: props.storeId,
			targetStoreId: data.targetStoreId,
			username: data.username,
			name: data.name,
			role: data.role,
			status: data.status,
			...(!data.id ? { password: data.password } : {})
		});
	}
});
const Crud = useCrud(
	{
		service: {
			page: (data: any) => api('page', { ...data, storeId: props.storeId }),
			info: ({ id }: any) => api('info?storeId=' + props.storeId + '&id=' + id),
			add: (data: any) => api('save', data),
			update: (data: any) => api('save', data)
		}
	},
	app => app.refresh()
);
async function action(row: any, operation: 'status' | 'resetPassword') {
	if (busy.value) return;
	try {
		await ElMessageBox.confirm(
			operation === 'resetPassword'
				? `确认将“${row.name}（${row.username}）”的密码重置为123456？原登录立即失效。`
				: `确认${row.status === 1 ? '停用' : '启用'}“${row.name}（${row.username}）”？原登录将失效。`,
			'门店账号管理',
			{ type: 'warning' }
		);
		busy.value = true;
		await api(operation, {
			id: row.id,
			storeId: props.storeId,
			...(operation === 'status' ? { status: row.status === 1 ? 0 : 1 } : {})
		});
		ElMessage.success(operation === 'resetPassword' ? '密码已重置为123456' : '账号状态已更新');
		await Crud.value?.refresh();
	} catch (error: any) {
		if (error?.message) ElMessage.error(error.message);
	} finally {
		busy.value = false;
	}
}
</script>
