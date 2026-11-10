<template>
	<div v-if="compact" class="cover-layout">
	<section class="compact-cover">
		<div class="cover-stage">
			<div v-if="working" class="generating-cover" role="status">
				<div><cl-svg name="icon-ai" aria-hidden="true" />正在生成封面…</div>
			</div>
			<img v-else-if="candidate" :src="candidate.url" alt="AI 生成的候选封面" />
			<el-image v-else-if="cover" :src="cover" :preview-src-list="[cover]"
				preview-teleported fit="cover" alt="当前优惠券封面">
				<template #error><span>封面加载失败</span></template>
			</el-image>
			<span v-else>暂无封面</span>
		</div>
		<div class="cover-controls">
		<div class="cover-ai-heading">
			<span class="cover-ai-mark"><cl-svg name="icon-ai" aria-hidden="true" /></span>
			<div>
				<strong>AI 封面设计</strong>
				<p class="cover-note">根据当前优惠券内容生成封面。</p>
			</div>
		</div>
		<div class="cover-primary">
		<el-button type="primary" :plain="!!candidate" :disabled="!allowed || !draftReady || disabled || working || !!pending"
			:loading="preparing" @click="generateCover">
			{{ working ? '生成中' : candidate ? '重新生成' : 'AI 生成封面' }}
		</el-button>
		<el-button v-if="candidate && !working" type="primary" :disabled="disabled" @click="select(candidate)">选用封面</el-button>
		</div>
		<p v-if="candidate && !working" class="cover-note">选用后更新左侧券面预览。</p>
		<el-button v-if="allowed" link :disabled="disabled || preparing" @click="open()">生成设置与历史</el-button>
		<small v-if="!allowed">当前账号没有 AI 封面生成权限</small>
		<small v-else-if="!draftReady">请先填写券名称、券类型及适用范围。</small>
		<el-button v-if="pending" :loading="submitting" @click="resolvePending">核对上次提交</el-button>
		<details v-if="allowed">
			<summary @click="loadDescription">调整生成描述</summary>
			<el-input v-model="prompt" type="textarea" :rows="3" maxlength="4000" :disabled="working" />
			<el-button link :loading="preparing" @click="prepare">按当前券内容恢复描述</el-button>
		</details>
		<p v-if="error || (!working && activeTask?.errorMessage)" class="failure" role="alert">{{ error || activeTask?.errorMessage }}</p>
		<el-button v-if="activeTask?.status === 'STORAGE_FAILED'" link :loading="submitting"
			@click="retryStorage(activeTask)">重试保存已有图片</el-button>
		<el-button v-if="activeTask && isProcessing(activeTask.status) && error" link @click="refresh()">重新查询进度</el-button>
		<div class="cover-manual">
			<span>手动选图</span>
			<slot />
		</div>
		</div>
	</section>
	</div>
	<template v-else>
	<el-button v-if="allowed" :disabled="disabled || !draftReady" @click="open()"><cl-svg name="icon-ai" />AI生成封面</el-button>
	<el-button v-else disabled title="需独立AI图片权限及本次优惠券编辑权限"><cl-svg name="icon-ai" />AI生成封面</el-button>
	</template>
	<el-dialog v-model="visible" title="AI优惠券封面" width="min(1000px, 96vw)" append-to-body :close-on-click-modal="false">
		<el-alert title="一次生成一张正方形图片，所有成功图片自动存入本商家素材库。只有点击“选用封面”才回填，保存优惠券后生效。" type="info" :closable="false" />
		<el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
		<div v-if="quota" class="quota">{{ merchantName || '当前商家' }} · {{ quota.day }}：已受理 {{ quota.used }} / {{ quota.limit }} 次，剩余 {{ quota.remaining }} 次。失败或结果未知的已受理请求也计入额度。</div>
		<el-alert v-if="quota && (!quota.enabled || !quota.keyConfigured)" :title="!quota.enabled ? 'AI生成已暂停；仍可查看和选用历史图片' : '服务密钥未配置；请联系运营'" type="warning" :closable="false" />
		<el-alert v-if="pending" title="上次提交尚未确认。先核对原请求，避免重复生成和扣除额度。" type="warning" :closable="false">
			<el-button :loading="submitting" @click="resolvePending">核对上次提交</el-button>
		</el-alert>
		<el-form label-position="top" class="prompt-form" @submit.prevent>
			<el-form-item label="图片提示词（可编辑）">
				<el-input v-model="prompt" type="textarea" :rows="4" maxlength="4000" show-word-limit placeholder="根据当前券表单自动填写，可按需调整" />
			</el-form-item>
			<div class="actions">
				<el-button :loading="preparing" :disabled="submitting" @click="prepare">重新读取当前表单</el-button>
				<el-select v-model="model" aria-label="图片模型" style="width: 260px">
					<el-option v-for="item in quota?.models || []" :key="item" :label="item === 'qwen-image-2.1-turbo' ? '快速 · ' + item : item" :value="item" />
				</el-select>
				<el-button type="primary" :disabled="!canGenerate" :loading="submitting" @click="generate('GENERATE')">{{ parent ? '按提示词重新生成' : '生成一张' }}</el-button>
				<el-button v-if="parent" type="success" :disabled="!canGenerate" @click="generate('EDIT')">基于所选图完善</el-button>
				<el-tag v-if="parent" closable @close="parent = undefined">已选参考图 · {{ parent.createTime }}</el-tag>
			</div>
			<p v-if="activeTask && isProcessing(activeTask.status)" role="status">{{ statusNames[activeTask.status] }}；可以关闭窗口，稍后从历史查看结果。</p>
		</el-form>
		<div class="history-title"><strong>本商家生成历史</strong><el-button :loading="loading" @click="refresh()">刷新历史与额度</el-button></div>
		<el-empty v-if="!rows.length && !loading" description="暂无生成记录" />
		<div v-loading="loading" class="history">
			<article v-for="item in rows" :key="item.id" class="candidate" :class="{ chosen: parent?.id === item.id }">
				<el-image v-if="item.url" :src="item.url" :preview-src-list="[item.url]" preview-teleported fit="contain" :alt="item.mode === 'EDIT' ? '基于原图完善的封面' : '生成的封面'" />
				<div v-else class="no-image">{{ statusNames[item.status] || item.status }}</div>
				<div class="candidate-info"><el-tag :type="item.status === 'STORED' ? 'success' : item.status === 'UNKNOWN' || item.status === 'FAILED' ? 'danger' : 'info'">{{ statusNames[item.status] || item.status }}</el-tag>
					<span>{{ item.mode === 'EDIT' ? '原图完善版本' : '重新生成' }}{{ item.archived ? ' · 已从素材库归档，选用会恢复' : '' }}</span>
					<small>{{ item.createTime }} · {{ item.actorName }}</small>
					<p v-if="item.errorMessage" class="failure">{{ item.errorMessage }}</p>
					<details><summary>提示词及版本</summary><p class="saved-prompt">{{ item.prompt }}</p><p v-if="item.parentTaskId">保留原图和当前版本；由所选原图继续完善。</p></details>
					<div class="actions">
						<el-button size="small" @click="prompt = item.prompt">复用提示词</el-button>
						<template v-if="item.status === 'STORED' && item.url">
							<el-button size="small" @click="chooseParent(item)">以此图继续完善</el-button>
							<el-button size="small" type="primary" :disabled="submitting || disabled" @click="select(item)">选用封面</el-button>
						</template>
						<el-button v-if="item.status === 'STORAGE_FAILED'" size="small" :loading="submitting" @click="retryStorage(item)">重试保存已有图片</el-button>
					</div>
				</div>
			</article>
		</div>
		<el-pagination v-if="total > 12" v-model:current-page="page" :page-size="12" :total="total" layout="total, prev, pager, next" @current-change="refresh()" />
		<template #footer><el-button @click="visible = false">关闭，不更换封面</el-button></template>
	</el-dialog>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useBase } from '/$/base';
