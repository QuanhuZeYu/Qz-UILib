package club.heiqi.uilib.font.latex.layout;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.MathFontStyle;
import club.heiqi.uilib.font.latex.MathStyleOverride;
import club.heiqi.uilib.font.latex.node.LatexAccent;
import club.heiqi.uilib.font.latex.node.LatexAtom;
import club.heiqi.uilib.font.latex.node.LatexAtom.AtomClass;
import club.heiqi.uilib.font.latex.node.LatexAtom.OperatorMode;
import club.heiqi.uilib.font.latex.node.LatexFrac;
import club.heiqi.uilib.font.latex.node.LatexGroup;
import club.heiqi.uilib.font.latex.node.LatexOperator;
import club.heiqi.uilib.font.latex.node.LatexSpace;
import club.heiqi.uilib.font.latex.node.LatexSupSub;

/** 区分各字体的注入度量，防止只换绘制字体而未同步测量。 */
public class MathNamedOperatorGeometryRegressionTest {
    private static final float ROOT = 10.0F;
    private static final float EPS = 1e-4F;
    private static final MathLayoutService SERVICE = new MathLayoutService();
    private static final MathMetrics METRICS = new Metrics(1.0F);

    private static final class Metrics implements MathMetrics {
        private final float factor;
        Metrics(float factor) { this.factor = factor; }
        public MathMetrics forFontStyle(MathFontStyle font) {
            return new Metrics(font == MathFontStyle.BOLD ? 2.0F
                    : font == MathFontStyle.ITALIC ? 3.0F : 1.0F);
        }
        public float advance(String text, float size) { return text.length() * size * factor / 2.0F; }
        public float ascent(float size) { return size * factor * 0.8F; }
        public float descent(float size) { return size * factor * 0.2F; }
        public float xHeight(float size) { return size * factor * 0.4F; }
        public float italicCorrection(String text, float size) { return size * factor / 10.0F; }
        public float italicOverhang(String text, float size) { return size * factor / 4.0F; }
    }

    private static LatexAtom atom(String text, MathFontStyle font) {
        return new LatexAtom(text, AtomClass.ORD, OperatorMode.NONE, MathStyleOverride.INHERIT, font);
    }
    private static MathBox layout(LatexNode node, MathStyle.Level level) {
        return SERVICE.layout(Collections.singletonList(node), new MathStyle(ROOT, level, false), METRICS);
    }
    private static MathBox layout(LatexNode node) { return layout(node, MathStyle.Level.TEXT); }
    private static GlyphElem glyph(MathBox box, String text) {
        for (GlyphElem g : box.getGlyphs()) { if (text.equals(g.getText())) { return g; } }
        throw new AssertionError(text);
    }
    private static LatexGroup group(LatexNode... nodes) { return new LatexGroup(Arrays.asList(nodes)); }

    @Test
    public void oldGlyphAndMetricsApisRemainCompatible() {
        Assert.assertEquals(MathFontStyle.INHERIT, new GlyphElem("x", 0, 0, 1, true, false).getMathFontStyle());
        MathMetrics legacy = new MathMetrics() {
            public float advance(String text, float size) { return size; }
            public float ascent(float size) { return size; }
            public float descent(float size) { return 0; }
            public float xHeight(float size) { return size; }
        };
        Assert.assertSame(legacy, legacy.forFontStyle(MathFontStyle.BOLD));
    }

    @Test
    public void boldUsesSelectedMetricsAndNormalExplicitlyResets() {
        MathBox bold = layout(atom("x", MathFontStyle.BOLD));
        Assert.assertEquals(METRICS.forFontStyle(MathFontStyle.BOLD).advance("x", ROOT), bold.getWidth(), EPS);
        Assert.assertEquals(METRICS.forFontStyle(MathFontStyle.BOLD).ascent(ROOT), bold.getHeight(), EPS);
        Assert.assertFalse(glyph(bold, "x").isItalic());
        Assert.assertFalse(glyph(bold, "x").isInheritTextItalic());
        Assert.assertEquals(MathFontStyle.BOLD, glyph(bold, "x").getMathFontStyle());
        MathBox normal = layout(atom("x", MathFontStyle.MATH_NORMAL));
        Assert.assertTrue(glyph(normal, "x").isItalic());
        Assert.assertFalse(glyph(normal, "x").isInheritTextItalic());
        LatexAtom plus = new LatexAtom("+", AtomClass.BIN, OperatorMode.NONE,
                MathStyleOverride.INHERIT, MathFontStyle.BOLD);
        Assert.assertEquals(MathFontStyle.UPRIGHT, glyph(layout(plus), "+").getMathFontStyle());
        Assert.assertEquals(METRICS.advance("+", ROOT), layout(plus).getWidth(), EPS);
    }

