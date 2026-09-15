package club.heiqi.config.schema;

/**
 * 递归配置值的节点种类。
 *
 * <p>与 {@link FieldType} 同规：新增常量一律追加在末尾，不移动既有常量的序号。</p>
 */
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
    OBJECT,
    /** 64 位整数标量（内存形态 {@code Long}，判读规则见 {@link IntegerCodec}）。 */
    INTEGER
}
