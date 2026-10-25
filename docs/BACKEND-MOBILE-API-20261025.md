# 券管家用户端与商家核销端接口汇总

最后更新日期：**2026-10-25**。

范围：用户移动端（含游客可访问功能）与商家手机核销端。按业务模块组织，本文可单独交给前端，不需要拼接历史变更文件。

依据：现有模块契约及本机 `cool-admin-java-test` Controller、DTO、关键服务代码；核对时后端HEAD为 `127a738`，同时包含当前工作区代码，不代表运行实例一定已经部署相同版本。本次仅整理文档，没有调用有写入副作用的接口，也没有重新验收短信、微信或真机。

本文中主业务模块均为“后端已有实现，前端接入与部署以实际环境验收为准”。不将“有接口”等同于“前端已经完成”。未指定字段类型的ID为整数标识，序列化也可能返回字符串；前端原样传回。

### 更新记录

| 日期 | 内容 |
|---|---|
| 2026-10-25 | 汇总两个移动端；合并登录、资料、浏览、地图、领购、券包、入驻申请、门店身份、验券、消费账单、核销流水；补充框架既有接口清单 |
| 2026-10-24 | 消费账单、线下收款确认、内容券兑换；旧核销confirm请求不再适用；新增附近门店与腾讯地图；游客入驻改为审核后发营业码 |
| 2026-10-23 | 增加inspect识别券类型与规则 |
| 2026-10-22 | 店长/员工单门店身份及记录权限 |
| 2026-10-20及以前 | 登录资料、忘记密码、公开浏览、领购与券包 |

### 业务模块目录

1. 通用约定
2. 用户认证与登录
3. 用户资料与账号安全
4. 行业分类与门店浏览
5. 优惠券浏览
6. 购券订单与免费领取
7. 券包、持券详情与二维码
8. 地图定位与附近门店
9. 游客商家入驻申请
10. 商家核销端认证与门店身份
11. 验券识别与优惠试算
12. 消费账单与确认核销
13. 核销记录
14. 旧接口兼容、能力缺口及联调顺序
15. 用户端框架既有能力附录

## 1. 通用约定

- 服务根地址：开发后端默认 `http://127.0.0.1:8001`；手机必须换为设备可访问的电脑IP或服务器域名。IP随网络变化，不把历史IP当作固定地址。
- POST默认JSON；GET参数放Query。除文件上传外 `Content-Type: application/json`。
- 普通用户使用APP Token；商家核销使用MERCHANT Token。请求头为 `Authorization: 原始Token`，**不加Bearer**，不能跨端复用令牌。
- 游客登录、验证码、入驻凭证接口不发送旧Authorization；公开浏览不要求登录。地图代理要求APP登录。
- JSON成功：`{"code":1000,"message":"success","data":...}`。业务失败读message；同时处理HTTP401/403，不能仅凭HTTP200判成功。data可能为null。
- HTTP401或业务401：可按对应端协议尝试刷新一次，失败则退出；403不循环刷新。
- 本文优惠券、交易、核销及消费账单金额均为**整数分**；990表示9.90元。折扣 `discountRate=8.8` 表示8.8折，不除以100。
- 业务时区Asia/Shanghai。交易时间通常为 `yyyy-MM-dd HH:mm:ss`；消费账单Date字段依部署的统一序列化配置，解析时不要假设本机时区。
- 业务分页一般为请求page/size，响应 `{"list":[],"pagination":{"page":1,"size":20,"total":0}}`；框架附录的CRUD可能使用另一种分页结构，不混用。
- `couponTemplateId`是券模板；`customerCouponId`/券包id是具体持券；`publicationId`是发布记录；`code`是券码。购券订单和消费账单是两类业务，id/orderNo不能混用。
- merchantId、operatorId、customerId、订单号、优惠金额、状态以服务端可信身份和计算结果为准，不让客户端任意指定。
- 页面展示名称和中文状态；ID仅用于关联。私有详情与历史结果在退出/切换账号后清除，忽略迟到的旧会话响应。

### 当前核销业务口径

购券使用模拟支付；到店消费补款使用**商家人工确认线下已收款**。这不是微信扣款接口。内容类券直接兑换，无本次补款。尚未提供普通顾客“不用券直接在线支付消费单”的接口，不据此前讨论自行增加。

---

## 2. 用户认证与登录

文档核对日期：2026-10-25；协议来源更新日期：2026-10-20。

### 图片验证码

```http
GET /app/user/login/captcha?type=base64&width=150&height=50
```

`type`、`width`、`height` 为当前用户端请求必填参数，分别传 `base64`、150、50。尺寸也可以按 UI 调整。

响应：

```json
{
  "code":1000,
  "message":"success",
  "data":{"captchaId":"图片验证码ID","data":"data:image/png;base64,图片内容"}
}
```

图片使用 `data.data` 显示，保留同次返回的 `captchaId`。刷新图片时清空旧图片验证码输入。用户图片验证码与管理员图片验证码分别从各自端点获取。

### 发送短信验证码

```http
POST /app/user/login/smsCode
```

```json
{"phone":"13800000000","captchaId":"图片验证码ID","code":"1234"}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| phone | string | 是 | 中国大陆11位手机号 |
| captchaId | string | 是 | 本次图片验证码的 ID |
| code | string | 是 | 图片验证码内容；不是短信验证码 |

成功响应为通用成功格式，无短信验证码返回。图片验证码通过并发送成功后，本次图片验证码会被消费；后续填写短信码即可，重新发送时获取新的图片验证码。

当前短信规则：6位数字，5分钟有效，同手机号60秒发送间隔；成功验证后不能重复使用，连续错误5次使本地验证记录失效。验证码由阿里云短信认证服务生成并核验，短信签名当前为“恒创联众”。发送接口成功表示请求已受理，实际送达以手机收信及服务商回执为准。

### 短信登录

```http
POST /app/user/login/phone
```

```json
{"phone":"13800000000","smsCode":"123456"}
```

两个字段均为必填 string。未注册手机号验证成功会自动创建普通用户账号；该账号尚未设置密码时，可登录后在设置页设置密码。

### 手机号与密码登录

```http
POST /app/user/login/password
```

```json
{"phone":"13800000000","password":"ExamplePass123"}
```

两个字段均为必填 string。手机号就是账号；不传 `username`。账号不存在、未设置密码、密码错误或账号不可用时拒绝登录。密码原样提交，不在前端做 MD5/BCrypt；通过 HTTPS 部署后传输，后端负责密码哈希存储。

### 显式注册

```http
POST /app/user/login/register
```

```json
{"phone":"13800000000","smsCode":"123456","password":"ExamplePass123"}
```

三个字段均为必填 string。新密码8至32位，不包含空白字符；后端还限制 UTF-8 编码不超过72字节。确认密码只用于前端校验，不传后端。

注册成功即返回登录 Token。已有手机号拒绝重复注册，不覆盖密码；已通过短信登录自动创建的账号，应登录后设置密码。

### 登录／注册共同返回格式

```json
{
  "code":1000,
  "message":"success",
  "data":{
    "userId":42,
    "token":"访问Token",
    "expire":7200,
    "refreshToken":"刷新Token",
    "refreshExpire":604800
  }
}
```

`expire` 和 `refreshExpire` 为有效时长，单位秒，示例数值不代表固定配置。使用实际响应值。成功后先保存 Token，再调用个人资料接口；不要把密码或短信验证码存入用户缓存。

普通用户正式登录响应还包含 `userId`（integer）。账号页面发起绑定时，前端用它确认登录结果仍属于当前手机号账号，避免意外切换到另一个微信已绑定账号。

### 刷新用户 Token

```http
POST /app/user/login/refreshToken
```

```json
{"refreshToken":"当前刷新Token"}
```

返回本章登录响应格式，更新访问 Token 与有效时长。刷新失败进入重新登录流程。前端应在当前项目请求层封装此调用。

### 微信小程序登录与首次绑定手机号（2026-10-13新增）

`POST /app/user/login/mini` 请求：`{"code":"本次uni.login取得的临时code"}`。不传客户端 openid，也不需要 encryptedData/iv；身份由后端使用 WxJava 向微信兑换。

已绑定手机号时返回本章登录响应中的普通用户 Token；未绑定时返回：

```json
{"code":1000,"message":"success","data":{"bindingRequired":true,"bindingToken":"随机临时凭证","bindingExpire":600}}
```

未绑定时没有访问 Token，也不会立即创建新的 user_info 账号。bindingToken 只放页面内存中，不放 URL、长期缓存或日志，10分钟过期。前端弹出手机号验证框，复用本章图片与短信验证码接口获取短信验证码，然后调用：

```http
POST /app/user/login/bindWechatPhone
Content-Type: application/json

