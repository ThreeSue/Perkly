<template>
	<ManagementShell
		title="门店客服"
		description="本店工作人员共享会话；消息自动刷新，回复显示实际发送人"
	>
		<el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
		<div class="chat-layout">
			<section class="chat-list">
				<el-button :loading="busy" @click="load">刷新会话</el-button>
				<el-empty v-if="!sessions.length && !busy" description="暂无顾客会话" />
				<button
					v-for="item in sessions"
					:key="item.id"
					:disabled="sending || uploading"
					class="chat-session"
					:class="{ selected: selected?.id === item.id }"
					@click="select(item)"
				>
					<strong>{{ item.customerName }}</strong
					><span>{{ item.storeName }}</span>
				</button>
				<el-pagination
					size="small"
					v-model:current-page="page"
					:page-size="20"
					:total="total"
					layout="prev, pager, next"
					@current-change="load"
				/>
			</section>
			<section class="chat-main">
				<el-empty v-if="!selected" description="请选择顾客会话" />
				<template v-else
					><h3>{{ selected.customerName }} · {{ selected.storeName }}</h3>
					<el-button :disabled="loadingMessages || !messages.length" @click="older"
						>加载更早消息</el-button
					>
					<div ref="messageBox" class="chat-messages">
						<article
							v-for="m in messages"
							:key="m.id"
							class="chat-message"
							:class="{ staff: m.senderKind === 'WORKSPACE' }"
						>
							<small>{{ m.senderName }} · {{ m.createTime }}</small>
							<p>{{ m.content }}</p>
							<div class="chat-images">
								<el-image
									v-for="(url, i) in m.images || []"
									:key="url"
									:src="url"
									:preview-src-list="m.images"
									:initial-index="i"
									preview-teleported
									fit="cover"
								/>
							</div>
						</article>
						<el-empty v-if="!messages.length" description="暂无消息" />
					</div>
					<div class="chat-compose">
						<el-input
							v-model="content"
							aria-label="回复内容"
							type="textarea"
							:rows="3"
							maxlength="2000"
							show-word-limit
							:disabled="sending"
							placeholder="输入回复，或上传图片"
						/>
						<div class="chat-images">
							<div v-for="(url, i) in images" :key="url">
								<el-image :src="url" fit="cover" /><el-button
									link
									:disabled="sending"
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
								:disabled="sending || uploading || images.length >= 9"
								><el-button :loading="uploading">上传图片</el-button></el-upload
							><el-button
								type="primary"
								:loading="sending"
								:disabled="uploading"
								@click="send"
								>发送回复</el-button
							>
						</div>
					</div>
				</template>
			</section>
		</div>
	</ManagementShell>
</template>
<script setup lang="ts">
import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { featureApi } from '../feature-api';
import ManagementShell from '../components/management-shell.vue';
const sessions = ref<any[]>([]),
	selected = ref<any>(),
	messages = ref<any[]>([]),
	page = ref(1),
	total = ref(0),
	busy = ref(false),
	error = ref(''),
	loadingMessages = ref(false),
	content = ref(''),
	images = ref<string[]>([]),
	sending = ref(false),
	uploading = ref(false);
let timer: ReturnType<typeof setInterval> | undefined,
	stopped = false,
	requestId = crypto.randomUUID(),
	generation = 0;
