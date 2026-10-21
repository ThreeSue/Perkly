import { createMockService } from "@coupon-manager/shared";
import { appConfig } from "@/config/business";
export const mockService = createMockService(appConfig.id, appConfig.merchantId);