{"bindingToken":"微信登录返回的临时凭证","phone":"13800000000","smsCode":"123456"}
```

三个字段均为必填 string。绑定凭证关联后端已核验的微信记录；短信确认该手机号的持有人。手机号没有账号时创建或补全账号，成功返回本章登录Token 格式；已有不同的手机号账号时，短信核验后先返回确认信息，尚不保存关联或签发登录 Token：

```json
{
  "code":1000,"message":"success",
  "data":{
    "confirmationRequired":true,
    "confirmationToken":"随机确认凭证",
    "confirmationExpire":600,
    "canUseWechatProfile":true,
    "wechatProfile":{"id":11,"nickName":"微信账号昵称","avatarUrl":"https://example.com/wx.png"},
    "phoneProfile":{"id":42,"nickName":"手机号账号昵称","avatarUrl":null,"phoneMasked":"138****0000"}
  }
}
```

两份资料只在短信验证通过后展示。微信侧没有已保存资料时对应字段可能为空，canUseWechatProfile=false，前端不允许选择空资料覆盖原账号。确认凭证10分钟有效，只保留在页面内存中；此时旧 bindingToken 已消费。用户选择保留来源后调用：

```http
POST /app/user/login/confirmWechatBinding
Content-Type: application/json

{"confirmationToken":"上一步的确认凭证","profileSource":"phone"}
```

profileSource 只接受 phone（保留手机号账号资料）或 wechat（使用微信对应账号已有的昵称头像）。选择微信资料时只替换其中非空的昵称、头像；不修改手机号账号ID、密码及其他业务数据。确认成功才保存微信关联、消费确认凭证并返回本章登录Token；取消确认不保存关联。

拒绝过期／不存在／重复使用的绑定凭证、错误短信码、不可用账号、手机号已绑定另一微信，以及微信已绑定其他手机号。账号创建／绑定放在同一数据库事务中；数据库写入失败后可能需要重新获取短信码。当前按一个平台账号绑定一个微信实现。

关联存于 user_wx 新增的 userId 字段，并建立唯一索引；保留对旧版 user_info.unionid 的读取兼容。旧微信无手机号记录遇到已有手机号账号时，将微信关联到手机号账号；不删除旧记录，也不自动迁移旧记录的其他业务数据。后端重启时需由当前 AutoTable 配置同步字段和索引。当前为单实例同步保护，多实例部署需增加分布式并发控制。

首次绑定页面是否已接入，以当前前端版本为准。实际微信身份兑换、短信收取和数据库绑定仍需真实环境联调。

---

## 3. 用户资料与账号安全

文档核对日期：2026-10-25；协议来源更新日期：2026-10-20；2026-10-25核对会话规则。

### 获取个人资料

```http
GET /app/user/info/person
Authorization: <用户Token>
```

`data` 为用户资料对象，主要使用 `id`、`phone`、`nickName`、`avatarUrl`、`gender`、`status`、`createTime`、`updateTime`。密码不返回；前端按实际返回字段渲染，部分可选字段可能为空。

当前状态值：1正常、0禁用、2注销。手机号展示可脱敏，发送修改密码短信使用后端返回的完整绑定手机号。

### 更新个人资料

```http
POST /app/user/info/updatePerson
Authorization: <用户Token>
```

```json
{"nickName":"券管家用户","avatarUrl":"https://example.com/avatar.png","gender":0}
```

当前接口仅接受 `nickName`（string）、`avatarUrl`（string）、`gender`（integer）。用户 ID 由 Token 确定，不接受通过此接口修改手机号、密码或账号状态。当前编辑页面应带上这三项的现有值与修改值；不假设遗漏字段一定被保留。后端成功时 `data` 为更新操作的 boolean 结果，前端应确认成功并重新获取资料。

### 设置／修改密码

```http
POST /app/user/info/updatePassword
Authorization: <用户Token>
```

```json
{"password":"NewExamplePass123","code":"123456"}
```

`password` 为新密码，`code` 为新获取的短信验证码，均为必填 string；此处不是 `smsCode`。不传手机号或用户 ID，后端从当前登录用户读取绑定手机号并核验。

调用顺序：获取用户资料 → 为绑定手机号获取图片验证码与新短信验证码 → 输入新密码和确认密码 → 提交本接口。登录时已经使用的短信码不能复用。

成功响应为通用成功格式。前端清空密码和验证码输入，结束倒计时并提示成功。当前凭据版本机制使旧访问/刷新令牌在密码变更后失效，应清除本地旧登录凭证并重新登录。入口：“我的 → 设置 → 账号 → 设置／修改密码”。

### 账号与绑定状态

登录后 `GET /app/user/info/account`，使用用户 Token。返回当前账号的 id、nickName、avatarUrl、phoneBound、phoneMasked、wechatBound、passwordSet。其中绑定与密码状态均为 boolean；不返回密码哈希、openid 或 unionid。

“我的 → 设置 → 账号”显示绑定状态和密码设置状态，提供绑定手机号／微信及设置／修改密码入口。已绑定手机号账号从此处绑定微信时，手机号固定为当前账号，仍需短信验证；如果微信已关联另一个账号，前端拒绝切换当前登录账号。

### 忘记密码接口

`POST /app/user/login/resetPassword`，Content-Type为`application/json`，允许游客访问，不发送Authorization。

```json
{
  "phone": "13800000000",
  "smsCode": "123456",
  "password": "NewPassword123"
}
```

示例手机号和验证码仅说明格式。phone为中国大陆11位手机号；smsCode为真实短信验证得到的6位数字；password为8—32位、不含空白字符，UTF-8编码最多72字节。前端额外校验两次密码一致，不将确认密码发给后端。

成功沿用R包装：`{"code":1000,"message":"success","data":null}`，不返回登录令牌、不自动登录；实际响应是否省略null字段沿用全局JSON配置。客户端显示成功页并清除本机旧登录凭证。非1000业务码读取message，失败时不进入成功页。

### 其他已存在的账号接口

| 方法 | 路径 | 请求体 | 返回与规则 |
|---|---|---|---|
| POST | `/app/user/info/logoff` | 无业务参数 | APP Token；注销账号，不等于退出登录；成功data为空 |
| POST | `/app/user/info/bindPhone` | `{"phone":"手机号","code":"短信验证码"}` | APP Token；既有手机号绑定能力，不替代微信首次绑定流程；手机号验证规则由UserInfoService执行 |

修改或重设密码后，旧APP访问与刷新Token失效；不要保留旧文档“会话继续有效”的行为。用户端当前未发现专用logout路由；清本地Token不能描述为服务端撤销全部会话。

---

## 4. 行业分类与门店浏览

文档核对日期：2026-10-25；协议来源更新日期：2026-10-20。

### 行业分类

```http
GET /app/merchant/category/list
```

无参数。返回完整平铺数组，字段为`id、parentId、name、icon、sort`。一级分类parentId为null；二级分类通过parentId归属一级分类。只返回启用且允许前台展示的分类，其一级分类也必须可见；sort升序，相同sort按id升序。

```json
{"code":1000,"message":"success","data":[
  {"id":3,"parentId":null,"name":"餐饮美食","icon":null,"sort":10},
  {"id":6,"parentId":3,"name":"奶茶咖啡","icon":null,"sort":20}
]}
```

分类不传给列表接口时表示查询全部可展示结果。所有示例ID和内容仅说明格式，不代表数据库实际记录。

### 门店列表

```http
POST /app/merchant/store/page
Content-Type: application/json

