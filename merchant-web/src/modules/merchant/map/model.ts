export interface LocationValue {
	address: string;
	longitude: number;
	latitude: number;
}
export interface StorePoint {
	id: number;
	name: string;
	merchantId: number;
	merchantName: string;
	storeTypeId?: number;
	storeTypeName?: string;
	address: string;
	longitude: number | null;
	latitude: number | null;
	status: number;
	statusText?: string;
	located: boolean;
	phone?: string;
	businessHours?: string;
}
export interface StoreMapResult {
	coordinateSystem: string;
	total: number;
	locatedCount: number;
	unlocatedCount: number;
	list: StorePoint[];
}
export interface MapFilters {
	keyword?: string;
	merchantId?: number;
	storeTypeId?: number;
	status?: number;
}
export interface Place extends LocationValue {
	id?: string;
	title?: string;
	province?: string;
	city?: string;
	district?: string;
}
// Keep the selected place name alongside its street address for recognisable directions.
export function formatPlaceAddress(place: Pick<Place, 'address' | 'title'>): string {
	const address = (place.address || '').trim();
	const title = (place.title || '').trim();
	if (!address) return title;
	if (!title || address.includes(title) || title.includes(address)) return title.includes(address) ? title : address;
	return `${address}（${title}）`;
}
// Reject missing/blank coordinates without treating a valid zero as empty.
export function coordinate(value: unknown, max: number): number | undefined {
	if (value == null || typeof value === 'boolean' || (typeof value === 'string' && !value.trim()))
		return;
	const number = Number(value);
	return Number.isFinite(number) && Math.abs(number) <= max ? number : undefined;
}
export function hasCoordinates(value: { longitude?: unknown; latitude?: unknown }): boolean {
	return (
		coordinate(value.longitude, 180) !== undefined &&
		coordinate(value.latitude, 90) !== undefined
	);
}
export function drawable(point: StorePoint): boolean {
	return point.located === true && hasCoordinates(point);
}
export function locationPayload(value: LocationValue): LocationValue {
	if (!hasCoordinates(value)) throw new Error('请先在地图上选择有效位置');
	const address = value.address.trim();
	if (!address || address.length > 500) throw new Error('请核对详细地址，长度为1至500字');
	return {
		address,
		longitude: Number(Number(value.longitude).toFixed(7)),
		latitude: Number(Number(value.latitude).toFixed(7))
	};
}
export function mapFilters(value: MapFilters): MapFilters {
	const keyword = value.keyword?.trim();
	if (keyword && keyword.length > 100) throw new Error('关键词不能超过100字');
	return {
		keyword: keyword || undefined,
		merchantId: value.merchantId || undefined,
		storeTypeId: value.storeTypeId || undefined,
		status: typeof value.status === 'number' ? value.status : undefined
	};
}
