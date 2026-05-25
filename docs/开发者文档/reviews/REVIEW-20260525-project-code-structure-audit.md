# 全项目代码结构深度审查

## 审查信息

- 审查日期：2026-05-25
- 审查主题：全项目主代码结构、复用性、死代码与长期维护风险
- 审查范围：`src/main/java/**` 全部模块，覆盖 UI、font、net、config、client、mixin、internal/devtools 等
- 审查视角：代码复用性、低引用/死代码、职责膨胀、包依赖方向、作者 API 泄漏、文档与实现漂移
- 明确不做：本次只沉淀审查结论，不直接重构源码；旧 `REVIEW-20260520-ui-framework-structure-audit.md` 作为背景，但所有结论以当前源码重新扫描为准

## 核实方法

- 统计 `src/main/java/club/heiqi/uilib` 下 Java 文件数、模块行数、大文件排行。
- 搜索 `TODO/FIXME/HACK/遗留/兼容/未实现/@Deprecated/__`、宽泛异常捕获、反射桥、全局单例与 fallback 语义。
- 对示例/诊断入口、远程页面/HUD、配置模板、网络门面、字体服务与输入宿主耦合做人工抽样阅读。
- 对稳定 API 清单、内部 API 说明与当前源码做一致性核对。

## 现状基线

当前主代码约 445 个 Java 文件、约 8 万行。按一层模块粗分：

| 模块 | 文件数 | 行数 |
|---|---:|---:|
| `ui` | 317 | 60981 |
| `font` | 44 | 6526 |
| `net` | 54 | 5334 |
| `config` | 9 | 3544 |
| `internal` | 6 | 2407 |
| `mixin` | 6 | 392 |
| `client` | 4 | 316 |
| 根包与 `gl` | 5 | 389 |

当前超千行或接近超千行的热点仍然较多：

| 文件 | 行数 | 主要风险 |
|---|---:|---|
| `ui/style/cascade/UiStyleDeclaration.java` | 2490 | 样式声明 shotgun surgery 仍未完全消除 |
| `config/ForgeConfigTemplateScreen.java` | 2105 | 配置模板页面、编辑器绑定、保存事务、主题与文案混合 |
| `ui/screen/example/UiAnimationCapabilityShowcaseDocumentPageController.java` | 1981 | 大型诊断页混入主产物 |
| `ui/screen/example/HtmlLikeBrowserSemanticsShowcaseDocumentPageController.java` | 1701 | 大型诊断页混入主产物 |
| `ui/screen/example/HtmlLikeSmokeDocumentPageController.java` | 1631 | 大型诊断页混入主产物 |
| `internal/devtools/NetRuntimeSelfChecks.java` | 1259 | 端点注册、用例编排、远程页面/HUD HTML 构造混合 |
| `ui/document/HtmlLikeDocumentWidget.java` | 1228 | 已拆分但仍是核心运行时聚合点 |
| `ui/render/UiMainLayerSnapshotService.java` | 1177 | GL 捕获、atlas、tile 策略仍集中 |
| `ui/layout/DocumentLayoutEngine.java` | 1176 | 布局调度与多个 helper 的共享上下文仍集中 |
| `ui/dom/UiDocument.java` | 1172 | 作者 API 与内部 runtime bridge 混合 |
| `ui/remote/RemoteHtmlDocumentParser.java` | 1135 | HTML/CSS/Form 安全子集解析集中 |
| `ui/animation/DocumentAnimationRuntimeState.java` | 1069 | 动画运行态语义集中 |
| `ui/hud/UiHudDocumentHost.java` | 1045 | HUD 注册、渲染、输入、布局兜底混合 |
| `ui/control/DocumentTextAreaControl.java` | 1019 | 多行输入控件复杂度接近独立子系统 |

测试侧约 96 个 Java 文件、约 2.7 万行，主要集中在 UI（约 2.4 万行）。这说明核心行为有较强回归保护，但也意味着 UI 之外的 `font/net/config/internal` 相比主代码体量，测试密度明显更低。

## 综合结论

项目的主线设计仍然成立：对外入口围绕 `UiDocumentScreens`、远程页面/HUD 与 `NetService` 收敛；UI 侧已经完成一轮明显的包结构和 god class 拆分；网络层也坚持了 route/key + content body 的协议身份，避免了 Java 类消息膨胀。

