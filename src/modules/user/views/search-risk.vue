<template>
<CatalogWorkbench title="内容风险管控" description="复核内容命中记录、配置检测规则与发送平台提醒。命中记录不等于账号违规。" class="risk-workbench">
 <el-result v-if="!allowed" icon="warning" title="无权查看内容风险记录" />
 <template v-else>
 <div class="risk-body">
 <div class="risk-summary"><div><span>待复核账号 · 已去重</span><strong>{{ stats.pendingAccounts ?? '—' }}</strong></div><div><span>待复核记录</span><strong>{{ stats.pendingRecords ?? '—' }}</strong></div><div><span>已确认账号 · 已去重</span><strong>{{ stats.confirmedAccounts ?? '—' }}</strong></div></div>
 <el-tabs v-model="tab" @tab-change="page=1;load()"><el-tab-pane label="待复核" name="pending"/><el-tab-pane label="全部记录" name="records"/><el-tab-pane label="词库与规则" name="words"/></el-tabs>
 <el-button @click="auditDialog=true">查看全局审计</el-button>
 <div class="risk-toolbar">
 <template v-if="tab!=='words'"><el-input v-model="userId" placeholder="账号 ID" clearable aria-label="账号 ID"/><el-select v-if="tab!=='pending'" v-model="status" placeholder="全部状态" clearable aria-label="处理状态"><el-option v-for="(label,key) in statuses" :key="key" :label="label" :value="key"/></el-select><el-select v-model="scene" placeholder="全部来源" clearable aria-label="内容来源"><el-option v-for="(label,key) in riskScenes" :key="key" :label="label" :value="key"/></el-select><el-select v-model="subjectType" placeholder="全部主体" clearable aria-label="账号类型"><el-option v-for="(label,key) in subjects" :key="key" :label="label" :value="key"/></el-select><el-input v-model="source" placeholder="来源入口路径" clearable aria-label="来源入口路径"/><el-date-picker v-model="dates" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss" start-placeholder="开始时间" end-placeholder="结束时间（不含）" aria-label="记录时间范围"/><el-button type="primary" @click="page=1;load()">查询</el-button><el-button @click="userId='';status='';scene='';source='';subjectType='';dates=[];page=1;load()">重置</el-button></template>
 <el-button v-else-if="manage" type="primary" @click="editWord()">新增词条</el-button><el-button @click="load">刷新</el-button>
 </div>
 <RiskControls v-if="tab==='words'" :manage="manage"/>
 <el-alert v-if="statsError" :title="statsError" type="warning" :closable="false" show-icon />
 <el-alert v-if="error" title="风险数据加载失败" type="error" :closable="false" show-icon><span>{{ error }}</span><el-button :loading="loading" @click="load">重新加载</el-button></el-alert>
 <el-table v-loading="loading" :data="tab!=='words'?records:words" :empty-text="error ? '数据未加载，请重试' : '暂无匹配记录'" row-key="id">
 <template v-if="tab!=='words'">
 <el-table-column label="命中内容" min-width="260"><template #default="{row}"><span class="risk-content"><template v-for="(part,i) in highlight(row)" :key="i"><mark v-if="part.hit">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></span></template></el-table-column>
 <el-table-column label="主体类型" width="120"><template #default="{row}">{{ subjectTypeLabel(row) }}</template></el-table-column><el-table-column label="用户名称" min-width="150" show-overflow-tooltip><template #default="{row}">{{ subjectLabel(row) }}</template></el-table-column><el-table-column label="内容来源" min-width="150"><template #default="{row}">{{ riskScenes[row.scene] || '历史搜索记录' }}<small class="source-path">{{ row.source }}</small></template></el-table-column><el-table-column prop="createTime" label="时间" width="180"/><el-table-column label="状态" width="110"><template #default="{row}">{{ statuses[row.status] || row.status }}</template></el-table-column><el-table-column label="操作" width="100" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openDetail(row)">查看详情</el-button></template></el-table-column>
 </template>
 <template v-else><el-table-column prop="word" label="词条" min-width="240"/><el-table-column label="规则"><template #default="{row}">{{ modes[row.mode] }}</template></el-table-column><el-table-column label="状态"><template #default="{row}">{{ row.enabled?'启用':'停用' }}</template></el-table-column><el-table-column label="操作" width="100"><template #default="{row}"><el-button v-if="manage" link type="primary" @click="editWord(row)">编辑</el-button></template></el-table-column></template>
 </el-table>
 <RiskSchedule v-if="tab==='words'" :manage="manage"/>
 <el-pagination v-if="tab!=='words' && !error" v-model:current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="load"/>
 </div>
 </template>
 <el-drawer v-model="auditDialog" title="全局风险操作审计" size="min(900px, 100vw)" destroy-on-close><RiskAudit v-if="auditDialog"/></el-drawer>
 <el-drawer v-model="detail" title="风险记录与处置" size="min(620px, 100vw)">
 <el-alert v-if="detailError" :title="detailError" type="error" :closable="false"><el-button @click="selected && openDetail(selected)">重试</el-button></el-alert>
 <div v-loading="detailLoading"><template v-if="selected && !detailError && !detailLoading"><el-alert v-if="selected.scene==='AI_OUTPUT'" title="这是 AI 系统输出，不归责于用户，不可据此处罚账号。" type="info" :closable="false"/><p class="risk-content"><template v-for="(part,i) in highlight(selected)" :key="i"><mark v-if="part.hit">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></p><el-descriptions :column="1" border><el-descriptions-item label="主体类型">{{ subjectTypeLabel(selected) }}</el-descriptions-item><el-descriptions-item label="用户名称">{{ subjectLabel(selected) }}</el-descriptions-item><el-descriptions-item label="入口">{{ selected.source }}</el-descriptions-item><el-descriptions-item label="状态">{{ statuses[selected.status] }}</el-descriptions-item><el-descriptions-item label="处理人">{{ selected.operatorName || '—' }}</el-descriptions-item><el-descriptions-item label="处理时间">{{ selected.handledAt || '—' }}</el-descriptions-item><el-descriptions-item label="处理原因">{{ selected.reason || '—' }}</el-descriptions-item></el-descriptions>
 <template v-if="manage && selected.status==='PENDING'"><el-input v-model="reason" type="textarea" maxlength="200" show-word-limit placeholder="填写复核依据或误报原因（必填）" class="risk-reason"/><el-button :loading="saving" @click="resolve('IGNORED')">本次忽略</el-button><el-button :loading="saving" @click="resolve('FALSE_POSITIVE')">标记误报</el-button><el-button type="primary" :loading="saving" @click="resolve('CONFIRMED')">确认需关注</el-button></template>
 <el-descriptions :column="1" border class="risk-context"><el-descriptions-item label="来源">{{ riskScenes[selected.scene || 'SEARCH'] }}</el-descriptions-item><el-descriptions-item label="关联业务">{{ selected.businessType || '无关联业务' }} {{ selected.businessId || '' }}</el-descriptions-item><el-descriptions-item label="AI 辅助建议">{{ selected.aiAdvice || '暂无建议，以人工复核为准' }}</el-descriptions-item></el-descriptions><RiskWarning :key="selected.id" :risk="selected"/>
 </template></div></el-drawer>
 <el-dialog v-model="wordDialog" title="词条设置" width="min(480px, 94vw)"><el-form label-position="top"><el-form-item label="词条"><el-input v-model="wordForm.word" maxlength="100" show-word-limit/></el-form-item><el-form-item label="检测规则"><el-select v-model="wordForm.mode"><el-option v-for="(label,key) in modes" :key="key" :label="label" :value="key"/></el-select></el-form-item><el-form-item label="启用"><el-switch v-model="wordForm.enabled"/></el-form-item></el-form><template #footer><el-button @click="wordDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveWord">保存</el-button></template></el-dialog>
