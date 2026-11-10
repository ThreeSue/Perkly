<template>
	<WorkbenchLayout
		title="门店客服"
		context="查看顾客咨询并以当前账号回复。"
		class="chat-workbench"
	>
		<template #actions>
			<el-button v-if="identity === 'STORE'" @click="settingsOpen = true"
				>AI 客服设置</el-button
			>
			<el-button class="workbench-refresh" :loading="busy" @click="load">刷新会话</el-button>
		</template>

		<div class="chat-layout">
			<section class="workbench-panel chat-list" aria-labelledby="chat-list-heading">
				<header class="chat-pane-head">
					<h2 id="chat-list-heading">顾客会话</h2>
					<span class="chat-count">{{ total }} 个</span>
				</header>
				<el-select v-model="sessionFilter" aria-label="筛选本页会话状态"
					><el-option label="本页全部会话" value="" /><el-option
						v-for="(label, value) in stateNames"
						:key="value"
						:label="label"
						:value="value"
				/></el-select>
				<p v-if="sessions.length && !visibleSessions.length" class="chat-history">
					本页没有符合条件的会话
				</p>
				<div v-loading="busy && !sessions.length" class="chat-list-body" :aria-busy="busy">
					<ManagementState
						v-if="sessionError"
						kind="error"
						title="会话加载失败"
						:description="sessionError"
						@retry="load"
					/>
					<ManagementState
						v-else-if="!busy && !sessions.length"
						title="暂无顾客会话"
						description="顾客发起咨询后，会话会显示在这里。"
					/>
					<ul v-if="sessions.length" class="chat-sessions">
						<li v-for="item in visibleSessions" :key="item.id">
							<button
								type="button"
								:disabled="sending || uploading || switching || transitioning"
								:aria-pressed="selected?.id === item.id"
								class="chat-session"
								@click="select(item)"
							>
								<span class="chat-avatar" aria-hidden="true">{{
									initial(item.customerName)
								}}</span>
								<span class="chat-session-label">
									<strong>{{ item.customerName || '顾客' }}</strong>
									<span v-if="item.state">{{
										stateNames[item.state] || item.state
									}}</span>
									<span>{{ item.storeName || '门店未记录' }}</span>
								</span>
								<span
									v-if="selected?.id === item.id"
									class="chat-selected-mark"
									aria-hidden="true"
								/>
							</button>
						</li>
					</ul>
				</div>
				<footer v-if="total > pageSize" class="chat-list-footer">
					<el-pagination
						size="small"
						:current-page="page"
						:page-size="pageSize"
						:total="total"
						:pager-count="5"
						:disabled="busy"
						layout="prev, pager, next"
						@current-change="changePage"
					/>
				</footer>
			</section>

			<section class="workbench-panel chat-main" aria-label="顾客消息">
				<ManagementState
					v-if="!selected"
					class="chat-idle"
					:title="sessions.length ? '选择一个会话' : '消息工作区'"
					:description="
						sessions.length
							? '从顾客会话列表打开消息，查看上下文后再回复。'
							: '收到咨询后，可在此查看消息并回复。'
					"
				/>
				<template v-else>
					<header class="chat-pane-head chat-person">
						<span class="chat-avatar" aria-hidden="true">{{
							initial(selected.customerName)
						}}</span>
						<div>
							<h2>{{ selected.customerName || '顾客' }}</h2>
							<p>{{ selected.storeName || '门店未记录' }}</p>
						</div>
						<span class="chat-live">{{ stateLabel }}</span>
						<el-button
							v-if="detail?.canTakeover"
							:loading="transitioning"
							@click="transition('takeover')"
							>接管会话</el-button
						>
						<el-button
							v-if="detail?.canClose"
							:loading="transitioning"
							@click="transition('close')"
							>结束接待</el-button
						>
					</header>
					<div v-if="messages.length" class="chat-history">
						<el-button
							v-if="messages.length && hasOlder"
							size="small"
							:loading="loadingOlder"
							:disabled="loadingMessages"
							@click="fetchMessages(true)"
							>更早消息</el-button
						>
						<span v-else-if="messages.length">已显示最早消息</span>
						<template v-if="messageError && messages.length">
							<span role="status">消息更新失败</span>
							<el-button link type="primary" @click="retryMessages">重试</el-button>
						</template>
					</div>
					<div
						ref="messageBox"
						v-loading="loadingMessages && !messages.length"
						class="chat-messages"
						:aria-busy="loadingMessages && !messages.length"
					>
						<ManagementState
							v-if="messageError && !messages.length"
							kind="error"
							title="消息加载失败"
							:description="messageError"
							@retry="retryMessages"
						/>
						<ManagementState
							v-else-if="!loadingMessages && !messages.length"
							title="暂无消息"
						/>
						<article
							v-for="m in messages"
							:key="m.id"
							class="chat-message"
							:class="{ staff: m.senderKind === 'WORKSPACE' }"
						>
							<small
								>{{
									m.senderKind === 'AI'
										? '门店 AI 助手'
										: m.senderKind === 'SYSTEM'
											? '系统通知'
											: m.senderName || '发送人未记录'
								}}
								<span>{{ m.createTime }}</span></small
							>
							<div class="chat-bubble">
								<ChatMessageCard v-if="m.card" :card="m.card" />
								<p v-if="m.content">{{ m.content }}</p>
								<div v-if="m.images?.length" class="chat-images">
									<el-image
										v-for="(url, i) in m.images"
										:key="i"
										:src="url"
										:preview-src-list="m.images"
										:initial-index="i"
										preview-teleported
										fit="cover"
										alt="会话图片"
									/>
								</div>
							</div>
						</article>
					</div>
					<div v-if="detailError" class="chat-history" role="status">
						{{ detailError }}<el-button link @click="loadDetail">重试</el-button>
					</div>
					<div v-if="detail?.summary" class="chat-history">
						问题摘要：{{ detail.summary }}
					</div>
					<div class="chat-compose">
						<div v-if="detail?.canSuggest" class="chat-actions">
							<el-button :loading="advising" @click="suggest()">建议回复</el-button
							><el-button
								:disabled="!content.trim() || advising"
								@click="suggest(true)"
								>润色草稿</el-button
							>
						</div>
						<div v-if="advice && detail?.canSuggest" class="chat-advice">
							<strong>仅员工可见 · 尚未发送</strong>
							<p>{{ advice }}</p>
							<el-button
								:disabled="sending"
								@click="
									content = advice;
									advice = '';
								"
								>采用到输入框</el-button
							><el-button link @click="advice = ''">放弃建议</el-button>
						</div>
						<p v-if="adviceError" role="status">{{ adviceError }}</p>
						<el-select
							v-if="detail?.canReply"
							v-model="selectedCard"
							value-key="id"
							clearable
							:loading="cardLoading"
							:disabled="sending"
							placeholder="附上本店优惠券（可选）"
							aria-label="选择发送的优惠券"
							@visible-change="v => v && loadCards()"
							><el-option
								v-for="card in cardOptions"
								:key="card.id"
								:label="card.title"
								:value="card"
								:disabled="!card.available || !card.applicable"
						/></el-select>
						<p v-if="cardError" role="status">{{ cardError }}</p>
						<label for="chat-reply">回复顾客</label>
						<el-input
							id="chat-reply"
							v-model="content"
							type="textarea"
							:rows="3"
							maxlength="2000"
							show-word-limit
							resize="none"
							:disabled="sending || !detail?.canReply"
							placeholder="输入回复，或上传图片"
						/>
						<div v-if="images.length" class="chat-images chat-attachments">
							<div v-for="(url, i) in images" :key="i">
								<el-image :src="url" fit="cover" alt="待发送图片" />
								<el-button
									link
									:disabled="sending || !detail?.canReply"
									@click="images.splice(i, 1)"
									>移除</el-button
								>
							</div>
						</div>
						<div class="chat-actions">
							<el-upload
								:http-request="upload"
								:show-file-list="false"
								accept="image/png,image/jpeg,image/gif"
								:disabled="
									sending || uploading || images.length >= 9 || !detail?.canReply
								"
								><el-button :loading="uploading">上传图片</el-button></el-upload
							>
							<el-button
								type="primary"
								:loading="sending"
								:disabled="uploading || !canSend || !detail?.canReply"
								@click="send"
								>发送回复</el-button
							>
						</div>
					</div>
				</template>
			</section>
		</div>
		<ChatSettings v-model="settingsOpen" />
	</WorkbenchLayout>
