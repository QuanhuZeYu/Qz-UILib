package club.heiqi.uilib.host.client.settings;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import club.heiqi.uilib.host.client.font.FontConfigurationFiles;
import club.heiqi.uilib.host.client.font.MinecraftFontServices;
import club.heiqi.uilib.host.client.font.VanillaFontOptions;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.settings.SettingsConfiguration;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** MC应用唯一配置装配；字体声明归特性，页面只借用主题/设置，不拥有应用订阅。 */
public final class MinecraftApplicationConfiguration {
    private static final MinecraftApplicationConfiguration LISTENER = new MinecraftApplicationConfiguration();
    private static final ReactiveDomain DOMAIN = ReactiveDomain.direct();
    private static ConfigSession session;
    private static SettingsConfiguration settings;
    private static ThemeConfiguration themes;
    private static ConfigKey<List<String>> fonts;
    private static Path path;
    private static Owner owner;
    private static Signal<Long> reloadResources;
    private MinecraftApplicationConfiguration() {}

    public static void load(File legacyFile) {
        if (session != null) throw new IllegalStateException("MC应用配置重复安装");
        DOMAIN.execute(() -> {
            try {
                List<FontSource> builtins = FontTextService.defaultSources(MinecraftApplicationConfiguration.class.getClassLoader(), 1);
                fonts = FontCandidateConfiguration.key(builtins);
                session = new ConfigSession(DOMAIN, ConfigSchema.of(VanillaFontOptions.REPLACE_VANILLA, fonts, ThemeConfiguration.KEY));
                path = FontConfigurationFiles.initialize(session, legacyFile.toPath(), VanillaFontOptions.REPLACE_VANILLA, fonts, builtins);
                reloadResources = DOMAIN.signal(0L);
                settings = new SettingsConfiguration(DOMAIN, session, fonts, VanillaFontOptions.REPLACE_VANILLA,
                        path, FontCandidateConfiguration.defaults(builtins), MinecraftFontServices::validateCandidates,
                        () -> reloadResources.set(reloadResources.get() + 1));
                owner = DOMAIN.owner();
                themes = new ThemeConfiguration(DOMAIN, session, owner);
                DOMAIN.effect(owner, () -> VanillaFontOptions.publish(session.get(VanillaFontOptions.REPLACE_VANILLA), session.get(fonts)));
                DOMAIN.effect(owner, () -> {
                    reloadResources.get();
                    List<String> declarations = session.get(fonts);
                    // 安装前及F3T去重时没有实际更新，不宣布资源成功或清除旧诊断。
                    if (MinecraftFontServices.configurationUpdateAvailable())
                        settings.updateResources(() -> MinecraftFontServices.configurationChanged(declarations));
                });
            } catch (IOException failure) { throw new UncheckedIOException("MC应用配置初始化失败: " + path, failure); }
        });
        FMLCommonHandler.instance().bus().register(LISTENER);
        FMLLog.info("[Qz-UILib] 核心配置文件=%s；修改后通过资源重载更新", path);
    }

    public static ThemeConfiguration themes() {
        if (themes == null) throw new IllegalStateException("MC应用配置尚未安装");
        return themes;
    }
    public static SettingsConfiguration settings() {
        if (settings == null) throw new IllegalStateException("MC应用配置尚未安装");
        return settings;
    }
    public static void reload() {
        SettingsConfiguration current = settings();
        DOMAIN.execute(() -> {
            try { current.reload(); }
            catch (IOException failure) { throw new UncheckedIOException("MC应用配置重载失败: " + path, failure); }
        });
    }
    /** 资源listener执行真实字体重建：同一诊断边界记录失败，原异常继续交资源管理器。 */
    public static void updateFontResourcesOrThrow(Runnable update) {
        SettingsConfiguration current = settings();
        DOMAIN.execute(() -> current.updateResourcesOrThrow(update));
    }
    @SubscribeEvent
    public void changed(ConfigChangedEvent.OnConfigChangedEvent event) {
        if ("qz_uilib".equals(event.modID) && MinecraftFontServices.configurationUpdateAvailable())
            updateFontResourcesOrThrow(() -> MinecraftFontServices.configurationChanged(VanillaFontOptions.candidates()));
    }
}
