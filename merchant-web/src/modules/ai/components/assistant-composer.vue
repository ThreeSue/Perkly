<template>
  <form class="assistant-composer" @submit.prevent="submit">
    <el-input v-model="value" type="textarea" resize="none" :rows="2" :maxlength="1500"
      :placeholder="placeholder" aria-label="发送给AI助手的消息" @keydown.enter.exact="enter" />
    <div class="composer-actions"><span>Enter 发送 · Shift + Enter 换行</span>
      <el-button native-type="submit" type="primary" :loading="busy" :disabled="!value.trim()">发送</el-button>
    </div>
  </form>
</template>
<script setup lang="ts">
const value = defineModel<string>({ default: '' });
const props = defineProps<{ busy?: boolean; placeholder?: string }>();
const emit = defineEmits<{ send: [text: string] }>();
function submit() { if (!props.busy && value.value.trim()) emit('send', value.value.trim()); }
function enter(event: Event | KeyboardEvent) { if (!(event as KeyboardEvent).isComposing) { event.preventDefault(); submit(); } }
</script>
<style scoped>
.assistant-composer { margin: 0; padding: 12px; border-top: 1px solid var(--el-border-color-light); background: var(--el-bg-color); }
.composer-actions { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 8px; }
.composer-actions span { color: var(--el-text-color-secondary); font-size: 11px; }
.assistant-composer :deep(textarea) { box-shadow: none; padding: 8px 2px; background: transparent; }
@media (max-width: 420px) { .composer-actions span { max-width: 120px; } }
</style>