新的主要问题不是“架构方向错误”，而是**内部能力扩张后缺少第二层复用边界**：诊断页、配置模板、远程页面/HUD、网络自检、样式声明、HUD 宿主各自长成了小型子系统，但仍有不少逻辑留在单一门面或单一页面类中。短期可继续维护；中长期若继续加能力，会出现新增功能要同时改大文件、文档 API 清单跟不上源码、诊断代码影响主产物可读性的风险。

本轮没有发现可以无脑删除的大块生产死代码；更明显的是“诊断/示例专用代码仍在主产物里”和“内部 `public` API 以文档约束而非编译边界约束”。

## 问题清单

### P0 — 低风险高收益

#### P0-1 LTS 稳定 API 清单与源码存在漂移

- `docs/使用文档/v4.x-LTS-稳定API清单.md` 两处写的是 `UiStyleColor`，当前源码实际类型为 `ui.style.values.UiColor`。
- 同一清单写 `DocumentLinkActivationEvent.markHandled()` / `isHandled()` 已删除；当前 `DocumentLinkActivationEvent` 的类注释仍说这两个方法“已映射为别名并标记为废弃”，但源码中已经没有这两个方法。
- 影响：业务作者按稳定 API 清单复制类名会直接编译失败；源码注释与稳定 API 清单互相打架，会削弱 LTS 边界可信度。
- 建议：独立文档/注释修复提交即可，不需要行为变更。把 `UiStyleColor` 改为 `UiColor`；把 `DocumentLinkActivationEvent` 注释改为“旧别名已移除，请使用 preventDefault/isDefaultPrevented”。

#### P0-2 旧 UI 审查索引中的整改摘要已再次过期

- `docs/开发者文档/reviews/README.md` 仍记录部分类拆分后的行数，例如 `HtmlLikeDocumentWidget` “主类降至 999 行”、`DocumentLayoutEngine` “主类降至 965 行”。
- 当前重新计数显示：`HtmlLikeDocumentWidget` 1228 行、`DocumentLayoutEngine` 1176 行，且 `UiStyleDeclaration` 已到 2490 行。
- 影响：索引页原本承担“长期指针 + 当前摘要”的职责，过期数字会误导后续 Agent 对剩余风险的优先级判断。
- 建议：不要继续在旧审查条目里追加流水；本报告作为新的当前基线，索引摘要只保留“历史整改已完成但当前仍有新热点”。

### P1 — 影响维护效率的结构问题

> 整改状态（2026-05-25）：P1-1 ~ P1-4 已完成。诊断页迁入 `internal.devtools.pages`，库存概览模型迁入 `ui.inventory`；远程页面与远程 HUD 共享内部 `RemoteHtmlSessionGateway`；配置模板拆出 `ConfigTemplateDocumentBuilder` 与默认属性绑定类族；网络运行时自检保留 `NetRuntimeSelfChecks` 门面，端点注册、用例执行、远程 smoke 页面构造分别拆入包内协作者。
>
> 下列 P1 条目保留原审查发现，最新状态以上方整改状态为准。

#### P1-1 诊断/示例页仍有约 1 万行进入主产物

- `ui/screen/example` 当前 17 个 Java 文件，合计约 10315 行。
- 三个最大页面分别为：`UiAnimationCapabilityShowcaseDocumentPageController` 1981 行、`HtmlLikeBrowserSemanticsShowcaseDocumentPageController` 1701 行、`HtmlLikeSmokeDocumentPageController` 1631 行。
- 这些页面主要由 `InternalDiagnosticScreenRegistry` 和 `/qzuilib test` 诊断路径使用，但类本身是 `public final`，并且 `client/MinecraftInventoryOverviewModel` 还直接 import `ui.screen.example.InventoryOverviewModel` / `InventoryOverviewSlotContentProvider`。
- 影响：主产物浏览成本、编译体积和 public 类型表面持续膨胀；示例 model 被客户端真实适配器引用，导致“example”包不再只是 example。
- 建议：分两步处理：先把 `InventoryOverviewModel` / `InventoryOverviewSlotContentProvider` 迁到 `ui.inventory` 或 `internal.devtools` 的明确边界；再评估把大型诊断页放到独立 `devtools` source set、可选子模块或至少 `internal.devtools.pages` 包。

#### P1-2 远程页面与远程 HUD 的会话/Stream/提交逻辑重复

