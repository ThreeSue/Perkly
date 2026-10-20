// 这四项是首页固定的功能入口，不是从后端返回的商品分类数据。
import type { Merchant, MerchantQuery } from "@/types/discovery";

export const categories = [
	{ id: "takeaway", name: "外卖" },
	{ id: "group-buy", name: "团购" },
	{ id: "shows", name: "电影演出" },
	{ id: "stays", name: "民宿" }
];

export const DISCOVERY_API_NOT_READY = "顾客端优惠项目接口尚未提供，当前不展示本地样例数据。";

export async function listMerchants(query: MerchantQuery = {}): Promise<Merchant[]> {
	void query;
	throw new Error(DISCOVERY_API_NOT_READY);
}
export async function getMerchant(id: string): Promise<Merchant> {
	void id;
	throw new Error(DISCOVERY_API_NOT_READY);
}
