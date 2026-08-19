package club.heiqi.uilib.font.util;

/**
 * Unicode 文本控制/格式字符统一分类器 —— 所有渲染解析器的控制字符单处真相。
 *
 * <h3>口径（对齐现代文本引擎：有语义的解析执行，无语义的渲染为不可见）</h3>
 * <ul>
 *   <li>{@link CharClass#NEWLINE}：{@code \n \r \v \f NEL(U+0085) LS(U+2028) PS(U+2029)}，
 *       统一折叠为内部换行（{@code \r\n} 折叠为一个换行）；</li>
 *   <li>{@link CharClass#TAB}：{@code \t}，固定 {@value #TAB_WIDTH_SPACES} 个空格列宽（测量层换算）；</li>
 *   <li>{@link CharClass#FOLDABLE_SPACE}：ASCII 空格、NBSP、OGHAM SPACE MARK、U+2000..U+200A、
 *       NARROW NO-BREAK SPACE、MEDIUM MATHEMATICAL SPACE、全角空格 —— 断词/折叠统一口径；</li>
 *   <li>{@link CharClass#SOFT_BREAK}：ZWSP(U+200B)，零宽软断行机会；</li>
 *   <li>{@link CharClass#SOFT_HYPHEN}：U+00AD，断行处显示连字符，行内零宽不可见；</li>
 *   <li>{@link CharClass#JOINER}：ZWNJ/ZWJ(U+200C/U+200D)，零宽连字控制（无连字布局引擎时跳过渲染）；</li>
 *   <li>{@link CharClass#VARIATION_SELECTOR}：U+FE00..U+FE0F / U+E0100..U+E01EF，零宽、附着前一字形；</li>
 *   <li>{@link CharClass#COMBINING_MARK}：组合标记（Mn/Mc/Me），与前一字符合并成簇、不单独断行；</li>
 *   <li>{@link CharClass#BIDI_CONTROL}：LRM/RLM/ALM/LRE/RLE/PDF/LRO/RLO/LRI/RLI/FSI/PDI，
 *       单独留位（备将来 bidi 升级），当前按不可见处理；</li>
 *   <li>{@link CharClass#INVISIBLE}：其余 C0/C1 控制、BOM(U+FEFF)、WORD JOINER(U+2060)、
 *       INVISIBLE SEPARATOR/TIMES/PLUS(U+2061..U+2064)、Cf 格式字符全集（阿拉伯数字/经文格式、
 *       废弃交互注释锚点、音乐符号格式、埃及圣书体格式、速记格式、语言标签 TAG 字符）、
 *       韩文填充(U+115F/1160/3164)、非字符(FDD0..FDEF/各平面末尾 FFFE/FFFF)与孤立代理项 ——
 *       无语义，测量零宽、渲染跳过。</li>
 * </ul>
 *
 * <p>分类为纯静态 O(1) 判断，无平台/字体依赖，可被渲染层（font）与 scene 装配层共用。
 * 行为默认开启，不设开关（公共口径）。码点以 0x 数值形式书写，避免源文件内的控制字符字面量。</p>
 */
public final class UnicodeTextClassifier {

    /** {@code \t} 的固定列宽（空格数，4 空格业界默认口径）。 */
    public static final int TAB_WIDTH_SPACES = 4;

    private UnicodeTextClassifier() {
    }

    /** 控制字符分类。 */
    public enum CharClass {
        /** 硬换行类（折叠为内部 {@code '\n'}）。 */
        NEWLINE,
        /** 制表符（固定空格列宽）。 */
        TAB,
        /** 可折叠断词空白家族。 */
        FOLDABLE_SPACE,
        /** ZWSP 零宽软断行机会。 */
        SOFT_BREAK,
        /** 软连字符（断行处显示 '-'）。 */
        SOFT_HYPHEN,
        /** ZWNJ/ZWJ 零宽连字控制。 */
        JOINER,
        /** 变体选择符（零宽，附着前一字符）。 */
        VARIATION_SELECTOR,
        /** 组合标记（Mn/Mc/Me，与前一字符合并成簇）。 */
        COMBINING_MARK,
        /** bidi 方向控制（当前无 bidi 布局，按不可见处理；单列备升级）。 */
        BIDI_CONTROL,
        /** 无语义不可见控制/格式字符（C0/C1 残留、BOM、WORD JOINER 等）。 */
        INVISIBLE,
        /** 普通可见字符。 */
        REGULAR
    }

