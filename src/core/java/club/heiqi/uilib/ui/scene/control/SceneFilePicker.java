package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
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
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 可复用的本地文件选择组合控件，行为归 specs/content/2026-10-07-uilib-theme-spec.md。
 * 作为可见来源节点的直属孩子声明，正式所属层占用当前视口并隔离后台输入。
 * 一次挂载对应一次选择；确认或取消先结束本次工作并隐藏，再通知调用方。
 * 调用方通常用 ownedChildren 移除已结束控件；卸载不补发取消或提交。
 */
public final class SceneFilePicker {
    private SceneFilePicker() {}

    public static SceneNode create(ScenePageContext context, Path initialDirectory,
            Consumer<Path> onSelected, Runnable onCancelled) {
        return create(context, initialDirectory, context.theme(), onSelected, onCancelled);
    }
    public static SceneNode create(ScenePageContext context, Path initialDirectory,
            ReadableSignal<SceneTheme> theme, Consumer<Path> onSelected, Runnable onCancelled) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        return new Selection(context, Objects.requireNonNull(initialDirectory, "initialDirectory"),
                Objects.requireNonNull(theme, "theme"), Objects.requireNonNull(onSelected, "onSelected"),
                Objects.requireNonNull(onCancelled, "onCancelled")).create();
    }

    private static final class Selection {
        private final ScenePageContext context;
        private final ReadableSignal<SceneTheme> theme;
        private final Consumer<Path> onSelected;
        private final Runnable onCancelled;
        private final SceneEditState path;
        private final Signal<List<FilePickerDirectory.Entry>> entries;
        private final Signal<String> message;
        private final Signal<Path> directory, selected;
        private final Signal<Boolean> busy, showing, roots, next;
        private final Signal<Long> page, revision;
        private FilePickerDirectory reader;
        private SceneEditState.Submission pendingPath;
        private long request;
        private boolean attached, finished;

        Selection(ScenePageContext context, Path initialDirectory, ReadableSignal<SceneTheme> theme,
                Consumer<Path> onSelected, Runnable onCancelled) {
            this.context = context; this.theme = theme; this.onSelected = onSelected; this.onCancelled = onCancelled;
            Path initial = initialDirectory.toAbsolutePath().normalize();
            directory = context.domain().signal(initial); selected = context.domain().signal(null);
            path = new SceneEditState(context.domain(), initial.toString());
            entries = context.domain().signal(Collections.emptyList());
            message = context.domain().signal("正在读取目录…");
            busy = context.domain().signal(false); showing = context.domain().signal(true);
            roots = context.domain().signal(false); next = context.domain().signal(false);
            page = context.domain().signal(0L); revision = context.domain().signal(0L);
        }
        SceneNode create() {
            SceneNode field = SceneTextField.create(context, path, theme, () -> false,
                    this::go, SceneTextField.SubmitPolicy.CONFIRM, () -> "目录或文件路径");
            SceneNode navigation = row().add(button("上一级", this::up))
                    .add(button("根目录", () -> showRoots(0))).add(button("刷新", this::refresh));
            SceneNode address = row().add(field.size(Size.fill(1), Size.natural()))
                    .add(button("前往", () -> go(path.request(true))).size(Size.natural(), Size.natural()));
            SceneNode items = column().size(Size.fill(1), Size.natural());
            items.bind(NodeBinding.repeat(entries, entry -> entry.path, item -> entry(item)));
            SceneScrollArea.Area listing = SceneScrollArea.create(context, items, () -> new SceneScrollArea.Options(), theme);
            listing.viewport(SceneNode.Axis.COLUMN).bind(NodeBinding.value(() -> {
                revision.get(); return 0;
            }, SceneNode::scrollOffset));
            SceneNode previous = button("上一页", () -> turn(-1))
                    .bind(NodeBinding.value(() -> !busy.get() && page.get() > 0, SceneNode::enabled));
            SceneNode following = button("下一页", () -> turn(1))
                    .bind(NodeBinding.value(() -> !busy.get() && next.get(), SceneNode::enabled));
            SceneNode pages = row().add(previous)
                    .add(SceneLabel.create(context, () -> "第 " + (page.get() + 1) + " 页", theme)
                            .size(Size.fill(1), Size.natural())).add(following);
            SceneNode choose = button("选择文件", this::confirm)
                    .bind(NodeBinding.value(() -> !busy.get(), SceneNode::enabled));
            SceneNode actions = row().add(button("取消", this::cancel)).add(choose);
            SceneNode panel = column().size(Size.fill(1), Size.fill(1)).control(new Panel())
                    .bind(NodeBinding.value(theme, (node, value) -> node.padding(value.padding + 8).background(value.panel)))
                    .add(SceneLabel.create(context, () -> "选择本地文件", theme))
                    .add(navigation).add(address).add(SceneLabel.create(context, message, theme))
                    .add(listing.node()).add(pages).add(actions);
            SceneNode modal = SceneLayer.create(context, () -> new SceneLayer.Policy(0, SceneLayer.Participation.INTERACTIVE))
                    .size(Size.fill(1), Size.fill(1)).padding(12).background(0x99000000).modal(true)
                    .initialFocus(field).onEscape(() -> { cancel(); return true; }).add(panel)
                    .bind(NodeBinding.value(showing, SceneNode::visible));
            modal.bind((runtime, node, owner) -> {
                attach();
                return new BindingActivation() {
                    @Override public void refresh() {}
                    @Override public void close() { detach(); }
                };
            });
            return modal;
        }
        private SceneNode column() {
            return new SceneNode().gap(8).bind(NodeBinding.value(() -> theme.get().gap, SceneNode::gap));
        }
        private SceneNode row() { return column().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()); }
        private SceneNode button(String text, Runnable action) {
            return SceneButton.create(context, () -> text, theme, action);
        }
        private SceneNode entry(ReadableSignal<FilePickerDirectory.Entry> item) {
            ReadableSignal<SceneTheme> rowTheme = () -> {
                SceneTheme base = theme.get();
                if (!item.get().path.equals(selected.get())) return base;
                return new SceneTheme(base.textStyle, base.panel, base.pressed, base.hover, base.pressed,
                        base.focus, base.disabled, base.disabledText, base.tooltip, base.tooltipText, base.padding, base.gap);
            };
            return SceneButton.create(context, () -> {
                FilePickerDirectory.Entry value = item.get();
                return (value.directory ? "[目录] " : "") + value.name();
            }, rowTheme, () -> {
                FilePickerDirectory.Entry value = item.get();
                if (value.directory) browse(value.path, 0);
                else {
                    selected.set(value.path); path.reset(value.path.toString());
                    message.set("已选择：" + value.name());
                }
            }, SceneTextPolicy.ELLIPSIS_SINGLE_LINE);
        }
        private void attach() {
            attached = true; finished = false; showing.set(true);
            reader = new FilePickerDirectory();
            browse(directory.get(), 0);
        }
        private void detach() {
            attached = false; request++;
            if (reader != null) { reader.close(); reader = null; }
        }
        private boolean active() { return attached && !finished; }
        private long begin(String status) {
            if (pendingPath != null) path.reject(pendingPath, "路径导航已更改");
            pendingPath = null;
            long id = ++request;
            revision.set(id); busy.set(true); next.set(false); selected.set(null);
            entries.set(Collections.emptyList()); message.set(status);
            return id;
        }
        private void browse(Path target, long targetPage) {
            if (!active()) return;
            Path absolute = target.toAbsolutePath().normalize();
            path.reset(absolute.toString());
            reader.browse(begin("正在读取目录…"), absolute, targetPage);
        }
        private void go(SceneEditState.Submission submission) {
            if (!active()) return;
            try {
                Path target = resolve(submission.text);
                long id = begin("正在读取目录…");
                pendingPath = submission;
                reader.locate(id, target);
            } catch (IllegalArgumentException | SecurityException failure) {
                invalidPath(submission, failure);
            }
        }
        private void invalidPath(SceneEditState.Submission submission, RuntimeException failure) {
            begin("无法访问路径：" + failure.getMessage());
            reader.cancelRead(); busy.set(false);
            path.reject(submission, message.get());
        }
        private Path resolve(String raw) {
            if (raw.isEmpty()) throw new IllegalArgumentException("请输入目录或文件路径");
            Path result = raw.regionMatches(true, 0, "file:", 0, 5)
                    ? Paths.get(URI.create(raw)) : directory.get().getFileSystem().getPath(raw);
            return (result.isAbsolute() ? result : directory.get().resolve(result)).toAbsolutePath().normalize();
        }
        private void showRoots(long targetPage) {
            if (active()) reader.roots(begin("正在读取根目录…"), directory.get().getFileSystem(), targetPage);
        }
        private void up() {
            if (!active()) return;
            Path parent = directory.get().getParent();
            if (parent == null || roots.get()) showRoots(0); else browse(parent, 0);
        }
        private void refresh() {
            if (roots.get()) showRoots(0); else browse(directory.get(), 0);
        }
        private void turn(int direction) {
            if (!active() || busy.get()) return;
            long target = page.get() + direction;
            if (target < 0 || direction > 0 && !next.get()) return;
            if (roots.get()) showRoots(target); else browse(directory.get(), target);
        }
        private void confirm() {
            if (!active() || busy.get()) return;
            SceneEditState.Submission submission = path.request(true);
            try {
                Path target = resolve(submission.text);
                long id = begin("正在核验文件…");
                pendingPath = submission; reader.verify(id, target);
            } catch (IllegalArgumentException | SecurityException failure) { invalidPath(submission, failure); }
        }
        private void receive(FilePickerDirectory.Result result) {
            if (!active() || result.request != request) return;
            busy.set(false);
            SceneEditState.Submission submission = pendingPath; pendingPath = null;
            if (result.error != null) {
                message.set("无法访问路径：" + result.error);
                if (submission != null) path.reject(submission, message.get());
                return;
            }
            if (result.verified != null) {
                if (submission != null) path.accept(submission, result.verified.toString());
                finish(); onSelected.accept(result.verified); return;
            }
            roots.set(result.roots); page.set(result.page); next.set(result.next);
            entries.set(result.entries); selected.set(result.selected);
            if (result.directory != null) {
                directory.set(result.directory);
                String normalized = (result.selected == null ? result.directory : result.selected).toString();
                if (submission != null) path.accept(submission, normalized);
                else path.synchronizeExternal(normalized);
            }
            if (result.selected != null) message.set("已选择：" + result.selected.getFileName());
            else if (result.entries.isEmpty()) message.set(result.roots ? "没有可用根目录。" : "空目录");
            else message.set((result.roots ? "根目录 / 盘符" : "单击目录进入，选择文件后确认。")
                    + "（本页 " + result.entries.size() + " 项）");
        }
        /** 仅请求未结束时参与正式输入帧；普通排列仍完全复用 SceneFlowLayout。 */
        private final class Panel implements SceneControl {
            @Override public long nextInputNanos(SceneNode node) {
                return active() && busy.get() ? 0 : Long.MAX_VALUE;
            }
            @Override public void inputFrame(SceneNode node, SceneGeometryView geometry, long now) {
                if (!active()) return;
                FilePickerDirectory.Result result = reader.take();
                if (result != null) receive(result);
            }
            @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) {
                return new MeasuredSize(0, 0);
            }
            @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text,
                    SceneChildMeasurer children) {
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
        private void finish() { finished = true; showing.set(false); detach(); }
        private void cancel() { if (active()) { finish(); onCancelled.run(); } }
    }
}
