export declare const businessEndLabels: Readonly<{
  client: "客户端";
  merchant: "商家端";
  platformAdmin: "平台管理端";
  merchantIndependent: "商家独立端";
  verification: "商家核销端";
}>;

export type BusinessEndId = keyof typeof businessEndLabels;
export declare const businessEndIds: readonly BusinessEndId[];

export type ModuleId =
  | "merchants"
  | "coupons"
  | "wallet"
  | "orders"
  | "stores"
  | "members"
  | "users"
  | "reports"
  | "records";
export interface BusinessRow {
  id: string;
  title: string;
  description: string;
  status: string;
  code?: string;
  couponId?: string;
  merchantId?: string;
  amountFen?: number;
}
export interface DemoSession {
  appId: BusinessEndId;
  nickname: string;
}
export interface PageResult {
  items: BusinessRow[];
  total: number;
}
export interface AppDefinition {
  label: string;
  description: string;
  private: boolean;
  modules: ModuleId[];
}
export declare const appDefinitions: Readonly<
  Record<BusinessEndId, AppDefinition>
>;
export declare const moduleLabels: Readonly<Record<ModuleId, string>>;
export declare function formatMoney(fen: number): string;
export declare function normalizeVoucherCode(code: string): string;
export declare function validateVoucherCode(code: string): boolean;
export interface MockService {
  login(nickname: string): Promise<DemoSession>;
  logout(): void;
  list(module: ModuleId, page?: number, size?: number): Promise<PageResult>;
  claim(couponId: string): Promise<BusinessRow>;
  preview(code: string): Promise<BusinessRow>;
  verify(code: string): Promise<BusinessRow>;
}
export declare function createMockService(
  appId: BusinessEndId,
  merchantId?: string,
): MockService;
