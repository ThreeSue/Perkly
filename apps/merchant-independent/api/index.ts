import type { MockService } from "@coupon-manager/shared";
import { appConfig } from "@/config/business";
import { mockService } from "@/mocks";
// 页面只调用这个边界。接入后端时替换此实现，不把网络请求写进页面。
const notConnected = () => Promise.reject(new Error("尚未接入真实接口，请先完成 API 适配"));
export const api: MockService = appConfig.mockEnabled
	? mockService
	: {
			login: notConnected,
			list: notConnected,
			claim: notConnected,
			preview: notConnected,
			verify: notConnected,
			logout() {}
		};
