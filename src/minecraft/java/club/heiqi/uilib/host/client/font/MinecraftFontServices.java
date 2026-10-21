package club.heiqi.uilib.host.client.font;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import club.heiqi.uilib.ui.scene.text.font.FontResources;
import club.heiqi.uilib.host.resource.ResourceManagerFontSources;
import club.heiqi.uilib.host.client.settings.MinecraftApplicationConfiguration;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.host.client.gl.GlApi;
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

/** MC资源管理适配，生产client init安装；字体/GL服务不依赖开发scene。
 * 父级退出挂接必须在 Minecraft.shutdownMinecraftApplet HEAD 主/GL线程调用 closeShared()，
 * 这里不使用shutdownhook，不在任意JVM线程释放主线程FontTextService或GL对象。
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
    // 显式资源重载先读取文件，再统一换代；避免同一load触发配置effect重复换代。
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
            shared = new ClientFontServices(prepared, ClientFontServices.Settings.defaults(), true,
                    IsolatedFontEngine.shared(), gl,
                    MinecraftHostExtensions.programs(MinecraftFontServices.class.getClassLoader(), gl));
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

    /** 配置入口不安装服务；启动前只保留快照，退出后通知也不能重新创建owner。 */
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

    /** 异步校验与随后apply复用同代、同声明的准备future；不发布配置或native状态。 */
    public static CompletableFuture<Void> validateCandidates(List<String> declarations) {
        if (shared == null) throw new IllegalStateException("客户端字体服务尚未安装/已关闭");
        checkThread();
        return preparation.validate(sources, declarations, systemDirectories()).thenApply(prepared -> null);
    }

    private static FontResourcePreparation.Sources captureSources(IResourceManager manager) {
        checkThread();
        try {
            // 只在当前主线程捕获streams，worker绝不打开当前manager。
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
                shared.reload(prepared);
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
        // 完整列表只消费已声明来源；默认内置资源作为可选目录，不再自动追加尾链。
        return FontCandidateConfiguration.resolve(
                declarations, builtins,
                (path, position) -> resolver.family(Paths.get(path), "system-file-" + position));
    }

    private static void checkThread() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("MC字体资源仅限客户端主线程");
    }

    /** 已发布字体排版代际；同来源候选变更也换代，外部缓存不能用来源revision代替。 */
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
     * 无文字消费时不创建呈现范围、worker或GL资源。 */
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
        // 应用配置先终止异步观察，桥和native owner仍按同一主线程生命周期释放。
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

    /** 仅资源指定的候选列表；读取失败显式报告，不回退classpath或append隐藏字体。
     * 旧plan已加载FontSource保持byte snapshot；退休代际未加载source拒绝重读新包。
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
