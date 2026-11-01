export type RecordRow = Record<string, any>;
export const couponTypes: Record<string, string> = {
	FULL_REDUCTION: '满减券',
	DISCOUNT: '折扣券',
	CASH: '代金券',
	PACKAGE: '套餐券',
	EXPERIENCE: '体验券',
	EXCHANGE: '兑换券'
};
export const colors: Record<string, string> = {
	FULL_REDUCTION: '#c94b43',
	DISCOUNT: '#367bbd',
	CASH: '#a66a10',
	PACKAGE: '#7b5db8',
	EXPERIENCE: '#247f73',
	EXCHANGE: '#b34b7b'
};
export const purchaseStates = [
	{ value: 'PENDING', label: '待支付' },
	{ value: 'PAID', label: '已完成' },
	{ value: 'CANCELLED', label: '已取消' },
	{ value: 'FAILED', label: '支付失败' },
	{ value: 'EXPIRED', label: '已超时' }
];
export const consumptionStates = [
	{ value: 'PENDING', label: '待结算' },
	{ value: 'COMPLETED', label: '已完成' },
	{ value: 'CANCELLED', label: '已取消' }
];
export function money(value: unknown, cents = false) {
	if (value == null || value === '') return '—';
	const n = Number(value);
	return Number.isFinite(n) ? (n / (cents ? 100 : 1)).toFixed(2) + ' 元' : '—';
}
export function readable(value: unknown) {
	return value == null || value === '' ? '—' : String(value);
}
export function filterPayload(form: RecordRow, range: string[]) {
	return {
		...(form.orderNo?.trim() ? { orderNo: form.orderNo.trim() } : {}),
		...(form.status ? { status: form.status } : {}),
		...(form.merchantId ? { merchantId: form.merchantId } : {}),
		...(form.storeId ? { storeId: form.storeId } : {}),
		...(range?.length === 2 ? { startTime: range[0], endTime: range[1] } : {})
	};
}
export function items(row: RecordRow) {
	return Array.isArray(row.items) ? row.items : [];
}
export function contentItems(row: RecordRow) {
	const v = row.couponSnapshot?.contentItems;
	return Array.isArray(v) ? v : [];
}
