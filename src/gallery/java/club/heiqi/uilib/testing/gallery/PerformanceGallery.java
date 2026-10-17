package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.control.SceneDocument;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.text.DocumentContent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** 测试侧共享压测声明与有界运行。计时边界和验收见性能压测规格，不进入正式发行。 */
public final class PerformanceGallery implements ScenePage {
    public enum Load {
        IDLE("静止列表"), SCROLL("列表滚动"), UPDATE("局部更新"),
        DOCUMENT("文档滚动"), EDIT("连续编辑"), RESIZE("宽度重排");
        public final String title;
        Load(String title) { this.title = title; }
    }
    /** 宿主只递交正式输入；MC人工模式没有模拟输入能力。 */
    public interface Driver {
        void initialize(SceneNode target);
        void scroll(int delta);
        void edit(String text, SceneEditEvent.Command command);
    }
    private final String host, boundary, version;
    private final Consumer<String> reports;
    private ScenePageContext context;
    private ReadableSignal<SceneTheme> theme;
    private Signal<String> status;
    private Signal<List<SceneNode>> content;
    private Signal<Size> width;
    private final List<Signal<String>> rows = new ArrayList<>();
    private SceneNode target;
    private SceneEditState editor;
    private Signal<Load> selected;
    private Signal<Integer> scale;
    private Load load;
    private int runScale, warmup, targetFrames, steps, contentLength;
    private long declarationNanos, firstFrameNanos, previousStart;
    private boolean running, first;
    private Runnable pending;
    private int lastWidth, lastHeight, currentTargetWidth, previousTargetWidth, observedChanges, previousOffset;
    private String previousText;
    private PerformanceSamples samples;
    private String lastReport;
    private PerformanceReport.Snapshot before;
    public PerformanceGallery(String host, String boundary, String version, Consumer<String> reports) {
        this.host = host; this.boundary = boundary; this.version = version; this.reports = reports;
    }
    @Override public void build(ScenePageContext context) {
        this.context = context;
        theme = context.domain().computed(context.scope(), () -> GalleryStyle.theme(context.themeMode().get() == club.heiqi.uilib.ui.scene.control.ThemeMode.DARK));
        selected = context.domain().signal(Load.IDLE);
        scale = context.domain().signal(200);
        status = context.domain().signal("选择负载和规模后开始；滚动/编辑在MC中请持续手动操作。统计仅在结束时刷新。");
        content = context.domain().signal(Collections.emptyList());
        width = context.domain().signal(Size.fill(1));
        SceneNode root = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(28).gap(16);
        root.bind(NodeBinding.value(theme, (n,t) -> n.background(t.panel == 0xffffffff ? GalleryStyle.LIGHT_BACKGROUND : GalleryStyle.DARK_BACKGROUND)));
        root.add(GalleryStyle.header(context,theme,"QZ / 性能压测广场","选择一项负载，观察界面在持续操作中的表现。",null));
        SceneNode loads = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(5).scrollable(true);
        for (Load choice : Load.values()) loads.add(GalleryStyle.button(context, () -> (selected.get() == choice ? "● " : "") + choice.title, theme, () -> {
            if (running) stop(); selected.set(choice); status.set("已选择：" + choice.title);
        }));
        root.add(loads);
        SceneNode actions = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(8);
        actions.add(GalleryStyle.button(context, () -> "规模：" + scale.get(), theme, () -> {
            if (running) stop(); scale.set(scale.get() == 50 ? 200 : scale.get() == 200 ? 1000 : 50);
            status.set("已选择规模：" + scale.get());
        }));
        actions.add(GalleryStyle.primary(context, () -> "开始 / 重跑", theme, () -> start(selected.get(), scale.get(), 30, 120)));
        actions.add(GalleryStyle.button(context, () -> "停止", theme, this::stop));
        root.add(actions);
        root.add(GalleryStyle.label(context, status, theme, 13, false));
        SceneNode deck = new SceneNode().size(Size.fill(1), Size.fill(1)).clip(true);
        deck.bind(NodeBinding.ownedChildren(content));
        SceneNode empty=GalleryStyle.card(theme).gap(10)
                .add(GalleryStyle.label(context,()->"准备开始",theme,22,false))
                .add(GalleryStyle.label(context,()->"选择负载和规模，点击开始。运行区域会显示对应的真实内容。",theme,15,true));
        root.add(GalleryStyle.frame(empty,theme).bind(NodeBinding.value(()->content.get().isEmpty(),SceneNode::visible)));
        root.add(deck);
        context.own(() -> { try { if (running) finish(false); } finally { pending = null; releaseWorkload(); } });
        context.mountOwned(root);
    }
    /** 业务测试动作通过同一domain写状态；负载声明与首次挂载各自计时。 */
    public void start(Load requested, int count, int warmFrames, int frames) {
        if (count != 50 && count != 200 && count != 1000) throw new IllegalArgumentException("规模须为50/200/1000");
        if (warmFrames < 0 || frames <= 0) throw new IllegalArgumentException("采样参数非法");
        // handler只发布运行意图；下一次宿主frame才挂载，确保首次布局纳入首帧。
        context.domain().execute(() -> pending = () -> activate(requested, count, warmFrames, frames));
    }
    private void activate(Load requested, int count, int warmFrames, int frames) {
            if (running) finish(false);
            load = requested; selected.set(requested); runScale = count; scale.set(count); warmup = warmFrames; targetFrames = frames;
            samples = new PerformanceSamples(frames); steps = 0; previousStart = 0; first = true;
            currentTargetWidth = previousTargetWidth = observedChanges = previousOffset = 0; previousText = null;
            editor = null; rows.clear(); width.set(Size.fill(1));
            before = PerformanceReport.Snapshot.capture();
            long begin = System.nanoTime();
            target = workload(count);
            content.set(Collections.singletonList(target));
            declarationNanos = System.nanoTime() - begin;
            status.set("运行：" + load.title + " / " + count + "；预热 " + warmFrames + "，采样 " + frames + "。请在负载区域操作。");
            running = true;
    }
    private SceneNode workload(int count) {
        if (load == Load.EDIT) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < count; i++) text.append("交接 ").append(i).append(" / Café é العربية אבג · 工坊记录。\n");
            contentLength = text.length();
            editor = new SceneEditState(context.domain(), text.toString(), SceneEditState.Mode.MULTI_LINE);
            return SceneTextArea.create(context, editor, theme, () -> false, r -> editor.accept(r, r.text)).size(Size.fill(1), Size.fill(1));
        }
        SceneNode viewport = new SceneNode().size(Size.fill(1), Size.fill(1)).scrollable(true).clip(true).gap(5);
        viewport.bind(NodeBinding.value(width, (n,w) -> n.size(w, Size.fill(1))));
        if (load == Load.RESIZE) viewport.add(new SceneNode().size(Size.fixed(0), Size.fixed(0)).control(new club.heiqi.uilib.ui.scene.control.SceneControl() {
            @Override public club.heiqi.uilib.ui.scene.layout.MeasuredSize measure(SceneNode node, club.heiqi.uilib.ui.scene.layout.Constraints limits, club.heiqi.uilib.ui.scene.text.SceneTextMeasurer text) {
                return new club.heiqi.uilib.ui.scene.layout.MeasuredSize(0, 0);
            }
            @Override public club.heiqi.uilib.ui.scene.layout.ChildPlacements arrange(SceneNode node, club.heiqi.uilib.ui.scene.layout.LayoutBox allocated, club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer children) {
                return new club.heiqi.uilib.ui.scene.layout.ChildPlacements(Collections.emptyList());
            }
            @Override public void paint(SceneNode node, club.heiqi.uilib.ui.scene.layout.SceneGeometryView geometry,
                    club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder paint) {
                // 测试侧只观测正式分配宽度；不把几何读回业务声明或修改布局。
                int actual = geometry.get(viewport).box.width;
                currentTargetWidth = actual;
            }
        }));
        if (load == Load.DOCUMENT) {
            // 预留外层滚轮命中留白，自动驱动不会在嵌套表格水平视口上停止纵向负载。
            viewport.padding(12);
            StringBuilder markdown = new StringBuilder("# 压测交接记录\n\n");
            for (int i = 0; i < count; i++) {
                markdown.append("## 批次 ").append(i).append("\n\n工坊 **材料核对** Café é العربية אבג。保持真实字体整形与换行。\n\n");
                if (i % 10 == 0) markdown.append("| 项目 | 数量 |\n| --- | ---: |\n| 铜锭 | 64 |\n| 产出 | 32 |\n\n$\\frac{a+b}{c}$\n\n");
            }
            DocumentContent document = club.heiqi.uilib.ui.scene.text.MarkdownDocument.parse(markdown.toString());
            contentLength = markdown.length();
            viewport.add(SceneDocument.create(context, () -> document, theme, null, link -> {}));
        } else {
            contentLength = 0;
            for (int i = 0; i < count; i++) {
                final int index = i;
                String initial = "条目 " + i + " / 材料核对 Café · 数量 32 · 工坊记录";
                Signal<String> value = context.domain().signal(initial); rows.add(value); contentLength += initial.length();
                SceneNode row = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(8);
                row.add(GalleryStyle.label(context, value, theme, 15, false).size(Size.fill(1), Size.natural()));
                row.add(GalleryStyle.button(context, () -> "核对 " + index, theme, () -> value.set("已核对 " + index)));
                viewport.add(row);
            }
        }
        return viewport;
    }
    /** 计时仅涵盖所声明的宿主边界；路径核对、统计和文件写入在边界外。 */
    public void frame(Runnable render, Driver driver, int viewportWidth, int viewportHeight) {
        if (pending != null) { Runnable action = pending; pending = null; context.domain().execute(action); }
        lastWidth = viewportWidth; lastHeight = viewportHeight;
        boolean measured = running;
        long begin = measured ? System.nanoTime() : 0;
        if (measured && !first) {
            int step = steps;
            if (load == Load.UPDATE) context.domain().execute(() -> rows.get(step % rows.size()).set("更新 " + step + " / 工坊材料变化"));
            if (load == Load.RESIZE) context.domain().execute(() -> width.set(step % 2 == 0 ? Size.fixed(Math.max(120, viewportWidth / 2)) : Size.fill(1)));
            if (driver != null && (load == Load.SCROLL || load == Load.DOCUMENT)) driver.scroll(step % 60 < 40 ? -1 : 1);
            if (driver != null && load == Load.EDIT) driver.edit(step % 2 == 0 ? "测a" : null, step % 2 == 0 ? null : SceneEditEvent.Command.BACKSPACE);
        }
        render.run();
        long end = measured ? System.nanoTime() : 0;
        if (!measured || !running) return;
        context.domain().execute(() -> observe(!first && steps >= warmup));
        if (first) {
            firstFrameNanos = end - begin; first = false; previousStart = begin;
            if (driver != null) driver.initialize(target);
            return;
        }
        if (steps++ >= warmup) {
            samples.add(end - begin, previousStart == 0 ? 0 : begin - previousStart);
            if (samples.size() == targetFrames) context.domain().execute(() -> finish(true, viewportWidth, viewportHeight));
        }
        previousStart = begin;
    }
    public void stop() { if (context != null) context.domain().execute(() -> { pending = null; if (running) finish(false); else releaseWorkload(); }); }
    private void finish(boolean complete) { finish(complete, lastWidth, lastHeight); }
    private void finish(boolean complete, int w, int h) {
        running = false;
        boolean path = pathObserved();
        lastReport = PerformanceReport.json(host, boundary, version, load.name(), runScale, warmup, targetFrames,
                complete, path, observedChanges, contentLength, w, h, declarationNanos, firstFrameNanos, samples, before, PerformanceReport.Snapshot.capture());
        PerformanceSamples.Summary summary = samples.frame();
        status.set((complete ? path ? "完成" : "未命中负载" : "已中断") + " " + load.title + " / n=" + summary.count
                + String.format(Locale.ROOT, " / p50 %.2f · p95 %.2f · p99 %.2f · max %.2f ms / >33ms %d",
                        summary.p50Nanos / 1e6, summary.p95Nanos / 1e6, summary.p99Nanos / 1e6, summary.maxNanos / 1e6, summary.above33));
        try { reports.accept(lastReport); } finally { if (!complete) releaseWorkload(); }
    }
    private void releaseWorkload() {
        content.set(Collections.emptyList()); rows.clear(); target = null; editor = null; previousText = null;
    }
    /** 在宿主计时区外只读取已经应用的事实，不调用pipeline.current或制造额外prepare。
     * 只预热时操作不能冒充稳定负载；往返滚回零位、删除到原长度也保留真实变化证据。 */
    private void observe(boolean sampled) {
        boolean changed = false;
        if (load == Load.SCROLL || load == Load.DOCUMENT) {
            int actual = context.runtime().scrollOffset(target);
            changed = actual != previousOffset; previousOffset = actual;
        } else if (load == Load.EDIT) {
            String actual = editor.snapshot().get().text;
            changed = previousText != null && !previousText.equals(actual); previousText = actual;
        } else if (load == Load.UPDATE) {
            changed = !first && rows.get(steps % rows.size()).get().equals("更新 " + steps + " / 工坊材料变化");
        } else if (load == Load.RESIZE) {
            changed = currentTargetWidth != previousTargetWidth; previousTargetWidth = currentTargetWidth;
        }
        if (sampled && changed) observedChanges++;
    }
    private boolean pathObserved() { return load == Load.IDLE || observedChanges > 0; }
    public boolean running() { return running || pending != null; }
    public String lastReport() { return lastReport; }
    public int sampleCount() { return samples == null ? 0 : samples.size(); }
    public SceneNode target() { return target; }
}
