import type { MerchantAdmission, MerchantAdmissionDetails } from "@/types/merchant-admission";
// 用户最终确认继续原入驻流程，不套用“审核发营业码”的不同业务契约。
// 待后端提供“提交即建账号/首店、待审可登录、查询/重提、按版本确认结果”契约后在此适配。
export const merchantOnboardingAvailable = false;
export const onboardingContractMessage =
	"入驻流程保留原方案；提交、审核进度及结果确认接口待后端按该流程对齐。";
export async function submitMerchantOnboarding(
	_input: MerchantAdmissionDetails & { phone: string; password: string }
): Promise<MerchantAdmission> {
	throw new Error(onboardingContractMessage);
}
export async function queryMerchantAdmission(): Promise<MerchantAdmission | null> {
	throw new Error(onboardingContractMessage);
}
export async function acknowledgeMerchantAdmission(
	_applicationId: string,
	_version: number
): Promise<MerchantAdmission> {
	throw new Error(onboardingContractMessage);
}
export async function resubmitMerchantAdmission(
	_applicationId: string,
	_version: number,
	_details: MerchantAdmissionDetails
): Promise<MerchantAdmission> {
	throw new Error(onboardingContractMessage);
}
