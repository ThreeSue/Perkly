import { request, useStore } from "@/.cool";
import { config } from "@/config";
import type { ApiId, PageResult } from "@/types/discovery";
import type {
	OrderDetail,
	OrderSummary,
	OrderStatus,
	PurchaseInput,
	WalletCoupon,
	WalletDetail,
	WalletState
} from "@/types/trade";

function requiredId(id: ApiId): string {
	const value = String(id);
	if (!/^[1-9]\d*$/.test(value)) throw new Error("业务ID无效，请返回列表重试");
	return value;
}

// 所有业务请求绑定当前会话；退出或切换身份后的响应不得写回私有页面。
async function call<T>(url: string, data?: object): Promise<T> {
	const { user } = useStore();
	if (!user.token) throw new Error("请先登录后操作");
	const version = user.sessionVersion.value;
	const result = await request({
		url,
		method: data ? "POST" : "GET",
		data: data ?? {},
		header: { "Content-Type": "application/json" }
	});
	if (version !== user.sessionVersion.value || !user.token)
		throw new Error("登录身份已变化，请重新加载");
	if (result == null) throw new Error("服务未返回业务结果，请查询后重试");
	return result as T;
}

export function createPurchaseRequestId(): string {
	return `purchase_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 14)}`;
}

function purchase(path: string, input: PurchaseInput): Promise<OrderDetail> {
	requiredId(input.couponTemplateId);
	if (!Number.isInteger(input.quantity) || input.quantity < 1 || input.quantity > 20)
		throw new Error("每次请选择1至20张券");
	if (!/^[A-Za-z0-9_-]{8,64}$/.test(input.requestId))
		throw new Error("本次操作标识无效，请重新进入详情");
	// 显式挑选字段，绝不发送客户端售价、总金额、顾客ID。
	return call(path, {
		couponTemplateId: input.couponTemplateId,
		quantity: input.quantity,
		requestId: input.requestId
	});
}

/** POST /app/order/create：付费券建单；同一操作重试复用 requestId。 */
export const createOrder = (input: PurchaseInput) => purchase("/app/order/create", input);
/** POST /app/order/claim：免费券领取并由后端发券。 */
export const claimCoupon = (input: PurchaseInput) => purchase("/app/order/claim", input);
/** POST /app/order/simulatePay：仅模拟支付，由后端决定状态和发券。 */
export function simulatePay(id: ApiId): Promise<OrderDetail> {
	requiredId(id);
	return call("/app/order/simulatePay", { id, result: "SUCCESS" });
}
/** POST /app/order/cancel：只取消待支付订单。 */
export function cancelOrder(id: ApiId): Promise<OrderDetail> {
	requiredId(id);
	return call("/app/order/cancel", { id });
}
/** GET /app/order/info：本人完整订单。 */
export const getOrder = (id: ApiId) => call<OrderDetail>(`/app/order/info?id=${requiredId(id)}`);
/** POST /app/order/page：本人订单摘要分页。 */
export const listOrders = (page = 1, status?: OrderStatus) =>
	call<PageResult<OrderSummary>>("/app/order/page", {
		page,
		size: 20,
		...(status ? { status } : {})
	});
/** POST /app/coupon/wallet/page：本人券包，不含券码。 */
export const listWallet = (page = 1, state?: WalletState) =>
	call<PageResult<WalletCoupon>>("/app/coupon/wallet/page", {
		page,
		size: 20,
		...(state ? { state } : {})
	});
/** GET /app/coupon/wallet/info：持券快照和后端已生成的PNG二维码。 */
export const getWalletCoupon = (id: ApiId) =>
	call<WalletDetail>(`/app/coupon/wallet/info?id=${requiredId(id)}`);

/** GET /app/coupon/wallet/qrcode：鉴权读取已生成的PNG，仅作详情图片失败时的备用来源。 */
export async function getWalletQrImage(id: ApiId): Promise<string> {
	const encodedId = requiredId(id);
	// 复用JSON请求的刷新Token与本人持券校验，随后用当前凭据读取二进制。
	await getWalletCoupon(id);
	const { user } = useStore();
	const session = user.sessionVersion.value;
	if (!user.token) throw new Error("请先登录后查看二维码");
	return new Promise((resolve, reject) => {
		uni.request({
			url: config.baseUrl + `/app/coupon/wallet/qrcode?id=${encodedId}`,
			method: "GET",
			responseType: "arraybuffer",
			timeout: 15000,
			header: { Authorization: user.token },
			success(response) {
				if (session !== user.sessionVersion.value || !user.token) {
					reject(new Error("登录身份已变化"));
					return;
				}
				const buffer = response.data as ArrayBuffer;
				const bytes = new Uint8Array(buffer);
				const signature = [137, 80, 78, 71, 13, 10, 26, 10];
				if (
					response.statusCode === 200 &&
					bytes.length >= 8 &&
					signature.every((value, index) => bytes[index] === value)
				) {
					resolve("data:image/png;base64," + uni.arrayBufferToBase64(buffer));
					return;
				}
				let message = "二维码读取失败，请刷新持券状态后重试";
				try {
					if (bytes.length < 16384) {
						let encoded = "";
						bytes.forEach((value) => {
							encoded += "%" + value.toString(16).padStart(2, "0");
						});
						const body = JSON.parse(decodeURIComponent(encoded) ?? "") as {
							message?: string;
						};
						if (body.message) message = body.message;
					}
				} catch {
					/* 非JSON错误使用可操作的通用提示。 */
				}
				if (response.statusCode === 401) user.logout();
				reject(new Error(message));
			},
			fail() {
				reject(new Error("二维码读取失败，请检查网络后重试"));
			}
		});
	});
}
