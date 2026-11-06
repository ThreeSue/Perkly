<template>
	<cl-crud ref="Crud">
		<cl-row>
			<!-- 刷新按钮 -->
			<cl-refresh-btn />
			<!-- 删除按钮 -->
			<cl-multi-delete-btn />
			<cl-flex1 />

			<!-- 搜索 -->
			<cl-search ref="Search" />
		</cl-row>

		<cl-row>
			<!-- 数据表格 -->
			<cl-table ref="Table" />
		</cl-row>

		<cl-row>
			<cl-flex1 />
			<!-- 分页控件 -->
			<cl-pagination />
		</cl-row>

		<!-- 新增、编辑 -->
		<cl-upsert ref="Upsert" />
		<el-dialog v-model="detailVisible" :title="t('账号详情与绑定关系')" width="640px">
			<div v-loading="detailLoading">
				<template v-if="detail">
					<el-alert v-if="detail.historyPending" :closable="false" type="warning" :title="t('历史待处理记录：未自动删除或停用，处理前请检查业务数据。')" class="mb-4" />
					<el-descriptions :column="2" border>
						<el-descriptions-item :label="t('账号ID')">{{ detail.id }}</el-descriptions-item>
						<el-descriptions-item :label="t('昵称')">{{ detail.nickName || '—' }}</el-descriptions-item>
						<el-descriptions-item :label="t('手机号')">{{ maskPhone(detail.phone) }}</el-descriptions-item>
						<el-descriptions-item :label="t('微信绑定')">{{ detail.wechatBound ? t('已绑定') : t('未绑定') }}</el-descriptions-item>
						<el-descriptions-item :label="t('登录密码')">{{ detail.passwordSet ? t('已设置') : t('未设置') }}</el-descriptions-item>
						<el-descriptions-item :label="t('账号状态')">{{ options.status.find(item => item.value === detail?.status)?.label || '—' }}</el-descriptions-item>
						<el-descriptions-item :label="t('创建时间')" :span="2">{{ detail.createTime }}</el-descriptions-item>
						<el-descriptions-item v-if="detail.associatedAccountId" :label="t('微信当前关联账号ID')" :span="2">{{ detail.associatedAccountId }}</el-descriptions-item>
					</el-descriptions>
					<template v-if="detail.legacyAccounts?.length">
						<p class="mt-4 mb-2">{{ t('历史关联记录（保留待处理）') }}</p>
						<el-table :data="detail.legacyAccounts" border show-overflow-tooltip>
							<el-table-column prop="id" :label="t('旧账号ID')" width="110" />
							<el-table-column prop="nickName" :label="t('旧昵称')" />
							<el-table-column prop="phoneMasked" :label="t('手机号')" />
						</el-table>
					</template>
				</template>
			</div>
		</el-dialog>
	</cl-crud>
</template>

<script lang="ts" setup>
defineOptions({
	name: 'user-list'
});

import { useCrud, useSearch, useTable, useUpsert } from '@cool-vue/crud';
import { useI18n } from 'vue-i18n';
import { useCool } from '/@/cool';
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';

const { t } = useI18n();
const { service } = useCool();

interface AccountDetail {
	id: number; nickName?: string; phone?: string; status: number; createTime?: string;
	wechatBound: number; passwordSet: number; historyPending: number; associatedAccountId?: number;
	legacyAccounts?: { id: number; nickName?: string; phoneMasked?: string }[];
}
const detailVisible = ref(false);
const detailLoading = ref(false);
const detail = ref<AccountDetail | null>(null);
function maskPhone(phone?: string) { return phone ? phone.slice(0, 3) + '****' + phone.slice(-4) : t('未绑定'); }
async function showDetail(id: number) {
	detailVisible.value = true; detailLoading.value = true; detail.value = null;
	try { detail.value = await service.user.info.info({ id }) as unknown as AccountDetail; }
	catch (error) { ElMessage.error((error as Error).message || t('获取账号详情失败')); }
	finally { detailLoading.value = false; }
}

