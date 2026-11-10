<template>
	<ManagementShell register
		:title="isPlatform ? '平台储值账务' : '储值消费账单'"
		description="先开消费账单，可选用券，再由顾客确认普通模拟支付或储值支付"
	>
		<div class="management-toolbar">
			<el-radio-group
				v-model="tab"
				@change="
					page = 1;
					load();
				"
			>
				<el-radio-button v-if="isPlatform" value="accounts">储值账户</el-radio-button>
				<el-radio-button v-if="!isPlatform" value="bills">消费结算账单</el-radio-button>
				<el-radio-button value="transactions">储值流水</el-radio-button>
			</el-radio-group>
			<el-button v-if="!isPlatform && canCreate" type="primary" @click="open"
				>开消费账单</el-button
			>
			<el-button :loading="busy" @click="load">刷新</el-button>
		</div>
		<ManagementState v-if="error" kind="error" title="账务记录加载失败" :description="error" @retry="load" />
		<el-table class="management-table" :height="registerHeight" v-else-if="tab === 'accounts'" v-loading="busy" :data="rows" empty-text="暂无储值账户"
			><el-table-column prop="customerName" label="顾客" /><el-table-column
				label="当前余额（元）"
				><template #default="{ row }">{{ money(row.balance) }}</template></el-table-column
			><el-table-column prop="updateTime" label="更新时间"
		/></el-table>
		<el-table
			v-else-if="tab === 'transactions' || isPlatform"
			class="management-table" :height="registerHeight"
			v-loading="busy"
			:data="rows"
			empty-text="暂无储值流水"
			><el-table-column prop="customerName" label="顾客" width="140" /><el-table-column
				prop="merchantName"
				label="商家"
				min-width="140" /><el-table-column
				prop="storeName"
				label="门店"
				min-width="140" /><el-table-column label="类型" width="100"
				><template #default="{ row }">{{
					types[row.type] || row.type
				}}</template></el-table-column
			><el-table-column label="状态" width="90"
				><template #default="{ row }"><el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'">{{ row.status === 'SUCCESS' ? '成功' : '失败' }}</el-tag></template></el-table-column
			><el-table-column label="原额" width="110"
				><template #default="{ row }">{{
					money(row.originalAmount)
				}}</template></el-table-column
			><el-table-column label="实际金额" width="110"
				><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column
			><el-table-column label="优惠" width="100"
				><template #default="{ row }">{{
					money(row.discountAmount)
				}}</template></el-table-column
			><el-table-column
				prop="activityTitle"
				label="优惠活动"
				min-width="140" /><el-table-column v-if="isPlatform" label="交易后余额" width="120"
				><template #default="{ row }">{{
					money(row.balanceAfter)
				}}</template></el-table-column
			><el-table-column prop="createTime" label="时间" width="180"
		/></el-table>
		<el-table v-else class="management-table" :height="registerHeight" v-loading="busy" :data="rows" empty-text="暂无消费账单"
			><el-table-column prop="merchantName" label="商家" min-width="130" /><el-table-column
				prop="storeName"
				label="门店"
				min-width="130"
			/><el-table-column label="金额" width="110"
				><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column
			><el-table-column label="状态" width="100"
				><template #default="{ row }"><el-tag :type="billTone(row.status)">{{ statuses[row.status] || row.status }}</el-tag></template></el-table-column
			><el-table-column label="券优惠" width="110">
				<template #default="{ row }">{{ money(row.couponDiscount) }}</template>
			</el-table-column><el-table-column label="实付" width="110">
				<template #default="{ row }">{{ row.status === 'PAID' ? money(row.paidAmount) : '待付款' }}</template>
			</el-table-column><el-table-column
				prop="remark"
				label="备注"
				min-width="150"
				show-overflow-tooltip
			/><el-table-column prop="createTime" label="开单时间" width="180" /><el-table-column
				label="操作"
				width="180" fixed="right" align="right"
				><template #default="{ row }"
					><el-button link type="primary" @click="show(row)">出示账单</el-button
					><el-button
						v-if="row.status === 'PENDING' && canCreate"
						link
						type="danger"
						@click="cancel(row)"
						>取消</el-button
					></template
				></el-table-column
			></el-table
		>
		<div v-if="!error && total" class="management-pagination">
			<el-pagination
				v-model:current-page="page"
				:page-size="20"
				:total="total"
				layout="total, prev, pager, next"
				@current-change="load"
			/>
		</div>
		<el-drawer
			v-model="visible"
			append-to-body
			class="workspace-editor"
			title="开具无券直接消费账单"
			size="min(600px,100vw)"
			:close-on-click-modal="false"
			><el-form label-position="top" :disabled="saving" @submit.prevent="save"
				><el-form-item label="消费门店"
					><el-select v-model="form.storeId" filterable style="width: 100%"
						><el-option
							v-for="s in stores"
							:key="s.id"
							:value="s.id"
							:label="s.merchantName + ' / ' + s.name" /></el-select></el-form-item
				><el-form-item label="消费金额（元）"
					><el-input-number
						v-model="amountYuan"
						:min="0.01"
						:max="1000000"
						:precision="2" /></el-form-item
				><el-form-item label="备注"
					><el-input v-model="form.remark" maxlength="200" placeholder="选填，便于后续查账" /></el-form-item>
			</el-form>
			<template #footer>
				<el-button :disabled="saving" @click="visible = false">取消</el-button>
				<el-button type="primary" :loading="saving" @click="save">确认开单</el-button>
			</template>
		</el-drawer
		>
		<el-drawer v-model="billVisible" append-to-body title="消费结算账单" size="min(640px,100vw)"
			><template v-if="bill"
				><div class="bill-summary">
					<div><span>用券后应付</span><strong>{{ money(bill.remainingAmount ?? bill.amount) }} 元</strong></div>
					<el-tag effect="light" :type="billTone(bill.status)">{{ statuses[bill.status] || bill.status }}</el-tag>
				</div>
				<el-descriptions :column="1" border>
					<el-descriptions-item label="消费门店">{{ bill.storeName }}</el-descriptions-item>
					<el-descriptions-item label="消费金额">{{ money(bill.amount) }} 元</el-descriptions-item>
					<el-descriptions-item label="券优惠">{{ money(bill.couponDiscount) }} 元</el-descriptions-item>
				</el-descriptions>
				<p v-if="bill.status === 'PAID'">实付 {{ money(bill.paidAmount) }} 元 · {{ bill.paymentMode === 'SIMULATED' ? '普通模拟支付' : '储值支付' }}</p>
				<el-alert
					title="将账单编号交给顾客确认付款。储值支付先用券，再对剩余金额应用一个最优惠活动。"
					type="info"
					:closable="false"
				/>
				<p class="bill-code">{{ bill.billNo }}</p>
				<el-button @click="copyBill">复制支付账单编号</el-button>
				<el-button @click="refreshBill">刷新付款结果</el-button>
				<el-form v-if="bill.status === 'PENDING' && !bill.consumptionOrderId && canCreate" label-position="top" style="margin-top: 20px">
					<el-form-item label="选用金额类优惠券（可选）">
						<el-input v-model="couponCode" placeholder="输入或扫描顾客券码" />
					</el-form-item>
					<el-button :loading="saving" @click="applyCoupon">确认用券</el-button>
				</el-form>
			</template
			></el-drawer
		>
	</ManagementShell>
