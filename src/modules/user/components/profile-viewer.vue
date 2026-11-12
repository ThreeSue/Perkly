<template>
	<el-drawer v-model="visible" size="min(880px, 100vw)" destroy-on-close class="profile-drawer" @closed="stop">
		<template #header><div class="profile-heading"><h2>{{ name || '用户' }}的消费画像</h2><p>基于近 180 天真实业务记录，辅助运营理解消费习惯</p></div></template>
		<div v-loading="loading" class="profile-body" :aria-busy="loading">
			<el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
			<div class="profile-toolbar"><el-tag :type="data?.state === 'FAILED' ? 'danger' : 'info'">{{ stateText }}</el-tag><span>{{ data?.cutoff ? `数据截至 ${date(data.cutoff)}` : '尚未生成画像' }}</span><el-button :loading="updating" :disabled="pending" @click="refresh">{{ data?.revision ? '重新分析' : '生成画像' }}</el-button><el-button text @click="load">刷新状态</el-button></div>
			<el-alert v-if="data?.errorCode" type="warning" title="本次分析未完成，已有画像仍保留。请稍后重试。" :closable="false" />
			<template v-if="stats">
				<section class="profile-summary"><h3>{{ summary }}</h3><p>完成消费是主要依据；付费购券反映意向，免费领券仅作弱参考。</p><div class="profile-tags"><el-tag v-for="tag in data.interpretation?.tags || []" :key="tag.label" effect="plain">{{ tag.label }}</el-tag><span v-if="!data.interpretation?.tags?.length">暂没有足够依据提炼品类标签</span></div></section>
				<div class="profile-section-head"><h3>消费概览</h3><el-radio-group v-model="days" size="small" aria-label="统计时间范围"><el-radio-button :value="30">近 30 天</el-radio-button><el-radio-button :value="90">近 90 天</el-radio-button><el-radio-button :value="180">近 180 天</el-radio-button></el-radio-group></div>
				<dl class="profile-metrics"><div><dt>完成消费</dt><dd>{{ stats.visits }}<small> 次</small></dd></div><div><dt>已知消费金额</dt><dd>{{ money(stats.consumptionCents) }}</dd></div><div><dt>付费购券</dt><dd>{{ stats.paidPurchases }}<small> 次</small></dd></div><div><dt>免费领券</dt><dd>{{ stats.freeClaims }}<small> 次</small></dd></div></dl>
				<p class="profile-note">消费金额覆盖 {{ stats.knownAmountVisits }} 次，另有 {{ stats.unknownAmountVisits }} 次金额未知。购券支出 {{ money(stats.purchaseSpendCents) }} 单独统计。</p>
				<p v-if="stats.knownDiscountVisits" class="profile-note">{{ stats.knownDiscountVisits }} 次消费有优惠记录，其中 {{ stats.discountedVisits }} 次实际享受减免，合计 {{ money(stats.discountCents) }}。未据此推断消费能力。</p>
				<div class="profile-columns"><section><h3>实际消费价位</h3><p class="profile-note">仅统计金额已知的消费，不使用券面原价推算</p><div v-for="band in stats.priceBands" :key="band.name" class="profile-band"><div><span>{{ band.name }}</span><strong>{{ band.visits }} 次</strong></div><meter :value="band.visits" :max="Math.max(1, stats.knownAmountVisits)" :aria-label="band.name" /></div></section><section><h3>数据完整度</h3><dl class="profile-quality"><div><dt>消费金额已知</dt><dd>{{ stats.knownAmountVisits }} / {{ stats.visits }} 次</dd></div><div><dt>时间为近似值</dt><dd>{{ data.evidence.statistics.approximateTimeVisits }} 次</dd></div><div><dt>待核对来源</dt><dd>{{ data.evidence.batch.issues.length }} 项</dd></div></dl><p class="profile-note">近似时间不用于计算消费间隔。数据异常不会被模型自行补齐。</p></section></div>
				<section class="profile-evidence"><h3>行业偏好与消费节奏</h3><el-table :data="stats.categories" empty-text="暂没有可统计的行业消费记录"><el-table-column label="行业"><template #default="{ row }">{{ data.evidence.categoryNames?.[row.categoryId] || '历史分类' }}</template></el-table-column><el-table-column prop="visits" label="完成消费" width="100" /><el-table-column label="平均间隔"><template #default="{ row }">{{ row.meanGapDays == null ? '样本不足' : `约 ${row.meanGapDays.toFixed(1)} 天` }}</template></el-table-column><el-table-column label="最近消费"><template #default="{ row }">{{ date(row.lastVisit) }}</template></el-table-column></el-table><p class="profile-note">按不同消费日计算间隔，同一天的多次消费不会人为拉短周期。</p></section>
				<section class="profile-evidence"><h3>偏好依据</h3><p class="profile-note">标签来自业务内容，展开可核对原文与对应事实。</p><el-collapse><el-collapse-item v-for="(tag, index) in data.interpretation?.tags || []" :key="index" :title="`${tag.label} · ${tag.factKeys.length} 条关联记录`" :name="index"><blockquote>{{ tag.quote }}</blockquote><p>依据来源：{{ tag.factKeys.join('、') }}</p></el-collapse-item></el-collapse><el-empty v-if="!data.interpretation?.tags?.length" description="更多真实消费后再形成偏好" :image-size="64" /></section>
				<section class="profile-evidence"><div class="profile-section-head"><h3>推荐预览</h3><el-button :loading="previewLoading" @click="preview">查看当前优惠券推荐</el-button></div><p class="profile-note">与用户端共用推荐排序；此处未提供定位，不展示“附近”。</p><el-alert v-if="previewError" :title="previewError" type="error" :closable="false" /><el-table v-if="previewRows" :data="previewRows" empty-text="当前没有符合领购条件的公开优惠券"><el-table-column prop="name" label="优惠券" /><el-table-column prop="merchantName" label="商家" /><el-table-column label="售价" width="100"><template #default="{ row }">{{ money(row.salePrice) }}</template></el-table-column><el-table-column prop="recommendationReason" label="推荐依据" /></el-table></section>
				<section v-permission="'profile:update'" class="profile-evidence"><h3>纠正不准确的标签</h3><p class="profile-note">排除标签后，推荐不再使用该标签；原始消费与模型证据仍保留，重新分析也不会取消纠正。</p><div class="profile-correction"><el-select v-model="excludedDraft" multiple clearable placeholder="选择需要排除的已有标签" aria-label="需要排除的画像标签"><el-option v-for="label in correctionOptions" :key="label" :label="label" :value="label" /></el-select><el-button :loading="correcting" @click="correct">保存纠正</el-button></div></section>
				<footer class="profile-note">画像版本 {{ data.revision }} · {{ data.modelVersion }} · 更新于 {{ date(data.updatedAt) }}。标签不是偏好概率，也不代表用户身份或收入。</footer>
			</template>
			<el-empty v-else-if="!loading" :description="pending ? '已加入分析队列，完成后会在这里显示' : '先生成画像，再查看消费摘要与偏好依据'" :image-size="120" />
		</div>
	</el-drawer>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { request } from '/@/cool/service/request';
