package club.heiqi.config.schema;

/** 递归配置值的节点种类。 */
public enum ValueKind {
    /** 字符串标量。 */
    STRING,
    /** 有限数值标量。 */
    NUMBER,
    /** 布尔标量。 */
    BOOLEAN,
    /** 固定选项字符串标量。 */
    CHOICE,
    /** 有序列表。 */
    LIST,
    /** 字符串键对象。 */
    OBJECT
}