    @Test
    public void scriptAndAccentRemeasureUsingGlyphFont() {
        LatexAtom base = atom("x", MathFontStyle.ITALIC);
        MathMetrics chosen = METRICS.forFontStyle(MathFontStyle.ITALIC);
        MathBox scripts = layout(new LatexSupSub(base, atom("2", MathFontStyle.BOLD), null));
        Assert.assertEquals(chosen.advance("x", ROOT) + chosen.italicCorrection("x", ROOT)
                + chosen.italicOverhang("x", ROOT), glyph(scripts, "2").getX(), EPS);
        Assert.assertEquals(MathFontStyle.BOLD, glyph(scripts, "2").getMathFontStyle());
        MathBox accent = layout(new LatexAccent("^", base, false, false));
        float centered = (chosen.advance("x", ROOT) - METRICS.advance("^", ROOT)) / 2.0F;
        Assert.assertEquals(centered + MathConstants.ACCENT_SKEW_FACTOR * chosen.xHeight(ROOT),
                glyph(accent, "^").getX(), EPS);
        MathBox fraction = layout(new LatexFrac(base, atom("y", MathFontStyle.BOLD)));
        Assert.assertEquals(MathFontStyle.BOLD, glyph(fraction, "y").getMathFontStyle());
    }

    @Test
    public void namedOperatorPreservesEmptyAndStructuredNaturalBody() {
        LatexNode body = group(atom("ab", MathFontStyle.UPRIGHT), new LatexSpace(0.25),
                new LatexFrac(atom("c", MathFontStyle.BOLD), atom("d", MathFontStyle.UPRIGHT)));
        MathBox natural = layout(body);
        MathBox named = layout(new LatexOperator(body, true));
        Assert.assertEquals(natural.getWidth(), named.getWidth(), EPS);
        Assert.assertEquals(natural.getHeight(), named.getHeight(), EPS);
        Assert.assertEquals(natural.getDepth(), named.getDepth(), EPS);
        Assert.assertEquals(natural.getRules().size(), named.getRules().size());
        Assert.assertEquals(natural.getGlyphs().size(), named.getGlyphs().size());
        Assert.assertEquals(0.0F, layout(new LatexOperator(group(), true)).getWidth(), EPS);
        Assert.assertTrue(layout(new LatexSupSub(new LatexOperator(group(), true),
                atom("2", MathFontStyle.UPRIGHT), null), MathStyle.Level.DISPLAY).getWidth() > 0);
    }

    @Test
    public void starDisplayAndExplicitLimitsUseNaturalBaseWithoutBigOperatorTail() {
        for (boolean star : new boolean[] { false, true }) {
            for (MathStyle.Level level : MathStyle.Level.values()) {
                for (int flag : new int[] { LatexAtom.LIMITS_DEFAULT, LatexAtom.LIMITS_LIMITS, LatexAtom.LIMITS_NOLIMITS }) {
                    LatexOperator op = new LatexOperator(atom("name", MathFontStyle.UPRIGHT), star);
                    op.setLimitsFlag(flag);
                    MathBox box = layout(new LatexSupSub(op, atom("2", MathFontStyle.UPRIGHT),
                            atom("3", MathFontStyle.UPRIGHT)), level);
                    MathBox bare = layout(op, level);
                    boolean stacked = flag == LatexAtom.LIMITS_LIMITS
                            || (flag == LatexAtom.LIMITS_DEFAULT && star && level == MathStyle.Level.DISPLAY);
                    Assert.assertEquals(0.0F, glyph(box, "name").getY(), EPS);
                    Assert.assertEquals(glyph(bare, "name").getSizeScale(), glyph(box, "name").getSizeScale(), EPS);
                    if (stacked) {
                        Assert.assertEquals(bare.getWidth(), box.getWidth(), EPS);
                        Assert.assertEquals((bare.getWidth() - METRICS.advance("2", ROOT * glyph(box, "2").getSizeScale())) / 2.0F,
                                glyph(box, "2").getX(), EPS);
                    } else {
                        Assert.assertEquals(bare.getWidth(), glyph(box, "2").getX(), EPS);
                    }
                }
            }
        }
    }

    @Test
    public void transparentNamedOperatorKeepsLocalDisplayAndOuterScriptSize() {
        LatexOperator op = new LatexOperator(atom("name", MathFontStyle.UPRIGHT), true, MathStyleOverride.DISPLAY);
        LatexGroup wrapper = new LatexGroup(Collections.<LatexNode>singletonList(op), MathStyleOverride.SCRIPT, true);
        MathBox box = layout(new LatexSupSub(wrapper, atom("2", MathFontStyle.BOLD), null), MathStyle.Level.SCRIPT);
        Assert.assertEquals(1.0F, glyph(box, "name").getSizeScale(), EPS);
        Assert.assertEquals(new MathStyle(ROOT, MathStyle.Level.SCRIPT, false).superscript().size() / ROOT,
                glyph(box, "2").getSizeScale(), EPS);
        Assert.assertTrue(glyph(box, "2").getX() < METRICS.advance("name", ROOT));
    }

    @Test
    public void naturalBigOperatorAxisCopyKeepsExplicitFontMetadata() {
        LatexAtom integral = new LatexAtom("∫", AtomClass.OP, OperatorMode.BIG_OPERATOR,
                MathStyleOverride.INHERIT, MathFontStyle.UPRIGHT);
        integral.setLimitsFlag(LatexAtom.LIMITS_NOLIMITS);
        GlyphElem g = glyph(layout(integral), "∫");
        Assert.assertEquals(MathFontStyle.UPRIGHT, g.getMathFontStyle());
        Assert.assertFalse(g.isInheritTextItalic());
        Assert.assertFalse(g.isItalic());
    }
}
