import { request } from './cool/service/request';
import { config } from './config';

export function merchantRequest(path: string, data?: unknown): Promise<any> {
	return request({
		url: `${config.baseUrl}/merchant/${path}`,
		method: data === undefined ? 'GET' : 'POST',
		data
	}) as any;
}

export function uploadMerchantImage(file: File, signal?: AbortSignal): Promise<string> {
	const data = new FormData();
	data.append('file', file);
	return request({ url: `${config.baseUrl}/merchant/workspace/image/upload`, method: 'POST', data, signal, timeout: 60000 }) as any;
}
