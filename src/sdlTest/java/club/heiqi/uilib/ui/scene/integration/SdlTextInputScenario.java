package club.heiqi.uilib.ui.scene.integration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.lwjgl.input.Keyboard;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLKeyboard.*;
import static org.lwjgl.sdl.SDLScancode.*;
import static org.lwjgl.sdl.SDLVideo.*;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.host.minecraft.SceneNativeTextInput;
import club.heiqi.uilib.ui.host.minecraft.SceneNativeTextInput.NativeEvent;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 用真实 SDL 事实缓冲执行生产消费 hook/桥和正式 Session；默认不创建窗口或设备。
 * window 参数只保留显式真实 SDL 文本生命周期入口，设备验收另行授权。 */
public final class SdlTextInputScenario extends TestApplication {
    public static int windowId = 71, begins, ends;
    public static SDL_Event currentEvent;
    private static long nativeWindow;
    public static float scale() { return 2; }
    public static void begin() {
        begins++;
        if (nativeWindow != 0) require(SDL_StartTextInput(nativeWindow), "原生start失败");
    }
    public static void end() {
        ends++;
        if (nativeWindow != 0) require(SDL_StopTextInput(nativeWindow), "原生stop失败");
    }
    public static void onMainThread(Runnable operation) { operation.run(); }
    public static void pump() {
        SDL_Event previous = currentEvent;
        try (SDL_Event.Buffer events = SDL_Event.calloc(1)) {
            int count;
            while ((count = SDL_PeepEvents(events, SDL_GETEVENT, SDL_EVENT_FIRST, SDL_EVENT_LAST)) > 0) {
                currentEvent = events.get(0);
                SceneNativeTextInput.captureCurrentEvent();
            }
            require(count == 0, "真实SDL队列读取失败");
        } finally { currentEvent = previous; }
    }

    @Override protected void runTests() throws Exception {
        scenario("缓存消费方法与槽精确匹配，缺失描述符可诊断", this::consumerSupport);
        scenario("同消费序保留键鼠文本焦点且复制原生载荷", this::orderedFacts);
        scenario("真实来源组字与普通提交保持原子撤销", this::formalScene);
        scenario("旧来源跨焦点、同批指针与重置基线不转投", this::sourceIsolation);
        scenario("停止无最终提交后恢复直接建立新组字", this::textBorrow);
        if (arguments().length == 1 && arguments()[0].equals("window"))
            scenario("真实窗口文本输入开始停止", this::windowLifecycle);
    }

