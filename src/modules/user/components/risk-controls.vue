<template>
 <section class="risk-controls">
  <div class="section-title"><h3>检测入口</h3><el-button :loading="loading" :disabled="!!busy" @click="load();loadSource()">刷新规则</el-button></div>
  <p>规则动作仅作用于命中词条的内容。独立评价业务、图片文字识别暂未接入。</p>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <div v-loading="loading" class="policy-grid">
   <article v-for="policy in policies" :key="policy.scene">
    <strong>{{ riskScenes[policy.scene] || policy.scene }}</strong>
    <el-switch :model-value="policy.enabled" :disabled="!manage || !!busy" :aria-label="`${riskScenes[policy.scene]}检测开关`" @change="value=>save(policy,{enabled:!!value})" />
    <el-select :model-value="policy.action" :disabled="!manage || !!busy" :aria-label="`${riskScenes[policy.scene]}命中动作`" @change="value=>save(policy,{action:value})"><el-option label="沿用词条规则" value="INHERIT"/><el-option label="拦截命中内容" value="BLOCK"/><el-option label="进入人工复核" value="REVIEW"/></el-select>
   </article>
  </div>
  <el-empty v-if="!loading && !error && !policies.length" description="暂无入口规则" :image-size="48"/>
  <h3>命中词库</h3><el-alert v-if="sourceError" :title="sourceError" type="warning" :closable="false"/><p v-if="wordSource">引擎：{{ wordSource.engine }} · 词库：{{ wordSource.dictionary }} · {{ wordSource.license }}<br/>内置库{{ wordSource.bundledEnabled ? '已启用' : '已停用' }}，命中仅进入复核。白名单按完整文本归一化后精确匹配。</p>
 </section>
</template>
<script setup lang="ts">
import {ref,onMounted} from 'vue';
import {ElMessage} from 'element-plus';
import {riskApi,riskScenes} from '../api/content-risk';
const props=defineProps<{manage:boolean}>();
interface Policy{scene:string;enabled:boolean;action:string}
const wordSource=ref<{engine:string;dictionary:string;license:string;bundledEnabled:boolean}>(),sourceError=ref('');
async function loadSource(){try{wordSource.value=await riskApi('word-sources');sourceError.value='';}catch{sourceError.value='词库来源加载失败';}}
const policies=ref<Policy[]>([]),loading=ref(false),error=ref(''),busy=ref('');
async function load(){loading.value=true;error.value='';try{const result=await riskApi<Policy[]>('policies');if(!Array.isArray(result))throw Error();policies.value=result;}catch{error.value='入口规则加载失败，请刷新重试';}finally{loading.value=false;}}
async function save(policy:Policy,change:Partial<Policy>){if(!props.manage||busy.value)return;busy.value=policy.scene;try{await riskApi('policies/save',{...policy,...change});Object.assign(policy,change);ElMessage.success('规则已保存');}catch{ElMessage.error('规则未保存，原设置保持不变');}finally{busy.value='';}}
onMounted(()=>{load();loadSource();});
</script>
<style scoped>
.risk-controls{padding:12px 0}.section-title{display:flex;justify-content:space-between;align-items:center}h3{font-size:15px;margin:12px 0}p{font-size:13px;color:var(--el-text-color-secondary)}.policy-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px;margin:16px 0 24px}.policy-grid article{display:grid;grid-template-columns:1fr auto;gap:8px;padding:14px;border:1px solid var(--el-border-color-lighter);border-radius:8px}.policy-grid .el-select{grid-column:1/-1}.policy-grid strong{font-size:14px;align-self:center}
</style>