{"page":1,"size":20,"keyword":"咖啡","categoryId":3,"merchantId":1}
```

| 参数 | 必填 | 说明 |
|---|---|---|
| page、size | 否 | 分页 |
| keyword | 否 | 门店名称、商家名称、门店地址的包含搜索；去首尾空格，最多100字 |
| categoryId | 否 | 商家行业分类ID；一级分类包含直属二级分类，二级分类仅匹配本级 |
| merchantId | 否 | 限定某个商家的门店 |

筛选条件组合生效。不传筛选条件即可持续分页浏览全部公开门店。ID筛选参数须大于0；不匹配返回空列表。按门店ID倒序排列。

只返回`store.status=1`且所属`merchant_info.status=1`的门店。商家内部的门店分类不作为平台行业分类使用。

```json
{
  "code":1000,"message":"success",
  "data":{
    "list":[{
      "id":101,"name":"青禾咖啡·金山店",
      "merchantId":1,"merchantName":"青禾咖啡",
      "merchantLogo":"/uploads/logo.png","cover":"/uploads/logo.png",
      "categoryId":6,"categoryName":"奶茶咖啡",
      "address":"福州市仓山区……","phone":"0591-12345678",
      "businessHours":"09:00-22:00",
      "longitude":119.20,"latitude":26.03,
      "status":1,"statusText":"营业中"
    }],
    "pagination":{"page":1,"size":20,"total":1}
  }
}
```

字段说明：id是门店ID；categoryId/categoryName是商家行业分类；地址、电话、营业时间及坐标来自门店。cover优先返回门店独立封面，未设置时回退到商家Logo；两者都为空时前端可使用默认图。merchantLogo仍表示商家Logo。status/statusText对应后台门店状态，不根据营业时间实时判断是否处于营业时段。没有定位计算，不返回虚构距离。

### 门店详情

```http
GET /app/merchant/store/info?id=101
```

id必填且为正整数。data为单个门店对象，包含列表中的全部字段，额外返回`merchantIntroduction`（商家简介，可为空）。不返回内部备注、联系人姓名、联系人私人电话等管理字段。

不存在、门店停用或所属商家停用时返回业务错误“门店不存在或暂不可展示”。详情与列表使用相同可见规则，不能通过猜测ID读取隐藏门店。

详情页展示优惠券时，调用`POST /app/coupon/page`接口并传`storeId=101`。优惠券在详情页单独分页加载，门店详情不内嵌无上限券列表。

---

## 5. 优惠券浏览

文档核对日期：2026-10-25；协议来源更新日期：2026-10-20。

### 优惠券列表

```http
POST /app/coupon/page
Content-Type: application/json

