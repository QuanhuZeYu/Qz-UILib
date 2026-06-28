package club.heiqi.config.schema;

/**
 * 配置字段类型枚举。
 * P0 仅支持标量类型，复杂类型预留扩展。
 */
public enum FieldType {
    /** 字符串类型 */
    STRING,
    /** 数值类型（整数或浮点） */
    NUMBER,
    /** 布尔类型 */
    BOOLEAN,
    /** 枚举选择类型，从固定选项列表中取值 */
    CHOICE;
    // 预留扩展：LONG_TEXT, SIMPLE_LIST, TABLE, OBJECT, KEY_VALUE_MAP, PRESET_SELECTOR, RAW_EDITOR, ENHANCED_PICKER
}
