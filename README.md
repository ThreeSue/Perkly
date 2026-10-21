# 券管家工作区

项目背景、五端职责和协作约定见 [`AGENTS.md`](./AGENTS.md)。开始新对话或改代码前先读它。

**开始写业务请先看 [docs/START-HERE.md](./docs/START-HERE.md)。** 客户端首页、门店列表/详情及优惠券列表/详情已接入后端只读接口；目前未完成真实服务联调，小程序需要设备可访问的后端地址。底部有首页、我的券、我的；用户购券、订单、券包及后端二维码已按契约接入代码，待真实账号联调；三个商家核销接口按用户要求暂缓。详见 [客户端交易接入记录](./docs/CLIENT-TRADE-INTEGRATION-20261021.md)。3.0 数据库规划不含购物车，客户端不保留进程内购物车或假支付。客户端发现页接入说明见 [门店与优惠券浏览接入记录](./docs/CLIENT-DISCOVERY-INTEGRATION-20261020.md)；后端接口能力与功能映射见 [接口覆盖与后端需求报告](./docs/接口覆盖与后端需求报告.md)，跨端闭环阶段安排见 [购券核销闭环计划](./docs/客户端与商家端购券核销闭环计划-20261021.md)，客户端数据清理记录见 [静态审计](./docs/CLIENT-DATA-AUDIT-20261020.md)。其他四端保留原业务骨架。

客户端本轮功能清理、布局规划和三张静态样稿见 [布局美术规划](./docs/client-design-20261020/功能清理与布局美术规划.md)。用户已选择样稿1“商家双列、大图卡片”，蓝灰白视觉已应用于客户端首页、我的券、个人中心和底部导航；H5 页面检查记录见 [视觉检查报告](./design-qa.md)。门店与优惠券查询已按已确认契约接入，发现页行业分类按接口`parentId`分为一级、二级筛选；真实服务联调仍需设备可访问地址和符合公开条件的数据。

本仓库包含五个独立的 uni-app x / Cool Unix 应用。HBuilderX 项目根目录是各应用子目录，请按需分别打开。

| 应用目录 | 业务端 |
| --- | --- |
| `apps/client` | 客户端 |
| `apps/merchant` | 商家端 |
| `apps/platform-admin` | 平台管理端 |
| `apps/merchant-independent` | 商家独立端 |
| `apps/verification` | 商家核销端 |

公共代码放在 `packages/shared`，包含五端标识、公共模型、券码校验、金额格式化和独立 mock 工厂。共享代码不调用 `uni` API。

```text
apps/<端>/
  api/                    # 页面 API 边界、真实网络基础
  mocks/                  # 本端独立演示实例
  stores/session.ts       # 本端会话
  types/                  # 公共类型入口
  config/business.ts      # mock 开关、商家配置、接口地址
  pages/business/         # 首页、列表、登录、第二导航页、个人中心
packages/shared/
  src/                    # 五端标识、模型、纯函数、mock 工厂
  styles/business.scss    # 公共业务样式源
docs/                     # 开工、API 草案与验证记录
```

## 安装依赖

在本目录执行 `pnpm install`。

## HBuilderX

在 HBuilderX 中分别打开 `apps` 下的应用目录，然后选择运行到 H5 或微信小程序。每个应用的 `platformConfig.json` 已设置为 `WEB` 和 `MP-WEIXIN`。

## 平台标识

各应用的 DCloud AppID 和微信小程序 AppID 留空。需要运行到微信开发者工具或发布时，再填入你自己的对应 AppID。

各应用的 `config/proxy.ts` 已停用官方演示地址。客户端业务接口未交付前不使用本地 mock 冒充真实业务；接口适配只按后端确认的契约接入，不能单靠切换开关宣称已连通。

商家端已按手机核销功能图调整为 10 个业务页面，沿用 client 黄色视觉；商家核销预检/确认/流水接口契约已提供但前端未接入，员工登录、门店授权、建券和审核接口仍待补齐，不再演示登录。后端逐项交接见 [商家端接口交接](docs/MERCHANT-API-HANDOFF.md)。
