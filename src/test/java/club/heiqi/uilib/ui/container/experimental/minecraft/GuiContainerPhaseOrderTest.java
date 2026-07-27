package club.heiqi.uilib.ui.container.experimental.minecraft;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

/** 原版与 GTNH transformed 路径共同支持的绘制 phase 合同。 */
public class GuiContainerPhaseOrderTest {
    @Test
    public void phaseOrderKeepsCarriedBeforeVanillaAndThirdPartyTooltips() {
        Assert.assertEquals(Arrays.asList(
                "VANILLA_BACKGROUND",
                "SCENE_MAIN",
                "VANILLA_SLOT_FOREGROUND",
                "THIRD_PARTY_OBJECTS",
                "SCENE_OVERLAY",
                "CARRIED",
                "VANILLA_THIRD_PARTY_TOOLTIP"), GuiContainerScenePhaseHook.phaseOrder());
        try {
            GuiContainerScenePhaseHook.phaseOrder().add("INVALID");
            Assert.fail();
        } catch (UnsupportedOperationException expected) { }
    }
}
