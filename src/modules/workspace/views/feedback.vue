<template>
	<ManagementShell register title="用户反馈" description="平台统一受理，单次回复后保留处理记录">
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
		<ManagementState v-if="error" kind="error" title="反馈列表加载失败" :description="error" @retry="load" />
		<el-table v-else class="management-table" :height="registerHeight" v-loading="busy" :data="rows" empty-text="暂无反馈">
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
		<div v-if="!error && total" class="management-pagination">
			<el-pagination
				v-model:current-page="page"
				:page-size="20"
				:total="total"
				layout="total, prev, pager, next"
				@current-change="load"
			/>
		</div>
		<el-drawer
			v-model="visible"
			append-to-body
			destroy-on-close
			@close="detailRevision++"
			title="反馈处理"
			size="min(720px, 100vw)"
			:close-on-click-modal="false"
		>
			<el-skeleton v-if="detailBusy" :rows="5" animated />
			<ManagementState v-else-if="detailError" kind="error" title="反馈详情加载失败" :description="detailError" @retry="open(activeId)" />
			<template v-else-if="detail"
				><header class="feedback-heading">
					<h3>{{ detail.customerName || '未记录用户名' }}</h3>
					<el-tag :type="detail.status === 'REPLIED' ? 'success' : 'warning'">
						{{ detail.status === 'REPLIED' ? '已回复' : '待回复' }}
					</el-tag>
					<time>{{ detail.createTime }}</time>
				</header>
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
					></el-form
				>
			</template>
			<template #footer>
				<el-button :disabled="saving" @click="visible = false">关闭</el-button>
				<el-button v-if="detail?.status === 'PENDING'" type="primary" :disabled="!reply.trim()" :loading="saving" @click="save">确认回复</el-button>
			</template>
		</el-drawer>
	</ManagementShell>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { featureApi } from '../feature-api';
import ManagementState from '../components/management-state.vue';
import ManagementShell from '../components/management-shell.vue';
import { useRegisterHeight } from '../composables/register-height';
const registerHeight = useRegisterHeight();
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
let listRevision = 0;
async function load() {
	const round = ++listRevision;
	busy.value = true;
	error.value = '';
	try {
		const r = await featureApi(
			`support/feedback/page?pageNumber=${page.value}&pageSize=20${status.value ? '&status=' + status.value : ''}`
		);
		if (round !== listRevision) return;
		rows.value = r.list;
		total.value = r.total;
	} catch (e: any) {
		if (round !== listRevision) return;
		rows.value = [];
		total.value = 0;
		error.value = e.message;
	} finally {
		if (round === listRevision) busy.value = false;
	}
}
const detailBusy = ref(false);
const detailError = ref('');
const activeId = ref(0);
let detailRevision = 0;
async function open(id: number) {
	activeId.value = id;
	const round = ++detailRevision;
	visible.value = true;
	detail.value = undefined;
	reply.value = '';
	detailError.value = '';
	detailBusy.value = true;
	try {
		const result = await featureApi('support/feedback/detail?id=' + id);
		if (round === detailRevision) detail.value = result;
	} catch (e: any) {
		if (round === detailRevision) detailError.value = e.message || '请重试。';
	} finally {
		if (round === detailRevision) detailBusy.value = false;
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
.feedback-heading {
	display: grid;
	grid-template-columns: 1fr auto;
	gap: 6px 12px;
	align-items: center;
	padding: 14px;
	background: var(--workspace-neutral);
	border-radius: 8px;
}
.feedback-heading h3 { margin: 0; color: var(--workspace-ink); }
.feedback-heading time { color: var(--el-text-color-secondary); font-size: 12px; }
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
