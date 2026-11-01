import { service } from '/@/cool';
import type { LocationValue, MapFilters, Place, StoreMapResult } from './model';
import { locationPayload, mapFilters } from './model';

const prefix = 'admin/merchant/';
export const storeMapApi = {
	list: (filters: MapFilters): Promise<StoreMapResult> =>
		service.request({ url: prefix + 'store/map', method: 'POST', data: mapFilters(filters) }),
	update: (id: number, value: LocationValue): Promise<boolean> =>
		service.request({
			url: prefix + 'store/location/update',
			method: 'POST',
			data: { id, ...locationPayload(value) }
		}),
	suggest: (
		keyword: string,
		region?: string
	): Promise<{ coordinateSystem: string; list: Place[]; total: number }> =>
		service.request({
			url: prefix + 'map/suggest',
			method: 'GET',
			params: { keyword: keyword.trim(), region: region?.trim() || undefined }
		}),
	geocode: (address: string, region?: string): Promise<Place> =>
		service.request({
			url: prefix + 'map/geocode',
			method: 'GET',
			params: { address: address.trim(), region: region?.trim() || undefined }
		}),
	reverse: (longitude: number, latitude: number): Promise<Place> =>
		service.request({
			url: prefix + 'map/reverseGeocode',
			method: 'GET',
			params: { longitude, latitude }
		})
};
