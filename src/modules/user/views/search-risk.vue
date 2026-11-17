<template>
<CatalogWorkbench title="风险记录" description="查看命中内容和来源，复核后按需发送平台提醒。" class="risk-workbench">
 <el-result v-if="!allowed" icon="warning" title="无权查看内容风险记录" />
 <template v-else>
 <div class="risk-body">
 <div class="risk-summary"><div><span>待复核账号 · 已去重</span><strong>{{ stats.pendingAccounts ?? '—' }}</strong></div><div><span>待复核记录</span><strong>{{ stats.pendingRecords ?? '—' }}</strong></div><div><span>已确认账号 · 已去重</span><strong>{{ stats.confirmedAccounts ?? '—' }}</strong></div></div>
 <div class="risk-list-header">
  <el-tabs v-model="tab" class="risk-list-tabs" @tab-change="page=1;load()"><el-tab-pane label="待复核" name="pending"/><el-tab-pane label="全部记录" name="records"/></el-tabs>
  <RiskPreprocessing v-if="manage && tab === 'pending'" class="risk-list-action" @finished="refreshAfterPreprocessing" />
 </div>
 <div class="risk-toolbar">
 <el-input v-model="matchedWord" placeholder="命中词（完整词条）" clearable aria-label="命中词" @input="matchedWordId=undefined" @keyup.enter="page=1;load()"/>
 <el-select v-model="scene" placeholder="全部来源" clearable aria-label="内容来源"><el-option v-for="(label,key) in riskScenes" :key="key" :label="label" :value="key"/></el-select>
 <el-select v-if="tab!=='pending'" v-model="status" placeholder="全部复核状态" clearable aria-label="复核状态"><el-option v-for="(label,key) in statuses" :key="key" :label="label" :value="key"/></el-select>
 <el-button type="primary" @click="page=1;load()">查询</el-button><el-button @click="userId='';status='';scene='';matchedWord='';matchedWordId=undefined;subjectType='';dates=[];page=1;load()">重置</el-button>
 <el-button @click="load">刷新</el-button>
 <details class="risk-filters">
  <summary>更多筛选</summary>
  <div class="risk-toolbar">
   <el-input v-model="userId" placeholder="账号 ID" clearable aria-label="账号 ID"/>
   <el-select v-model="subjectType" placeholder="全部主体" clearable aria-label="账号类型"><el-option v-for="(label,key) in subjects" :key="key" :label="label" :value="key"/></el-select>
   <el-date-picker v-model="dates" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss" start-placeholder="开始时间" end-placeholder="结束时间（不含）" aria-label="记录时间范围"/>
  </div>
 </details>
 </div>
 <div v-if="manage && batchRows.length" class="risk-toolbar">
  <span>已选 {{ batchRows.length }} 条待复核记录</span>
  <el-button @click="batchDialog=true">批量复核</el-button>
  <el-button :disabled="!actionableAiRows.length || saving" @click="previewAiResolution">按 AI 建议处理</el-button>
 </div>
 <el-alert v-if="statsError" :title="statsError" type="warning" :closable="false" show-icon />
 <el-alert v-if="error" title="风险数据加载失败" type="error" :closable="false" show-icon><span>{{ error }}</span><el-button :loading="loading" @click="load">重新加载</el-button></el-alert>
 <el-table @selection-change="batchRows=$event" v-loading="loading" :data="records" :empty-text="error ? '数据未加载，请重试' : '暂无匹配记录'" row-key="id">
 <el-table-column v-if="manage" type="selection" width="44" :selectable="(row:Risk)=>row.status==='PENDING' && !row.duplicateOfId"/>
 <el-table-column label="命中内容" min-width="260"><template #default="{row}"><span class="risk-snippet"><template v-for="(part,i) in hitSnippet(row)" :key="i"><mark v-if="part.hit">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></span></template></el-table-column>
 <el-table-column label="提交者" min-width="145" show-overflow-tooltip><template #default="{row}">{{ subjectLabel(row) }}<small class="source-path">{{ subjectTypeLabel(row) }}</small></template></el-table-column>
 <el-table-column label="内容来源" min-width="140" show-overflow-tooltip><template #default="{row}">{{ originLabel(row) }}<small v-if="row.originName" class="source-path">{{ row.originName }}</small></template></el-table-column>
 <el-table-column label="检测结果" width="114"><template #default="{row}"><span :class="row.detectionResult==='BLOCKED' ? 'result-blocked' : ''">{{ detectionLabels[row.detectionResult || ''] || '未记录' }}</span></template></el-table-column>
 <el-table-column prop="createTime" label="时间" width="173"/>
 <el-table-column label="复核状态" min-width="120"><template #default="{row}">{{ row.duplicateOfId ? '已归并' : statuses[row.status] || row.status }}<small v-if="row.aiAdvice && !row.duplicateOfId" class="source-path">{{ aiSummary(row.aiAdvice) }}</small></template></el-table-column>
 <el-table-column label="操作" width="100" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openDetail(row)">查看详情</el-button></template></el-table-column>
 </el-table>
 <el-pagination v-if="!error" v-model:current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="load"/>
 </div>
 </template>
 <el-dialog v-model="aiResolutionDialog" title="按 AI 建议处理" width="min(760px, 94vw)" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
  <p>确认后处理 {{ aiResolutionRows.length }} 条记录。<template v-if="aiSkippedCount">另有 {{ aiSkippedCount }} 条缺少可采纳的建议，将继续待复核。</template></p>
  <el-table :data="aiResolutionRows" row-key="id" max-height="360">
   <el-table-column label="命中内容" min-width="185">
    <template #default="{row}"><span class="risk-snippet"><template v-for="(part,i) in hitSnippet(row)" :key="i"><mark v-if="part.hit">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></span></template>
   </el-table-column>
   <el-table-column prop="aiAdvice" label="AI 建议和理由" min-width="240" />
   <el-table-column label="将执行的操作" width="145"><template #default="{row}">{{ aiResolutionLabels[row.aiSuggestedStatus as AiSuggestedStatus] }}</template></el-table-column>
  </el-table>
  <template #footer>
   <el-button :disabled="saving" @click="aiResolutionDialog=false">取消</el-button>
   <el-button type="primary" :loading="saving" @click="resolveByAi">确认处理 {{ aiResolutionRows.length }} 条</el-button>
  </template>
 </el-dialog>
 <el-dialog v-model="batchDialog" @closed="batchReason=''" title="批量人工复核" width="min(500px,94vw)"><p>将处理 {{ batchRows.length }} 条记录；不会发送提醒或修改账号状态。任一记录不可处理时整批不提交。</p><el-input v-model="batchReason" type="textarea" maxlength="200" show-word-limit placeholder="复核原因（选填）"/><template #footer><el-button :loading="saving" @click="resolveBatch('IGNORED')">忽略</el-button><el-button :loading="saving" @click="resolveBatch('FALSE_POSITIVE')">误报</el-button><el-button type="primary" :loading="saving" @click="resolveBatch('CONFIRMED')">确认需关注</el-button></template></el-dialog>
 <el-drawer v-model="detail" title="风险记录与处置" size="min(620px, 100vw)">
 <el-alert v-if="detailError" :title="detailError" type="error" :closable="false"><el-button @click="selected && openDetail(selected)">重试</el-button></el-alert>
 <div v-loading="detailLoading"><template v-if="selected && !detailError && !detailLoading">
 <el-alert v-if="selected.originType==='AI_GENERATED' && selected.provenanceStatus==='VERIFIED'" title="这是 AI 系统输出，不归责于用户。" type="info" :closable="false"/>
 <p class="risk-content"><template v-for="(part,i) in highlight(selected)" :key="i"><mark v-if="part.hit">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></p>
 <div v-if="hitWords(selected).length" class="hit-links">
  <span>命中词</span>
  <el-button v-for="hit in hitWords(selected)" :key="hit.wordId ?? hit.word" link type="danger" @click="openWord(hit)">{{ hit.word }}</el-button>
 </div>
 <el-descriptions :column="1" border>
  <el-descriptions-item label="检测结果">{{ detectionLabels[selected.detectionResult || ''] || '未记录' }}</el-descriptions-item>
  <el-descriptions-item label="复核状态">{{ selected.duplicateOfId ? '已归并至记录 ' + selected.duplicateOfId : statuses[selected.status] || selected.status }}</el-descriptions-item>
  <el-descriptions-item label="提交者">{{ subjectLabel(selected) }} · {{ subjectTypeLabel(selected) }}</el-descriptions-item>
  <el-descriptions-item label="内容来源">{{ originLabel(selected) }}</el-descriptions-item>
  <el-descriptions-item v-if="selected.originMerchantName" label="所属商家">{{ selected.originMerchantName }}</el-descriptions-item>
  <el-descriptions-item v-if="selected.originName && !selected.chatContext" label="关联业务">{{ selected.originName }}</el-descriptions-item>
  <el-descriptions-item label="命中字段">{{ fieldLabels[(selected.originField || '').split(':')[0]] || selected.originField || '未记录' }}</el-descriptions-item>
  <el-descriptions-item label="检测入口">{{ entryLabels[selected.detectionEntry || ''] || '未记录' }}<small class="source-path">{{ selected.source }}</small></el-descriptions-item>
  <template v-if="selected.chatContext">
   <el-descriptions-item label="客服会话">会话 {{ selected.chatContext.chatId }}<template v-if="selected.chatContext.round"> · 第 {{ selected.chatContext.round }} 轮</template></el-descriptions-item>
   <el-descriptions-item label="聊天双方">{{ selected.chatContext.customerName || '顾客姓名未记录' }} ↔ {{ selected.chatContext.storeName || '门店名称未记录' }}<small v-if="selected.chatContext.merchantName" class="source-path">{{ selected.chatContext.merchantName }}</small></el-descriptions-item>
   <el-descriptions-item label="检测时的接待状态">{{ receptionLabels[selected.chatContext.state || ''] || '未记录' }}<template v-if="selected.chatContext.humanRequested"> · 顾客已请求人工</template></el-descriptions-item>
   <el-descriptions-item label="检测时的接待对象">{{ selected.chatContext.assigneeName || (selected.chatContext.assigneeId ? '员工姓名未记录' : selected.chatContext.state === 'AI' ? '门店 AI 客服' : selected.chatContext.state === 'WAITING' ? '等待员工接待' : '未记录') }}</el-descriptions-item>
  </template>
  <el-descriptions-item v-else-if="selected.scene==='CHAT' || selected.originType==='CHAT'" label="客服会话">未记录</el-descriptions-item>
  <template v-if="selected.handledAt">
   <el-descriptions-item label="处理人">{{ selected.operatorName || '未记录' }}</el-descriptions-item>
   <el-descriptions-item label="处理时间">{{ selected.handledAt }}</el-descriptions-item>
   <el-descriptions-item label="处理原因">{{ selected.reason || '未填写' }}</el-descriptions-item>
  </template>
 </el-descriptions>
 <el-alert v-if="selected.aiAdvice" title="AI 辅助建议" :description="selected.aiAdvice" type="info" :closable="false" />
 <template v-if="manage && selected.status==='PENDING' && !selected.duplicateOfId"><el-input v-model="reason" type="textarea" maxlength="200" show-word-limit placeholder="复核依据或误报原因（选填，最多200字）" class="risk-reason"/><el-button :loading="saving" @click="resolve('IGNORED')">本次忽略</el-button><el-button :loading="saving" @click="resolve('FALSE_POSITIVE')">标记误报</el-button><el-button type="primary" :loading="saving" @click="resolve('CONFIRMED')">确认需关注</el-button></template>
 <RiskWarning v-if="!selected.duplicateOfId" :key="selected.id" :risk="selected"/>
 </template></div></el-drawer>
