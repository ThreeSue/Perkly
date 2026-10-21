import type { BusinessEndId } from "@coupon-manager/shared";
export const appConfig = {
	id: "verification" as BusinessEndId,
	mockEnabled: true,
	baseUrl: "",
	merchantId: "m001"
};
// mockEnabled 关闭前，先根据后端文档实现 api/index.ts 的真实接口适配。
// merchant-independent 的 m001 是演示品牌配置，实际业务定义待确认。
