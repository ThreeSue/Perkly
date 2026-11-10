<template>
  <section class="notice-writing">
    <el-button text type="primary" @click="open = !open"><cl-svg name="icon-ai" /> AI 起草 / 润色</el-button>
    <div v-if="open">
      <p>只生成候选稿，采用后回填标题与正文。收件人和通知类型由你设置。</p>
      <AssistantComposer v-model="instruction" :busy="busy" placeholder="说明要表达的意思，或输入润色要求" @send="generate" />
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <div v-if="candidate" class="notice-candidate"><strong>{{ candidate.title }}</strong><p>{{ candidate.content }}</p>
        <el-button :disabled="busy" @click="generate(lastInstruction)">重新生成</el-button>
        <el-button :disabled="busy" @click="candidate = undefined">放弃</el-button>
        <el-button type="primary" :disabled="busy" @click="adopt">采用候选稿</el-button>
      </div>
    </div>
  </section>
</template>
<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue';
import { featureApi } from '/$/workspace/feature-api';
import AssistantComposer from './assistant-composer.vue';
const props=defineProps<{title:string;content:string}>();
const emit=defineEmits<{adopt:[draft:{title:string;content:string}]}>();
const open=ref(false),busy=ref(false),error=ref(''),instruction=ref(''),candidate=ref<{title:string;content:string}>();
let lastInstruction='', disposed=false;
onBeforeUnmount(()=>{disposed=true;});
async function generate(text:string) {
  if(!text.trim() || busy.value)return;busy.value=true;error.value='';lastInstruction=text;
  try {const result=await featureApi('ai/assistant/workbench/notice-draft',{instruction:text,title:props.title,content:props.content,previous:candidate.value});
    if(!disposed){candidate.value=result;instruction.value='';}
  } catch(e:any){if(!disposed)error.value=e?.message || '生成失败，原表单未改变';}finally{busy.value=false;}
}
function adopt(){if(candidate.value){emit('adopt',{...candidate.value});candidate.value=undefined;open.value=false;}}
</script>
<style scoped>
.notice-writing { margin:8px 0 20px; }.notice-writing p { color:var(--el-text-color-secondary); line-height:1.7; font-size:13px; }
.notice-candidate { padding:16px 0; }.notice-candidate p { white-space:pre-wrap; color:var(--el-text-color-primary); }
</style>
