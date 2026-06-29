# 错误记录

本文件作为错误记录索引，按类型分组管理。详细问题分析存放在 `docs/开发者文档/errors/` 目录下。

> 2026-05-21 合并整理：将 49 条历史错误按类型归并为 12 个分组，便于快速定位同类问题。

---

## GL/渲染状态泄漏类（合并，7 条）

OpenGL 状态未正确保存/恢复，导致后续绘制异常。

- [`ERROR-20260426-ui-rounded-fill-cull-state.md`](ERROR-20260426-ui-rounded-fill-cull-state.md) — 宿主世界渲染遗留 3D GL 状态导致 rounded fill 色块不可见
- [`ERROR-20260501-opacity-context-framebuffer-binding.md`](ERROR-20260501-opacity-context-framebuffer-binding.md) — Opacity FBO 首次创建时 framebuffer 绑定泄漏导致屏闪
- [`ERROR-20260503-inventory-deferred-item-state-leak.md`](ERROR-20260503-inventory-deferred-item-state-leak.md) — 背包 deferred 物品批次回放间 GL 状态/depth 未重置
- [`ERROR-20260513-font-decoration-gl-state-leak.md`](ERROR-20260513-font-decoration-gl-state-leak.md) — 文本装饰后 GL 状态泄漏导致后续贴图透明区域发白
- [`ERROR-20260515-font-icon-alpha-state-leak.md`](ERROR-20260515-font-icon-alpha-state-leak.md) — 字体接管后图标透明状态污染（blend/alpha 通道不一致）
- [`ERROR-20260517-font-post-render-hardcoded-state.md`](ERROR-20260517-font-post-render-hardcoded-state.md) — 字体渲染后硬编码 GL 状态破坏调用方自定义状态
- [`ERROR-20260606-host-image-missing-texture-fallback.md`](ERROR-20260606-host-image-missing-texture-fallback.md) — 宿主图片缺失纹理未预检导致 Minecraft 默认紫黑 missing texture 泄漏到 UILib fallback

**共性教训**：任何跨越渲染边界的绘制入口必须用真实 GL 状态保存/恢复包裹完整生命周期，不能用硬编码 `glEnable`/`glDisable` 模拟结束状态；需要 UILib 自定义 fallback 的宿主资源必须在绑定前完成可用性预检。

---

## 圆角/裁剪渲染类（合并，4 条）

Stencil、clip、圆角相关的渲染问题。

- [`ERROR-20260419-rounded-clip-runtime-stencil.md`](ERROR-20260419-rounded-clip-runtime-stencil.md) — Rounded structural clip 开启 stencil 后子树内容整体不可见
- [`ERROR-20260419-surface-clip-coupling.md`](ERROR-20260419-surface-clip-coupling.md) — border-radius 外观与 descendant clip 语义耦合导致内容消失
- [`ERROR-20260428-smoke-absolute-probe-clipped.md`](ERROR-20260428-smoke-absolute-probe-clipped.md) — Absolute 元素被 DOM 父级 overflow:hidden 裁剪不可见
- [`ERROR-20260518-rounded-command-uniform-zero.md`](ERROR-20260518-rounded-command-uniform-zero.md) — 分角圆角 uniform 为 0 退化、box-shadow/clip 多处回归风险

**共性教训**：圆角裁剪必须区分"外观装饰"与"后代裁剪"两个独立语义；脱流元素的裁剪应相对 containing block 而非 DOM 父级。

---

## 字体系统类（合并，5 条）

字体加载、渲染、重载与 GL 资源管理。

