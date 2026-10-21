import { ref } from "vue";
import type { DemoSession } from "@coupon-manager/shared";
import { api } from "@/api";
export const session = ref<DemoSession | null>(null);
export async function login(nickname: string) {
	session.value = await api.login(nickname);
}
export function logout() {
	api.logout();
	session.value = null;
}
export function requireSession(): boolean {
	if (session.value != null) return true;
	const pages = getCurrentPages();
	if (pages.length === 0 || pages[pages.length - 1].route !== "pages/business/login")
		uni.navigateTo({ url: "/pages/business/login" });
	return false;
}
// 演示会话仅保存在当前应用内存，刷新即退出，各端不会共享身份。
