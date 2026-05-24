# 远程 HUD 弹幕文字不可见且交互浮窗首次拖拽跳位

## 错误现象

- 运行 `/qzuilib test` 的“运行时远程 HUD”后，弹幕只显示一个空的胶囊背景，文字不可见。
- 远程 HUD `DIALOG` 浮窗可以拖拽，但第一次拖拽会先跳到左上角附近，后续拖拽才正常跟随鼠标。
- 远程 HUD `DIALOG` 内容外侧叠加了默认暗色 shell 和宿主标题栏，使服务端下发 HTML 看起来像被多套了一层可见父容器。

## 触发场景

- 远程 HUD 弹幕使用 `transform: translate(...)` 方式移动外层 shell。
- 交互 HUD 在输入路由前仍可能使用注册时的临时 `320x180` widget 视口，尚未同步真实 `displayWidth/displayHeight`。
- DIALOG shell 初始由父级 flex 居中，首次拖拽时才被 `attachFixed(...)` 从 `relative/static` 语义切换成 `fixed`。
- DIALOG shell 同时承担定位/拖拽、默认视觉卡片和标题栏职责，导致宿主 chrome 与作者 HTML/CSS 的视觉边界混在一起。

## 根本原因

- HUD 文本可能走 deferred text batch。弹幕 shell 使用 GL transform 移动时，背景随 transform 移动，但 deferred 文本批次可能按未变换坐标提交，导致文字仍停在屏幕外。
- HUD 输入路径命中测试前未刷新 widget 布局尺寸。首次拖拽读取到的是临时小视口下的布局边界，拖拽初始化把浮窗从错误基线切到 fixed 定位，形成跳位。
- 更深层的 DIALOG 跳位根因是定位模型混用：视觉上由 flex 布局居中，交互时再切换为 fixed 拖拽。即使补了视口同步，只要首次交互还会切换定位模型，就仍可能在旧布局帧、旧测量结果或测试 fallback 下重新计算到错误基线。
- DIALOG 宿主 shell 被赋予背景、边框、内边距和标题栏后，会破坏“远程 HUD 只是把同一份 HTML 挂到 HUD 层”的边界，使作者页面的外观被额外父容器影响。

## 修复方案

- 弹幕移动改为更新 fixed `left` 布局坐标，不再依赖 transform 移动 shell；弹幕外观交给下发 HTML/CSS，不再额外绘制默认外层胶囊。
- HUD 输入路由在命中测试和事件派发前同步当前 Minecraft native viewport 到每个 HUD widget。
- DIALOG 根层与 shell 不再额外绘制全屏暗色背景、默认背景、边框、内边距或宿主标题栏；宿主只保留居中、拖拽和关闭按钮承载语义。
- 远程 HUD `DIALOG` shell 从创建开始就是 `position: fixed`，初始隐藏并在真实 HUD 视口可用后按实际尺寸居中；拖拽只更新已有 `left/top`，不再在首次拖拽时切换定位模型。
- 远程 HUD `DIALOG` 优先使用下发 HTML 中的 `data-qz-hud-drag-handle="true"` 元素作为拖拽把手，没有显式把手时整块解析内容作为兜底拖拽区域。

## 预防措施

- HUD 运行时交互必须在输入前同步真实视口，不能只依赖渲染阶段刷新布局。
- 可拖拽浮窗必须在创建期确定定位模型。不要把“flex/normal flow 初始布局”与“fixed 拖拽运行态”混用到同一个 shell 上。
- 会进入 deferred text batch 的文本，不应依赖 GL transform 做核心位置动画；优先使用布局坐标或显式验证文本与背景同时位移。
- 远程 HUD 的宿主包装层应默认是布局/交互承载层，除明确模式需要外，不应把额外视觉 chrome 叠到作者 HTML 外侧。
- 远程 HUD 这类协议级 smoke 必须真机看一次截图，JVM 单测只能覆盖布局和命令层，不能替代实际 HUD 渲染链路。
