# 通用 HUD（虚拟窗口）

`club.heiqi.uilib.ui.hud.api` 提供不依赖业务类型的客户端 HUD。HUD 是一个**锚定在屏幕四角的
虚拟窗口**：窗口内容用与 UI 页面完全相同的 scene 代码构建（控件 + 响应式 signal），
宿主统一负责四角锚定、安全区、缩放与帧管线。

首版不提供输入（点击、键盘）；内容变化走 signal，由宿主每帧物化。

## 最小接入

```java
HudRegistration hud = ClientHudService.getInstance().register(
        HudSpec.builder("example:status").anchor(HudAnchor.TOP_LEFT).build(),
        rt -> SceneNode.row().setHitTestable(false)
                .setText("Mining").setTextColor(0xFFFFFFFF).setFontSize(14));

// 模组资源释放时；register 与 close 均须在客户端主线程调用。
hud.close();
```

需要控制排序、边距或可见性时使用完整入口：

```java
HudSpec spec = HudSpec.builder("example:details")
        .anchor(HudAnchor.BOTTOM_RIGHT)
        .stackOrder(20)
        .margin(6)
        .minWidth(48)
        .maxWidth(240)
        .visibility(HudVisibility.GAMEPLAY_ONLY)
        .build();

HudRegistration registration = ClientHudService.getInstance().register(spec, rt -> contentRoot(rt));
```

## 窗口工厂契约

`HudWindowFactory.build(SceneRuntime rt)` 在窗口挂载时调用**一次**，返回内容根节点。此后内容变化
必须走 signal：

```java
Signal<String> status = Signal.create("Idle");

HudRegistration hud = ClientHudService.getInstance().register(spec, rt -> {
    SceneNode root = SceneNode.column().setHitTestable(false);
    rt.mount(root, SceneLabel.create(rt, new SceneLabel.Props(status, 0xFFFFFFFF, 14)));
    return root;
});

// 任意线程安全地更新内容（与 UI 页面同机制，帧末 flush 物化）
status.set("Mining");
```

- 工厂内可以使用全部 scene 能力：`SceneLabel`（含富文本/链接渲染）、`SceneButton`（仅渲染）、
  `rt.bind/bindText/mount/show/forEach` 与任何 `SceneNode` 组合。
- 工厂挂载失败（抛异常）只跳过该 HUD，不影响其它窗口。
- **空内容整窗隐藏**：内容树布局尺寸为零（signal 卸载、空文本）时，窗口连同宿主外壳一起隐藏；
  用 `rt.show(root, condition, childFactory)` 表达条件显隐。
- 宿主提供窗口外壳（半透明背景、padding、子树裁剪、内容宽度收缩），业务方只写内容树，不写绝对坐标。

## 布局与安全区

- 四角锚点按 `stackOrder`、再按注册顺序稳定堆叠。
- 窗口宽度按内容收缩，`minWidth/maxWidth` 施加通用 logical px 约束。
- 只有内容超过扣除 safeInsets 与 margin 后的视口上限时才 clamp 并裁剪（宿主以放置盒硬裁剪，
  超界内容不会画到窗口外）。
- 默认字号 token 为 14px（宿主外壳与 `HudLayoutEngine.lineHeight` 口径）；语义强调字号上限 18px。
- HUD 独立 scale 只在 host 边界换算一次并与 Minecraft GUI scale 隔离。
- 已知占位可用 `registerAvoidance` 提供 `HudInsets`，多个模组可共享安全区。
- F3 和未知第三方 HUD 无可靠测量协议，UILib 明确不猜测其绘制范围。

`GAMEPLAY_ONLY` 是默认策略：仅已进入世界且 `currentScreen == null` 时显示。
registration 归调用 mod 所有，断线或世界卸载只释放 UILib 的 session 窗口；重连后会自动重建，
调用方无需重新注册。仅在 mod 资源释放时于客户端主线程调用 `close()`。

## 与旧「快照协议」的差异（4.9 起）

旧版 `HudSnapshot/HudLine/HudSpan/HudTone` 行式数据协议已随 4.9 删除（路线 A，一步到位）：

- 旧：每帧返回 `HudSnapshot`，宿主把「行/片段/色调」翻译成固定节点模板。
- 新：挂载时返回 scene 内容树，内容变化走 signal——与 UI 页面完全同机制，无翻译层。
- 迁移：把每帧快照 provider 改写为「Signal 持有状态 + 窗口工厂绑定信号」，tick 侧只写 signal。