- [`ERROR-20260515-font-reload-generation-barrier.md`](ERROR-20260515-font-reload-generation-barrier.md) — 字体重载缺少代际屏障，新旧字形混合显示
- [`ERROR-20260515-splash-font-shader-reload-race.md`](ERROR-20260515-splash-font-shader-reload-race.md) — Splash 线程与主线程字体 shader 重载竞态崩溃
- [`ERROR-20260519-font-style-drawtext-recursion.md`](ERROR-20260519-font-style-drawtext-recursion.md) — 字体样式 drawText 两个重载互相调用导致 StackOverflow
- [`ERROR-20260602-transform-text-deferred-batch.md`](ERROR-20260602-transform-text-deferred-batch.md) — Transform 内文本延迟批处理绕过父元素矩阵
- [`ERROR-20260603-font-shutdown-hook-gl-dispose.md`](ERROR-20260603-font-shutdown-hook-gl-dispose.md) — JVM shutdown hook 非渲染线程释放字体 GL 资源导致 native 崩溃
- [`ERROR-20260625-glyph-coordinate-system-mismatch.md`](ERROR-20260625-glyph-coordinate-system-mismatch.md) — 烘焙字号57.6与渲染缩放分母64不自洽，污染quad尺寸+x/y定位，致真机四现象（偏上/偏右/缺边/偏小）；fontScale=0.9 是旧补丁hack，违反业界"烘焙字号=渲染缩放分母"约定
- [`ERROR-20260625-trim-getstringwidth-fontsizepx-mismatch.md`](ERROR-20260625-trim-getstringwidth-fontsizepx-mismatch.md) — trimRawStringToWidth无参重载错用DEFAULT_FONT_SIZE_PX=18，与getStringWidth的9px空间2倍口径不一致，致trim返回空串；测试在4.0主分支上一直失败

**共性教训**：字体异步管线必须以 runtimeVersion 隔离代际；多线程字体入口必须在运行时锁下串行化；方法重载链必须有明确终止点；跨命令字体批处理必须继承或显式携带当前视觉上下文；字体 GL 资源创建、重载和释放都必须受渲染线程边界保护；烘焙字号与渲染缩放分母必须同一常量，禁止独立系数分别缩放；同一 API 的 trim/wrap/measure 重载必须保持宽度口径一致，无 fontSizePx 重载不应默认用更大的 UI 像素字号。

---

## 布局引擎类（合并，9 条）

Flex、block、positioned 定位与尺寸计算。

- [`ERROR-20260512-flex-column-auto-cross-size-zero.md`](ERROR-20260512-flex-column-auto-cross-size-zero.md) — flex column 非 stretch 子项 auto 宽度被错误压成 0
- [`ERROR-20260513-flex-column-text-block-overlap.md`](ERROR-20260513-flex-column-text-block-overlap.md) — flex column 文本块未按真实换行高度参与兄弟排布导致压叠
- [`ERROR-20260511-hud-demo-panel-overflow.md`](ERROR-20260511-hud-demo-panel-overflow.md) — HUD 面板子控件溢出（content-box 语义下百分比宽度预期行为）
- [`ERROR-20260427-custom-paint-content-box.md`](ERROR-20260427-custom-paint-content-box.md) — CustomRenderer 参数传入 padding box 而非 content box
- [`ERROR-20260518-browser-semantics-audit.md`](ERROR-20260518-browser-semantics-audit.md) — 全面审查发现 28 处不符合浏览器语义的实现
- [`ERROR-20260523-form-control-browser-semantics.md`](ERROR-20260523-form-control-browser-semantics.md) — 表单控件渲染缺少 flex 匿名文本与 textarea 内容盒语义
- [`ERROR-20260602-form-control-input-textarea-caret.md`](ERROR-20260602-form-control-input-textarea-caret.md) — input 空值缺少原生编辑高度且 textarea 光标混用文档/屏幕坐标
- [`ERROR-20260615-flex-anonymous-item-layout-self-pollution.md`](ERROR-20260615-flex-anonymous-item-layout-self-pollution.md) — flex 匿名文本项布局期 setAttribute 自污染 layoutVersion 致配置页每帧重排（含 UI 卡顿诊断可复用路线）
- [`ERROR-20260617-dom-coarse-subtree-dirty-marking.md`](ERROR-20260617-dom-coarse-subtree-dirty-marking.md) — DOM 层粗粒度结构标脏：列表项增删经容器 append/removeChild 污染未变兄弟子树，layout 层 version 闸门判定复用失败致真实重算（先验地基债，I7
  在列表增删场景未达成，forEach 复用首次暴露）。**【已还清 2026-06-18】** 方案 X（结构变更只标容器自身 self+subtree+冒泡、不递归整子树，受影响兄弟由 layout 闸门按需捕获）；oracle 否决原方向 1（reconcileChildren 批量 API 过度设计且分模式标脏撞 I6），根因是无条件递归非逐次提交，<10 行根除，回归锚点已翻转为正向
  I7 断言

