package club.heiqi.uilib.ui.input;

import org.junit.Assert;
import org.junit.Test;

/**
 * `UiNativeTextInputInspector` 的内部边界测试。
 */
public class UiNativeTextInputInspectorTest {

    /**
     * 验证聊天框类名会被识别为支持原生文本框回焦。
     */
    @Test
    public void shouldTreatGuiChatClassNameAsPreferredRefocusTarget() {
        Assert.assertTrue(UiNativeTextInputInspector.supportsPreferredTextInputRefocus(null,
                "net.minecraft.client.gui.GuiChat"));
        Assert.assertFalse(UiNativeTextInputInspector.supportsPreferredTextInputRefocus(null,
                "net.minecraft.client.gui.inventory.GuiChest"));
        Assert.assertFalse(UiNativeTextInputInspector.supportsPreferredTextInputRefocus(null, null));
    }
}
