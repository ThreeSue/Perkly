<template>
	<ManagementShell register title="站内通知" description="查看收到的通知，或在已发送中查看阅读回执">
		<el-alert v-if="incomingDraft" title="AI候选稿已带入，尚未保存或发送" type="info" :closable="false">
			<el-button type="primary" @click="applyIncomingDraft">采用到通知表单</el-button>
			<el-button @click="incomingDraft = undefined">放弃候选稿</el-button>
		</el-alert>
		<div class="management-toolbar">
			<el-radio-group
				v-model="sent"
				@change="
					page = 1;
					load();
				"
			>
				<el-radio-button :value="false">收件箱（未读 {{ notices.unread }}）</el-radio-button>
				<el-radio-button :value="true">已发送</el-radio-button>
			</el-radio-group>
			<el-button :loading="busy" @click="load">刷新</el-button>
			<el-button v-if="canSend" type="primary" @click="compose">发送通知</el-button>
		</div>
		<ManagementState v-if="error" kind="error" title="通知加载失败" :description="error" @retry="load" />
		<el-table v-else class="management-table" :height="registerHeight" v-loading="busy" :data="rows" :empty-text="sent ? '还没有发送过通知' : '收件箱暂无通知'">
			<el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
			<el-table-column label="类型" width="100"><template #default="{ row }"><el-tag :type="row.noticeType === 'STRONG' ? 'warning' : 'info'">{{ row.noticeType === 'STRONG' ? '强提醒' : '轻通知' }}</el-tag></template></el-table-column>
			<el-table-column v-if="!sent" label="确认" width="100"><template #default="{ row }">{{ row.noticeType === 'STRONG' ? (row.confirmedAt ? '已确认' : '待确认') : '无需确认' }}</template></el-table-column>
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
			<el-table-column label="操作" width="110" fixed="right" align="right"
				><template #default="{ row }"
					><el-button link type="primary" @click="open(row)"
						>查看{{ sent ? '回执' : '' }}</el-button
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
		<el-drawer v-model="detailOpen" append-to-body title="通知详情" size="min(680px, 100vw)">
			<template v-if="detail"
				><header class="notice-detail-heading">
					<h3>{{ detail.title }}</h3>
					<p class="notice-meta">{{ detail.senderName }} · {{ time(detail.createTime) }}</p>
					<p>{{ detail.noticeType === 'STRONG' ? '强提醒' : '轻通知' }}<span v-if="!sent && detail.noticeType === 'STRONG'"> · {{ detail.confirmedAt ? '已确认 ' + time(detail.confirmedAt) : '待确认' }}</span></p>
				</header>
				<p class="notice-content">{{ detail.content }}</p>
				<el-button v-if="detail.businessType === 'WALLET_ACTIVITY'" type="primary" @click="$router.push('/my/activities?id=' + detail.businessId)">活动详情与参与设置</el-button>
				<el-table v-if="sent" :data="receipts"
					><el-table-column prop="name" label="接收人" /><el-table-column label="阅读状态"
						><template #default="{ row }">{{
							row.readAt ? time(row.readAt) : '未读'
						}}</template></el-table-column
					><el-table-column v-if="detail.noticeType === 'STRONG'" label="确认状态"><template #default="{ row }">{{ row.confirmedAt ? time(row.confirmedAt) : '待确认' }}</template></el-table-column></el-table
				>
			</template>
			<template #footer><el-button @click="detailOpen = false">关闭</el-button></template>
		</el-drawer>
		<el-drawer
			v-model="editorOpen"
			append-to-body
			class="workspace-editor"
			title="发送下级通知"
			size="min(760px, 100vw)"
			:close-on-click-modal="false"
			:before-close="closeEditor"
		>
			<el-form label-position="top" :disabled="sending">
				<el-form-item label="通知类型">
					<el-radio-group v-model="form.noticeType"><el-radio value="LIGHT">轻通知</el-radio><el-radio value="STRONG">强提醒</el-radio></el-radio-group>
					<p>{{ form.noticeType === 'STRONG' ? '将向所选接收人弹窗提醒，需要点击“我知道了”确认。' : '进入收件箱并显示未读提示，不强制弹窗。' }}</p>
				</el-form-item>
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
				<NoticeWriting :title="form.title" :content="form.content" @adopt="draft => { form.title = draft.title; form.content = draft.content; }" />
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
			<el-space v-if="isPlatform" wrap class="recipient-tools">
				<el-select
					v-model="recipientMerchantId"
					aria-label="商家范围"
					filterable
					style="width: 200px"
					:disabled="sending || loadingRecipients"
					@change="filterRecipients"
				>
					<el-option label="所有商家" :value="0" />
					<el-option v-for="merchant in merchants" :key="merchant.id" :label="merchant.name" :value="merchant.id" />
				</el-select>
				<el-select
					v-model="recipientAccountType"
					aria-label="接收账号类型"
					style="width: 160px"
					:disabled="sending || loadingRecipients"
					@change="filterRecipients"
				>
					<el-option label="全部可通知人员" value="" />
					<el-option label="商家账号" value="MERCHANT" />
					<el-option label="门店员工" value="STORE" />
				</el-select>
				<el-button
					:disabled="sending || loadingRecipients || !!recipientError || !recipientTotal"
					@click="selectAllRecipients"
				>
					全选筛选结果
				</el-button>
				<el-button
					:disabled="sending || loadingRecipients || !form.recipientIds.length"
					@click="form.recipientIds = []"
				>
					清空选择
				</el-button>
				<span>已选 {{ form.recipientIds.length }} / 100 人</span>
			</el-space>
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
								sending || loadingRecipients ||
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
				:disabled="sending || loadingRecipients"
				@current-change="loadRecipients"
			/>
			<template #footer
				><el-button :disabled="sending || loadingRecipients" @click="closeEditor(() => (editorOpen = false))"
					>取消</el-button
				><el-button type="primary" :loading="sending" :disabled="loadingRecipients" @click="send"
					>确认发送</el-button
				></template
			>
		</el-drawer>
	</ManagementShell>
