import axios from 'axios';
import dev from '/@/config/dev';
import prod from '/@/config/prod';

export interface ActivationApplication {
	applicationNo: string;
	merchantName: string;
	state: 'PENDING' | 'REJECTED' | 'APPROVED' | 'CODE_EXPIRED' | 'ACTIVATED';
	stateText: string;
	accountId?: string | number | null;
}

// 独立公共实例：不读取经营Token、不参与经营会话刷新，不记录手机号凭证和密码。
const publicHttp = axios.create({ timeout: 20000, withCredentials: false });
export class ActivationRequestError extends Error {
	constructor(
		message: string,
		public uncertain = false
	) {
		super(message);
	}
}
async function request<T>(path: string, body?: object): Promise<T> {
	try {
		const { data } = await publicHttp.request({
			url: `${(import.meta.env.DEV ? dev : prod).baseUrl}${path}`,
			method: body === undefined ? 'GET' : 'POST',
			data: body
		});
		if (data?.code !== 1000)
			throw new ActivationRequestError(data?.message || '请求失败，请重试');
		return data.data as T;
	} catch (error) {
		if (error instanceof ActivationRequestError) throw error;
		throw new ActivationRequestError('网络请求失败，请检查连接后重试', true);
	}
}
const root = '/app/merchant/onboarding/';
export const activationApi = {
	captcha: () => request<{ captchaId: string; data: string }>('/merchant/auth/captcha'),
	sms: (phone: string, captchaId: string, verifyCode: string) =>
		request(root + 'smsCode', { phone, captchaId, verifyCode }),
	verify: (phone: string, smsCode: string) =>
		request<{ onboardingToken: string; expiresIn: number }>(root + 'verify', {
			phone,
			smsCode
		}),
	list: (onboardingToken: string) =>
		request<ActivationApplication[]>(root + 'list', { onboardingToken }),
	info: (onboardingToken: string, applicationNo: string) =>
		request<ActivationApplication>(root + 'info', { onboardingToken, applicationNo }),
	activate: (onboardingToken: string, code: string, username: string, password: string) =>
		request<ActivationApplication>('/merchant/auth/activate', {
			onboardingToken,
			code,
			username,
			password
		})
};
