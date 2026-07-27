# Experimental long Entry 容器施工图 / 实现蓝图

> **状态：P1 源码与测试已实现并提交；P2 prerequisite 固定列 Grid（P2a）已实施，P2 scene 投影已在工作树实现；P3 已完成 mappings/注入点侦察并进入实施。以上均无本地编译/JUnit/CI 证据。** 本文仍是 P1-P3 的 API 和行为合同，不把测试源码写成通过证明。能力为 experimental，不进入 v4.x LTS 稳定 API，不承诺 4.x 补丁兼容。

## 1. 术语与边界

- **Entry**：容器中一个由 `EntryKey` 标识的聚合项及其 `long amount`。Entry 不是 vanilla Slot，没有玩家槽 index、Slot 生命周期或 fake Slot 身份。
- **EntryKey**：backend 生成且解释的稳定不透明 key；列表重排、更新和操作均按 key，不按 index。
- **ItemDescriptor**：纯数据的物品身份/编码值，不含 `EntryKey`、live `ItemStack` 或 UI 状态。
- **confirmed snapshot**：服务端当前状态的不可变投影；scene 只消费该状态。`pending` 不是新真值。
- **carried/cursor**：玩家当前确认的携带物，仅存在 host/玩家库存语义，不进入 `LongContainerSnapshot` 和 storage port。
- **itemMax**：每次 MC 操作从物品声明查询的最大堆叠量；不是容器全局上限。UILib 不为 `itemMax > 127` 加 cap。
- **semantic intent**：核心可识别的有限业务意图；raw 鼠标按钮、键码、坐标只在 host 边界存在。

核心包及 scene wrapper 不 import `net.minecraft`、`net.minecraftforge`、`org.lwjgl`/`org.lwjglx` 或 GL。MC `ItemStack`、`InventoryPlayer`、掉落实体只允许出现在 `club.heiqi.uilib.ui.container.experimental.minecraft`。

## 2. 依赖方向

```text
model
  ↑
storage / operation / presentation
  ↑
scene（只投影 snapshot、pending 并上抛 intent）
  ↑
minecraft host/controller（唯一 MC 边界）
```

`model` 不依赖其它 experimental 子包；`storage`、`operation`、`presentation` 只依赖 model；`scene` 依赖 model/operation、既有 `ReadableSignal` 和 scene runtime；`minecraft` 才依赖 storage/operation/presentation 以及 MC/Forge。不得反向让 UILib 依赖 Qz-Miner 或 Qz-Storage。

## 3. P1 proposed Java API（纯数据 value contracts）

根包：`club.heiqi.uilib.ui.container.experimental`。

### 3.1 `model.EntryKey`

```java
public final class EntryKey {
    public EntryKey(String namespace, String value);
    public String namespace();
    public String value();
    @Override public boolean equals(Object other);
    @Override public int hashCode();
    @Override public String toString();
}
```

`namespace` 和 `value` 均非 null、非空；按严格字符串值语义比较，不 trim、不大小写折叠、不解析为 index。它是 backend 不透明稳定 key；UILib 不生成业务 key，也不从 `ItemDescriptor` 结构相等推导 key。

### 3.2 `model.ItemDescriptor`

```java
public final class ItemDescriptor {
    public ItemDescriptor(String typeId, String codecId, byte[] payload);
    public String typeId();
    public String codecId();
    public byte[] payload();
    @Override public boolean equals(Object other);
    @Override public int hashCode();
}
```

三个字段不可变；字符串非 null、非空，payload 非 null。构造和 `payload()` 都必须 defensive copy；equals/hashCode 是 `typeId + codecId + payload` 的结构值语义。它不持有 `EntryKey`；结构相等只表达 descriptor 相等，不决定 backend 是否 merge。

### 3.3 `model.LongEntrySnapshot` 与 `LongContainerSnapshot`

```java
public final class LongEntrySnapshot {
    public LongEntrySnapshot(EntryKey key, ItemDescriptor item, long amount);
    public EntryKey key();
    public ItemDescriptor item();
    public long amount();
}

public final class LongContainerSnapshot {
    public LongContainerSnapshot(List<LongEntrySnapshot> entries);
    public List<LongEntrySnapshot> entries();
}
```

