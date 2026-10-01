package club.heiqi.uilib.font.config;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.util.UnicodeTextClassifier;

/**
 * 六槽字体指派：把「语言类别（西文/中文）× 风格（正常/粗体/斜体）」映射到已发现字体族名。
 *
 * <p>这是用户可配置的<b>字面选择</b>：空串表示未指派，退回引擎的自动字体排序与字重匹配。
 * 指派到某槽的字体族将优先参与该类别 + 风格的码点匹配；斜体槽被指派时，斜体文本改用
 * 该字体族的<b>真实斜体字面</b>（不再走渲染期几何斜切）。</p>
 *
 * <p>语言类别按码点判定（{@link UnicodeTextClassifier#isCjk(int)}）：中文涵盖 CJK 统一表意、
 * 假名、谚文、CJK 标点与全角形式；其余归西文。</p>
 */
public final class FontFaceAssignment {

    /** 全空的指派（未启用新特性，行为与既有版本一致）。 */
    public static final FontFaceAssignment EMPTY = new FontFaceAssignment("", "", "", "", "", "");

    private final String westernNormal;
    private final String westernBold;
    private final String westernItalic;
    private final String cjkNormal;
    private final String cjkBold;
    private final String cjkItalic;

    /**
     * @param westernNormal 西文正常字体族
     * @param westernBold   西文粗体字体族
     * @param westernItalic 西文斜体字体族
     * @param cjkNormal     中文正常字体族
     * @param cjkBold       中文粗体字体族
     * @param cjkItalic     中文斜体字体族
     */
    public FontFaceAssignment(String westernNormal, String westernBold, String westernItalic, String cjkNormal,
            String cjkBold, String cjkItalic) {
        this.westernNormal = normalize(westernNormal);
        this.westernBold = normalize(westernBold);
        this.westernItalic = normalize(westernItalic);
        this.cjkNormal = normalize(cjkNormal);
        this.cjkBold = normalize(cjkBold);
        this.cjkItalic = normalize(cjkItalic);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /** @return 六个槽是否全为未指派 */
    public boolean isEmpty() {
        return westernNormal.isEmpty() && westernBold.isEmpty() && westernItalic.isEmpty()
                && cjkNormal.isEmpty() && cjkBold.isEmpty() && cjkItalic.isEmpty();
    }

    /**
     * 解析某码点在某字面下应使用的已指派字体族。
     *
     * @param codepoint 字符码点
     * @param fontType  目标字面
     * @return 字体族名；未指派返回空串
     */
    public String familyFor(int codepoint, FontType fontType) {
        boolean cjk = UnicodeTextClassifier.isCjk(codepoint);
        if (fontType != null && fontType.isItalic()) {
            return cjk ? cjkItalic : westernItalic;
        }
        if (fontType != null && fontType.isBold()) {
            return cjk ? cjkBold : westernBold;
        }
        return cjk ? cjkNormal : westernNormal;
    }

    /**
     * 该码点所属语言类别是否指派了真实斜体字面。
     *
     * @param codepoint 字符码点
     * @return 是否可走真实斜体字面
     */
    public boolean hasItalic(int codepoint) {
        return !familyFor(codepoint, FontType.ITALIC).isEmpty();
    }

    public String getWesternNormal() {
        return westernNormal;
    }

    public String getWesternBold() {
        return westernBold;
    }

    public String getWesternItalic() {
        return westernItalic;
    }

    public String getCjkNormal() {
        return cjkNormal;
    }

    public String getCjkBold() {
        return cjkBold;
    }

    public String getCjkItalic() {
        return cjkItalic;
    }
}
