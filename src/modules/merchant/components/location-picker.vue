<template>
	<el-dialog
		v-model="visible"
		:title="persist ? '补录 / 修改门店位置' : '选择门店位置'"
		width="min(1100px,96vw)"
		destroy-on-close
		:close-on-click-modal="!saving"
		:close-on-press-escape="!saving"
		:before-close="beforeClose"
		@closed="invalidate"
	>
		<template v-if="visible">
			<el-alert
				title="选择搜索结果后会带入地点名称和地址；请核对地图位置及详细地址，再点击确认。"
				type="info"
				:closable="false"
				show-icon
			/>
			<div class="picker-layout">
				<div class="place-search">
					<el-input
						v-model="region"
						clearable
						maxlength="50"
						placeholder="城市（选填，如福州）"
						:disabled="saving"
					/>
					<el-input
						v-model="keyword"
						clearable
						maxlength="100"
						placeholder="搜索地点名称"
						:disabled="saving || !canSuggest"
					/>
					<div v-if="suggesting" class="hint">正在搜索地点…</div>
					<el-alert
						v-if="suggestError"
						:title="suggestError"
						type="error"
						:closable="false"
					/>
					<el-empty
						v-if="searched && !suggesting && !places.length && !suggestError"
						description="没有找到地点，请调整关键词或直接点击地图"
						:image-size="60"
					/>
					<el-scrollbar max-height="350px"
						><button
							v-for="(place, index) in places"
							:key="place.id || index"
							type="button"
							class="place-item"
							:disabled="saving"
							@click="choose(place)"
						>
							<strong>{{ place.title || place.address }}</strong
							><span>{{ place.address }}</span>
						</button></el-scrollbar
					>
				</div>
				<div class="picker-map">
					<TencentMap :editable="!saving" :position="position" @pick="pick" />
				</div>
			</div>
			<CoordinateEntry :translate="translateGps" @pick="pick" />
			<el-form label-width="90px" class="address-form" @submit.prevent>
				<el-form-item label="详细地址" required
					><el-input
						v-model="address"
						maxlength="500"
						placeholder="请核对并补充楼号、楼层等详细地址"
						:disabled="saving"
					/><el-button
						:disabled="!address.trim() || resolving || saving || !canGeocode"
						:loading="geocoding"
						@click="parseAddress"
						>解析此地址</el-button
					></el-form-item
				>
				<el-form-item label="地图坐标"
					><span
						>{{
							position
								? `经度 ${position.longitude}，纬度 ${position.latitude}`
								: '尚未选择位置'
						}}（GCJ-02）</span
					><span v-if="resolving" class="hint">正在解析位置…</span></el-form-item
				>
			</el-form>
			<el-alert
				v-if="locationError"
				:title="locationError"
				type="warning"
				:closable="false"
			/>
			<el-alert v-if="saveError" :title="saveError" type="error" :closable="false" />
			<el-checkbox v-model="confirmed" :disabled="saving || resolving || !position"
				>我已核对地图位置与详细地址一致</el-checkbox
			>
		</template>
		<template #footer
			><el-button :disabled="saving" @click="visible = false">取消</el-button
			><el-button
				type="primary"
				:loading="saving"
				:disabled="!position || !confirmed || resolving || !address.trim()"
				@click="confirm"
				>{{ persist ? '确认保存位置' : '确认使用此位置' }}</el-button
			></template
		>
	</el-dialog>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import CoordinateEntry from '/$/workspace/components/coordinate-entry.vue';
import { service } from '/@/cool';
const translateGps = (longitude: number, latitude: number) =>
	service.request({
		url: 'admin/merchant/map/translateGps',
		method: 'GET',
		params: { longitude, latitude }
	}) as Promise<any>;
import TencentMap from './tencent-map.vue';
import { formatPlaceAddress, hasCoordinates, locationPayload, type LocationValue, type Place } from '../map/model';
import { storeMapApi } from '../map/api';
import { mapPermission } from '../map/permission';
const props = withDefaults(defineProps<{ persist?: boolean }>(), { persist: false });
const emit = defineEmits<{ confirmed: [location: LocationValue]; saved: [] }>();
const visible = ref(false),
	saving = ref(false),
	resolving = ref(false),
	geocoding = ref(false),
	confirmed = ref(false);
const keyword = ref(''),
	region = ref(''),
	address = ref(''),
	suggesting = ref(false),
	searched = ref(false),
	suggestError = ref(''),
	locationError = ref(''),
	saveError = ref('');
const places = ref<Place[]>([]),
	position = ref<{ longitude: number; latitude: number }>();
const canSuggest = computed(() => mapPermission('map:suggest')),
	canGeocode = computed(() => mapPermission('map:geocode'));
let id: number | undefined,
	session = 0,
	searchRevision = 0,
	pointRevision = 0,
	timer: ReturnType<typeof setTimeout> | undefined;
