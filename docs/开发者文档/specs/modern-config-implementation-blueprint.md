# 现代化配置页施工图（实现蓝图）

> 基础决策：`DECISION-20260628-modern-config-new-mental-model.md`（三态四层软依赖架构）
> 对齐宪章：`NORTH_STAR.md`，重点 I1/I2/I6/I9 + 反模式
> 本文档是实现蓝图，不含实现体。【已验证】= 读代码确认；【设计提议】= 本施工图提出的方案。

## 已验证事实摘要（侦察结论，作为设计前提）

- 【已验证】现有 `club.heiqi.config` 核心类：`Config`(工厂)、`ConfigNode`(只读树)、`MutableConfig`/`DefaultMutableConfig`(可变树+dirty)、`ConfigSource`(read)、`ConfigWriter`(write)、`ConfigFormat`(JSON/YAML)、`ConfigChangeEvent`(path/old/new/ChangeType)、`ConfigChangeListener`。
- 【已验证】`DefaultMutableConfig.set(path,value)` 用 `path.split("\\.")` 导航嵌套 `Map<String,Object>`，dirty 标记直接写在同一数据对象上——旧栈反模式（草稿污染权威）。
- 【已验证】`DefaultMutableConfig.save()→saveTo(source)→writer.write()`：转不可变节点后整体写文件，无 try/写失败回滚。
- 【已验证】reactive 层：`Signal.create/get/set`、`Computed.create(Supplier)/get/dispose`、`ReadableSignal<T>.get()`；写入经 `ReactiveScheduler` 帧末批处理（I9）。
- 【已验证】`SceneRuntime`：`mount(parent, Supplier<SceneNode>)`、`bind(Invalidation, ReadableSignal, Consumer)`、`bindText`、`on(node,type,handler)`、`forEach`、`show`、`portal/portalAnchored`、`focusable`、`flush`、`dispose`。
- 【已验证】控件范式：每控件 = `Props record + create(rt,props):Supplier<SceneNode>`，受控（value 外部 signal 持有，onChange 上抛）。现成可复用：`SceneTextInput`(STRING)、`SceneToggle`(BOOLEAN)、`SceneSlider`(NUMBER)、`SceneSegmented`/`SceneRadioGroup`/`SceneSelect`(CHOICE)、`SceneButton`。
- 【已验证】`SceneFormHostWidget` 是新模型 UI 层的现成手写原型：current/draft 双 signal + Computed 派生 dirty/error/canSave + 受控控件 + save/cancel/restore 闭环。
- 【已验证】`SceneNode` 已支持 `flexGrow`（`SceneSegmented` 旧注释"scene 无 flex-grow"已过时）。
- 【已验证】`SceneChromeTokens`/`SceneStateColors`/`ScenePalette` 已存在，是 scene 控件统一 chrome token 来源。
- 【已验证】现有自研 `YamlConfigLoader`/`YamlConfigWriter` 是简化实现：写不出注释、不支持多行字符串/锚点、复杂嵌套 round-trip 缺测试覆盖。**将被 SnakeYAML 替换内部实现**（外部 API 不变），详见决策第十节与下方"1.7.10 环境约束"。

## 一、包结构总览

在 `club.heiqi.config` 下分核心层（零依赖）与 UI 层（软依赖 uilib），与现有类同包共存。

