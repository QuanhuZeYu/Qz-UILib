package club.heiqi.config;

import java.util.List;
import java.util.Map;

/**
 * 配置节点接口，表示配置树中的一个节点。
 * 支持多种数据类型：原始类型、列表、映射表等。
 */
public interface ConfigNode {

    /**
     * 获取节点类型
     * 
     * @return 节点类型
     */
    NodeType getType();

    /**
     * 判断节点是否为空（null 或未定义）
     * 
     * @return 如果为空返回 true
     */
    boolean isNull();

    /**
     * 获取字符串值
     * 
     * @return 字符串值，如果无法转换则返回 null
     */
    String asString();

    /**
     * 获取整数值
     * 
     * @return 整数值
     * @throws ConfigException 如果无法转换为整数
     */
    int asInt() throws ConfigException;

    /**
     * 获取长整数值
     * 
     * @return 长整数值
     * @throws ConfigException 如果无法转换为长整数
     */
    long asLong() throws ConfigException;

    /**
     * 获取浮点数值
     * 
     * @return 浮点数值
     * @throws ConfigException 如果无法转换为浮点数
     */
    double asDouble() throws ConfigException;

    /**
     * 获取布尔值
     * 
     * @return 布尔值
     * @throws ConfigException 如果无法转换为布尔值
     */
    boolean asBoolean() throws ConfigException;

    /**
     * 获取列表值
     * 
     * @return 列表值，如果不是列表类型则返回 null
     */
    List<ConfigNode> asList();

    /**
     * 获取映射表值
     * 
     * @return 映射表值，如果不是映射表类型则返回 null
     */
    Map<String, ConfigNode> asMap();

    /**
     * 通过路径获取子节点
     * 
     * @param path 路径，使用点号分隔，例如 "server.port"
     * @return 子节点，如果不存在则返回空节点
     */
    ConfigNode get(String path);

    /**
     * 通过索引获取列表元素
     * 
     * @param index 索引
     * @return 列表元素，如果不存在或不是列表则返回空节点
     */
    ConfigNode get(int index);

    /**
     * 判断是否存在指定路径的节点
     * 
     * @param path 路径
     * @return 如果存在返回 true
     */
    boolean has(String path);

    /**
     * 获取整数值，如果无法转换则返回默认值
     * 
     * @param defaultValue 默认值
     * @return 整数值或默认值
     */
    int asInt(int defaultValue);

    /**
     * 获取长整数值，如果无法转换则返回默认值
     * 
     * @param defaultValue 默认值
     * @return 长整数值或默认值
     */
    long asLong(long defaultValue);

    /**
     * 获取浮点数值，如果无法转换则返回默认值
     * 
     * @param defaultValue 默认值
     * @return 浮点数值或默认值
     */
    double asDouble(double defaultValue);

    /**
     * 获取布尔值，如果无法转换则返回默认值
     * 
     * @param defaultValue 默认值
     * @return 布尔值或默认值
     */
    boolean asBoolean(boolean defaultValue);

    /**
     * 获取字符串值，如果为空则返回默认值
     *
     * @param defaultValue 默认值
     * @return 字符串值或默认值
     */
    String asString(String defaultValue);

    /**
     * 获取节点前块注释（节点上方独立行的 {@code #} 注释）。
     *
     * <p>多行块注释以单条 {@link CommentMeta} 返回，{@link CommentMeta#getValue()}
     * 用 {@code \n} 连接各行。无注释或非 YAML 来源时返回 {@code null}。</p>
     *
     * @return 块注释元数据，无则返回 null
     */
    default CommentMeta getBlockComment() {
        return null;
    }

    /**
     * 获取节点同行内联注释（与节点同一行的 {@code #} 注释）。
     *
     * @return 内联注释元数据，无则返回 null
     */
    default CommentMeta getInlineComment() {
        return null;
    }

    /**
     * 获取 collection 节点（Map/List）的末尾注释。
     *
     * <p>仅 Map/List 节点可能携带；标量节点始终返回 {@code null}。</p>
     *
     * @return 末尾注释元数据，无则返回 null
     */
    default CommentMeta getEndComment() {
        return null;
    }

    /**
     * 节点类型枚举
     */
    enum NodeType {
        /** 空值 */
        NULL,
        /** 字符串 */
        STRING,
        /** 数字 */
        NUMBER,
        /** 布尔值 */
        BOOLEAN,
        /** 列表 */
        LIST,
        /** 映射表 */
        MAP
    }
}