</CatalogWorkbench>
</template>
<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount} from 'vue';
import {useRoute,useRouter} from 'vue-router';
import {ElMessage,ElMessageBox} from 'element-plus';
import CatalogWorkbench from '/@/modules/workspace/components/catalog-workbench.vue';
import {checkPerm} from '/@/modules/base/utils/permission';
import {isPlatform} from '/@/workspace';
import {riskApi as api,riskScenes} from '../api/content-risk';
import RiskWarning from '../components/risk-warning.vue';
import RiskPreprocessing from '../components/risk-preprocessing.vue';
type AiSuggestedStatus = 'CONFIRMED' | 'FALSE_POSITIVE';
interface ChatContext { chatId:number; customerName?:string; storeName?:string; merchantName?:string; round?:number; state?:string; assigneeId?:number; assigneeName?:string; humanRequested?:boolean }
interface WordHit { word:string; wordId?:number }
interface Risk {chatContext?:ChatContext;originMerchantName?:string;detectionResult?:string;originName?:string;originType?:string;originId?:number;originField?:string;provenanceStatus?:string;submittedByType?:string;submittedById?:number;detectionEntry?:string;contentVersion?:string;duplicateOfId?:number;scene?:string;subjectType?:string;subjectId?:number;subjectName?:string;businessType?:string;businessId?:number;aiAdvice?:string;aiSuggestedStatus?:AiSuggestedStatus;id:number;content:string;matchedWords:string;matchedDetails?:string;userId?:number;source:string;status:string;createTime?:string;operatorId?:number;operatorName?:string;handledAt?:string;reason?:string}
const allowed=computed(()=>isPlatform&&checkPerm('search:risk:view'));
const manage=computed(()=>allowed.value&&checkPerm('search:risk:manage'));
const statuses:Record<string,string>={PENDING:'待复核',IGNORED:'已忽略',CONFIRMED:'已确认需关注',FALSE_POSITIVE:'已标记误报'};
const tab=ref('pending'),records=ref<Risk[]>([]),stats=ref<Record<string,number>>({}),page=ref(1),total=ref(0),status=ref(''),userId=ref(''),error=ref(''),loading=ref(false),saving=ref(false),detail=ref(false),selected=ref<Risk>(),reason=ref('');
const batchRows=ref<Risk[]>([]),batchDialog=ref(false),batchReason=ref('');
const aiResolutionLabels: Record<AiSuggestedStatus,string> = { CONFIRMED: '确认需关注', FALSE_POSITIVE: '标记误报' };
const aiResolutionDialog = ref(false);
const aiResolutionRows = ref<Risk[]>([]);
const aiSkippedCount = ref(0);
const actionableAiRows = computed(() => batchRows.value.filter(row => row.status === 'PENDING' && row.aiAdvice
 && (row.aiSuggestedStatus === 'CONFIRMED' || row.aiSuggestedStatus === 'FALSE_POSITIVE')));
