<template>
	<WorkbenchLayout title="验券与核销" context="读取顾客的券码，核对适用门店及优惠权益。">
		<div v-if="uncertain" class="workbench-inline-alert">
			<el-alert
				title="上次请求结果尚未确认。请查询原账单或重试原请求。"
				type="warning"
				show-icon
				:closable="false"
			/>
		</div>
		<div class="workbench-grid redemption-grid">
			<aside class="workbench-stack">
				<section class="workbench-panel" aria-labelledby="redemption-store-title">
					<header class="workbench-section-head"><h2 id="redemption-store-title">本次消费门店</h2></header>
					<div class="redemption-side">
						<el-skeleton v-if="initialLoading" :rows="1" animated />
						<div v-else-if="identity === 'STORE'" class="store-scope">
							<el-icon aria-hidden="true"><Location /></el-icon>
							<strong>{{ stores.find(s => s.id === storeId)?.name || '暂无可核销门店' }}</strong>
						</div>
						<el-select v-else v-model="storeId" aria-label="本次消费门店" placeholder="选择营业门店" filterable :disabled="!!inspection || !!order || busy || uncertain || !!initialError">
							<el-option v-for="s in stores" :key="s.id" :value="s.id" :label="s.name + (s.status === 1 ? '' : '（当前非营业）')" :disabled="s.status !== 1" />
						</el-select>
					</div>
				</section>
				<section class="workbench-panel" aria-labelledby="redemption-flow-title">
					<header class="workbench-section-head"><h2 id="redemption-flow-title">处理进度</h2></header>
					<ol class="redemption-flow">
						<li :aria-current="!inspection && !order ? 'step' : undefined"><el-icon><FullScreen /></el-icon><span><strong>读取券码</strong><small>扫描或手动输入</small></span></li>
						<li :aria-current="inspection && !order ? 'step' : undefined"><el-icon><Document /></el-icon><span><strong>核对优惠权益</strong><small>检查券类型和有效期</small></span></li>
						<li :aria-current="order ? 'step' : undefined"><el-icon><component :is="order?.status === 'COMPLETED' ? CircleCheck : CreditCard" /></el-icon><span><strong>完成处理</strong><small>确认兑换或由顾客付款</small></span></li>
					</ol>
				</section>
			</aside>
			<section class="workbench-panel redemption-console" aria-labelledby="console-title">
				<header class="workbench-section-head">
					<div><h2 id="console-title">{{ order ? '消费订单' : inspection ? '确认券信息' : '读取券码' }}</h2>
						<p>{{ order ? order.orderNo : inspection ? '核对以下权益与有效期，再继续操作。' : '使用扫码枪或手动输入顾客出示的券码。' }}</p>
					</div>
					<el-tag v-if="order" :type="order.status === 'COMPLETED' ? 'success' : order.status === 'PENDING' ? 'warning' : 'info'">{{ order.statusText }}</el-tag>
				</header>
				<div v-if="error" class="console-alert" role="alert"><el-alert :title="error" type="error" :closable="false" show-icon /></div>
				<el-skeleton v-if="initialLoading && !order" :rows="4" animated class="console-body" aria-label="正在加载门店" />
				<ManagementState v-else-if="initialError" title="门店资料加载失败" kind="error" :description="initialError" @retry="loadScope" />
				<div v-else-if="order" class="console-body">
					<div class="bill-total"><span>应补款</span><strong>{{ money(order.payableAmount) }} 元</strong></div>
					<dl class="redemption-facts">
						<div v-if="order.consumptionAmount != null"><dt>消费金额</dt><dd>{{ money(order.consumptionAmount) }} 元</dd></div>
						<div><dt>优惠金额</dt><dd>{{ money(order.discountAmount) }} 元</dd></div>
						<div><dt>消费门店</dt><dd>{{ order.storeName }}</dd></div>
						<div><dt>优惠券</dt><dd>{{ order.couponName }}</dd></div>
					</dl>
				</div>
				<div v-else-if="inspection" class="console-body">
					<div class="inspected-coupon">
						<div><h3>{{ inspection.couponName || inspection.name || '优惠券' }}</h3><p v-if="inspection.description">{{ inspection.description }}</p></div>
						<el-tag v-if="inspection.typeText">{{ inspection.typeText }}</el-tag>
					</div>
					<dl class="redemption-facts">
						<div v-if="inspection.contents?.length">
							<dt>权益内容</dt>
							<dd v-if="Array.isArray(inspection.contents)">
								<ul class="voucher-contents">
									<li v-for="(item, index) in inspection.contents" :key="index">
										{{ item.name || '兑换项目' }} × {{ item.quantity ?? 1 }}
										<small v-if="item.itemDescription">{{ item.itemDescription }}</small>
									</li>
								</ul>
							</dd>
							<dd v-else>{{ inspection.contents }}</dd>
						</div>
						<div><dt>有效期</dt><dd>{{ inspection.validFrom }} 至 {{ inspection.validTo }}</dd></div>
					</dl>
					<el-alert v-if="inspection.requiresConsumptionAmount" title="金额类优惠券在消费开单后选用，由顾客确认付款。" type="info" :closable="false" show-icon />
					<el-form v-else label-position="top">
						<el-form-item label="消费备注（可选）">
							<el-input v-model="remark" type="textarea" :rows="2" :disabled="uncertain || busy" placeholder="填写本次消费备注" maxlength="200" show-word-limit />
						</el-form-item>
					</el-form>
				</div>
				<el-form v-else class="console-body" label-position="top" @submit.prevent="inspect">
					<el-form-item label="券码（支持扫码枪输入）">
						<el-input v-model="code" :prefix-icon="FullScreen" placeholder="扫描或输入券码" :disabled="busy" maxlength="2048" autocomplete="off" class="code-input" />
					</el-form-item>
					<p v-if="!storeId" class="console-note">请先选择本次消费门店。</p>
					<div class="scan-controls">
						<el-button type="primary" native-type="submit" :loading="busy" :disabled="!code.trim() || !storeId || uncertain">读取券信息</el-button>
						<el-button v-if="code" :disabled="busy || uncertain" @click="reset">清空券码</el-button>
					</div>
				</el-form>
				<footer v-if="inspection || order" class="console-footer">
					<el-button v-if="uncertain && !order" type="primary" :loading="busy" @click="prepare">重试原请求</el-button>
					<template v-else-if="order">
						<el-button v-if="order.status === 'PENDING'" type="primary" :loading="busy" :disabled="uncertain" @click="confirm">转为顾客确认付款</el-button>
						<el-button v-if="order.status === 'PENDING'" :disabled="uncertain || busy" @click="cancel">取消账单</el-button>
						<el-button :loading="busy" @click="recover">刷新原账单结果</el-button>
					</template>
					<el-button v-else-if="inspection.requiresConsumptionAmount" type="primary" :disabled="busy" @click="openSettlement">前往消费开单</el-button>
					<el-button v-else type="primary" :loading="busy" @click="prepare">确认兑换并自动生成订单</el-button>
					<el-button :disabled="busy || uncertain || order?.status === 'PENDING'" @click="reset">开始下一笔</el-button>
				</footer>
			</section>

		</div>
	</WorkbenchLayout>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { CircleCheck, CreditCard, Document, FullScreen, Location } from '@element-plus/icons-vue';
