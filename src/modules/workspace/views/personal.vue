<template>
	<PersonalWorkbench :name="me?.name" :fields="fields">
		<section v-loading="loading" class="workbench-panel personal-section" :aria-busy="loading">
			<ManagementState
				v-if="error"
				kind="error"
				title="个人资料加载失败"
				:description="error"
				@retry="load"
			/>
			<template v-else>
				<h2>个人资料</h2>
				<el-form
					label-position="top"
					:disabled="loading || Boolean(busy)"
					@submit.prevent="saveName"
				>
					<el-form-item label="姓名">
						<el-input v-model="name" maxlength="50" autocomplete="name" />
					</el-form-item>
					<el-button
						type="primary"
						native-type="submit"
						:loading="busy === 'profile'"
						:disabled="!canSaveName"
						>保存姓名</el-button
					>
				</el-form>
			</template>
		</section>
		<section class="workbench-panel personal-section">
			<h2>修改登录密码</h2>
			<p>保存新密码后需要重新登录。</p>
			<el-form label-position="top" :disabled="Boolean(busy)" @submit.prevent="savePassword">
				<el-form-item label="当前密码">
					<el-input
						v-model="oldPassword"
						type="password"
						show-password
						autocomplete="current-password"
					/>
				</el-form-item>
				<el-form-item label="新密码">
					<el-input
						v-model="newPassword"
						type="password"
						show-password
						maxlength="64"
						autocomplete="new-password"
					/>
				</el-form-item>
				<el-form-item label="再次输入新密码" :error="passwordError">
					<el-input
						v-model="repeat"
						type="password"
						show-password
						maxlength="64"
						autocomplete="new-password"
					/>
				</el-form-item>
				<el-button
					type="primary"
					native-type="submit"
					:loading="busy === 'password'"
					:disabled="!canSavePassword"
				>
					保存并重新登录
				</el-button>
			</el-form>
		</section>
	</PersonalWorkbench>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useBase } from '/$/base';
import { merchantRequest } from '/@/merchant-api';
import { workspaceApi } from '../api';
import PersonalWorkbench from '../components/personal-workbench.vue';
import ManagementState from '../components/management-state.vue';

interface AccountProfile {
	name: string;
	username: string;
	merchantName?: string;
	storeName?: string;
	roleName?: string;
}
const { user } = useBase();
const me = ref<AccountProfile>();
const name = ref('');
const oldPassword = ref('');
const newPassword = ref('');
const repeat = ref('');
const loading = ref(false);
const error = ref('');
const busy = ref<'' | 'profile' | 'password'>('');
const fields = computed(() =>
	me.value
		? [
				{ label: '登录账号', value: me.value.username },
				{ label: '商家', value: me.value.merchantName },
				{ label: '门店', value: me.value.storeName || '按授权范围查看' },
				{ label: '岗位', value: me.value.roleName }
			]
		: []
);
const canSaveName = computed(() =>
	Boolean(me.value && name.value.trim() && name.value.trim() !== me.value.name)
);
const passwordError = computed(() =>
	repeat.value && repeat.value !== newPassword.value ? '两次新密码不一致' : ''
);
const canSavePassword = computed(() =>
	Boolean(oldPassword.value && newPassword.value && repeat.value && !passwordError.value)
);

async function load() {
	loading.value = true;
	error.value = '';
	try {
		me.value = await workspaceApi('me');
		name.value = me.value?.name || '';
	} catch (err: any) {
		error.value = err?.message || '请稍后重试';
	} finally {
		loading.value = false;
	}
}
async function saveName() {
	if (busy.value || !canSaveName.value) return;
	busy.value = 'profile';
	try {
		await workspaceApi('personal', { name: name.value.trim() });
		await load();
		ElMessage.success('姓名已保存');
		void user.get().catch(() => undefined);
	} catch (err: any) {
		ElMessage.error(err?.message || '保存失败，请重试');
	} finally {
		busy.value = '';
	}
}
async function savePassword() {
	if (busy.value || !canSavePassword.value) return;
	busy.value = 'password';
	try {
		await merchantRequest('auth/changePassword', {
			oldPassword: oldPassword.value,
			newPassword: newPassword.value
		});
		ElMessage.success('密码已修改，请重新登录');
		await user.logout();
	} catch (err: any) {
		ElMessage.error(err?.message || '修改失败，请重试');
	} finally {
		busy.value = '';
	}
}
onMounted(load);
</script>
