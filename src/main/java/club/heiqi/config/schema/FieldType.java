package club.heiqi.config.schema;

/**
 * 配置字段类型枚举。
 * 旧五种类型保持兼容；STRUCTURED_LIST 是递归 ValueSpec 的正式入口。
 *
 * <p>新增常量一律追加在末尾：ordinal 是既有制品可观测的事实，移动既有常量的序号会无声改变
 * 依赖它的调用方。</p>
 */
public enum FieldType {
    /** 字符串类型 */
    STRING,
    /** 数值类型（整数或浮点） */
    NUMBER,
    /** 布尔类型 */
    BOOLEAN,
    /** 枚举选择类型，从固定选项列表中取值 */
    CHOICE,
    /** 字符串列表类型（如字体排序、字符规则） */
    SIMPLE_LIST,
    /** 由 ValueSpec 描述的 List<Object> 类型 */
    STRUCTURED_LIST,
    /**
     * 整数类型：值语义是 64 位有符号整数（内存形态 {@link Long}），落盘形态是十进制整数字面量
     * （{@code 16777216}），范围校验与 UI 编辑都按整数语义。
     *
     * <p>与 {@link #NUMBER} 的分工：NUMBER 是「有限数值」（内存形态 {@code Double}，落盘必然走
     * 浮点形态，{@code 16777216.0}）；INTEGER 是「整数」，落盘不带小数点与指数。既有 NUMBER
     * 字段的读值、写值、UI 表现与持久数据不因本类型增加而改变——{@code color()} 那种「值语义是
     * 整数 0xRRGGBB 但 type 仍是 NUMBER」的字段继续留在 NUMBER，它的值域与控件形态由 widget
     * 声明表达，不需要新类型。</p>
     *
     * <p>判读规则（什么算整数值、{@code 1.6777216E7} 与 {@code 1.0} 怎么读）见
     * {@link IntegerCodec}；落盘形态由 {@code YamlConfigWriter} 按值类型（{@link Long} ⇒
     * {@code Tag.INT}）决定，本枚举不参与序列化判断。</p>
     */
    INTEGER;
    // 预留扩展：LONG_TEXT, TABLE, OBJECT, KEY_VALUE_MAP, PRESET_SELECTOR, RAW_EDITOR, ENHANCED_PICKER
}
