# 券管家：当前已接入功能的前端联调接口文档

更新日期：2026-10-13。以当前后端源码和前端调用为依据；JSON 示例为格式示例，不包含真实账号凭据，也不代表每个接口均经过真实环境联调。

正式用户端：`cool-unix-main`。运营管理端：`cool-admin-vue-8.x`。后端：`cool-admin-java-test`。本文只整理当前功能使用的用户账号、商家分类和必要的管理员登录接口；框架自带的微信登录、支付、系统配置等接口不在本次交接范围。

## 1. 通用约定

- 本地后端地址：`http://127.0.0.1:8001`。
- Swagger：`http://127.0.0.1:8001/swagger-ui/index.html`。部分框架通用 CRUD 接口在 Swagger 中没有完整请求体输入框，以本文请求示例为准。
- 下文路径均为后端真实路径。POST 使用 `Content-Type: application/json`。
- 需要登录的接口携带 `Authorization: <原始Token>`，当前不加 `Bearer ` 前缀。
- `/app/user/...` 使用普通用户 Token；`/admin/...` 使用管理员 Token。两者账号体系不同。
- Cool Unix 的 `request` 已处理 Token、统一响应解析和自动刷新；调用成功得到的是 `data`。直接使用 `fetch` 或 `uni.request` 时需要自行解析完整响应。
- Cool Unix 当前 H5 开发代理前缀为 `/dev`，会转发到本地后端并移除前缀。小程序/真机需配置可访问的后端地址，设备上的 `127.0.0.1` 指向设备自身。

成功示例：

```json
{"code":1000,"message":"success","data":null}
```

业务失败示例：

```json
{"code":1001,"message":"两次请求间隔过短","data":null}
```

仅 `code=1000` 表示业务成功，不能只检查 HTTP 200。HTTP 401 或业务 `code=401` 时进入重新登录流程；其他失败展示服务端 `message`。没有返回数据的接口可能返回 `data:null`。

## 2. 接口总览

| 功能 | 方法 | 路径 | 认证 |
|---|---|---|---|
| 用户图片验证码 | GET | `/app/user/login/captcha` | 无 |
| 发送用户短信验证码 | POST | `/app/user/login/smsCode` | 无 |
| 用户短信登录 | POST | `/app/user/login/phone` | 无 |
| 用户密码登录 | POST | `/app/user/login/password` | 无 |
| 用户注册 | POST | `/app/user/login/register` | 无 |
| 刷新用户 Token | POST | `/app/user/login/refreshToken` | 无，提交 refreshToken |
| 用户个人资料 | GET | `/app/user/info/person` | 用户 Token |
| 更新用户资料 | POST | `/app/user/info/updatePerson` | 用户 Token |
| 设置／修改密码 | POST | `/app/user/info/updatePassword` | 用户 Token |
| 管理员图片验证码 | GET | `/admin/base/open/captcha` | 无 |
| 管理员登录 | POST | `/admin/base/open/login` | 无 |
| 管理员个人资料 | GET | `/admin/base/comm/person` | 管理员 Token |
| 管理员退出登录 | POST | `/admin/base/comm/logout` | 管理员 Token |
| 分类列表 | POST | `/admin/merchant/category/list` | 管理员 Token及对应权限 |
| 分类分页 | POST | `/admin/merchant/category/page` | 管理员 Token及对应权限 |
| 分类详情 | GET | `/admin/merchant/category/info` | 管理员 Token及对应权限 |
| 新增分类 | POST | `/admin/merchant/category/add` | 管理员 Token及对应权限 |
| 修改分类 | POST | `/admin/merchant/category/update` | 管理员 Token及对应权限 |
| 删除分类 | POST | `/admin/merchant/category/delete` | 管理员 Token及对应权限 |

分类分页是当前分类控制器已开放的通用接口；树形页面优先使用完整列表。

## 3. 普通用户账号

### 3.1 图片验证码

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

### 3.2 发送短信验证码

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

### 3.3 短信登录

```http
POST /app/user/login/phone
```

```json
{"phone":"13800000000","smsCode":"123456"}
```

两个字段均为必填 string。未注册手机号验证成功会自动创建普通用户账号；该账号尚未设置密码时，可登录后在设置页设置密码。

### 3.4 手机号与密码登录

```http
POST /app/user/login/password
```

```json
{"phone":"13800000000","password":"ExamplePass123"}
```

两个字段均为必填 string。手机号就是账号；不传 `username`。账号不存在、未设置密码、密码错误或账号不可用时拒绝登录。密码原样提交，不在前端做 MD5/BCrypt；通过 HTTPS 部署后传输，后端负责密码哈希存储。

