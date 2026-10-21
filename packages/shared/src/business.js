export const appDefinitions = Object.freeze({
  client: {
    label: "客户端",
    description: "发现身边的商家，查看优惠与我的券。",
    private: false,
    modules: ["merchants", "coupons", "wallet", "orders"],
  },
  merchant: {
    label: "商家端",
    description: "管理店铺、优惠券与日常经营。",
    private: true,
    modules: ["stores", "coupons", "orders", "members"],
  },
  platformAdmin: {
    label: "平台管理端",
    description: "处理商家入驻，维护平台内容与运营。",
    private: true,
    modules: ["merchants", "coupons", "users", "reports"],
  },
  merchantIndependent: {
    label: "商家独立端",
    description: "商家专属顾客入口，品牌和数据按商家配置。",
    private: false,
    modules: ["coupons", "wallet", "orders"],
  },
  verification: {
    label: "商家核销端",
    description: "先确认券信息，再执行核销。",
    private: true,
    modules: ["records", "stores"],
  },
});
export const moduleLabels = Object.freeze({
  merchants: "商家",
  coupons: "优惠券",
  wallet: "我的券",
  orders: "订单",
  stores: "门店",
  members: "会员",
  users: "用户",
  reports: "运营数据",
  records: "核销记录",
});
export function formatMoney(fen) {
  return "¥" + (fen / 100).toFixed(2);
}
export function normalizeVoucherCode(code) {
  return code.trim().toUpperCase();
}
export function validateVoucherCode(code) {
  return /^[A-Z0-9]{6,32}$/.test(normalizeVoucherCode(code));
}
export function createMockService(appId, merchantId = "m001") {
  if (!appDefinitions[appId]) throw new Error("未知应用");
  let session = null;
  const merchants = [
    {
      id: "m001",
      title: "青禾咖啡",
      description: "餐饮 · 示例商家",
      status: "已入驻",
    },
    {
      id: "m002",
      title: "晴日书店",
      description: "零售 · 示例商家",
      status: "待审核",
    },
  ];
  const coupons = [
    {
      id: "c001",
      merchantId: "m001",
      title: "咖啡体验券",
      description: "青禾咖啡 · 示例优惠券",
      status: "可领取",
      amountFen: 1500,
    },
    {
      id: "c002",
      merchantId: "m002",
      title: "阅读体验券",
      description: "晴日书店 · 示例优惠券",
      status: "可领取",
      amountFen: 1000,
    },
  ];
  const vouchers = [
    {
      id: "v001",
      merchantId: "m001",
      couponId: "c001",
      title: "咖啡体验券",
      description: "示例券码：QGJ20260001",
      code: "QGJ20260001",
      status: "待使用",
    },
    {
      id: "v002",
      merchantId: "m002",
      couponId: "c002",
      title: "阅读体验券",
      description: "示例券码：QGJ20260002",
      code: "QGJ20260002",
      status: "待使用",
    },
  ];
  const records = [];
  const delay = () => new Promise((resolve) => setTimeout(resolve, 120));
  const requireLogin = () => {
    if (!session) throw new Error("请先进入本地演示登录");
  };
  const customer = () => {
    requireLogin();
    if (!["client", "merchantIndependent"].includes(appId))
      throw new Error("当前应用不能领券");
  };
  const scoped = () =>
    ["merchant", "merchantIndependent", "verification"].includes(appId);
  const requireStaff = () => {
    requireLogin();
    if (appId !== "verification") throw new Error("当前应用不能核销");
  };
  const findVoucher = (code) => {
    requireStaff();
    const item = vouchers.find(
      (v) =>
        v.code === normalizeVoucherCode(code) && v.merchantId === merchantId,
    );
    if (!item) throw new Error("券码不存在或不属于当前商家");
    return item;
  };
  return {
    async login(nickname) {
      await delay();
      if (!nickname.trim()) throw new Error("请输入演示昵称");
      session = { appId, nickname: nickname.trim() };
      return { ...session };
    },
    logout() {
      session = null;
    },
    async list(module, page = 1, size = 10) {
      await delay();
      if (!appDefinitions[appId].modules.includes(module))
        throw new Error("该模块不属于当前应用");
      if (
        appDefinitions[appId].private ||
        ["wallet", "orders", "records"].includes(module)
      )
        requireLogin();
      let rows = [];
      if (module === "merchants") rows = merchants;
      if (module === "coupons")
        rows = coupons.filter((c) => !scoped() || c.merchantId === merchantId);
      if (module === "wallet")
        rows = vouchers.filter(
          (v) => appId === "client" || v.merchantId === merchantId,
        );
      if (module === "records") rows = records;
      if (module === "stores")
        rows = [
          {
            id: "s001",
            title: "青禾咖啡 · 大学路店",
            description: "商家 m001 · 示例门店",
            status: "营业中",
          },
        ];
      const start = (Math.max(1, page) - 1) * Math.min(50, Math.max(1, size));
      return {
        items: rows.slice(start, start + size).map((r) => ({ ...r })),
        total: rows.length,
      };
    },
    async claim(couponId) {
      await delay();
      customer();
      const coupon = coupons.find(
        (c) => c.id === couponId && (!scoped() || c.merchantId === merchantId),
      );
      if (!coupon) throw new Error("优惠券不存在或不属于当前商家");
      const existing = vouchers.find((v) => v.couponId === couponId);
      if (existing) return { ...existing };
      const item = {
        id: "v" + (vouchers.length + 1),
        merchantId: coupon.merchantId,
        couponId,
        title: coupon.title,
        description: "本地演示领取",
        status: "待使用",
        code: "QGJ2026" + String(vouchers.length + 1).padStart(4, "0"),
      };
      vouchers.push(item);
      return { ...item };
    },
    async preview(code) {
      await delay();
      if (!validateVoucherCode(code))
        throw new Error("请输入 6～32 位字母或数字券码");
      return { ...findVoucher(code) };
    },
    async verify(code) {
      await delay();
      const item = findVoucher(code);
      if (item.status !== "待使用")
        throw new Error("该券已经核销，请查看核销记录");
      item.status = "已核销";
      records.unshift({
        id: "r" + (records.length + 1),
        title: item.title,
        description: "核销码 " + item.code + " · " + session.nickname,
        code: item.code,
        status: "核销成功",
      });
      return { ...item };
    },
  };
}
