// 商家端接入边界：不复用顾客 Token、不调用 mock、不根据表名猜测 URL / DTO。
// 后端交接：docs/MERCHANT-API-HANDOFF.md；补齐契约后在此适配 Cool Unix request。
export const merchantCapabilities = {
	identity: { label: "员工身份验证", reason: "员工登录、商家归属、角色与权限接口待接入。" },
	stores: { label: "授权门店", reason: "授权门店列表与当前门店接口待接入。" },
	dashboard: { label: "今日核销数量", reason: "今日核销统计接口待接入，暂不显示统计数值。" },
	preview: {
		label: "券码校验",
		reason: "券码查询及核销资格校验接口待接入，暂不能进入核销确认。"
	},
	redeem: { label: "确认核销", reason: "核销提交与结果查询接口待接入，暂不能核销。" },
	records: { label: "核销记录", reason: "核销记录与时间筛选待接入，登录及门店权限尚待验证。" },
	managerRecords: { label: "全店核销记录", reason: "店长授权与当前门店全店核销记录接口待接入。" },
	coupons: { label: "门店可用优惠券", reason: "门店可用券列表、详情及规则接口待接入。" },
	create: { label: "快速建券", reason: "券类型字典、建券、适用门店及提交审核接口待接入。" },
	audits: { label: "审核进度", reason: "审核状态与驳回原因查询接口待接入。" },
	help: { label: "帮助与常见问题", reason: "帮助内容接口待接入；下方仅提供操作说明。" },
	feedback: { label: "问题反馈", reason: "员工问题反馈提交接口待接入，暂不能提交。" },
	logout: { label: "退出登录", reason: "员工登录会话与退出接口待接入。" }
} as const;
export type MerchantCapability = keyof typeof merchantCapabilities;
// 接口缺失时必须拒绝，不能返回空列表/0/成功来冒充后端结果。
export async function requestMerchantCapability(capability: MerchantCapability): Promise<never> {
	throw new Error(merchantCapabilities[capability].reason);
}