{"page":1,"size":20,"storeId":101,"type":"CASH"}
```

| 参数 | 必填 | 说明 |
|---|---|---|
| page、size | 否 | 分页 |
| keyword | 否 | 券名称或商家名称的包含搜索，最多100字 |
| categoryId | 否 | 商家行业分类，同门店列表的上下级规则 |
| merchantId | 否 | 所属商家ID |
| storeId | 否 | 返回适用于此门店的券，兼容全店适用和指定门店两种范围 |
| type | 否 | 本章六种券类型之一；不传表示全部 |

| type | typeText |
|---|---|
| FULL_REDUCTION | 满减券 |
| DISCOUNT | 折扣券 |
| CASH | 代金券 |
| PACKAGE | 套餐券 |
| EXPERIENCE | 体验券 |
| EXCHANGE | 兑换券 |

#### 公开范围

查询复用`coupon_template、coupon_publication、coupon_store、store、merchant_info`等已有表。服务端固定要求：

1. 券模板启用、所属商家启用。
2. 发布渠道为PLATFORM、审核状态为2（通过）、上架状态为1（上架）。
3. 已发布版本published_version与模板rule_version一致，避免展示未经审核的新版本。
4. 至少有一家同商家的营业门店适用该券。
5. 传storeId时，该门店必须营业、属于对应商家，且符合券的适用范围。

草稿、待审核、驳回、下架、仅商家自有端发布或版本不一致的券，均不返回。**模板启用不等于平台上架。** 运营端现已支持提交审核、审核和上下架，由运营/商家管理端维护，本汇总不收录其管理接口。若没有符合条件的发布记录，即使运营端已有模板，用户端仍会返回空列表；现有数据需经运营实际审核、上架。

允许展示仍在平台上架的未开始、售罄和已结束券，通过saleState区分；它们不能因前端按钮而被视为已领取或已购买。按发布时间倒序、同时间按券模板ID倒序。

#### 返回字段

分页结构与门店列表相同；list每项包含：

| 字段 | 说明 |
|---|---|
| id、publicationId | 券模板ID、平台发布记录ID；不是顾客持券ID，也不是核销码 |
| merchantId、merchantName、merchantLogo | 所属商家公开信息 |
| categoryId、categoryName | 商家行业分类 |
| name、type、typeText、cover、description | 券名称、类型、类型文字、封面、完整使用说明 |
| salePrice | 售价，整数分；0表示售价为零，实际领取需调用claim接口 |
| minSpend、discountAmount、deductAmount、maxDiscount | 门槛、满减金额、抵扣额、最高优惠；整数分，不适用时为null |
| discountRate | 折扣数，例如8.80表示8.8折，不适用时为null |
| scopeType | 0商家全部营业门店；1指定门店 |
| issueLimit、issuedCount、remainingStock | 发行上限、已发放数量、剩余数量；不限量时issueLimit和remainingStock为null |
| perCustomerLimit | 每位顾客领取／购买总量上限，不表示当前用户剩余可领数量 |
| allowCombine | 0不可同享其他优惠，1允许同享 |
| saleStartTime、saleEndTime | 可领取／购买起止时间 |
| validityType、validFrom、validTo、validDays | FIXED使用固定起止日期；AFTER_ISSUE使用发券后有效天数 |
| saleState、saleStateText | 下表展示状态 |

| saleState | 文字 | 条件及优先级 |
|---|---|---|
| ENDED | 已结束 | 领取购买结束或固定有效期结束，优先级最高 |
| SOLD_OUT | 已售罄 | 有发行上限且已发放数量达到上限 |
| NOT_STARTED | 未开始 | 领取购买开始时间未到 |
| AVAILABLE | 可领购 | 通过上述时间和库存判断；实际领购仍由下单/领取接口重新校验 |

金额示例：`{"salePrice":1990,"deductAmount":2000,"discountRate":null}`表示售价19.90元、可抵扣20元。

### 优惠券详情

```http
GET /app/coupon/info?id=201
```

id为券模板ID，必填且大于0。与列表使用完全相同的公开范围；不存在或不可展示时返回业务错误“优惠券不存在或暂不可展示”。

data包含本章优惠券列表每个列表项的全部字段，另返回：

- `stores`：适用的营业门店数组，每项字段同第4章门店列表门店列表。全店适用返回当前所有营业门店；指定门店仅返回合法关联且营业的门店。
- `contents`：商品／服务关联数组，包含productId、name（项目名称）、productType（GOODS菜品商品、SERVICE服务、EXPERIENCE体验）、relationType（1适用范围，2套餐或兑换内容）、quantity、description（规格补充）。无关联返回空数组。名称、类型来自同一商家的真实商品记录；旧失效记录的名称可为null，前端应提示内容待完善。

详情示意（以下只展示新增的关联字段，其他字段同本章优惠券列表）：

```json
{
  "stores":[{"id":101,"name":"青禾咖啡·金山店","merchantId":1,"merchantName":"青禾咖啡"}],
  "contents":[{"productId":501,"name":"中杯拿铁","productType":"GOODS","relationType":2,"quantity":2,"description":"指定拿铁两杯"}]
}
```

门店列表无上限嵌套仅用于单券详情的适用范围；首页门店、首页优惠券和门店内优惠券使用各自分页接口。

---

## 6. 购券订单与免费领取

文档核对日期：2026-10-25；协议来源更新日期：2026-10-20。

### 创建订单与免费领取

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

### 模拟支付、取消与订单查询

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

---

## 7. 券包、持券详情与二维码

文档核对日期：2026-10-25；协议来源更新日期：2026-10-20。

### 我的券包和二维码

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

---

## 8. 地图定位与附近门店

文档核对日期：2026-10-25；协议来源更新日期：2026-10-24。

### 用户端附近营业门店

`POST /app/merchant/store/nearby`，公开查询，不要求用户登录。

```json
{
  "longitude": 119.2,
  "latitude": 26.1,
  "radiusMeters": 10000,
  "page": 1,
  "size": 20,
  "keyword": "咖啡",
  "merchantId": 1,
  "categoryId": 2
}
```

经纬度必填。其余可选：半径默认10000米，范围1至100000米；page默认1且须大于0；size默认20，范围1至100。
`merchantId` 为所属商家；`categoryId` 沿用原公开列表含义，为商家分类ID，同时包含其直接下级分类。
关键词最多100字，匹配门店、商家名称和地址。

成功 `data` 为：

```json
{
  "list": [],
  "pagination": { "page": 1, "size": 20, "total": 0 }
}
```

`list` 每项包含StorePoint字段：id、name、merchantId、merchantName、storeTypeId、storeTypeName、cover、address、phone、businessHours、longitude、latitude、status、statusText、located、locationStateText，额外提供数值 `distanceMeters`（米，未取整），是球面直线距离而非步行/驾车路线距离。
`pagination.total` 是符合条件的门店总条数。数据库先筛选半径、按距离升序排序、同距离按门店ID升序，再分页。
只包含有效坐标、门店营业且商家启用的数据。没有坐标或没有附近门店时返回空列表，不自动混入远处门店。
未取得用户位置时，前端继续调用原有普通门店分页接口。
距离计算使用本地数据库与MyBatis-Flex，不消耗腾讯周边搜索接口配额。

### 腾讯地点搜索与地址解析

以下三个接口使用APP Token，依赖后端已配置腾讯地图服务；Key和签名不返回前端。坐标统一GCJ-02，经度-180至180，纬度-90至90。

### 地点输入提示

`GET /app/merchant/map/suggest?keyword=闽侯县人民政府&region=福州`

`keyword` 必填，1至100字；`region` 可选，最多50字。提供城市时仅返回该城市的地点；省略时全国搜索。
每次最多20条，为选址提示，不承诺返回所有地点。

```json
{
  "coordinateSystem": "GCJ-02",
  "total": 1,
  "list": [
    {
      "id": "腾讯POI标识",
      "title": "地点名称",
      "address": "详细地址",
      "longitude": 119.2,
      "latitude": 26.1,
      "province": "福建省",
      "city": "福州市",
      "district": "闽侯县"
    }
  ]
}
```

前端输入建议防抖300至500毫秒并避免重复请求。

### 地址转坐标

`GET /app/merchant/map/geocode?address=福建省福州市闽侯县人民政府&region=福州`

address必填，1至500字；region可选，最多50字。只解析，不自动保存门店。需要人工核对地图位置。

### 坐标转地址

`GET /app/merchant/map/reverseGeocode?longitude=119.2&latitude=26.1`

经纬度均必填，范围同位置补录。两种解析成功的 `data`：

```json
{
  "coordinateSystem": "GCJ-02",
  "address": "地址文字",
  "longitude": 119.2,
  "latitude": 26.1,
  "province": "福建省",
  "city": "福州市",
  "district": "闽侯县"
}
```

地址转坐标时address保留输入地址，逆地址解析时为腾讯返回的地址。部分区划字段可能为空。
失败时显示后端message，不要求前端自己拼接腾讯请求URL；网络超时、授权/配额异常不会透传包含凭证的上游异常。

---

## 9. 游客商家入驻申请

文档核对日期：2026-10-25；协议来源更新日期：2026-10-24。

### 业务流程

用户端设置“我要成为商家”允许游客进入。申请人验证联系电话后提交商家基本资料；运营在原审核中心审批；通过后申请人取得商家营业码；在商家端验证同一手机号，输入营业码并设置商家账号密码；开通后使用既有商家登录接口登录。

全程不要求创建、登录普通用户账号，不写入 `user_info`。审核通过仅发码，**不会立即创建商家或OWNER账号，也不会提前开放经营权限**。正式商家和账号在确认开通的事务中创建。不会自动创建门店。

### 鉴权及通用约定

- 下列新接口可不带 `Authorization`；身份依据为短信验证返回的 `onboardingToken`。它不是APP或MERCHANT登录令牌，不能访问经营、用户资料、运营接口。
- 除验证码发送/验证外，所有操作均需在请求体中提交 `onboardingToken`。不可使用普通用户token替代。
- 手机号目前使用既有短信认证服务支持的中国大陆手机号。填写的联系电话由验证凭证决定，不接受客户端另传手机号改变申请归属。
- `onboardingToken` 有效30分钟；后端同时校验绝对失效时间。过期后重新验证手机号，即可继续查询原申请，不需要重新申请。
- JSON成功包装为 `{"code":1000,"message":"success","data":...}`。失败判断 `code != 1000` 并显示 `message`。
- 不把手机号验证凭证、营业码或密码放进URL，不提交到Git，不记录到前端日志；前端调用这些公共接口时不携带旧APP/商家登录令牌。

#### 忘记密码接口清单

以下新接口全部为POST：

| 路径 | 功能 |
|---|---|
| `/app/merchant/onboarding/smsCode` | 图片验证码通过后发送短信 |
| `/app/merchant/onboarding/verify` | 验证短信，取得专用入驻凭证 |
| `/app/merchant/onboarding/submit` | 游客提交申请 |
| `/app/merchant/onboarding/resubmit` | 已驳回申请修改重提 |
| `/app/merchant/onboarding/list` | 查询该验证手机号的申请摘要，最新100条 |
| `/app/merchant/onboarding/info` | 查询本人申请详情及可用营业码 |
| `/app/merchant/onboarding/renewCode` | 对已通过、未开通申请重新发码 |

继续复用：

- `GET /merchant/auth/captcha`：图片验证码。
- `GET /merchant/auth/registrationCategories`：启用的入驻商家分类。

### 短信验证

`POST /app/merchant/onboarding/smsCode`，JSON：

```json
{"phone":"申请人联系电话","captchaId":"图片验证码ID","verifyCode":"图片验证码"}
```

使用现有短信认证服务、发送间隔及验证码尝试限制，不会注册普通用户。

`POST /app/merchant/onboarding/verify`，JSON：

```json
{"phone":"申请人联系电话","smsCode":"6位短信验证码"}
```

成功 `data`：

```json
{"onboardingToken":"64位随机凭证","expiresIn":1800}
```

查询进度、重新获取营业码、在商家端开通时，均可先执行此验证流程。用户端和商家端分别验证手机号，无须跨应用传递用户端的临时凭证。

### 首次申请与驳回重提

`POST /app/merchant/onboarding/submit`，JSON：

```json
{
  "onboardingToken":"手机号验证返回的凭证",
  "requestId":"onboard_20261024_000001",
  "merchantName":"商家名称",
  "categoryId":1,
  "contactName":"联系人",
  "address":"经营地址",
  "introduction":"商家介绍，可不填"
}
```

除 `introduction` 外以上字段必填；merchantName最多100字，contactName最多50字，address最多500字，introduction最多2000字，categoryId为正整数。`requestId` 为8～64位字母、数字、下划线或短横线；同一手机号、同一标识重试相同首次申请返回原申请，不重复建单；不同资料必须使用新标识。网络结果不明确时应查询进度或沿用原标识重试，不另建重复申请。

当前不要求上传营业执照，也不提交 `licenseUrl`。申请时不填写商家账号或密码，也不提交 `userId`、审核状态、商家ID、角色或开通状态。

`POST /app/merchant/onboarding/resubmit` 使用相同字段，增加必填 `applicationNo`。只允许本人已驳回申请；申请单号保留、提交轮次递增，旧轮次及审核意见保留。重提响应丢失时先查进度，已变为待审核即表示重提成功，不应再次重提。

### 查看进度与营业码

`POST /app/merchant/onboarding/list`：

```json
{"onboardingToken":"手机号验证凭证"}
```

返回本人申请摘要数组，最多100条，不返回营业码、完整资料或驳回详情。一个手机号可有多份不同商家的申请，前端应允许选择具体申请。

`POST /app/merchant/onboarding/info`：

```json
{"onboardingToken":"手机号验证凭证","applicationNo":"申请单号"}
```

返回 `data` 字段：

| 字段 | 说明 |
|---|---|
| applicationNo / submissionNo | 申请单号/本次提交轮次 |
| merchantName | 商家名称 |
| reviewStatus | 原审批状态：1待审、2通过、3驳回 |
| state / stateText | 新流程状态及中文说明，见下表 |
| reason | 当前轮次驳回原因，仅详情返回 |
| snapshot | 当前提交的资料，仅详情返回 |
| activationCode（营业码） | 仅本人详情且审核通过、未开通、未过期时返回 |
| codeExpiresAt | 营业码失效时间 |
| activatedAt | 正式开通时间 |
| merchantId / accountId | 开通成功后的关联ID，开通前为空 |

| state | 页面展示 |
|---|---|
| PENDING | 待审核 |
| REJECTED | 已驳回，可修改重提 |
| APPROVED | 审核通过，待开通；显示营业码及复制按钮 |
| CODE_EXPIRED | 营业码已过期，引导重新获取 |
| ACTIVATED | 已开通，前往商家端登录 |

营业码格式为 `MK-` 加32位随机十六进制字符，**不是营业执照号、申请单号或日常登录密码**，有效7天、只能成功使用一次。数据库以摘要校验、AES-GCM加密保存供本人查看；列表和运营审核响应不返回码。

### 重新发码

`POST /app/merchant/onboarding/renewCode` 与详情相同请求体。仅本人审核通过且未开通申请可调用；成功返回详情及新营业码，旧码立即失效。重新获取不重复审批、不新建商家。

加密密钥从服务端JWT签名配置派生；运维轮换签名密钥后若旧码无法展示，验证手机号后重新获取即可。

商家营业码激活和OWNER经营账号管理属于商家网页版，本汇总不收录。申请人入驻与员工入职不是同一业务；不提供邀请码加入门店、撤销员工邀请等流程。当前不自动创建门店。

---

## 10. 商家核销端认证与门店身份

文档核对日期：2026-10-25；协议来源更新日期：2026-10-22；2026-10-25按代码核对。


本端使用已开通的MANAGER/STAFF账号，每个账号绑定唯一门店。OWNER是商家网页版身份，不能作为本端店长使用。账号由商家老板/平台管理员管理，不提供手机端员工邀请注册。

| 方法 | 路径 | 认证 | 请求 |
|---|---|---|---|
| GET | `/merchant/auth/captcha` | 无 | 无参数；返回captchaId、data图片，150×45 |
| POST | `/merchant/auth/login` | 无 | `{"username":"员工账号","password":"账号密码","captchaId":"图片ID","verifyCode":"图片字符"}` |
| POST | `/merchant/auth/refreshToken` | 无 | `{"refreshToken":"刷新Token"}` |
| GET | `/merchant/staff/me` | MERCHANT Token | 无参数 |
| POST | `/merchant/auth/changePassword` | MERCHANT Token | `{"oldPassword":"原密码","newPassword":"新密码"}` |
| POST | `/merchant/auth/logout` | MERCHANT Token | 无业务参数，可传 `{}` |

### 登录与刷新响应

成功data为 `{"token":"访问Token","expire":7200,"refreshToken":"刷新Token","refreshExpire":604800}`；有效时长单位秒，以实际响应为准，不能写死。成功登录后必须读取staff/me，核实角色和绑定门店再开放核销界面。失败不伪造门店身份。

### 当前门店身份

staff/me的data字段：id、username、name、merchantId、merchantName、storeId、storeName、role、roleText、canViewStoreRecords。role为MANAGER/STAFF，roleText为店长/员工；店长canViewStoreRecords=true。未绑定、多绑定、跨商家或门店停用均拒绝。

### 会话与密码

刷新轮换会话；退出使访问和刷新令牌同时失效。账号停用、密码重置、角色/门店改变后旧会话失效。changePassword验证原密码，新密码6至64位且BCrypt输入不超过72字节，不能与原密码相同，成功data为空；修改成功后清理本地认证并重新登录。

只持久化认证凭证，不持久化门店私有记录；403不触发无限刷新。改密码与认证请求不记录明文密码、验证码或Token。

---

## 11. 验券识别与优惠试算

文档核对日期：2026-10-25；协议来源更新日期：2026-10-23；2026-10-24新增分流字段。


### 识别券

`POST /merchant/redemption/inspect`；MANAGER/STAFF的MERCHANT Token；请求 `{"code":"完整64位小写十六进制券码"}`。storeId可省略；传入必须等于当前绑定门店。扫码结果原样传，不把持券ID或购券订单号当券码。

识别只查询，不建账单、不核销、不预占资格；校验账号、门店、归属、快照适用范围、顾客状态、有效期和持券状态。失败展示message并阻止后续确认。

### 成功返回

沿用code=1000、message、data的统一包装。data包含：

| 字段 | 说明 |
|---|---|
| customerCouponId、couponName | 持券ID和发券时券名称 |
| merchantName、storeId、storeName | 商家与当前核销门店 |
| type、typeText | 类型编码、中文名称 |
| requiresConsumptionAmount | 是否需要先填写消费金额，再进行试算与确认 |
| description、validFrom、validTo | 持券使用说明及实际有效期 |
| minSpend | 消费门槛，整数分，无值可为null |
| contents | 套餐、兑换、体验项目及数量 |
| rule | 发券时公开规则快照，与顾客持券详情rule结构一致 |
| confirmed | 恒为false，不是核销成功 |

| type | typeText | requiresConsumptionAmount |
|---|---|---|
| FULL_REDUCTION | 满减券 | true |
| DISCOUNT | 折扣券 | true |
| CASH | 代金券 | true |
| PACKAGE | 套餐券 | false |
| EXPERIENCE | 体验券 | false |
| EXCHANGE | 兑换券 | false |

rule包含name、type、description、scopeType、storeIds/storeNames、contentItems、ruleVersion、allowCombine，以及salePrice、minSpend、discountAmount、deductAmount、maxDiscount、discountRate。

金额统一为整数分；discountRate为折数，例如8.8表示8.8折。rule.discountAmount是满减规则面额，并非本次实际优惠。

inspect不返回顶层discountAmount/consumptionAmount，避免把仅识别误当作已试算。本次优惠由prepare返回，独立试算时由preview返回。

### 分流字段与调用顺序

inspect额外返回nextAction：金额类为ENTER_BILL，内容类为CONFIRM_CONTENT。

- ENTER_BILL：输入金额/备注 → consumption/prepare → 核对账单号、优惠、补款 → 线下收款 → consumption/confirm。
- CONFIRM_CONTENT：核对套餐/体验/兑换内容 → consumption/redeemContent，自动开单并核销。

### 可选的纯试算

`POST /merchant/redemption/preview`，请求 `{"code":"完整券码","consumptionAmount":15000}`；storeId规则同inspect。金额类必须传正整数分，内容类允许省略金额。

返回customerCouponId、couponName、merchantName、storeId/storeName、type/typeText、requiresConsumptionAmount、nextAction、description、validFrom/validTo、minSpend、contents、consumptionAmount、discountAmount、confirmed=false。顶层discountAmount为本次计算优惠，不等于规则中的面额。

preview不核销、不建账单、不占券；新流程prepare已返回试算结果，可以省略这次调用。不要根据预检成功直接把券标记为已使用。

---

## 12. 消费账单与确认核销

文档核对日期：2026-10-25；协议来源更新日期：2026-10-24。

### 最终交互及范围

首页一个「扫码用券」入口，扫码一次即可：

- 满减 FULL_REDUCTION、折扣 DISCOUNT、代金 CASH：inspect → 弹框填写金额及备注 → prepare → 展示账单、优惠和补款 → 商家线下收款 → confirm。
- 套餐 PACKAGE、体验 EXPERIENCE、兑换 EXCHANGE：inspect → 展示券内容 → 点击确认使用 → redeemContent，一次事务完成建单及核销，无需补款。
- 扫码识别、试算、建立待结算账单均不消耗券。无需重新扫码。
- 差价第一版采用商家人工确认线下已收款，**不接真实支付、不调用微信扣款、不让顾客在用户端支付**。OFFLINE仅表示商家确认收到了款。
- 一笔消费账单一张具体持券，一张券只能有一个待结算或成功账单。取消释放占用，允许另建账单。
- 本版不处理套餐混合加购、多券叠加、退款、撤销已完成核销。内容券单独成单。

### 通用约定

- 商家接口沿用原始 `Authorization: token`，不加 Bearer；登录、刷新、验证码接口不变。
- 成功结构：`{"code":1000,"message":"success","data":...}`。失败按 `code != 1000` 处理并展示 message；认证失败也可能是 HTTP 401/403。
- 所有输入和输出金额均为**整数分**：150元传15000，禁止浮点金额。
- merchantId、operatorId、优惠金额、订单号、状态等由后端产生，不能由前端指定。
- 门店账号可省略 storeId，后端取绑定门店；显式传其他门店会拒绝。OWNER 操作建单须显式指定本商家门店。
- `requestId`：前端为每次新开单产生8～64位字母数字/下划线/短横线标识，例如 UUID。一次操作的超时重试继续使用原标识；下一单换新标识。
- 同一操作人、同一 requestId、相同内容返回原单，不会多开单；不能更换金额/券/门店/备注后复用。账单修改后以 info 查询原单，不再拿初始开单请求覆盖它。

### 新增商家接口

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/merchant/consumption/prepare` | 金额类券验门槛、试算并建立待结算账单 |
| POST | `/merchant/consumption/revise` | 修改待结算账单金额、备注，保留原账单号 |
| POST | `/merchant/consumption/confirm` | 确认收款、完成账单并核销 |
| POST | `/merchant/consumption/redeemContent` | 内容类券自动开单并核销 |
| POST | `/merchant/consumption/cancel` | 取消待结算账单、释放券 |
| POST | `/merchant/consumption/page` | 按身份查询消费账单 |
| GET | `/merchant/consumption/info?id=账单ID` | 查询消费账单详情及关联券、核销记录 |