</CatalogWorkbench>
</template>
<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount} from 'vue';
import {ElMessage} from 'element-plus';
import CatalogWorkbench from '/@/modules/workspace/components/catalog-workbench.vue';
import {checkPerm} from '/@/modules/base/utils/permission';
import {isPlatform} from '/@/workspace';
import {riskApi as api,riskScenes} from '../api/content-risk';
import RiskWarning from '../components/risk-warning.vue';
import RiskAudit from '../components/risk-audit.vue';
import RiskControls from '../components/risk-controls.vue';
import RiskSchedule from '../components/risk-schedule.vue';
interface Risk {scene?:string;subjectType?:string;subjectId?:number;subjectName?:string;businessType?:string;businessId?:number;aiAdvice?:string;id:number;content:string;matchedWords:string;matchedDetails?:string;userId?:number;source:string;status:string;createTime?:string;operatorId?:number;operatorName?:string;handledAt?:string;reason?:string}
interface Word {id?:number;word:string;mode:string;enabled:boolean}
const allowed=computed(()=>isPlatform&&checkPerm('search:risk:view'));
const manage=computed(()=>allowed.value&&checkPerm('search:risk:manage'));
const statuses:Record<string,string>={PENDING:'待复核',IGNORED:'已忽略',CONFIRMED:'已确认需关注',FALSE_POSITIVE:'已标记误报'};
const modes:Record<string,string>={BLOCK:'拦截',REVIEW:'人工复核',ALLOW:'白名单'};
const tab=ref('pending'),records=ref<Risk[]>([]),words=ref<Word[]>([]),stats=ref<Record<string,number>>({}),page=ref(1),total=ref(0),status=ref(''),userId=ref(''),error=ref(''),loading=ref(false),saving=ref(false),detail=ref(false),selected=ref<Risk>(),reason=ref(''),wordDialog=ref(false),wordForm=ref<Word>({word:'',mode:'REVIEW',enabled:true});
const auditDialog=ref(false),source=ref('');
const statsError=ref(''),scene=ref(''),subjectType=ref(''),dates=ref<string[]>([]),detailError=ref(''),detailLoading=ref(false);
const subjects:Record<string,string>={APP:'普通用户',MERCHANT:'商家账号',ADMIN:'运营账号',GUEST:'游客',SYSTEM:'系统',UNKNOWN:'来源待核实'};
function subjectTypeLabel(row:Risk){if(row.scene==='AI_OUTPUT')return '系统';return subjects[row.subjectType || (row.userId ? 'APP' : 'GUEST')] || '来源待核实';}
function subjectLabel(row:Risk){if(row.scene==='AI_OUTPUT'||row.subjectType==='SYSTEM')return '系统输出（不归责用户）';if(row.subjectType==='UNKNOWN')return '来源待核实';return row.subjectName || (row.subjectId || row.userId ? '姓名暂不可用' : '未登录访客');}
let detailSequence=0;
async function openDetail(row:Risk){const seq=++detailSequence;selected.value=row;detail.value=true;detailLoading.value=true;detailError.value='';reason.value='';try{const result=await api<Risk>('records/detail',undefined,{id:row.id});if(seq===detailSequence)selected.value=result;}catch{if(seq===detailSequence)detailError.value='详情加载失败，请重试';}finally{if(seq===detailSequence)detailLoading.value=false;}}
let sequence=0;
async function load(){
 if(!allowed.value)return;
 const seq=++sequence, currentTab=tab.value;
 loading.value=true;error.value='';statsError.value='';
 try{
  if(currentTab!=='words'&&userId.value&&!/^\d+$/.test(userId.value))throw Error('请输入有效账号 ID');
  const results=await Promise.allSettled([
   currentTab!=='words'?api('records',undefined,{page:page.value,size:20,status:currentTab==='pending'?'PENDING':status.value||undefined,subjectId:userId.value||undefined,subjectType:subjectType.value||undefined,scene:scene.value||undefined,source:source.value||undefined,from:dates.value?.[0],to:dates.value?.[1]}):api('words'),
   api('stats')
  ]);
  if(seq!==sequence)return;
  const [listResult,statsResult]=results;
  const failures:string[]=[];
  stats.value={};
  if(statsResult.status==='fulfilled'&&statsResult.value&&typeof statsResult.value==='object'){
   const count=statsResult.value as unknown as Record<string,number>;
   for(const key of ['pendingAccounts','pendingRecords','confirmedAccounts']){
    if(typeof count[key]==='number'&&Number.isFinite(count[key]))stats.value[key]=count[key];
   }
  }else statsError.value='统计暂时不可用，列表仍可正常查询；点击刷新重试';
  records.value=[];words.value=[];total.value=0;
  if(listResult.status==='rejected'){
   const code=listResult.reason?.code;
   failures.push(code===403?'当前账号没有访问权限':code===404?'搜索风险接口尚未就绪，请联系管理员确认部署':'记录加载失败，请稍后重试；如持续失败，请联系管理员检查服务及数据库迁移');
  }else if(currentTab!=='words'){
   const result=listResult.value as unknown as {list?:Risk[];total?:number}|null;
   if(result&&Array.isArray(result.list)&&typeof result.total==='number'&&Number.isFinite(result.total)&&result.total>=0){
    records.value=result.list;total.value=result.total;
   }else failures.push('记录接口返回格式异常，请联系管理员');
  }else if(Array.isArray(listResult.value))words.value=listResult.value;
  else failures.push('词库接口返回格式异常，请联系管理员');
  error.value=failures.join('；'); 
 }catch(e:any){if(seq===sequence){records.value=[];words.value=[];total.value=0;error.value=e.message||'加载失败，请重试';}}
 finally{if(seq===sequence)loading.value=false;}
}
function highlight(row:Risk){let hits:string[]=[];try{const parsed=JSON.parse(row.matchedWords||'[]');if(Array.isArray(parsed))hits=parsed.filter(x=>typeof x==='string'&&x.length>0);}catch{}const text=row.content||'';const mask=new Uint8Array(text.length);try{const ranges=JSON.parse(row.matchedDetails||'[]');if(Array.isArray(ranges))for(const r of ranges){if(Number.isInteger(r.start)&&Number.isInteger(r.end)&&r.start>=0&&r.end>r.start&&r.end<=text.length)mask.fill(1,r.start,r.end);}}catch{}for(const hit of hits){let start=0,pos;while((pos=text.indexOf(hit,start))!==-1){mask.fill(1,pos,pos+hit.length);start=pos+1;}}const parts:{text:string;hit:boolean}[]=[];for(let i=0;i<text.length;i++){const hit=!!mask[i],last=parts[parts.length-1];if(last&&last.hit===hit)last.text+=text[i];else parts.push({text:text[i],hit});}return parts;}
function editWord(row?:Word){wordForm.value=row?{...row}:{word:'',mode:'REVIEW',enabled:true};wordDialog.value=true;}
async function saveWord(){if(!manage.value||saving.value)return;if(!wordForm.value.word.trim()){ElMessage.warning('请填写词条');return;}saving.value=true;try{await api('words/save',wordForm.value);wordDialog.value=false;ElMessage.success('词条已保存');await load();}catch(e:any){ElMessage.error(e.message||'保存失败');}finally{saving.value=false;}}
async function resolve(next:string){if(!manage.value||!selected.value||saving.value)return;if(!reason.value.trim()){ElMessage.warning('请填写处理原因');return;}const id=selected.value.id, currentDetail=detailSequence;saving.value=true;try{await api('records/resolve',{id,status:next,reason:reason.value.trim()});if(currentDetail===detailSequence&&selected.value?.id===id)detail.value=false;ElMessage.success('处置已记录');await load();}catch(e:any){ElMessage.error(e.message||'处置失败');}finally{saving.value=false;}}
onBeforeUnmount(()=>{sequence++;detailSequence++;});
watch(detail,value=>{reason.value='';if(!value)detailSequence++;});
watch(allowed,value=>{if(value)load();else{sequence++;loading.value=false;records.value=[];words.value=[];stats.value={};total.value=0;detail.value=false;wordDialog.value=false;auditDialog.value=false;}},{immediate:true});
</script>
<style scoped>
.risk-workbench :deep(.catalog-surface){overflow:auto}.risk-body{padding:20px 24px}.risk-summary{border-bottom:1px solid var(--el-border-color-lighter);margin-bottom:12px}.risk-summary>div{min-width:150px;padding:0 20px 12px 0}.source-path{display:block;color:var(--el-text-color-secondary);font-size:12px;overflow-wrap:anywhere}.risk-context{margin-top:20px}.risk-toolbar :deep(.el-date-editor){max-width:400px;flex:1 1 320px}.el-alert{margin:12px 0}.risk-toolbar .el-select{max-width:160px}@media(max-width:700px){.risk-body{padding:12px}}

.risk-summary{display:flex;gap:32px;padding:16px 0}.risk-summary div{display:grid;gap:8px}.risk-summary span{color:var(--el-text-color-secondary);font-size:13px}.risk-summary strong{font-size:26px;color:var(--workspace-accent)}.risk-toolbar{display:flex;gap:10px;margin:12px 0;flex-wrap:wrap}.risk-toolbar .el-input,.risk-toolbar .el-select{width:200px}.risk-content{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7}mark{background:var(--el-color-danger-light-9);color:var(--el-color-danger);border-radius:3px;padding:1px 2px}.risk-reason{margin:20px 0}.el-pagination{justify-content:flex-end;margin-top:16px}@media(max-width:600px){.risk-summary{gap:16px;flex-wrap:wrap}}
</style>


