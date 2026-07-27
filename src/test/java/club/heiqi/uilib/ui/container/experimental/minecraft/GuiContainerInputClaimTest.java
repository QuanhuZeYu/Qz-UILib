package club.heiqi.uilib.ui.container.experimental.minecraft;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.container.experimental.model.EntryKey;

/** pointer/key owner claim 的值不变量。 */
public class GuiContainerInputClaimTest {
    private static final EntryKey KEY = new EntryKey("test", "entry");

    @Test
    public void onlyLongEntryCarriesAKey() {
        ContainerInputClaim entry = ContainerInputClaim.longEntry(KEY);
        Assert.assertEquals(ContainerInputOwner.LONG_ENTRY, entry.owner());
        Assert.assertEquals(KEY, entry.entryKey());
        Assert.assertTrue(entry.consumesVanilla());
        Assert.assertFalse(ContainerInputClaim.vanilla().consumesVanilla());
        Assert.assertFalse(ContainerInputClaim.none().consumesVanilla());
    }

    @Test
    public void rejectsOwnerKeyMismatch() {
        try {
            new ContainerInputClaim(ContainerInputOwner.SCENE_OVERLAY, KEY);
            Assert.fail();
        } catch (IllegalArgumentException expected) { }
        try {
            new ContainerInputClaim(ContainerInputOwner.LONG_ENTRY, null);
            Assert.fail();
        } catch (IllegalArgumentException expected) { }
    }

    @Test
    public void sceneOwnersConsumeAndVanillaOwnersPassThrough() {
        Assert.assertTrue(new ContainerInputClaim(ContainerInputOwner.SCENE_OVERLAY, null).consumesVanilla());
        Assert.assertTrue(new ContainerInputClaim(ContainerInputOwner.SCENE_FOCUSED, null).consumesVanilla());
        Assert.assertTrue(ContainerInputClaim.longEntry(KEY).consumesVanilla());
        Assert.assertFalse(ContainerInputClaim.vanilla().consumesVanilla());
    }
}
