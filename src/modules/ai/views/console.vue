<template>
 <ManagementShell class="ai-control-page" title="AI 功能管理" description="统一管理各端 AI 的使用状态与每日额度。业务权限仍按原有授权执行。">
  <div class="heading"><span>功能总览 <small>6 项能力</small></span><el-button :loading="loading" @click="load">刷新状态</el-button></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
  <p class="note">开关独立生效；额度每日按北京时间重置。关闭 AI 不会关闭原有人工操作。</p>
  <el-table :data="rows" v-loading="loading" class="feature-table">
   <el-table-column label="AI 功能" min-width="240"><template #default="{ row }"><strong>{{ row.name }}</strong><div class="secondary">{{ row.description }}</div></template></el-table-column>
   <el-table-column label="启用状态" width="145"><template #default="{ row }"><el-tag v-if="row.id === 'image' && settings" :type="settings.enabled ? 'success' : 'info'">{{ settings.enabled ? '已开启' : '已关闭' }}</el-tag><el-tag v-else-if="controls[row.id]" :type="controls[row.id].enabled ? 'success' : 'info'">{{ controls[row.id].enabled ? '已开启' : '已关闭' }}</el-tag><span v-else>读取失败</span></template></el-table-column>
   <el-table-column prop="scope" label="开放范围" min-width="180" />
   <el-table-column label="每日额度" min-width="155"><template #default="{ row }"><span v-if="row.id === 'image' && settings">{{ settings.defaultDailyLimit }} 张 / 商家</span><span v-else-if="controls[row.id]">{{ controls[row.id].dailyLimit == null ? '不限' : controls[row.id].dailyLimit + (row.id === 'support' ? ' 次 / 门店' : row.id === 'profile' ? ' 次 / 平台' : ' 次 / 账号') }}</span><span v-else>—</span></template></el-table-column>
   <el-table-column label="今日用量" width="145"><template #default="{ row }">{{ row.id === 'image' ? imageUsed : controls[row.id]?.todayUsed ?? '—' }}</template></el-table-column>
   <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button link type="primary" :disabled="loading || (row.id === 'image' ? !settings : !controls[row.id])" @click="select(row)">设置</el-button></template></el-table-column>
  </el-table>
  <p class="footnote">额度指平台内部限制。聊天按账号、客服按门店、生图按商家、画像按平台计额。受理后失败也计次；不会扩大原有业务权限。</p>
  <el-drawer v-model="visible" :title="selected?.name" size="min(480px, 100vw)">
   <template v-if="selected?.id === 'image' && settings">
    <el-form label-position="top" :disabled="saving">
     <el-form-item label="允许生成封面"><el-switch v-model="draft.enabled" active-text="开启" inactive-text="关闭" /></el-form-item>
     <el-form-item label="默认商家每日额度"><el-input-number v-model="draft.limit" :min="0" :max="10000" :precision="0" /><span class="unit">张 / 天</span></el-form-item>
     <p class="note">0 表示不允许新请求。已有商家独立额度优先；关闭将暂停新请求和排队任务。</p>
     <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
    </el-form>
    <el-divider />
    <el-button @click="advanced = true">商家独立额度与高级管理</el-button>
   </template>
   <template v-else-if="selected && controls[selected.id]">
    <el-alert v-if="controls[selected.id].runtimeNote" :title="controls[selected.id].runtimeNote" type="warning" :closable="false" />
    <el-form label-position="top" :disabled="saving">
     <el-form-item label="允许使用"><el-switch v-model="draft.enabled" active-text="开启" inactive-text="关闭" /></el-form-item>
     <el-form-item label="每日额度"><el-switch v-model="unlimited" aria-label="不限次数" active-text="不限次数" inactive-text="指定次数" /><el-input-number v-if="!unlimited" v-model="draft.limit" :min="0" :max="100000" :precision="0" /></el-form-item>
     <p class="note">0 次表示不受理新请求。保存后对新请求生效，已开始的任务不强行中断。</p>
     <el-button type="primary" :loading="saving" @click="saveFeature">保存设置</el-button>
    </el-form>
   </template>
  </el-drawer>
  <el-drawer v-model="advanced" title="AI 生图高级管理" size="min(1200px, 100vw)" destroy-on-close @closed="load"><ImageConsole /></el-drawer>
 </ManagementShell>
