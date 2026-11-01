<template>
	<el-empty v-if="!canView" description="当前账号无门店分布地图权限" />
	<div v-else class="store-map-page">
		<div class="filter-panel">
			<h3>门店分布地图</h3>
			<el-form inline @submit.prevent="search">
				<el-form-item label="关键词"
					><el-input
						v-model="filters.keyword"
						clearable
						maxlength="100"
						placeholder="门店、商家名称或地址"
						@keyup.enter="search"
				/></el-form-item>
				<el-form-item label="所属商家"
					><MerchantSelect v-model="filters.merchantId"
				/></el-form-item>
				<el-form-item label="门店类型"
					><el-select
						v-model="filters.storeTypeId"
						clearable
						filterable
						placeholder="全部门店类型"
						><el-option
							v-for="item in types"
							:key="item.id"
							:value="item.id"
							:label="item.name" /></el-select
				></el-form-item>
				<el-form-item label="营业状态"
					><el-select v-model="filters.status" clearable placeholder="全部状态"
						><el-option label="营业中" :value="1" /><el-option
							label="已停用"
							:value="0" /></el-select
				></el-form-item>
				<el-form-item
					><el-button type="primary" :loading="loading" @click="search">搜索</el-button
					><el-button @click="reset">重置</el-button
					><el-button :disabled="loading" @click="load">刷新</el-button></el-form-item
				>
			</el-form>
			<div class="summary">
				<span
					>筛选结果 <strong>{{ result.total }}</strong> 家</span
				><el-tag type="success">已定位 {{ result.locatedCount }}</el-tag
				><el-tag type="warning">待补录 {{ result.unlocatedCount }}</el-tag
				><span class="legend"
					><i class="open" />营业中 <i class="closed" />已停用 ·
					数字圆点表示聚合门店</span
				>
			</div>
		</div>
		<el-alert v-if="error" :title="error" type="error" :closable="false"
			><el-button text @click="load">重新加载门店</el-button></el-alert
		>
		<div v-loading="loading" class="distribution">
			<aside class="store-list">
				<el-tabs v-model="listState"
					><el-tab-pane
						:label="`已定位（${result.locatedCount}）`"
						name="located" /><el-tab-pane
						:label="`待补充位置（${result.unlocatedCount}）`"
						name="pending"
				/></el-tabs>
				<el-scrollbar ref="scrollbar" class="list-scroll">
					<el-empty
						v-if="!displayed.length && !loading"
						:description="
							error
								? '加载失败，请重试'
								: listState === 'pending'
									? '当前筛选下没有待补录门店'
									: '当前筛选下没有已定位门店'
						"
						:image-size="65"
					/>
					<div
						v-for="store in displayed"
						:key="store.id"
						:ref="el => remember(el, store.id)"
						:class="['store-card', { selected: selectedId === store.id }]"
						tabindex="0"
						role="button"
						:aria-label="'选择门店 ' + store.name"
						:aria-pressed="selectedId === store.id"
						@click="select(store.id)"
						@keydown.enter.prevent="select(store.id)"
					>
						<div class="store-heading">
							<strong :title="store.name">{{ store.name }}</strong
							><el-tag :type="store.status === 1 ? 'success' : 'info'" size="small">{{
								store.status === 1 ? '营业' : '停用'
							}}</el-tag>
						</div>
						<p :title="store.merchantName">
							{{ store.merchantName }} · {{ store.storeTypeName || '未设置类型' }}
						</p>
						<p :title="store.address">{{ store.address || '暂无地址' }}</p>
						<div class="card-actions">
							<el-button
								v-if="canDetail"
								text
								type="primary"
								@click.stop="detail?.open(store)"
								>查看详情</el-button
							><el-button
								v-if="canUpdate"
								text
								type="primary"
								@click.stop="picker?.open(store)"
								>{{ drawable(store) ? '修改位置' : '补充位置' }}</el-button
							>
						</div>
					</div>
				</el-scrollbar>
			</aside>
			<div class="map-panel">
				<TencentMap :points="result.list" :selected-id="selectedId" @select="select" />
				<div v-if="selected" class="selected-card">
					<div class="store-heading">
						<strong>{{ selected.name }}</strong
						><el-button text aria-label="关闭门店信息" @click="selectedId = undefined"
							>关闭</el-button
						>
					</div>
					<p>{{ selected.merchantName }}</p>
					<p>{{ selected.address || '暂无地址' }}</p>
					<el-tag :type="selected.status === 1 ? 'success' : 'info'">{{
						selected.status === 1 ? '营业中' : '已停用'
					}}</el-tag>
					<p v-if="!drawable(selected)">此门店待补充位置，未在地图上绘制。</p>
					<div>
						<el-button
							v-if="canDetail"
							text
							type="primary"
							@click="detail?.open(selected)"
							>查看详情</el-button
						><el-button
							v-if="canUpdate"
							text
							type="primary"
							@click="picker?.open(selected)"
							>{{ drawable(selected) ? '修改位置' : '补充位置' }}</el-button
						>
					</div>
				</div>
				<div v-if="!loading && !error && !result.locatedCount" class="empty-map-note">
					没有可绘制的门店位置，请从“待补充位置”中人工补录。
				</div>
			</div>
		</div>
		<LocationPicker ref="picker" persist @saved="load" /><StoreDetail ref="detail" />
	</div>