`amount > 0`，key/item 非 null；一个 Entry 必须能够由 descriptor 确定物化。零量 Entry 不出现在列表中。container 的 entries 是有序、不可变、非 null 列表，key 唯一；构造和访问均不能暴露可变 list。snapshot 不含 cursor/carried、单一 global capacity、MC Slot、网络字段或 revision CAS。

### 3.4 `storage.LongContainerStorage`

```java
public interface LongContainerStorage {
    LongContainerSnapshot snapshot();
    TransferResult insert(ItemDescriptor item, long requested, TransferMode mode);
    TransferResult extract(EntryKey key, long requested, TransferMode mode);
}

public enum TransferMode { EXACT, UP_TO }

public enum TransferStatus { COMPLETED, PARTIAL, NO_CHANGE, NOT_FOUND }

public final class TransferResult {
    public TransferResult(TransferStatus status, long moved,
                          LongContainerSnapshot snapshot);
    public TransferStatus status();
    public long moved();
    public LongContainerSnapshot snapshot();
}
```

`requested > 0`；`moved` 在 `[0, requested]`，snapshot 非 null 且是操作后状态。`EXACT` 只有能完整移动 requested 才改变，否则 `moved == 0` 且 `NO_CHANGE`；`UP_TO` 可 partial，`0 < moved < requested` 为 `PARTIAL`，完整移动为 `COMPLETED`，零移动为 `NO_CHANGE`。extract 的 stale/不存在 key 为 `NOT_FOUND` 且零变化；insert 不以 key 为输入。容量、merge、排序和 key 分配均是 backend 语义。该 port 不认识 raw input、Shift、玩家槽、掉落、scene、MC、网络、retry ledger、事务框架或自动重试。

## 4. Operation 与 presentation API

### 4.1 `operation.LongContainerIntent`

```java
public final class LongContainerIntent {
    public enum Kind {
        TAKE_STACK, TAKE_HALF_STACK, DEPOSIT_ALL, DEPOSIT_ONE,
        QUICK_EXTRACT, DROP_ONE, DROP_STACK
    }
    public static LongContainerIntent takeStack(EntryKey key);
    public static LongContainerIntent takeHalfStack(EntryKey key);
    public static LongContainerIntent depositAll();
    public static LongContainerIntent depositOne();
    public static LongContainerIntent quickExtract(EntryKey key);
    public static LongContainerIntent dropOne(EntryKey key);
    public static LongContainerIntent dropStack(EntryKey key);
    public Kind kind();
    public EntryKey key();
}
```

只有 take/quick/drop 携带必要 key；deposit 的 key 必须为空并面向整个容器，点击哪个 Entry 不影响 deposit。intent 不携带 raw button/keycode、坐标、客户端 itemMax、`ItemStack`、最终 requested amount、玩家槽 index 或 clicked key 的 deposit。Shift 玩家槽→container 是 MC host 的玩家槽路径，不构造 Entry intent。

### 4.2 `presentation`

```java
public interface ItemPresentationResolver<I> {
    ItemPresentation<I> resolve(ItemDescriptor item);
}

public final class ItemPresentation<I> {
    public ItemPresentation(I icon, String displayName, List<String> tooltipLines);
    public I icon();
    public String displayName();
    public List<String> tooltipLines();
}
```

`ItemPresentationResolver` 只读 descriptor 并返回不可变 presentation，不读写 storage。`I` 由消费方选择，核心不绑定 MC/GL；scene 实现可使用既有平台无关 image source。金额显示使用独立 `java.util.function.LongFunction<String>`，必须覆盖所有 long 值，不把数量先转 int。

## 5. 正常路径与状态表

服务端主线程以当前状态重读；同一容器最多一个变更请求在途。客户端 `pending=true` 时变更输入 consume/no-op，hover、tooltip、scroll 继续。请求完成后只由服务端返回的最新 snapshot 更新 confirmed signal；不自动 retry。

