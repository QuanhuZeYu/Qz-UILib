package club.heiqi.config;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 可变配置的默认实现
 */
public class DefaultMutableConfig implements MutableConfig {

    private final ConfigFormat format;
    private final ConfigSource source;
    private final Map<ConfigFormat, ConfigWriter> writers;
    private final List<ConfigChangeListener> listeners;

    private Map<String, Object> data;
    private boolean dirty;
    /** 原始带注释的 ConfigNode 树，用于未修改时保留注释 round-trip */
    private ConfigNode originalNode;
    /** 是否仍处于未修改的原始状态：true 时 asImmutable/save 直接用 originalNode，保留注释 */
    private boolean pristine;

    /**
     * 从已有配置节点创建可变配置
     * 
     * @param node 配置节点
     * @param format 配置格式
     * @param source 配置源（可为 null）
     */
    public DefaultMutableConfig(ConfigNode node, ConfigFormat format, ConfigSource source) {
        this.format = format;
        this.source = source;
        this.writers = new HashMap<ConfigFormat, ConfigWriter>();
        this.listeners = new CopyOnWriteArrayList<ConfigChangeListener>();
        this.dirty = false;

        // 注册默认写入器
        writers.put(ConfigFormat.JSON, new JsonConfigWriter());
        writers.put(ConfigFormat.YAML, new YamlConfigWriter());

        // 转换为可变数据结构
        this.data = convertToMutableMap(node);
        // 保留原始带注释的 ConfigNode 树，未修改时 round-trip 可保留注释
        this.originalNode = node;
        this.pristine = true;
    }

    /**
     * 创建空的可变配置
     * 
     * @param format 配置格式
     * @param source 配置源（可为 null）
     */
    public DefaultMutableConfig(ConfigFormat format, ConfigSource source) {
        this(NullConfigNode.INSTANCE, format, source);
        this.data = new HashMap<String, Object>();
    }

    @Override
    public MutableConfig set(String path, Object value) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        String[] parts = path.split("\\.");
        Map<String, Object> current = data;

        // 导航到目标位置
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object next = current.get(part);

