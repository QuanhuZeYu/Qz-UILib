package club.heiqi.uilib.ui.scene.integration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;

import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLKeyboard.*;
import static org.lwjgl.sdl.SDLScancode.*;
import static org.lwjgl.sdl.SDLVideo.*;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 真实SDL hidden window和生产Watch资源回归，不启动游戏或字体服务。
 * 验证观察器的native边界与正式scene编辑；Display/InputEvents宿主绑定、Windows候选窗及物理小键盘仍需宿主实测。 */
public final class SdlTextInputScenario extends TestApplication {
    private long window;
    private int windowId;
    private ExecutorService workers;

    @Override
    protected void setup() {
        require(SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS), "SDL初始化失败");
        onClose(() -> SDL_Quit());
        window = SDL_CreateWindow("UILib SDL regression", 320, 160, SDL_WINDOW_HIDDEN);
        require(window != 0, "SDL hidden window创建失败");
        onClose(() -> SDL_DestroyWindow(window));
        windowId = SDL_GetWindowID(window);
        require(windowId != 0, "真实windowID不能为空");
        workers = Executors.newFixedThreadPool(2);
        onClose(
                () -> {
                    workers.shutdown();
                    require(workers.awaitTermination(10, TimeUnit.SECONDS), "native worker未退出");
                });
    }

    @Override
    protected void runTests() throws Exception {
        scenario("真实窗口文本输入开始停止", this::textLifecycle);
        scenario("生产Watch跨线程复制且Peep仍消费原序列，remove后不重放", this::observeWithoutConsuming);
        scenario("生产Watch关闭等待在途callback与正式字段原子IME", this::closeAndScene);
    }

    private void textLifecycle() {
        require(SDL_StartTextInput(window), "SDL_StartTextInput失败");
        onClose(() -> SDL_StopTextInput(window));
        require(SDL_TextInputActive(window), "真实窗口文本输入未激活");
        require(SDL_StartTextInput(window) && SDL_TextInputActive(window), "重复开始改变生命周期");
        require(SDL_StopTextInput(window) && !SDL_TextInputActive(window), "真实窗口文本输入未停止");
        System.out.println("EVIDENCE SDL hiddenWindow=" + windowId + " Start/Active/Stop=true");
    }

    /** 仅反射生产私有资源，不复制注册、代理default方法或关闭算法。 */
    private WatchResource watch(LongConsumer observer) throws Exception {
        Class<?> type =
                Class.forName("club.heiqi.uilib.ui.host.minecraft.SceneNativeTextInput$Watch");
        Constructor<?> constructor = type.getDeclaredConstructor(LongConsumer.class);
        constructor.setAccessible(true);
        Object watch = constructor.newInstance(observer);
        Method close = type.getDeclaredMethod("close");
        close.setAccessible(true);
        Method check = type.getDeclaredMethod("checkFailure");
        check.setAccessible(true);
        return new WatchResource(watch, close, check);
    }

    private static final class WatchResource implements AutoCloseable {
        final Object value;
        final Method close, check;

        WatchResource(Object value, Method close, Method check) {
            this.value = value;
            this.close = close;
            this.check = check;
        }

        @Override
        public void close() throws Exception {
            close.invoke(value);
        }

        void checkFailure() throws Exception {
            check.invoke(value);
        }
    }

    private static final class Observed {
        final int type, scancode;
        final String text;
        final Thread thread;

        Observed(SDL_Event event) {
            type = event.type();
            thread = Thread.currentThread();
            scancode =
                    type == SDL_EVENT_KEY_DOWN || type == SDL_EVENT_KEY_UP
                            ? event.key().scancode()
                            : 0;
            text =
                    type == SDL_EVENT_TEXT_INPUT
                            ? event.text().textString()
                            : type == SDL_EVENT_TEXT_EDITING ? event.edit().textString() : null;
        }
    }

    private boolean ours(SDL_Event event) {
        int type = event.type();
        return type == SDL_EVENT_TEXT_INPUT
                ? event.text().windowID() == windowId
                : type == SDL_EVENT_TEXT_EDITING
                        ? event.edit().windowID() == windowId
                        : (type == SDL_EVENT_KEY_DOWN || type == SDL_EVENT_KEY_UP)
                                && event.key().windowID() == windowId;
    }

    /** Push后保留native文本分配至Peep消费完成，避免把悬空pointer当作复制测试。 */
    private final class Input implements AutoCloseable {
        final SDL_Event event = SDL_Event.calloc();
        final ByteBuffer bytes;

        Input(int type, String text) {
            bytes = text == null ? null : MemoryUtil.memUTF8(text);
            event.type(type);
            if (type == SDL_EVENT_TEXT_INPUT)
                event.text().type(type).windowID(windowId).text(bytes);
            else if (type == SDL_EVENT_TEXT_EDITING)
                event.edit().type(type).windowID(windowId).text(bytes);
            else
                event.key()
                        .type(type)
                        .windowID(windowId)
                        .scancode(SDL_SCANCODE_KP_1)
                        .down(type == SDL_EVENT_KEY_DOWN);
        }

        void push() {
            require(SDL_PushEvent(event), "SDL_PushEvent失败 type=" + event.type());
        }

        @Override
        public void close() {
            event.free();
            if (bytes != null) MemoryUtil.memFree(bytes);
        }
    }

    private List<Observed> consume() {
        List<Observed> result = new ArrayList<>();
        try (SDL_Event.Buffer buffer = SDL_Event.calloc(1)) {
            int count;
            while ((count =
                            SDL_PeepEvents(
                                    buffer, SDL_GETEVENT, SDL_EVENT_KEY_DOWN, SDL_EVENT_TEXT_INPUT))
                    > 0) {
                SDL_Event event = buffer.get(0);
                if (ours(event)) result.add(new Observed(event));
            }
            require(count == 0, "SDL_PeepEvents失败");
        }
        return result;
    }

    private void observeWithoutConsuming() throws Exception {
        SDL_FlushEvents(SDL_EVENT_KEY_DOWN, SDL_EVENT_TEXT_INPUT);
        List<Observed> observed = Collections.synchronizedList(new ArrayList<>());
        AutoCloseable watch =
                own(
                        watch(
                                pointer -> {
                                    SDL_Event event = SDL_Event.create(pointer);
                                    if (ours(event)) observed.add(new Observed(event));
                                }));
        Thread main = Thread.currentThread();
        List<Observed> consumed;
        try (Input preedit = new Input(SDL_EVENT_TEXT_EDITING, "拼音");
                Input empty = new Input(SDL_EVENT_TEXT_EDITING, "");
                Input commit = new Input(SDL_EVENT_TEXT_INPUT, "中文🙂");
                Input key = new Input(SDL_EVENT_KEY_DOWN, null);
                Input digit = new Input(SDL_EVENT_TEXT_INPUT, "1")) {
            workers.submit(
                            () -> {
                                preedit.push();
                                empty.push();
                                commit.push();
                                key.push();
                                digit.push();
                            })
                    .get(10, TimeUnit.SECONDS);
            consumed = consume();
            require(observed.size() == 5 && consumed.size() == 5, "观察器不能消费或丢失SDL原事件");
            for (int i = 0; i < observed.size(); i++) {
                Observed a = observed.get(i), b = consumed.get(i);
                require(
                        a.type == b.type
                                && java.util.Objects.equals(a.text, b.text)
                                && a.scancode == b.scancode,
                        "Peep与观察序列不一致 index=" + i);
                require(a.thread != main, "Push worker未实际进入native callback");
            }
            // 覆写原native分配，再释放；快照仍必须保留原中文/空preedit/数字。
            MemoryUtil.memSet(MemoryUtil.memAddress(commit.bytes), 0, commit.bytes.remaining());
        }
        require(
                "中文🙂".equals(observed.get(2).text) && "".equals(observed.get(1).text),
                "native文本未复制为不可变Java快照");
        require(
                observed.get(3).scancode == SDL_SCANCODE_KP_1 && "1".equals(observed.get(4).text),
                "小键盘扫描码不能当ASCII字符");
        watch.close();
        try (Input late = new Input(SDL_EVENT_TEXT_INPUT, "关闭后")) {
            workers.submit(late::push).get(10, TimeUnit.SECONDS);
            require(consume().size() == 1 && observed.size() == 5, "remove后仍callback或改变SDL消费队列");
        }
        require(consume().isEmpty(), "SDL消费后发生重放");
        System.out.println(
                "EVIDENCE productionWatch workerSnapshots=5 PeepSameSequence=5 removeNoReplay=true KP1Text=1");
    }

    private void closeAndScene() throws Exception {
        CountDownLatch entered = new CountDownLatch(1),
                release = new CountDownLatch(1),
                closing = new CountDownLatch(1);
        AtomicBoolean returned = new AtomicBoolean();
        AutoCloseable watch =
                own(
                        watch(
                                pointer -> {
                                    if (!ours(SDL_Event.create(pointer))) return;
                                    entered.countDown();
                                    try {
                                        require(
                                                release.await(10, TimeUnit.SECONDS),
                                                "在途callback等待超时");
                                    } catch (InterruptedException failure) {
                                        Thread.currentThread().interrupt();
                                        throw new AssertionError(failure);
                                    }
                                    returned.set(true);
                                }));
        try (Input event = new Input(SDL_EVENT_TEXT_INPUT, "在途")) {
            Future<?> push = workers.submit(event::push);
            require(entered.await(10, TimeUnit.SECONDS), "生产Watch未接收真实Push");
            Future<?> close =
                    workers.submit(
                            () -> {
                                closing.countDown();
                                try {
                                    watch.close();
                                } catch (Exception failure) {
                                    throw new RuntimeException(failure);
                                }
                                require(returned.get(), "Watch在callback退出前完成释放");
                            });
            try {
                require(closing.await(10, TimeUnit.SECONDS), "关闭worker未开始");
                // 有界Future等待区分关闭已完成和仍受在途callback阻塞；不靠sleep猜时序。
                try {
                    close.get(100, TimeUnit.MILLISECONDS);
                    throw new AssertionError("callback未退出时close已完成");
                } catch (java.util.concurrent.TimeoutException expected) {
                }
            } finally {
                release.countDown();
            }
            push.get(10, TimeUnit.SECONDS);
            close.get(10, TimeUnit.SECONDS);
            require(consume().size() == 1, "close在途改变原SDL队列");
        } finally {
            release.countDown();
        }
        formalScene();
        callbackFailure();
        System.out.println(
                "EVIDENCE productionWatch closeInflight=true callbackFailureIdentity=true formalScene IMEAtomicUndo=true emptyPreedit=true");
    }

    private void callbackFailure() throws Exception {
        AssertionError expected = new AssertionError("native callback故障探针");
        WatchResource watch =
                watch(
                        pointer -> {
                            if (ours(SDL_Event.create(pointer))) throw expected;
                        });
        try (Input event = new Input(SDL_EVENT_TEXT_INPUT, "故障")) {
            workers.submit(event::push).get(10, TimeUnit.SECONDS);
            require(consume().size() == 1, "callback故障不能吞掉SDL原事件");
            try {
                watch.checkFailure();
                throw new AssertionError("callback故障未交回调用线程");
            } catch (java.lang.reflect.InvocationTargetException failure) {
                require(failure.getCause() == expected, "callback故障身份丢失");
            }
        } finally {
            try {
                watch.close();
                throw new AssertionError("close未报告callback故障");
            } catch (java.lang.reflect.InvocationTargetException failure) {
                require(failure.getCause() == expected, "close故障身份丢失");
            }
            watch.close();
        }
    }

    private void formalScene() throws Exception {
        ReactiveDomain domain = ReactiveDomain.direct();
        SceneEditState[] state = new SceneEditState[1];
        domain.execute(() -> state[0] = new SceneEditState(domain, ""));
        PlainHost host = new PlainHost();
        ScenePageSession session =
                own(
                        ScenePageSession.open(
                                domain,
                                host,
                                host,
                                ctx -> {
                                    SceneNode field =
                                            SceneTextField.create(
                                                            ctx,
                                                            state[0],
                                                            domain.signal(SceneTheme.light()),
                                                            domain.signal(false),
                                                            submission -> {},
                                                            SceneTextField.SubmitPolicy.CONFIRM)
                                                    .size(Size.fixed(260), Size.natural());
                                    ctx.mount(new SceneNode().initialFocus(field).add(field));
                                }));
        session.frame();
        List<Observed> snapshots = Collections.synchronizedList(new ArrayList<>());
        try (AutoCloseable watch =
                        watch(
                                pointer -> {
                                    SDL_Event event = SDL_Event.create(pointer);
                                    if (ours(event)) snapshots.add(new Observed(event));
                                });
                Input preedit = new Input(SDL_EVENT_TEXT_EDITING, "zhong");
                Input empty = new Input(SDL_EVENT_TEXT_EDITING, "");
                Input commit = new Input(SDL_EVENT_TEXT_INPUT, "中文🙂");
                Input digit = new Input(SDL_EVENT_TEXT_INPUT, "1")) {
            require(SDL_StartTextInput(window), "场景开始文本输入失败");
            try {
                workers.submit(
                                () -> {
                                    preedit.push();
                                    empty.push();
                                    commit.push();
                                    digit.push();
                                })
                        .get(10, TimeUnit.SECONDS);
                require(consume().size() == 4 && snapshots.size() == 4, "正式场景SDL输入不完整");
                // 这里是scene宿主fixture的递交；生产Display/InputEvents绑定不在此独立classpath上。
                for (Observed snapshot : snapshots)
                    session.edit(
                            snapshot.type == SDL_EVENT_TEXT_EDITING
                                    ? SceneEditEvent.composition(
                                            snapshot.text, !snapshot.text.isEmpty())
                                    : SceneEditEvent.committed(snapshot.text));
                session.frame();
                domain.execute(
                        () -> require("中文🙂1".equals(state[0].text().get()), "正式字段未按序接收native提交"));
                session.edit(SceneEditEvent.command(SceneEditEvent.Command.UNDO, false, null));
                session.frame();
                domain.execute(
                        () -> require("中文🙂".equals(state[0].text().get()), "数字提交不应并入IME原子撤销"));
                session.edit(SceneEditEvent.command(SceneEditEvent.Command.UNDO, false, null));
                session.frame();
                domain.execute(() -> require("".equals(state[0].text().get()), "整串IME应一次撤销"));
            } finally {
                require(SDL_StopTextInput(window), "场景停止文本输入失败");
            }
        }
    }

    /** 普通测量fixture只提供布局事实，不加载字体服务或复制编辑算法。 */
    private static final class PlainHost implements SceneHost, SceneTextMeasurer {
        @Override
        public int width() {
            return 320;
        }

        @Override
        public int height() {
            return 160;
        }

        @Override
        public long metricsRevision() {
            return 0;
        }

        @Override
        public void replay(PaintPlan plan) {}

        @Override
        public TextLayout layout(String text, TextStyle style, int width) {
            return layout(text, style, width, TextPolicy.NORMAL);
        }

        @Override
        public TextLayout layout(String text, TextStyle style, int width, TextPolicy policy) {
            int advance = text.codePointCount(0, text.length()) * 8;
            TextLayout.Line line =
                    new TextLayout.Line(
                            text,
                            0,
                            0,
                            0,
                            text.length(),
                            0,
                            text.length(),
                            advance,
                            style.size,
                            style.size,
                            0,
                            style.size + 2,
                            Collections.emptyList());
            return new TextLayout(
                    advance,
                    style.size + 2,
                    advance,
                    style.size + 2,
                    style,
                    Collections.singletonList(line),
                    text,
                    text,
                    0,
                    Collections.emptyList());
        }
    }

    public static void main(String[] args) throws Exception {
        new SdlTextInputScenario().run(args);
    }
}
