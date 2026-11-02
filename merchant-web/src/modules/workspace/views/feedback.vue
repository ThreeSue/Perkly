<template>
	<ManagementShell title="用户反馈" description="平台统一受理，单次回复后保留处理记录">
		<el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
		<div class="management-toolbar">
			<el-select
				v-model="status"
				clearable
				placeholder="全部状态"
				@change="
					page = 1;
					load();
				"
				><el-option label="待回复" value="PENDING" /><el-option
					label="已回复"
					value="REPLIED" /></el-select
			><el-button :loading="busy" @click="load">刷新</el-button>
		</div>
		<el-table v-loading="busy" :data="rows" empty-text="暂无反馈">
			<el-table-column prop="customerName" label="用户" width="150" /><el-table-column
				prop="content"
				label="反馈内容"
				min-width="240"
				show-overflow-tooltip
			/>
			<el-table-column label="状态" width="100"
				><template #default="{ row }"
					><el-tag :type="row.status === 'PENDING' ? 'warning' : 'success'">{{
						row.status === 'PENDING' ? '待回复' : '已回复'
					}}</el-tag></template
				></el-table-column
			>
			<el-table-column prop="createTime" label="提交时间" width="180" /><el-table-column
				label="操作"
				width="100"
				><template #default="{ row }"
					><el-button link type="primary" @click="open(row.id)"
						>查看处理</el-button
					></template
				></el-table-column
			>
		</el-table>
		<el-pagination
			v-model:current-page="page"
			:page-size="20"
			:total="total"
			layout="total, prev, pager, next"
			@current-change="load"
		/>
		<el-dialog
			v-model="visible"
			title="反馈处理"
			width="min(720px, 94vw)"
			:close-on-click-modal="false"
		>
			<template v-if="detail"
				><p>{{ detail.customerName }} · {{ detail.createTime }}</p>
				<p class="feedback-text">{{ detail.content }}</p>
				<div class="feedback-images">
					<el-image
						v-for="(url, index) in detail.images || []"
						:key="url"
						:src="url"
						:preview-src-list="detail.images"
						:initial-index="index"
						preview-teleported
						fit="cover"
					/>
				</div>
				<template v-if="detail.status === 'REPLIED'"
					><h4>平台回复</h4>
					<p class="feedback-text">{{ detail.reply }}</p>
					<p>{{ detail.repliedAt }}</p></template
				>
				<el-form v-else label-position="top"
					><el-form-item label="回复内容（仅可回复一次）"
						><el-input
							v-model="reply"
							type="textarea"
							:rows="5"
							maxlength="2000"
							show-word-limit /></el-form-item
					><el-button type="primary" :loading="saving" @click="save"
						>确认回复</el-button
					></el-form
				>
			</template>
		</el-dialog>
	</ManagementShell>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { featureApi } from '../feature-api';
import ManagementShell from '../components/management-shell.vue';
const rows = ref<any[]>([]),
	detail = ref<any>(),
	page = ref(1),
	total = ref(0),
	status = ref(''),
	error = ref(''),
	busy = ref(false),
	visible = ref(false),
	saving = ref(false),
	reply = ref('');
async function load() {
	busy.value = true;
	error.value = '';
	try {
		const r = await featureApi(
			`support/feedback/page?pageNumber=${page.value}&pageSize=20${status.value ? '&status=' + status.value : ''}`
		);
		rows.value = r.list;
		total.value = r.total;
	} catch (e: any) {
		rows.value = [];
		error.value = e.message;
	} finally {
		busy.value = false;
	}
}
async function open(id: number) {
	try {
		detail.value = await featureApi('support/feedback/detail?id=' + id);
		reply.value = '';
		visible.value = true;
	} catch (e: any) {
		ElMessage.error(e.message);
	}
}
async function save() {
	if (!reply.value.trim()) return ElMessage.warning('请填写回复内容');
	try {
		await ElMessageBox.confirm('回复后将保留记录，不支持覆盖或多轮回复。', '确认回复');
	} catch {
		return;
	}
	saving.value = true;
	try {
		await featureApi('support/feedback/reply', { id: detail.value.id, content: reply.value });
		visible.value = false;
		ElMessage.success('已回复');
		await load();
	} catch (e: any) {
		ElMessage.error(e.message);
	} finally {
		saving.value = false;
	}
}
onMounted(load);
</script>
<style scoped>
.feedback-text {
	white-space: pre-wrap;
	overflow-wrap: anywhere;
	line-height: 1.8;
}
.feedback-images {
	display: flex;
	flex-wrap: wrap;
	gap: 12px;
	margin: 16px 0;
}
.feedback-images .el-image {
	width: 100px;
	height: 100px;
	border-radius: 8px;
}
.el-pagination {
	margin-top: 16px;
}
</style>
