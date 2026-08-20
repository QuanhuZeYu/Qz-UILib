package club.heiqi.uilib.font.latex.node;

import club.heiqi.uilib.font.latex.LatexNode;

/**
 * 重音节点（{@code \hat \bar \vec \dot \ddot \tilde}）与可变长横线
 * （{@code \overline \\underline}）。
 */
public final class LatexAccent extends LatexNode {

    private final String accentText;
    private final LatexNode base;
    private final boolean stretchable;
    private final boolean below;

    /**
     * 创建重音节点。
     *
     * @param accentText  重音显示文本（组合变音符，如 U+0302）；可变长横线时为 null
     * @param base        被修饰内容
     * @param stretchable 是否可变长横线（\overline/\\underline）
     * @param below       是否置于下方（\\underline）
     */
    public LatexAccent(String accentText, LatexNode base, boolean stretchable, boolean below) {
        super(Kind.ACCENT);
        if (base == null) {
            throw new IllegalArgumentException("base 不能为空");
        }
        if (!stretchable && (accentText == null || accentText.isEmpty())) {
            throw new IllegalArgumentException("非可变长重音需要 accentText");
        }
        this.accentText = accentText;
        this.base = base;
        this.stretchable = stretchable;
        this.below = below;
    }

    /** @return 重音显示文本；可变长横线时为 null */
    public String getAccentText() {
        return accentText;
    }

    public LatexNode getBase() {
        return base;
    }

    /** @return 是否可变长横线（\overline/\\underline） */
    public boolean isStretchable() {
        return stretchable;
    }

    /** @return 是否置于下方（\\underline） */
    public boolean isBelow() {
        return below;
    }

    @Override
    public String toString() {
        return "Accent(" + (stretchable ? (below ? "underline" : "overline") : accentText) + ", " + base + ")";
    }
}