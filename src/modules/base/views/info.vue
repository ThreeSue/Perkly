<template>
	<PersonalWorkbench :name="user.info?.nickName" :fields="[]">
		<el-form label-position="top" :model="form" :disabled="loading" @submit.prevent="save">
			<section class="workbench-panel personal-section">
				<h2>{{ $t('基本信息') }}</h2>
				<el-form-item :label="$t('头像')">
					<cl-upload v-model="form.headImg" />
				</el-form-item>
				<el-form-item :label="$t('昵称')">
					<el-input v-model="form.nickName" :placeholder="$t('请填写昵称')" autocomplete="nickname" clearable />
				</el-form-item>
			</section>
			<section class="workbench-panel personal-section platform-password">
				<h2>{{ $t('修改密码') }}</h2>
				<p>只修改头像或昵称时，密码字段可留空。</p>
				<el-form-item :label="$t('原密码')">
					<el-input v-model="form.oldPassword" type="password" autocomplete="current-password" show-password />
				</el-form-item>
				<el-form-item :label="$t('新密码')">
					<el-input v-model="form.password" type="password" autocomplete="new-password" show-password />
				</el-form-item>
				<el-button type="primary" native-type="submit" :loading="loading">{{ $t('保存修改') }}</el-button>
			</section>
		</el-form>
	</PersonalWorkbench>
</template>
<script lang="ts" setup>
defineOptions({
	name: 'my-info'
});

import { ElMessage } from 'element-plus';
import { onMounted, reactive, ref } from 'vue';
import { useBase } from '/$/base';
import { useCool } from '/@/cool';
import PersonalWorkbench from '/@/modules/workspace/components/personal-workbench.vue';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
const { service } = useCool();
const { user } = useBase();

// 表单数据
const form = reactive({
	headImg: '',
	nickName: '',
	password: '',
	oldPassword: ''
});

// 保存状态
const loading = ref(false);

// 保存
async function save() {
	loading.value = true;

	await service.base.comm
		.personUpdate(form)
		.then(() => {
			form.password = '';
			form.oldPassword = '';

			ElMessage.success(t('修改成功'));
			user.get();
		})
		.catch(err => {
			ElMessage.error(err.message);
		});

	loading.value = false;
}

onMounted(() => {
	form.headImg = user.info?.headImg || '';
	form.nickName = user.info?.nickName || '';
});
</script>
<style scoped>
.platform-password { margin-block-start: 16px; }
</style>