#### prepare：金额类券开单

```json
{
  "storeId": 1,
  "code": "扫码获得的64位小写十六进制券码",
  "consumptionAmount": 15000,
  "remark": "3号桌",
  "requestId": "前端生成的UUID"
}
```

code、consumptionAmount、requestId必填；remark可省略，最多200字；消费金额须大于0，最多9999999999分。适用门店、有效期、顾客状态和门槛均在后端检查。

成功返回本章的OrderView：`status=PENDING`，`version=1`。示例满120减20的券对应 discountAmount=2000、payableAmount=13000。未达到门槛不创建账单、不核销，弹框保留用户输入。

券已关联其他待结算账单时不会重复建单；前端应引导查询原账单并继续结算或取消。待结算账单目前不会自动超时取消，需显式调用 cancel。

#### revise：在同一账单上修改金额

```json
{"orderId":123,"version":1,"consumptionAmount":16000,"remark":"3号桌，增加消费"}
```

orderId、version、consumptionAmount必填。只支持待结算金额类账单，重新验证券和门槛。成功后保留id/orderNo，version递增，返回新的优惠和应补款。remark省略会置为空串。

前端必须用最新返回的版本和金额刷新收款确认界面，不能继续提交旧金额。

#### confirm：商家确认线下已收款并核销

