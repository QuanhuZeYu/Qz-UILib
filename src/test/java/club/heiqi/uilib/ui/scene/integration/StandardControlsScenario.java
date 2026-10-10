package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneTextPolicy;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.font.FontSource;

/** 仅替换宿主字体事实；正式页面负责绑定、布局、路由与提示计划。 */
public final class StandardControlsScenario extends TestApplication {
    private static final String LONG = "动态主题下的完整按钮文案需要换行并保留所有内容供悬停及键盘聚焦查看";

    /** 可观察的字体边界，非控件呈现镜像：每字一个簇，行高随本行度量变化。 */
    private static final class TextHost implements SceneHost, SceneTextMeasurer {
        final FontSource font =
                new FontSource(
                        "fixture", 0, "fixture", new byte[] {1}, 0, false, Collections.emptySet());
        final List<Request> requests = new ArrayList<>();
        int width = 360, height = 320;
        PaintPlan last;

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public long metricsRevision() {
            return 0;
        }

        @Override
        public void replay(PaintPlan plan) {
            last = plan;
        }

        @Override
        public TextLayout layout(String text, TextStyle style, int width) {
            return layout(text, style, width, TextPolicy.NORMAL);
        }

        @Override
        public TextLayout layout(String text, TextStyle style, int width, TextPolicy policy) {
            requests.add(new Request(text, style.size, width, policy.wrap));
            List<TextLayout.Line> lines = new ArrayList<>();
            int start = 0, y = 0;
            float maximum = 0;
            while (start < text.length()) {
                int end = start;
                float advance = 0;
                while (end < text.length() && text.charAt(end) != '\n') {
                    float next = advance(text.charAt(end), style);
                    if (policy.wrap != TextPolicy.Wrap.NONE
                            && end > start
                            && advance + next > Math.max(0, width)) break;
                    advance += next;
                    end++;
                }
                float ascent = style.size * .75f, descent = style.size - ascent;
                float lineHeight =
                        policy.lineHeight > 0
                                ? policy.lineHeight
                                : style.size + 2 + (lines.size() % 2) * 3;
                List<TextLayout.Glyph> glyphs = new ArrayList<>();
                List<TextLayout.Cluster> clusters = new ArrayList<>();
                float x = 0;
                for (int i = start; i < end; i++) {
                    float step = advance(text.charAt(i), style);
                    int gi = glyphs.size();
                    glyphs.add(
                            new TextLayout.Glyph(
                                    font,
                                    style.size,
                                    text.charAt(i),
                                    i,
                                    i + 1,
                                    i,
                                    i + 1,
                                    x,
                                    y + ascent,
                                    step,
                                    0,
                                    false,
                                    false));
                    clusters.add(
                            new TextLayout.Cluster(i, i + 1, i, i + 1, gi, gi + 1, x, step, style));
                    x += step;
                }
                lines.add(
                        new TextLayout.Line(
                                text.substring(start, end),
                                0,
                                y,
                                start,
                                end,
                                start,
                                end,
                                advance,
                                y + ascent,
                                ascent,
                                descent,
                                lineHeight,
                                glyphs,
                                clusters,
                                Collections.emptyList()));
                maximum = Math.max(maximum, advance);
                y += (int) Math.ceil(lineHeight);
                start = end < text.length() && text.charAt(end) == '\n' ? end + 1 : end;
            }
            return new TextLayout(
                    (int) Math.ceil(maximum),
                    y,
                    maximum,
                    y,
                    style,
                    lines,
                    text,
                    text,
                    0,
                    Collections.emptyList());
        }

        private float advance(char c, TextStyle style) {
            return c < 128 ? style.size * .5f : style.size;
        }
    }

    private static final class Request {
        final String text;
        final int size, width;
        final TextPolicy.Wrap wrap;

        Request(String text, int size, int width, TextPolicy.Wrap wrap) {
            this.text = text;
            this.size = size;
            this.width = width;
            this.wrap = wrap;
        }
    }

