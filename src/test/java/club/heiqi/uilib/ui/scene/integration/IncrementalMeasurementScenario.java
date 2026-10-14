package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneDocument;
import club.heiqi.uilib.ui.scene.control.SceneImage;
import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.ImageState;
import club.heiqi.uilib.ui.scene.image.SharedImageService;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.text.TextContent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
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
        scenario("动态增删重排重挂与卸载后依赖隔离", this::structureScopes);
        scenario("跨父移动保留挂载与文字请求", this::moveScope);
        scenario("同源码新文档声明更新目标且不清理旁行", this::documentScope);
        scenario("真实共享图像换代与READY备用保留当前来源", this::imageScope);
        scenario("退出分支与关闭管线不持续持有旧节点", this::releasedNodes);
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
        SceneNode first, stable, custom, root, editor, dynamic, destination;
        Signal<List<SceneNode>> branch;
        SceneEditState draft;
        Page() { this((page, context) -> {}); }
        Page(java.util.function.BiConsumer<Page, ScenePageContext> decorate) {
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
                branch = domain.signal(Collections.emptyList());
                dynamic = new SceneNode().size(Size.fill(1), Size.natural()).bind(NodeBinding.children(branch));
                destination = new SceneNode().size(Size.fill(1), Size.natural());
                root = new SceneNode().size(Size.fill(1), Size.fill(1)).scrollable(true).clip(true).gap(6)
                        .add(first).add(stable).add(custom).add(editor).add(dynamic).add(destination);
                decorate.accept(this, context);
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
    @SuppressWarnings("unchecked")
    private void structureScopes() {
        try (Page p = new Page()) {
            int stable = p.requests.count("固定未变行 Café");
            SceneNode[] nodes = new SceneNode[2]; Extension[] control = new Extension[1];
            Signal<Boolean>[] visible = new Signal[1], enabled = new Signal[1];
            MountHandle[] original = new MountHandle[1];
            p.update(() -> {
                control[0] = new Extension(p.domain);
                visible[0] = p.domain.signal(true); enabled[0] = p.domain.signal(true);
                nodes[0] = new SceneNode().text("可重挂条目").control(control[0])
                        .bind(NodeBinding.value(visible[0], SceneNode::visible))
                        .bind(NodeBinding.value(enabled[0], SceneNode::enabled));
                nodes[1] = new SceneNode().text("另一条目");
                p.branch.set(Arrays.asList(nodes[0], nodes[1]));
                p.session.runtime().focusRouter().request(p.session.runtime().captureMount(p.editor));
            });
            require(p.requests.count("固定未变行 Café") == stable, "添加子树使无关行重复请求");
            p.domain.execute(() -> original[0] = p.session.runtime().captureMount(nodes[0]));
            p.update(() -> p.branch.set(Arrays.asList(nodes[1], nodes[0])));
            p.domain.execute(() -> require(original[0].active() && p.entry(nodes[1]).box.y < p.entry(nodes[0]).box.y,
                    "子序列重排丢失挂载或未更新实际位置"));
            p.update(() -> visible[0].set(false));
            p.domain.execute(() -> require(p.entry(nodes[0]) == null, "隐藏节点仍发布几何"));
            p.update(() -> { visible[0].set(true); enabled[0].set(false); });
            p.update(() -> { enabled[0].set(true); p.branch.set(Collections.emptyList()); });
            int requests = p.requests.count("可重挂条目"), measures = control[0].measures;
            PaintPlan empty = p.host.paint;
            p.update(() -> control[0].measure.set(17));
            require(p.host.paint == empty && control[0].measures == measures, "卸载后依赖仍唤醒旧节点");
            p.update(() -> p.branch.set(Collections.singletonList(nodes[0])));
            p.domain.execute(() -> require(!original[0].active() && p.entry(nodes[0]).text.originalText.equals("可重挂条目"), "重挂未结束旧token或未建立当前正文"));
            require(p.requests.count("可重挂条目") > requests, "同节点重挂复用了旧挂载请求");
            p.session.edit(SceneEditEvent.text("X")); p.session.frame();
            p.domain.execute(() -> require(p.draft.text().get().contains("X")
                    && p.session.runtime().focusRouter().focused().get() == p.editor, "无关结构更新破坏草稿或焦点"));
            require(p.requests.count("固定未变行 Café") == stable, "结构或显隐资格变化清理了无关文字");
        }
    }
    private void moveScope() {
        try (Page p = new Page()) {
            SceneNode[] node = {null}; MountHandle[] handle = {null}; int stable = p.requests.count("固定未变行 Café");
            p.update(() -> { node[0] = new SceneNode().size(Size.fill(1), Size.natural()).text("连续移动的正文"); p.branch.set(Collections.singletonList(node[0])); });
            int calls = p.requests.count("连续移动的正文");
            p.domain.execute(() -> handle[0] = p.session.runtime().captureMount(node[0]));
            p.update(() -> handle[0].moveTo(p.destination));
            p.domain.execute(() -> require(handle[0].active() && node[0].parent() == p.destination
                    && p.entry(node[0]).text.originalText.equals("连续移动的正文"), "跨父移动丢失连续挂载或当前正文"));
            require(p.requests.count("连续移动的正文") == calls && p.requests.count("固定未变行 Café") == stable,
                    "同约束跨父移动清理了仍有效的节点请求");
        }
    }
    @SuppressWarnings("unchecked")
    private void documentScope() {
        Signal<TextContent>[] content = new Signal[1]; SceneNode[] paragraph = {null}; List<String> actions = new ArrayList<>();
        try (Page p = new Page((page, context) -> {
            content[0] = page.domain.signal(TextContent.richTags("<a href=\"next\">link</a>"));
            paragraph[0] = SceneDocument.paragraph(context, content[0], page.domain.signal(SceneTheme.light()), null, actions::add);
            page.root.add(paragraph[0]);
        })) {
            int stable = p.requests.count("固定未变行 Café");
            final LayoutBox[] box = {null}; p.domain.execute(() -> box[0] = p.entry(paragraph[0]).box);
            p.session.pointer(ScenePointerEvent.down(0, box[0].x + 2, box[0].y + 2)); p.session.frame();
            p.update(() -> content[0].set(TextContent.richTags(content[0].get().source)));
            p.session.pointer(ScenePointerEvent.up(0, box[0].x + 2, box[0].y + 2)); p.session.frame();
            require(actions.isEmpty(), "同源码新声明复活旧链接手势");
            p.session.pointer(ScenePointerEvent.syntheticActivation(box[0].x + 2, box[0].y + 2)); p.session.frame();
            require(actions.equals(Collections.singletonList("next")), "新声明未建立当前链接目标");
            require(p.requests.count("固定未变行 Café") == stable, "同源码文档通知未保留节点来源");
        }
    }
    @SuppressWarnings("unchecked")
    private void imageScope() throws Exception {
        Signal<ImageSource>[] source = new Signal[1]; SceneNode[] image = {null};
        Signal<String>[] backupText = new Signal[1];
        // 位图输入已携带真实像素；共享服务仍通过正式有界worker及drain发布生命周期。
        try (SharedImageService service = new SharedImageService(value -> () -> value.bitmap, SharedImageService.Budget.defaults());
                Page p = new Page((page, context) -> {
                    source[0] = page.domain.signal(ImageSource.bitmap(new ImageContent(4, 3, new byte[4 * 3 * 4])));
                    backupText[0] = page.domain.signal("未就绪备用");
                    SceneNode backupNode = new SceneNode().bind(NodeBinding.value(backupText[0], SceneNode::text));
                    image[0] = SceneImage.create(service, source[0], page.domain.signal(ImageOptions.defaults()), backupNode);
                    page.root.add(image[0]).add(new SceneNode().size(Size.fixed(1), Size.fixed(400)));
                })) {
            awaitImage(p, service, image[0]);
            int stable = p.requests.count("固定未变行 Café");
            int backup = p.requests.count("未就绪备用");
            require(backup > 0, "真实READY图像没有沿正式绘制捕获自定义备用子树");
            // READY隐藏备用树仍挂载；旁行重排不能按主几何访问集合清理其有效文字。
            p.update(() -> p.value.set("旁行更新保留READY备用"));
            require(p.requests.count("未就绪备用") == backup, "主布局清理了仍挂载READY备用树的有效请求");
            p.session.pointer(ScenePointerEvent.scroll(20, 20, -1)); p.session.frame();
            p.domain.execute(() -> require(p.session.runtime().scrollOffset(p.root) > 0, "正式滚动未生效"));
            require(p.requests.count("未就绪备用") == backup, "滚动重排清理了READY备用的有效请求");
            p.update(() -> backupText[0].set("当前READY备用内容"));
            require(p.requests.count("当前READY备用内容") > 0 && p.host.paint.commands().stream()
                    .anyMatch(command -> command.pendingPaint != null && command.pendingPaint.commands().stream()
                            .anyMatch(child -> child.text != null && child.text.originalText.equals("当前READY备用内容"))),
                    "隐藏备用来源更新未进入当代图像备用PaintPlan");
            p.update(() -> source[0].set(ImageSource.bitmap(new ImageContent(13, 7, new byte[13 * 7 * 4]))));
            awaitImage(p, service, image[0]);
            p.domain.execute(() -> require(SceneImage.state(image[0]).get().content.width == 13
                    && p.entry(image[0]).box.width == 13 && p.entry(image[0]).box.height == 7, "图像新资源未更新自然盒"));
            require(p.requests.count("固定未变行 Café") == stable, "内部图像通知未保留所属节点来源");
        }
    }
    private void awaitImage(Page p, SharedImageService service, SceneNode image) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        boolean[] ready = {false};
        do {
            service.drain(); p.session.frame();
            p.domain.execute(() -> ready[0] = SceneImage.state(image).get().status == ImageState.Status.READY);
            if (ready[0]) return;
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("正式图像worker未就绪");
    }
    private List<WeakReference<SceneNode>> removedBranch(Page p) {
        List<SceneNode> nodes = new ArrayList<>();
        p.domain.execute(() -> { for (int i = 0; i < 12; i++) nodes.add(new SceneNode().text("生命周期条目" + i)); });
        p.update(() -> p.branch.set(nodes));
        List<WeakReference<SceneNode>> refs = new ArrayList<>();
        for (SceneNode node : nodes) refs.add(new WeakReference<>(node));
        p.update(() -> p.branch.set(Collections.emptyList()));
        return refs;
    }
    private static final class ClosedPage {
        final Object pipeline;
        final WeakReference<SceneNode> root;
        ClosedPage(Object pipeline, SceneNode root) { this.pipeline = pipeline; this.root = new WeakReference<>(root); }
    }
    private ClosedPage closedPage() {
        Page page = new Page();
        ClosedPage[] closed = {null};
        page.domain.execute(() -> closed[0] = new ClosedPage(page.session.pipeline(), page.root));
        page.close();
        return closed[0];
    }
    private void releasedNodes() {
        try (Page p = new Page()) {
            List<WeakReference<SceneNode>> refs = new ArrayList<>();
            for (int i = 0; i < 3; i++) refs.addAll(removedBranch(p));
            for (int i = 0; i < 3; i++) { System.gc(); System.runFinalization(); }
            require(refs.stream().allMatch(ref -> ref.get() == null), "退出分支仍被活动页面持续持有");
        }
        ClosedPage closed = closedPage();
        for (int i = 0; i < 3; i++) { System.gc(); System.runFinalization(); }
        require(closed.pipeline != null && closed.root.get() == null, "关闭管线仍被调用方持有时没有释放旧树");
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
