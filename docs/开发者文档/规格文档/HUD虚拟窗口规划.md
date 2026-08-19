# HUD 虚拟窗口规划：去掉特殊编码，HUD 直接使用 scene UI 代码

> 状态：规划草案（待用户确认路线与兼容策略）
> 素材基线：2026-08-20 源码现状（`client/hud` + `ui/hud/api` 共 23 个文件）

## 1. 背景：今天的 HUD 是什么

今天 HUD 是一条专用数据管线，业务方不能直接使用 UI 代码：

```text
业务 mod ── HudSnapshotProvider（行/片段数据协议）──► HudRegistry ──► SceneHudHost
                                                                          │
                                              RetainedHud.createLine/createSpan 手工编译节点树
                                              HudTone.color() 硬编码调色板 / HudTokens 尺寸表
                                              HudLayoutEngine 四角锚定（HUD 专用布局器）
                                                                          ▼
                                              SceneRuntime/SceneLayoutEngine/ScenePaintEngine/Replayer
```

「特殊编码」具体指以下实现，全部位于 `client/hud/SceneHudHost.java`：

| 位置 | 特殊编码 | 说明 |
|---|---|---|
| `RetainedHud.createLine` | 手工拼 row/label/track/fill 四个 SceneNode | 每个 HUD 行 = 写死结构（文本行 + 进度槽） |
| `RetainedHud.createSpan` | 每个 span 一条 `runtime.bindComputed` | 快照字段到节点属性的逐字段手工翻译 |
| `RetainedHud.color` | `HudTone` → ARGB 的 switch 硬编码调色板 | 与 scene 主题/`SceneChromeTokens` 脱节 |
| `HudLayoutEngine` | 四角锚定 + 堆叠专用数学 | 与 `scene.overlay.SceneAnchorResolver` 两套锚定 |
| `HudTokens` | compact/normal 两套硬编码字号行盒表 | HUD 专用 token，scene 无此概念 |

后果：HUD 无法使用 `SceneLabel` 富文本、`SceneButton` 等任何 scene 控件；能表达的内容被钉死在
「行/片段/色调/进度槽」四件套上；新增一种 HUD 形态（如状态图标、小地图框）都必须在
`SceneHudHost` 里再写一套手工模板。

## 2. 目标态：HUD = 锚定在屏幕四角的虚拟窗口

把 HUD 抽象为**虚拟窗口（VirtualWindow）**：一个无 GuiScreen 生命周期、锚定在视口四角、
直接挂载 scene 节点树的主机。业务方用与普通 UI 页面完全相同的代码构建内容：

```java
HudRegistration hud = ClientHudService.getInstance().register(
    HudSpec.builder("example:status").anchor(HudAnchor.TOP_LEFT).build(),
    (rt, window) -> {   // rt = SceneRuntime，与 UI 页面同源
        SceneNode root = SceneNode.column();
        rt.mount(root, SceneLabel.create(rt, new SceneLabel.Props(energyText)));
        rt.mount(root, SceneButton.create(rt, new SceneButton.Props(...)));  // 可选：阶段二交互
        return root;
    });
```

核心变化：

1. **注册协议从「数据快照」变为「窗口工厂」**：provider 不再是每帧返回 `HudSnapshot`，
   而是返回一次性的 scene 节点树；内容变化走 signal（`ReadableSignal`），与 UI 页面同机制。
2. **删除翻译层**：`createLine/createSpan/color()/HudTokens` 手工模板全部下线；
   `HudSnapshot/HudLine/HudSpan/HudTone` 按路线 A 或 B 处置（见 §4）。
3. **复用主机管线**：`SceneHudHost` 不再手写 render 循环，改挂 `SceneFramePipeline` 的
   HUD 变体（或直接复用，多窗口 = 多 root 帧管线），layout/paint/replay/settle 全部同源。
4. **锚定统一**：四角锚定收编为 `SceneAnchorResolver` 的「视口锚点」模式（区别于现有
   trigger 锚定），堆叠由锚点 offset 累积表达；`HudLayoutEngine` 数学迁入 scene.overlay，
   HUD 专用布局器删除。

## 3. 分层设计

