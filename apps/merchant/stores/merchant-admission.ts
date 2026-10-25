import { computed, readonly, ref, shallowRef, watch } from "vue";
import { merchantIdentity } from "@/stores/merchant-auth";
import {
	queryMerchantAdmission,
	acknowledgeMerchantAdmission,
	resubmitMerchantAdmission,
	merchantOnboardingAvailable
} from "@/api/merchant-onboarding";
import { merchantSessionVersion } from "@/api/merchant-http";
import { setMerchantAdmissionAllowed } from "@/stores/session";
import type { MerchantAdmission, MerchantAdmissionDetails } from "@/types/merchant-admission";
const admission = shallowRef<MerchantAdmission | null>(null),
	error = ref(""),
	busy = ref(false);
let round = 0;
watch(
	() => merchantIdentity.value?.id,
	() => {
		round++;
		admission.value = null;
		error.value = "";
		busy.value = false;
	},
	{ flush: "sync" }
);
export const merchantAdmission = readonly(admission);
export const merchantAdmissionError = readonly(error);
export const merchantAdmissionBusy = readonly(busy);
export const merchantAdmissionNeedsNotice = computed(
	() => !!admission.value && admission.value.status !== "pending" && !admission.value.acknowledged
);
export const merchantAdmissionVisible = computed(
	() =>
		!!admission.value &&
		(admission.value.status !== "approved" || !admission.value.acknowledged)
);
async function request(action: () => Promise<MerchantAdmission | null>, affectsEligibility = true) {
	if (busy.value || !merchantIdentity.value) return false;
	const version = merchantSessionVersion(),
		current = ++round;
	busy.value = true;
	error.value = "";
	try {
		const result = await action();
		if (version !== merchantSessionVersion() || current !== round) return false;
		if (
			result &&
			(String(result.accountId) !== String(merchantIdentity.value?.id) ||
				String(result.merchantId) !== String(merchantIdentity.value?.merchantId))
		)
			throw new Error("入驻申请归属验证失败");
		admission.value = result;
		if (affectsEligibility) setMerchantAdmissionAllowed(result?.status === "approved");
		return true;
	} catch (cause) {
		if (version === merchantSessionVersion() && current === round)
			error.value = cause instanceof Error ? cause.message : "申请状态暂时无法读取";
		return false;
	} finally {
		if (current === round) busy.value = false;
	}
}
export function refreshMerchantAdmission() {
	return merchantOnboardingAvailable ? request(queryMerchantAdmission) : Promise.resolve(false);
}
export function acknowledgeCurrentAdmission(applicationId: string, version: number) {
	const value = admission.value;
	return value && value.applicationId === applicationId && value.version === version
		? request(() => acknowledgeMerchantAdmission(value.applicationId, value.version), false)
		: Promise.resolve(false);
}
export function resubmitCurrentAdmission(details: MerchantAdmissionDetails) {
	const value = admission.value;
	return value
		? request(() => resubmitMerchantAdmission(value.applicationId, value.version, details))
		: Promise.resolve(false);
}