</template>
<script setup lang="ts">
import { ref, computed, onMounted, onActivated, onBeforeUnmount } from 'vue';
import { identity } from '/@/workspace';
import NoticeWriting from '/$/ai/components/notice-writing.vue';
import dayjs from 'dayjs';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useBase } from '/$/base';
import { isPlatform } from '/@/workspace';
import { workspaceApi } from '../api';
import { useNoticeStore } from '../stores/notices';
import ManagementState from '../components/management-state.vue';
import ManagementShell from '../components/management-shell.vue';
import { useRegisterHeight } from '../composables/register-height';
const registerHeight = useRegisterHeight();
const { menu, user } = useBase();
const notices = useNoticeStore();
const canSend = computed(
	() => menu.perms.includes('notice:send') || user.info?.username === 'admin'
);
const sent = ref(false),
	page = ref(1),
	total = ref(0),
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
const form = ref({ requestId: '', title: '', content: '', recipientIds: [] as number[], noticeType: 'LIGHT' });
const incomingDraft = ref<{title:string;content:string}>();
function receiveIncomingDraft() {
	const raw=sessionStorage.getItem('perkly.ai.notice-draft');if(!raw)return;
	sessionStorage.removeItem('perkly.ai.notice-draft');
	try {const draft=JSON.parse(raw);if(draft.accountKey===identity+':'+user.info?.id && typeof draft.title==='string' && typeof draft.content==='string')incomingDraft.value=draft;}catch { /* Ignore invalid local transfer data. */ }
}
function applyIncomingDraft() {
	if(!incomingDraft.value || !canSend.value || sending.value)return;
	if(!editorOpen.value)compose();
	form.value.title=incomingDraft.value.title;form.value.content=incomingDraft.value.content;incomingDraft.value=undefined;
}
onMounted(()=>{receiveIncomingDraft();window.addEventListener('perkly:notice-draft',receiveIncomingDraft);});
onActivated(receiveIncomingDraft);
onBeforeUnmount(()=>window.removeEventListener('perkly:notice-draft',receiveIncomingDraft));
const recipientMerchantId = ref(0);
const recipientAccountType = ref('');
const merchants = ref<{ id: number; name: string }[]>([]);
function recipientUrl(pageNumber: number, pageSize: number) {
	const params = new URLSearchParams({ pageNumber: String(pageNumber), pageSize: String(pageSize) });
	if (recipientMerchantId.value) params.set('merchantId', String(recipientMerchantId.value));
	if (recipientAccountType.value) params.set('accountType', recipientAccountType.value);
	return `notices/recipients?${params}`;
}
const time = (v: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '—');
const label = (r: any) =>
	`${r.name || r.username} · ${r.merchantName}${r.storeName ? ' / ' + r.storeName : ''} · ${r.roleName}`;