```
club.heiqi.config                          [现有 + 新增，核心层零依赖]
├─ (现有保留) Config / ConfigNode / MutableConfig / DefaultMutableConfig
│             ConfigSource / ConfigWriter / ConfigLoader / ConfigFormat
│             ConfigException / Json*/Yaml* 加载写入器           ← 复用，作 Persistence 底座
├─ (现有保留) ConfigChangeEvent / ConfigChangeListener           ← 复用作 EventBus 事件/监听器
│
├─ schema/                                  [新建·核心层·零依赖]
│   ├─ ConfigSchema          Schema 根 + Builder DSL 入口
│   ├─ SectionSpec           分类(section)声明，内嵌字段 builder
│   ├─ FieldSpec             单字段元数据(路径/类型/默认/约束)
│   ├─ FieldType             枚举 STRING/NUMBER/BOOLEAN/CHOICE(+预留)
│   └─ FieldConstraints      约束载体(min/max/maxLength/choices/required)
│
├─ runtime/                                 [新建·核心层·零依赖]
│   ├─ Authority             内存权威快照(typed get / load / apply)
│   ├─ DraftBuffer           纯数据草稿容器(current+draft 每字段)
│   ├─ Persistence           文件读写(整文件覆写 + 写失败回滚)
│   ├─ LegacyAdapter         getRawJson/setRawJson 透传(包装 ConfigNode)
│   ├─ ConfigEventBus        轻量发布订阅(复用 ConfigChangeEvent/Listener)
│   ├─ ValidationResult      校验结果(字段→错误信息)
│   └─ ConfigManager         门面：协调 Schema/Authority/Persistence/EventBus
│
└─ ui/                                      [新建·UI 层·软依赖 uilib]
    ├─ ConfigUI              门面入口 open(authority, schema)
    ├─ DraftSignalAdapter    DraftBuffer→signal 适配(每字段 Signal+Computed)
    ├─ ConfigScreen          页面骨架(分类导航+字段区+按钮区)
    ├─ field/                字段控件适配器
    │   ├─ FieldRenderer         接口：render(rt, ctx): SceneNode
    │   ├─ StringFieldRenderer   → SceneTextInput
    │   ├─ NumberFieldRenderer   → SceneSlider / SceneTextInput(数值)
    │   ├─ BooleanFieldRenderer  → SceneToggle
    │   ├─ ChoiceFieldRenderer   → SceneSegmented / SceneSelect
    │   └─ FieldRendererRegistry FieldType→FieldRenderer 注册表(可插拔扩展口)
    └─ theme/
        └─ ConfigTheme        配置页 token(委托 SceneChromeTokens，不新立主题层)
```

**层归属标注**：
- 核心层（`schema/`+`runtime/`+复用的现有类）：零硬依赖 uilib。
- UI 层（`ui/`）：软依赖 uilib，import `club.heiqi.uilib.ui.scene.*` / `ui.reactive.*`，经可选加载，无 uilib 时不实例化。

**与现有类关系**：
| 现有类 | 处置 | 理由 |
|---|---|---|
| `ConfigNode`/`Config`/加载器 | 复用 | Persistence 内部用 `Config.load` 读、`ConfigNode` 表示文件态 |
| `ConfigWriter`/`ConfigSource`/`ConfigFormat` | 复用 | Persistence 写盘底座 |
| `ConfigChangeEvent`/`ConfigChangeListener` | 复用 | ConfigEventBus 只作注册表+广播，不重造 |
| `MutableConfig`/`DefaultMutableConfig` | 不作权威KV | 直接持 Map 更干净，避免误用 dirty/listener 反模式 API |
| `uilib/config/*`(Forge 旧栈) | 不动 | legacy，新架构不继承 |

## 二、核心层详细设计

### 1. ConfigSchema（Builder DSL）
- 职责：声明式描述一组配置字段，核心层与 UI 层共用的唯一字段真相源。
- 守：决策"Schema 纯数据声明无 UI 依赖"；I6 精神。

```java
public final class ConfigSchema {
    public static Builder builder(String modId);
    public String modId();
    public List<SectionSpec> sections();
    public FieldSpec field(String path);
    public Collection<FieldSpec> allFields();
    public static final class Builder {
        public SectionSpec.Builder section(String name);
        public ConfigSchema build();
    }
}
```
- 内部结构：`Map<String, FieldSpec> byPath`（扁平索引）+ `List<SectionSpec>`（保序）。

### 2. SectionSpec / FieldSpec / FieldConstraints

```java
public final class SectionSpec {
    public String name();
    public String title();
    public List<FieldSpec> fields();
    public static final class Builder {
        public Builder string(String key);
        public Builder number(String key);
        public Builder bool(String key);
        public Builder choice(String key);
        public ConfigSchema.Builder endSection();
    }
}

public final class FieldSpec {
    public String path();                  // 全路径 "section.key"
    public FieldType type();
    public Object defaultValue();
    public FieldConstraints constraints();
    public String label();
    public String helper();
}

public enum FieldType { STRING, NUMBER, BOOLEAN, CHOICE; /* 预留扩展 */ }

public final class FieldConstraints {
    public double min();  public double max();
    public int maxLength();
    public List<String> choices();
    public boolean required();
}
```
- 路径约定：沿用现有 `split("\\.")` 点号路径。
- FieldSpec 不可变，杜绝 UI 层回写字段定义。

