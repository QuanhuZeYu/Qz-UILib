package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneAppearance;
import club.heiqi.uilib.ui.scene.control.SceneCheckBox;
import club.heiqi.uilib.ui.scene.control.SceneDraggable;
import club.heiqi.uilib.ui.scene.control.SceneGrid;
import club.heiqi.uilib.ui.scene.control.SceneScrollArea;
import club.heiqi.uilib.ui.scene.control.SceneSlider;
import club.heiqi.uilib.ui.scene.control.SceneSortable;
import club.heiqi.uilib.ui.scene.motion.ElasticOptions;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.PresenceOptions;
import club.heiqi.uilib.ui.scene.motion.SceneMotion;
import club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 共享动效案例只发布业务状态；运动、自然排版和输入沿同一 scene 运行时。 */
public final class MotionPage {
    private final ScenePageContext context;
    private final ReadableSignal<SceneTheme> theme;
    private final Runnable previewTheme;
    private final Signal<MotionPolicy> policy;
    private final Signal<Boolean> target, bouncy, expanded, longText, notice;
    private final Signal<Integer> clicks, drops, noticeClicks, nextItem;
    private final Signal<List<String>> items, sortOrder;
    private final Signal<Double> volume;
    private final Signal<Integer> sliderCommits, sortCommits;
    private SceneNode elasticSlider, sortable;
    private final java.util.Map<String, SceneNode> sortHandles = new java.util.LinkedHashMap<>();
    private final Signal<String> removed, selection;
    private final ReadableSignal<MotionSpec> spatial;
    private final ReadableSignal<PresenceOptions> presence;
    private final SceneEditState draft;
    private final SceneDraggable.State dragState;
    private SceneNode root, movingButton, panel, editor, list, noticeNode, noticeButton, draggable, dragContent;
    private SceneScrollArea.Area scrollArea;

    public MotionPage(ScenePageContext context, ReadableSignal<SceneTheme> theme, Runnable previewTheme) {
        this.context = context; this.theme = theme; this.previewTheme = previewTheme;
        policy = context.domain().signal(MotionPolicy.NORMAL);
        target = context.domain().signal(false); bouncy = context.domain().signal(true);
        expanded = context.domain().signal(false); longText = context.domain().signal(false);
        notice = context.domain().signal(true);
        clicks = context.domain().signal(0); drops = context.domain().signal(0);
        noticeClicks = context.domain().signal(0); nextItem = context.domain().signal(4);
        items = context.domain().signal(Arrays.asList("设计", "开发", "验收"));
        sortOrder = context.domain().signal(Arrays.asList("清晨", "漫步", "夜航"));
        volume = context.domain().signal(50.0); sliderCommits = context.domain().signal(0); sortCommits = context.domain().signal(0);
        removed = context.domain().signal(null); selection = context.domain().signal("点击条目选择它");
        spatial = context.domain().computed(context.scope(), () -> MotionSpec.spring(.42, bouncy.get() ? .68 : 1.0).precision(.1, .1));
        presence = context.domain().computed(context.scope(), () -> new PresenceOptions(MotionSpec.tween(180_000_000L, MotionSpec.SMOOTH))
                .initialEnter(true).budget(4).maximumExit(1_600_000_000L));
        draft = new SceneEditState(context.domain(), "下一次发布，从一个小改进开始");
        dragState = SceneDraggable.state(context.domain());
    }

    public SceneNode create(Runnable leave) {
        root = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(24).gap(20).scrollable(true);
        SceneNode heading = column(8);
        heading.add(GalleryStyle.label(context, () -> "动效实验室", theme, 28, false));
        heading.add(GalleryStyle.label(context, () -> "拉到边缘，感受阻力；松开，让界面自然归位。", theme, 15, true));
        root.add(heading);
        SceneNode toolbar = adaptiveRow(8);
        for (MotionPolicy option : MotionPolicy.values()) {
            String name = policyName(option);
            toolbar.add(GalleryStyle.button(context, () -> (policy.get() == option ? "● " : "") + name,
                    theme, () -> policy.set(option)));
        }
        toolbar.add(GalleryStyle.button(context, () -> "切换浅深色", theme, previewTheme));
        if (leave != null) toolbar.add(GalleryStyle.button(context, () -> "返回场景概览", theme, leave));
        root.add(toolbar);
        SceneNode grid = SceneGrid.create(context, () -> new SceneGrid.Policy(430, 20, 20));
        grid.add(sliderCard()).add(sortCard()).add(stateCard()).add(panelCard()).add(collectionCard()).add(gestureCard());
        root.add(grid);
        return root;
    }

