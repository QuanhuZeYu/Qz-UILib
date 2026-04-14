package club.heiqi.uilib.ui.screen;

import org.junit.Assert;
import org.junit.Test;

/**
 * `UiTestMutationProbeState` 的纯 JVM 测试。
 */
public class UiTestMutationProbeStateTest {

    /**
     * 验证开关、重置与最近操作文案语义。
     */
    @Test
    public void shouldHandleToggleResetAndActionState() {
        UiTestMutationProbeState state = new UiTestMutationProbeState();

        state.onWrapToggleChanged(false);
        Assert.assertEquals("已关闭自动换行提示", state.getActionStateText());

        state.onWidthPresetChanged("宽页");
        Assert.assertEquals("已切换宽度档位到 宽页", state.getActionStateText());

        UiTestMutationProbeState.MutationTextUpdate enabledReset = state.onMutationToggleChanged(true, "§k渲染");
        Assert.assertTrue(enabledReset.shouldApplyText);
        Assert.assertEquals("探针已重置，等待下一次文本变更。", enabledReset.text);
        Assert.assertEquals("已启用高频字符变更探针", state.getActionStateText());
        Assert.assertEquals("§k渲染", state.getLastMutationMode());
        Assert.assertEquals(0, state.getMutationSetTextCount());

        UiTestMutationProbeState.MutationTextUpdate disabledReset = state.onMutationToggleChanged(false, "§k渲染");
        Assert.assertTrue(disabledReset.shouldApplyText);
        Assert.assertTrue(disabledReset.text.contains("探针未启用。开启后可以直接观察"));
        Assert.assertEquals("已停止高频字符变更探针", state.getActionStateText());
        Assert.assertEquals(0, state.getMutationSetTextCount());
        Assert.assertEquals("", state.getLastMutationText());
    }

    /**
     * 验证三种模式的输出差异与模式切换重置语义。
     */
    @Test
    public void shouldProduceDifferentTextsForThreeModes() {
        UiTestMutationProbeState state = new UiTestMutationProbeState();

        state.onMutationToggleChanged(true, "§k渲染");
        UiTestMutationProbeState.MutationTextUpdate obfuscated = state.tickMutation(true, "§k渲染", 0, 0L);
        Assert.assertTrue(obfuscated.shouldApplyText);
        Assert.assertTrue(obfuscated.text.contains("§kQZUILIB-DIAGNOSTIC-STREAM"));
        Assert.assertEquals(1, state.getMutationSetTextCount());

        UiTestMutationProbeState.MutationTextUpdate obfuscatedSecond = state.tickMutation(true, "§k渲染", 0, 1L);
        Assert.assertFalse(obfuscatedSecond.shouldApplyText);
        Assert.assertEquals(1, state.getMutationSetTextCount());

        UiTestMutationProbeState.MutationTextUpdate stableReset = state.onMutationModeChanged(true, "同长替换");
        Assert.assertTrue(stableReset.shouldApplyText);
        Assert.assertEquals("探针已重置，等待下一次文本变更。", stableReset.text);
        Assert.assertEquals(0, state.getMutationSetTextCount());
        Assert.assertEquals("", state.getLastMutationText());

        UiTestMutationProbeState.MutationTextUpdate stable = state.tickMutation(true, "同长替换", 0, 0L);
        Assert.assertTrue(stable.shouldApplyText);
        Assert.assertTrue(stable.text.contains("同长替换样本 000001"));
        Assert.assertTrue(stable.text.contains("/ token="));
        Assert.assertEquals(1, state.getMutationSetTextCount());

        UiTestMutationProbeState.MutationTextUpdate reflowReset = state.onMutationModeChanged(true, "长文重排");
        Assert.assertTrue(reflowReset.shouldApplyText);
        Assert.assertEquals(0, state.getMutationSetTextCount());

        UiTestMutationProbeState.MutationTextUpdate reflow = state.tickMutation(true, "长文重排", 0, 0L);
        Assert.assertTrue(reflow.shouldApplyText);
        Assert.assertTrue(reflow.text.contains("长文重排样本 000001"));
        Assert.assertTrue(reflow.text.contains("assets/qz_uilib/ui/diagnostic/"));
        Assert.assertEquals(1, state.getMutationSetTextCount());
    }

    /**
     * 验证频率节流与 `mutationSetTextCount` 计数语义。
     */
    @Test
    public void shouldThrottleByRateAndCountOnlyRealTextChanges() {
        UiTestMutationProbeState state = new UiTestMutationProbeState();
        state.onMutationToggleChanged(true, "同长替换");

        UiTestMutationProbeState.MutationTextUpdate first = state.tickMutation(true, "同长替换", 1, 50_000_000L);
        Assert.assertTrue(first.shouldApplyText);
        Assert.assertEquals(1, state.getMutationSetTextCount());

        UiTestMutationProbeState.MutationTextUpdate throttled = state.tickMutation(true, "同长替换", 1, 60_000_000L);
        Assert.assertFalse(throttled.shouldApplyText);
        Assert.assertEquals(1, state.getMutationSetTextCount());

        UiTestMutationProbeState.MutationTextUpdate second = state.tickMutation(true, "同长替换", 1, 100_000_000L);
        Assert.assertTrue(second.shouldApplyText);
        Assert.assertTrue(second.text.contains("同长替换样本 000002"));
        Assert.assertEquals(2, state.getMutationSetTextCount());

        UiTestMutationProbeState.MutationTextUpdate rateChange = state.onMutationRateChanged("200ms");
        Assert.assertFalse(rateChange.shouldApplyText);
        Assert.assertEquals(0, state.getMutationSetTextCount());
        Assert.assertEquals("", state.getLastMutationText());
        Assert.assertEquals("已切换探针频率到 200ms", state.getActionStateText());

        UiTestMutationProbeState.MutationTextUpdate afterRateChange = state.tickMutation(true, "同长替换", 2, 200_000_000L);
        Assert.assertTrue(afterRateChange.shouldApplyText);
        Assert.assertTrue(afterRateChange.text.contains("同长替换样本 000001"));
        Assert.assertEquals(1, state.getMutationSetTextCount());
    }
}
