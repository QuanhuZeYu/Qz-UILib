package club.heiqi.uilib.ui.host.minecraft;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.LWJGLUtil;
import club.heiqi.uilib.host.embedded.NativeInputPolicy;
import club.heiqi.uilib.host.embedded.ScrollAccumulator;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.host.SceneTabInputAdapter;
import club.heiqi.uilib.ui.scene.host.SceneEscapeInputAdapter;
import club.heiqi.uilib.ui.scene.host.SceneKeyInputAdapter;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot;

/** 正式 MC 页面入口：内容声明与 GuiScreen 生命周期、唯一原生输入消费者分离。
 * 同次打开的重初始化保留会话；本页负责所属资源，共享字体服务归应用持有。
 * 游戏是否暂停由页面通过 doesGuiPauseGame 声明，默认沿用 GuiScreen。
 */
public class MinecraftSceneScreen extends GuiScreen {
    private final ReactiveDomain domain;
    private final ScenePage page;
    private final club.heiqi.uilib.ui.media.MediaService mediaService;
    private ScenePageSession session;
    private volatile Inputs inputs;

    public MinecraftSceneScreen(ScenePage page) {
        this(ReactiveDomain.direct(), page);
    }

    /** 借用业务状态所属域，其生命周期由业务持有者管理。 */
    public MinecraftSceneScreen(ReactiveDomain domain, ScenePage page) {
        this(domain, page, null);
    }
    public MinecraftSceneScreen(ScenePage page, club.heiqi.uilib.ui.media.MediaService media) {
        this(ReactiveDomain.direct(), page, Objects.requireNonNull(media, "media"));
    }
    /** 借用业务持有的媒体服务，native 随实际媒体会话初始化。 */
    public MinecraftSceneScreen(ReactiveDomain domain, ScenePage page, club.heiqi.uilib.ui.media.MediaService media) {
        this.mediaService = media;
        this.domain = Objects.requireNonNull(domain, "domain");
        this.page = Objects.requireNonNull(page, "page");
    }

    /** 返回同页 Host 最近发布的纯值快照，未开屏或关闭后返回空快照。 */
    public SceneSemanticSnapshot semantics() {
        Inputs current = inputs;
        return current == null ? SceneSemanticSnapshot.EMPTY : current.semantics();
    }

    /** 沿原会话有序入口递交；future在handler及Effect结算后完成。 */
    public CompletableFuture<SceneSemanticRequest.Result> semantic(SceneSemanticRequest request) {
        Objects.requireNonNull(request, "语义请求");
        Inputs current = inputs;
        return current == null ? CompletableFuture.completedFuture(SceneSemanticRequest.Result.CLOSED)
                : current.semantic(request);
    }

