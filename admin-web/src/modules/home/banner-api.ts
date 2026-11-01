import { service } from '/@/cool';

export type JumpType = 'NONE' | 'STORE' | 'COUPON' | 'WEB';
export type Banner = {
	id?: number;
	title: string;
	imageUrl: string;
	jumpType: JumpType;
	jumpTarget: string | null;
	sort: number;
	status: number;
	startTime: string | null;
	endTime: string | null;
	targetName?: string;
	displayStateText?: string;
};
export type TargetOption = { id: number; name: string; merchantName?: string };
export type PageResult<T> = {
	list: T[];
	pagination: { page: number; size: number; total: number };
};
export const jumpTypes = [
	{ value: 'NONE', label: '不跳转' },
	{ value: 'STORE', label: '门店详情' },
	{ value: 'COUPON', label: '优惠券详情' },
	{ value: 'WEB', label: '网页链接' }
] as const;
export function emptyBanner(): Banner {
	return {
		title: '',
		imageUrl: '',
		jumpType: 'NONE',
		jumpTarget: null,
		sort: 0,
		status: 0,
		startTime: null,
		endTime: null
	};
}
export function bannerPayload(value: Banner): Banner {
	return {
		...(value.id ? { id: value.id } : {}),
		title: value.title.trim(),
		imageUrl: value.imageUrl.trim(),
		jumpType: value.jumpType,
		jumpTarget: value.jumpType === 'NONE' ? null : String(value.jumpTarget ?? '').trim(),
		sort: value.sort,
		status: value.status,
		startTime: value.startTime || null,
		endTime: value.endTime || null
	};
}
const prefix = 'admin/home/banner/';
export const bannerApi = {
	page: (data: {
		page: number;
		size: number;
		keyword?: string;
		status?: number;
		jumpType?: string;
	}): Promise<PageResult<Banner>> =>
		service.request({ url: prefix + 'page', method: 'POST', data }),
	info: (id: number): Promise<Banner> =>
		service.request({ url: prefix + 'info', params: { id } }),
	save: (data: Banner): Promise<unknown> =>
		service.request({
			url: prefix + (data.id ? 'update' : 'add'),
			method: 'POST',
			data: bannerPayload(data)
		}),
	status: (id: number, status: number): Promise<boolean> =>
		service.request({ url: prefix + 'status', method: 'POST', data: { id, status } }),
	delete: (ids: number[]): Promise<boolean> =>
		service.request({ url: prefix + 'delete', method: 'POST', data: { ids } }),
	targets: (type: string, keyword: string, page: number): Promise<PageResult<TargetOption>> =>
		service.request({ url: prefix + 'targetOptions', params: { type, keyword, page } }),
	preview: (): Promise<Banner[]> => service.request({ url: 'app/home/banner/list' })
};