**共性教训**：flex column 的交叉轴 auto 尺寸必须走固有内容宽度测量；盒模型 API 必须明确传递 content box 还是 padding box；控件渲染异常优先补齐通用 HTML-like/CSS 语义；布局/样式/命中等只读流程绝不能改文档失效版本，布局期创建的临时元素必须走静默写入入口；结构标脏粒度应区分「容器需重排子项」与「未变兄弟子树仍干净」，
列表协调应通过批量提交携带复用信息避免无条件全子树标脏。

---

## 滚动/交互类（合并，7 条）

滚动条、hover 状态、焦点与拖拽。

- [`ERROR-20260427-html-nested-scrollbar-idle-cover.md`](ERROR-20260427-html-nested-scrollbar-idle-cover.md) — 嵌套滚动条空闲时仍显示遮挡内容
- [`ERROR-20260427-html-root-scroll-focus.md`](ERROR-20260427-html-root-scroll-focus.md) — 点击 HTML-like 元素后旧页面壳随机滚动跳动
- [`ERROR-20260427-html-scrollbar-missing.md`](ERROR-20260427-html-scrollbar-missing.md) — 根视口滚动后缺少可见滚动条
- [`ERROR-20260509-html-hover-stale-after-scroll.md`](ERROR-20260509-html-hover-stale-after-scroll.md) — 滚动后 hover 状态未同步更新
- [`ERROR-20260518-html-drag-right-bottom-anchor-jump.md`](ERROR-20260518-html-drag-right-bottom-anchor-jump.md) — 浮窗首次拖拽时因 right/bottom 锚点跳位
- [`ERROR-20260602-textarea-stale-visual-line-cache.md`](ERROR-20260602-textarea-stale-visual-line-cache.md) — textarea 删除换行后复用过期视觉行缓存导致运行时崩溃
- [`ERROR-20260614-uitest-top-layer-option-hit.md`](ERROR-20260614-uitest-top-layer-option-hit.md) — UiTest select top-layer option 自动断言直接点静态边界导致命中失败
- [`ERROR-20260624-scene-scroll-migration-coverage-test-debt.md`](ERROR-20260624-scene-scroll-migration-coverage-test-debt.md) — 滚动迁移 fixer 漏迁 ObjectField host（侦察「推测未读」被当不存在）+ 旧测试「错对错」迁移后暴露

**共性教训**：滚动偏移变化后必须重新命中测试更新 hover；拖拽起始必须先将锚点归一化为 left/top；top-layer、弹层和变换后元素的自动断言应以真实 hit-test 命中为准，不能只点元素静态边界中心。

---

## HUD 系统类（合并，8 条）

HUD 输入分发、显示控制与生命周期。

- [`ERROR-20260511-hud-input-unregister-cme.md`](ERROR-20260511-hud-input-unregister-cme.md) — HUD 输入分发期间即时注销触发 ConcurrentModificationException
- [`ERROR-20260511-hud-overlay-click-through-and-missing-drag.md`](ERROR-20260511-hud-overlay-click-through-and-missing-drag.md) — HUD 交互浮窗点击穿透下层容器且缺少拖拽能力
- [`ERROR-20260513-hud-deferred-post-main-double-drain.md`](ERROR-20260513-hud-deferred-post-main-double-drain.md) — HUD deferred post-main 双重 drain 导致回放丢失
- [`ERROR-20260517-hud-main-menu-title-screen-leak.md`](ERROR-20260517-hud-main-menu-title-screen-leak.md) — HUD 在主页类屏幕上误显（主页识别名单不完整）
- [`ERROR-20260523-remote-hud-danmaku-drag.md`](ERROR-20260523-remote-hud-danmaku-drag.md) — 远程 HUD 弹幕文字不可见、DIALOG 额外父容器与首次拖拽跳位
- [`ERROR-20260523-remote-hud-select-popup-click-through.md`](ERROR-20260523-remote-hud-select-popup-click-through.md) — 远程 HUD select 下拉选项点击穿透到下方按钮或原生界面
- [`ERROR-20260525-hud-input-interface-static-overload.md`](ERROR-20260525-hud-input-interface-static-overload.md) — HUD 输入桥接口方法与静态辅助方法同名导致测试编译重载解析错误
- [`ERROR-20260610-hud-menu-native-scan.md`](ERROR-20260610-hud-menu-native-scan.md) — 多人游戏菜单页仍触发 HUD 原生文本框反射深扫，Java 21 下撞到 JDK 模块封装崩溃

