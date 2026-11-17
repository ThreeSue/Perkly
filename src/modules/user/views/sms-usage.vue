<template>
	<ManagementShell title="短信验证码" description="查看验证码发送与核验记录，管理每个手机号的每日发送上限。">
		<el-result v-if="!allowed" icon="warning" title="无权查看短信验证码" />
		<div v-else class="sms-content">
			<el-tabs v-model="tab">
				<el-tab-pane label="用量概览" name="overview" />
				<el-tab-pane label="使用明细" name="events" />
				<el-tab-pane label="发送设置" name="limits" />
				<el-tab-pane label="云端查询（可选）" name="cloud" />
			</el-tabs>
			<section v-if="tab === 'overview'" v-loading="localBusy">
				<div class="sms-toolbar">
					<el-radio-group v-model="days" aria-label="统计范围">
						<el-radio-button v-for="(label, day) in ranges" :key="day" :value="Number(day)">{{ label }}</el-radio-button>
					</el-radio-group>
					<el-button @click="refreshLocal">刷新</el-button>
					<span class="sms-muted">北京时间 · 发送请求按取得额度的次数统计</span>
				</div>
				<el-alert v-if="localError" :title="localError" type="error" :closable="false" show-icon />
				<template v-else-if="overview">
					<el-alert v-if="warning" :title="warning" type="warning" :closable="false" show-icon />
					<div class="sms-toolbar" role="status">
						<span>每个号码每天最多 {{ overview.dailyLimit }} 次 · 间隔至少 {{ overview.intervalSeconds }} 秒</span>
						<el-button v-if="canManage" link type="primary" @click="tab = 'limits'">调整发送上限</el-button>
					</div>
					<p class="sms-muted">{{ overview.recordingSince ? '本系统发送记录始于 ' + overview.recordingSince : '尚无本系统发送记录，接入后的验证码请求会自动记入' }}。未注册号码同样计数，阿里云官网历史用量不计入此处。</p>
					<dl class="sms-totals">
						<div><dt>今日发送请求</dt><dd>{{ format(today.attempts) }}</dd></div>
						<div><dt>本月发送请求</dt><dd>{{ format(overview.monthAttempts) }}</dd></div>
						<div><dt>所选范围 · 阿里云受理</dt><dd>{{ format(totals.accepted) }}</dd></div>
						<div><dt>所选范围 · 限额拦截</dt><dd>{{ format(totals.blocked) }}</dd></div>
					</dl>
					<dl class="sms-totals">
						<div><dt>所选范围 · 发送失败</dt><dd>{{ format(totals.failed) }}</dd></div>
						<div><dt>所选范围 · 结果待确认</dt><dd>{{ format(totals.unknown) }}</dd></div>
						<div><dt>所选范围 · 核验通过（去重）</dt><dd>{{ format(totals.verified) }}</dd></div>
						<div><dt>所选范围 · 核验未通过 / 异常</dt><dd>{{ format(totals.verifyFailed) }}</dd></div>
					</dl>
					<p class="sms-muted">受理成功表示阿里云已接受发送请求；最终送达情况在云端查询中查看。页面每 15 秒刷新。</p>
					<div class="sms-breakdown">
						<section class="sms-section">
							<h3>每日趋势</h3>
							<v-chart class="sms-chart" :option="chart" autoresize />
						</section>
						<section class="sms-section">
							<h3>按用途统计</h3>
							<el-table :data="sceneRows" empty-text="该范围内暂无记录">
								<el-table-column prop="label" label="用途" min-width="112" />
								<el-table-column prop="attempts" label="发送请求" align="right" width="88" />
								<el-table-column prop="accepted" label="受理" align="right" width="65" />
								<el-table-column prop="verified" label="核验通过" align="right" width="88" />
								<el-table-column prop="blocked" label="拦截" align="right" width="65" />
							</el-table>
						</section>
					</div>
				</template>
			</section>

			<section v-else-if="tab === 'events'" v-loading="localBusy">
				<div class="sms-toolbar">
					<el-select v-model="days" aria-label="明细时间范围">
						<el-option v-for="(label, day) in ranges" :key="day" :value="Number(day)" :label="label" />
					</el-select>
					<el-select v-model="scene" clearable placeholder="全部用途" aria-label="用途">
						<el-option v-for="(label, key) in scenes" :key="key" :value="key" :label="label" />
					</el-select>
					<el-select v-model="kind" clearable placeholder="全部事件" aria-label="事件类型">
						<el-option v-for="(label, key) in kinds" :key="key" :value="key" :label="label" />
					</el-select>
					<el-select v-model="result" clearable placeholder="全部结果" aria-label="事件结果">
						<el-option v-for="(label, key) in outcomes" :key="key" :value="key" :label="label" />
					</el-select>
					<el-button @click="refreshLocal">刷新</el-button>
				</div>
				<el-alert v-if="localError" :title="localError" type="error" :closable="false" show-icon />
				<el-table v-else :data="events?.list ?? []" row-key="id" empty-text="该范围内暂无记录">
					<el-table-column label="时间（北京时间）" min-width="169">
						<template #default="{ row }">{{ row.time?.replace('T', ' ').slice(0, 19) }}</template>
					</el-table-column>
					<el-table-column label="手机号" min-width="118">
						<template #default="{ row }"><span class="sms-number">{{ row.phoneMask || '—' }}</span></template>
					</el-table-column>
					<el-table-column label="使用者" min-width="150">
						<template #default="{ row }">
							{{ row.actorName }}
							<small v-if="row.verifiedAccount" class="sms-muted sms-secondary">核验后关联 · {{ row.verifiedAccount }}</small>
						</template>
					</el-table-column>
					<el-table-column label="用途" min-width="113">
						<template #default="{ row }">{{ row.kind === 'POLICY' ? '—' : scenes[row.scene] || '未分类' }}</template>
					</el-table-column>
					<el-table-column label="事件" min-width="96">
						<template #default="{ row }">{{ kinds[row.kind] }}</template>
					</el-table-column>
					<el-table-column label="结果" min-width="125">
						<template #default="{ row }">
							<el-tag :type="tag(row.result)" effect="plain">{{ outcomes[row.result] || '未知' }}</el-tag>
							<small v-if="row.kind === 'POLICY'" class="sms-muted sms-secondary">{{ row.previousLimit }} → {{ row.nextLimit }} 次/日</small>
						</template>
					</el-table-column>
					<el-table-column label="服务返回码" min-width="140" show-overflow-tooltip>
						<template #default="{ row }">{{ row.providerCode || '—' }}</template>
					</el-table-column>
					<el-table-column label="阿里云请求 ID" min-width="160" show-overflow-tooltip>
						<template #default="{ row }">{{ row.providerRequestId || '—' }}</template>
					</el-table-column>
				</el-table>
				<el-pagination v-if="!localError && events && events.total > pageSize" class="sms-pagination"
					v-model:current-page="page" :total="events.total" :page-size="pageSize"
					layout="total, prev, pager, next" @current-change="refreshLocal" />
			</section>

			<section v-else-if="tab === 'limits'" v-loading="localBusy" class="sms-settings">
				<el-alert v-if="localError" :title="localError" type="error" :closable="false" show-icon />
				<form v-else-if="savedLimit !== undefined" @submit.prevent="save">
					<h3>单号码每日上限</h3>
					<p>登录、注册、找回密码和商家入驻等场景共用同一个额度，每天北京时间 00:00 重置。达到上限后，仍可使用有效期内已收到的验证码。</p>
					<div class="sms-toolbar">
						<el-input-number v-model="dailyLimit" :min="1" :max="30" :step="1" :precision="0"
							:disabled="!canManage || saving" aria-label="每个手机号每日最多发送次数" />
						<span class="sms-muted">次 / 号码 / 日</span>
						<el-button v-if="canManage" type="primary" native-type="submit" :loading="saving"
							:disabled="dailyLimit === undefined || dailyLimit === savedLimit">保存设置</el-button>
					</div>
					<p class="sms-muted">只有通过发送前校验且取得额度的请求计入次数，阿里云受理失败或结果未知也占用额度。</p>
				</form>
				<el-button v-if="localError" @click="refreshLocal">重新加载</el-button>
			</section>

			<section v-else class="sms-cloud">
				<section class="sms-section" style="grid-column: 1 / -1">
					<h3>送达回执 · 最近 {{ days }} 天</h3>
					<p class="sms-muted">回执同步用于补充最终送达结果，不影响本系统的发送限额和使用记录。</p>
					<el-button :loading="receiptBusy" @click="loadReceipts">查询回执状态</el-button>
					<el-alert v-if="receiptError" :title="receiptError" type="error" :closable="false" show-icon />
					<div v-else-if="delivery" class="sms-toolbar" role="status">
						<el-tag :type="delivery.sync.state === 'RUNNING' ? 'success' : delivery.sync.state === 'ERROR' ? 'danger' : 'info'">{{ syncLabel }}</el-tag>
						<span class="sms-muted">{{ delivery.recordingSince ? '本地记录始于 ' + delivery.recordingSince : '尚无本地发送记录' }} · 官网历史数据未同步</span>
						<span class="sms-muted">最近连接：{{ timeLabel(delivery.sync.lastPoll) }} · 最近回执：{{ timeLabel(delivery.sync.lastReceipt) }}</span>
						<span v-if="delivery.sync.errorCode" class="sms-muted">同步错误：{{ delivery.sync.errorCode }}，后台将自动重试</span>
					</div>
					<p v-if="delivery && !receiptError">已送达 {{ delivery.delivered }} · 送达失败 {{ delivery.failed }} · 尚未收到回执 {{ delivery.pending }}</p>
				</section>
				<p class="sms-muted">此处显示阿里云公开接口返回的账号级数据。接口未返回数据时，可到阿里云控制台核对历史记录。</p>
				<section class="sms-section">
					<h3>短信认证统计</h3>
					<div class="sms-toolbar">
						<el-radio-group v-model="cloudDays" aria-label="阿里云统计范围" @change="cloudUsage = undefined; cloudUsageError = ''">
							<el-radio-button :value="7" :disabled="usageBusy">最近 7 天</el-radio-button>
							<el-radio-button :value="30" :disabled="usageBusy">最近 30 天</el-radio-button>
						</el-radio-group>
						<el-button :loading="usageBusy" type="primary" @click="loadCloudUsage">查询用量</el-button>
					</div>
					<el-alert v-if="cloudUsageError" :title="cloudUsageError" type="error" :closable="false" show-icon />
					<el-empty v-else-if="cloudUsage && !cloudUsage.trend.length && cloudUsage.total == null &&
						cloudUsage.success == null && cloudUsage.failed == null && cloudUsage.unknown == null"
						:image-size="64" description="公开接口未返回所选时段的用量">
						<el-link type="primary" href="https://dypns.console.aliyun.com/smsServiceRecord" target="_blank" rel="noopener noreferrer">
							在阿里云查看发送记录
						</el-link>
					</el-empty>
					<template v-else-if="cloudUsage">
						<dl class="sms-totals">
							<div><dt>总计</dt><dd>{{ format(cloudUsage.total) }}</dd></div>
							<div><dt>成功</dt><dd>{{ format(cloudUsage.success) }}</dd></div>
							<div><dt>失败</dt><dd>{{ format(cloudUsage.failed) }}</dd></div>
							<div><dt>未知</dt><dd>{{ format(cloudUsage.unknown) }}</dd></div>
						</dl>
						<el-table :data="cloudUsage.trend" empty-text="阿里云未返回每日记录">
							<el-table-column prop="date" label="日期" min-width="120" />
							<el-table-column prop="success" label="成功" align="right" min-width="80" />
							<el-table-column prop="failed" label="失败" align="right" min-width="80" />
							<el-table-column prop="unknown" label="未知" align="right" min-width="80" />
						</el-table>
					</template>
				</section>
				<section class="sms-section">
					<h3>月度账单</h3>
					<div class="sms-toolbar">
						<el-date-picker v-model="month" type="month" value-format="YYYY-MM" :clearable="false" :disabled="billingBusy"
							:disabled-date="futureMonth" placeholder="选择账单月份" aria-label="账单月份" @change="billing = undefined; billingError = ''" />
						<el-button :loading="billingBusy" type="primary" @click="loadBilling">查询账单</el-button>
					</div>
					<el-alert v-if="billingError" :title="billingError" type="error" :closable="false" show-icon />
					<el-empty v-else-if="billing && billing.amount == null && !billing.items.length"
						:image-size="64" description="公开接口未返回该月账单">
						<el-link type="primary" href="https://dypns.console.aliyun.com/costStatistics" target="_blank" rel="noopener noreferrer">
							在阿里云查看账单
						</el-link>
					</el-empty>
					<template v-else-if="billing">
						<p class="sms-bill">{{ billing.month }} 合计 <strong>{{ billing.amount == null ? '未返回' : '¥' + billing.amount }}</strong></p>
						<el-table :data="billing.items" empty-text="阿里云未返回计费明细">
							<el-table-column prop="item" label="计费项" min-width="140" />
							<el-table-column prop="scene" label="阿里云场景" min-width="140" />
							<el-table-column prop="units" label="计费量" align="right" min-width="85" />
							<el-table-column prop="unitPrice" label="单价" align="right" min-width="85" />
							<el-table-column prop="amount" label="金额" align="right" min-width="85" />
						</el-table>
					</template>
				</section>
			</section>
		</div>
	</ManagementShell>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted, onActivated, onDeactivated } from 'vue';