const cache = new Map<string, Place[]>();
function invalidate() {
	session++;
	searchRevision++;
	pointRevision++;
	clearTimeout(timer);
	suggesting.value = false;
	resolving.value = false;
	geocoding.value = false;
}
function open(value: { id?: number; address?: string; longitude?: unknown; latitude?: unknown }) {
	invalidate();
	id = value.id;
	keyword.value = '';
	region.value = '';
	address.value = value.address || '';
	places.value = [];
	searched.value = false;
	suggestError.value = '';
	locationError.value = '';
	saveError.value = '';
	confirmed.value = false;
	position.value = hasCoordinates(value)
		? { longitude: Number(value.longitude), latitude: Number(value.latitude) }
		: undefined;
	visible.value = true;
}
watch([keyword, region], () => {
	clearTimeout(timer);
	const revision = ++searchRevision;
	const current = session;
	suggesting.value = false;
	places.value = [];
	suggestError.value = '';
	searched.value = false;
	if (!visible.value || !keyword.value.trim() || !canSuggest.value) return;
	timer = setTimeout(async () => {
		const key = JSON.stringify([keyword.value.trim(), region.value.trim()]);
		suggesting.value = true;
		searched.value = true;
		try {
			let rows = cache.get(key);
			if (!rows) {
				const result = await storeMapApi.suggest(keyword.value, region.value);
				rows = result.list;
				cache.set(key, rows);
				if (cache.size > 40) cache.delete(cache.keys().next().value!);
			}
			if (current === session && revision === searchRevision) places.value = rows;
		} catch (e: any) {
			if (current === session && revision === searchRevision)
				suggestError.value = e.message || '地点搜索失败，请重试';
		} finally {
			if (current === session && revision === searchRevision) suggesting.value = false;
		}
	}, 400);
});
watch(
	address,
	() => {
		// Editing the text invalidates any older address lookup still in flight.
		pointRevision++;
		resolving.value = false;
		geocoding.value = false;
		confirmed.value = false;
		saveError.value = '';
	},
	{ flush: 'sync' }
);
function choose(place: Place) {
	if (saving.value || !hasCoordinates(place)) return;
	pointRevision++;
	resolving.value = false;
	geocoding.value = false;
	position.value = { longitude: place.longitude, latitude: place.latitude };
	address.value = formatPlaceAddress(place);
	confirmed.value = false;
	locationError.value = '';
}
async function pick(point: { longitude: number; latitude: number }) {
	if (saving.value) return;
	position.value = point;
	confirmed.value = false;
	locationError.value = '';
	const revision = ++pointRevision,
		current = session;
	resolving.value = true;
	geocoding.value = false;
	try {
		if (!mapPermission('map:reverseGeocode'))
			throw new Error('当前账号无逆地址解析权限，请手工核对详细地址');
		const result = await storeMapApi.reverse(point.longitude, point.latitude);
		if (current === session && revision === pointRevision) address.value = result.address;
	} catch (e: any) {
		if (current === session && revision === pointRevision)
			locationError.value = (e.message || '逆地址解析失败') + '；请手工核对地址后确认。';
	} finally {
		if (current === session && revision === pointRevision) resolving.value = false;
	}
}
async function parseAddress() {
	if (resolving.value || saving.value || !canGeocode.value) return;
	const revision = ++pointRevision,
		current = session;
	resolving.value = true;
	geocoding.value = true;
	confirmed.value = false;
	locationError.value = '';
	try {
		const result = await storeMapApi.geocode(address.value, region.value);
		if (current !== session || revision !== pointRevision) return;
		if (!hasCoordinates(result)) throw new Error('地址解析未返回有效坐标');
		position.value = { longitude: result.longitude, latitude: result.latitude };
	} catch (e: any) {
		if (current === session && revision === pointRevision)
			locationError.value = e.message || '地址解析失败，请调整地址';
	} finally {
		if (current === session && revision === pointRevision) {
			resolving.value = false;
			geocoding.value = false;
		}
	}
}
async function confirm() {
	if (saving.value || resolving.value || !confirmed.value || !position.value) return;
	try {
		const value = locationPayload({ address: address.value, ...position.value });
		saveError.value = '';
		if (props.persist) {
			if (id == null) throw new Error('门店信息不完整，请重新打开');
			if (!mapPermission('store:location:update')) throw new Error('当前账号无位置修改权限');
			saving.value = true;
			const result = await storeMapApi.update(id, value);
			if (result !== true) throw new Error('保存未成功，请重试');
			ElMessage.success('门店位置已保存');
			emit('saved');
		}
		emit('confirmed', value);
		visible.value = false;
	} catch (e: any) {
		saveError.value = e.message || '保存失败，所选位置已保留，请重试';
	} finally {
		saving.value = false;
	}
}
function beforeClose(done: () => void) {
	if (!saving.value) done();
}
onBeforeUnmount(invalidate);
defineExpose({ open });
</script>
<style scoped>
.picker-layout {
	display: grid;
	grid-template-columns: 250px minmax(0, 1fr);
	gap: 16px;
	margin: 16px 0;
}
.place-search {
	display: flex;
	flex-direction: column;
	gap: 10px;
}
.picker-map {
	height: 380px;
	min-width: 0;
}
.place-item {
	display: flex;
	flex-direction: column;
	width: 100%;
	gap: 5px;
	text-align: left;
	padding: 10px;
	border: 0;
	border-bottom: 1px solid var(--el-border-color-light);
	background: var(--el-bg-color);
	color: var(--el-text-color-primary);
	cursor: pointer;
}
.place-item:hover {
	background: var(--el-fill-color-light);
}
.place-item span,
.hint {
	font-size: 12px;
	color: var(--el-text-color-secondary);
}
.address-form :deep(.el-form-item__content) {
	gap: 10px;
}
.address-form :deep(.el-input) {
	flex: 1;
	min-width: 180px;
}
@media (max-width: 700px) {
	.picker-layout {
		grid-template-columns: 1fr;
	}
	.picker-map {
		height: 300px;
	}
}
</style>
