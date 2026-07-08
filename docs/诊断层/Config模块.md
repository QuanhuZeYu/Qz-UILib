# Config 模块

> ⚠️ 旧架构文档：本文反映旧 config 架构（旧决策已删除）。新 config 架构 U1（FormFieldShell/FormTheme 零 config 依赖下沉）/ U2（FormPageShell 组合式页骨架 + ConfigScreen 复用）已落地，U3（renderer 样板收敛）规划中，见 `docs/反馈层/交接.md` §2 与 `docs/反馈层/决策/config-migration-modern.md`。本文待 U3 闭合后整体重写，当前仅作历史背景与反模式参照。

## 架构模型（2026-06-28 新立，旧决策已删除）

现代化配置页采用**三态四层软依赖架构**（旧决策已删除，废弃更早的旧决策）。

- **三态**：Authority（内存权威快照，游戏读取唯一来源）/ DraftBuffer（独立深拷贝草稿，纯数据）/ Persistence（文件，只存权威值）。三者物理隔离，草稿不污染权威。
- **四层**（按模块归属拆核心层 + UI 层，共四类协作者）：
  - 核心层：`ConfigSchema`（Builder DSL 字段声明）/ `Authority`（typed get）/ `DraftBuffer`（纯数据草稿容器）/ `Persistence`（整文件覆写+回滚）/ `LegacyAdapter`（getRawJson/setRawJson 透传）/ `EventBus`（轻量变更通知）
  - UI 层：`ConfigScreen` + 字段控件 + 页面骨架 + 主题 + DraftBuffer→signal 适配
- **软依赖**：`club.heiqi.config` 核心层零硬依赖 uilib（迫不得已独立可运行）；UI 层软依赖 uilib，有 uilib 则加载并用其通用组件搭配置页，无 uilib 降级到纯数据 + LegacyAdapter。
- **职责边界**：uilib 只放通用组件（按钮/滑块/开关/文本框/下拉等），不含配置页业务；配置页业务全在 config 包 UI 层内。
- **保存语义**：校验 → Authority.apply → Persistence.save → 成功 EventBus 广播 / 失败回滚 Authority。
- **持久化格式**（2026-06-28 补充，决策第十节）：新旧配置统一采用 YAML 格式（ConfigFormat.YAML）。引入 SnakeYAML 2.2（JVM 8+ 兼容）作为 YAML 库依赖，通过 GTNH buildscript 内建 shadow 机制打包（`usesShadowedDependencies = true` + `shadowImplementation`，自动 relocate 包名）。SnakeYAML 不在 MC 1.7.10 自带依赖中（不同于 Gson 由 Forge 提供），必须 shadow 打包。现有自研 `YamlConfigLoader`/`YamlConfigWriter` 简化实现（写不出注释、不支持多行字符串/锚点）将被 SnakeYAML 替换内部实现，外部 API（ConfigFormat/ConfigSerializer）不变。配置文件可带注释，round-trip 不丢。
- 详见旧决策（已删除，本节为历史背景）。

> 下方"Modern Config 模板页规划"与"Scene Modern Config 迁移边界"小节描述的是**旧栈实现**，新架构不继承其方案，仅作历史背景与反模式参照。

## 模块定位

- **独立模块**：位于 `club.heiqi.config` 包，独立于 `uilib` 模块
- **未来规划**：设计为可独立拆分的模组，暂时放在主项目中
- **职责边界**：提供比 Forge Configuration 更复杂的配置模式，支持 JSON/YAML 格式

## 核心设计

### 统一入口
- `Config` 类：工厂类，提供配置加载的统一入口
  - `Config.load(File)`: 自动识别格式并加载
  - `Config.parse(String, ConfigFormat)`: 解析字符串
  - `Config.registerLoader(ConfigLoader)`: 注册自定义加载器

### 配置节点
- `ConfigNode` 接口：表示配置树中的节点
  - 支持类型：NULL, STRING, NUMBER, BOOLEAN, LIST, MAP
  - 路径访问：`node.get("server.database.host")`
  - 类型转换：`asInt()`, `asString()`, `asBoolean()` 等
  - 默认值：`asInt(defaultValue)`
  - 判断存在：`has(path)`

### 配置源
- `ConfigSource` 接口：配置数据来源抽象
  - `FileConfigSource`: 文件源
  - `InputStreamConfigSource`: 流源
  - `StringConfigSource`: 字符串源

### 格式支持
- **JSON**: 基于 Gson 实现，支持完整 JSON 语法
- **YAML**: 简化实现，支持基本 YAML 语法（注释、嵌套、列表、引号字符串等）

## 使用示例

### JSON 配置
```java
// 从文件加载
ConfigNode config = Config.load(new File("config.json"));

// 访问配置
String host = config.get("server.host").asString("localhost");
int port = config.get("server.port").asInt(8080);
boolean debug = config.get("debug").asBoolean(false);

// 访问列表
ConfigNode items = config.get("items");
for (int i = 0; i < items.asList().size(); i++) {
    String item = items.get(i).asString();
}

// 访问嵌套结构
String username = config.get("database.credentials.username").asString();
```

### YAML 配置
```java
// 从字符串解析
String yaml = "server:\n" +
              "  host: localhost\n" +
              "  port: 8080\n" +
              "debug: false";
ConfigNode config = Config.parse(yaml, ConfigFormat.YAML);

// 使用方式与 JSON 相同
String host = config.get("server.host").asString();
```

## 扩展性

### 自定义格式
```java
public class TomlConfigLoader implements ConfigLoader {
    @Override
    public ConfigNode load(ConfigSource source) throws ConfigException {
        // 实现 TOML 解析逻辑
    }
    
    @Override
    public ConfigFormat getFormat() {
        return ConfigFormat.TOML; // 需要先在 ConfigFormat 枚举中添加
    }
}

// 注册加载器
Config.registerLoader(new TomlConfigLoader());
```

## 与 Forge Configuration 的区别

| 特性 | Forge Configuration | Config 模块 |
|------|---------------------|------------|
| 支持格式 | Forge 专有格式 | JSON, YAML（可扩展） |
| 数据结构 | 扁平 Key-Value | 嵌套树形结构 |
| 类型支持 | 基础类型 | 基础类型 + 列表 + 映射表 |
| 路径访问 | 不支持 | 支持点号路径 |
| 独立性 | 依赖 Forge | 独立模块 |

## 测试覆盖

- `JsonConfigLoaderTest`: JSON 加载器测试
  - 简单值、嵌套结构、数组、类型转换、默认值等
- `YamlConfigLoaderTest`: YAML 加载器测试
  - 基本语法、注释、列表、嵌套、引号字符串等

## 已知限制

### YAML 实现
- 当前为简化实现，不支持：
  - 锚点（`&anchor`）和别名（`*alias`）
  - 多行字符串（`|` 和 `>`）
  - 复杂的内联语法（inline maps/arrays）
  - 标签（`!!str`, `!!int` 等）
- 如需完整 YAML 支持，建议集成 SnakeYAML 库

## 维护规则

- 保持模块独立性，避免依赖 uilib 模块
- 新增格式支持时，必须同时添加测试用例
- 核心接口变更需评估向后兼容性
