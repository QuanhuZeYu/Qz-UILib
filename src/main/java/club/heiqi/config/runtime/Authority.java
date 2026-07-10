package club.heiqi.config.runtime;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.ConfigSource;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.FieldType;
import club.heiqi.config.MutableConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 内存权威快照，保存事务的唯一"真相源"。
 *
 * <p>关键约束：</p>
 * <ul>
 *   <li>直接持 {@code Map<String, Object>}，不复用 {@code DefaultMutableConfig}。</li>
 *   <li>Schema 字段存 typed 值（String / Double / Boolean），按全路径 {@code "section.key"} 为键。</li>
 *   <li>非 Schema 顶层 key 存 {@link ConfigNode} 子树，原样保留供 {@link LegacyAdapter} 透传。</li>
 *   <li>{@link #applyAll(Map)} 为包级私有，强制保存走 {@link ConfigManager}。</li>
 *   <li>{@link #snapshotTyped()} 供 {@link DraftBuffer} 深拷贝种子。</li>
 *   <li>{@link #getRaw(String)} / {@link #putRaw(String, Object)} 供 {@link LegacyAdapter} 受控访问。</li>
 *   <li>公开与包级读写统一持事务锁；容器和 {@link ConfigNode} 读出口均返回防御副本。</li>
 * </ul>
 *
 * <p>本类零依赖 uilib。</p>
 */
public final class Authority {

    private final ConfigSchema schema;
    /** Schema 字段 path → typed value；非 Schema 顶层 key → ConfigNode 子树 */
    private Map<String, Object> typedValues;
    /** Authority、Legacy 与 ConfigManager 保存事务共享的锁域 */
    private final Object transactionLock;
    private final LegacyAdapter legacyAdapter;

    private Authority(ConfigSchema schema, Map<String, Object> typedValues) {
        this.schema = schema;
        this.typedValues = typedValues;
        this.transactionLock = new Object();
        this.legacyAdapter = new LegacyAdapter(this);
    }

    /**
     * 从文件启动加载权威快照。
     *
     * <p>文件不存在或为空时，Schema 字段全部补 {@link FieldSpec#defaultValue()}。
     * 文件存在时按 {@link ConfigFormat#YAML} 解析；Schema 字段从解析树中按 path 取值并转 typed，
     * 缺失补默认；非 Schema 顶层 key 原样存 {@link ConfigNode} 子树。</p>
     *
     * @param file   配置文件，可为 null 或不存在
     * @param schema 配置 schema
     * @return 权威快照
     * @throws ConfigException 文件存在但解析失败
     */
    public static Authority load(File file, ConfigSchema schema) throws ConfigException {
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }

        ConfigNode root = null;
        if (file != null && file.isFile() && file.length() > 0) {
            root = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        }

        Map<String, Object> typed = new HashMap<String, Object>();

        // Schema 字段：取 typed 值，缺失补默认
        for (FieldSpec field : schema.allFields()) {
            Object typedValue;
            ConfigNode node = root != null ? root.get(field.path()) : null;
            if (node != null && !node.isNull()) {
                typedValue = extractTyped(node, field.type());
            } else {
                typedValue = normalizeDefault(field.defaultValue(), field.type());
            }
            typed.put(field.path(), typedValue);
        }

        // 非 Schema 顶层 key：原样存 ConfigNode 子树
        if (root != null && root.getType() == ConfigNode.NodeType.MAP) {
            Map<String, ConfigNode> rootMap = root.asMap();
            if (rootMap != null) {
                for (Map.Entry<String, ConfigNode> entry : rootMap.entrySet()) {
                    String key = entry.getKey();
                    if (!schema.containsTopLevel(key)) {
                        typed.put(key, entry.getValue());
                    }
                }
            }
        }

        return new Authority(schema, typed);
    }

    /**
     * 按 path 取 typed 值。
     *
     * @param path 字段全路径
     * @return typed 值，不存在返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String path) {
        synchronized (transactionLock) {
            return (T) ValueCopy.copyOf(typedValues.get(path));
        }
    }

    /**
     * 取字符串值。
     *
     * @param path 字段路径
     * @return 字符串值，不存在返回 null
     */
    public String getString(String path) {
        synchronized (transactionLock) {
            Object value = typedValues.get(path);
            return value == null ? null : String.valueOf(value);
        }
    }

    /**
     * 取数值。
     *
     * @param path 字段路径
     * @return double 值，不存在或非数值返回 0.0
     */
    public double getNumber(String path) {
        synchronized (transactionLock) {
            Object value = typedValues.get(path);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return 0.0;
        }
    }

    /**
     * 取布尔值。
     *
     * @param path 字段路径
     * @return boolean 值，不存在返回 false
     */
    public boolean getBool(String path) {
        synchronized (transactionLock) {
            Object value = typedValues.get(path);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            return false;
        }
    }

    /**
     * @return 关联的 schema
     */
    public ConfigSchema schema() {
        return schema;
    }

    /**
     * @return 旧式透传适配器
     */
    public LegacyAdapter legacy() {
        return legacyAdapter;
    }

    /**
     * 替换全部 typed 值，包级私有。仅 {@link ConfigManager} 在保存事务中调用。
     *
     * @param newValues 新的 typed 值映射
     */
    void applyAll(Map<String, Object> newValues) {
        synchronized (transactionLock) {
            if (newValues == null) {
                this.typedValues = new HashMap<String, Object>();
            } else {
                // 先完整构造副本再替换，异常时保留原 Authority
                this.typedValues = ValueCopy.copyMapValues(newValues);
            }
        }
    }

    /**
     * typed 值深层防御拷贝，供写盘与 DraftBuffer 种子使用。
     *
     * @return 新 Map
     */
    Map<String, Object> snapshotTyped() {
        synchronized (transactionLock) {
            return ValueCopy.copyMapValues(typedValues);
        }
    }

    /**
     * 事务旁路检测用深快照：标量/List/Map 深拷贝，ConfigNode 经 YAML 序列化重建。
     *
     * @return 与内部存储隔离的 Map
     */
    Map<String, Object> deepSnapshotTyped() {
        return snapshotTyped();
    }

    /**
     * 与 {@link #deepSnapshotTyped()} 结果按 path 深度相等比较（用于 validator 旁路检测）。
     */
    boolean matchesDeepSnapshot(Map<String, Object> snapshot) {
        synchronized (transactionLock) {
            if (snapshot == null || typedValues.size() != snapshot.size()) {
                return false;
            }
            for (Map.Entry<String, Object> e : typedValues.entrySet()) {
                if (!valueDeepEquals(e.getValue(), snapshot.get(e.getKey()))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static boolean valueDeepEquals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof ConfigNode && b instanceof ConfigNode) {
            try {
                String sa = club.heiqi.config.ConfigSerializer.toString(
                        (ConfigNode) a, club.heiqi.config.ConfigFormat.YAML);
                String sb = club.heiqi.config.ConfigSerializer.toString(
                        (ConfigNode) b, club.heiqi.config.ConfigFormat.YAML);
                return Objects.equals(sa, sb);
            } catch (RuntimeException e) {
                return false;
            }
        }
        if (a instanceof List && b instanceof List) {
            List<?> la = (List<?>) a;
            List<?> lb = (List<?>) b;
            if (la.size() != lb.size()) {
                return false;
            }
            for (int i = 0; i < la.size(); i++) {
                if (!valueDeepEquals(la.get(i), lb.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof Map && b instanceof Map) {
            Map<?, ?> ma = (Map<?, ?>) a;
            Map<?, ?> mb = (Map<?, ?>) b;
            if (ma.size() != mb.size()) {
                return false;
            }
            for (Map.Entry<?, ?> e : ma.entrySet()) {
                if (!valueDeepEquals(e.getValue(), mb.get(e.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(a, b);
    }

    /**
     * 取原始子树，供 {@link LegacyAdapter}。包级私有。
     *
     * <p>Schema 字段返回标量包装成的 {@link ConfigNode}；非 Schema 字段按 path 导航子树。</p>
     *
     * @param path 字段路径
     * @return ConfigNode，不存在返回 null
     */
    ConfigNode getRaw(String path) {
        synchronized (transactionLock) {
            ConfigNode node = getRawLocked(path);
            return node == null ? null : (ConfigNode) ValueCopy.copyOf(node);
        }
    }

    /**
     * 已持事务锁时导航原始子树。
     */
    private ConfigNode getRawLocked(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (schema.containsPath(path)) {
            return scalarToNode(typedValues.get(path));
        }
        String[] parts = path.split("\\.");
        Object top = typedValues.get(parts[0]);
        if (!(top instanceof ConfigNode)) {
            return null;
        }
        ConfigNode node = (ConfigNode) top;
        if (parts.length == 1) {
            return node;
        }
        StringBuilder sub = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (i > 1) {
                sub.append(".");
            }
            sub.append(parts[i]);
        }
        ConfigNode child = node.get(sub.toString());
        return child == null || child.isNull() ? null : child;
    }

    /**
     * 写回原始子树，供 {@link LegacyAdapter}。包级私有。
     *
     * <p>Schema 字段从 {@link ConfigNode} 提取 typed 值存入；非 Schema 顶层 key 直接存
     * {@link ConfigNode}；非 Schema 嵌套路径通过 {@link MutableConfig} 重建子树。</p>
     *
     * @param path  字段路径
     * @param value ConfigNode 子树
     */
    void putRaw(String path, Object value) {
        synchronized (transactionLock) {
            if (path == null || path.isEmpty()) {
                return;
            }
            if (schema.containsPath(path)) {
                if (value instanceof ConfigNode) {
                    ConfigNode node = (ConfigNode) value;
                    if (!node.isNull()) {
                        typedValues.put(path, extractTyped(node, schema.field(path).type()));
                    }
                }
                return;
            }
            String[] parts = path.split("\\.");
            String topKey = parts[0];
            if (parts.length == 1) {
                if (value instanceof ConfigNode) {
                    typedValues.put(topKey, ValueCopy.copyOf(value));
                }
                return;
            }
            // 嵌套非 Schema 路径：用 MutableConfig 重建子树
            Object existing = typedValues.get(topKey);
            MutableConfig mc = Config.createMutable(ConfigFormat.YAML);
            if (existing instanceof ConfigNode && !((ConfigNode) existing).isNull()) {
                loadSubtreeInto(mc, (ConfigNode) existing);
            }
            StringBuilder sub = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) {
                    sub.append(".");
                }
                sub.append(parts[i]);
            }
            mc.set(sub.toString(), value);
            typedValues.put(topKey, ValueCopy.copyOf(mc.asImmutable()));
        }
    }

    /**
     * @return 与 ConfigManager、LegacyAdapter 共享的事务锁
     */
    Object transactionLock() {
        return transactionLock;
    }

    /**
     * 把 ConfigNode 子树的内容灌入 MutableConfig（仅处理 MAP 类型）。
     */
    private static void loadSubtreeInto(MutableConfig mc, ConfigNode subtree) {
        if (subtree.getType() != ConfigNode.NodeType.MAP) {
            return;
        }
        Map<String, ConfigNode> map = subtree.asMap();
        if (map == null) {
            return;
        }
        for (Map.Entry<String, ConfigNode> entry : map.entrySet()) {
            mc.set(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 把 typed 标量包装成 ConfigNode。
     */
    private static ConfigNode scalarToNode(Object typedValue) {
        if (typedValue == null) {
            return null;
        }
        MutableConfig mc = Config.createMutable(ConfigFormat.YAML);
        mc.set("_", typedValue);
        return mc.get("_");
    }

    /**
     * 从 ConfigNode 按 FieldType 提取 typed 值。
     */
    private static Object extractTyped(ConfigNode node, FieldType type) {
        if (node == null || node.isNull()) {
            return null;
        }
        switch (type) {
            case STRING:
                return node.asString();
            case NUMBER:
                return node.asDouble(0.0);
            case BOOLEAN:
                return node.asBoolean(false);
            case CHOICE:
                return node.asString();
            case SIMPLE_LIST: {
                List<ConfigNode> raw = node.asList();
                if (raw == null) {
                    return new ArrayList<String>();
                }
                List<String> out = new ArrayList<String>(raw.size());
                for (ConfigNode n : raw) {
                    out.add(n.asString());
                }
                return out;
            }
            default:
                return node.asString();
        }
    }

    /**
     * 把 FieldSpec.defaultValue() 规范化为 typed 值。包级可见，供 {@link DraftBuffer} 复用。
     *
     * @param defaultValue 原始默认值
     * @param type         字段类型
     * @return typed 值（String / Double / Boolean）
     */
    static Object normalizeDefault(Object defaultValue, FieldType type) {
        if (defaultValue == null) {
            switch (type) {
                case STRING:
                case CHOICE:
                    return "";
                case NUMBER:
                    return 0.0;
                case BOOLEAN:
                    return false;
                case SIMPLE_LIST:
                    return new ArrayList<String>();
                default:
                    return null;
            }
        }
        switch (type) {
            case STRING:
            case CHOICE:
                return String.valueOf(defaultValue);
            case NUMBER:
                if (defaultValue instanceof Number) {
                    return ((Number) defaultValue).doubleValue();
                }
                try {
                    return Double.parseDouble(String.valueOf(defaultValue));
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            case BOOLEAN:
                if (defaultValue instanceof Boolean) {
                    return defaultValue;
                }
                return Boolean.parseBoolean(String.valueOf(defaultValue));
            default:
                return defaultValue;
        }
    }
}
