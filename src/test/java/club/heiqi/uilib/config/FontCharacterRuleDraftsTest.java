package club.heiqi.uilib.config;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

/**
 * `FontCharacterRuleDrafts` 的草稿解析与校验测试。
 */
public class FontCharacterRuleDraftsTest {

    /**
     * 验证远程同步草稿会沿用通用列表拆分语义。
     */
    @Test
    public void shouldSplitRemoteDraftWithGenericListSeparators() {
        String[] rules = FontCharacterRuleDrafts.splitDraftValue("字=Alpha, A-Z=Latin\nU+E000-U+F8FF=Icon");

        Assert.assertArrayEquals(new String[] { "字=Alpha", "A-Z=Latin", "U+E000-U+F8FF=Icon" }, rules);
    }

    /**
     * 验证格式错误的规则会被草稿校验拦截。
     */
    @Test
    public void shouldRejectInvalidRuleDraft() {
        String validationError = FontCharacterRuleDrafts.validateRules(Arrays.asList("ABC=Alpha"));

        Assert.assertNotNull(validationError);
        Assert.assertTrue(validationError.contains("第 1 条规则无效"));
    }

    /**
     * 验证重叠规则保留优先级语义，只作为提示而不阻断保存。
     */
    @Test
    public void shouldWarnButNotRejectOverlappingRules() {
        java.util.List<String> rules = Arrays.asList("A-Z=Latin", "A=Icon");

        Assert.assertNull(FontCharacterRuleDrafts.validateRules(rules));
        Assert.assertNotNull(FontCharacterRuleDrafts.findOverlapWarning(rules));
    }
}