</template>
<script setup lang="ts">
import { ref, onMounted, onActivated } from 'vue';
import { useProcessStore } from '/$/base/store/process';
import { ElMessage } from 'element-plus';
import ManagementShell from '/$/workspace/components/management-shell.vue';
import { featureApi } from '/$/workspace/feature-api';
import ImageConsole from '../components/image-console.vue';
type Feature = { id: string; name: string; description: string; scope: string };
type Settings = { enabled: boolean; defaultDailyLimit: number; allowedModels: string[]; estimatedUnitPrice: number | null };
const rows: Feature[] = [
 {id:'user',name:'用户端助手',description:'找门店、找优惠，解释券与订单',scope:'登录用户'},
 {id:'merchant',name:'核销端助手',description:'协助门店人员查询业务数据',scope:'授权商家与门店员工'},
 {id:'support',name:'门店 AI 客服',description:'自动接待咨询，必要时转人工',scope:'按门店接待配置与授权'},
 {id:'admin',name:'管理端 AI 助手',description:'查询数据，协助完成管理操作',scope:'已授权管理账号'},
 {id:'image',name:'AI 生图',description:'生成优惠券封面，完善已有图片',scope:'已授权账号及商家策略'},
 {id:'profile',name:'用户画像分析',description:'分析消费偏好，辅助个性化推荐',scope:'平台任务'}
];
const settings = ref<Settings>(), selected = ref<Feature>();
const loading=ref(false), saving=ref(false), visible=ref(false), advanced=ref(false), error=ref('');
const controls=ref<Record<string,{enabled:boolean;dailyLimit:number|null;todayUsed:number;runtimeNote?:string}>>({});
const unlimited=ref(true), imageUsed=ref<number|string>('—');
const draft=ref({enabled:false,limit:0});
async function load() {
 loading.value=true; error.value=''; settings.value=undefined; controls.value={}; imageUsed.value='—';
 const results=await Promise.allSettled([featureApi('ai/console/settings'),featureApi('ai/console/features')]);
 if(results[0].status==='fulfilled') settings.value=results[0].value;
 if(results[1].status==='fulfilled') controls.value=Object.fromEntries(results[1].value.map((r:any)=>[r.id,r]));
 const failures=results.filter(r=>r.status==='rejected');
 if(failures.length) error.value='部分 AI 配置读取失败，请确认后端已更新并启动，再点刷新状态重试。';
 try {
  const day=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai'}).format(new Date());
  let total=0;
  for(let page=1;;page++) {
   const u=await featureApi(`ai/console/usage?startDay=${day}&endDay=${day}&pageNumber=${page}&pageSize=100`);
   total+=u.list.reduce((n:number,r:any)=>n+(r.requests||0),0);
   if(page*100>=u.total) break;
  }
  imageUsed.value=total;
 } catch { error.value ||= '生图用量暂时读取失败，可刷新重试；设置仍可使用。'; }
 finally {loading.value=false;}
}
function select(row:Feature){selected.value=row;if(settings.value)draft.value={enabled:settings.value.enabled,limit:settings.value.defaultDailyLimit};if(row.id!=='image' && controls.value[row.id]){const c=controls.value[row.id];draft.value={enabled:c.enabled,limit:c.dailyLimit??100};unlimited.value=c.dailyLimit==null;}visible.value=true;}
async function save(){if(!settings.value||saving.value)return;saving.value=true;try{await featureApi('ai/console/settings',{enabled:draft.value.enabled,defaultDailyLimit:draft.value.limit,allowedModels:settings.value.allowedModels,estimatedUnitPrice:settings.value.estimatedUnitPrice??null});ElMessage.success('生图设置已保存');visible.value=false;await load();}catch(e:any){ElMessage.error(e?.message||'保存失败，请重试');}finally{saving.value=false;}}
async function saveFeature(){if(!selected.value||saving.value)return;saving.value=true;try{await featureApi('ai/console/features',{id:selected.value.id,enabled:draft.value.enabled,dailyLimit:unlimited.value?null:draft.value.limit});ElMessage.success('设置已保存');visible.value=false;await load();}catch(e:any){ElMessage.error(e?.message||'保存失败');}finally{saving.value=false;}}
const process = useProcessStore();
onMounted(() => { process.setTitle('AI 功能管理'); load(); });
onActivated(() => process.setTitle('AI 功能管理'));
</script>
<style scoped>
.heading { display:flex; justify-content:space-between; align-items:center; margin:8px 0 20px; font-size:18px; font-weight:600; }
.heading small { font-size:13px; font-weight:400; color:var(--el-text-color-secondary); margin-left:12px; }
.note,.footnote,.secondary { color:var(--el-text-color-secondary); font-size:13px; line-height:1.7; }
.note { margin:16px 0; max-width:75ch; }
.feature-table strong { font-size:15px; color:var(--el-text-color-primary); font-weight:600; }
.feature-table :deep(.el-table__cell) { padding:12px 0; }
.secondary { margin-top:3px; }
.footnote { margin-top:20px; }
.description { margin-top:24px; line-height:1.7; }
.unit { margin-left:10px; color:var(--el-text-color-secondary); }
.ai-control-page :deep(.management-body) { display:block; }
.ai-control-page :deep(.management-heading) { padding:14px 18px; }
.feature-table { width:100%; height:auto; }
</style>
