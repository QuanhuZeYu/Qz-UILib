# Config 模块使用指南

## 快速开始

Config 模块是一个独立的配置管理库，支持 JSON 和 YAML 格式，提供比 Forge Configuration 更强大的嵌套结构支持。

**核心特性**：
- 支持只读配置（`ConfigNode`）和可变配置（`MutableConfig`）
- 内存数据与文件自动同步
- 配置变更监听和通知
- 链式调用 API
- scene 配置页可为结构化列表 member 注册 SearchPicker beta editor；每个 screen 独立 registry，定制后冻结

> SearchPicker 与 5 参 `ConfigUI.buildScreen` 当前为 beta API，不属于 LTS 稳定承诺。

### 只读配置（基本使用）

```java
import club.heiqi.config.Config;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.ConfigException;
import java.io.File;

// 从文件加载（自动识别格式）
try {
    ConfigNode config = Config.load(new File("config.json"));
    
    // 读取配置值
    String host = config.get("server.host").asString("localhost");
    int port = config.get("server.port").asInt(8080);
    boolean debug = config.get("debug").asBoolean(false);
    
    System.out.println("Server: " + host + ":" + port);
} catch (ConfigException e) {
    e.printStackTrace();
}
```

### JSON 配置示例

**config.json**:
```json
{
  "server": {
    "host": "localhost",
    "port": 8080,
    "ssl": {
      "enabled": true,
      "certificate": "/path/to/cert.pem"
    }
  },
  "database": {
    "host": "db.example.com",
    "port": 3306,
    "credentials": {
      "username": "admin",
      "password": "secret"
    }
  },
  "features": ["auth", "logging", "cache"],
  "debug": false
}
```

**读取配置**:
```java
ConfigNode config = Config.load(new File("config.json"));

// 嵌套访问
String dbHost = config.get("database.host").asString();
int dbPort = config.get("database.port").asInt();
String username = config.get("database.credentials.username").asString();

// 访问列表
ConfigNode features = config.get("features");
for (int i = 0; i < features.asList().size(); i++) {
    String feature = features.get(i).asString();
    System.out.println("Feature: " + feature);
}

// 检查配置是否存在
if (config.has("server.ssl.enabled")) {
    boolean sslEnabled = config.get("server.ssl.enabled").asBoolean();
    if (sslEnabled) {
        String cert = config.get("server.ssl.certificate").asString();
        // 启用 SSL
    }
}
```

### YAML 配置示例

**config.yaml**:
```yaml
# 服务器配置
server:
  host: localhost
  port: 8080
  ssl:
    enabled: true
    certificate: /path/to/cert.pem

# 数据库配置
database:
  host: db.example.com
  port: 3306
  credentials:
    username: admin
    password: secret

# 功能开关
features:
  - auth
  - logging
  - cache

# 调试模式
debug: false
```

**读取配置**（与 JSON 使用方式相同）:
```java
ConfigNode config = Config.load(new File("config.yaml"));
String host = config.get("server.host").asString();
// ... 其他操作与 JSON 相同
```

### 从字符串解析

```java
import club.heiqi.config.ConfigFormat;

String jsonString = "{\"name\": \"test\", \"value\": 42}";
ConfigNode config = Config.parse(jsonString, ConfigFormat.JSON);

String yamlString = "name: test\nvalue: 42";
ConfigNode config2 = Config.parse(yamlString, ConfigFormat.YAML);
```

### 类型转换与默认值

```java
ConfigNode config = Config.load(new File("config.json"));

// 使用默认值（如果配置不存在或转换失败）
String host = config.get("server.host").asString("localhost");
int port = config.get("server.port").asInt(8080);
double timeout = config.get("server.timeout").asDouble(30.0);
boolean debug = config.get("debug").asBoolean(false);

// 检查配置是否存在
if (config.has("optional.feature")) {
    String feature = config.get("optional.feature").asString();
    // 使用可选配置
}

// 手动处理异常
try {
    int value = config.get("some.number").asInt();
    // 使用 value
} catch (ConfigException e) {
    // 处理转换错误
    System.err.println("Invalid number format");
}
```

## 可变配置（推荐）

### 创建和修改配置

```java
import club.heiqi.config.Config;
import club.heiqi.config.MutableConfig;
import club.heiqi.config.ConfigFormat;
import java.io.File;

// 创建新的配置文件
File configFile = new File("config/mymod.json");
MutableConfig config = Config.createMutable(configFile, ConfigFormat.JSON);

// 设置配置值（支持链式调用）
config.set("server.host", "localhost")
      .set("server.port", 8080)
      .set("database.credentials.username", "admin")
      .set("database.credentials.password", "secret")
      .set("debug", true);

// 保存到文件
config.save();
```

