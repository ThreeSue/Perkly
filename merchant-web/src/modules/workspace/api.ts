import { request } from '/@/cool/service/request';
import { config } from '/@/config';
import { isPlatform } from '/@/workspace';
export const workspaceApi = (path: string, data?: unknown): Promise<any> =>
	request({
		url: `${config.baseUrl}/${isPlatform ? 'admin/workspace' : 'merchant/governance'}/${path}`,
		method: data === undefined ? 'GET' : 'POST',
		data
	}) as any;
