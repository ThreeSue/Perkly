import { featureApi } from '/$/workspace/feature-api';

export interface CoverDraft {
	couponId?: number;
	merchantId?: number;
	name: string;
	type: string;
	description?: string;
	scopeType: number;
	storeIds: number[];
	contentItems: { name: string; quantity: number; itemDescription?: string }[];
	minSpend?: number;
	discountAmount?: number;
	deductAmount?: number;
	discountRate?: number;
}
export interface ImageTask {
	id: number; merchantId: number; merchantName: string; actorName: string;
	requestId: string; parentTaskId?: number; parentMaterialId?: number;
	mode: string; model: string; prompt: string; status: string;
	materialId?: number; url?: string; archived: boolean; durationMs?: number;
	estimatedCost?: number; errorMessage?: string; createTime: string;
}
export interface ImageQuota {
	merchantId: number; day: string; limit: number; used: number; remaining: number;
	enabled: boolean; keyConfigured: boolean; models: string[];
}
export interface ImageCreate {
	requestId: string; mode: 'GENERATE' | 'EDIT'; model: string;
	prompt: string; parentTaskId?: number; draft: CoverDraft;
}
export const statusNames: Record<string, string> = {
	QUEUED: '排队中', RUNNING: '生成中', UNKNOWN: '云端结果待核查',
	GENERATED: '已生成，等待入库', STORING: '保存素材中',
	STORAGE_FAILED: '已生成，素材保存失败', STORED: '已入素材库', FAILED: '生成失败'
};
export const isProcessing = (status: string) => ['QUEUED', 'RUNNING', 'GENERATED', 'STORING'].includes(status);
const numeric = (value: unknown) => value === '' || value == null ? undefined : Number(value);
// 每次打开读取当前未保存表单；不把封面、手机号或整个业务实体送往模型。
export function coverDraft(form: Record<string, any>, merchantId?: number): CoverDraft {
	return {
		couponId: numeric(form.id), merchantId: numeric(form.merchantId ?? merchantId),
		name: form.name || '', type: form.type || '', description: form.description || undefined,
		scopeType: Number(form.scopeType ?? 0), storeIds: (form.storeIds || []).map(Number),
		contentItems: ['PACKAGE', 'EXPERIENCE', 'EXCHANGE'].includes(form.type)
			? (form.contentItems || []).map((item: any) => ({ name: item.name || '', quantity: Number(item.quantity), itemDescription: item.itemDescription || undefined })) : [],
		minSpend: numeric(form.minSpend), discountAmount: numeric(form.discountAmount),
		deductAmount: numeric(form.deductAmount), discountRate: numeric(form.discountRate)
	};
}
export const imageApi = {
	prompt: (draft: CoverDraft): Promise<{ prompt: string; merchantName: string; storeNames: string[] }> => featureApi('ai/images/prompt', draft),
	quota: (merchantId: number): Promise<ImageQuota> => featureApi(`ai/images/quota?merchantId=${merchantId}`),
	page: (merchantId: number, page = 1): Promise<{ list: ImageTask[]; total: number }> => featureApi(`ai/images/page?merchantId=${merchantId}&pageNumber=${page}&pageSize=12`),
	create: (input: ImageCreate): Promise<ImageTask> => featureApi('ai/images/create', input),
	select: (merchantId: number, couponId: number | undefined, id: number): Promise<ImageTask> => featureApi('ai/images/select', { merchantId, couponId, id }),
	retryStorage: (merchantId: number, couponId: number | undefined, id: number): Promise<ImageTask> => featureApi('ai/images/retry-storage', { merchantId, couponId, id })
};
