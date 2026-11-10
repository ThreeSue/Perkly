<template>
  <div class="module-assistant-layout" :class="{ 'is-open': open && scope }">
    <div ref="main" class="module-assistant-main"><slot /></div>
    <Teleport v-if="scope && toolbar" :to="toolbar">
      <AssistantEntry :active="open" @click="open = !open" />
    </Teleport>
    <aside class="module-assistant-side" :inert="!open || !scope" :aria-hidden="!open || !scope">
      <ManagementAssistant v-if="scope && visited" :key="route.path + ':' + accountKey" :scope="scope" :page-context="route.path" compact @close="open = false" @saved="refresh" @adopt="adopt" />
    </aside>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, shallowRef, watch, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useBase } from '/$/base';
import { useCool } from '/@/cool';
import { identity } from '/@/workspace';
import ManagementAssistant from './management-assistant.vue';
import AssistantEntry from './assistant-entry.vue';
const route=useRoute(),router=useRouter();const {user}=useBase();
const emit=defineEmits<{saved:[]}>();
const { mitt }=useCool();
function refresh(){mitt.emit('view.refresh');emit('saved');}
const open=ref(false);
const visited=ref(false);
watch(open,value=>{if(value)visited.value=true;});
const main=ref<HTMLElement>();
const toolbar=shallowRef<HTMLElement>();
let observer: MutationObserver | undefined;
let frame=0;
// Pages share a small set of toolbar layouts. Teleport keeps the control in normal
// flow, including wrapped filters, without changing each page's business markup.
const toolbarSelectors=['.management-toolbar','.record-filters','.record-toolbar','.queue-controls',
 '.filter-panel > .el-form','.cl-crud > .cl-row','.store-toolbar','.grant-heading',
 '.workbench-banner-actions','.management-heading','.workbench-banner'];
