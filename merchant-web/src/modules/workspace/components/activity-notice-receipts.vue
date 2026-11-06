<template>
	<div v-loading="busy">
		<el-alert v-if="error" :title="error" type="error" :closable="false" />
		<el-button @click="load">刷新发送记录</el-button>
		<el-table :data="events" empty-text="暂无通知事件">
			<el-table-column label="事件"><template #default="{ row }">{{ kinds[row.kind] || row.kind }}</template></el-table-column>
			<el-table-column prop="activityVersion" label="版本" width="70" />
			<el-table-column prop="dueAt" label="计划时间" min-width="170" />
			<el-table-column label="发送状态"><template #default="{ row }">{{ states[row.status] || row.status }}</template></el-table-column>
			<el-table-column prop="gaps" label="缺岗提示" show-overflow-tooltip />
			<el-table-column label="操作" min-width="160">
				<template #default="{ row }">
					<el-button link @click="receipts(row.id)">查看回执</el-button>
					<el-button link @click="retry(row.id)">重试失败</el-button>
				</template>
			</el-table-column>
		</el-table>
		<el-table v-if="selected" :data="deliveries" empty-text="等待到时生成收件任务">
			<el-table-column prop="name" label="接收人" />
			<el-table-column label="投递状态"><template #default="{ row }">{{ states[row.status] || row.status }}</template></el-table-column>
			<el-table-column label="阅读"><template #default="{ row }">{{ row.status === 'SENT' ? row.readAt || '未读' : '尚未投递' }}</template></el-table-column>
			<el-table-column prop="lastError" label="失败原因" show-overflow-tooltip />
		</el-table>
	</div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { featureApi } from '../feature-api';
const props = defineProps<{ id: number }>();
const kinds: Record<string,string> = { PUBLISHED: '发布', REMINDER: '开始前提醒', ENDED: '到期结束', CLOSED: '提前结束', CHANGED: '重要调整', PARTICIPATION: '参与范围调整' };
const states: Record<string,string> = { PENDING: '等待处理', READY: '投递中', FAILED: '等待重试', SENT: '已投递', SKIPPED: '已跳过', DONE: '已完成' };
const events = ref<any[]>([]), deliveries = ref<any[]>([]);
const selected = ref<number>(), busy = ref(false), error = ref('');
async function load() {
	busy.value = true;
	try {
		events.value = await featureApi('wallet/activities/notice-events?id=' + props.id);
		if (selected.value) await receipts(selected.value);
	} catch (e: any) { error.value = e.message; }
	finally { busy.value = false; }
}
async function receipts(id: number) {
	try {
		selected.value = id;
		deliveries.value = await featureApi('wallet/activities/notice-events/receipts?eventId=' + id);
	} catch (e: any) { error.value = e.message; }
}
async function retry(id: number) {
	try {
		await featureApi('wallet/activities/notice-events/retry', { id });
		ElMessage.success('已安排失败任务重试，请稍后刷新');
		await load();
	} catch (e: any) { error.value = e.message; }
}
watch(() => props.id, load, { immediate: true });
</script>
