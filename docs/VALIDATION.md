# 框架校验记录

日期：2026-10-12。本机 HBuilderX 5.26 / uni-app x，五端目标为 H5 与微信小程序。

## 结构与基础行为

- `pnpm install`：完成。
- `pnpm check`：五个独立应用、30 个业务 SFC 的解析与 Vue 编译、路由及共享样式检查通过。
- `pnpm test:shared`：4 项通过，覆盖独立会话、商家范围、重复领取、并发重复核销。
- `pnpm format:check`：通过。
- premium UI 静态审计：0 项发现，报告为 `docs/premium-audit.json`。
- DESIGN.md 结构检查：0 个错误；颜色 border 存在未被组件元数据引用的提示，运行时样式实际使用该颜色。

## HBuilderX 实际开发编译

| 应用 | H5 开发编译 | 微信小程序开发编译 |
| --- | --- | --- |
| client | 通过 10:18 | 通过 10:24 |
| merchant | 通过 10:19 | 通过 10:25 |
| platform-admin | 通过 10:19 | 通过 10:26 |
| merchant-independent | 通过 10:20 | 通过 10:27 |
| verification | 通过 10:20 | 通过 10:23 |

实际执行 `cli.exe launch web --project <应用名> --compile true` 和 `cli.exe launch mp-weixin --project <应用名> --compile true`。微信小程序开发产物位于各应用 `unpackage/dist/dev/mp-weixin`，产物未加入版本控制。

这些结果表示开发编译及开发产物准备通过。H5 使用 Vite 按需编译，未进行完整浏览器交互检查；微信开发者工具及真机上的登录、扫码和样式尚未验收。没有生产发行、远程发布或真实后端联调。

首次小程序编译触发 HBuilderX 官方编译插件与 Sass 插件自动安装；安装完成后重试通过。AppID 保持空白，接入微信开发者工具需要自己的配置。
## 商家手机端更新（2026-10-20）

10 页面 H5 / 微信小程序开发编译与 H5 页面检查已通过。真实员工及核销接口待接入。检查范围、全仓库既有客户端检查失败原因见 [本轮商家端校验](MERCHANT-VALIDATION-20261020.md)。