import WorkbenchLayout from '../components/workbench-layout.vue';
import ManagementState from '../components/management-state.vue';
import { useRouter } from 'vue-router';
import { featureApi } from '../feature-api';
import { ElMessage, ElMessageBox } from 'element-plus';
import { merchantRequest } from '/@/merchant-api';
import { workspaceApi } from '../api';
import { identity } from '/@/workspace';
import { storage } from '/@/cool/utils';
const router = useRouter();
function openSettlement() {
	void router.push('/' + (identity === 'STORE' ? 'store' : 'merchant') + '/wallet');
}
const stores = ref<any[]>([]),
	storeId = ref<number>(),
	code = ref(''),
	inspection = ref<any>(),
	order = ref<any>(),
	amount = ref(''),
	remark = ref(''),
	received = ref(false),
	busy = ref(false),
	uncertain = ref(false),
	error = ref(''),
	requestId = ref(''),
	userId = ref<number>();
const money = (v: number | null) => (v == null ? '—' : (v / 100).toFixed(2));
function persist() {
	storage.set('redemption.pending', {
		userId: userId.value,
		storeId: storeId.value,
		code: code.value,
		inspection: inspection.value,
		order: order.value,
		amount: amount.value,
		remark: remark.value,
		requestId: requestId.value,
		uncertain: uncertain.value
	});
}
async function run(action: () => Promise<void>, write = false) {
	if (busy.value) return;
	busy.value = true;
	error.value = '';
	try {
		if (write) {
			uncertain.value = true;
			persist();
		}
		await action();
		uncertain.value = false;
		persist();
	} catch (e: any) {
		error.value = e.message || '操作未完成';
		if (write && e.business === true) {
			uncertain.value = false;
			if (!order.value) requestId.value = crypto.randomUUID();
		}
		if (write) persist();
	} finally {
		busy.value = false;
	}
}
async function inspect() {
	if (!storeId.value || !code.value.trim() || inspection.value || order.value || uncertain.value) return;
	await run(async () => {
		const result = await merchantRequest('redemption/inspect', {
			storeId: storeId.value,
			code: code.value.trim()
		});
		if (
			!['ENTER_BILL', 'CONFIRM_CONTENT'].includes(result.nextAction) ||
			typeof result.requiresConsumptionAmount !== 'boolean'
		)
			throw new Error('券类型无法确认，请联系管理员');
		inspection.value = result;
		requestId.value = crypto.randomUUID();
	});
}
function cents() {
	if (!/^(0|[1-9]\d{0,7})(\.\d{1,2})?$/.test(amount.value.trim()))
		throw new Error('请输入有效金额，最多两位小数');
	const [whole, decimal = ''] = amount.value.trim().split('.');
	return Number(whole) * 100 + Number(decimal.padEnd(2, '0'));
}
async function prepare() {
	if (!inspection.value) return;
	try {
		const input = {
			storeId: storeId.value,
			code: code.value.trim(),
			remark: remark.value,
			requestId: requestId.value,
			consumptionAmount: inspection.value.requiresConsumptionAmount ? cents() : null
		};
		if (!uncertain.value && !inspection.value.requiresConsumptionAmount)
			await ElMessageBox.confirm(
				'确认兑换券内权益？确认后生成消费订单并完成核销。',
				'兑换确认'
			);
		await run(async () => {
			order.value = await merchantRequest(
				inspection.value.requiresConsumptionAmount
					? 'consumption/prepare'
					: 'consumption/redeemContent',
				input
			);
			ElMessage.success(order.value.status === 'COMPLETED' ? '已完成核销' : '账单已生成');
		}, true);
	} catch (e: any) {
		if (e?.message) error.value = e.message;
	}
}
async function confirm() {
	await run(async () => {
		const bill = await featureApi('wallet/bills/import', { orderId: order.value.id });
		await router.push({ path: '/' + (identity === 'STORE' ? 'store' : 'merchant') + '/wallet', query: { billId: bill.id } });
	}, true);
}
async function cancel() {
	try {
		await ElMessageBox.confirm('确认取消待结算账单？', '取消账单');
		await run(async () => {
			order.value = await merchantRequest('consumption/cancel', { orderId: order.value.id });
		}, true);
	} catch (e: any) {
		if (e?.message) error.value = e.message;
	}
}
async function recover() {
	if (!order.value) return;
	await run(async () => {
		order.value = await merchantRequest(`consumption/info?id=${order.value.id}`);
	});
}
function reset() {
	inspection.value = undefined;
	order.value = undefined;
	code.value = '';
	amount.value = '';
	remark.value = '';
	received.value = false;
	requestId.value = '';
	error.value = '';
	uncertain.value = false;
	storage.remove('redemption.pending');
}
const initialLoading = ref(false);
const initialError = ref('');
async function loadScope() {
	if (initialLoading.value) return;
	initialLoading.value = true;
	initialError.value = '';
	try {
		const me = await workspaceApi('me');
		userId.value = me.id;
		stores.value = await workspaceApi('stores');
		storeId.value = me.storeId || me.defaultStoreId;
		const saved = storage.get('redemption.pending');
		if (saved && saved.userId === me.id) {
			storeId.value = saved.storeId;
			code.value = saved.code;
			inspection.value = saved.inspection;
			order.value = saved.order;
			amount.value = saved.amount;
			remark.value = saved.remark;
			requestId.value = saved.requestId;
			uncertain.value = saved.uncertain;
			if (order.value) await recover();
		}
	} catch (e: any) {
		initialError.value = e.message || '门店资料加载失败，请重试。';
	} finally {
		initialLoading.value = false;
	}
}
onMounted(loadScope);
</script>

