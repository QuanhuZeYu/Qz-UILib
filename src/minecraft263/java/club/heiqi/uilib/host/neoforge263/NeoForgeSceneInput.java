package club.heiqi.uilib.host.neoforge263;

import static org.lwjgl.sdl.SDLKeyboard.*;
import static org.lwjgl.sdl.SDLClipboard.*;
import static org.lwjgl.sdl.SDLMouse.*;
import static org.lwjgl.sdl.SDLScancode.*;
import static org.lwjgl.sdl.SDLKeycode.*;
import static org.lwjgl.sdl.SDLVideo.*;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import club.heiqi.uilib.host.embedded.EmbeddedSceneInputs;
import club.heiqi.uilib.host.embedded.EmbeddedSceneInputs.Key;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest;
import java.util.concurrent.CompletableFuture;
import club.heiqi.uilib.ui.scene.input.SceneCompositionSource;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import org.lwjgl.sdl.SDL_Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import org.lwjgl.system.MemoryStack;

/** SDL 的采集事实转换；物理门和业务输入仍由共同 adapter/session 维护。 */
final class NeoForgeSceneInput implements AutoCloseable {
    private final NeoForgeSceneScreen screen;
    private final ScenePageSession page;
    private final EmbeddedSceneInputs inputs;
    private final long window;
    private final Set<Integer> down = new HashSet<>(), imeConfirmKeys = new HashSet<>();
    private SceneNode textOwner;
    private SceneCompositionSource composition;
    private boolean focused, available, composing, cancelledComposition, nativeText;
    private double scrollRemainder;

    NeoForgeSceneInput(NeoForgeSceneScreen screen, ScenePageSession page) {
        this.screen = screen; this.page = page;
        window = Minecraft.getInstance().getWindow().handle();
        inputs = new EmbeddedSceneInputs(page, new SceneEditEvent.Clipboard() {
            @Override public String read() {
                long text = nSDL_GetClipboardText();
                if (text == 0) throw nativeFailure("读取剪贴板");
                try { return org.lwjgl.system.MemoryUtil.memUTF8(text); }
                finally { org.lwjgl.sdl.SDLStdinc.nSDL_free(text); }
            }
            @Override public void write(String text) {
                if (!SDL_SetClipboardText(text)) throw nativeFailure("设置剪贴板");
            }
        }, screen::onClose);
        focused = (SDL_GetWindowFlags(window) & SDL_WINDOW_INPUT_FOCUS) != 0;
        updateAvailability();
    }

    CompletableFuture<SceneSemanticRequest.Result> semantic(SceneSemanticRequest request) {
        return inputs.semantic(request);
    }

    private static IllegalStateException nativeFailure(String action) {
        return new IllegalStateException(action + "失败：" + org.lwjgl.sdl.SDLError.SDL_GetError());
    }
    private void updateAvailability() {
        available = focused && screen.current();
        inputs.availability(available);
    }
    void focus(boolean value) {
        focused = value;
        updateAvailability();
        if (!value) { cancelComposition(); stopNativeText(); }
    }