| 入口/状态 | server 读取 | storage 调用 | 成功后的外部操作 | 失败/过期 |
|---|---|---|---|---|
| 空 cursor 左键 Entry | 当前 key、descriptor、itemMax | `extract(key, min(amount,itemMax), EXACT)` | 预先 materialize 后放入 cursor，moved 守恒 | 零变化并返回最新 snapshot |
| 空 cursor 右键 Entry | 同上 | `extract(key, ceil(min(amount,itemMax)/2), EXACT)` | 放入 cursor | 零变化 |
| 非空 cursor 左键 Entry | 当前 carried descriptor/数量 | `insert(carried, carriedCount, EXACT)` | 成功后扣 carried 全部 | 容量不足零变化；clicked key 忽略 |
| 非空 cursor 右键 Entry | 当前 carried descriptor | `insert(carried, 1, EXACT)` | 成功后扣 1 | 容量不足零变化；clicked key 忽略 |
| Shift Entry→背包 | 当前 key、amount、itemMax、背包可接受容量 | `extract(key, min(amount,itemMax,capacity), UP_TO)` | 将实际 moved 放入背包 | 只确认实际 moved，零变化不改双方 |
| Shift 玩家槽→container | 服务端重读玩家槽 descriptor/数量 | `insert(item, slotCount, UP_TO)` | 扣实际 moved | 目标不足允许 partial |
| Q Entry | 当前 key、descriptor | `extract(key, 1, EXACT)` | 先扣，再 materialize 并立即生成 1 个掉落 | 零变化；不生成掉落 |
| Ctrl-Q Entry | 当前 key、descriptor、itemMax | `extract(key, min(amount,itemMax), EXACT)` | 先扣，再生成对应掉落 | 零变化；不生成掉落 |
| 数字键 / creative clone / QUICK_CRAFT | 不进入核心 | consume/no-op | 无 | 不改变 confirmed 状态 |
| 双击 | 每击抵达时的 confirmed/cursor | 无特殊批处理 | 每次按当时状态解释 | 第二击 pending 时自然 no-op |

所有成功/partial 路径记录 `moved` 并满足 container + cursor/inventory/drop 的实际 moved 守恒。`EXACT` 的完整性由 storage 保证；MC controller 不把 `requested` 变成协议或 ledger。Q/Ctrl-Q 在服务端主线程先扣正常库存，随后物化并生成掉落；首版不承诺进程崩溃或断电补偿。

close/death 只处理真实 confirmed carried：先尝试并入 `InventoryPlayer`，余量按正常游戏路径掉落。host 必须避免与 vanilla close 对同一 carried 重复处理，不建设跨重启 escrow。

## 6. Scene 投影：`SceneLongEntryGrid`

建议路径：`ui.container.experimental.scene.SceneLongEntryGrid`。

P2 prerequisite 已由通用 scene 固定列 Grid 提供：Entry keyed nodes 始终作为同一 Grid parent 的直接 children，按行主序排布，不创建逐行 wrapper，因而 reorder/insert/remove 可按 `EntryKey` 保留 identity。P2 卡片显式采用既有 fill 宽度语义填充轨道，并避免与轨道冲突的 `preferredWidth`；Grid 地基不会暗中 clamp 显式宽度。P2 解析并保留 `ItemPresentation.tooltipLines` 数据，但不创建 tooltip portal 或第二套浮层；后续若需要展示必须复用通用 portal 地基。

```java
public final class SceneLongEntryGrid {
    public static Supplier<SceneNode> create(SceneRuntime runtime, Props props);

    public static final class Props {
        public static Builder builder();
        public static final class Builder {
            public Builder snapshot(ReadableSignal<LongContainerSnapshot> snapshot);
            public Builder pending(ReadableSignal<Boolean> pending);
            public Builder carriedEmpty(ReadableSignal<Boolean> carriedEmpty);
            public Builder onIntent(Consumer<LongContainerIntent> onIntent);
            public Builder presentation(ItemPresentationResolver<SceneImageSource> resolver);
            public Builder amountFormatter(LongFunction<String> formatter);
            public Builder columns(int columns);
            public Builder entryHeight(int logicalPx);
            public Builder gap(int logicalPx);
            public Props build();
        }
    }
}
```

`snapshot` 是 confirmed signal，`pending` 只作 client UI gate；`carriedEmpty` 是 host 提供的 confirmed cursor 占用投影，不携带物品或数量，也不进入 container snapshot。控件不持 canonical storage、不直接 set confirmed snapshot、不把 pending 的请求预绘为成功。列表仅在 entries 子树内按 `EntryKey` keyed reconcile，更新同 key 保留节点 identity，删除零量 Entry，新增/移动只影响列表范围。组件 factory 只建树和 signal bind；handler 只上抛 `Consumer<LongContainerIntent>` 或写外部 signal，禁止直接写 SceneNode 属性。

