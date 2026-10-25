import type { MerchantStaff } from "@/types/merchant-api";
export function verifiedStaff(value: MerchantStaff): MerchantStaff {
	const validId = (id: unknown) => /^[1-9]\d*$/.test(String(id));
	if (
		!value ||
		!validId(value.id) ||
		!validId(value.merchantId) ||
		!validId(value.storeId) ||
		!["MANAGER", "STAFF"].includes(value.role) ||
		value.canViewStoreRecords !== (value.role === "MANAGER")
	)
		throw new Error("账号角色或绑定门店无效，请联系商家管理员");
	return { ...value };
}