### 加载和修改现有配置

```java
// 从文件加载
MutableConfig config = Config.loadMutable(new File("config/mymod.json"));

// 读取值
String host = config.get("server.host").asString("localhost");
int port = config.get("server.port").asInt(8080);

// 修改值
config.set("server.port", 9090);
config.set("features.newFeature", true);

// 移除配置项
config.remove("debug");

// 检查是否有未保存的修改
if (config.isDirty()) {
    config.save();
}
```

### 配置自动同步

```java
// 场景：游戏运行时修改配置

MutableConfig config = Config.loadMutable(new File("config/settings.json"));

// 1. 玩家修改配置
config.set("graphics.quality", "high");
config.set("audio.volume", 80);

// 2. 自动保存（脏标记跟踪）
if (config.isDirty()) {
    config.save();
    System.out.println("配置已保存");
}

// 3. 重新加载（撤销未保存的修改）
config.set("graphics.quality", "low");  // 临时修改
config.reload();  // 从文件重新加载，放弃未保存的修改
```

### 配置变更监听

```java
MutableConfig config = Config.loadMutable(new File("config/settings.json"));

// 添加监听器
config.addChangeListener(new ConfigChangeListener() {
    @Override
    public void onConfigChanged(ConfigChangeEvent event) {
        System.out.println("配置项 " + event.getPath() + " 已变更");
        System.out.println("  旧值: " + event.getOldValue());
        System.out.println("  新值: " + event.getNewValue());
        System.out.println("  类型: " + event.getType());
        
        // 根据变更类型执行操作
        switch (event.getType()) {
            case SET:
                // 配置项被设置
                applyConfigChange(event.getPath(), event.getNewValue());
                break;
            case REMOVE:
                // 配置项被移除
                revertToDefault(event.getPath());
                break;
            case RELOAD:
                // 配置被重新加载
                reloadAllSettings();
                break;
        }
    }
});

// 修改配置会触发监听器
config.set("volume", 80);  // 触发 onConfigChanged
```

### 实用模式

#### 模式 1：配置管理器

```java
public class ModConfig {
    private static MutableConfig config;
    
    public static void init(File configFile) throws ConfigException {
        // 加载或创建配置
        config = Config.loadMutable(configFile);
        
        // 设置默认值（如果不存在）
        if (!config.has("version")) {
            config.set("version", 1);
        }
        if (!config.has("server.host")) {
            config.set("server.host", "localhost");
        }
        
        // 保存默认值
        if (config.isDirty()) {
            config.save();
        }
        
        // 添加监听器
        config.addChangeListener(new ConfigChangeListener() {
            @Override
            public void onConfigChanged(ConfigChangeEvent event) {
                // 实时应用配置变更
                applyConfig();
            }
        });
    }
    
    public static String getServerHost() {
        return config.get("server.host").asString("localhost");
    }
    
    public static void setServerHost(String host) throws ConfigException {
        config.set("server.host", host);
        config.save();
    }
    
    public static void save() throws ConfigException {
        config.save();
    }
}
```

#### 模式 2：定期自动保存

```java
public class AutoSaveConfig {
    private final MutableConfig config;
    private final Timer saveTimer;
    
    public AutoSaveConfig(File file) throws ConfigException {
        this.config = Config.loadMutable(file);
        
        // 每 5 分钟自动保存
        this.saveTimer = new Timer(true);
        saveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (config.isDirty()) {
                    try {
                        config.save();
                        System.out.println("配置已自动保存");
                    } catch (ConfigException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, 5 * 60 * 1000, 5 * 60 * 1000);
    }
    
    public MutableConfig getConfig() {
        return config;
    }
    
    public void shutdown() throws ConfigException {
        saveTimer.cancel();
        if (config.isDirty()) {
            config.save();
        }
    }
}
```

### 高级用法

#### 遍历配置映射

```java
ConfigNode config = Config.load(new File("config.json"));
ConfigNode section = config.get("database");

if (section.getType() == ConfigNode.NodeType.MAP) {
    for (Map.Entry<String, ConfigNode> entry : section.asMap().entrySet()) {
        System.out.println(entry.getKey() + " = " + entry.getValue().asString());
    }
}
```

#### 遍历配置列表

```java
ConfigNode items = config.get("items");

if (items.getType() == ConfigNode.NodeType.LIST) {
    for (ConfigNode item : items.asList()) {
        System.out.println("Item: " + item.asString());
    }
}
```

