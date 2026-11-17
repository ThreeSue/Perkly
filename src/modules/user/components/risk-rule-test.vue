<template>
 <el-button @click="open=true">测试规则</el-button>
 <el-dialog v-model="open" title="测试词库规则" width="min(560px, 94vw)" append-to-body>
  <el-form label-position="top" @submit.prevent="run">
   <el-form-item label="检测场景">
    <el-select v-model="scene" :disabled="loading">
     <el-option v-for="(label,key) in riskScenes" :key="key" :label="label" :value="key" />
    </el-select>
   </el-form-item>
   <el-form-item label="测试文字">
    <el-input v-model="text" type="textarea" :rows="4" :maxlength="scene==='SEARCH'?100:20000" show-word-limit :disabled="loading" />
   </el-form-item>
  </el-form>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <section v-if="result" aria-live="polite" class="rule-result">
   <strong>{{ actions[result.action] || '未返回规则动作' }}</strong>
   <div v-if="result.hits.length" class="rule-hits">
    <el-tag v-for="hit in uniqueHits" :key="hit.word" type="danger">{{ hit.word }}</el-tag>
   </div>
  </section>
  <template #footer>
   <el-button @click="open=false">关闭</el-button>
   <el-button type="primary" :disabled="!text.trim()" :loading="loading" @click="run">查看结果</el-button>
  </template>
 </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { riskApi, riskScenes } from '../api/content-risk';

interface RuleResult { action: string; hits: { word: string }[] }
const open=ref(false), scene=ref('CHAT'), text=ref(''), loading=ref(false), error=ref('');
const result=ref<RuleResult>();
const actions:Record<string,string>={BLOCK:'将拦截',REVIEW:'将放行并留痕',DISABLED:'此场景检测已停用',WHITELISTED:'全文白名单放行',PASS:'放行，未命中词条'};
const uniqueHits=computed(()=>[...new Map(result.value?.hits.map(hit=>[hit.word,hit])).values()]);
watch([text,scene],()=>{result.value=undefined;error.value='';});
async function run(){
 if(loading.value||!text.value.trim())return;
 loading.value=true;error.value='';result.value=undefined;
 try{result.value=await riskApi<RuleResult>('words/test',{text:text.value,scene:scene.value});}
 catch(e){error.value=(e as {message?:string})?.message || '测试失败，请重试';}
 finally{loading.value=false;}
}
</script>

<style scoped>
.rule-result{background:var(--el-fill-color-light);border-radius:8px;padding:14px;line-height:1.5}
.rule-hits{display:flex;flex-wrap:wrap;gap:8px;margin-top:8px}
</style>
