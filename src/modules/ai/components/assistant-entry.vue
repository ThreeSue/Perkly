<template>
  <button type="button" class="assistant-entry" :class="{ 'is-active': active }"
    aria-label="AI 助手" :aria-expanded="active" @click="$emit('click')">
    <cl-svg name="icon-ai" aria-hidden="true" />
  </button>
</template>
<script setup lang="ts">
defineProps<{ active?: boolean }>();
defineEmits<{ click: [] }>();
</script>
<style scoped>
.assistant-entry {
  flex: 0 0 38px; width: 38px; height: 38px; padding: 8px; margin-inline-start: auto;
  display: inline-flex; align-items: center; justify-content: center; cursor: pointer;
  color: var(--el-color-primary); background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7); border-radius: 10px;
  transition: background-color 160ms ease, border-color 160ms ease;
}
.assistant-entry :deep(.cl-svg) { width: 22px; height: 22px; animation: ai-attention 4.8s ease-in-out 3; }
.assistant-entry:hover, .assistant-entry.is-active { background: var(--el-color-primary-light-8); border-color: var(--el-color-primary); }
.assistant-entry:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 3px; }
.assistant-entry.is-active :deep(.cl-svg), .assistant-entry:hover :deep(.cl-svg) { animation: none; }
@keyframes ai-attention { 0%, 55%, 100% { transform: scale(1) rotate(0); } 70% { transform: scale(1.09) rotate(-7deg); } 85% { transform: scale(1.04) rotate(5deg); } }
@media (prefers-reduced-motion: reduce) { .assistant-entry, .assistant-entry :deep(.cl-svg) { animation: none; transition: none; } }
</style>