import { config } from '/@/config';
const visible = ref(false), loading = ref(false), updating = ref(false), error = ref(''), name = ref('');
const data = ref<any>(null), days = ref(90);
const previewRows = ref<any[] | null>(null), previewLoading = ref(false), previewError = ref('');
const excludedDraft = ref<string[]>([]), correcting = ref(false);
const correctionOptions = computed<string[]>(() => [...new Set<string>([...(data.value?.excludedLabels || []), ...(data.value?.interpretation?.tags || []).map((t: any) => t.label)])]);
let customerId = 0, serial = 0, timer: ReturnType<typeof setTimeout> | undefined;
const pending = computed(() => ['PENDING', 'RUNNING'].includes(data.value?.state));
const stateText = computed(() => ({ NOT_BUILT: '尚未分析', PENDING: '等待分析', RUNNING: '正在分析', COMPLETED: '分析完成', FAILED: '更新失败' }[data.value?.state as string] || '等待加载'));
const stats = computed(() => data.value?.evidence?.statistics?.windows?.find((w: any) => w.days === days.value));
const summary = computed(() => data.value?.evidence?.statistics?.sufficiency === 'NO_CONSUMPTION' ? '尚无完成消费，暂不判断消费习惯' : data.value?.evidence?.statistics?.sufficiency === 'LIMITED' ? '消费样本较少，偏好仍需观察' : '已有消费依据，可结合明细了解偏好');
function money(value: number) { return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(value / 100); }
function date(value: string) { return new Date(value).toLocaleString('zh-CN', { hour12: false }); }
function stop() { serial++; if (timer) clearTimeout(timer); }
async function load() {
	if (timer) clearTimeout(timer);
	const id = ++serial;
	loading.value = !data.value; error.value = '';
	try { const result: any = await request({ url: `${config.baseUrl}/admin/profile/info`, params: { customerId } }); if (id === serial) { data.value = result; excludedDraft.value = [...(result.excludedLabels || [])]; } }
	catch (e) { if (id === serial) error.value = (e as Error).message || '画像读取失败，请刷新重试'; }
	finally { if (id === serial) { loading.value = false; if (pending.value && visible.value) timer = setTimeout(load, 5000); } }
}
async function refresh() {
	updating.value = true; error.value = '';
	const id = ++serial;
	try { const result = await request({ url: `${config.baseUrl}/admin/profile/refresh`, method: 'POST', data: { customerId } }); if (id === serial) { data.value = result; await load(); } }
	catch (e) { if (id === serial) error.value = (e as Error).message || '提交失败，请稍后重试'; }
	finally { updating.value = false; }
}
async function preview() {
	const target = customerId;
	previewLoading.value = true; previewError.value = '';
	try { const result: any = await request({ url: `${config.baseUrl}/admin/profile/preview`, method: 'POST', params: { customerId }, data: { kind: 'COUPON', page: 1, size: 5 } }); if (visible.value && target === customerId) previewRows.value = result.list; }
	catch (e) { if (target === customerId) previewError.value = (e as Error).message || '推荐读取失败'; }
	finally { previewLoading.value = false; }
}
async function correct() {
	const target = customerId; correcting.value = true; error.value = '';
	try { const result: any = await request({ url: `${config.baseUrl}/admin/profile/correction`, method: 'POST', data: { customerId, excludedLabels: excludedDraft.value } }); if (visible.value && target === customerId) { data.value = result; excludedDraft.value = [...result.excludedLabels]; previewRows.value = null; } }
	catch (e) { if (target === customerId) error.value = (e as Error).message || '纠正保存失败'; }
	finally { correcting.value = false; }
}
function open(row: { id: number; nickName?: string }) { stop(); customerId = row.id; name.value = row.nickName || '用户'; data.value = null; previewRows.value = null; previewError.value = ''; visible.value = true; days.value = 90; load(); }
onBeforeUnmount(stop);
defineExpose({ open });
</script>

