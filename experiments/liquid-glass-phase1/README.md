# Perkly Liquid Glass 第一阶段实验

2026-10-24 本目录是第一阶段原版与 Canvas/WebGL 可行性研究，不是商家端可运行入口。实际商家导航已接入 `apps/merchant/components/merchant-floating-nav.uvue`；微信端先使用半透明中性玻璃和弹簧交互，真实背景折射仍待单独验证。原生快照缓存与 WebGL 光学的研究边界见 [微信适配方案](./MINIPROGRAM-ADAPTATION.md)。

H5 原版库、DOM 背景更新、受控纹理及四项导航样机已运行。独立 uni-app x 探针用 HBuilderX 5.26 编译成功，商家应用也已在微信开发者工具模拟器中显示。该证据只证明编译和页面显示；探针的 Canvas/WebGL Shader、原生背景获取及真机触摸性能仍未验收，浏览器画面不作为微信运行证据。

## 启动和入口

在本目录运行 `npm ci --ignore-scripts --cache .npm-cache`，然后 `npm run dev`。

- `http://127.0.0.1:9910/`：左侧原版库、右侧可移植实验；明确标注浏览器与微信的区别。
- `/original.html`：未经改动的上游 examples/src/App.tsx 和 CSS。
- `/snapshot.html`：原版 WebGL 捕获指定 HTML 兄弟背景，观察 DOM 自动更新与手动刷新。
- `wechat/`：原生微信探针，需在微信开发者工具中导入并使用自己的 AppID。
- `uni-app-x/`：独立 .uvue 包装；微信产物由 HBuilderX 生成在其 unpackage/dist/dev/mp-weixin。

`npm run build` 构建三份 H5 入口并同步微信组件；`npm test` 运行六项核心测试。三入口生产构建已通过。

## 已核对的仓库

前端工作区为 `C:/Users/Eric/Documents/HBuilderProjects/券管家`，检查时分支 main，HEAD d5d8942；初始仅 AGENTS.md 存在本轮设计记录改动。五个独立应用均声明 Vue ^3.5.13、Cool Unix 1.1.0-beta.6、Vite ^6.3.5，目标 WEB / MP-WEIXIN。路由对应 .uvue 数量为 client 16、merchant 10、其余三个应用各5。详见 repository-inventory.json。

client 与 merchant 的 pages.json 保留原生 tab 路由、custom:false；组件调用 hideTabBar 隐藏系统底栏，使用本地 floating-nav 绘制并通过 switchTab 跳转。正式商家仍为三项导航，本实验四项不进入正式路由。

## 范围变化

技术验证阶段没有修改正式导航或共享光学代码，React 及原版库仅安装在本实验包。随后用户明确要求立即落地黑白灰修订布局，已另行修改商家三页的展示模板、样式与标题栏配置；身份、API、日期规则未变。该 UI 工作的证据在 ../../docs/merchant-monochrome-20261023/README.md。不能再把整个工作区描述为“完全未修改”，但正式导航实现和主依赖没有改变。`node scripts/verify-isolation.mjs --allow-merchant-ui` 比较初始2431份文件，仅明确允许六份已跟踪商家展示文件变化；不带参数仍作最初严格基线比较。新增局部样式、报告和实验文件另列于 Git 状态。

## 实测与待验证

| 能力 | 浏览器结果 | 微信结果 |
|---|---|---|
| 原版 simple-liquid-glass WebGL | diagnostics=webgl/webgl-requested/high；可见文字折射 | 原版 DOM/React 入口不能直接运行 |
| HTML 背景更新 | 改色/改字自动进入镜片；refreshBackdrop 成功 | 原生视图背景捕获待验证 |
| 移植实验 Shader | WebGL 1.0 编译、链接、纹理更新成功，glError=0 | 只有编译产物，运行待验证 |
| 四项导航运动 | 自动控制器验证：按压高60.57px、拖动宽96.51px，回落至79.2×52px，吸附第4项；方向键可切到第3项 | 真实触摸待验证 |
| 性能 | 6秒采样 CPU提交P95约0.1ms、上传约2.5ms；RAF间隔P95约1016.7ms，窗口调度受限，不能据此判断流畅度 | 开发工具和真机均未采样 |

四项运动使用时间步进弹簧，速度影响宽高，Shader 根据实时 SDF 几何改变采样；没有 CSS scale。自动手势是合成控制器输入，不等同物理触摸测试。右侧解析 SDF 为重写实验，未宣称与原版光学逐像素一致。

## 图像证据

- evidence-original-site.png：在线原站首页；并非 Listen 播放器已经单独验收。
- evidence-dom-before.png / evidence-dom-auto-update.png：HTML 捕获更新前后。
- evidence-comparison.png：独立实验浏览器入口画面；窄屏需向下查看两份实验。
- evidence-portable-browser.png：四项导航受控纹理的浏览器画面，非微信截图。
- evidence-idle-browser.json：暂停动态源后，通过不绘制诊断确认两次计数不增长。

源码固定版本、npm 完整性及版权见 source-provenance.json、upstream/LICENSE 和 upstream/THIRD_PARTY_NOTICES.md。原始归档选取了源代码、示例和声明；未修改这些文件。背景捕获不能递归包含玻璃自身。

模块定位和复用分类见 SOURCE-MAP.md；性能步骤与纹理替代方案见 PERFORMANCE.md。
