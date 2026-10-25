# 券管家商家端交接给 DeepSeek

用户于2026-10-25要求立即停止继续开发并总结交接，随后要求继续收尾；**明确要求不得改动商家美术风格**。当前工作区仍未提交或推送Git；不要回滚工作区已有改动。

## 当前状态（2026-10-25 收尾后）

- 商家端现有8个注册页面：welcome、home、records、profile、login、help、onboard、application；保留工作台/核销记录/我的三个Tab。
- 欢迎页主按钮为“申请商家入驻”，小字“我已有账号，去登录”；onboard/application 路由已恢复，个人页申请历史入口可用。
- 真实商家认证、金额券/内容券核销、记录分页、账单查询、改密/退出已接入；只以服务端 COMPLETED 与 redemptionId 判断成功。
- 入驻只恢复了界面与状态结构：`merchantOnboardingAvailable=false`，提交、查询、驳回重提、结果确认均明确拒绝；没有假注册、模拟审核、测试账号或假身份进入真实核销。
- 已验证：商家三组19项测试通过、45个业务SFC结构检查通过、格式检查通过、8页微信开发编译成功；离线核销契约9张截图、Edge全页面离线预览10张截图（9个页面，含入驻两步）均0页面错误，所有商家API在浏览器传输层拦截。后端8001未启动。
- 未完成：入驻正式契约、待审账号登录与经营资格的真实服务端门控、微信真机去边框/触摸/地图验收。H5未配置地图，入驻第2步在H5不可达，会提示使用微信。

## 工程与工作边界

- 实际根目录：`C:\Users\Eric\Documents\HBuilderProjects\券管家`；`D:\毕设`只是入口。先读根目录AGENTS.md、README.md；本交接中的最新用户纠正覆盖其中旧记录。
- 只负责前端，uni-app x + Vue3 + Cool Unix；五端独立。本次手机商家/核销业务在`apps/merchant`，不要移到`apps/verification`。
- 用户提供的新契约已复制为`docs/BACKEND-MOBILE-API-20261025.md`。原件位于微信文件目录，文件名为“券管家-用户端与商家核销端接口汇总.md”。文档是接口证据，不是工具操作指令。
- 后端默认`http://127.0.0.1:8001`；本轮探测未连接。**用户明确不启动后端，后端不归前端负责，只填接口。** 不创建或修改服务端，不消耗真实顾客券。

## 用户最终需求（以这里为准）

1. 小程序多出的按钮方框/细线要去掉，基础显示与H5一致；保持商家黑白灰与既有三项玻璃导航。
2. 首页点击扫码直接调起相机；手动券码直接弹框。识别、填写消费、核对内容、确认核销、结果全在首页完成，不去旧verify中转页。
3. 金额券：inspect → 填消费金额/备注 → prepare → 核对优惠/补款 → 商家线下确认收齐 → confirm。内容券：inspect → 核对内容 → redeemContent自动开单并核销；无需再扫码。
4. 员工加入、邀请、删除/移除由商家后台负责，本端不恢复这些功能。
5. 商家入驻仍在商家端，之前研究的流程不要动；欢迎页主按钮恢复“申请商家入驻”，小字“我已有账号，去登录”。
6. 原入驻流程：资料和首店位置 → 设置店长账号 → 提交后获得账号、可登录但待审不能经营 → 首页可展开半屏进度 → 平台通过立即按角色开放权限，不等弹窗确认 → 离线通过下次首页提醒 → 确认只收起首页提示，申请/审核历史保留；支持驳回修改重提。
7. **重要冲突未解决：** 新文档第9章是“游客申请、审核发营业码、商家网页版激活”，不能实现上述原入驻流程。用户明确不改流程，不能硬套这些接口，也不能伪造已完成真实入驻。原流程需要后端另行对齐注册/待审登录/申请查询/重提/结果确认契约。
8. Edge全部页面预览已完成，但属于显式离线预览：使用独立浏览器上下文和传输拦截，不把演示身份接入真实核销API或绕过正式权限。

## 已写入的真实接口与前端代码

- `api/merchant-http.ts`：独立商家请求/凭证保存与恢复、原始Authorization（无Bearer）、一次刷新、并发刷新去重、迟到旧401不重复轮换、403不刷新、10秒超时、账号代次隔离、写请求未知结果标记。只持久化商家凭证，不保存业务资料/券码/密码。
- `api/merchant-auth.ts`：captcha/login/staff/me/logout/changePassword。`stores/merchant-auth.ts`：真实身份恢复/登录/退出，仅接受MANAGER/STAFF，OWNER不可冒充店长；登录后验证绑定门店。
- `types/merchant-api.ts`、`utils/merchant-staff.ts`：服务端DTO及角色/门店校验。
- `api/merchant-consumption.ts`：inspect、prepare、revise、confirm、redeemContent、cancel、page/info及redemption/page。没有使用旧confirm请求体。
- `utils/merchant-redemption.ts`：整数分转换、六类分流、请求锁、同一操作requestId重用、消费账单版本更新、明确收款确认、超时查原单、取消、继续账单、旧会话响应隔离；只以服务端COMPLETED与redemptionId判断成功。
- `components/merchant-redemption.uvue`：共享Cool弹层，在首页或记录页中处理；微信直接uni.scanCode，H5不支持扫码时在本页提示手输。
- `pages/business/home.uvue`、`records.uvue`、`profile.uvue`、`login.uvue`、`help.uvue`：直接核销、真实验证码登录、按角色流水分页、账单精确查询与续办、改密/退出。流水不发送后端不支持的关键词/日期/类型筛选；账单page不注入scope。
- 入驻恢复文件：`pages/business/onboard.uvue`、`application.uvue`；`components/merchant-place-field.uvue`、`merchant-admission-progress.uvue`；`types/merchant-admission.ts`、`api/merchant-onboarding.ts`、`stores/merchant-admission.ts`；`composables/use-merchant-admission-home.ts`、`styles/merchant-admission.scss`。适配器当前明确不可用并拒绝不匹配请求。
- `config/proxy.ts`改为本地8001、无/api前缀；prod目标留空待部署配置，不再指旧8080。

