# 开工入口

实际工程根目录：`C:\Users\Eric\Documents\HBuilderProjects\券管家`。先读 AGENTS.md。

1. 在根目录执行 `pnpm install`。
2. HBuilderX → 文件 → 打开目录，选择 `apps/client`（其他端同理），不要选择 workspace 根目录。
3. 运行 → 运行到浏览器 → H5。五端沿用现有的独立 Vite 端口设置。
4. 微信小程序需要在对应 manifest.json 填入自己的 AppID，再运行到微信开发者工具；目前没有填写 AppID，也没有发布任何应用。
5. 首页开始写业务，文件是 `pages/business/home.uvue`；对应模块列表是 `pages/business/list.uvue`，底部第二项是 `records.uvue`，个人中心是 `profile.uvue`。

根目录命令：`pnpm check` 检查五端结构与 Vue SFC 语法；`pnpm test:shared` 检查 mock 的身份分离、商家范围与重复核销；`pnpm sync:theme` 同步公共业务样式。它们不代替 HBuilderX 平台编译。

原 pages/index、pages/user、pages/template 已用于客户端业务页的结构适配，原演示路由不直接注册，详见 CLIENT.md。原 pages.json、App.uvue 和配置在 `.scaffold-backup/20261012`，备份不进入版本控制。

第一轮分工建议：

| 人员 | 先交付 | 文件范围 |
| --- | --- | --- |
| 前端开发者 | 客户端浏览/领券/我的券 → 核销端预览/核销/记录 → 商家端券管理 | apps/client、verification、merchant；共享纯函数放 packages/shared |
| 后端开发者 | 先确定五端身份、商家/门店、优惠券、用户券和核销 API | 在后端自己的工程实现；用 docs/API-CONTRACT.md 对齐前端 |
| 资源成员 | 商家图片、分类与文案、Logo 文件、测试场景表 | 原始素材记录来源，优化后的素材交给对应 apps/*/static；文案与场景放 docs |

按已提供契约，优先完成“浏览券 → 免费领取或创建购券订单并模拟支付 → 我的订单/券包 → 商家预检并确认核销 → 商家流水”的真实接口闭环。付费券必须由后端模拟支付状态决定是否发券；该流程不接真实支付，也不含购物车。用户购券、券包、商家核销契约于 2026-10-21 收到；用户端已接入代码、待真实账号联调，三个商家接口按用户最新要求暂缓；员工身份、商家门店授权等前置契约仍需补齐。商家独立端定义、真实身份和品牌资料仍待确认。

## 快捷运行

在工作区根目录执行 `pnpm dev:client`，会导入客户端子项目并在 HBuilderX 中启动 H5。其他端对应 `pnpm dev:merchant`、`pnpm dev:platform-admin`、`pnpm dev:merchant-independent`、`pnpm dev:verification`。仅导入项目使用对应的 `pnpm open:<端>`。

脚本使用本机 HBuilderX 路径。换电脑后，将 `HBUILDERX_CLI` 环境变量设置为新电脑的 `cli.exe` 绝对路径，也可以直接通过 HBuilderX 菜单运行。

## 当前演示边界

- 五端有独立的登录状态与 mock 实例，刷新后恢复演示数据，各端不会同步领取和核销结果。
- 演示登录只输入昵称，不涉及真实账号。真实身份、商家权限、库存与核销防重需要接入后端。
- 核销端可用示例券码 `QGJ20260001` 演练“预览 → 确认 → 记录”；不要使用真实客户券码。
- 用户购券订单、券包及商家核销接口契约已提供但前端未接入；商家端建券/编辑、平台审核、会员及经营统计仍需接口契约。
- 商家独立端暂按商家专属顾客入口搭骨架，准确业务含义仍待确认。

2026-10-12：五端 H5 开发编译准备与微信小程序开发编译已通过；客户端 H5 已走通本地登录、领券、我的券和消费记录；模板适配后的记录见 CLIENT.md，微信开发者工具和真机仍待验收。详见 [VALIDATION.md](./VALIDATION.md)。

## 商家端当前入口（2026-10-20）

apps/merchant 已按用户手机核销功能图改为工作台、核销记录、我的及相应功能页；不再使用昵称演示登录，接口缺失时保持待接入。前端页面与后端逐项交接见 [MERCHANT-API-HANDOFF.md](MERCHANT-API-HANDOFF.md)，运行校验见 [MERCHANT-VALIDATION-20261020.md](MERCHANT-VALIDATION-20261020.md)。
