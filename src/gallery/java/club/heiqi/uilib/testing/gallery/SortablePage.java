package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneGrid;
import club.heiqi.uilib.ui.scene.control.SceneSortable;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 排序页只维护权威集合；布局、候选、捕获和节点迁移由公共控件协调。 */
public final class SortablePage {
    private SortablePage() {}
    public static SceneNode create(ScenePageContext context, ReadableSignal<SceneTheme> theme, Runnable leave) {
        SceneNode root = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(28).gap(20).scrollable(true);
        SceneNode header = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(16);
        header.add(GalleryStyle.label(context, () -> "Sortable", theme, 30, false).size(Size.fill(1), Size.natural()));
        header.add(GalleryStyle.button(context, () -> "返回场景概览", theme, leave)); root.add(header);
        root.add(GalleryStyle.label(context, () -> "拖动改变顺序，也可以将条目移入另一个列表。", theme, 16, false));
        root.add(GalleryStyle.label(context, () -> "参考：dnd-kit / React / Sortable", theme, 13, true));
        SceneNode examples = GalleryStyle.grid(context, theme);
        examples.add(example(context, theme, "纵向列表", SceneSortable.Layout.VERTICAL, false, false, false, false));
        examples.add(example(context, theme, "独立把手与子动作", SceneSortable.Layout.VERTICAL, true, false, false, false));
        examples.add(example(context, theme, "不同高度与动态内容", SceneSortable.Layout.VERTICAL, true, true, false, false));
        examples.add(example(context, theme, "禁用条目", SceneSortable.Layout.VERTICAL, true, false, true, false));
        root.add(examples);
        root.add(example(context, theme, "横向与不同宽度", SceneSortable.Layout.HORIZONTAL, true, true, false, false));
        root.add(example(context, theme, "响应网格", SceneSortable.Layout.GRID, true, true, false, false));
        root.add(example(context, theme, "多个列表与空接收区", SceneSortable.Layout.VERTICAL, true, false, false, true));
        root.add(scrolling(context, theme));
        root.add(GalleryStyle.label(context, () -> "Tab 到达条目，空格或 Enter 拾起，方向键选择插入位置，再次确认放下。Esc 取消，取消不会改变顺序。", theme, 14, true));
        return root;
    }