### 3.5 显式注册

```http
POST /app/user/login/register
```

```json
{"phone":"13800000000","smsCode":"123456","password":"ExamplePass123"}
```

三个字段均为必填 string。新密码8至32位，不包含空白字符；后端还限制 UTF-8 编码不超过72字节。确认密码只用于前端校验，不传后端。

注册成功即返回登录 Token。已有手机号拒绝重复注册，不覆盖密码；已通过短信登录自动创建的账号，应登录后设置密码。

### 3.6 登录／注册共同返回格式

```json
{
  "code":1000,
  "message":"success",
  "data":{
    "token":"访问Token",
    "expire":7200,
    "refreshToken":"刷新Token",
    "refreshExpire":604800
  }
}
```

`expire` 和 `refreshExpire` 为有效时长，单位秒，示例数值不代表固定配置。使用实际响应值。成功后先保存 Token，再调用个人资料接口；不要把密码或短信验证码存入用户缓存。

### 3.7 刷新用户 Token

```http
POST /app/user/login/refreshToken
```

```json
{"refreshToken":"当前刷新Token"}
```

返回3.6格式，更新访问 Token 与有效时长。刷新失败进入重新登录流程。正式用户端已封装此调用。

### 3.8 获取个人资料

```http
GET /app/user/info/person
Authorization: <用户Token>
```

`data` 为用户资料对象，主要使用 `id`、`phone`、`nickName`、`avatarUrl`、`gender`、`status`、`createTime`、`updateTime`。密码不返回；前端按实际返回字段渲染，部分可选字段可能为空。

当前状态值：1正常、0禁用、2注销。手机号展示可脱敏，发送修改密码短信使用后端返回的完整绑定手机号。

### 3.9 更新个人资料

```http
POST /app/user/info/updatePerson
Authorization: <用户Token>
```

```json
{"nickName":"券管家用户","avatarUrl":"https://example.com/avatar.png","gender":0}
```

当前接口仅接受 `nickName`（string）、`avatarUrl`（string）、`gender`（integer）。用户 ID 由 Token 确定，不接受通过此接口修改手机号、密码或账号状态。当前编辑页面应带上这三项的现有值与修改值；不假设遗漏字段一定被保留。后端成功时 `data` 为更新操作的 boolean 结果，前端应确认成功并重新获取资料。

### 3.10 设置／修改密码

```http
POST /app/user/info/updatePassword
Authorization: <用户Token>
```

```json
{"password":"NewExamplePass123","code":"123456"}
```

`password` 为新密码，`code` 为新获取的短信验证码，均为必填 string；此处不是 `smsCode`。不传手机号或用户 ID，后端从当前登录用户读取绑定手机号并核验。

调用顺序：获取用户资料 → 为绑定手机号获取图片验证码与新短信验证码 → 输入新密码和确认密码 → 提交本接口。登录时已经使用的短信码不能复用。

成功响应为通用成功格式。前端清空密码和验证码输入，结束倒计时并提示成功。当前后端修改密码后仍保留已签发的登录会话。入口：“我的 → 设置 → 设置／修改密码”。

## 4. 运营管理端账号

### 4.1 管理员图片验证码与登录

```http
GET /admin/base/open/captcha?type=base64&width=150&height=50
```

返回图片验证码格式同3.1，需使用此管理员端点返回的 `captchaId`。

```http
POST /admin/base/open/login
```

```json
{"username":"管理员用户名","password":"管理员密码","captchaId":"管理员图片验证码ID","verifyCode":"1234"}
```

四个字段均为必填 string，管理员图片码字段为 `verifyCode`。成功返回 Token 格式同3.6，取得的是管理员 Token。

### 4.2 管理员资料与退出

```http
GET /admin/base/comm/person
Authorization: <管理员Token>
```

返回当前管理员资料对象，密码不返回。此对象与普通用户资料对象不同，前端不要共用普通用户字段假设。

```http
POST /admin/base/comm/logout
Authorization: <管理员Token>
Content-Type: application/json

{}
```

成功返回通用成功格式，前端清空管理员登录状态。普通用户端当前退出操作为清除本地登录缓存，不调用此管理员退出接口。

## 5. 商家分类管理

### 5.1 分类字段