Entry 视觉显示 resolver 的 icon/name 与 `LongFunction` 的 long 文本；resolver 的 tooltip lines 保持不可变 presentation 数据，本阶段不渲染。`carriedEmpty=true` 时左/右分别上抛 take stack/half；否则分别上抛无 clicked key 的 deposit all/one；Shift 点击上抛 `QUICK_EXTRACT(key)`，middle consume/no-op。服务端仍重读真实 cursor，客户端布尔值不构成授权或 itemMax 来源。pending 时 pointer activation consume/no-op，hover 与 scroll 路由仍工作。scene 不创建自动 retry、双击状态、客户端 inventory 或 cursor。

## 7. MC adapter 与 controller

仅以下包允许出现 MC/Forge 类型：`club.heiqi.uilib.ui.container.experimental.minecraft`。

### 7.1 `MinecraftItemDescriptorCodec`

```java
public final class MinecraftItemDescriptorCodec {
    public ItemDescriptor describe(ItemStack stack);
    public ItemStack materialize(ItemDescriptor descriptor, long amount);
    public boolean matches(ItemStack stack, ItemDescriptor descriptor);
    public long maxStackSize(ItemDescriptor descriptor, ItemStack reference);
}
```

codec 负责 describe/materialize/matches/maxStackSize；payload 和 codecId 的具体编码由该 adapter 固定。`materialize` 必须拒绝不能由 descriptor 确定物化的值。每次操作查询物品声明的 itemMax；不增加 `>127` cap，原版协议/NBT 对极大数量的不兼容只作残余风险。

### 7.2 controller 与玩家槽路径

`MinecraftLongContainerController` 接收当前服务端 storage、`InventoryPlayer`、codec 和掉落 emitter，把 intent 映射为 storage 操作；它不把 raw event 传入 storage。操作在服务端主线程完成，服务端 current state 权威。controller 可复用现有 Fetch/Store 能力，但 P0/P1 不设计新 wire protocol、request ledger 或持久 session。

```java
public final class MinecraftLongContainerController {
    public TransferResult execute(LongContainerIntent intent, InventoryPlayer player);
    public TransferResult shiftFromPlayerSlot(int slotIndex, InventoryPlayer player);
    public void returnConfirmedCarried(InventoryPlayer player, DropEmitter drops);
}

public interface DropEmitter {
    void spawn(ItemStack stack);
}
```

`execute` 与 `shiftFromPlayerSlot` 必须在服务端主线程调用并总是返回操作后的 snapshot；unsupported host 动作不调用 controller。`slotIndex` 只存在于 MC adapter 方法，不进入 intent/storage。`returnConfirmedCarried` 只接管 host 明确拥有且尚未被 vanilla close 处理的 carried，并在处理后清空该所有权，防止重复归还。

玩家槽 shift-in 在 host 的 vanilla Slot 路径处理：服务端重读 Slot 当前 stack，describe 后调用 `insert(..., UP_TO)`，只按实际 moved 扣 Slot。Entry shift-out 先以玩家背包可接受容量和 itemMax 限制 requested，再调用 `extract(..., UP_TO)`；对实际 moved 逐项写入背包。背包空、partial、满均不假设成功。

## 8. Issue #66 sibling `GuiContainer` host

建议路径：`ui.container.experimental.minecraft.GuiContainerLongEntryHost`、`GuiContainerSceneSurface` 与内部 phase hook。该 host/surface 与 `McScreenBridge`/`UiSurface` 并列，不修改旧方法返回类型，不把 `GuiContainer` 类型带入 scene 核心。

### 8.1 输入 claim 与唯一 owner

新增同步、只读 claim：

