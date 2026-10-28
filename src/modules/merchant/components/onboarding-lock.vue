<template>
    <div class="onboarding-lock" v-loading="loading">
        <el-scrollbar><div class="p-[20px]">
            <el-result :icon="state.status === 3 ? 'error' : 'info'" :title="state.status === 3 ? '入驻申请已驳回' : state.status === 2 ? '审核通过，待开通账号' : '入驻申请审核中'" sub-title="审核通过后请在小程序领取营业码，退出后在登录页开通正式商家账号；当前申请会话不能用于经营。">
                <template #extra><el-button type="primary" :loading="loading" @click="refresh">刷新审批状态</el-button><el-button v-if="state.status===3" type="warning" @click="resubmit">修改资料并重新提交</el-button><el-button @click="logout">退出登录</el-button></template>
            </el-result>
            <el-alert v-if="state.status===3" :title="'驳回原因：' + (state.reason || '请联系平台运营人员核对')" type="error" show-icon :closable="false" class="mb-[20px]" />
            <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb-[20px]" />
            <el-descriptions title="入驻申请资料（只读）" :column="1" border>
                <el-descriptions-item label="登录账号">{{state.username}}</el-descriptions-item>
                <el-descriptions-item label="商家名称">{{state.merchantName}}</el-descriptions-item>
                <el-descriptions-item label="审核状态">{{state.status===3 ? '已驳回' : state.status===2 ? '审核通过' : '待审核'}}</el-descriptions-item>
                <el-descriptions-item label="商家分类">{{state.snapshot?.categoryName}}</el-descriptions-item>
                <el-descriptions-item label="联系人">{{state.snapshot?.contactName}}</el-descriptions-item>
                <el-descriptions-item label="联系电话">{{state.snapshot?.contactPhone}}</el-descriptions-item>
                <el-descriptions-item label="经营地址">{{state.snapshot?.address || '—'}}</el-descriptions-item>
            </el-descriptions>
        </div></el-scrollbar>
        <MerchantRegister ref="Register" @submitted="refresh" />
    </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessageBox } from 'element-plus';
import { useBase } from '/$/base';
import { merchantRequest } from '/@/merchant-api';
import MerchantRegister from '../../base/pages/login/components/merchant-register.vue';
const { user } = useBase();
const state=ref<any>({...user.info});
const loading=ref(false),error=ref('');
const Register=ref<InstanceType<typeof MerchantRegister>>();
async function refresh() {
    if(loading.value) return;
    loading.value=true;error.value='';
    try {
        state.value=await merchantRequest('auth/onboarding');
    } catch(err:any) { error.value=err.message || '状态加载失败，请重试'; }
    finally { loading.value=false; }
}
async function logout() {
    try { await merchantRequest('auth/logout',{}); } finally { await user.logout(); }
}
function resubmit() {
    const data=state.value.snapshot || {};
    Register.value?.open({username:state.value.username,merchantName:state.value.merchantName,contactName:data.contactName,contactPhone:data.contactPhone,categoryId:data.categoryId,address:data.address},true);
}
onMounted(async()=>{
    await refresh();
    if((user.info as any)?.onboardingLocked) void ElMessageBox.alert(state.value.status===3 ? `入驻申请已驳回：${state.value.reason || '请联系平台运营人员'}。历史申请可修改重提。` : '申请会话不能用于经营。审核通过后，请在小程序领取营业码，回网页登录页开通正式账号。','入驻审批提示',{type:'warning',confirmButtonText:'我知道了'}).catch(()=>{});
});
</script>
<style scoped>
.onboarding-lock {height:100%;background:var(--el-bg-color);border-radius:6px;}
</style>
