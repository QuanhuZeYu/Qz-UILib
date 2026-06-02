# 错误记录

本文件作为错误记录索引，按类型分组管理。详细问题分析存放在 `docs/开发者文档/errors/` 目录下。

> 2026-05-21 合并整理：将 49 条历史错误按类型归并为 12 个分组，便于快速定位同类问题。

---

## GL/渲染状态泄漏类（合并，6 条）

OpenGL 状态未正确保存/恢复，导致后续绘制异常。

- [`ERROR-20260426-ui-rounded-fill-cull-state.md`](ERROR-20260426-ui-rounded-fill-cull-state.md) — 宿主世界渲染遗留 3D GL 状态导致 rounded fill 色块不可见
- [`ERROR-20260501-opacity-context-framebuffer-binding.md`](ERROR-20260501-opacity-context-framebuffer-binding.md) — Opacity FBO 首次创建时 framebuffer 绑定泄漏导致屏闪
- [`ERROR-20260503-inventory-deferred-item-state-leak.md`](ERROR-20260503-inventory-deferred-item-state-leak.md) — 背包 deferred 物品批次回放间 GL 状态/depth 未重置
- [`ERROR-20260513-font-decoration-gl-state-leak.md`](ERROR-20260513-font-decoration-gl-state-leak.md) — 文本装饰后 GL 状态泄漏导致后续贴图透明区域发白
- [`ERROR-20260515-font-icon-alpha-state-leak.md`](ERROR-20260515-font-icon-alpha-state-leak.md) — 字体接管后图标透明状态污染（blend/alpha 通道不一致）
- [`ERROR-20260517-font-post-render-hardcoded-state.md`](ERROR-20260517-font-post-render-hardcoded-state.md) — 字体渲染后硬编码 GL 状态破坏调用方自定义状态

**共性教训**：任何跨越渲染边界的绘制入口必须用真实 GL 状态保存/恢复包裹完整生命周期，不能用硬编码 `glEnable`/`glDisable` 模拟结束状态。

---

## 圆角/裁剪渲染类（合并，4 条）

Stencil、clip、圆角相关的渲染问题。

- [`ERROR-20260419-rounded-clip-runtime-stencil.md`](ERROR-20260419-rounded-clip-runtime-stencil.md) — Rounded structural clip 开启 stencil 后子树内容整体不可见
- [`ERROR-20260419-surface-clip-coupling.md`](ERROR-20260419-surface-clip-coupling.md) — border-radius 外观与 descendant clip 语义耦合导致内容消失
- [`ERROR-20260428-smoke-absolute-probe-clipped.md`](ERROR-20260428-smoke-absolute-probe-clipped.md) — Absolute 元素被 DOM 父级 overflow:hidden 裁剪不可见
- [`ERROR-20260518-rounded-command-uniform-zero.md`](ERROR-20260518-rounded-command-uniform-zero.md) — 分角圆角 uniform 为 0 退化、box-shadow/clip 多处回归风险

**共性教训**：圆角裁剪必须区分"外观装饰"与"后代裁剪"两个独立语义；脱流元素的裁剪应相对 containing block 而非 DOM 父级。

---

## 字体系统类（合并，3 条）

字体加载、渲染、重载与 GL 资源管理。

- [`ERROR-20260515-font-reload-generation-barrier.md`](ERROR-20260515-font-reload-generation-barrier.md) — 字体重载缺少代际屏障，新旧字形混合显示
- [`ERROR-20260515-splash-font-shader-reload-race.md`](ERROR-20260515-splash-font-shader-reload-race.md) — Splash 线程与主线程字体 shader 重载竞态崩溃
- [`ERROR-20260519-font-style-drawtext-recursion.md`](ERROR-20260519-font-style-drawtext-recursion.md) — 字体样式 drawText 两个重载互相调用导致 StackOverflow

**共性教训**：字体异步管线必须以 runtimeVersion 隔离代际；多线程字体入口必须在运行时锁下串行化；方法重载链必须有明确终止点。

---

## 布局引擎类（合并，6 条）

Flex、block、positioned 定位与尺寸计算。