import type { EChartsOption } from 'echarts';
import { ElMessage } from 'element-plus';
import ManagementShell from '/$/workspace/components/management-shell.vue';
import { featureApi } from '/$/workspace/feature-api';
import { useMenuStore } from '/$/base/store/menu';
import { isPlatform } from '/@/workspace';

const ranges = { 1: '今天', 7: '最近 7 天', 30: '最近 30 天' };
const scenes: Record<string, string> = {
	UNCLASSIFIED: '未分类', REGISTER: '注册', LOGIN: '短信登录', RESET_PASSWORD: '找回密码',
	WECHAT_BIND: '微信绑定手机', BIND_PHONE: '绑定手机号', CHANGE_PASSWORD: '修改密码', MERCHANT_ONBOARDING: '商家入驻'
};
const kinds: Record<string, string> = { SEND: '发送', VERIFY: '核验', POLICY: '设置变更' };
const outcomes: Record<string, string> = {
	RESERVED: '已取得额度', ACCEPTED: '阿里云已受理', REJECTED: '发送失败', UNKNOWN: '结果未知',
	DAILY_LIMIT: '达到日上限', COOLDOWN: '发送间隔拦截', VERIFIED: '核验通过', INCORRECT: '验证码错误',
	VERIFY_ERROR: '核验服务异常', VERIFIED_REPEAT: '重复核验通过（不计数）', UPDATED: '上限已修改'
};
const attemptResults = new Set(['RESERVED', 'ACCEPTED', 'REJECTED', 'UNKNOWN']);
const blockedResults = new Set(['DAILY_LIMIT', 'COOLDOWN']);
const format = (value?: number | null) => value == null ? '未返回' : new Intl.NumberFormat('zh-CN').format(value);
const tag = (value: string) => ['VERIFIED', 'ACCEPTED'].includes(value) ? 'success' :
	blockedResults.has(value) ? 'warning' : ['VERIFY_ERROR', 'REJECTED', 'INCORRECT'].includes(value) ? 'danger' : 'info';

