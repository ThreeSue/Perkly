import { nextTick, onUnmounted, ref } from "vue";
import {
	merchantAdmission,
	merchantAdmissionNeedsNotice,
	refreshMerchantAdmission,
	acknowledgeCurrentAdmission
} from "@/stores/merchant-admission";
import { merchantSessionVersion } from "@/api/merchant-http";
export function useMerchantAdmissionHome() {
	const notice = ref<ClConfirmComponentPublicInstance | null>(null);
	let visible = false,
		revision = 0,
		timer: ReturnType<typeof setInterval> | null = null,
		shown = "";
	function hide() {
		visible = false;
		revision++;
		if (timer) clearInterval(timer);
		timer = null;
		notice.value?.close();
	}
	function showNotice() {
		const value = merchantAdmission.value;
		if (!visible || !value || !merchantAdmissionNeedsNotice.value) return;
		const version = merchantSessionVersion(),
			round = revision;
		notice.value?.open({
			title: value.status === "approved" ? "入驻审核通过" : "入驻申请需修改",
			message:
				value.status === "approved"
					? "审核通过，经营权限已按账号身份开放。点击确定仅收起首页提示，申请与审核历史仍保留。"
					: value.rejectionReason || "请在入驻申请中查看审核意见并修改资料。",
			confirmText: "确定",
			showCancel: false,
			beforeClose(action, controls) {
				if (action !== "confirm") {
					controls.close();
					return;
				}
				controls.showLoading();
				void acknowledgeCurrentAdmission(value.applicationId, value.version).then(
					(success) => {
						if (visible && round === revision && version === merchantSessionVersion()) {
							controls.hideLoading();
							if (success) controls.close();
						}
					}
				);
			}
		});
	}
	async function sync() {
		const round = revision;
		await refreshMerchantAdmission();
		await nextTick();
		if (!visible || round !== revision) return;
		const value = merchantAdmission.value,
			key = value ? value.applicationId + ":" + value.version + ":" + value.status : "";
		if (merchantAdmissionNeedsNotice.value && key !== shown) {
			shown = key;
			showNotice();
		}
	}
	function show() {
		visible = true;
		revision++;
		void sync();
		if (timer) clearInterval(timer);
		timer = setInterval(() => {
			if (merchantAdmission.value?.status === "pending") void sync();
		}, 30000);
	}
	onUnmounted(hide);
	return { notice, showNotice, show, hide };
}
