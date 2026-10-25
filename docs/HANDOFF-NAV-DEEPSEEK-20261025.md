# DeepSeek交接：商家导航、速度折射和性能

交接日期：2026-10-25。用户要求停止继续实现并交接。本文件记录已保存到磁盘的状态，不把成功编译或H5验证当作微信真机验收。

## 1. 开始前必读

- 实际工作区：`C:\Users\Eric\Documents\HBuilderProjects\券管家`。
- 先读该目录的 `AGENTS.md`、`README.md`、`DESIGN.md`、`UX-CONTRACT.md`；当前分支 `main`。
- `D:\毕设\cool-unix` 是旧模板，不能继续在那里开发；`C:\Users\Eric\Perkly` 也不是本次前端源目录。
- 五个独立应用：`apps/client`、`apps/merchant`、`apps/platform-admin`、`apps/merchant-independent`、`apps/verification`；公共代码 `packages/shared`。uni-app x + Vue3 + Cool Unix，只负责前端，目标H5和微信小程序。
- HBuilderX使用这些源码，微信开发者工具打开编译产物 `apps/merchant/unpackage/dist/dev/mp-weixin`。两者不是需要分别维护的两套源代码。不要编辑unpackage来修源码。
- 工作区有大量同会话和并行聊天的未提交改动；未提交、未推送。不要reset、clean、整目录覆盖或按旧版本还原。

## 2. 用户最新决定（覆盖历史）

1. 商家内容采用已选第三套黑白灰修订布局，核心Tab为工作台、核销记录、我的。客户端保持自己的既定视觉，不随商家重做。
2. 用户已否定彩色描边：“我要的是折射，把彩边去掉，非常难看，折射度要随着透镜的移动速度改变，大小也是，上下大小和速度成正比，写成函数即可。”不得恢复彩虹图片、彩色装饰描边或白色光斑。
3. 用户反馈微信小程序切页明显卡顿，需要性能优化，不能只把CSS动画时间缩短后宣称解决。
4. 客户端和商家端固定浅色，不能重新跟随系统深色或恢复深色切换入口。
5. 店长与员工使用同一个登录入口；欢迎页保留“申请商家入驻”主按钮，底部小字“我已有账号，去登录”。
6. 并行聊天刚获用户纠正：商家入驻仍在本端，沿用此前“建账号后待审、首页显示进度、通过即开放权限、弹窗只提醒”的流程；不能改成“审核后营业码”流程。员工邀请、注册、加入、删除归商家后台，不在本端。

历史棱彩报告 `docs/merchant-prism-20261025/README.md` 已明确标记撤销；里面的截图不能当作当前效果。

## 3. 这轮完成的源码修改

| 文件 | 当前职责 |
| --- | --- |
| `apps/merchant/components/merchant-floating-nav.uvue` | 商家正式三Tab导航；删除谱色image；速度驱动形变；微信原生动画绑定和失败回退；触摸与跨页清理 |
| `apps/merchant/composables/use-nav-glass.ts` | 本端共享运动状态；H5帧循环；小程序批量弹簧关键帧；跨Tab续接与中断；静止速度衰减 |
| `packages/shared/src/glass-navigation.js`、`.d.ts` | 保留原共享几何/弹簧，新增商家专用纯函数 `merchantLensResponse` |
| `packages/shared/h5/nav-refraction.js`、`.d.ts` | 实际H5背景位移；新增 `speedResponsive` 选项；保留客户端默认行为 |
| `tests/glass-navigation.test.mjs` | 原导航控制器回归；更新速度参数的测试边界 |
| `tests/merchant-nav-motion.test.mjs` | 原运动测试及新批量动画、续接、中断、衰减、原生不可用回退测试 |

已删除本聊天此前添加的 `scripts/generate-merchant-prism-rim.mjs` 和 `apps/merchant/static/glass/merchant-prism-rim.png`，正式导航不再引用它们。历史截图仍保留作记录。

## 4. 速度函数与单位

唯一映射位于 `packages/shared/src/glass-navigation.js`：

