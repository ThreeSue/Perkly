<template>
	<div class="risk-preprocessing">
		<small v-if="message" class="risk-preprocessing__status" :class="{ 'risk-preprocessing__error': error }" role="status">{{ message }}</small>
		<el-button class="risk-preprocessing__button" type="primary" :loading="starting || running"
			title="分析最近最多 10 条尚无 AI 建议的词库命中记录" @click="start">
			<template #icon><cl-svg class="risk-preprocessing__logo" name="icon-ai" aria-hidden="true" /></template>
			<template #loading><cl-svg class="risk-preprocessing__logo risk-preprocessing__logo--loading" name="icon-ai" aria-hidden="true" /></template>
			{{ task?.status === 'QUEUED' ? '等待预处理' : task?.status === 'RUNNING' ? 'AI 预处理中' : 'AI 预处理' }}
		</el-button>
	</div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { riskApi } from '../api/content-risk';

interface Task {
	id: number;
	status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
	checkedCount: number;
	limitCount: number;
	modelCalls: number;
}

const emit = defineEmits<{ finished: [] }>();
const task = ref<Task>();
const starting = ref(true);
const error = ref('');
const running = computed(() => task.value?.status === 'QUEUED' || task.value?.status === 'RUNNING');
const message = computed(() => {
	if (error.value) return error.value;
	const current = task.value;
	if (current?.status === 'QUEUED') return '任务排队中';
	if (current?.status === 'RUNNING') return `已处理 ${current.checkedCount} / ${current.limitCount} 条`;
	if (current?.status === 'COMPLETED') return `本批完成，已检查 ${current.checkedCount} 条`;
	if (current?.status === 'FAILED') return `已检查 ${current.checkedCount} 条，剩余记录可重新发起预处理`;
	return '';
});

let timer: ReturnType<typeof setTimeout> | undefined;
let mounted = false;

async function refresh() {
	const previous = task.value;
	const pending = running.value;
	try {
		const next = previous && pending
			? (await riskApi<{ task?: Task }>('jobs/status', undefined, { id: previous.id })).task
			: await riskApi<Task | null>('jobs/preprocess');
		if (!mounted) return;
		task.value = next ?? undefined;
		error.value = '';
		if (pending && !running.value) emit('finished');
	} catch {
		if (mounted) error.value = '暂时无法获取 AI 预处理进度';
	} finally {
		if (mounted && running.value) timer = setTimeout(refresh, 3000);
	}
}

async function start() {
	if (starting.value || running.value) return;
	starting.value = true;
	error.value = '';
	try {
		const next = await riskApi<Task>('jobs/preprocess', {});
		if (!mounted) return;
		task.value = next;
		await refresh();
	} catch (failure) {
		if (mounted) error.value = failure instanceof Error ? failure.message
			: (failure as { message?: string })?.message || '无法发起 AI 预处理，请稍后重试';
	} finally {
		starting.value = false;
	}
}

onMounted(async () => {
	mounted = true;
	await refresh();
	starting.value = false;
});
onUnmounted(() => { mounted = false; clearTimeout(timer); });
</script>

<style scoped>
.risk-preprocessing { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 8px 12px; }
.risk-preprocessing__button { height: 36px; padding-inline: 14px; font-weight: 600; }
.risk-preprocessing__button :deep(.el-icon), .risk-preprocessing__logo { width: 18px; height: 18px; font-size: 18px; }
.risk-preprocessing__logo--loading { margin-inline-end: 6px; animation: risk-preprocessing-spin 2s linear infinite; }
.risk-preprocessing__status { color: var(--el-text-color-regular); font-size: 13px; }
.risk-preprocessing__error { color: var(--el-color-danger); }
@keyframes risk-preprocessing-spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .risk-preprocessing__logo--loading { animation: none; } }
</style>
