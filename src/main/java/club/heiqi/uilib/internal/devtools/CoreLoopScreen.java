package club.heiqi.uilib.internal.devtools;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
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
    private MinecraftSceneHost host;
    private Owner business;
    private Signal<Integer> scroll;
    @Override public void initGui() {
        if (runtime != null) return;
        domain.execute(() -> {
            runtime = new SceneRuntime(domain); business = domain.owner();
            List<Integer> initialRows = new ArrayList<>();
            for (int item = 1; item <= 60; item++) initialRows.add(item);
            Signal<List<Integer>> rows = domain.signal(initialRows);
            Signal<Integer> count = domain.signal(0), fontSize = domain.signal(9);
            Signal<Boolean> expanded = domain.signal(false), modal = domain.signal(false);
            scroll = domain.signal(0);
            Computed<TextStyle> style = domain.computed(business, () -> new TextStyle("minecraft", fontSize.get(), 0xFFFFFFFF));
            Computed<String> heading = domain.computed(business, () -> (expanded.get() ? "核心闭环：标题加长时自然换行，正文自动获得剩余空间。累计业务操作：" : "核心闭环：") + count.get());
            SceneNode title = label(heading, style).size(Size.fill(1), Size.natural());
            SceneNode controls = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(2);
            controls.add(button("添加", style, () -> { List<Integer> next = new ArrayList<>(rows.get()); next.add(next.isEmpty() ? 1 : next.get(next.size() - 1) + 1); rows.set(next); count.set(count.get() + 1); }));
            controls.add(button("标题", style, () -> expanded.set(!expanded.get())));
            controls.add(button("字号", style, () -> fontSize.set(fontSize.get() == 9 ? 14 : 9)));
            controls.add(button("模态", style, () -> modal.set(true)));
            SceneNode body = new SceneNode().size(Size.fill(1), Size.fill(1)).scrollable(true).gap(2)
                .onScroll(delta -> scroll.set(Math.max(0, scroll.get() - delta * 20)))
                .bind(NodeBinding.value(scroll, SceneNode::scrollOffset))
                .bind(NodeBinding.repeat(rows, value -> value, value -> {
                    return new SceneNode().bind(NodeBinding.value(value, (node, item) -> node.text("条目 " + item + "：滚轮滚动正文，标题与操作区保持位置。")))
                        .bind(NodeBinding.value(style, SceneNode::style)).size(Size.fill(1), Size.natural()).background(0xAA202830);
                }));
            SceneNode panel = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(8).gap(4).background(0xE0101820)
                .add(title).add(controls).add(body);
            runtime.attach(null, panel);
            SceneNode dialog = new SceneNode().size(Size.fixed(180), Size.natural()).padding(10).gap(5).background(0xFF303848).modal(true)
                .bind(NodeBinding.value(modal, SceneNode::visible))
                .add(new SceneNode().text("模态打开：后台按钮应隔离").size(Size.fill(1), Size.natural()).bind(NodeBinding.value(style, SceneNode::style)))
                .add(button("关闭", style, () -> modal.set(false)));
            runtime.attach(null, dialog);
            host = new MinecraftSceneHost(this);
            pipeline = new SceneFramePipeline(runtime, host, host);
        });
    }
    private SceneNode label(club.heiqi.uilib.ui.reactive.ReadableSignal<String> text, club.heiqi.uilib.ui.reactive.ReadableSignal<TextStyle> style) {
        return new SceneNode().bind(NodeBinding.value(text, SceneNode::text)).bind(NodeBinding.value(style, SceneNode::style));
    }
    private SceneNode button(String text, club.heiqi.uilib.ui.reactive.ReadableSignal<TextStyle> style, Runnable action) {
        return new SceneNode().text(text).size(Size.fill(1), Size.natural()).padding(3).background(0xFF405060)
            .bind(NodeBinding.value(style, SceneNode::style)).onClick(action);
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) { drawDefaultBackground(); pipeline.frame(); }
    @Override protected void mouseClicked(int mouseX, int mouseY, int button) { if (button == 0) pipeline.click(mouseX, mouseY); }
    @Override public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int x = Mouse.getEventX() * width / mc.displayWidth;
            int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            pipeline.scroll(x, y, Integer.signum(wheel));
        }
    }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void onGuiClosed() { domain.execute(() -> { runtime.close(); business.dispose(); host.close(); }); }
}
