package club.heiqi.uilib.host.forge112;

import club.heiqi.uilib.host.embedded.EmbeddedSceneHost;
import club.heiqi.uilib.host.embedded.EmbeddedSceneInputs;
import club.heiqi.uilib.host.embedded.EmbeddedSceneInputs.Key;
import club.heiqi.uilib.host.embedded.EmbeddedSceneServices;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/** GuiScreen 仅转换原生事实；内容、输入所有权和呈现均由共享页面环境协调。 */
public class Minecraft112SceneScreen extends GuiScreen {
    private final GuiScreen parent;
    private final ScenePage page;
    private final MediaService media;
    private ScenePageSession session;
    private EmbeddedSceneInputs inputs;
    private boolean repeatEvents, repeatBorrowed;
    private boolean leftControl, rightControl, leftShift, rightShift, leftAlt, rightAlt, leftMeta, rightMeta;
    private double wheelRemainder;

    public Minecraft112SceneScreen(GuiScreen parent, ScenePage page) { this(parent, page, null); }
    public Minecraft112SceneScreen(GuiScreen parent, ScenePage page, MediaService media) {
        this.parent = parent;
        this.page = java.util.Objects.requireNonNull(page, "page");
        this.media = media;
    }

    @Override public void initGui() {
        if (session != null) return;
        EmbeddedSceneServices services = Forge112Client.services();
        EmbeddedSceneHost host = new EmbeddedSceneHost(services, Display::getWidth, Display::getHeight, media);
        boolean[] owned = {false};
        repeatEvents = Keyboard.areRepeatEventsEnabled();
        Keyboard.enableRepeatEvents(true);
        repeatBorrowed = true;
        try {
            session = ScenePageSession.open(services.domain(), host, services.fonts(), context -> {
                context.own(host); owned[0] = true;
                page.build(context);
            });
            ScenePageSession opened = session;
            inputs = new EmbeddedSceneInputs(opened, new SceneEditEvent.Clipboard() {
                public String read() { return GuiScreen.getClipboardString(); }
                public void write(String text) { GuiScreen.setClipboardString(text); }
            }, () -> { if (session == opened && mc.currentScreen == this) requestClose(); });
        } catch (RuntimeException | Error failure) {
            try { onGuiClosed(); } catch (Throwable cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            if (!owned[0]) try { host.close(); } catch (Throwable cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ScenePageSession opened = session;
        if (opened == null || mc.currentScreen != this) return;
        handleInput();
        if (session == opened && mc.currentScreen == this) opened.frame();
    }

    @Override public void handleInput() {
        EmbeddedSceneInputs collecting = inputs;
        if (collecting == null) return;
        collecting.availability(Display.isActive());
        Lwjgl2InputQueue.drain(this::handleMouseInput, this::handleKeyboardInput);
        if (inputs != collecting) return;
        Set<Integer> physical = new HashSet<>();
        Set<Key> special = EnumSet.noneOf(Key.class);
        for (int key = 0; key < Keyboard.KEYBOARD_SIZE; key++) if (Keyboard.isKeyDown(key)) {
            physical.add(key); special.add(semantic(key));
        }
        boolean released = true;
        for (int button = 0; button < Mouse.getButtonCount(); button++) released &= !Mouse.isButtonDown(button);
        collecting.historyDrained(physical, special, released);
        leftControl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL); rightControl = Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        leftShift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT); rightShift = Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        leftAlt = Keyboard.isKeyDown(Keyboard.KEY_LMENU); rightAlt = Keyboard.isKeyDown(Keyboard.KEY_RMENU);
        leftMeta = Keyboard.isKeyDown(Keyboard.KEY_LMETA); rightMeta = Keyboard.isKeyDown(Keyboard.KEY_RMETA);
    }

    @Override public void handleKeyboardInput() {
        if (inputs == null) return;
        int key = Keyboard.getEventKey();
        boolean down = Keyboard.getEventKeyState();
        if (key == Keyboard.KEY_LCONTROL) leftControl = down;
        if (key == Keyboard.KEY_RCONTROL) rightControl = down;
        if (key == Keyboard.KEY_LSHIFT) leftShift = down;
        if (key == Keyboard.KEY_RSHIFT) rightShift = down;
        if (key == Keyboard.KEY_LMENU) leftAlt = down;
        if (key == Keyboard.KEY_RMENU) rightAlt = down;
        if (key == Keyboard.KEY_LMETA) leftMeta = down;
        if (key == Keyboard.KEY_RMETA) rightMeta = down;
        if (key != Keyboard.KEY_NONE)
            inputs.key(key, semantic(key), down, Keyboard.isRepeatEvent(), leftControl || rightControl,
                    leftShift || rightShift, leftAlt || rightAlt, leftMeta || rightMeta);
        if (down || key == Keyboard.KEY_NONE) inputs.character(Keyboard.getEventCharacter());
    }

    @Override public void handleMouseInput() {
        if (inputs == null) return;
        long sampled = Mouse.getEventNanoseconds();
        int x = Mouse.getEventX(), y = Display.getHeight() - Mouse.getEventY() - 1, button = Mouse.getEventButton();
        if (button >= 0) inputs.pointer((Mouse.getEventButtonState() ? ScenePointerEvent.down(button, x, y)
                : ScenePointerEvent.up(button, x, y)).at(sampled));
        else inputs.pointer(ScenePointerEvent.move(x, y).at(sampled));
        wheelRemainder += Mouse.getEventDWheel() / 120.0;
        int wheel = (int) wheelRemainder;
        if (wheel != 0) { wheelRemainder -= wheel; inputs.pointer(ScenePointerEvent.scroll(x, y, wheel).at(sampled)); }
    }

    protected void requestClose() { mc.displayGuiScreen(parent); }
    @Override public void onGuiClosed() {
        ScenePageSession closing = session;
        session = null; inputs = null;
        try { if (closing != null) closing.close(); }
        finally {
            if (repeatBorrowed) { repeatBorrowed = false; Keyboard.enableRepeatEvents(repeatEvents); }
        }
    }
    @Override public boolean doesGuiPauseGame() { return false; }

    private static Key semantic(int key) {
        switch (key) {
            case Keyboard.KEY_TAB: return Key.TAB;
            case Keyboard.KEY_RETURN: return Key.ENTER;
            case Keyboard.KEY_NUMPADENTER: return Key.KEYPAD_ENTER;
            case Keyboard.KEY_SPACE: return Key.SPACE;
            case Keyboard.KEY_ESCAPE: return Key.ESCAPE;
            case Keyboard.KEY_LSHIFT: return Key.LEFT_SHIFT;
            case Keyboard.KEY_RSHIFT: return Key.RIGHT_SHIFT;
            case Keyboard.KEY_LEFT: return Key.LEFT;
            case Keyboard.KEY_RIGHT: return Key.RIGHT;
            case Keyboard.KEY_UP: return Key.UP;
            case Keyboard.KEY_DOWN: return Key.DOWN;
            case Keyboard.KEY_HOME: return Key.HOME;
            case Keyboard.KEY_END: return Key.END;
            case Keyboard.KEY_PRIOR: return Key.PAGE_UP;
            case Keyboard.KEY_NEXT: return Key.PAGE_DOWN;
            case Keyboard.KEY_BACK: return Key.BACKSPACE;
            case Keyboard.KEY_DELETE: return Key.DELETE;
            case Keyboard.KEY_A: return Key.A;
            case Keyboard.KEY_B: return Key.B;
            case Keyboard.KEY_C: return Key.C;
            case Keyboard.KEY_D: return Key.D;
            case Keyboard.KEY_E: return Key.E;
            case Keyboard.KEY_F: return Key.F;
            case Keyboard.KEY_G: return Key.G;
            case Keyboard.KEY_H: return Key.H;
            case Keyboard.KEY_I: return Key.I;
            case Keyboard.KEY_J: return Key.J;
            case Keyboard.KEY_K: return Key.K;
            case Keyboard.KEY_L: return Key.L;
            case Keyboard.KEY_M: return Key.M;
            case Keyboard.KEY_N: return Key.N;
            case Keyboard.KEY_O: return Key.O;
            case Keyboard.KEY_P: return Key.P;
            case Keyboard.KEY_Q: return Key.Q;
            case Keyboard.KEY_R: return Key.R;
            case Keyboard.KEY_S: return Key.S;
            case Keyboard.KEY_T: return Key.T;
            case Keyboard.KEY_U: return Key.U;
            case Keyboard.KEY_V: return Key.V;
            case Keyboard.KEY_W: return Key.W;
            case Keyboard.KEY_X: return Key.X;
            case Keyboard.KEY_Y: return Key.Y;
            case Keyboard.KEY_Z: return Key.Z;
            case Keyboard.KEY_F1: return Key.F1;
            case Keyboard.KEY_F2: return Key.F2;
            case Keyboard.KEY_F3: return Key.F3;
            case Keyboard.KEY_F4: return Key.F4;
            case Keyboard.KEY_F5: return Key.F5;
            case Keyboard.KEY_F6: return Key.F6;
            case Keyboard.KEY_F7: return Key.F7;
            case Keyboard.KEY_F8: return Key.F8;
            case Keyboard.KEY_F9: return Key.F9;
            case Keyboard.KEY_F10: return Key.F10;
            case Keyboard.KEY_F11: return Key.F11;
            case Keyboard.KEY_F12: return Key.F12;
            case Keyboard.KEY_0: return Key.DIGIT_0;
            case Keyboard.KEY_1: return Key.DIGIT_1;
            case Keyboard.KEY_2: return Key.DIGIT_2;
            case Keyboard.KEY_3: return Key.DIGIT_3;
            case Keyboard.KEY_4: return Key.DIGIT_4;
            case Keyboard.KEY_5: return Key.DIGIT_5;
            case Keyboard.KEY_6: return Key.DIGIT_6;
            case Keyboard.KEY_7: return Key.DIGIT_7;
            case Keyboard.KEY_8: return Key.DIGIT_8;
            case Keyboard.KEY_9: return Key.DIGIT_9;
            case Keyboard.KEY_SLASH: return Key.SLASH;
            case Keyboard.KEY_COMMA: return Key.COMMA;
            case Keyboard.KEY_PERIOD: return Key.PERIOD;
            case Keyboard.KEY_MINUS: return Key.MINUS;
            case Keyboard.KEY_EQUALS: return Key.EQUAL;
            case Keyboard.KEY_SEMICOLON: return Key.SEMICOLON;
            case Keyboard.KEY_APOSTROPHE: return Key.APOSTROPHE;
            case Keyboard.KEY_LBRACKET: return Key.LEFT_BRACKET;
            case Keyboard.KEY_RBRACKET: return Key.RIGHT_BRACKET;
            case Keyboard.KEY_BACKSLASH: return Key.BACKSLASH;
            case Keyboard.KEY_GRAVE: return Key.GRAVE;
            case Keyboard.KEY_INSERT: return Key.INSERT;
            default: return Key.OTHER;
        }
    }
}