```java
public interface GuiContainerSceneSurface {
    ContainerInputClaim claimDown(int logicalX, int logicalY,
                                  SceneMouseButton button,
                                  boolean shiftDown, boolean controlDown);
    void dispatchClaimedPointer(ContainerInputClaim claim, ScenePointerAction action,
                                int logicalX, int logicalY, SceneMouseButton button,
                                boolean shiftDown, boolean controlDown);
    ContainerInputClaim claimKey(SceneKey key,
                                 boolean shiftDown, boolean controlDown);
    void dispatchClaimedKey(ContainerInputClaim claim, SceneKey key,
                            boolean shiftDown, boolean controlDown);
    boolean isPending();
    PaintPlan paintMain(int logicalWidth, int logicalHeight,
                        int logicalMouseX, int logicalMouseY, float partialTicks);
    PaintPlan paintOverlayAndTooltip();
}

public enum ContainerInputOwner {
    SCENE_OVERLAY, SCENE_FOCUSED, LONG_ENTRY, VANILLA_SLOT, NONE
}

public final class ContainerInputClaim {
    public ContainerInputOwner owner();
    public EntryKey entryKey();
}
```

修饰键在 host 边界归一成布尔语义，不携带 native keycode；claim 只读最近 confirmed layout/hit-test、focus 和 scene semantic target，不写 signal、不提交 intent。只有 `LONG_ENTRY` 可带非 null `EntryKey`。DOWN 选择一个完整 claim 后，host 保存该 claim；MOVE/UP/CANCEL 只携带 DOWN claim 发给同一 owner，不重新 hit-test 或按 index 找 Entry。dispatch 同时接收 host 边界归一化的当前修饰键语义，确保只读 claim 不必写入隐藏状态也能在 DOWN dispatch 区分 Shift。不得再让另一 owner 看见同一次边沿，禁止 double-dispatch。`SCENE_OVERLAY` 表示 active overlay 的 scene 路由，`SCENE_FOCUSED` 只用于键盘的非 Entry focused scene 路由。

- overlay 优先；未命中 overlay 才判断 scene semantic Entry；再判断 vanilla Slot；否则 NONE。
- pointer DOWN 命中 long Entry 的 middle/creative clone 仍 claim `LONG_ENTRY`，dispatch 后 consume/no-op。由 long Entry 开始的 drag 在 MOVE/UP 阶段保持原 `LONG_ENTRY` owner，不进入 vanilla `QUICK_CRAFT`；由 vanilla Slot 开始的 `QUICK_CRAFT` 则从 DOWN 到 MOVE/UP 完整保持 `VANILLA_SLOT`，不让 scene 截获。
- stale key 由服务端 lookup 返回 no-change/latest snapshot；claim 不按 index 猜测和不自动 retry。
- pointer CANCEL 只通知并清除原 owner，不合成 CLICK、drop、deposit、extract 或其它 storage intent；close/death 走 host lifecycle 的 carried 归还规则。

键盘与 pointer 是两条独立裁决路径。host 在 `GuiContainer.keyTyped` 产生任何 vanilla 副作用前把原生键归一为 `SceneKey`，随后同步调用 `claimKey`；不得先调用 `super.keyTyped` 再补 scene dispatch，也不得用 scene 内部 `stopPropagation` 代替 host claim。每次 key activation 当场原子裁决，不创建或复用 pointer owner：

1. active overlay 对该键有 scene 语义时返回 `SCENE_OVERLAY`；没有 active overlay 时，非 Entry focused scene 对该键有 scene 语义才返回 `SCENE_FOCUSED`。这两个 owner 均只 dispatch scene 一次且不调用 vanilla。
2. `ESCAPE` 是特例：active overlay 优先返回 `SCENE_OVERLAY` 并 consume；没有 active overlay 时不由普通 focused scene 或 long Entry 截获，返回 `VANILLA_SLOT`/`NONE` 并至多调用一次 `super.keyTyped`，保留 vanilla close。
3. 前两步未 claim 时，在唯一 long Entry target 上判断 container shortcut；focused long Entry 优先，否则使用当前 hovered long Entry。`KEY_Q`、`DIGIT_1` 至 `DIGIT_9` 返回带该 `EntryKey` 的 `LONG_ENTRY`。`controlDown` 为 false/true 的 `KEY_Q` 分别 dispatch `DROP_ONE(key)`/`DROP_STACK(key)`；`shiftDown` 不改变这两个映射。数字键首版 dispatch 后 consume/no-op，不提交 intent。
4. `pending=true` 时上述 Q/Ctrl-Q/数字键仍返回 `LONG_ENTRY` 并 consume，但 `dispatchClaimedKey` 不提交任何 intent。未命中 long Entry 的 Q/Ctrl-Q/数字键不伪造 key，返回 `VANILLA_SLOT`/`NONE`，只走 vanilla 一次；其它 unsupported key 同样交 vanilla。

