package club.heiqi.uilib.internal.chat3.viewmodel;

/**
 * chat3 行级 markdown 轻量规则(设计稿 §3.5 + §10.1 四轮审查拍板补做,方向 = markdown
 * 可解析聊天框):chat3 无 markdown 解析器,本类以「行级规则分派」模式落地两件事——
 * 无序列表「• 」前缀行与块级公式独占行,与既有 {@link ChatCodeSpanSplitter}/引用行检测
 * 同模式,为未来完整 markdown 解析留口({@link Kind} 枚举按行级规则扩展)。
 *
 * <p>规则(纯 JVM,无 MC/GL 依赖):</p>
 * <ul>
 *   <li><b>无序列表</b>:行首 {@code ^\s*[-*+] } → 渲染为「• 」前缀 + 内容,前缀用正文色;
 *       层级缩进按标记前导空格数 / 2 简单映射(2 空格 = 1 级,每级输出 2 个空格);</li>
 *   <li><b>有序列表</b>:{@code N. } 识别为 {@link Kind#ORDERED_LIST},保留序号原样渲染
 *       (不做前缀替换;缩进/样式留给未来完整解析);</li>
 *   <li><b>块级公式</b>:以 {@code $$} 开头(可配尾部 {@code $$})或 {@code $...$} 独占行 →
 *       渲染为 TeX 源(经 {@link club.heiqi.uilib.font.layout.TextSegment#forLatex} 走既有
 *       LaTeX 渲染链),行节点上下各 4px 间距、左对齐(不居中);</li>
 *   <li>code 段内不套用列表规则:检测发生在行级、段解析之前,行首反引号不命中列表标记,
 *       行内 code 内容也不会被行级检测触碰。</li>
 * </ul>
 */
public final class ChatMarkdownLineRule {

    /** 行级规则类别(未来完整 markdown 解析器的行级分派口)。 */
    public enum Kind {
        /** 普通行(原样渲染)。 */
        NONE,
        /** 无序列表行(减号/星号/加号标记)。 */
        UNORDERED_LIST,
        /** 有序列表行(N. 标记,当前保留序号原样渲染)。 */
        ORDERED_LIST,
        /** 块级公式独占行($$...$$ / $...$)。 */
        BLOCK_MATH
    }

    /** 行级规则匹配结果。 */
    public static final class Match {

        private final Kind kind;
        /** 列表层级(前导空格数 / 2;非列表行恒 0)。 */
        private final int level;
        /** 去标记后的列表内容(非列表行恒 null)。 */
        private final String content;
        /** 块级公式 TeX 源(非公式行恒 null)。 */
        private final String latexSource;

        private Match(Kind kind, int level, String content, String latexSource) {
            this.kind = kind;
            this.level = level;
            this.content = content;
            this.latexSource = latexSource;
        }

        /** @return 规则类别 */
        public Kind getKind() {
            return kind;
        }

        /** @return 列表层级(2 前导空格 = 1 级) */
        public int getLevel() {
            return level;
        }

        /** @return 去标记后的列表内容 */
        public String getContent() {
            return content;
        }

        /** @return 块级公式 TeX 源 */
        public String getLatexSource() {
            return latexSource;
        }
    }

    /** 无规则命中(普通行)单例。 */
    public static final Match NONE = new Match(Kind.NONE, 0, null, null);

    /** 无序列表标记字符。 */
    private static boolean isBulletMark(char c) {
        return c == '-' || c == '*' || c == '+';
    }

    private ChatMarkdownLineRule() {
    }

    /**
     * 行级规则分派。
     *
     * @param line 显示行(行切分后,含格式码的纯文本行;null/空 → {@link #NONE})
     * @return 规则匹配(无命中 = {@link #NONE})
     */
    public static Match classify(String line) {
        if (line == null || line.isEmpty()) {
            return NONE;
        }
        int leading = 0;
        while (leading < line.length() && line.charAt(leading) == ' ') {
            leading++;
        }
        int level = leading / 2;
        String body = line.substring(leading);
        // 剥离尾部 § 格式码对(渲染文本尾常带 §r 重置码,块级公式边界判定不得被其污染)
        body = stripTrailingFormatCodes(body);
        if (body.isEmpty()) {
            return NONE;
        }
        // 块级公式:$$ 开头(可配尾部 $$)
        if (body.startsWith("$$")) {
            String source = body.substring(2);
            if (source.endsWith("$$")) {
                source = source.substring(0, source.length() - 2);
            }
            return new Match(Kind.BLOCK_MATH, level, null, source);
        }
        // 无序列表:[-*+] 后跟空格
        if (body.length() >= 2 && isBulletMark(body.charAt(0)) && body.charAt(1) == ' ') {
            return new Match(Kind.UNORDERED_LIST, level, body.substring(2), null);
        }
        // 有序列表:N. 保留序号(识别保留,不做前缀替换)
        int digits = 0;
        while (digits < body.length() && Character.isDigit(body.charAt(digits))) {
            digits++;
        }
        if (digits > 0 && digits + 1 < body.length()
                && body.charAt(digits) == '.' && body.charAt(digits + 1) == ' ') {
            return new Match(Kind.ORDERED_LIST, level, body, null);
        }
        // 块级公式:$...$ 独占行(整行恰好一对 $ 包裹)
        if (body.startsWith("$") && body.length() >= 3 && body.endsWith("$")
                && countDollars(body) == 2) {
            return new Match(Kind.BLOCK_MATH, level, null, body.substring(1, body.length() - 1));
        }
        return NONE;
    }

    private static int countDollars(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '$') {
                count++;
            }
        }
        return count;
    }

    /** 剥离行尾连续 §x 格式码对(零宽、无视觉语义)。 */
    private static String stripTrailingFormatCodes(String text) {
        int end = text.length();
        while (end >= 2 && text.charAt(end - 2) == '\u00a7') {
            end -= 2;
        }
        return end == text.length() ? text : text.substring(0, end);
    }
}