</template>
<style scoped>
.bill-summary {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12px;
	margin-bottom: 14px;
	padding: 14px 16px;
	border-radius: 10px;
	background: var(--workspace-neutral, var(--el-fill-color-light));
}
.bill-summary span { color: var(--el-text-color-regular); }
.bill-summary strong {
	display: block;
	margin-top: 4px;
	color: var(--workspace-ink, var(--el-text-color-primary));
	font-size: 24px;
	font-variant-numeric: tabular-nums;
}
</style>
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { isPlatform } from '/@/workspace';
import { useBase } from '/$/base';
import { featureApi } from '../feature-api';
import ManagementState from '../components/management-state.vue';
import ManagementShell from '../components/management-shell.vue';
import { useRegisterHeight } from '../composables/register-height';
const registerHeight = useRegisterHeight();
const { menu } = useBase();
const route = useRoute();
const couponCode = ref('');
async function refreshBill() {
	try {
		bill.value = await featureApi('wallet/bills/info?id=' + bill.value.id);
		await load();
	} catch (e: any) { ElMessage.error(e.message); }
}
async function applyCoupon() {
	if (!couponCode.value.trim()) return ElMessage.warning('请输入券码');
	saving.value = true;
	try {
		bill.value = await featureApi('wallet/bills/coupon', { id: bill.value.id, version: bill.value.version ?? 1, code: couponCode.value.trim() });
		couponCode.value = '';
		await load();
	} catch (e: any) { ElMessage.error(e.message); }
	finally { saving.value = false; }
}
const canCreate = computed(() => menu.perms.includes('redeem:use'));
const types: Record<string, string> = {
		RECHARGE: '模拟充值',
		DIRECT: '直接消费',
		PURCHASE: '购券支付'
	},
	statuses: Record<string, string> = { PENDING: '待支付', PAID: '已支付', CANCELLED: '已取消' };