**共性教训**：HUD 注册表遍历必须基于快照或等效防御；多层 HUD 输入只路由最上层命中层；弹出型控件要按顶层语义处理；主页黑名单需覆盖第三方主页类；非交互菜单页不能执行 HUD 输入探测或反射深扫。

---

## 动画系统类（合并，4 条）

Keyframe、transition 与 opacity 效果。

- [`ERROR-20260501-keyframe-radius-clamp-jump.md`](ERROR-20260501-keyframe-radius-clamp-jump.md) — Keyframe 圆角 clamp 跳变 + fill-mode 与 transition 冲突
- [`ERROR-20260501-smoke-keyframe-opacity-flicker.md`](ERROR-20260501-smoke-keyframe-opacity-flicker.md) — Keyframe opacity 阶段触发 FBO 路径切换导致屏闪
- [`ERROR-20260522-animation-transform-opacity-clipping.md`](ERROR-20260522-animation-transform-opacity-clipping.md) — 展示页 transform 与 opacity 混合导致内容裁切
- [`ERROR-20260429-backdrop-stale-snapshot-reuse.md`](ERROR-20260429-backdrop-stale-snapshot-reuse.md) — Backdrop 旧快照过度复用导致采样错层

**共性教训**：动画属性 clamp 必须在归一化阶段完成，不能在插值后跳变；opacity 路径切换必须在帧边界而非动画中途。

---

## Gradle/构建环境类（合并，11 条）

Toolchain、依赖版本、构建配置与运行时类路径。

- [`ERROR-20260424-gradle-zulu8-toolchain.md`](ERROR-20260424-gradle-zulu8-toolchain.md) — Gradle 无法找到 Zulu 8 工具链 + 并发文件锁
- [`ERROR-20260425-idea-runclient21-jbr-toolchain.md`](ERROR-20260425-idea-runclient21-jbr-toolchain.md) — runClient21 解析 JBR 21 工具链失败/卡住
- [`ERROR-20260426-gradle-java8-worker-userpath.md`](ERROR-20260426-gradle-java8-worker-userpath.md) — 中文用户路径导致 Java 8 Worker 启动失败
- [`ERROR-20260518-gradle-parallel-build-race.md`](ERROR-20260518-gradle-parallel-build-race.md) — 并行 Gradle 进程竞争 build 目录导致编译失败
- [`ERROR-20260624-parallel-fixer-gradle-build-race.md`](ERROR-20260624-parallel-fixer-gradle-build-race.md) — 并行 fixer 子代理各自跑 build 导致 class 缓存错乱，误判为新控件代码问题
- [`ERROR-20260601-gradle-gtnhconvention-github-manifest-flaky.md`](ERROR-20260601-gradle-gtnhconvention-github-manifest-flaky.md) — `gtnhconvention` 配置阶段偶发拉取 GitHub manifest 失败
- [`ERROR-20260509-runclient21-angelica-gtnhlib-mismatch.md`](ERROR-20260509-runclient21-angelica-gtnhlib-mismatch.md) — Angelica 与 GTNHLib 版本错配导致 runClient21 崩溃
- [`ERROR-20260519-lwjgl3ify-runtime-compile-classpath-gap.md`](ERROR-20260519-lwjgl3ify-runtime-compile-classpath-gap.md) — lwjgl3ify 运行时类路径与编译类路径不一致
- [`ERROR-20260523-runserver-lwjgl3ify-relauncher.md`](ERROR-20260523-runserver-lwjgl3ify-relauncher.md) — runServer 被 LWJGL3ify relauncher 中止，未进入完整 dedicated server smoke
- [`ERROR-20260608-runclient21-codechicken-mapping-dir.md`](ERROR-20260608-runclient21-codechicken-mapping-dir.md) — CodeChickenLib 映射目录配置指向旧 Gradle 缓存导致 runClient21 弹出 MCP 目录选择窗口
- [`ERROR-20260609-runclient21-gtnh290-modern-java-chain.md`](ERROR-20260609-runclient21-gtnh290-modern-java-chain.md) — GTNH 2.9 beta 下 runClient21 modern Java 链路与第三方 mixin 冲突

