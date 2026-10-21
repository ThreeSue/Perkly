# 前端模型与接口接入边界

本文件是前端与后端组员讨论的草案，不是已经提供的后端协议。

公共模型位于 `packages/shared/src/index.d.ts`。当前业务列表使用 `BusinessRow` 展示模型；接入后端后应在各端 `api/` 内将真实 DTO 转换为展示模型。

暂定响应：`{ code: 0, message: "成功", data: ... }`；分页：`{ items: [], total: 0 }`。最终响应码、字段名、分页参数均以组员的真实接口文档为准。

| 模块 | 需要后端提供 | 前端入口 |
| --- | --- | --- |
| 身份 | 各端登录、用户信息、会话失效、角色与门店绑定 | stores/session.ts、api/index.ts |
| 商家/门店 | 入驻状态、资料、门店列表、审核动作 | list('merchants' / 'stores') |
| 优惠券 | 券列表、适用门店、库存、有效期、领取限制 | list('coupons')、claim |
| 我的券 | 用户券、核销码、券生命周期 | list('wallet') |
| 核销 | 券预览、核销确认、幂等规则、记录与操作员 | preview、verify、list('records') |
| 订单/会员/统计 | 数据模型、过滤条件、分页、权限 | 已预留模块路由 |

页面只调用 `api/index.ts`。真实网络基础在 `api/request.ts`，默认不执行。接入顺序：取得接口文档 → 填写 `config/business.ts` 的 baseUrl → 实现真实 API 适配与真实会话 → 检查权限/错误码 → 最后关闭 mockEnabled。单独关闭 mock 不会偷偷访问演示站点，而会明确报“尚未接入真实接口”。

mock 中的商家范围和防重复核销仅用于交互演示，不能作为安全保障。后端必须从登录身份确定商家/门店，执行原子核销并返回稳定的重复请求结果。前端不能自行决定真实支付、券状态和库存。

商家独立端暂按品牌化顾客端布置，演示商家 m001；实际租户绑定与品牌配置待需求确认。五个 mock 实例各自独立；跨端数据不会同步，刷新后恢复种子数据。

## 客户端服务层

客户端不再使用通用 BusinessRow。领域类型和 ClientService 在 `apps/client/types/business.ts`，实现边界为 `apps/client/api/index.ts`；本地实现为 `mocks/client.js`。服务包含 listMerchants/getMerchant、listCoupons/getCoupon、claim、listWallet/getVoucher、listConsumption/getConsumption、login/logout。列表参数含 keyword/category/merchantId/page/size，券包另含 status。金额为整数分，有效期用 ISO 日期时间字符串。claim 返回 voucher 与 alreadyClaimed，供页面处理重复领取。具体参数和返回类型以类型文件为准。

这些是前端内部契约，不代表后端 DTO、响应信封或端点。消费记录是本地历史展示，真实账户、支付、下单及核销端同步尚未实现。取得接口文档后在 API 适配层映射，页面交互保留。