`dispatchClaimedKey` 必须消费刚才返回的完整 `ContainerInputClaim`，不得重新 hit-test、按 index 重找 Entry 或改判 owner。`isPending()` 是只读诊断探针，不替代 claim/dispatch 自身的 pending gate。`SCENE_OVERLAY`、`SCENE_FOCUSED`、`LONG_ENTRY` 均禁止随后调用 `super.keyTyped`；`VANILLA_SLOT`/`NONE` 不提交 scene intent，host 对该 activation 至多调用一次 `super.keyTyped`。已有 HUD keyboard capture 在 host claim 前绝对优先，避免两个 HEAD injector 因顺序产生 scene dispatch。一个 key activation 最多进入一个 owner。

### 8.2 绘制分相与坐标

`GuiContainerSceneSurface` 产出 claim/PaintPlan，内部 `GuiContainerScenePhaseHook` 使用注入/phase callback 连接 vanilla 的既有生命周期；二者均不复制 `GuiContainer` 私有 draw loop，不反射或复制 vanilla 私有状态。原版与当前 GTNH transformed 路径可共同维持的顺序固定为：

```text
vanilla background
→ scene main PaintPlan/replay
→ vanilla Slot/item/foreground
→ third-party objects（存在时）
→ scene overlay PaintPlan/replay
→ carried
→ vanilla/third-party tooltip
```

原版 `GuiContainer` 本身在 carried 后绘制 Slot tooltip；当前 GTNH 路径还在 foreground 后绘制 NEI objects、在 carried 后绘制 NEI tooltips。因此 host 不承诺 carried 为整个方法的最终图层，也不通过复制私有 draw loop、重绘 carried 或吞掉第三方 tooltip 伪造该顺序。scene overlay 位于 carried 前；Entry tooltip 若后续启用，必须复用通用 portal 并接受上述宿主层级合同。

`paintMain(...)` 接收 host 已在边界换算完毕的 logical viewport、pointer 与 partial ticks，用于驱动当帧 input/layout/paint；`paintOverlay()` 只返回同帧独立 overlay plan，不重跑主树。场景 paint 只产自包含不可变 `PaintPlan`，replay 只消费该 plan 和 `UiRenderBackend`；host 不让 renderer 读取 signal/SceneNode。host 从与现有 scene 输入桥同源的 `PlatformStateReader` 读取物理 pointer/修饰键，不从 `GuiContainer` scaled callback 坐标反推；logical px 与 framebuffer/display pixel 的换算只在 host 边界一次完成，禁止读取或派生 Minecraft GUI Scale/`ScaledResolution` 进入 UILib 闭环，遵守 I13。

### 8.3 phase hook 的 P3 侦察约束

P3 已读取当前 1.7.10 generated source 与 GTNH transformed runtime source，确认 background 后可注入 scene main，foreground/第三方 objects 后且 carried 前可注入 scene overlay。注入点必须 fail-soft 并输出可开关 phase 漂移诊断；若后续 mappings 或第三方改写使目标点失效，不以反射、私有状态复制或重复绘制冒险补洞。该设计不把静态 API 图纸冒充可编译 host。

### 8.4 P3 临时诊断与清理门禁

P3 使用集中 JVM 开关 `-Dqzuilib.experimental.container.debug=true`（默认关闭）和统一前缀 `[QZUILIB-P3-TEMP]`。临时详细日志覆盖 phase hook、attach/detach、claim/owner、坐标换算、pending gate、controller 请求/结果、dispose/close 与第三方 phase 漂移。完整 CI、clean consumer 和游戏矩阵闭环后，必须主动提醒用户并删除逐事件/逐帧详细插桩，只保留必要的低频异常日志；未完成该清理不得把 P3 标记为完成。

## 9. P0-P3 文件级实施顺序

P0 仅是本文件、ADR、索引和交接的设计静态证据；不新增 Java，不把后续测试写成通过。

### P1：纯数据与 value contracts（源码与测试已实现并提交；未编译或运行）

