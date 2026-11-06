<template>
	<div class="map-shell">
		<div ref="container" class="map-canvas" aria-label="腾讯地图" />
		<div v-if="loading || error" class="map-message">
			<el-result v-if="error" icon="warning" title="地图暂不可用" :sub-title="error"
				><template #extra
					><el-button @click="init">重新加载地图</el-button></template
				></el-result
			>
			<span v-else>正在加载腾讯地图…</span>
		</div>
		<div v-if="!error && !loading" class="map-tools">
			<el-button size="small" @click="fit">显示全部位置</el-button><span>GCJ-02</span>
		</div>
	</div>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, onActivated, onDeactivated, ref, watch } from 'vue';
import { drawable, hasCoordinates, type StorePoint } from '../map/model';
import { loadTencentMap, markerIcon, type TencentSdk } from '../map/sdk';
const props = withDefaults(
	defineProps<{
		points?: StorePoint[];
		selectedId?: number;
		editable?: boolean;
		position?: { longitude: number; latitude: number };
	}>(),
	{ points: () => [] }
);
const emit = defineEmits<{
	select: [id: number];
	pick: [position: { longitude: number; latitude: number }];
	ready: [];
}>();
const container = ref<HTMLDivElement>(),
	loading = ref(false),
	error = ref('');
let sdk: TencentSdk,
	map: any,
	markers: any,
	selectedMarker: any,
	cluster: any,
	editor: any,
	observer: ResizeObserver | undefined;
let alive = true,
	run = 0;
