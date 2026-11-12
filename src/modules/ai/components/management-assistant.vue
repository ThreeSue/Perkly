<template>
  <section class="management-assistant assistant-edge is-visible" :class="{ compact, 'is-workbench': !compact && scope === 'GLOBAL' }" aria-label="AI助手">
    <header class="management-assistant__header">
      <div class="assistant-heading"><cl-svg name="icon-ai" aria-hidden="true" /><div><strong>{{ scope === 'GLOBAL' ? 'AI 助手' : names[scope] + ' AI 助手' }}</strong>
        <p>{{ scope === 'GLOBAL' ? '查询业务资料，核对后完成授权操作' : '仅服务当前模块，切换任务由你决定' }}</p></div></div>
      <el-button text @click="emit('close')">关闭</el-button>
    </header>
    <div ref="transcript" class="management-assistant__transcript" aria-live="polite" aria-relevant="additions">
      <div v-if="!turns.length" class="assistant-welcome"><h2>今天需要处理什么？</h2><p>可以先查询，再告诉我需要调整的地方。</p>
        <div class="assistant-suggestions"><el-button v-for="text in suggestions" :key="text" @click="send(text)">{{ text }}</el-button></div>
      </div>
      <article v-for="(turn,index) in turns" :key="index" :class="['assistant-turn',turn.role]">
        <span class="turn-speaker"><cl-svg v-if="turn.role === 'assistant'" name="icon-ai" aria-hidden="true" />{{ turn.role === 'user' ? '你' : '助手' }}</span>
        <p>{{ turn.text }}</p>
        <template v-if="turn.reply">
          <div v-for="(result,i) in turn.reply.results" :key="i" class="assistant-result">
            <strong>{{ names[result.module] || result.module }}</strong><p class="result-note">{{ result.note }}</p>
            <template v-if="result.state === 'CONTINUE'"><el-button @click="couponDemand = String(result.data); couponOpen = true">继续优惠券任务</el-button></template>
            <template v-else-if="result.state === 'COUPON_RESULT'">
              <p>{{ result.data.reply.text }}</p>
              <el-button :disabled="busy || turn.reply !== latestReply" @click="openCoupon(result.data)">核对或继续优惠券任务</el-button>
            </template>
            <template v-else-if="result.state === 'DRAFT'">
              <AssistantFacts :value="result.data" :scope="result.module" /><el-button type="primary" @click="emit('adopt', result.data)">采用候选稿</el-button>
              <el-button @click="result.data = null; result.note = '已放弃，原表单未改变'; result.state = 'CANCELLED'">放弃</el-button>
            </template>
            <details v-else-if="result.data" :open="result.state === 'COMPLETED'"><summary>查看真实结果</summary><AssistantFacts :value="result.data" :scope="result.module" /></details>
          </div>
          <el-button v-if="turn.reply.handoffText" type="primary" @click="handoff(turn.reply.handoffText)">前往 AI 工作台继续</el-button>
        </template>
      </article>
      <div v-if="busy" class="assistant-waiting" role="status"><span class="waiting-indicator" aria-hidden="true"><i></i><i></i><i></i></span><span>正在核对业务数据…</span></div>
    </div>
    <div v-if="preview" class="assistant-review">
      <strong>待确认操作</strong>
      <div v-for="(step,i) in preview.steps" :key="i"><p>{{ step.note }}</p><AssistantFacts :value="step.data" :scope="step.module" /></div>
      <p v-if="!preview.steps.length">未完成步骤保留，可重试或调整需求。已完成步骤不会重复执行。</p>
      <div class="review-actions"><el-button :disabled="busy" @click="cancel">取消待办</el-button><el-button type="primary" :loading="busy" @click="confirm">确认执行</el-button></div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div class="assistant-input-area"><AssistantComposer v-model="message" :busy="busy" placeholder="描述要查询或处理的事情" @send="send" /></div>
    <el-drawer v-model="couponOpen" append-to-body size="min(840px, 96vw)" title="优惠券任务" :destroy-on-close="false">
      <CouponAssistant :key="couponRevision" :initial-text="couponDemand" :bootstrap="couponBootstrap" standalone-editor :visible="couponOpen" @saved="emit('saved')" @close="couponOpen = false" />
    </el-drawer>
  </section>
