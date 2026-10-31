<template>
	<div class="map-host" :aria-busy="loading">
		<div ref="container" class="map-canvas" aria-label="腾讯门店地图" />
		<div v-if="loading" class="map-state">
			<el-icon class="is-loading"><Loading /></el-icon><span>正在加载腾讯地图…</span>
		</div>
		<div v-if="error" class="map-state">
			<el-result icon="warning" title="地图暂时不可用" :sub-title="error"
				><template #extra
					><el-button @click="init">重新加载地图</el-button></template
				></el-result
			>
		</div>
		<el-button
			v-if="!loading && !error && points.length && !editable"
			class="fit-button"
			size="small"
			@click="fitAll"
			>查看全部标记</el-button
		>
		<div v-if="!loading && !error && !points.length" class="empty-tip">
			{{ editable ? '尚未选择位置，请搜索地点或点击地图选点' : '当前没有可绘制的门店位置' }}
		</div>
	</div>
</template>
<script setup lang="ts">
import { nextTick, onMounted, onBeforeUnmount, onActivated, onDeactivated, ref, watch } from 'vue';
import { Loading } from '@element-plus/icons-vue';
import { loadTencentMap } from '../map/tencent-sdk';
import { validCoordinate, type MapPoint } from '../map/store-map';
const props = withDefaults(
	defineProps<{ points: MapPoint[]; selectedId?: string; editable?: boolean }>(),
	{ editable: false }
);
const emit = defineEmits<{
	select: [id: string];
	pick: [value: { longitude: number; latitude: number }];
	ready: [];
}>();
const container = ref<HTMLDivElement>();
const loading = ref(false),
	error = ref('');
let sdk: any, map: any, markers: any, highlight: any, cluster: any, editor: any;
let revision = 0,
	active = true;
let groups = new Map<string, any>();
const disposers: (() => void)[] = [];
const listen = (target: any, event: string, callback: (e: any) => void) => {
	target.on(event, callback);
	disposers.push(() => target.off(event, callback));
};
function icon(color: string, text = '', selected = false) {
	const shape = text
		? `<circle cx="23" cy="23" r="20" fill="${color}" stroke="white" stroke-width="3"/><text x="23" y="29" text-anchor="middle" fill="white" font-size="15" font-family="sans-serif">${text}</text>`
		: `<path d="M23 44C17 35 7 27 7 18a16 16 0 1 1 32 0c0 9-10 17-16 26Z" fill="${color}" stroke="${selected ? '#f59e0b' : 'white'}" stroke-width="${selected ? 4 : 2}"/><circle cx="23" cy="18" r="5" fill="white"/>`;
	return (
		'data:image/svg+xml;charset=UTF-8,' +
		encodeURIComponent(
			`<svg xmlns="http://www.w3.org/2000/svg" width="46" height="48">${shape}</svg>`
		)
	);
}
const position = (point: MapPoint) =>
	new sdk.LatLng(Number(point.latitude), Number(point.longitude));