    private SceneNode sliderCard() {
        SceneNode card = section("弹性音量", "拖过两端，轨道会拉伸；松手后恢复。");
        SceneNode value = GalleryStyle.centeredRow().size(Size.fill(1), Size.natural()).gap(12);
        value.add(GalleryStyle.label(context, () -> "音量", theme, 16, true).size(Size.fill(1), Size.natural()));
        value.add(GalleryStyle.label(context, () -> Math.round(volume.get()) + "%", theme, 32, false));
        card.add(value);
        elasticSlider = SceneSlider.create(context, () -> new SceneSlider.Model(0, 100, 1, volume.get(), true)
                .elastic(new ElasticOptions(40, spatial.get())), volume::set,
                next -> { volume.set(next); sliderCommits.set(sliderCommits.get() + 1); }, theme).size(Size.fill(1), Size.fixed(84));
        card.add(new SceneNode().size(Size.fill(1), Size.natural()).padding(30).add(elasticSlider));
        SceneNode legend = GalleryStyle.centeredRow().size(Size.fill(1), Size.natural());
        legend.add(GalleryStyle.label(context, () -> "静音", theme, 14, true).size(Size.fill(1), Size.natural()));
        legend.add(GalleryStyle.label(context, () -> "最大音量", theme, 14, true)); card.add(legend);
        return GalleryStyle.frame(card, theme);
    }
    private SceneNode sortCard() {
        SceneNode card = section("弹性排序", "按住把手上下拖动，相邻曲目会让位。");
        sortable = SceneSortable.create(context,
                () -> java.util.Collections.singletonList(new SceneSortable.Group<>("播放队列", sortOrder.get())), name -> name,
                key -> { SceneNode rows = column(8); return new SceneSortable.Container(rows, rows, SceneSortable.Layout.VERTICAL); },
                name -> {
                    SceneNode handle = GalleryStyle.dragGrip(theme); sortHandles.put(name.get(), handle);
                    return new SceneSortable.Row(sortRow(name, handle), handle);
                }, name -> sortRow(name, GalleryStyle.dragGrip(theme)), move -> {
                    List<String> next = new ArrayList<>(sortOrder.get()); next.remove(move.key);
                    int index = move.beforeKey == null ? next.size() : next.indexOf(move.beforeKey);
                    next.add(index < 0 ? next.size() : index, move.key); sortOrder.set(next); sortCommits.set(sortCommits.get() + 1);
                }, () -> new SceneSortable.Options<String, String>().autoScroll(false).reorderMotion(spatial.get()).dropMotion(spatial.get()));
        card.add(sortable);
        card.add(GalleryStyle.button(context, () -> "重置队列", theme, () -> sortOrder.set(Arrays.asList("清晨", "漫步", "夜航"))));
        return GalleryStyle.frame(card, theme);
    }
    private SceneNode sortRow(ReadableSignal<String> name, SceneNode handle) {
        SceneNode row = GalleryStyle.centeredRow().size(Size.fill(1), Size.natural()).padding(12).gap(14);
        row.add(handle); row.add(GalleryStyle.label(context, name, theme, 16, false).size(Size.fill(1), Size.natural()));
        row.add(GalleryStyle.label(context, () -> name.get().equals("清晨") ? "3:24" : name.get().equals("漫步") ? "4:08" : "2:56", theme, 14, true));
        return GalleryStyle.frame(row, theme);
    }
    private SceneNode stateCard() {
        SceneNode card = section("状态响应", "切换目标后，试着点击移动中的按钮。");
        SceneNode actions = adaptiveRow(8);
        actions.add(GalleryStyle.primary(context, () -> "切换目标", theme, () -> target.set(!target.get())));
        actions.add(GalleryStyle.button(context, () -> bouncy.get() ? "阻尼：弹性" : "阻尼：平稳", theme,
                () -> bouncy.set(!bouncy.get())));
        card.add(actions);
        card.add(SceneCheckBox.create(context, () -> target.get() ? SceneCheckBox.State.CHECKED : SceneCheckBox.State.UNCHECKED,
                () -> "强调状态", theme, target::set));
        SceneNode stage = column(0).size(Size.fill(1), Size.fixed(86)).padding(14).clip(true);
        movingButton = GalleryStyle.primary(context, () -> "点击 · " + clicks.get(), theme, () -> clicks.set(clicks.get() + 1))
                .size(Size.natural(), Size.natural())
                .bind(SceneMotion.translateX(() -> target.get() ? 76f : 0f, spatial))
                .bind(SceneMotion.opacity(() -> target.get() ? 1f : .78f, () -> MotionSpec.tween(180_000_000L, MotionSpec.SMOOTH)));
        stage.add(movingButton);
        SceneNode surface = GalleryStyle.frame(stage, theme).bind(SceneMotion.background(
                () -> target.get() ? new SceneAppearance.Palette(theme.get()).selected : theme.get().panel,
                () -> theme.get().feedbackMotion));
        card.add(surface);
        card.add(GalleryStyle.label(context, () -> "当前目标：" + (target.get() ? "右侧" : "左侧") + " · 已点击 " + clicks.get() + " 次", theme, 15, true));
        return GalleryStyle.frame(card, theme);
    }

