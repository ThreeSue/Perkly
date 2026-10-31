<template>
    <div class="view-my">
        <el-scrollbar><div class="p-[20px]">
            <div class="title">账号安全 · 修改密码</div>
            <el-alert title="忘记密码请联系平台运营人员重置，重置后的密码为123456。" type="info" :closable="false" show-icon class="mb-[20px]" />
            <el-form ref="Form" :model="form" :rules="rules" label-position="top" :disabled="loading" @submit.prevent="save">
                <el-form-item label="登录账号"><el-input :model-value="user.info?.username" disabled /></el-form-item>
                <el-form-item label="原密码" prop="oldPassword"><el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" /></el-form-item>
                <el-form-item label="新密码" prop="newPassword"><el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" placeholder="请输入6至64位新密码" maxlength="64" /></el-form-item>
                <el-form-item label="确认新密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
                <el-form-item><el-button type="primary" :loading="loading" native-type="submit">保存新密码</el-button></el-form-item>
            </el-form>
        </div></el-scrollbar>
    </div>
</template>
<script lang="ts" setup>
import { reactive, ref } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { useBase } from '/$/base';
import { merchantRequest } from '/@/merchant-api';
defineOptions({ name: 'my-info' });
const { user } = useBase();
const Form = ref<FormInstance>();
const loading = ref(false);
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });
const rules: FormRules = {
    oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
    newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 6, max: 64, message: '密码需6至64位', trigger: 'blur' }],
    confirmPassword: [{ validator: (_rule, value, callback) => callback(value && value === form.newPassword ? undefined : new Error('两次输入的新密码不一致')), trigger: 'blur' }]
};
async function save() {
    if (loading.value || !await Form.value?.validate().catch(() => false)) return;
    loading.value = true;
    try {
        await merchantRequest('auth/changePassword', { oldPassword: form.oldPassword, newPassword: form.newPassword });
        form.oldPassword = form.newPassword = form.confirmPassword = '';
        ElMessage.success('密码已修改，请使用新密码重新登录');
        await user.logout();
    } catch (error: any) { ElMessage.error(error.message || '修改失败'); }
    finally { loading.value = false; }
}
</script>
<style scoped>
.view-my { background: var(--el-bg-color); height:100%; border-radius:6px; }
.el-form { width:400px; max-width:100%; }
.title { margin-bottom:20px; font-size:15px; font-weight:bold; }
</style>