let listRevision = 0;
async function load() {
	const round = ++listRevision;
	busy.value = true;
	error.value = '';
	try {
		const [result] = await Promise.all([
			workspaceApi(`notices/page?sent=${sent.value}&pageNumber=${page.value}&pageSize=20`),
			notices.refresh()
		]);
		if (round !== listRevision) return;
		rows.value = result.list;
		total.value = result.total;
	} catch (e: any) {
		if (round !== listRevision) return;
		rows.value = [];
		total.value = 0;
		error.value = e.message || '通知加载失败';
	} finally {
		if (round === listRevision) busy.value = false;
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
	if (loadingRecipients.value) return;
	loadingRecipients.value = true;
	recipientError.value = '';
	try {
		const r = await workspaceApi(recipientUrl(recipientPage.value, 20));
		options.value = r.list;
		recipientTotal.value = r.total;
	} catch (e: any) {
		options.value = [];
		recipientTotal.value = 0;
		recipientError.value = e.message;
	} finally {
		loadingRecipients.value = false;
	}
}
function filterRecipients() {
	if (loadingRecipients.value || sending.value) return;
	recipientPage.value = 1;
	void loadRecipients();
}
async function selectAllRecipients() {
	if (loadingRecipients.value || sending.value) return;
	if (recipientTotal.value > 100) {
		ElMessage.warning('筛选结果超过100人，请缩小范围后再全选');
		return;
	}
	loadingRecipients.value = true;
	try {
		const result = await workspaceApi(recipientUrl(1, 100));
		const ids = new Set([...form.value.recipientIds, ...result.list.map((row: any) => row.id)]);
		if (result.total > 100 || ids.size > 100) {
			ElMessage.warning('合计超过100人，请缩小筛选范围或移除部分已选人员');
			return;
		}
		for (const row of result.list) add(row);
	} catch (e: any) {
		ElMessage.error(e.message || '全选失败，请重试');
	} finally {
		loadingRecipients.value = false;
	}
}
function compose() {
	form.value = { requestId: crypto.randomUUID(), title: '', content: '', recipientIds: [], noticeType: 'LIGHT' };
	selectedOptions.value = [];
	recipientPage.value = 1;
	recipientMerchantId.value = 0;
	recipientAccountType.value = '';
	editorOpen.value = true;
	loadRecipients();
	if (isPlatform) {
		workspaceApi('merchants')
			.then(result => (merchants.value = result))
			.catch(() => ElMessage.error('商家列表加载失败，请重新打开通知窗口'));
	}
}
function add(row: any) {
	if (form.value.recipientIds.includes(row.id) || form.value.recipientIds.length >= 100) return;
	if (!selectedOptions.value.some(r => r.id === row.id)) selectedOptions.value.push(row);
	form.value.recipientIds.push(row.id);
}
async function closeEditor(done: () => void) {
	if (sending.value || loadingRecipients.value) return;
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
	if (sending.value || loadingRecipients.value) return;
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
.notice-detail-heading {
	padding-bottom: 12px;
	border-bottom: 1px solid var(--el-border-color-lighter);
}
.notice-detail-heading h3 {
	margin: 0;
	color: var(--workspace-ink, var(--el-text-color-primary));
	font-size: 20px;
	overflow-wrap: anywhere;
}
.notice-content {
	white-space: pre-wrap;
	overflow-wrap: anywhere;
	line-height: 1.8;
}
.notice-meta {
	color: var(--el-text-color-secondary);
}
.recipient-tools {
	margin-bottom: 12px;
}
.el-pagination {
	margin-top: 16px;
	flex-wrap: wrap;
}
</style>