    private void consumerSupport() throws Exception {
        require(SceneNativeTextInput.supported(), "缓存3.0.31消费能力未识别");
        Class<?> support = Class.forName("club.heiqi.uilib.ui.host.minecraft.SceneNativeInputSupport");
        Method inspect = support.getDeclaredMethod("consumerFailure", ClassLoader.class);
        inspect.setAccessible(true);
        String path = "org/lwjglx/opengl/Display.class";
        byte[] actual;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            require(input != null, "缺少实际消费目标class");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int size;
            while ((size = input.read(buffer)) >= 0) bytes.write(buffer, 0, size);
            actual = bytes.toByteArray();
        }
        byte[] name = "lwjgl3ify$handleSdlEvent".getBytes(StandardCharsets.UTF_8);
        int changed = 0;
        for (int index = 0; index <= actual.length - name.length; index++) {
            boolean match = true;
            for (int item = 0; item < name.length; item++) match &= actual[index + item] == name[item];
            if (match) { actual[index + name.length - 1] = 'X'; changed++; }
        }
        require(changed == 1, "消费目标方法常量不唯一");
        ClassLoader incompatible = new ClassLoader(getClass().getClassLoader()) {
            @Override public InputStream getResourceAsStream(String name) {
                return name.equals(path) ? new ByteArrayInputStream(actual) : super.getResourceAsStream(name);
            }
        };
        Object diagnostic = inspect.invoke(null, incompatible);
        require(diagnostic != null && diagnostic.toString().contains("lwjgl3ify$handleSdlEvent()Z"),
                "消费方法缺失没有精确诊断");
        System.out.println("SDL_CONSUMER_BINDING actualDescriptor=()Z publicStatic=true eventSlot=SDL_Event missingMethodDiagnosed=true");
    }

    /** 仅替换窗口事实及 begin/end 边界；Bindings 的实际 SDL 解析和宿主键映射均走生产实现。 */
    private final class NativeEnvironment implements AutoCloseable {
        final SceneNativeTextInput bridge = new SceneNativeTextInput();
        final Method hook;
        NativeEnvironment() throws Exception {
            require(SDL_InitSubSystem(SDL_INIT_EVENTS), "SDL事件子系统初始化失败");
            begins = 0; ends = 0;
            Object bindings = field(SceneNativeTextInput.class, "bindings").get(bridge);
            Class<?> type = bindings.getClass();
            field(type, "windowId").set(bindings, SdlTextInputScenario.class.getField("windowId"));
            field(type, "currentEvent").set(bindings, SdlTextInputScenario.class.getField("currentEvent"));
            field(type, "scale").set(bindings, SdlTextInputScenario.class.getMethod("scale"));
            field(type, "begin").set(bindings, SdlTextInputScenario.class.getMethod("begin"));
            field(type, "end").set(bindings, SdlTextInputScenario.class.getMethod("end"));
            field(type, "mainThread").set(bindings, SdlTextInputScenario.class.getMethod("onMainThread", Runnable.class));
            field(type, "pump").set(bindings, SdlTextInputScenario.class.getMethod("pump"));
            hook = SceneNativeTextInput.class.getMethod("captureCurrentEvent");
            hook.setAccessible(true);
            bridge.textInput(true);
        }
        void capture(Input input) throws Exception {
            currentEvent = input.event;
            hook.invoke(null);
        }
        void input(int type, String text) throws Exception {
            try (Input input = new Input(type, text)) { capture(input); }
        }
        public void close() {
            bridge.close(); currentEvent = null;
            require(begins == ends && !bridge.active(), "原生输入借用未配对结束");
            SDL_QuitSubSystem(SDL_INIT_EVENTS);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }

    private static final class Input implements AutoCloseable {
        final SDL_Event event = SDL_Event.calloc();
        final ByteBuffer bytes;
        Input(int type, String text) {
            bytes = text == null ? null : MemoryUtil.memUTF8(text);
            event.type(type);
            if (type == SDL_EVENT_TEXT_INPUT) event.text().type(type).windowID(windowId).text(bytes);
            else if (type == SDL_EVENT_TEXT_EDITING) event.edit().type(type).windowID(windowId).text(bytes);
            else if (type == SDL_EVENT_KEY_DOWN || type == SDL_EVENT_KEY_UP)
                event.key().type(type).windowID(windowId).scancode(SDL_SCANCODE_X).down(type == SDL_EVENT_KEY_DOWN);
            else if (type == SDL_EVENT_MOUSE_MOTION)
                event.motion().type(type).windowID(windowId).x(12.5f).y(20).timestamp(4);
            else if (type == SDL_EVENT_MOUSE_BUTTON_DOWN || type == SDL_EVENT_MOUSE_BUTTON_UP)
                event.button().type(type).windowID(windowId).button((byte) 1).x(12.5f).y(20).timestamp(4);
            else if (type == SDL_EVENT_WINDOW_FOCUS_LOST) event.window().type(type).windowID(windowId);
        }
        public void close() { event.free(); if (bytes != null) MemoryUtil.memFree(bytes); }
    }

    private void orderedFacts() throws Exception {
        try (NativeEnvironment nativeInput = new NativeEnvironment()) {
            nativeInput.input(SDL_EVENT_KEY_DOWN, null);
            try (Input text = new Input(SDL_EVENT_TEXT_INPUT, "中🙂")) {
                nativeInput.capture(text);
                MemoryUtil.memSet(MemoryUtil.memAddress(text.bytes), 0, text.bytes.remaining());
            }
            nativeInput.input(SDL_EVENT_MOUSE_MOTION, null);
            nativeInput.input(SDL_EVENT_MOUSE_BUTTON_DOWN, null);
            nativeInput.input(SDL_EVENT_WINDOW_FOCUS_LOST, null);
            nativeInput.input(SDL_EVENT_KEY_UP, null);
            List<NativeEvent> facts = nativeInput.bridge.drain();
            require(facts.size() == 6 && facts.get(0).key == Keyboard.KEY_X && facts.get(0).down,
                    "同一消费槽的首键丢失");
            require("中🙂".equals(facts.get(1).edit.text), "原生文字未复制或序列被按设备分组");
            require(facts.get(2).pointer.kind == ScenePointerEvent.Kind.MOVE
                    && facts.get(3).pointer.kind == ScenePointerEvent.Kind.DOWN
                    && Boolean.FALSE.equals(facts.get(4).focused) && !facts.get(5).down,
                    "鼠标、失焦和键盘未保持同一次消费序");
            require(facts.get(2).pointer.x == 25 && facts.get(2).pointer.y == 40
                    && facts.get(2).pointer.timeNanos == 4 && facts.get(3).pointer.button == 0,
                    "原生坐标/主键/采集时刻转换错误");
            require(nativeInput.bridge.drain().isEmpty(), "消费事实被重复递交");
            System.out.println("SDL_CONSUMER_FACTS key,text,move,down,focus,up sameSlotOrder=true immutableText=true coordinates=true noReplay=true");
        }
    }

    private void formalScene() throws Exception {
        ReactiveDomain domain = ReactiveDomain.direct();
        SceneEditState[] state = {null};
        PlainHost host = new PlainHost();
        try (ScenePageSession page = ScenePageSession.open(domain, host, host, context -> {
            state[0] = new SceneEditState(domain, "");
            SceneNode field = SceneTextField.create(context, state[0], context.theme(), () -> false, ignored -> {})
                    .size(Size.fixed(260), Size.natural());
            context.mount(new SceneNode().initialFocus(field).add(field));
        }); NativeEnvironment nativeInput = new NativeEnvironment()) {
            page.frame();
            nativeInput.input(SDL_EVENT_TEXT_EDITING, "zhong");
            nativeInput.input(SDL_EVENT_TEXT_EDITING, "");
            nativeInput.input(SDL_EVENT_TEXT_INPUT, "中文🙂");
            nativeInput.input(SDL_EVENT_TEXT_INPUT, "1");
            List<NativeEvent> facts = deliver(nativeInput, page);
            require(facts.get(0).edit.compositionPhase == SceneEditEvent.CompositionPhase.BEGIN
                    && facts.get(1).edit.compositionPhase == SceneEditEvent.CompositionPhase.UPDATE
                    && facts.get(2).edit.compositionPhase == SceneEditEvent.CompositionPhase.COMMIT
                    && facts.get(0).edit.compositionSource == facts.get(1).edit.compositionSource
                    && facts.get(1).edit.compositionSource == facts.get(2).edit.compositionSource,
                    "SDL组字段没有保持同一个正式来源");
            page.frame();
            domain.execute(() -> require("中文🙂1".equals(state[0].text().get()), "正式字段未接收有来源文本序列"));
            page.edit(SceneEditEvent.command(SceneEditEvent.Command.UNDO, false, null)); page.frame();
            domain.execute(() -> require("中文🙂".equals(state[0].text().get()), "普通数字并入IME原子撤销"));
            page.edit(SceneEditEvent.command(SceneEditEvent.Command.UNDO, false, null)); page.frame();
            domain.execute(() -> require("".equals(state[0].text().get()), "有来源IME提交未保持原子撤销"));
            System.out.println("SDL_SOURCE_TEXT ordinaryText=true emptyPreedit=true atomicIMEUndo=true sameSource=true");
        }
    }

    private static List<NativeEvent> deliver(NativeEnvironment nativeInput, ScenePageSession page) {
        List<NativeEvent> facts = nativeInput.bridge.drain();
        for (NativeEvent fact : facts) {
            if (fact.edit != null) page.edit(fact.edit);
            else if (fact.pointer != null) page.pointer(fact.pointer);
            else if (Boolean.FALSE.equals(fact.focused)) page.focusLost();
        }
        return facts;
    }

    private void sourceIsolation() throws Exception {
        ReactiveDomain domain = ReactiveDomain.direct();
        SceneEditState[] states = new SceneEditState[2];
        SceneNode[] fields = new SceneNode[2];
        PlainHost host = new PlainHost();
        try (ScenePageSession page = ScenePageSession.open(domain, host, host, context -> {
            for (int index = 0; index < fields.length; index++) {
                states[index] = new SceneEditState(domain, index == 0 ? "甲" : "乙");
                fields[index] = SceneTextField.create(context, states[index], context.theme(), () -> false, ignored -> {})
                        .size(Size.fixed(260), Size.fixed(48));
            }
            context.mount(new SceneNode().initialFocus(fields[0]).add(fields[0]).add(fields[1]));
        }); NativeEnvironment nativeInput = new NativeEnvironment()) {
            page.frame();
            nativeInput.input(SDL_EVENT_TEXT_EDITING, "jia");
            deliver(nativeInput, page); page.frame();
            domain.execute(() -> {
                require(states[0].snapshot().get().composing, "原字段未开始组字");
                require(page.runtime().focusRouter().request(page.runtime().captureMount(fields[1])), "乙未取得焦点");
            });
            nativeInput.input(SDL_EVENT_TEXT_EDITING, "迟到候选");
            nativeInput.input(SDL_EVENT_TEXT_INPUT, "迟到提交");
            deliver(nativeInput, page); page.frame();
            domain.execute(() -> {
                require("甲".equals(states[0].text().get()) && "乙".equals(states[1].text().get()), "旧来源进入后来焦点");
                require(page.runtime().focusRouter().request(page.runtime().captureMount(fields[0])), "甲未恢复焦点");
            });
            nativeInput.input(SDL_EVENT_TEXT_EDITING, "same-batch");
            deliver(nativeInput, page); page.frame();
            club.heiqi.uilib.ui.scene.layout.LayoutBox[] targetBox = {null};
            domain.execute(() -> targetBox[0] = page.pipeline().current().geometry.get(fields[1]).box);
            club.heiqi.uilib.ui.scene.layout.LayoutBox box = targetBox[0];
            // 真实SDL坐标经生产桥缩放，指针与COMMIT在同一次drain中交给原会话。
            try (Input pointer = new Input(SDL_EVENT_MOUSE_BUTTON_DOWN, null)) {
                pointer.event.button().x((box.x + 8) / scale()).y((box.y + 8) / scale());
                nativeInput.capture(pointer);
            }
            nativeInput.input(SDL_EVENT_TEXT_INPUT, "同批旧提交");
            List<NativeEvent> batch = deliver(nativeInput, page); page.frame();
            require(batch.size() == 2 && batch.get(0).pointer != null && batch.get(1).edit.compositionSource != null,
                    "同批反例未经过指针到有来源提交的顺序");
            domain.execute(() -> {
                require(page.runtime().focusRouter().focused().get() == fields[1], "同批指针未命中乙字段");
                require("甲".equals(states[0].text().get()) && "乙".equals(states[1].text().get()), "同批POINTER到COMMIT写入后来目标");
            });
            nativeInput.input(SDL_EVENT_MOUSE_BUTTON_UP, null);
            nativeInput.input(SDL_EVENT_TEXT_EDITING, "old-baseline");
            deliver(nativeInput, page); page.frame();
            domain.execute(() -> states[1].reset("新基线"));
            nativeInput.input(SDL_EVENT_TEXT_INPUT, "旧基线提交");
            deliver(nativeInput, page); page.frame();
            domain.execute(() -> require("新基线".equals(states[1].text().get()), "旧来源改写重置后的基线"));
            nativeInput.input(SDL_EVENT_TEXT_EDITING, "xin");
            nativeInput.input(SDL_EVENT_TEXT_EDITING, "");
            nativeInput.input(SDL_EVENT_KEY_DOWN, null);
            nativeInput.input(SDL_EVENT_TEXT_INPUT, "新");
            deliver(nativeInput, page); page.frame();
            domain.execute(() -> require("新基线新".equals(states[1].text().get()), "新来源未正常提交，或空preedit后按键丢失联系"));
            System.out.println("SDL_SOURCE_ISOLATION lateFocus=true pointerThenCommit=true resetBaseline=true freshSource=true");
        }
    }

    private void textBorrow() throws Exception {
        ReactiveDomain domain = ReactiveDomain.direct();
        SceneEditState[] state = {null};
        PlainHost host = new PlainHost();
        try (ScenePageSession page = ScenePageSession.open(domain, host, host, context -> {
            state[0] = new SceneEditState(domain, "甲");
            SceneNode field = SceneTextField.create(context, state[0], context.theme(), () -> false, ignored -> {})
                    .size(Size.fixed(260), Size.fixed(48));
            context.mount(new SceneNode().initialFocus(field).add(field));
        }); NativeEnvironment nativeInput = new NativeEnvironment()) {
            page.frame();
            nativeInput.input(SDL_EVENT_TEXT_EDITING, "旧候选");
            SceneEditEvent begin = deliver(nativeInput, page).get(0).edit;
            page.frame();
            nativeInput.bridge.textInput(false);
            List<NativeEvent> cancelled = nativeInput.bridge.drain();
            require(cancelled.size() == 1 && cancelled.get(0).edit.compositionPhase == SceneEditEvent.CompositionPhase.CANCEL
                    && cancelled.get(0).edit.compositionSource == begin.compositionSource, "停止没有取消原来源");
            page.edit(cancelled.get(0).edit); page.frame();
            nativeInput.bridge.textInput(false);
            require(nativeInput.bridge.drain().isEmpty(), "重复停止重复递交取消");
            try (Input oldUpdate = new Input(SDL_EVENT_TEXT_EDITING, "停止期间旧候选");
                    Input oldCommit = new Input(SDL_EVENT_TEXT_INPUT, "停止期间旧提交");
                    Input key = new Input(SDL_EVENT_KEY_DOWN, null)) {
                require(SDL_PushEvent(oldUpdate.event) && SDL_PushEvent(oldCommit.event) && SDL_PushEvent(key.event), "旧事实未进入真实SDL队列");
                nativeInput.bridge.textInput(true);
                List<NativeEvent> boundary = nativeInput.bridge.drain();
                require(boundary.size() == 1 && boundary.get(0).edit == null && boundary.get(0).down,
                        "原生start前旧文本进入新session，或键事实被全局flush丢弃");
            }
            // SDL_StopTextInput没有最终TEXT_INPUT承诺；真实新组字不能等一个不存在的旧提交。
            nativeInput.input(SDL_EVENT_TEXT_EDITING, "xin");
            nativeInput.input(SDL_EVENT_TEXT_INPUT, "新中文");
            List<NativeEvent> fresh = nativeInput.bridge.drain();
            require(fresh.size() == 2 && fresh.get(0).edit.compositionPhase == SceneEditEvent.CompositionPhase.BEGIN
                    && fresh.get(0).edit.compositionSource != begin.compositionSource
                    && fresh.get(0).edit.compositionSource == fresh.get(1).edit.compositionSource, "真实新组字段未建立独立来源");
            for (NativeEvent fact : fresh) page.edit(fact.edit);
            page.frame();
            domain.execute(() -> require("甲新中文".equals(state[0].text().get()), "原生重启后首轮新组字被旧联系拒绝"));
            nativeInput.bridge.textInput(true);
            require(begins == 2 && ends == 1, "重复start叠加宿主借用");
            System.out.println("SDL_SOURCE_BORROW cancelSameSource=true noFinalCommitRequired=true nativeQueueBoundary=true keysPreserved=true freshSource=true repeatStartBalanced=true");
        }
    }

    private void windowLifecycle() throws Exception {
        require(SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS), "SDL初始化失败");
        try {
            long window = SDL_CreateWindow("UILib SDL regression", 320, 160, SDL_WINDOW_HIDDEN);
            require(window != 0, "SDL hidden window创建失败");
            int previousWindow = windowId;
            nativeWindow = window; windowId = SDL_GetWindowID(window);
            try (NativeEnvironment nativeInput = new NativeEnvironment()) {
                require(SDL_TextInputActive(window), "SDL文本输入未激活");
                nativeInput.input(SDL_EVENT_TEXT_EDITING, "旧候选");
                SceneEditEvent old = nativeInput.bridge.drain().get(0).edit;
                nativeInput.bridge.textInput(false);
                require(!SDL_TextInputActive(window), "SDL文本输入未停止");
                nativeInput.bridge.drain();
                // 真实Stop没有产生任何最终提交，下一次Start仍须立即接受全新来源。
                nativeInput.bridge.textInput(true);
                nativeInput.input(SDL_EVENT_TEXT_EDITING, "xin");
                nativeInput.input(SDL_EVENT_TEXT_INPUT, "新中文");
                List<NativeEvent> fresh = nativeInput.bridge.drain();
                require(SDL_TextInputActive(window) && fresh.size() == 2
                        && fresh.get(0).edit.compositionPhase == SceneEditEvent.CompositionPhase.BEGIN
                        && fresh.get(0).edit.compositionSource != old.compositionSource
                        && fresh.get(0).edit.compositionSource == fresh.get(1).edit.compositionSource,
                        "真实Stop/Start后的首轮输入被旧source拒绝");
                System.out.println("SDL_NATIVE_RESTART realStopStart=true noOldCommit=true freshSource=true version="
                        + org.lwjgl.sdl.SDLVersion.SDL_GetVersion() + " revision=" + org.lwjgl.sdl.SDLVersion.SDL_GetRevision());
            } finally {
                nativeWindow = 0; windowId = previousWindow; SDL_DestroyWindow(window);
            }
        } finally { SDL_Quit(); }
    }

    private static final class PlainHost implements SceneHost, SceneTextMeasurer {
        public int width() { return 320; }
        public int height() { return 160; }
        public long metricsRevision() { return 0; }
        public void replay(PaintPlan plan) {}
        public TextLayout layout(String text, TextStyle style, int width) { return layout(text, style, width, TextPolicy.NORMAL); }
        public TextLayout layout(String text, TextStyle style, int width, TextPolicy policy) {
            int advance = text.codePointCount(0, text.length()) * 8;
            TextLayout.Line line = new TextLayout.Line(text, 0, 0, 0, text.length(), 0, text.length(), advance,
                    style.size, style.size, 0, style.size + 2, Collections.emptyList());
            return new TextLayout(advance, style.size + 2, advance, style.size + 2, style,
                    Collections.singletonList(line), text, text, 0, Collections.emptyList());
        }
    }
    public static void main(String[] args) throws Exception { new SdlTextInputScenario().run(args); }
}