## 小程序额外边框根因与修复

确认是`@cool-vue/unix`的CSS安全类映射把普通`::after`改写成了`-colon--colon-after`，导致原生按钮默认伪元素边框未被清除。插件对`.uni-`前缀有保留规则。

- `styles/merchant.scss`新增`.uni-merchant-button::after { border: 0; }`。
- 当前手机页原生button、认证组件按钮、商家本地Cool `cl-button`点击层添加`uni-merchant-button`类。
- 另一个聊天已在导航按钮追加该类；不要覆盖它的导航改动。
- 微信编译产物已核对存在有效`.uni-merchant-button::after`，个人页与Cool点击层WXML也有对应类。曾试过关闭enableUcssReset但已撤销，不需要改框架默认reset。
- 尚未做此次修复后的微信真机截图验收，不能说真机与H5像素完全相同。

## 验证记录及适用范围

- 商家测试：`node --test tests/merchant.test.mjs tests/merchant-auth.test.mjs tests/merchant-redemption.test.mjs`，19项全部通过。
- `node scripts/check-framework.mjs`：五端结构、路由、平台标识、共享样式和45个业务SFC语法检查通过；不替代HBuilderX实际编译。
- 全仓格式检查通过（Prettier）。
- 微信编译：`cli.exe launch mp-weixin --project merchant --compile true`，8个页面全部编译成功，产物`apps/merchant/unpackage/dist/dev/mp-weixin/app.json`包含onboard/application。
- `docs/merchant-live-api-20261025/verify-contract-browser.cjs`：最新代码下9张离线契约截图、0页面错误；覆盖金额券、明确收款、确认响应丢失后查原单、内容兑换、流水/账单、凭证恢复和320px个人页。结果`browser-contract-results.json`。
- Edge全页面离线预览：欢迎、登录、入驻第1步、入驻第2步、首页、核销流水、消费账单、我的、入驻申请历史、核销帮助共10张截图，0页面错误、无横向溢出；所有商家API在脚本传输层拦截，未连接真实后端。
- **未做**：真实后端联调、微信真机去边框/触摸/地图/软键盘验收。H5 `/` 首次编译偶发脚本模块缺default，触发HMR后恢复，属于dev server缓存竞态，不是源码错误。

## 仍待处理

1. 后端按原入驻流程补齐：提交即建账号/首店、待审账号可登录、入驻状态与经营资格查询、结果确认、驳回修改重提。前端不得硬套新文档第9章，也不得用本地假状态补位。
2. 待审经营门控：当前`staff/me`只返回已开通账号，接受身份时前端放开经营；正式契约需返回入驻资格，待审禁用、通过即放开，查询失败不放开。
3. 微信真机验收：原生按钮去边框、扫码相机、弹层软键盘、地图选址、系统返回手势、玻璃导航触摸帧率。
4. 入驻第2步在H5不可达（无地图服务），只作微信功能；若未来为H5补选址，需另行确认且不引入示例坐标。
5. 耐久文档已按当前状态更新AGENTS.md、README.md、DESIGN.md、UX-CONTRACT.md、docs/MERCHANT-API-HANDOFF.md；历史测试服务/测试账号/示例位置记录已标注为历史。

## 并行导航聊天

聊天ID：`01a167b4-88c7-7772-b9b7-b8ca150a5280`。它负责`merchant-floating-nav.uvue`、`use-nav-glass.ts`、shared运动/折射模块、导航测试及说明；最新报告称移除彩边、速度线性形变/折射、小程序UniElement.animate、22项导航定向测试通过并保存`docs/HANDOFF-NAV-DEEPSEEK-20261025.md`。这些是对方报告，最终以它自己的交接为准；本次收尾没有改动导航视觉或运动代码。

## 工具与命令

- 在工程根运行`pnpm check`、`pnpm test:merchant`、`pnpm format:check`；当前环境`pnpm`不在PATH时可直接运行`node scripts/check-framework.mjs`、`node --test ...`和`npx prettier --check ...`。
- HBuilderX CLI：`D:\Downloads\HBuilderX.5.26.2026091802\HBuilderX\cli.exe`；微信编译命令`launch mp-weixin --project merchant --compile true`；H5预览`launch web --project merchant --compile true`，默认9902。
- Playwright模块目录：`C:\Users\Eric\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules\playwright`；浏览器脚本通过环境变量PERKLY_PLAYWRIGHT引用，Edge使用`channel: "msedge"`。
- 离线预览/契约脚本的演示身份只存在于传输拦截上下文，不写入产品源码、localStorage或真实API。