type Group = { dayKey: string; scene: string; kind: string; result: string; total: number };
type Overview = { from: string; today: string; groups: Group[]; monthAttempts: number;
	dailyWarningThreshold: number; monthlyWarningThreshold: number; dailyLimit: number; intervalSeconds: number; recordingSince: string | null };
type Event = { id: string; time: string; phoneMask?: string; scene: string; kind: string; result: string; actorName: string;
	verifiedAccount?: string; providerCode?: string; providerRequestId?: string; previousLimit?: number; nextLimit?: number };
type Counts = { attempts: number; accepted: number; blocked: number; verified: number; failed: number; unknown: number; verifyFailed: number };
type CloudCounts = { success: number | null; failed: number | null; unknown: number | null };
type CloudUsage = CloudCounts & { total: number | null; trend: (CloudCounts & { date: string })[] };
type Bill = { month: string; amount: string | null; items: { item: string; scene: string; units: string; unitPrice: string; amount: string }[] };
type Limits = { dailyLimit: number };

const menu = useMenuStore();
const permission = (key: string) => isPlatform && menu.perms.some(value =>
	typeof value === 'string' && value.split(',').some(item => item.trim() === key));
const allowed = computed(() => permission('sms:usage:view'));
const canManage = computed(() => permission('sms:usage:manage'));
const tab = ref('overview');
const days = ref(7);
const scene = ref('');
const kind = ref('');
const result = ref('');
const page = ref(1);
const pageSize = 20;
const overview = ref<Overview>();
type Delivery = { delivered: number; failed: number; pending: number; recordingSince: string | null;
	sync: { state: string; lastPoll: string | null; lastReceipt: string | null; errorCode: string | null } };
