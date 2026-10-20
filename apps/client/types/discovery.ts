export type CategoryId = "takeaway" | "group-buy" | "shows" | "stays";
export type Merchant = {
	id: string;
	name: string;
	image: string;
	category: CategoryId;
	description: string;
	offer: string;
	price: string;
	unit: string;
	address: string;
	hours: string;
	tags: string[];
};
export type MerchantQuery = { keyword?: string; category?: string };
