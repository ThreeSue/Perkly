<template>
    <cl-dialog v-model="visible" :title="resubmit ? '重新提交入驻申请' : '商家入驻注册'" width="680px" height="65vh" :close-on-click-modal="false">
        <el-alert title="此处仅兼容历史账号密码申请的驳回重提。新申请请在小程序提交；通过后凭营业码开通账号。" type="info" show-icon :closable="false" class="mb-[20px]" />
        <el-alert v-if="resubmit" title="请填写原申请账号和密码，修改资料后重新提交。" type="warning" :closable="false" class="mb-[20px]" />
        <el-form ref="Form" :model="form" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="submit">
            <el-row :gutter="20">
                <el-col v-for="field in fields" :key="field.prop" :xs="24" :sm="12">
                    <el-form-item :label="field.label" :prop="field.prop"><el-input v-model="form[field.prop]" :maxlength="field.max" :type="field.password ? 'password' : 'text'" :show-password="field.password" :placeholder="field.placeholder" :autocomplete="field.password ? 'new-password' : 'off'" /></el-form-item>
                </el-col>
                <el-col :span="24"><el-form-item label="商家分类" prop="categoryId"><el-cascader v-model="form.categoryId" :options="categories" :props="{ emitPath: false, checkStrictly: true }" filterable clearable placeholder="请按层级选择商家分类" :disabled="optionsLoading" style="width:100%" /></el-form-item></el-col>
                <el-col :span="24"><el-form-item label="详细地址" prop="address"><el-input v-model="form.address" maxlength="500" placeholder="请输入商家经营地址" /></el-form-item></el-col>
                <el-col :span="24"><el-form-item label="图片验证码" prop="verifyCode"><div class="captcha-row"><el-input v-model="form.verifyCode" maxlength="4" placeholder="请输入验证码" /><PicCaptcha v-if="visible" ref="Captcha" v-model="form.captchaId" @change="form.verifyCode = ''" /></div></el-form-item></el-col>
            </el-row>
        </el-form>
        <template #footer><el-button :disabled="saving" @click="visible=false">{{resubmit ? '取消' : '返回登录'}}</el-button><el-button type="primary" :loading="saving" :disabled="optionsLoading || !categories.length" @click="submit">提交入驻申请</el-button></template>
    </cl-dialog>
</template>
<script lang="ts" setup>
import { reactive, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { merchantRequest } from '/@/merchant-api';
import PicCaptcha from './pic-captcha.vue';
const emit = defineEmits<{ submitted: [username: string] }>();
const resubmit = ref(false);
const visible = ref(false), saving = ref(false), optionsLoading = ref(false);
const Form = ref<FormInstance>();
const Captcha = ref<InstanceType<typeof PicCaptcha>>();
interface CategoryOption { value: number; label: string; children?: CategoryOption[] }
const categories = ref<CategoryOption[]>([]);
const empty = () => ({ username:'', password:'', confirmPassword:'', merchantName:'', contactName:'', contactPhone:'', categoryId:undefined as number | undefined, address:'', captchaId:'', verifyCode:'' });
const form = reactive(empty());
const fields: {prop:'username'|'password'|'confirmPassword'|'merchantName'|'contactName'|'contactPhone';label:string;max:number;password?:boolean;placeholder?:string}[] = [
    { prop:'merchantName', label:'商家名称', max:100 }, { prop:'contactName', label:'联系人姓名', max:50 },
    { prop:'contactPhone', label:'联系电话', max:30 }, { prop:'username', label:'登录账号', max:40, placeholder:'4至40位字母、数字或下划线，可用拼音' },
    { prop:'password', label:'登录密码', max:64, password:true, placeholder:'6至64位密码' }, { prop:'confirmPassword', label:'确认密码', max:64, password:true }
];
const rules: FormRules = {
    ...Object.fromEntries(['merchantName','contactName','contactPhone','categoryId','address','verifyCode'].map(key => [key,[{required:true, message:'请填写或选择此项',trigger:'blur'}]])),
    contactPhone:[{required:true,message:'请输入联系电话',trigger:'blur'},{validator:(_rule,value,callback) => {
        const phone=String(value || ''); const digits=phone.replace(/\D/g,'').length;
        callback(/^[+0-9][0-9 ()\-]{5,29}$/.test(phone) && digits>=7 && digits<=20 ? undefined : new Error('请输入含7至20位数字的有效手机号或固定电话'));
    },trigger:'blur'}],
    username:[{required:true,message:'请输入登录账号',trigger:'blur'},{pattern:/^[A-Za-z0-9_]{4,40}$/,message:'账号需4至40位字母、数字或下划线',trigger:'blur'}],
    password:[{required:true,message:'请输入密码',trigger:'blur'},{min:6,max:64,message:'密码需6至64位',trigger:'blur'}],
    confirmPassword:[{validator:(_rule,value,callback) => callback(value && value === form.password ? undefined : new Error('两次输入的密码不一致')),trigger:'blur'}]
};
async function open(data?: Record<string, any>, isResubmit = false) {
    if (saving.value) return;
    if (!isResubmit || !data?.username) { ElMessage.info('新申请请前往客户端或核销端小程序提交'); return; }
    Object.assign(form, empty(), data || {}); resubmit.value = isResubmit; visible.value=true; optionsLoading.value=true; categories.value=[];
    try {
        const rows: {id:number;name:string;parentId?:number}[] = await merchantRequest('auth/registrationCategories');
        const nodes = new Map<number, CategoryOption>(rows.map(row => [row.id, { value: row.id, label: row.name }]));
        const roots: CategoryOption[] = [];
        for (const row of rows) {
            const node = nodes.get(row.id)!;
            const parent = nodes.get(row.parentId!);
            if (parent && parent !== node) (parent.children ??= []).push(node);
            else roots.push(node);
        }
        categories.value = roots;
        if (form.categoryId != null && !nodes.has(form.categoryId)) form.categoryId = undefined;
    } catch(error:any) { ElMessage.error(error.message || '商家分类加载失败，请关闭后重试'); }
    finally { optionsLoading.value=false; }
}
async function submit() {
    if (saving.value || optionsLoading.value || !await Form.value?.validate().catch(()=>false)) return;
    saving.value=true;
    try {
        const {confirmPassword,...input}=form;
        await merchantRequest('auth/resubmit',input);
        const username=form.username; visible.value=false; Object.assign(form,empty()); emit('submitted',username);
        await ElMessageBox.alert('历史申请已重新提交。审核通过后请在小程序验证联系人手机号，领取营业码，再回网页开通正式商家账号。','提交成功',{type:'success'});
    } catch(error:any) { ElMessage.error(error.message || '申请提交失败'); void Captcha.value?.refresh(); }
    finally { saving.value=false; }
}
defineExpose({open});
</script>
<style scoped>
.captcha-row {display:flex;align-items:center;gap:12px;width:100%;}
.captcha-row .el-input {flex:1;min-width:0;}
.captcha-row :deep(.pic-captcha) {flex:none;}
</style>
