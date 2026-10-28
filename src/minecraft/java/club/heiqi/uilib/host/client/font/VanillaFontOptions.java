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
import club.heiqi.uilib.ui.render.batch.BatchingConfiguration;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.settings.SettingsConfiguration;

/** 原版字体特性声明与域外不可变投影；应用配置持有及订阅归MC应用装配。 */
public final class VanillaFontOptions {
    public static final ConfigKey<Boolean> REPLACE_VANILLA =
            new ConfigKey<>("mc.replaceVanillaFont", ConfigCodecs.BOOLEAN, true)
                    .withComment("是否使用 UILib 接管 MC 原版文字绘制。");
    // 保留MC既有声明入口；共享配置页与桌面消费同一Core键身份。
    public static final ConfigKey<Boolean> AGGRESSIVE_BATCHING = BatchingConfiguration.KEY;
    private static volatile Settings settings = defaults();
    private VanillaFontOptions() {}
    private static Settings defaults() {
        try {
            return new Settings(REPLACE_VANILLA.defaultValue(), FontCandidateConfiguration.defaults(
                    FontTextService.defaultSources(VanillaFontOptions.class.getClassLoader(), 1)), AGGRESSIVE_BATCHING.defaultValue());
        } catch (IOException failure) { throw new UncheckedIOException("字体默认声明初始化失败", failure); }
    }
    private static final class Settings {
        final boolean enabled;
        final List<String> candidates;
        final boolean aggressiveBatching;
        Settings(boolean enabled, List<String> candidates, boolean aggressiveBatching) {
            this.enabled=enabled;
            this.candidates=Collections.unmodifiableList(new ArrayList<>(candidates));
            this.aggressiveBatching=aggressiveBatching;
        }
    }
    /** 已有调用只更新原版接管与候选，保留当前批处理策略。 */
    public static void publish(boolean enabled, List<String> candidates) { publish(enabled, candidates, settings.aggressiveBatching); }
    /** 应用源域 Effect 发布冻结投影，供绘制入口直接读取。 */
    public static void publish(boolean enabled, List<String> candidates, boolean aggressiveBatching) {
        settings = new Settings(enabled, candidates, aggressiveBatching);
    }
    public static boolean enabled() { return settings.enabled; }
    public static List<String> candidates() { return settings.candidates; }
    public static boolean aggressiveBatching() { return settings.aggressiveBatching; }
    // MC 接入入口统一转交应用配置持有者。
    public static void load(File legacyFile) { MinecraftApplicationConfiguration.load(legacyFile); }
    public static ThemeConfiguration themeConfiguration() { return MinecraftApplicationConfiguration.themes(); }
    public static SettingsConfiguration settingsConfiguration() { return MinecraftApplicationConfiguration.settings(); }
    static void reload() { MinecraftApplicationConfiguration.reload(); }
}