const fieldLabels:Record<string,string>={name:'名称',address:'地址',description:'描述',content:'正文',title:'标题',nickName:'昵称',workspaceNickName:'工作账号昵称',introduction:'简介',businessHours:'营业时间',reply:'回复',keyword:'关键词',message:'消息',prompt:'提示词',instruction:'用户指令',itemName:'券内项目名称',itemDescription:'券内项目说明'};
const entryLabels:Record<string,string>={WRITE:'业务写入',GENERATED_CONTENT:'生成内容',RULE_RESCAN:'词库重扫',SCHEDULED_SCAN:'定时巡检',SEARCH_INPUT:'搜索输入'};
async function resolveBatch(next: string) {
 if (!manage.value || saving.value) return;
 const ids = [...new Set(batchRows.value.map(row => row.id))];
 if (!ids.length || ids.length > 100) {
  ElMessage.warning('请选择 1 至 100 条待复核记录');
  return;
 }
 const note = batchReason.value.trim();
 saving.value = true;
 try {
  await ElMessageBox.confirm(`将${statuses[next]} ${ids.length} 条记录，确认继续？`, '批量复核');
  if (!manage.value) return;
  const result = await api<{resolvedCount:number;ids:number[]}>('records/resolve-batch', { ids, status: next, reason: note || null });
  if (result?.resolvedCount !== ids.length || !Array.isArray(result.ids) || result.ids.length !== ids.length
      || ids.some(id => !result.ids.includes(id))) {
   throw new Error('返回结果不完整，请刷新核对');
  }
  batchDialog.value = false;
  batchRows.value = [];
  ElMessage.success(`已复核 ${result.resolvedCount} 条`);
  await load();
 } catch (error: any) {
  if (error === 'cancel' || error === 'close') return;
  ElMessage.error(error.message || '批量复核未完成，请刷新后重试');
  if (Number(error.code) === 409) {
   batchDialog.value = false;
   await load();
  }
 } finally {
  saving.value = false;
 }
}

