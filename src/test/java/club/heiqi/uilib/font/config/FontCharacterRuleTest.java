package club.heiqi.uilib.font.config;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link FontCharacterRule} 的解析测试（含 P5 逗号多点语法扩展）。
 */
public class FontCharacterRuleTest {

    /**
     * 验证单字符规则会解析为单码点范围。
     */
    @Test
    public void shouldParseSingleCharacterRule() {
        List<FontCharacterRule> rules = FontCharacterRule.parse("字=TestFont");

        Assert.assertEquals("单字符展开为 1 条", 1, rules.size());
        FontCharacterRule rule = rules.get(0);
        Assert.assertTrue(rule.isValid());
        Assert.assertTrue(rule.isActive());
        Assert.assertEquals("字", rule.getSelector());
        Assert.assertEquals("TestFont", rule.getFontName());
        Assert.assertEquals('字', rule.getStartCodepoint());
        Assert.assertEquals('字', rule.getEndCodepoint());
    }

    /**
     * 验证 Unicode 码点范围规则会正确命中范围内字符。
     */
    @Test
    public void shouldParseUnicodeRangeRule() {
        List<FontCharacterRule> rules = FontCharacterRule.parse("U+E000-U+E002=IconFont");

        Assert.assertEquals("范围展开为 1 条", 1, rules.size());
        FontCharacterRule rule = rules.get(0);
        Assert.assertTrue(rule.isValid());
        Assert.assertTrue(rule.matches(0xE000));
        Assert.assertTrue(rule.matches(0xE001));
        Assert.assertTrue(rule.matches(0xE002));
        Assert.assertFalse(rule.matches(0xE003));
    }

    /**
     * 验证禁用规则保留格式但不参与运行时命中。
     */
    @Test
    public void shouldKeepDisabledRuleInactive() {
        List<FontCharacterRule> rules = FontCharacterRule.parse("disabled:A-Z=LatinFont");

        Assert.assertEquals("disabled 单段展开为 1 条", 1, rules.size());
        FontCharacterRule rule = rules.get(0);
        Assert.assertTrue(rule.isValid());
        Assert.assertFalse(rule.isActive());
        Assert.assertFalse(rule.matches('A'));
        Assert.assertEquals("disabled:A-Z=LatinFont", rule.toConfigValue());
    }

    /**
     * 验证格式错误时提供可读错误信息。
     */
    @Test
    public void shouldReportInvalidRule() {
        List<FontCharacterRule> rules = FontCharacterRule.parse("ABC=TestFont");

        Assert.assertEquals("invalid 兜底返回单条 list", 1, rules.size());
        FontCharacterRule rule = rules.get(0);
        Assert.assertFalse(rule.isValid());
        Assert.assertNotNull(rule.getErrorMessage());
    }

    // ==================== P5 逗号多点语法扩展 ====================

    /**
     * 逗号多单字符 selector 展开为多条独立 valid rule，各 matches 对应码点。
     */
    @Test
    public void shouldExpandCommaMultiCharSelector() {
        List<FontCharacterRule> rules = FontCharacterRule.parse("a,b,c=Font");

        Assert.assertEquals("3 段展开为 3 条", 3, rules.size());
        for (FontCharacterRule rule : rules) {
            Assert.assertTrue("每段 valid", rule.isValid());
            Assert.assertTrue("每段 active", rule.isActive());
            Assert.assertEquals("fontName 透传", "Font", rule.getFontName());
        }
        Assert.assertEquals("段 0 selector", "a", rules.get(0).getSelector());
        Assert.assertEquals("段 1 selector", "b", rules.get(1).getSelector());
        Assert.assertEquals("段 2 selector", "c", rules.get(2).getSelector());
        Assert.assertTrue("段 0 命中 a", rules.get(0).matches('a'));
        Assert.assertTrue("段 1 命中 b", rules.get(1).matches('b'));
        Assert.assertTrue("段 2 命中 c", rules.get(2).matches('c'));
        Assert.assertFalse("段 0 不命中 b", rules.get(0).matches('b'));
    }