### 3. Authority（内存权威快照）
- 职责：游戏运行时读取的唯一来源；持 typed 内存值。
- 守：决策"游戏读取唯一来源"；I6 精神（不持 signal）。
- **裁决：不复用 DefaultMutableConfig，直接持 Map<String,Object>**

```java
public final class Authority {
    public static Authority load(File file, ConfigSchema schema) throws ConfigException;
    public static Authority load(ConfigSource src, ConfigFormat fmt, ConfigSchema schema) throws ConfigException;
    public <T> T get(String path);
    public String getString(String path);
    public double getNumber(String path);
    public boolean getBool(String path);
    public ConfigSchema schema();
    public LegacyAdapter legacy();
    void applyAll(Map<String, Object> typedValues);   // package-private
    Map<String, Object> snapshotTyped();              // 供 DraftBuffer 深拷贝
}
```
- 内部结构：`Map<String,Object>` 存 typed 值（不复用 DefaultMutableConfig）。
- `applyAll` 设为 package-private，强制保存走 ConfigManager。

### 4. DraftBuffer（纯数据草稿容器）★ 三态隔离核心
- 职责：每字段持 current+draft 两个值的纯数据容器；零 signal、零 uilib 依赖。
- 守：决策"三态物理隔离"+"DraftBuffer 不持 signal"。
- **裁决：方案A——核心层持真相，signal 是镜像**

```java
public final class DraftBuffer {
    public static DraftBuffer from(Authority authority);
    public Object getDraft(String path);
    public Object getCurrent(String path);
    public void setDraft(String path, Object value);
    public boolean isDirty(String path);
    public boolean isDirtyAny();
    public String error(String path);
    public boolean hasError();
    public ValidationResult validateAll();
    public void resetToCurrent();
    public void resetFieldToDefault(String path);
    public Map<String, Object> draftSnapshot();
    public void commitDraftToCurrent();
    public ConfigSchema schema();
    public Collection<String> fieldPaths();
}
```
- 内部结构：`Map<String, Object> currentValues` + `Map<String, Object> draftValues`（两个独立 Map，物理隔离）。
- 严禁 import `ui.reactive` 或 `ui.scene`。

### 5. Persistence（文件读写 + 回滚）
- 职责：整文件覆写；封装现有 ConfigWriter/Config.load。
- 守：决策"写失败回滚 Authority"；I9 精神。
- **持久化格式默认 YAML**（ConfigFormat.YAML）：新旧配置统一 YAML，由 SnakeYAML 提供完整 YAML 1.1 特性（注释 round-trip 不丢、多行字符串、锚点/别名）。ConfigFormat 枚举与外部 API 不变，YamlConfigLoader/YamlConfigWriter 内部实现替换为 SnakeYAML。

```java
public final class Persistence {
    public Persistence(File file, ConfigFormat format);   // 默认传 ConfigFormat.YAML
    public ConfigNode read() throws ConfigException;
    public void writeAll(Map<String, Object> typedValues, ConfigSchema schema) throws ConfigException;
}
```
- 回滚责任在 ConfigManager（先 snapshot→apply→write 失败则 restore snapshot）。

### 6. LegacyAdapter（旧式透传）
- 职责：对复杂嵌套对象提供 getRawJson/setRawJson 字符串透传。

```java
public final class LegacyAdapter {
    LegacyAdapter(Authority authority);
    public String getRawJson(String path);
    public void setRawJson(String path, String json);
}
```
- setRawJson 写回也必须经 Authority 受控路径。

### 7. ConfigEventBus（复用现有 Event/Listener）
- 职责：Authority 权威变更后通知游戏侧重载。
- **裁决：复用现有 ConfigChangeEvent/ConfigChangeListener，不重造**

```java
public final class ConfigEventBus {
    public void subscribe(ConfigChangeListener listener);
    public void unsubscribe(ConfigChangeListener listener);
    void publish(ConfigChangeEvent event);             // package-private
}
```
- 内部结构：`CopyOnWriteArrayList<ConfigChangeListener>`，监听器异常隔离。

### 8. ConfigManager（门面）
- 职责：统一入口，独占保存事务序列。
- 守：决策"整页事务保存"+"保存失败回滚"+"不跳过校验"。

