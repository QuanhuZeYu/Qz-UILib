package club.heiqi.uilib.internal.devtools;

import java.util.ArrayList;
import java.util.List;
import club.heiqi.uilib.ui.host.minecraft.MinecraftSceneScreen;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** MC 验收入口 /qzcore；只声明业务树，运行与宿主接线由正式入口管理。 */
public final class CoreLoopScreen extends MinecraftSceneScreen {
    public CoreLoopScreen() { super(CoreLoopScreen::buildPage); }

    private static void buildPage(ScenePageContext ctx) {
        ReactiveDomain domain = ctx.domain();
        SceneRuntime runtime = ctx.runtime();
        Owner business = ctx.scope();
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
        ctx.mount(panel);
        focusM.bind(NodeBinding.value(runtime.focusRouter().focused(), (node,target) -> node.background(target == node ? 0xFF287848 : 0xFF405060)));
        focusN.bind(NodeBinding.value(runtime.focusRouter().focused(), (node,target) -> node.background(target == node ? 0xFF287848 : 0xFF405060)));
        SceneNode inner = new SceneNode().size(Size.fill(1), Size.natural()).padding(6).gap(3).background(0xFF465060).modal(true).initialFocus(focusN).onEscape(() -> { innerModal.set(false); return true; })
            .bind(NodeBinding.value(innerModal, SceneNode::visible))
            .add(label(focusStatus, style)).add(focusN)
            .add(button("返回外层M", style, () -> innerModal.set(false)))
            .add(button("关闭全部", style, () -> { innerModal.set(false); modal.set(false); }));
        SceneNode dialog = new SceneNode().size(Size.fixed(360), Size.natural()).padding(10).gap(5).background(0xFF303848).modal(true).initialFocus(focusM).onEscape(() -> { innerModal.set(false); modal.set(false); return true; })
            .bind(NodeBinding.value(modal, SceneNode::visible))
            .add(label(focusStatus, style)).add(focusM)
            .add(button("内层模态", style, () -> innerModal.set(true)))
            .add(button("启禁A", style, () -> focusAEnabled.set(!focusAEnabled.get())))
            .add(button("关闭", style, () -> { innerModal.set(false); modal.set(false); }))
            .add(inner);
        ctx.mount(dialog);
    }

    private static SceneNode label(club.heiqi.uilib.ui.reactive.ReadableSignal<String> text, club.heiqi.uilib.ui.reactive.ReadableSignal<TextStyle> style) {
        return new SceneNode().bind(NodeBinding.value(text, SceneNode::text)).bind(NodeBinding.value(style, SceneNode::style));
    }
    private static SceneNode button(String text, club.heiqi.uilib.ui.reactive.ReadableSignal<TextStyle> style, Runnable action) {
        return new SceneNode().text(text).size(Size.fill(1), Size.natural()).padding(3).background(0xFF405060)
            .bind(NodeBinding.value(style, SceneNode::style)).onClick(action);
    }
    @Override public boolean doesGuiPauseGame() { return false; }
}