    @Override
    public void initGui() {
        if (session != null) return;
        MinecraftSceneHost host = new MinecraftSceneHost(mediaService);
        final boolean[] owned = {false};
        final Inputs[] openingInputs = {null};
        ScenePageSession opened = null;
        try {
            opened =
                    ScenePageSession.open(
                            domain,
                            host,
                            host,
                            ctx -> {
                                ctx.own(host);
                                owned[0] = true;
                                page.build(ctx);
                            },
                            closing -> closeOpening(closing, openingInputs[0]));
            session = opened;
            Inputs acquired = new Inputs(opened, host::semantics);
            openingInputs[0] = acquired;
            inputs = acquired;
            acquired.initialize();
        } catch (RuntimeException | Error failure) {
            // 输入初始化失败时退休本次输入，并结束会话及其 scope。
            try {
                closeOpening(opened, openingInputs[0]);
            } catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            // 直接释放尚待转交作用域的宿主，清理失败追加到原始失败。
            if (!owned[0]) {
                try {
                    host.close();
                } catch (RuntimeException | Error cleanup) {
                    if (cleanup != failure) failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
    }

    /** MC 可独立于 runTick 绘制；呈现前耗尽原生历史，再按物理释放事实恢复失焦后的输入。 */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (session == null) return;
        ScenePageSession opened = session;
        Inputs collecting = inputs;
        if (mc.currentScreen == this) {
            collecting.syncInputAvailability();
            // 呈现前与 runTick 共用原生历史消费者，及时响应拖动；next 保证每个事件消费一次。
            handleInput();
            if (mc.currentScreen == this && session == opened && inputs == collecting) opened.frame();
        }
        // frame 内换页后由当前 UILib screen 接续绘制，使本帧呈现新页。
        GuiScreen active = mc.currentScreen;
        if (active != this) {
            if (active instanceof MinecraftSceneScreen) active.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        if (session == opened && inputs == collecting) collecting.syncNativeTextFocus();
    }

    @Override
    public void handleInput() {
        Inputs collecting = inputs;
        club.heiqi.uilib.host.lwjgl2.Lwjgl2InputQueue.drain(this::handleMouseInput, this::handleKeyboardInput);
        if (collecting != null && inputs == collecting) collecting.historyDrained();
    }

    @Override
    public void handleKeyboardInput() {
        if (inputs != null) inputs.handleKeyboardInput();
    }

    @Override
    public void handleMouseInput() {
        if (inputs != null) inputs.handleMouseInput();
    }

    private void fallbackEscape() { super.keyTyped((char) 0, Keyboard.KEY_ESCAPE); }

    /** 同次会话的宿主输入借用；独立adapter仍持有各自物理按压门，核心持焦点/手势权威。 */
    private final class Inputs implements AutoCloseable {
        final ScenePageSession opened;
        private final Supplier<SceneSemanticSnapshot> semantics;
        private final NativeInputPolicy inputPolicy =
                new NativeInputPolicy(LWJGLUtil.getPlatform() == LWJGLUtil.PLATFORM_MACOSX);
        private final ScrollAccumulator scroll = new ScrollAccumulator();
        Inputs(ScenePageSession opened, Supplier<SceneSemanticSnapshot> semantics) {
            this.opened = opened;
            this.semantics = Objects.requireNonNull(semantics, "语义读取端口");
        }
        SceneSemanticSnapshot semantics() { return semantics.get(); }
        CompletableFuture<SceneSemanticRequest.Result> semantic(SceneSemanticRequest request) {
            return opened.semantic(request);
        }
        private SceneTabInputAdapter tabInput;
        private club.heiqi.uilib.ui.scene.host.SceneEditInputAdapter editInput;
        private SceneKeyInputAdapter keyInput;
        private boolean leftCtrl, rightCtrl, leftShift, rightShift, leftAlt, rightAlt, leftMeta, rightMeta, repeat;
        private SceneNativeTextInput nativeText;
        private RepeatEvents repeatEvents;
        private char pendingHigh;
        private club.heiqi.uilib.ui.scene.node.SceneNode pendingTextOwner;
        private final club.heiqi.uilib.ui.scene.input.SceneEditEvent.Clipboard clipboard =
                new club.heiqi.uilib.ui.scene.input.SceneEditEvent.Clipboard() {
                    public String read() {
                        return GuiScreen.getClipboardString();
                    }

                    public void write(String value) {
                        GuiScreen.setClipboardString(value);
                    }
                };
        private SceneEscapeInputAdapter escapeInput;
        private club.heiqi.uilib.ui.scene.host.SceneKeyPressInputAdapter enterInput,
                keypadEnterInput,
                spaceInput;

        /** 宿主记录原生输入可用性；目标、挂载代际和按钮权威归核心。 */
        private boolean pointerWindowActive;

        private boolean pointerSuspended = true;

        private void initialize() {
            repeatEvents = new RepeatEvents();
            if (SceneNativeTextInput.available()) nativeText = new SceneNativeTextInput();
            editInput = new club.heiqi.uilib.ui.scene.host.SceneEditInputAdapter();
            keyInput = new SceneKeyInputAdapter(event -> {
                if (event.key == SceneKey.ESCAPE && event.phase == SceneKeyEvent.Phase.DOWN)
                    opened.key(event, () -> {
                        // 延迟关闭核验递交事件的页面与会话身份。
                        if (session == opened && mc.currentScreen == MinecraftSceneScreen.this) fallbackEscape();
                    });
                else opened.key(event);
            });
            tabInput = new SceneTabInputAdapter(direction -> keyInput.key(new SceneKeyEvent(SceneKey.TAB,
                    primary(),
                    direction == club.heiqi.uilib.ui.scene.input.SceneNavigationEvent.PREVIOUS,
                    leftAlt || rightAlt, extra(), repeat, clipboard), true));
            // 主Enter和小键盘Enter保留各自的物理按压段。
            enterInput = new club.heiqi.uilib.ui.scene.host.SceneKeyPressInputAdapter(
                    () -> keyInput.key(event(SceneKey.ENTER), true));
            keypadEnterInput = new club.heiqi.uilib.ui.scene.host.SceneKeyPressInputAdapter(
                    () -> keyInput.key(event(SceneKey.KEYPAD_ENTER), true));
            spaceInput = new club.heiqi.uilib.ui.scene.host.SceneKeyPressInputAdapter(() -> {
                keyInput.key(event(SceneKey.SPACE), true);
                // 无原生文本桥时保留空格文本来源；激活的DOWN/UP仍由Core解释。
                if (nativeText == null) opened.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.text(" "));
            });
            escapeInput = new SceneEscapeInputAdapter(() -> keyInput.key(event(SceneKey.ESCAPE), true));
            if (nativeText != null) inputAvailability(Display.isActive());
        }

        private boolean primary() { return inputPolicy.primary(leftCtrl || rightCtrl, leftMeta || rightMeta); }
        private boolean extra() { return inputPolicy.extra(leftCtrl || rightCtrl, leftMeta || rightMeta); }

        private SceneKeyEvent event(SceneKey key) {
            return new SceneKeyEvent(key, primary(), leftShift || rightShift,
                    leftAlt || rightAlt, extra(), repeat, clipboard);
        }

        /** 同一消费者耗尽历史之后，才确认物理全释放并恢复输入。 */
        void historyDrained() {
            // 原生事件可能同步关页，继续采集前核验会话身份。
            if (inputs != this || session != opened) return;
            syncInputAvailability();
            if (nativeText != null)
                for (SceneNativeTextInput.NativeEvent event : nativeText.drain()) {
                    if (inputs != this || session != opened) return;
                    if (event.focused != null) inputAvailability(event.focused);
                    else if (event.pointer != null) routePointer(event.pointer);
                    else if (event.edit != null) {
                        if (pointerWindowActive && editInput.available()) opened.edit(event.edit);
                    } else routeKey(event.key, event.down, event.repeat, (char) 0);
                }
            if (pointerSuspended && pointerWindowActive && allNativeButtonsReleased()) {
                opened.pointer(ScenePointerEvent.allButtonsReleased());
                pointerSuspended = false;
            }
            if (Keyboard.isCreated()) {
                tabInput.historyDrained(
                        Keyboard.isKeyDown(Keyboard.KEY_TAB),
                        Keyboard.isKeyDown(Keyboard.KEY_LSHIFT),
                        Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
                escapeInput.historyDrained(Keyboard.isKeyDown(Keyboard.KEY_ESCAPE));
                enterInput.historyDrained(Keyboard.isKeyDown(Keyboard.KEY_RETURN));
                keypadEnterInput.historyDrained(Keyboard.isKeyDown(Keyboard.KEY_NUMPADENTER));
                spaceInput.historyDrained(Keyboard.isKeyDown(Keyboard.KEY_SPACE));
                // 历史耗尽后，以物理快照建立下一段修饰键起点。
                leftCtrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL);
                rightCtrl = Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
                leftShift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
                rightShift = Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                leftAlt = Keyboard.isKeyDown(Keyboard.KEY_LMENU);
                rightAlt = Keyboard.isKeyDown(Keyboard.KEY_RMENU);
                leftMeta = Keyboard.isKeyDown(Keyboard.KEY_LMETA);
                rightMeta = Keyboard.isKeyDown(Keyboard.KEY_RMETA);
                java.util.Set<Integer> physical = new java.util.HashSet<>();
                java.util.Set<SceneKey> named = java.util.EnumSet.noneOf(SceneKey.class);
                for (int key = 0; key < Keyboard.KEYBOARD_SIZE; key++)
                    if (Keyboard.isKeyDown(key)) {
                        physical.add(key);
                        SceneKey value = MinecraftSceneScreen.semantic(key);
                        if (value != null) named.add(value);
                    }
                editInput.historyDrained(physical);
                keyInput.historyDrained(named);
            }
        }

        /** 翻译当前游标；增强路径的兼容历史仅耗尽，正式事件来自同一 SDL 消费序。 */
        void handleKeyboardInput() {
            if (inputs != this || session != opened) return;
            syncInputAvailability();
            if (nativeText != null) return; // 增强路径从 native 序列递交正式事件，此处耗尽兼容历史。
            routeKey(
                    Keyboard.getEventKey(),
                    Keyboard.getEventKeyState(),
                    Keyboard.isRepeatEvent(),
                    Keyboard.getEventCharacter());
        }

        private void routeKey(int key, boolean down, boolean repeat, char character) {
            if (inputs != this || session != opened) return;
            // WindowsKeyboard.handleChar 可递交 key=NONE/state=false 的独立WM_CHAR（包括中文提交）。
            // 独立字符按窗口与旧持键文本资格处理；物理按键集合只记录键事件。
            boolean accepted =
                    key == Keyboard.KEY_NONE && character >= 32
                            ? editInput.available()
                            : editInput.key(key, down, repeat);
            this.repeat = repeat;
            if (key == Keyboard.KEY_LCONTROL) leftCtrl = down;
            else if (key == Keyboard.KEY_RCONTROL) rightCtrl = down;
            else if (key == Keyboard.KEY_LMENU) leftAlt = down;
            else if (key == Keyboard.KEY_RMENU) rightAlt = down;
            else if (key == Keyboard.KEY_LMETA) leftMeta = down;
            else if (key == Keyboard.KEY_RMETA) rightMeta = down;
            SceneKey named = MinecraftSceneScreen.semantic(key);
            // 每个已知释放都回到接收DOWN的同一适配器；合法按压与取消归Core。
            if (!down && named != null) keyInput.key(event(named), false);
            if (key == Keyboard.KEY_LSHIFT) {
                leftShift = down;
                tabInput.shift(true, down);
            } else if (key == Keyboard.KEY_RSHIFT) {
                rightShift = down;
                tabInput.shift(false, down);
            } else if (key == Keyboard.KEY_TAB) {
                pendingHigh = 0;
                tabInput.tab(down, repeat);
                return;
            } else if (key == Keyboard.KEY_RETURN) {
                pendingHigh = 0;
                enterInput.key(down, repeat);
                return;
            } else if (key == Keyboard.KEY_NUMPADENTER) {
                pendingHigh = 0;
                keypadEnterInput.key(down, repeat);
                return;
            } else if (key == Keyboard.KEY_SPACE) {
                pendingHigh = 0;
                if (nativeText == null && down && repeat && accepted)
                    opened.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.text(" "));
                else spaceInput.key(down, repeat);
                return;
            } else if (key == Keyboard.KEY_ESCAPE) {
                pendingHigh = 0;
                escapeInput.key(down, repeat);
                return;
            }
            if (named != null && down && accepted) {
                if ((!named.letter() && character < 32) || primary() || extra()) pendingHigh = 0;
                keyInput.key(event(named), down);
            }
            if (!accepted) return;
            final club.heiqi.uilib.ui.scene.node.SceneNode[] current = {null};
            opened.runtime()
                    .domain()
                    .execute(() -> current[0] = opened.runtime().focusRouter().focused().get());
            if (pendingTextOwner != current[0]) {
                pendingHigh = 0;
                pendingTextOwner = current[0];
            }
            if (character >= 32 && !primary()) {
                if (Character.isHighSurrogate(character)) {
                    pendingHigh = character;
                    return;
                }
                // 丢弃孤立低代理，高代理与同一焦点的后续低代理组成完整 UTF-16。
                if (Character.isLowSurrogate(character) && pendingHigh == 0) return;
                String text =
                        Character.isLowSurrogate(character) && pendingHigh != 0
                                ? new String(new char[] {pendingHigh, character})
                                : String.valueOf(character);
                pendingHigh = 0;
                opened.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.text(text));
            }
        }

        /** 翻译 LWJGL2 当前事件；增强路径改由统一 native 序列递交鼠标事实。 */
        void handleMouseInput() {
            if (inputs != this || session != opened || nativeText != null) return;
            syncInputAvailability();
            pendingHigh = 0; // 指针意图可能改变焦点，先结束上一目标的半代理缓存。
            int button = Mouse.getEventButton();
            boolean down = button >= 0 && Mouse.getEventButtonState();
            int x = Mouse.getEventX();
            int y =
                    club.heiqi.uilib.host.client.font.FramebufferCoordinates.mouseY(
                            Display.getHeight(), Mouse.getEventY());
            // LWJGL2 的原生一格为 120；先转换标准格，再由共享策略保留多格与不足一格的余量。
            int wheel = scroll.add(Mouse.getEventDWheel() / 120.0);
            if (button >= 0) routePointer(down ? ScenePointerEvent.down(button, x, y)
                    : ScenePointerEvent.up(button, x, y));
            else routePointer(ScenePointerEvent.move(x, y));
            if (wheel != 0) routePointer(ScenePointerEvent.scroll(x, y, wheel));
        }

        private void routePointer(ScenePointerEvent event) {
            pendingHigh = 0;
            // 开屏/失焦期间只递交旧段 UP，完整释放确认仍在同一历史耗尽后。
            if (!pointerSuspended || event.kind == ScenePointerEvent.Kind.UP) opened.pointer(event);
        }

        /** 失焦取消手势；重获焦点后等待历史耗尽与物理全释放，再接受新按压。 */
        private void syncInputAvailability() {
            if (nativeText == null) inputAvailability(Display.isActive());
        }

        private void inputAvailability(boolean active) {
            if (nativeText != null) nativeText.availability(active);
            if (editInput != null) editInput.availability(active);
            if (keyInput != null) keyInput.availability(active);
            if (tabInput != null) tabInput.availability(active);
            if (escapeInput != null) escapeInput.availability(active);
            if (enterInput != null) enterInput.availability(active);
            if (keypadEnterInput != null) keypadEnterInput.availability(active);
            if (spaceInput != null) spaceInput.availability(active);
            if (!active) {
                if (pointerWindowActive) {
                    // 仅递交原生失焦事实；指针/焦点/组字沿核心同一有序入口结束。
                    opened.focusLost();
                }
                pointerSuspended = true;
                pendingHigh = 0;
                leftCtrl = false;
                rightCtrl = false;
                leftShift = false;
                rightShift = false;
                leftAlt = false;
                rightAlt = false;
                leftMeta = false;
                rightMeta = false;
            }
            pointerWindowActive = active;
        }

        private void syncNativeTextFocus() {
            if (nativeText == null || inputs != this || session != opened) return;
            domain.execute(
                    () -> {
                        if (inputs != this || session != opened || nativeText == null) return;
                        club.heiqi.uilib.ui.scene.node.SceneNode node =
                                opened.runtime().focusRouter().focused().get();
                        // Core 协调目标与组字失效；SDL 来源在可编辑字段切换间保持原联系。
                        boolean requested =
                                node != null
                                        && opened.runtime().interactive(node)
                                        && node.control() != null
                                        && node.control().acceptsTextInput(node);
                        nativeText.textInput(requested);
                    });
        }

        /** handleInput 耗尽历史后查询；Mouse 已创建且所有按钮释放时返回 true。 */
        private boolean allNativeButtonsReleased() {
            if (!Mouse.isCreated()) return false;
            for (int button = 0; button < Mouse.getButtonCount(); button++)
                if (Mouse.isButtonDown(button)) return false;
            return true;
        }


        @Override public void close() {
            SceneNativeTextInput closing = nativeText;
            RepeatEvents repeatClosing = repeatEvents;
            nativeText = null;
            repeatEvents = null;
            pendingHigh = 0;
            pendingTextOwner = null;
            scroll.reset();
            // repeat 与 native 联系同属本次宿主输入，必须在可重入页面释放回调前同步退休。
            // 先恢复 repeat，再调用可能同步重入开页的 native close。
            Throwable failure = null;
            try { if (repeatClosing != null) repeatClosing.close(); }
            catch (RuntimeException | Error next) { failure = next; }
            try { if (closing != null) closing.close(); }
            catch (RuntimeException | Error next) {
                if (failure == null) failure = next; else if (failure != next) failure.addSuppressed(next);
            }
            if (failure instanceof Error) throw (Error) failure;
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        }
    }

