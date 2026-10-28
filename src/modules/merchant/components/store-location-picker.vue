<template>
	<cl-dialog
		v-model="visible"
		title="核对门店位置"
		width="1000px"
		height="72vh"
		:close-on-click-modal="false"
		:close-on-press-escape="!saving"
		:before-close="beforeClose"
		@closed="invalidate"
	>
		<div v-if="visible" class="location-picker">
			<el-alert
				title="GCJ-02坐标：搜索、点击地图或拖动标记仅修改预览，核对地址后确认才会应用或保存。"
				type="info"
				:closable="false"
				show-icon
			/>
			<el-form inline class="search-form" @submit.prevent="search">
				<el-form-item label="城市"
					><el-input
						v-model="region"
						maxlength="50"
						clearable
						placeholder="可选，如福州"
						:disabled="saving"
				/></el-form-item>
				<el-form-item label="搜索地点"
					><el-input
						v-model="keyword"
						maxlength="100"
						clearable
						placeholder="输入地点或门店名称"
						:disabled="saving"
						@keyup.enter="search"
				/></el-form-item>
				<el-button
					:loading="searching"
					:disabled="saving || !keyword.trim()"
					@click="search"
					>搜索地点</el-button
				>
			</el-form>
			<el-alert v-if="searchError" :title="searchError" type="error" :closable="false" />
			<div class="picker-workspace">
				<div class="picker-map">
					<TencentStoreMap :points="points" selected-id="draft" editable @pick="pick" />
				</div>
				<el-scrollbar class="place-list" v-loading="searching"
					><el-empty
						v-if="!places.length"
						:description="
							searched
								? '没有找到地点，请调整关键词或解析详细地址'
								: '搜索结果将在这里显示'
						"
						:image-size="50"
					/><button
						v-for="(place, index) in places"
						:key="place.id || index"
						type="button"
						class="place-item"
						:disabled="saving || !validCoordinate(place.longitude, place.latitude)"
						@click="choose(place)"
					>
						<strong>{{ place.title || '地点' }}</strong
						><span>{{ place.address || '暂无详细地址' }}</span>
					</button></el-scrollbar
				>
			</div>
			<el-alert
				v-if="positionError"
				:title="positionError"
				type="warning"
				:closable="false"
				show-icon
			/>
			<el-form label-position="top" :disabled="saving">
				<el-form-item label="核对详细地址"
					><div class="address-row">
						<el-input
							v-model="draft.address"
							maxlength="500"
							show-word-limit
							placeholder="请输入并核对门店详细地址"
							@input="addressEdited"
						/><el-button
							:loading="geocoding"
							:disabled="!draft.address.trim()"
							@click="geocode"
							>解析地址并预览</el-button
						>
					</div></el-form-item
				>
				<div class="coordinates" v-loading="reversing">
					<span>经度：{{ hasPoint ? draft.longitude : '尚未选择' }}</span
					><span>纬度：{{ hasPoint ? draft.latitude : '尚未选择' }}</span
					><el-tag v-if="!hasPoint" type="warning">待补充位置</el-tag>
				</div>
				<el-checkbox
					v-model="confirmed"
					:disabled="!hasPoint || !draft.address.trim() || reversing || geocoding"
					>我已核对地图位置和详细地址</el-checkbox
				>
			</el-form>
			<el-alert
				v-if="saveError"
				:title="saveError"
				type="error"
				:closable="false"
				show-icon
			/>
		</div>
		<template #footer
			><el-button :disabled="saving" @click="visible = false">取消</el-button
			><el-button
				type="primary"
				:loading="saving"
				:disabled="
					!hasPoint || !confirmed || !draft.address.trim() || reversing || geocoding
				"
				@click="confirm"
				>{{ confirmText }}</el-button
			></template
		>
	</cl-dialog>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import TencentStoreMap from './tencent-store-map.vue';