</template>
<script setup lang="ts">
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue';
import type { ComponentPublicInstance } from 'vue';
import { ElMessage } from 'element-plus';
import { useCool } from '/@/cool';
import MerchantSelect from '../components/merchant-select.vue';
import TencentMap from '../components/tencent-map.vue';
import LocationPicker from '../components/location-picker.vue';
import StoreDetail from '../components/store-detail.vue';
import { storeMapApi } from '../map/api';
import { drawable, type MapFilters, type StoreMapResult } from '../map/model';
import { mapPermission } from '../map/permission';
defineOptions({ name: 'merchant-store-map' });
const { service } = useCool();
const canView = computed(() => mapPermission('store:map')),
	canDetail = computed(() => mapPermission('store:info')),
	canUpdate = computed(() => mapPermission('store:location:update'));
const filters = ref<MapFilters>({}),
	types = ref<{ id: number; name: string }[]>([]),
	loading = ref(false),
	error = ref(''),
	listState = ref('located'),
	selectedId = ref<number>();
const result = ref<StoreMapResult>({
	coordinateSystem: 'GCJ-02',
	total: 0,
	locatedCount: 0,
	unlocatedCount: 0,
	list: []
});
const selected = computed(() => result.value.list.find(s => s.id === selectedId.value));
const displayed = computed(() =>
	result.value.list.filter(s => (listState.value === 'located' ? drawable(s) : !drawable(s)))
);
const picker = ref<InstanceType<typeof LocationPicker>>(),
	detail = ref<InstanceType<typeof StoreDetail>>(),
	scrollbar = ref();