- `RemoteDocumentPages` 与 `RemoteHudOverlays` 都维护 `SERVER_SESSIONS`、`SESSION_TTL_MILLIS`、`STREAM_MAX_BYTES`、`sha256Hex`、`decodeOpenOffer`、`decodeSubmitPayload`、Stream 拉取、player 校验、表单提交分发、反射调用客户端桥等流程。
- HUD 版本多了 dismiss 与 overlay key，但大部分 session 生命周期和 HTML Stream 逻辑与页面版本相同。
- 影响：安全校验、TTL、header、hash 校验、异常日志和测试用 reset 的修复很容易漏改一边；远程 HTML 能力继续扩展时会产生双倍改动。
- 建议：抽内部 `RemoteHtmlSessionGateway` 或 `RemoteHtmlSessionStore`，共享 session、hash、Stream response、player 校验和过期清理；页面/HUD 只保留 channel id、payload 字段和提交事件构造差异。

#### P1-3 配置模板已经从“页面模板”长成独立配置编辑器子系统

- `ForgeConfigTemplateScreen` 2105 行，包含 DOM 构建、分类解析、属性绑定选择、保存事务、按钮状态、`Spec`、`Theme`、`TextSet`、`CategorySpec`、`PropertyBinding` 及多种绑定子类。
- `ForgeConfigTemplatePropertyDrafts` 已经把校验/写回/rollback 抽出去，这是正确方向；但 UI 绑定层和页面布局层仍在同一文件中。
- `FontSortOrderControl` 997 行，本身已经是复杂控件，和配置模板扩展点高度绑定。
- 影响：新增一种属性编辑器或调整配置页布局时，容易同时触碰页面结构、保存逻辑和绑定状态；配置模板的公共扩展点会越来越难稳定。
- 建议：优先抽 `ConfigPropertyBinding` 顶级类族和 `ConfigTemplateDocumentBuilder`；`ForgeConfigTemplateScreen` 保留生命周期、保存协调和对外 `Spec`。

#### P1-4 网络运行时自检类承担了过多职责

- `NetRuntimeSelfChecks` 1259 行，同时做：注册 10+ Channel/Fetch/Stream/Store 端点、构造 pending future、运行用例、构造远程页面/HUD HTML、处理提交回调、超时调度。
- `NetSelfCheckPage` 581 行，UI 列表与执行编排也绑定较紧。
- 影响：自检本身非常有价值，但继续加网络场景会让内部诊断代码比被诊断对象更难读；远程页面/HUD smoke HTML 字符串不利于复用和局部验证。
- 建议：拆成 `NetSelfCheckRegistry`、`NetSelfCheckRunner`、`RemoteSelfCheckPages` 三类；页面类只负责展示和触发。

### P2 — 需要分阶段推进的重构

> 整改状态（2026-05-25）：P2-1 ~ P2-4 已完成。`UiStyleDeclaration`
> 已以 paint-only 属性族引入 `StyleDeclarationSlot<T>` 试点，先移除
> `opacity/backgroundColor/borderColor/textColor` 与声明表的双份状态；UI 核心完成第二轮低风险拆分，
> 包括 HUD 渲染流水线下沉到 `UiHudRenderPipeline`、DOM 查询下沉到 `DocumentQuerySupport`、
> TextArea 文本规范化/索引辅助下沉到 `DocumentTextAreaTextSupport`；`NetService` 保持对外门面不变，
> 入站信封分发、Stream 下载/取消状态、Store 访问控制发送分别拆入 `NetEnvelopeDispatcher`、
> `NetStreamDownloadRegistry`、`NetStoreSender`；字体 mixin fallback 重复逻辑收口到
> `FontRendererFallbackInvoker`。
>
> 下列 P2 条目保留原审查发现，最新状态以上方整改状态为准。

#### P2-1 `UiStyleDeclaration` 的类型化字段与 `EnumMap` 过渡态仍然双轨

- 当前 `UiStyleDeclaration` 同时维护大量类型化字段和 `EnumMap<UiStyleProperty, Object> declaredValues`。
- 旧审查中 P2-6 指出的 “每加一个 CSS 属性需要多点同改” 已经部分缓解，但从 2490 行体量看，声明层仍是样式系统最大维护热点。
- 影响：新增属性时仍可能出现 setter、getter、clear、keyword、important、computed style 消费不同步；`ComputedStyle` 766 行也会跟随膨胀。
- 建议：不要一次性大改。按属性族分批迁移到统一 `StyleDeclarationSlot<T>` 或属性描述符表，先从低风险 paint-only 属性做试点，再覆盖 layout 属性。