生产文件精确新增：`model/EntryKey.java`、`model/ItemDescriptor.java`、`model/LongEntrySnapshot.java`、`model/LongContainerSnapshot.java`、`storage/LongContainerStorage.java`、`storage/TransferMode.java`、`storage/TransferStatus.java`、`storage/TransferResult.java`、`operation/LongContainerIntent.java`、`presentation/ItemPresentation.java`、`presentation/ItemPresentationResolver.java`，根路径均为 `src/main/java/club/heiqi/uilib/ui/container/experimental/`。上述源码已实现并提交；本机禁令下未编译或运行。

测试精确新增：`src/test/java/club/heiqi/uilib/ui/container/experimental/model/EntryKeyTest.java`、`ItemDescriptorTest.java`、`LongContainerSnapshotTest.java`、`storage/LongContainerStorageContractTest.java`、`operation/LongContainerIntentTest.java`、`presentation/ItemPresentationTest.java`。测试源码覆盖 null/defensive-copy/equality/long 边界、snapshot 不变量、EXACT/UP_TO fake backend、intent 字段边界、presentation 不读 storage；未执行 JUnit。P1 不依赖 scene runtime，不改旧 inventory/slot 文档或源码。

### P2：scene 投影（源码与测试已实现于工作树；未提交、编译或运行）

前置 P2a 已实施通用固定列 Grid 与 L2 测试源码：`SceneNode.grid(n)` / `gridColumns` 保持 entries 为同 parent 直接 children；尚无编译、JUnit 或 CI 结果，状态为 `INCOMPLETE`。

生产文件新增 `src/main/java/club/heiqi/uilib/ui/container/experimental/scene/SceneLongEntryGrid.java`，Props/Builder 固定为其 nested types，不另拆文件。测试新增 `src/test/java/club/heiqi/uilib/ui/container/experimental/scene/SceneLongEntryGridTest.java` 与 `src/test/java/club/heiqi/uilib/ui/scene/integration/LongEntryGridInteractionTest.java`。实现 keyed EntryKey reconcile、同 key 回查最新 confirmed snapshot、carriedEmpty semantic 映射、pending gate、presentation 和 long formatter；测试源码覆盖 reorder/insert/remove、同 key confirmed replacement、完整 long、左右键/Shift/middle、pending consume/no-op 与 hover/scroll 继续。tooltip lines 仅保留为 presentation 数据，无 portal/显示测试。触及 runtime/signal/input 的交互测试固定归 L3 `scene/integration/`，未塞入 L2 `scene/layout/`；上述源码尚无编译或 JUnit 结果。

### P3：MC adapter、#66 host 与消费证据

生产文件已在工作树新增 `minecraft/MinecraftItemDescriptorCodec.java`、`minecraft/MinecraftLongContainerController.java`、`minecraft/DropEmitter.java`、`minecraft/GuiContainerLongEntryHost.java`、`minecraft/GuiContainerSceneSurface.java`、`minecraft/GuiContainerScenePhaseHook.java`、`minecraft/ContainerInputOwner.java`、`minecraft/ContainerInputClaim.java` 与平台中立临时日志 helper `minecraft/ExperimentalContainerDiagnostics.java`；新增 `src/main/java/club/heiqi/uilib/mixin/early/MixinGuiContainerScenePhases.java`，并修改 `EarlyMixins.buildMixinsForSide(...)` 在 client side 动态登记。`mixins.qz_uilib.early.json` 继续只承载 config，不伪造静态 mixin 列表。测试已在工作树新增 `src/test/java/club/heiqi/uilib/ui/container/experimental/minecraft/MinecraftItemDescriptorCodecTest.java`、`MinecraftLongContainerControllerTest.java`、`GuiContainerInputClaimTest.java`、`GuiContainerPhaseOrderTest.java`，并扩充 `EarlyMixinsTest` 的 side 过滤断言。上述实现仍需 CI 编译/JUnit 与真机矩阵校验；input 测试还需在 clean consumer 补齐真实 surface 对 Entry/非 Entry 的 Q、Ctrl-Q、`DIGIT_1..DIGIT_9`、pending、overlay/ESC、middle clone 和两种起点 `QUICK_CRAFT` 的全链不穿透证据。P3 顺序为 mappings/注入点侦察→codec→controller→surface pointer/key claim/owner→phase draw→clean consumer。侦察已确认 early mixin 可行，但第三方 transformed 顺序仍须通过 fail-soft hook、临时诊断和运行矩阵验证；不得自行换成反射/复制。P3 不隐含修改 Qz-Miner、Qz-Storage、版本、发布或新 wire protocol。

