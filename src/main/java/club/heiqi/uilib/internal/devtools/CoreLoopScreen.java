package club.heiqi.uilib.internal.devtools;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;
import club.heiqi.uilib.ui.scene.host.SceneTabInputAdapter;
import org.lwjgl.opengl.Display;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.TextStyle;
/** MC 验收入口 /qzcore；业务只写逻辑状态，框架维护列表与布局。 */
public final class CoreLoopScreen extends GuiScreen {
    private final ReactiveDomain domain = ReactiveDomain.direct();
    private SceneRuntime runtime;
    private SceneFramePipeline pipeline;
    private SceneTabInputAdapter tabInput;
    private MinecraftSceneHost host;
    private Owner business;
    /** 宿主只记录原生输入可用性；目标、挂载代际和物理按钮权威全部归核心。 */
    private boolean pointerWindowActive;
    private boolean pointerSuspended = true;
    @Override public void initGui() {
        if (runtime != null) return;
        domain.execute(() -> {
            runtime = new SceneRuntime(domain); business = domain.owner();
            List<Integer> initialRows = new ArrayList<>();
            for (int item = 1; item <= 60; item++) initialRows.add(item);
            Signal<List<Integer>> rows = domain.signal(initialRows);
            Signal<Integer> count = domain.signal(0), fontSize = domain.signal(20);
            Signal<Boolean> expanded = domain.signal(false), modal = domain.signal(false);
            Computed<TextStyle> style = domain.computed(business, () -> new TextStyle("default", fontSize.get(), 0xFFFFFFFF));
            Computed<String> heading = domain.computed(business, () -> (expanded.get() ? "核心闭环：标题加长时自然换行，正文自动获得剩余空间。累计业务操作：" : "核心闭环：") + count.get());
            SceneNode title = label(heading, style).size(Size.fill(1), Size.natural());
            SceneNode controls = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(2);
            controls.add(button("添加", style, () -> { List<Integer> next = new ArrayList<>(rows.get()); next.add(next.isEmpty() ? 1 : next.get(next.size() - 1) + 1); rows.set(next); count.set(count.get() + 1); }));
            controls.add(button("标题", style, () -> expanded.set(!expanded.get())));
            controls.add(button("字号", style, () -> fontSize.set(fontSize.get() == 20 ? 28 : 20)));
            controls.add(button("模态", style, () -> modal.set(true)));
            SceneNode body = new SceneNode().size(Size.fill(1), Size.fill(1)).scrollable(true).gap(2)
                .scrollStep(32)
                .bind(NodeBinding.repeat(rows, value -> value, value -> {
                    return new SceneNode().bind(NodeBinding.value(value, (node, item) -> node.text("条目 " + item + "：滚轮滚动正文，标题与操作区保持位置。")))
                        .bind(NodeBinding.value(style, SceneNode::style)).size(Size.fill(1), Size.natural()).background(0xAA202830);
                }));
            Signal<Boolean> focusVisible = domain.signal(true), focusEnabled = domain.signal(true);
            Signal<Boolean> focusAEnabled = domain.signal(true), innerModal = domain.signal(false), restoreA = domain.signal(false);
            // 明确的焦点操作不改变普通按钮策略；目标与token始终由runtime管理。
            SceneNode focusA = button("选择A", style, () -> { });
            SceneNode focusB = button("选择B", style, () -> { });
            SceneNode focusM = button("选择M", style, () -> { });
            SceneNode focusN = button("选择N", style, () -> { });
            focusM.focusable(true).onClick(() -> runtime.focusRouter().request(runtime.captureMount(focusM)));
            focusN.focusable(true).onClick(() -> runtime.focusRouter().request(runtime.captureMount(focusN)));
            focusA.bind(NodeBinding.value(focusAEnabled, SceneNode::enabled));
            focusA.focusable(true).onClick(() -> runtime.focusRouter().request(runtime.captureMount(focusA)));
            focusB.focusable(true).onClick(() -> runtime.focusRouter().request(runtime.captureMount(focusB)));
            focusA.bind(NodeBinding.value(runtime.focusRouter().focused(), (node,target) -> node.background(target == node ? 0xFF287848 : 0xFF405060)));
            focusB.bind(NodeBinding.value(runtime.focusRouter().focused(), (node,target) -> node.background(target == node ? 0xFF287848 : 0xFF405060)));
            SceneNode focusTargets = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(2)
                .bind(NodeBinding.value(focusVisible, SceneNode::visible)).bind(NodeBinding.value(focusEnabled, SceneNode::enabled))
                .add(focusA).add(focusB);
            Computed<String> focusStatus = domain.computed(business, () -> {
                SceneNode target = runtime.focusRouter().focused().get();
                return "焦点：" + (target == focusA ? "A" : target == focusB ? "B" : target == focusM ? "M" : target == focusN ? "N" : "无")
                    + "；目标" + (focusVisible.get() ? "显示" : "隐藏") + "/" + (focusEnabled.get() ? "启用" : "禁用")
                    + "；A" + (focusAEnabled.get() ? "启用" : "禁用") + "；恢复：" + (restoreA.get() ? "指定A" : "原焦点");
            });
            SceneNode focusActions = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(2)
                .add(button("清除焦点", style, () -> runtime.focusRouter().clear()))
                .add(button("显隐目标", style, () -> focusVisible.set(!focusVisible.get())))
                .add(button("启禁目标", style, () -> focusEnabled.set(!focusEnabled.get())))
                .add(button("恢复策略", style, () -> restoreA.set(!restoreA.get())));
            SceneNode panel = new SceneNode().initialFocus(focusA)
                .bind(NodeBinding.value(restoreA, (node,useA) -> node.restoreFocus(useA ? focusA : null)))
                .size(Size.fill(1), Size.fill(1)).padding(8).gap(4).background(0xE0101820)
                .add(title).add(controls).add(label(focusStatus, style)).add(focusTargets).add(focusActions).add(body);
            runtime.attach(null, panel);
            focusM.bind(NodeBinding.value(runtime.focusRouter().focused(), (node,target) -> node.background(target == node ? 0xFF287848 : 0xFF405060)));
            focusN.bind(NodeBinding.value(runtime.focusRouter().focused(), (node,target) -> node.background(target == node ? 0xFF287848 : 0xFF405060)));
            SceneNode inner = new SceneNode().size(Size.fill(1), Size.natural()).padding(6).gap(3).background(0xFF465060).modal(true).initialFocus(focusN)
                .bind(NodeBinding.value(innerModal, SceneNode::visible))
                .add(label(focusStatus, style)).add(focusN)
                .add(button("返回外层M", style, () -> innerModal.set(false)))
                .add(button("关闭全部", style, () -> { innerModal.set(false); modal.set(false); }));
            SceneNode dialog = new SceneNode().size(Size.fixed(360), Size.natural()).padding(10).gap(5).background(0xFF303848).modal(true).initialFocus(focusM)
                .bind(NodeBinding.value(modal, SceneNode::visible))
                .add(label(focusStatus, style)).add(focusM)
                .add(button("内层模态", style, () -> innerModal.set(true)))
                .add(button("启禁A", style, () -> focusAEnabled.set(!focusAEnabled.get())))
                .add(button("关闭", style, () -> { innerModal.set(false); modal.set(false); }))
                .add(inner);
            runtime.attach(null, dialog);
            host = new MinecraftSceneHost();
            pipeline = new SceneFramePipeline(runtime, host, host);
            tabInput = new SceneTabInputAdapter(pipeline::navigation);
        });
    }
    private SceneNode label(club.heiqi.uilib.ui.reactive.ReadableSignal<String> text, club.heiqi.uilib.ui.reactive.ReadableSignal<TextStyle> style) {
        return new SceneNode().bind(NodeBinding.value(text, SceneNode::text)).bind(NodeBinding.value(style, SceneNode::style));
    }
    private SceneNode button(String text, club.heiqi.uilib.ui.reactive.ReadableSignal<TextStyle> style, Runnable action) {
        return new SceneNode().text(text).size(Size.fill(1), Size.natural()).padding(3).background(0xFF405060)
            .bind(NodeBinding.value(style, SceneNode::style)).onClick(action);
    }
    /** MC可在没有runTick时绘制；这里只观测失焦并取消，绝不能用物理快照提前放行历史事件。 */
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (pipeline == null) return;
        syncInputAvailability();
        pipeline.frame();
    }
    /** 父类独占Mouse.next/Keyboard.next；本轮历史事件全部消费后，才确认物理全释放并恢复输入。 */
    @Override public void handleInput() {
        super.handleInput();
        // ESC等键盘事件可能已关闭本页，不能访问已释放的pipeline。
        if (pipeline == null) return;
        syncInputAvailability();
        if (pointerSuspended && pointerWindowActive && allNativeButtonsReleased()) {
            pipeline.pointer(ScenePointerEvent.allButtonsReleased());
            pointerSuspended = false;
        }
        if (Keyboard.isCreated()) tabInput.historyDrained(Keyboard.isKeyDown(Keyboard.KEY_TAB),
            Keyboard.isKeyDown(Keyboard.KEY_LSHIFT), Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
    }
    /** 父类handleInput是唯一Keyboard.next消费者；按当前历史事件翻译，不读取未来修饰键快照。 */
    @Override public void handleKeyboardInput() {
        if (pipeline == null) return;
        syncInputAvailability();
        int key = Keyboard.getEventKey(); boolean down = Keyboard.getEventKeyState();
        if (key == Keyboard.KEY_LSHIFT) tabInput.shift(true, down);
        else if (key == Keyboard.KEY_RSHIFT) tabInput.shift(false, down);
        else if (key == Keyboard.KEY_TAB) { tabInput.tab(down, Keyboard.isRepeatEvent()); return; }
        super.handleKeyboardInput();
    }
    /** 父类handleInput是唯一Mouse.next消费者；本方法只翻译当前历史事件，不再派发原版鼠标行为。 */
    @Override public void handleMouseInput() {
        if (pipeline == null) return;
        syncInputAvailability();
        int button = Mouse.getEventButton();
        boolean down = button >= 0 && Mouse.getEventButtonState();
        int x = Mouse.getEventX();
        int y = club.heiqi.uilib.host.client.font.FramebufferCoordinates.mouseY(Display.getHeight(), Mouse.getEventY());
        int wheel = Mouse.getEventDWheel();
        if (pointerSuspended) {
            // 失焦/开屏期间不建立新owner，只把旧段的UP交给核心吞尾。
            if (button >= 0 && !down) pipeline.pointer(ScenePointerEvent.up(button, x, y));
            return;
        }
        if (button >= 0) pipeline.pointer(down ? ScenePointerEvent.down(button, x, y) : ScenePointerEvent.up(button, x, y));
        else pipeline.pointer(ScenePointerEvent.move(x, y));
        if (wheel != 0) pipeline.scroll(x, y, Integer.signum(wheel));
    }
    /** 窗口焦点边沿取消手势；重获焦点仍等所有物理键释放，不能借恢复焦点重建旧owner。 */
    private void syncInputAvailability() {
        boolean active = Display.isActive();
        if (tabInput != null) tabInput.availability(active);
        if (!active) {
            if (pointerWindowActive) pipeline.pointer(ScenePointerEvent.cancel());
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
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void onGuiClosed() {
        domain.execute(() -> {
            if (runtime == null) return;
            // 先丢掉逻辑队列/取消owner，不为处理CANCEL而frame，避免关闭时点击及绘制。
            try { pipeline.close(); }
            finally {
                try { runtime.close(); }
                finally {
                    try { business.dispose(); }
                    finally {
                        try { host.close(); }
                        finally { runtime = null; business = null; host = null; pipeline = null; tabInput = null; pointerWindowActive = false; pointerSuspended = true; }
                    }
                }
            }
        });
    }
}
