<template>
	<TradeRecords purchase summary-only :page-api="page" :info-api="info" />
</template>

<script setup lang="ts">
import TradeRecords from '../components/trade-records.vue';
import { merchantRequest } from '/@/merchant-api';
import { purchaseStates, type RecordRow } from '../record-model';

defineOptions({ name: 'merchant-orders' });

function orderRow(order: RecordRow) {
	return {
		...order,
		createdAt: order.createTime,
		status: order.orderStatus,
		statusText:
			purchaseStates.find(state => state.value === order.orderStatus)?.label || '未知状态'
	};
}
async function page(params: RecordRow) {
	const result = await merchantRequest('workspace/order/page', params);
	return { list: (result.records ?? []).map(orderRow), total: result.totalRow ?? 0 };
}
async function info(id: number) {
	const result = await merchantRequest('workspace/order/info?id=' + encodeURIComponent(id));
	return { ...orderRow(result.order), items: result.items };
}
</script>