</template>

<script setup lang="ts">
import { computed, ref, nextTick, onMounted, onBeforeUnmount } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { featureApi } from '../feature-api';
import WorkbenchLayout from '../components/workbench-layout.vue';
import ManagementState from '../components/management-state.vue';
import ChatSettings from '../components/chat-settings.vue';
import ChatMessageCard, { type MessageCard } from '../components/chat-message-card.vue';
import { identity } from '/@/workspace';
const settingsOpen = ref(false);
const advice = ref(''),
	adviceError = ref(''),
	advising = ref(false);
async function suggest(polish = false) {
	if (!selected.value || !detail.value?.canSuggest || advising.value) return;
	const id = selected.value.id;
	advising.value = true;
	adviceError.value = '';
	try {
		const r = await featureApi('chats/suggest', {
			chatId: id,
			instruction: polish ? '润色当前回复，不改变事实' : '根据顾客最新问题提供建议回复',
			previous: polish ? content.value : ''
		});
		if (selected.value?.id === id) advice.value = r.content;
	} catch (e: any) {
		if (selected.value?.id === id)
			adviceError.value = e.message || '暂时无法生成建议，请自行回复';
	} finally {
		advising.value = false;
	}
}

interface ChatSession {
	state?: string;
	id: number;
	customerName?: string;
	storeName?: string;
}
interface ChatMessage {
	card?: MessageCard;
	id: number;
	senderKind: string;
	senderName?: string;
	createTime: string;
	content?: string;
	images?: string[];
}
interface ChatDetail {
	state: 'AI' | 'WAITING' | 'HUMAN' | 'CLOSED';
	version: number;
	canTakeover: boolean;
	canClose: boolean;
	canReply: boolean;
	canSuggest: boolean;
	summary?: string;
}
const detail = ref<ChatDetail>();
const detailError = ref('');
const transitioning = ref(false);
const stateLabel = computed(() =>
	detail.value
		? { AI: 'AI 接待', WAITING: '等待人工', HUMAN: '人工接待', CLOSED: '已结束' }[
				detail.value.state
			]
		: '正在确认接待权限'
);
async function loadDetail() {
	const id = selected.value?.id;
	if (!id) return;
	try {
		const result = await featureApi(`chats/detail?chatId=${id}`);
		if (selected.value?.id === id && !stopped) {
			detail.value = result;
			detailError.value = '';
		}
	} catch (e: any) {
		if (selected.value?.id === id && !stopped) {
			detail.value = undefined;
			detailError.value = e?.message || '无法确认接待权限，暂不可回复';
		}
	}
}
async function transition(action: 'takeover' | 'close') {
	if (!selected.value || !detail.value || transitioning.value) return;
	if (action === 'close') {
		try {
			await ElMessageBox.confirm('结束本轮接待后将保留全部历史消息。', '结束接待');
		} catch {
			return;
		}
	}
	transitioning.value = true;
	try {
		await featureApi(`chats/${action}`, {
			chatId: selected.value.id,
			version: detail.value.version,
			requestId: crypto.randomUUID()
		});
		await loadDetail();
		await fetchMessages();
	} catch (e: any) {
		ElMessage.error(e?.message || '接待状态更新失败');
		await loadDetail();
	} finally {
		transitioning.value = false;
	}
}
const stateNames: Record<string, string> = {
	AI: 'AI 接待',
	WAITING: '等待人工',
	HUMAN: '人工接待',
	CLOSED: '已结束'
};
const sessionFilter = ref('');
const visibleSessions = computed(() =>
	sessions.value.filter(s => !sessionFilter.value || s.state === sessionFilter.value)
);
const cardOptions = ref<MessageCard[]>([]),
	selectedCard = ref<MessageCard>(),
	cardLoading = ref(false),
	cardError = ref('');
