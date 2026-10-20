package club.heiqi.uilib.host.client.settings;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import club.heiqi.uilib.host.client.font.FontConfigurationFiles;
import club.heiqi.uilib.host.client.font.MinecraftFontServices;
import club.heiqi.uilib.host.client.font.VanillaFontOptions;
import club.heiqi.uilib.ui.config.ConfigDocument;
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
import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;

/** MC应用唯一配置装配；字体声明归特性，页面只借用主题/设置，不拥有应用订阅。 */
public final class MinecraftApplicationConfiguration {
    private static final MinecraftApplicationConfiguration LISTENER = new MinecraftApplicationConfiguration();
    private static final ConcurrentLinkedQueue<Runnable> OWNER_ACTIONS = new ConcurrentLinkedQueue<>();
    private static final ReactiveDomain DOMAIN = new ReactiveDomain(MinecraftApplicationConfiguration::executeOwner);
    private static ConfigSession session;
    private static SettingsConfiguration settings;
    private static ThemeConfiguration themes;
    private static ConfigKey<List<String>> fonts;
    private static Path path;
    private static Owner owner;
    private static Signal<Long> reloadResources;
    private MinecraftApplicationConfiguration() {}

    private static void executeOwner(Runnable action) {
        OWNER_ACTIONS.add(action);
        Minecraft client = Minecraft.getMinecraft();
        // 原任务队列负责正常调度；同一 drain 也可在无后续 tick 的退出屏障结算。
        if (client.func_152345_ab()) pumpOwner();
        else client.func_152344_a(MinecraftApplicationConfiguration::pumpOwner);
    }

    private static void pumpOwner() {
        for (Runnable action; (action = OWNER_ACTIONS.poll()) != null;) action.run();
    }

    /** 1.7 的 preInit():void 后紧接 init():void 安装字体，尚无可等待的异步加载屏障。
     * 保留同步 bootstrap 的真实配置意图；有宿主加载屏障后才将 prepare 移到 worker，
     * 不能先用默认配置安装字体，再让迟到文档改写启动选择。运行期 reload 已独立异步。 */
    public static void load(File legacyFile) {
        if (!Minecraft.getMinecraft().func_152345_ab()) throw new IllegalStateException("MC应用配置安装仅限客户端主线程");
        if (session != null) throw new IllegalStateException("MC应用配置重复安装");
        try {
            List<FontSource> builtins = FontTextService.defaultSources(MinecraftApplicationConfiguration.class.getClassLoader(), 1);
            ConfigKey<List<String>> declaredFonts = FontCandidateConfiguration.key(builtins);
            ConfigSchema schema = ConfigSchema.of(VanillaFontOptions.REPLACE_VANILLA, declaredFonts, ThemeConfiguration.KEY);
            Path selected = FontConfigurationFiles.yamlPath(legacyFile.toPath());
            ConfigDocument document = FontConfigurationFiles.prepare(schema, legacyFile.toPath(),
                    VanillaFontOptions.REPLACE_VANILLA, declaredFonts, builtins);
            DOMAIN.execute(() -> {
                fonts = declaredFonts;
                path = selected;
                session = new ConfigSession(DOMAIN, schema);
                session.publish(document);
                reloadResources = DOMAIN.signal(0L);
                settings = SettingsConfiguration.withAsyncValidation(DOMAIN, session, fonts, VanillaFontOptions.REPLACE_VANILLA,
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
                        MinecraftFontServices.configurationChanged(declarations);
                });
                EventBus bus = FMLCommonHandler.instance().bus();
                bus.register(LISTENER);
                owner.own(() -> bus.unregister(LISTENER));
            });
        } catch (IOException failure) { throw new UncheckedIOException("MC应用配置初始化失败: " + legacyFile, failure); }
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

    /** future 表达文档读取与原域发布完成；字体准备及其诊断归原资源 owner。 */
    public static CompletableFuture<Void> reload() {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        DOMAIN.execute(() -> settings().reload().whenComplete((ignored, failure) -> {
            if (failure == null) completed.complete(null);
            else completed.completeExceptionally(failure);
        }), completed::completeExceptionally);
        return completed;
    }

    /** 客户端应用退出时结束配置请求及应用订阅；普通页面关闭不调用此入口。 */
    public static void close() {
        if (!Minecraft.getMinecraft().func_152345_ab()) throw new IllegalStateException("MC应用配置关闭仅限客户端主线程");
        // 先结算已从 worker 递交但游戏尚未 tick 的 drain，不能丢掉 domain 的唯一 scheduled 任务。
        try { pumpOwner(); }
        catch (RuntimeException | Error failure) {
            try { closeOnOwner(); }
            catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
        closeOnOwner();
    }

    private static void closeOnOwner() {
        DOMAIN.execute(() -> {
            try (Owner closingOwner = owner) {
                if (settings != null) settings.close();
            }
        });
        pumpOwner();
    }

    /** 资源 owner 递交真实发布；完成包括原配置域的诊断及 Effect 结算。 */
    public static CompletableFuture<Void> applyFontResources(Runnable actualApply) {
        Objects.requireNonNull(actualApply, "actualApply");
        CompletableFuture<Void> completed = new CompletableFuture<>();
        DOMAIN.enqueue(() -> {
            DOMAIN.deferCleanup(() -> completed.complete(null));
            if (owner == null || owner.isDisposed()) throw new CancellationException("MC应用配置所属资源范围已结束");
            settings().updateResourcesOrThrow(actualApply);
        }, completed::completeExceptionally);
        return completed;
    }
    @SubscribeEvent
    public void changed(ConfigChangedEvent.OnConfigChangedEvent event) {
        if ("qz_uilib".equals(event.modID) && MinecraftFontServices.configurationUpdateAvailable())
            MinecraftFontServices.configurationChanged(VanillaFontOptions.candidates());
    }
}
