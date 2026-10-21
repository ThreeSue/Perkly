# 商家端校验记录

日期：2026-10-20。仅描述本轮实际执行结果；未发布、未连接真实商家后端、未进行微信开发者工具/真机验收。

## 检查结果

| 检查 | 实际结果 |
| --- | --- |
| pnpm check | 通过：五端结构、路由、平台标识、公共样式和 36 个业务 SFC；已将 merchant-pending 组件纳入语法检查 |
| pnpm test:merchant | 2 项通过：13 项缺失能力全部明确拒绝；无 mock/请求/业务存储；演示身份停用及业务路由边界 |
| pnpm test:shared | 4 项通过：历史共享 mock 工具检查，不代表商家端已接入真实业务 |
| 商家端本轮文件 Prettier 检查 | 通过 |
| pnpm format:check（全仓库） | 未通过：既有 apps/client/api/discovery.ts 格式问题；未改动该文件 |
| pnpm test:client | 未通过：历史测试仍读取已删除的 apps/client/mocks/discovery.ts，启动报 ENOENT；未为让测试通过而恢复假数据 |
| premium UI strict 静态审计 | 0 项发现，报告 docs/merchant-premium-audit.json；不等于完整无障碍/交互认证 |
| HBuilderX 5.26 H5 开发编译 | 通过，localhost:9902；浏览器访问后完成按需页面编译 |
| HBuilderX 5.26 微信小程序开发编译 | 通过，10 个页面；初次 CLI 请求无响应，中止该请求后重试成功；核对新 app.json 包含本轮 10 页面 |

HBuilderX 实际命令：launch web --project merchant --compile true、launch web --project merchant（用于持续 H5 预览）、launch mp-weixin --project merchant --compile true。小程序产物位于 apps/merchant/unpackage/dist/dev/mp-weixin，不能据此声称真机通过。

## H5 浏览器检查

- 全部 10 页面打开成功，无观察到的 error/warn 日志。工作台四入口、原生底部三导航、查券→建券→审核进度，以及我的→登录/帮助的入口可用；反馈页也已打开核对。
- 券码与查券关键词可输入、清空；仅当前页面临时状态，不提交真实请求，不创建业务数据。
- 本人/店长记录视图切换，页面正确显示不同待接入能力；切换不产生权限或加载员工资料。
- 桌面和 390×844 窄屏检查通过，已核对黄色标题区域、白卡、灰底、表单和原生 tabBar；窄屏无观察到的横向溢出。
- 登录/扫码/门店选择/核销确认/建券提交/反馈提交保持禁用，并有可见文字解释；没有演示登录和伪造的业务成功结果。

## 尚未验收

真实登录、权限与会话过期、服务端列表与分页、扫码/二维码解析、试算、防重核销、审核、反馈提交、离线/网络异常及真实成功/失败流程均依赖后端契约，当前不能进行这些业务验收。完整键盘与屏幕阅读器矩阵、微信开发者工具和真机测试尚未完成。当前只验收可接入页面与明确待接入状态；后续清单见 MERCHANT-API-HANDOFF.md。
