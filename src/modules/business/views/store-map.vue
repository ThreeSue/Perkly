<template>
	<div class="store-map-view">
		<el-form inline class="filters" @submit.prevent="refresh">
			<el-form-item label="关键词"
				><el-input
					v-model="filters.keyword"
					clearable
					maxlength="100"
					placeholder="门店名称或地址"
					@keyup.enter="refresh"
			/></el-form-item>
			<el-form-item label="门店类型"
				><el-select
					v-model="filters.storeTypeId"
					clearable
					placeholder="全部类型"
					style="width: 160px"
					><el-option
						v-for="item in storeTypes"
						:key="item.id"
						:label="item.name"
						:value="item.id" /></el-select
			></el-form-item>
			<el-form-item label="营业状态"
				><el-select
					v-model="filters.status"
					clearable
					placeholder="全部状态"
					style="width: 140px"
					><el-option label="营业" :value="1" /><el-option
						label="停用"
						:value="0" /></el-select
			></el-form-item>
			<el-button type="primary" :loading="loading" @click="refresh">搜索</el-button
			><el-button @click="reset">重置</el-button><el-button @click="refresh">刷新</el-button>
		</el-form>
		<el-alert v-if="error" :title="error" type="error" :closable="false" show-icon
			><template #default
				><el-button link @click="refresh">重新加载门店</el-button></template
			></el-alert
		>
		<el-alert v-if="typeError" title="门店类型选项加载失败" type="warning" :closable="false"
			><template #default
				><el-button link @click="loadTypes">重新加载类型</el-button></template
			></el-alert
		>
		<div class="map-summary">
			<span
				>当前筛选结果：<b>{{ result?.total ?? '—' }}</b> 家</span
			><el-tag type="success">已定位 {{ result?.locatedCount ?? '—' }}</el-tag
			><el-tag type="warning">待补录 {{ result?.unlocatedCount ?? '—' }}</el-tag
			><span class="legend"
				><i class="open-dot" />营业<i class="closed-dot" />停用<span class="cluster-dot"
					>聚合</span
				>点击聚合点展开</span
			>
		</div>
		<div class="map-workspace" v-loading="loading">
			<section class="map-panel">
				<TencentStoreMap
					ref="Map"
					:points="mapPoints"
					:selected-id="selected ? String(selected.id) : undefined"
					@select="selectId"
				/>
				<div v-if="selected" class="selected-store">
					<div class="selected-heading">
						<strong>{{ selected.name }}</strong
						><el-tag :type="selected.status === 1 ? 'success' : 'info'">{{
							selected.status === 1 ? '营业' : '停用'
						}}</el-tag>
					</div>
					<p>{{ selected.storeTypeName }} · {{ selected.address || '未填写地址' }}</p>
					<p>
						电话：{{ selected.phone || '未填写'
						}}<span v-if="!drawable(selected)"> · 待补充位置</span>
					</p>
					<el-button size="small" @click="detailsVisible = true">门店详情</el-button
					><el-button type="primary" size="small" @click="editLocation(selected)">{{
						drawable(selected) ? '编辑位置' : '补录位置'
					}}</el-button>
				</div>
			</section>
			<aside class="store-list">
				<el-tabs v-model="tab"
					><el-tab-pane
						:label="'已定位（' + located.length + '）'"
						name="located" /><el-tab-pane
						:label="'待补充位置（' + unlocated.length + '）'"
						name="unlocated" /></el-tabs
				><el-scrollbar ref="List"
					><el-empty
						v-if="!visibleStores.length"
						:description="
							error
								? '门店加载失败，请重试'
								: loading
									? '正在加载门店'
									: '没有符合条件的门店'
						"
						:image-size="60"
					/><button
						v-for="store in visibleStores"
						:key="store.id"
						type="button"
						class="store-card"
						:class="{ selected: selected?.id === store.id }"
						:data-store-id="store.id"
						@click="selectStore(store)"
					>
						<strong>{{ store.name }}</strong
						><span
							><el-tag size="small" :type="store.status === 1 ? 'success' : 'info'">{{
								store.status === 1 ? '营业' : '停用'
							}}</el-tag>
							{{ store.storeTypeName }}</span
						><span>{{ store.address || '未填写地址' }}</span
						><span v-if="!drawable(store)" class="missing"
							>待补充位置 · 点击后可补录</span
						>
					</button></el-scrollbar
				>
			</aside>
		</div>
		<StoreLocationPicker ref="Picker" />
		<cl-dialog v-model="detailsVisible" title="门店详情" width="680px"
			><el-descriptions v-if="selected" :column="1" border
				><el-descriptions-item label="门店名称">{{ selected.name }}</el-descriptions-item
				><el-descriptions-item label="门店类型">{{
					selected.storeTypeName
				}}</el-descriptions-item
				><el-descriptions-item label="营业状态">{{
					selected.status === 1 ? '营业' : '停用'
				}}</el-descriptions-item
				><el-descriptions-item label="详细地址">{{
					selected.address || '—'
				}}</el-descriptions-item
				><el-descriptions-item label="联系电话">{{
					selected.phone || '—'
				}}</el-descriptions-item
				><el-descriptions-item label="营业时间">{{
					selected.businessHours || '—'
				}}</el-descriptions-item
				><el-descriptions-item label="位置">{{
					drawable(selected)
						? `经度 ${selected.longitude}，纬度 ${selected.latitude}`
						: '待补充位置'
				}}</el-descriptions-item></el-descriptions
			><template #footer
				><el-button v-if="selected" type="primary" @click="editLocation(selected)"
					>编辑门店位置</el-button
				></template
			></cl-dialog
		>
	</div>
