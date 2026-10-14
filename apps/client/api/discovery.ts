import { categories, merchants } from "@/mocks/discovery";
import type { Merchant, MerchantQuery } from "@/types/discovery";
export { categories };
// 未来仅在此替换真实适配，不调用管理员接口。

export async function listMerchants(query: MerchantQuery = {}): Promise<Merchant[]> {
	const keyword = (query.keyword ?? "").trim().toLowerCase();
	const result = merchants.filter(
		(item) =>
			(!query.category || item.category === query.category) &&
			(!keyword ||
				[item.name, item.description, item.offer, ...item.tags]
					.join(" ")
					.toLowerCase()
					.includes(keyword))
	);
	return query.limit ? result.slice(0, query.limit) : result;
}
export async function getMerchant(id: string): Promise<Merchant> {
	const item = merchants.find((item) => item.id === id);
	if (!item) throw new Error("商品不存在");
	return item;
}