## 10. 验收与测试矩阵

| 层级 | 必须覆盖 | 证据归属 |
|---|---|---|
| value | null、空字符串、payload 双向 copy、equals/hash、`Long.MAX_VALUE`、零/负 amount 拒绝、key 唯一/有序/不可变 | P1 JUnit + review |
| storage | EXACT 全量/零变化、UP_TO partial、stale key、容量由 backend 决定、status/moved/snapshot 一致 | P1 contract test |
| intent/presentation | 7 种 intent、deposit 无 key、无 raw 字段、resolver 不读 storage、long formatter | P1 JUnit |
| keyed scene | EntryKey reorder/insert/remove、snapshot 只读、pending、不预改 confirmed、tooltip data、scroll/hover | P2 integration |
| normal operation | 左取 `min(amount,itemMax)`；右取 `ceil(min(amount,itemMax)/2)`；carried 左全量/右 1 且 EXACT；deposit 忽略 clicked Entry | P3 controller |
| shortcut | Shift out 单次最多 itemMax 且 target partial；Shift in partial；Entry Q→DROP_ONE、Ctrl-Q→DROP_STACK；数字键/clone/QUICK_CRAFT consume/no-op；非 Entry shortcut 保持 vanilla；双击按当时状态 | P3 controller/host |
| itemMax | 1、16、64、>127；每次查询声明值、不加 cap；>127 残余风险 | P3 MC/CI + review |
| inventory/drop | 背包空、partial、满；confirmed carried close/death 先并包再正常掉落，避免重复处理 | P3 MC/game |
| claim | pointer 的 overlay/Entry/vanilla Slot/NONE owner，DOWN 单选且 MOVE/UP/CANCEL 保持 owner；key 的 overlay/focused scene→Entry→vanilla 优先级，Entry/非 Entry 的 Q、Ctrl-Q、数字键、pending、overlay/ESC；每次 activation 无 double-dispatch 且至多一次 super | P3 host |
| pointer unsupported | Entry middle/creative clone consume/no-op；Entry 起点 drag 不进入 QUICK_CRAFT；vanilla Slot 起点 QUICK_CRAFT 完整保留；CANCEL 只清原 owner且零 intent | P3 host |
| paint/coords | background→main→Slot/foreground→第三方 objects→overlay→carried→vanilla/第三方 tooltip，PaintPlan 自包含，GUI scale 1/>1 仍一次边界换算 | P3 client |
| runtime/artifact | integrated/dedicated server、pending、clean consumer、main/dev/sources 制品 | CI/user；本设计无结果 |

未执行的 CI、编译、JUnit、制品、dedicated server 和游戏内验证不得写成通过；P0 只有文档和静态 Git 证据。

## 11. 兼容策略、残余风险与实施前检查单

新 API 只进入 experimental 包，不列入 `docs/使用文档/v4.x-LTS-稳定API清单.md`；不原地改签名、不删除 `InventorySlotSnapshot`/`SlotContentSnapshot`，不修改 `McScreenBridge`/`UiSurface`，不提高或降低依赖版本，不改变版本号、发布通道或下游坐标。Qz-Miner 只有在独立制品与 clean consumer 证据成立后才可另开消费任务。

残余风险：真实 mappings/GuiContainer 注入点可能要求修订 phase hook；服务端直接操作不承诺断电/进程崩溃一致性；`itemMax > 127` 可能不兼容原版网络/NBT；backend merge/capacity/identity 由消费方负责；现有旧 snapshot 文档漂移不在本任务清理范围。上述均不在首版预建 WAL、journal、escrow、retry ledger、通用事务或未知第三方上限机制。

实施前必须重新读取当前源码、AGENTS、NORTH_STAR 与边界；核对 P1 storage 没有 carried/global capacity/EntryKey-in-descriptor/high-level intent；核对 P2 signal/keyed/pending 与 R1-R13/I1-I13；读取 mappings 确定 phase hook；逐条落实左/右/Shift/Q/Ctrl-Q、unsupported、双击、close/death、itemMax 与 moved 守恒；建立 P1→P2→P3 独立测试、CI、clean consumer、制品和游戏矩阵；没有结果保持 `INCOMPLETE`。