            if (!(next instanceof Map)) {
                // 创建中间节点
                Map<String, Object> newMap = new HashMap<String, Object>();
                current.put(part, newMap);
                current = newMap;
            } else {
                current = (Map<String, Object>) next;
            }
        }

        // 设置值
        String key = parts[parts.length - 1];
        Object oldValue = current.get(key);
        Object convertedValue = convertValue(value);
        current.put(key, convertedValue);

        // 标记为已修改
        dirty = true;
        pristine = false;

        // 触发事件
        notifyListeners(new ConfigChangeEvent(path, oldValue, convertedValue, 
                ConfigChangeEvent.ChangeType.SET));

        return this;
    }

    @Override
    public MutableConfig remove(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        String[] parts = path.split("\\.");
        Map<String, Object> current = data;

        // 导航到目标位置
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object next = current.get(part);

            if (!(next instanceof Map)) {
                return this; // 路径不存在
            }
            current = (Map<String, Object>) next;
        }

        // 移除值
        String key = parts[parts.length - 1];
        Object oldValue = current.remove(key);

        if (oldValue != null) {
            dirty = true;
            pristine = false;
            notifyListeners(new ConfigChangeEvent(path, oldValue, null, 
                    ConfigChangeEvent.ChangeType.REMOVE));
        }

        return this;
    }

    @Override
    public MutableConfig clear() {
        Map<String, Object> oldData = this.data;
        this.data = new HashMap<String, Object>();
        dirty = true;
        pristine = false;

        notifyListeners(new ConfigChangeEvent("", oldData, null, 
                ConfigChangeEvent.ChangeType.CLEAR));

        return this;
    }

    @Override
    public void save() throws ConfigException {
        if (source == null) {
            throw new ConfigException("No source file associated with this config");
        }
        saveTo(source);
    }

    @Override
    public void saveTo(ConfigSource target) throws ConfigException {
        ConfigWriter writer = writers.get(format);
        if (writer == null) {
            throw new ConfigException("No writer registered for format: " + format);
        }

        // 转换为不可变节点并写入
        // 未修改（pristine）时直接用原始带注释的 ConfigNode，保留注释 round-trip；
        // 已修改时用 data 重建（注释丢失，已知遗留 TODO）
        ConfigNode node = pristine && originalNode != null ? originalNode : convertToImmutableNode(data);
        writer.write(node, target);

        dirty = false;
    }

    @Override
    public void reload() throws ConfigException {
        if (source == null) {
            throw new ConfigException("No source file associated with this config");
        }

        ConfigLoader loader = Config.getLoader(format);
        if (loader == null) {
            throw new ConfigException("No loader registered for format: " + format);
        }

        ConfigNode node = loader.load(source);
        this.data = convertToMutableMap(node);
        this.originalNode = node;
        this.pristine = true;
        this.dirty = false;

        notifyListeners(new ConfigChangeEvent("", null, data, 
                ConfigChangeEvent.ChangeType.RELOAD));
    }

    @Override
    public ConfigFormat getFormat() {
        return format;
    }

    @Override
    public ConfigSource getSource() {
        return source;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void markClean() {
        this.dirty = false;
    }

    @Override
    public void addChangeListener(ConfigChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeChangeListener(ConfigChangeListener listener) {
        listeners.remove(listener);
    }

    @Override
    public ConfigNode asImmutable() {
        // 未修改时返回原始带注释的 ConfigNode，保留注释；已修改时用 data 重建
        if (pristine && originalNode != null) {
            return originalNode;
        }
        return convertToImmutableNode(data);
    }

    // ConfigNode 接口实现

    @Override
    public NodeType getType() {
        return NodeType.MAP;
    }

    @Override
    public boolean isNull() {
        return data == null || data.isEmpty();
    }

    @Override
    public String asString() {
        return data.toString();
    }

    @Override
    public int asInt() throws ConfigException {
        throw new ConfigException("Cannot convert root config to int");
    }

    @Override
    public long asLong() throws ConfigException {
        throw new ConfigException("Cannot convert root config to long");
    }

    @Override
    public double asDouble() throws ConfigException {
        throw new ConfigException("Cannot convert root config to double");
    }

    @Override
    public boolean asBoolean() throws ConfigException {
        throw new ConfigException("Cannot convert root config to boolean");
    }

    @Override
    public List<ConfigNode> asList() {
        return null;
    }

    @Override
    public Map<String, ConfigNode> asMap() {
        Map<String, ConfigNode> result = new HashMap<String, ConfigNode>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            result.put(entry.getKey(), convertToImmutableNode(entry.getValue()));
        }
        return result;
    }

    @Override
    public ConfigNode get(String path) {
        if (path == null || path.isEmpty()) {
            return this;
        }

        String[] parts = path.split("\\.");
        Object current = data;

        for (String part : parts) {
            if (!(current instanceof Map)) {
                return NullConfigNode.INSTANCE;
            }

            Map<String, Object> map = (Map<String, Object>) current;
            current = map.get(part);

            if (current == null) {
                return NullConfigNode.INSTANCE;
            }
        }

        return convertToImmutableNode(current);
    }

    @Override
    public ConfigNode get(int index) {
        return NullConfigNode.INSTANCE;
    }

    @Override
    public boolean has(String path) {
        return !get(path).isNull();
    }

    @Override
    public int asInt(int defaultValue) {
        return defaultValue;
    }

    @Override
    public long asLong(long defaultValue) {
        return defaultValue;
    }

    @Override
    public double asDouble(double defaultValue) {
        return defaultValue;
    }

    @Override
    public boolean asBoolean(boolean defaultValue) {
        return defaultValue;
    }

    @Override
    public String asString(String defaultValue) {
        return defaultValue;
    }

    // 辅助方法

    /**
     * 转换为可变映射表
     * 
     * @param node 配置节点
     * @return 可变映射表
     */
    private Map<String, Object> convertToMutableMap(ConfigNode node) {
        if (node.getType() != NodeType.MAP) {
            return new HashMap<String, Object>();
        }

        Map<String, ConfigNode> sourceMap = node.asMap();
        if (sourceMap == null) {
            return new HashMap<String, Object>();
        }

        Map<String, Object> result = new HashMap<String, Object>();
        for (Map.Entry<String, ConfigNode> entry : sourceMap.entrySet()) {
            result.put(entry.getKey(), convertToMutableValue(entry.getValue()));
        }

        return result;
    }

    /**
     * 转换为可变值
     * 
     * @param node 配置节点
     * @return 可变值
     */
    private Object convertToMutableValue(ConfigNode node) {
        if (node.isNull()) {
            return null;
        }

        switch (node.getType()) {
            case STRING:
                return node.asString();

            case NUMBER:
                try {
                    return node.asLong();
                } catch (ConfigException e) {
                    return node.asDouble(0.0);
                }

            case BOOLEAN:
                return node.asBoolean(false);

            case LIST:
                List<ConfigNode> sourceList = node.asList();
                List<Object> resultList = new ArrayList<Object>();
                if (sourceList != null) {
                    for (ConfigNode item : sourceList) {
                        resultList.add(convertToMutableValue(item));
                    }
                }
                return resultList;

            case MAP:
                return convertToMutableMap(node);

            default:
                return null;
        }
    }

    /**
     * 转换为不可变节点
     * 
     * @param value 值
     * @return 配置节点
     */
    private ConfigNode convertToImmutableNode(Object value) {
        if (value == null) {
            return NullConfigNode.INSTANCE;
        }

        if (value instanceof String) {
            return new StringConfigNode((String) value);
        }

        if (value instanceof Number) {
            return new NumberConfigNode((Number) value);
        }

        if (value instanceof Boolean) {
            return new BooleanConfigNode((Boolean) value);
        }

        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<ConfigNode> nodes = new ArrayList<ConfigNode>();
            for (Object item : list) {
                nodes.add(convertToImmutableNode(item));
            }
            return new ListConfigNode(nodes);
        }

        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<String, ConfigNode> nodes = new HashMap<String, ConfigNode>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                nodes.put(key, convertToImmutableNode(entry.getValue()));
            }
            return new MapConfigNode(nodes);
        }

        if (value instanceof ConfigNode) {
            return (ConfigNode) value;
        }

        // 默认转为字符串
        return new StringConfigNode(String.valueOf(value));
    }

    /**
     * 转换值（用于 set 方法）
     * 
     * @param value 原始值
     * @return 转换后的值
     */
    private Object convertValue(Object value) {
        if (value instanceof ConfigNode) {
            return convertToMutableValue((ConfigNode) value);
        }
        return value;
    }

    /**
     * 通知监听器
     * 
     * @param event 变更事件
     */
    private void notifyListeners(ConfigChangeEvent event) {
        for (ConfigChangeListener listener : listeners) {
            try {
                listener.onConfigChanged(event);
            } catch (Exception e) {
                // 忽略监听器异常
                e.printStackTrace();
            }
        }
    }

    /**
     * 注册配置写入器
     * 
     * @param writer 写入器
     */
    public void registerWriter(ConfigWriter writer) {
        if (writer != null) {
            writers.put(writer.getFormat(), writer);
        }
    }
}
