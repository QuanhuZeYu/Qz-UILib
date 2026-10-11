package club.heiqi.uilib.internal.devtools;
import club.heiqi.uilib.ui.host.minecraft.MinecraftSceneScreen;
import club.heiqi.uilib.testing.gallery.GalleryEnvironment;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.ui.scene.image.ImageService;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.host.client.image.MinecraftImageServices;
import club.heiqi.uilib.host.client.image.MinecraftItemSource;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.enchantment.Enchantment;
/** MC仅供给平台来源；导航/内容/草稿由共享广场声明。 */
public final class CoreLoopScreen extends MinecraftSceneScreen {
    public CoreLoopScreen(TestGallery.Page initial) { super(new TestGallery(new Environment(), initial)); }
    @Override public boolean doesGuiPauseGame() { return false; }
    private static final class Environment implements GalleryEnvironment {
        public ImageService images() { return MinecraftImageServices.shared().images(); }
        public String initialLanguage() { return "zh_CN".equals(Minecraft.getMinecraft().gameSettings.language) ? "zh_CN" : "en_US"; }
        public boolean hasItems() { return true; }
        public ImageSource item(boolean enchanted, int amount, int damage) {
            ItemStack stack = new ItemStack(enchanted ? Items.diamond_pickaxe : Items.diamond, amount, enchanted ? damage : 0);
            if (enchanted) stack.addEnchantment(Enchantment.efficiency, 3);
            return new MinecraftItemSource(stack);
        }
        public ImageSource embed(boolean alternate) {
            ItemStack stack = new ItemStack(alternate ? Items.diamond_pickaxe : Items.iron_ingot, alternate ? 1 : 32, alternate ? 900 : 0);
            if (alternate) stack.addEnchantment(Enchantment.efficiency, 3);
            return new MinecraftItemSource(stack);
        }
    }
}