    @Override
    protected void runTests() throws Exception {
        scenario("派生主题首挂及同页换色保持挂载焦点滚动，文案字号内边距驱动自然高度", this::dynamicTheme);
        scenario("限高仅完整行省略，提示完整且末端绘制，空间及主题变化重新度量", this::boundedText);
        scenario("正式按钮按下释放和Tab激活，禁用取消及模态隔离提示与业务事件", this::inputAndRanges);
        scenario("公开单行省略与溢出策略使用正式文字空间", this::policies);
        scenario("Enter和空格按压门等待历史释放且repeat一次", this::keyPressSegments);
        scenario("业务目标激活不抢焦点且屏外按钮不投影提示", this::activationAndViewport);
        scenario("全部鼠标释放清除按下颜色与失活owner呈现", this::releasePresentation);
    }

    private void releasePresentation() {
        ReactiveDomain domain = ReactiveDomain.direct();
        TextHost host = new TextHost();
        SceneNode[] button = {null};
        Signal<Boolean>[] actionEnabled = new Signal[1];
        ScenePageSession page =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            actionEnabled[0] = domain.signal(true);
                            button[0] =
                                    SceneButton.create(
                                                    ctx,
                                                    () -> "按钮",
                                                    domain.signal(SceneTheme.light()),
                                                    () -> {})
                                            .size(Size.fixed(100), Size.fixed(50))
                                            .bind(
                                                    NodeBinding.value(
                                                            actionEnabled[0],
                                                            (node, value) ->
                                                                    node.onClick(
                                                                            value
                                                                                    ? () -> {}
                                                                                    : null)));
                            ctx.mount(button[0]);
                        });
        onClose(page::close);
        page.frame();
        page.pointer(ScenePointerEvent.down(0, 5, 5));
        page.frame();
        require(
                host.last.commands().stream()
                        .anyMatch(
                                c ->
                                        c.kind == PaintCommand.Kind.RECT
                                                && c.color == SceneTheme.light().pressed),
                "按下未呈现");
        page.pointer(ScenePointerEvent.allButtonsReleased());
        page.frame();
        domain.execute(
                () -> require(!page.runtime().pointerRouter().pressed(button[0]), "物理全释放未清owner"));
        require(
                host.last.commands().stream()
                        .noneMatch(
                                c ->
                                        c.kind == PaintCommand.Kind.RECT
                                                && c.color == SceneTheme.light().pressed),
                "全释放按下色缓存残留");
        page.pointer(ScenePointerEvent.down(0, 5, 5));
        page.frame();
        domain.execute(() -> actionEnabled[0].set(false));
        page.frame();
        require(
                host.last.commands().stream()
                        .noneMatch(
                                c ->
                                        c.kind == PaintCommand.Kind.RECT
                                                && c.color == SceneTheme.light().pressed),
                "handler撤销按下色缓存残留");
    }

    private void activationAndViewport() {
        ReactiveDomain domain = ReactiveDomain.direct();
        TextHost host = new TextHost();
        int[] clicks = {0};
        SceneNode[] buttons = new SceneNode[2];
        ScenePageSession page =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            Signal<SceneTheme> theme = domain.signal(SceneTheme.light());
                            buttons[0] =
                                    SceneButton.create(ctx, () -> "首按钮", theme, () -> clicks[0]++);
                            buttons[1] =
                                    SceneButton.create(ctx, () -> LONG, theme, () -> clicks[0]++)
                                            .size(Size.fixed(80), Size.fixed(50));
                            ctx.mount(
                                    new SceneNode()
                                            .size(Size.fixed(100), Size.natural())
                                            .add(buttons[0])
                                            .add(
                                                    new SceneNode()
                                                            .size(
                                                                    Size.fixed(100),
                                                                    Size.fixed(host.height + 1)))
                                            .add(buttons[1]));
                        });
        onClose(page::close);
        page.frame();
        domain.execute(
                () -> {
                    require(page.runtime().focusRouter().focused().get() == buttons[0], "初始首按钮");
                    page.runtime().dispatchClick(buttons[1]);
                    require(clicks[0] == 1, "synthetic业务激活未执行");
                    require(
                            page.runtime().focusRouter().focused().get() == buttons[0],
                            "按业务目标激活抢焦点");
                    page.runtime().focusRouter().request(page.runtime().captureMount(buttons[1]));
                });
        page.frame();
        require(
                host.last.commands().stream()
                        .noneMatch(
                                c ->
                                        c.kind == PaintCommand.Kind.RECT
                                                && c.color == SceneTheme.light().tooltip),
                "完全屏外按钮生成可见提示");
    }

    private void keyPressSegments() {
        int[] count = {0};
        club.heiqi.uilib.ui.scene.host.SceneKeyPressInputAdapter key =
                new club.heiqi.uilib.ui.scene.host.SceneKeyPressInputAdapter(() -> count[0]++);
        key.availability(true);
        key.key(true, false);
        require(count[0] == 0, "开页持键不可激活");
        key.historyDrained(true);
        key.key(false, false);
        key.key(true, false);
        require(count[0] == 0, "未完成历史耗尽门");
        key.historyDrained(false);
        key.key(true, false);
        key.key(true, true);
        key.key(true, false);
        require(count[0] == 1, "同段重复激活");
        key.key(false, false);
        key.key(true, false);
        require(count[0] == 2, "新段未激活");
        key.availability(false);
        key.key(false, false);
        key.availability(true);
        key.key(true, false);
        require(count[0] == 2, "恢复旧尾部误激活");
        key.historyDrained(false);
        key.key(true, false);
        require(count[0] == 3, "恢复后新段未激活");
    }

    private void dynamicTheme() {
        ReactiveDomain domain = ReactiveDomain.direct();
        TextHost host = new TextHost();
        Signal<Boolean>[] dark = new Signal[1];
        Signal<Integer>[] size = new Signal[1], padding = new Signal[1];
        Signal<String>[] text = new Signal[1];
        SceneNode[] root = new SceneNode[1], button = new SceneNode[1], label = new SceneNode[1];
        MountHandle[] mount = new MountHandle[1];
        int[] builds = {0};
        ScenePageSession session =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            builds[0]++;
                            dark[0] = domain.signal(false);
                            size[0] = domain.signal(18);
                            padding[0] = domain.signal(8);
                            text[0] = domain.signal("短文案");
                            Computed<SceneTheme> theme =
                                    domain.computed(
                                            ctx.scope(),
                                            () -> {
                                                SceneTheme base =
                                                        dark[0].get()
                                                                ? SceneTheme.dark()
                                                                : SceneTheme.light();
                                                return withPadding(
                                                        base.withTextStyle(
                                                                new TextStyle(
                                                                        "fixture",
                                                                        size[0].get(),
                                                                        base.textStyle.color)),
                                                        padding[0].get());
                                            });
                            label[0] =
                                    SceneLabel.create(ctx, text[0], theme)
                                            .size(Size.fill(1), Size.natural());
                            button[0] = SceneButton.create(ctx, text[0], theme, () -> {});
                            root[0] =
                                    new SceneNode()
                                            .size(Size.fixed(120), Size.fixed(120))
                                            .scrollable(true)
                                            .scrollOffset(12)
                                            .add(label[0])
                                            .add(button[0])
                                            .add(
                                                    new SceneNode()
                                                            .size(Size.fill(1), Size.fixed(400)));
                            ctx.mount(root[0]);
                            mount[0] = ctx.runtime().captureMount(button[0]);
                        });
        onClose(session::close);
        session.frame();
        int[] beforeHeight = {0}, beforeScroll = {0};
        domain.execute(
                () -> {
                    require(
                            label[0].style().size == 18
                                    && label[0].style().color == SceneTheme.light().textStyle.color,
                            "派生主题首次挂载无有效值");
                    require(
                            !label[0].focusable()
                                    && !new SceneNode().focusable()
                                    && button[0].focusable()
                                    && button[0].clickFocus(),
                            "标准与普通节点焦点默认语义错误");
                    session.runtime().focusRouter().request(mount[0]);
                    beforeHeight[0] = entry(session, button[0]).box.height;
                    beforeScroll[0] = entry(session, root[0]).scrollOffset;
                    dark[0].set(true);
                });
        session.frame();
        domain.execute(
                () -> {
                    require(
                            mount[0].active()
                                    && builds[0] == 1
                                    && session.runtime().focusRouter().focused().get() == button[0],
                            "换色重建或丢焦点");
                    require(
                            entry(session, root[0]).scrollOffset == beforeScroll[0]
                                    && beforeScroll[0] > 0,
                            "换色丢滚动位置");
                    require(
                            entry(session, button[0]).box.height == beforeHeight[0]
                                    && hasColor(host.last, SceneTheme.dark().button),
                            "换色未更新计划或改变自然高度");
                    text[0].set(LONG);
                });
        session.frame();
        int[] grown = {0};
        domain.execute(
                () -> {
                    grown[0] = entry(session, button[0]).box.height;
                    require(
                            grown[0] > beforeHeight[0]
                                    && entry(session, label[0]).text.lines().size() > 1,
                            "动态文案未自然换行增高");
                    size[0].set(24);
                    padding[0].set(12);
                });
        session.frame();
        domain.execute(
                () ->
                        require(
                                entry(session, button[0]).box.height > grown[0]
                                        && button[0].padding() == 12
                                        && entry(session, label[0]).text.style.size == 24
                                        && mount[0].active(),
                                "字号内边距变化未更新实际几何"));
    }

    private void boundedText() {
        ReactiveDomain domain = ReactiveDomain.direct();
        TextHost host = new TextHost();
        Signal<SceneTheme>[] theme = new Signal[1];
        Signal<Integer>[] limit = new Signal[1];
        SceneNode[] button = new SceneNode[1];
        ScenePageSession session =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            theme[0] = domain.signal(SceneTheme.light());
                            limit[0] = domain.signal(60);
                            button[0] =
                                    SceneButton.create(ctx, domain.signal(LONG), theme[0], () -> {})
                                            .bind(
                                                    NodeBinding.value(
                                                            limit[0],
                                                            (n, h) ->
                                                                    n.size(
                                                                            Size.fixed(120),
                                                                            Size.natural()
                                                                                    .bounds(
                                                                                            0,
                                                                                            h))));
                            ctx.mount(
                                    new SceneNode()
                                            .size(Size.fixed(120), Size.natural())
                                            .add(button[0])
                                            .add(
                                                    new SceneNode()
                                                            .size(Size.fixed(120), Size.fixed(10))
                                                            .background(0xFF123456)));
                        });
        onClose(session::close);
        session.frame();
        domain.execute(
                () -> {
                    SceneGeometryView.Entry e = entry(session, button[0]);
                    require(e.box.height == 60, "最大高度约束未应用");
                    assertCompleteLines(e, button[0].padding());
                    require(ellipsis(e.text), "受限换行缺少真实省略glyph");
                    require(LONG.equals(e.text.originalText), "正文计划丢完整源文");
                });
        // 初始范围默认已选首个按钮；清焦点后单独验证hover提示不改变owner。
        domain.execute(() -> session.runtime().focusRouter().clear());
        session.frame();
        PaintPlan[] plain = {host.last};
        SceneGeometry[] geometry = new SceneGeometry[1];
        domain.execute(() -> geometry[0] = session.pipeline().current().geometry);
        session.pointer(ScenePointerEvent.move(5, 5));
        session.frame();
        domain.execute(
                () -> {
                    require(
                            session.pipeline().current().geometry == geometry[0],
                            "pointer MOVE触发几何重排");
                    require(host.last != plain[0], "悬停未使paint失效");
                    assertTooltip(host, LONG, SceneTheme.light());
                    require(entry(session, button[0]).box.height == 60, "提示占用正文几何");
                    require(session.runtime().focusRouter().focused().get() == null, "被动提示抢焦点");
                });
        int requests = host.requests.size();
        host.width = 260;
        session.frame();
        domain.execute(
                () -> {
                    assertTooltip(host, LONG, SceneTheme.light());
                    require(
                            host.requests.size() > requests && requested(host, LONG, 18, 244),
                            "宿主大小变化未重新量提示");
                    theme[0].set(
                            SceneTheme.dark()
                                    .withTextStyle(
                                            new TextStyle(
                                                    "fixture",
                                                    24,
                                                    SceneTheme.dark().textStyle.color)));
                });
        session.frame();
        domain.execute(
                () -> {
                    assertTooltip(host, LONG, theme[0].get());
                    require(requested(host, LONG, 24, 244), "主题字号变化未重新量提示");
                    limit[0].set(10);
                });
        session.frame();
        domain.execute(
                () -> {
                    require(entry(session, button[0]).text.lines().isEmpty(), "极小文字空间绘制了不完整行");
                    assertTooltip(host, LONG, theme[0].get());
                });
        session.pointer(ScenePointerEvent.move(300, 300));
        session.navigation(SceneNavigationEvent.NEXT);
        session.frame();
        domain.execute(
                () -> {
                    require(
                            session.runtime().focusRouter().focused().get() == button[0],
                            "Tab未聚焦极小按钮");
                    assertTooltip(host, LONG, theme[0].get());
                });
    }

    private void inputAndRanges() {
        ReactiveDomain domain = ReactiveDomain.direct();
        TextHost host = new TextHost();
        int[] clicks = {0};
        Signal<Boolean>[] enabled = new Signal[1], modal = new Signal[1];
        SceneNode[] button = new SceneNode[1];
        ScenePageSession session =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            enabled[0] = domain.signal(true);
                            modal[0] = domain.signal(false);
                            button[0] =
                                    SceneButton.create(
                                                    ctx,
                                                    domain.signal(LONG),
                                                    domain.signal(SceneTheme.light()),
                                                    () -> clicks[0]++)
                                            .size(Size.fixed(120), Size.fixed(60))
                                            .bind(
                                                    NodeBinding.value(
                                                            enabled[0], SceneNode::enabled));
                            ctx.mount(button[0]);
                            ctx.mount(
                                    new SceneNode()
                                            .modal(true)
                                            .size(Size.fixed(80), Size.fixed(80))
                                            .bind(NodeBinding.value(modal[0], SceneNode::visible)));
                        });
        onClose(session::close);
        session.frame();
        session.pointer(ScenePointerEvent.down(0, 10, 10));
        session.frame();
        domain.execute(
                () ->
                        require(
                                clicks[0] == 0
                                        && session.runtime().pointerRouter().pressed(button[0])
                                        && hasColor(host.last, SceneTheme.light().pressed),
                                "DOWN未显示权威pressed或提前发业务事件"));
        session.pointer(ScenePointerEvent.up(0, 10, 10));
        session.frame();
        domain.execute(
                () -> {
                    require(
                            clicks[0] == 1
                                    && session.runtime().focusRouter().focused().get() == button[0],
                            "UP未单次clickFocus");
                    session.runtime().focusRouter().clear();
                });
        session.pointer(ScenePointerEvent.move(300, 300));
        session.navigation(SceneNavigationEvent.NEXT);
        session.navigation(SceneNavigationEvent.ACTIVATE);
        session.frame();
        domain.execute(
                () -> {
                    require(clicks[0] == 2, "Tab ACTIVATE未复用单次业务click");
                    assertTooltip(host, LONG, SceneTheme.light());
                    session.runtime().focusRouter().clear();
                });
        session.pointer(ScenePointerEvent.down(0, 10, 10));
        session.pointer(ScenePointerEvent.cancel());
        session.frame();
        domain.execute(
                () ->
                        require(
                                !session.runtime().pointerRouter().pressed(button[0])
                                        && !hasRectColor(host.last, SceneTheme.light().tooltip),
                                "取消残留pressed或后台提示"));
        session.pointer(ScenePointerEvent.up(0, 10, 10));
        session.frame();
        domain.execute(
                () -> {
                    require(clicks[0] == 2, "取消尾部发布click");
                    enabled[0].set(false);
                });
        session.frame();
        session.pointer(ScenePointerEvent.move(10, 10));
        session.navigation(SceneNavigationEvent.NEXT);
        session.navigation(SceneNavigationEvent.ACTIVATE);
        session.frame();
        domain.execute(
                () -> {
                    require(
                            clicks[0] == 2
                                    && hasColor(host.last, SceneTheme.light().disabled)
                                    && hasColor(host.last, SceneTheme.light().disabledText)
                                    && !hasRectColor(host.last, SceneTheme.light().tooltip),
                            "禁用仍交互或提示/颜色错误");
                    enabled[0].set(true);
                });
        session.frame();
        session.pointer(ScenePointerEvent.move(10, 10));
        session.frame();
        domain.execute(
                () -> {
                    assertTooltip(host, LONG, SceneTheme.light());
                    modal[0].set(true);
                });
        session.frame();
        session.pointer(ScenePointerEvent.syntheticActivation(10, 10));
        session.navigation(SceneNavigationEvent.ACTIVATE);
        session.frame();
        domain.execute(
                () ->
                        require(
                                clicks[0] == 2
                                        && !hasRectColor(host.last, SceneTheme.light().tooltip),
                                "模态后方按钮抢输入或提示"));
    }

    private void policies() {
        ReactiveDomain domain = ReactiveDomain.direct();
        TextHost host = new TextHost();
        SceneNode[] single = new SceneNode[1], overflow = new SceneNode[1];
        ScenePageSession session =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            Signal<String> text = domain.signal(LONG);
                            Signal<SceneTheme> theme = domain.signal(SceneTheme.light());
                            single[0] =
                                    SceneButton.create(
                                                    ctx,
                                                    text,
                                                    theme,
                                                    () -> {},
                                                    SceneTextPolicy.ELLIPSIS_SINGLE_LINE)
                                            .size(Size.fixed(120), Size.natural());
                            overflow[0] =
                                    SceneButton.create(
                                                    ctx,
                                                    text,
                                                    theme,
                                                    () -> {},
                                                    SceneTextPolicy.OVERFLOW)
                                            .size(Size.fixed(120), Size.natural());
                            ctx.mount(
                                    new SceneNode()
                                            .size(Size.fixed(120), Size.natural())
                                            .add(single[0])
                                            .add(overflow[0]));
                        });
        onClose(session::close);
        session.frame();
        domain.execute(
                () -> {
                    TextLayout a = entry(session, single[0]).text,
                            b = entry(session, overflow[0]).text;
                    require(
                            a.lines().size() == 1 && ellipsis(a) && LONG.equals(a.originalText),
                            "单行策略未省略保留源文");
                    require(
                            b.lines().size() == 1
                                    && !ellipsis(b)
                                    && b.width > 120
                                    && LONG.equals(b.originalText),
                            "溢出策略意外裁切或换行");
                    require(
                            host.requests.stream()
                                    .anyMatch(
                                            r ->
                                                    LONG.equals(r.text)
                                                            && r.wrap == TextPolicy.Wrap.NONE),
                            "公开策略未传给字体边界");
                });
    }

    private static SceneTheme withPadding(SceneTheme t, int padding) {
        return new SceneTheme(
                t.textStyle,
                t.panel,
                t.button,
                t.hover,
                t.pressed,
                t.focus,
                t.disabled,
                t.disabledText,
                t.tooltip,
                t.tooltipText,
                padding,
                t.gap);
    }

    private static SceneGeometryView.Entry entry(ScenePageSession s, SceneNode n) {
        return s.pipeline().current().geometry.get(n);
    }

    private static boolean hasColor(PaintPlan plan, int color) {
        return plan.commands().stream().anyMatch(c -> c.color == color);
    }

    private static boolean hasRectColor(PaintPlan plan, int color) {
        return plan.commands().stream()
                .anyMatch(c -> c.kind == PaintCommand.Kind.RECT && c.color == color);
    }

    private static boolean ellipsis(TextLayout text) {
        return text.lines().stream()
                .flatMap(l -> l.glyphs().stream())
                .anyMatch(g -> g.glyphId == '…');
    }

    private static boolean requested(TextHost host, String text, int size, int width) {
        return host.requests.stream()
                .anyMatch(r -> r.text.equals(text) && r.size == size && r.width == width);
    }

    private static void assertCompleteLines(SceneGeometryView.Entry e, int padding) {
        for (TextLayout.Line line : e.text.lines())
            require(
                    line.y + line.lineHeight <= Math.max(0, e.box.height - padding * 2)
                            && line.baseline + line.descent
                                    <= Math.max(0, e.box.height - padding * 2),
                    "出现部分行或错误行高");
    }

    private static void assertTooltip(TextHost host, String text, SceneTheme theme) {
        List<PaintCommand> commands = host.last.commands();
        require(commands.size() >= 2, "没有提示命令");
        PaintCommand background = commands.get(commands.size() - 2),
                last = commands.get(commands.size() - 1);
        require(
                background.kind == PaintCommand.Kind.RECT
                        && background.color == theme.tooltip
                        && last.kind == PaintCommand.Kind.TEXT
                        && last.color == theme.tooltipText,
                "提示未在计划末端绘制");
        require(
                text.equals(last.text.originalText)
                        && text.equals(last.text.logicalText)
                        && last.text.lines().stream()
                                .map(l -> l.text)
                                .reduce("", String::concat)
                                .equals(text),
                "提示未保留并排版全量文案");
        require(
                background.box.x >= 0
                        && background.box.y >= 0
                        && background.box.x + background.box.width <= host.width
                        && background.box.y + background.box.height <= host.height,
                "提示超出真实host区域");
    }

    public static void main(String[] args) throws Exception {
        new StandardControlsScenario().run(args);
    }
}