const clusterLookup = new Map<string, any>();
function latLng(p: { latitude: number | null; longitude: number | null }) {
	return new sdk.LatLng(Number(p.latitude), Number(p.longitude));
}
function dispose() {
	editor?.destroy();
	cluster?.destroy();
	markers?.setMap(null);
	selectedMarker?.setMap(null);
	map?.destroy();
	editor = cluster = markers = selectedMarker = map = undefined;
	clusterLookup.clear();
}
async function init() {
	const current = ++run;
	loading.value = true;
	error.value = '';
	dispose();
	try {
		sdk = await loadTencentMap();
		if (!alive || current !== run || !container.value) return;
		// A neutral viewport is not a store coordinate and is never included in save payloads.
		map = new sdk.Map(container.value, {
			center: new sdk.LatLng(35, 105),
			zoom: 4,
			pitch: 0,
			viewMode: '2D'
		});
		const styles = {
			open: new sdk.MarkerStyle({ width: 30, height: 40, src: markerIcon('#16a34a') }),
			closed: new sdk.MarkerStyle({ width: 30, height: 40, src: markerIcon('#64748b') }),
			selected: new sdk.MarkerStyle({ width: 34, height: 44, src: markerIcon('#2563eb') })
		};
		markers = new sdk.MultiMarker({ map, id: 'store-points', styles, geometries: [] });
		selectedMarker = new sdk.MultiMarker({ map, id: 'store-selected', styles, geometries: [] });
		if (props.editable) {
			editor = new sdk.tools.GeometryEditor({
				map,
				overlayList: [{ id: 'location', overlay: markers }],
				activeOverlayId: 'location',
				actionMode: sdk.tools.constants.EDITOR_ACTION.INTERACT,
				selectable: true,
				enableKeyboardDelete: false,
				enableKeyboardCtrl: false,
				enableKeyboardEsc: false
			});
			editor.on('adjust_complete', (geometry: any) => {
				if (geometry?.position) emitPick(geometry.position);
			});
			map.on('click', (event: any) => {
				if (event.latLng) emitPick(event.latLng);
			});
		} else {
			cluster = new sdk.MarkerCluster({
				map,
				enableDefaultStyle: false,
				minimumClusterSize: 2,
				gridSize: 60,
				maxZoom: 18,
				geometries: []
			});
			cluster.on('cluster_changed', renderClusters);
			markers.on('click', (event: any) => {
				const item = clusterLookup.get(event.geometry?.id);
				if (item) {
					map.fitBounds(item.bounds, { padding: 60 });
					if (map.getZoom() < 20) map.setZoom(Math.min(20, map.getZoom() + 1));
				} else if (event.geometry?.properties?.storeId != null)
					emit('select', event.geometry.properties.storeId);
			});
			selectedMarker.on('click', (event: any) => {
				if (event.geometry?.properties?.storeId != null)
					emit('select', event.geometry.properties.storeId);
			});
		}
		update();
		fit();
		emit('ready');
	} catch {
		if (current === run)
			error.value =
				'腾讯地图未能启动，请检查网络、网页 Key 授权或浏览器 WebGL 支持。门店列表仍可使用。';
	} finally {
		if (current === run) loading.value = false;
	}
}
function emitPick(p: any) {
	emit('pick', {
		longitude: Number(p.getLng().toFixed(7)),
		latitude: Number(p.getLat().toFixed(7))
	});
}
function renderClusters() {
	if (!cluster || !markers) return;
	clusterLookup.clear();
	const styles: any = {
		open: new sdk.MarkerStyle({ width: 30, height: 40, src: markerIcon('#16a34a') }),
		closed: new sdk.MarkerStyle({ width: 30, height: 40, src: markerIcon('#64748b') }),
		selected: new sdk.MarkerStyle({ width: 34, height: 44, src: markerIcon('#2563eb') })
	};
	const geometries = cluster.getClusters().map((item: any, index: number) => {
		if (item.geometries.length === 1) {
			const point = item.geometries[0];
			return { ...point, styleId: point.properties.status === 1 ? 'open' : 'closed' };
		}
		const id = 'cluster-' + index;
		clusterLookup.set(id, item);
		styles[id] = new sdk.MarkerStyle({
			width: 44,
			height: 44,
			anchor: { x: 22, y: 22 },
			src: markerIcon('#2563eb', String(item.geometries.length))
		});
		return { id, styleId: id, position: item.center };
	});
	markers.setStyles(styles);
	markers.setGeometries(geometries);
}
function update() {
	if (!map) return;
	if (props.editable) {
		markers.setGeometries(
			props.position && hasCoordinates(props.position)
				? [{ id: 'draft', styleId: 'selected', position: latLng(props.position) }]
				: []
		);
		if (props.position && hasCoordinates(props.position)) {
			editor?.select(['draft']);
			map.setCenter(latLng(props.position));
			if (map.getZoom() < 14) map.setZoom(16);
		}
	} else {
		cluster.setGeometries(
			props.points
				.filter(drawable)
				.map(p => ({
					id: 'store-' + p.id,
					position: latLng(p),
					properties: { storeId: p.id, status: p.status }
				}))
		);
		focus();
	}
}
function focus() {
	if (!map || props.editable) return;
	const point = props.points.find(p => p.id === props.selectedId && drawable(p));
	selectedMarker.setGeometries(
		point
			? [
					{
						id: 'selected',
						styleId: 'selected',
						position: latLng(point),
						properties: { storeId: point.id }
					}
				]
			: []
	);
	if (point) {
		map.setCenter(latLng(point));
		map.setZoom(18);
	}
}
function fit() {
	if (!map) return;
	if (props.editable) {
		if (props.position && hasCoordinates(props.position)) {
			map.setCenter(latLng(props.position));
			map.setZoom(16);
		}
		return;
	}
	const points = props.points.filter(drawable);
	if (!points.length) return;
	if (points.length === 1) {
		map.setCenter(latLng(points[0]));
		map.setZoom(16);
		return;
	}
	const bounds = new sdk.LatLngBounds();
	points.forEach(p => bounds.extend(latLng(p)));
	map.fitBounds(bounds, { padding: 60 });
}
watch(
	() => props.points,
	() => {
		update();
		fit();
	},
	{ deep: true }
);
watch(() => props.selectedId, focus);
watch(() => props.position, update, { deep: true });
onMounted(() => {
	void init();
	observer = new ResizeObserver(() => {
		if (map) map.setCenter(map.getCenter());
	});
	if (container.value) observer.observe(container.value);
});
onActivated(() => {
	if (!map) void init();
});
onDeactivated(() => {
	run++;
	dispose();
});
onBeforeUnmount(() => {
	alive = false;
	run++;
	observer?.disconnect();
	dispose();
});
defineExpose({ fit });
</script>
<style scoped>
.map-shell {
	position: relative;
	height: 100%;
	min-height: 320px;
	background: var(--el-fill-color-light);
	overflow: hidden;
	border-radius: 6px;
}
.map-canvas {
	height: 100%;
	width: 100%;
	min-height: 320px;
}
.map-message {
	position: absolute;
	inset: 0;
	display: grid;
	place-items: center;
	background: var(--el-bg-color);
	z-index: 2;
}
.map-tools {
	position: absolute;
	top: 12px;
	left: 12px;
	display: flex;
	align-items: center;
	gap: 10px;
	background: var(--el-bg-color);
	padding: 5px 9px;
	border-radius: 4px;
	color: var(--el-text-color-secondary);
	font-size: 12px;
}
</style>
