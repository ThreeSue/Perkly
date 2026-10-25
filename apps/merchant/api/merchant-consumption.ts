import { merchantRequest } from "@/api/merchant-http";
import type {
	CouponInspection,
	ConsumptionOrder,
	MerchantId,
	MerchantPage,
	RedemptionRecord
} from "@/types/merchant-api";
export const inspectCoupon = (code: string) =>
	merchantRequest<CouponInspection>("/merchant/redemption/inspect", "POST", { code });
export const prepareConsumption = (input: {
	code: string;
	consumptionAmount: number;
	remark: string;
	requestId: string;
}) => merchantRequest<ConsumptionOrder>("/merchant/consumption/prepare", "POST", input);
export const redeemContent = (input: { code: string; remark: string; requestId: string }) =>
	merchantRequest<ConsumptionOrder>("/merchant/consumption/redeemContent", "POST", input);
export const reviseConsumption = (input: {
	orderId: MerchantId;
	version: number;
	consumptionAmount: number;
	remark: string;
}) => merchantRequest<ConsumptionOrder>("/merchant/consumption/revise", "POST", input);
export const confirmConsumption = (input: {
	orderId: MerchantId;
	version: number;
	receivedAmount: number;
}) => merchantRequest<ConsumptionOrder>("/merchant/consumption/confirm", "POST", input);
export const cancelConsumption = (orderId: MerchantId) =>
	merchantRequest<ConsumptionOrder>("/merchant/consumption/cancel", "POST", { orderId });
export const getConsumption = (id: MerchantId) =>
	merchantRequest<ConsumptionOrder>(
		"/merchant/consumption/info?id=" + encodeURIComponent(String(id))
	);
export const listConsumptions = (
	input: {
		page?: number;
		size?: number;
		status?: string;
		orderNo?: string;
		customerCouponId?: MerchantId;
	} = {}
) =>
	merchantRequest<MerchantPage<ConsumptionOrder>>("/merchant/consumption/page", "POST", {
		page: 1,
		size: 20,
		...input
	});
export const listRedemptions = (page: number, scope: "self" | "store") =>
	merchantRequest<MerchantPage<RedemptionRecord>>("/merchant/redemption/page", "POST", {
		page,
		size: 20,
		scope
	});
