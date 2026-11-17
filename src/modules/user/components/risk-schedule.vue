<template>
<section v-loading="loading" class="schedule">
 <header><div><h3>定时巡检</h3><p>计划和运行数据每次刷新时从服务端读取。</p></div><el-button :disabled="loading" @click="load">刷新计划</el-button></header>
 <el-alert v-if="error" :title="error" type="error" :closable="false"/>
 <template v-if="saved">
 <el-descriptions :column="2" border><el-descriptions-item label="当前计划">{{ saved.enabled ? '已启用' : '已关闭' }}</el-descriptions-item><el-descriptions-item label="今日调用">{{ saved.usedCalls ?? '—' }} / {{ saved.dailyBudget }}</el-descriptions-item><el-descriptions-item label="当前任务">{{ taskLabel }}</el-descriptions-item><el-descriptions-item label="最近运行">{{ saved.lastRunAt || '尚未运行' }}</el-descriptions-item><el-descriptions-item label="下次运行">{{ saved.enabled ? saved.nextRunAt || '等待调度' : '计划已关闭' }}</el-descriptions-item><el-descriptions-item label="最近结果">{{ saved.lastResult === 'NOT_STARTED' ? '尚未运行' : saved.lastResult || '暂无结果' }}</el-descriptions-item></el-descriptions>
 </template>
</section>
</template>
<script setup lang="ts">
import {computed,ref,onMounted,onUnmounted} from 'vue';
import { riskApi } from '../api/content-risk';

interface Schedule {
	enabled: boolean;
	dailyBudget: number;
	usedCalls?: number;
	activeJobId?: number | string;
	lastRunAt?: string;
	nextRunAt?: string;
	lastResult?: string;
}

const saved = ref<Schedule>();
const taskState = ref('');
const taskLabel = computed(() => {
	if (!saved.value?.activeJobId) return '无当前定时任务';
	const labels: Record<string, string> = { QUEUED: '排队中', RUNNING: '运行中', DONE: '已完成', COMPLETED: '已完成', FAILED: '失败', PARTIAL: '部分完成' };
	return labels[taskState.value] || '状态暂不可用';
});
const loading = ref(false);
const error = ref('');
let seq = 0;

async function load() {
	const request = ++seq;
	loading.value = true;
	error.value = '';
	try {
		const data = await riskApi<Schedule>('schedule');
		if (typeof data?.enabled !== 'boolean') throw new Error('巡检计划返回格式异常');
		if (request !== seq) return;
		saved.value = data;
		taskState.value = '';
		if (data.activeJobId) {
			try {
				const task = await riskApi<{ state?: string }>('jobs/status', undefined, { id: data.activeJobId });
				if (request === seq) taskState.value = task?.state || '';
			} catch {
				if (request === seq) taskState.value = '';
			}
		}
	} catch {
		if (request === seq) {
			saved.value = undefined;
			error.value = '巡检计划加载失败，请刷新重试';
		}
	} finally {
		if (request === seq) loading.value = false;
	}
}
onMounted(load);onUnmounted(()=>seq++);
</script>
<style scoped>
.schedule{padding:16px 0}header{display:flex;align-items:center;justify-content:space-between;gap:16px}h3{font-size:16px;margin:0}p{color:var(--el-text-color-secondary);font-size:13px}
</style>
