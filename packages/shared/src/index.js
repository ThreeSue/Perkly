export const businessEndLabels = Object.freeze({
  client: "客户端",
  merchant: "商家端",
  platformAdmin: "平台管理端",
  merchantIndependent: "商家独立端",
  verification: "商家核销端",
});

export const businessEndIds = Object.freeze(Object.keys(businessEndLabels));

export * from "./business.js";