```java
public final class ConfigManager {
    public static ConfigManager bootstrap(File file, ConfigSchema schema) throws ConfigException;
    public Authority authority();
    public ConfigSchema schema();
    public ConfigEventBus eventBus();
    public DraftBuffer openDraft();
    public SaveOutcome save(DraftBuffer draft);
    public void flushRaw() throws ConfigException;  // 补登记：供 LegacyAdapter.setRawJson 后显式持久化
}
```
- save 序列：1 validateAll 失败返回 invalid → 2 snapshot=authority.snapshotTyped() → 3 authority.applyAll(draft) → 4 persistence.writeAll 失败回滚 → 5 commitDraftToCurrent + publish。

## 三、UI 层详细设计

### 1. ConfigUI（门面入口）

```java
public final class ConfigUI {
    public static void open(ConfigManager manager);
    public static void open(ConfigManager manager, GuiScreen parent);
}
```

### 2. DraftSignalAdapter（DraftBuffer→signal 适配）★ 数据态→渲染态桥
- **裁决：方案A——DraftBuffer 持真相，signal 是镜像。保存后 current 同步用显式 afterSaveSync。**

```java
public final class DraftSignalAdapter {
    public DraftSignalAdapter(SceneRuntime rt, DraftBuffer draft);
    public Signal<Object> draftSignal(String path);
    public Computed<String> errorSignal(String path);
    public Computed<Boolean> dirtySignal(String path);
    public Computed<Boolean> hasErrorSignal();
    public Computed<Boolean> isDirtySignal();
    public Computed<Boolean> canSaveSignal();
    public void onFieldEdit(String path, Object newValue);
    public void resetToCurrent();
    public void resetFieldToDefault(String path);
    public void afterSaveSync();
    public void dispose();
}
```
- 机制：draftSignal(path).set(v) 的真值落点是 DraftBuffer——adapter 内部同步 draft.setDraft(path, v)，使 DraftBuffer 始终是数据真相，signal 是响应式镜像。
- 保存后 current 同步用显式 afterSaveSync 重置受影响 Computed（保存是低频整页事务，I9 允许）。

### 3. 字段控件（FieldRenderer）

| FieldType | scene 控件 | 适配要点 |
|---|---|---|
| STRING | SceneTextInput | value=draftSignal，onChange=onFieldEdit |
| NUMBER | SceneSlider(有range) / SceneTextInput(无range) | 有 range→slider；无 range→数值文本框 |
| BOOLEAN | SceneToggle | value=draftSignal |
| CHOICE | SceneSegmented(≤4项) / SceneSelect(多项) | onSelect→onFieldEdit；overlay 走 portalAnchored |

```java
public interface FieldRenderer {
    SceneNode render(SceneRuntime rt, FieldContext ctx);
}
public final class FieldRendererRegistry {
    public void register(FieldType type, FieldRenderer renderer);
    public FieldRenderer resolve(FieldSpec spec);
}
```

### 4. ConfigScreen（页面骨架）

```java
public class ConfigScreen extends AbstractSceneHostWidget {
    public ConfigScreen(PlatformInputSource input, ConfigManager mgr,
                        DraftSignalAdapter adapter, FieldRendererRegistry registry);
    // 结构：
    //   root(COLUMN, fillParentHeight)
    //     ├ titleBar(固定)
    //     ├ statusSummary(dirty/error 徽标)
    //     ├ viewport(scrollable)
    //     │   └ content(COLUMN, forEach section→forEach field→registry.render)
    //     └ actionBar(固定): 恢复默认 / 取消(enabled=isDirty) / 保存(enabled=canSave)
}
```
- 按钮回调：保存→mgr.save(draft)+adapter.afterSaveSync()；取消→adapter.resetToCurrent()；恢复默认→逐字段 resetFieldToDefault。

### 5. 主题/样式（ConfigTheme）
- 委托 SceneChromeTokens/SceneStateColors，不新立主题层。

## 四、数据流实现路径