const delivery = ref<Delivery>();
const receiptError = ref('');
const receiptBusy = ref(false);
const syncLabel = computed(() => ({ RUNNING: '回执连接正常', CONNECTING: '正在连接回执队列', ERROR: '回执同步异常', DISABLED: '回执消费未启用' }[delivery.value?.sync.state ?? ''] ?? '同步状态未知'));
const timeLabel = (value: string | null) => value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '暂无';
const events = ref<{ list: Event[]; total: number }>();
const localError = ref('');
const localBusy = ref(false);
const savedLimit = ref<number>();
const dailyLimit = ref<number>();
const saving = ref(false);
const cloudDays = ref(7);
const currentMonth = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit' })
	.formatToParts().filter(part => part.type === 'year' || part.type === 'month').map(part => part.value).join('-');
const month = ref(currentMonth);
const cloudUsage = ref<CloudUsage>();
const billing = ref<Bill>();
const usageBusy = ref(false);
const billingBusy = ref(false);
const cloudUsageError = ref('');
const billingError = ref('');
let querySerial = 0;

function counts(groups: Group[]): Counts {
	const values = { attempts: 0, accepted: 0, blocked: 0, verified: 0, failed: 0, unknown: 0, verifyFailed: 0 };
	for (const row of groups) {
		if (attemptResults.has(row.result)) values.attempts += row.total;
		if (row.result === 'ACCEPTED') values.accepted += row.total;
		if (blockedResults.has(row.result)) values.blocked += row.total;
		if (row.result === 'VERIFIED') values.verified += row.total;
		if (row.result === 'REJECTED') values.failed += row.total;
		if (['RESERVED', 'UNKNOWN'].includes(row.result)) values.unknown += row.total;
		if (['INCORRECT', 'VERIFY_ERROR'].includes(row.result)) values.verifyFailed += row.total;
	}
	return values;
}

