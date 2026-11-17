<template>
	<section class="rescan">
		<div class="toolbar">
			<label for="risk-rescan-source">资料类型</label>
			<el-select id="risk-rescan-source" v-model="source" placeholder="选择资料类型" :disabled="scanning">
				<el-option v-for="key in sources" :key="key" :value="key" :label="sourceLabels[key]" />
			</el-select>
			<el-button type="primary" :disabled="!canScan" :loading="scanning" @click="rescan(nextCursor)">
				{{ nextCursor != null ? '继续下一批' : scanResult?.source === source ? '重新开始重扫' : '开始重扫' }}
			</el-button>
		</div>
		<details class="advanced">
			<summary>高级选项</summary>
			<div class="toolbar">
				<label for="risk-rescan-after">从编号之后</label>
				<el-input id="risk-rescan-after" v-model="afterId" inputmode="numeric" maxlength="19" class="cursor-input" :disabled="scanning" />
				<label for="risk-rescan-limit">每批对象数</label>
				<el-input-number id="risk-rescan-limit" v-model="limit" :min="1" :max="100" :precision="0" :disabled="scanning" />
			</div>
		</details>
		<el-alert v-if="scanError" :title="scanError" type="error" :closable="false" />
		<el-alert v-if="scanResult" :title="scanResult.message" type="success" :closable="false" />
	</section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { riskApi, riskScenes } from '../api/content-risk';

const props = defineProps<{ manage: boolean }>();

type RecordId = number | string;

interface ScanResult {
	checked: number;
	created: number;
	existing: number;
	nextAfterId: RecordId;
	hasMore: boolean;
}

const sources = ['MERCHANT', 'STORE', 'COUPON', 'PROFILE', 'WORKSPACE_PROFILE', 'FEEDBACK', 'CHAT', 'NOTICE', 'MERCHANT_APPLICATION', 'PLATFORM_WARNING'];
const sourceLabels = { ...riskScenes, WORKSPACE_PROFILE: '工作账号资料', MERCHANT_APPLICATION: '商家入驻申请', PLATFORM_WARNING: '平台警告通知' } as Record<string, string>;
const source = ref('');
const afterId = ref('0');
const limit = ref<number | undefined>(100);
const scanning = ref(false);
const scanError = ref('');
const scanResult = ref<{ message: string; source: string; nextAfterId?: RecordId }>();
const nextCursor = computed(() => scanResult.value?.source === source.value ? scanResult.value?.nextAfterId : undefined);
const canScan = computed(() => props.manage && !scanning.value && sources.includes(source.value)
	&& validId(nextCursor.value ?? afterId.value) && isCount(limit.value) && limit.value > 0 && limit.value <= 100);
watch([source, afterId, limit], () => { scanResult.value = undefined; scanError.value = ''; });

function isCount(value: unknown): value is number {
	return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
}

function validId(value: unknown) {
	if (isCount(value)) return true;
	return typeof value === 'string' && /^(0|[1-9][0-9]{0,18})$/.test(value)
		&& (value.length < 19 || value <= '9223372036854775807');
}

async function rescan(cursor?: RecordId) {
	if (!canScan.value) return;
	const start = cursor == null ? afterId.value : String(cursor);
	const size = limit.value;
	const scanSource = source.value;
	scanning.value = true;
	scanError.value = '';
	try {
		const data = await riskApi<ScanResult>('history/rescan', { source: scanSource, afterId: start, limit: size });
		if (![data?.checked, data?.created, data?.existing].every(isCount) || !validId(data?.nextAfterId) || typeof data.hasMore !== 'boolean') {
			throw new Error('重扫结果无法核对，请先查看操作记录');
		}
		scanResult.value = {
			source: scanSource,
			message: `本批检查 ${data.checked} 个对象，新增 ${data.created} 条记录，已有 ${data.existing} 条。${data.hasMore ? '可继续下一批。' : '该范围已查完。'}`,
			nextAfterId: data.hasMore ? data.nextAfterId : undefined
		};
	} catch (error) {
		scanError.value = error && typeof error === 'object' && 'message' in error && typeof error.message === 'string'
			? error.message : '重扫失败，请核对操作记录后重试';
	} finally {
		scanning.value = false;
	}
}
</script>

<style scoped>
.toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin: 12px 0; }
.el-select { width: 180px; }
.cursor-input { width: 160px; }
.el-alert { margin: 12px 0; }
.advanced summary { color: var(--el-text-color-regular); cursor: pointer; width: fit-content; }
</style>