<style scoped>
.profile-heading h2 { margin: 0; font-size: 22px; color: var(--el-text-color-primary); }
.profile-heading p,.profile-note { color: var(--el-text-color-regular); font-size: 13px; line-height: 1.7; }
.profile-heading p { margin: 8px 0 0; }
.profile-body { min-height: 260px; color: var(--el-text-color-primary); }
.profile-toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 24px; }
.profile-toolbar > span:nth-child(2) { flex: 1; font-size: 13px; color: var(--el-text-color-regular); }
.profile-summary { background: var(--el-fill-color-light); padding: 24px; border-radius: 12px; margin: 20px 0 32px; }
h3 { font-size: 16px; margin: 0 0 12px; font-weight: 600; }
.profile-summary h3 { font-size: 20px; line-height: 1.5; }
.profile-summary p { line-height: 1.7; font-size: 14px; color: var(--el-text-color-regular); }
.profile-tags { display: flex; flex-wrap: wrap; gap: 8px; font-size: 13px; }
.profile-section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.profile-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); padding: 22px 0; border-block: 1px solid var(--el-border-color-lighter); gap: 20px; }
dt { font-size: 13px; color: var(--el-text-color-regular); } dd { margin: 8px 0 0; font-variant-numeric: tabular-nums; font-size: 23px; font-weight: 600; } dd small { font-size: 13px; font-weight: 400; }
.profile-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 36px; margin: 32px 0; }
.profile-band { margin-top: 18px; }.profile-band > div { display: flex; justify-content: space-between; font-size: 14px; }
meter { display: block; width: 100%; height: 12px; margin-top: 8px; accent-color: var(--el-color-primary); }
meter::-webkit-meter-bar { background: var(--el-fill-color); border: 0; height: 6px; }
meter::-webkit-meter-optimum-value { background: var(--el-color-primary); }
.profile-quality > div { display: flex; align-items: baseline; justify-content: space-between; padding: 12px 0; border-bottom: 1px solid var(--el-border-color-lighter); }.profile-quality dd { font-size: 15px; }
.profile-evidence { margin-top: 32px; } blockquote { margin: 8px 0; padding: 16px; background: var(--el-fill-color-light); line-height: 1.7; } footer { margin-top: 28px; }
.profile-correction { display: flex; gap: 12px; }.profile-correction .el-select { flex: 1; min-width: 0; }
@media (max-width: 600px) { .profile-columns { grid-template-columns: 1fr; gap: 24px; }.profile-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }.profile-summary { padding: 18px; } }
</style>
