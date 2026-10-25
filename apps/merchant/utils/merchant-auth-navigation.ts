export function merchantAuthBack() {
	uni.navigateBack({ fail: () => uni.redirectTo({ url: "/pages/business/welcome" }) });
}