const totals = computed(() => counts(overview.value?.groups ?? []));
const today = computed(() => counts(overview.value?.groups.filter(row => row.dayKey === overview.value?.today) ?? []));
const warning = computed(() => {
	const data = overview.value;
	if (!data) return '';
	const notes: string[] = [];
	if (data.dailyWarningThreshold > 0 && today.value.attempts >= data.dailyWarningThreshold) {
		notes.push('今日发送请求已达到预警值 ' + format(data.dailyWarningThreshold) + ' 次');
	}
	if (data.monthlyWarningThreshold > 0 && data.monthAttempts >= data.monthlyWarningThreshold) {
		notes.push('本月发送请求已达到预警值 ' + format(data.monthlyWarningThreshold) + ' 次');
	}
	return notes.join('，');
});
const sceneRows = computed(() => Object.entries(scenes).map(([key, label]) => ({
	label, ...counts(overview.value?.groups.filter(row => row.scene === key) ?? [])
})).filter(row => row.attempts || row.verified || row.blocked)
	.sort((a, b) => b.attempts - a.attempts || b.verified - a.verified));
const chart = computed<EChartsOption>(() => {
	const data = overview.value;
	if (!data) return {};
	const first = Date.parse(data.from);
	const length = (Date.parse(data.today) - first) / 86_400_000 + 1;
	const dates = Array.from({ length }, (_, i) => new Date(first + i * 86_400_000).toISOString().slice(0, 10));
	const daily = dates.map(date => counts(data.groups.filter(row => row.dayKey === date)));
	return {
		animation: false, color: ['#5276dc', '#228f76', '#ca7b34'],
		grid: { left: 12, right: 18, top: 12, bottom: 40, containLabel: true },
		tooltip: { trigger: 'axis', renderMode: 'richText' }, legend: { bottom: 0, textStyle: { color: 'inherit' } },
		xAxis: { type: 'category', data: dates.map(date => date.slice(5)), axisLabel: { color: 'inherit', hideOverlap: true } },
		yAxis: { type: 'value', minInterval: 1, axisLabel: { color: 'inherit' }, splitLine: { lineStyle: { color: '#7f8da333' } } },
		aria: { enabled: true },
		series: [
			{ name: '发送请求', type: 'bar', barMaxWidth: 20, data: daily.map(row => row.attempts) },
			{ name: '核验通过', type: 'bar', barMaxWidth: 20, data: daily.map(row => row.verified) },
			{ name: '限额拦截', type: 'bar', barMaxWidth: 20, data: daily.map(row => row.blocked) }
		]
	};
});