import { isPlatform, identity } from '/@/workspace';
import { coverDraft, imageApi, isProcessing, statusNames, type ImageCreate, type ImageTask, type ImageQuota } from '../api';

const props = defineProps<{ draft: Record<string, any>; disabled?: boolean; compact?: boolean; cover?: string | null }>();
const emit = defineEmits<{ select: [value: { id: number; url: string; name: string }] }>();
const { user, menu } = useBase();
const draft = () => coverDraft(props.draft, (user.info as any)?.merchantId);
const merchantId = computed(() => draft().merchantId);
const draftReady = computed(() => {
	const value = draft();
	return !!(value.merchantId && value.name.trim() && value.type && (value.scopeType !== 1 || value.storeIds.length));
});
const allowed = computed(() => {
	const has = (p: string) => isPlatform && user.info?.username === 'admin' || menu.perms.includes(p);
	return has('ai:image:generate') && (isPlatform ? has(props.draft.id ? 'coupon:template:update' : 'coupon:template:add') : has(props.draft.id ? 'coupon:update' : 'coupon:create') && has('material:view'));
});
const visible = ref(false), loading = ref(false), preparing = ref(false), submitting = ref(false);
const prompt = ref(''), model = ref(''), error = ref(''), merchantName = ref('');
const rows = ref<ImageTask[]>([]), quota = ref<ImageQuota>(), parent = ref<ImageTask>();
const pending = ref<ImageCreate>(), page = ref(1), total = ref(0), activeId = ref<number>();
const activeTask = computed(() => rows.value.find(t => t.id === activeId.value));
const working = computed(() => submitting.value || isProcessing(activeTask.value?.status || ''));
const candidate = computed(() => {
	const task = activeTask.value;
	return task?.status === 'STORED' && task.url && task.url !== props.cover ? task : undefined;
});
function loadDescription() {
	if (!prompt.value && !preparing.value && !working.value) void prepare();
}
async function generateCover() {
	if (working.value || preparing.value || props.disabled || !allowed.value) return;
	error.value = '';
	if (!visible.value) {
		const description = prompt.value;
		await open(false);
		if (description) prompt.value = description;
	} else {
		await refresh(true);
	}
	if (!error.value && canGenerate.value) await generate('GENERATE');
	else if (!error.value && !pending.value) {
		if (quota.value && !quota.value.enabled) error.value = 'AI 生成已暂停';
		else if (quota.value && !quota.value.keyConfigured) error.value = '生成服务未配置，请联系运营';
		else if (quota.value?.remaining === 0) error.value = '今日生成额度已用完';
		else if (prompt.value.trim().length < 5) error.value = '生成描述未就绪，请重试';
		else error.value = '暂时无法生成，请稍后重试';
	}
}
const canGenerate = computed(() => allowed.value && !!quota.value?.enabled && !!quota.value?.keyConfigured && quota.value.remaining > 0 && !!model.value && prompt.value.trim().length >= 5 && !submitting.value && !preparing.value && !pending.value && !isProcessing(activeTask.value?.status || ''));
const pendingKey = () => `ai-cover-pending:${identity}:${user.info?.id}:${merchantId.value}`;
let timer: ReturnType<typeof setTimeout> | undefined;
let revision = 0;
function stopPolling() { clearTimeout(timer); timer = undefined; revision++; }
function remember(input?: ImageCreate) {
	pending.value = input;
	if (input) sessionStorage.setItem(pendingKey(), JSON.stringify(input));
	else sessionStorage.removeItem(pendingKey());
}
async function open(showDialog = true) {
	if (!allowed.value || props.disabled) return;
	visible.value = showDialog; parent.value = undefined; error.value = ''; page.value = 1;
	try { pending.value = JSON.parse(sessionStorage.getItem(pendingKey()) || 'null') || undefined; } catch { pending.value = undefined; }
	await Promise.all([prompt.value ? Promise.resolve() : prepare(), refresh()]);
}
async function prepare() {
	preparing.value = true;
	try { const value = await imageApi.prompt(draft()); prompt.value = value.prompt; merchantName.value = value.merchantName; error.value = ''; }
	catch (cause: any) { error.value = cause?.message || '无法读取当前券表单，请重试'; }
	finally { preparing.value = false; }
}
async function refresh(background = false) {
	clearTimeout(timer);
	timer = undefined;
	const owner = merchantId.value, version = ++revision;
	if (!owner) { error.value = '请先选择优惠券所属商家'; rows.value = []; quota.value = undefined; return; }
	if (!background) loading.value = true;
	let loaded = false;
	try {
		const [history, budget] = await Promise.all([imageApi.page(owner, page.value), imageApi.quota(owner)]);
		if (version !== revision || owner !== merchantId.value) return;
		rows.value = history.list; total.value = history.total; quota.value = budget;
		if (!budget.models.includes(model.value)) model.value = budget.models[0] || '';
		const accepted = pending.value && history.list.find(t => t.requestId === pending.value?.requestId);
		if (accepted) { activeId.value = accepted.id; remember(); }
		loaded = true;
	} catch (cause: any) { if (version === revision) error.value = cause?.message || '生成历史加载失败'; }
	finally {
		if (version === revision) {
			loading.value = false;
			if ((visible.value || props.compact) && loaded && (pending.value || (props.compact ? isProcessing(activeTask.value?.status || '') : rows.value.some(t => isProcessing(t.status)))))
				timer = setTimeout(() => refresh(true), 3000);
		}
	}
}
async function submit(input: ImageCreate) {
	submitting.value = true; error.value = '';
	try {
		const task = await imageApi.create(input);
		remember(); activeId.value = task.id; page.value = 1;
		await refresh();
	} catch (cause: any) {
		const rejected = cause?.business || [400, 401, 403, 404, 409, 429].includes(cause?.code);
		if (rejected) remember();
		error.value = (cause?.message || '提交未确认') + (rejected ? '' : '；请核对上次提交后再发起新请求。');
	}
	finally { submitting.value = false; }
}
async function generate(mode: 'GENERATE' | 'EDIT') {
	if (!canGenerate.value || mode === 'EDIT' && !parent.value) return;
	const input: ImageCreate = { requestId: crypto.randomUUID(), mode, model: model.value, prompt: prompt.value.trim(), draft: draft(), ...(mode === 'EDIT' ? { parentTaskId: parent.value!.id } : {}) };
	remember(input); await submit(input);
}
async function resolvePending() { if (pending.value && !submitting.value) await submit(pending.value); }
function chooseParent(item: ImageTask) { parent.value = item; prompt.value = item.prompt; }
async function select(item: ImageTask) {
	if (props.disabled || !allowed.value || submitting.value || merchantId.value !== item.merchantId) return;
	submitting.value = true;
	try {
		const selected = await imageApi.select(item.merchantId, draft().couponId, item.id);
		if (!selected.url || !selected.materialId || selected.merchantId !== merchantId.value) throw new Error('选用结果无效，请刷新历史');
		emit('select', { id: selected.materialId, url: selected.url, name: 'AI优惠券封面' });
		if (props.compact) activeId.value = undefined;
		visible.value = false;
	} catch (cause: any) { error.value = cause?.message || '选用失败，原封面已保留'; }
	finally { submitting.value = false; }
}
async function retryStorage(item: ImageTask) {
	if (submitting.value) return;
	submitting.value = true;
	try { await imageApi.retryStorage(item.merchantId, draft().couponId, item.id); await refresh(); }
	catch (cause: any) { error.value = cause?.message || '素材保存失败，请稍后重试'; }
	finally { submitting.value = false; }
}
watch(merchantId, () => { visible.value = false; prompt.value = ''; rows.value = []; quota.value = undefined; parent.value = undefined; activeId.value = undefined; stopPolling(); });
watch(visible, value => { if (!value && !props.compact) { stopPolling(); loading.value = false; } });
onBeforeUnmount(stopPolling);
</script>
<style scoped>
.cover-layout { container: coupon-cover / inline-size; width: 100%; min-width: 0; }
.compact-cover {
	width: 100%;
	display: grid;
	grid-template-columns: minmax(120px, 192px) minmax(0, 1fr);
	align-items: start;
	gap: 20px;
	padding: 14px;
	box-sizing: border-box;
	border: 1px solid var(--el-border-color-lighter);
	border-radius: 14px;
}
.cover-stage { aspect-ratio: 1; display: grid; place-items: center; overflow: hidden; border-radius: 12px; background: var(--el-fill-color-light); color: var(--el-text-color-secondary); }
.cover-stage > :is(img, .el-image, .generating-cover) { width: 100%; height: 100%; object-fit: cover; }
.cover-controls { display: grid; gap: 10px; justify-items: start; min-width: 0; line-height: 1.5; }
.cover-ai-heading { display: flex; align-items: center; gap: 10px; min-width: 0; color: var(--el-text-color-primary); }
.cover-ai-heading > div { min-width: 0; overflow-wrap: anywhere; }
.cover-ai-mark {
	display: grid;
	place-items: center;
	flex: 0 0 36px;
	height: 36px;
	border-radius: 9px;
	background: var(--el-color-primary-light-9);
	color: var(--el-color-primary);
}
.cover-ai-mark :deep(svg) { width: 20px; height: 20px; }
.cover-manual { width: 100%; display: grid; gap: 6px; padding-top: 10px; border-top: 1px solid var(--el-border-color-lighter); }
.cover-manual > span { font-size: 12px; color: var(--el-text-color-regular); }
.generating-cover > div { display: grid; justify-items: center; gap: 8px; }
.generating-cover :deep(svg) { width: 24px; height: 24px; color: var(--el-color-primary); }
.cover-primary { display: flex; flex-wrap: wrap; gap: 8px; }
.cover-primary .el-button + .el-button { margin-left: 0; }
.cover-note { margin: 0; font-size: 12px; color: var(--el-text-color-secondary); }
@container coupon-cover (max-width: 480px) {
	.compact-cover { grid-template-columns: minmax(0, 1fr); gap: 16px; }
	.cover-stage { width: min(100%, 160px); justify-self: center; }
	.cover-controls { width: 100%; justify-items: stretch; overflow-wrap: anywhere; }
	.cover-primary { display: grid; grid-template-columns: minmax(0, 1fr); }
	.cover-controls :deep(.el-button) { max-width: 100%; height: auto; min-height: 32px; white-space: normal; }
	.cover-controls :deep(.el-button > span) { white-space: normal; overflow-wrap: anywhere; }
}
.compact-cover details { width: 100%; }
.compact-cover summary { cursor: pointer; color: var(--el-text-color-secondary); margin-bottom: 8px; }
.generating-cover {
	display: grid; place-items: center;
	background: linear-gradient(110deg, var(--el-fill-color-light) 24%, var(--el-fill-color-dark) 48%, var(--el-fill-color-light) 72%);
	background-size: 240% 100%;
	animation: cover-sweep 1.8s linear infinite;
}
@keyframes cover-sweep { to { background-position: -240% 0; } }
@media (prefers-reduced-motion: reduce) { .generating-cover { animation: none; } }

.el-alert + .el-alert { margin-top: 10px; }
.quota { margin: 12px 0; color: var(--el-text-color-secondary); line-height: 1.6; }
.prompt-form { margin: 16px 0 24px; }
.actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.actions .el-button + .el-button { margin-left: 0; }
.history-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.history { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 14px; }
.candidate { border: 1px solid var(--el-border-color); border-radius: 8px; overflow: hidden; }
.candidate.chosen { border-color: var(--el-color-success); }
.candidate .el-image, .no-image { width: 100%; height: 220px; background: var(--el-fill-color-light); }
.no-image { display: grid; place-items: center; }
.candidate-info { display: flex; flex-direction: column; gap: 10px; padding: 14px; }
.candidate-info small { color: var(--el-text-color-secondary); }
.saved-prompt { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 180px; overflow: auto; }
.failure { color: var(--el-color-danger); overflow-wrap: anywhere; }
.el-pagination { margin-top: 18px; }
</style>