| 层 | 现状态 | 目标态 |
|---|---|---|
| 公共 API（`ui.hud.api`） | `ClientHudService.register(spec, HudSnapshotProvider)` | `register(spec, HudWindowFactory)`；spec 保留 anchor/visibility/stackOrder/margin/minWidth/maxWidth，删除 compact 或改为窗口级 token |
| 服务实现（`client.hud`） | `HudRegistry` + `SceneHudHost` 翻译层 | `HudRegistry` 保留（注册表/线程检查/异常隔离是好设计）；`SceneHudHost` 瘦身为「每注册项一个 RetainedWindow：SceneRuntime + 帧管线 + 锚定 overlay」 |
| 帧管线 | 手写 render 循环（parallel） | 复用/派生 `SceneFramePipeline`（HUD 变体：无 inputSource，锚定约束注入） |
| 渲染桥（`client/UiHudRenderListener`） | Forge 事件 + GL 围栏 + 上下文组装 | 保留（Forge 桥、`HudGlStateGuard`、`UiRenderContext` 组装都是合理的 host 边界代码） |
| 缩放 | `ScaledHudBackend` 全方法转发装饰器 | 保留语义，实现收编为 `UiRenderBackend` 的通用 `scaled(float)` 装饰器（backend 层一般化） |
| 主题 | `HudTone` 硬编码 ARGB | 语义色调迁入 scene 主题 token（`SceneChromeTokens` 扩展 HUD tone 组），业务仍只写语义 |

## 4. 兼容路线（需用户决策）

`ui.hud.api` 当前在 v4.x-LTS 稳定 API 清单中（✅ 被动展示、不接收输入）。两条路线：

**路线 A（推荐）：breaking，一步到位**
- 4.9 随新 minor 直接替换注册协议，删除 `HudSnapshot/HudLine/HudSpan/HudTone/CompactHud/TextHud`。
- 迁移成本：已知下游（Qz-Miner）可同步迁移；对外发布面需在实施时按 LTS 变更流程评估影响并公告。
- 符合「去掉特殊编码」的本意，不留双协议维护负担。

**路线 B：过渡期双轨**
- 新增 `register(spec, HudWindowFactory)`；旧 `register(spec, HudSnapshotProvider)` 标记
  `@Deprecated`，内部由「snapshot → 窗口」适配器（把旧翻译层原样降级为兼容适配器）驱动。
- 一个 minor 周期后按计划删除。代价：翻译层代码还要存活一个版本。

## 5. 分阶段里程碑

| 阶段 | 内容 | 验收 | 状态 |
|---|---|---|---|
| H1 窗口内核 | `HudWindowFactory` 注册协议 + `SceneHudHost` 每窗口 `SceneFramePipeline` 帧循环；快照翻译层删除 | scene 代码构建 HUD，空内容整窗隐藏、工厂异常隔离；build 全绿 | ✅ 完成（`f22edc59`） |
| H2 锚定统一 | `SceneAnchorResolver.resolveViewport` 视口锚点模式；`HudLayoutEngine` 删除；堆叠 offset 语义对拍 | 锚定契约测试全量迁移 `SceneAnchorResolverTest` | ✅ 完成（`dcd1fe93`） |
| H3 旧协议下线 | `HudSnapshot` 系删除；HudTone 语义迁 `SceneChromeTokens.HUD_*` token；`ScaledHudBackend` 一般化为 `UiRenderBackend.scaled(float)` | 旧 API 编译错误清零；LTS 清单更新 | ✅ 完成（本轮提交） |
| H4（可选）交互 | HUD 虚拟窗口支持输入：host 注入 `PlatformInputSource`（tick 轮询 + GuiIngame 旁路，1.7.10 无现成 HUD 事件） | SceneButton/链接点击在 HUD 窗口可用，hover/光标正常 | 待评估 |

H4 单独列为可选：HUD 交互需要自建输入注入路径（`RenderGameOverlayEvent` 之外没有按键/鼠标回调，
要 mixin `GuiIngame` 或每 tick 轮询），且会引入「HUD 与普通 GUI 同时抢输入」的仲裁问题，
建议在窗口内核稳定后单独评估。

## 6. 风险与注意

- **公共 API 变更**：LTS 清单中 HUD 组整体替换，按仓库规范属公共 API 变更，须用户确认路线后实施。
- **帧管线语义**：`SceneFramePipeline` 的 11 阶段协议为单 root 设计；多 HUD 窗口需先做
  「多 root 帧管线」或每窗口独立 pipeline 的性能/正确性评估（多 HUD 场景 flush 次数预算）。
- **保留不动**：`HudRegistry` 注册表语义（重复 id 拒绝、封板快照、provider 异常隔离）、
  `HudGlStateGuard`、`HudInsets/registerAvoidance` 安全区协议、`HudVisibility` 策略——这些不是
  「特殊编码」，是 HUD 宿主的正当能力。
- **命名**：`VirtualWindow` 与既有 `UiScreenHostSession`/overlay 概念区分清楚：overlay 是
  「窗口内浮层」，HUD 窗口是「屏幕级锚定窗口」。

## 7. 与架构审查的关系

本规划直接解决《[2026-08 深度架构审查](../架构审查/2026-08-深度架构审查.md)》中的 A1「HUD 平行管线与特殊编码」条目；
落地前建议同步处理 A2「文本测量三层接口收敛」（HUD 窗口同样消费 `SceneTextMeasurer`）
与「`ScaledHudBackend` 一般化」，避免在新窗口协议上继续叠加旧结构。