```js
merchantLensResponse(velocity, expansion = 0)
// velocity: px/ms，取绝对值并限制在0–2，NaN/Infinity回到0
// p: 按压展开程度，限制在0–1.08
scaleX = 1 - 0.08 * v;
scaleY = 1 + 0.28 * v + 0.08 * p;
refraction = 24 + 18 * v;
```

大小与速度的关系是有上限的线性关系，不是给透镜画一圈颜色。按住不移动只有8%的基础扩张；速度越快，透镜越高、略收窄，折射位移越强。方向反转不改变强度。

控制器的 `speed` 内部单位为“Tab格/秒”；组件按照 `(bounds.width - 14) / 3` 得到每格像素，再除1000转换为px/ms。不要将这两个单位混用。

输入停止64ms后调用 `decaySpeed()`，速度和倾斜按60ms时间常数指数衰减，直至归零。松手后使用现有阻尼弹簧速度继续驱动，最终恢复基础形状。H5减少动态效果时保持原先的立即归位行为。

## 5. 性能优化方式

之前虽然合并成单条16ms循环，但微信回弹阶段每一帧仍改Vue响应式数据并走组件渲染/逻辑与视图通信。

现在小程序通过 `attachNative()` 注册本页渲染器，先一次计算有界弹簧关键帧，再调用透镜 `UniElement.animate`。每帧都完整指定transform，使用linear插值，保留真实弹簧的过冲/回弹。逻辑层仅有一次收尾通知，不再逐16ms提交位置。

依据DCloud官方文档，微信目标的 `UniElement.animate` 使用WXS+transition；当前HBuilderX 5.26已编译出透镜的 `change:eA`/WXS绑定和 `element.animate` 调用：

https://doc.dcloud.net.cn/uni-app-x/api/dom/unielement.html#animate

小程序不能依赖 `onfinish` 回调完成清理，因此当前采用有界收尾计时器。页面隐藏会取消本组件动画及收尾通知；新Tab按时间线采样当前位置和速度续接，不重头播放。旧页迟到隐藏不能取消新页的渲染器。

拖动仍按触点更新并合并同帧事件，松手前消费最后触点。固定导航尺寸缓存到resize，避免每次切页都查询布局。若找不到原生元素，通过 `useReactive()` 回到原响应式动画，功能可用，但该回退路径不具有批量播放的性能收益。

**真机必须确认 `bindNative()` 确实取得UniElement并走到 `nativeReady=true`。目前只确认编译与调度测试，不知道真机是否实际进入回退。**

导航button已按并行聊天约定追加 `uni-merchant-button` 类。该聊天在商家全局样式中解决Cool构建器将普通伪元素编码的问题，不要删除这个类或顺手改动其全局修复。

## 6. 已验证与未验证

### 已验证

- 最终导航定向测试22项全部通过：`docs/nav-speed-20261025/tests.log`。
- 最后结构检查为45个业务SFC，通过；该数字会随并行入驻恢复变动。
- 本轮修改文件的Prettier检查通过；严格UI静态审计0发现；DESIGN官方lint0错误/警告。
- 浏览器通过真实登录页操作，但在Playwright路由边界提供虚构接口响应；没有向应用加入演示账号代码，也没有进行真实业务写入。
- H5已检查彩色image不存在、伪元素为none、慢/快移动使真实SVG背景位移和高度同步变化、静止恢复、三个Tab切换和320px无横向溢出。
- H5一次实测：按住静止高度51.84px/强度24；慢动53.62px/26.04；较快移动57.17px/30.07；停住51.84px/24。桌面输入事件速度会受浏览器自动化耗时影响，不能用它推断微信FPS。
- 最后微信开发编译成功：2026-10-25 15:04:39，当时6个注册页面；日志 `docs/nav-speed-20261025/merchant-mp.log`。之后若并行聊天恢复路由，需要按最终pages.json重新编译。

### 待处理/不能宣称完成

