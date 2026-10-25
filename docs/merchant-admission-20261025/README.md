# 商家入驻审核前端实施与验收

> 最新边界（2026-10-25）：员工邀请、注册和人员管理已从当前手机端移除，归商家后台；下文邀请相关内容仅为当时实现/验收的历史记录。当前实现与回归见 [移除记录](../merchant-staff-removal-20261025/README.md)。

> 当前状态（2026-10-25 收尾）：入驻适配器 `merchantOnboardingAvailable=false`，提交、查询、结果确认、驳回重提均明确拒绝；开发测试服务、模拟审核和测试账号已删除。下文为当时实现与验收记录，不代表当前可用。

日期：2026-10-25。范围：`apps/merchant`；基于已有 uni-app x / Vue 3 / Cool Unix 与商家黑白灰风格。用户已确认并授权：首页顶部可展开半页面进度、通过立即按角色开放经营入口、弹窗只提醒、离线通过后下次进首页提醒、确认后收起首页提示但保留申请和审核历史。

## 已实现

- 注册申请默认待审，保留登录能力。首页、我的、申请资料、帮助和反馈可访问；核销、记录、查券、建券、员工邀请等经营能力受入驻状态及原角色约束。首页禁用按钮并解释原因；导航直达也拦截，页面壳拒绝渲染被禁止的内容。
- 首页顶部进度卡默认收起，显示三节点与当前状态。点击或 Enter/Space 展开后自然占约半屏，长内容随页面滚动，不裁切驳回原因或固定整页高度。刷新、详情、查看结果按钮复用现有商家按钮。
- 平台通过结果一经读取即开放原角色的前端入口，无须先确认弹窗，也不会自动上架商品或券。通过但未确认时进首页弹出 Cool 结果提醒；确定请求成功后收起进度区。Esc 仅关闭本次提醒，不标为确认。驳回确认后仍保留修改提示。
- 首页/登录回到前台同步状态，首页待审期间每30秒查询，隐藏/卸载停查并关闭提醒。自动提醒按具体结果去重。退出/换号和新提交使旧查询、旧确认失效；状态获取失败不放开经营；确认提示失败不撤销已通过的资格。
- 我的保留“入驻申请与审核记录”入口，显示当前资料、提交时间、提交次数、历次审核状态与驳回原因。店长在驳回后可修改并重提；地图选址复用 `merchant-place-field`，H5 缺地图配置时明确说明。重提增加版本，旧审核/旧确认不得覆盖新版。取消修改有丢稿确认。
- 申请页底部开发测试工具可手动模拟通过/驳回，驳回必填原因；没有自动审核。测试服务同时限制未通过时创建/使用邀请，不能只改页面变量绕过测试前置。

## 接入边界

当前业务状态来自用户已授权的**隔离开发测试账号服务**，不是正式平台。测试账户、申请与确认只存在本次页面运行的内存中；退出重登可恢复，整页刷新清空，不向客户端、后端或本机库写入。本次没有实现服务端、平台后台真实审核接口、真实商品上架、核销、收款或跨设备持久确认。

正式构建明确拒绝这些测试 API。`types/merchant-admission.ts` 是前端视图模型；正式 URL、DTO、状态码、权限和结果确认粒度仍须后端交付，再由 `api/merchant-auth.ts` 适配。真实业务接口必须自行校验商家经营资格、角色和授权门店，前端拦截不代替服务端授权。待交付能力逐项见 [接口交接](../MERCHANT-API-HANDOFF.md)。

## 主要实现位置

| 文件 | 职责 |
| --- | --- |
| `components/merchant-admission-progress.uvue`、`styles/merchant-admission.scss` | 首页进度及商家中性样式 |
| `composables/use-merchant-admission-home.ts` | 首页同步、前台轮询、提醒生命周期 |
| `stores/merchant-admission.ts`、`utils/merchant-admission.ts` | 状态、资格派生、异步隔离、北京时间 |
| `pages/business/application.uvue` | 申请、历史、驳回修改重提、显式测试工具 |
| `components/merchant-place-field.uvue` | 首次入驻及重提共用选址、提示关联与首错焦点 |
| `utils/merchant-access.ts`、`composables/merchant-auth-guard.ts`、`components/business-shell.uvue` | 路由及页面内容限制 |
| `api/merchant-auth.ts`、`mocks/merchant-auth.ts` | 开发测试适配与正式接入边界 |

## 验证

- `pnpm check`：五端结构和53个业务SFC脚本/模板检查通过。
- `pnpm test:merchant`：21项通过，包含9项审核测试；覆盖待审登录、通过即开放、结果确认、重登未确认、按账号隔离、驳回重提/旧版本、跨账号旧响应、查询与确认失败、提醒去重和离页不弹。原账号/邀请测试已改为显式通过审核后再邀请。
- 已执行要求的 shared / client / trade / glass / theme 回归，连同商家共63项通过。测试服务和正式构建拒绝路径均有覆盖；不代表真实后端联调。
- 修改文件 Prettier 检查通过；官方 DESIGN lint 0错误0警告；严格UI静态审计0发现。见 `merchant-tests.log`、`design-lint.json`、`premium-audit.json`。
- HBuilderX 5.26 商家15页微信开发最终编译通过，10:19:24开始、10:20:52成功，晚于导航10:18:55的时序修复。记录在 `merchant-mp.log`。编译结果与真机运行分开，不宣称微信真机、实际到账或真实服务权限已验收。
- 实际 H5 开发服务 `http://localhost:9902/`，无头 Edge，390×844与320×740、系统深色偏好、减少动态效果；固定浅色。`verify-browser.cjs` 最终通过10张场景截图，0页面脚本错误、0业务写请求，结果在 `browser-results.json`。
- 浏览器覆盖：首次注册待审、键盘展开半页、伪造参数直达经营页被拒、缺驳回原因的纠错、长原因展示、修改取消/保留、重新提交、通过但未确认已开放、Esc不确认、退出重登再提醒、确定后首页提示消失、申请与历史保留、窄屏无横向溢出。
- 同工作区入驻/登录流程联合回归另见 `../merchant-onboarding-20261025/browser-results.json`：13场景；表单专项另5场景。该聊天维护欢迎/登录/入驻/员工页与Cool原语，本轮维护审核状态、核销资格前置及首页审核展示。

## 截图

| 状态 | 图片 |
| --- | --- |
| 首页进度收起 | [home-pending-collapsed.png](./home-pending-collapsed.png) |
| 首页展开约半屏 | [home-pending-expanded.png](./home-pending-expanded.png) |
| 驳回与修改原因 | [application-rejected.png](./application-rejected.png) |
| 重新提交待审 | [application-resubmitted.png](./application-resubmitted.png) |
| 通过后提醒，尚未点击确认 | [approval-notice-before-confirm.png](./approval-notice-before-confirm.png) |
| 重登后仍提醒未确认结果 | [approval-after-relogin.png](./approval-after-relogin.png) |
| 确认后收起首页提示 | [home-approved-confirmed.png](./home-approved-confirmed.png) |
| 320px查看申请及历史 | [application-history-narrow.png](./application-history-narrow.png) |

设计复核：沿用商家原有中性色、中文字体、白色内容卡和既有按钮。新增进度区不引入第二套色源；结果弹窗使用已有Cool组件。未观察到窄屏溢出或本轮新增配色漂移。未取得后端契约、真实平台审核和微信真机运行证据的部分保持待接入。
