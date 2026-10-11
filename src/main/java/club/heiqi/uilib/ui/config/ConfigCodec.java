package club.heiqi.uilib.ui.config;

/** 声明类型与 JDK 对象树之间的边界。树仅含 null、标量、List 和 String 键 Map。
 * read 的 path 供自定义转换定位错误；不得从输入决定任意 Java 类的实例化。
 * snapshot 必须复制并深冻结向外发布的内容。业务对象应不可变；对象 builder
 * 会基于已冻结字段重新调用 factory，factory 不得重新引入可变共享内容。
 * 自定义业务值须按配置语义实现 equals/hashCode，供相同值加载及 set 去重通知。 */
public interface ConfigCodec<T> {
    T read(Object value, String path);
    Object write(T value);
    T snapshot(T value);
    /** 相对字段路径的声明说明；用于首次生成节点，不覆盖用户注释。 */
    default java.util.Map<String, String> comments() { return java.util.Collections.emptyMap(); }
    /** 复合 codec 按实际 children 展开路径，包含列表索引和映射键。 */
    default java.util.Map<String, String> comments(T value) { return comments(); }
}
