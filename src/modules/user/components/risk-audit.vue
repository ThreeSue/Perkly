<template>
<section v-loading="loading"><header><div><h3>全局风险操作审计</h3><p>包含全部规则、复核、通知和巡检操作，不限于当前风险记录。</p></div><el-button @click="load">刷新审计</el-button></header><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-table :data="rows" :empty-text="error ? '审计数据未加载' : '暂无操作记录'"><el-table-column prop="createTime" label="时间" width="180"/><el-table-column label="操作" min-width="140"><template #default="{row}">{{ actions[row.action] || row.action }}</template></el-table-column><el-table-column prop="operatorId" label="操作人 ID" width="120"/><el-table-column prop="targetId" label="目标编号" width="100"/><el-table-column label="详情" min-width="200"><template #default="{row}"><details><summary>查看操作内容</summary><pre>{{ row.detail }}</pre></details></template></el-table-column></el-table><el-pagination v-if="!error" v-model:current-page="page" :page-size="20" :total="total" layout="total,prev,pager,next" @current-change="load"/></section>
</template>
<script setup lang="ts">
import {ref,onMounted,onUnmounted} from 'vue';import {riskApi} from '../api/content-risk';
interface Audit{id:number;createTime:string;operatorId:number;targetId:number;action:string;detail:string}
const rows=ref<Audit[]>([]),page=ref(1),total=ref(0),error=ref(''),loading=ref(false);let seq=0;
const actions:Record<string,string>={RESOLVE:'人工复核',WORD_SAVE:'保存词条',WORD_DELETE:'删除词条',POLICY_SAVE:'修改入口规则',WARNING_SEND:'发送警告',SCHEDULE_SAVE:'保存巡检计划'};
async function load(){const id=++seq;loading.value=true;error.value='';try{const result=await riskApi<{list:Audit[];total:number}>('audit',undefined,{page:page.value,size:20});if(!Array.isArray(result.list)||typeof result.total!=='number')throw Error();if(id===seq){rows.value=result.list;total.value=result.total;}}catch{if(id===seq){rows.value=[];error.value='审计加载失败，请刷新重试';}}finally{if(id===seq)loading.value=false;}}
onMounted(load);onUnmounted(()=>seq++);
</script>
<style scoped>
header{display:flex;justify-content:space-between;align-items:center;gap:12px}h3{font-size:16px}p{font-size:13px;color:var(--el-text-color-secondary)}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit}.el-pagination{justify-content:flex-end;margin-top:16px}summary{cursor:pointer;color:var(--el-color-primary)}
</style>
