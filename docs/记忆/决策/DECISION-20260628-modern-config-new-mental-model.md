# 决策：现代化配置页全新思维模型——三态四层软依赖架构

## 一、决策摘要

- **废弃** `DECISION-20260623-scene-modern-config-foundation`（scene 现代配置页一期 4 字段 + draft/current 双副本）。
- **确立** 现代化配置页的全新架构思维模型：**三态四层软依赖架构**。
- **明确声明**：不参考旧栈（旧 HTML-like / `ui.dom` 现代配置页 + `ModernConfigTemplateScreen`），完全重新设计。旧栈仅作为反模式参照与性能教训背景，不继承其方案、不搬迁其代码。

本决策只定架构模型，不涉及具体实现。后续 Schema/Authority/DraftBuffer/Persistence/EventBus/UI 层适配均为独立实现任务。

## 二、废弃登记

- **废弃对象**：`DECISION-20260623-scene-modern-config-foundation`
- **废弃理由**：用户要求完全重新设计，不参考旧栈。旧决策的"搬旧栈到 scene"思路（一期 4 字段 + draft/current 双副本 + 直接复用 `MutableConfig` 数据源 + 依赖 scene top-layer/Select 地基）被取代。新模型从配置模块自身的职责边界出发重新定义数据流与状态隔离，不再以"迁旧页面到新栈"为出发点。
- **继承关系**：新决策**取代**旧决策。旧决策中仍有价值的部分（如"草稿与权威物理隔离"的性能教训、`Select/top-layer` 是通用控件地基的判断）作为**背景引用**而非方案继承——新模型自洽定义这些边界，不依赖旧决策的施工顺序。
- 旧决策中"先补 top-layer/overlay 再做 CHOICE"的施工顺序约束**不再适用**：新模型下 `CHOICE` 由 config UI 层基于 uilib 通用组件实现，是否依赖 top-layer 取决于 uilib 自身能力，不在本决策范围。

## 三、架构模型（核心）

### 3.1 模块归属

```
club.heiqi.config          独立模块, 零硬依赖
├─ 核心层    Schema/Authority/DraftBuffer/Persistence/LegacyAdapter/EventBus
│            纯数据, 无 uilib, 迫不得已也能独立运行
└─ UI层      配置样式库(字段控件+页面骨架+主题)
             软依赖 uilib: 有 uilib 则加载, 用其组件搭配置页
             无 uilib 则不加载, 降级到纯数据+LegacyAdapter
```

**关键说明**：

- `club.heiqi.config` 包极其推荐使用 uilib，但**零硬依赖**。无 uilib 时迫不得已也能保证核心层（Authority + LegacyAdapter + EventBus）运行，只是没有现代配置页 UI。
- `uilib` 作为通用 UI 库只放方便的通用组件（按钮/滑块/开关/文本框/下拉等），**不含配置页业务**。
- 配置页业务（字段控件 + 页面骨架 + 主题 + DraftBuffer→signal 适配）**全在 config 包的 UI 层内**。uilib 不反向依赖 config。

### 3.2 各层职责

| 层/组件 | 职责 | 关键约束 |
|---|---|---|
| **ConfigSchema** | Builder DSL 声明字段（路径/类型/默认/约束） | 纯数据声明，无 UI 依赖，核心层和 UI 层共用 |
| **Authority** | 内存权威快照，启动加载 + 写时同步，对外提供 `typed get(path)` | 游戏运行时读取的**唯一来源** |
| **DraftBuffer** | 纯数据草稿容器，每字段持 `current`+`draft` 值，提供 `get/set/validate/isDirty/isError` 查询 | 不持 signal，零响应式依赖 |
| **Persistence** | 文件读写，整文件覆写 | 写失败回滚 Authority |
| **LegacyAdapter** | 透传 `getRawJson`/`setRawJson` | 无 UI 时复杂嵌套对象手动解析 JSON 字符串 |
| **EventBus** | 自研轻量 `ConfigChangeEvent` + 监听器接口 | Authority 变更后通知游戏侧重载 |
| **UI层 ConfigScreen** | 挂载时读 DraftBuffer 字段，为每字段包成 uilib signal，`Computed` 聚合 `dirty/error/canSave`，用 uilib 通用组件搭 scene 配置页 | 只绑 DraftBuffer，不碰 Authority |

### 3.3 三态定义

- **Authority（权威态）**：内存权威快照，游戏读取唯一来源。
- **DraftBuffer（草稿态）**：独立深拷贝副本，纯数据，物理隔离于 Authority。
- **Persistence（持久态）**：文件，只存权威值。

三态是三个独立对象，草稿不污染权威，权威不持有草稿引用。

### 3.4 数据流

```
启动:  文件 --load+Schema校验补默认--> Authority
读取:  游戏 --> Authority.get(path) [typed]
旧式:  调用方 --> LegacyAdapter.getRawJson(path)
开页:  Authority --深拷贝--> DraftBuffer(纯数据)
       UI层 ConfigScreen挂载 --> 读DraftBuffer字段 --> 包成signal --> scene UI
编辑:  handler --> uilib signal.set --> 同步写回 DraftBuffer.set
保存:  DraftBuffer.validate全字段 --> Authority.apply(整页draft) --> Persistence.save
       --> 成功EventBus广播 / 失败回滚Authority
取消:  DraftBuffer丢弃重拷, signal重建
恢复默认: 只动draft值, Authority不动
```

## 四、关键不变量

