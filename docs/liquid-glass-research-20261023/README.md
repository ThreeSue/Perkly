# 液态玻璃研究与白色高光移除 · 2026-10-23

> 前期研究记录：用户随后已授权接入H5导航，当前实现见 [H5接入](../h5-liquid-nav-20261023/README.md)。小程序研究限制继续有效。

## 结论

**H5 的实时背景折射已完成独立样机，实测可行。微信小程序存在 Snapshot + WebGL 的候选路径，但当前项目未验证其实际运行和性能，不能承诺苹果级任意背景实时折射。**

本轮只将“删除白色高光”落入正式客户端/商家端；折射实验保存在 docs，不接入业务路由、不改变渲染器、不安装依赖、不修改接口/数据/框架。实验为自主编写的几何位移场，不声称复现苹果完整光学模型。

## 正式导航已修改

- 删除外壳白色光斑、透镜反光图层、装饰性渐变和白色内反光。选中项保留浅蓝灰指示底色，果冻形变、惯性和点击/键盘导航保留。
- 删除光照坐标、强度和共享光照计算，非拖动鼠标移动不再提交动画帧。全局鼠标监听仅支持拖出胶囊后的拖动/释放；隐藏与卸载仍清理。
- 客户端/商家端共用样式及数学，本端独立导航、登录、构建及发布保持。

## 已运行的 H5 样机

启动：在仓库根目录运行 `node docs/liquid-glass-research-20261023/preview.mjs`，访问 `http://127.0.0.1:9904/`。只在本机 loopback 提供固定四个前端文件，无后端服务或外网部署。

左侧是透明参照，右侧使用 `backdrop-filter: url(#lens-warp)`。Canvas 只生成胶囊曲面位移图，作为 SVG 的 `feImage → feDisplacementMap` 输入；背后的文字和网格仍是实时 HTML，由浏览器合成，无网页截图、无 blur、无白色高光。位移场只在尺寸变化时生成，移动时不重建 Canvas。

实测右侧边缘文字与网格弯曲，关闭折射即恢复笔直；拖动/方向键移动透镜可改变受影响区域，背景滚动约161.6px后仍显示当前内容的折射。首版场强过大造成局部字形折叠，已将幅度从0.42降到0.18、曲面带宽从20px增到28px，改善连续性。最终样机是折射能力验证，不是正式产品视觉验收。

- [折射开启](./01-refraction-on.jpg)
- [折射关闭](./02-refraction-off.jpg)
- [移动与背景滚动](./03-drag-scroll.jpg)
- [正式导航删除高光](./04-nav-no-highlight.jpg)
- [正式导航375px窄屏](./05-nav-mobile.jpg)

当前只实测桌面 Chromium 内核预览。CSS.supports 或计算样式仅能证明语法接受，不能证明渲染结果；本轮结论依赖实际开启/关闭截图。Safari/Firefox、手机 H5 性能均未验收。WebKit 仍有背板 SVG 引用滤镜的兼容问题记录，不能直接宣称同一方案覆盖所有浏览器。

## 微信小程序的具体候选路径

### Snapshot → RGBA → WebGL

微信官方 API 类型明确包含 `Snapshot.takeSnapshot`，可对绑定 snapshot 组件的子树截图，格式可选 RGBA/PNG，导出类型可选 arraybuffer/file；Canvas 支持 WebGL 上下文。由此提出以下**待验证工程方案**，不是已经跑通的小程序实现：

1. 用 Skyline snapshot 包裹需要折射的背景子树，导航绘制层放在截图子树之外，避免拍进自身导致递归反馈。
2. 请求 `type: arraybuffer`、`format: rgba`，优先将原始像素上传 WebGL 纹理，省去 PNG 编解码；真实像素尺寸、字节布局、方向、预乘 alpha 需设备验证。
3. WebGL 用胶囊 SDF/曲面法线计算背景采样偏移，并与现有弹簧形变统一参数；导航按钮继续保持正常可访问与可点击。
4. 先测“截图 + 数据回传 + 上传 + 绘制”的整体耗时，再决定刷新率和可覆盖范围。不能只测着色器帧率。

