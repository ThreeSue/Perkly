---
version: alpha
name: 券管家
description: 顾客与商家手机端采用蓝灰白、清透胶囊导航；五端独立。
colors:
  primary: "#526d88"
  text: "#26313b"
  muted: "#647585"
  background: "#eef2f6"
  surface: "#ffffff"
  border: "#dce4ec"
  danger: "#b42318"
typography:
  sans:
    fontFamily: "system-ui, PingFang SC, Microsoft YaHei, sans-serif"
  mono:
    fontFamily: "ui-monospace, monospace"
rounded:
  DEFAULT: "20px"
  sm: "12px"
spacing:
  page: "20px"
  gap: "16px"
components:
  button:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    rounded: "{rounded.sm}"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.DEFAULT}"
  divider:
    backgroundColor: "{colors.border}"
    height: "1px"
  description:
    textColor: "{colors.muted}"
  error:
    textColor: "{colors.danger}"
---

# 券管家设计约定

## Overview

2026-10-23 当前规范，覆盖此前客户端原生平底导航与商家黄色样式。用户明确授权自主视觉决策，并提供紧凑金融应用参考图，要求悬浮胶囊底部导航、液态玻璃风格（非磨砂玻璃）。保留已确认蓝灰白主色、双列门店大图、uni-app x + Vue 3 + Cool Unix、五端独立以及所有业务接口与状态规则。

主要交付微信小程序，H5 辅助预览；zh-CN，业务时间 Asia/Shanghai。顾客浏览和券包要易读、便携；商家核销要操作明确、状态可信。适用范围为 apps/client 与 apps/merchant；其他三个应用的原有公共运营主题继续由 packages/shared/styles/business.scss 管理，不混淆尚未确认的业务。

设计探索：比较了生活方式杂志、紧凑工具面板、票券凭证三种方向。选择生活指南的浏览层次与票券凭证的识别语言；参考图的紧凑卡面、悬浮胶囊和清透亮边用于导航与券包。避免把界面做成金融仪表盘，不增加虚构余额、评分、销量、距离或优惠统计。

## Colors

采用既有运行时源模型（Model B）。顾客/商家手机端语义色唯一源为 packages/shared/styles/commerce-tokens.scss；本文件镜像其值。客户端 discovery-palette.scss 只做导入适配；discovery.scss、trade.scss、客户端组件与 merchant.scss 均消费此源。

| 角色       | 色值    | 用途                       |
| ---------- | ------- | -------------------------- |
| primary    | #526d88 | 主操作、链接、焦点、图标   |
| strong     | #304b65 | 凭证卡面、胶囊当前项       |
| text       | #26313b | 标题与正文                 |
| muted      | #647585 | 次要说明，替换低对比旧灰色 |
| background | #eef2f6 | 页面底色                   |
| surface    | #ffffff | 内容表面                   |
| border     | #dce4ec | 结构边界                   |
| price      | #a3482d | 金额，兼用文字标明含义     |
| danger     | #b42318 | 错误                       |

Cool Unix Tailwind 的 bluegray 原色板由各应用 tailwind.config.ts 适配，primary-500 必须与共享源一致；pages.json/theme.json 为平台色值适配。原有客户端暗色语义变量继续保留，不新增主题切换入口。商家本轮仅浅色。

## Typography

中文采用系统字体：-apple-system、BlinkMacSystemFont、PingFang SC、Microsoft YaHei、sans-serif，无远程字体下载。品牌/页面主标题 24–25px，券包卡面主标题 27px，分区 19px，卡片标题 16–18px，正文 14–15px，辅助 12–13px，正文行高 1.6–1.8。金额用 21px 加粗、券码完整等宽换行；门店名允许换行，地址可截断且在详情提供完整值。强调依赖字重和层次，不以超大标题制造气氛。

## Layout

顾客/商家手机端内容最大 680px，外边距 20px，主要节奏 4/8/12/16/20/24/32px。首页保留四类入口和双列门店卡；图片固定比例，缺图用商家首字与明确缺图说明，不造门店照片。列表/详情复用同一宽度、搜索和反馈。个人中心从单一登录框调整为身份区、券包入口和订单入口；资料与设置仍按真实登录状态提供。

券包与订单的六类筛选采用 3 列两行分组，保留完整状态，不强迫窄屏横向滚动。页面自然滚动，不锁定内容高度。底部胶囊宽度最多 370px，左右至少 24px，距底部 16px 加 safe-area；预留 104px 加安全区，滚动到底后操作不被覆盖。

## Elevation & Depth