</template>
<script setup lang="ts">
import './assistant-edge.css';
import { ref, computed, nextTick, onBeforeUnmount, onMounted, watch } from 'vue';
import { useBase } from '/$/base';
import { identity } from '/@/workspace';
import { useRouter } from 'vue-router';
import { featureApi } from '/$/workspace/feature-api';
import AssistantComposer from './assistant-composer.vue';
import AssistantFacts from './assistant-facts.vue';
import CouponAssistant from './coupon-assistant.vue';
const props = withDefaults(defineProps<{ scope?: string; compact?: boolean; initialText?: string; selectedId?: number; pageContext?: string }>(), { scope: 'GLOBAL' });
const emit = defineEmits<{ close: []; saved: []; adopt: [draft: { title: string; content: string }] }>();
type Result = { module: string; state: string; note: string; data: any };
type Preview = { token: string; steps: Result[] };
type Reply = { sessionId: string; text: string; results: Result[]; preview?: Preview; degraded: boolean; handoffText?: string };
const router = useRouter();
const { user } = useBase();
const message = ref(props.initialText || '');
watch(()=>props.initialText,value=>{if(value)message.value=value;});
const sessionId = ref<string>();
const preview = ref<Preview>();
const latestReply=ref<Reply>();
const couponBootstrap=ref<any>();const couponRevision=ref(0);
function openCoupon(data:any){if(couponBootstrap.value!==data){couponBootstrap.value=data;couponDemand.value='';couponRevision.value++;}couponOpen.value=true;}
const turns = ref<{role: 'user'|'assistant';text:string;reply?:Reply}[]>([]);
const busy=ref(false), error=ref(''), transcript=ref<HTMLElement>(), couponOpen=ref(false), couponDemand=ref('');
let disposed=false;onBeforeUnmount(()=>{disposed=true;});
const names: Record<string,string> = {GLOBAL:'工作台',COUPON:'优惠券',CATEGORY:'商家分类',STORE_TYPE:'门店类型',MERCHANT:'商家',STORE:'门店',ORDER:'购券订单',CONSUMPTION:'消费账单',REDEMPTION:'核销记录',STAFF:'员工与岗位',APPROVAL:'审核',ACTIVITY:'活动',NOTICE:'通知'};
const capabilities=ref<{module:string;read:boolean;actions:string[]}[]>([]);
onMounted(async()=>{try{const result=await featureApi('ai/assistant/workbench/capabilities');if(!disposed)capabilities.value=result;}catch(e:any){error.value=e?.message || '能力列表加载失败';}});
const suggestions = computed(()=> capabilities.value.filter(c=>(props.scope==='GLOBAL' || c.module===props.scope) && c.read)
  .slice(0,4).map(c=>c.module==='NOTICE'?'查询我收到的通知':'查询'+names[c.module]));
