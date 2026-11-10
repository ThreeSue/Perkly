import { request } from '/@/cool/service/request';
import { config } from '/@/config';
import { isPlatform } from '/@/workspace';
export const featureApi = (path: string, data?: unknown): Promise<any> =>
	request({
		url: `${config.baseUrl}/${isPlatform ? 'admin' : 'merchant/governance'}/${path}`,
		method: data === undefined ? 'GET' : 'POST',
		data,
		...(path.startsWith('ai/assistant/workbench/') ? { timeout: 210000 } : {}),
		NProgress: false
	} as any) as any;
