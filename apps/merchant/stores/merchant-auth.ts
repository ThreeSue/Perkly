import { computed, readonly, ref, shallowRef } from "vue";
import {
	getMerchantStaff,
	loginMerchant,
	logoutMerchantRemote,
	type MerchantLoginInput
} from "@/api/merchant-auth";
import {
	loadMerchantCredentials,
	saveMerchantCredentials,
	clearMerchantCredentials,
	hasMerchantCredentials,
	merchantSessionVersion,
	onMerchantSessionExpired
} from "@/api/merchant-http";
import {
	applyVerifiedMerchantContext,
	clearMerchantSession,
	merchantRecordAccess,
	merchantAdmissionAllowed,
	setMerchantAdmissionAllowed
} from "@/stores/session";
import { verifiedStaff } from "@/utils/merchant-staff";
import type { MerchantStaff } from "@/types/merchant-api";
const identity = shallowRef<MerchantStaff | null>(null);
const restoring = ref(true),
	signingOut = ref(false);
let restoration: Promise<void> | null = null;
let authAttempt = 0;
export const merchantIdentity = readonly(identity);
export const merchantRestoring = readonly(restoring);
export const merchantSignedIn = computed(() => identity.value !== null);
export const merchantIsManager = computed(() => identity.value?.role === "MANAGER");
export const merchantCanOperate = computed(
	() =>
		merchantSignedIn.value &&
		merchantAdmissionAllowed.value &&
		!restoring.value &&
		!signingOut.value
);
export const merchantDisplayRecordAccess = merchantRecordAccess;
export function clearMerchantAuthentication() {
	authAttempt++;
	clearMerchantCredentials();
	identity.value = null;
	clearMerchantSession();
}
onMerchantSessionExpired(() => {
	identity.value = null;
	clearMerchantSession();
	uni.reLaunch({ url: "/pages/business/login" });
});
function accept(value: MerchantStaff) {
	const staff = verifiedStaff(value);
	applyVerifiedMerchantContext({
		operatorId: String(staff.id),
		merchantId: String(staff.merchantId),
		storeId: String(staff.storeId),
		stores: [
			{
				storeId: String(staff.storeId),
				merchantId: String(staff.merchantId),
				role: staff.role === "MANAGER" ? "manager" : "employee"
			}
		]
	});
	identity.value = staff;
	// 当前已确认的staff/me只返回已开通账号；待审账号仍需原入驻契约补齐。
	setMerchantAdmissionAllowed(true);
}
export async function refreshMerchantIdentity() {
	const version = merchantSessionVersion();
	const staff = await getMerchantStaff();
	if (version !== merchantSessionVersion()) throw new Error("账号已切换，请重新登录");
	accept(staff);
}
export function restoreMerchantAuthentication() {
	if (restoration) return restoration;
	restoration = (async () => {
		loadMerchantCredentials();
		try {
			if (hasMerchantCredentials()) await refreshMerchantIdentity();
		} catch {
			clearMerchantAuthentication();
		} finally {
			restoring.value = false;
		}
	})();
	return restoration;
}
export async function authenticateMerchant(input: MerchantLoginInput) {
	await restoreMerchantAuthentication();
	const attempt = ++authAttempt;
	const tokens = await loginMerchant(input);
	if (attempt !== authAttempt) throw new Error("本次登录已失效");
	identity.value = null;
	clearMerchantSession();
	try {
		saveMerchantCredentials(tokens);
		await refreshMerchantIdentity();
	} catch (error) {
		if (attempt === authAttempt) clearMerchantAuthentication();
		throw error;
	}
}
export async function logoutMerchant() {
	if (signingOut.value) return false;
	signingOut.value = true;
	let revoked = false;
	try {
		await logoutMerchantRemote();
		revoked = true;
	} finally {
		clearMerchantAuthentication();
		signingOut.value = false;
	}
	return revoked;
}