<style scoped>
.redemption-grid { grid-template-columns: minmax(0, 1.9fr) minmax(260px, 1fr); }
.redemption-grid .workbench-stack { grid-column: 2; grid-row: 1; }
.redemption-console { grid-column: 1; grid-row: 1; }
.redemption-console .workbench-section-head { padding-block: 18px; border-block-end: 1px solid var(--el-border-color-lighter); }
.console-body { box-sizing: border-box; padding: 20px 18px; }
.console-alert { margin: 12px 18px 0; }
.console-body .el-form-item { margin-bottom: 12px; }
.console-body :deep(.code-input .el-input__wrapper) { min-height: 48px; font-size: 16px; }
.console-note { margin: 2px 0 10px; color: var(--el-text-color-regular); font-size: 13px; }
.console-footer, .scan-controls { display: flex; flex-wrap: wrap; gap: 8px; }
.console-footer { padding: 13px 18px; border-block-start: 1px solid var(--el-border-color-lighter); background: var(--el-fill-color-light); }
.console-footer .el-button, .scan-controls .el-button { margin: 0; }
.redemption-side { padding: 0 18px 16px; }
.redemption-side .el-select { width: 100%; }
.store-scope {
	display: flex;
	align-items: center;
	gap: 8px;
	padding: 12px;
	border-radius: 6px;
	background: var(--workspace-selected);
	color: var(--workspace-accent-text);
	overflow-wrap: anywhere;
}
.store-scope .el-icon { flex: none; }
.redemption-flow { margin: 0; padding: 0 18px 14px; list-style: none; }
.redemption-flow li { display: flex; align-items: center; gap: 12px; padding: 10px 8px; border-block-start: 1px solid var(--el-border-color-lighter); }
.redemption-flow li[aria-current] { border-color: transparent; border-radius: 6px; background: var(--workspace-selected); }
.redemption-flow li .el-icon { display: grid; place-items: center; width: 34px; height: 34px; color: var(--workspace-accent-text); }
.redemption-flow strong { display: block; font-size: 14px; }
.redemption-flow small { display: block; margin-top: 4px; color: var(--el-text-color-regular); }
.inspected-coupon, .bill-total {
	display: flex;
	justify-content: space-between;
	align-items: start;
	gap: 12px;
	padding: 14px 16px;
	border-radius: 6px;
	background: var(--workspace-selected);
}
.inspected-coupon h3 { margin: 0; font-size: 20px; overflow-wrap: anywhere; }
.inspected-coupon p { margin: 6px 0 0; color: var(--el-text-color-regular); overflow-wrap: anywhere; }
.bill-total strong { font-size: 24px; }
.redemption-facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(200px, 100%), 1fr)); margin: 12px 0; }
.redemption-facts > div { padding: 8px 12px 8px 0; border-block-end: 1px solid var(--el-border-color-lighter); }
.redemption-facts dt { color: var(--el-text-color-regular); font-size: 12px; }
.redemption-facts dd { margin: 4px 0 0; overflow-wrap: anywhere; }
@media (max-width: 960px) {
	.redemption-grid { grid-template-columns: minmax(0, 1fr); }
	/* Store selection stays first when the two columns stack. */
	.redemption-grid .workbench-stack { grid-column: 1; grid-row: 1; }
	.redemption-console { grid-row: 2; }
	.redemption-grid .workbench-stack > section + section { display: none; }
}
.voucher-contents { margin: 0; padding-inline-start: 18px; }
.voucher-contents li + li { margin-block-start: 4px; }
.voucher-contents small { display: block; color: var(--el-text-color-regular); }
</style>
