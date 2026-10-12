package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.font.FramebufferSceneRenderer;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.gl.Lwjgl3GlApi;
import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.host.client.image.ClientImageServices;
import club.heiqi.uilib.host.client.image.JavaImageLoader;
import club.heiqi.uilib.ui.scene.image.ImageService;
import club.heiqi.uilib.ui.scene.image.SharedImageService;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.settings.SettingsConfiguration;
import club.heiqi.uilib.ui.settings.SettingsPage;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontFamilyResolver;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;

/** 单窗口GLFW宿主；核心空间使用framebuffer像素，原生事件只在此转换一次。
 * GLFW只提供Unicode提交，不提供平台IME预编辑；不宣称支持预编辑组合界面。
 * 所有操作须在创建线程执行；关闭仍在有效GL上下文内结束GPU资源。
 */
public final class DesktopWindow implements SceneHost, AutoCloseable {
    final ReactiveDomain domain = ReactiveDomain.direct();
    final DesktopPage samplePage;
    final Path configPath;
    private final Thread thread = Thread.currentThread();
    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private long window, revision;
    private int framebufferWidth, framebufferHeight, windowWidth, windowHeight;
    private double cursorX, cursorY, scrollRemainder;
    private boolean closed;
    private String title;
    private Owner owner;
    private SettingsConfiguration settingsConfiguration;
    private SettingsPage settingsPage;
    private boolean pendingSettingsClose, pendingSettingsReload;
    ConfigSession configuration;
    ConfigKey<List<String>> fontCandidates;
    ClientFontServices fonts;
    private ClientImageServices images;
    private FramebufferSceneRenderer renderer;
    ScenePageSession page;
    PaintPlan lastPlan;
    int resizeCallbacks;

