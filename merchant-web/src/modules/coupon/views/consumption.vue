<template>
	<TradeRecords
		:purchase="false"
		:page-api="page"
		:info-api="info"
		:can-page="allowed('page')"
		:can-info="allowed('info')"
		:load-stores="canStores ? loadStores : undefined"
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
defineOptions({ name: 'trade-consumption' });
const { user } = useBase();
const allowed = (action: string) =>
	user.info?.username === 'admin' || checkPerm('coupon:consumptionOrder:' + action);
const canMerchants = user.info?.username === 'admin' || checkPerm('merchant:info:page');
const canStores = user.info?.username === 'admin' || checkPerm('merchant:store:page');
const page = (data: any) =>
	service.request({ url: 'admin/coupon/consumptionOrder/page', method: 'POST', data });
const info = (id: any) =>
	service.request({ url: 'admin/coupon/consumptionOrder/info', method: 'GET', params: { id } });
const loadStores = async (keyword: string, merchantId?: number) =>
	(await service.merchant.store.page({ page: 1, size: 30, keyWord: keyword, merchantId })).list;
</script>