- [`ERROR-20260512-flex-column-auto-cross-size-zero.md`](ERROR-20260512-flex-column-auto-cross-size-zero.md) — flex column 非 stretch 子项 auto 宽度被错误压成 0
- [`ERROR-20260513-flex-column-text-block-overlap.md`](ERROR-20260513-flex-column-text-block-overlap.md) — flex column 文本块未按真实换行高度参与兄弟排布导致压叠
- [`ERROR-20260511-hud-demo-panel-overflow.md`](ERROR-20260511-hud-demo-panel-overflow.md) — HUD 面板子控件溢出（content-box 语义下百分比宽度预期行为）
- [`ERROR-20260427-custom-paint-content-box.md`](ERROR-20260427-custom-paint-content-box.md) — CustomRenderer 参数传入 padding box 而非 content box
- [`ERROR-20260518-browser-semantics-audit.md`](ERROR-20260518-browser-semantics-audit.md) — 全面审查发现 28 处不符合浏览器语义的实现
- [`ERROR-20260523-form-control-browser-semantics.md`](ERROR-20260523-form-control-browser-semantics.md) — 表单控件渲染缺少 flex 匿名文本与 textarea 内容盒语义

**共性教训**：flex column 的交叉轴 auto 尺寸必须走固有内容宽度测量；盒模型 API 必须明确传递 content box 还是 padding box；控件渲染异常优先补齐通用 HTML-like/CSS 语义。

---

## 滚动/交互类（合并，5 条）

滚动条、hover 状态、焦点与拖拽。

- [`ERROR-20260427-html-nested-scrollbar-idle-cover.md`](ERROR-20260427-html-nested-scrollbar-idle-cover.md) — 嵌套滚动条空闲时仍显示遮挡内容
- [`ERROR-20260427-html-root-scroll-focus.md`](ERROR-20260427-html-root-scroll-focus.md) — 点击 HTML-like 元素后旧页面壳随机滚动跳动
- [`ERROR-20260427-html-scrollbar-missing.md`](ERROR-20260427-html-scrollbar-missing.md) — 根视口滚动后缺少可见滚动条
- [`ERROR-20260509-html-hover-stale-after-scroll.md`](ERROR-20260509-html-hover-stale-after-scroll.md) — 滚动后 hover 状态未同步更新
- [`ERROR-20260518-html-drag-right-bottom-anchor-jump.md`](ERROR-20260518-html-drag-right-bottom-anchor-jump.md) — 浮窗首次拖拽时因 right/bottom 锚点跳位

**共性教训**：滚动偏移变化后必须重新命中测试更新 hover；拖拽起始必须先将锚点归一化为 left/top。

---

## HUD 系统类（合并，7 条）

HUD 输入分发、显示控制与生命周期。

- [`ERROR-20260511-hud-input-unregister-cme.md`](ERROR-20260511-hud-input-unregister-cme.md) — HUD 输入分发期间即时注销触发 ConcurrentModificationException
- [`ERROR-20260511-hud-overlay-click-through-and-missing-drag.md`](ERROR-20260511-hud-overlay-click-through-and-missing-drag.md) — HUD 交互浮窗点击穿透下层容器且缺少拖拽能力
- [`ERROR-20260513-hud-deferred-post-main-double-drain.md`](ERROR-20260513-hud-deferred-post-main-double-drain.md) — HUD deferred post-main 双重 drain 导致回放丢失
- [`ERROR-20260517-hud-main-menu-title-screen-leak.md`](ERROR-20260517-hud-main-menu-title-screen-leak.md) — HUD 在主页类屏幕上误显（主页识别名单不完整）
- [`ERROR-20260523-remote-hud-danmaku-drag.md`](ERROR-20260523-remote-hud-danmaku-drag.md) — 远程 HUD 弹幕文字不可见、DIALOG 额外父容器与首次拖拽跳位
- [`ERROR-20260523-remote-hud-select-popup-click-through.md`](ERROR-20260523-remote-hud-select-popup-click-through.md) — 远程 HUD select 下拉选项点击穿透到下方按钮或原生界面
- [`ERROR-20260525-hud-input-interface-static-overload.md`](ERROR-20260525-hud-input-interface-static-overload.md) — HUD 输入桥接口方法与静态辅助方法同名导致测试编译重载解析错误

**共性教训**：HUD 注册表遍历必须基于快照或等效防御；多层 HUD 输入只路由最上层命中层；弹出型控件要按顶层语义处理；主页黑名单需覆盖第三方主页类。

---

## 动画系统类（合并，4 条）

Keyframe、transition 与 opacity 效果。

