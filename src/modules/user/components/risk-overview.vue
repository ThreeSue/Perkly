<template>
 <section v-if="allowed" class="risk-overview workbench-panel" aria-label="内容风险概览">
  <header><div><h2>内容风险</h2><p>命中记录交由人工复核，不自动处罚账号</p></div><div><el-button :loading="loading" @click="load">刷新风险数据</el-button><router-link to="/user/search-risk">进入复核 →</router-link></div></header>
  <el-alert v-if="error" :title="error" type="warning" :closable="false" />
  <div class="metrics"><div v-for="(label,key) in labels" :key="key"><span>{{ label }}</span><strong>{{ counts[key] ?? '—' }}</strong></div></div>
  <div class="task"><div><strong><i v-if="state==='RUNNING' && task?.phase==='AI_RUNNING'" class="running"/>{{ state==='RUNNING' ? (task?.phase==='AI_RUNNING' ? 'AI 正在检测' : task?.phase==='RULE_SCAN' ? '正在进行词库检查' : '正在检查任务状态') : stateLabels[state] || '任务状态未知' }}</strong><p v-if="task">{{ riskScenes[task.source] || '已有风险记录' }} · 已检查 {{ task.checkedCount }} 条 · 模型调用 {{ task.modelCalls }} 次</p><p v-else>{{ state==='UNKNOWN' ? '任务状态加载失败，请刷新重试' : '尚未启动扫描任务' }}</p><p v-if="task?.error" role="alert">任务执行失败，请检查服务后重试。</p></div><el-button v-if="manage && state==='FAILED' && task" :loading="starting" @click="retry">继续失败任务</el-button><el-button v-if="manage" :disabled="['RUNNING','QUEUED'].includes(state)" @click="dialog=true">新建检查任务</el-button></div>
  <el-dialog v-model="dialog" title="新建有限范围检查" width="min(520px, 94vw)" append-to-body>
   <el-form label-position="top"><el-form-item label="检查来源"><el-select v-model="form.source"><el-option v-for="source in sources" :key="source" :label="riskScenes[source] || '已有风险记录'" :value="source"/></el-select></el-form-item><el-form-item label="最多检查条数"><el-input-number v-model="form.limitCount" :min="1" :max="100"/></el-form-item><el-form-item label="模型调用上限（0 表示仅词库扫描）"><el-input-number v-model="form.modelBudget" :min="0" :max="Math.min(20,form.limitCount)"/></el-form-item></el-form>
   <el-alert title="AI 仅提供建议，不自动封禁账号或删除内容。" :closable="false"/>
   <el-alert v-if="actionError" :title="actionError" type="error" :closable="false"/>
   <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="starting" @click="start">开始检查</el-button></template>
  </el-dialog>
 </section>
</template>
<script setup lang="ts">
import {computed,ref,onMounted,onUnmounted,onActivated,onDeactivated} from 'vue';
import {checkPerm} from '/@/modules/base/utils/permission';
import {isPlatform} from '/@/workspace';
import {riskApi,riskScenes} from '../api/content-risk';
interface Task{id:number;source:string;checkedCount:number;modelCalls:number;error?:string;phase?:string}
const allowed=computed(()=>isPlatform&&checkPerm('search:risk:view')),manage=computed(()=>allowed.value&&checkPerm('search:risk:manage'));
const labels={pendingRecords:'待复核记录',pendingAccounts:'待复核账号 · 去重',todayBlocked:'今日拦截请求',bannedAccounts:'当前停用账号'};
const stateLabels:Record<string,string>={IDLE:'暂无检查任务',QUEUED:'任务等待执行',RUNNING:'正在检查',COMPLETED:'检查完成',FAILED:'检查失败'};
const counts=ref<Record<string,number>>({}),state=ref('IDLE'),task=ref<Task>(),loading=ref(false),error=ref(''),starting=ref(false),dialog=ref(false),actionError=ref('');
const form=ref({source:'RISKS',afterId:0,limitCount:50,modelBudget:0});
const sources=['PROFILE','FEEDBACK','CHAT','MERCHANT','STORE','COUPON','NOTICE','RISKS'];
let timer:ReturnType<typeof setTimeout>|undefined,seq=0,active=true;
function stop(){active=false;seq++;clearTimeout(timer);loading.value=false;}
async function load(){if(!allowed.value||!active)return;clearTimeout(timer);const current=++seq;loading.value=true;error.value='';const results=await Promise.allSettled([riskApi<Record<string,number>>('dashboard'),riskApi<{state:string;task?:Task}>('jobs/status')]);if(current!==seq)return;const [stats,jobs]=results;counts.value=stats.status==='fulfilled'?stats.value:{};if(jobs.status==='fulfilled'){state.value=jobs.value.state;task.value=jobs.value.task;}else{state.value='UNKNOWN';task.value=undefined;}if(results.some(r=>r.status==='rejected'))error.value='部分风险数据暂时无法加载，请刷新重试';loading.value=false;if(active&&['RUNNING','QUEUED'].includes(state.value))timer=setTimeout(load,5000);}
async function start(){if(!manage.value||starting.value)return;starting.value=true;actionError.value='';try{await riskApi('jobs/start',{...form.value,modelBudget:Math.min(form.value.modelBudget,form.value.limitCount)});dialog.value=false;await load();}catch{actionError.value='任务未创建，请检查权限和服务后重试';}finally{starting.value=false;}}
async function retry(){if(!manage.value||!task.value||starting.value)return;starting.value=true;try{await riskApi('jobs/retry',{id:task.value.id});await load();}catch{error.value='任务重试失败，请稍后重试';}finally{starting.value=false;}}
onMounted(load);onActivated(()=>{active=true;load();});onDeactivated(stop);onUnmounted(stop);
</script>
<style scoped>
.risk-overview{padding:20px 24px}header{display:flex;align-items:center;justify-content:space-between;gap:16px}h2{font-size:18px;margin:0}p{font-size:13px;color:var(--el-text-color-secondary);margin:6px 0}header a{color:var(--el-color-primary);margin-left:16px;font-size:14px}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;padding:20px 0}.metrics>div{display:grid;gap:8px}.metrics span{font-size:13px;color:var(--el-text-color-secondary)}.metrics strong{font-size:26px;color:var(--workspace-accent)}.task{display:flex;align-items:center;gap:12px;border-top:1px solid var(--el-border-color-lighter);padding-top:14px}.task>div{flex:1}.running{display:inline-block;width:8px;height:8px;border-radius:50%;background:var(--el-color-primary);margin-right:8px;animation:pulse 1.5s ease-in-out infinite}@keyframes pulse{50%{opacity:.3}}@media(prefers-reduced-motion:reduce){.running{animation:none}}@media(max-width:700px){header,.task{flex-wrap:wrap}.metrics{grid-template-columns:repeat(2,1fr)}}
</style>
