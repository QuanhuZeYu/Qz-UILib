package club.heiqi.uilib.host.neoforge263;

import java.util.Objects;
import java.util.function.Consumer;
import club.heiqi.uilib.host.embedded.EmbeddedSceneHost;
import club.heiqi.uilib.host.embedded.EmbeddedSceneServices;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;

/** 任意正式 ScenePage 的 26.3 入口；extract 不提交 GL，真实帧后范围推进同一 session。 */
public class NeoForgeSceneScreen extends Screen {
    private final Screen parent;
    private final ScenePage content;
    private final MediaService media;
    private final NeoForgeFramebuffer framebuffer = new NeoForgeFramebuffer();
    private Consumer<Runnable> closeRequest = Runnable::run;
    private ScenePageSession session;
    private NeoForgeSceneInput input;
    private NeoForgeSceneCoordinates coordinates;

    public NeoForgeSceneScreen(ScenePage page) {
        this(Minecraft.getInstance().gui.screen(), page, null);
    }
    public NeoForgeSceneScreen(Screen parent, ScenePage page, MediaService media) {
        super(Component.literal("UILib"));
        this.parent = parent; this.content = Objects.requireNonNull(page, "page"); this.media = media;
    }
    public final NeoForgeSceneScreen closeRequests(Consumer<Runnable> request) {
        this.closeRequest = Objects.requireNonNull(request, "关闭策略"); return this;
    }

    @Override protected void init() {
        coordinates = NeoForgeSceneCoordinates.input(minecraft.getWindow());
        if (session != null) return;
        EmbeddedSceneServices services = NeoForgeUILib.services();
        EmbeddedSceneHost host = new EmbeddedSceneHost(services, () -> coordinates.width(), () -> coordinates.height(), media);
        boolean[] owned = {false};
        try {
            session = ScenePageSession.open(services.domain(), host, services.fonts(), context -> {
                ownPageResources(context);
                context.own(host); owned[0] = true; content.build(context);
            });
            input = new NeoForgeSceneInput(this, session);
        } catch (RuntimeException | Error failure) {
            try { removed(); } catch (Throwable cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            if (!owned[0]) try { host.close(); } catch (Throwable cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** 人工应用可先转交所属资源；scope 逆序释放，renderer 先于其媒体服务结束。 */
    protected void ownPageResources(ScenePageContext context) {}

    /** 提取式 Screen 只提供宿主生命周期；共同计划在实际 GPU 阶段生成并回放。 */
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    final void present() {
        if (session == null || session.ended() || minecraft.gui.screen() != this) return;
        var target = minecraft.gameRenderer.mainRenderTarget();
        // GameRenderer 已按本帧 WindowRenderState 重建目标；不能再与下一次 OS 尺寸作跨代比较。
        coordinates = NeoForgeSceneCoordinates.frame(target, minecraft.getWindow());
        input.historyDrained();
        if (minecraft.gui.overlay() != null) { input.syncTextInput(); return; }
        ScenePageSession opened = session;
        framebuffer.render(target, () -> {
            if (session == opened && minecraft.gui.screen() == this) opened.frame();
        });
        if (session == opened && minecraft.gui.screen() == this) input.syncTextInput();
    }

    final NeoForgeSceneCoordinates frameCoordinates() { return coordinates; }
    final boolean current() { return session != null && !session.ended() && minecraft.gui.screen() == this && minecraft.gui.overlay() == null; }
    public final void nativeFocus(boolean focused) { if (input != null) input.focus(focused); }
    public final void committedText(String text) { if (current()) input.committed(text); }
    public final void nativeMouseMoved(double x, double y) { if (current()) input.nativeMove(x, y); }

    // 原版在帧前把所有 MOVE 合并，且会再调用 mouseDragged；正式 MOVE 已在 native 消费者顺序递交。
    @Override public void mouseMoved(double x, double y) {}
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return true; }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { if (input != null) input.button(event, true); return true; }
    @Override public boolean mouseReleased(MouseButtonEvent event) { if (input != null) input.button(event, false); return true; }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { if (input != null) input.scroll(x, y, vertical); return true; }
    @Override public boolean keyPressed(KeyEvent event) { if (input != null) input.key(event, true); return true; }
    @Override public boolean keyReleased(KeyEvent event) { if (input != null) input.key(event, false); return true; }
    @Override public boolean charTyped(CharacterEvent event) { return true; }
    @Override public boolean preeditUpdated(PreeditEvent event) { if (input != null) input.preedit(event); return true; }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() {
        ScenePageSession opened = session;
        if (opened == null) return;
        opened.domain().execute(() -> closeRequest.accept(() -> {
            if (session == opened && minecraft.gui.screen() == this) minecraft.gui.setScreen(parent);
        }));
    }

    @Override public void removed() {
        ScenePageSession closing = session; session = null;
        NeoForgeSceneInput oldInput = input; input = null;
        Throwable failure = null;
        try { if (oldInput != null) oldInput.close(); } catch (RuntimeException | Error next) { failure = next; }
        try { if (closing != null) closing.close(); } catch (RuntimeException | Error next) {
            if (failure == null) failure = next; else if (failure != next) failure.addSuppressed(next);
        }
        try { framebuffer.close(); } catch (RuntimeException | Error next) {
            if (failure == null) failure = next; else if (failure != next) failure.addSuppressed(next);
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
    }
}