async function loadCards() {
	if (!selected.value || !detail.value?.canReply) return;
	const id = selected.value.id;
	cardLoading.value = true;
	cardError.value = '';
	try {
		const r = await featureApi(`chats/template-options?chatId=${id}&keyword=`);
		if (selected.value?.id === id) cardOptions.value = r.list;
	} catch (e: any) {
		if (selected.value?.id === id) cardError.value = e.message || '无法读取本店优惠券';
	} finally {
		cardLoading.value = false;
	}
}
const pageSize = 20;
const messagePageSize = 50;
const sessions = ref<ChatSession[]>([]);
const selected = ref<ChatSession>();
const messages = ref<ChatMessage[]>([]);
const page = ref(1);
const total = ref(0);
const busy = ref(false);
const sessionError = ref('');
const messageError = ref('');
const loadingMessages = ref(false);
const loadingOlder = ref(false);
const hasOlder = ref(false);
const content = ref('');
const images = ref<string[]>([]);
const sending = ref(false);
const uploading = ref(false);
const switching = ref(false);
const canSend = computed(() =>
	Boolean(content.value.trim() || images.value.length || selectedCard.value)
);

let timer: ReturnType<typeof setInterval> | undefined;
let stopped = false;
let requestId = crypto.randomUUID();
let generation = 0;
let failedOlder = false;
const messageBox = ref<HTMLElement>();

