import { config } from "@/config";
import type { MerchantTokens } from "@/types/merchant-api";

const key = "qgj.merchant.credentials.v1";
type StoredTokens = MerchantTokens & { accessUntil: number; refreshUntil: number };
let credentials: StoredTokens | null = null;
let epoch = 0;
let refreshing: Promise<void> | null = null;
let expired = () => {};
export class MerchantRequestError extends Error {
	constructor(
		message: string,
		public uncertain = false,
		public code = 0
	) {
		super(message);
	}
}
export const merchantSessionVersion = () => epoch;
export const hasMerchantCredentials = () => credentials !== null;
export function onMerchantSessionExpired(callback: () => void) {
	expired = callback;
}
export function clearMerchantCredentials() {
	epoch++;
	credentials = null;
	uni.removeStorageSync(key);
}
export function saveMerchantCredentials(value: MerchantTokens, newSession = true) {
	if (!value?.token || !value.refreshToken || !(value.expire > 0) || !(value.refreshExpire > 0))
		throw new Error("商家登录响应无效");
	if (newSession) epoch++;
	credentials = {
		...value,
		accessUntil: Date.now() + value.expire * 1000,
		refreshUntil: Date.now() + value.refreshExpire * 1000
	};
	uni.setStorageSync(key, credentials);
}
export function loadMerchantCredentials() {
	const value = uni.getStorageSync(key) as StoredTokens | null;
	if (
		value &&
		typeof value.token === "string" &&
		typeof value.refreshToken === "string" &&
		value.refreshUntil > Date.now()
	) {
		credentials = value;
		epoch++;
	} else clearMerchantCredentials();
}
async function send<T>(
	path: string,
	method: "GET" | "POST",
	data: any,
	token?: string
): Promise<T> {
	return new Promise((resolve, reject) => {
		uni.request({
			url: config.baseUrl + path,
			method,
			data,
			timeout: 10000,
			header: {
				"Content-Type": "application/json",
				...(token ? { Authorization: token } : {})
			},
			success(response) {
				const body = response.data as { code?: number; message?: string; data?: T } | null;
				if (response.statusCode === 401 || body?.code === 401)
					return reject(
						new MerchantRequestError(
							body?.message || "登录已过期，请重新登录",
							false,
							401
						)
					);
				if (response.statusCode < 200 || response.statusCode >= 300 || body?.code !== 1000)
					return reject(
						new MerchantRequestError(
							body?.message || "服务暂不可用，请稍后重试",
							response.statusCode >= 500 && method === "POST",
							response.statusCode === 403 ? 403 : (body?.code ?? response.statusCode)
						)
					);
				resolve(body.data as T);
			},
			fail() {
				reject(
					new MerchantRequestError(
						method === "POST"
							? "请求结果未确认，请查询结果或重试原操作"
							: "网络连接失败，请检查网络后重试",
						method === "POST"
					)
				);
			}
		});
	});
}
async function refresh() {
	if (refreshing) return refreshing;
	const version = epoch,
		value = credentials;
	if (!value || value.refreshUntil <= Date.now())
		throw new MerchantRequestError("登录已过期，请重新登录", false, 401);
	const task = (async () => {
		const result = await send<MerchantTokens>("/merchant/auth/refreshToken", "POST", {
			refreshToken: value.refreshToken
		});
		if (version !== epoch) throw new Error("账号已切换，请重新操作");
		saveMerchantCredentials(result, false);
	})();
	refreshing = task;
	try {
		await task;
	} finally {
		if (refreshing === task) refreshing = null;
	}
}
export async function merchantRequest<T>(
	path: string,
	method: "GET" | "POST" = "GET",
	data: any = {},
	publicRequest = false
): Promise<T> {
	if (publicRequest) return send<T>(path, method, data);
	const version = epoch;
	const assertCurrent = () => {
		if (version !== epoch) throw new Error("登录状态已改变，请重新操作");
	};
	try {
		if (!credentials) throw new MerchantRequestError("请先登录商家账号", false, 401);
		if (credentials.accessUntil <= Date.now() + 10000) await refresh();
		assertCurrent();
		let result: T;
		const sentToken = credentials?.token;
		try {
			result = await send<T>(path, method, data, sentToken);
		} catch (error) {
			assertCurrent();
			if (!(error instanceof MerchantRequestError) || error.code !== 401) throw error;
			if (credentials?.token === sentToken) await refresh();
			assertCurrent();
			result = await send<T>(path, method, data, credentials?.token);
		}
		assertCurrent();
		return result;
	} catch (error) {
		assertCurrent();
		if (error instanceof MerchantRequestError && error.code === 401) {
			clearMerchantCredentials();
			expired();
		}
		throw error;
	}
}
