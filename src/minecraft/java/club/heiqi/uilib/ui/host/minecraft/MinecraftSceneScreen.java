package club.heiqi.uilib.ui.host.minecraft;

import java.util.Objects;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.host.SceneTabInputAdapter;
import club.heiqi.uilib.ui.scene.host.SceneEscapeInputAdapter;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;

/** 正式 MC 页面入口：内容声明与 GuiScreen 生命周期、唯一原生输入消费者分离。
 * 同一次打开的重初始化保留会话，关闭只释放本页资源，不关闭共享字体服务。
 * 游戏是否暂停由页面通过 doesGuiPauseGame 声明，默认沿用 GuiScreen。
 */
public class MinecraftSceneScreen extends GuiScreen {
    private final ReactiveDomain domain;
    private final ScenePage page;
    private ScenePageSession session;
    private Inputs inputs;

    public MinecraftSceneScreen(ScenePage page) {
        this(ReactiveDomain.direct(), page);
    }

    /** 借用外部业务状态所属域，结束页面不结束该域。 */
    public MinecraftSceneScreen(ReactiveDomain domain, ScenePage page) {
        this.domain = Objects.requireNonNull(domain, "domain");
        this.page = Objects.requireNonNull(page, "page");
    }

    @Override
    public void initGui() {
        if (session != null) return;
        MinecraftSceneHost host = new MinecraftSceneHost();
        final boolean[] owned = {false};
        try {
            session =
                    ScenePageSession.open(
                            domain,
                            host,
                            host,
                            ctx -> {
                                ctx.own(host);
                                owned[0] = true;
                                ctx.own(new RepeatEvents());
                                page.build(ctx);
                            });
            inputs = new Inputs(session);
            inputs.initialize();
        } catch (RuntimeException | Error failure) {
            // 已打开会话后的输入桥/适配器初始化失败也释放scope和repeat借用。
            try {
                onGuiClosed();
            } catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            // open 的早期失败也释放尚未转交作用域的本页资源，原始失败保持权威。
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

    /** MC可在没有runTick时绘制；这里只观测失焦并取消，绝不能用物理快照提前放行历史事件。 */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (session == null) return;
        inputs.syncInputAvailability();
        session.frame();
        // 按钮在本次 frame 内换页时旧会话已结束，不能回放旧计划。
        // MC 此次仍调用旧 screen；交给当前 UILib screen 完成本次绘制，避免先 present 游戏世界。
        GuiScreen active = mc.currentScreen;
        if (active != this) {
            if (active instanceof MinecraftSceneScreen) active.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        if (inputs != null) inputs.syncNativeTextFocus();
    }

    @Override
    public void handleInput() {
        super.handleInput();
        if (inputs != null) inputs.historyDrained();
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
        Inputs(ScenePageSession opened) { this.opened = opened; }
        private SceneTabInputAdapter tabInput;
        private club.heiqi.uilib.ui.scene.host.SceneEditInputAdapter editInput;
        private boolean leftCtrl, rightCtrl, leftShift, rightShift;
        private SceneNativeTextInput nativeText;
        private club.heiqi.uilib.ui.scene.node.SceneNode textOwner;
        private boolean textRequested;
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

        /** 宿主只记录原生输入可用性；目标、挂载代际和物理按钮权威全部归核心。 */
        private boolean pointerWindowActive;

        private boolean pointerSuspended = true;

        private void initialize() {
            if (SceneNativeTextInput.available()) nativeText = new SceneNativeTextInput();
            tabInput =
                    new SceneTabInputAdapter(
                            direction ->
                                    opened.editOrNavigate(
                                            club.heiqi.uilib.ui.scene.input.SceneEditEvent.command(
                                                    club.heiqi.uilib.ui.scene.input.SceneEditEvent
                                                            .Command.TAB,
                                                    direction
                                                            == club.heiqi.uilib.ui.scene.input
                                                                    .SceneNavigationEvent.PREVIOUS,
                                                    clipboard),
                                            direction));
            editInput = new club.heiqi.uilib.ui.scene.host.SceneEditInputAdapter();
            // 动作共享，物理按压门仍是两个独立adapter。
            enterInput = new club.heiqi.uilib.ui.scene.host.SceneKeyPressInputAdapter(this::activateEnter);
            keypadEnterInput = new club.heiqi.uilib.ui.scene.host.SceneKeyPressInputAdapter(this::activateEnter);
            spaceInput =
                    new club.heiqi.uilib.ui.scene.host.SceneKeyPressInputAdapter(
                            () ->
                                    opened.editOrNavigate(
                                            club.heiqi.uilib.ui.scene.input.SceneEditEvent.command(
                                                    (nativeText != null
                                                            ? club.heiqi.uilib.ui.scene.input
                                                                    .SceneEditEvent.Command
                                                                    .ACTIVATE_ONLY
                                                            : club.heiqi.uilib.ui.scene.input
                                                                    .SceneEditEvent.Command.SPACE),
                                                    false,
                                                    clipboard),
                                            club.heiqi.uilib.ui.scene.input.SceneNavigationEvent
                                                    .ACTIVATE));
            escapeInput =
                    new SceneEscapeInputAdapter(
                            () ->
                                    opened.escape(
                                            () -> {
                                                // 延迟结果只可关闭递交事件的同一页/会话，不能借旧事件关闭后来打开的页面。
                                                if (session == opened && mc.currentScreen == MinecraftSceneScreen.this)
                                                    fallbackEscape();
                                            }));
        }

        private void activateEnter() {
            opened.editOrNavigate(
                    club.heiqi.uilib.ui.scene.input.SceneEditEvent.command(
                            leftCtrl || rightCtrl
                                    ? club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.SUBMIT
                                    : club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.CONFIRM,
                            leftShift || rightShift, clipboard),
                    club.heiqi.uilib.ui.scene.input.SceneNavigationEvent.ACTIVATE);
        }

        /** 父类独占Mouse.next/Keyboard.next；本轮历史事件全部消费后，才确认物理全释放并恢复输入。 */
        void historyDrained() {
            // ESC等键盘事件可能已关闭本页，不能访问已释放的session。
            if (inputs != this || session != opened) return;
            syncInputAvailability();
            if (nativeText != null)
                for (SceneNativeTextInput.NativeEvent event : nativeText.drain()) {
                    if (event.edit != null) {
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
                // 仅在历史耗尽后建立下一段修饰键起点，不让未来快照影响已经采集的事件。
                leftCtrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL);
                rightCtrl = Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
                leftShift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
                rightShift = Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                java.util.Set<Integer> physical = new java.util.HashSet<>();
                for (int key = 0; key < Keyboard.KEYBOARD_SIZE; key++)
                    if (Keyboard.isKeyDown(key)) physical.add(key);
                editInput.historyDrained(physical);
            }
        }

        /** 父类handleInput是唯一Keyboard.next消费者；按当前历史事件翻译，不读取未来修饰键快照。 */
        void handleKeyboardInput() {
            if (inputs != this || session != opened) return;
            syncInputAvailability();
            if (nativeText != null) return; // 增强路径在同一native序列翻译，旧Keyboard合成字符只消费不再投递。
            routeKey(
                    Keyboard.getEventKey(),
                    Keyboard.getEventKeyState(),
                    Keyboard.isRepeatEvent(),
                    Keyboard.getEventCharacter());
        }

        private void routeKey(int key, boolean down, boolean repeat, char character) {
            if (inputs != this || session != opened) return;
            // WindowsKeyboard.handleChar 可递交 key=NONE/state=false 的独立WM_CHAR（包括中文提交）。
            // 字符不是物理释放；只经过窗口/旧持键文本门，不往按键集合写入虚假的NONE。
            boolean accepted =
                    key == Keyboard.KEY_NONE && character >= 32
                            ? editInput.available()
                            : editInput.key(key, down, repeat);
            if (key == Keyboard.KEY_LCONTROL) leftCtrl = down;
            else if (key == Keyboard.KEY_RCONTROL) rightCtrl = down;
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
                if (nativeText == null && repeat && accepted)
                    opened.edit(
                            club.heiqi.uilib.ui.scene.input.SceneEditEvent.command(
                                    club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.SPACE,
                                    false,
                                    clipboard));
                else spaceInput.key(down, repeat);
                return;
            } else if (key == Keyboard.KEY_ESCAPE) {
                pendingHigh = 0;
                escapeInput.key(down, repeat);
                return;
            }
            if (!accepted) return;
            club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command command = null;
            switch (key) {
                case Keyboard.KEY_LEFT:
                    command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.LEFT;
                    break;
                case Keyboard.KEY_RIGHT:
                    command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.RIGHT;
                    break;
                case Keyboard.KEY_UP:
                    command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.UP;
                    break;
                case Keyboard.KEY_DOWN:
                    command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.DOWN;
                    break;
                case Keyboard.KEY_HOME:
                    command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.HOME;
                    break;
                case Keyboard.KEY_END:
                    command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.END;
                    break;
                case Keyboard.KEY_BACK:
                    command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.BACKSPACE;
                    break;
                case Keyboard.KEY_DELETE:
                    command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.DELETE;
                    break;
                default:
                    break;
            }
            if (leftCtrl || rightCtrl)
                switch (key) {
                case Keyboard.KEY_LEFT: command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.WORD_LEFT; break;
                case Keyboard.KEY_RIGHT: command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.WORD_RIGHT; break;
                case Keyboard.KEY_BACK: command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.WORD_BACKSPACE; break;
                case Keyboard.KEY_DELETE: command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.WORD_DELETE; break;
                    case Keyboard.KEY_HOME:
                        command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.DOCUMENT_HOME;
                        break;
                    case Keyboard.KEY_END:
                        command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.DOCUMENT_END;
                        break;
                    case Keyboard.KEY_A:
                        command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.SELECT_ALL;
                        break;
                    case Keyboard.KEY_C:
                        command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.COPY;
                        break;
                    case Keyboard.KEY_X:
                        command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.CUT;
                        break;
                    case Keyboard.KEY_V:
                        command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.PASTE;
                        break;
                    case Keyboard.KEY_Z:
                        command =
                                (leftShift || rightShift)
                                        ? club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.REDO
                                        : club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.UNDO;
                        break;
                    case Keyboard.KEY_Y:
                        command = club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.REDO;
                        break;
                    default:
                        break;
                }
            if (command != null) {
                pendingHigh = 0;
                opened.edit(
                        club.heiqi.uilib.ui.scene.input.SceneEditEvent.command(
                                command, leftShift || rightShift, clipboard));
                return;
            }
            final club.heiqi.uilib.ui.scene.node.SceneNode[] current = {null};
            opened.runtime()
                    .domain()
                    .execute(() -> current[0] = opened.runtime().focusRouter().focused().get());
            if (pendingTextOwner != current[0]) {
                pendingHigh = 0;
                pendingTextOwner = current[0];
            }
            if (character >= 32 && !(leftCtrl || rightCtrl)) {
                if (Character.isHighSurrogate(character)) {
                    pendingHigh = character;
                    return;
                }
                // 丢弃孤立低代理；高代理等待同一页面焦点的下一个单元，不向字体递交非法UTF-16。
                if (Character.isLowSurrogate(character) && pendingHigh == 0) return;
                String text =
                        Character.isLowSurrogate(character) && pendingHigh != 0
                                ? new String(new char[] {pendingHigh, character})
                                : String.valueOf(character);
                pendingHigh = 0;
                opened.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.text(text));
            }
        }

        /** 父类handleInput是唯一Mouse.next消费者；本方法只翻译当前历史事件，不再派发原版鼠标行为。 */
        void handleMouseInput() {
            if (inputs != this || session != opened) return;
            syncInputAvailability();
            pendingHigh = 0; // 指针焦点意图尚在管线队列中，不能将半代理带到后来的目标。
            int button = Mouse.getEventButton();
            boolean down = button >= 0 && Mouse.getEventButtonState();
            int x = Mouse.getEventX();
            int y =
                    club.heiqi.uilib.host.client.font.FramebufferCoordinates.mouseY(
                            Display.getHeight(), Mouse.getEventY());
            int wheel = Mouse.getEventDWheel();
            if (pointerSuspended) {
                // 失焦/开屏期间不建立新owner，只把旧段的UP交给核心吞尾。
                if (button >= 0 && !down) opened.pointer(ScenePointerEvent.up(button, x, y));
                return;
            }
            if (button >= 0)
                opened.pointer(
                        down
                                ? ScenePointerEvent.down(button, x, y)
                                : ScenePointerEvent.up(button, x, y));
            else opened.pointer(ScenePointerEvent.move(x, y));
            if (wheel != 0) opened.pointer(ScenePointerEvent.scroll(x, y, Integer.signum(wheel)));
        }

        /** 窗口焦点边沿取消手势；重获焦点仍等所有物理键释放，不能借恢复焦点重建旧owner。 */
        private void syncInputAvailability() {
            boolean active = Display.isActive();
            if (nativeText != null) nativeText.availability(active);
            if (editInput != null) editInput.availability(active);
            if (tabInput != null) tabInput.availability(active);
            if (escapeInput != null) escapeInput.availability(active);
            if (enterInput != null) enterInput.availability(active);
            if (keypadEnterInput != null) keypadEnterInput.availability(active);
            if (spaceInput != null) spaceInput.availability(active);
            if (!active) {
                if (pointerWindowActive) {
                    opened.pointer(ScenePointerEvent.cancel());
                    opened.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.composition("", false));
                }
                pointerSuspended = true;
                pendingHigh = 0;
                leftCtrl = false;
                rightCtrl = false;
                leftShift = false;
                rightShift = false;
            }
            pointerWindowActive = active;
        }

