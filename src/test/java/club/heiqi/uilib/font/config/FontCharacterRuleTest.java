package club.heiqi.uilib.font.config;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link FontCharacterRule} 的解析测试。
 */
public class FontCharacterRuleTest {

    /**
     * 验证单字符规则会解析为单码点范围。
     */
    @Test
    public void shouldParseSingleCharacterRule() {
        FontCharacterRule rule = FontCharacterRule.parse("字=TestFont");

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
        FontCharacterRule rule = FontCharacterRule.parse("U+E000-U+E002=IconFont");

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
        FontCharacterRule rule = FontCharacterRule.parse("disabled:A-Z=LatinFont");

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
        FontCharacterRule rule = FontCharacterRule.parse("ABC=TestFont");

        Assert.assertFalse(rule.isValid());
        Assert.assertNotNull(rule.getErrorMessage());
    }
}