```json
{"orderId":123,"version":1,"receivedAmount":13000}
```

三个字段必填。receivedAmount必须严格等于账单payableAmount；无需补款传0。不得将顾客先前购买券的价格计入本次receivedAmount。

成功后：

- status=COMPLETED；有补款paymentMode=OFFLINE，无补款paymentMode=NONE。
- 账单完成、成功核销记录、券已使用在同一数据库事务提交。
- confirmedBy/confirmedByName记录最终确认人，operatorId/operatorName记录开单人。
- 相同订单、相同收款金额的完成确认可重试，返回原结果，不重复收款或核销；完成后的重试不要求version仍是最新值。
- 待结算时version不符、券过期/已作废/本店不适用、金额不符都会拒绝完成；保留待结算账单，可取消。错误后不要先清空页面。

**本接口只是人工收款确认，不执行资金支付。** 不能仅凭点击过收款按钮或前端页面切换就调用成功确认。

#### redeemContent：套餐/体验/兑换券一键完成

```json
{"storeId":1,"code":"扫码获得的券码","remark":"双人套餐","requestId":"前端生成的UUID"}
```

不传consumptionAmount；金额类券不能调用此接口跳过收款流程。

在同一事务内生成唯一账单号、保存发券时的商品/服务快照、核销并完成账单。响应：status=COMPLETED，consumptionAmount=null，discountAmount=0，payableAmount=0，receivedAmount=0，paymentMode=NONE，redemptionId有值。

