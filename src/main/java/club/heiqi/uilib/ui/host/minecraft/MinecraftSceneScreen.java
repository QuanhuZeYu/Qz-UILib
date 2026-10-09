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
    private final ReactiveDomain domain = ReactiveDomain.direct();
    private final ScenePage page;
    private ScenePageSession session;
    private SceneTabInputAdapter tabInput;
    private SceneEscapeInputAdapter escapeInput;
    /** 宿主只记录原生输入可用性；目标、挂载代际和物理按钮权威全部归核心。 */
    private boolean pointerWindowActive;
    private boolean pointerSuspended = true;

    public MinecraftSceneScreen(ScenePage page) { this.page = Objects.requireNonNull(page, "page"); }

    @Override public void initGui() {
        if (session != null) return;
        MinecraftSceneHost host = new MinecraftSceneHost();
        final boolean[] owned = { false };
        try {
            session = ScenePageSession.open(domain, host, host, ctx -> {
                ctx.own(host);
                owned[0] = true;
                page.build(ctx);
            });
        } catch (RuntimeException | Error failure) {
            // open 的早期失败也释放尚未转交作用域的本页资源，原始失败保持权威。
            if (!owned[0]) {
                try { host.close(); }
                catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
        final ScenePageSession opened = session;
        tabInput = new SceneTabInputAdapter(opened::navigation);
        escapeInput = new SceneEscapeInputAdapter(() -> opened.escape(() -> {
            super.keyTyped((char) 0, Keyboard.KEY_ESCAPE);
        }));
    }

    /** MC可在没有runTick时绘制；这里只观测失焦并取消，绝不能用物理快照提前放行历史事件。 */
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (session == null) return;
        syncInputAvailability();
        session.frame();
    }
    /** 父类独占Mouse.next/Keyboard.next；本轮历史事件全部消费后，才确认物理全释放并恢复输入。 */
    @Override public void handleInput() {
        super.handleInput();
        // ESC等键盘事件可能已关闭本页，不能访问已释放的session。
        if (session == null) return;
        syncInputAvailability();
        if (pointerSuspended && pointerWindowActive && allNativeButtonsReleased()) {
            session.pointer(ScenePointerEvent.allButtonsReleased());
            pointerSuspended = false;
        }
        if (Keyboard.isCreated()) {
            tabInput.historyDrained(Keyboard.isKeyDown(Keyboard.KEY_TAB),
                Keyboard.isKeyDown(Keyboard.KEY_LSHIFT), Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
            escapeInput.historyDrained(Keyboard.isKeyDown(Keyboard.KEY_ESCAPE));
        }
    }
    /** 父类handleInput是唯一Keyboard.next消费者；按当前历史事件翻译，不读取未来修饰键快照。 */
    @Override public void handleKeyboardInput() {
        if (session == null) return;
        syncInputAvailability();
        int key = Keyboard.getEventKey(); boolean down = Keyboard.getEventKeyState();
        if (key == Keyboard.KEY_LSHIFT) tabInput.shift(true, down);
        else if (key == Keyboard.KEY_RSHIFT) tabInput.shift(false, down);
        else if (key == Keyboard.KEY_TAB) { tabInput.tab(down, Keyboard.isRepeatEvent()); return; }
        else if (key == Keyboard.KEY_ESCAPE) { escapeInput.key(down, Keyboard.isRepeatEvent()); return; }
        super.handleKeyboardInput();
    }
    /** 父类handleInput是唯一Mouse.next消费者；本方法只翻译当前历史事件，不再派发原版鼠标行为。 */
    @Override public void handleMouseInput() {
        if (session == null) return;
        syncInputAvailability();
        int button = Mouse.getEventButton();
        boolean down = button >= 0 && Mouse.getEventButtonState();
        int x = Mouse.getEventX();
        int y = club.heiqi.uilib.host.client.font.FramebufferCoordinates.mouseY(Display.getHeight(), Mouse.getEventY());
        int wheel = Mouse.getEventDWheel();
        if (pointerSuspended) {
            // 失焦/开屏期间不建立新owner，只把旧段的UP交给核心吞尾。
            if (button >= 0 && !down) session.pointer(ScenePointerEvent.up(button, x, y));
            return;
        }
        if (button >= 0) session.pointer(down ? ScenePointerEvent.down(button, x, y) : ScenePointerEvent.up(button, x, y));
        else session.pointer(ScenePointerEvent.move(x, y));
        if (wheel != 0) session.pointer(ScenePointerEvent.scroll(x, y, Integer.signum(wheel)));
    }
    /** 窗口焦点边沿取消手势；重获焦点仍等所有物理键释放，不能借恢复焦点重建旧owner。 */
    private void syncInputAvailability() {
        boolean active = Display.isActive();
        if (tabInput != null) tabInput.availability(active);
        if (escapeInput != null) escapeInput.availability(active);
        if (!active) {
            if (pointerWindowActive) session.pointer(ScenePointerEvent.cancel());
            pointerSuspended = true;
        }
        pointerWindowActive = active;
    }
    /** 只在handleInput历史事件消费完成后调用；未创建Mouse不能被当成物理全释放。 */
    private boolean allNativeButtonsReleased() {
        if (!Mouse.isCreated()) return false;
        for (int button = 0; button < Mouse.getButtonCount(); button++) if (Mouse.isButtonDown(button)) return false;
        return true;
    }
    @Override public void onGuiClosed() {
        ScenePageSession opened = session;
        session = null;
        tabInput = null;
        escapeInput = null;
        pointerWindowActive = false;
        pointerSuspended = true;
        if (opened != null) opened.close();
    }
}
