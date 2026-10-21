import type { ApiId, CouponType } from "./discovery";

export type OrderStatus = "PENDING" | "PAID" | "CANCELLED" | "FAILED" | "EXPIRED";
export type WalletState = "UNUSED" | "USED" | "EXPIRED" | "NOT_STARTED" | "VOID";
export type OrderSummary = {
	id: ApiId;
	orderNo: string;
	merchantName: string;
	orderStatus: OrderStatus;
	orderStatusText: string;
	paymentStatus: string;
	totalAmount: number;
	createdAt: string;
	paidAt: string | null;
	expiresAt: string;
};
export type OrderDetail = OrderSummary & {
	items: {
		id: ApiId;
		couponTemplateId: ApiId;
		couponName: string;
		unitPrice: number;
		quantity: number;
		subtotal: number;
		cover: string | null;
		ruleVersion: number;
	}[];
	couponIds: ApiId[];
};
export type WalletCoupon = {
	id: ApiId;
	couponTemplateId: ApiId;
	merchantId: ApiId;
	merchantName: string;
	name: string;
	type: CouponType;
	cover: string | null;
	state: WalletState;
	stateText: string;
	validFrom: string;
	validTo: string;
	issuedAt: string;
	usedAt: string | null;
	sourceType: string;
};
export type WalletDetail = WalletCoupon & {
	code: string;
	qrCodeContent: string;
	qrCodeImage: string;
	rule: {
		name: string;
		type: CouponType;
		description: string;
		scopeType: number;
		storeIds: ApiId[];
		storeNames: string[];
		contentItems: unknown[];
		ruleVersion: number;
		allowCombine: number;
		salePrice: number;
		minSpend: number | null;
		discountAmount: number | null;
		deductAmount: number | null;
		maxDiscount: number | null;
		discountRate: number | null;
	};
};
export type PurchaseInput = { couponTemplateId: ApiId; quantity: number; requestId: string };