    /** 原生键码只在宿主边界命名；编辑与应用命令的解释由Core完成。 */
    private static SceneKey semantic(int key) {
        switch (key) {
            case Keyboard.KEY_TAB: return SceneKey.TAB;
            case Keyboard.KEY_RETURN: return SceneKey.ENTER;
            case Keyboard.KEY_NUMPADENTER: return SceneKey.KEYPAD_ENTER;
            case Keyboard.KEY_SPACE: return SceneKey.SPACE;
            case Keyboard.KEY_ESCAPE: return SceneKey.ESCAPE;
            case Keyboard.KEY_LEFT: return SceneKey.LEFT;
            case Keyboard.KEY_RIGHT: return SceneKey.RIGHT;
            case Keyboard.KEY_UP: return SceneKey.UP;
            case Keyboard.KEY_DOWN: return SceneKey.DOWN;
            case Keyboard.KEY_HOME: return SceneKey.HOME;
            case Keyboard.KEY_END: return SceneKey.END;
            case Keyboard.KEY_PRIOR: return SceneKey.PAGE_UP;
            case Keyboard.KEY_NEXT: return SceneKey.PAGE_DOWN;
            case Keyboard.KEY_BACK: return SceneKey.BACKSPACE;
            case Keyboard.KEY_DELETE: return SceneKey.DELETE;
            case Keyboard.KEY_A: return SceneKey.A;
            case Keyboard.KEY_B: return SceneKey.B;
            case Keyboard.KEY_C: return SceneKey.C;
            case Keyboard.KEY_D: return SceneKey.D;
            case Keyboard.KEY_E: return SceneKey.E;
            case Keyboard.KEY_F: return SceneKey.F;
            case Keyboard.KEY_G: return SceneKey.G;
            case Keyboard.KEY_H: return SceneKey.H;
            case Keyboard.KEY_I: return SceneKey.I;
            case Keyboard.KEY_J: return SceneKey.J;
            case Keyboard.KEY_K: return SceneKey.K;
            case Keyboard.KEY_L: return SceneKey.L;
            case Keyboard.KEY_M: return SceneKey.M;
            case Keyboard.KEY_N: return SceneKey.N;
            case Keyboard.KEY_O: return SceneKey.O;
            case Keyboard.KEY_P: return SceneKey.P;
            case Keyboard.KEY_Q: return SceneKey.Q;
            case Keyboard.KEY_R: return SceneKey.R;
            case Keyboard.KEY_S: return SceneKey.S;
            case Keyboard.KEY_T: return SceneKey.T;
            case Keyboard.KEY_U: return SceneKey.U;
            case Keyboard.KEY_V: return SceneKey.V;
            case Keyboard.KEY_W: return SceneKey.W;
            case Keyboard.KEY_X: return SceneKey.X;
            case Keyboard.KEY_Y: return SceneKey.Y;
            case Keyboard.KEY_Z: return SceneKey.Z;
            case Keyboard.KEY_F1: return SceneKey.F1;
            case Keyboard.KEY_F2: return SceneKey.F2;
            case Keyboard.KEY_F3: return SceneKey.F3;
            case Keyboard.KEY_F4: return SceneKey.F4;
            case Keyboard.KEY_F5: return SceneKey.F5;
            case Keyboard.KEY_F6: return SceneKey.F6;
            case Keyboard.KEY_F7: return SceneKey.F7;
            case Keyboard.KEY_F8: return SceneKey.F8;
            case Keyboard.KEY_F9: return SceneKey.F9;
            case Keyboard.KEY_F10: return SceneKey.F10;
            case Keyboard.KEY_F11: return SceneKey.F11;
            case Keyboard.KEY_F12: return SceneKey.F12;
            case Keyboard.KEY_0: return SceneKey.DIGIT_0;
            case Keyboard.KEY_1: return SceneKey.DIGIT_1;
            case Keyboard.KEY_2: return SceneKey.DIGIT_2;
            case Keyboard.KEY_3: return SceneKey.DIGIT_3;
            case Keyboard.KEY_4: return SceneKey.DIGIT_4;
            case Keyboard.KEY_5: return SceneKey.DIGIT_5;
            case Keyboard.KEY_6: return SceneKey.DIGIT_6;
            case Keyboard.KEY_7: return SceneKey.DIGIT_7;
            case Keyboard.KEY_8: return SceneKey.DIGIT_8;
            case Keyboard.KEY_9: return SceneKey.DIGIT_9;
            case Keyboard.KEY_SLASH: return SceneKey.SLASH;
            case Keyboard.KEY_COMMA: return SceneKey.COMMA;
            case Keyboard.KEY_PERIOD: return SceneKey.PERIOD;
            case Keyboard.KEY_MINUS: return SceneKey.MINUS;
            case Keyboard.KEY_EQUALS: return SceneKey.EQUAL;
            case Keyboard.KEY_SEMICOLON: return SceneKey.SEMICOLON;
            case Keyboard.KEY_APOSTROPHE: return SceneKey.APOSTROPHE;
            case Keyboard.KEY_LBRACKET: return SceneKey.LEFT_BRACKET;
            case Keyboard.KEY_RBRACKET: return SceneKey.RIGHT_BRACKET;
            case Keyboard.KEY_BACKSLASH: return SceneKey.BACKSLASH;
            case Keyboard.KEY_GRAVE: return SceneKey.GRAVE;
            case Keyboard.KEY_INSERT: return SceneKey.INSERT;
            default: return null;
        }
    }

    /** LWJGL2.next 在关闭repeat时直接跳过重复事件；页面借用该设置，宿主输入结束恢复原值。
     * 编辑与既有导航可接收repeat，按钮/Enter/ESC仍由既有按压门消费一次。 */
    private static final class RepeatEvents implements AutoCloseable {
        private final boolean previous = Keyboard.areRepeatEventsEnabled();
        private boolean closed;

        RepeatEvents() {
            Keyboard.enableRepeatEvents(true);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                Keyboard.enableRepeatEvents(previous);
            }
        }
    }

    private void closeOpening(ScenePageSession opened, Inputs closing) {
        // 按身份断开本次接入，保留同 screen 后继代的会话与输入。
        if (session == opened) session = null;
        if (inputs == closing) inputs = null;
        new MinecraftScreenResources(opened, closing).close();
    }

    @Override
    public void onGuiClosed() {
        // 关屏同步退休输入；session的前置退休回调复用相同幂等边界。
        closeOpening(session, inputs);
    }
}
