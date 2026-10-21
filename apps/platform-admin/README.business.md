# 券管家 · 平台管理端

在 HBuilderX 中单独打开本目录，运行到 H5 或微信小程序。业务入口为 pages/business/home.uvue。

- api/index.ts：页面 API 边界，默认调用本地 mock。
- api/request.ts：真实请求基础，等待后端契约后接入。
- mocks/index.ts：本端独立的 mock 实例。
- stores/session.ts：本端演示会话，刷新即退出。
- config/business.ts：应用标识、演示商家与后端地址。
- types/：共用模型的类型入口。

示例模块是开工骨架；订单、会员、审核、平台统计等完整业务仍待开发。旧模板页面保留在 pages/index、pages/demo 等目录，但不注册到当前业务路由。

