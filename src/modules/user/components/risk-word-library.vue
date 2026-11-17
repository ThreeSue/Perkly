<template>
<section class="word-library">
 <el-alert v-if="sourceError" :title="sourceError" type="warning" :closable="false">
  <el-button @click="loadSource">重试</el-button>
 </el-alert>
 <el-alert v-else type="info" :closable="false" :title="sourceDescription">
  <p v-if="wordSource" class="source-meta">
   引擎 {{ wordSource.engine }} · 词库 {{ wordSource.dictionary }} · {{ wordSource.license }}
   <br />白名单按完整文本归一化后精确匹配。
  </p>
 </el-alert>
 <div class="toolbar"><el-input v-model="keyword" placeholder="搜索词条" clearable @keyup.enter="search"/><el-select v-model="enabled" placeholder="全部状态" clearable><el-option label="启用" :value="true"/><el-option label="停用" :value="false"/></el-select><el-input v-model="source" placeholder="导入来源（选填）" clearable/><el-button type="primary" @click="search">查询</el-button><el-button @click="keyword='';source='';enabled=undefined;search()">重置</el-button><el-button v-if="manage" @click="edit()">新增词条</el-button><el-button v-if="manage" @click="importDialog=true">导入已审定词库</el-button><RiskRuleTest v-if="manage" /></div>
 <el-alert v-if="linkedError" :title="linkedError" type="warning" :closable="false" />
 <el-drawer v-model="linkedOpen" title="命中词条" size="min(450px,100vw)">
  <el-descriptions v-if="linkedWord" :column="1" border>
   <el-descriptions-item label="词条">{{ linkedWord.word }}</el-descriptions-item>
   <el-descriptions-item label="规则">{{ modes[linkedWord.mode] }}</el-descriptions-item>
   <el-descriptions-item label="状态">{{ linkedWord.deleted?'已删除':linkedWord.enabled?'启用':'停用' }}</el-descriptions-item>
   <el-descriptions-item label="来源">{{ linkedWord.source || '未记录' }}</el-descriptions-item>
  </el-descriptions>
  <template #footer>
   <el-button v-if="manage && linkedWord && !linkedWord.deleted" :disabled="saving" @click="toggle(linkedWord)">{{ linkedWord.enabled?'停用词条':'启用词条' }}</el-button>
   <el-button v-if="manage && linkedWord && !linkedWord.deleted" @click="edit(linkedWord)">编辑词条</el-button>
   <el-button v-if="manage && linkedWord && (!linkedWord.enabled || linkedWord.deleted)" type="primary" @click="reviewHits(linkedWord)">核对历史命中</el-button>
  </template>
 </el-drawer>
 <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button @click="load">重试</el-button></el-alert>
 <el-table v-loading="loading" :data="rows" :empty-text="error?'词库未加载':'暂无匹配词条'" row-key="id"><el-table-column prop="word" label="词条" min-width="180"/><el-table-column label="规则" width="110"><template #default="{row}">{{ modes[row.mode] || row.mode }}</template></el-table-column><el-table-column label="状态" width="90"><template #default="{row}">{{ row.enabled?'启用':'停用' }}</template></el-table-column><el-table-column label="来源与版本" min-width="180"><template #default="{row}">{{ row.source || '历史来源未记录' }}<small>{{ row.sourceVersion || '—' }}</small></template></el-table-column><el-table-column prop="license" label="许可证" min-width="120"/><el-table-column v-if="manage" label="操作" width="235"><template #default="{row}"><el-button link @click="edit(row)">编辑</el-button><el-button v-if="!row.enabled" link @click="reviewHits(row)">核对命中</el-button><el-button link :disabled="saving" @click="toggle(row)">{{ row.enabled?'停用':'启用' }}</el-button><el-button link type="danger" :disabled="saving" @click="remove(row)">删除</el-button></template></el-table-column></el-table>
 <el-pagination v-if="!error" v-model:current-page="page" :page-size="20" :total="total" layout="total,prev,pager,next" @current-change="load"/>
 <el-dialog v-model="dialog" title="词条设置" width="min(480px,94vw)"><el-form label-position="top"><el-form-item label="词条"><el-input v-model="form.word" maxlength="100" show-word-limit/></el-form-item><el-form-item label="规则"><el-select v-model="form.mode"><el-option v-for="(label,key) in modes" :key="key" :label="label" :value="key"/></el-select></el-form-item><el-form-item label="启用"><el-switch v-model="form.enabled"/></el-form-item></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save(form)">保存</el-button></template></el-dialog>
 <el-dialog v-model="importDialog" title="导入服务器已审定词库" width="min(500px,94vw)"><p>仅导入服务器内已审定的 houbb 版本，不接受远程地址。同版本可重试，已删除条目不会恢复。</p><el-input v-model="version" placeholder="已审定版本号"/><el-alert v-if="importResult" :title="importResult" type="success" :closable="false"/><template #footer><el-button @click="importDialog=false">关闭</el-button><el-button type="primary" :loading="saving" :disabled="!version.trim()" @click="importWords">导入</el-button></template></el-dialog>