1. **微信真实动态背景折射尚未完成。** 当前微信消费速度函数的几何结果，H5才有真实背景位移。折射强度参数已经计算出来，不等于微信已经采样了页面背景。
2. 未做修复后微信真机帧率、低端机触摸、冷/热切页耗时测试，不能宣称“已稳定60FPS”或“真机卡顿彻底解决”。
3. 最后全仓测试70项、69通过、1失败：`tests/merchant-auth.test.mjs:241` 对onboard/application等旧入口预期 `unavailable`，实际正在恢复为 `public`。与并行聊天按用户决定恢复入驻入口有关，已通知该聊天；不要为通过旧测试而删除入驻。详见 `docs/nav-speed-20261025/all-tests.log`。
4. 另一聊天的真实API核销、入驻恢复尚未全部收尾。其交接文件约定为 `docs/HANDOFF-DEEPSEEK-20261025.md`，以实际生成文件和最新源码为准。不要将本导航报告中“6页编译成功”的快照视为应该删除新路由的要求。
5. 用户还希望用Edge打开全部页面，这项尚未完成，不得绕过真实权限或宣称已完成。

## 7. 预览与运行状态

- 现有商家H5预览保留在 `http://127.0.0.1:9902/`。本机使用localhost曾挂起，改为显式IPv4后验证成功。
- 我为排查临时启动的9903预览已经停止（对应进程48432）；不要按旧PID重启/停止其他进程。
- 最终微信编译已结束，没有留下本轮编译任务。Playwright浏览器验证已关闭自己的浏览器。
- HBuilderX：`D:\Downloads\HBuilderX.5.26.2026091802\HBuilderX\cli.exe`。
- 如终端缺Node/Pnpm，可用本机缓存运行时：`C:\Users\Eric\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin` 和 `...\dependencies\bin\fallback` 加入当前终端PATH。

在实际工作区执行：

```powershell
pnpm check
pnpm test:glass
node --test tests/*.test.mjs
node docs/nav-speed-20261025/verify-browser.cjs
& 'D:\Downloads\HBuilderX.5.26.2026091802\HBuilderX\cli.exe' launch mp-weixin --project 'C:\Users\Eric\Documents\HBuilderProjects\券管家\apps\merchant' --compile true
```

浏览器脚本默认127.0.0.1:9902，也接受 `MERCHANT_H5_URL` 环境变量。它使用离线传输夹具，不是真实登录联调证明。当前脚本依赖本机缓存的Playwright/Edge，跨机器需要调整require路径。

## 8. 下一步顺序

1. 合并阅读两份交接和最新源码，完成并行入驻恢复的路由/测试一致性；保留真实API改动。
2. 在微信开发者工具和真实手机上记录冷/热切换、拖动、松手、快速反向、离页恢复，检查原生批量动画是否生效，再用实际指标定位剩余卡顿。不要把H5自动化的几十毫秒切页时间当作微信性能数据。
3. 若继续实现微信真实折射，先研究可靠的局部背景纹理提供链路；旧实验在 `experiments/liquid-glass-phase1`，文档包括 `MINIPROGRAM-ADAPTATION.md`、`SOURCE-MAP.md`、`PERFORMANCE.md`。保留上游MIT许可；其中Shader/Canvas样机不是正式商家入口。不能依赖网页DOM捕获，不能整页逐帧截图，也不能虚构Snapshot crop参数。
4. 后续真实折射渲染器直接消费同一 `merchantLensResponse` 输出，不再用彩边、光斑或单纯透明度冒充折射。

## 给DeepSeek的启动说明（可复制）

先读取 `C:\Users\Eric\Documents\HBuilderProjects\券管家\AGENTS.md`、README，以及本交接和另一份业务交接。延续现有uni-app x五端前端，不另建项目，不覆盖未提交修改。商家导航已删除彩边，速度函数与批量弹簧动画已保存；优先验收微信原生动画路径与真机卡顿，并如实处理微信真实背景折射尚未完成的问题。保留用户既定的商家入驻流程、店长员工共用登录、黑白灰和固定浅色规则。