内容表面基本无阴影；悬浮导航使用适量投影。H5 Chromium采用SVG背板位移实现实时背景折射，蓝灰底色与深色文字保持阅读；无blur、无白色光斑或反光渐变。外壳与选中透镜联合裁切，按住时纵向变大、横向略窄，上下露出约3–7px，松手弹性收回。曲面RGB微色散叠加0.8px低透明度环境彩边，控制在细边，不把主体染成彩虹。浅色材质底色rgba(224,236,246,.68)、文字#263746；极黑背景合成的基础对比估算约4.64:1，不替代完整设备可读性验收。暗色材质提高遮盖度。Safari/Firefox、小程序与系统减少透明度/高对比保留可读半透明回退，不冒充已完成通用跨平台折射。

## Shapes

卡片 20px、关键凭证 22px、输入/搜索 12–16px、分类图标容器 18px、胶囊外壳 40px、内部当前项 30px。票券以细分隔/虚线与凭证信息层次识别，避免全屏堆叠装饰卡片。

## Components

2026-10-23 商家按钮尺寸补充：merchant.scss 统一普通操作按钮最小高度44px，长文案换行；日期范围触发器最小46px，复用 Cool 滚轮弹层，取消/确定等宽。记录页不设角色切换，券类型按本轮要求为空白禁用区域。配色、宽度与导航保持既有规范。

| 能力               | 运行时所有者                                           | 规则                                                 |
| ------------------ | ------------------------------------------------------ | ---------------------------------------------------- |
| 颜色/几何          | packages/shared/styles/commerce-tokens.scss            | 客户端薄导入层；商家直接消费                         |
| 胶囊材质           | packages/shared/styles/floating-nav.scss               | 两端共用透明度、亮边、安全区、交互与减少动态规则     |
| 顾客导航           | components/client-floating-nav.uvue                    | 首页/我的券/我的；保持 switchTab 和原路由缓存        |
| 商家导航           | components/merchant-floating-nav.uvue + business-shell | 仅工作台/核销记录/我的三个主页面显示                 |
| 基础按钮/表单/弹窗 | Cool Unix 现有 cl-\* 与 useUi                          | 保留鉴权、提交锁、错误恢复和确认行为                 |
| 搜索               | client-search.uvue                                     | 明确提交，清空、中文输入、旧响应隔离遵循 UX-CONTRACT |
| 浏览状态           | client-browse-state.uvue；discovery.scss               | 首页统一加载/空/失败与重试；列表/详情有对应明确反馈  |
| 订单与券包状态     | client-trade-state.uvue；trade.scss                    | 登录引导、读取、错误重试、空状态，业务只以后端为准   |
| 商家待接入         | merchant-pending.uvue + merchant.scss                  | 清晰说明能力缺口；不把未接入当作业务空结果或成功     |
| H5 滚动条/焦点     | client-system.scss；merchant.scss                      | 全局基线、可见焦点、高对比系统回退                   |

组件修改应使用语义变量；平台必须写入十六进制色的适配位置以本表映射复核。现有其他三端沿用 business.scss 及 sync:theme，不在本轮换肤。

## Do's and Don'ts

不新增后端、假交易、假身份、购物车或真实支付。颜色不能独自表达状态。透明材质集中在导航，不损害主内容可读性。hover 不能是唯一操作方式；导航支持 Enter/Space 与 aria-current，按压反馈短且尊重 reduced-motion。用户最新要求覆盖历史设计限制，不要求重复选择颜色与布局。

## Verification

实际检查与截图见 docs/design-20261023/README.md；严格静态审计输出 docs/design-20261023-premium-audit.json。静态检查、H5截图、小程序开发编译与真实后端/真机验收分别记录，不能互相替代。

## 玻璃导航运动增量（2026-10-23）

用户补充要求高光可滑动，并具有果冻效应、惯性。当前透镜位置由实际容器测量与触点计算，不使用设备姿态权限。松手后使用阻尼弹簧（stiffness 380、damping 25）带有限越界回弹，伸缩/轻倾斜随加速度变化，跨 tab 切换共享本端内存运动状态，避免重建跳位。纯数学在 packages/shared/src/glass-navigation.js；两端 use-nav-glass.ts 适配 Vue 与平台条件。H5交互期约16ms一步、小程序约32ms一步，900ms上限后精确归位，静止不保留动画定时器。H5尊重系统减少动态效果偏好；小程序不依赖backdrop-filter、SVG滤镜或WebGL。光学效果为清透材质与动态高光模拟，不宣称等同苹果原生渲染器。测试见 tests/glass-navigation.test.mjs。

用户最新已授权将真实背景折射先接入H5导航，并要求按住透镜放大、上下略超出外壳、左右收窄、薄彩边。当前H5渲染由packages/shared/h5/nav-refraction.js持有，纯位移图/形状几何在packages/shared/src/glass-refraction.js，两端本地组件只管理路由与生命周期。尺寸变化时生成图片，运动只更新SVG参数，不逐帧重建Canvas。放大弹簧650ms、位移弹簧900ms内结束，减少动态效果时直接归位、不放大。小程序仍为原半透明与果冻回退。白色高光删除要求继续有效。实现及证据见docs/h5-liquid-nav-20261023/README.md。