#### P2-2 UI 核心大类拆分后出现“第二轮再聚合”

- 当前仍超过或接近千行的 UI 核心包括 `HtmlLikeDocumentWidget` 1228、`UiMainLayerSnapshotService` 1177、`DocumentLayoutEngine` 1176、`UiDocument` 1172、`DocumentAnimationRuntimeState` 1069、`UiHudDocumentHost` 1045、`DocumentTextAreaControl` 1019。
- 这不是旧审查的简单残留：部分文件曾经下降过，但后续能力继续扩展又增长回来。
- 影响：说明拆分边界还停留在“把明显 helper 拿出去”，下一层需要按运行时状态、宿主生命周期、控件输入模型继续拆。
- 建议：按真实变更频率拆，不按行数机械拆。优先拆 `UiHudDocumentHost` 的 input/render/registration 三段和 `DocumentTextAreaControl` 的文本模型/视图/事件处理，再评估布局与 snapshot 服务。

#### P2-3 `NetService` 作为门面同时承载协议路由、发送、Stream 下载与 Store 访问控制

- `NetService` 当前 898 行，不算超千行，但职责跨度大：注册表、transport bootstrap/freeze、Envelope dispatch、Fetch/Stream/Store 发送、Stream 分片下载、主线程队列桥接、握手和断连清理。
- 影响：网络层现在是对外稳定入口，一旦继续加入双向 Fetch、更多传输适配或监控指标，所有行为都会压进同一个门面。
- 建议：保持 `NetService` API 不变，内部拆 `NetEnvelopeDispatcher`、`NetStreamDownloadRegistry`、`NetStoreSender`；门面只负责注册、bootstrap 和委派。

#### P2-4 字体服务整体边界合理，但生命周期语义仍依赖单例门面

- `FontService` 459 行，体量不大，但集中持有 registry、matcher、glyph page、dispatcher、layout service、batch renderer、shader、reload debouncer 与 render thread 判定。
- `MixinFontRenderer` 对 9 个 FontRenderer 方法重复包 try/catch/fallback，并依赖一次性失败日志。
- 影响：字体服务现在可维护，但所有生命周期状态都挂在单例上；未来如果支持多字体族、资源包热切换细分或多渲染上下文，单例内部状态会成为扩展阻力。
- 建议：短期只抽 `FontRendererFallbackInvoker` 消除 mixin 重复；中期再考虑把 reload/session 状态独立为 `FontRuntimeSession`。

### P3 — 长期设计债

> 整改状态（2026-05-25）：P3-1 ~ P3-4 已收口。P3-1 按当前兼容策略保持 public
> `__` 方法不动，仅在稳定 API 清单中明确其属于内部协作的君子协定：公开出来方便特殊宿主、
> 兼容层或诊断路径使用，但不保证未来稳定。P3-2 已通过 `UiHostInputCaptureParticipant`
> 与 `UiManagedInputScreen` 收口 input 包对 HUD / `BaseScreen` 的反向 import。P3-3 已为
> `DocumentRemoteImageCache` 拆分 `clear()` 与 JVM 退出阶段 `shutdown()`，并允许 shutdown
> 后按需重建下载线程池。P3-4 已在使用文档与 Javadoc 中强调 `DocumentCustomRenderer` /
> `CUSTOM` 只是宿主级逃生口，普通业务表面应优先走标准 DOM / 样式 / paint command。
>
> 下列 P3 条目保留原审查发现，最新状态以上方整改状态为准。

#### P3-1 内部 `public __` API 仍靠文档约束而非编译边界约束

- 当前仍存在 `UiDocument.__showTopLayerElement`、`__hideTopLayerElement`、`__getTopLayerElements`、`__isTopLayerElement`、`__createPseudoElementRuntime`、`__setInteractionRuntime`、`__dispatchLinkActivation`，以及 `ElementNode.__getElementUid`、`DocumentNode.__appendGeneratedChild`。
- 稳定 API 清单把它们归为内部，但 Java 层面仍是 public。
- 影响：第三方一旦直接调用，未来收口会变成兼容负担；`__` 前缀只能提示风险，不能阻止依赖。
- 建议：新能力不要再增加 public `__` 方法；后续用 `dom.internal` 访问器或 package-private runtime bridge 替换，必要时保留 deprecated facade。

#### P3-2 input 包仍存在对宿主层的反向依赖