**共性教训**：`GRADLE_USER_HOME` 必须显式设置避免中文路径；依赖版本必须锁定一致；并行构建需避免共享 build 目录；被忽略的运行目录配置需要由启动任务按当前环境生成。

---

## JVM 测试环境类（合并，4 条）

纯 JVM 测试中 GL/GuiScreen/字体不可用。

- [`ERROR-20260426-jvm-test-default-font-service.md`](ERROR-20260426-jvm-test-default-font-service.md) — 纯 JVM 测试误触默认字体服务加载 LWJGL 失败
- [`ERROR-20260427-jvm-test-render-context-gl.md`](ERROR-20260427-jvm-test-render-context-gl.md) — 测试 render context 未覆写方法回落到 GL 实现导致失败
- [`ERROR-20260508-jvm-test-guiscreen-static-init.md`](ERROR-20260508-jvm-test-guiscreen-static-init.md) — 纯 JVM 测试实例化 GuiScreen 触发客户端静态初始化崩溃
- [`ERROR-20260513-jvm-test-font-guard-bufferutils.md`](ERROR-20260513-jvm-test-font-guard-bufferutils.md) — FontRenderStateGuard 初始化调用 BufferUtils 导致测试失败

**共性教训**：纯 JVM 测试不要直接实例化继承 `GuiScreen`/`BaseScreen` 的页面类；文本测量相关测试要注入确定性 `TextMeasureService`；任何可能触发 GL/LWJGL 的路径必须有 headless 保护。

---

## 配置/业务页面类（合并，10 条）

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

## 事件系统类（合并，6 条）

事件传播、默认行为与生命周期时序。

- [`ERROR-20260521-key-prevent-default-default-action.md`](ERROR-20260521-key-prevent-default-default-action.md) — 键盘事件 preventDefault 后默认 click 行为仍触发
- [`ERROR-20260506-client-command-gui-open-timing.md`](ERROR-20260506-client-command-gui-open-timing.md) — 客户端命令直接开屏被聊天关闭覆盖（生命周期时序）
- [`ERROR-20260613-lwjgl2-config-text-input.md`](ERROR-20260613-lwjgl2-config-text-input.md) — 非 lwjgl3ify 环境配置页文本框缺少文本输入事件
- [`ERROR-20260618-signal-set-dedup-stale-value.md`](ERROR-20260618-signal-set-dedup-stale-value.md) — Signal.set 去重拿已 flush 旧值比较，吞掉「同帧 set 回帧初值」的写入（中文连打残缺、toggle 抖动/计数器回弹通用 latent bug；去重应移到 flush
  阶段对比帧初值 vs 帧末终值）
- [`ERROR-20260622-scene-text-frame-merge.md`](ERROR-20260622-scene-text-frame-merge.md) — Scene Text 同帧多 TEXT 事件未合并，中文 IME 提交短句时多次受控写入互相覆盖只保留末字
- [`ERROR-20260622-scene-runtime-on-cleanup.md`](ERROR-20260622-scene-runtime-on-cleanup.md) — SceneRuntime.on 的 Javadoc 承诺 Owner cleanup，但实现只委托 inputRouter.on，导致可卸载组件内注册的输入 handler 不随 mount dispose
  自动退订
- [`ERROR-20260622-scene-screen-switch-enqueue.md`](ERROR-20260622-scene-screen-switch-enqueue.md) — Scene 新栈 hub 初版在 drawScreen 内直接 displayGuiScreen，可能在渲染栈内关闭并 dispose 自己；切屏必须走
  UiScreenManager.enqueue 帧外执行

**共性教训**：默认行为必须在事件传播完成后检查 `isDefaultPrevented()` 再执行；GUI 打开必须延迟到当前帧结束后，尤其不能在 `drawScreen` 渲染栈内直接切屏；输入层回归不能只检查键事件，还必须覆盖文本事件来源；reactive 去重必须基于「帧初值 vs 帧末合并终值」在 flush 阶段裁定，绝不能在 set 时拿尚未 flush 的旧值比较；同帧多条
TEXT 应在输入封板层归一为完整文本，不能靠 router 内逐条 flush 或控件 pending 修补；可编辑文本状态不要用 `signal.get()` 读-改-写（flush 前恒旧值），应持即时可变模型 + signal 单向派生；组件级事件绑定必须纳入 Owner cleanup，否则可卸载组件会泄漏 handler。