async function scroll(){await nextTick();if(transcript.value)transcript.value.scrollTop=transcript.value.scrollHeight;}
async function receive(reply: Reply) {
  if(disposed)return;
  sessionId.value=reply.sessionId;preview.value=reply.preview;latestReply.value=reply;
  const text=reply.text || '系统提示：AI文字暂时不可用，已保留真实结果。';
  const index=turns.value.length;
  turns.value.push({role:'assistant',text:'',reply});
  if(window.matchMedia('(prefers-reduced-motion: reduce)').matches){turns.value[index].text=text;await scroll();return;}
  const chunk=Math.max(4,Math.ceil(text.length/24));
  for(let end=chunk;!disposed;end+=chunk){
    turns.value[index].text=text.slice(0,end);await scroll();
    if(end>=text.length)break;
    await new Promise(resolve=>setTimeout(resolve,20));
  }
}
async function send(text: string) {
  if(busy.value || !text.trim())return;
  busy.value=true;error.value='';turns.value.push({role:'user',text});message.value='';void scroll();
  try {
    const reply:Reply=await featureApi('ai/assistant/workbench/chat',{sessionId:sessionId.value,requestId:crypto.randomUUID(),scope:props.scope,text,selectedId:props.selectedId,pageContext:props.pageContext});
    await receive(reply);
    const coupon=reply.results.find(r=>r.state==='COUPON_RESULT');
    if(coupon && (coupon.data.reply.draft || coupon.data.reply.token || coupon.data.reply.clarification || coupon.data.reply.batch?.length))openCoupon(coupon.data);
  } catch(e:any){error.value=e?.message || '请求失败，请重试';message.value=text;}
  finally{busy.value=false;}
}
async function confirm() {
  if(!preview.value || busy.value)return;busy.value=true;error.value='';
  try {const reply:Reply=await featureApi('ai/assistant/workbench/confirm',{sessionId:sessionId.value,token:preview.value.token});await receive(reply);if(reply.results.some(r=>r.state==='COMPLETED'))emit('saved');}
  catch(e:any){error.value=e?.message || '执行结果尚未确认，请保留任务并重试核对';}
  finally{busy.value=false;}
}
async function cancel() {
  if(!sessionId.value || busy.value)return;busy.value=true;
  try{await featureApi('ai/assistant/workbench/cancel',{sessionId:sessionId.value});preview.value=undefined;latestReply.value=undefined;couponOpen.value=false;couponBootstrap.value=undefined;}
  catch(e:any){error.value=e?.message || '取消失败，请重试';}finally{busy.value=false;}
}
async function handoff(text:string) {
  if(busy.value)return;
  if(preview.value){await cancel();if(preview.value)return;}
  sessionStorage.setItem('perkly.ai.handoff',JSON.stringify({text,accountKey:identity+':'+user.info?.id,createdAt:Date.now()}));
  await router.push('/my/assistant');
}
</script>
<style scoped>
.management-assistant { box-sizing:border-box; display:flex; flex-direction:column; height:100%; min-width:0; min-height:0; background:var(--el-bg-color); color:var(--el-text-color-primary); border:1px solid var(--el-border-color-light); border-radius:12px; overflow:hidden; }
.management-assistant__header { display:flex; justify-content:space-between; align-items:center; gap:8px; padding:16px 20px; border-bottom:1px solid var(--el-border-color-light); }
.management-assistant__header strong { font-size:16px; }.management-assistant__header p { color:var(--el-text-color-secondary); font-size:12px; margin:6px 0 0; }
.management-assistant__transcript { flex:1; min-height:0; overflow:auto; padding:24px; scrollbar-width:thin; scroll-behavior:smooth; }
.assistant-welcome { padding:48px 0; max-width:600px; margin:auto; }.assistant-welcome h2 { font-size:26px; margin:0 0 12px; }.assistant-welcome p { color:var(--el-text-color-secondary); }
.assistant-suggestions { display:flex; flex-wrap:wrap; gap:8px; margin-top:24px; }.assistant-suggestions .el-button { margin:0; }
.assistant-turn { max-width:720px; margin:0 auto 24px; }.assistant-turn.user { text-align:right; }.assistant-turn.user > p { display:inline-block; text-align:left; background:var(--workspace-selection); padding:12px 16px; border-radius:12px; max-width:85%; }
.assistant-turn p { white-space:pre-wrap; overflow-wrap:anywhere; line-height:1.75; margin:8px 0; }.turn-speaker { display:block; font-size:12px; color:var(--el-text-color-secondary); }
.assistant-result { padding:12px 0; border-top:1px solid var(--el-border-color-light); }.result-note { font-size:12px; color:var(--el-text-color-secondary); }
details summary { cursor:pointer; color:var(--el-color-primary); margin:8px 0; }.assistant-review { padding:16px 20px; max-height:38%; overflow:auto; border-top:1px solid var(--el-border-color-light); }.review-actions { display:flex; justify-content:flex-end; margin-top:12px; gap:8px; }
.assistant-waiting { color:var(--el-text-color-secondary); font-size:13px; }.compact .management-assistant__transcript { padding:14px; }.compact .assistant-welcome { padding:16px 0; }.compact .assistant-welcome h2 { font-size:18px; }.compact .management-assistant__header { padding:12px; }
@media (prefers-reduced-motion:reduce) { .management-assistant__transcript { scroll-behavior:auto; } }