function initial(name?: string) {
	return Array.from(name?.trim() || '客')[0];
}
async function load() {
	if (busy.value) return;
	busy.value = true;
	sessionError.value = '';
	try {
		const result = await featureApi(`chats/page?pageNumber=${page.value}&pageSize=${pageSize}`);
		if (stopped) return;
		sessions.value = result.list;
		total.value = result.total;
	} catch (error: any) {
		if (!stopped) sessionError.value = error?.message || '暂时无法读取顾客会话';
	} finally {
		busy.value = false;
	}
}
function changePage(value: number) {
	if (busy.value || page.value === value) return;
	sessions.value = [];
	page.value = value;
	void load();
}
async function select(row: ChatSession) {
	if (
		row.id === selected.value?.id ||
		sending.value ||
		uploading.value ||
		switching.value ||
		transitioning.value
	)
		return;
	if (content.value || images.value.length || selectedCard.value) {
		switching.value = true;
		try {
			await ElMessageBox.confirm('切换会话会丢弃当前未发送内容。', '切换会话');
		} catch {
			return;
		} finally {
			switching.value = false;
		}
	}
	if (stopped) return;
	selected.value = row;
	advice.value = '';
	adviceError.value = '';
	detail.value = undefined;
	detailError.value = '';
	void loadDetail();
	generation++;
	failedOlder = false;
	messages.value = [];
	messageError.value = '';
	loadingMessages.value = false;
	hasOlder.value = false;
	content.value = '';
	images.value = [];
	selectedCard.value = undefined;
	cardOptions.value = [];
	cardError.value = '';
	requestId = crypto.randomUUID();
	await fetchMessages();
}
async function fetchMessages(before = false) {
	if (!selected.value || loadingMessages.value) return;
	loadingMessages.value = true;
	loadingOlder.value = before;
	const { id } = selected.value;
	const round = generation;
	const oldHeight = messageBox.value?.scrollHeight || 0;
	const oldTop = messageBox.value?.scrollTop || 0;
	const initialLoad = messages.value.length === 0;
	const follow = initialLoad || oldHeight - oldTop - (messageBox.value?.clientHeight || 0) < 80;
	const params = new URLSearchParams({ chatId: String(id), pageSize: String(messagePageSize) });
	if (!initialLoad) {
		const cursor = before ? messages.value[0].id : messages.value[messages.value.length - 1].id;
		params.set(before ? 'beforeId' : 'afterId', String(cursor));
	}
	try {
		const list: ChatMessage[] = await featureApi(`chats/messages?${params}`);
		if (stopped || round !== generation) return;
		if (before || initialLoad) hasOlder.value = list.length >= messagePageSize;
		if (!failedOlder || before) {
			messageError.value = '';
			failedOlder = false;
		}
		if (!list.length) return;
		const merged = before ? [...list, ...messages.value] : [...messages.value, ...list];
		messages.value = [...new Map(merged.map(message => [message.id, message])).values()];
		await nextTick();
		const box = messageBox.value;
		if (round !== generation || !box) return;
		if (before) box.scrollTop = box.scrollHeight - oldHeight + oldTop;
		else if (follow) box.scrollTop = box.scrollHeight;
	} catch (error: any) {
		if (stopped || round !== generation) return;
		failedOlder = before;
		messageError.value = error?.message || '暂时无法读取消息';
	} finally {
		if (round === generation) {
			loadingMessages.value = false;
			loadingOlder.value = false;
		}
	}
}
function retryMessages() {
	void fetchMessages(failedOlder);
}
async function upload(options: any) {
	if (options.file.size > 5 * 1024 * 1024) {
		ElMessage.warning('图片不能超过5MB');
		return;
	}
	uploading.value = true;
	const round = generation;
	const data = new FormData();
	data.append('file', options.file);
	try {
		const url = await featureApi('chats/images/upload', data);
		if (round === generation) images.value.push(url);
		options.onSuccess?.(url);
	} catch (e: any) {
		ElMessage.error(e.message);
		options.onError?.(e);
	} finally {
		uploading.value = false;
	}
}
async function send() {
	if (
		!selected.value ||
		sending.value ||
		uploading.value ||
		switching.value ||
		transitioning.value
	)
		return;
	if (!detail.value?.canReply) return ElMessage.warning('请先接管会话并确认回复权限');
	if (!canSend.value) return ElMessage.warning('请输入文字或上传图片');
	sending.value = true;
	try {
		await featureApi('chats/send', {
			chatId: selected.value.id,
			requestId,
			content: content.value,
			images: images.value,
			card: selectedCard.value
				? { type: selectedCard.value.type, id: selectedCard.value.id }
				: undefined
		});
		content.value = '';
		images.value = [];
		selectedCard.value = undefined;
		cardOptions.value = [];
		cardError.value = '';
		requestId = crypto.randomUUID();
		await fetchMessages();
	} catch (e: any) {
		ElMessage.error((e.message || '发送失败') + '，重试保留原请求标识');
	} finally {
		sending.value = false;
	}
}
onMounted(async () => {
	await load();
	if (stopped) return;
	let tick = 0;
	timer = setInterval(() => {
		if (document.visibilityState === 'visible' && !sending.value) {
			fetchMessages();
			void loadDetail();
			if (++tick % 4 === 0 && !sessionError.value) load();
		}
	}, 3000);
});
onBeforeUnmount(() => {
	stopped = true;
	generation++;
	if (timer) clearInterval(timer);
});
</script>