const options = reactive({
	wechatBound: [{ label: t('未绑定'), value: 0, type: 'info' }, { label: t('已绑定'), value: 1, type: 'success' }],
	passwordSet: [{ label: t('未设置'), value: 0, type: 'info' }, { label: t('已设置'), value: 1, type: 'success' }],
	historyPending: [{ label: t('正式账号'), value: 0, type: 'success' }, { label: t('历史待处理'), value: 1, type: 'warning' }],
	loginType: [
		{
			label: t('小程序'),
			value: 0,
			type: 'danger'
		},
		{
			label: t('公众号'),
			value: 1,
			type: 'success'
		},
		{
			label: t('H5'),
			value: 2
		}
	],
	gender: [
		{
			label: t('未知'),
			value: 0,
			type: 'info'
		},
		{
			label: t('男'),
			value: 1,
			type: 'success'
		},
		{
			label: t('女'),
			value: 2,
			type: 'danger'
		}
	],
	status: [
		{
			label: t('禁用'),
			value: 0,
			type: 'danger'
		},
		{
			label: t('正常'),
			value: 1,
			type: 'success'
		},
		{
			label: t('已注销'),
			value: 2,
			type: 'warning'
		}
	]
});

// cl-table
const Table = useTable({
	props: { showOverflowTooltip: true },
	columns: [
		{
			type: 'selection',
			width: 60
		},
		{
			label: t('昵称'),
			prop: 'nickName',
			minWidth: 150
		},
		{ label: t('用户ID'), prop: 'id', width: 100, sortable: 'custom' },
		{
			label: t('头像'),
			prop: 'avatarUrl',
			minWidth: 100,
			component: {
				name: 'cl-avatar'
			}
		},
		{
			label: t('手机号'),
			prop: 'phone',
			formatter: row => maskPhone(row.phone),
			minWidth: 120
		},
		{
			label: t('性别'),
			prop: 'gender',
			dict: options.gender,
			minWidth: 120
		},
		{
			label: t('微信绑定'),
			prop: 'wechatBound',
			dict: options.wechatBound,
			minWidth: 120
		},
		{ label: t('密码状态'), prop: 'passwordSet', dict: options.passwordSet, minWidth: 110 },
		{ label: t('账号分类'), prop: 'historyPending', dict: options.historyPending, minWidth: 120 },
		{
			label: t('状态'),
			prop: 'status',
			minWidth: 120,
			dict: options.status
		},
		{
			label: t('创建时间'),
			prop: 'createTime',
			sortable: 'desc',
			minWidth: 170
		},
		{
			type: 'op',
			width: 240,
			buttons: [{ label: t('详情'), type: 'primary', onClick: ({ scope }) => showDetail(scope.row.id) }, 'edit', 'delete']
		}
	]
});

// cl-upsert
const Upsert = useUpsert({
	items: [
		{
			prop: 'avatarUrl',
			label: t('头像'),
			component: { name: 'cl-upload' }
		},
		{
			prop: 'nickName',
			label: t('昵称'),
			component: { name: 'el-input' },
			required: true
		},
		{
			prop: 'phone',
			label: t('手机号'),
			component: {
				name: 'el-input',
				props: {
					maxlength: 11,
					disabled: true
				}
			}
		},
		{
			prop: 'gender',
			label: t('性别'),
			value: 1,
			component: {
				name: 'el-radio-group',
				options: options.gender
			}
		},
		{
			prop: 'status',
			label: t('状态'),
			value: 1,
			component: {
				name: 'el-radio-group',
				options: options.status
			}
		}
	]
});

// cl-search
const Search = useSearch({
	items: [
		{ label: t('账号分类'), prop: 'accountGroup', value: 'formal', component: { name: 'el-select', options: [
			{ label: t('正式账号'), value: 'formal' }, { label: t('历史待处理'), value: 'history' }, { label: t('全部记录'), value: 'all' }
		] } },
		{ label: t('账号状态'), prop: 'status', component: { name: 'el-select', options: options.status, props: { clearable: true, placeholder: t('全部') } } },
		{ label: t('微信绑定'), prop: 'wechatBound', component: { name: 'el-select', options: options.wechatBound, props: { clearable: true, placeholder: t('全部') } } },
		{ label: t('密码状态'), prop: 'passwordSet', component: { name: 'el-select', options: options.passwordSet, props: { clearable: true, placeholder: t('全部') } } },
		{ prop: 'keyWord', component: { name: 'el-input', props: { clearable: true, placeholder: t('用户ID、昵称或手机号') } } }
	],
	onChange(data) { Crud.value?.refresh({ ...data, page: 1 }); }
});

// cl-crud
const Crud = useCrud(
	{
		service: service.user.info
	},
	app => {
		app.refresh();
	}
);
</script>
