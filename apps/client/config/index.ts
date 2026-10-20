import { isMp, type Config } from "@/.cool";
import { dev } from "./dev";
import { prod } from "./prod";

// 判断当前是否为开发环境
export const isDev = process.env.NODE_ENV == "development";

// 公开浏览接口允许游客访问，不发送 token，也不触发过期 token 刷新。
export const ignoreTokens: string[] = [
	"/app/merchant/category/list",
	"/app/merchant/store/page",
	"/app/merchant/store/info",
	"/app/coupon/page",
	"/app/coupon/info"
];

// 根据环境导出最终配置
export const config = {
	name: "券管家",
	locale: "zh-cn",
	showDarkButton: false,
	isCustomTabBar: false,
	backTop: true,
	wx: {
		debug: false
	},
	...(isDev ? dev() : prod())
} as Config;
