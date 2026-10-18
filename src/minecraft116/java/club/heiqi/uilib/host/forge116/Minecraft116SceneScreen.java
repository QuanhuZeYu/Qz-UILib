package club.heiqi.uilib.host.forge116;

import static org.lwjgl.glfw.GLFW.*;
import club.heiqi.uilib.host.embedded.EmbeddedSceneHost;
import club.heiqi.uilib.host.embedded.EmbeddedSceneInputs;
import club.heiqi.uilib.host.embedded.EmbeddedSceneInputs.Key;
import club.heiqi.uilib.host.embedded.EmbeddedSceneServices;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import com.mojang.blaze3d.matrix.MatrixStack;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.StringTextComponent;

/** 只消费 Minecraft 递交的 Screen 事件；不替换游戏 GLFW 回调或创建第二个事件轮询。 */
public class Minecraft116SceneScreen extends Screen {
    private final Screen parent;
    private final ScenePage page;
    private final MediaService media;
    private final Set<Integer> held = new HashSet<>();
    private ScenePageSession session;
    private EmbeddedSceneInputs inputs;
    private double scrollRemainder;

    public Minecraft116SceneScreen(Screen parent, ScenePage page) { this(parent, page, null); }
    public Minecraft116SceneScreen(Screen parent, ScenePage page, MediaService media) {
        super(new StringTextComponent("UILib"));
        this.parent = parent;
        this.page = java.util.Objects.requireNonNull(page, "page");
        this.media = media;
    }
    private long window() { return minecraft.getMainWindow().getHandle(); }
    private int framebufferWidth() { int[] w = {0}, h = {0}; glfwGetFramebufferSize(window(), w, h); return w[0]; }
    private int framebufferHeight() { int[] w = {0}, h = {0}; glfwGetFramebufferSize(window(), w, h); return h[0]; }