若以390×844、3倍像素密度、每像素RGBA四字节做全屏取样，约11.3MiB/帧，30次/秒约339MiB/s，仅为数据量估算，不是设备实测。截图还可能包含GPU回读、逻辑/视图层传输和重新上传；缩小实际采样子树、内容变化时更新纹理、交互结束停算可减少开销。降低背景更新频率会导致滚动时纹理滞后，必须如实视为质量取舍。

当前客户端 manifest/pages.json 及生成 app.json 未配置 Skyline；不能把这条候选路径当作当前 WebView 页面直接可用。DCloud 官方说明 uni-app x 的 Skyline 支持仍处于实验阶段，worklet 需外部 JS；切换后要回归 Cool Unix 组件、布局、滚动、安全区及手势，因此本轮未擅自切换。

### 受控 Canvas 场景

另一条可研究路径是将被折射的背景区域也绘制为 Canvas/WebGL 内容，直接对已有纹理采样。它能绕过任意页面截图，但需要维护对应内容的绘制、字体、图片、布局、滚动同步和无障碍替代。适合小范围固定场景；整页照搬会显著增加当前项目复杂度。本轮未实施。

### 系统原生液态玻璃

DCloud 的 `glass-effect-view` 对 iOS 26+ 调用系统液态玻璃，官方兼容表仅 iOS Vapor 支持，Web/微信小程序均不支持。它不能作为当前“H5 + 微信小程序”的统一组件。名称中的玻璃与微信组件框架 `glass-easel` 也不是同一能力，不能靠启用框架名获得液态折射。

## 推荐

H5可在当前技术栈内继续打磨并逐步接入；微信小程序应先对Snapshot + RGBA + WebGL做独立真机验证。先验证采样和性能，再决定是否切换页面渲染器。没有证据表明仅修改当前WXSS就能得到通用实时折射，也没有证据支持断言小程序在任何方案下都做不到。

## 本轮检查

- `pnpm check`：43个SFC结构/语法通过；格式检查通过。
- 玻璃数学/组件控制器8项通过；既有23项通过，共31项。删除已无产品用途的光照测试，不新写删除样式的镜像测试。
- 严格UI审计与DESIGN官方lint均0错误0警告。
- HBuilderX 5.26：客户端16页、商家10页微信小程序开发编译成功。
- 实际H5：正式导航高光图层数量0、透镜background-image为none；拖动进入“我的”、Enter返回首页。窄屏检查单独记录截图；实验开关、拖动、方向键、滚动可用。
- 小程序折射、微信开发者工具运行与iOS/Android真机性能尚未验证。未改后端、未启动后端、未操作保留的联调数据、未发布。

## 一手依据

- [GoogleChromeLabs：背板位移玻璃参考](https://github.com/GoogleChromeLabs/css-web-ui-demos/blob/main/html-in-canvas/public/assets/liquid_glass_reference/README.md)：验证技术方向，本实验未复制其实现或资产。
- [WebKit issue 245510](https://bugs.webkit.org/show_bug.cgi?id=245510)：背板SVG引用滤镜兼容问题。
- [微信官方 API 类型源码](https://github.com/wechat-miniprogram/api-typings/blob/master/types/wx/lib.wx.api.d.ts)：Snapshot、TakeSnapshotOption、Canvas.getContext；官方文档页本次工具无法打开，因此使用微信官方维护的类型及注释核查，未据此宣称真机通过。
- [DCloud：编译到小程序 / Skyline](https://doc.dcloud.net.cn/uni-app-x/mp/index.html#%E5%BE%AE%E4%BF%A1%E5%B0%8F%E7%A8%8B%E5%BA%8F-skyline)。
- [DCloud：glass-effect-view](https://doc.dcloud.net.cn/uni-app-x/component/glass-effect-view.html)：兼容表本次通过官方搜索索引取得，页面正文抓取超时；未将第三方插件兼容宣传作为依据。
