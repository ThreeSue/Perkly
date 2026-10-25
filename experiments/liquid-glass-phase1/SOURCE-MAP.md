# 上游模块定位与复用分类

上游 5.4.0，提交53e1e98ec18a42d01971bd08fc05175772a91852。以下路径相对 upstream/。保留 LICENSE 与 THIRD_PARTY_NOTICES，尤其 samasante、ybouane、html-to-image 的声明。

| 能力 | 源码位置 | 分类及说明 |
|---|---|---|
| 顶点、通用片元、模糊 | src/vendor/ybouane/shaders.ts；GlassRenderer.ts | 需适配：数学可用，DOM Canvas 创建、分辨率、上下文、FBO 生命周期需重写 |
| 原包光学片元与 RGB 采样 | src/core/webgl/optics.ts:6 | 需适配：u_lensMap、RGB 三路偏移和 map 的通道/neutral/scale 必须保持配套 |
| WebGL 运行循环与上传 | src/core/webgl/runtime.ts；source.ts；background.ts | 需重写：含 document、getBoundingClientRect、HTMLVideoElement、HTMLCanvasElement、滚动与观察器 |
| 经典 SVG 位移图 | src/core/displacementMap.ts；displacementField.ts | 纯数学函数可复用；SVG/data URI 输出在微信需要 RGBA 栅格化或改为解析场 |
| 原生材质位移图/预设 | src/vendor/samasante/displacement.ts；src/core/nativeOptics.ts | 纯参数可原样复用；图像生成与上传路径需适配 |
| SVG 色散与材质 | src/core/DisplacementFilter.tsx；src/vendor/samasante/MaterialFilter.tsx | WXSS 不能原样执行 feDisplacementMap/feColorMatrix；需转为 Shader |
| 边缘高光 | optics.ts 的 map B/specular；nativeOptics.ts 预设；src/index.tsx 的边框层 | 原版演示保留；正式接入须分别控制光学高光与 CSS 边框。关闭 Interactive.specular 不等于关闭所有高光 |
| LiquidGlassInteractive | src/interactive/index.tsx:113；spring.ts；options.ts | spring 纯计算可复用，调用需固定步长/时间适配；指针、CSS变量、DOM变换、Web Animations 需重写 |
| 显式 HTML 背景 | src/vendor/ybouane/HtmlCapture.ts；core/webgl/source.ts | 微信不能原样运行浏览器 DOM 捕获。source.ts 使用共享缓存与120ms有界刷新，不是全页面逐帧截图 |
| Scene 背景与缓存 | src/backdrop/capture.ts；index.tsx；core/useSceneBackdrop.ts | 缓存策略可借鉴；toSvg/foreignObject、DOM 观察和 React hook 不能直接搬到微信 |
| 跨框架 H5 | src/web-component/index.ts；src/webgl/index.ts | 可用于独立 H5 验证；Web Component 本身不是微信组件，不能据此宣称 .uvue 全平台直接兼容 |

## 可原样复用

纯函数/参数：stepSpring、isSettled、clamp、经典位移场的数学辅助、材质预设数值。所有派生内容保留许可证。React Hook 与原始 TS 模块的依赖边界需拆开，不能仅复制一个文件后声称整包平台无关。

## 需重写适配

触摸坐标与生命周期、Tab 吸附、速度拉伸、Canvas 获取、纹理供给、上下文丢失恢复、DPR 限制、滚动同步、可访问按钮层及 CSS 材质。上游 Interactive 主要提供指针弹性；四 Tab 拖动吸附是本实验新增的交互核心，并非原包自带完整导航。

本地 shared/motion.js、shared/renderer.js 为平台无关重写核心；scripts/build-wx.mjs 生成微信 CommonJS 副本。后者使用解析 SDF，尚未移植原包完整位移贴图、模糊链与材质层，所以视觉不等同原版。

## 不可直接执行与待验证

微信逻辑层不能原样运行网页 DOM/foreignObject 捕获与 JSX/CSS/SVG 滤镜。任意微信视图的实时背景不是因此已被证明永久不可实现：Skyline 子树快照、受控 Canvas 场景均为替代方向。前者仍需专门验证 API、像素格式与性能；本工程未切换正式渲染器，也未声称快照 API 已实测。

Safari、Firefox、iOS/Android 真机、微信 WebGL 编译/纹理方向/色彩/触摸、正式背景同步均待验证。拒绝使用全页面逐帧截图作为解决办法。