</template>
<script setup lang="ts">
import {
	computed,
	nextTick,
	onMounted,
	onActivated,
	onDeactivated,
	onBeforeUnmount,
	reactive,
	ref,
	watch
} from 'vue';
import { merchantRequest } from '/@/merchant-api';
import TencentStoreMap from '../components/tencent-store-map.vue';
import StoreLocationPicker from '../components/store-location-picker.vue';
import {
	drawable,
	fetchStoreMap,
	updateStoreLocation,
	type MapFilter,
	type StoreMapResult,
	type StorePoint
} from '../map/store-map';
defineOptions({ name: 'merchant-store-map' });
const filters = reactive<MapFilter>({ keyword: '', storeTypeId: undefined, status: undefined });
const result = ref<StoreMapResult>(),
	selected = ref<StorePoint>(),
	tab = ref('located'),
	loading = ref(false),
	error = ref(''),
	typeError = ref(false),
	detailsVisible = ref(false);
const storeTypes = ref<{ id: number; name: string }[]>([]);
const Map = ref<InstanceType<typeof TencentStoreMap>>(),
	Picker = ref<InstanceType<typeof StoreLocationPicker>>(),
	List = ref<any>();
const located = computed(() => result.value?.list.filter(drawable) || []),
	unlocated = computed(() => result.value?.list.filter(s => !drawable(s)) || []);
const visibleStores = computed(() => (tab.value === 'located' ? located.value : unlocated.value));
const mapPoints = computed(() =>
	located.value.map(s => ({
		id: String(s.id),
		longitude: Number(s.longitude),
		latitude: Number(s.latitude),
		status: s.status
	}))
);
let version = 0,
	active = true,
	timer: ReturnType<typeof setTimeout> | undefined;