- [`ERROR-20260501-keyframe-radius-clamp-jump.md`](ERROR-20260501-keyframe-radius-clamp-jump.md) — Keyframe 圆角 clamp 跳变 + fill-mode 与 transition 冲突
- [`ERROR-20260501-smoke-keyframe-opacity-flicker.md`](ERROR-20260501-smoke-keyframe-opacity-flicker.md) — Keyframe opacity 阶段触发 FBO 路径切换导致屏闪
- [`ERROR-20260522-animation-transform-opacity-clipping.md`](ERROR-20260522-animation-transform-opacity-clipping.md) — 展示页 transform 与 opacity 混合导致内容裁切
- [`ERROR-20260429-backdrop-stale-snapshot-reuse.md`](ERROR-20260429-backdrop-stale-snapshot-reuse.md) — Backdrop 旧快照过度复用导致采样错层

**共性教训**：动画属性 clamp 必须在归一化阶段完成，不能在插值后跳变；opacity 路径切换必须在帧边界而非动画中途。

---

## Gradle/构建环境类（合并，6 条）

Toolchain、依赖版本、构建配置与运行时类路径。

- [`ERROR-20260424-gradle-zulu8-toolchain.md`](ERROR-20260424-gradle-zulu8-toolchain.md) — Gradle 无法找到 Zulu 8 工具链 + 并发文件锁
- [`ERROR-20260425-idea-runclient21-jbr-toolchain.md`](ERROR-20260425-idea-runclient21-jbr-toolchain.md) — runClient21 解析 JBR 21 工具链失败/卡住
- [`ERROR-20260426-gradle-java8-worker-userpath.md`](ERROR-20260426-gradle-java8-worker-userpath.md) — 中文用户路径导致 Java 8 Worker 启动失败
- [`ERROR-20260518-gradle-parallel-build-race.md`](ERROR-20260518-gradle-parallel-build-race.md) — 并行 Gradle 进程竞争 build 目录导致编译失败
- [`ERROR-20260601-gradle-gtnhconvention-github-manifest-flaky.md`](ERROR-20260601-gradle-gtnhconvention-github-manifest-flaky.md) — `gtnhconvention` 配置阶段偶发拉取 GitHub manifest 失败
- [`ERROR-20260509-runclient21-angelica-gtnhlib-mismatch.md`](ERROR-20260509-runclient21-angelica-gtnhlib-mismatch.md) — Angelica 与 GTNHLib 版本错配导致 runClient21 崩溃
- [`ERROR-20260519-lwjgl3ify-runtime-compile-classpath-gap.md`](ERROR-20260519-lwjgl3ify-runtime-compile-classpath-gap.md) — lwjgl3ify 运行时类路径与编译类路径不一致
- [`ERROR-20260523-runserver-lwjgl3ify-relauncher.md`](ERROR-20260523-runserver-lwjgl3ify-relauncher.md) — runServer 被 LWJGL3ify relauncher 中止，未进入完整 dedicated server smoke

**共性教训**：`GRADLE_USER_HOME` 必须显式设置避免中文路径；依赖版本必须锁定一致；并行构建需避免共享 build 目录。

---

## JVM 测试环境类（合并，4 条）

纯 JVM 测试中 GL/GuiScreen/字体不可用。

- [`ERROR-20260426-jvm-test-default-font-service.md`](ERROR-20260426-jvm-test-default-font-service.md) — 纯 JVM 测试误触默认字体服务加载 LWJGL 失败
- [`ERROR-20260427-jvm-test-render-context-gl.md`](ERROR-20260427-jvm-test-render-context-gl.md) — 测试 render context 未覆写方法回落到 GL 实现导致失败
- [`ERROR-20260508-jvm-test-guiscreen-static-init.md`](ERROR-20260508-jvm-test-guiscreen-static-init.md) — 纯 JVM 测试实例化 GuiScreen 触发客户端静态初始化崩溃
- [`ERROR-20260513-jvm-test-font-guard-bufferutils.md`](ERROR-20260513-jvm-test-font-guard-bufferutils.md) — FontRenderStateGuard 初始化调用 BufferUtils 导致测试失败

**共性教训**：纯 JVM 测试不要直接实例化继承 `GuiScreen`/`BaseScreen` 的页面类；文本测量相关测试要注入确定性 `TextMeasureService`；任何可能触发 GL/LWJGL 的路径必须有 headless 保护。

