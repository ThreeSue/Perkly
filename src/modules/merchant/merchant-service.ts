import { merchantRequest } from '/@/merchant-api';

export type MerchantResource = 'store' | 'coupon' | 'order';

// 仅适配商家接口，表格、分页及表单交给框架CRUD组件。
export function createMerchantCrudService(kind: MerchantResource) {
	return {
		async page(params: any) {
			const page = params.page || 1, size = params.size || 20;
			const result = await merchantRequest(`workspace/${kind}/page`, { page, size, keyWord: params.keyWord?.trim() || undefined });
			return { list: result.records || [], pagination: { page, size, total: result.totalRow || 0 } };
		},
		info: ({ id }: { id: number }) => merchantRequest(`workspace/${kind}/info?id=${id}`),
		add: (data: any) => merchantRequest(`workspace/${kind}/save`, data),
		update: (data: any) => merchantRequest(`workspace/${kind}/save`, data),
		delete: (data: { ids: number[] }) => merchantRequest(`workspace/${kind}/delete`, data)
	};
}
