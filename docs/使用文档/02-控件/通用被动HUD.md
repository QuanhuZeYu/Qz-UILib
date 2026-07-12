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
        .visibility(HudVisibility.GAMEPLAY_ONLY)
        .build();

HudRegistration registration = ClientHudService.getInstance().register(spec, provider);
```

provider 只会在 render 主线程调用，应无副作用并返回不可变 `HudSnapshot`。每行必须使用稳定且
不重复的 id；provider 抛出的运行时异常只会跳过该 HUD 的当前帧，不影响其它 HUD。

## 布局与安全区

- 四角锚点按 `stackOrder`、再按注册顺序稳定堆叠。
- margin、行距、颜色和进度外观由 UILib token 管理，业务方不写绝对坐标。
- GUI scale 由 Forge bridge 转换为逻辑视口，内容会 clamp 在视口内。
- 已知占位可用 `registerAvoidance` 提供 `HudInsets`，多个模组可共享安全区。
- F3 和未知第三方 HUD 无可靠测量协议，UILib 明确不猜测其绘制范围。

`GAMEPLAY_ONLY` 是默认策略：仅已进入世界且 `currentScreen == null` 时显示。
断线或世界卸载后注册资源会释放，调用方应在新世界生命周期重新注册。
