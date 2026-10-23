package club.heiqi.uilib.ui.host.minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import club.heiqi.uilib.host.client.extension.MinecraftHostExtension;
import club.heiqi.uilib.host.client.extension.MinecraftHostExtensions;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneCommand;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot;
import club.heiqi.uilib.ui.scene.text.TextLayout;

/** 正式会话、命令和生产 Inputs；只在中性 SPI 与窗口事实边界提供隔离环境，不创建游戏或 GL。 */
public final class MinecraftScreenInputLifecycleScenario extends TestApplication {
    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        return (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null);
    }

    /** 唯一 owner 与退休来源只属于 fixture；不替代 Core 输入/焦点/组字权威。 */
    private static final class NativeEnvironment implements MinecraftHostExtension, AutoCloseable {
        final AtomicReference<Bridge> owner = new AtomicReference<>();
        final Field services = field(MinecraftHostExtensions.class, "SERVICES");
        final Field display = field(Display.class, "display_impl");
        final Field window = field(Display.class, "window_created");
        final sun.misc.Unsafe access = unsafe();
        final Object oldServices = services.get(null), oldDisplay = display.get(null);
        final boolean oldWindow = window.getBoolean(null), oldRepeat = Keyboard.areRepeatEventsEnabled();
        int opened, closed;
        RuntimeException initializationFailure;
        NativeEnvironment() throws Exception {
            require(!Display.isCreated() && !Keyboard.isCreated(), "场景只能运行于没有游戏窗口的隔离 JVM");
            Object active = java.lang.reflect.Proxy.newProxyInstance(oldDisplay.getClass().getClassLoader(),
                    new Class<?>[] {display.getType()}, (proxy, method, args) -> {
                        if (method.getName().equals("isActive")) return true;
                        throw new AssertionError("生命周期场景访问设备：" + method);
                    });
            replace(display, active);
            window.setBoolean(null, true);
            Keyboard.enableRepeatEvents(false);
            replace(services, Collections.singletonList(this));
        }
        private void replace(Field target, Object value) {
            access.putObjectVolatile(access.staticFieldBase(target), access.staticFieldOffset(target), value);
        }
        public boolean nativeInputAvailable(ClassLoader loader) { return true; }
        public SceneNativeTextInput.Port nativeInput(ClassLoader loader) {
            Bridge bridge = new Bridge(this);
            if (!owner.compareAndSet(null, bridge))
                throw new IllegalStateException("Another native text input bridge is registered");
            opened++;
            return bridge;
        }
        public void close() throws Exception {
            try {
                Bridge bridge = owner.get();
                if (bridge != null) bridge.close();
            } finally {
                replace(services, oldServices);
                replace(display, oldDisplay);
                window.setBoolean(null, oldWindow);
                Keyboard.enableRepeatEvents(oldRepeat);
            }
        }
    }

    private static final class Bridge implements SceneNativeTextInput.Port {
        final NativeEnvironment environment;
        boolean closed, text;
        int textCalls;
        Runnable closing;
        Bridge(NativeEnvironment environment) { this.environment = environment; }
        public boolean active() { return !closed; }
        public void textInput(boolean enabled) { textCalls++; if (!closed) text = enabled; }
        public void availability(boolean available) {
            if (environment.initializationFailure != null) throw environment.initializationFailure;
        }
        public List<SceneNativeTextInput.NativeEvent> drain() { return Collections.emptyList(); }
        public void close() {
            if (closed) return;
            closed = true;
            text = false;
            require(environment.owner.compareAndSet(this, null), "关闭旧桥抢占了新 owner");
            environment.closed++;
            if (closing != null) closing.run();
        }
    }

    private static final class Host implements SceneHost {
        int frames;
        public int width() { return 80; }
        public int height() { return 60; }
        public long metricsRevision() { return 1; }
        public void replay(PaintPlan plan) { frames++; }
    }

    private static final class Page implements AutoCloseable {
        final ReactiveDomain domain;
        final Host host = new Host();
        final MinecraftSceneScreen screen;
        final ScenePageSession session;
        final Object inputs;
        final Bridge bridge;
        int releases, commands;
        Runnable next;
        Runnable releasing;
        Page(Minecraft minecraft, NativeEnvironment environment, boolean editor) throws Exception {
            this(minecraft, environment, editor, ReactiveDomain.direct());
        }
        Page(Minecraft minecraft, NativeEnvironment environment, boolean editor, ReactiveDomain domain) throws Exception {
            this.domain = domain;
            screen = editor ? new MinecraftHudEditorScreen(domain, context -> {})
                    : new MinecraftSceneScreen(domain, context -> {});
            field(GuiScreen.class, "mc").set(screen, minecraft);
            session = ScenePageSession.open(domain, host,
                    (text, style, width) -> {
                        require(text.isEmpty(), "无字按钮不伪造字体度量");
                        return new TextLayout(0, 0, style, Collections.emptyList());
                    }, context -> {
                        context.own(() -> { releases++; if (releasing != null) releasing.run(); });
                        context.mount(SceneButton.create(context, () -> "", context.theme(),
                                SceneCommand.of(() -> true, () -> { commands++; next.run(); }))
                                .size(Size.fill(1), Size.fill(1)));
                    });
            inputs = installInputs(screen, session);
            bridge = environment.owner.get();
            minecraft.currentScreen = screen;
        }
        void click() {
            session.pointer(ScenePointerEvent.down(0, 10, 10));
            session.pointer(ScenePointerEvent.up(0, 10, 10));
            session.frame();
        }
        public void close() { screen.onGuiClosed(); }
    }

    private static Object installInputs(MinecraftSceneScreen screen, ScenePageSession session) throws Exception {
        field(MinecraftSceneScreen.class, "session").set(screen, session);
        Class<?> type = Class.forName(MinecraftSceneScreen.class.getName() + "$Inputs");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(
                MinecraftSceneScreen.class, ScenePageSession.class, Supplier.class);
        constructor.setAccessible(true);
        Object inputs = constructor.newInstance(screen, session, (Supplier<SceneSemanticSnapshot>) () -> SceneSemanticSnapshot.EMPTY);
        field(MinecraftSceneScreen.class, "inputs").set(screen, inputs);
        invoke(inputs, "initialize");
        return inputs;
    }

    private static void invoke(Object target, String method) throws Exception {
        Method action = target.getClass().getDeclaredMethod(method);
        action.setAccessible(true);
        try { action.invoke(target); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw failure;
        }
    }

    @Override protected void runTests() throws Exception {
        scenario("命令内设置与编辑页反复往返、旧页失效且独占输入同步转交", this::commandSwitches);
        scenario("native关闭与scope释放重入仍仅关闭所属资源", this::reentrantClose);
        scenario("输入初始化失败保留首错并清理已取得的会话和桥", this::initializationFailure);
        scenario("已排队的native焦点同步在关屏后不接触退休桥或新页", this::queuedFocus);
        scenario("正式文本字段组字在关屏时结束且迟到旧提交不落新页", this::compositionRetires);
    }

    private void commandSwitches() throws Exception {
        Minecraft minecraft = (Minecraft) unsafe().allocateInstance(Minecraft.class);
        List<Page> pages = new ArrayList<>();
        try (NativeEnvironment environment = new NativeEnvironment()) {
            try {
                Page current = new Page(minecraft, environment, false);
                pages.add(current);
                for (int index = 0; index < 12; index++) {
                    Page previous = current;
                    boolean editor = index % 2 == 0;
                    Page[] opened = {null};
                    previous.next = () -> {
                        previous.close();
                        require(previous.session.ended() && previous.releases == 0,
                                "命令内关屏没有立即失效或提前卸载正在路由的树");
                        try { opened[0] = new Page(minecraft, environment, editor); }
                        catch (RuntimeException failure) { throw failure; }
                        catch (Exception failure) { throw new IllegalStateException(failure); }
                        require(!previous.bridge.active() && !previous.bridge.text,
                                "命令内关屏尚未退休旧 native/IME 联系");
                        pages.add(opened[0]);
                    };
                    previous.bridge.textInput(true);
                    previous.click();
                    current = opened[0];
                    require(current != null && previous.commands == 1 && previous.releases == 1,
                            "命令没有完整切页或scope未释放");
                    require(environment.owner.get() == current.bridge && Keyboard.areRepeatEventsEnabled(),
                            "旧域延迟清理覆盖了新页 native owner/repeat 借用");
                    int frames = previous.host.frames;
                    previous.screen.drawScreen(0, 0, 0);
                    invoke(previous.inputs, "historyDrained");
                    invoke(previous.inputs, "syncNativeTextFocus");
                    previous.session.frame();
                    previous.session.pointer(ScenePointerEvent.up(0, 10, 10));
                    require(previous.screen.semantics() == SceneSemanticSnapshot.EMPTY && previous.host.frames == frames,
                            "关闭后迟到回调访问旧页或回放旧计划");
                    previous.close();
                    require(environment.owner.get() == current.bridge, "重复关闭破坏新桥");
                }
                current.close();
                require(environment.opened == 13 && environment.closed == 13 && environment.owner.get() == null,
                        "往返后 native 资源没有对称释放");
                require(!Keyboard.areRepeatEventsEnabled(), "最后关屏没有恢复原 repeat 设置");
                System.out.println("MC_NATIVE_LIFECYCLE switches=12 bridges=13 closed=13 scopeReleased=true lateCallbacksIgnored=true repeatRestored=true");
            } finally { for (Page page : pages) page.close(); }
        }
    }

    private void reentrantClose() throws Exception {
        Minecraft minecraft = (Minecraft) unsafe().allocateInstance(Minecraft.class);
        try (NativeEnvironment environment = new NativeEnvironment()) {
            Page first = new Page(minecraft, environment, false);
            Page[] second = {null};
            first.bridge.closing = first::close;
            first.releasing = () -> {
                first.close();
                try { second[0] = new Page(minecraft, environment, true); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            };
            try {
                first.close();
                require(first.releases == 1 && second[0] != null && environment.owner.get() == second[0].bridge,
                        "重入释放重复清理或阻止新页建立 native 联系");
                first.close();
                require(environment.closed == 1, "重入后重复关闭再次释放桥");
            } finally {
                first.close();
                if (second[0] != null) second[0].close();
            }
            require(environment.closed == 2 && !Keyboard.areRepeatEventsEnabled(), "重入清理资源不对称");
        }
    }

    private void queuedFocus() throws Exception {
        Minecraft minecraft = (Minecraft) unsafe().allocateInstance(Minecraft.class);
        java.util.ArrayDeque<Runnable> actions = new java.util.ArrayDeque<>();
        try (NativeEnvironment environment = new NativeEnvironment()) {
            Page previous = new Page(minecraft, environment, false, new ReactiveDomain(actions::add));
            Page current = null;
            try {
                while (!actions.isEmpty()) actions.remove().run();
                invoke(previous.inputs, "syncNativeTextFocus");
                require(!actions.isEmpty(), "异步域没有保留待同步焦点操作");
                int calls = previous.bridge.textCalls;
                previous.close();
                current = new Page(minecraft, environment, true);
                while (!actions.isEmpty()) actions.remove().run();
                require(previous.bridge.textCalls == calls && previous.releases == 1,
                        "已排队回调访问退休桥或旧 scope 未释放");
                require(environment.owner.get() == current.bridge && Keyboard.areRepeatEventsEnabled(),
                        "旧域排队的焦点/清理改变新页 native 或 repeat");
            } finally {
                previous.close();
                while (!actions.isEmpty()) actions.remove().run();
                if (current != null) current.close();
            }
        }
    }

    private void compositionRetires() throws Exception {
        Minecraft minecraft = (Minecraft) unsafe().allocateInstance(Minecraft.class);
        ReactiveDomain domain = ReactiveDomain.direct();
        Host host = new Host();
        club.heiqi.uilib.ui.scene.edit.SceneEditState[] state = {null};
        club.heiqi.uilib.ui.scene.node.SceneNode[] node = {null};
        try (NativeEnvironment environment = new NativeEnvironment();
                club.heiqi.uilib.ui.scene.text.font.FontTextService text =
                        new club.heiqi.uilib.ui.scene.text.font.FontTextService(club.heiqi.uilib.host.font.IsolatedFontEngine.shared())) {
            MinecraftSceneScreen screen = new MinecraftSceneScreen(domain, context -> {});
            field(GuiScreen.class, "mc").set(screen, minecraft);
            ScenePageSession session = ScenePageSession.open(domain, host, text, context -> {
                state[0] = new club.heiqi.uilib.ui.scene.edit.SceneEditState(domain, "A");
                node[0] = club.heiqi.uilib.ui.scene.control.SceneTextField.create(context, state[0],
                        context.theme(), () -> false, ignored -> {}).size(Size.fill(1), Size.fill(1));
                context.mount(node[0]);
            });
            Object inputs = installInputs(screen, session);
            Bridge bridge = environment.owner.get();
            Page next = null;
            try {
                session.frame();
                domain.execute(() -> require(session.runtime().focusRouter().request(session.runtime().captureMount(node[0])),
                        "正式字段未取得焦点"));
                club.heiqi.uilib.ui.scene.input.SceneCompositionSource source = new club.heiqi.uilib.ui.scene.input.SceneCompositionSource();
                session.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.compositionBegin(source, "候选"));
                session.frame();
                invoke(inputs, "syncNativeTextFocus");
                domain.execute(() -> require(state[0].snapshot().get().composing && bridge.text, "关屏前未建立真实组字与native联系"));
                domain.execute(screen::onGuiClosed);
                domain.execute(() -> require(!state[0].snapshot().get().composing, "关屏后旧编辑状态仍保留组字联系"));
                next = new Page(minecraft, environment, true);
                session.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.compositionCommit(source, "旧提交"));
                session.frame();
                invoke(inputs, "historyDrained");
                invoke(inputs, "syncNativeTextFocus");
                domain.execute(() -> require(state[0].snapshot().get().text.equals("A"), "关屏后的旧来源提交仍改写草稿"));
                require(!bridge.text && environment.owner.get() == next.bridge && next.commands == 0,
                        "旧 native/IME 联系影响后继页");
            } finally {
                screen.onGuiClosed();
                if (next != null) next.close();
            }
        }
    }

    /** 使用真实 initGui/catch/onGuiClosed；共享服务只提供无设备资源，不替换生产清理算法。 */
    private void initializationFailure() throws Exception {
        club.heiqi.uilib.host.client.gl.GlApi gl = (club.heiqi.uilib.host.client.gl.GlApi)
                java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[] {club.heiqi.uilib.host.client.gl.GlApi.class},
                        (proxy, method, args) -> { throw new AssertionError("失败初始化不应调用 GL：" + method); });
        try (club.heiqi.uilib.host.client.image.ClientImageServices images =
                        new club.heiqi.uilib.host.client.image.ClientImageServices(gl,
                                source -> () -> { throw new AssertionError("失败初始化不加载图像"); },
                                club.heiqi.uilib.ui.scene.image.SharedImageService.Budget.defaults());
                club.heiqi.uilib.host.client.font.ClientFontServices fonts =
                        new club.heiqi.uilib.host.client.font.ClientFontServices(getClass().getClassLoader(), 1,
                                club.heiqi.uilib.host.client.font.ClientFontServices.Settings.defaults(),
                                club.heiqi.uilib.host.font.IsolatedFontEngine.shared(), gl,
                                club.heiqi.uilib.host.client.gl.GlProgramProvider.NONE);
                NativeEnvironment environment = new NativeEnvironment()) {
            Field imageOwner = field(club.heiqi.uilib.host.client.image.MinecraftImageServices.class, "shared");
            Field imageThread = field(club.heiqi.uilib.host.client.image.MinecraftImageServices.class, "thread");
            Field fontOwner = field(club.heiqi.uilib.host.client.font.MinecraftFontServices.class, "shared");
            Field fontThread = field(club.heiqi.uilib.host.client.font.MinecraftFontServices.class, "thread");
            Field themes = field(club.heiqi.uilib.host.client.settings.MinecraftApplicationConfiguration.class, "themes");
            Object[] previous = {imageOwner.get(null), imageThread.get(null), fontOwner.get(null), fontThread.get(null), themes.get(null)};
            ReactiveDomain domain = ReactiveDomain.direct();
            club.heiqi.uilib.ui.reactive.Owner[] themeOwner = {null};
            domain.execute(() -> {
                themeOwner[0] = domain.owner();
                club.heiqi.uilib.ui.config.ConfigSession config = new club.heiqi.uilib.ui.config.ConfigSession(domain,
                        club.heiqi.uilib.ui.config.ConfigSchema.of(club.heiqi.uilib.ui.scene.control.ThemeConfiguration.KEY));
                try {
                    themes.set(null, new club.heiqi.uilib.ui.scene.control.ThemeConfiguration(domain, config, themeOwner[0]));
                } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
            });
            int[] released = {0};
            MinecraftSceneScreen screen = new MinecraftSceneScreen(domain, context -> context.own(() -> released[0]++));
            imageOwner.set(null, images); imageThread.set(null, Thread.currentThread());
            fontOwner.set(null, fonts); fontThread.set(null, Thread.currentThread());
            try {
                RuntimeException original = new IllegalStateException("native availability 初始化失败");
                environment.initializationFailure = original;
                try {
                    screen.initGui();
                    throw new AssertionError("输入初始化失败未传播");
                } catch (RuntimeException actual) {
                    require(actual == original, "输入初始化失败原始 identity 丢失");
                    require(actual.getSuppressed().length == 0, "正常清理意外失败");
                }
                require(environment.owner.get() == null && environment.opened == 1 && environment.closed == 1,
                        "半初始化页遗留 native owner");
                require(released[0] == 1 && field(MinecraftSceneScreen.class, "session").get(screen) == null
                        && field(MinecraftSceneScreen.class, "inputs").get(screen) == null,
                        "真正 initGui 失败未结束 scope 或残留半初始化入口");
                require(!Keyboard.areRepeatEventsEnabled(), "失败的输入初始化遗留 repeat 借用");
                screen.onGuiClosed();
                require(released[0] == 1 && environment.closed == 1, "失败后重复关屏重复释放");
            } finally {
                screen.onGuiClosed();
                domain.execute(themeOwner[0]::close);
                imageOwner.set(null, previous[0]); imageThread.set(null, previous[1]);
                fontOwner.set(null, previous[2]); fontThread.set(null, previous[3]); themes.set(null, previous[4]);
            }
        }
    }

    public static void main(String[] args) throws Exception { new MinecraftScreenInputLifecycleScenario().run(args); }
}