```
1. 启动加载
   ConfigManager.bootstrap(file, schema)
     → Persistence.read() → Config.load(file) → ConfigNode
     → 按 schema.allFields() 逐字段取值，缺失补 defaultValue
     → Authority(typed Map 填充)

2. 游戏读取
   authority.getString("general.name") → 内部 typed Map.get(path)

3. 旧式透传
   authority.legacy().getRawJson("nested.complex") → ConfigNode 子树 → JSON 字符串

4. 打开配置页(仅有 uilib)
   ConfigUI.open(manager)
     → DraftBuffer draft = manager.openDraft()              // 深拷贝
     → DraftSignalAdapter adapter = new (rt, draft)          // 每字段 Signal+Computed
     → ConfigScreen screen = new (input, manager, adapter, registry)
         → forEach field: registry.resolve(spec).render(rt, ctx)
     → 打开 MC GuiScreen

5. 编辑
   用户输入 → SceneTextInput handler → onChange.accept(newValue)
     → adapter.onFieldEdit(path, v)
         → draftSignal(path).set(v)        // 经 ReactiveScheduler 帧末批处理(I9)
         → draft.setDraft(path, v)          // 同步写回核心层纯数据真相
     → errorSignal/dirtySignal/canSaveSignal Computed 自动重算

6. 保存
   保存按钮 onClick → manager.save(draft)
     1 draft.validateAll() 有错 → 返回 invalid，不写文件
     2 snapshot = authority.snapshotTyped()
     3 authority.applyAll(draft.draftSnapshot())
     4 persistence.writeAll(typed, schema)
         成功 → 5；失败 → authority.applyAll(snapshot) 回滚 → ioFailed
     5 draft.commitDraftToCurrent(); eventBus.publish(ConfigChangeEvent(...))
     UI 侧：adapter.afterSaveSync()

7. 取消
   取消按钮 → adapter.resetToCurrent()
     → draft.resetToCurrent() + 逐字段 draftSignal.set(current)

8. 恢复默认
   恢复按钮 → 逐字段 adapter.resetFieldToDefault(path)
     → draft.resetFieldToDefault(path) + draftSignal.set(默认值)
```

## 五、实现顺序与依赖图

```
依赖方向(↑被依赖)：
  Schema(零依赖)
    ↑
  Authority ← Persistence(复用现有 writer/loader) ← LegacyAdapter
    ↑
  DraftBuffer(拷 Authority) + ConfigEventBus(复用现有 Event/Listener)
    ↑
  ConfigManager(协调全部核心层)
    ↑─────────────────────[核心层/UI层 软依赖边界]──────────────
  DraftSignalAdapter(适配 DraftBuffer→signal)
    ↑
  FieldRenderer(复用现成 scene 控件) + FieldRendererRegistry
    ↑
  ConfigScreen(scene host) ← ConfigTheme
    ↑
  ConfigUI(门面)
```

### 阶段拆分

**P0 核心层最小可用**（无 uilib 可独立运行）
- 产出：`schema/` 全部 + `Authority` + `Persistence` + `DraftBuffer` + `ConfigEventBus` + `ConfigManager` + `LegacyAdapter`
- 产出：YamlConfigLoader/YamlConfigWriter 用 SnakeYAML 重写（替换自研简化实现，支持注释/多行字符串/锚点）
- 可并行：`schema/`(独立) ∥ `Persistence`(只依赖现有类) ∥ 构建侧 shadow/SnakeYAML 接入；三者完成后 `Authority` → `DraftBuffer` → `ConfigManager` 串行
- 验收：纯 JVM 单测——启动加载补默认、typed get 正确、DraftBuffer 与 Authority 物理隔离、save 校验失败不写、IO 失败回滚、成功广播、无 uilib import（grep 验证）

**P1 UI 层最小可用**（有 uilib，4 字段类型）
- 依赖：P0 完成
- 产出：`DraftSignalAdapter` + 4 个 `FieldRenderer` + `FieldRendererRegistry` + `ConfigScreen`(扁平) + `ConfigUI` + `ConfigTheme`
- 可并行：4 个 FieldRenderer 互相独立；DraftSignalAdapter 须先完成
- 验收：接入真实 schema，打开配置页编辑→保存→重开值持久；三态隔离运行时验证

**P2 完善**（排期项）
- 分类导航、嵌套分类树、字段虚拟化、8 种复杂字段类型、全局搜索

## 六、风险与约束

### 现有 config 类复用/改造风险
- 不破坏现有调用方：对 ConfigNode/Config/MutableConfig/ConfigWriter 只读复用不改签名。
- ConfigChangeEvent 复用：**裁决新增 BATCH_SAVE 枚举值**表示整页保存（轻量增量，不破坏现有用法）。
- Authority 不复用 DefaultMutableConfig，直接持 Map。

### scene 地基现状约束
- B4 COLUMN O(n²)：ConfigScreen content 区字段数大时布局成本上升。P0/P1 限小字段量（≤30），P2 做虚拟化。
- chrome 主题层未立项：ConfigTheme 委托 SceneChromeTokens/SceneStateColors，不另立 token 体系。
- CHOICE overlay：SceneSelect 已自带 portalAnchored overlay，直接复用，不自造私有浮层。
- scene 已支持 flexGrow（旧注释过时）。

