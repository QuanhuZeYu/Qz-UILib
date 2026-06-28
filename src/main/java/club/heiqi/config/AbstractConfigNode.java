package club.heiqi.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置节点的抽象基类，提供通用实现。
 */
abstract class AbstractConfigNode implements ConfigNode {

    /** 节点前块注释（单条，多行块注释 value 含 \n）；null 表示无注释 */
    private CommentMeta blockComment;
    /** 节点同行内联注释；null 表示无 */
    private CommentMeta inlineComment;
    /** collection 节点末尾注释（仅 Map/List 用）；null 表示无 */
    private CommentMeta endComment;

    @Override
    public CommentMeta getBlockComment() {
        return blockComment;
    }

    @Override
    public CommentMeta getInlineComment() {
        return inlineComment;
    }

    @Override
    public CommentMeta getEndComment() {
        return endComment;
    }

    /** 设置块注释，供 Loader 写入 */
    void setBlockComment(CommentMeta blockComment) {
        this.blockComment = blockComment;
    }

    /** 设置内联注释，供 Loader 写入 */
    void setInlineComment(CommentMeta inlineComment) {
        this.inlineComment = inlineComment;
    }

    /** 设置末尾注释，供 Loader 写入 */
    void setEndComment(CommentMeta endComment) {
        this.endComment = endComment;
    }

    @Override
    public boolean isNull() {
        return getType() == NodeType.NULL;
    }

    @Override
    public String asString() {
        if (isNull()) {
            return null;
        }
        return String.valueOf(getRawValue());
    }

    @Override
    public int asInt() throws ConfigException {
        Object value = getRawValue();
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                throw new ConfigException("Cannot convert '" + value + "' to int", e);
            }
        }
        throw new ConfigException("Cannot convert " + getType() + " to int");
    }

    @Override
    public long asLong() throws ConfigException {
        Object value = getRawValue();
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                throw new ConfigException("Cannot convert '" + value + "' to long", e);
            }
        }
        throw new ConfigException("Cannot convert " + getType() + " to long");
    }

    @Override
    public double asDouble() throws ConfigException {
        Object value = getRawValue();
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                throw new ConfigException("Cannot convert '" + value + "' to double", e);
            }
        }
        throw new ConfigException("Cannot convert " + getType() + " to double");
    }

    @Override
    public boolean asBoolean() throws ConfigException {
        Object value = getRawValue();
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value instanceof String) {
            String str = ((String) value).toLowerCase();
            if ("true".equals(str) || "yes".equals(str) || "1".equals(str)) {
                return true;
            }
            if ("false".equals(str) || "no".equals(str) || "0".equals(str)) {
                return false;
            }
        }
        throw new ConfigException("Cannot convert " + getType() + " to boolean");
    }

    @Override
    public List<ConfigNode> asList() {
        if (getType() != NodeType.LIST) {
            return null;
        }
        return getListValue();
    }

    @Override
    public Map<String, ConfigNode> asMap() {
        if (getType() != NodeType.MAP) {
            return null;
        }
        return getMapValue();
    }

    @Override
    public ConfigNode get(String path) {
        if (path == null || path.isEmpty()) {
            return this;
        }

        String[] parts = path.split("\\.");
        ConfigNode current = this;

        for (String part : parts) {
            if (current.getType() != NodeType.MAP) {
                return NullConfigNode.INSTANCE;
            }
            Map<String, ConfigNode> map = current.asMap();
            if (map == null || !map.containsKey(part)) {
                return NullConfigNode.INSTANCE;
            }
            current = map.get(part);
        }

        return current;
    }

    @Override
    public ConfigNode get(int index) {
        if (getType() != NodeType.LIST) {
            return NullConfigNode.INSTANCE;
        }
        List<ConfigNode> list = asList();
        if (list == null || index < 0 || index >= list.size()) {
            return NullConfigNode.INSTANCE;
        }
        return list.get(index);
    }

    @Override
    public boolean has(String path) {
        return !get(path).isNull();
    }

    @Override
    public int asInt(int defaultValue) {
        try {
            return asInt();
        } catch (ConfigException e) {
            return defaultValue;
        }
    }

    @Override
    public long asLong(long defaultValue) {
        try {
            return asLong();
        } catch (ConfigException e) {
            return defaultValue;
        }
    }

    @Override
    public double asDouble(double defaultValue) {
        try {
            return asDouble();
        } catch (ConfigException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean asBoolean(boolean defaultValue) {
        try {
            return asBoolean();
        } catch (ConfigException e) {
            return defaultValue;
        }
    }

    @Override
    public String asString(String defaultValue) {
        String value = asString();
        return value != null ? value : defaultValue;
    }

    /**
     * 获取原始值对象
     * 
     * @return 原始值
     */
    protected abstract Object getRawValue();

    /**
     * 获取列表值（仅当类型为 LIST 时）
     * 
     * @return 列表值
     */
    protected List<ConfigNode> getListValue() {
        return null;
    }

    /**
     * 获取映射表值（仅当类型为 MAP 时）
     * 
     * @return 映射表值
     */
    protected Map<String, ConfigNode> getMapValue() {
        return null;
    }
}

/**
 * 空配置节点实现
 */
class NullConfigNode extends AbstractConfigNode {

    static final NullConfigNode INSTANCE = new NullConfigNode();

    private NullConfigNode() {}

    @Override
    public NodeType getType() {
        return NodeType.NULL;
    }

    @Override
    protected Object getRawValue() {
        return null;
    }
}

/**
 * 字符串配置节点实现
 */
class StringConfigNode extends AbstractConfigNode {

    private final String value;

    StringConfigNode(String value) {
        this.value = value;
    }

    @Override
    public NodeType getType() {
        return NodeType.STRING;
    }

    @Override
    protected Object getRawValue() {
        return value;
    }
}

/**
 * 数字配置节点实现
 */
class NumberConfigNode extends AbstractConfigNode {

    private final Number value;

    NumberConfigNode(Number value) {
        this.value = value;
    }

    @Override
    public NodeType getType() {
        return NodeType.NUMBER;
    }

    @Override
    protected Object getRawValue() {
        return value;
    }
}

/**
 * 布尔配置节点实现
 */
class BooleanConfigNode extends AbstractConfigNode {

    private final Boolean value;

    BooleanConfigNode(Boolean value) {
        this.value = value;
    }

    @Override
    public NodeType getType() {
        return NodeType.BOOLEAN;
    }

    @Override
    protected Object getRawValue() {
        return value;
    }
}

/**
 * 列表配置节点实现
 */
class ListConfigNode extends AbstractConfigNode {

    private final List<ConfigNode> value;

    ListConfigNode(List<ConfigNode> value) {
        this.value = value != null ? value : new ArrayList<ConfigNode>();
    }

    @Override
    public NodeType getType() {
        return NodeType.LIST;
    }

    @Override
    protected Object getRawValue() {
        return value;
    }

    @Override
    protected List<ConfigNode> getListValue() {
        return Collections.unmodifiableList(value);
    }
}

/**
 * 映射表配置节点实现
 */
class MapConfigNode extends AbstractConfigNode {

    private final Map<String, ConfigNode> value;

    MapConfigNode(Map<String, ConfigNode> value) {
        this.value = value != null ? value : new HashMap<String, ConfigNode>();
    }

    @Override
    public NodeType getType() {
        return NodeType.MAP;
    }

    @Override
    protected Object getRawValue() {
        return value;
    }

    @Override
    protected Map<String, ConfigNode> getMapValue() {
        return Collections.unmodifiableMap(value);
    }
}
