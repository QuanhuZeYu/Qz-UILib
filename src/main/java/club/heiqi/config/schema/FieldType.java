package club.heiqi.config.schema;

/**
 * 配置字段类型枚举。
 * 旧五种类型保持兼容；STRUCTURED_LIST 是递归 ValueSpec 的正式入口。
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
    STRUCTURED_LIST;
    // 预留扩展：LONG_TEXT, TABLE, OBJECT, KEY_VALUE_MAP, PRESET_SELECTOR, RAW_EDITOR, ENHANCED_PICKER
}