### 1.7.10 环境约束
- Java 8 + jabel desugar：record 经 @Desugar 可用。
- 核心层保持手写或 record，避免引入新注解处理器依赖。
- **YAML 库**：引入 SnakeYAML 2.2（JVM 8+ 兼容），通过 GTNH buildscript 内建 shadow 机制打包（`usesShadowedDependencies = true` + `shadowImplementation`），自动 relocate 包名。SnakeYAML 不在 MC 自带依赖中，必须 shadow 打包。用 SnakeYAML 替换现有自研 YamlConfigLoader/YamlConfigWriter 内部实现，外部 API 不变。

### 软依赖 uilib 的实现方式
- **裁决：P0/P1 同 jar + 运行时检测；思维模型是分开的，独立发布留 P2**
- 核心层编译不依赖 uilib（物理上不 import），独立成包。
- UI 层编译依赖 uilib（直接 import scene/reactive）。
- 入口处运行时检测：调用方检测 ConfigUI/uilib 类是否可加载，可用则 ConfigUI.open，不可用则降级到核心层+LegacyAdapter。
- ConfigUI 类本身不反射加载 scene；反射边界在"是否打开 ConfigUI"这一层，由调用方决定。核心层永不引用 ConfigUI。

## 七、验收检查清单

| # | 验收项 | 验证手段 | 守哪条 |
|---|---|---|---|
| 1 | 零硬依赖 | grep 核心层 import 无 club.heiqi.uilib | 决策"config 零硬依赖" |
| 2 | 三态物理隔离 | 单测：DraftBuffer 与 Authority 内部 Map 不同实例；编辑 draft 后 authority.get 不变 | 决策"三态物理隔离"/I6 |
| 3 | DraftBuffer 零 signal | grep DraftBuffer.java 无 Signal/Computed/ui.reactive/ui.scene | 决策"DraftBuffer 不持 signal" |
| 4 | 保存校验前置 | 单测：非法 draft → save 返回 invalid，文件不变、Authority 不变 | 决策"不跳过校验直写" |
| 5 | 保存失败回滚 | 单测：mock writeAll 抛异常 → authority.snapshotTyped() == 保存前 | 决策"保存失败回滚" |
| 6 | 保存成功广播 | 单测：注册 listener → 成功 save → 收到 ConfigChangeEvent(BATCH_SAVE) | 决策"成功 EventBus 广播" |
| 7 | 无 uilib 独立运行 | 单测：bootstrap→get→legacy→save 全链路无 uilib 在 classpath | 决策"配置页可选" |
| 8 | UI 只绑草稿 | grep ConfigScreen/FieldRenderer 无 authority.applyAll；只经 adapter signal | 决策"渲染层只绑草稿"/I1 |
| 9 | signal-first | 编辑路径：handler 只调 onFieldEdit→signal.set，不直接 node.setText | I1/I2 |
| 10 | 整页事务 | 单测：一次 save = 一次 writeAll | I9 |
| 11 | YAML 注释保真 | 单测：带 # 注释的 yml 文件 round-trip 后注释不丢 | YAML 统一格式 |

## 八、裁决记录（2026-06-28）

| 裁决点 | 选项 | 结果 |
|---|---|---|
| current/draft 真相归属 | 方案A 核心层持真相 vs 方案B UI层持signal | **方案A**：DraftBuffer 持 current+draft 真相，signal 是镜像；保存后 afterSaveSync 显式同步 |
| Authority KV 存储 | 复用 DefaultMutableConfig vs 直接持Map | **直接持Map**：不复用 DefaultMutableConfig，避免误用 dirty/listener 反模式 API |
| EventBus 复用 vs 重造 | 复用现有 vs 全新自研 | **复用现有**：ConfigChangeEvent/ConfigChangeListener 不重造，ConfigEventBus 只作注册表+广播 |
| 事件类型枚举 | 复用 RELOAD vs 新增 BATCH_SAVE vs 新建 SaveEvent | **新增 BATCH_SAVE**：在现有 ChangeType 枚举新增一枚，语义精确，轻量增量不破坏现有 |
| 软依赖打包形态 | 同jar+运行时检测 vs 独立source set | **同jar+运行时检测**（P0/P1）：思维模型分开，独立发布留 P2 |
