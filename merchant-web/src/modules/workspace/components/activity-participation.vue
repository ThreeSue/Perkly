<template>
	<div v-loading="busy">
		<el-alert v-if="error" :title="error" type="error" :closable="false" />
		<template v-if="detail">
			<h3>{{ detail.title }}</h3>
			<p>{{ detail.description }}</p>
            <p>{{ detail.kind === 'STAMP' ? '跨商家消费集章 · 同一商家只计一章，达标发奖励券' : detail.discountRate + ' 折' }} · {{ detail.startAt }} 至 {{ detail.endAt }}</p>
            <p>{{ detail.status === 'OFFLINE' ? '已结束' : detail.enabled ? '活动已启用，按时间生效' : '活动已暂停' }}</p>
			<p>适用门店 {{ detail.stores.map((s: any) => s.name).join('、') }}</p>
		</template>
		<template v-if="participation">
            <el-alert title="活动范围内默认参加。调整只影响后续消费，已完成订单优惠和已赚取的章保留。" type="info" :closable="false" />
			<el-checkbox v-model="exited" :disabled="!participation.canExit">整商家退出此活动</el-checkbox>
			<p>排除以下门店</p>
			<el-checkbox-group v-model="excluded">
				<el-checkbox v-for="s in participation.stores" :key="s.id" :value="s.id" :disabled="exited">{{ s.name }}</el-checkbox>
			</el-checkbox-group>
			<el-button type="primary" :loading="busy" @click="save">保存参与设置</el-button>
		</template>
		<el-button @click="load">刷新</el-button>
	</div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useBase } from '/$/base';
import { identity } from '/@/workspace';
import { featureApi } from '../feature-api';
const props = defineProps<{ id: number }>();
const { menu } = useBase();
const adjustable = computed(() => identity === 'MERCHANT' && menu.perms.includes('activity:participate'));
const detail = ref<any>(), participation = ref<any>();
const busy = ref(false), error = ref(''), exited = ref(false), excluded = ref<number[]>([]);
async function load() {
	busy.value = true;
	error.value = '';
	try {
		detail.value = await featureApi('wallet/activities/detail?id=' + props.id);
		participation.value = undefined;
		if (adjustable.value && detail.value.status === 'PUBLISHED') {
			participation.value = await featureApi('wallet/activities/participation?id=' + props.id);
			exited.value = participation.value.exited;
			excluded.value = [...participation.value.excludedStoreIds];
		}
	} catch (e: any) { error.value = e.message; }
	finally { busy.value = false; }
}
async function save() {
	busy.value = true;
	try {
		await featureApi('wallet/activities/participation', { id: props.id, version: participation.value.version,
			exited: participation.value.canExit ? exited.value : undefined, excludedStoreIds: excluded.value });
		ElMessage.success('已更新参与设置');
		await load();
	} catch (e: any) { error.value = e.message; }
	finally { busy.value = false; }
}
watch(() => props.id, load, { immediate: true });
</script>
