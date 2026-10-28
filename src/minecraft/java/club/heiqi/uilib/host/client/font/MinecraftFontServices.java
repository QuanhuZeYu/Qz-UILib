package club.heiqi.uilib.host.client.font;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import club.heiqi.uilib.ui.scene.text.font.FontResources;
import club.heiqi.uilib.host.resource.ResourceManagerFontSources;
import club.heiqi.uilib.host.client.settings.MinecraftApplicationConfiguration;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlGlyphAtlas;
import club.heiqi.uilib.host.client.gl.GlTokens;
import club.heiqi.uilib.host.client.extension.MinecraftHostExtensions;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import cpw.mods.fml.common.FMLLog;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

/** MC 资源管理适配，由客户端 init 安装。
 * Minecraft.shutdownMinecraftApplet HEAD 在主/GL 线程调用 closeShared()，
 * 在上下文销毁前结束所属 FontTextService 与 GL 资源。
 */
public final class MinecraftFontServices {
    private static ClientFontServices shared;
    private static ResourceFonts loader;
    private static FontResourcePreparation preparation;
    private static FontResourcePreparation.Sources sources;
    private static long request, resourceRequest;
    private static final java.util.Set<CompletableFuture<Void>> pending = new java.util.LinkedHashSet<>();
    private static Thread thread;
    private static long revision, resourceRevision;
    private static boolean installed;
    // 显式资源重载先读取文件，再统一换代；配置 Effect 在该阶段合并到同次更新。
    private static boolean resourceReloading;

    private MinecraftFontServices() {}

    public static void installShared() {
        if (installed) {
            checkThread();
            if (shared == null)
                throw new IllegalStateException("客户端字体owner已退出，不可重新注册reload listener");
            return;
        }
        thread = Thread.currentThread();
        IResourceManager resources = Minecraft.getMinecraft().getResourceManager();
        if (!(resources instanceof IReloadableResourceManager))
            throw new IllegalStateException("字体资源要求可重载MC ResourceManager");
        ResourceFonts initial = new ResourceFonts(resources, thread);
        try {
            // 1.7 init():void 后立即安装原版测量入口，尚无异步bootstrap屏障。
            // 此处显式同步准备整代；运行中的配置/资源变化全部经同一owner的worker准备。
            long generation = ++resourceRevision;
            revision = generation;
            List<FontSource> builtins = FontTextService.defaultSources(initial, generation);
            FontResources prepared = FontResources.prepare(initial, generation, builtins,
                    resolveCandidates(VanillaFontOptions.candidates(), builtins, generation));
            GlApi gl = MinecraftGlHost.create();
            shared = new ClientFontServices(prepared, fontSettings(gl), true,
                    IsolatedFontEngine.shared(), gl,
                    MinecraftHostExtensions.programs(MinecraftFontServices.class.getClassLoader(), gl));
            shared.allowBatchReordering(VanillaFontOptions.aggressiveBatching());
            preparation = new FontResourcePreparation();
            sources = FontResourcePreparation.preparedSources(initial, prepared, builtins);
            loader = initial;
        } catch (IOException failure) { throw new IllegalStateException("客户端字体服务安装失败", failure); }
        installed = true;
        // MC注册listener会立即回调；安装期也从同一入口建立配置+内置候选的新代际。
        ((IReloadableResourceManager) resources)
                .registerReloadListener(
                        new IResourceManagerReloadListener() {
                            @Override
                            public void onResourceManagerReload(IResourceManager manager) {
                                if (shared == null) return;
                                checkThread();
                                for (club.heiqi.uilib.host.client.extension.MinecraftHostExtension extension
                                        : club.heiqi.uilib.host.client.extension.MinecraftHostExtensions.services())
                                    extension.resourcesReloaded();
                                resourceReloading = true;
                                long reload = ++resourceRequest;
                                FontResourcePreparation.Sources captured = captureSources(manager);
                                preparation.prepare(captured, FontCandidateConfiguration.defaults(captured.builtins), Collections.emptyList());
                                MinecraftApplicationConfiguration.reload().whenComplete((ignored, failure) -> executeOwner(() -> {
                                    if (shared == null || reload != resourceRequest) return;
                                    if (failure != null) { resourceReloading = false; reportFailure(failure); return; }
                                    sources = captured;
                                    applyCandidates(VanillaFontOptions.candidates()).whenComplete((unused, error) -> executeOwner(() -> {
                                        if (reload == resourceRequest) resourceReloading = false;
                                        if (error != null) reportFailure(error);
                                    }));
                                }));
                            }
                        });
    }