const cards = new Map<number, HTMLElement>();
let revision = 0;
function remember(el: Element | ComponentPublicInstance | null, id: number) {
	if (el instanceof HTMLElement) cards.set(id, el);
	else cards.delete(id);
}
async function select(id: number) {
	selectedId.value = id;
	const item = selected.value;
	if (!item) return;
	listState.value = drawable(item) ? 'located' : 'pending';
	await nextTick();
	const card = cards.get(id);
	if (card && scrollbar.value?.wrapRef) {
		const wrap = scrollbar.value.wrapRef;
		wrap.scrollTo({ top: card.offsetTop - 16, behavior: 'smooth' });
	}
}
async function load() {
	if (!canView.value) return;
	const current = ++revision;
	loading.value = true;
	error.value = '';
	try {
		const data = await storeMapApi.list(filters.value);
		if (current !== revision) return;
		if (data.coordinateSystem !== 'GCJ-02') throw new Error('地图坐标系不匹配，已停止绘制');
		result.value = data;
		if (selectedId.value && !data.list.some(s => s.id === selectedId.value))
			selectedId.value = undefined;
	} catch (e: any) {
		if (current === revision) {
			error.value = e.message || '门店分布加载失败';
			result.value = {
				coordinateSystem: 'GCJ-02',
				total: 0,
				locatedCount: 0,
				unlocatedCount: 0,
				list: []
			};
			selectedId.value = undefined;
		}
	} finally {
		if (current === revision) loading.value = false;
	}
}
function search() {
	selectedId.value = undefined;
	void load();
}
function reset() {
	filters.value = {};
	search();
}
onMounted(() => {
	if (!canView.value) return;
	void load();
	void service.merchant.storeCategory
		.list({})
		.then(rows => {
			types.value = rows as unknown as { id: number; name: string }[];
		})
		.catch((e: any) => ElMessage.error(e.message || '门店类型加载失败'));
});
onBeforeUnmount(() => revision++);
</script>
<style scoped>
.store-map-page {
	height: 100%;
	min-height: 620px;
	display: flex;
	flex-direction: column;
	gap: 12px;
}
.filter-panel {
	background: var(--el-bg-color);
	padding: 14px 18px;
	border-radius: 6px;
}
h3 {
	font-size: 16px;
	margin: 0 0 14px;
}
.filter-panel :deep(.el-form-item) {
	margin-bottom: 12px;
	margin-right: 14px;
}
.filter-panel :deep(.el-select) {
	width: 180px;
}
.summary,
.legend {
	display: flex;
	align-items: center;
	gap: 14px;
	flex-wrap: wrap;
}
.legend {
	font-size: 12px;
	color: var(--el-text-color-secondary);
	margin-left: auto;
	gap: 6px;
}
.legend i {
	display: inline-block;
	width: 10px;
	height: 10px;
	border-radius: 50%;
	margin-left: 8px;
}
.legend .open {
	background: #16a34a;
}
.legend .closed {
	background: #64748b;
}
.distribution {
	display: grid;
	grid-template-columns: 350px minmax(0, 1fr);
	gap: 12px;
	flex: 1;
	min-height: 400px;
}
.store-list {
	background: var(--el-bg-color);
	padding: 0 12px 12px;
	display: flex;
	flex-direction: column;
	min-height: 0;
}
.list-scroll {
	flex: 1;
	min-height: 0;
}
.store-card {
	padding: 14px 12px;
	border: 1px solid var(--el-border-color-light);
	border-radius: 5px;
	margin: 0 0 10px;
	cursor: pointer;
}
.store-card:hover {
	background: var(--el-fill-color-light);
}
.store-card.selected {
	border-color: var(--el-color-primary);
	background: var(--el-color-primary-light-9);
}
.store-heading {
	display: flex;
	align-items: center;
	gap: 10px;
	justify-content: space-between;
}
.store-heading strong {
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	min-width: 0;
}
.store-card p {
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	margin: 8px 0;
	font-size: 13px;
	color: var(--el-text-color-secondary);
}
.card-actions {
	display: flex;
	gap: 12px;
}
.map-panel {
	position: relative;
	min-width: 0;
	min-height: 400px;
}
.selected-card {
	position: absolute;
	right: 14px;
	top: 14px;
	max-width: 300px;
	background: var(--el-bg-color);
	padding: 12px 16px;
	border-radius: 6px;
	box-shadow: var(--el-box-shadow-light);
	z-index: 3;
}
.selected-card p {
	font-size: 13px;
	overflow-wrap: anywhere;
}
.empty-map-note {
	position: absolute;
	left: 12px;
	right: 12px;
	bottom: 16px;
	padding: 12px;
	background: var(--el-bg-color);
	color: var(--el-text-color-secondary);
	font-size: 13px;
	border-radius: 4px;
}
@media (max-width: 1000px) {
	.distribution {
		grid-template-columns: 280px minmax(0, 1fr);
	}
}
@media (max-width: 700px) {
	.store-map-page {
		height: auto;
	}
	.distribution {
		display: flex;
		flex-direction: column-reverse;
	}
	.map-panel {
		height: 400px;
	}
	.list-scroll {
		max-height: 400px;
	}
}
</style>
