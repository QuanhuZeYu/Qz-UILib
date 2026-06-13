# Config 模块

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
