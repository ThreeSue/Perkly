<template>
	<div class="personal">
		<h2>个人中心</h2>
		<el-descriptions :column="1" border
			><el-descriptions-item label="姓名">{{ me.name }}</el-descriptions-item
			><el-descriptions-item label="登录账号">{{ me.username }}</el-descriptions-item
			><el-descriptions-item label="商家">{{ me.merchantName }}</el-descriptions-item
			><el-descriptions-item label="门店">{{
				me.storeName || '按授权范围查看'
			}}</el-descriptions-item
			><el-descriptions-item label="岗位">{{
				me.roleName
			}}</el-descriptions-item></el-descriptions
		>
		<h3>个人资料</h3>
		<el-form label-position="top"
			><el-form-item label="姓名"><el-input v-model="name" maxlength="50" /></el-form-item
			><el-button :loading="busy" @click="saveName">保存姓名</el-button></el-form
		>
		<h3>修改登录密码</h3>
		<el-form label-position="top"
			><el-form-item label="当前密码"
				><el-input
					v-model="oldPassword"
					type="password"
					show-password
					autocomplete="current-password" /></el-form-item
			><el-form-item label="新密码"
				><el-input
					v-model="newPassword"
					type="password"
					show-password
					maxlength="64"
					autocomplete="new-password" /></el-form-item
			><el-form-item label="再次输入新密码"
				><el-input
					v-model="repeat"
					type="password"
					show-password
					maxlength="64"
					autocomplete="new-password" /></el-form-item
			><el-button type="primary" :loading="busy" @click="save"
				>保存并重新登录</el-button
			></el-form
		>
	</div>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { workspaceApi } from '../api';
import { merchantRequest } from '/@/merchant-api';
import { useBase } from '/$/base';
const { user } = useBase();
const name = ref('');
async function saveName() {
	busy.value = true;
	try {
		await workspaceApi('personal', { name: name.value });
		await user.get();
		me.value = await workspaceApi('me');
		ElMessage.success('姓名已保存');
	} catch (e: any) {
		ElMessage.error(e.message);
	} finally {
		busy.value = false;
	}
}
const me = ref<any>({}),
	oldPassword = ref(''),
	newPassword = ref(''),
	repeat = ref(''),
	busy = ref(false);
onMounted(async () => {
	try {
		me.value = await workspaceApi('me');
		name.value = me.value.name;
	} catch (e: any) {
		ElMessage.error(e.message);
	}
});
async function save() {
	if (newPassword.value !== repeat.value) return ElMessage.error('两次新密码不一致');
	busy.value = true;
	try {
		await merchantRequest('auth/changePassword', {
			oldPassword: oldPassword.value,
			newPassword: newPassword.value
		});
		ElMessage.success('密码已修改，请重新登录');
		await user.logout();
	} catch (e: any) {
		ElMessage.error(e.message);
	} finally {
		busy.value = false;
	}
}
</script>
<style scoped>
.personal {
	padding: 24px;
	max-width: 760px;
	box-sizing: border-box;
	height: 100%;
	overflow: auto;
}
h3 {
	margin: 28px 0;
}
</style>
