// 这四项是首页固定的功能入口，不是从后端返回的商家分类数据。
import type { Merchant, MerchantQuery } from "@/types/discovery";

export const categories = [
	{ id: "takeaway", name: "外卖" },
	{ id: "group-buy", name: "团购" },
	{ id: "shows", name: "电影演出" },
	{ id: "stays", name: "民宿" }
];

export const DISCOVERY_API_NOT_READY = "客户端尚未接通后端商家查询，暂时无法显示商家。";

export async function listMerchants(query: MerchantQuery = {}): Promise<Merchant[]> {
	void query;
	throw new Error(DISCOVERY_API_NOT_READY);
}
export async function getMerchant(id: string): Promise<Merchant> {
	void id;
	throw new Error(DISCOVERY_API_NOT_READY);
}