    /**
     * 分类码点。
     *
     * @param codepoint Unicode 码点
     * @return 分类（永不为 null）
     */
    public static CharClass classify(int codepoint) {
        switch (codepoint) {
            case 0x000A: // \n
            case 0x000D: // \r
            case 0x000B: // \v
            case 0x000C: // \f
            case 0x0085: // NEL
            case 0x2028: // LINE SEPARATOR
            case 0x2029: // PARAGRAPH SEPARATOR
                return CharClass.NEWLINE;
            case 0x0009: // \t
                return CharClass.TAB;
            case 0x0020: // SPACE
            case 0x00A0: // NO-BREAK SPACE
            case 0x1680: // OGHAM SPACE MARK
            case 0x202F: // NARROW NO-BREAK SPACE
            case 0x205F: // MEDIUM MATHEMATICAL SPACE
            case 0x3000: // IDEOGRAPHIC SPACE
                return CharClass.FOLDABLE_SPACE;
            case 0x180B: // MONGOLIAN FREE VARIATION SELECTOR ONE
            case 0x180C: // MONGOLIAN FREE VARIATION SELECTOR TWO
            case 0x180D: // MONGOLIAN FREE VARIATION SELECTOR THREE
            case 0x180F: // MONGOLIAN FREE VARIATION SELECTOR FOUR
                return CharClass.VARIATION_SELECTOR;
            case 0x180E: // MONGOLIAN VOWEL SEPARATOR（历史上 Zs，现 Cf，窄空白语义）
                return CharClass.FOLDABLE_SPACE;
            case 0x200B: // ZERO WIDTH SPACE
                return CharClass.SOFT_BREAK;
            case 0x00AD: // SOFT HYPHEN
                return CharClass.SOFT_HYPHEN;
            case 0x200C: // ZERO WIDTH NON-JOINER
            case 0x200D: // ZERO WIDTH JOINER
                return CharClass.JOINER;
            case 0x200E: // LEFT-TO-RIGHT MARK
            case 0x200F: // RIGHT-TO-LEFT MARK
            case 0x061C: // ARABIC LETTER MARK
            case 0x202A: // LEFT-TO-RIGHT EMBEDDING
            case 0x202B: // RIGHT-TO-LEFT EMBEDDING
            case 0x202C: // POP DIRECTIONAL FORMATTING
            case 0x202D: // LEFT-TO-RIGHT OVERRIDE
            case 0x202E: // RIGHT-TO-LEFT OVERRIDE
            case 0x2066: // LEFT-TO-RIGHT ISOLATE
            case 0x2067: // RIGHT-TO-LEFT ISOLATE
            case 0x2068: // FIRST STRONG ISOLATE
            case 0x2069: // POP DIRECTIONAL ISOLATE
                return CharClass.BIDI_CONTROL;
            case 0xFEFF: // ZERO WIDTH NO-BREAK SPACE / BOM
            case 0x2060: // WORD JOINER
            case 0x2061: // FUNCTION APPLICATION
            case 0x2062: // INVISIBLE TIMES
            case 0x2063: // INVISIBLE SEPARATOR
            case 0x2064: // INVISIBLE PLUS
                return CharClass.INVISIBLE;
            default:
                break;
        }
        if (codepoint >= 0x2000 && codepoint <= 0x200A) {
            return CharClass.FOLDABLE_SPACE;
        }
        if ((codepoint >= 0xFE00 && codepoint <= 0xFE0F)
                || (codepoint >= 0xE0100 && codepoint <= 0xE01EF)) {
            return CharClass.VARIATION_SELECTOR;
        }
        if (codepoint <= 0x1F || (codepoint >= 0x7F && codepoint <= 0x9F)) {
            return CharClass.INVISIBLE;
        }
        // ===== 其余 Unicode 格式字符（Cf 全集）+ 不可见填充 + 非字符防御 =====
        // （本区间判断覆盖 C0/C1 之外的全部格式/控制语义码点，静默不可见：
        //   阿拉伯数字/经文格式、废弃交互注释锚点、音乐符号格式、埃及圣书体格式、
        //   速记格式、语言标签 TAG 字符、U+2065 未分配控制段、
        //   非字符 FDD0..FDEF 与各平面末尾 FFFE/FFFF、韩文填充、孤立代理项。）
        if ((codepoint >= 0x0600 && codepoint <= 0x0605)
                || codepoint == 0x06DD || codepoint == 0x070F
                || (codepoint >= 0x0890 && codepoint <= 0x0891) || codepoint == 0x08E2
                || codepoint == 0x110BD || codepoint == 0x110CD
                || (codepoint >= 0x13430 && codepoint <= 0x13438)
                || (codepoint >= 0x1BCA0 && codepoint <= 0x1BCA3)
                || (codepoint >= 0x1D173 && codepoint <= 0x1D17A)
                || (codepoint >= 0xE0001 && codepoint <= 0xE007F)
                || codepoint == 0x2065
                || (codepoint >= 0xFFF9 && codepoint <= 0xFFFB)
                || (codepoint >= 0xFDD0 && codepoint <= 0xFDEF)
                || codepoint == 0x115F || codepoint == 0x1160 || codepoint == 0x3164
                || (codepoint >= 0xD800 && codepoint <= 0xDFFF)
                || (codepoint & 0xFFFF) == 0xFFFE || (codepoint & 0xFFFF) == 0xFFFF) {
            return CharClass.INVISIBLE;
        }
        int type = Character.getType(codepoint);
        if (type == Character.NON_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK) {
            return CharClass.COMBINING_MARK;
        }
        return CharClass.REGULAR;
    }

