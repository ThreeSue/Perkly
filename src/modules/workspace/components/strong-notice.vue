<template>
	<el-dialog :model-value="!!current" title="强提醒" width="min(520px, calc(100vw - 32px))"
		append-to-body :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false">
		<template v-if="current">
			<h3 class="reminder-title">{{ current.title }}</h3>
			<p class="reminder-meta">{{ current.senderName }} · {{ dayjs(current.createTime).format('YYYY-MM-DD HH:mm:ss') }}</p>
			<div class="reminder-content">{{ current.content }}</div>
			<el-alert v-if="error" :title="error" type="error" :closable="false" />
		</template>
		<template #footer><el-button type="primary" :loading="busy" @click="confirm">我知道了</el-button></template>
	</el-dialog>
</template>
<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import dayjs from 'dayjs';
import { useBase } from '/$/base';
import { identity } from '/@/workspace';
import { useNoticeStore } from '../stores/notices';
const { user } = useBase();
const notices = useNoticeStore();
const owning = ref(false), busy = ref(false), error = ref('');
const account = computed(() => user.token && user.info?.id ? `${identity}:${user.info.id}` : '');
const current = computed(() => owning.value ? notices.pending[0] : undefined);
let release: (() => void) | undefined, acquiring = false, disposed = false;
let timer: ReturnType<typeof setInterval>;
let generation = 0;
function relinquish() { generation++; owning.value = false; release?.(); release = undefined; }
async function claim() {
	if (disposed || document.hidden || !account.value || !notices.pending.length || owning.value || acquiring) return;
	const key = account.value, version = generation;
	if (!navigator.locks) { owning.value = true; return; }
	acquiring = true;
	try {
		await navigator.locks.request(`perkly-strong-notice:${key}`, { ifAvailable: true }, async lock => {
			if (!lock || disposed || version !== generation || key !== account.value || document.hidden) return;
			// Recheck server state after acquiring the cross-tab display lock.
			await notices.refresh();
			if (disposed || version !== generation || key !== account.value || !notices.pending.length) return;
			owning.value = true;
			await new Promise<void>(resolve => { release = resolve; });
		});
	} finally { acquiring = false; }
}
async function confirm() {
	if (!current.value || busy.value) return;
	const id = current.value.id, key = account.value;
	busy.value = true; error.value = '';
	try {
		await notices.confirm(id);
		if (key !== account.value) return;
		try { localStorage.setItem(`perkly-notice-confirmed:${key}`, JSON.stringify({ id, at: Date.now() })); } catch { /* Server state remains authoritative. */ }
	} catch (cause: any) { if (key === account.value) error.value = cause.message || '确认未成功，请重试'; }
	finally { if (key === account.value) busy.value = false; }
}
function sync(event: StorageEvent) {
	if (event.key === `perkly-notice-confirmed:${account.value}`) void notices.refresh();
}
function visibility() { if (document.hidden) relinquish(); else void claim(); }
watch(account, () => { relinquish(); busy.value = false; error.value = ''; void claim(); });
watch(() => notices.pending.length, count => { if (!count) relinquish(); else void claim(); });
watch(() => current.value?.id, () => { error.value = ''; });
onMounted(() => {
	timer = setInterval(() => void claim(), 3000);
	window.addEventListener('storage', sync); document.addEventListener('visibilitychange', visibility);
	void claim();
});
onBeforeUnmount(() => { disposed = true; relinquish(); clearInterval(timer); window.removeEventListener('storage', sync); document.removeEventListener('visibilitychange', visibility); });
</script>
<style scoped>
.reminder-title { margin: 0 0 8px; overflow-wrap: anywhere; }
.reminder-meta { color: var(--el-text-color-regular); margin: 0 0 20px; }
.reminder-content { max-height: 50vh; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; margin-bottom: 16px; }
</style>