---

## 网络生命周期类（2 条）

- [`ERROR-20260523-net-client-handshake-net-handler-race.md`](ERROR-20260523-net-client-handshake-net-handler-race.md) — 客户端能力握手在 NetHandler 构造期反查全局 NetHandler 导致连接崩溃
- [`ERROR-20260523-net-fml-pipeline-first-login-race.md`](ERROR-20260523-net-fml-pipeline-first-login-race.md) — 首次连接时 Qz 能力握手早于 FML connection-established 语义导致登录握手竞态

**共性教训**：早期 mixin 已拿到的生命周期对象应优先直接传递或缓存，不能在构造期依赖全局单例反查；跨模组网络协议握手必须等 FML connection-established 语义成立后再发送。

---

## 其他（3 条）

- [`ERROR-20260426-powershell-git-commit-quoting.md`](ERROR-20260426-powershell-git-commit-quoting.md) — PowerShell git commit 消息转义错误导致提交失败
- [`ERROR-20260607-codegraph-mcp-startup.md`](ERROR-20260607-codegraph-mcp-startup.md) — Windows 下 opencode 直接启动 npm `.cmd` shim 且重复传 `--mcp` 导致 CodeGraph MCP 启动失败
- [`ERROR-20260624-skip-review-before-commit.md`](ERROR-20260624-skip-review-before-commit.md) — fixer 完成后跳过独立 review 直接提交，违反"实现完成后必须经一次独立子代理审核"纪律
- [`ERROR-20260626-b6-delegation-and-gl-test-gap.md`](ERROR-20260626-b6-delegation-and-gl-test-gap.md) — B6 FBO 跨 15 文件复杂改动主 Agent 自读自实现违反委派纪律 + reviewer 中断未恢复用"有条件通过"事后追认 + GL 行为零测试覆盖违反 ERROR-20260419 预防措施
- [`ERROR-20260626-font-perframe-info-log-flood.md`](ERROR-20260626-font-perframe-info-log-flood.md) — 字体 per-frame 诊断日志误用 INFO 级且绕过 FontRuntimeDiagnostics 节流，fontRuntimeDebug 开关一开每帧刷屏致 fml-client-latest.log 膨胀 272MB/164 万行
- [`ERROR-20260629-decision-overupgrade-without-due-diligence.md`](ERROR-20260629-decision-overupgrade-without-due-diligence.md) — 决策升级未做尽职调查：双向严父依赖物理不可能（Java 接口契约 compile-time 依赖 + `ConfigScreen extends AbstractSceneHostWidget` 反射化硬点 + 与决策 L41 物理冲突）却升级并提交，revert 后改三档红线 + 扩大豁免

**教训**：PowerShell 中 git commit 消息含特殊字符时必须用单引号包裹或正确转义；Windows 下 opencode MCP 启动 npm 包应优先经 `cmd.exe /d /s /c` 包装，并确认包本身是否已经进入 MCP 模式；fixer → build → test → review → commit 是硬性链路，build/test 通过不等于 review 通过，改动简单不是跳过 review 的借口；oracle 给裁决 ≠ 免派 fixer，跨多文件复杂改动无论是否有裁决实现一律派 @fixer，GL/scissor/stencil/FBO 改动必须显式回答"哪些 GL 行为被 recording 覆盖、哪些只能真机验收"，reviewer/oracle 返回空必须原 task_id 恢复不得事后追认；per-frame/高频热路径日志禁止用 INFO 级，诊断日志必须走统一节流入口（FontRuntimeDiagnostics），周期性统计用时间窗口采样而非前 N 条上限；任何决策升级（尤其包依赖方向这种物理约束）必须先派 Oracle 做可行性评估 + 读已落地代码识别硬点 + 与其他条款做一致性检查，用户确认架构方向意图 ≠ 可行性已验证，不得"用户说啥就改啥"。
