import { isH5, request } from "@/.cool";
import { config, isDev } from "@/config";
import type {
	ApiId,
	Coupon,
	CouponDetail,
	CouponQuery,
	IndustryCategory,
	PageResult,
	Store,
	StoreDetail,
	StoreQuery
} from "@/types/discovery";

const DEFAULT_PAGE = 1;
const DEFAULT_SIZE = 20;

function requiredId(id: ApiId, label: string): string {
	const value = String(id).trim();
	if (!/^[1-9]\d*$/.test(value)) throw new Error(`${label}必须为正整数`);
	return encodeURIComponent(value);
}

function normalizeImage(path: string | null): string | null {
	if (!path) return null;
	if (/^(https?:|data:|wxfile:|blob:|\/\/)/i.test(path)) return path;
	const relativePath = path.startsWith("/") ? path : `/${path}`;
	if (isDev && isH5()) return `${config.baseUrl}${relativePath}`;
	const host = (config.host ?? "").replace(/\/$/, "");
	return host ? `${host}${relativePath}` : relativePath;
}

function normalizeStore(store: Store): Store {
	const merchantLogo = normalizeImage(store.merchantLogo);
	return {
		...store,
		merchantLogo,
		cover: normalizeImage(store.cover) ?? merchantLogo
	};
}

function normalizeCoupon(coupon: Coupon): Coupon {
	return {
		...coupon,
		merchantLogo: normalizeImage(coupon.merchantLogo),
		cover: normalizeImage(coupon.cover)
	};
}

export async function listIndustryCategories(): Promise<IndustryCategory[]> {
	const result = (await request({
		url: "/app/merchant/category/list",
		method: "GET"
	})) as IndustryCategory[] | null;
	return result ?? [];
}

export async function listStores(query: StoreQuery = {}): Promise<PageResult<Store>> {
	const result = (await request({
		url: "/app/merchant/store/page",
		method: "POST",
		header: { "Content-Type": "application/json" },
		data: {
			page: query.page ?? DEFAULT_PAGE,
			size: query.size ?? DEFAULT_SIZE,
			...(query.keyword?.trim() ? { keyword: query.keyword.trim() } : {}),
			...(query.categoryId != null ? { categoryId: query.categoryId } : {}),
			...(query.merchantId != null ? { merchantId: query.merchantId } : {})
		}
	})) as PageResult<Store>;
	return { ...result, list: (result?.list ?? []).map(normalizeStore) };
}

export async function getStore(id: ApiId): Promise<StoreDetail> {
	const result = (await request({
		url: `/app/merchant/store/info?id=${requiredId(id, "门店ID")}`,
		method: "GET"
	})) as StoreDetail;
	return {
		...normalizeStore(result),
		merchantIntroduction: result.merchantIntroduction
	};
}

/** 后端接口：POST /app/coupon/page。公开优惠券分页查询。 */
export async function listCoupons(query: CouponQuery = {}): Promise<PageResult<Coupon>> {
	const result = (await request({
		url: "/app/coupon/page",
		method: "POST",
		header: { "Content-Type": "application/json" },
		data: {
			page: query.page ?? DEFAULT_PAGE,
			size: query.size ?? DEFAULT_SIZE,
			...(query.keyword?.trim() ? { keyword: query.keyword.trim() } : {}),
			...(query.categoryId != null ? { categoryId: query.categoryId } : {}),
			...(query.merchantId != null ? { merchantId: query.merchantId } : {}),
			...(query.storeId != null ? { storeId: query.storeId } : {}),
			...(query.type ? { type: query.type } : {})
		}
	})) as PageResult<Coupon>;
	return { ...result, list: (result?.list ?? []).map(normalizeCoupon) };
}

/** 后端接口：GET /app/coupon/info?id={券模板ID}。公开优惠券详情查询。 */
export async function getCoupon(id: ApiId): Promise<CouponDetail> {
	const result = (await request({
		url: `/app/coupon/info?id=${requiredId(id, "优惠券模板ID")}`,
		method: "GET"
	})) as CouponDetail;
	return {
		...normalizeCoupon(result),
		stores: (result.stores ?? []).map(normalizeStore),
		contents: result.contents ?? []
	};
}

export function formatMoney(cents: number | null): string {
	return cents == null ? "—" : (cents / 100).toFixed(2);
}

export function getApiErrorMessage(error: unknown, fallback: string): string {
	if (error instanceof Error && error.message) return error.message;
	if (error != null && typeof error === "object" && "message" in error) {
		const message = (error as { message?: unknown }).message;
		if (typeof message === "string" && message.trim()) return message;
	}
	return fallback;
}