- `UiHostInputCoordinator` 直接 import `ui.hud.UiHudDocumentHost`。
- `UiNativeTextInputInspector` 直接 import `ui.screen.BaseScreen`。
- 旧 UI 结构审查已经指出 input 应是被 screen/hud 调用的下游服务；当前问题仍存在。
- 影响：输入基础设施难以独立复用，screen/hud 任一侧生命周期变化都会影响 input 包。
- 建议：让 screen/hud 各自提供 `InputCaptureParticipant` 或 `NativeTextInputProvider`，input 包只消费接口。

#### P3-3 远程图片缓存的 `shutdown()` 语义与单例复用存在隐患

- `DocumentRemoteImageCache` 是进程级单例，内部 `ExecutorService` 在构造时固定创建；`shutdown()` 会永久关闭 executor。
- 稳定 API 清单将 `shutdown()` 标为不稳定但对外可见，源码注释又提到“客户端断连或 JVM 退出阶段”使用。
- 影响：如果后续按注释在断连阶段调用，重连后 `request()` 可能向已关闭 executor 提交任务；当前 `ClientProxy` 只在 JVM shutdown 调用，暂未触发。
- 建议：明确 `shutdown()` 只允许 JVM 退出，或让 cache 在 shutdown 后可重新创建 executor；断连清理应拆成 `clear()`。

#### P3-4 诊断页和文档页面仍有自定义 renderer 穿透到渲染后端

- 示例页与性能页中仍能直接使用 `DocumentCustomRenderer` 与 `UiRenderContext`。
- 旧审查已指出自定义渲染会穿透 paint command 中立性；当前在诊断页里使用可以接受，但它仍是对外可见能力。
- 影响：作者层若依赖该路径，会绕过 HTML-like 标准绘制与测试替身，增加渲染后端兼容成本。
- 建议：使用文档继续强调普通业务页面不要使用 CUSTOM；长期用自定义 paint command 或 host renderer registry 替代直接拿 `UiRenderContext`。

## 改进优先级建议

| 优先级 | 建议动作 | 风险 | 备注 |
|---|---|---|---|
| P0 | 修正稳定 API 清单与 `DocumentLinkActivationEvent` 注释漂移 | 低 | 文档/注释级提交 |
| P0 | 用本报告替代旧索引里的过期当前基线 | 低 | 索引只保留摘要，避免继续追加长流水 |
| P1 | 抽远程页面/HUD 共享 session + Stream gateway | 中 | 已完成，页面/HUD 共享内部远程 HTML session gateway |
| P1 | 拆配置模板绑定类族与文档构建器 | 中 | 已完成，保留公开 `Spec` 入口 |
| P1 | 拆网络自检注册/运行/HTML 构造 | 低 | 已完成，保留 `NetRuntimeSelfChecks` 门面 |
| P2 | 分批推进 `UiStyleDeclaration` 属性描述符化 | 高 | 已完成 paint-only 属性族试点，后续新增属性按 slot 模板延展 |
| P2 | 对 HUD、TextArea、UiDocument 做第二轮按状态/生命周期拆分 | 中 | 已完成 HUD 渲染、TextArea 文本支持、DOM 查询拆分 |
| P2 | 拆 `NetService` 内部协作者 | 中 | 已完成 envelope dispatch、Stream 下载注册器、Store sender 下沉 |
| P2 | 抽字体 mixin fallback 调用器 | 低 | 已完成 `FontRendererFallbackInvoker` 收口 |
| P3 | 收口 public `__` 内部 API、input 反向依赖、远程图片缓存生命周期与 CUSTOM 使用边界 | 中 | 已完成兼容性收口；`__` 仍按内部君子协定公开 |

## 不建议立即做的事

- 不建议为了“瘦身”直接删除诊断页本身：这些页面已收口到内部开发工具包，仍是 `/qzuilib test` 和大量回归测试的主要载体。
- 不建议一次性重写 `UiStyleDeclaration` / `ComputedStyle`：涉及样式、控件、动画、布局和测试，应该以属性族为单位迁移。
- 不建议把 `NetService` 对外 API 拆散：当前门面心智是优点，应该只拆内部协作者。

## 验证说明

- 本报告是文档审查，不修改生产源码。
- 建议文档提交前运行 `git diff --check`。
- 因本次只新增/更新 Markdown 文档，默认不运行完整 `test`；若后续启动任一 P1/P2 重构，应至少运行 `compileJava` 与相关模块单元测试。
- P1 整改完成后已运行 `$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache test`，结果通过。
