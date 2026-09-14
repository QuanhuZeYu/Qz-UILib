package club.heiqi.config.schema;

/**
 * 颜色字段的 {@code 0xRRGGBB} ↔ {@code #RRGGBB} 编解码（纯函数，无状态、不依赖 scene/控件）。
 *
 * <p>值的语义是整数 {@code 0xRRGGBB}（{@code [0, 0xFFFFFF]}），与 NUMBER 字段共用既有校验通道
 * （{@code DraftBuffer} 的范围校验），本类只负责「值 ↔ 用户能看懂的文本」这一层：
 * {@link #format(int)} 是规范显示形态的唯一写法，{@link #parse(String)} 是输入形态的唯一判读点。
 * 渲染器不得另写字面量规则。</p>
 *
 * <h3>接受的输入形态</h3>
 * <ul>
 *   <li>{@code #RRGGBB}——规范显示形态本身，十六进制字母大小写皆可；</li>
 *   <li>{@code 0xRRGGBB} / {@code 0XRRGGBB}——代码与 schema helper 里惯用的写法（照抄也能过）；</li>
 *   <li>{@code RRGGBB}——不带前缀但含十六进制字母的六位形态（如 {@code 40E6FF}）；</li>
 *   <li>纯十进制（如 {@code 4253439}）——颜色字段改造前数字框的唯一形态，必须继续可用；
 *       前导零不影响判读（{@code "0000255"} = 255）。</li>
 * </ul>
 *
 * <h3>歧义裁决（六位纯数字，如 {@code 123456}）</h3>
 * <p>既可读作十六进制也可读作十进制，本类取<b>十进制</b>：该形态在颜色字段出现之前就是十进制，
 * 取十六进制会让存量值与用户习惯静默换义；要十六进制就加 {@code #} 或 {@code 0x}
 * （前者正是本字段的显示形态）。</p>
 *
 * <h3>非法输入</h3>
 * <p>位数不符、含非十六进制字符、带符号 / 小数点 / 指数、越界（&lt;0 或 &gt;0xFFFFFF）一律返回
 * {@code null}；<b>不夹取、不四舍五入、不回落默认值</b>——非法原文由调用方按原文写入草稿，
 * 走 {@code DraftBuffer} 既有的「值不是有效数字 / 数值超出范围」报错路径。</p>
 */
public final class HexColorCodec {

    /** 颜色值域上界（{@code 0xFFFFFF}）；颜色字段的 range 上界与 {@link #format(int)} 的编码位宽同源。 */
    public static final int MAX_RGB = 0xFFFFFF;

    /** 十六进制位数（{@code RRGGBB}）。 */
    private static final int HEX_DIGITS = 6;

    /** 十进制有效位数上界（{@code 16777215} 为 8 位，多于此必越界，可提前拒绝）。 */
    private static final int DECIMAL_DIGITS_MAX = 8;

    /** 纯静态工具，禁止实例化。 */
    private HexColorCodec() {
    }

    /**
     * 编码：{@code 0xRRGGBB} → {@code #RRGGBB}（大写十六进制，取低 24 位）。
     *
     * @param rgb 颜色值
     * @return 规范显示文本，恒为 7 字符
     */
    public static String format(int rgb) {
        char[] out = new char[HEX_DIGITS + 1];
        out[0] = '#';
        for (int i = 0; i < HEX_DIGITS; i++) {
            int nibble = (rgb >>> ((HEX_DIGITS - 1 - i) * 4)) & 0xF;
            out[i + 1] = (char) (nibble < 10 ? '0' + nibble : 'A' + (nibble - 10));
        }
        return new String(out);
    }

    /**
     * 解码：接受类头列出的四种形态（首尾空白忽略）。
     *
     * @param text 用户输入文本，可为 null
     * @return 颜色值；非法时 {@code null}（不抛异常、不返回近似值）
     */
    public static Integer parse(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.charAt(0) == '#') {
            return parseHex(trimmed.substring(1));
        }
        if (hasHexPrefix(trimmed)) {
            return parseHex(trimmed.substring(2));
        }
        // 无前缀六位：只有含十六进制字母才读作十六进制，纯数字留给十进制（歧义裁决见类头）
        if (trimmed.length() == HEX_DIGITS && containsHexLetter(trimmed)) {
            return parseHex(trimmed);
        }
        return parseDecimal(trimmed);
    }

    /** @return 是否带 {@code 0x} / {@code 0X} 前缀（裸 {@code "0x"} 不是合法颜色，交给 parseHex 拒绝） */
    private static boolean hasHexPrefix(String text) {
        return text.length() > 2 && (text.startsWith("0x") || text.startsWith("0X"));
    }

    /**
     * 六位十六进制 → 值；位数不符或含非十六进制字符返回 {@code null}。
     *
     * @param digits 前缀之后的数字部分
     * @return 颜色值，非法时 null
     */
    private static Integer parseHex(String digits) {
        if (digits.length() != HEX_DIGITS) {
            return null;
        }
        int value = 0;
        for (int i = 0; i < HEX_DIGITS; i++) {
            int digit = hexDigit(digits.charAt(i));
            if (digit < 0) {
                return null;
            }
            value = (value << 4) | digit;
        }
        return Integer.valueOf(value);
    }

    /**
     * 纯十进制 → 值；只允许数字字符、值域 {@code [0, 0xFFFFFF]}。前导零既不加值也不计位数。
     *
     * @param text 非空的待解析文本（空串已由 {@link #parse(String)} 拒绝）
     * @return 颜色值，非法或越界时 null
     */
    private static Integer parseDecimal(String text) {
        int firstSignificant = 0;
        while (firstSignificant < text.length() && text.charAt(firstSignificant) == '0') {
            firstSignificant++;
        }
        if (firstSignificant == text.length()) {
            // 全为前导零（"0" / "000000"）：值 0，位数不再受限
            return Integer.valueOf(0);
        }
        String digits = text.substring(firstSignificant);
        if (digits.length() > DECIMAL_DIGITS_MAX) {
            return null;
        }
        int value = 0;
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
            value = value * 10 + (c - '0');
        }
        return value > MAX_RGB ? null : Integer.valueOf(value);
    }

    /**
     * @param c 单个字符
     * @return 该字符的十六进制值，非法返回 {@code -1}
     */
    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    /**
     * @param text 待检文本
     * @return 是否含十六进制字母（a-f / A-F）——用于把「六位纯数字」排除出十六进制分支
     */
    private static boolean containsHexLetter(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                return true;
            }
        }
        return false;
    }
}
