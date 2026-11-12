<template>
 <section class="warning-section" aria-label="平台警告通知">
  <h3>平台警告通知</h3>
  <p>确认仅表示收到通知，不代表承认违规。</p>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <el-button :loading="loading" @click="load">刷新发送回执</el-button>
  <el-button v-if="canSend && eligible" type="primary" :loading="loading" @click="prepare">编辑警告通知</el-button>
  <p v-if="!eligible">该记录没有可接收通知的真实账号，或已忽略，不能发送警告。</p>
  <el-empty v-if="!loading && !error && !receipts.length" description="尚未发送警告通知" :image-size="48" />
  <article v-for="item in receipts" :key="item.id">
   <strong>{{ item.title }}</strong><p>{{ item.content }}</p>
   <small>{{ item.noticeType==='STRONG'?'强提醒':'轻通知' }} · {{ item.createTime }} · {{ item.confirmedAt?'已确认收到':item.readAt?'已读':'未读' }}</small>
  </article>
  <el-dialog v-model="open" title="预览并发送平台警告" append-to-body width="min(600px, 94vw)">
   <el-alert :title="`接收账号：${form.subjectType==='APP'?'普通用户':'商家账号'} ${form.subjectId}`" :closable="false" />
   <el-form label-position="top">
    <el-form-item label="标题"><el-input v-model="form.title" maxlength="100" show-word-limit /></el-form-item>
    <el-form-item label="正文（接收人将看到以下内容）"><el-input v-model="form.content" type="textarea" :rows="7" maxlength="5000" show-word-limit /></el-form-item>
    <el-form-item label="提醒方式"><el-radio-group v-model="form.noticeType"><el-radio-button value="LIGHT">轻通知</el-radio-button><el-radio-button value="STRONG">强提醒</el-radio-button></el-radio-group></el-form-item>
   </el-form>
   <el-alert v-if="sendError" :title="sendError" type="error" :closable="false" />
   <template #footer><el-button @click="open=false">取消</el-button><el-button type="primary" :loading="sending" :disabled="!form.title.trim() || !form.content.trim()" @click="send">确认发送</el-button></template>
  </el-dialog>
 </section>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { checkPerm } from '/@/modules/base/utils/permission';
import { riskApi } from '../api/content-risk';
const props=defineProps<{risk:{id:number;subjectType?:string;subjectId?:number;status:string}}>();
interface Warning {id:number;title:string;content:string;noticeType:string;createTime:string;readAt?:string;confirmedAt?:string}
const receipts=ref<Warning[]>([]),loading=ref(false),sending=ref(false),open=ref(false),error=ref(''),sendError=ref('');
const form=ref({subjectType:'',subjectId:0,title:'',content:'',noticeType:'LIGHT'});
let requestId='',sequence=0,previewSequence=0,previewRiskId=0;
const canSend=computed(()=>checkPerm('search:risk:manage')&&checkPerm('notice:send'));
const eligible=computed(()=>!!props.risk.subjectId&&['APP','MERCHANT'].includes(props.risk.subjectType||'')&&!['IGNORED','FALSE_POSITIVE'].includes(props.risk.status));
async function load(){const seq=++sequence;loading.value=true;error.value='';try{const result=await riskApi<Warning[]>('warnings/receipts',undefined,{riskId:props.risk.id});if(seq===sequence)receipts.value=Array.isArray(result)?result:[];}catch{if(seq===sequence)error.value='发送回执加载失败，请重试';}finally{if(seq===sequence)loading.value=false;}}
async function prepare(){const seq=++previewSequence,id=props.risk.id;loading.value=true;error.value='';try{const result=await riskApi<typeof form.value>('warnings/preview',undefined,{riskId:id});if(seq!==previewSequence||id!==props.risk.id)return;form.value=result;previewRiskId=id;requestId=crypto.randomUUID();sendError.value='';open.value=true;}catch{if(seq===previewSequence)error.value='无法获取警告预览，请检查权限或稍后重试';}finally{if(seq===previewSequence)loading.value=false;}}
async function send(){if(!canSend.value||!eligible.value||sending.value||previewRiskId!==props.risk.id)return;const id=previewRiskId;sending.value=true;sendError.value='';try{await riskApi('warnings/send',{riskId:id,requestId,title:form.value.title,content:form.value.content,noticeType:form.value.noticeType});if(id!==props.risk.id)return;open.value=false;ElMessage.success('警告通知已发送');await load();}catch{if(id===props.risk.id)sendError.value='发送未成功，请重试；请勿重复新建通知';}finally{sending.value=false;}}
watch(()=>props.risk.id,()=>{previewSequence++;previewRiskId=0;open.value=false;sendError.value='';receipts.value=[];load();},{immediate:true});
</script>
<style scoped>
.warning-section{margin-top:24px;border-top:1px solid var(--el-border-color-light);padding-top:16px}.warning-section p{line-height:1.6;white-space:pre-wrap;overflow-wrap:anywhere}.warning-section article{padding:16px 0;border-bottom:1px solid var(--el-border-color-light)}small{color:var(--el-text-color-secondary)}.el-alert{margin-bottom:12px}
</style>