| 字段 | 类型 | 说明 |
|---|---|---|
| id | integer | 分类 ID，后端生成 |
| parentId | integer / null | 上级分类 ID，一级分类使用 null |
| name | string | 分类名称，最大50字符 |
| icon | string / null | 分类图标地址，最大500字符 |
| sort | integer | 分类排序值，越小越靠前，默认0 |
| showOnFront | integer | 1前台展示、0不展示，默认1 |
| status | integer | 1启用、0停用，默认1 |
| createTime | string | 创建时间，后端维护 |
| updateTime | string | 更新时间，后端维护 |

一级、二级分类存于同一张表，通过 `parentId` 关联。顶级传 null，不使用0。前端当前按两级分类组织界面；后端尚未完整限制层级、上级有效性及循环关系。

### 5.2 获取完整列表

```http
POST /admin/merchant/category/list
Authorization: <管理员Token>

{"order":"sort","sort":"asc"}
```

也可以提交 `{}`，此时框架默认按创建时间倒序。查询参数 `order` 表示排序字段；查询参数 `sort` 是 `asc` 或 `desc`，与分类实体的整数 `sort` 字段含义不同。

示例返回：

```json
{
  "code":1000,
  "message":"success",
  "data":[
    {"id":3,"parentId":null,"name":"餐饮美食","icon":null,"sort":10,"showOnFront":1,"status":1},
    {"id":6,"parentId":3,"name":"奶茶咖啡","icon":null,"sort":10,"showOnFront":1,"status":1}
  ]
}
```

实际响应还包含时间字段，ID 以数据库实际值为准。列表是平铺数组，前端按 ID 与 parentId 组装树；上级选择框显示分类名称，提交对应 ID。当前分类控制器未配置名称关键词、parentId/status 条件过滤，不要假设这些参数会生效。两级下拉可从完整列表中筛选一级分类，避免将二级分类作为上级。

这是管理员接口。普通用户端分类展示接口尚未单独开发，不把管理员 Token 放入用户端。

### 5.3 分页与详情

```http
POST /admin/merchant/category/page
Authorization: <管理员Token>

{"page":1,"size":20,"order":"sort","sort":"asc"}
```

`page`、`size` 为 integer，默认1和20，页码从1开始。`data` 格式：

```json
{"list":[],"pagination":{"page":1,"size":20,"total":0}}
```

`pagination.total` 是总记录数。树形展示需要完整列表，不能只用一页数据拼树。

```http
GET /admin/merchant/category/info?id=3
Authorization: <管理员Token>
```

`id` 为必填 integer，`data` 为单个分类对象，字段同5.1。

### 5.4 新增分类

```http
POST /admin/merchant/category/add
Authorization: <管理员Token>

{"parentId":null,"name":"餐饮美食","icon":null,"sort":10,"showOnFront":1,"status":1}
```

`name` 必填，前端建议显式传入排序和状态。新增二级分类时将 `parentId` 换成已有一级分类 ID，`name` 为二级分类名称。

成功 `data` 格式：`{"id":新分类ID}`。已有同上级同名称数据时示例请求会失败。

当前新增校验：名称不能为空，会去掉两端空格；同一上级下名称不能重复。状态与展示标志使用数字0/1，不传字符串或 boolean。

### 5.5 修改分类

```http
POST /admin/merchant/category/update
Authorization: <管理员Token>

{"id":6,"parentId":3,"name":"奶茶咖啡","icon":null,"sort":20,"showOnFront":1,"status":1}
```

`id` 必填。成功返回通用成功格式。编辑建议从详情取原值后提交可编辑业务字段，时间由后端维护。当前修改沿用框架通用逻辑，尚未复用新增时的同名校验。

### 5.6 删除分类

```http
POST /admin/merchant/category/delete
Authorization: <管理员Token>

{"ids":[6]}
```

`ids` 为必填 ID 数组，可批量提交。成功返回通用成功格式。当前没有删除父分类时的子分类保护或商家引用保护，前端暂避免直接删除含子项的分类。

## 6. 前端交接注意事项

1. 三处验证码字段不同：用户登录／注册为 `smsCode`，修改密码为 `code`，管理员登录的图片码为 `verifyCode`。
2. 登录／注册成功拿 Token 后再获取资料，不把成功 HTTP 状态当成业务成功。
3. 所有验证码都保留为 string，避免数字转换丢失前导0。
4. 微信／QQ 绑定、普通用户分类查询、商家入驻及审核还未纳入当前已开发联调功能。
5. 本文是当前代码的接口合同整理。修改密码页面已完成语法与路由检查，实际短信发送及新密码登录需要人工联调；不要将“页面已接入”写成“所有流程已验证通过”。
