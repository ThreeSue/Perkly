// 前端标准化授权模型，不是后端 DTO；只能由正式员工身份/门店适配器映射。
export type MerchantRecordContext = {
	operatorId: string;
	merchantId: string;
	storeId: string;
	stores: { storeId: string; merchantId: string; role: "employee" | "manager" }[];
};

export function resolveMerchantRecordAccess(context: MerchantRecordContext | null) {
	if (
		!context ||
		!context.operatorId?.trim() ||
		!context.merchantId?.trim() ||
		!context.storeId?.trim() ||
		!Array.isArray(context.stores)
	)
		return null;
	const grants = context.stores.filter(
		(store) => store.storeId === context.storeId && store.merchantId === context.merchantId
	);
	// 缺失、冲突或未知授权均拒绝，不能把未知角色默认视为店长。
	if (grants.length !== 1 || !["employee", "manager"].includes(grants[0].role)) return null;
	return {
		scope: grants[0].role === "manager" ? ("store" as const) : ("self" as const),
		merchantId: context.merchantId,
		storeId: context.storeId,
		operatorId: grants[0].role === "employee" ? context.operatorId : null
	};
}

export function shanghaiDate(now = Date.now()): string {
	return new Date(now + 8 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

export function createRecordDateShortcuts(today: string) {
	const before = (days: number) =>
		new Date(Date.parse(today + "T00:00:00Z") - days * 86400000).toISOString().slice(0, 10);
	return [
		{ label: "今天", value: [today, today] },
		{ label: "近7天", value: [before(6), today] },
		{ label: "近30天", value: [before(29), today] }
	];
}