这里的0表示「本次无需补款」，不表示顾客买券免费。购券价在couponSnapshot.salePrice，购券交易仍保留在purchase_order。商品/服务及数量从couponSnapshot.contentItems读取。

#### cancel：取消账单

```json
{"orderId":123}
```

只支持PENDING → CANCELLED。返回原账单，不删除历史；券不核销并释放占用。重复取消返回同一结果。已完成账单不允许取消。

修改待结算金额优先使用revise，保持原单号；取消后重新开单须更换requestId，生成新单号。

#### page：分页查询

```json
{"page":1,"size":20,"storeId":1,"status":"COMPLETED","orderNo":"XF...","customerCouponId":456}
```

所有筛选可省略。page默认1、范围1～100000；size默认20、范围1～100。orderNo精确匹配；customerCouponId用于从一张持券反查关联账单。按账单ID倒序。

```json
{"list":[],"pagination":{"page":1,"size":20,"total":0}}
```

身份过滤由后端强制执行，不传scope：

- STAFF：仅绑定门店、本人开出的账单；详情、修改、取消、确认也受此限制。
- MANAGER：绑定门店全部账单，可处理本店员工开出的待结算账单。
- OWNER：本商家账单，可用storeId进一步筛选。
- 伪造筛选值不能扩大数据范围。商家page不接受merchantId扩大查询范围。

#### OrderView字段（开单、修改、确认、取消、详情和列表统一）

| 字段 | 含义 |
|---|---|
| id / orderNo | 内部账单ID / 后端生成的全局唯一账单号；修改、结算、重试均保留原号 |
| merchantId / merchantName | 所属商家及开单时名称快照 |
| storeId / storeName | 所属门店及开单时名称快照 |
| operatorId / operatorName | 开单人ID及名称快照 |
| confirmedBy / confirmedByName | 完成核销的确认人ID及名称快照，未完成为空 |
| customerCouponId | 顾客具体持券ID，不能用券模板ID替代 |
| couponName / couponType | 用券名称及六种券类型 |
| couponSnapshot | 发券时规则的公开快照；金额为分，包含套餐明细等；不包含原始券码 |
| status / statusText | PENDING/待结算、COMPLETED/已完成、CANCELLED/已取消 |
| consumptionAmount | 到店消费金额，整数分；内容类为null |
| discountAmount | 本次金额优惠，整数分；内容类兑换不折算为优惠金额，为0 |
| payableAmount | 本次还需补款，整数分 |
| receivedAmount | 商家确认本次实收补款，整数分 |
| paymentMode | 未完成为空；完成为OFFLINE或NONE |
| remark | 消费备注 |
| version | 账单版本，修改及状态变动递增；修改和确认须带入最新版本 |
| redemptionId | 成功核销记录ID；未核销为空 |
| createdAt / completedAt | 建单时间 / 完成时间；沿用服务端统一日期序列化配置 |