    private SceneNode panelCard() {
        SceneNode card = section("自然展开", "展开时可以修改文字，内容按当前宽度重新换行。");
        SceneNode actions = adaptiveRow(8);
        actions.add(GalleryStyle.primary(context, () -> expanded.get() ? "收起面板" : "展开面板", theme,
                () -> expanded.set(!expanded.get())));
        actions.add(GalleryStyle.button(context, () -> longText.get() ? "使用短文案" : "使用长文案", theme,
                () -> longText.set(!longText.get())));
        card.add(actions);
        panel = column(12).clip(true).bind(SceneMotion.layoutSize(spatial));
        SceneNode content = column(12).padding(12).bind(NodeBinding.value(expanded, SceneNode::visible));
        content.add(GalleryStyle.label(context, () -> longText.get()
                ? "每次状态变化都会形成新的自然布局。文字增加、窗口变窄或继续编辑时，面板会朝新的内容尺寸接续，周围内容也随实际空间移动。可以在展开途中反向收起，再立即打开。"
                : "把想法写下来，让下一步更清楚。", theme, 15, false));
        editor = SceneTextField.create(context, draft, theme, () -> false, submission -> {}, SceneTextField.SubmitPolicy.CONFIRM)
                .size(Size.fill(1), Size.natural());
        content.add(editor); panel.add(content); card.add(panel);
        card.add(GalleryStyle.label(context, () -> expanded.get() ? "草稿会随本次页面保留" : "面板已收起", theme, 15, true));
        return GalleryStyle.frame(card, theme);
    }

    private SceneNode collectionCard() {
        SceneNode card = section("集合与存在性", "重排延续条目；移除时立即结束操作，再播放有限退场。");
        SceneNode actions = adaptiveRow(8);
        actions.add(GalleryStyle.button(context, () -> "倒转顺序", theme, () -> {
            List<String> next = new ArrayList<>(items.get()); java.util.Collections.reverse(next); items.set(next);
        }));
        actions.add(GalleryStyle.button(context, () -> "添加条目", theme, () -> {
            List<String> next = new ArrayList<>(items.get()); next.add("条目 " + nextItem.get());
            nextItem.set(nextItem.get() + 1); items.set(next);
        }));
        actions.add(GalleryStyle.button(context, () -> "移除末项", theme, () -> {
            List<String> next = new ArrayList<>(items.get()); removed.set(next.remove(next.size() - 1)); items.set(next);
        }).bind(NodeBinding.value(() -> !items.get().isEmpty(), SceneNode::enabled)));
        card.add(actions);
        card.add(GalleryStyle.button(context, () -> "恢复移除项", theme, () -> {
            List<String> next = new ArrayList<>(items.get()); next.add(removed.get()); items.set(next); removed.set(null);
        }).bind(NodeBinding.value(() -> removed.get() != null, SceneNode::enabled)));
        list = column(8).bind(SceneMotion.layoutPositions(spatial));
        list.bind(NodeBinding.repeat(items, value -> value, value ->
                GalleryStyle.button(context, () -> "选择 · " + value.get(), theme,
                        () -> selection.set("已选择：" + value.get())).size(Size.fill(1), Size.natural()), presence));
        card.add(list).add(GalleryStyle.label(context, selection, theme, 15, true));
        card.add(GalleryStyle.button(context, () -> notice.get() ? "隐藏提示" : "显示提示", theme, () -> notice.set(!notice.get())));
        noticeButton = GalleryStyle.button(context, () -> "确认提示 · " + noticeClicks.get(), theme,
                () -> noticeClicks.set(noticeClicks.get() + 1));
        noticeNode = column(8).padding(12).add(GalleryStyle.label(context, () -> "提示可以在退场途中重新显示。", theme, 15, false))
                .add(noticeButton).bind(SceneMotion.presence(notice, presence));
        card.add(noticeNode);
        return GalleryStyle.frame(card, theme);
    }