    private DesktopWindow(Path path, boolean sample) {
        configPath = Objects.requireNonNull(path, "configPath").toAbsolutePath().normalize();
        samplePage = sample ? new DesktopPage() : null;
    }
    public static DesktopWindow open(Path configuration) throws IOException {
        return open(configuration, null);
    }
    /** 业务页面直接接入既有ScenePage/ScenePageContext，无第二套页面抽象。 */
    public static DesktopWindow open(Path configuration, ScenePage content) throws IOException {
        return open(configuration, content, null);
    }
    /** 图像owner就绪后声明业务页面；服务归窗口，页面订阅仍归页面作用域。 */
    public static DesktopWindow openWithImages(Path configuration, Function<ImageService, ScenePage> content) throws IOException {
        return open(configuration, null, Objects.requireNonNull(content, "content"));
    }
    /** 与普通页面共用窗口、字体服务和配置域；关闭确认归共享配置页。 */
    public static DesktopWindow openSettings(Path configuration) throws IOException {
        return open(configuration, null, null, true);
    }
    private static DesktopWindow open(Path configuration, ScenePage content, Function<ImageService, ScenePage> factory) throws IOException {
        return open(configuration, content, factory, false);
    }
    private static DesktopWindow open(Path configuration, ScenePage content, Function<ImageService, ScenePage> factory, boolean settings) throws IOException {
        DesktopWindow result = new DesktopWindow(configuration, !settings && content == null && factory == null);
        try {
            result.initialize(content, factory, settings);
            return result;
        } catch (Throwable failure) {
            try { result.close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            if (failure instanceof IOException) throw (IOException) failure;
            if (failure instanceof UncheckedIOException) throw ((UncheckedIOException) failure).getCause();
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            if (failure instanceof Error) throw (Error) failure;
            throw new IllegalStateException(failure);
        }
    }
    private <T extends AutoCloseable> T own(T resource) { resources.push(resource); return resource; }
    private void initialize(ScenePage supplied, Function<ImageService, ScenePage> factory, boolean settings) throws IOException {
        GLFWErrorCallback errors = GLFWErrorCallback.createPrint(System.err);
        errors.set();
        own(() -> { glfwSetErrorCallback(null); errors.free(); });
        if (!glfwInit()) throw new IllegalStateException("GLFW初始化失败");
        own(() -> { GL.setCapabilities(null); glfwTerminate(); });
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
        window = glfwCreateWindow(800, 600, "UILib Desktop", 0, 0);
        if (window == 0) throw new IllegalStateException("桌面GL窗口创建失败");
        own(() -> { Callbacks.glfwFreeCallbacks(window); glfwMakeContextCurrent(0); glfwDestroyWindow(window); window = 0; });
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glfwSwapInterval(1);
        updateDimensions();
        ClassLoader loader = DesktopWindow.class.getClassLoader();
        fonts = new ClientFontServices(loader, 1, ClientFontServices.Settings.defaults(),
                true, new LwjglFontEngine(), new Lwjgl3GlApi(), GlProgramProvider.NONE);
        // worker与其native会话在销毁窗口前完成结束；只在关闭等待，不在帧里等待字形。
        own(this::closeFonts);
        images = new ClientImageServices(fonts.gl(), new JavaImageLoader(id -> {
            // 桌面资源显式按domain:path映射，不依赖游戏ResourceManager或游戏类路径。
            int separator = id.indexOf(':');
            if (separator <= 0 || separator == id.length() - 1)
                throw new IOException("桌面图像资源标识须为domain:path: " + id);
            String resource = "assets/" + id.substring(0, separator) + "/" + id.substring(separator + 1);
            InputStream snapshot = loader.getResourceAsStream(resource);
            if (snapshot == null) throw new IOException("桌面图像资源不存在: " + id + " -> " + resource);
            return snapshot;
        }, JavaImageLoader.Budget.defaults()), SharedImageService.Budget.defaults());
        own(this::closeImages);
        renderer = own(new FramebufferSceneRenderer(fonts, images));
        List<FontSource> defaults = FontTextService.defaultSources(loader, 1);
        fontCandidates = FontCandidateConfiguration.key(defaults);
        domain.execute(() -> {
            owner = domain.owner();
            own(() -> domain.execute(owner::close));
            configuration = new ConfigSession(domain, ConfigSchema.of(DesktopPage.PROFILE, fontCandidates));
            try {
                if (Files.exists(configPath)) configuration.load(configPath);
                else {
                    Path parent = configPath.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    configuration.save(configPath);
                }
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
            if (settings) settingsConfiguration = new SettingsConfiguration(domain, configuration,
                    fontCandidates, null, configPath, FontCandidateConfiguration.defaults(defaults),
                    this::validateFontCandidates, this::settingsResourcesReloaded);
            Signal<Long> reload = domain.signal(0L);
            reloadRevision = reload;
            domain.effect(owner, () -> {
                title = configuration.get(DesktopPage.PROFILE).title;
                glfwSetWindowTitle(window, title);
            });
            domain.effect(owner, () -> {
                reload.get();
                List<String> declarations = configuration.get(fontCandidates);
                Runnable update = () -> {
                    try { applyFonts(declarations); }
                    catch (IOException failure) { throw new UncheckedIOException(failure); }
                };
                if (settingsConfiguration != null) settingsConfiguration.updateResources(update);
                else update.run();
            });
        });
        ScenePage content = factory == null ? supplied : Objects.requireNonNull(factory.apply(images.images()), "content page");
        if (settings) {
            settingsPage = new SettingsPage(settingsConfiguration, this::leaveSettings);
            content = settingsPage;
        }
        page = own(ScenePageSession.open(domain, this, fonts,
                content == null ? ctx -> samplePage.build(ctx, configuration, this::reloadUnchecked) : content));
        installCallbacks();
        glfwShowWindow(window);
    }
    private Signal<Long> reloadRevision;
    private void settingsResourcesReloaded() {
        domain.checkWrite();
        reloadRevision.set(reloadRevision.get() + 1);
        images.resourcesReloaded();
    }
    private void closeImages() throws Exception {
        // close后owner不可再访问，先取得共享服务；仅关闭阶段等待worker，不阻塞正常帧。
        SharedImageService shared = images.shared();
        Throwable failure = null;
        try { images.close(); } catch (Throwable next) { failure = next; }
        try {
            if (!shared.awaitClose(5000)) throw new IllegalStateException("图像worker未结束");
        } catch (Throwable next) {
            if (next instanceof InterruptedException) Thread.currentThread().interrupt();
            if (failure == null) failure = next;
            else if (failure != next) failure.addSuppressed(next);
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof Exception) throw (Exception) failure;
        if (failure != null) throw new IllegalStateException(failure);
    }
    private void closeFonts() throws Exception {
        Throwable failure = null;
        try { fonts.close(); } catch (Throwable next) { failure = next; }
        try {
            if (!fonts.awaitRasterizerClose(5000)) throw new IllegalStateException("字体worker未结束");
        } catch (Throwable next) {
            if (next instanceof InterruptedException) Thread.currentThread().interrupt();
            if (failure == null) failure = next;
            else if (failure != next) failure.addSuppressed(next);
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof Exception) throw (Exception) failure;
        if (failure != null) throw new IllegalStateException(failure);
    }
    private void validateFontCandidates(List<String> declarations) {
        try { resolveFonts(declarations, revision + 1); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private void applyFonts(List<String> declarations) throws IOException {
        fonts.reload(resolveFonts(declarations, ++revision));
    }
    private List<FontSource> resolveFonts(List<String> declarations, long generation) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String path : FontCandidateConfiguration.files(declarations)) files.add(Paths.get(path));
        List<FontSource> builtins = FontTextService.defaultSources(DesktopWindow.class.getClassLoader(), generation);
        FontFamilyResolver resolver = new FontFamilyResolver(files, Collections.emptyList(), generation, System.err::println);
        return FontCandidateConfiguration.resolve(declarations, builtins,
                (path, position) -> resolver.family(Paths.get(path), "desktop-file-" + position));
    }
    public void reloadConfiguration() throws IOException {
        check();
        if (settingsPage != null) { pendingSettingsReload = true; return; }
        try {
            domain.execute(() -> {
                try { configuration.load(configPath); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
                reloadRevision.set(reloadRevision.get() + 1);
                images.resourcesReloaded();
            });
        } catch (UncheckedIOException failure) { throw failure.getCause(); }
    }
    private void reloadUnchecked() {
        try { reloadConfiguration(); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private void installCallbacks() {
        glfwSetCursorPosCallback(window, (w, x, y) -> cursor(x, y));
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> mouse(button, action));
        glfwSetScrollCallback(window, (w, x, y) -> scroll(y));
        glfwSetCharCallback(window, (w, codepoint) -> character(codepoint));
        glfwSetKeyCallback(window, (w, key, scan, action, mods) -> key(key, action, mods));
        glfwSetWindowFocusCallback(window, (w, focused) -> focus(focused));
        glfwSetFramebufferSizeCallback(window, (w, width, height) -> { resizeCallbacks++; updateDimensions(); });
        glfwSetWindowSizeCallback(window, (w, width, height) -> updateDimensions());
        glfwSetWindowContentScaleCallback(window, (w, x, y) -> { revision++; updateDimensions(); });
        glfwSetWindowCloseCallback(window, w -> {
            // GLFW已置关闭标记；确认期间必须继续帧循环，只有leave才真正置true。
            if (settingsPage != null) glfwSetWindowShouldClose(w, false);
            requestClose();
        });
        double[] x = new double[1], y = new double[1];
        glfwGetCursorPos(window, x, y);
        cursorX = x[0]; cursorY = y[0];
    }
    private void updateDimensions() {
        int[] width = new int[1], height = new int[1];
        glfwGetFramebufferSize(window, width, height);
        framebufferWidth = width[0]; framebufferHeight = height[0];
        glfwGetWindowSize(window, width, height);
        windowWidth = width[0]; windowHeight = height[0];
    }
    void cursor(double x, double y) {
        check(); cursorX = x; cursorY = y;
        page.pointer(ScenePointerEvent.move(sceneX(), sceneY()));
    }
    private int sceneX() { return windowWidth <= 0 ? 0 : (int)Math.floor(cursorX * framebufferWidth / windowWidth); }
    private int sceneY() { return windowHeight <= 0 ? 0 : (int)Math.floor(cursorY * framebufferHeight / windowHeight); }
    void mouse(int button, int action) {
        check();
        page.pointer(action == GLFW_PRESS ? ScenePointerEvent.down(button, sceneX(), sceneY())
                : ScenePointerEvent.up(button, sceneX(), sceneY()));
    }
    void scroll(double delta) {
        check(); scrollRemainder += delta;
        int whole = (int)scrollRemainder;
        if (whole != 0) { scrollRemainder -= whole; page.pointer(ScenePointerEvent.scroll(sceneX(), sceneY(), whole)); }
    }
    void character(int codepoint) {
        check();
        page.edit(SceneEditEvent.text(new String(Character.toChars(codepoint))));
    }
    private final SceneEditEvent.Clipboard clipboard = new SceneEditEvent.Clipboard() {
        public String read() {
            glfwGetError(null);
            String text = glfwGetClipboardString(window);
            checkClipboardError("读取", true);
            return text == null ? "" : text;
        }
        public void write(String text) {
            glfwGetError(null);
            glfwSetClipboardString(window, text);
            checkClipboardError("写入", false);
        }
    };
    private void checkClipboardError(String operation, boolean emptyTextAllowed) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer description = stack.mallocPointer(1);
            int error = glfwGetError(description);
            if (error == GLFW_NO_ERROR || emptyTextAllowed && error == GLFW_FORMAT_UNAVAILABLE) return;
            String reason = description.get(0) == 0 ? "" : description.getStringUTF8(0);
            throw new IllegalStateException("GLFW剪贴板" + operation + "失败 (" + error + "): " + reason);
        }
    }
    void key(int key, int action, int mods) {
        check();
        if (action == GLFW_RELEASE) return;
        boolean shift = (mods & GLFW_MOD_SHIFT) != 0, control = (mods & GLFW_MOD_CONTROL) != 0;
        boolean fresh = action == GLFW_PRESS;
        if (control && key == GLFW_KEY_R) { if (fresh) reloadUnchecked(); return; }
        SceneEditEvent.Command command = null;
        if (control) {
            switch (key) {
                case GLFW_KEY_A: command = SceneEditEvent.Command.SELECT_ALL; break;
                case GLFW_KEY_C: command = SceneEditEvent.Command.COPY; break;
                case GLFW_KEY_X: command = SceneEditEvent.Command.CUT; break;
                case GLFW_KEY_V: command = SceneEditEvent.Command.PASTE; break;
                case GLFW_KEY_Z: command = shift ? SceneEditEvent.Command.REDO : SceneEditEvent.Command.UNDO; break;
                case GLFW_KEY_Y: command = SceneEditEvent.Command.REDO; break;
                case GLFW_KEY_HOME: command = SceneEditEvent.Command.DOCUMENT_HOME; break;
                case GLFW_KEY_END: command = SceneEditEvent.Command.DOCUMENT_END; break;
                default: break;
            }
        } else {
            switch (key) {
                case GLFW_KEY_LEFT: command = SceneEditEvent.Command.LEFT; break;
                case GLFW_KEY_RIGHT: command = SceneEditEvent.Command.RIGHT; break;
                case GLFW_KEY_HOME: command = SceneEditEvent.Command.HOME; break;
                case GLFW_KEY_END: command = SceneEditEvent.Command.END; break;
                case GLFW_KEY_BACKSPACE: command = SceneEditEvent.Command.BACKSPACE; break;
                case GLFW_KEY_DELETE: command = SceneEditEvent.Command.DELETE; break;
                case GLFW_KEY_UP: command = SceneEditEvent.Command.UP; break;
                case GLFW_KEY_DOWN: command = SceneEditEvent.Command.DOWN; break;
                default: break;
            }
        }
        if (command != null) { page.edit(SceneEditEvent.command(command, shift, clipboard)); return; }
        if (!fresh) return;
        if (key == GLFW_KEY_TAB) page.editOrNavigate(SceneEditEvent.command(SceneEditEvent.Command.TAB, shift, clipboard),
                shift ? SceneNavigationEvent.PREVIOUS : SceneNavigationEvent.NEXT);
        else if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER)
            // Ctrl+Enter是多行显式提交，普通Enter仍先交编辑目标处理组字/换行。
            page.editOrNavigate(SceneEditEvent.command(control ? SceneEditEvent.Command.SUBMIT : SceneEditEvent.Command.CONFIRM, shift, clipboard), SceneNavigationEvent.ACTIVATE);
        else if (key == GLFW_KEY_SPACE)
            page.editOrNavigate(SceneEditEvent.command(SceneEditEvent.Command.ACTIVATE_ONLY, shift, clipboard), SceneNavigationEvent.ACTIVATE);
        else if (key == GLFW_KEY_ESCAPE) page.escape(this::requestClose);
    }
    void focus(boolean focused) {
        check();
        if (!focused) {
            page.focusLost();
        } else {
            boolean released = true;
            for (int b = 0; b <= GLFW_MOUSE_BUTTON_LAST; b++) released &= glfwGetMouseButton(window, b) == GLFW_RELEASE;
            if (released) page.pointer(ScenePointerEvent.allButtonsReleased());
        }
    }
    private void check() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("桌面窗口操作必须在创建线程");
        if (closed) throw new IllegalStateException("桌面窗口已关闭");
    }
    @Override public int width() { return framebufferWidth; }
    @Override public int height() { return framebufferHeight; }
    @Override public long metricsRevision() { return revision + fonts.fonts().generation() + fonts.readyVersion(); }
    /** 正式帧管线在测量/输入前接收完成结果，由既有图像信号使相关声明失效。 */
    @Override public void prepareResources() { check(); images.prepareResources(); }
    @Override public void replay(PaintPlan plan) { lastPlan = plan; renderer.replay(plan, width(), height()); }
    public void frame() {
        check(); glfwPollEvents();
        if (shouldClose() || width() <= 0 || height() <= 0) return;
        glfwMakeContextCurrent(window);
        glViewport(0, 0, width(), height());
        glDisable(GL_DEPTH_TEST); glDisable(GL_SCISSOR_TEST);
        glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
        fonts.beginPresentation(++presentation);
        try { page.frame(); } finally { fonts.endPresentation(); }
        // 原生关闭/快捷键先等本批已采集输入结算，避免确认读取旧草稿。
        // 意图一次性消费；关闭与重载同时到达时优先保留离开确认。
        if (pendingSettingsClose || pendingSettingsReload) {
            boolean close = pendingSettingsClose;
            pendingSettingsClose = pendingSettingsReload = false;
            if (!shouldClose()) {
                if (close) settingsPage.requestClose();
                else settingsPage.requestReload();
            }
        }
        glfwSwapBuffers(window);
    }
    private long presentation;
    public boolean shouldClose() { return closed || glfwWindowShouldClose(window); }
    public void requestClose() {
        check();
        page.pointer(ScenePointerEvent.cancel());
        if (settingsPage != null) pendingSettingsClose = true;
        else glfwSetWindowShouldClose(window, true);
    }
    private void leaveSettings() {
        check();
        // 确认后的真实关闭只结束窗口；持久化始终由页面显式保存承担。
        glfwSetWindowShouldClose(window, true);
    }
    long handle() { return window; }
    String title() { return title; }
    @Override public void close() {
        if (closed) return;
        if (Thread.currentThread() != thread) throw new IllegalStateException("桌面窗口必须在创建线程关闭");
        closed = true;
        Throwable failure = null;
        while (!resources.isEmpty()) {
            try { resources.pop().close(); }
            catch (Throwable next) { if (failure == null) failure = next; else if (failure != next) failure.addSuppressed(next); }
        }
        if (failure instanceof Error) throw (Error)failure;
        if (failure instanceof RuntimeException) throw (RuntimeException)failure;
        if (failure != null) throw new IllegalStateException("桌面窗口释放失败", failure);
    }
}
