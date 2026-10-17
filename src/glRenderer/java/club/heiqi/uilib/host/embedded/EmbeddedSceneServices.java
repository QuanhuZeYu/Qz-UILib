package club.heiqi.uilib.host.embedded;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.image.ClientImageServices;
import club.heiqi.uilib.host.client.image.JavaImageLoader;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.image.ImageService;
import club.heiqi.uilib.ui.scene.image.SharedImageService;
import club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontFamilyResolver;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontEngine;
import club.heiqi.uilib.ui.settings.SettingsConfiguration;

/** 嵌入式应用的资源与配置归属。版本宿主只注入资源事实和具体绑定；页面借用同一服务。
 * 对应 specs/host/2026-10-18-uilib-minecraft-hosts-spec.md，不依赖 Minecraft 或窗口库。
 */
public final class EmbeddedSceneServices implements AutoCloseable {
    private final ReactiveDomain domain = ReactiveDomain.direct();
    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private final ClassLoader loader;
    private ClientFontServices fonts;
    private ClientImageServices images;
    private ConfigSession configuration;
    private ConfigKey<List<String>> candidates;
    private SettingsConfiguration settings;
    private ThemeConfiguration themes;
    private long revision = 1;
    private boolean closed;

    private EmbeddedSceneServices(ClassLoader loader) { this.loader = java.util.Objects.requireNonNull(loader, "loader"); }

    public static EmbeddedSceneServices open(Path path, ClassLoader loader, FontEngine engine, GlApi gl,
            JavaImageLoader.Resources imageResources) throws IOException {
        EmbeddedSceneServices result = new EmbeddedSceneServices(loader);
        try { result.initialize(path.toAbsolutePath().normalize(), engine, gl, imageResources); return result; }
        catch (IOException | RuntimeException | Error failure) {
            try { result.close(); } catch (Throwable cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private void initialize(Path path, FontEngine engine, GlApi gl, JavaImageLoader.Resources imageResources) throws IOException {
        fonts = new ClientFontServices(loader, revision, ClientFontServices.Settings.defaults(), engine, gl, GlProgramProvider.NONE);
        resources.push(this::closeFonts);
        images = new ClientImageServices(gl, new JavaImageLoader(imageResources, JavaImageLoader.Budget.defaults()), SharedImageService.Budget.defaults());
        resources.push(this::closeImages);
        List<FontSource> defaults = FontTextService.defaultSources(loader, revision);
        candidates = FontCandidateConfiguration.key(defaults);
        try {
            domain.execute(() -> {
                Owner owner = domain.owner();
                resources.push(() -> domain.execute(owner::close));
                configuration = new ConfigSession(domain, ConfigSchema.of(candidates, ThemeConfiguration.KEY));
                try {
                    if (Files.exists(path)) configuration.load(path);
                    else { if (path.getParent() != null) Files.createDirectories(path.getParent()); configuration.save(path); }
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
                themes = new ThemeConfiguration(domain, configuration, owner);
                settings = new SettingsConfiguration(domain, configuration, candidates, null, path,
                        FontCandidateConfiguration.defaults(defaults), this::validateFonts, this::resourcesReloaded);
                domain.effect(owner, () -> {
                    List<String> declarations = configuration.get(candidates);
                    settings.updateResources(() -> applyFonts(declarations));
                });
            });
        } catch (UncheckedIOException failure) { throw failure.getCause(); }
    }

    public ReactiveDomain domain() { return domain; }
    public ClientFontServices fonts() { return fonts; }
    public SettingsConfiguration settings() { return settings; }
    public ThemeConfiguration themes() { return themes; }
    public ImageService images() { return images.images(); }
    ClientImageServices clientImages() { return images; }

    private List<FontSource> resolveFonts(List<String> declarations, long generation) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String file : FontCandidateConfiguration.files(declarations)) files.add(Paths.get(file));
        List<FontSource> builtins = FontTextService.defaultSources(loader, generation);
        FontFamilyResolver resolver = new FontFamilyResolver(files, Collections.emptyList(), generation, System.err::println);
        return FontCandidateConfiguration.resolve(declarations, builtins,
                (file, position) -> resolver.family(Paths.get(file), "embedded-file-" + position));
    }
    private void validateFonts(List<String> declarations) {
        try { resolveFonts(declarations, revision + 1); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private void applyFonts(List<String> declarations) {
        try { fonts.reload(resolveFonts(declarations, ++revision)); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    /** 宿主在自己的资源重载完成边界通知；同语言或同一路径重载也使所属资源失效。 */
    public void resourcesReloaded() {
        domain.execute(() -> settings.updateResourcesOrThrow(() -> {
            applyFonts(configuration.get(candidates));
            images.resourcesReloaded();
        }));
    }

    private void closeFonts() throws Exception {
        closeAndAwait(fonts, () -> fonts.awaitRasterizerClose(5000), "字体");
    }
    private void closeImages() throws Exception {
        SharedImageService shared = images.shared();
        closeAndAwait(images, () -> shared.awaitClose(5000), "图像");
    }
    private interface Await { boolean complete() throws InterruptedException; }
    private static void closeAndAwait(AutoCloseable resource, Await await, String name) throws Exception {
        Throwable failure = null;
        try { resource.close(); } catch (Throwable next) { failure = next; }
        try { if (!await.complete()) throw new IllegalStateException("嵌入宿主" + name + "worker未结束"); }
        catch (Throwable next) {
            if (next instanceof InterruptedException) Thread.currentThread().interrupt();
            if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next);
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof Exception) throw (Exception) failure;
        if (failure != null) throw new IllegalStateException(failure);
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        Throwable failure = null;
        while (!resources.isEmpty()) try { resources.pop().close(); }
        catch (Throwable next) { if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next); }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure != null) throw new IllegalStateException("嵌入宿主资源释放失败", failure);
    }
}