    private SceneNode gestureCard() {
        SceneNode card = section("弹性边界", "拖到边缘后继续拉，阻力会增大；松手回弹。Esc 取消。");
        SceneNode stage = column(0).size(Size.fill(1), Size.fixed(204)).padding(44).clip(true);
        SceneNode handle = GalleryStyle.dragGrip(theme);
        SceneNode body = GalleryStyle.centeredRow().size(Size.natural(), Size.natural()).padding(14).gap(12)
                .add(handle).add(GalleryStyle.label(context, () -> "拖动我", theme, 15, false));
        dragContent = GalleryStyle.frame(body, theme).size(Size.natural(), Size.natural());
        draggable = SceneDraggable.create(context, dragContent, dragState, () -> new SceneDraggable.Options()
                .handles(handle).bounds(SceneDraggable.Bounds.PARENT).autoScroll(false).elastic(new ElasticOptions(32, spatial.get()))
                .onDrop(drop -> drops.set(drops.get() + 1))).size(Size.natural(), Size.natural());
        stage.add(draggable); card.add(GalleryStyle.frame(stage, theme));
        card.add(GalleryStyle.label(context, () -> "正常放下 " + drops.get() + " 次", theme, 15, true));
        card.add(GalleryStyle.label(context, () -> "滚动边界", theme, 18, false));
        card.add(GalleryStyle.label(context, () -> "滚到页面底部，再向下滚动内层列表，观察边界回弹。", theme, 15, true));
        SceneNode content = column(8).padding(12);
        for (int index = 1; index <= 8; index++) {
            String name = "滚动条目 " + index;
            content.add(GalleryStyle.button(context, () -> name, theme, () -> selection.set("已选择：" + name))
                    .size(Size.fill(1), Size.natural()));
        }
        scrollArea = SceneScrollArea.create(context, content, () -> new SceneScrollArea.Options().autoHide(false)
                .motion(new ScrollMotionOptions(spatial.get()).inertia(MotionSpec.decay(7)).maximumDisplacement(36)), theme);
        card.add(scrollArea.node().size(Size.fill(1), Size.fixed(172)));
        return GalleryStyle.frame(card, theme);
    }

    private SceneNode section(String title, String description) {
        return GalleryStyle.card(theme).gap(14)
                .add(GalleryStyle.label(context, () -> title, theme, 20, false))
                .add(GalleryStyle.label(context, () -> description, theme, 15, true));
    }
    private static SceneNode column(int gap) { return new SceneNode().size(Size.fill(1), Size.natural()).gap(gap); }
    private SceneNode adaptiveRow(int gap) {
        return column(gap).bind(NodeBinding.value(() -> context.viewportWidth().get() < 520,
                (node, narrow) -> node.axis(narrow ? SceneNode.Axis.COLUMN : SceneNode.Axis.ROW)));
    }
    private static String policyName(MotionPolicy value) {
        switch (value) { case NORMAL: return "正常"; case REDUCED: return "减少动效"; case OFF: return "关闭动效"; default: throw new AssertionError(value); }
    }

    public ReadableSignal<MotionPolicy> policy() { return policy; }
    public ReadableSignal<Boolean> target() { return target; }
    public ReadableSignal<Integer> clicks() { return clicks; }
    public ReadableSignal<Integer> drops() { return drops; }
    public ReadableSignal<Integer> noticeClicks() { return noticeClicks; }
    public ReadableSignal<List<String>> items() { return items; }
    public ReadableSignal<String> selection() { return selection; }
    public SceneEditState draft() { return draft; }
    public SceneDraggable.State dragState() { return dragState; }
    public SceneNode root() { return root; }
    public SceneNode movingButton() { return movingButton; }
    public SceneNode panel() { return panel; }
    public SceneNode editor() { return editor; }
    public SceneNode list() { return list; }
    public SceneNode notice() { return noticeNode; }
    public SceneNode noticeButton() { return noticeButton; }
    public SceneNode draggable() { return draggable; }
    public SceneNode dragContent() { return dragContent; }
    public SceneScrollArea.Area scrollArea() { return scrollArea; }
    public SceneNode elasticSlider() { return elasticSlider; }
    public ReadableSignal<Double> volume() { return volume; }
    public ReadableSignal<Integer> sliderCommits() { return sliderCommits; }
    public ReadableSignal<List<String>> sortOrder() { return sortOrder; }
    public ReadableSignal<Integer> sortCommits() { return sortCommits; }
    public SceneNode sortHandle(String key) { return sortHandles.get(key); }
    public SceneNode sortable() { return sortable; }
}
