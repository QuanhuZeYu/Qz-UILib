package club.heiqi.uilib.host.client.settings;

import java.util.Collections;
import java.util.Set;

import club.heiqi.uilib.host.client.font.VanillaFontOptions;
import club.heiqi.uilib.ui.host.minecraft.MinecraftSceneScreen;
import club.heiqi.uilib.ui.settings.SettingsConfiguration;
import club.heiqi.uilib.ui.settings.SettingsPage;
import cpw.mods.fml.client.IModGuiFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/** Forge只负责正式入口与父屏幕；内容、编辑及确认由共享ScenePage承担。 */
public final class UILibGuiFactory implements IModGuiFactory {
    @Override public void initialize(Minecraft minecraftInstance) {}

    @Override public Class<? extends GuiScreen> mainConfigGuiClass() {
        return SettingsScreen.class;
    }

    @Override public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }

    @Override public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }

    /** Forge通过公开GuiScreen父屏幕构造器打开，借用同一个MC配置域。 */
    public static final class SettingsScreen extends MinecraftSceneScreen {
        public SettingsScreen(GuiScreen parent) {
            this(parent, VanillaFontOptions.settingsConfiguration());
        }

        private SettingsScreen(GuiScreen parent, SettingsConfiguration configuration) {
            super(configuration.domain(), new SettingsPage(configuration,
                    () -> Minecraft.getMinecraft().displayGuiScreen(parent)));
        }
    }
}
