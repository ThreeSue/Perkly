<template>
	<div class="profile" v-loading="loading">
		<header>
			<div>
				<h1>商家资料</h1>
				<p>查看当前商家的基本资料与联系信息</p>
			</div>
			<el-button @click="load">刷新</el-button>
		</header>
		<el-alert v-if="failure" :title="failure" type="error" :closable="false" show-icon />
		<el-card style="margin-bottom: 20px" shadow="never"
			><template #header>默认门店与营业执照号</template
			><el-form label-position="top"
				><el-form-item label="默认门店（可不设置）"
					><el-select
						v-model="settings.defaultStoreId"
						clearable
						filterable
						:disabled="!editable"
						style="width: 100%"
						><el-option
							v-for="s in stores"
							:key="s.id"
							:value="s.id"
							:label="s.name"
							:disabled="s.status !== 1" /></el-select></el-form-item
				><el-form-item label="营业执照号（首次填写后永久锁定）"
					><el-input
						v-model="settings.licenseNumber"
						maxlength="18"
						:disabled="!editable || locked" /></el-form-item
				><el-button v-if="editable" type="primary" :loading="loading" @click="saveSettings"
					>保存设置</el-button
				></el-form
			></el-card
		>
		<el-card v-if="!failure" shadow="never"
			><el-descriptions :column="1" border
				><el-descriptions-item label="商家名称">{{
					profile.name || '—'
				}}</el-descriptions-item
				><el-descriptions-item label="品牌 Logo"
					><el-image
						v-if="profile.logo"
						:src="profile.logo"
						fit="contain"
						style="width: 80px; height: 80px"
					/><span v-else>未设置</span></el-descriptions-item
				><el-descriptions-item label="商家简介">{{
					profile.introduction || '未填写'
				}}</el-descriptions-item
				><el-descriptions-item label="联系人">{{
					profile.contactName || '未填写'
				}}</el-descriptions-item
				><el-descriptions-item label="联系电话">{{
					profile.contactPhone || '未填写'
				}}</el-descriptions-item
				><el-descriptions-item label="联系地址">{{
					profile.address || '未填写'
				}}</el-descriptions-item
				><el-descriptions-item label="商家状态"
					><el-tag :type="profile.status === 1 ? 'success' : 'info'">{{
						profile.status === 1 ? '启用' : '停用'
					}}</el-tag></el-descriptions-item
				></el-descriptions
			>
			<p class="hint">商家主体资料变更请联系平台运营。</p></el-card
		>
	</div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { merchantRequest } from '/@/merchant-api';
import { workspaceApi } from '/$/workspace/api';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useBase } from '/$/base';
import { computed } from 'vue';
const { menu } = useBase();
const editable = computed(() => menu.perms.includes('profile:edit'));
const settings = ref<any>({}),
	stores = ref<any[]>([]),
	locked = ref(false);
async function saveSettings() {
	try {
		if (!locked.value && settings.value.licenseNumber)
			await ElMessageBox.confirm(
				'营业执照号首次保存后不能修改，请核对无误后继续。',
				'确认执照号'
			);
		loading.value = true;
		await workspaceApi('settings', {
			defaultStoreId: settings.value.defaultStoreId || null,
			licenseNumber: settings.value.licenseNumber || null
		});
		locked.value = !!settings.value.licenseNumber;
		ElMessage.success('设置已保存');
	} catch (e: any) {
		if (e?.message) ElMessage.error(e.message);
	} finally {
		loading.value = false;
	}
}
defineOptions({ name: 'merchant-profile' });
const profile = ref<any>({}),
	loading = ref(false),
	failure = ref('');
async function load() {
	loading.value = true;
	failure.value = '';
	try {
		const [p, s, list] = await Promise.all([
			merchantRequest('workspace/profile'),
			workspaceApi('settings'),
			workspaceApi('stores')
		]);
		profile.value = p;
		settings.value = s;
		stores.value = list;
		locked.value = !!s.licenseNumber;
	} catch (error: any) {
		failure.value = error.message || '加载失败，请重试';
	} finally {
		loading.value = false;
	}
}
onMounted(load);
</script>
<style scoped>
.profile {
	padding: 24px;
	box-sizing: border-box;
	height: 100%;
	overflow: auto;
}
header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	margin-bottom: 24px;
}
h1 {
	margin: 0 0 12px;
	font-size: 24px;
}
p {
	color: var(--el-text-color-secondary);
}
.hint {
	margin-top: 20px;
}
</style>