#### 从输入流加载

```java
import club.heiqi.config.ConfigSource;
import java.io.InputStream;

InputStream stream = getClass().getResourceAsStream("/config.json");
ConfigNode config = Config.load(
    ConfigSource.fromInputStream(stream, "resource:config.json"),
    ConfigFormat.JSON
);
```

## 配置节点类型

| 类型 | 说明 | 示例 |
|------|------|------|
| NULL | 空值或不存在 | `null`, `~` (YAML) |
| STRING | 字符串 | `"hello"`, `'world'` |
| NUMBER | 数字（整数或浮点数） | `42`, `3.14` |
| BOOLEAN | 布尔值 | `true`, `false`, `yes`, `no` (YAML) |
| LIST | 列表/数组 | `[1, 2, 3]`, YAML 列表 |
| MAP | 映射表/对象 | `{"key": "value"}`, YAML 映射 |

## Schema 结构化列表

配置页 schema 可用递归 `ValueSpec` 声明结构化对象列表。下面的声明表达
`List<Object{id:String,members:List<String>}>`：

```java
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.Values;

ConfigSchema schema = ConfigSchema.builder("my-mod")
        .section("general")
        .structuredList("rules", Values.object(
                Values.member("id", Values.string()),
                Values.member("members", Values.list(Values.string())))
                .withIdentityMember("id"))
        .endSection()
        .build();
```

`STRUCTURED_LIST` 在 Authority、Draft 和 YAML 边界严格校验节点类型；object 中未声明的
member 会在读取、编辑和写盘时保留。默认 scene renderer 提供新增、删除、上移/下移、标量
、`List<String>` member 编辑、`List<CHOICE>` 受控多选，以及字段级 reset。choice 已知值按 schema
顺序显示并去重；值中未知字符串追加“（已失效）”，只能取消删除，保存前仍按精确元素路径校验。
自定义 `DraftValidator` 可使用
`general.rules[0].members[1]` 这类嵌套路径返回错误，配置页会映射到对应 member。

对象列表可用 `withIdentityMember("id")` 声明可靠身份；identity member 构建阶段只允许稳定可比较的
`STRING`、`NUMBER`、`BOOLEAN` 或 `CHOICE` 标量，`LIST`/`OBJECT` 等容器会直接拒绝。reset/reload
会按唯一、非空身份复用内部 keyed 行；身份重复、缺失或为 null 时不猜测。未声明 identity 时仅对同位置
深值相等的行复用，业务 identity 不会直接作为 scene key。

## API 对比

### ConfigNode vs MutableConfig

| 特性 | ConfigNode（只读） | MutableConfig（可变） |
|------|-------------------|---------------------|
| 读取配置 | ✓ | ✓ |
| 修改配置 | ✗ | ✓ (set/remove/clear) |
| 保存到文件 | ✗ | ✓ (save/saveTo) |
| 重新加载 | ✗ | ✓ (reload) |
| 变更监听 | ✗ | ✓ (addChangeListener) |
| 脏标记 | ✗ | ✓ (isDirty) |
| 线程安全 | ✓（不可变） | ✗（需外部同步） |
| 使用场景 | 临时读取、传递配置 | 配置管理、实时修改 |

**选择建议**：
- 只需要读取配置 → 使用 `ConfigNode`
- 需要修改并保存配置 → 使用 `MutableConfig`
- 需要配置热更新 → 使用 `MutableConfig` + 监听器

## 注意事项

1. **YAML 限制**：当前 YAML 实现为简化版本，不支持锚点、别名、多行字符串等高级特性
2. **路径分隔符**：使用点号 `.` 分隔嵌套路径，如 `"database.credentials.username"`
3. **类型安全**：建议使用带默认值的方法（如 `asInt(defaultValue)`）避免异常
4. **线程安全**：
   - `ConfigNode` 是不可变的，可以安全地在多线程环境中共享
   - `MutableConfig` 不是线程安全的，需要外部同步
5. **内存占用**：`MutableConfig` 会在内存中保持完整的配置树，大型配置文件请注意内存使用

## 与 Forge Configuration 对比

使用 Config 模块的优势：
- 支持复杂嵌套结构，不局限于扁平 Key-Value
- 支持标准格式（JSON/YAML），易于编辑和版本控制
- 独立于 Forge，可用于任何 Java 项目
- 提供路径访问和类型转换等便利功能

何时使用 Forge Configuration：
- 需要与 Forge Mod 配置 GUI 集成
- 配置结构简单，只需扁平 Key-Value
- 需要 Forge 的配置同步机制
