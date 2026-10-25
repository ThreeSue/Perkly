import {
	merchantRestoring,
	merchantSignedIn,
	merchantCanOperate,
	restoreMerchantAuthentication
} from "@/stores/merchant-auth";
import { merchantRouteAccess } from "@/utils/merchant-access";
let redirecting = false;
export function ensureMerchantAccess(url: string) {
	if (merchantRestoring.value) return true;
	const access = merchantRouteAccess(
		url,
		merchantSignedIn.value,
		false,
		merchantCanOperate.value
	);
	if (access === "allowed" || access === "public") return true;
	if (!redirecting) {
		if (access === "review")
			uni.showToast({ title: "审核通过后开放，请在首页查看进度", icon: "none" });
		redirecting = true;
		uni.reLaunch({
			url: access === "login" ? "/pages/business/login" : "/pages/business/home",
			complete: () => {
				redirecting = false;
			}
		});
	}
	return false;
}
export function enterMerchantWorkspace() {
	uni.switchTab({ url: "/pages/business/home" });
}
export function installMerchantAuthGuard(app: VueApp) {
	void restoreMerchantAuthentication();
	for (const method of ["navigateTo", "redirectTo", "reLaunch", "switchTab"])
		uni.addInterceptor(method, {
			invoke(options: any) {
				return ensureMerchantAccess(options.url ?? "");
			}
		});
	app.mixin({
		onShow() {
			void restoreMerchantAuthentication().then(() => {
				const pages = getCurrentPages();
				const route = pages[pages.length - 1]?.route;
				if (route) ensureMerchantAccess(route);
			});
		}
	});
}
