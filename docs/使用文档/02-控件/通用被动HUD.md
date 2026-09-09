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

// 更新内容（与 UI 页面同机制，帧末 flush 物化）
// 线程契约：signal 写必须在客户端主线程（ui.reactive 无同步原语；跨线程仅 volatile 标志）
status.set("Mining");
```

- 工厂内可以使用全部 scene 能力：`SceneLabel`（含富文本/链接渲染）、`SceneButton`（仅渲染）、
  `rt.bind/bindText/mount/show/forEach` 与任何 `SceneNode` 组合。
- 工厂挂载失败（抛异常）只跳过该 HUD，不影响其它窗口。
- **线程契约（4.9 起成文）**：注册、关闭、`SceneRuntime`/signal 读写全部限定客户端主线程；
  scene 反应式内核（`ui.reactive`）无同步原语，不提供任意线程写入能力。网络线程等异步来源
  只投递 volatile 标志/队列（参照 chat3 `markDataDirty` 模式），由主线程 `tick` 冲刷进 signal。
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

## HUD 外接工具栏（外接挂载层）

`HudToolbarService` 把「某个 HUD 外侧挂一条工具栏」声明在 **HUD 级别**，而不是写死在某个
容器组件内部：工具栏挂在内容盒外侧 `HudToolbarSide` 指定的一条边（TOP/BOTTOM/LEFT/RIGHT，
默认 **BOTTOM**），与内容盒之间留 `gap`（默认 4），沿该边方向恰占 `thickness`（默认 28）。
**外框 = 内容盒 + 该边上的 gap + thickness**，外框才是测量 / 四角锚定 / clamp / 裁剪的输入——
四边工具栏既不遮挡 HUD 主体，也不会被视口裁掉。聊天 HUD（`qzuilib:chat3`）是首个使用者。

```java
// 为 example:status 挂一条底部工具栏；工厂与 HUD 窗口工厂同一契约（HudWindowFactory）。
HudRegistration toolbar = HudToolbarService.getInstance().register(
        "example:status",
        HudToolbarSpec.builder(HudToolbarSide.BOTTOM)
                .gap(4)
                .thickness(28)
                .visible(Signal.create(Boolean.TRUE))   // false = 不挂载、不占外框尺寸
                .build(),
        rt -> SceneNode.row(4).setHitTestable(true)
                .setCrossAxisAlign(CrossAxisAlign.CENTER));   // 按钮用 SceneButton + rt.forEach

toolbar.close();
```

- 同一 hudId 只允许一条工具栏，重复注册抛 `IllegalArgumentException`（不静默覆盖）。
- 工具栏工厂失败只丢工具栏，HUD 主体照常显示（单点隔离）。
- `visible` 为 false 时工具栏移出树，外框退化为内容尺寸；恢复为 true 时按挂载边插回原位置。
- 交叉轴语义：水平边（TOP/BOTTOM）工具栏用 SHRINK 宽度（= 自身内在宽与内容宽取大者，不拉伸），
  竖直边（LEFT/RIGHT）工具栏未设 `preferredHeight` 时由 STRETCH 拉满内容高。
- 注册/注销后，宿主在下一帧重建该 HUD 的保留窗口以接上/摘掉外接层（注册表版本经帧末批处理
  生效，故有一帧延迟）。
- 确定性 placement 查询：`HudToolbarLayer.Result.outerWidth(contentWidth)` /
  `outerHeight(contentHeight)`；`isVisible()` 以**实际挂载状态**为准（不是信号已提交值，
  避免同帧内"信号已请求可见、树还没挂上"给出错位外框）。
- 工具栏节点仍是普通 scene 子树，输入命中与作用域由宿主/页面统一管理（HUD 窗口宿主本身无
  输入源）。

### 默认缩放工具

注册工具栏后，公共层默认在调用方工厂内容之后加入「- / 1:1 / +」三个通用 scene 按钮；
水平边排成一行，竖直边排成一列。减号缩小、加号放大，每次改变 10 个百分点；
范围为 50%–200%，1:1 恢复 100%，其悬停提示显示当前倍率。达到边界时对应按钮禁用。

只想保留自定义工具时，在规格中设置 `scaleControls(false)`：

```java
HudToolbarSpec customOnly = HudToolbarSpec.builder()
        .scaleControls(false)
        .build();

// 也可在客户端主线程以语义动作控制已注册 HUD，无需拿 scene 节点。
HudScaleState scale = HudToolbarService.getInstance().scale("example:status");
if (scale != null) {
    scale.setPercent(150);
    scale.reset();
}
```

倍率属于 **每个工具栏注册项**，同一 HUD 在被动宿主和打开态聊天页面共享倍率，各次投放仍各建
自己的 scene 节点。隐藏工具栏或关闭缩放按钮不会重置倍率；未注册工具栏的 HUD 保持 100%。
状态保留到工具栏注销或服务清空，重新注册及重启使用默认值，不写配置文件。
缩放动作立即更新状态，宿主在帧开始采样并在下一帧完整生效；它不属于 HUD 位置编辑草稿，
因此「取消编辑」不回滚倍率，「恢复位置默认」也不重置倍率，倍率复位由 1:1 完成。

缩放作用于内容与整条工具栏，宿主全局 HudScaleSetting 仍是独立的外层倍率。
公共层继续用 scene / Signal / SceneButton / SceneTooltip 及 PaintCommand 管线；
缩放仅在宿主边界以 scaled backend 和输入坐标反向换算成对实施，不改字体配置或原版 GUI。
被动 HUD 宿主不新增输入源，打开聊天时按钮、文本选择和拖动使用 UILib 原有输入路由。

自定义页面接入时，Result.logicalOuterWidth/Height 查询缩放前布局尺寸，outerWidth/Height 查询
按百分比向上取整后的视觉外框，后者用于锚定、安全区和拖动 clamp。宿主须在一帧内固定倍率，
用同一倍率缩放绘制/裁剪并反向转换布局约束与输入；仅给节点加 Transform 不满足此契约。

## 与旧「快照协议」的差异（4.9 起）

旧版 `HudSnapshot/HudLine/HudSpan/HudTone` 行式数据协议已随 4.9 删除（路线 A，一步到位）：

- 旧：每帧返回 `HudSnapshot`，宿主把「行/片段/色调」翻译成固定节点模板。
- 新：挂载时返回 scene 内容树，内容变化走 signal——与 UI 页面完全同机制，无翻译层。
- 迁移：把每帧快照 provider 改写为「Signal 持有状态 + 窗口工厂绑定信号」，tick 侧只写 signal。
