package club.heiqi.uilib.mixin.early;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import cpw.mods.fml.relauncher.Side;

/**
 * `EarlyMixins` 启动侧过滤测试。
 */
public class EarlyMixinsTest {

    private static final String ANGELICA_TWEAKER = "com.gtnewhorizons.angelica.loading.AngelicaTweaker";

    @Test
    public void shouldNotReturnClientMixinsOnDedicatedServerSide() {
        List<String> mixins = EarlyMixins.buildMixinsForSide(Side.SERVER);

        Assert.assertTrue(mixins.contains("network.MixinNetHandlerPlayServer"));
        Assert.assertFalse(mixins.contains("network.MixinNetHandlerPlayClient"));
        Assert.assertFalse(mixins.contains("MixinFontRenderer"));
        Assert.assertFalse(mixins.contains("MixinGuiScreenKeyboardIsolation"));
        Assert.assertFalse(mixins.contains("MixinGuiContainerKeyTypedIsolation"));
        assertNoNameTagMixins(mixins);
    }

    @Test
    public void shouldReturnClientAndServerNetworkMixinsOnClientSide() {
        List<String> mixins = EarlyMixins.buildMixinsForSide(Side.CLIENT);

        Assert.assertTrue(mixins.contains("network.MixinNetHandlerPlayClient"));
        Assert.assertTrue(mixins.contains("network.MixinNetHandlerPlayServer"));
        Assert.assertTrue(mixins.contains("MixinGuiScreenKeyboardIsolation"));
        Assert.assertFalse(mixins.contains("MixinGuiContainerKeyTypedIsolation"));
        assertGenericNameTagMixins(mixins);
        Assert.assertFalse(mixins.contains("nametag.MixinAngelicaPlayerNameTagReplay"));
    }

    @Test
    public void shouldAddAngelicaGuardOnlyForClientWithAngelicaTweaker() {
        Set<String> loadedCoreMods = Collections.singleton(ANGELICA_TWEAKER);

        List<String> clientMixins = EarlyMixins.buildMixinsForSide(Side.CLIENT, loadedCoreMods);
        assertGenericNameTagMixins(clientMixins);
        Assert.assertTrue(clientMixins.contains("nametag.MixinAngelicaPlayerNameTagReplay"));

        List<String> serverMixins = EarlyMixins.buildMixinsForSide(Side.SERVER, loadedCoreMods);
        assertNoNameTagMixins(serverMixins);
    }

    private static void assertGenericNameTagMixins(List<String> mixins) {
        Assert.assertTrue(mixins.contains("nametag.MixinEntityRendererPlayerNameTagPass"));
        Assert.assertTrue(mixins.contains("nametag.MixinRendererLivingEntityPlayerNameTag"));
        Assert.assertTrue(mixins.contains("nametag.MixinRenderPlayerScoreboardNameTag"));
    }

    private static void assertNoNameTagMixins(List<String> mixins) {
        Assert.assertFalse(mixins.contains("nametag.MixinEntityRendererPlayerNameTagPass"));
        Assert.assertFalse(mixins.contains("nametag.MixinRendererLivingEntityPlayerNameTag"));
        Assert.assertFalse(mixins.contains("nametag.MixinRenderPlayerScoreboardNameTag"));
        Assert.assertFalse(mixins.contains("nametag.MixinAngelicaPlayerNameTagReplay"));
    }
}