function fit(points: MapPoint[]) {
	if (!map || !points.length) return;
	if (
		points.length === 1 ||
		points.every(p => p.longitude === points[0].longitude && p.latitude === points[0].latitude)
	) {
		map.setCenter(position(points[0]));
		map.setZoom(props.editable ? 16 : 17);
		return;
	}
	const bounds = new sdk.LatLngBounds();
	points.forEach(p => bounds.extend(position(p)));
	map.fitBounds(bounds, { padding: 50 });
}
function fitAll() {
	fit(props.points);
}
function focus(point: MapPoint) {
	if (map) {
		map.setCenter(position(point));
		map.setZoom(Math.max(map.getZoom(), props.editable ? 16 : 17));
	}
}
function drawClusters() {
	if (!cluster || !markers) return;
	groups = new Map();
	const styles: any = {
		open: new sdk.MarkerStyle({
			width: 34,
			height: 38,
			anchor: { x: 17, y: 38 },
			src: icon('#259565')
		}),
		closed: new sdk.MarkerStyle({
			width: 34,
			height: 38,
			anchor: { x: 17, y: 38 },
			src: icon('#7b8495')
		})
	};
	const geometries = (cluster.getClusters() || []).map((group: any, index: number) => {
		const count = group.geometries.length;
		if (count === 1) {
			const geometry = group.geometries[0];
			return { ...geometry, styleId: geometry.properties?.status === 1 ? 'open' : 'closed' };
		}
		const id = 'cluster-' + index,
			styleId = 'count-' + count;
		groups.set(id, group);
		if (!styles[styleId])
			styles[styleId] = new sdk.MarkerStyle({
				width: 46,
				height: 48,
				anchor: { x: 23, y: 24 },
				src: icon('#4165d7', String(count))
			});
		return { id, styleId, position: group.center };
	});
	markers.setStyles(styles);
	markers.setGeometries(geometries);
}
function sync() {
	if (!map || !markers) return;
	const geometries = props.points
		.filter(p => validCoordinate(p.longitude, p.latitude))
		.map(p => ({
			id: p.id,
			position: position(p),
			styleId: p.status === 1 ? 'open' : 'closed',
			properties: { status: p.status }
		}));
	if (props.editable) {
		markers.setGeometries(geometries);
		if (geometries.length) editor?.select([geometries[0].id]);
	} else {
		cluster.setGeometries(geometries);
		drawClusters();
	}
	const selected = props.points.find(p => p.id === props.selectedId);
	highlight?.setGeometries(
		selected ? [{ id: 'selected', styleId: 'selected', position: position(selected) }] : []
	);
}
function picked(point: any) {
	if (!point) return;
	const longitude = point.getLng(),
		latitude = point.getLat();
	if (validCoordinate(longitude, latitude))
		emit('pick', {
			longitude: Number(longitude.toFixed(7)),
			latitude: Number(latitude.toFixed(7))
		});
}
function dispose() {
	revision++;
	disposers.splice(0).forEach(fn => fn());
	editor?.destroy();
	cluster?.destroy();
	markers?.setMap(null);
	highlight?.setMap(null);
	map?.destroy();
	editor = cluster = markers = highlight = map = undefined;
	groups.clear();
}
async function init() {
	dispose();
	const current = revision;
	loading.value = true;
	error.value = '';
	await nextTick();
	try {
		sdk = await loadTencentMap();
		if (!active || current !== revision || !container.value) return;
		const first = props.points[0];
		map = new sdk.Map(container.value, {
			center: first ? position(first) : new sdk.LatLng(35, 105),
			zoom: first ? 14 : 4,
			viewMode: '2D'
		});
		markers = new sdk.MultiMarker({
			map,
			styles: {
				open: new sdk.MarkerStyle({
					width: 34,
					height: 38,
					anchor: { x: 17, y: 38 },
					src: icon('#259565')
				}),
				closed: new sdk.MarkerStyle({
					width: 34,
					height: 38,
					anchor: { x: 17, y: 38 },
					src: icon('#7b8495')
				})
			},
			geometries: []
		});
		markers.setStopPropagation(true);
		if (props.editable) {
			editor = new sdk.tools.GeometryEditor({
				map,
				overlayList: [{ id: 'location', overlay: markers }],
				actionMode: sdk.tools.constants.EDITOR_ACTION.INTERACT,
				selectable: true,
				enableKeyboardDelete: false,
				enableKeyboardCtrl: false,
				enableKeyboardEsc: false
			});
			listen(editor, 'adjust_complete', (geometry: any) => picked(geometry.position));
			listen(map, 'click', (event: any) => picked(event.latLng));
		} else {
			highlight = new sdk.MultiMarker({
				map,
				styles: {
					selected: new sdk.MarkerStyle({
						width: 44,
						height: 48,
						anchor: { x: 22, y: 48 },
						src: icon('#4165d7', '', true)
					})
				},
				geometries: []
			});
			highlight.setStopPropagation(true);
			listen(highlight, 'click', () => {
				if (props.selectedId) emit('select', props.selectedId);
			});
			cluster = new sdk.MarkerCluster({
				map,
				enableDefaultStyle: false,
				minimumClusterSize: 2,
				gridSize: 60,
				maxZoom: 16,
				geometries: []
			});
			listen(cluster, 'cluster_changed', drawClusters);
			listen(markers, 'click', (event: any) => {
				const id = event.geometry?.id;
				if (!id) return;
				const group = groups.get(id);
				if (group) {
					map.fitBounds(group.bounds, { padding: 60 });
					map.setZoom(Math.min(20, map.getZoom() + 1));
				} else emit('select', id);
			});
		}
		listen(map, 'context_lost', () => {
			error.value = '浏览器地图渲染已中断，请重新加载地图。';
		});
		sync();
		fitAll();
		emit('ready');
	} catch (cause: any) {
		if (current === revision)
			error.value = cause?.message?.startsWith('尚未配置网页地图Key')
				? cause.message
				: '地图加载失败，请检查网络、浏览器硬件加速及网页Key的域名授权后重试。';
	} finally {
		if (current === revision) loading.value = false;
	}
}
watch(
	() => props.points,
	() => {
		sync();
		if (props.editable && props.points[0]) focus(props.points[0]);
	},
	{ deep: true }
);
watch(
	() => props.selectedId,
	() => {
		sync();
		const selected = props.points.find(p => p.id === props.selectedId);
		if (selected) focus(selected);
	}
);
onMounted(init);
let firstActivation = true;
onActivated(() => {
	active = true;
	if (firstActivation) {
		firstActivation = false;
		return;
	}
	void init();
});
onDeactivated(() => {
	active = false;
	dispose();
});
onBeforeUnmount(() => {
	active = false;
	dispose();
});
defineExpose({ focus, fitAll });
</script>
<style scoped>
.map-host {
	height: 100%;
	min-height: 300px;
	position: relative;
	background: var(--el-fill-color-light);
	border-radius: 6px;
	overflow: hidden;
}
.map-canvas {
	position: absolute;
	inset: 0;
}
.map-state {
	position: absolute;
	inset: 0;
	background: var(--el-bg-color);
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 10px;
	z-index: 2;
}
.fit-button {
	position: absolute;
	right: 12px;
	top: 12px;
	z-index: 1;
}
.empty-tip {
	position: absolute;
	left: 12px;
	bottom: 12px;
	padding: 8px 12px;
	background: var(--el-bg-color);
	border-radius: 4px;
	pointer-events: none;
}
</style>