    /**
     * 逗号分隔混合 U+XXXX 范围与单码点。
     */
    @Test
    public void shouldExpandMixedUnicodeRangeAndCodepoint() {
        List<FontCharacterRule> rules = FontCharacterRule.parse("U+0041-U+0043,U+3000=Font");

        Assert.assertEquals("范围 + 单码点展开 2 条", 2, rules.size());
        Assert.assertTrue("段 0 valid", rules.get(0).isValid());
        Assert.assertTrue("段 0 命中范围内 A", rules.get(0).matches(0x0041));
        Assert.assertTrue("段 0 命中范围内 C", rules.get(0).matches(0x0043));
        Assert.assertTrue("段 1 valid", rules.get(1).isValid());
        Assert.assertTrue("段 1 命中 U+3000", rules.get(1).matches(0x3000));
    }

    /**
     * 逗号分隔多范围。
     */
    @Test
    public void shouldExpandMultipleRanges() {
        List<FontCharacterRule> rules = FontCharacterRule.parse("a-z,0-9=Font");

        Assert.assertEquals("2 范围展开 2 条", 2, rules.size());
        Assert.assertTrue("段 0 valid", rules.get(0).isValid());
        Assert.assertTrue("段 0 命中 a", rules.get(0).matches('a'));
        Assert.assertTrue("段 0 命中 z", rules.get(0).matches('z'));
        Assert.assertTrue("段 1 valid", rules.get(1).isValid());
        Assert.assertTrue("段 1 命中 0", rules.get(1).matches('0'));
        Assert.assertTrue("段 1 命中 9", rules.get(1).matches('9'));
    }

    /**
     * 逗号间空段跳过，不报错不展开（边界③）。
     */
    @Test
    public void shouldSkipEmptyCommaSegments() {
        List<FontCharacterRule> rules = FontCharacterRule.parse("a,,b=Font");

        Assert.assertEquals("空段跳过后 2 条", 2, rules.size());
        Assert.assertEquals("段 0 selector", "a", rules.get(0).getSelector());
        Assert.assertEquals("段 1 selector", "b", rules.get(1).getSelector());
        Assert.assertTrue("段 0 valid", rules.get(0).isValid());
        Assert.assertTrue("段 1 valid", rules.get(1).isValid());
    }

    /**
     * disabled 前缀在逗号拆分前剥离，所有展开段共享 enabled=false（边界②）。
     */
    @Test
    public void disabledPrefixAppliesToAllCommaSegments() {
        List<FontCharacterRule> rules = FontCharacterRule.parse("disabled:a,b=Font");

        Assert.assertEquals("disabled 2 段展开 2 条", 2, rules.size());
        for (FontCharacterRule rule : rules) {
            Assert.assertFalse("每段 enabled=false", rule.isEnabled());
            Assert.assertFalse("每段不 active", rule.isActive());
            Assert.assertTrue("每段格式仍 valid", rule.isValid());
        }
    }

    /**
     * 展开段可混合 valid / invalid，invalid 段 errorMessage 非空但不影响其他段。
     */
    @Test
    public void mixedValidInvalidSegmentsExpandIndependently() {
        List<FontCharacterRule> rules = FontCharacterRule.parse("a,XY,b=Font");

        Assert.assertEquals("3 段展开 3 条（含 invalid）", 3, rules.size());
        Assert.assertTrue("段 0 a valid", rules.get(0).isValid());
        Assert.assertNull("段 0 无 errorMessage", rules.get(0).getErrorMessage());
        Assert.assertFalse("段 1 XY invalid", rules.get(1).isValid());
        Assert.assertNotNull("段 1 errorMessage 非空", rules.get(1).getErrorMessage());
        Assert.assertTrue("段 2 b valid", rules.get(2).isValid());
        Assert.assertNull("段 2 无 errorMessage", rules.get(2).getErrorMessage());
    }

