<template>
	<ManagementShell title="站内通知" description="查看下级通知及接收回执，发送范围以当前授权为准">
		<el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
		<div class="management-toolbar">
			<el-radio-group
				v-model="sent"
				@change="
					page = 1;
					load();
				"
			>
				<el-radio-button :value="false">收件箱（未读 {{ unread }}）</el-radio-button>
				<el-radio-button :value="true">已发送</el-radio-button>
			</el-radio-group>
			<el-button :loading="busy" @click="load">刷新</el-button>
			<el-button v-if="canSend" type="primary" @click="compose">发送通知</el-button>
		</div>
		<el-table v-loading="busy" :data="rows" empty-text="暂无通知">
			<el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
			<el-table-column prop="senderName" label="发送人" width="140" />
			<el-table-column label="发送时间" width="190"
				><template #default="{ row }">{{ time(row.createTime) }}</template></el-table-column
			>
			<el-table-column v-if="!sent" label="状态" width="90"
				><template #default="{ row }"
					><el-tag :type="row.readAt ? 'info' : 'warning'">{{
						row.readAt ? '已读' : '未读'
					}}</el-tag></template
				></el-table-column
			>
			<el-table-column label="操作" width="110"
				><template #default="{ row }"
					><el-button link type="primary" @click="open(row)"
						>查看{{ sent ? '回执' : '' }}</el-button
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
		<el-dialog v-model="detailOpen" title="通知详情" width="min(680px, 94vw)">
			<template v-if="detail"
				><h3>{{ detail.title }}</h3>
				<p class="notice-meta">{{ detail.senderName }} · {{ time(detail.createTime) }}</p>
				<p class="notice-content">{{ detail.content }}</p>
				<el-table v-if="sent" :data="receipts"
					><el-table-column prop="name" label="接收人" /><el-table-column label="阅读状态"
						><template #default="{ row }">{{
							row.readAt ? time(row.readAt) : '未读'
						}}</template></el-table-column
					></el-table
				>
			</template>
		</el-dialog>
		<el-dialog
			v-model="editorOpen"
			title="发送下级通知"
			width="min(760px, 94vw)"
			:close-on-click-modal="false"
			:before-close="closeEditor"
		>
			<el-form label-position="top" :disabled="sending">
				<el-form-item label="标题"
					><el-input v-model="form.title" maxlength="100" show-word-limit
				/></el-form-item>
				<el-form-item label="正文"
					><el-input
						v-model="form.content"
						type="textarea"
						:rows="5"
						maxlength="5000"
						show-word-limit
				/></el-form-item>
				<el-form-item label="具体接收人员（最多100人）">
					<el-select
						v-model="form.recipientIds"
						multiple
						filterable
						:multiple-limit="100"
						placeholder="从下方添加可通知人员"
						style="width: 100%"
						><el-option
							v-for="r in selectedOptions"
							:key="r.id"
							:label="label(r)"
							:value="r.id"
					/></el-select>
				</el-form-item>
			</el-form>
			<el-alert
				v-if="recipientError"
				:title="recipientError"
				type="error"
				:closable="false"
			/>
			<el-table
				v-loading="loadingRecipients"
				:data="options"
				empty-text="当前范围内暂无可通知人员"
			>
				<el-table-column label="账号"
					><template #default="{ row }"
						>{{ row.name || row.username }}（{{ row.username }}）</template
					></el-table-column
				>
				<el-table-column prop="merchantName" label="商家" /><el-table-column
					prop="storeName"
					label="门店"
				/><el-table-column prop="roleName" label="岗位" />
				<el-table-column label="操作" width="80"
					><template #default="{ row }"
						><el-button
							link
							:disabled="
								sending ||
								form.recipientIds.includes(row.id) ||
								form.recipientIds.length >= 100
							"
							@click="add(row)"
							>添加</el-button
						></template
					></el-table-column
				>
			</el-table>
			<el-pagination
				v-model:current-page="recipientPage"
				:page-size="20"
				:total="recipientTotal"
				layout="total, prev, pager, next"
				@current-change="loadRecipients"
			/>
			<template #footer
				><el-button :disabled="sending" @click="closeEditor(() => (editorOpen = false))"
					>取消</el-button
				><el-button type="primary" :loading="sending" @click="send"
					>确认发送</el-button
				></template
			>
		</el-dialog>
	</ManagementShell>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import dayjs from 'dayjs';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useBase } from '/$/base';
