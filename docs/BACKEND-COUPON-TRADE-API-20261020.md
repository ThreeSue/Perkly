# 用户端购券、券包与商家核销接口

更新：2026-10-20。本次仅新增后端；前端暂缓。继续复用已有商家、门店、优惠券模板与上架数据，不引入购物车。

## 1. 公共约定

- 请求域名和端口沿用现有后端，下面路径均相对于服务根地址。
- `/app/**` 使用用户登录返回的 `Authorization` token；`/merchant/**` 使用商家登录返回的 token。身份从登录态取得，客户端不用提交 `customerId`、`merchantId`、`operatorId`。
- JSON 接口成功结构为 `{"code":1000,"message":"success","data":...}`；失败判断 `code != 1000` 并显示 `message`。未登录、账号失效或角色错误会被鉴权拦截。
- **本文新增接口的金额统一为整数分**，例如 990 表示 9.90 元；`discountRate:8.8` 仍表示 8.8 折。数据库金额继续以元保存，已有运营/商家接口的金额约定不变。
- 时间为北京时间，格式 `yyyy-MM-dd HH:mm:ss`。ID 是内部关联值，页面展示对应的商家名称、门店名称和中文状态。
- 分页请求默认 `page:1,size:20`，`size` 上限 100；响应 `data.list` 为记录数组，`data.pagination` 包含 `page,size,total`，其中 `total` 是记录总数。

## 2. 接口清单

| 方法 | 路径 | 登录身份 | 功能 |
|---|---|---|---|
| POST | `/app/order/create` | 用户 | 创建购券订单 |
| POST | `/app/order/claim` | 用户 | 免费领取并立即发券 |
| POST | `/app/order/simulatePay` | 用户 | 模拟支付并发券 |
| POST | `/app/order/cancel` | 用户 | 取消待支付订单 |
| POST | `/app/order/page` | 用户 | 我的订单分页 |
| GET | `/app/order/info?id=订单ID` | 用户 | 我的订单详情 |
| POST | `/app/coupon/wallet/page` | 用户 | 我的券包分页 |
| GET | `/app/coupon/wallet/info?id=持券ID` | 用户 | 持券详情、券码和二维码 |
| GET | `/app/coupon/wallet/qrcode?id=持券ID` | 用户 | PNG 二维码图片 |
| POST | `/merchant/redemption/preview` | 商家 | 扫码/手输券码后预检 |
| POST | `/merchant/redemption/confirm` | 商家 | 确认核销 |
| POST | `/merchant/redemption/page` | 商家 | 本商家核销流水 |

## 3. 创建订单与免费领取

`POST /app/order/create`，JSON 示例：

```json
{"couponTemplateId":101,"quantity":2,"requestId":"purchase_20261020_000001"}
```

| 参数 | 必填 | 说明 |
|---|---|---|
| couponTemplateId | 是 | 展示接口返回的优惠券模板 ID |
| quantity | 否 | 默认 1，每单 1～20 张；一单购买一种模板 |
| requestId | 是 | 本次操作唯一标识，8～64 位字母、数字、下划线或短横线 |

同一用户以同一 `requestId` 重试相同内容，返回原订单；换模板或数量须换 `requestId`。订单 15 分钟内有效。下单时检查库存，**不预占库存**；支付时再次检查库存和每人累计限额。

响应 `data` 示例（订单详情结构）：

```json
{
  "id":201,
  "orderNo":"Q示例订单号",
  "channel":"PLATFORM",
  "merchantId":10,
  "merchantName":"演示商家",
  "orderStatus":"PENDING",
  "orderStatusText":"待支付",
  "paymentStatus":"UNPAID",
  "totalAmount":1980,
  "createdAt":"2026-10-20 21:00:00",
  "paidAt":null,
  "expiresAt":"2026-10-20 21:15:00",
  "items":[{
    "id":301,"couponTemplateId":101,"couponName":"20元代金券",
    "unitPrice":990,"quantity":2,"subtotal":1980,"cover":null,"ruleVersion":1
  }],
  "couponIds":[]
}
```

