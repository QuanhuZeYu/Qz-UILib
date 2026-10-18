package club.heiqi.uilib.ui.scene.integration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collections;
import org.lwjgl.input.Keyboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import club.heiqi.uilib.testing.DisplayTestEnvironment;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.host.minecraft.MinecraftSceneScreen;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneShortcut;
import club.heiqi.uilib.ui.scene.control.SceneCommand;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;

/** 实际 MC screen.drawScreen 与正式 Session；仅宿主环境注入，不启动世界或 FML。 */
public final class MinecraftScreenSwitchScenario extends TestApplication {
    private boolean nativeOnly;
    private static final class Host implements SceneHost {
        int frames;
        public int width() { return 80; }
        public int height() { return 60; }
        public long metricsRevision() { return 1; }
        public void replay(PaintPlan plan) {
            if (plan.commands().stream().noneMatch(p -> p.kind == PaintCommand.Kind.RECT))
                throw new AssertionError("页面背景没有进入回放");
            frames++;
        }
    }

    private static void field(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private AutoCloseable installSession(MinecraftSceneScreen screen, ScenePageSession session) throws Exception {
        field(MinecraftSceneScreen.class, screen, "session", session);
        // 本场景绕过完整MC字体/原生文本安装，只注入宿主环境；同次会话借用仍用生产对象。
        Class<?> type = Class.forName(MinecraftSceneScreen.class.getName() + "$Inputs");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(MinecraftSceneScreen.class, ScenePageSession.class);
        constructor.setAccessible(true);
        Object inputs = constructor.newInstance(screen, session);
        field(MinecraftSceneScreen.class, screen, "inputs", inputs);
        Method initialize = type.getDeclaredMethod("initialize");
        initialize.setAccessible(true);
        initialize.invoke(inputs);
        return (AutoCloseable) inputs;
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        return (sun.misc.Unsafe) access.get(null);
    }

    /** 只固定窗口可用性；真实Display、LWJGL事件消费者和生产Inputs保持原路径。 */
    private static final class ActiveWindow implements AutoCloseable {
        final sun.misc.Unsafe access = unsafe();
        final Field field = org.lwjgl.opengl.Display.class.getDeclaredField("display_impl");
        final Object base, original;
        final long offset;
        boolean available = true;
        ActiveWindow(boolean nativeOnly) throws Exception {
            field.setAccessible(true);
            original = field.get(null);
            base = access.staticFieldBase(field);
            offset = access.staticFieldOffset(field);
            Object active = java.lang.reflect.Proxy.newProxyInstance(original.getClass().getClassLoader(),
                    new Class<?>[] {field.getType()}, (proxy, method, arguments) -> {
                        if (method.getName().equals("isActive")) return available;
                        if (nativeOnly) throw new AssertionError("无窗口输入夹具访问显示操作：" + method.getName());
                        method.setAccessible(true);
                        return method.invoke(original, arguments);
                    });
            access.putObjectVolatile(base, offset, active);
        }
        public void close() { access.putObjectVolatile(base, offset, original); }
    }

    /** 隔离JVM只借用LWJGL历史缓冲和可用性事实，不创建Display/设备或调用系统输入。 */
    private static final class NativeHistoryEnvironment implements AutoCloseable {
        final Field window = nativeField(org.lwjgl.opengl.Display.class, "window_created");
        final Field keyboard = nativeField(Keyboard.class, "created");
        final Field buffer = nativeField(Keyboard.class, "readBuffer");
        final Object oldBuffer = buffer.get(null);
        final ActiveWindow active;
        NativeHistoryEnvironment() throws Exception {
            require(!org.lwjgl.opengl.Display.isCreated() && !Keyboard.isCreated() && !org.lwjgl.input.Mouse.isCreated(),
                    "无窗口输入场景必须在未创建原生设备的隔离JVM执行");
            active = new ActiveWindow(true);
            window.setBoolean(null, true);
            keyboard.setBoolean(null, true);
            buffer.set(null, ByteBuffer.allocate(0));
        }
        private static Field nativeField(Class<?> type, String name) throws Exception {
            Field result = type.getDeclaredField(name); result.setAccessible(true); return result;
        }
        public void close() throws Exception {
            buffer.set(null, oldBuffer); keyboard.setBoolean(null, false); window.setBoolean(null, false); active.close();
        }
    }

    /** 沿已有LWJGL2原生载荷回归的方式借用历史；字节由真正WindowsKeyboard生成。 */
    private static final class NativeCharacters implements AutoCloseable {
        final Field buffer = keyboardField("readBuffer"), current = keyboardField("current_event"),
                scratch = keyboardField("tmp_event");
        final Object oldBuffer, oldCurrent, oldScratch;
        final ByteBuffer physical = (ByteBuffer) keyboardField("keyDownBuffer").get(null);
        final byte[] oldPhysical = new byte[physical.capacity()];
        NativeCharacters() throws Exception {
            if (!Keyboard.isCreated()) throw new AssertionError("场景需要实际LWJGL2 Keyboard");
            oldBuffer = buffer.get(null); oldCurrent = current.get(null); oldScratch = scratch.get(null);
            Constructor<?> event = current.getType().getDeclaredConstructor();
            event.setAccessible(true);
            current.set(null, event.newInstance()); scratch.set(null, event.newInstance());
            buffer.set(null, ByteBuffer.allocate(0));
            for (int key = 0; key < oldPhysical.length; key++) oldPhysical[key] = physical.get(key);
            physical();
        }
        static Field keyboardField(String name) throws Exception {
            Field result = Keyboard.class.getDeclaredField(name);
            result.setAccessible(true);
            return result;
        }
        void load(char... text) throws Exception {
            Class<?> type = Class.forName("org.lwjgl.opengl.WindowsKeyboard");
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object producer = constructor.newInstance();
            Method character = type.getDeclaredMethod("handleChar", int.class, long.class, boolean.class);
            Method read = type.getDeclaredMethod("read", ByteBuffer.class);
            character.setAccessible(true); read.setAccessible(true);
            for (int i = 0; i < text.length; i++) character.invoke(producer, (int) text[i], (long) i + 1, false);
            ByteBuffer payload = ByteBuffer.allocate(Keyboard.EVENT_SIZE * text.length);
            read.invoke(producer, payload); payload.flip();
            buffer.set(null, payload);
        }
        void physical(int... held) {
            for (int key = 0; key < physical.capacity(); key++) physical.put(key, (byte) 0);
            for (int key : held) physical.put(key, (byte) 1);
        }
        void keys(int[]... events) throws Exception {
            Class<?> type = Class.forName("org.lwjgl.opengl.WindowsKeyboard");
            Constructor<?> constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
            Object producer = constructor.newInstance();
            Method put = type.getDeclaredMethod("putEvent", int.class, byte.class, int.class, long.class, boolean.class);
            Method read = type.getDeclaredMethod("read", ByteBuffer.class);
            put.setAccessible(true); read.setAccessible(true);
            for (int i = 0; i < events.length; i++) {
                int[] event = events[i];
                put.invoke(producer, event[0], (byte) event[1], event[2], (long) i + 1, event[3] != 0);
            }
            ByteBuffer payload = ByteBuffer.allocate(Keyboard.EVENT_SIZE * events.length);
            read.invoke(producer, payload); payload.flip(); buffer.set(null, payload);
        }
        public void close() throws Exception {
            buffer.set(null, oldBuffer); current.set(null, oldCurrent); scratch.set(null, oldScratch);
            for (int key = 0; key < oldPhysical.length; key++) physical.put(key, oldPhysical[key]);
        }
    }

    private static final class InputScreen extends MinecraftSceneScreen {
        int nativeEvents;
        Runnable afterKey;
        InputScreen() { super(context -> {}); }
        @Override public void handleKeyboardInput() {
            nativeEvents++;
            super.handleKeyboardInput();
            Runnable action = afterKey;
            afterKey = null;
            if (action != null) action.run();
        }
    }

    private static final SceneKey[] NAMED_KEYS = {
            SceneKey.F1, SceneKey.F2, SceneKey.F3, SceneKey.F4, SceneKey.F5, SceneKey.F6, SceneKey.F7, SceneKey.F8, SceneKey.F9, SceneKey.F10, SceneKey.F11, SceneKey.F12, SceneKey.DIGIT_0, SceneKey.DIGIT_1, SceneKey.DIGIT_2, SceneKey.DIGIT_3, SceneKey.DIGIT_4, SceneKey.DIGIT_5, SceneKey.DIGIT_6, SceneKey.DIGIT_7, SceneKey.DIGIT_8, SceneKey.DIGIT_9, SceneKey.SLASH, SceneKey.COMMA, SceneKey.PERIOD, SceneKey.MINUS, SceneKey.EQUAL, SceneKey.SEMICOLON, SceneKey.APOSTROPHE, SceneKey.LEFT_BRACKET, SceneKey.RIGHT_BRACKET, SceneKey.BACKSLASH, SceneKey.GRAVE, SceneKey.INSERT
    };
    private static final int[] NATIVE_KEYS = {
            Keyboard.KEY_F1, Keyboard.KEY_F2, Keyboard.KEY_F3, Keyboard.KEY_F4, Keyboard.KEY_F5, Keyboard.KEY_F6, Keyboard.KEY_F7, Keyboard.KEY_F8, Keyboard.KEY_F9, Keyboard.KEY_F10, Keyboard.KEY_F11, Keyboard.KEY_F12, Keyboard.KEY_0, Keyboard.KEY_1, Keyboard.KEY_2, Keyboard.KEY_3, Keyboard.KEY_4, Keyboard.KEY_5, Keyboard.KEY_6, Keyboard.KEY_7, Keyboard.KEY_8, Keyboard.KEY_9, Keyboard.KEY_SLASH, Keyboard.KEY_COMMA, Keyboard.KEY_PERIOD, Keyboard.KEY_MINUS, Keyboard.KEY_EQUALS, Keyboard.KEY_SEMICOLON, Keyboard.KEY_APOSTROPHE, Keyboard.KEY_LBRACKET, Keyboard.KEY_RBRACKET, Keyboard.KEY_BACKSLASH, Keyboard.KEY_GRAVE, Keyboard.KEY_INSERT
    };

    private final class NativeField implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final Host host = new Host();
        final InputScreen screen = new InputScreen();
        final club.heiqi.uilib.ui.scene.text.font.FontTextService text =
                new club.heiqi.uilib.ui.scene.text.font.FontTextService(club.heiqi.uilib.host.font.IsolatedFontEngine.shared());
        final club.heiqi.uilib.ui.scene.edit.SceneEditState[] state = {null};
        final SceneNode[] node = {null}, search = {null}, action = {null};
        final club.heiqi.uilib.ui.scene.edit.SceneEditState[] searchState = {null};
        int searches, saves, activations;
        final int[] namedHits = new int[NAMED_KEYS.length];
        final ScenePageSession page;
        NativeField(Minecraft mc) throws Exception {
            field(GuiScreen.class, screen, "mc", mc);
            page = ScenePageSession.open(domain, host, text, context -> {
                state[0] = new club.heiqi.uilib.ui.scene.edit.SceneEditState(domain, "");
                node[0] = club.heiqi.uilib.ui.scene.control.SceneTextField.create(context, state[0],
                        domain.signal(club.heiqi.uilib.ui.scene.control.SceneTheme.light()), domain.signal(false), ignored -> {});
                searchState[0] = new club.heiqi.uilib.ui.scene.edit.SceneEditState(domain, "");
                search[0] = club.heiqi.uilib.ui.scene.control.SceneTextField.create(context, searchState[0],
                        context.theme(), () -> false, ignored -> {});
                action[0] = club.heiqi.uilib.ui.scene.control.SceneButton.create(context, () -> "动作", context.theme(),
                        () -> activations++);
                java.util.List<SceneShortcut> shortcuts = new java.util.ArrayList<>();
                shortcuts.add(SceneShortcut.primary(SceneKey.F, SceneCommand.of(() -> true, () -> {
                    searches++;
                    pageFocus(context.runtime(), search[0]);
                })));
                shortcuts.add(SceneShortcut.primary(SceneKey.S, SceneCommand.of(() -> true, () -> saves++)));
                for (int index = 0; index < NAMED_KEYS.length; index++) {
                    final int key = index;
                    shortcuts.add(SceneShortcut.of(NAMED_KEYS[index], SceneCommand.of(() -> true,
                            () -> namedHits[key]++), SceneShortcut.Modifier.ALT));
                }
                context.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).background(0xff112233)
                        .initialFocus(node[0]).add(node[0]).add(search[0]).add(action[0])
                        .bind(NodeBinding.shortcuts(SceneShortcut.Scope.PAGE, shortcuts.toArray(new SceneShortcut[0]))));
            });
            installSession(screen, page);
        }
        void activate(Minecraft mc) {
            mc.currentScreen = screen;
            screen.drawScreen(0, 0, 0);
            focus();
        }
        void focus() { domain.execute(() -> require(page.runtime().focusRouter().request(
                page.runtime().captureMount(node[0])), "真实文本控件未取得焦点")); }
        String draft() {
            String[] value = {null};
            domain.execute(() -> value[0] = state[0].snapshot().get().text);
            return value[0];
        }
        String searchDraft() {
            String[] value = {null}; domain.execute(() -> value[0] = searchState[0].snapshot().get().text); return value[0];
        }
        public void close() { try { screen.onGuiClosed(); } finally { text.close(); } }
    }

    private static int[] down(int key) { return new int[] {key, 1, 0, 0}; }
    private static int[] up(int key) { return new int[] {key, 0, 0, 0}; }
    private static int[] repeat(int key) { return new int[] {key, 1, 0, 1}; }
    private static void pageFocus(club.heiqi.uilib.ui.scene.runtime.SceneRuntime runtime, SceneNode node) {
        require(runtime.focusRouter().request(runtime.captureMount(node)), "合法当前目标未取得焦点");
    }

    private void nativeCommands() throws Exception {
        Minecraft mc = (Minecraft) unsafe().allocateInstance(Minecraft.class);
        boolean repeats = Keyboard.areRepeatEventsEnabled();
        Keyboard.enableRepeatEvents(true);
        try (ActiveWindow active = new ActiveWindow(nativeOnly); NativeCharacters history = new NativeCharacters();
                NativeField input = new NativeField(mc)) {
            mc.currentScreen = input.screen;
            history.physical(Keyboard.KEY_LCONTROL, Keyboard.KEY_F);
            history.keys(down(Keyboard.KEY_LCONTROL), down(Keyboard.KEY_F), repeat(Keyboard.KEY_F));
            input.screen.drawScreen(0, 0, 0);
            require(input.searches == 0, "开屏旧历史激活搜索命令");
            history.physical(); history.keys(up(Keyboard.KEY_F), up(Keyboard.KEY_LCONTROL));
            input.screen.drawScreen(0, 0, 0); input.focus();

            history.keys(down(Keyboard.KEY_LCONTROL), down(Keyboard.KEY_F), up(Keyboard.KEY_F),
                    up(Keyboard.KEY_LCONTROL), new int[] {Keyboard.KEY_NONE, 0, '中', 0});
            input.screen.drawScreen(0, 0, 0);
            require(input.searches == 1 && input.draft().isEmpty() && input.searchDraft().equals("中"),
                    "搜索命令未先于同批文字切换正式编辑目标");
            history.keys(new int[] {Keyboard.KEY_SPACE, 1, ' ', 0}, new int[] {Keyboard.KEY_SPACE, 1, ' ', 1}, up(Keyboard.KEY_SPACE));
            input.screen.drawScreen(0, 0, 0);
            history.load('\uD83D', '\uDE00', 'f', '/'); input.screen.drawScreen(0, 0, 0);
            history.keys(new int[] {Keyboard.KEY_1, 1, '\uD83D', 0},
                    new int[] {Keyboard.KEY_1, 1, '\uDE00', 0}, up(Keyboard.KEY_1));
            input.screen.drawScreen(0, 0, 0);
            require(input.searchDraft().equals("中  😀f/😀"), "LWJGL2空格起手/repeat、代理对或新增命名键文本丢失/重复");

            history.physical(Keyboard.KEY_LCONTROL, Keyboard.KEY_S);
            history.keys(down(Keyboard.KEY_LCONTROL), down(Keyboard.KEY_S)); input.screen.drawScreen(0, 0, 0);
            history.keys(repeat(Keyboard.KEY_S)); input.screen.drawScreen(0, 0, 0);
            require(input.saves == 1, "应用命令repeat重复执行");
            active.available = false; input.screen.drawScreen(0, 0, 0);
            active.available = true;
            history.keys(repeat(Keyboard.KEY_S)); input.screen.drawScreen(0, 0, 0);
            require(input.saves == 1, "失焦恢复的旧按压激活应用命令");
            history.physical(); history.keys(up(Keyboard.KEY_S), up(Keyboard.KEY_LCONTROL)); input.screen.drawScreen(0, 0, 0);
            history.keys(down(Keyboard.KEY_LCONTROL), down(Keyboard.KEY_S), up(Keyboard.KEY_S), up(Keyboard.KEY_LCONTROL));
            input.screen.drawScreen(0, 0, 0);
            require(input.saves == 2, "真实释放后的fresh DOWN未执行命令");
            history.keys(down(Keyboard.KEY_LCONTROL), down(Keyboard.KEY_LMENU), down(Keyboard.KEY_S), up(Keyboard.KEY_S),
                    up(Keyboard.KEY_LMENU), down(Keyboard.KEY_LMETA), down(Keyboard.KEY_S), up(Keyboard.KEY_S),
                    up(Keyboard.KEY_LMETA), up(Keyboard.KEY_LCONTROL)); input.screen.drawScreen(0, 0, 0);
            require(input.saves == 2, "Alt或额外Meta被错误匹配为精确Primary组合");

            input.focus(); input.page.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.composition("候", true)); input.page.frame();
            history.keys(down(Keyboard.KEY_LCONTROL), down(Keyboard.KEY_F), up(Keyboard.KEY_F), up(Keyboard.KEY_LCONTROL));
            input.screen.drawScreen(0, 0, 0);
            input.domain.execute(() -> require(input.searches == 1 && input.page.runtime().focusRouter().focused().get() == input.node[0],
                    "组字中的应用键抢走当前编辑目标"));
            input.page.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.composition("", false)); input.page.frame();
            input.domain.execute(() -> pageFocus(input.page.runtime(), input.action[0]));
            history.keys(down(Keyboard.KEY_RETURN), down(Keyboard.KEY_NUMPADENTER), repeat(Keyboard.KEY_RETURN),
                    up(Keyboard.KEY_RETURN), up(Keyboard.KEY_NUMPADENTER)); input.screen.drawScreen(0, 0, 0);
            require(input.activations == 2, "主Enter与小键盘Enter混用物理段或repeat重复激活");
            System.out.println("MC_NATIVE_COMMAND sameBatchFocus=true oldHistoryBlocked=true focusRecoveryBlocked=true"
                    + " saves=" + input.saves + " searches=" + input.searches + " enterActivations=" + input.activations
                    + " spaceAndSurrogates=true modifiersExact=true imeRetained=true");
        } finally { Keyboard.enableRepeatEvents(repeats); }
    }

    private void nativeKeyPhases() throws Exception {
        Minecraft mc = (Minecraft) unsafe().allocateInstance(Minecraft.class);
        boolean repeats = Keyboard.areRepeatEventsEnabled();
        Keyboard.enableRepeatEvents(true);
        try (ActiveWindow active = new ActiveWindow(nativeOnly); NativeCharacters history = new NativeCharacters();
                NativeField input = new NativeField(mc)) {
            mc.currentScreen = input.screen;
            history.physical(Keyboard.KEY_SPACE);
            history.keys(down(Keyboard.KEY_SPACE), repeat(Keyboard.KEY_SPACE));
            input.screen.drawScreen(0, 0, 0);
            input.domain.execute(() -> pageFocus(input.page.runtime(), input.action[0]));
            history.physical(); history.keys(up(Keyboard.KEY_SPACE)); input.screen.drawScreen(0, 0, 0);
            require(input.activations == 0, "开屏旧Space的UP误激活当前按钮");

            history.physical(Keyboard.KEY_SPACE); history.keys(down(Keyboard.KEY_SPACE));
            input.screen.drawScreen(0, 0, 0);
            require(input.activations == 0, "Space在DOWN提前激活按钮");
            history.keys(repeat(Keyboard.KEY_SPACE)); input.screen.drawScreen(0, 0, 0);
            require(input.activations == 0, "Space repeat提前激活按钮");
            history.physical(); history.keys(up(Keyboard.KEY_SPACE)); input.screen.drawScreen(0, 0, 0);
            require(input.activations == 1, "Space合法UP没有单次激活原按钮");
            history.keys(up(Keyboard.KEY_SPACE)); input.screen.drawScreen(0, 0, 0);
            require(input.activations == 1, "重复Space UP再次激活按钮");

            history.physical(Keyboard.KEY_SPACE); history.keys(down(Keyboard.KEY_SPACE));
            input.screen.drawScreen(0, 0, 0);
            input.focus();
            input.domain.execute(() -> pageFocus(input.page.runtime(), input.action[0]));
            history.physical(); history.keys(up(Keyboard.KEY_SPACE)); input.screen.drawScreen(0, 0, 0);
            require(input.activations == 1, "Space按压离开焦点后返回仍在UP激活");

            history.physical(Keyboard.KEY_SPACE); history.keys(down(Keyboard.KEY_SPACE));
            input.screen.drawScreen(0, 0, 0);
            active.available = false; input.screen.drawScreen(0, 0, 0);
            active.available = true; input.screen.drawScreen(0, 0, 0);
            input.domain.execute(() -> pageFocus(input.page.runtime(), input.action[0]));
            history.physical(); history.keys(up(Keyboard.KEY_SPACE)); input.screen.drawScreen(0, 0, 0);
            require(input.activations == 1, "失焦恢复的Space旧UP激活按钮");
            history.keys(down(Keyboard.KEY_SPACE), repeat(Keyboard.KEY_SPACE), up(Keyboard.KEY_SPACE));
            input.screen.drawScreen(0, 0, 0);
            require(input.activations == 2, "取消后新的完整Space按压未正常激活");

            history.physical(Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER);
            history.keys(down(Keyboard.KEY_RETURN), down(Keyboard.KEY_NUMPADENTER), repeat(Keyboard.KEY_RETURN));
            input.screen.drawScreen(0, 0, 0);
            require(input.activations == 4, "两Enter未在各自DOWN单次激活");
            history.physical(); history.keys(up(Keyboard.KEY_RETURN), up(Keyboard.KEY_NUMPADENTER));
            input.screen.drawScreen(0, 0, 0);
            require(input.activations == 4, "Enter的UP重复执行DOWN动作");
            System.out.println("MC_NATIVE_KEY_PHASE spaceDownDeferred=true spaceUpOnce=true"
                    + " oldUpBlocked=true repeatBlocked=true focusRoundTripCancelled=true focusLossCancelled=true"
                    + " enterDownIndependent=true activations=" + input.activations);
        } finally { Keyboard.enableRepeatEvents(repeats); }
    }

    private void nativeNamedKeys() throws Exception {
        Minecraft mc = (Minecraft) unsafe().allocateInstance(Minecraft.class);
        boolean repeats = Keyboard.areRepeatEventsEnabled(); Keyboard.enableRepeatEvents(true);
        try (ActiveWindow active = new ActiveWindow(nativeOnly); NativeCharacters history = new NativeCharacters();
                NativeField input = new NativeField(mc)) {
            input.activate(mc);
            input.domain.execute(() -> pageFocus(input.page.runtime(), input.action[0]));
            require(NAMED_KEYS.length == NATIVE_KEYS.length, "命名键与原生事件表未一一对应");
            for (int index = 0; index < NATIVE_KEYS.length; index++) {
                int key = NATIVE_KEYS[index];
                history.keys(down(Keyboard.KEY_LMENU), down(key), repeat(key), up(key), down(key), up(key), up(Keyboard.KEY_LMENU));
                input.screen.drawScreen(0, 0, 0);
                require(input.namedHits[index] == 2, "命名键映射、repeat或同批释放重按错误：" + NAMED_KEYS[index]);
            }
            require(input.draft().isEmpty() && input.searchDraft().isEmpty(), "命名键错误合成了文本");
            System.out.println("MC_NATIVE_NAMED_KEYS keys=" + NAMED_KEYS.length + " activations="
                    + java.util.Arrays.stream(input.namedHits).sum() + " repeatBlocked=true sameBatchRelease=true noSynthesizedText=true");
        } finally { Keyboard.enableRepeatEvents(repeats); }
    }

    private void nativeRenderCadence() throws Exception {
        Minecraft mc = (Minecraft) unsafe().allocateInstance(Minecraft.class);
        try (ActiveWindow active = new ActiveWindow(nativeOnly); NativeCharacters history = new NativeCharacters();
                NativeField input = new NativeField(mc)) {
            input.activate(mc);
            input.screen.handleInput(); // 第一个逻辑tick；随后中间render没有tick采集。
            int renderHz = 120, tickHz = 20, renderPerTick = renderHz / tickHz;
            for (int frame = 1; frame < renderPerTick; frame++) {
                history.load('中');
                input.screen.drawScreen(0, 0, 0);
                require(input.draft().equals(String.join("", Collections.nCopies(frame, "中"))),
                        "中间render仍在等待下一20Hz tick采集，frame=" + frame);
            }
            String beforeTick = input.draft();
            input.screen.handleInput(); // 下一逻辑tick不能重放render已耗尽的历史。
            input.screen.drawScreen(0, 0, 0);
            require(input.draft().equals(beforeTick) && input.screen.nativeEvents == renderPerTick - 1,
                    "tick或空render重复递交已消费原生字符");
            System.out.println("MC_NATIVE_RENDER_INPUT simulatedRenderHz=" + renderHz + " simulatedTickHz=" + tickHz
                    + " nativeEvents=" + input.screen.nativeEvents + " nextTickReplay=false");
        }
    }

    private void closeWhileCollecting(boolean switchPage) throws Exception {
        Minecraft mc = (Minecraft) unsafe().allocateInstance(Minecraft.class);
        try (ActiveWindow active = new ActiveWindow(nativeOnly); NativeCharacters history = new NativeCharacters();
                NativeField old = new NativeField(mc); NativeField next = new NativeField(mc)) {
            old.activate(mc);
            int oldFrames = old.host.frames;
            old.screen.afterKey = () -> {
                old.screen.onGuiClosed();
                mc.currentScreen = switchPage ? next.screen : null;
            };
            history.load('旧', '尾');
            old.screen.drawScreen(0, 0, 0);
            require(old.page.ended() && old.host.frames == oldFrames && old.draft().isEmpty(),
                    "采集中关闭后仍访问旧会话或结算旧输入");
            require(old.screen.nativeEvents == 2 && next.screen.nativeEvents == 0 && next.draft().isEmpty(),
                    "旧原生历史未耗尽或被转投新页面");
            if (switchPage) {
                require(next.host.frames == 1, "采集中切页当帧没有呈现新页");
                next.focus();
                history.load('新');
                next.screen.drawScreen(0, 0, 0);
                require(next.draft().equals("新"), "切页后新的原生输入不能继续进入正式控件");
            } else {
                old.screen.drawScreen(0, 0, 0);
                require(mc.currentScreen == null && old.host.frames == oldFrames, "关闭后的迟到draw仍访问旧会话");
            }
        }
    }

    @Override
    protected void runTests() throws Exception {
        nativeOnly = arguments().length == 1 && arguments()[0].equals("native-only");
        if (nativeOnly) own(new NativeHistoryEnvironment());
        else own(new DisplayTestEnvironment(80, 60, "UILib MC screen same-frame switch"));
        scenario("原生命令切焦、文本顺序与物理尾部隔离", this::nativeCommands);
        scenario("原生Space按下释放与焦点取消、双Enter起手", this::nativeKeyPhases);
        scenario("常用命名键经真实历史与同批释放重按", this::nativeNamedKeys);
        scenario("中间render采集原生字符且后续tick不重放", this::nativeRenderCadence);
        scenario("原生采集中关闭不访问旧会话", () -> closeWhileCollecting(false));
        scenario("原生采集中切页隔离历史尾部", () -> closeWhileCollecting(true));
        if (nativeOnly) return;
        scenario("点击切页当帧提交新页且旧输入不转投", () -> {
            // MC构造会启动完整游戏基础设施；环境只供currentScreen身份，生产screen正常构造。
            Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            access.setAccessible(true);
            Minecraft mc = (Minecraft) ((sun.misc.Unsafe) access.get(null)).allocateInstance(Minecraft.class);
            MinecraftSceneScreen first = new MinecraftSceneScreen(context -> {});
            MinecraftSceneScreen second = new MinecraftSceneScreen(context -> {});
            field(GuiScreen.class, first, "mc", mc);
            field(GuiScreen.class, second, "mc", mc);
            Host oldHost = new Host(), newHost = new Host();
            int[] clicks = {0, 0};
            SceneTextMeasurer text = (value, style, width) -> new TextLayout(0, 0, style, Collections.emptyList());
            ScenePageSession next = own(ScenePageSession.open(ReactiveDomain.direct(), newHost, text, context ->
                    context.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).background(0xff223344)
                            .onClick(() -> clicks[1]++))));
            ScenePageSession[] previous = new ScenePageSession[1];
            previous[0] = own(ScenePageSession.open(ReactiveDomain.direct(), oldHost, text, context ->
                    context.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).background(0xff112233)
                            .onClick(() -> {
                                clicks[0]++;
                                first.onGuiClosed();
                                mc.currentScreen = second;
                            }))));
            own(installSession(first, previous[0]));
            own(installSession(second, next));
            mc.currentScreen = first;
            first.drawScreen(10, 10, 0);
            require(oldHost.frames == 1, "初始旧页未呈现");
            previous[0].pointer(ScenePointerEvent.down(0, 10, 10));
            previous[0].pointer(ScenePointerEvent.up(0, 10, 10));
            previous[0].pointer(ScenePointerEvent.down(0, 10, 10));
            previous[0].pointer(ScenePointerEvent.up(0, 10, 10));
            first.drawScreen(10, 10, 0);
            require(clicks[0] == 1 && clicks[1] == 0, "旧批次重放或转投新页");
            require(oldHost.frames == 1 && newHost.frames == 1, "换页当帧未提交新页背景");
            second.drawScreen(10, 10, 0);
            require(newHost.frames == 2, "下一正常帧不能继续呈现新页");
            System.out.println("ACTUAL_MC_DRAW oldReplay=1 newReplay=2 oldClick=1 newClick=0");
        });
        scenario("MC失焦入口按采集序列清焦点和组字", () -> {
            ReactiveDomain domain = ReactiveDomain.direct();
            club.heiqi.uilib.ui.scene.edit.SceneEditState[] declared = {null};
            domain.execute(() -> declared[0] = new club.heiqi.uilib.ui.scene.edit.SceneEditState(domain, "A"));
            club.heiqi.uilib.ui.scene.edit.SceneEditState state = declared[0];
            SceneNode[] node = {null};
            SceneTextMeasurer text = own(new club.heiqi.uilib.ui.scene.text.font.FontTextService(
                    club.heiqi.uilib.host.font.IsolatedFontEngine.shared()));
            ScenePageSession session = own(ScenePageSession.open(domain, new Host(), text, context -> {
                node[0] = club.heiqi.uilib.ui.scene.control.SceneTextField.create(context, state,
                        domain.signal(club.heiqi.uilib.ui.scene.control.SceneTheme.light()),
                        domain.signal(false), ignored -> {});
                context.mount(node[0]);
            }));
            MinecraftSceneScreen screen = new MinecraftSceneScreen(context -> {});
            AutoCloseable inputs = own(installSession(screen, session));
            session.frame();
            domain.execute(() -> session.runtime().focusRouter().request(session.runtime().captureMount(node[0])));
            session.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.composition("候选", true));
            session.frame();
            domain.execute(() -> require(session.runtime().focusRouter().focused().get() == node[0]
                    && state.snapshot().get().composing, "失焦前未建立真实焦点和组字"));
            field(inputs.getClass(), inputs, "pointerWindowActive", true);
            // 只注入Display的原生可用性事实；实际Inputs/有序事件与核心焦点协议照常运行。
            Field displayField = org.lwjgl.opengl.Display.class.getDeclaredField("display_impl");
            displayField.setAccessible(true);
            Object original = displayField.get(null);
            Object inactive = java.lang.reflect.Proxy.newProxyInstance(original.getClass().getClassLoader(),
                    new Class<?>[] {displayField.getType()}, (proxy, method, arguments) -> {
                        if (method.getName().equals("isActive")) return false;
                        method.setAccessible(true);
                        return method.invoke(original, arguments);
                    });
            Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            access.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) access.get(null);
            Object base = unsafe.staticFieldBase(displayField);
            long offset = unsafe.staticFieldOffset(displayField);
            java.lang.reflect.Method sync = inputs.getClass().getDeclaredMethod("syncInputAvailability");
            sync.setAccessible(true);
            try {
                unsafe.putObjectVolatile(base, offset, inactive);
                sync.invoke(inputs);
            } finally {
                unsafe.putObjectVolatile(base, offset, original);
            }
            domain.execute(() -> require(session.runtime().focusRouter().focused().get() == node[0],
                    "采集失焦事实过早改焦点"));
            session.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.text("迟到"));
            session.frame();
            domain.execute(() -> {
                require(session.runtime().focusRouter().focused().get() == null && !state.snapshot().get().composing,
                        "MC失焦未经正式入口清焦点/组字");
                require(state.snapshot().get().text.equals("A"), "失焦后的同批文字落到旧字段");
            });
        });
    }

    public static void main(String[] args) throws Exception {
        new MinecraftScreenSwitchScenario().run(args);
    }
}
