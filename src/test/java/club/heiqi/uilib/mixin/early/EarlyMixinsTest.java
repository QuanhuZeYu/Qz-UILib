package club.heiqi.uilib.mixin.early;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import cpw.mods.fml.relauncher.Side;

/**
 * `EarlyMixins` 启动侧过滤测试。
 */
public class EarlyMixinsTest {

    @Test
    public void shouldNotReturnClientMixinsOnDedicatedServerSide() {
        List<String> mixins = EarlyMixins.buildMixinsForSide(Side.SERVER);

        Assert.assertTrue(mixins.contains("network.MixinNetHandlerPlayServer"));
        Assert.assertFalse(mixins.contains("network.MixinNetHandlerPlayClient"));
        Assert.assertFalse(mixins.contains("MixinFontRenderer"));
        Assert.assertFalse(mixins.contains("MixinGuiScreenKeyboardIsolation"));
        Assert.assertFalse(mixins.contains("MixinGuiContainerKeyTypedIsolation"));
    }

    @Test
    public void shouldReturnClientAndServerNetworkMixinsOnClientSide() {
        List<String> mixins = EarlyMixins.buildMixinsForSide(Side.CLIENT);

        Assert.assertTrue(mixins.contains("network.MixinNetHandlerPlayClient"));
        Assert.assertTrue(mixins.contains("network.MixinNetHandlerPlayServer"));
    }
}