---

## 配置/业务页面类（合并，9 条）

配置模板、inventory、tooltip 与业务页面。

- [`ERROR-20260509-config-template-save-state-rollback.md`](ERROR-20260509-config-template-save-state-rollback.md) — 配置模板保存失败后 Property 内存值未回滚
- [`ERROR-20260502-inventory-slot-opacity-workaround.md`](ERROR-20260502-inventory-slot-opacity-workaround.md) — 背包 slot 半透明底板穿透游戏画面
- [`ERROR-20260503-inventory-overlay-tooltip-table-review.md`](ERROR-20260503-inventory-overlay-tooltip-table-review.md) — 背包 overlay 重复绘制 + tooltip 未刷新 + 空 thead 语义问题
- [`ERROR-20260507-inventory-tooltip-radius-guard.md`](ERROR-20260507-inventory-tooltip-radius-guard.md) — Tooltip 32px 半径口径偏差导致窄屏定位测试失败
- [`ERROR-20260509-screen-context-missing-runtime-adapters.md`](ERROR-20260509-screen-context-missing-runtime-adapters.md) — 页面渲染上下文遗漏 runtimeAdapters 导致物品图标不显示
- [`ERROR-20260519-smoke-diagnostic-text-clipped.md`](ERROR-20260519-smoke-diagnostic-text-clipped.md) — Smoke 诊断文本被固定小高度裁切
- [`ERROR-20260522-browser-semantics-outline-null-crash.md`](ERROR-20260522-browser-semantics-outline-null-crash.md) — 语义展示页 focus 回写向非空 setter 传 null 导致崩溃
- [`ERROR-20260525-config-sync-headless-static-init.md`](ERROR-20260525-config-sync-headless-static-init.md) — 配置同步模型误借 GuiScreen 类上的静态工具，导致纯 JVM 测试触发 Minecraft 客户端静态初始化
- [`ERROR-20260525-config-sync-category-alias-smoke.md`](ERROR-20260525-config-sync-category-alias-smoke.md) — 配置同步分类解析隐式小写兼容与 smoke 部分草稿导致单独运行失败
- [`ERROR-20260602-runtime-test-card-visual-output.md`](ERROR-20260602-runtime-test-card-visual-output.md) — 运行时测试卡片样例自身覆盖被测 CSS，且结果摘要暴露值对象地址

**共性教训**：配置保存必须先验证再写入，失败时回滚内存值；渲染上下文必须完整传递所有运行时适配器；Forge 配置分类查询不能用会隐式创建分类的 API 代替存在性检查，历史分类兼容必须显式声明 alias。

---

## 事件系统类（合并，2 条）

事件传播、默认行为与生命周期时序。

- [`ERROR-20260521-key-prevent-default-default-action.md`](ERROR-20260521-key-prevent-default-default-action.md) — 键盘事件 preventDefault 后默认 click 行为仍触发
- [`ERROR-20260506-client-command-gui-open-timing.md`](ERROR-20260506-client-command-gui-open-timing.md) — 客户端命令直接开屏被聊天关闭覆盖（生命周期时序）

**共性教训**：默认行为必须在事件传播完成后检查 `isDefaultPrevented()` 再执行；GUI 打开必须延迟到当前帧结束后。

---

## 网络生命周期类（2 条）

- [`ERROR-20260523-net-client-handshake-net-handler-race.md`](ERROR-20260523-net-client-handshake-net-handler-race.md) — 客户端能力握手在 NetHandler 构造期反查全局 NetHandler 导致连接崩溃
- [`ERROR-20260523-net-fml-pipeline-first-login-race.md`](ERROR-20260523-net-fml-pipeline-first-login-race.md) — 首次连接时 Qz 能力握手早于 FML connection-established 语义导致登录握手竞态

**共性教训**：早期 mixin 已拿到的生命周期对象应优先直接传递或缓存，不能在构造期依赖全局单例反查；跨模组网络协议握手必须等 FML connection-established 语义成立后再发送。

---

## 其他（1 条）

- [`ERROR-20260426-powershell-git-commit-quoting.md`](ERROR-20260426-powershell-git-commit-quoting.md) — PowerShell git commit 消息转义错误导致提交失败

**教训**：PowerShell 中 git commit 消息含特殊字符时必须用单引号包裹或正确转义。