import { workspaceApi } from '../api';
import ManagementShell from '../components/management-shell.vue';
const { menu, user } = useBase();
const canSend = computed(
	() => menu.perms.includes('notice:send') || user.info?.username === 'admin'
);
const sent = ref(false),
	page = ref(1),
	total = ref(0),
	unread = ref(0),
	busy = ref(false),
	error = ref('');
const rows = ref<any[]>([]),
	detail = ref<any>(),
	receipts = ref<any[]>([]),
	detailOpen = ref(false);
const editorOpen = ref(false),
	sending = ref(false),
	loadingRecipients = ref(false),
	recipientError = ref('');
const options = ref<any[]>([]),
	selectedOptions = ref<any[]>([]),
	recipientPage = ref(1),
	recipientTotal = ref(0);
const form = ref({ requestId: '', title: '', content: '', recipientIds: [] as number[] });
const time = (v: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '—');
const label = (r: any) =>
	`${r.name || r.username} · ${r.merchantName}${r.storeName ? ' / ' + r.storeName : ''} · ${r.roleName}`;
async function load() {
	busy.value = true;
	error.value = '';
	try {
		const [result, count] = await Promise.all([
			workspaceApi(`notices/page?sent=${sent.value}&pageNumber=${page.value}&pageSize=20`),
			workspaceApi('notices/unread')
		]);
		rows.value = result.list;
		total.value = result.total;
		unread.value = count;
	} catch (e: any) {
		rows.value = [];
		error.value = e.message || '通知加载失败';
	} finally {
		busy.value = false;
	}
}
async function open(row: any) {
	try {
		if (sent.value) {
			receipts.value = await workspaceApi(`notices/receipts?id=${row.id}`);
			detail.value = row;
		} else {
			detail.value = await workspaceApi(`notices/detail?id=${row.id}`);
			await workspaceApi('notices/read', { id: row.id });
			await load();
		}
		detailOpen.value = true;
	} catch (e: any) {
		ElMessage.error(e.message);
	}
}
async function loadRecipients() {
	loadingRecipients.value = true;
	recipientError.value = '';
	try {
		const r = await workspaceApi(
			`notices/recipients?pageNumber=${recipientPage.value}&pageSize=20`
		);
		options.value = r.list;
		recipientTotal.value = r.total;
	} catch (e: any) {
		options.value = [];
		recipientError.value = e.message;
	} finally {
		loadingRecipients.value = false;
	}
}
function compose() {
	form.value = { requestId: crypto.randomUUID(), title: '', content: '', recipientIds: [] };
	selectedOptions.value = [];
	recipientPage.value = 1;
	editorOpen.value = true;
	loadRecipients();
}
function add(row: any) {
	if (!selectedOptions.value.some(r => r.id === row.id)) selectedOptions.value.push(row);
	form.value.recipientIds.push(row.id);
}
async function closeEditor(done: () => void) {
	if (sending.value) return;
	if (form.value.title || form.value.content || form.value.recipientIds.length) {
		try {
			await ElMessageBox.confirm(
				'关闭将丢弃当前草稿。如果刚才请求超时，请先检查已发送列表再重新发送。',
				'关闭通知'
			);
		} catch {
			return;
		}
	}
	done();
}
async function send() {
	if (!form.value.title.trim() || !form.value.content.trim() || !form.value.recipientIds.length)
		return ElMessage.warning('请填写标题、正文并选择具体接收人员');
	sending.value = true;
	try {
		await workspaceApi('notices/send', form.value);
		editorOpen.value = false;
		ElMessage.success('通知已发送');
		sent.value = true;
		page.value = 1;
		await load();
	} catch (e: any) {
		ElMessage.error(`${e.message || '发送失败'}；重试会保留原请求标识`);
	} finally {
		sending.value = false;
	}
}
onMounted(load);
</script>
<style scoped>
.notice-content {
	white-space: pre-wrap;
	overflow-wrap: anywhere;
	line-height: 1.8;
}
.notice-meta {
	color: var(--el-text-color-secondary);
}
.el-pagination {
	margin-top: 16px;
	flex-wrap: wrap;
}
</style>
