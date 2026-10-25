# 券管家 · 商家端

在 HBuilderX 中单独打开本目录，运行到 H5 或微信小程序。业务入口为 `pages/business/home.uvue`。

## 当前业务代码

- `api/merchant-http.ts`：商家请求层；原始 `Authorization` Token、刷新去重、401/403、超时与未知写结果标记。
- `api/merchant-auth.ts`：验证码、登录、刷新、`staff/me`、退出和修改密码。
- `api/merchant-consumption.ts`：验券识别、消费账单、确认核销、内容券兑换及核销流水。
- `api/merchant-onboarding.ts`：原入驻流程适配器；当前 `merchantOnboardingAvailable=false`，提交/查询/结果确认仍待后端按原流程交付，页面不会伪造成功。
- `stores/merchant-auth.ts`、`stores/session.ts`：真实身份恢复、角色/门店校验和经营资格门控。
- `config/proxy.ts`：开发后端默认 `http://127.0.0.1:8001`；真机联调需改为设备可访问地址。
- `pages.json`：当前注册 8 个商家页面，保留三个 Tab；`pages/demo`、`pages/template`、`pages/user` 等旧模板未注册。

## 边界

- 不包含本地 mock、演示账号、假订单或假核销；业务数据只来自后端接口。
- 员工账号由商家后台创建和维护，本端不提供邀请、加入或移除流程。
- 入驻流程与新文档的“营业码激活”流程尚未对齐，不能直接套用；详见 `docs/MERCHANT-API-HANDOFF.md`。
- 微信真实背景折射尚未实现；当前导航只验证速度形变和运动性能。