const messageBox = ref<HTMLElement>();
async function load() {
	busy.value = true;
	try {
		const r = await featureApi(`chats/page?pageNumber=${page.value}&pageSize=20`);
		if (stopped) return;
		sessions.value = r.list;
		total.value = r.total;
		error.value = '';
	} catch (e: any) {
		error.value = e.message;
	} finally {
		busy.value = false;
	}
}
async function select(row: any) {
	if (row.id === selected.value?.id) return;
	if (content.value || images.value.length) {
		try {
			await ElMessageBox.confirm('切换会话会丢弃当前未发送内容。', '切换会话');
		} catch {
			return;
		}
	}
	selected.value = row;
	generation++;
	messages.value = [];
	content.value = '';
	images.value = [];
	requestId = crypto.randomUUID();
	await fetchMessages();
}
async function fetchMessages(before = false) {
	if (!selected.value || loadingMessages.value) return;
	loadingMessages.value = true;
	const chatId = selected.value.id,
		round = generation;
	const oldHeight = messageBox.value?.scrollHeight || 0,
		oldTop = messageBox.value?.scrollTop || 0;
	const follow =
		!messages.value.length || oldHeight - oldTop - (messageBox.value?.clientHeight || 0) < 80;
	const cursor = messages.value.length
		? before
			? '&beforeId=' + messages.value[0].id
			: '&afterId=' + messages.value[messages.value.length - 1].id
		: '';
	try {
		const list = await featureApi(`chats/messages?chatId=${chatId}&pageSize=50${cursor}`);
		if (stopped || round !== generation) return;
		const merged = before ? [...list, ...messages.value] : [...messages.value, ...list];
		messages.value = [...new Map(merged.map((m: any) => [m.id, m])).values()] as any[];
		error.value = '';
		await nextTick();
		if (messageBox.value) {
			if (before)
				messageBox.value.scrollTop = messageBox.value.scrollHeight - oldHeight + oldTop;
			else if (follow) messageBox.value.scrollTop = messageBox.value.scrollHeight;
		}
	} catch (e: any) {
		if (round === generation) error.value = e.message;
	} finally {
		loadingMessages.value = false;
	}
}
async function older() {
	await fetchMessages(true);
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
	if (!selected.value || (!content.value.trim() && !images.value.length))
		return ElMessage.warning('请输入文字或上传图片');
	sending.value = true;
	try {
		await featureApi('chats/send', {
			chatId: selected.value.id,
			requestId,
			content: content.value,
			images: images.value
		});
		content.value = '';
		images.value = [];
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
	let tick = 0;
	timer = setInterval(() => {
		if (document.visibilityState === 'visible' && !sending.value) {
			fetchMessages();
			if (++tick % 4 === 0 && !busy.value) load();
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
.chat-layout {
	display: grid;
	grid-template-columns: 260px minmax(0, 1fr);
	gap: 16px;
	min-height: 500px;
}
.chat-list {
	border-right: 1px solid var(--el-border-color-lighter);
	padding-right: 16px;
}
.chat-session {
	display: flex;
	flex-direction: column;
	gap: 8px;
	width: 100%;
	border: 0;
	border-radius: 8px;
	padding: 16px;
	background: var(--el-fill-color-light);
	color: var(--el-text-color-primary);
	text-align: left;
	margin-top: 8px;
	cursor: pointer;
}
.chat-session.selected {
	background: var(--el-color-primary-light-9);
}
.chat-messages {
	height: 340px;
	overflow: auto;
	padding: 12px;
	background: var(--el-fill-color-light);
}
.chat-message {
	border-radius: 8px;
	background: var(--el-bg-color);
	margin: 10px 40px 10px 0;
	padding: 12px;
	overflow-wrap: anywhere;
}
.chat-message.staff {
	margin: 10px 0 10px 40px;
	border: 1px solid var(--el-color-primary-light-7);
}
.chat-message p {
	white-space: pre-wrap;
	line-height: 1.7;
}
.chat-message small {
	color: var(--el-text-color-secondary);
}
.chat-images,
.chat-actions {
	display: flex;
	flex-wrap: wrap;
	gap: 8px;
	margin-top: 10px;
}
.chat-images .el-image {
	width: 90px;
	height: 90px;
	border-radius: 6px;
}
.chat-compose {
	margin-top: 12px;
}
@media (max-width: 800px) {
	.chat-layout {
		grid-template-columns: 1fr;
	}
	.chat-list {
		border-right: 0;
		max-height: 200px;
		overflow: auto;
	}
}
</style>