    /** 已安装且处于配置更新阶段时返回可用；服务创建与退出归客户端生命周期。 */
    public static boolean configurationUpdateAvailable() {
        if (shared == null) return false;
        checkThread();
        return !resourceReloading;
    }

    /** apply只消费当前发布值；文件读取属于显式F3T或共享配置页reload。 */
    public static CompletableFuture<Void> configurationChanged(List<String> declarations) {
        if (shared == null) return CompletableFuture.completedFuture(null);
        checkThread();
        if (resourceReloading) return CompletableFuture.completedFuture(null);
        return applyCandidates(declarations);
    }

    /** 异步校验与 apply 复用同代、同声明的准备 future，发布由 apply 负责。 */
    public static CompletableFuture<Void> validateCandidates(List<String> declarations) {
        if (shared == null) throw new IllegalStateException("客户端字体服务尚未安装/已关闭");
        checkThread();
        return preparation.validate(sources, declarations, systemDirectories()).thenApply(prepared -> null);
    }

    private static FontResourcePreparation.Sources captureSources(IResourceManager manager) {
        checkThread();
        try {
            // 主线程捕获本代独立 streams，worker 消费捕获结果。
            ResourceManagerFontSources captured = new ResourceManagerFontSources(MinecraftFontServices.class.getClassLoader(),
                    id -> manager.getResource(new ResourceLocation(id)).getInputStream());
            return captured.capture(++resourceRevision);
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    private static CompletableFuture<Void> applyCandidates(List<String> declarations) {
        long current = ++request;
        CompletableFuture<Void> completion = new CompletableFuture<>();
        pending.add(completion);
        completion.whenComplete((ignored, failure) -> pending.remove(completion));
        FontResourcePreparation.Sources requestedSources = sources;
        preparation.prepare(requestedSources, declarations, systemDirectories()).whenComplete((prepared, failure) -> executeOwner(() -> {
            if (shared == null || current != request) { completion.cancel(false); return; }
            MinecraftApplicationConfiguration.applyFontResources(() -> {
                if (shared == null || current != request) { completion.cancel(false); return; }
                if (failure != null) throw resourceFailure(failure);
                try {
                    shared.reload(prepared);
                } finally {
                    // 字体换代时清空 CPU 采样 memo，释放旧 logical 布局引用。
                    MinecraftVanillaFont.fontResourcesChanged();
                }
                revision = prepared.revision;
                preparation.applied(requestedSources, declarations);
                if (loader != null) { loader.retire(); loader = null; }
                FMLLog.info("[Qz-UILib] 字体候选换代 revision=%s candidates=%s", revision, declarations);
            }).whenComplete((ignored, error) -> {
                if (error == null) completion.complete(null);
                else { completion.completeExceptionally(error); reportFailure(error); }
            });
        }));
        return completion;
    }
    private static void executeOwner(Runnable action) {
        if (Thread.currentThread() == thread) action.run(); else Minecraft.getMinecraft().func_152344_a(action);
    }
    private static RuntimeException resourceFailure(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        if (failure instanceof Error) throw (Error) failure;
        return failure instanceof RuntimeException ? (RuntimeException) failure : new IllegalStateException("MC字体资源准备失败", failure);
    }
    private static void reportFailure(Throwable failure) {
        FMLLog.log(org.apache.logging.log4j.Level.ERROR, failure, "[Qz-UILib] 字体资源准备/发布失败");
    }
    private static List<java.nio.file.Path> systemDirectories() {
        List<java.nio.file.Path> directories = new ArrayList<>();
        String windows = System.getenv("WINDIR"), local = System.getenv("LOCALAPPDATA");
        if (windows != null) directories.add(Paths.get(windows, "Fonts"));
        if (local != null) directories.add(Paths.get(local, "Microsoft", "Windows", "Fonts"));
        return directories;
    }

    private static List<FontSource> resolveCandidates(
            List<String> declarations, List<FontSource> builtins, long generation) throws IOException {
        List<String> paths = FontCandidateConfiguration.files(declarations);
        List<java.nio.file.Path> configured = new ArrayList<java.nio.file.Path>();
        for (String path : paths) configured.add(Paths.get(path));
        List<java.nio.file.Path> directories = systemDirectories();
        club.heiqi.uilib.ui.scene.text.font.FontFamilyResolver resolver =
                paths.isEmpty() ? null : new club.heiqi.uilib.ui.scene.text.font.FontFamilyResolver(
                        configured, directories, generation,
                        message -> FMLLog.warning("[Qz-UILib] %s", message));
        // 按完整声明列表解析候选，内置资源以可选目录参与显式选择。
        return FontCandidateConfiguration.resolve(
                declarations, builtins,
                (path, position) -> resolver.family(Paths.get(path), "system-file-" + position));
    }

    /** 配置 Effect 在 owner 线程发布批处理策略，绘制入口消费当前值。 */
    public static void batchingChanged(boolean enabled) {
        if (shared == null) return;
        checkThread();
        shared.allowBatchReordering(enabled);
    }

    private static ClientFontServices.Settings fontSettings(GlApi gl) {
        ClientFontServices.Settings base = ClientFontServices.Settings.defaults();
        GlGlyphAtlas.Budget atlas = base.atlas;
        // 世界文字保持原顺序时，较大二维页可减少glyph跨页拆批；仍受真实GL能力和原总预算约束。
        int page = Math.min(1024, gl.glGetInteger(GlTokens.GL_MAX_TEXTURE_SIZE));
        return new ClientFontServices.Settings(base.cpu, base.mode,
                new GlGlyphAtlas.Budget(page, atlas.layers, atlas.maxGroups,
                        atlas.maxResidents, atlas.maxDemand, atlas.gpuBytes),
                base.upload, base.quadCapacity, base.maxDemand);
    }

    private static void checkThread() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("MC字体资源仅限客户端主线程");
    }

    /** 返回已发布的字体排版代际，供外部缓存响应来源重载与同来源候选变化。 */
    public static long fontGeneration() {
        return shared().fonts().generation();
    }

    public static long resourceRevision() {
        checkThread();
        return revision;
    }

    public static ClientFontServices shared() {
        checkThread();
        if (shared == null) throw new IllegalStateException("客户端字体服务尚未安装/已关闭");
        return shared;
    }

    /** LWJGL窗口交换之前的通知；org.lwjgl类被宿主排除转换，故由MC调用点转交。
     * 仅结束已激活的文字呈现范围。 */
    public static void beforePresent() {
        if (shared == null) return;
        checkThread();
        MinecraftVanillaFont.beforePresent();
        if (shared.presentationStats().active) shared.endPresentation();
    }

    public static void closeShared() {
        if (shared == null) return;
        checkThread();
        ClientFontServices owner = shared;
        shared = null;
        for (CompletableFuture<Void> update : new ArrayList<>(pending)) update.cancel(false);
        // 在同一主线程先结束原版桥，再逆序释放应用配置与字体 owner。
        try (ClientFontServices closing = owner; AutoCloseable configuration = MinecraftApplicationConfiguration::close) {
            MinecraftVanillaFont.close();
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("MC字体资源退出失败", failure); }
        finally {
            if (preparation != null) { preparation.close(); preparation = null; }
            sources = null;
            if (loader != null) {
                loader.retire();
                loader = null;
            }
        }
    }

    /** qz_uilib 资产从本代 ResourceManager 读取，读取失败携带资源身份报告。
     * 旧 plan 保留已加载 FontSource 的字节快照，退休代际拒绝再次读取。
     */
    private static final class ResourceFonts extends ClassLoader {
        private final IResourceManager resources;
        private final Thread thread;
        private boolean retired;

        ResourceFonts(IResourceManager resources, Thread thread) {
            super(MinecraftFontServices.class.getClassLoader());
            this.resources = resources;
            this.thread = thread;
        }

        void retire() {
            retired = true;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (!name.startsWith("assets/qz_uilib/")) return super.getResourceAsStream(name);
            if (Thread.currentThread() != thread || retired)
                throw new IllegalStateException("字体资源代际必须先在主线程measure加载: " + name);
            try {
                return resources
                        .getResource(
                                new ResourceLocation(
                                        "qz_uilib", name.substring("assets/qz_uilib/".length())))
                        .getInputStream();
            } catch (IOException failure) {
                throw new IllegalStateException("MC字体资源读取失败: " + name, failure);
            }
        }
    }
}
