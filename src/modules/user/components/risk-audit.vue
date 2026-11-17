<template>
<section v-loading="loading"><header><div><h3>风险操作记录</h3><p>包含全部规则、复核、通知和巡检操作，不限于当前风险记录。</p></div><el-button @click="load">刷新记录</el-button></header><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-table :data="rows" :empty-text="error ? '审计数据未加载' : '暂无操作记录'"><el-table-column prop="createTime" label="时间" width="180"/><el-table-column label="操作" min-width="140"><template #default="{row}">{{ actions[row.action] || row.action }}</template></el-table-column><el-table-column label="操作人" min-width="120"><template #default="{row}">{{ row.operatorName || (row.operatorId == null ? "系统任务" : "姓名暂不可用") }}</template></el-table-column><el-table-column label="操作对象" min-width="220" show-overflow-tooltip><template #default="{row}">{{ targetLabel(row.targetName) }}</template></el-table-column><el-table-column label="详情" min-width="200"><template #default="{row}"><details><summary>查看操作内容</summary><dl class="audit-detail"><div v-for="(item,index) in detailItems(row)" :key="index"><dt>{{ item.label }}</dt><dd>{{ item.value }}</dd></div></dl></details></template></el-table-column></el-table><el-pagination v-if="!error" v-model:current-page="page" :page-size="20" :total="total" layout="total,prev,pager,next" @current-change="load"/></section>
</template>
<script setup lang="ts">
import {ref,onMounted,onUnmounted} from 'vue';import {riskApi,riskScenes} from '../api/content-risk';
interface Audit{operatorName?:string;targetName?:string;id:number;createTime:string;operatorId:number;targetId:number;action:string;detail:string}
const rows=ref<Audit[]>([]),page=ref(1),total=ref(0),error=ref(''),loading=ref(false);let seq=0;
const actions:Record<string,string>={RESOLVE_BATCH:'批量人工复核',WORD_IMPORT:'导入词库',IMPORT_HOUBB:'导入已审定词库',HISTORY_APPLY:'应用历史整理',HISTORY_UPDATE_ORIGIN:'修正历史来源',HISTORY_MERGE:'归并重复记录',HISTORY_RESCAN:'纯词库重扫',USER_STATUS:'调整账号状态',JOB_START:'启动巡检',JOB_RETRY:'重试巡检',RESOLVE:'人工复核',WORD_SAVE:'保存词条',WORD_DELETE:'删除词条',POLICY_SAVE:'修改入口规则',WARNING_SEND:'发送警告',SCHEDULE_CONFIG:'保存巡检计划',SCHEDULE_SAVE:'保存巡检计划'};
function targetLabel(value?:string){if(!value)return '对象名称暂不可用';return value.replace(/(检测入口：|巡检任务：|重扫资料：)([A-Z_]+)/,(_,prefix,key)=>prefix+(riskScenes[key] || ({RISKS:'已有风险记录',WORKSPACE_PROFILE:'工作账号资料',MERCHANT_APPLICATION:'商家入驻申请',PLATFORM_WARNING:'平台警告通知'} as Record<string,string>)[key] || key));}
function detailItems(row:Audit):{label:string;value:string}[]{
 let data:Record<string,unknown>;
 try{const parsed=JSON.parse(row.detail);if(!parsed||typeof parsed!=='object'||Array.isArray(parsed))return [{label:'历史记录',value:'仅保存了关联编号，未记录具体操作内容'}];data=parsed;}catch{return [{label:'历史记录',value:'详情格式无法识别'}];}
 if(!Object.keys(data).length)return [{label:'历史记录',value:'当时未保存具体操作内容，无法还原'}];
 const result:{label:string;value:string}[]=[];
 const accountStates:Record<string,string>={'0':'禁用','1':'正常','2':'已注销'};
 const values:Record<string,string>={...riskScenes,VERIFIED:'已核实',UNVERIFIED:'待核实',WRITE:'业务写入',RULE_RESCAN:'词库重扫',GENERATED_CONTENT:'生成内容',RISKS:'已有风险记录',APP:'普通用户',MERCHANT:'商家账号',ADMIN:'运营账号',SYSTEM:'系统',BLOCK:'拦截',REVIEW:'人工复核',ALLOW:'白名单',INHERIT:'沿用词条规则',PENDING:'待复核',IGNORED:'本次忽略',FALSE_POSITIVE:'误报',CONFIRMED:'确认需关注',LIGHT:'轻通知',STRONG:'强提醒'};
 const labels:Record<string,string>={originId:'原始对象编号',duplicateOfId:'归并主记录编号',submittedByType:'已核实的提交者类型',originType:'原始业务类型',originField:'原始字段',contentVersion:'内容版本',provenanceStatus:'归因证据',detectionEntry:'检测入口',evidence:'归因证据',sourceVersion:'词库版本',inserted:'新增词条数',existing:'已有条数',tombstoned:'保留删除数',checked:'检查数',created:'新增记录数',updated:'修正记录数',merged:'归并记录数',unchanged:'未变化记录数',resolvedCount:'复核条数',word:'词条',mode:'处理方式',enabled:'是否启用',scene:'检测入口',action:'命中处理方式',status:'复核结果',reason:'处理原因',aiAdvice:'采纳的 AI 建议',subjectType:'账号类型',title:'通知标题',content:'通知内容',noticeType:'通知方式',intervalMinutes:'检查间隔（分钟）',batchSize:'每批最多条数',dailyBudget:'每日模型调用上限',sources:'巡检来源',source:'巡检来源',limit:'最多检查条数',modelBudget:'模型调用预算'};
 if(row.action==='USER_STATUS')result.push({label:'账号状态',value:`${accountStates[String(data.from)] || '未知状态'} → ${accountStates[String(data.to)] || '未知状态'}`});
 const format=(v:unknown):string=>typeof v==='boolean'?(v?'开启':'关闭'):v==null||v===''?'未填写':values[String(v)]||String(v);
 for (const [key,label] of [['before','整理前'],['after','整理后']] as const) {
  const snapshot=data[key];
  if (snapshot && typeof snapshot==='object') {
   for (const [field,value] of Object.entries(snapshot)) {
    if (labels[field] && value != null) result.push({label:label+' · '+labels[field],value:format(value)});
   }
  }
 }
 for(const [key,value] of Object.entries(data)){
  if(!labels[key])continue;
  result.push({label:labels[key],value:Array.isArray(value)?value.map(format).join('、'):format(value)});
 }
 return result.length?result:[{label:'历史记录',value:'未记录可展示的操作说明'}];
}
async function load(){const id=++seq;loading.value=true;error.value='';try{const result=await riskApi<{list:Audit[];total:number}>('audit',undefined,{page:page.value,size:20});if(!Array.isArray(result.list)||typeof result.total!=='number')throw Error();if(id===seq){rows.value=result.list;total.value=result.total;}}catch{if(id===seq){rows.value=[];error.value='审计加载失败，请刷新重试';}}finally{if(id===seq)loading.value=false;}}
onMounted(load);onUnmounted(()=>seq++);
</script>
<style scoped>
header{display:flex;justify-content:space-between;align-items:center;gap:12px}h3{font-size:16px}p{font-size:13px;color:var(--el-text-color-secondary)}.audit-detail{margin:10px 0}.audit-detail>div{margin:8px 0}.audit-detail dt{font-size:12px;color:var(--el-text-color-secondary)}.audit-detail dd{margin:3px 0 0;white-space:pre-wrap;overflow-wrap:anywhere}.el-pagination{justify-content:flex-end;margin-top:16px}summary{cursor:pointer;color:var(--el-color-primary)}
</style>
