import type { BusinessEndId } from "@coupon-manager/shared";
export const appConfig = {
	id: "merchant" as BusinessEndId,
	mockEnabled: false,
	baseUrl: "",
	merchantId: ""
};
// 商家及门店归属必须由员工身份接口返回，禁止硬编码演示商家。
