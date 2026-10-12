package club.heiqi.uilib.host.client.font;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import club.heiqi.uilib.host.client.settings.MinecraftApplicationConfiguration;
import club.heiqi.uilib.ui.config.ConfigCodecs;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.settings.SettingsConfiguration;

/** 原版字体特性声明与域外不可变投影；应用配置持有及订阅归MC应用装配。 */
public final class VanillaFontOptions {
    public static final ConfigKey<Boolean> REPLACE_VANILLA =
            new ConfigKey<>("mc.replaceVanillaFont", ConfigCodecs.BOOLEAN, true)
                    .withComment("是否使用 UILib 接管 MC 原版文字绘制。");
    private static volatile Settings settings = defaults();
    private VanillaFontOptions() {}
    private static Settings defaults() {
        try {
            return new Settings(REPLACE_VANILLA.defaultValue(), FontCandidateConfiguration.defaults(
                    FontTextService.defaultSources(VanillaFontOptions.class.getClassLoader(), 1)));
        } catch (IOException failure) { throw new UncheckedIOException("字体默认声明初始化失败", failure); }
    }
    private static final class Settings {
        final boolean enabled;
        final List<String> candidates;
        Settings(boolean enabled, List<String> candidates) {
            this.enabled=enabled;
            this.candidates=Collections.unmodifiableList(new ArrayList<>(candidates));
        }
    }
    /** 应用源域Effect发布已冻结值；FontRenderer只消费该投影，不读盘或重复派生。 */
    public static void publish(boolean enabled, List<String> candidates) { settings = new Settings(enabled, candidates); }
    public static boolean enabled() { return settings.enabled; }
    public static List<String> candidates() { return settings.candidates; }
    // 已有接入入口保留转发，新页面直接借用应用配置持有者。
    public static void load(File legacyFile) { MinecraftApplicationConfiguration.load(legacyFile); }
    public static ThemeConfiguration themeConfiguration() { return MinecraftApplicationConfiguration.themes(); }
    public static SettingsConfiguration settingsConfiguration() { return MinecraftApplicationConfiguration.settings(); }
    static void reload() { MinecraftApplicationConfiguration.reload(); }
}
