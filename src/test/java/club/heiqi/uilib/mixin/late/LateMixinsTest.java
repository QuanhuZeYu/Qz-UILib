package club.heiqi.uilib.mixin.late;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

import cpw.mods.fml.relauncher.Side;

/** 可选 AE 兼容只在同时满足客户端和 Mod ID 门禁时返回。 */
public class LateMixinsTest {

    @Test
    public void clientWithAeLoadsMonitorCompatibility() {
        assertEquals(Collections.singletonList("ae2.MixinAbstractPartMonitor"),
                LateMixins.buildMixinsForSide(Side.CLIENT, Collections.singleton("appliedenergistics2")));
        assertEquals("mixins.qz_uilib.late.json", new LateMixins().getMixinConfig());
    }

    @Test
    public void clientWithoutAeDoesNotLoadMonitorCompatibility() {
        assertTrue(LateMixins.buildMixinsForSide(Side.CLIENT, Collections.emptySet()).isEmpty());
        assertTrue(LateMixins.buildMixinsForSide(Side.CLIENT, Collections.singleton("ae2")).isEmpty());
        assertTrue(LateMixins.buildMixinsForSide(Side.CLIENT, null).isEmpty());
    }

    @Test
    public void dedicatedServerNeverLoadsClientCompatibilityEvenWithAe() {
        assertTrue(LateMixins.buildMixinsForSide(Side.SERVER, Collections.singleton("appliedenergistics2")).isEmpty());
        assertTrue(LateMixins.buildMixinsForSide(Side.SERVER, Collections.emptySet()).isEmpty());
        assertTrue(LateMixins.buildMixinsForSide(Side.SERVER, null).isEmpty());
    }

    @Test
    public void unknownSideDoesNotLoadClientCompatibility() {
        assertTrue(LateMixins.buildMixinsForSide(null, Collections.singleton("appliedenergistics2")).isEmpty());
    }
}
