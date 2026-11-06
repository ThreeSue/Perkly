import { merchantRequest } from '/@/merchant-api';

export interface LocationValue {
	address: string;
	longitude: number;
	latitude: number;
}
export interface StorePoint {
	id: number;
	name: string;
	merchantName?: string;
	storeTypeId: number;
	storeTypeName: string;
	address: string;
	longitude: number | null;
	latitude: number | null;
	located: boolean;
	status: number;
	statusText?: string;
	phone?: string;
	businessHours?: string;
	cover?: string;
}
export interface StoreMapResult {
	coordinateSystem: string;
	total: number;
	locatedCount: number;
	unlocatedCount: number;
	list: StorePoint[];
}
export interface MapFilter {
	keyword?: string;
	storeTypeId?: number;
	status?: number;
}
export interface Place extends LocationValue {
	id?: string;
	title?: string;
}
export interface MapPoint {
	id: string;
	longitude: number;
	latitude: number;
	status?: number;
}
export function validCoordinate(longitude: unknown, latitude: unknown): boolean {
	return (
		longitude !== null &&
		longitude !== undefined &&
		longitude !== '' &&
		latitude !== null &&
		latitude !== undefined &&
		latitude !== '' &&
		Number.isFinite(Number(longitude)) &&
		Number.isFinite(Number(latitude)) &&
		Number(longitude) >= -180 &&
		Number(longitude) <= 180 &&
		Number(latitude) >= -90 &&
		Number(latitude) <= 90
	);
}
export function drawable(store: StorePoint): boolean {
	return store.located === true && validCoordinate(store.longitude, store.latitude);
}
export function mapFailureMessage(error: unknown, fallback: string): string {
	const message = (error as { message?: unknown })?.message;
	if (typeof message !== 'string' || !message.trim()) return fallback;
	if (/[（(]\s*121\s*[）)]/.test(message))
		return '地图地址服务返回额度限制（121），请联系平台管理员核对服务额度。仍可手动在地图选点并填写详细地址，核对后保存。';
	if (/[（(]\s*120\s*[）)]/.test(message))
		return '地图地址服务请求过于频繁（120），请稍后重试；也可手动选点并填写详细地址。';
	return message;
}
export function mapFilters(input: MapFilter): MapFilter {
	return {
		keyword: input.keyword?.trim() || undefined,
		storeTypeId: input.storeTypeId,
		status: input.status
	};
}
export function mapQuery(path: string, params: Record<string, string | number | undefined>) {
	const query = new URLSearchParams();
	Object.entries(params).forEach(([key, value]) => {
		if (value !== undefined && value !== '') query.set(key, String(value));
	});
	return merchantRequest(`workspace/map/${path}?${query.toString()}`);
}
export const fetchStoreMap = (filters: MapFilter): Promise<StoreMapResult> =>
	merchantRequest('workspace/store/map', mapFilters(filters));
export const updateStoreLocation = (id: number, location: LocationValue) =>
	merchantRequest('workspace/store/location/update', { id, ...location });