async function refreshLocal() {
	const serial = ++querySerial;
	const active = tab.value;
	localError.value = '';
	localBusy.value = allowed.value && active !== 'cloud';
	if (!localBusy.value) return;
	try {
		if (active === 'overview') {
			const data: Overview = await featureApi('sms/usage/overview?days=' + days.value);
			if (serial === querySerial) overview.value = data;
		} else if (active === 'events') {
			const params = new URLSearchParams({ days: String(days.value), page: String(page.value), size: String(pageSize) });
			if (scene.value) params.set('scene', scene.value);
			if (kind.value) params.set('kind', kind.value);
			if (result.value) params.set('result', result.value);
			const data = await featureApi('sms/usage/events?' + params);
			if (serial === querySerial) events.value = data;
		} else {
			const data: Limits = await featureApi('sms/usage/limits');
			if (serial === querySerial) savedLimit.value = dailyLimit.value = data.dailyLimit;
		}
	} catch (error) {
		if (serial === querySerial) localError.value = message(error, '短信记录暂时无法加载，请刷新后重试');
	} finally {
		if (serial === querySerial) localBusy.value = false;
	}
}

watch([tab, allowed, days, scene, kind, result], () => {
	page.value = 1;
	void refreshLocal();
}, { immediate: true });

let refreshTimer: ReturnType<typeof setInterval> | undefined;
function startRefresh() {
	if (refreshTimer) return;
	refreshTimer = setInterval(() => {
		if (!document.hidden && allowed.value && !localBusy.value && ['overview', 'events'].includes(tab.value)) void refreshLocal();
	}, 15_000);
}
function stopRefresh() { clearInterval(refreshTimer); refreshTimer = undefined; }
onMounted(startRefresh);
onActivated(startRefresh);
onDeactivated(stopRefresh);
onUnmounted(stopRefresh);