    private static SceneNode example(ScenePageContext context, ReadableSignal<SceneTheme> theme, String title,
            SceneSortable.Layout layout, boolean handles, boolean variable, boolean disabled, boolean multiple) {
        List<SceneSortable.Group<String, Integer>> initial = multiple
                ? Arrays.asList(group("A", 1, 2, 3), group("B", 4, 5, 6), group("C"))
                : Collections.singletonList(group("列表", 1, 2, 3, 4, 5, 6));
        return section(context, theme, title, layout, initial, handles, variable, disabled, false);
    }
    private static SceneNode scrolling(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        List<Integer> entries = new ArrayList<>(); for (int i = 1; i <= 24; i++) entries.add(i);
        return section(context, theme, "列表边缘连续滚动", SceneSortable.Layout.VERTICAL,
                Collections.singletonList(new SceneSortable.Group<>("滚动列表", entries)), true, true, false, true);
    }
    private static SceneSortable.Group<String, Integer> group(String name, Integer... values) {
        return new SceneSortable.Group<>(name, Arrays.asList(values));
    }
    private static SceneNode section(ScenePageContext context, ReadableSignal<SceneTheme> theme, String title,
            SceneSortable.Layout layout, List<SceneSortable.Group<String, Integer>> initial,
            boolean handles, boolean variable, boolean disabled, boolean scrolling) {
        Signal<List<SceneSortable.Group<String, Integer>>> values = context.domain().signal(initial);
        Signal<String> result = context.domain().signal("拖动时只预览，放下后更新顺序。");
        Signal<Boolean> expanded = context.domain().signal(false);
        Signal<Integer> clicks = context.domain().signal(0);
        SceneNode card = GalleryStyle.card(theme).gap(14);
        card.add(GalleryStyle.label(context, () -> title, theme, 21, false));
        card.add(GalleryStyle.label(context, result, theme, 13, true));
        SceneNode board = SceneSortable.create(context, values, item -> item, containerKey -> {
            SceneNode items = layout == SceneSortable.Layout.GRID
                    ? SceneGrid.create(context, () -> new SceneGrid.Policy(120, 10, 10))
                    : new SceneNode().axis(layout == SceneSortable.Layout.HORIZONTAL ? SceneNode.Axis.ROW : SceneNode.Axis.COLUMN);
            items.size(Size.fill(1), scrolling ? Size.fixed(260) : Size.natural().bounds(88, Integer.MAX_VALUE)).gap(10).padding(12);
            if (scrolling || layout == SceneSortable.Layout.HORIZONTAL) items.scrollable(true).clip(true);
            SceneNode column = new SceneNode().size(Size.fill(1), Size.natural()).gap(8);
            column.add(GalleryStyle.label(context, () -> containerKey, theme, 16, false));
            column.add(GalleryStyle.label(context, () -> empty(values.get(), containerKey) ? "拖到这里 · 空列表" : "按住条目或使用键盘排序", theme, 12, true));
            column.add(GalleryStyle.frame(items, theme));
            return new SceneSortable.Container(column, items, layout);
        }, item -> {
            SceneNode body = GalleryStyle.centeredRow().size(layout == SceneSortable.Layout.HORIZONTAL
                    ? Size.fixed(variable ? 112 + item.get() % 3 * 36 : 140) : Size.fill(1), Size.natural()).padding(12).gap(10);
            SceneNode grip = GalleryStyle.dragGrip(theme);
            if (handles) body.add(grip);
            SceneNode text = new SceneNode().size(Size.fill(1), Size.natural()).gap(5);
            text.add(GalleryStyle.label(context, () -> "条目 " + item.get() + (disabled && item.get() == 3 ? " · 已禁用" : ""), theme, 15, false));
            if (variable) {
                SceneNode detail = GalleryStyle.label(context, () -> item.get() % 2 == 0 ? "不同尺寸的内容也按实际位置排序。" : "简短内容", theme, 12, true);
                detail.bind(NodeBinding.value(() -> item.get() % 2 == 0 || expanded.get(), SceneNode::visible)); text.add(detail);
            }
            body.add(text);
            if (handles && !scrolling && layout == SceneSortable.Layout.VERTICAL && !variable)
                body.add(GalleryStyle.button(context, () -> "+", theme, () -> clicks.set(clicks.get() + 1)));
            SceneNode surface = GalleryStyle.frame(body, theme).size(layout == SceneSortable.Layout.HORIZONTAL ? Size.natural() : Size.fill(1), Size.natural());
            surface.bind(NodeBinding.value(() -> !(disabled && item.get() == 3), SceneNode::enabled));
            return handles ? new SceneSortable.Row(surface, grip) : new SceneSortable.Row(surface);
        }, item -> GalleryStyle.frame(GalleryStyle.label(context, () -> "条目 " + item.get() + " · 拖动中", theme, 15, false).padding(14), theme)
                .size(Size.fixed(layout == SceneSortable.Layout.HORIZONTAL ? 150 : 220), Size.natural()), move -> {
            values.set(moved(values.get(), move));
            result.set("条目 " + move.key + " → " + move.containerKey + (move.beforeKey == null ? " 的末尾" : "，条目 " + move.beforeKey + " 之前"));
        });
        board.gap(16); card.add(board);
        SceneNode actions = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(12);
        actions.add(GalleryStyle.button(context, () -> "重置顺序", theme, () -> { values.set(initial); result.set("已重置顺序"); }));
        if (variable && layout == SceneSortable.Layout.VERTICAL)
            actions.add(GalleryStyle.button(context, () -> expanded.get() ? "收起内容" : "展开内容", theme, () -> expanded.set(!expanded.get())));
        if (handles && !variable && !scrolling)
            actions.add(GalleryStyle.label(context, () -> "独立按钮点击：" + clicks.get(), theme, 13, true));
        card.add(actions);
        return GalleryStyle.frame(card, theme);
    }
    private static boolean empty(List<SceneSortable.Group<String, Integer>> groups, String key) {
        for (SceneSortable.Group<String, Integer> group : groups) if (Objects.equals(group.key, key)) return group.items.isEmpty();
        return true;
    }
    private static List<SceneSortable.Group<String, Integer>> moved(List<SceneSortable.Group<String, Integer>> current,
            SceneSortable.Move<Integer, String> move) {
        List<SceneSortable.Group<String, Integer>> next = new ArrayList<>();
        for (SceneSortable.Group<String, Integer> group : current) {
            List<Integer> items = new ArrayList<>(group.items); items.remove(move.key);
            if (Objects.equals(group.key, move.containerKey)) {
                int before = move.beforeKey == null ? items.size() : items.indexOf(move.beforeKey);
                items.add(before < 0 ? items.size() : before, move.key);
            }
            next.add(new SceneSortable.Group<>(group.key, items));
        }
        return Collections.unmodifiableList(next);
    }
}
