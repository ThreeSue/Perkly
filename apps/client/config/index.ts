import { isMp, type Config } from "@/.cool";
import { dev } from "./dev";
import { prod } from "./prod";

// 判断当前是否为开发环境
export const isDev = process.env.NODE_ENV == "development";

// 公开浏览、登录和找回密码接口允许游客访问，不携带旧token或触发刷新。
export const ignoreTokens: string[] = [
	"/app/merchant/category/list",
	"/app/merchant/store/page",
	"/app/merchant/store/info",
	"/app/coupon/page",
	"/app/coupon/info",
	"/app/user/login/captcha",
	"/app/user/login/smsCode",
	"/app/user/login/resetPassword",
	"/app/user/login/password",
	"/app/user/login/phone",
	"/app/user/login/register",
	"/app/user/login/refreshToken"
];

// 根据环境导出最终配置
export const config = {
	name: "券管家",
	locale: "zh-cn",
	showDarkButton: false,
	isCustomTabBar: true,
	backTop: true,
	wx: {
		debug: false
	},
	...(isDev ? dev() : prod())
} as Config;
