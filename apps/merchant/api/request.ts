import { appConfig } from "@/config/business";
export interface ApiEnvelope<T> {
	code: number;
	message: string;
	data: T;
}
// 暂定接口结构，最终以组员提供的后端契约为准。不会自动调用。
export function request<T>(
	path: string,
	method: "GET" | "POST" = "GET",
	data: Record<string, unknown> = {},
	token = ""
): Promise<T> {
	return new Promise((resolve, reject) => {
		if (!appConfig.baseUrl) {
			reject(new Error("请先配置后端接口地址"));
			return;
		}
		uni.request({
			url: appConfig.baseUrl + path,
			method,
			data,
			timeout: 10000,
			header: token ? { Authorization: "Bearer " + token } : {},
			success(response) {
				const body = response.data as ApiEnvelope<T> | null;
				if (
					response.statusCode >= 200 &&
					response.statusCode < 300 &&
					body != null &&
					body.code === 0
				)
					resolve(body.data);
				else reject(new Error(body?.message || "请求失败，请稍后重试"));
			},
			fail() {
				reject(new Error("无法连接服务，请检查网络后重试"));
			}
		});
	});
}
