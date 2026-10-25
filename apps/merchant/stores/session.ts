import { computed, readonly, shallowRef } from "vue";
import { resolveMerchantRecordAccess, type MerchantRecordContext } from "@/utils/merchant-records";

// 只接受 merchant/staff/me 验证后的门店身份；不读取URL或缓存中的角色授权。
const verifiedContext = shallowRef<MerchantRecordContext | null>(null);
const admissionAllowed = shallowRef(false);
export const merchantAdmissionAllowed = readonly(admissionAllowed);
export function setMerchantAdmissionAllowed(value: boolean) {
	admissionAllowed.value = value;
}
export const merchantSession = readonly(verifiedContext);
export const merchantRecordAccess = computed(() =>
	resolveMerchantRecordAccess(verifiedContext.value)
);
export const merchantSessionReady = computed(() => merchantRecordAccess.value !== null);

// 仅供未来正式认证适配器使用。role 是前端标准化角色，不推测后端权限码。
// 门店/账号/授权变更应整体替换上下文，不能由页面自行切换记录范围。
export function applyVerifiedMerchantContext(context: MerchantRecordContext) {
	verifiedContext.value = resolveMerchantRecordAccess(context)
		? {
				...context,
				stores: context.stores.map((store) => ({ ...store }))
			}
		: null;
}

// 正式适配器在退出、过期或撤销授权时调用，并同时清除私有数据及使在途响应失效。
export function clearMerchantSession() {
	verifiedContext.value = null;
	admissionAllowed.value = false;
}