`POST /app/order/claim` 使用相同请求字段，仅允许售价为 0 的优惠券。成功时已完成订单并发券，`orderStatus:PAID`、`paymentStatus:SUCCESS`、`totalAmount:0`，`couponIds` 返回生成的持券 ID。重试沿用原 `requestId`，不会重复领券。付费券调用领取接口会失败，整个事务回滚。

## 4. 模拟支付、取消与订单查询

`POST /app/order/simulatePay`：

```json
{"id":201,"result":"SUCCESS"}
```

`result` 默认 `SUCCESS`，也可传 `FAILED`。这是演示用模拟支付，**尚未接入微信支付、支付宝或真实资金结算**。

- 成功：同一事务内生成对应数量的持券、唯一券码和真实 PNG 二维码，增加已发行数量，再将订单改为 `PAID/SUCCESS`。任一步骤失败，整笔回滚。
- 支付失败：订单改为 `FAILED/FAILED`，不发券；需重新下单。
- 已支付订单再次提交 `SUCCESS` 返回原结果，不重复发券。
- 超时、取消、下架、售罄、累计超限、模板规则版本发生变化等情况不能支付。
- 不接受客户端提交售价、总金额或优惠金额，金额由后端模板计算。

返回订单详情，`couponIds` 如 `[401,402]`；这些是持券 ID，用于券包详情和二维码接口，不是模板 ID。

`POST /app/order/cancel` 请求 `{"id":201}`：仅待支付订单可取消，重复取消返回原结果；不会删除订单。

`POST /app/order/page` 请求 `{"page":1,"size":20,"status":"PAID"}`。`status` 可省略，枚举：

| 状态 | 中文 | 说明 |
|---|---|---|
| PENDING | 待支付 | 尚未超时 |
| PAID | 已完成 | 支付/免费领取成功，已发券 |
| CANCELLED | 已取消 | 用户取消 |
| FAILED | 支付失败 | 模拟支付失败 |
| EXPIRED | 已超时 | 待支付且已过失效时间，查询时计算 |

分页列表是订单摘要，不含 `items`、`couponIds`。`GET /app/order/info?id=201` 返回上面的完整详情。只能访问当前用户自己的订单。

## 5. 我的券包和二维码

`POST /app/coupon/wallet/page`：

```json
{"page":1,"size":20,"state":"UNUSED"}
```

`state` 可省略；可选 `UNUSED` 待使用、`USED` 已使用、`EXPIRED` 已过期、`NOT_STARTED` 未生效、`VOID` 已作废。有效期动态计算，不依赖定时任务改状态。

列表记录字段：`id`（持券 ID）、`couponTemplateId`、`merchantId`、`merchantName`、`name`、`type`、`cover`、`state`、`stateText`、`validFrom`、`validTo`、`issuedAt`、`usedAt`、`sourceType`。新发券来源为 `PURCHASE` 或 `CLAIM`；列表不返回券码和二维码。

`GET /app/coupon/wallet/info?id=401` 在列表字段基础上返回：

```json
{
  "code":"64位小写十六进制随机券码",
  "qrCodeContent":"与code完全相同的券码",
  "qrCodeImage":"data:image/png;base64,实际PNG图片数据",
  "rule":{
    "name":"20元代金券","type":"CASH","description":"使用说明",
    "scopeType":1,"storeIds":[20],"storeNames":["演示门店"],
    "contentItems":[],"ruleVersion":1,"allowCombine":0,
    "salePrice":990,"minSpend":null,"discountAmount":null,
    "deductAmount":2000,"maxDiscount":null,"discountRate":null
  }
}
```

上述是详情增加字段示例，实际 `data` 同时包含所有列表字段。规则来自发券时保存的快照，之后模板修改或下架不会重写已领取券的权益。`AFTER_ISSUE` 自发券时起按北京时间自然日加有效天数；`FIXED` 使用模板固定日期区间。

二维码为 320×320 黑白 PNG，包含券码文本，不包含手机号等用户资料；**后端发券时已经绘制并保存，不需要前端重新生成二维码**。核销以数据库持券状态为准，截图或重复扫描不能重复核销。

`GET /app/coupon/wallet/qrcode?id=401` 返回 `Content-Type:image/png` 二进制图片，设置 `Cache-Control:no-store`，不套 JSON 成功包装。错误仍返回框架 JSON。请求必须携带用户 token；前端未来可直接使用详情里的 `qrCodeImage`，或带鉴权请求此图片接口。

