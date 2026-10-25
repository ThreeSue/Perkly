import { merchantRequest } from "@/api/merchant-http";
import type { MerchantStaff, MerchantTokens } from "@/types/merchant-api";
export type MerchantLoginInput = {
	username: string;
	password: string;
	captchaId: string;
	verifyCode: string;
};
export const getMerchantCaptcha = () =>
	merchantRequest<{ captchaId: string; data: string }>("/merchant/auth/captcha", "GET", {}, true);
export const loginMerchant = (input: MerchantLoginInput) =>
	merchantRequest<MerchantTokens>("/merchant/auth/login", "POST", input, true);
export const getMerchantStaff = () => merchantRequest<MerchantStaff>("/merchant/staff/me");
export const logoutMerchantRemote = () =>
	merchantRequest<null>("/merchant/auth/logout", "POST", {});
export const changeMerchantPassword = (oldPassword: string, newPassword: string) =>
	merchantRequest<null>("/merchant/auth/changePassword", "POST", { oldPassword, newPassword });
