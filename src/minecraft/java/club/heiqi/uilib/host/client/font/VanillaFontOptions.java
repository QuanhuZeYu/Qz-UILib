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
    public static final ConfigKey<Boolean> AGGRESSIVE_BATCHING =
            new ConfigKey<>("render.aggressiveBatching", ConfigCodecs.BOOLEAN, false)
                    .withComment("按兼容绘制状态集中提交以减少纹理切换；开启可改变重叠文字、阴影或透明图元的覆盖顺序。修改后通过 F3+T 资源重载生效。");
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
    /** 应用源域Effect发布已冻结值；绘制入口只消费该投影，不读盘或重复派生。 */
    public static void publish(boolean enabled, List<String> candidates, boolean aggressiveBatching) {
        settings = new Settings(enabled, candidates, aggressiveBatching);
    }
    public static boolean enabled() { return settings.enabled; }
    public static List<String> candidates() { return settings.candidates; }
    public static boolean aggressiveBatching() { return settings.aggressiveBatching; }
    // 已有接入入口保留转发，新页面直接借用应用配置持有者。
    public static void load(File legacyFile) { MinecraftApplicationConfiguration.load(legacyFile); }
    public static ThemeConfiguration themeConfiguration() { return MinecraftApplicationConfiguration.themes(); }
    public static SettingsConfiguration settingsConfiguration() { return MinecraftApplicationConfiguration.settings(); }
    static void reload() { MinecraftApplicationConfiguration.reload(); }
}
