<template>
	<el-drawer
		v-model="visible"
		title="门店详情"
		size="min(650px,95vw)"
		destroy-on-close
		@closed="revision++"
	>
		<div v-loading="loading">
			<el-alert v-if="error" :title="error" type="error" :closable="false" /><el-descriptions
				v-if="data"
				:column="1"
				border
				><el-descriptions-item v-for="field in fields" :key="field[0]" :label="field[0]"
					><div class="field" :title="field[1]">{{ field[1] }}</div></el-descriptions-item
				></el-descriptions
			>
		</div>
	</el-drawer>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { useCool } from '/@/cool';
const { service } = useCool();
const visible = ref(false),
	loading = ref(false),
	error = ref(''),
	data = ref<Record<string, any>>();
let revision = 0;
const fields = computed(() => {
	const d = data.value || {};
	return [
		['门店名称', d.name],
		['所属商家', d.merchantName],
		['门店类型', d.storeTypeName || d.categoryName],
		['详细地址', d.address],
		['联系电话', d.phone],
		['营业时间', d.businessHours],
		['营业状态', ['禁用','营业','停业','关闭'][d.status] || '未知'],
		['位置状态', d.located === true ? '已设置位置' : '待补充位置'],
		['经度', d.longitude],
		['纬度', d.latitude]
	].map(([label, value]) => [String(label), value == null || value === '' ? '—' : String(value)]);
});
async function open(point: Record<string, any>) {
	const current = ++revision;
	data.value = point;
	error.value = '';
	visible.value = true;
	loading.value = true;
	try {
		const result = await service.merchant.store.info({ id: point.id });
		if (current === revision) {
			data.value = {
				...point,
				...result,
				merchantName: result.merchantName || point.merchantName,
				storeTypeName: result.storeTypeName || result.categoryName || point.storeTypeName
			};
		}
	} catch (e: any) {
		if (current === revision) error.value = e.message || '门店详情加载失败';
	} finally {
		if (current === revision) loading.value = false;
	}
}
defineExpose({ open });
</script>
<style scoped>
.field {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	max-width: 420px;
}
</style>
