<template>
	<div class="redeem" v-loading="busy">
		<h2>验券与核销</h2>
		<el-alert v-if="error" :title="error" type="error" :closable="false" /><el-alert
			v-if="uncertain"
			title="上次请求结果尚未确认。请查询原账单或重试原请求，不要换券重复操作。"
			type="warning"
			:closable="false"
		/><el-form label-position="top"
			><el-form-item label="本次消费门店"
				><el-select
					v-model="storeId"
					:disabled="!!inspection || !!order || identity === 'STORE'"
					filterable
					><el-option
						v-for="s in stores"
						:key="s.id"
						:value="s.id"
						:label="s.name + (s.status === 1 ? '' : '（当前非营业）')"
						:disabled="s.status !== 1" /></el-select></el-form-item
			><el-form-item label="券码（支持扫码枪输入）"
				><el-input
					v-model="code"
					:disabled="!!inspection || !!order"
					@keyup.enter="inspect"
					maxlength="2048" /></el-form-item
			><el-button
				v-if="!inspection && !order"
				type="primary"
				:disabled="!code || !storeId"
				@click="inspect"
				>读取券信息</el-button
			></el-form
		><el-card v-if="inspection" shadow="never"
			><template #header
				>{{ inspection.couponName || inspection.name || '优惠券' }} ·
				{{ inspection.typeText }}</template
			>
			<p>{{ inspection.description }}</p>
			<p v-if="inspection.contents">权益内容：{{ inspection.contents }}</p>
			<p>有效期：{{ inspection.validFrom }} 至 {{ inspection.validTo }}</p>
			<template v-if="inspection.requiresConsumptionAmount">
				<el-alert title="金额类优惠券在消费开单后选用，由顾客确认付款。" type="info" :closable="false" />
				<el-button @click="openSettlement">前往消费开单</el-button>
			</template
			><el-input
				v-model="remark"
				:disabled="!!order || uncertain"
				placeholder="消费备注（可选）"
				maxlength="200"
				show-word-limit
			/><el-button v-if="!order && !inspection.requiresConsumptionAmount" style="margin-top: 16px" type="primary" @click="prepare">{{
				inspection.requiresConsumptionAmount ? '生成消费账单' : '确认兑换并自动生成订单'
			}}</el-button></el-card
		><el-card v-if="order" shadow="never"
			><template #header>消费订单 {{ order.orderNo }}</template
			><el-descriptions :column="1" border
				><el-descriptions-item label="状态">{{ order.statusText }}</el-descriptions-item
				><el-descriptions-item label="消费金额">{{
					money(order.consumptionAmount)
				}}</el-descriptions-item
				><el-descriptions-item label="优惠金额">{{
					money(order.discountAmount)
				}}</el-descriptions-item
				><el-descriptions-item label="应补款">{{
					money(order.payableAmount)
				}}</el-descriptions-item
				><el-descriptions-item label="门店">{{ order.storeName }}</el-descriptions-item
				><el-descriptions-item label="券">{{
					order.couponName
				}}</el-descriptions-item></el-descriptions
			><template v-if="order.status === 'PENDING'"
				>
				<div class="actions">
					<el-button
						type="primary"
						:disabled="uncertain"
						@click="confirm"
						>转为顾客确认付款</el-button
					><el-button :disabled="uncertain" @click="cancel">取消账单</el-button>
				</div></template
			><el-button style="margin-top: 16px" @click="recover"
				>刷新原账单结果</el-button
			></el-card
		>
		<div class="actions">
			<el-button v-if="uncertain && !order" @click="prepare">重试原请求</el-button
			><el-button :disabled="busy || uncertain || order?.status === 'PENDING'" @click="reset"
				>开始下一笔</el-button
			>
		</div>
	</div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue';
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
onMounted(async () => {
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
		error.value = e.message;
	}
});
</script>
<style scoped>
.redeem {
	max-width: 900px;
	padding: 24px;
	height: 100%;
	overflow: auto;
	box-sizing: border-box;
}
.el-select {
	width: 100%;
}
.el-card,
.el-alert {
	margin-bottom: 20px;
}
.actions {
	display: flex;
	gap: 12px;
	margin-top: 20px;
}
</style>