</section>
</template>
<script setup lang="ts">
import {computed,ref,onMounted,onBeforeUnmount,watch} from 'vue';
import {useRoute,useRouter} from 'vue-router';
import RiskRuleTest from './risk-rule-test.vue';
import {ElMessage,ElMessageBox} from 'element-plus';
import {riskApi} from '../api/content-risk';
const props=defineProps<{manage:boolean}>();
interface WordSource {
 engine: string;
 dictionary: string;
 license: string;
 bundledEnabled: boolean;
 managed?: boolean;
}

const wordSource = ref<WordSource>();
const sourceError = ref('');
const sourceDescription = computed(() => {
 if (wordSource.value?.managed) return '当前使用管理词库，启用且未删除的词条参与检测。';
 if (wordSource.value?.bundledEnabled) return '内置词库作为补充，命中内置词进入人工复核。';
 if (wordSource.value) return '当前内置词库未参与检测。';
 return '启用的管理词条参与检测，白名单按完整文本精确匹配。';
});

let sourceSequence = 0;
async function loadSource() {
 const request = ++sourceSequence;
 try {
  const data = await riskApi<WordSource>('word-sources');
  if (typeof data?.bundledEnabled !== 'boolean') throw new Error('词库来源返回格式异常');
  if (request === sourceSequence) {
   wordSource.value = data;
   sourceError.value = '';
  }
 } catch {
  if (request === sourceSequence) {
   wordSource.value = undefined;
   sourceError.value = '词库来源加载失败';
  }
 }
}

