package club.heiqi.uilib.host.client.font;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import club.heiqi.uilib.ui.config.ConfigCodecs;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** MC 只声明专有开关、提供路径与事件；配置权威值、校验和文件读写由核心持有。
 * 旧 Forge 文件仅在新文件不存在时迁移，见无平台配置规格第 3 节。 */
public final class VanillaFontOptions {
    private static final VanillaFontOptions LISTENER = new VanillaFontOptions();
    private static final ConfigKey<Boolean> REPLACE_VANILLA =
            new ConfigKey<>("mc.replaceVanillaFont", ConfigCodecs.BOOLEAN, true);
    private static final ReactiveDomain DOMAIN = ReactiveDomain.direct();
    private static ConfigKey<List<String>> candidates;
    private static ConfigSession configuration;
    private static Owner projection;
    private static Path path;
    // 原版 FontRenderer 在域外频繁读开关，只消费核心发布的不可变投影，不重复计算或读文件。
    private static volatile Settings settings = defaults();

    private static Settings defaults() {
        try {
            // 模块来源当前只创建懒资源声明；保留其公开 IOException 通道，不做失败降级。
            return new Settings(REPLACE_VANILLA.defaultValue(),
                    FontCandidateConfiguration.defaults(FontTextService.defaultSources(
                            VanillaFontOptions.class.getClassLoader(), 1)));
        } catch (IOException error) {
            throw new UncheckedIOException("字体默认声明初始化失败", error);
        }
    }

    private static final class Settings {
        final boolean enabled;
        final List<String> candidates;

        Settings(boolean enabled, List<String> candidates) {
            this.enabled = enabled;
            this.candidates = candidates;
        }
    }

    private VanillaFontOptions() {}

    public static void load(File legacyFile) {
        if (configuration != null) throw new IllegalStateException("字体配置重复安装");
        String name = legacyFile.getName();
        int extension = name.lastIndexOf('.');
        path = legacyFile.toPath().resolveSibling(
                (extension < 0 ? name : name.substring(0, extension)) + ".properties");
        DOMAIN.execute(() -> {
            try {
                List<FontSource> builtins = FontTextService.defaultSources(
                        VanillaFontOptions.class.getClassLoader(), 1);
                candidates = FontCandidateConfiguration.key(builtins);
                configuration = new ConfigSession(DOMAIN, ConfigSchema.of(REPLACE_VANILLA, candidates));
                if (Files.exists(path)) {
                    configuration.load(path);
                } else {
                    if (legacyFile.isFile()) migrate(legacyFile, builtins);
                    Files.createDirectories(path.toAbsolutePath().getParent());
                    configuration.save(path);
                }
                projection = DOMAIN.owner();
                DOMAIN.effect(projection, () -> settings = new Settings(
                        configuration.get(REPLACE_VANILLA), configuration.get(candidates)));
            } catch (IOException error) {
                throw new UncheckedIOException("字体配置初始化失败: " + path, error);
            }
        });
        FMLCommonHandler.instance().bus().register(LISTENER);
        FMLLog.info("[Qz-UILib] 核心配置文件=%s；修改后通过资源重载更新", path);
    }

    private static void migrate(File file, List<FontSource> builtins) throws IOException {
        LegacyFontConfiguration.Snapshot old = LegacyFontConfiguration.read(file.toPath());
        List<String> migrated = FontCandidateConfiguration.migrate(old.paths, builtins);
        configuration.set(REPLACE_VANILLA, old.enabled);
        configuration.set(candidates, migrated);
        // 不 save 旧文件，不建立双写；新文件记录当次默认尾链的明确身份和顺序。
        FMLLog.info("[Qz-UILib] 旧字体配置一次迁移: %s -> %s", file, path);
    }

    /** 由 MC 资源重载或配置事件调用；核心先校验完整文件再发布。 */
    static void reload() {
        if (configuration == null) throw new IllegalStateException("字体配置尚未安装");
        DOMAIN.execute(() -> {
            try {
                configuration.load(path);
            } catch (IOException error) {
                throw new UncheckedIOException("字体配置重载失败: " + path, error);
            }
        });
    }

    public static boolean enabled() {
        return settings.enabled;
    }

    public static List<String> candidates() {
        return settings.candidates;
    }

    @SubscribeEvent
    public void changed(ConfigChangedEvent.OnConfigChangedEvent event) {
        if ("qz_uilib".equals(event.modID)) MinecraftFontServices.configurationChanged();
    }
}