function previewAiResolution() {
 if (!manage.value || saving.value || !actionableAiRows.value.length) return;
 aiResolutionRows.value = actionableAiRows.value.slice();
 aiSkippedCount.value = batchRows.value.length - aiResolutionRows.value.length;
 aiResolutionDialog.value = true;
}

async function resolveByAi() {
 if (!manage.value || saving.value || !aiResolutionDialog.value || !aiResolutionRows.value.length) return;
 const items = aiResolutionRows.value.map(row => ({ id: row.id, expectedAdvice: row.aiAdvice }));
 saving.value = true;
 try {
  const result = await api<{resolvedCount:number;ids:number[]}>('records/resolve-by-ai', { items });
  if (result?.resolvedCount !== items.length || !Array.isArray(result.ids) || result.ids.length !== items.length
      || items.some(item => !result.ids.includes(item.id))) {
   throw new Error('返回结果不完整，请刷新核对');
  }
  aiResolutionDialog.value = false;
  ElMessage.success(`已按 AI 建议处理 ${result.resolvedCount} 条`);
  await load();
 } catch (failure) {
  const error = failure as { code?: number; message?: string };
  ElMessage.error(error?.message || '处理失败，请刷新后重试');
  if (Number(error?.code) === 409 || Number(error?.code) === 404) {
   aiResolutionDialog.value = false;
   await load();
  }
 } finally {
  saving.value = false;
 }
}
const route=useRoute(), router=useRouter();
const matchedWord=ref(typeof route.query.matchedWord === 'string' ? route.query.matchedWord : '');
const matchedWordId=ref(typeof route.query.wordId === 'string' ? route.query.wordId : undefined);
const detectionLabels:Record<string,string>={BLOCKED:'已拦截',ALLOWED_RECORDED:'放行留痕',SCAN_HIT:'重扫发现',UNKNOWN:'未记录'};
const receptionLabels:Record<string,string>={AI:'AI 接待',HUMAN:'人工接待',WAITING:'等待接待',CLOSED:'会话已结束'};
function hitWords(row:Risk):WordHit[]{
 const hits=new Map<string,WordHit>();
 try{for(const word of JSON.parse(row.matchedWords || '[]'))if(typeof word==='string'&&word)hits.set(word,{word});}catch{}
 try{for(const hit of JSON.parse(row.matchedDetails || '[]'))if(typeof hit?.word==='string'&&hit.word)hits.set(hit.word,{word:hit.word,wordId:hit.wordId});}catch{}
 return [...hits.values()];
}
function openWord(hit:WordHit){
 detail.value=false;
 router.push({path:'/user/search-risk/words',query:hit.wordId ? {wordId:String(hit.wordId)} : {word:hit.word}});
}
watch(()=>[route.query.matchedWord,route.query.wordId],([word,id])=>{
 if(typeof word==='string'){
  matchedWord.value=word;matchedWordId.value=typeof id==='string'?id:undefined;page.value=1;tab.value='pending';load();
 }
});
const statsError=ref(''),scene=ref(''),subjectType=ref(''),dates=ref<string[]>([]),detailError=ref(''),detailLoading=ref(false);
const subjects:Record<string,string>={APP:'普通用户',MERCHANT:'商家账号',ADMIN:'运营账号',STORE:'门店账号',WORKSPACE:'工作账号',GUEST:'游客',SYSTEM:'系统',UNKNOWN:'提交者未记录'};
const namedOrigins:Record<string,string>={'MERCHANT.name':'商家名称','STORE.name':'门店名称','COUPON.name':'优惠券名称','COUPON.itemName':'券内项目名称','COUPON.itemDescription':'券内项目说明'};
const originTypes:Record<string,string>={MERCHANT_APPLICATION:'商家入驻申请',PLATFORM_WARNING:'平台警告通知',AI_INPUT:'AI 用户输入'};

