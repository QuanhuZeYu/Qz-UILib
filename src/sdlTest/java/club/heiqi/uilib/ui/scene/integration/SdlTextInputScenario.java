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
    public static float scale() { return 2; }
    public static void begin() { begins++; }
    public static void end() { ends++; }

    @Override protected void runTests() throws Exception {
        scenario("缓存消费方法与槽精确匹配，缺失描述符可诊断", this::consumerSupport);
        scenario("同消费序保留键鼠文本焦点且复制原生载荷", this::orderedFacts);
        scenario("生产兼容组字与普通提交保持原子撤销", this::formalScene);
        scenario("停止开始不等待未承诺的旧提交", this::textBorrow);
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
            begins = 0; ends = 0;
            Object bindings = field(SceneNativeTextInput.class, "bindings").get(bridge);
            Class<?> type = bindings.getClass();
            field(type, "windowId").set(bindings, SdlTextInputScenario.class.getField("windowId"));
            field(type, "currentEvent").set(bindings, SdlTextInputScenario.class.getField("currentEvent"));
            field(type, "scale").set(bindings, SdlTextInputScenario.class.getMethod("scale"));
            field(type, "begin").set(bindings, SdlTextInputScenario.class.getMethod("begin"));
            field(type, "end").set(bindings, SdlTextInputScenario.class.getMethod("end"));
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
            for (NativeEvent fact : nativeInput.bridge.drain()) if (fact.edit != null) page.edit(fact.edit);
            page.frame();
            domain.execute(() -> require("中文🙂1".equals(state[0].text().get()), "正式字段未接收兼容文本序列"));
            page.edit(SceneEditEvent.command(SceneEditEvent.Command.UNDO, false, null)); page.frame();
            domain.execute(() -> require("中文🙂".equals(state[0].text().get()), "普通数字并入IME原子撤销"));
            page.edit(SceneEditEvent.command(SceneEditEvent.Command.UNDO, false, null)); page.frame();
            domain.execute(() -> require("".equals(state[0].text().get()), "兼容IME提交未保持原子撤销"));
            System.out.println("SDL_COMPAT_TEXT ordinaryText=true emptyPreedit=true atomicIMEUndo=true");
        }
    }

    private void textBorrow() throws Exception {
        try (NativeEnvironment nativeInput = new NativeEnvironment()) {
            nativeInput.input(SDL_EVENT_TEXT_EDITING, "旧候选");
            nativeInput.bridge.textInput(false);
            require(nativeInput.bridge.drain().isEmpty(), "停止未丢弃待处理旧文本");
            nativeInput.input(SDL_EVENT_TEXT_INPUT, "停止期间");
            require(nativeInput.bridge.drain().isEmpty(), "停止期间仍采集文本意图");
            nativeInput.bridge.textInput(true);
            nativeInput.input(SDL_EVENT_TEXT_EDITING, "xin");
            nativeInput.input(SDL_EVENT_TEXT_INPUT, "新中文");
            List<NativeEvent> facts = nativeInput.bridge.drain();
            require(facts.size() == 2 && facts.get(0).edit.composing && "新中文".equals(facts.get(1).edit.text),
                    "重新开始等待不存在的旧提交");
            nativeInput.bridge.textInput(true);
            require(begins == 2 && ends == 1, "重复start叠加宿主借用");
            System.out.println("SDL_COMPAT_BORROW stopClearsPending=true inactiveTextRejected=true restartImmediate=true repeatStartBalanced=true");
        }
    }

    private void windowLifecycle() {
        require(SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS), "SDL初始化失败");
        try {
            long window = SDL_CreateWindow("UILib SDL regression", 320, 160, SDL_WINDOW_HIDDEN);
            require(window != 0, "SDL hidden window创建失败");
            try {
                require(SDL_StartTextInput(window) && SDL_TextInputActive(window), "SDL文本输入未激活");
                require(SDL_StopTextInput(window) && !SDL_TextInputActive(window), "SDL文本输入未停止");
            } finally { SDL_DestroyWindow(window); }
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
