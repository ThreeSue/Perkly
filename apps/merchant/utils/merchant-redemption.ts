import type { ConsumptionOrder, CouponInspection, MerchantId } from "@/types/merchant-api";
import type * as ConsumptionApi from "@/api/merchant-consumption";
type Api = Pick<
	typeof ConsumptionApi,
	| "inspectCoupon"
	| "prepareConsumption"
	| "reviseConsumption"
	| "confirmConsumption"
	| "redeemContent"
	| "cancelConsumption"
	| "getConsumption"
	| "listConsumptions"
>;
export function moneyToCents(value: string): number {
	if (!/^\d{1,8}(\.\d{1,2})?$/.test(value)) throw new Error("请输入最多两位小数的消费金额");
	const [whole, decimal = ""] = value.split(".");
	const amount = Number(whole) * 100 + Number(decimal.padEnd(2, "0"));
	if (!Number.isSafeInteger(amount) || amount <= 0 || amount > 9999999999)
		throw new Error("消费金额须大于0且不超过99,999,999.99元");
	return amount;
}
export function formatMerchantMoney(value: number | null | undefined) {
	if (value == null) return "—";
	if (!Number.isSafeInteger(value) || value < 0) return "金额异常";
	return Math.floor(value / 100) + "." + String(value % 100).padStart(2, "0");
}
export function redemptionRequestId() {
	return (
		"consume_" +
		Date.now().toString(36) +
		"_" +
		Math.random().toString(36).slice(2, 12) +
		Math.random().toString(36).slice(2, 12)
	);
}
export function initialRedemptionState() {
	return {
		visible: false,
		stage: "code",
		busy: false,
		error: "",
		uncertain: false,
		code: "",
		amount: "",
		remark: "",
		received: false,
		inspection: null as CouponInspection | null,
		order: null as ConsumptionOrder | null
	};
}
export type RedemptionState = ReturnType<typeof initialRedemptionState>;
export function createRedemptionFlow(
	api: Api,
	state: RedemptionState,
	session: () => { version: number; storeId: string } | null
) {
	let generation = 0;
	let submission: { fingerprint: string; requestId: string } | null = null;
	function reset() {
		generation++;
		submission = null;
		Object.assign(state, initialRedemptionState());
	}
	function open() {
		reset();
		state.visible = true;
	}
	function requestId(input: object) {
		const fingerprint = JSON.stringify(input);
		if (!submission || submission.fingerprint !== fingerprint) {
			if (state.uncertain) throw new Error("请先查询原操作结果，不能更换内容");
			submission = { fingerprint, requestId: redemptionRequestId() };
		}
		return submission.requestId;
	}
	function acceptOrder(order: ConsumptionOrder, storeId: string, expectedId?: MerchantId) {
		if (
			!order ||
			String(order.storeId) !== storeId ||
			(expectedId != null && String(order.id) !== String(expectedId)) ||
			(state.inspection &&
				String(order.customerCouponId) !== String(state.inspection.customerCouponId)) ||
			!["PENDING", "COMPLETED", "CANCELLED"].includes(order.status) ||
			!Number.isSafeInteger(order.version) ||
			order.version < 1 ||
			!Number.isSafeInteger(order.payableAmount) ||
			order.payableAmount < 0 ||
			(order.status === "COMPLETED" && !order.redemptionId)
		)
			throw Object.assign(new Error("账单响应异常，请在消费账单中核实结果"), {
				uncertain: true
			});
		state.order = order;
		state.uncertain = false;
		state.received = false;
		state.amount =
			order.consumptionAmount == null ? "" : formatMerchantMoney(order.consumptionAmount);
		state.remark = order.remark || "";
		state.stage = order.status === "PENDING" ? "bill" : "result";
	}
	async function run(
		action: (
			context: { version: number; storeId: string },
			current: () => boolean
		) => Promise<void>,
		mutation = false
	) {
		if (state.busy) return;
		const context = session(),
			round = generation;
		if (!context) {
			state.error = "登录身份未验证，请重新登录";
			return;
		}
		const current = () =>
			round === generation &&
			session()?.version === context.version &&
			session()?.storeId === context.storeId;
		state.busy = true;
		state.error = "";
		try {
			await action(context, current);
		} catch (error) {
			if (current()) {
				state.error = error instanceof Error ? error.message : "操作失败，请稍后重试";
				if (mutation && ((error as { uncertain?: boolean })?.uncertain || state.order))
					state.uncertain = true;
			}
		} finally {
			if (current()) state.busy = false;
		}
	}
	async function inspect() {
		if (state.busy) return;
		if (!/^[a-f0-9]{64}$/.test(state.code)) {
			state.error = "请输入完整的64位小写字母数字券码，或扫描顾客券包二维码";
			return;
		}
		await run(async (context, current) => {
			const result = await api.inspectCoupon(state.code);
			if (!current()) return;
			const amount = ["FULL_REDUCTION", "DISCOUNT", "CASH"].includes(result.type);
			const content = ["PACKAGE", "EXPERIENCE", "EXCHANGE"].includes(result.type);
			if (
				String(result.storeId) !== context.storeId ||
				(!amount && !content) ||
				result.requiresConsumptionAmount !== amount ||
				result.nextAction !== (amount ? "ENTER_BILL" : "CONFIRM_CONTENT")
			)
				throw new Error("优惠券类型或适用门店无法确认，请联系商家管理员");
			state.inspection = result;
			state.stage = amount ? "amount" : "content";
		});
	}
	async function prepare() {
		if (state.busy || (state.uncertain && state.order)) return;
		let consumptionAmount: number;
		try {
			consumptionAmount = moneyToCents(state.amount);
		} catch (error) {
			state.error = (error as Error).message;
			return;
		}
		if (state.remark.length > 200) {
			state.error = "消费备注最多200字";
			return;
		}
		await run(async (context, current) => {
			const order = state.order;
			const body = { code: state.code, consumptionAmount, remark: state.remark };
			const result = order
				? await api.reviseConsumption({
						orderId: order.id,
						version: order.version,
						consumptionAmount,
						remark: state.remark
					})
				: await api.prepareConsumption({ ...body, requestId: requestId(body) });
			if (current()) acceptOrder(result, context.storeId, order?.id);
		}, true);
	}
	async function redeem() {
		if (state.busy || !state.inspection || state.inspection.nextAction !== "CONFIRM_CONTENT")
			return;
		await run(async (context, current) => {
			const body = { code: state.code, remark: state.remark };
			const result = await api.redeemContent({ ...body, requestId: requestId(body) });
			if (current()) {
				acceptOrder(result, context.storeId);
				if (result.status !== "COMPLETED") {
					state.uncertain = true;
					throw new Error("尚未确认完成，请查询原操作结果");
				}
			}
		}, true);
	}
	async function confirm() {
		const order = state.order;
		if (!order || order.status !== "PENDING" || state.uncertain || state.busy) return;
		if (order.payableAmount > 0 && !state.received) {
			state.error = "请先确认本次补款已线下收齐";
			return;
		}
		await run(async (context, current) => {
			const result = await api.confirmConsumption({
				orderId: order.id,
				version: order.version,
				receivedAmount: order.payableAmount
			});
			if (current()) {
				acceptOrder(result, context.storeId, order.id);
				if (result.status !== "COMPLETED") {
					state.uncertain = true;
					throw new Error("尚未确认完成，请刷新账单结果");
				}
			}
		}, true);
	}
	async function cancel() {
		const order = state.order;
		if (!order || order.status !== "PENDING" || state.uncertain) return;
		await run(async (context, current) => {
			const result = await api.cancelConsumption(order.id);
			if (current()) acceptOrder(result, context.storeId, order.id);
		}, true);
	}
	async function recover() {
		await run(async (context, current) => {
			const order = state.order;
			if (order) {
				const result = await api.getConsumption(order.id);
				if (current()) acceptOrder(result, context.storeId, order.id);
			} else if (state.inspection) {
				const page = await api.listConsumptions({
					customerCouponId: state.inspection.customerCouponId
				});
				if (!current()) return;
				const result = page.list.find(
					(item) => item.status === "PENDING" || item.status === "COMPLETED"
				);
				if (result) acceptOrder(result, context.storeId);
				else state.error = "尚未查询到待结算或已完成账单，可重试原操作。";
			}
		});
	}
	async function resume(id: MerchantId) {
		open();
		await run(async (context, current) => {
			const result = await api.getConsumption(id);
			if (current()) acceptOrder(result, context.storeId, id);
		});
	}
	function edit() {
		if (!state.busy && !state.uncertain && state.order?.status === "PENDING") {
			state.stage = "amount";
			state.received = false;
		}
	}
	return { open, reset, inspect, prepare, redeem, confirm, cancel, recover, resume, edit };
}
