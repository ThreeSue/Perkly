# 商家手机端移除员工管理与邀请

日期：2026-10-25。用户确认员工邀请、加入/注册、撤销及移除由商家后台负责，不属于手机核销流程。本次只调整 `apps/merchant`，没有在其他应用或服务端新增人员管理。

## 当前边界

- 商家手机端不再提供员工管理、员工自助加入或邀请码注册。
- 保留店长与员工已有账号登录；员工身份、可用门店和本人/全店记录范围继续从后台授权身份派生。正式账号与授权接口仍待后端交付。
- 保留商家入驻、平台审核进度和申请历史。入驻测试服务仍只在开发环境使用。
- 旧 `staff`、`join` 路径及其他未注册路由均不可直达；页面入口删除不代表真实服务端权限控制已经接通。
- 商家后台人员管理不属于本次前端工作范围。

## 本次改动

移除了员工管理和邀请码加入页面及路由、欢迎页和登录页上的邀请入口、商家个人页的员工管理入口，以及对应的 API 适配、内存测试服务、数据类型、表单校验和专属样式。账号与权限说明已改为提示员工账号由商家后台开通和授权。

更新了角色路由回归：无论店长还是员工身份，旧员工页面都返回不可用；未登录时私有旧路径仍要求先登录。入驻审核隔离测试改为验证不同商家账号间的申请与经营资格隔离，不再通过邀请员工来证明隔离。

历史入驻记录中的邀请码实现、截图和浏览器日志保留为当时的历史证据，不代表当前手机端仍支持该功能。当前注册页面共13个。

## 验收

- `pnpm check`：51 个业务 SFC 检查通过。
- `pnpm test:shared`、`pnpm test:client`、`pnpm test:merchant`、`pnpm test:trade`、`pnpm test:glass`、`pnpm test:theme`：共67项通过，其中商家20项。
- `pnpm format:check`：通过。
- 严格 UI 静态审计：0 个发现；官方 DESIGN lint：0 错误、0 警告。
- HBuilderX 5.26 微信开发构建：商家13页编译成功，输出见 [merchant-mp.log](./merchant-mp.log)。这只验证编译，不代表微信真机运行或真实服务已验收。
- H5 Edge 回归：10个场景截图，0页面脚本错误、0业务写请求；审核前/通过后个人页的退出操作在滚动到底时仍完整位于浮动导航上方，390px和320px宽度均通过。结果见 [browser-results.json](./browser-results.json)。

## 浏览器证据

| 页面 | 证据 |
| --- | --- |
| 欢迎页（邀请入口已移除） | [welcome.png](./welcome.png) |
| 登录页 | [login.png](./login.png) |
| 待审店长个人页 | [manager-profile-bottom-visible.png](./manager-profile-bottom-visible.png) |
| 审核通过后个人页 | [approved-profile-bottom-visible.png](./approved-profile-bottom-visible.png) |
| 320px个人页 | [profile-narrow-bottom-visible.png](./profile-narrow-bottom-visible.png) |

没有真实后端账号或员工授权可用于本轮联调；后端接口交付后仍需确认手机端获取登录身份、门店授权和记录范围的契约。
