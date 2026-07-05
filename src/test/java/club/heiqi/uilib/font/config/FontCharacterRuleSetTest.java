package club.heiqi.uilib.font.config;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link FontCharacterRuleSet} 的 L2 纯数学边界测试。
 *
 * <p>不挂 runtime / 不建 scene，仅断言 resolveFontName 的代数性质（按
 * {@code docs/传感层/测试体系约定.md} §L2 边界，纯数学：首命中语义 / 空集 / 逗号展开匹配）。</p>
 *
 * <p>覆盖 P5 逗号多点语法扩展：{@code a,b=X} 配置 → 'a' 与 'b' 都命中 X。</p>
 */
public class FontCharacterRuleSetTest {

    /**
     * resolveFontName 按规则顺序匹配，首 active 命中返回。
     */
    @Test
    public void resolveFontNameReturnsFirstActiveMatch() {
        FontCharacterRuleSet set = FontCharacterRuleSet.parse(new String[] {
                "a-z=FontA",
                "a=FontB"
        });

        Assert.assertEquals("首命中 FontA", "FontA", set.resolveFontName('a'));
        Assert.assertEquals("范围内 g 命中 FontA", "FontA", set.resolveFontName('g'));
    }

    /**
     * 空配置数组 / 全空行 → 返回 EMPTY，resolveFontName 返回 null。
     */
    @Test
    public void emptyOrNullConfigReturnsEmptySet() {
        FontCharacterRuleSet fromNull = FontCharacterRuleSet.parse(null);
        FontCharacterRuleSet fromEmpty = FontCharacterRuleSet.parse(new String[0]);
        FontCharacterRuleSet fromBlank = FontCharacterRuleSet.parse(new String[] {"", "  ", null});

        Assert.assertTrue("null 配置 → 空集", fromNull.isEmpty());
        Assert.assertTrue("空数组 → 空集", fromEmpty.isEmpty());
        Assert.assertTrue("全空行 → 空集", fromBlank.isEmpty());
        Assert.assertNull("空集 resolveFontName 返回 null", fromNull.resolveFontName('a'));
        Assert.assertSame("empty() 与 parse 空输入共享 EMPTY 单例", fromEmpty, FontCharacterRuleSet.empty());
    }

    /**
     * P5 逗号多点：单条 rawRule 逗号展开后多个码点都命中同一 fontName。
     */
    @Test
    public void commaMultiPointExpandsAndMatchesAllSegments() {
        FontCharacterRuleSet set = FontCharacterRuleSet.parse(new String[] {"a,b,c=X"});

        Assert.assertEquals("a 命中 X", "X", set.resolveFontName('a'));
        Assert.assertEquals("b 命中 X", "X", set.resolveFontName('b'));
        Assert.assertEquals("c 命中 X", "X", set.resolveFontName('c'));
        Assert.assertNull("d 不命中", set.resolveFontName('d'));
    }

    /**
     * P5 逗号多点混合范围：单条 rawRule 含范围 + 单码点都命中。
     */
    @Test
    public void commaMultiPointWithRangeMatchesAllCodepoints() {
        FontCharacterRuleSet set = FontCharacterRuleSet.parse(new String[] {
                "U+3000-U+303F,U+4E00=CJK"
        });

        Assert.assertEquals("U+3000 命中", "CJK", set.resolveFontName(0x3000));
        Assert.assertEquals("U+303F 命中", "CJK", set.resolveFontName(0x303F));
        Assert.assertEquals("U+4E00 命中", "CJK", set.resolveFontName(0x4E00));
        Assert.assertNull("U+3040 不命中", set.resolveFontName(0x3040));
    }

    /**
     * invalid 规则不参与匹配（isActive=false）。
     */
    @Test
    public void invalidRulesAreSkippedInResolve() {
        FontCharacterRuleSet set = FontCharacterRuleSet.parse(new String[] {
                "XY=BadFont",
                "a=GoodFont"
        });

        Assert.assertEquals("invalid 行不参与，a 命中 GoodFont", "GoodFont", set.resolveFontName('a'));
        Assert.assertNull("invalid selector 不命中任何码点", set.resolveFontName('X'));
    }

    /**
     * P5 逗号展开含 invalid 段：valid 段正常命中，invalid 段跳过。
     */
    @Test
    public void mixedValidInvalidSegmentsResolveValidOnes() {
        FontCharacterRuleSet set = FontCharacterRuleSet.parse(new String[] {
                "a,XY,b=Font"
        });

        Assert.assertEquals("a 命中", "Font", set.resolveFontName('a'));
        Assert.assertEquals("b 命中", "Font", set.resolveFontName('b'));
        // XY 段 invalid 不参与匹配；X / Y 单字符虽不在 selector，但断言不命中
        Assert.assertNull("invalid 段不产生匹配", set.resolveFontName('X'));
    }

    /**
     * disabled 规则不参与匹配。
     */
    @Test
    public void disabledRulesAreSkippedInResolve() {
        FontCharacterRuleSet set = FontCharacterRuleSet.parse(new String[] {
                "disabled:a=OffFont",
                "a=OnFont"
        });

        Assert.assertEquals("disabled 行不参与，命中启用行 OnFont", "OnFont", set.resolveFontName('a'));
    }

    /**
     * P5 disabled + 逗号多点：disabled 作用于整条 rawRule 所有展开段（边界②）。
     */
    @Test
    public void disabledCommaMultiPointSkipsAllSegments() {
        FontCharacterRuleSet set = FontCharacterRuleSet.parse(new String[] {
                "disabled:a,b=OffFont"
        });

        Assert.assertNull("disabled 透传 a 不命中", set.resolveFontName('a'));
        Assert.assertNull("disabled 透传 b 不命中", set.resolveFontName('b'));
    }

    /**
     * getRules 返回不可变快照。
     */
    @Test(expected = UnsupportedOperationException.class)
    public void getRulesReturnsImmutableSnapshot() {
        FontCharacterRuleSet set = FontCharacterRuleSet.parse(new String[] {"a=X"});
        set.getRules().add(FontCharacterRule.parseLine("b=Y"));
    }
}
