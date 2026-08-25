package club.heiqi.uilib.ui.scene.control;

/**
 * 输入长度上限口径。
 *
 * <p>{@link SceneTextInput} 的 maxLength 默认按 {@link #CODEPOINT}(Unicode 码点,代理对算 1)
 * 判定;{@link #UTF16} 按 Java char 单元(UTF-16 code unit)判定,与原版
 * GuiTextField.maxStringLength 口径一致(emoji 等代理对字符占 2 单元)。</p>
 *
 * <p>向后兼容:未显式指定时恒为 {@link #CODEPOINT},既有默认行为不变。</p>
 */
public enum MaxLengthUnit {

    /** 按 Unicode 码点计数(默认口径,代理对算 1 个字符)。 */
    CODEPOINT,

    /** 按 UTF-16 char 单元计数(原版 maxStringLength 口径,emoji 占 2 单元)。 */
    UTF16
}