        private void syncNativeTextFocus() {
            if (nativeText == null || inputs != this || session != opened) return;
            domain.execute(
                    () -> {
                        club.heiqi.uilib.ui.scene.node.SceneNode node =
                                opened.runtime().focusRouter().focused().get();
                        if (textOwner != node) {
                            nativeText.textInput(false);
                            textOwner = node;
                        }
                        boolean requested =
                                node != null
                                        && opened.runtime().interactive(node)
                                        && node.control() != null
                                        && node.control().acceptsTextInput(node);
                        if (textRequested && !requested)
                            opened.edit(
                                    club.heiqi.uilib.ui.scene.input.SceneEditEvent.composition(
                                            "", false));
                        textRequested = requested;
                        nativeText.textInput(requested);
                    });
        }

        /** 只在handleInput历史事件消费完成后调用；未创建Mouse不能被当成物理全释放。 */
        private boolean allNativeButtonsReleased() {
            if (!Mouse.isCreated()) return false;
            for (int button = 0; button < Mouse.getButtonCount(); button++)
                if (Mouse.isButtonDown(button)) return false;
            return true;
        }


        @Override public void close() {
            SceneNativeTextInput closing = nativeText;
            nativeText = null;
            if (closing != null) closing.close();
        }
    }

    /** LWJGL2.next 在关闭repeat时直接跳过重复事件；页面借用该设置，scope结束恢复原值。
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

    @Override
    public void onGuiClosed() {
        ScenePageSession opened = session;
        Inputs closing = inputs;
        // 先断开旧输入联系，迟到ESC不能关闭新会话；原生桥在session释放后结束。
        session = null;
        inputs = null;
        try {
            if (opened != null) opened.close();
        } finally {
            if (closing != null) closing.close();
        }
    }
}