/* Full-page conversations share one reading measure, including pending feedback. */
.management-assistant { --conversation-width:800px; }
.assistant-heading { display:flex; align-items:center; gap:12px; min-width:0; }
.assistant-heading > :deep(.cl-svg) { width:30px; height:30px; flex-shrink:0; color:var(--el-color-primary); }
.turn-speaker { display:flex; align-items:center; gap:8px; font-weight:600; }
.turn-speaker :deep(.cl-svg) { width:20px; height:20px; color:var(--el-color-primary); }
.user .turn-speaker { justify-content:flex-end; }
.assistant-turn { max-width:var(--conversation-width); margin-bottom:28px; }
.assistant-turn > p { font-size:15px; line-height:1.8; }
.assistant-turn.user > p { border-radius:16px 16px 4px 16px; background:var(--el-color-primary-light-9); }
.assistant-result { margin-top:16px; padding:14px 16px; border:1px solid var(--el-border-color-lighter); border-radius:10px; background:var(--el-fill-color-extra-light); }
.assistant-result > strong { font-size:13px; }
.assistant-result summary { font-size:13px; padding-block:4px; }
.assistant-waiting { display:flex; align-items:center; gap:10px; max-width:var(--conversation-width); margin:0 auto 24px; min-height:32px; }
.waiting-indicator { display:flex; gap:4px; color:var(--el-color-primary); }
.waiting-indicator i { width:5px; height:5px; border-radius:50%; background:currentColor; animation:assistant-pending 1.2s ease-in-out infinite; }
.waiting-indicator i:nth-child(2) { animation-delay:.15s; }.waiting-indicator i:nth-child(3) { animation-delay:.3s; }
.assistant-input-area { flex-shrink:0; min-width:0; }
.is-workbench { border:0; border-radius:0; background:var(--el-bg-color-page); }
.is-workbench::before,.is-workbench::after { display:none; }
.is-workbench .management-assistant__header { width:min(var(--conversation-width),calc(100% - 48px)); box-sizing:border-box; margin:0 auto; padding:20px 0 16px; border-bottom:1px solid var(--el-border-color-light); flex-shrink:0; }
.is-workbench .management-assistant__header strong { font-size:20px; }
.is-workbench .management-assistant__transcript { padding:24px; }
.is-workbench .assistant-welcome { max-width:var(--conversation-width); padding:clamp(32px,8vh,80px) 0; }
.is-workbench .assistant-welcome h2 { font-size:28px; letter-spacing:-.02em; }
.is-workbench .assistant-input-area { padding:12px 24px 16px; }
.is-workbench :deep(.assistant-composer) { max-width:var(--conversation-width); box-sizing:border-box; margin:0 auto; padding:12px 16px; border:1px solid var(--el-border-color); border-radius:16px; background:var(--el-bg-color); transition:border-color .18s ease; }
.is-workbench :deep(.assistant-composer:focus-within) { border-color:var(--el-color-primary); }
.is-workbench :deep(.assistant-composer textarea) { font-size:14px; line-height:1.7; }
.is-workbench > .assistant-review,.is-workbench > .el-alert { width:min(var(--conversation-width),calc(100% - 48px)); box-sizing:border-box; margin-inline:auto; flex-shrink:0; }
.compact .assistant-heading { gap:8px; }.compact .assistant-heading > :deep(.cl-svg) { width:22px; height:22px; }
.compact .assistant-result { padding:10px; }.compact .assistant-turn > p { font-size:14px; }
@keyframes assistant-pending { 0%,80%,100% { opacity:.35; transform:translateY(0); } 40% { opacity:1; transform:translateY(-3px); } }
@media(max-width:600px) { .is-workbench .management-assistant__header { width:calc(100% - 24px); padding-block:12px; }.is-workbench .management-assistant__transcript { padding:16px 12px; }.is-workbench .assistant-input-area { padding:8px 12px 12px; }.assistant-turn.user > p { max-width:92%; } }
@media(prefers-reduced-motion:reduce) { .waiting-indicator i { animation:none; } }
</style>
