<template><TradeRecords :page-api="page" :info-api="info" :load-stores="stores" /></template>
<script setup lang="ts">
import TradeRecords from '../components/trade-records.vue';
import { merchantRequest } from '/@/merchant-api';
import { workspaceApi } from '/$/workspace/api';
defineOptions({ name: 'merchant-consumption-records' });
const page = (data: any) => merchantRequest('consumption/page', data);
const info = (id: any) => merchantRequest('consumption/info?id=' + encodeURIComponent(id));
const stores = async (keyword: string) => {
	const list = await workspaceApi('stores');
	return list.filter((s: any) => s.name?.includes(keyword));
};
</script>
