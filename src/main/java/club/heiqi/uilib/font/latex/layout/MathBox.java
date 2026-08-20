package club.heiqi.uilib.font.latex.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 数学布局盒（TeX box 简化）：宽/高/深 + 绘制单元。
 *
 * <p>坐标约定：盒内所有元素 x/y 相对<b>盒基线</b>；y 向下为正（屏幕坐标），
 * 基线上方为负。height = 基线以上高度，depth = 基线以下深度。</p>
 */
public final class MathBox {

    private final float width;
    private final float height;
    private final float depth;
    private final List<GlyphElem> glyphs;
    private final List<RuleElem> rules;

    /**
     * 创建布局盒。
     *
     * @param width  盒宽
     * @param height 基线上方高度
     * @param depth  基线下方深度
     * @param glyphs 字形单元（可为空）
     * @param rules  矩形规则线（可为空）
     */
    public MathBox(float width, float height, float depth, List<GlyphElem> glyphs, List<RuleElem> rules) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.glyphs = glyphs == null || glyphs.isEmpty()
                ? Collections.<GlyphElem>emptyList()
                : Collections.unmodifiableList(new ArrayList<GlyphElem>(glyphs));
        this.rules = rules == null || rules.isEmpty()
                ? Collections.<RuleElem>emptyList()
                : Collections.unmodifiableList(new ArrayList<RuleElem>(rules));
    }

    /** 空盒（宽高深 0）。 */
    public static MathBox empty() {
        return new MathBox(0.0F, 0.0F, 0.0F, null, null);
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public float getDepth() {
        return depth;
    }

    /** @return 总高（height + depth） */
    public float getTotalHeight() {
        return height + depth;
    }

    /** @return 字形单元（不可变，相对基线坐标） */
    public List<GlyphElem> getGlyphs() {
        return glyphs;
    }

    /** @return 规则线单元（不可变，相对基线坐标） */
    public List<RuleElem> getRules() {
        return rules;
    }

    @Override
    public String toString() {
        return "MathBox(w=" + width + ", h=" + height + ", d=" + depth + ", glyphs=" + glyphs.size()
                + ", rules=" + rules.size() + ")";
    }
}