| 不变量 | 约束 | 对齐 NORTH_STAR |
|---|---|---|
| config 零硬依赖 | 核心层不 import uilib，迫不得已独立可运行 | — |
| UI层软依赖 | config UI 层引用 uilib，但通过可选加载，无 uilib 时不崩 | — |
| 职责边界 | uilib 只放通用组件不沾配置业务；配置页业务全在 config 包内 | I6 精神延伸（契约不穿透） |
| 三态物理隔离 | Authority / DraftBuffer / 文件三者独立对象，草稿不污染权威 | I6 精神延伸（数据层不渗渲染态） |
| 渲染层只绑草稿 | ConfigScreen 不碰 Authority，只绑适配后的 signal | I6（渲染层不直接碰数据层内部） |
| signal-first | 草稿写入经 uilib signal + reactive 事务 | I1/I2 |
| 整页事务保存 | 一次保存 = 校验 → Authority.apply → Persistence.save → 失败回滚 | I9 精神延伸（批处理合并多次写入） |
| 配置页可选 | 不挂载 UI 层不影响核心层 Authority + LegacyAdapter + EventBus | — |
| 保存失败回滚 | 先校验再写入，写失败回滚 Authority（教训来自 ERROR-20260509） | — |

## 五、外部作者使用形态

```java
// 1. 声明 Schema (写一次, 核心和UI共用)
ConfigSchema schema = ConfigSchema.builder("my_mod")
    .section("general")
        .string("name").default("MyMod").build()
        .number("scale").range(0,10).default(1.0).build()
        .bool("enabled").default(true).build()
    .build();

// 2. 无 uilib 时: 只用核心层 + LegacyAdapter
Authority auth = Authority.load(file, schema);
String name = auth.get("general.name");
String raw = auth.legacy().getRawJson("nested.complex"); // 手动解析

// 3. 有 uilib 时: config UI 层一行挂载现代配置页
ConfigUI.open(auth, schema);  // 内部自动建 DraftBuffer + signal适配 + scene UI
```

## 六、对旧栈反模式的规避

- **旧栈问题**：草稿直接写在权威配置对象上，靠 `runWithRollback` 回滚兜底，未提交草稿与已持久化数据共用同一对象。
- **新模型规避**：`DraftBuffer` 是独立深拷贝副本，物理隔离于 `Authority`，彻底消除草稿污染权威。
- **旧栈问题**：一打开全量构建 DOM/Binding/搜索索引，~3FPS 真凶是即时模式 GL 全量重放 + 无视口裁切。
- **新模型规避**：signal 直驱 + Display List 增量 + 分级失效，天然规避全量重放。

## 七、暂缓项（从旧决策继承的边界，非继承方案）

- 远程配置同步（客户端 ↔ 服务端）暂缓，当前集中 UI 层。
- 8 种复杂字段类型（`LONG_TEXT`/`SIMPLE_LIST`/`TABLE`/`OBJECT`/`KEY_VALUE_MAP`/`PRESET_SELECTOR`/`RAW_EDITOR`/`ENHANCED_PICKER`）后续排期。
- 嵌套分类树、全局搜索、字段虚拟化后续排期。
- 但为这些**预留扩展口子**：Schema 可扩展、UI 层字段控件可插拔。

## 八、后续步骤

本决策只定架构模型，以下均为后续独立实现任务：

- 落 Schema Builder DSL 设计
- 落 Authority 核心 API
- 落 DraftBuffer 纯数据容器
- 落 Persistence 整文件覆写 + 回滚
- 落 EventBus 轻量实现
- 落 UI 层 DraftBuffer → signal 适配
- 落 UI 层 scene 配置页骨架

## 影响范围

- `club.heiqi.config` 模块架构方向重定义：从"提供 ConfigNode 配置树 + 旧 ModernConfig 模板页"转向"三态四层软依赖架构"。
- 旧 `ModernConfigTemplateScreen` 及其 DOM 协作者继续作为 legacy 参考实现，**新架构不继承其方案**，后续按新模型重写而非迁移。
- 旧 `DECISION-20260623` 一期施工顺序（先 top-layer 再 CHOICE）不再约束新模型；`CHOICE` 由 config UI 层基于 uilib 通用组件实现。
- 对外文档中关于旧现代配置页 12 模板能力的描述仍指旧 DOM 实现；新架构 UI 层完成前不得宣称等价覆盖。

## 后续注意事项

- 核心层**严禁** import uilib 任何类，违此条即破坏"零硬依赖"不变量。
- UI 层对 uilib 的引用必须走可选加载（运行时检测 / 软引用），无 uilib 时不得抛硬链接错误。
- `DraftBuffer` 不得持有任何 uilib signal 对象；signal 适配是 UI 层挂载时的事，核心层保持纯数据。
- 保存路径必须严格遵循"校验 → Authority.apply → Persistence.save → 失败回滚"顺序，不得跳过校验直接写文件。
- 后续实现任务开工前，先对照本决策《关键不变量》自检，偏离须按 NORTH_STAR《修订纪律》登记。

## 九、施工图与裁决记录（2026-06-28 补充）

施工图已落定：`docs/开发者文档/specs/modern-config-implementation-blueprint.md`

5 项实现裁决：
1. current/draft 真相归属 → 方案A：核心层 DraftBuffer 持真相，signal 是镜像
2. Authority KV 存储 → 直接持 Map，不复用 DefaultMutableConfig
3. EventBus → 复用现有 ConfigChangeEvent/ConfigChangeListener，不重造
4. 事件类型 → 新增 ChangeType.BATCH_SAVE 表示整页保存
5. 软依赖打包 → P0/P1 同 jar + 运行时检测，思维模型分开，独立发布留 P2

详见施工图文档第八节裁决记录。
