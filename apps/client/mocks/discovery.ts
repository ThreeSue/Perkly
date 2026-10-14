import type { Merchant } from "@/types/discovery";
// 文档尚未提供顾客端商家接口；这些商家与优惠仅用于本地展示。
export const categories = [
	{ id: "takeaway", name: "外卖" },
	{ id: "group-buy", name: "团购" },
	{ id: "shows", name: "电影演出" },
	{ id: "stays", name: "民宿" }
];
export const merchants: Merchant[] = [
	{
		id: "coffee",
		image: "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=640&auto=format&fit=crop&q=80",
		name: "青禾咖啡",
		category: "takeaway",
		description: "咖啡 · 轻食",
		offer: "双杯拿铁套餐",
		price: "19.9",
		unit: "/ 套",
		address: "大学路18号（示例地址）",
		hours: "09:00–21:00",
		tags: ["现磨咖啡", "双人分享"]
	},
	{
		id: "noodles",
		image: "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=640&auto=format&fit=crop&q=80",
		name: "巷里小面",
		category: "group-buy",
		description: "面食 · 家常味道",
		offer: "招牌小面单人餐",
		price: "16.8",
		unit: "/ 份",
		address: "文苑路26号（示例地址）",
		hours: "10:00–22:00",
		tags: ["到店团购", "招牌小面"]
	},
	{
		id: "cinema",
		image: "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=640&auto=format&fit=crop&q=80",
		name: "星河影城",
		category: "shows",
		description: "电影 · 休闲娱乐",
		offer: "双人观影套餐",
		price: "59.9",
		unit: "/ 套",
		address: "文化广场3楼（示例地址）",
		hours: "10:00–23:00",
		tags: ["周末放松", "双人套餐"]
	},
	{
		id: "stay",
		image: "https://images.unsplash.com/photo-1566073771259-6a8506099945?w=640&auto=format&fit=crop&q=80",
		name: "山间小住",
		category: "stays",
		description: "民宿 · 周末度假",
		offer: "庭院大床房",
		price: "199",
		unit: "/ 晚起",
		address: "青山路8号（示例地址）",
		hours: "入住14:00后 · 退房12:00前",
		tags: ["庭院民宿", "安静舒适"]
	},
	{
		id: "bakery",
		image: "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=640&auto=format&fit=crop&q=80",
		name: "麦香烘焙",
		category: "group-buy",
		description: "烘焙 · 下午茶",
		offer: "面包咖啡下午茶",
		price: "22.9",
		unit: "/ 套",
		address: "文苑路9号（示例地址）",
		hours: "08:00–20:00",
		tags: ["下午茶", "到店团购"]
	},
	{
		id: "lunch",
		image: "https://images.unsplash.com/photo-1547592180-85f173990554?w=640&auto=format&fit=crop&q=80",
		name: "暖暖食堂",
		category: "takeaway",
		description: "简餐 · 日常午饭",
		offer: "精选午餐套餐",
		price: "18.8",
		unit: "/ 份",
		address: "大学路32号（示例地址）",
		hours: "10:30–20:30",
		tags: ["日常简餐", "午餐优选"]
	}
];
