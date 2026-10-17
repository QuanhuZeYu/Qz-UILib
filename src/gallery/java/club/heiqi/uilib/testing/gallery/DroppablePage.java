package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneDragDrop;
import club.heiqi.uilib.ui.scene.control.SceneDraggable;
import club.heiqi.uilib.ui.scene.control.SceneDroppable;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 接收区案例只声明状态与内容；接收资格和目标判定归公共拖放作用域。 */
public final class DroppablePage {
    private DroppablePage() {}

    public static SceneNode create(ScenePageContext context, ReadableSignal<SceneTheme> theme, Runnable leave) {
        SceneNode root = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(28).gap(20).scrollable(true);
        root.add(GalleryStyle.header(context,theme,"Droppable","拾起卡片，移动到接收区，松开放下。",leave));
        root.add(GalleryStyle.label(context, () -> "参考：dnd-kit / React / Droppable", theme, 13, true));
        root.add(example(context, theme, false));
        root.add(example(context, theme, true));
        root.add(GalleryStyle.label(context, () -> "Tab 聚焦卡片，空格拾起，方向键选择接收区，空格放下；Esc 取消。接收结果由页面状态决定。", theme, 14, true));
        return root;
    }

    private static SceneNode example(ScenePageContext context, ReadableSignal<SceneTheme> theme, boolean multiple) {
        Signal<String> location = context.domain().signal(null);
        Signal<String> result = context.domain().signal("尚未放下");
        Signal<Boolean> accepting = context.domain().signal(true);
        SceneDragDrop<String> scope = SceneDragDrop.create(context, SceneDragDrop.Collision.POINTER, drop -> {
            location.set(drop.target);
            result.set("已放入 " + drop.target);
        });
        SceneNode card = GalleryStyle.card(theme).gap(16);
        card.add(GalleryStyle.label(context, () -> multiple ? "多个接收目标" : "单个接收区", theme, 21, false));
        card.add(GalleryStyle.label(context, () -> multiple ? "A 和 B 接收卡片；C 展示接收规则。放下后卡片保留身份并移动到目标。" : "接收区在有效悬停时高亮，离开或取消便结束反馈。", theme, 14, true));
        SceneNode source = new SceneNode().size(Size.fill(1), Size.fixed(80));
        Map<String, SceneNode> slots = new LinkedHashMap<>();
        SceneNode targets = GalleryStyle.grid(context, theme);
        for (String id : multiple ? new String[]{"A", "B", "C"} : new String[]{"接收区"}) {
            SceneNode slot = new SceneNode().size(Size.fill(1), Size.fixed(118)).padding(14).gap(8);
            slots.put(id, slot);
            SceneNode area = new SceneNode().size(Size.fill(1), Size.natural()).padding(14).gap(8);
            area.add(GalleryStyle.label(context, () -> id, theme, 18, false));
            area.add(GalleryStyle.label(context, () -> Objects.equals(scope.snapshot().get().target, id) ? "松开放入这里" : "C".equals(id) && !accepting.get() ? "当前不接收" : "将卡片拖到这里", theme, 13, true));
            area.add(slot);
            SceneNode surface = GalleryStyle.frame(area, theme);
            surface.bind(NodeBinding.value(() -> Objects.equals(scope.snapshot().get().target, id)
                    ? theme.get().hover : theme.get().panel, SceneNode::background));
            targets.add(SceneDroppable.create(context, scope, id, surface,
                    () -> new SceneDroppable.Options<String>().accepts(key -> !"C".equals(id) || accepting.get())));
        }
        SceneDraggable.State state = SceneDraggable.state(context.domain());
        SceneNode body = GalleryStyle.centeredRow().size(Size.fixed(164), Size.natural()).padding(14).gap(10);
        body.add(GalleryStyle.dragGrip(theme));
        body.add(GalleryStyle.label(context, () -> "拾起卡片", theme, 15, false));
        SceneNode surface = GalleryStyle.frame(body, theme).size(Size.natural(), Size.natural());
        surface.bind(NodeBinding.value(() -> state.snapshot().get().active ? theme.get().hover : theme.get().panel, SceneNode::background));
        SceneNode preview = GalleryStyle.frame(GalleryStyle.label(context, () -> "正在放置", theme, 15, false).padding(16), theme).size(Size.fixed(164), Size.natural());
        SceneNode draggable = SceneDraggable.create(context, surface, state,
                scope.draggable("卡片", new SceneDraggable.Options().overlay(preview)));
        source.add(draggable);
        // 所属变化由正式移动声明落实，输入回调仅更新 location。
        draggable.bind(NodeBinding.move(() -> location.get() == null ? source : slots.get(location.get())));
        card.add(source).add(targets);
        SceneNode actions = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(12);
        actions.add(GalleryStyle.button(context, () -> "重置卡片", theme, () -> { location.set(null); result.set("尚未放下"); }));
        if (multiple) actions.add(GalleryStyle.button(context, () -> accepting.get() ? "关闭 C 接收" : "开启 C 接收", theme, () -> accepting.set(!accepting.get())));
        actions.add(GalleryStyle.label(context, result, theme, 14, false));
        card.add(actions);
        return GalleryStyle.frame(card, theme);
    }
}