async function save() {
	if (!canManage.value || saving.value || dailyLimit.value === undefined) return;
	saving.value = true;
	try {
		const data: Limits = await featureApi('sms/usage/limits', { dailyLimit: dailyLimit.value });
		savedLimit.value = dailyLimit.value = data.dailyLimit;
		ElMessage.success('发送上限已保存，下次请求立即生效');
	} catch (error) {
		ElMessage.error(message(error, '发送上限保存失败，请重试'));
	} finally {
		saving.value = false;
	}
}

function message(error: unknown, fallback: string) {
	if (typeof error === 'object' && error !== null && 'message' in error && typeof error.message === 'string') return error.message;
	return fallback;
}

function futureMonth(date: Date) {
	return date.getFullYear() * 12 + date.getMonth() + 1 > Number(currentMonth.slice(0, 4)) * 12 + Number(currentMonth.slice(5));
}

async function loadReceipts() {
	if (receiptBusy.value) return;
	receiptBusy.value = true;
	receiptError.value = '';
	try { delivery.value = await featureApi('sms/usage/receipts?days=' + days.value); }
	catch (error) { receiptError.value = message(error, '送达回执暂不可用，不影响次数限制与使用记录'); }
	finally { receiptBusy.value = false; }
}

async function loadCloudUsage() {
	usageBusy.value = true;
	cloudUsageError.value = '';
	try { cloudUsage.value = await featureApi('sms/usage/cloud/usage?days=' + cloudDays.value); }
	catch (error) { cloudUsageError.value = message(error, '阿里云用量查询失败，请重试'); }
	finally { usageBusy.value = false; }
}

async function loadBilling() {
	billingBusy.value = true;
	billingError.value = '';
	try { billing.value = await featureApi('sms/usage/cloud/billing?month=' + encodeURIComponent(month.value)); }
	catch (error) { billingError.value = message(error, '阿里云账单查询失败，请重试'); }
	finally { billingBusy.value = false; }
}
</script>

<style scoped lang="scss">
.sms-content { padding: 4px 18px 24px; }
.sms-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-bottom: 14px; }
.sms-toolbar :deep(.el-select) { width: min(155px, 100%); }
.sms-muted { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.55; }
.sms-secondary { display: block; overflow: hidden; text-overflow: ellipsis; }
.sms-number, .sms-totals dd { font-variant-numeric: tabular-nums; }
.sms-totals {
	display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 12px 0 20px;
	border-block: 1px solid var(--el-border-color-lighter); background: var(--el-fill-color-lighter);
}
.sms-totals div { padding: 15px 18px; }
.sms-totals div + div { border-inline-start: 1px solid var(--el-border-color-lighter); }
.sms-totals dt { color: var(--el-text-color-secondary); font-size: 13px; }
.sms-totals dd { margin: 5px 0 0; font-size: clamp(22px, 2vw, 32px); color: var(--el-text-color-primary); font-weight: 700; }
.sms-breakdown, .sms-cloud { display: grid; gap: 18px; }
.sms-breakdown { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.sms-cloud { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.sms-cloud > p { grid-column: 1 / -1; margin: 0; }
.sms-section { min-width: 0; border-top: 1px solid var(--el-border-color-lighter); padding-top: 12px; }
.sms-section h3, .sms-settings h3 { margin: 0 0 12px; font-size: 16px; }
.sms-chart { width: 100%; height: 260px; color: var(--el-text-color-secondary); }
.sms-pagination { margin-top: 16px; justify-content: flex-end; }
.sms-settings { max-width: 650px; }
.sms-settings p { line-height: 1.65; }
.sms-settings .el-alert + .el-button { margin-top: 12px; }
.sms-bill strong { margin-inline-start: 8px; font-size: 19px; }
@media (max-width: 1080px) {
	.sms-breakdown, .sms-cloud { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 640px) {
	.sms-content { padding-inline: 12px; }
	.sms-totals { grid-template-columns: repeat(2, minmax(0, 1fr)); }
	.sms-totals div { padding: 10px; }
	.sms-totals div:nth-child(3) { border-inline-start: 0; }
	.sms-totals div:nth-child(n + 3) { border-top: 1px solid var(--el-border-color-lighter); }
}
</style>
