<template>
	<TradeRecords
		:purchase="true"
		:page-api="page"
		:info-api="info"
		:can-page="allowed('page')"
		:can-info="allowed('info')"
		><template v-if="canMerchants" #merchant-filter="{ form, change }"
			><el-form-item label="商家"
				><MerchantSelect
					v-model="form.merchantId"
					@change="change" /></el-form-item></template
	></TradeRecords>
</template>
<script setup lang="ts">
import TradeRecords from '/$/business/components/trade-records.vue';
import MerchantSelect from '../../merchant/components/merchant-select.vue';
import { service } from '/@/cool';
import { useBase, checkPerm } from '/$/base';
defineOptions({ name: 'trade-purchases' });
const { user } = useBase();
const allowed = (action: string) =>
	user.info?.username === 'admin' || checkPerm('coupon:purchaseOrder:' + action);
const canMerchants = user.info?.username === 'admin' || checkPerm('merchant:info:page');
const page = (data: any) =>
	service.request({ url: 'admin/coupon/purchaseOrder/page', method: 'POST', data });
const info = (id: any) =>
	service.request({ url: 'admin/coupon/purchaseOrder/info', method: 'GET', params: { id } });
</script>
