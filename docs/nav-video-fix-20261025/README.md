# 商家玻璃导航视频问题修复

2026-10-25，复查用户录制的微信小程序操作视频 `SVID_20261025_092601_1.mp4`。视频为 1264×2736、约23.9秒。抽取的代表帧见 [视频帧概览](contact-sheet.jpg) 与 [导航局部帧](nav-strips.jpg)。

## 发现

按住底部导航时，果冻透镜高度约76.6px，导航外壳高64px；生成给小程序的 `uvue.wxss` 会给 `view` 设默认 `overflow:hidden`，因此 `.floating-nav-track` 和 `.floating-nav-wrap` 两层都裁掉透镜上下约6px。组件仅给最外层设了可见溢出，没覆盖这两个内层容器。

切页时还存在几项额外工作：位移和展开各自启动定时器，移动过程逐帧改 `left` 触发布局，三个缓存 Tab 都跟随同一组动画引用刷新；触摸高频回调未合并，切页前还额外等待16ms。

## 修复

- 商家导航组件为 `.floating-nav-wrap`、`.floating-nav-track` 显式设置 `overflow:visible`，允许透镜超出外壳；透镜水平位置改用 `transform: translateX(...)`，避免逐帧布局。
- `use-nav-glass.ts` 将弹簧位移和按压展开合并到单一帧循环：H5 使用 `requestAnimationFrame`，小程序使用约16ms的 `setTimeout`；安静时停止调度，动画有界收敛。
- 非当前 Tab 冻结自己的展示值，不再每帧消费共享运动状态；页面重新显示时恢复当前弹簧位置。重复显示不重复初始化。
- 触摸移动每帧最多处理一次，松手前同步最后触点，取消/隐藏时丢弃过期触点；移除切页前固定16ms等待。
- 保留三项 Tab、既有路由、黑白灰配色与玻璃形变，不添加白色高光。

## 对比证据

同一台 Edge 桌面浏览器、390×844 H5 页面、按住首个 Tab 时，修复前透镜外框为 `y=757.7–834.3`、导航外壳为 `y=764–828`，两个祖先容器裁切；修复后透镜几何尺寸基本不变、外框仍超出外壳约6.3px，裁切祖先为0。可查看[修复前](before-held.png)和[修复后](after-held.png)截图及机器结果 [before-browser.json](before-browser.json)、[after-browser.json](after-browser.json)。

六次切页观测中，挂载 Tab 的透镜样式更新总数从339降至166，约减少51%。切页的浏览器计时受调度和页面负载影响，前后样本波动明显，不能据此宣称平均路由时间提升；代码已移除固定等待，并减少了无效逐帧更新与布局写入。H5 触摸路径和页面错误通过浏览器脚本检查，67项Node测试、51个业务SFC检查及格式检查通过。

HBuilderX 5.26 对商家13页微信开发目标编译成功，完整日志见 [merchant-mp.log](merchant-mp.log)；生成的 `components/merchant-floating-nav.wxss` 已确认两层容器都输出 `overflow: visible`。67项Node测试、51个业务SFC检查、格式检查、严格UI审计（0发现）及官方DESIGN lint（0错误/警告）通过。该编译确认小程序目标可以构建；视频是在旧版真机上录制的，修复后的微信真机触摸帧率和主观顺滑度还需重新运行当前产物确认。不能把 H5 浏览器测量或成功编译称作真机性能通过。

## 相关源文件

- `apps/merchant/components/merchant-floating-nav.uvue`
- `apps/merchant/composables/use-nav-glass.ts`
- `tests/glass-navigation.test.mjs`
- `tests/merchant-nav-motion.test.mjs`

复现与比较脚本见 [verify-nav.cjs](verify-nav.cjs)；样式帧提取脚本见 [extract-frames.cjs](extract-frames.cjs)。