账单查询不返回couponCode，不允许后台页面拿历史账单中的数据重新核销。显示业务名称和状态文字，ID用于内部关联。

---

## 13. 核销记录

文档核对日期：2026-10-25；协议来源更新日期：2026-10-22；2026-10-24增加账单关联。


`POST /merchant/redemption/page`，MERCHANT Token。

请求：`{"page":1,"size":20,"scope":"self"}`；storeId可选，但门店账号只能传绑定门店。page默认1，size默认20、上限100。成功data为list/pagination标准业务分页。

| 登录角色 | 前端自动选择scope | 后端范围 |
|---|---|---|
| STAFF | self | 当前绑定门店且本人完成的核销 |
| MANAGER | store | 当前绑定门店全部核销 |

不展示范围切换按钮。特别注意：旧流水接口省略scope时，MANAGER也默认self，必须由前端按staff/me身份自动传store；这与消费账单page的后端自动角色过滤不同。

记录字段：id、customerCouponId、couponName、storeId、storeName、operatorId、operatorName、consumptionAmount、discountAmount、redeemedAt、consumptionOrderId、orderNo。金额整数分；不返回原始券码和顾客手机号。历史记录的consumptionOrderId/orderNo可能为null，不补造订单号。

当前PageInput只接收page、size、storeId、scope，**没有关键词、日期、券类型筛选，也没有独立流水详情路由**。不要把未支持条件当成已过滤结果。按消费账单号精确搜索请用consumption/page的orderNo，详情用consumption/info。

---

## 14. 旧接口兼容、能力缺口及联调顺序

文档核对日期：2026-10-25；协议来源更新日期：2026-10-25汇总核对。


### 必须同步的破坏性变更

`POST /merchant/redemption/confirm` 仍存在，但已是消费账单确认的同义入口。请求改为 `{"orderId":123,"version":1,"receivedAmount":13000}`，响应是OrderView；不能再提交 `{storeId,code,consumptionAmount}`。新接入统一使用consumption/confirm，以status=COMPLETED判断成功，不依赖旧confirmed字段。

### 本汇总不提供的业务

- 员工邀请、邀请码注册/加入门店、撤销邀请没有已确认接口，不开发假流程。
- 核销记录关键词/日期/类型搜索尚未在当前DTO接收；账单号搜索可用消费账单接口。
- 当前消费账单必须关联持券，不提供普通无券消费开单或顾客端消费单在线支付。
- 不接真实支付、多券叠加、分次核销、套餐混合加购、退款或撤销已完成核销。
- 顾客持券已使用状态可查；独立的本人消费账单/核销历史接口尚未发现，不调用商家接口代替。
- 商家核销端没有建券、审核、统计、反馈、员工管理的移动端专用契约；不能套用OWNER经营接口或平台接口。

### 联调前提和顺序

1. 后端部署需包含最新消费账单、门店定位、入驻激活增量；仅旧版本重启不等于升级成功。已执行数据库不可清空重建。
2. 用户：验证码/登录 → person/account → 分类/门店/券浏览 → create+simulatePay或claim → wallet → 展示后端二维码。
3. 店长/员工：captcha/login → staff/me → inspect → 金额券prepare/revise/confirm或内容券redeemContent → consumption/page/info及redemption/page。
4. 核销成功后刷新用户券包，确认USED；验证重复提交不重复开单/核销，取消待账单不消耗券。
5. 不让测试用例消耗真实演示券，真机摄像头、二维码、短信送达和微信身份兑换必须另做实际验收。

### 维护规则

本文件作为两个移动端的统一交接入口；原模块文档保留历史依据。接口变化须更新对应业务章节和顶部日期/更新记录，明确参数、权限、响应是否破坏兼容。每个模块的核对日期不代表该模块接口都在当天新增。

---

## 15. 用户端框架既有能力附录

文档核对日期：2026-10-25；协议来源更新日期：2026-10-25代码盘点；非本期新增功能。


以下为后端现存用户端框架路由，单列是为避免“所有接口”遗漏；不代表当前券业务需要新增入口，也不代表这些能力都已验收。地址及其他授权登录渠道缺少当前移动端正式接入规格，不能直接扩展产品。

| 模块 | 方法与路径 | 参数与边界 |
|---|---|---|
| 字典 | POST `/app/dict/info/data` | APP Token；`{"types":["字典类型编码"]}`；data是按类型组织的字典结果，使用平台实际维护编码 |
| 公共参数 | GET `/app/base/comm/param` | 游客；Query key，只允许服务端白名单配置项 |
| EPS开发元数据 | GET `/app/base/comm/eps` | 游客；返回框架实体/服务元数据，不作为普通用户业务页面 |
| 上传模式 | GET `/app/base/comm/uploadMode` | APP Token；无参数，结果依服务端上传策略 |
| 文件上传 | POST `/app/base/comm/upload` | APP Token；multipart/form-data，字段file，控制器接收文件数组；响应依上传策略，不假定固定URL字段 |
| 收货地址新增 | POST `/app/user/address/add` | APP Token；框架遗留CRUD，实体字段见下方说明 |
| 收货地址修改 | POST `/app/user/address/update` | APP Token；实体字段及id |
| 收货地址删除 | POST `/app/user/address/delete` | APP Token；`{"ids":[1]}` |
| 收货地址分页 | POST `/app/user/address/page` | APP Token；`{"page":1,"size":20}`；按当前用户查询 |
| 收货地址列表 | POST `/app/user/address/list` | APP Token；JSON筛选参数；按当前用户查询 |
| 收货地址详情 | GET `/app/user/address/info` | APP Token；Query id |
| 默认收货地址 | GET `/app/user/address/default` | APP Token；无参数 |
| 公众号授权 | POST `/app/user/login/mp` | 游客；`{"code":"微信授权code"}`；需要对应微信配置，非小程序登录 |
| 微信APP授权 | POST `/app/user/login/wxApp` | 游客；`{"code":"微信授权code"}`；需要对应微信配置 |
| 一键手机号 | POST `/app/user/login/uniPhone` | 游客；access_token、openid、appId；需对应服务配置 |
| 旧小程序手机号 | POST `/app/user/login/miniPhone` | 游客；code、encryptedData、iv；遗留渠道，不替代本汇总的bindWechatPhone短信绑定流程 |

收货地址实体字段为contact、phone、province、city、district、address、isDefault（boolean），修改还需id。不要由前端任意指定userId。该组为框架遗留能力，当前未完成新增/修改/删除/详情的逐项归属验收，不能仅凭已登录就接入为正式产品功能。

这些遗留渠道、地址CRUD及文件策略未在本次做端到端验证；上线或接新页面前补齐响应契约和权限验收。本文不收录/admin管理接口、/merchant/workspace经营接口、OWNER员工账号管理、商家激活界面、运营审核操作及其他三个应用的接口。
