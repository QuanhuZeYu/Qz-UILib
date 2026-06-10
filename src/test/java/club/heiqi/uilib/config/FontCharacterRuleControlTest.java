package club.heiqi.uilib.config;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * `FontCharacterRuleControl` 的草稿校验测试。
 */
public class FontCharacterRuleControlTest {

    /**
     * 验证重叠规则不再阻断保存，运行时由顺序决定优先级。
     */
    @Test
    public void shouldAllowOverlappingRulesAsPriorityDrafts() {
        FontCharacterRuleControl control = new FontCharacterRuleControl(UiDocument.create(),
                Arrays.asList("A-Z=Latin", "A=Icon"), null);

        Assert.assertNull(control.validateDraft());
    }
}