<style scoped>
.chat-advice {
	padding: 12px;
	background: var(--workspace-accent-soft);
	border-radius: 8px;
	font-size: 13px;
	max-height: 200px;
	overflow: auto;
}
.chat-advice p {
	white-space: pre-wrap;
	line-height: 1.65;
}
.chat-workbench {
	display: flex;
	flex-direction: column;
	gap: 16px;
}
.chat-workbench :deep(.workbench-banner) {
	flex: none;
}
.chat-layout {
	display: grid;
	flex: 1;
	grid-template-columns: minmax(224px, 280px) minmax(0, 1fr);
	gap: 16px;
	min-height: 470px;
}
.chat-list,
.chat-main {
	display: flex;
	flex-direction: column;
	min-height: 0;
	overflow: hidden;
}
.chat-pane-head {
	display: flex;
	align-items: center;
	gap: 12px;
	min-height: 58px;
	padding: 10px 16px;
	border-block-end: 1px solid var(--el-border-color-lighter);
}
.chat-pane-head h2 {
	margin: 0;
	font-size: 16px;
	line-height: 1.4;
	overflow-wrap: anywhere;
}
.chat-pane-head p {
	margin: 3px 0 0;
	font-size: 12px;
	color: var(--el-text-color-regular);
}
.chat-count,
.chat-live {
	margin-inline-start: auto;
	color: var(--el-text-color-regular);
	font-size: 12px;
	white-space: nowrap;
}
.chat-list-body,
.chat-messages {
	flex: 1;
	min-height: 0;
	overflow: auto;
}
.chat-sessions {
	display: grid;
	gap: 4px;
	padding: 8px;
	margin: 0;
	list-style: none;
}
.chat-session {
	display: grid;
	grid-template-columns: 36px minmax(0, 1fr) 8px;
	align-items: center;
	gap: 10px;
	width: 100%;
	padding: 8px;
	border: 1px solid transparent;
	border-radius: 7px;
	background: transparent;
	color: var(--el-text-color-primary);
	text-align: start;
	cursor: pointer;
}
.chat-session:hover {
	background: var(--workspace-neutral);
}
.chat-session:focus-visible {
	outline: 2px solid var(--workspace-accent-text);
	outline-offset: -2px;
}
.chat-session:disabled {
	cursor: not-allowed;
	opacity: 0.65;
}
.chat-session[aria-pressed='true'] {
	border-color: var(--workspace-accent-border, var(--el-color-primary-light-7));
	background: var(--workspace-accent-soft);
}
.chat-session[aria-pressed='true'] strong {
	color: var(--workspace-accent-text);
}
.chat-selected-mark {
	width: 6px;
	height: 6px;
	border-radius: 50%;
	background: var(--workspace-accent-text);
}
.chat-avatar {
	display: grid;
	place-items: center;
	width: 36px;
	height: 36px;
	border-radius: 6px;
	background: var(--workspace-neutral);
	color: var(--el-text-color-regular);
	font-size: 14px;
	font-weight: 700;
}
.chat-person .chat-avatar,
.chat-session[aria-pressed='true'] .chat-avatar {
	background: var(--workspace-accent);
	color: white;
}
.chat-session-label {
	display: grid;
	gap: 3px;
	min-width: 0;
	font-size: 12px;
	color: var(--el-text-color-regular);
	overflow-wrap: anywhere;
}
.chat-session-label strong {
	font-size: 14px;
	color: var(--el-text-color-primary);
}
.chat-list-footer {
	display: flex;
	justify-content: center;
	padding: 8px;
	border-block-start: 1px solid var(--el-border-color-lighter);
}
.chat-idle {
	flex: 1;
}
.chat-live {
	display: flex;
	align-items: center;
	gap: 5px;
}
.chat-live i {
	width: 6px;
	height: 6px;
	border-radius: 50%;
	background: var(--workspace-accent-text);
}
.chat-history {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 8px;
	padding: 6px 10px;
	background: var(--workspace-neutral);
	color: var(--el-text-color-regular);
	font-size: 12px;
}
.chat-history:empty {
	display: none;
}
.chat-messages {
	min-height: 150px;
	padding: 12px 18px;
	background: var(--workspace-neutral);
}
.chat-message {
	display: grid;
	justify-items: start;
	gap: 4px;
	margin-bottom: 16px;
	overflow-wrap: anywhere;
}
.chat-message.staff {
	justify-items: end;
}
.chat-message small {
	color: var(--el-text-color-regular);
}
.chat-message small span {
	margin-inline-start: 6px;
}
.chat-bubble {
	box-sizing: border-box;
	max-width: min(88%, 62ch);
	padding: 8px 12px;
	border: 1px solid var(--el-border-color-lighter);
	border-radius: 8px;
	background: var(--el-bg-color);
}
.chat-message.staff .chat-bubble {
	background: var(--workspace-accent-soft);
}
.chat-bubble p {
	margin: 0;
	line-height: 1.65;
	white-space: pre-wrap;
}
.chat-images,
.chat-actions {
	display: flex;
	flex-wrap: wrap;
	gap: 8px;
}
.chat-bubble p + .chat-images {
	margin-block-start: 8px;
}
.chat-images .el-image {
	display: block;
	width: 76px;
	height: 76px;
	border-radius: 6px;
}
.chat-compose {
	display: grid;
	gap: 8px;
	padding: 12px 16px;
	border-block-start: 1px solid var(--el-border-color-lighter);
}
.chat-compose label {
	font-size: 13px;
	font-weight: 650;
}
.chat-attachments {
	max-height: 120px;
	overflow: auto;
}
.chat-actions {
	justify-content: space-between;
}
@media (max-width: 800px) {
	.chat-layout {
		grid-template-columns: minmax(0, 1fr);
		grid-template-rows: auto minmax(400px, 1fr);
	}
	.chat-list-body {
		max-height: 170px;
	}
	.chat-messages {
		padding-inline: 10px;
	}
}
</style>
