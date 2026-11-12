<template>
<section v-loading="loading" class="schedule">
 <header><div><h3>定时巡检</h3><p>有界循环回扫，不是即时增量检测。开启后可能产生模型调用费用。</p></div><el-button :disabled="saving" @click="load">刷新计划</el-button></header>
 <el-alert v-if="error" :title="error" type="error" :closable="false"/>
 <template v-if="saved">
 <el-descriptions :column="2" border><el-descriptions-item label="当前计划">{{ saved.enabled ? '已启用' : '已关闭' }}</el-descriptions-item><el-descriptions-item label="今日调用">{{ saved.usedCalls ?? '—' }} / {{ saved.dailyBudget }}</el-descriptions-item><el-descriptions-item label="最近运行">{{ saved.lastRunAt || '尚未运行' }}</el-descriptions-item><el-descriptions-item label="下次运行">{{ saved.enabled ? saved.nextRunAt || '等待调度' : '计划已关闭' }}</el-descriptions-item><el-descriptions-item label="最近结果">{{ saved.lastResult === 'NOT_STARTED' ? '尚未运行' : saved.lastResult || '暂无结果' }}</el-descriptions-item></el-descriptions>
 <el-form label-position="top" class="schedule-form" :disabled="!manage || saving">
 <el-form-item label="启用定时巡检"><el-switch v-model="form.enabled"/></el-form-item>
 <el-form-item label="检查间隔（分钟）"><el-input-number v-model="form.intervalMinutes" :min="1" :max="10080"/></el-form-item>
 <el-form-item label="每批最多条数"><el-input-number v-model="form.batchSize" :min="1" :max="18"/></el-form-item>
 <el-form-item label="每日模型调用上限"><el-input-number v-model="form.dailyBudget" :min="0" :max="1000"/></el-form-item>
 <el-form-item label="巡检来源" class="sources"><el-checkbox-group v-model="form.sources"><el-checkbox v-for="key in sources" :key="key" :value="key">{{ riskScenes[key] || '已有风险记录' }}</el-checkbox></el-checkbox-group></el-form-item>
 </el-form>
 <el-button v-if="manage" type="primary" :loading="saving" @click="save">保存巡检计划</el-button>
 </template>
</section>
</template>
<script setup lang="ts">
import {ref,onMounted,onUnmounted} from 'vue';
import {ElMessage,ElMessageBox} from 'element-plus';
import {riskApi,riskScenes} from '../api/content-risk';
const props=defineProps<{manage:boolean}>();
interface Config{enabled:boolean;intervalMinutes:number;batchSize:number;dailyBudget:number;sources:string[]}
interface Schedule extends Omit<Config,'sources'>{sourcesJson:string;usedCalls?:number;lastRunAt?:string;nextRunAt?:string;lastResult?:string}
const sources=['PROFILE','FEEDBACK','CHAT','MERCHANT','STORE','COUPON','NOTICE','RISKS'];
const saved=ref<Schedule>(),form=ref<Config>({enabled:false,intervalMinutes:60,batchSize:10,dailyBudget:0,sources:[]});
const loading=ref(false),saving=ref(false),error=ref('');let seq=0;
async function load(){const id=++seq;loading.value=true;error.value='';try{const data=await riskApi<Schedule>('schedule');const list=JSON.parse(data.sourcesJson);if(!Array.isArray(list))throw Error();if(id!==seq)return;saved.value=data;form.value={enabled:data.enabled,intervalMinutes:data.intervalMinutes,batchSize:data.batchSize,dailyBudget:data.dailyBudget,sources:list};}catch{if(id===seq){saved.value=undefined;error.value='巡检计划加载失败，请刷新重试';}}finally{if(id===seq)loading.value=false;}}
async function save(){if(!props.manage||saving.value)return;if(!form.value.sources.length){ElMessage.warning('至少选择一个来源');return;}saving.value=true;error.value='';try{if(form.value.enabled)await ElMessageBox.confirm('启用计划将按每日预算调用模型，可能产生费用。确认保存？','确认巡检计划',{type:'warning'});await riskApi('schedule/save',{...form.value});ElMessage.success('计划已保存');await load();}catch(e){if(e!=='cancel'&&e!=='close')error.value='计划未保存。运行中的计划不能修改，请稍后重试';}finally{saving.value=false;}}
onMounted(load);onUnmounted(()=>seq++);
</script>
<style scoped>
.schedule{padding:16px 0}header{display:flex;align-items:center;justify-content:space-between;gap:16px}h3{font-size:16px;margin:0}p{color:var(--el-text-color-secondary);font-size:13px}.schedule-form{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:12px;margin-top:20px}.sources{grid-column:1/-1}
</style>
