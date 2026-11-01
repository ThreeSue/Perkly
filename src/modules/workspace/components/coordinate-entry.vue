<template>
	<el-card shadow="never"
		><template #header>定位或手动输入坐标</template
		><el-form inline
			><el-form-item label="经度（GCJ-02）"
				><el-input-number
					v-model="longitude"
					:min="-180"
					:max="180"
					:precision="7"
					:controls="false" /></el-form-item
			><el-form-item label="纬度（GCJ-02）"
				><el-input-number
					v-model="latitude"
					:min="-90"
					:max="90"
					:precision="7"
					:controls="false" /></el-form-item
			><el-button :disabled="busy || !valid" @click="apply">应用手动坐标</el-button
			><el-button :loading="busy" @click="locate">使用当前位置</el-button></el-form
		>
		<p v-if="error" role="alert" style="color: var(--el-color-danger)">{{ error }}</p>
		<small
			>当前位置需浏览器允许定位；定位后转换为腾讯地图坐标，核对地址后再保存门店。</small
		></el-card
	>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
const props = defineProps<{
	translate: (
		longitude: number,
		latitude: number
	) => Promise<{ longitude: number; latitude: number }>;
}>();
const emit = defineEmits<{ pick: [point: { longitude: number; latitude: number }] }>();
const longitude = ref<number>(),
	latitude = ref<number>(),
	busy = ref(false),
	error = ref('');
let alive = true;
const valid = computed(
	() =>
		longitude.value != null &&
		latitude.value != null &&
		Number.isFinite(longitude.value) &&
		Number.isFinite(latitude.value)
);
function apply() {
	if (valid.value) emit('pick', { longitude: longitude.value!, latitude: latitude.value! });
}
async function locate() {
	if (busy.value) return;
	if (!navigator.geolocation) {
		error.value = '当前浏览器不支持定位，请手动选点。';
		return;
	}
	busy.value = true;
	error.value = '';
	try {
		const position = await new Promise<GeolocationPosition>((resolve, reject) =>
			navigator.geolocation.getCurrentPosition(resolve, reject, {
				enableHighAccuracy: true,
				timeout: 10000,
				maximumAge: 60000
			})
		);
		if (!alive) return;
		const point = await props.translate(position.coords.longitude, position.coords.latitude);
		if (!alive) return;
		longitude.value = Number(point.longitude);
		latitude.value = Number(point.latitude);
		apply();
	} catch (e: any) {
		if (alive)
			error.value =
				e.code === 1
					? '未获得定位授权，请手动选点或在浏览器设置中开放定位。'
					: e.message || '定位失败，请手动选点。';
	} finally {
		if (alive) busy.value = false;
	}
}
onBeforeUnmount(() => {
	alive = false;
});
</script>