    /** @return 是否为换行类控制字符（{@code \n \r \v \f NEL LS PS}） */
    public static boolean isLineBreak(int codepoint) {
        return classify(codepoint) == CharClass.NEWLINE;
    }

    /** @return 是否为制表符 {@code \t} */
    public static boolean isTab(int codepoint) {
        return classify(codepoint) == CharClass.TAB;
    }

    /** @return 是否为可折叠断词空白家族（空格/NBSP/U+1680/U+2000..200A/U+202F/U+205F/U+3000） */
    public static boolean isFoldableSpace(int codepoint) {
        return classify(codepoint) == CharClass.FOLDABLE_SPACE;
    }

    /** @return 是否为软断行机会（ZWSP 或软连字符） */
    public static boolean isSoftBreakOpportunity(int codepoint) {
        CharClass cls = classify(codepoint);
        return cls == CharClass.SOFT_BREAK || cls == CharClass.SOFT_HYPHEN;
    }

    /** @return 是否为软连字符 U+00AD */
    public static boolean isSoftHyphen(int codepoint) {
        return classify(codepoint) == CharClass.SOFT_HYPHEN;
    }

    /**
     * @return 是否为簇延续字符（变体选择符/组合标记，附着前一字符，不得落行首）
     */
    public static boolean isClusterContinuation(int codepoint) {
        CharClass cls = classify(codepoint);
        return cls == CharClass.VARIATION_SELECTOR || cls == CharClass.COMBINING_MARK;
    }

    /**
     * @return 是否为无语义剥离类（C0/C1 残留、bidi 控制、BOM 等，输入/测量/渲染一律剔除）
     */
    public static boolean isStripped(int codepoint) {
        CharClass cls = classify(codepoint);
        return cls == CharClass.INVISIBLE || cls == CharClass.BIDI_CONTROL;
    }

    /**
     * @return 是否渲染跳过（零宽无字形）：换行类、剥离类、软断行、连字控制、变体选择符
     */
    public static boolean isRenderSkipped(int codepoint) {
        CharClass cls = classify(codepoint);
        return cls == CharClass.NEWLINE || cls == CharClass.INVISIBLE || cls == CharClass.BIDI_CONTROL
                || cls == CharClass.SOFT_BREAK || cls == CharClass.SOFT_HYPHEN || cls == CharClass.JOINER
                || cls == CharClass.VARIATION_SELECTOR;
    }

    /**
     * @return 是否测量零宽（渲染跳过；tab 不算零宽，它有列宽）
     */
    public static boolean isZeroWidth(int codepoint) {
        return isRenderSkipped(codepoint);
    }

    /**
     * @return 是否为断词分隔（可折叠空白 + tab）
     */
    public static boolean isWordBoundary(int codepoint) {
        CharClass cls = classify(codepoint);
        return cls == CharClass.FOLDABLE_SPACE || cls == CharClass.TAB;
    }
}