    /**
     * 缺 = 分隔符：返回单条 invalid list（兜底）。
     */
    @Test
    public void missingSeparatorReturnsSingleInvalidList() {
        List<FontCharacterRule> rules = FontCharacterRule.parse("abc");

        Assert.assertEquals("缺 = 兜底单条", 1, rules.size());
        Assert.assertFalse("兜底 invalid", rules.get(0).isValid());
        Assert.assertNotNull("兜底 errorMessage 非空", rules.get(0).getErrorMessage());
    }

    /**
     * fontName 空：返回单条 invalid list（兜底）。
     */
    @Test
    public void emptyFontNameReturnsSingleInvalidList() {
        List<FontCharacterRule> rules = FontCharacterRule.parse("a=");

        Assert.assertEquals("fontName 空兜底单条", 1, rules.size());
        Assert.assertFalse("兜底 invalid", rules.get(0).isValid());
        Assert.assertNotNull("兜底 errorMessage 非空", rules.get(0).getErrorMessage());
    }

    /**
     * 全段皆空：返回空 list（边界③）。
     */
    @Test
    public void allEmptySegmentsReturnEmptyList() {
        List<FontCharacterRule> rules = FontCharacterRule.parse(",,=Font");

        Assert.assertTrue("全空段返回空 list", rules.isEmpty());
    }

    // ==================== parseLine：UI 单行解析（不展开逗号） ====================

    /**
     * parseLine 保留完整 selector 文本（含逗号），用于 UI 行字段表示。
     */
    @Test
    public void parseLineKeepsFullSelectorText() {
        FontCharacterRule line = FontCharacterRule.parseLine("a,b,c=Font");

        Assert.assertTrue("全 valid 段 errorMessage 为 null", line.isValid());
        Assert.assertEquals("selector 保留逗号原样", "a,b,c", line.getSelector());
        Assert.assertEquals("fontName 透传", "Font", line.getFontName());
        Assert.assertTrue("enabled 默认 true", line.isEnabled());
    }

    /**
     * parseLine 中含 invalid 段：errorMessage 取首个 invalid 段错误。
     */
    @Test
    public void parseLineReportsFirstInvalidSegmentError() {
        FontCharacterRule line = FontCharacterRule.parseLine("a,XY,b=Font");

        Assert.assertFalse("含 invalid 段整体 invalid", line.isValid());
        Assert.assertNotNull("errorMessage 非空", line.getErrorMessage());
        Assert.assertEquals("selector 仍保留完整文本", "a,XY,b", line.getSelector());
    }

    /**
     * parseLine 处理 disabled 透传 enabled=false。
     */
    @Test
    public void parseLineHonorsDisabledPrefix() {
        FontCharacterRule line = FontCharacterRule.parseLine("disabled:a,b=Font");

        Assert.assertFalse("disabled 透传 enabled=false", line.isEnabled());
        Assert.assertTrue("disabled 格式仍 valid", line.isValid());
        Assert.assertEquals("selector 保留", "a,b", line.getSelector());
    }

    /**
     * parseLine 缺 = 兜底 invalid。
     */
    @Test
    public void parseLineMissingSeparatorReturnsInvalid() {
        FontCharacterRule line = FontCharacterRule.parseLine("abc");

        Assert.assertFalse(line.isValid());
        Assert.assertNotNull(line.getErrorMessage());
    }

    /**
     * parseLine 全段皆空：返回 invalid（与 parse 返回空 list 不同，UI 视角下需提示错误）。
     */
    @Test
    public void parseLineAllEmptySegmentsReturnsInvalid() {
        FontCharacterRule line = FontCharacterRule.parseLine(",,=Font");

        Assert.assertFalse("全空段 parseLine invalid", line.isValid());
        Assert.assertNotNull("errorMessage 非空", line.getErrorMessage());
    }
}
