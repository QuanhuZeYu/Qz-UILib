package club.heiqi.uilib.ui.control;

/**
 * HTML-like 文本输入控件的输入类型。
 *
 * <p>对标浏览器 `&lt;input type&gt;` 的常用子集：普通文本、密码掩码与数字输入。
 * 类型只影响字符接受规则与显示方式，真实值始终通过 {@code getText()} 返回。</p>
 */
public enum DocumentInputType {

    /**
     * 普通单行文本，接受任意非控制字符。
     */
    TEXT("text"),

    /**
     * 密码输入，显示时以掩码字符替换真实字符，真实值仍可读取。
     */
    PASSWORD("password"),

    /**
     * 数字输入，仅接受数字及数值语法字符（符号、小数点、科学计数 e/E）。
     */
    NUMBER("number");

    private final String attributeValue;

    DocumentInputType(String attributeValue) {
        this.attributeValue = attributeValue;
    }

    /**
     * 返回对应的 HTML `type` 属性值。
     *
     * @return type 属性值
     */
    public String getAttributeValue() {
        return attributeValue;
    }

    /**
     * 判断指定码点是否被当前输入类型接受。
     *
     * <p>所有类型都拒绝控制字符与换行/制表；NUMBER 在此基础上仅放行数字与数值语法字符。</p>
     *
     * @param codepoint 待判断码点
     * @return 是否接受该码点
     */
    public boolean acceptsCodepoint(int codepoint) {
        if (Character.isISOControl(codepoint) || codepoint == '\n' || codepoint == '\r' || codepoint == '\t') {
            return false;
        }
        if (this == NUMBER) {
            return isNumericInputCodepoint(codepoint);
        }
        return true;
    }

    /**
     * 判断码点是否属于数字输入允许的字符集合。
     *
     * @param codepoint 待判断码点
     * @return 是否为数值语法字符
     */
    private static boolean isNumericInputCodepoint(int codepoint) {
        if (codepoint >= '0' && codepoint <= '9') {
            return true;
        }
        return codepoint == '.' || codepoint == '-' || codepoint == '+' || codepoint == 'e' || codepoint == 'E';
    }
}