interface Word{id?:number;word:string;mode:string;enabled:boolean;deleted?:boolean;source?:string;sourceVersion?:string;license?:string}
const route=useRoute(),router=useRouter();
const linkedWord=ref<Word>(),linkedOpen=ref(false),linkedError=ref('');
let linkSequence=0;
async function openLinkedWord(){
 const seq=++linkSequence;
 const id=typeof route.query.wordId==='string' ? route.query.wordId : undefined;
 const word=typeof route.query.word==='string' ? route.query.word : undefined;
 linkedOpen.value=false;linkedError.value='';
 if(!id&&!word)return;
 try {
  const result=await riskApi<Word>('words/detail',undefined,{id,word});
  if(seq!==linkSequence)return;
  linkedWord.value=result;linkedOpen.value=true;
 }catch(e){
  if(seq===linkSequence)linkedError.value=Number((e as {code?:number})?.code)===404 ? '该历史命中没有可关联的管理词条' : '词条加载失败，请重试';
 }
}
watch(()=>[route.query.wordId,route.query.word],openLinkedWord,{immediate:true});
function reviewHits(row:Word){
 linkedOpen.value=false;
 router.push({path:'/user/search-risk',query:{matchedWord:row.word, ...(row.id ? {wordId:String(row.id)} : {})}});
}
const modes:Record<string,string>={BLOCK:'拦截',REVIEW:'人工复核',ALLOW:'全文白名单'};
const rows=ref<Word[]>([]),keyword=ref(''),source=ref(''),enabled=ref<boolean>(),page=ref(1),total=ref(0),loading=ref(false),saving=ref(false),error=ref(''),dialog=ref(false),form=ref<Word>({word:'',mode:'REVIEW',enabled:true}),importDialog=ref(false),version=ref('1.0.0'),importResult=ref('');let seq=0;
function search(){page.value=1;load();}
async function load(){const id=++seq;loading.value=true;error.value='';try{const data=await riskApi<{list:Word[];total:number}>('words/page',undefined,{page:page.value,size:20,keyword:keyword.value.trim()||undefined,source:source.value.trim()||undefined,enabled:enabled.value});if(!Array.isArray(data.list)||!Number.isFinite(data.total))throw Error('词库分页格式异常');if(id===seq){rows.value=data.list;total.value=data.total;}}catch(e:any){if(id===seq){rows.value=[];total.value=0;error.value=e.message||'分页词库接口暂不可用，请确认后端部署后重试';}}finally{if(id===seq)loading.value=false;}}
function edit(row?:Word){form.value=row?{...row}:{word:'',mode:'REVIEW',enabled:true};dialog.value=true;}
async function save(row:Word){if(!props.manage||saving.value)return;if(!row.word.trim()){ElMessage.warning('请填写词条');return;}saving.value=true;try{await riskApi('words/save',{id:row.id,word:row.word.trim(),mode:row.mode,enabled:row.enabled});if(linkedWord.value?.id===row.id)linkedWord.value={...row};dialog.value=false;ElMessage.success('词条已保存');await load();}catch(e:any){ElMessage.error(e.message||'词条保存失败');}finally{saving.value=false;}}
async function toggle(row:Word){await save({...row,enabled:!row.enabled});}
async function remove(row:Word){if(!props.manage||saving.value)return;try{await ElMessageBox.confirm(`删除“${row.word}”后将停止参与检测，重新导入不会恢复。`,'删除词条',{type:'warning'});}catch{return;}saving.value=true;try{await riskApi('words/delete',{id:row.id});ElMessage.success('词条已删除');await load();}catch(e:any){ElMessage.error(e.message||'删除失败');}finally{saving.value=false;}}
let importRequest:{version:string;requestId:string}|undefined;
async function importWords(){if(!props.manage||saving.value||!version.value.trim())return;const v=version.value.trim();if(importRequest?.version!==v)importRequest={version:v,requestId:crypto.randomUUID()};saving.value=true;importResult.value='';try{const r=await riskApi<{inserted:number;existing:number;tombstoned:number;sourceVersion:string}>('words/import-houbb',importRequest);if(!Number.isFinite(r.inserted)||!Number.isFinite(r.existing))throw Error('导入返回结果异常，请核对操作记录');importResult.value=`版本 ${r.sourceVersion}：新增 ${r.inserted}，已有 ${r.existing}，保留删除 ${r.tombstoned}`;await Promise.all([load(), loadSource()]);}catch(e:any){ElMessage.error(e.message||'导入未完成，请确认已审定版本及接口部署');}finally{saving.value=false;}}
onMounted(() => {
 load();
 loadSource();
});
onBeforeUnmount(() => {
 seq++;
 sourceSequence++;
 linkSequence++;
});
</script>
<style scoped>
.source-meta { margin: 4px 0 0; overflow-wrap: anywhere; }
.toolbar{display:flex;flex-wrap:wrap;gap:10px;margin:16px 0}.toolbar .el-input{width:200px}.toolbar .el-select{width:120px}small{display:block;color:var(--el-text-color-secondary)}.el-pagination{justify-content:flex-end;margin-top:16px}.el-alert{margin:12px 0}
</style>
