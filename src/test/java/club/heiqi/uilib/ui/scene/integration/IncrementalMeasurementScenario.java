package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.math.MathFont;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 正式页面、真实字体委托与注入输入；请求复用和共同几何分别观测。 */
public final class IncrementalMeasurementScenario extends TestApplication {
    private FontTextService fonts;
    public static void main(String[] args) throws Exception { new IncrementalMeasurementScenario().run(args); }
    @Override protected void setup() throws Exception { fonts = own(new FontTextService(new LwjglFontEngine())); }
    @Override protected void runTests() throws Exception {
        scenario("稳定帧与固定宽度局部文案更新复用未变请求", this::localText);
        scenario("绘制与排列依赖保留正文请求且命中当前盒", this::dependencyStages);
        scenario("自然高度变化带动兄弟位置裁剪和范围", this::naturalHeight);
        scenario("窗口字号字体换代与无来源失效结束旧请求", this::globalInputs);
        scenario("过量宽度请求正常淘汰并保留最近请求", this::boundedRequests);
    }
    private final class Host implements SceneHost {
        int width = 320;
        PaintPlan paint;
        public int width() { return width; }
        public int height() { return 170; }
        public long metricsRevision() { return fonts.generation(); }
        public void replay(PaintPlan plan) { paint = plan; }
    }
    private static final class Requests implements SceneTextMeasurer {
        final SceneTextMeasurer delegate;
        final Map<String, Integer> calls = new HashMap<>();
        Requests(SceneTextMeasurer delegate) { this.delegate = delegate; }
        int count(String text) { return calls.getOrDefault(text, 0); }
        void record(String text) { calls.put(text, count(text) + 1); }
        public TextStyle resolveStyle(TextStyle style) { return delegate.resolveStyle(style); }
        public MathFont mathFont(TextStyle style) { return delegate.mathFont(style); }
        public TextLayout layout(String text, TextStyle style, int width) {
            record(text); return delegate.layout(text, style, width);
        }
        public TextLayout layout(String text, TextStyle style, int width, TextPolicy policy) {
            record(text); return delegate.layout(text, style, width, policy);
        }
        public TextLayout layout(AttributedText text, float width, TextPolicy policy) {
            record(text.originalText); return delegate.layout(text, width, policy);
        }
    }
    private static final class Extension implements SceneControl {
        final Signal<Integer> measure, arrange, paint;
        int measures, arrangements, clicks;
        Extension(ReactiveDomain domain) {
            measure = domain.signal(0); arrange = domain.signal(0); paint = domain.signal(0xff245688);
        }
        public List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Collections.singletonList(measure); }
        public List<ReadableSignal<?>> arrangementDependencies(SceneNode node) { return Collections.singletonList(arrange); }
        public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Collections.singletonList(paint); }
        public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) {
            measures++;
            TextLayout result = text.layout("扩展正文", node.style(), constraints.availableWidth, TextPolicy.NO_WRAP);
            return new MeasuredSize(result.width, result.height + measure.get());
        }
        public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
            arrangements++; arrange.get(); return new ChildPlacements(Collections.emptyList());
        }
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
            SceneGeometryView.Entry entry = geometry.get(node);
            target.add(PaintCommand.rect(entry.box, entry.clip, paint.get()));
        }
    }
    private final class Page implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final Host host = new Host();
        final Requests requests = new Requests(fonts);
        final ScenePageSession session;
        Signal<String> value;
        Signal<TextStyle> style;
        Signal<Integer> background;
        Extension extension;
        SceneNode first, stable, custom, root, editor;
        SceneEditState draft;
        Page() {
            session = ScenePageSession.open(domain, host, requests, context -> {
                value = domain.signal("初始文案");
                style = domain.signal(new TextStyle("default", 18, 0xff223344));
                background = domain.signal(0xffeeeeee);
                first = new SceneNode().size(Size.fill(1), Size.natural());
                first.bind(NodeBinding.value(value, SceneNode::text));
                first.bind(NodeBinding.value(style, SceneNode::style));
                first.bind(NodeBinding.value(background, SceneNode::background));
                stable = new SceneNode().size(Size.fill(1), Size.natural()).text("固定未变行 Café");
                extension = new Extension(domain);
                custom = new SceneNode().size(Size.fill(1), Size.natural()).control(extension).onClick(() -> extension.clicks++);
                draft = new SceneEditState(domain, "独立草稿", SceneEditState.Mode.SINGLE_LINE);
                editor = SceneTextField.create(context, draft, domain.signal(SceneTheme.light()), () -> false, r -> {});
                root = new SceneNode().size(Size.fill(1), Size.fill(1)).scrollable(true).clip(true).gap(6)
                        .add(first).add(stable).add(custom).add(editor);
                context.mount(root);
            });
            session.frame();
        }
        SceneGeometryView.Entry entry(SceneNode node) { return session.pipeline().current().geometry.get(node); }
        void update(Runnable action) { domain.execute(action); session.frame(); }
        public void close() { session.close(); }
    }
    private void localText() {
        try (Page p = new Page()) {
            PaintPlan previous = p.host.paint;
            int stable = p.requests.count("固定未变行 Café"), custom = p.requests.count("扩展正文");
            int measured = p.extension.measures;
            p.session.frame();
            require(p.host.paint == previous && p.extension.measures == measured, "稳定帧重复布局或重收集计划");
            p.update(() -> p.value.set("当前变化的文案"));
            p.domain.execute(() -> require(p.entry(p.first).text.originalText.equals("当前变化的文案"), "当前正文未实际呈现"));
            require(p.requests.count("固定未变行 Café") == stable && p.requests.count("扩展正文") == custom,
                    "局部文案使未变节点重复请求实际文字服务");
            require(p.extension.measures > measured, "本期不应跳过正式控件测量回调");
        }
    }
    private void dependencyStages() {
        try (Page p = new Page()) {
            int requests = p.requests.count("扩展正文"), measures = p.extension.measures, arrangements = p.extension.arrangements;
            PaintPlan old = p.host.paint;
            p.update(() -> { p.background.set(0xffaabbcc); p.extension.paint.set(0xff556677); });
            require(p.extension.measures == measures && p.extension.arrangements == arrangements && p.host.paint != old,
                    "绘制依赖错误进入测量排列或未更新画面");
            p.update(() -> p.style.set(p.style.get().withColor(0x77224466)));
            p.domain.execute(() -> require(p.entry(p.first).text.style.color == 0x77224466, "透明度换色未更新文字载荷"));
            require(p.extension.measures == measures, "纯颜色触发内容测量");
            p.update(() -> p.extension.arrange.set(1));
            require(p.extension.arrangements > arrangements && p.requests.count("扩展正文") == requests,
                    "排列依赖未执行当前回调或重新请求正文");
            final LayoutBox[] hit = {null};
            p.domain.execute(() -> hit[0] = p.entry(p.custom).box);
            p.session.pointer(ScenePointerEvent.down(0, hit[0].x + 2, hit[0].y + 2));
            p.session.pointer(ScenePointerEvent.up(0, hit[0].x + 2, hit[0].y + 2)); p.session.frame();
            require(p.extension.clicks == 1, "共同几何中的自定义控件未单次命中");
            int stable = p.requests.count("固定未变行 Café");
            p.update(() -> p.extension.measure.set(9));
            require(p.requests.count("扩展正文") > requests && p.requests.count("固定未变行 Café") == stable,
                    "正式测量依赖未携带节点来源");
        }
    }
    private void naturalHeight() {
        try (Page p = new Page()) {
            final int[] y = {0}; final int[] maximum = {0};
            int stable = p.requests.count("固定未变行 Café");
            p.domain.execute(() -> { y[0] = p.entry(p.stable).box.y; maximum[0] = p.entry(p.root).scrollMaximum; });
            p.update(() -> p.value.set("一\n二\n三\n四\n五\n六\n七\n八"));
            p.domain.execute(() -> {
                require(p.entry(p.stable).box.y > y[0] && p.entry(p.root).scrollMaximum > maximum[0], "自然高度未更新兄弟位置和滚动范围");
                require(p.entry(p.stable).clip != null && p.entry(p.stable).clip.height == p.host.height(), "后续内容未消费当前裁剪");
                require(p.entry(p.first).text.lines().size() == 8, "当前多行正文不完整");
            });
            require(p.requests.count("固定未变行 Café") == stable, "父重排使未变同宽文字重复请求");
        }
    }
    private void boundedRequests() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Host host = new Host();
        Requests requests = new Requests(fonts);
        String value = "Bounded real font requests";
        try (ScenePageSession session = ScenePageSession.open(domain, host, requests, context -> {
            SceneControl control = new SceneControl() {
                public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) {
                    for (int width = 24; width < 88; width++) {
                        TextLayout layout = text.layout(value, node.style(), width, TextPolicy.NORMAL);
                        require(layout.originalText.equals(value) && layout.lines().size() > 0, "过量约束丢失实际文字内容");
                    }
                    int latest = requests.count(value);
                    text.layout(value, node.style(), 87, TextPolicy.NORMAL);
                    require(requests.count(value) == latest, "最近有效请求未复用");
                    text.layout(value, node.style(), 24, TextPolicy.NORMAL);
                    require(requests.count(value) > latest, "过量请求仍无限保留最早宽度");
                    return new MeasuredSize(120, 30);
                }
                public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
                    return new ChildPlacements(Collections.emptyList());
                }
                public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
                    SceneGeometryView.Entry entry = geometry.get(node);
                    target.add(PaintCommand.rect(entry.box, entry.clip, 0xff335577));
                }
            };
            context.mount(new SceneNode().control(control).size(Size.fill(1), Size.natural()));
        })) {
            session.frame();
            require(host.paint != null && !host.paint.commands().isEmpty(), "有界请求场景未真实回放");
        }
    }
    private void globalInputs() throws Exception {
        try (Page p = new Page()) {
            int stable = p.requests.count("固定未变行 Café");
            p.host.width = 240; p.session.frame();
            require(p.requests.count("固定未变行 Café") > stable, "新窗口约束复用了旧请求");
            int before = p.requests.count("初始文案");
            p.update(() -> p.style.set(new TextStyle("default", 24, 0xff223344)));
            require(p.requests.count("初始文案") > before, "字号变化复用了旧请求");
            stable = p.requests.count("固定未变行 Café");
            p.update(() -> p.session.runtime().invalidate());
            require(p.requests.count("固定未变行 Café") > stable, "旧无来源失效失去整体更新含义");
            stable = p.requests.count("固定未变行 Café");
            fonts.reload(FontTextService.defaultSources(getClass().getClassLoader(), fonts.generation() + 1)); p.session.frame();
            require(p.requests.count("固定未变行 Café") > stable, "字体换代残留旧代际文字");
        }
    }
}