function originLabel(row:Risk) {
 if (row.originType==='AI_GENERATED') return row.provenanceStatus==='VERIFIED' ? 'AI 生成内容' : '来源待核实';
 if (row.originType && row.originType!=='UNKNOWN') {
  const field=row.originField?.split(':')[0];
  const name=field ? namedOrigins[`${row.originType}.${field}`] : undefined;
  return name || originTypes[row.originType] || riskScenes[row.originType] || '来源待核实';
 }
 return row.scene==='AI_OUTPUT' ? '历史生成入口（来源待核实）' : riskScenes[row.scene||''] || '来源待核实';
}

function subjectTypeLabel(row:Risk) {
 if (row.originType==='AI_GENERATED' && row.provenanceStatus==='VERIFIED') return '系统';
 return subjects[row.submittedByType || row.subjectType || 'UNKNOWN'] || '提交者未记录';
}

function subjectLabel(row:Risk) {
 if (row.originType==='AI_GENERATED' && row.provenanceStatus==='VERIFIED') return 'AI 生成内容（不归责用户）';
 const type = row.submittedByType || row.subjectType;
 if (type==='SYSTEM' && row.provenanceStatus!=='VERIFIED') return '历史系统标记（来源待核实）';
 if (type==='SYSTEM') return row.subjectName || '系统主体';
 if (!type || type==='UNKNOWN') return '—';
 return row.subjectName || (type==='GUEST' ? '未登录访客' : '姓名暂不可用');
}

