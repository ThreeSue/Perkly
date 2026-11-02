<template>
	<ManagementShell
		:title="isPlatform ? '平台储值账务' : '储值消费账单'"
		description="金额按人民币元展示；储值支付与模拟充值保留独立流水，不提供退款"
	>
		<el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
		<div class="management-toolbar">
			<el-radio-group
				v-model="tab"
				@change="
					page = 1;
					load();
				"
			>
				<el-radio-button v-if="isPlatform" value="accounts">储值账户</el-radio-button>
				<el-radio-button v-if="!isPlatform" value="bills">无券消费账单</el-radio-button>
				<el-radio-button value="transactions">储值流水</el-radio-button>
			</el-radio-group>
			<el-button v-if="!isPlatform && canCreate" type="primary" @click="open"
				>开消费账单</el-button
			>
			<el-button :loading="busy" @click="load">刷新</el-button>
		</div>
		<el-table v-if="tab === 'accounts'" v-loading="busy" :data="rows" empty-text="暂无储值账户"
			><el-table-column prop="customerName" label="顾客" /><el-table-column
				label="当前余额（元）"
				><template #default="{ row }">{{ money(row.balance) }}</template></el-table-column
			><el-table-column prop="updateTime" label="更新时间"
		/></el-table>
		<el-table
			v-else-if="tab === 'transactions' || isPlatform"
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
				><template #default="{ row }">{{
					row.status === 'SUCCESS' ? '成功' : '失败'
				}}</template></el-table-column
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
		<el-table v-else v-loading="busy" :data="rows" empty-text="暂无消费账单"
			><el-table-column prop="merchantName" label="商家" min-width="130" /><el-table-column
				prop="storeName"
				label="门店"
				min-width="130"
			/><el-table-column label="金额" width="110"
				><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column
			><el-table-column label="状态" width="100"
				><template #default="{ row }">{{
					statuses[row.status] || row.status
				}}</template></el-table-column
			><el-table-column
				prop="remark"
				label="备注"
				min-width="150"
				show-overflow-tooltip
			/><el-table-column prop="createTime" label="开单时间" width="180" /><el-table-column
				label="操作"
				width="180"
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
		<el-pagination
			v-model:current-page="page"
			:page-size="20"
			:total="total"
			layout="total, prev, pager, next"
			@current-change="load"
		/>
		<el-dialog
			v-model="visible"
			title="开具无券直接消费账单"
			width="min(600px,94vw)"
			:close-on-click-modal="false"
			><el-form label-position="top" :disabled="saving"
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
					><el-input v-model="form.remark" maxlength="200" /></el-form-item
				><el-button type="primary" :loading="saving" @click="save"
					>确认开单</el-button
				></el-form
			></el-dialog
		>
		<el-dialog v-model="billVisible" title="顾客储值支付账单" width="min(640px,94vw)"
			><template v-if="bill"
				><h3>{{ bill.storeName }}</h3>
				<p>消费金额：{{ money(bill.amount) }} 元</p>
				<p>状态：{{ statuses[bill.status] }}</p>
				<el-alert
					title="请顾客在移动端确认门店、原额及活动后应付金额，再进行支付。"
					type="info"
					:closable="false"
				/>
				<p class="bill-code">{{ bill.billNo }}</p>
				<el-button @click="copyBill">复制支付账单编号</el-button></template
			></el-dialog
		>
	</ManagementShell>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { isPlatform } from '/@/workspace';
import { useBase } from '/$/base';
import { featureApi } from '../feature-api';
import ManagementShell from '../components/management-shell.vue';
const { menu } = useBase();
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
async function load() {
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
		rows.value = r.list;
		total.value = r.total;
	} catch (e: any) {
		rows.value = [];
		error.value = e.message;
	} finally {
		busy.value = false;
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
onMounted(load);
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