async function refresh() {
	clearTimeout(timer);
	const current = ++version;
	loading.value = true;
	error.value = '';
	try {
		const response = await fetchStoreMap(filters);
		if (current !== version || !active) return;
		result.value = response;
		selected.value = response.list.find(s => s.id === selected.value?.id);
		for (const item of response.list) {
			if (!storeTypes.value.some(t => t.id === item.storeTypeId))
				storeTypes.value.push({ id: item.storeTypeId, name: item.storeTypeName });
		}
		await nextTick();
		if (selected.value) await selectStore(selected.value);
		else Map.value?.fitAll();
	} catch (err: any) {
		if (current === version && active) {
			error.value = err.message || '门店地图数据加载失败';
			result.value = undefined;
			selected.value = undefined;
		}
	} finally {
		if (current === version) loading.value = false;
	}
}
async function loadTypes() {
	try {
		const options = await merchantRequest('workspace/category/options');
		storeTypes.value = options;
		for (const item of result.value?.list || []) {
			if (!storeTypes.value.some(t => t.id === item.storeTypeId))
				storeTypes.value.push({ id: item.storeTypeId, name: item.storeTypeName });
		}
		typeError.value = false;
	} catch {
		typeError.value = true;
	}
}
function reset() {
	Object.assign(filters, { keyword: '', storeTypeId: undefined, status: undefined });
	void nextTick(refresh);
}
watch(filters, () => {
	clearTimeout(timer);
	version++;
	loading.value = false;
	timer = setTimeout(refresh, 400);
});
async function selectStore(store: StorePoint) {
	selected.value = store;
	tab.value = drawable(store) ? 'located' : 'unlocated';
	if (drawable(store))
		Map.value?.focus({
			id: String(store.id),
			longitude: Number(store.longitude),
			latitude: Number(store.latitude),
			status: store.status
		});
	await nextTick();
	List.value?.wrapRef
		?.querySelector(`[data-store-id="${store.id}"]`)
		?.scrollIntoView({ block: 'nearest' });
}
function selectId(id: string) {
	const store = result.value?.list.find(s => String(s.id) === id);
	if (store) void selectStore(store);
}
function editLocation(store: StorePoint) {
	detailsVisible.value = false;
	Picker.value?.open(
		{
			address: store.address,
			longitude: drawable(store) ? Number(store.longitude) : undefined,
			latitude: drawable(store) ? Number(store.latitude) : undefined
		},
		async location => {
			await updateStoreLocation(store.id, location);
			await refresh();
		},
		'确认保存位置'
	);
}
onMounted(() => {
	void loadTypes();
	void refresh();
});
let firstActivation = true;
onActivated(() => {
	active = true;
	if (firstActivation) {
		firstActivation = false;
		return;
	}
	void refresh();
});
function deactivate() {
	active = false;
	version++;
	clearTimeout(timer);
	loading.value = false;
}
onDeactivated(deactivate);
onBeforeUnmount(deactivate);
</script>
<style scoped>
.store-map-view {
	height: 100%;
	box-sizing: border-box;
	background: var(--el-bg-color);
	padding: 12px;
	display: flex;
	flex-direction: column;
	gap: 12px;
	overflow: auto;
}
.filters {
	display: flex;
	gap: 10px;
	flex-wrap: wrap;
	align-items: center;
}
.filters .el-form-item {
	margin: 0;
}
.filters .el-input {
	width: 210px;
}
.map-summary,
.legend {
	display: flex;
	gap: 12px;
	flex-wrap: wrap;
	align-items: center;
}
.legend {
	margin-left: auto;
	font-size: 12px;
	color: var(--el-text-color-secondary);
	gap: 7px;
}
.legend i {
	height: 10px;
	width: 10px;
	border-radius: 50%;
	display: inline-block;
}
.open-dot {
	background: #259565;
}
.closed-dot {
	background: #7b8495;
}
.cluster-dot {
	background: #4165d7;
	color: white;
	border-radius: 10px;
	padding: 2px 6px;
}
.map-workspace {
	flex: 1;
	min-height: 520px;
	display: grid;
	grid-template-columns: minmax(0, 1fr) 330px;
	gap: 12px;
}
.map-panel {
	display: flex;
	flex-direction: column;
	min-width: 0;
	gap: 10px;
}
.map-panel :deep(.map-host) {
	flex: 1;
	min-height: 360px;
}
.selected-store {
	border: 1px solid var(--el-border-color-light);
	padding: 12px;
	border-radius: 6px;
}
.selected-heading {
	display: flex;
	align-items: center;
	gap: 10px;
}
.selected-store p {
	font-size: 13px;
	color: var(--el-text-color-secondary);
	margin: 8px 0;
}
.store-list {
	display: flex;
	flex-direction: column;
	min-height: 0;
	border: 1px solid var(--el-border-color-light);
	border-radius: 6px;
	padding: 0 10px;
}
.store-list .el-scrollbar {
	flex: 1;
	height: 0;
	min-height: 280px;
}
.store-card {
	display: flex;
	flex-direction: column;
	gap: 9px;
	text-align: left;
	width: 100%;
	border: 1px solid var(--el-border-color-light);
	border-radius: 5px;
	margin-bottom: 10px;
	padding: 12px;
	background: var(--el-bg-color);
	color: var(--el-text-color-primary);
	cursor: pointer;
}
.store-card.selected {
	border-color: var(--el-color-primary);
	background: var(--el-color-primary-light-9);
}
.store-card span {
	font-size: 12px;
	color: var(--el-text-color-secondary);
}
.store-card .missing {
	color: var(--el-color-warning);
}
@media (max-width: 900px) {
	.map-workspace {
		grid-template-columns: 1fr;
	}
	.store-list .el-scrollbar {
		height: 260px;
		flex: none;
	}
	.legend {
		margin-left: 0;
	}
}
</style>
