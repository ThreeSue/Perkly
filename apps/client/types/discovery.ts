export type ApiId = number | string;

export type IndustryCategory = {
	id: ApiId;
	parentId: ApiId | null;
	name: string;
	icon: string | null;
	sort: number;
};

export type Pagination = {
	page: number;
	size: number;
	total: number;
};

export type PageResult<T> = {
	list: T[];
	pagination: Pagination;
};

export type StoreQuery = {
	page?: number;
	size?: number;
	keyword?: string;
	categoryId?: ApiId;
	merchantId?: ApiId;
};

export type Store = {
	id: ApiId;
	name: string;
	merchantId: ApiId;
	merchantName: string;
	merchantLogo: string | null;
	cover: string | null;
	categoryId: ApiId;
	categoryName: string;
	address: string;
	phone: string | null;
	businessHours: string | null;
	longitude: number | null;
	latitude: number | null;
	status: number;
	statusText: string;
};

export type StoreDetail = Store & {
	merchantIntroduction: string | null;
};

export type CouponType =
	| "FULL_REDUCTION"
	| "DISCOUNT"
	| "CASH"
	| "PACKAGE"
	| "EXPERIENCE"
	| "EXCHANGE";

export type CouponSaleState = "ENDED" | "SOLD_OUT" | "NOT_STARTED" | "AVAILABLE";

export type CouponQuery = {
	page?: number;
	size?: number;
	keyword?: string;
	categoryId?: ApiId;
	merchantId?: ApiId;
	storeId?: ApiId;
	type?: CouponType;
};

export type Coupon = {
	id: ApiId;
	publicationId: ApiId;
	merchantId: ApiId;
	merchantName: string;
	merchantLogo: string | null;
	categoryId: ApiId;
	categoryName: string;
	name: string;
	type: CouponType;
	typeText: string;
	cover: string | null;
	description: string;
	salePrice: number;
	minSpend: number | null;
	discountAmount: number | null;
	deductAmount: number | null;
	maxDiscount: number | null;
	discountRate: number | null;
	scopeType: number;
	issueLimit: number | null;
	issuedCount: number;
	remainingStock: number | null;
	perCustomerLimit: number | null;
	allowCombine: 0 | 1;
	saleStartTime: string | null;
	saleEndTime: string | null;
	validityType: string;
	validFrom: string | null;
	validTo: string | null;
	validDays: number | null;
	saleState: CouponSaleState;
	saleStateText: string;
};

export type CouponContent = {
	productId: ApiId;
	relationType: 1 | 2;
	quantity: number;
	description: string;
};

export type CouponDetail = Coupon & {
	stores: Store[];
	contents: CouponContent[];
};
