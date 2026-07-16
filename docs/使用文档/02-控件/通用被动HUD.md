# 通用被动 HUD

`club.heiqi.uilib.ui.hud.api` 提供不依赖业务类型的客户端 HUD。首版只负责被动展示，
不提供点击、拖拽或任意坐标回调。

## 最小接入

```java
HudRegistration hud = CompactHud.register("example:status", HudAnchor.TOP_LEFT, () ->
        HudSnapshot.of(
                HudLine.text("title", "Mining", HudTone.INFO),
                HudLine.progress("progress", "Vein", HudTone.SUCCESS, 0.65F)));

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

HudRegistration registration = ClientHudService.getInstance().register(spec, provider);
```

同一行需要不同语义强调时，使用 `HudSpan` 与富文本工厂：

```java
HudLine status = HudLine.rich("status",
        new HudSpan("label", "State: ", HudTone.MUTED),
        new HudSpan("value", "Ready", HudTone.INFO));

HudLine progress = HudLine.richProgress("scan", 0.65F,
        new HudSpan("label", "Scan: ", HudTone.MUTED),
        new HudSpan("value", "65%", HudTone.INFO));
```

provider 只会在 render 主线程调用，应无副作用并返回不可变 `HudSnapshot`。每行必须使用稳定且
不重复的 id；同一行内每个 span 也必须使用稳定且唯一的 id，以便 keyed 协调。span 文本与 tone
可以随快照变化，但不要用文本内容或列表位置临时生成 id。provider 抛出的运行时异常只会跳过
该 HUD 的当前帧，不影响其它 HUD。

`HudTone` 表达 `MUTED`、`INFO`、`SUCCESS` 等语义，由 UILib 主题解析为实际颜色；它不是 ARGB
值，也不是业务色注入点。旧的 `HudLine.text(...)`、`HudLine.progress(...)` 与读取方法继续兼容，
单色行无需迁移到富文本 API。

## 布局与安全区

- 四角锚点按 `stackOrder`、再按注册顺序稳定堆叠。
- Compact 默认字号 12px，Normal 默认字号 14px；UILib 为后续语义强调预留的字号上限是 18px，当前 API 不开放任意文本样式。
- HUD 外框默认按最长一行的内容宽度加水平 padding 收缩，进度槽跟随该实际宽度；`minWidth/maxWidth` 可施加通用 logical px 约束。
- 只有内容超过扣除 safeInsets 与 margin 后的视口上限时才 clamp 并裁剪；背景、边框、clip 与同锚点 stack 都使用实际收缩或 clamp 后的尺寸。
- margin、行距、颜色和进度外观由 UILib token 管理，业务方不写绝对坐标。
- HUD 独立 scale 只在 host 边界换算一次并与 Minecraft GUI scale 隔离。
- 已知占位可用 `registerAvoidance` 提供 `HudInsets`，多个模组可共享安全区。
- F3 和未知第三方 HUD 无可靠测量协议，UILib 明确不猜测其绘制范围。

`GAMEPLAY_ONLY` 是默认策略：仅已进入世界且 `currentScreen == null` 时显示。
registration 归调用 mod 所有，断线或世界卸载只释放 UILib 的 session scene；重连后会自动重建，
调用方无需重新注册。仅在 mod 资源释放时于客户端主线程调用 `close()`。