import {
	mapFailureMessage,
	mapQuery,
	validCoordinate,
	type LocationValue,
	type Place
} from '../map/store-map';
const visible = ref(false),
	saving = ref(false),
	confirmed = ref(false),
	searching = ref(false),
	searched = ref(false),
	reversing = ref(false),
	geocoding = ref(false);
const keyword = ref(''),
	region = ref(''),
	searchError = ref(''),
	positionError = ref(''),
	saveError = ref(''),
	confirmText = ref('应用到门店表单');
const draft = reactive<{ address: string; longitude: number | null; latitude: number | null }>({
	address: '',
	longitude: null,
	latitude: null
});
const places = ref<Place[]>([]);
const hasPoint = computed(() => validCoordinate(draft.longitude, draft.latitude));
const points = computed(() =>
	hasPoint.value
		? [
				{
					id: 'draft',
					longitude: Number(draft.longitude),
					latitude: Number(draft.latitude),
					status: 1
				}
			]
		: []
);
let activeSearchKey = '';
let session = 0,
	searchVersion = 0,
	positionVersion = 0,
	timer: ReturnType<typeof setTimeout> | undefined;
let apply: ((value: LocationValue) => void | Promise<void>) | undefined;
const message = (error: any, fallback: string) => error?.message || fallback;
onBeforeUnmount(invalidate);
function invalidate() {
	session++;
	searchVersion++;
	positionVersion++;
	clearTimeout(timer);
	apply = undefined;
}
function beforeClose(done: () => void) {
	if (!saving.value) done();
}
function open(
	initial: Partial<LocationValue>,
	callback: (value: LocationValue) => void | Promise<void>,
	text = '应用到门店表单'
) {
	if (saving.value) return;
	invalidate();
	apply = callback;
	visible.value = true;
	Object.assign(draft, {
		address: initial.address || '',
		longitude: validCoordinate(initial.longitude, initial.latitude)
			? Number(initial.longitude)
			: null,
		latitude: validCoordinate(initial.longitude, initial.latitude)
			? Number(initial.latitude)
			: null
	});
	keyword.value = '';
	region.value = '';
	places.value = [];
	searched.value = false;
	confirmed.value = false;
	searching.value = reversing.value = geocoding.value = false;
	searchError.value = positionError.value = saveError.value = '';
	confirmText.value = text;
}
watch([keyword, region], () => {
	clearTimeout(timer);
	searchVersion++;
	searching.value = false;
	places.value = [];
	searched.value = false;
	searchError.value = '';
	if (visible.value && keyword.value.trim()) timer = setTimeout(search, 400);
});
async function search() {
	clearTimeout(timer);
	const word = keyword.value.trim();
	if (!word || saving.value) return;
	const searchKey = JSON.stringify([word, region.value.trim()]);
	if (searching.value && activeSearchKey === searchKey) return;
	activeSearchKey = searchKey;
	const current = ++searchVersion,
		round = session;
	searching.value = true;
	searchError.value = '';
	searched.value = true;
	try {
		const result = await mapQuery('suggest', {
			keyword: word,
			region: region.value.trim() || undefined
		});
		if (current === searchVersion && round === session && visible.value)
			places.value = result.list || [];
	} catch (error: any) {
		if (current === searchVersion && round === session)
			searchError.value = mapFailureMessage(error, '地点搜索失败，请重试');
	} finally {
		if (current === searchVersion && round === session) searching.value = false;
	}
}
function setPoint(value: { longitude: number; latitude: number }) {
	confirmed.value = false;
	saveError.value = '';
	positionError.value = '';
	draft.longitude = Number(Number(value.longitude).toFixed(7));
	draft.latitude = Number(Number(value.latitude).toFixed(7));
}
function choose(place: Place) {
	if (saving.value || !validCoordinate(place.longitude, place.latitude)) return;
	positionVersion++;
	reversing.value = geocoding.value = false;
	setPoint(place);
	draft.address = (place.address || place.title || '').slice(0, 500);
}
async function pick(value: { longitude: number; latitude: number }) {
	if (saving.value) return;
	setPoint(value);
	const current = ++positionVersion,
		round = session;
	reversing.value = true;
	geocoding.value = false;
	try {
		const result = await mapQuery('reverseGeocode', value);
		if (current === positionVersion && round === session && visible.value) {
			draft.address = result.address || draft.address;
			if (!result.address) positionError.value = '未取得详细地址，请手动填写并核对。';
		}
	} catch (error: any) {
		if (current === positionVersion && round === session)
			positionError.value = mapFailureMessage(
				error,
				'地址解析失败，请手动核对详细地址后再确认'
			);
	} finally {
		if (current === positionVersion && round === session) reversing.value = false;
	}
}
function addressEdited() {
	confirmed.value = false;
	positionVersion++;
	reversing.value = geocoding.value = false;
	saveError.value = '';
}
async function geocode() {
	const address = draft.address.trim();
	if (!address || saving.value) return;
	const current = ++positionVersion,
		round = session;
	geocoding.value = true;
	reversing.value = false;
	positionError.value = '';
	confirmed.value = false;
	try {
		const result = await mapQuery('geocode', {
			address,
			region: region.value.trim() || undefined
		});
		if (current !== positionVersion || round !== session || !visible.value) return;
		if (!validCoordinate(result.longitude, result.latitude))
			throw new Error('地址解析未返回有效坐标，请调整地址重试');
		setPoint(result);
		draft.address = result.address || address;
	} catch (error: any) {
		if (current === positionVersion && round === session)
			positionError.value = mapFailureMessage(error, '地址解析失败，请重试');
	} finally {
		if (current === positionVersion && round === session) geocoding.value = false;
	}
}
async function confirm() {
	if (
		saving.value ||
		!hasPoint.value ||
		!confirmed.value ||
		!draft.address.trim() ||
		reversing.value ||
		geocoding.value
	)
		return;
	saving.value = true;
	saveError.value = '';
	try {
		await apply?.({
			address: draft.address.trim(),
			longitude: Number(draft.longitude),
			latitude: Number(draft.latitude)
		});
		visible.value = false;
		ElMessage.success(
			confirmText.value === '应用到门店表单' ? '位置已应用，请保存门店资料' : '门店位置已保存'
		);
	} catch (error: any) {
		saveError.value = message(error, '保存失败，预览已保留，请重试');
	} finally {
		saving.value = false;
	}
}
defineExpose({ open });
</script>
<style scoped>
.location-picker {
	display: flex;
	flex-direction: column;
	gap: 12px;
}
.search-form {
	display: flex;
	flex-wrap: wrap;
	gap: 8px;
	align-items: center;
}
.search-form .el-form-item {
	margin: 0;
}
.search-form .el-input {
	width: 190px;
}
.picker-workspace {
	display: grid;
	grid-template-columns: minmax(0, 1fr) 240px;
	gap: 12px;
}
.picker-map {
	height: 340px;
}
.place-list {
	height: 340px;
	border: 1px solid var(--el-border-color-light);
	border-radius: 6px;
}
.place-item {
	display: flex;
	flex-direction: column;
	gap: 6px;
	width: 100%;
	padding: 12px;
	border: 0;
	border-bottom: 1px solid var(--el-border-color-light);
	background: var(--el-bg-color);
	color: var(--el-text-color-primary);
	text-align: left;
	cursor: pointer;
}
.place-item:hover {
	background: var(--el-fill-color-light);
}
.place-item span {
	font-size: 12px;
	color: var(--el-text-color-secondary);
}
.place-item:disabled {
	opacity: 0.5;
	cursor: not-allowed;
}
.address-row {
	display: flex;
	width: 100%;
	gap: 10px;
}
.coordinates {
	display: flex;
	gap: 18px;
	flex-wrap: wrap;
	min-height: 28px;
	font-size: 13px;
}
@media (max-width: 760px) {
	.picker-workspace {
		grid-template-columns: 1fr;
	}
	.place-list {
		height: 160px;
	}
	.address-row {
		flex-direction: column;
	}
	.picker-map {
		height: 300px;
	}
}
</style>