只能查询本人持券。二维码可查看不代表可核销，过期、作废、未生效、已使用券都会在核销时拒绝。

## 6. 商家扫码预检与确认核销

商家须先通过已有商家登录接口取得商家 token。目前沿用已开通、已启用的 `OWNER` 商家账号；用户或运营 token 不能代替商家身份。

`POST /merchant/redemption/preview` 和 `POST /merchant/redemption/confirm` 使用相同字段：

```json
{"storeId":20,"code":"扫描二维码得到的完整64位券码","consumptionAmount":5000}
```

| 参数 | 必填 | 说明 |
|---|---|---|
| storeId | 是 | 当前商家营业中的门店，且满足持券快照的适用范围 |
| code | 是 | 扫码结果或手工输入的完整券码 |
| consumptionAmount | 按类型 | 实际消费整数分；满减、折扣、代金券须大于 0 |

预检成功 `data`：

```json
{
  "customerCouponId":401,"couponName":"20元代金券",
  "merchantName":"演示商家","storeId":20,"storeName":"演示门店",
  "consumptionAmount":5000,"discountAmount":2000,"contents":[],"confirmed":false
}
```

预检只检查，不扣券。确认会再次检查全部条件；成功响应同上，`confirmed:true`，并增加 `redemptionId`、`redeemedAt`。每张券仅可确认成功一次，重复确认返回业务失败，不新增流水。预检成功不保留核销资格，之后券过期、门店停用或被其他请求核销，确认仍会失败。

优惠计算规则：

| 券类型 | 核销计算 |
|---|---|
| FULL_REDUCTION | 满足最低消费后减快照中的满减金额 |
| DISCOUNT | 实际消费 × 折扣，按分四舍五入后计算优惠，应用最高优惠上限 |
| CASH | 按代金金额抵扣；仍需满足模板设置的最低消费 |
| PACKAGE / EXPERIENCE / EXCHANGE | 按快照 `contents` 兑现商品或服务，允许不填消费金额，`discountAmount` 为 0 |

货币优惠不超过实际消费。套餐/体验/兑换券返回 0 仅表示未折算货币优惠，不表示券没有权益。每张券整张核销一次，暂不提供分次核销或与外部收银系统的自动结算。`allowCombine` 作为快照规则展示，当前没有其他优惠或购物车金额参与叠加计算。

商家归属、门店营业状态、适用门店、顾客状态、有效期、持券状态均由后端校验；请求不能指定优惠金额、核销人或商家归属。

## 7. 核销流水

`POST /merchant/redemption/page`：

```json
{"page":1,"size":20,"storeId":20}
```

`storeId` 可省略查看本商家全部流水。记录字段：`id`、`customerCouponId`、`couponName`、`storeId`、`storeName`、`operatorId`、`consumptionAmount`、`discountAmount`、`redeemedAt`。不返回顾客手机号或原始券码；不能查询其他商家流水。

## 8. 联调顺序和数据库增量

1. 沿用已有展示接口取得已审核、上架且可购买的模板 ID。
2. 用户登录；付费券创建订单再模拟支付，免费券调用 `claim`。
3. 查询订单 `couponIds`，进入券包详情展示后端返回的二维码。
4. 商家登录，扫码后预检，检查权益和门店，再确认核销。
5. 用户券包变为已使用，商家流水出现一条核销记录。

数据库沿用 `purchase_order`、`purchase_order_item`、`customer_coupon`、`coupon_redemption`，增量脚本：`testing/data/coupon-purchase-v1.sql`，可重复执行。增加订单幂等请求标识/失效时间、逐张发券序号/二维码图片及相关索引；不重建既有表。

本地测试库已执行增量，执行前四张业务表备份位于 `testing/data/backups/coupon-trade-before-20261020.sql`。其他环境须先备份并执行同一增量，再重启后端加载新增接口。新增依赖为 ZXing Core 3.5.0。

库存和重复操作保护使用事务、业务行锁、发行数量条件更新，以及数据库唯一约束（用户请求标识、订单明细逐张序号、券码、单券核销流水）。每人限额按历史累计持券张数计算，包括已使用、已过期和已作废券；取消或失败的订单不占额度。
