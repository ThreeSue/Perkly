import { merchantRequest } from '/@/merchant-api';
import { request } from '/@/cool/service/request';
import { config } from '/@/config';

export interface MerchantMaterial {
	id: number;
	url: string;
	name: string;
	source?: 'UPLOAD' | 'AI';
	aiTaskId?: number;
	parentMaterialId?: number;
}

export interface MaterialCategory {
	id: number;
	name: string;
}

export interface MaterialPage {
	list: MerchantMaterial[];
	total: number;
}

export function uploadMaterial(file: File, signal?: AbortSignal): Promise<MerchantMaterial> {
	const data = new FormData();
	data.append('file', file);
	return request({
		url: `${config.baseUrl}/merchant/governance/materials/upload`,
		method: 'POST',
		data,
		signal,
		timeout: 60000
	}) as Promise<MerchantMaterial>;
}

export function listMaterials(page: number, keyword: string, categoryId?: number, source = 'ALL', merchantId?: number): Promise<MaterialPage> {
	const params = new URLSearchParams({ pageNumber: String(page), pageSize: '20' });
	if (keyword) params.set('keyword', keyword);
	if (categoryId !== undefined) params.set('categoryId', String(categoryId));
	params.set('source', source);
	if (merchantId !== undefined) {
		params.set('merchantId', String(merchantId));
		return request({ url: `${config.baseUrl}/admin/ai/images/materials?${params}`, method: 'GET' }) as Promise<MaterialPage>;
	}
	return merchantRequest(`governance/materials/page?${params}`);
}

export function listMaterialCategories(merchantId?: number): Promise<MaterialCategory[]> {
	if (merchantId !== undefined) return request({ url: `${config.baseUrl}/admin/ai/images/categories?merchantId=${merchantId}`, method: 'GET' }) as Promise<MaterialCategory[]>;
	return merchantRequest('governance/materials/categories');
}

export function importCoverMaterials(): Promise<{ imported: number; skipped: number }> {
	return merchantRequest('governance/materials/import-covers', {});
}
