package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneFlowLayout;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 可复用的本地文件选择组合控件，行为归 specs/content/2026-10-07-uilib-theme-spec.md。
 * 作为可见来源节点的直属孩子声明，正式所属层占用当前视口并隔离后台输入。
 * 一次挂载对应一次选择；确认或取消先结束本次工作并隐藏，再通知调用方。
 * 调用方通常用 ownedChildren 移除已结束控件；卸载不补发取消或提交。
 */
public final class SceneFilePicker {
    private final ScenePageContext context;
    private final ReadableSignal<SceneTheme> theme;
    private final FilePickerSelection selection;

    private SceneFilePicker(ScenePageContext context, ReadableSignal<SceneTheme> theme, FilePickerSelection selection) {
        this.context = context; this.theme = theme; this.selection = selection;
    }
    public static SceneNode create(ScenePageContext context, Path initialDirectory,
            Consumer<Path> onSelected, Runnable onCancelled) {
        return create(context, initialDirectory, context.theme(), onSelected, onCancelled);
    }
    public static SceneNode create(ScenePageContext context, Path initialDirectory,
            ReadableSignal<SceneTheme> theme, Consumer<Path> onSelected, Runnable onCancelled) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(initialDirectory, "initialDirectory");
        Objects.requireNonNull(theme, "theme");
        FilePickerSelection selection = new FilePickerSelection(context.domain(), initialDirectory,
                Objects.requireNonNull(onSelected, "onSelected"), Objects.requireNonNull(onCancelled, "onCancelled"));
        return new SceneFilePicker(context, theme, selection).createNode();
    }
    private SceneNode createNode() {
        SceneNode field = SceneTextField.create(context, selection.path, theme, () -> false,
                selection::go, SceneTextField.SubmitPolicy.CONFIRM, () -> "目录或文件路径");
        SceneNode panel = column().size(Size.fill(1), Size.fill(1)).control(new Panel())
                .bind(NodeBinding.value(theme, (node, value) -> node.padding(value.padding + 8).background(value.panel)))
                .add(SceneLabel.create(context, () -> "选择本地文件", theme))
                .add(navigation()).add(row().add(field.size(Size.fill(1), Size.natural()))
                        .add(button("前往", () -> selection.go(selection.path.request(true))).size(Size.natural(), Size.natural())))
                .add(SceneLabel.create(context, selection.message, theme)).add(listing()).add(pagination())
                .add(row().add(button("取消", selection::cancel)).add(button("选择文件", selection::confirm)
                        .bind(NodeBinding.value(() -> !selection.busy.get(), SceneNode::enabled))));
        SceneNode modal = SceneLayer.create(context, () -> new SceneLayer.Policy(0, SceneLayer.Participation.INTERACTIVE))
                .size(Size.fill(1), Size.fill(1)).padding(12).background(0x99000000).modal(true)
                .initialFocus(field).onEscape(() -> { selection.cancel(); return true; }).add(panel)
                .bind(NodeBinding.value(selection.showing, SceneNode::visible));
        modal.bind((runtime, node, owner) -> {
            selection.attach();
            return new BindingActivation() {
                @Override public void refresh() {}
                @Override public void close() { selection.detach(); }
            };
        });
        return modal;
    }
    private SceneNode navigation() {
        return row().add(button("上一级", selection::up)).add(button("根目录", () -> selection.showRoots(0)))
                .add(button("刷新", selection::refresh));
    }
    private SceneNode listing() {
        SceneNode items = column().size(Size.fill(1), Size.natural());
        items.bind(NodeBinding.repeat(selection.entries, entry -> entry.path, this::entry));
        SceneScrollArea.Area listing = SceneScrollArea.create(context, items, () -> new SceneScrollArea.Options(), theme);
        listing.viewport(SceneNode.Axis.COLUMN).bind(NodeBinding.value(() -> {
            selection.revision.get(); return 0;
        }, SceneNode::scrollOffset));
        return listing.node();
    }
    private SceneNode pagination() {
        SceneNode previous = button("上一页", () -> selection.turn(-1))
                .bind(NodeBinding.value(() -> !selection.busy.get() && selection.page.get() > 0, SceneNode::enabled));
        SceneNode following = button("下一页", () -> selection.turn(1))
                .bind(NodeBinding.value(() -> !selection.busy.get() && selection.next.get(), SceneNode::enabled));
        return row().add(previous).add(SceneLabel.create(context, () -> "第 " + (selection.page.get() + 1) + " 页", theme)
                .size(Size.fill(1), Size.natural())).add(following);
    }
    private SceneNode entry(ReadableSignal<FilePickerDirectory.Entry> item) {
        ReadableSignal<SceneTheme> rowTheme = () -> {
            SceneTheme base = theme.get();
            if (!item.get().path.equals(selection.selected.get())) return base;
            return new SceneTheme(base.textStyle, base.panel, base.pressed, base.hover, base.pressed,
                    base.focus, base.disabled, base.disabledText, base.tooltip, base.tooltipText, base.padding, base.gap);
        };
        return SceneButton.create(context, () -> {
            FilePickerDirectory.Entry value = item.get();
            return (value.directory ? "[目录] " : "") + value.name();
        }, rowTheme, () -> selection.activate(item.get()), SceneTextPolicy.ELLIPSIS_SINGLE_LINE);
    }
    private SceneNode column() {
        return new SceneNode().gap(8).bind(NodeBinding.value(() -> theme.get().gap, SceneNode::gap));
    }
    private SceneNode row() { return column().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()); }
    private SceneNode button(String text, Runnable action) { return SceneButton.create(context, () -> text, theme, action); }

    /** 正式输入帧领取目录结果，普通排列完全复用 SceneFlowLayout。 */
    private final class Panel implements SceneControl {
        @Override public long nextInputNanos(SceneNode node) { return selection.waiting() ? 0 : Long.MAX_VALUE; }
        @Override public void inputFrame(SceneNode node, SceneGeometryView geometry, long now) { selection.receiveCompletion(); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) {
            return new MeasuredSize(0, 0);
        }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text, SceneChildMeasurer children) {
            return SceneFlowLayout.measure(node, body, children);
        }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
            return SceneFlowLayout.arrange(node, box, children);
        }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry != null && (node.background() >>> 24) != 0)
                target.add(PaintCommand.rect(entry.box, entry.clip, node.background()));
        }
    }
}