const rows = ref<any[]>([]),
	stores = ref<any[]>([]),
	page = ref(1),
	total = ref(0),
	busy = ref(false),
	saving = ref(false),
	error = ref(''),
	tab = ref(isPlatform ? 'transactions' : 'bills'),
	visible = ref(false),
	amountYuan = ref(1),
	billVisible = ref(false),
	bill = ref<any>();
const form = ref({ requestId: '', storeId: undefined as number | undefined, remark: '' });
const money = (cents: number) => ((cents || 0) / 100).toFixed(2);
function billTone(status: string) {
	return status === 'PAID' ? 'success' : status === 'PENDING' ? 'warning' : 'info';
}
let listRevision = 0;
async function load() {
	const round = ++listRevision;
	busy.value = true;
	error.value = '';
	try {
		const path =
			tab.value === 'accounts'
				? 'wallet/accounts'
				: isPlatform || tab.value === 'transactions'
					? 'wallet/transactions'
					: 'wallet/bills/page';
		const r = await featureApi(`${path}?pageNumber=${page.value}&pageSize=20`);
		if (round !== listRevision) return;
		rows.value = r.list;
		total.value = r.total;
	} catch (e: any) {
		if (round !== listRevision) return;
		rows.value = [];
		total.value = 0;
		error.value = e.message;
	} finally {
		if (round === listRevision) busy.value = false;
	}
}
async function open() {
	try {
		stores.value = await featureApi('wallet/bills/stores');
		form.value = {
			requestId: crypto.randomUUID(),
			storeId: stores.value.length === 1 ? stores.value[0].id : undefined,
			remark: ''
		};
		amountYuan.value = 1;
		visible.value = true;
	} catch (e: any) {
		ElMessage.error(e.message);
	}
}
async function save() {
	if (saving.value) return;
	if (!form.value.storeId || amountYuan.value <= 0)
		return ElMessage.warning('请选择门店和有效消费金额');
	saving.value = true;
	try {
		const r = await featureApi('wallet/bills/create', {
			...form.value,
			amount: Math.round(amountYuan.value * 100)
		});
		visible.value = false;
		show(r);
		await load();
	} catch (e: any) {
		ElMessage.error(e.message);
	} finally {
		saving.value = false;
	}
}
function show(row: any) {
	couponCode.value = '';
	bill.value = row;
	billVisible.value = true;
}
async function copyBill() {
	try {
		await navigator.clipboard.writeText(bill.value.billNo);
		ElMessage.success('已复制');
	} catch {
		ElMessage.warning('请手动选中并复制账单编号');
	}
}
async function cancel(row: any) {
	try {
		await ElMessageBox.confirm('取消后顾客将无法支付该账单。', '取消消费账单');
	} catch {
		return;
	}
	try {
		await featureApi('wallet/bills/cancel', { id: row.id });
		await load();
	} catch (e: any) {
		ElMessage.error(e.message);
	}
}
onMounted(async () => {
	await load();
	if (!isPlatform && route.query.billId) {
		try { show(await featureApi('wallet/bills/info?id=' + Number(route.query.billId))); }
		catch (e: any) { ElMessage.error(e.message); }
	}
});
</script>
<style scoped>
.el-pagination {
	margin-top: 16px;
}
.bill-code {
	padding: 14px;
	background: var(--el-fill-color-light);
	overflow-wrap: anywhere;
	user-select: all;
}
</style>