    @Override protected void init() {
        if (session != null) return;
        EmbeddedSceneServices services = Forge116Client.services();
        EmbeddedSceneHost host = new EmbeddedSceneHost(services, this::framebufferWidth, this::framebufferHeight, media);
        boolean[] owned = {false};
        try {
            session = ScenePageSession.open(services.domain(), host, services.fonts(), context -> {
                context.own(host); owned[0] = true; page.build(context);
            });
            ScenePageSession opened = session;
            inputs = new EmbeddedSceneInputs(opened, new SceneEditEvent.Clipboard() {
                public String read() { String text = glfwGetClipboardString(window()); return text == null ? "" : text; }
                public void write(String text) { glfwSetClipboardString(window(), text); }
            }, () -> { if (session == opened && minecraft.currentScreen == this) requestClose(); });
        } catch (RuntimeException | Error failure) {
            try { onClose(); } catch (Throwable cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            if (!owned[0]) try { host.close(); } catch (Throwable cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        ScenePageSession opened = session;
        if (opened == null || minecraft.currentScreen != this) return;
        collectAvailability();
        Set<Integer> physical = new HashSet<>();
        for (int key : NATIVE_KEYS) if (glfwGetKey(window(), key) == GLFW_PRESS) physical.add(key);
        Set<Key> special = EnumSet.noneOf(Key.class);
        for (int key : physical) special.add(semantic(key));
        boolean released = true;
        for (int button = 0; button <= GLFW_MOUSE_BUTTON_LAST; button++) released &= glfwGetMouseButton(window(), button) == GLFW_RELEASE;
        inputs.historyDrained(physical, special, released);
        held.retainAll(physical);
        if (session == opened && minecraft.currentScreen == this) opened.frame();
    }
    private void collectAvailability() { if (inputs != null) inputs.availability(glfwGetWindowAttrib(window(), GLFW_FOCUSED) != 0); }
    private int x(double value) { return (int) Math.floor(value * framebufferWidth() / width); }
    private int y(double value) { return (int) Math.floor(value * framebufferHeight() / height); }
    @Override public void mouseMoved(double mouseX, double mouseY) {
        collectAvailability(); if (inputs != null) inputs.pointer(ScenePointerEvent.move(x(mouseX), y(mouseY)));
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        collectAvailability(); if (inputs != null) inputs.pointer(ScenePointerEvent.down(button, x(mouseX), y(mouseY))); return true;
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        collectAvailability(); if (inputs != null) inputs.pointer(ScenePointerEvent.up(button, x(mouseX), y(mouseY))); return true;
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        mouseMoved(mouseX, mouseY); return true;
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        collectAvailability(); scrollRemainder += delta;
        int whole = (int) scrollRemainder;
        if (inputs != null && whole != 0) { scrollRemainder -= whole; inputs.pointer(ScenePointerEvent.scroll(x(mouseX), y(mouseY), whole)); }
        return true;
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        collectAvailability(); boolean repeat = !held.add(key);
        if (inputs != null) inputs.key(key, semantic(key), true, repeat,
                (modifiers & GLFW_MOD_CONTROL) != 0, (modifiers & GLFW_MOD_SHIFT) != 0,
                (modifiers & GLFW_MOD_ALT) != 0, (modifiers & GLFW_MOD_SUPER) != 0);
        return true;
    }
    @Override public boolean keyReleased(int key, int scanCode, int modifiers) {
        collectAvailability(); held.remove(key);
        if (inputs != null) inputs.key(key, semantic(key), false, false,
                (modifiers & GLFW_MOD_CONTROL) != 0, (modifiers & GLFW_MOD_SHIFT) != 0,
                (modifiers & GLFW_MOD_ALT) != 0, (modifiers & GLFW_MOD_SUPER) != 0);
        return true;
    }
    @Override public boolean charTyped(char character, int modifiers) {
        collectAvailability(); if (inputs != null) inputs.character(character); return true;
    }
    protected void requestClose() { minecraft.displayGuiScreen(parent); }
    @Override public void closeScreen() { if (session != null) session.escape(this::requestClose); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        ScenePageSession closing = session; session = null; inputs = null; held.clear();
        if (closing != null) closing.close();
    }
    private static Key semantic(int key) {
        switch (key) {
            case GLFW_KEY_TAB: return Key.TAB;
            case GLFW_KEY_ENTER: return Key.ENTER;
            case GLFW_KEY_KP_ENTER: return Key.KEYPAD_ENTER;
            case GLFW_KEY_SPACE: return Key.SPACE;
            case GLFW_KEY_ESCAPE: return Key.ESCAPE;
            case GLFW_KEY_LEFT_SHIFT: return Key.LEFT_SHIFT;
            case GLFW_KEY_RIGHT_SHIFT: return Key.RIGHT_SHIFT;
            case GLFW_KEY_LEFT: return Key.LEFT;
            case GLFW_KEY_RIGHT: return Key.RIGHT;
            case GLFW_KEY_UP: return Key.UP;
            case GLFW_KEY_DOWN: return Key.DOWN;
            case GLFW_KEY_HOME: return Key.HOME;
            case GLFW_KEY_END: return Key.END;
            case GLFW_KEY_PAGE_UP: return Key.PAGE_UP;
            case GLFW_KEY_PAGE_DOWN: return Key.PAGE_DOWN;
            case GLFW_KEY_BACKSPACE: return Key.BACKSPACE;
            case GLFW_KEY_DELETE: return Key.DELETE;
            case GLFW_KEY_A: return Key.A;
            case GLFW_KEY_B: return Key.B;
            case GLFW_KEY_C: return Key.C;
            case GLFW_KEY_D: return Key.D;
            case GLFW_KEY_E: return Key.E;
            case GLFW_KEY_F: return Key.F;
            case GLFW_KEY_G: return Key.G;
            case GLFW_KEY_H: return Key.H;
            case GLFW_KEY_I: return Key.I;
            case GLFW_KEY_J: return Key.J;
            case GLFW_KEY_K: return Key.K;
            case GLFW_KEY_L: return Key.L;
            case GLFW_KEY_M: return Key.M;
            case GLFW_KEY_N: return Key.N;
            case GLFW_KEY_O: return Key.O;
            case GLFW_KEY_P: return Key.P;
            case GLFW_KEY_Q: return Key.Q;
            case GLFW_KEY_R: return Key.R;
            case GLFW_KEY_S: return Key.S;
            case GLFW_KEY_T: return Key.T;
            case GLFW_KEY_U: return Key.U;
            case GLFW_KEY_V: return Key.V;
            case GLFW_KEY_W: return Key.W;
            case GLFW_KEY_X: return Key.X;
            case GLFW_KEY_Y: return Key.Y;
            case GLFW_KEY_Z: return Key.Z;
            case GLFW_KEY_F1: return Key.F1;
            case GLFW_KEY_F2: return Key.F2;
            case GLFW_KEY_F3: return Key.F3;
            case GLFW_KEY_F4: return Key.F4;
            case GLFW_KEY_F5: return Key.F5;
            case GLFW_KEY_F6: return Key.F6;
            case GLFW_KEY_F7: return Key.F7;
            case GLFW_KEY_F8: return Key.F8;
            case GLFW_KEY_F9: return Key.F9;
            case GLFW_KEY_F10: return Key.F10;
            case GLFW_KEY_F11: return Key.F11;
            case GLFW_KEY_F12: return Key.F12;
            case GLFW_KEY_0: return Key.DIGIT_0;
            case GLFW_KEY_1: return Key.DIGIT_1;
            case GLFW_KEY_2: return Key.DIGIT_2;
            case GLFW_KEY_3: return Key.DIGIT_3;
            case GLFW_KEY_4: return Key.DIGIT_4;
            case GLFW_KEY_5: return Key.DIGIT_5;
            case GLFW_KEY_6: return Key.DIGIT_6;
            case GLFW_KEY_7: return Key.DIGIT_7;
            case GLFW_KEY_8: return Key.DIGIT_8;
            case GLFW_KEY_9: return Key.DIGIT_9;
            case GLFW_KEY_SLASH: return Key.SLASH;
            case GLFW_KEY_COMMA: return Key.COMMA;
            case GLFW_KEY_PERIOD: return Key.PERIOD;
            case GLFW_KEY_MINUS: return Key.MINUS;
            case GLFW_KEY_EQUAL: return Key.EQUAL;
            case GLFW_KEY_SEMICOLON: return Key.SEMICOLON;
            case GLFW_KEY_APOSTROPHE: return Key.APOSTROPHE;
            case GLFW_KEY_LEFT_BRACKET: return Key.LEFT_BRACKET;
            case GLFW_KEY_RIGHT_BRACKET: return Key.RIGHT_BRACKET;
            case GLFW_KEY_BACKSLASH: return Key.BACKSLASH;
            case GLFW_KEY_GRAVE_ACCENT: return Key.GRAVE;
            case GLFW_KEY_INSERT: return Key.INSERT;
            default: return Key.OTHER;
        }
    }
    private static final int[] NATIVE_KEYS = nativeKeys();
    private static int[] nativeKeys() {
        java.util.List<Integer> keys = new java.util.ArrayList<>();
        for (int[] range : new int[][] {{GLFW_KEY_0, GLFW_KEY_9}, {GLFW_KEY_A, GLFW_KEY_Z},
                {GLFW_KEY_F1, GLFW_KEY_F25}, {GLFW_KEY_KP_0, GLFW_KEY_KP_EQUAL},
                {GLFW_KEY_ESCAPE, GLFW_KEY_END}, {GLFW_KEY_CAPS_LOCK, GLFW_KEY_PAUSE},
                {GLFW_KEY_LEFT_SHIFT, GLFW_KEY_MENU}})
            for (int key = range[0]; key <= range[1]; key++) keys.add(key);
        for (int key : new int[] {GLFW_KEY_SPACE, GLFW_KEY_APOSTROPHE, GLFW_KEY_COMMA, GLFW_KEY_MINUS, GLFW_KEY_PERIOD,
                GLFW_KEY_SLASH, GLFW_KEY_SEMICOLON, GLFW_KEY_EQUAL, GLFW_KEY_LEFT_BRACKET, GLFW_KEY_BACKSLASH,
                GLFW_KEY_RIGHT_BRACKET, GLFW_KEY_GRAVE_ACCENT, GLFW_KEY_WORLD_1, GLFW_KEY_WORLD_2}) keys.add(key);
        int[] result = new int[keys.size()]; for (int i = 0; i < result.length; i++) result[i] = keys.get(i); return result;
    }
}