function aiSummary(advice: string) {
 const label = advice.split(' · ', 1)[0];
 return ['建议优先复核', '疑似误报', '需要更多上下文'].includes(label) ? `AI · ${label}` : '有 AI 建议';
}

async function refreshAfterPreprocessing() {
 if (!batchRows.value.length && !batchDialog.value && !saving.value) await load();
 const row = selected.value;
 if (!detail.value || !row || detailLoading.value) return;
 try {
  const refreshed = await api<Risk>('records/detail', undefined, { id: row.id });
  if (detail.value && selected.value === row) selected.value = { ...row, aiAdvice: refreshed.aiAdvice };
 } catch { /* Existing details stay available for manual review. */ }
}

let detailSequence=0;
async function openDetail(row:Risk){const seq=++detailSequence;selected.value=row;detail.value=true;detailLoading.value=true;detailError.value='';reason.value='';try{const result=await api<Risk>('records/detail',undefined,{id:row.id});if(seq===detailSequence)selected.value=result;}catch{if(seq===detailSequence)detailError.value='详情加载失败，请重试';}finally{if(seq===detailSequence)detailLoading.value=false;}}
let sequence=0;
async function load(){
 if(!allowed.value)return;
 const seq=++sequence, currentTab=tab.value;
 loading.value=true;error.value='';statsError.value='';batchRows.value=[];
 try{
  if(userId.value&&!/^\d+$/.test(userId.value))throw Error('请输入有效账号 ID');
  const results=await Promise.allSettled([
   api('records',undefined,{page:page.value,size:20,status:currentTab==='pending'?'PENDING':status.value||undefined,subjectId:userId.value||undefined,subjectType:subjectType.value||undefined,scene:scene.value||undefined,matchedWord:matchedWord.value.trim()||undefined,matchedWordId:matchedWordId.value,from:dates.value?.[0],to:dates.value?.[1]}),
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
  records.value=[];total.value=0;
  if(listResult.status==='rejected'){
   const code=listResult.reason?.code;
   failures.push(code===403?'当前账号没有访问权限':code===404?'搜索风险接口尚未就绪，请联系管理员确认部署':'记录加载失败，请稍后重试；如持续失败，请联系管理员检查服务及数据库迁移');
  }else{
   const result=listResult.value as unknown as {list?:Risk[];total?:number}|null;
   if(result&&Array.isArray(result.list)&&typeof result.total==='number'&&Number.isFinite(result.total)&&result.total>=0){
    records.value=result.list;total.value=result.total;
   }else failures.push('记录接口返回格式异常，请联系管理员');
  }
  error.value=failures.join('；'); 
 }catch(e:any){if(seq===sequence){records.value=[];total.value=0;error.value=e.message||'加载失败，请重试';}}
 finally{if(seq===sequence)loading.value=false;}
}
function highlight(row:Risk){let hits:string[]=[];try{const parsed=JSON.parse(row.matchedWords||'[]');if(Array.isArray(parsed))hits=parsed.filter(x=>typeof x==='string'&&x.length>0);}catch{}const text=row.content||'';const mask=new Uint8Array(text.length);try{const ranges=JSON.parse(row.matchedDetails||'[]');if(Array.isArray(ranges))for(const r of ranges){if(Number.isInteger(r.start)&&Number.isInteger(r.end)&&r.start>=0&&r.end>r.start&&r.end<=text.length)mask.fill(1,r.start,r.end);}}catch{}for(const hit of hits){let start=0,pos;while((pos=text.indexOf(hit,start))!==-1){mask.fill(1,pos,pos+hit.length);start=pos+1;}}const parts:{text:string;hit:boolean}[]=[];for(let i=0;i<text.length;i++){const hit=!!mask[i],last=parts[parts.length-1];if(last&&last.hit===hit)last.text+=text[i];else parts.push({text:text[i],hit});}return parts;}
// List previews retain original highlight ranges; the drawer uses the full text.
function hitSnippet(row:Risk){
 const parts=highlight(row), text=row.content||'';
 let offset=0, first=-1;
 for(const part of parts){if(part.hit){first=offset;break;}offset+=part.text.length;}
 const anchor=first<0?0:first;
 const lineStart=Math.max(text.lastIndexOf('\n',anchor-1),text.lastIndexOf('\r',anchor-1))+1;
 const nextBreak=text.slice(anchor).search(/[\r\n]/);
 const lineEnd=nextBreak<0?text.length:anchor+nextBreak;
 const start=Math.max(lineStart,anchor-12),end=Math.min(lineEnd,start+100);
 const result:{text:string;hit:boolean}[]=[];
 if(start>0)result.push({text:'…',hit:false});
 offset=0;
 for(const part of parts){
  const from=Math.max(start,offset),to=Math.min(end,offset+part.text.length);
  if(to>from)result.push({text:part.text.slice(from-offset,to-offset),hit:part.hit});
  offset+=part.text.length;
 }
 if(end<text.length)result.push({text:'…',hit:false});
 return result;
}
async function resolve(next:string){if(!manage.value||selected.value?.status!=='PENDING'||selected.value.duplicateOfId||saving.value)return;if(reason.value.length>200){ElMessage.warning('处理原因不能超过200字');return;}const id=selected.value.id, currentDetail=detailSequence;saving.value=true;try{await api('records/resolve',{id,status:next,reason:reason.value.trim()||null});if(currentDetail===detailSequence&&selected.value?.id===id)detail.value=false;ElMessage.success('处置已记录');await load();}catch(e:any){ElMessage.error(e.message||'处置失败');}finally{saving.value=false;}}
onBeforeUnmount(()=>{sequence++;detailSequence++;});
watch(detail,value=>{reason.value='';if(!value)detailSequence++;});
watch(allowed,value=>{if(value)load();else{sequence++;loading.value=false;records.value=[];stats.value={};total.value=0;detail.value=false;}},{immediate:true});
</script>
<style scoped>
.risk-list-header { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:4px 20px; margin-bottom:12px; border-bottom:1px solid var(--el-border-color-light); }
.risk-list-tabs { flex:1 0 auto; }
.risk-list-tabs :deep(.el-tabs__header) { margin:0; }
.risk-list-tabs :deep(.el-tabs__nav-wrap::after) { display:none; }
.risk-list-action { margin:4px 0; max-width:100%; }
.risk-snippet{display:block;max-width:100%;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:1.7}
.risk-filters{flex-basis:100%;font-size:13px;color:var(--el-text-color-regular)}
.risk-filters summary{cursor:pointer;width:fit-content}
.hit-links{display:flex;align-items:center;flex-wrap:wrap;gap:4px 12px;margin-bottom:16px}
.hit-links .el-button{margin:0;text-decoration:underline;text-underline-offset:3px}
.result-blocked{color:var(--el-color-danger);font-weight:600}

.risk-workbench :deep(.catalog-surface){overflow:auto}.risk-body{padding:20px 24px}.risk-summary{border-bottom:1px solid var(--el-border-color-lighter);margin-bottom:12px}.risk-summary>div{min-width:150px;padding:0 20px 12px 0}.source-path{display:block;color:var(--el-text-color-secondary);font-size:12px;overflow-wrap:anywhere}.risk-context{margin-top:20px}.risk-toolbar :deep(.el-date-editor){max-width:400px;flex:1 1 320px}.el-alert{margin:12px 0}.risk-toolbar .el-select{max-width:160px}@media(max-width:700px){.risk-body{padding:12px}}

.risk-summary{display:flex;gap:32px;padding:16px 0}.risk-summary div{display:grid;gap:8px}.risk-summary span{color:var(--el-text-color-secondary);font-size:13px}.risk-summary strong{font-size:26px;color:var(--workspace-accent)}.risk-toolbar{display:flex;gap:10px;margin:12px 0;flex-wrap:wrap}.risk-toolbar .el-input,.risk-toolbar .el-select{width:200px}.risk-content{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7}mark{background:var(--el-color-danger-light-9);color:var(--el-color-danger);border-radius:3px;padding:1px 2px}.risk-reason{margin:20px 0}.el-pagination{justify-content:flex-end;margin-top:16px}@media(max-width:600px){.risk-summary{gap:16px;flex-wrap:wrap}}
</style>