function locateToolbar() {
  cancelAnimationFrame(frame);
  frame=requestAnimationFrame(()=>{
    let target:HTMLElement | undefined;
    if(scope.value)for(const selector of toolbarSelectors){
      target=Array.from(main.value?.querySelectorAll<HTMLElement>(selector) || [])
        .find(el=>el.getClientRects().length>0 && !el.closest('[aria-hidden="true"]'));
      if(target)break;
    }
    if(toolbar.value===target)return;
    toolbar.value?.classList.remove('ai-entry-toolbar');
    target?.classList.add('ai-entry-toolbar');
    toolbar.value=target;
  });
}
onMounted(()=>{
  observer=new MutationObserver(locateToolbar);
  if(main.value)observer.observe(main.value,{childList:true,subtree:true,attributes:true,attributeFilter:['class','style','aria-hidden']});
  window.addEventListener('resize',locateToolbar);
  locateToolbar();
});
onBeforeUnmount(()=>{observer?.disconnect();cancelAnimationFrame(frame);window.removeEventListener('resize',locateToolbar);toolbar.value?.classList.remove('ai-entry-toolbar');});
const accountKey=computed(()=>identity+':'+user.info?.id);
const modules:Record<string,string> = {
 '/merchant/category':'CATEGORY','/merchant/storeCategory':'STORE_TYPE',
 '/merchant/info':'MERCHANT','/merchant/store':'STORE','/merchant/stores':'STORE','/store/stores':'STORE',
 '/merchant/profile':'MERCHANT','/merchant/orders':'ORDER','/coupon/purchaseOrder':'ORDER','/coupon/purchases':'ORDER',
 '/coupon/consumptionOrder':'CONSUMPTION','/coupon/consumption':'CONSUMPTION','/store/redemption':'REDEMPTION','/merchant/redemption':'REDEMPTION',
 '/workspace/accounts':'STAFF','/workspace/roles':'STAFF','/workspace/grants':'STAFF',
 '/approval/center':'APPROVAL','/workspace/activities':'ACTIVITY','/my/activities':'ACTIVITY','/my/notices':'NOTICE',
 '/workspace/notices':'NOTICE','/workspace-admin/notices':'NOTICE',
 '/workspace-admin/accounts':'STAFF','/workspace-admin/roles':'STAFF','/workspace-admin/authorization':'STAFF',
 '/merchant/accounts':'STAFF','/merchant/roles':'STAFF','/merchant/authorization':'STAFF',
 '/store/accounts':'STAFF','/store/roles':'STAFF',
 '/merchant/consumption':'CONSUMPTION','/store/consumption':'CONSUMPTION',
 '/merchant/notices':'NOTICE','/store/notices':'NOTICE','/merchant/activities':'ACTIVITY',
 '/approval/merchant':'APPROVAL','/approval/coupon':'APPROVAL','/merchant/storeMap':'STORE'
};
const scope=computed(()=>modules[route.path]);
watch(()=>route.path,async()=>{open.value=false;visited.value=false;toolbar.value?.classList.remove('ai-entry-toolbar');toolbar.value=undefined;await nextTick();locateToolbar();});
watch(accountKey,()=>{open.value=false;visited.value=false;});
function adopt(draft:{title:string;content:string}) {
 sessionStorage.setItem('perkly.ai.notice-draft',JSON.stringify({...draft,accountKey:accountKey.value}));
 window.dispatchEvent(new CustomEvent('perkly:notice-draft'));
 if(route.path!=='/my/notices')void router.push('/my/notices');
}
</script>
<style scoped>
.module-assistant-layout { --assistant-width:clamp(260px,23vw,310px); --assistant-motion:300ms cubic-bezier(.22,.61,.36,1); flex:1; min-width:0; min-height:0; display:grid; grid-template-columns:minmax(0,1fr) 0px; position:relative; overflow:hidden; container-type:inline-size; transition:grid-template-columns var(--assistant-motion); }
.module-assistant-layout.is-open { grid-template-columns:minmax(0,1fr) calc(var(--assistant-width) + 10px); }
.module-assistant-main { display:flex; flex:1; min-width:0; min-height:0; }
.module-assistant-main :deep(.ai-entry-toolbar) { display:flex; align-items:center; gap:10px; }
.module-assistant-main :deep(:is(.ai-entry-toolbar,.management-toolbar,.coupon-filters,.business-actions)) { flex-wrap:nowrap !important; min-width:0; overflow-x:auto; scrollbar-width:thin; }
.module-assistant-main :deep(:is(.ai-entry-toolbar,.management-toolbar,.coupon-filters,.business-actions) .el-button) { flex-shrink:0; white-space:nowrap; }
.module-assistant-main :deep(.ai-entry-toolbar > .el-form-item) { flex:0 1 auto; min-width:140px; margin-bottom:0; }
.module-assistant-main :deep(.ai-entry-toolbar > .el-form-item:last-of-type) { flex-shrink:0; min-width:max-content; }
.module-assistant-main :deep(.ai-entry-toolbar > .el-form-item:has(.el-date-editor)) { flex-shrink:0; min-width:280px; }
.module-assistant-main :deep(.ai-entry-toolbar .el-form-item__content) { flex-wrap:nowrap; }
.module-assistant-main :deep(.ai-entry-toolbar > :is(.el-input,.el-select)) { flex:0 1 180px; min-width:120px; }
.module-assistant-main :deep(:is(.business-actions,.ai-entry-toolbar) .cl-search) { flex-shrink:0; flex-wrap:nowrap; }
.module-assistant-main :deep(:is(.coupon-filters .filter-secondary,.queue-search,.queue-filters)) { flex-wrap:nowrap; flex-shrink:0; }
.module-assistant-main :deep(.ai-entry-toolbar > .assistant-entry) { position:sticky; right:0; z-index:2; flex-shrink:0; }
.module-assistant-side { position:relative; min-width:0; min-height:0; overflow:clip; opacity:0; visibility:hidden; transition:opacity var(--assistant-motion),visibility 0s linear 300ms; }
.module-assistant-side > * { position:absolute; inset:0 10px 10px auto; width:var(--assistant-width); transform:translateX(14px); transition:transform var(--assistant-motion); }
.is-open .module-assistant-side { opacity:1; visibility:visible; transition-delay:0s; }
.is-open .module-assistant-side > * { transform:translateX(0); }
@container(max-width:900px){
 .module-assistant-main { grid-column:1 / -1; }
 .module-assistant-side { position:absolute; inset:0 0 0 auto; width:min(320px,94%); z-index:5; }
 .module-assistant-side > * { width:calc(100% - 10px); }
}
@media(prefers-reduced-motion:reduce){.module-assistant-layout,.module-assistant-side,.module-assistant-side > *{transition:none;}}
</style>
