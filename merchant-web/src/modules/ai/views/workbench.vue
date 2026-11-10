<template><ManagementAssistant :key="accountKey" :initial-text="demand" @close="$router.push('/')" @adopt="adopt" /></template>
<script setup lang="ts">
import { computed, ref, onActivated, onMounted } from 'vue';
import { useBase } from '/$/base';
import { useRouter } from 'vue-router';
import { identity } from '/@/workspace';
import ManagementAssistant from '../components/management-assistant.vue';
const { user }=useBase();const router=useRouter();
const accountKey=computed(()=>identity+':'+user.info?.id);
const demand=ref('');
function receiveHandoff(){
  const raw=sessionStorage.getItem('perkly.ai.handoff');
  sessionStorage.removeItem('perkly.ai.handoff');
  if(!raw)return;
  try{const item=JSON.parse(raw);if(item.accountKey===accountKey.value && Date.now()-item.createdAt<1800000)demand.value=item.text;}catch{ /* Discard old or malformed handoffs. */ }
}
onMounted(receiveHandoff);onActivated(receiveHandoff);
function adopt(draft:{title:string;content:string}) {
  sessionStorage.setItem('perkly.ai.notice-draft',JSON.stringify({...draft,accountKey:accountKey.value}));
  void router.push('/my/notices');
}
</script>