    /** Post 在 SDL 消费者及客户端任务之后，物理确认不会跨过尚未消费的旧尾部。 */
    void historyDrained() {
        updateAvailability();
        Set<Integer> physical = new HashSet<>();
        Set<Key> special = EnumSet.noneOf(Key.class);
        var state = SDL_GetKeyboardState();
        for (int scan = 0; scan < state.remaining(); scan++) if (state.get(scan) != 0) {
            physical.add(scan); special.add(key(scan));
        }
        down.clear(); down.addAll(physical);
        imeConfirmKeys.retainAll(physical);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int buttons = SDL_GetMouseState(stack.mallocFloat(1), stack.mallocFloat(1));
            inputs.historyDrained(physical, special, buttons == 0);
        }
    }

    void nativeMove(double x, double y) {
        var coordinates = NeoForgeSceneCoordinates.input(Minecraft.getInstance().getWindow());
        inputs.pointer(ScenePointerEvent.move(coordinates.nativeX(x), coordinates.nativeY(y)));
    }
    void button(MouseButtonEvent event, boolean pressed) {
        // 26.3 原样保留 SDL 的 1/2/3；共同核心以 0 为主键，1 为右键。
        int button = switch (event.button()) { case 1 -> 0; case 3 -> 1; case 2 -> 2; default -> event.button() - 1; };
        var coordinates = NeoForgeSceneCoordinates.input(Minecraft.getInstance().getWindow());
        var pointer = pressed ? ScenePointerEvent.down(button, coordinates.guiX(event.x()), coordinates.guiY(event.y()))
                : ScenePointerEvent.up(button, coordinates.guiX(event.x()), coordinates.guiY(event.y()));
        inputs.pointer(pointer);
    }
    void scroll(double x, double y, double delta) {
        scrollRemainder += delta;
        int whole = (int) scrollRemainder;
        if (whole != 0) {
            scrollRemainder -= whole;
            var coordinates = NeoForgeSceneCoordinates.input(Minecraft.getInstance().getWindow());
            inputs.pointer(ScenePointerEvent.scroll(coordinates.guiX(x), coordinates.guiY(y), whole));
        }
    }

    void key(KeyEvent event, boolean pressed) {
        int scan = event.key();
        boolean repeat = pressed && !down.add(scan);
        if (!pressed) down.remove(scan);
        Key semantic = key(scan);
        // shortcutKey保留宿主键盘布局的字母身份；这里不解释复制、撤销等编辑动作。
        if (event.shortcutKey() >= 'a' && event.shortcutKey() <= 'z')
            semantic = Key.valueOf(String.valueOf((char) (event.shortcutKey() - 'a' + 'A')));
        // 旧 source 保留给迟到 commit；只有当前非空预编辑占有新的 IME 确认段。
        // 已占有的按压独立吞到 UP，不随空预编辑或提交结束而转交业务。
        if (imeConfirmKeys.contains(scan)) { if (!pressed) imeConfirmKeys.remove(scan); return; }
        if (pressed && composing && !cancelledComposition && (semantic == Key.ENTER || semantic == Key.KEYPAD_ENTER)) {
            imeConfirmKeys.add(scan); return;
        }
        if (pressed && !repeat && !composing && semantic != Key.ENTER && semantic != Key.KEYPAD_ENTER) {
            composition = null; cancelledComposition = false;
        }
        int extraModifier = InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? SDL_KMOD_CTRL : SDL_KMOD_GUI;
        inputs.key(scan, semantic, pressed, repeat, event.hasControlDownWithQuirk(), event.hasShiftDown(),
                event.hasAltDown(), (event.modifiers() & extraModifier) != 0);
    }

    private static Key key(int scan) {
        return switch (scan) {
            case SDL_SCANCODE_TAB -> Key.TAB;
            case SDL_SCANCODE_RETURN -> Key.ENTER;
            case SDL_SCANCODE_KP_ENTER -> Key.KEYPAD_ENTER;
            case SDL_SCANCODE_SPACE -> Key.SPACE;
            case SDL_SCANCODE_ESCAPE -> Key.ESCAPE;
            case SDL_SCANCODE_LSHIFT -> Key.LEFT_SHIFT;
            case SDL_SCANCODE_RSHIFT -> Key.RIGHT_SHIFT;
            case SDL_SCANCODE_LEFT -> Key.LEFT;
            case SDL_SCANCODE_RIGHT -> Key.RIGHT;
            case SDL_SCANCODE_UP -> Key.UP;
            case SDL_SCANCODE_DOWN -> Key.DOWN;
            case SDL_SCANCODE_HOME -> Key.HOME;
            case SDL_SCANCODE_END -> Key.END;
            case SDL_SCANCODE_PAGEUP -> Key.PAGE_UP;
            case SDL_SCANCODE_PAGEDOWN -> Key.PAGE_DOWN;
            case SDL_SCANCODE_BACKSPACE -> Key.BACKSPACE;
            case SDL_SCANCODE_DELETE -> Key.DELETE;
            case SDL_SCANCODE_A -> Key.A;
            case SDL_SCANCODE_B -> Key.B;
            case SDL_SCANCODE_C -> Key.C;
            case SDL_SCANCODE_D -> Key.D;
            case SDL_SCANCODE_E -> Key.E;
            case SDL_SCANCODE_F -> Key.F;
            case SDL_SCANCODE_G -> Key.G;
            case SDL_SCANCODE_H -> Key.H;
            case SDL_SCANCODE_I -> Key.I;
            case SDL_SCANCODE_J -> Key.J;
            case SDL_SCANCODE_K -> Key.K;
            case SDL_SCANCODE_L -> Key.L;
            case SDL_SCANCODE_M -> Key.M;
            case SDL_SCANCODE_N -> Key.N;
            case SDL_SCANCODE_O -> Key.O;
            case SDL_SCANCODE_P -> Key.P;
            case SDL_SCANCODE_Q -> Key.Q;
            case SDL_SCANCODE_R -> Key.R;
            case SDL_SCANCODE_S -> Key.S;
            case SDL_SCANCODE_T -> Key.T;
            case SDL_SCANCODE_U -> Key.U;
            case SDL_SCANCODE_V -> Key.V;
            case SDL_SCANCODE_W -> Key.W;
            case SDL_SCANCODE_X -> Key.X;
            case SDL_SCANCODE_Y -> Key.Y;
            case SDL_SCANCODE_Z -> Key.Z;
            case SDL_SCANCODE_F1 -> Key.F1;
            case SDL_SCANCODE_F2 -> Key.F2;
            case SDL_SCANCODE_F3 -> Key.F3;
            case SDL_SCANCODE_F4 -> Key.F4;
            case SDL_SCANCODE_F5 -> Key.F5;
            case SDL_SCANCODE_F6 -> Key.F6;
            case SDL_SCANCODE_F7 -> Key.F7;
            case SDL_SCANCODE_F8 -> Key.F8;
            case SDL_SCANCODE_F9 -> Key.F9;
            case SDL_SCANCODE_F10 -> Key.F10;
            case SDL_SCANCODE_F11 -> Key.F11;
            case SDL_SCANCODE_F12 -> Key.F12;
            case SDL_SCANCODE_0 -> Key.DIGIT_0;
            case SDL_SCANCODE_1 -> Key.DIGIT_1;
            case SDL_SCANCODE_2 -> Key.DIGIT_2;
            case SDL_SCANCODE_3 -> Key.DIGIT_3;
            case SDL_SCANCODE_4 -> Key.DIGIT_4;
            case SDL_SCANCODE_5 -> Key.DIGIT_5;
            case SDL_SCANCODE_6 -> Key.DIGIT_6;
            case SDL_SCANCODE_7 -> Key.DIGIT_7;
            case SDL_SCANCODE_8 -> Key.DIGIT_8;
            case SDL_SCANCODE_9 -> Key.DIGIT_9;
            case SDL_SCANCODE_SLASH -> Key.SLASH;
            case SDL_SCANCODE_COMMA -> Key.COMMA;
            case SDL_SCANCODE_PERIOD -> Key.PERIOD;
            case SDL_SCANCODE_MINUS -> Key.MINUS;
            case SDL_SCANCODE_EQUALS -> Key.EQUAL;
            case SDL_SCANCODE_SEMICOLON -> Key.SEMICOLON;
            case SDL_SCANCODE_APOSTROPHE -> Key.APOSTROPHE;
            case SDL_SCANCODE_LEFTBRACKET -> Key.LEFT_BRACKET;
            case SDL_SCANCODE_RIGHTBRACKET -> Key.RIGHT_BRACKET;
            case SDL_SCANCODE_BACKSLASH -> Key.BACKSLASH;
            case SDL_SCANCODE_GRAVE -> Key.GRAVE;
            case SDL_SCANCODE_INSERT -> Key.INSERT;
            default -> Key.OTHER;
        };
    }

    void preedit(PreeditEvent event) {
        if (!available) return;
        String text = event == null ? "" : event.fullText();
        if (cancelledComposition) {
            if (composition != null) page.edit(SceneEditEvent.compositionUpdate(composition, text, !text.isEmpty()));
            return;
        }
        if (!text.isEmpty() && composition == null && inputs.acceptsNativeText()) {
            composition = new SceneCompositionSource(); cancelledComposition = false;
            page.edit(SceneEditEvent.compositionBegin(composition, text));
        } else if (composition != null) {
            page.edit(SceneEditEvent.compositionUpdate(composition, text, !text.isEmpty()));
        }
        composing = !text.isEmpty();
    }
    void committed(String text) {
        if (!available) return;
        if (composition != null) page.edit(SceneEditEvent.compositionCommit(composition, text));
        else if (inputs.acceptsNativeText()) page.edit(SceneEditEvent.committed(text));
        composition = null; composing = false; cancelledComposition = false;
    }
    private void cancelComposition() {
        if (composition != null && !cancelledComposition) {
            page.edit(SceneEditEvent.compositionCancel(composition));
            // 保留旧来源直到其尾部到达；迟到 commit 仍交核心拒绝，不能重投当前焦点。
            cancelledComposition = true;
        }
        composing = false;
    }

    void syncTextInput() {
        SceneNode[] target = {null};
        page.domain().execute(() -> {
            SceneNode focusedNode = page.runtime().focusRouter().focused().get();
            if (focusedNode != null && focusedNode.control() != null && focusedNode.control().acceptsTextInput(focusedNode))
                target[0] = focusedNode;
        });
        if (target[0] != textOwner || !available) {
            cancelComposition(); stopNativeText(); textOwner = target[0];
        }
        if (available && textOwner != null && !nativeText) {
            if (!SDL_StartTextInput(window)) throw nativeFailure("开始 SDL 文本输入");
            nativeText = true;
            if (!SDL_ClearComposition(window)) {
                IllegalStateException failure = nativeFailure("清除旧 SDL 组字");
                try { stopNativeText(); } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
        if (nativeText) updateTextArea();
    }
    private void updateTextArea() {
        LayoutBox[] caret = {null};
        page.domain().execute(() -> {
            var geometry = page.pipeline().current().geometry;
            caret[0] = page.domain().readOnly(() -> textOwner.control().navigationRegion(textOwner, geometry));
        });
        if (caret[0] == null) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LayoutBox box = caret[0];
            var coordinates = screen.frameCoordinates();
            SDL_Rect.Buffer area = SDL_Rect.malloc(1, stack);
            area.get(0).set((int) Math.floor(coordinates.windowX(box.x)), (int) Math.floor(coordinates.windowY(box.y)),
                    Math.max(1, (int) Math.ceil(coordinates.windowX(box.width))),
                    Math.max(1, (int) Math.ceil(coordinates.windowY(box.height))));
            if (!SDL_SetTextInputArea(window, area, 0)) throw nativeFailure("定位 SDL 文本候选区域");
        }
    }
    private void stopNativeText() {
        if (!nativeText) return;
        nativeText = false;
        if (!SDL_StopTextInput(window)) throw nativeFailure("结束 SDL 文本输入");
    }
    @Override public void close() {
        Throwable failure = null;
        try { cancelComposition(); } catch (RuntimeException | Error next) { failure = next; }
        try { stopNativeText(); } catch (RuntimeException | Error next) {
            if (failure == null) failure = next; else if (failure != next) failure.addSuppressed(next);
        }
        textOwner = null;
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
    }
}
