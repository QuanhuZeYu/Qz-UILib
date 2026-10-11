package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.edit.SceneEditState.Mode;
import club.heiqi.uilib.ui.scene.edit.SceneEditState.Snapshot;
import club.heiqi.uilib.ui.scene.edit.SceneEditState.Submission;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneCompositionSource;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** editing spec 2：正式无平台页面来源隔离；不声称真实 SDL 或原生 IME 验收。 */
public final class CompositionIdentityScenario extends TestApplication {
    /** 只提供无native的字体边界事实；没有光标、选区、历史或编辑算法。真实字体另在GPU应用验收。 */
    private static final class PlainHost implements SceneHost, SceneTextMeasurer {
        int width = 360, height = 280;

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public long metricsRevision() {
            return 0;
        }

        public void replay(PaintPlan plan) {}

        public TextLayout layout(String text, TextStyle style, int width) {
            return layout(text, style, width, TextPolicy.NORMAL);
        }

        public TextLayout layout(String text, TextStyle style, int width, TextPolicy policy) {
            List<TextLayout.Line> lines = new ArrayList<>();
            float cell = style.size / 2f, height = style.size + 2, max = 0;
            int capacity = Math.max(1, (int) (width / cell));
            int start = 0;
            for (; ; ) {
                int newline = text.indexOf('\n', start),
                        end = newline < 0 ? text.length() : newline;
                int part = start;
                do {
                    int stop = Math.min(end, part + capacity);
                    List<TextLayout.Cluster> clusters = new ArrayList<>();
                    for (int i = part; i < stop; i++)
                        clusters.add(
                                new TextLayout.Cluster(
                                        i, i + 1, i, i + 1, 0, 0, (i - part) * cell, cell, style));
                    float advance = (stop - part) * cell;
                    int y = Math.round(lines.size() * height);
                    lines.add(
                            new TextLayout.Line(
                                    text.substring(part, stop),
                                    0,
                                    y,
                                    part,
                                    stop,
                                    part,
                                    stop,
                                    advance,
                                    y + style.size * .75f,
                                    style.size * .75f,
                                    style.size * .25f,
                                    height,
                                    Collections.emptyList(),
                                    clusters,
                                    Collections.emptyList()));
                    max = Math.max(max, advance);
                    part = stop;
                } while (part < end);
                if (newline < 0) break;
                start = newline + 1;
            }
            return new TextLayout(
                    (int) Math.ceil(max),
                    (int) Math.ceil(lines.size() * height),
                    max,
                    lines.size() * height,
                    style,
                    lines,
                    text,
                    text,
                    0,
                    Collections.emptyList());
        }
    }

    private final class Page implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final PlainHost host = new PlainHost();
        final List<Submission> submissions = new ArrayList<>();
        final Mode mode;
        SceneEditState first, second;
        SceneNode firstNode, secondNode, root;
        Signal<Boolean> readOnly, enabled, visible;
        Signal<List<SceneNode>> children;
        Signal<SceneTheme> theme;
        final ScenePageSession session;

        Page(Mode mode, SceneTextField.SubmitPolicy policy) {
            this.mode = mode;
            session = ScenePageSession.open(domain, host, host, ctx -> {
                first = new SceneEditState(domain, "甲", mode);
                second = new SceneEditState(domain, "乙", mode);
                readOnly = domain.signal(false);
                enabled = domain.signal(true);
                visible = domain.signal(true);
                theme = domain.signal(SceneTheme.light());
                firstNode = field(ctx, first, policy)
                        .bind(NodeBinding.value(enabled, SceneNode::enabled))
                        .bind(NodeBinding.value(visible, SceneNode::visible));
                secondNode = field(ctx, second, SceneTextField.SubmitPolicy.CONFIRM);
                children = domain.signal(Arrays.asList(firstNode, secondNode));
                root = new SceneNode().size(Size.fill(1), Size.fill(1))
                        .initialFocus(firstNode).bind(NodeBinding.children(children));
                ctx.mount(root);
            });
            onClose(session::close);
            session.frame();
        }

        SceneNode field(ScenePageContext ctx, SceneEditState state, SceneTextField.SubmitPolicy policy) {
            return (mode == Mode.SINGLE_LINE
                    ? SceneTextField.create(ctx, state, theme, readOnly, submissions::add, policy)
                    : SceneTextArea.create(ctx, state, theme, readOnly, submissions::add, policy))
                    .size(Size.fill(1), Size.fixed(70));
        }

        void edit(SceneEditEvent event) {
            session.edit(event);
            session.frame();
        }

        SceneCompositionSource begin(String candidate) {
            SceneCompositionSource source = new SceneCompositionSource();
            edit(SceneEditEvent.compositionBegin(source, candidate));
            return source;
        }

        void focus(SceneNode node) {
            domain.execute(() -> require(session.runtime().focusRouter()
                    .request(session.runtime().captureMount(node)), "合法焦点请求失败"));
        }

        Snapshot snapshot(SceneEditState state) {
            Snapshot[] result = {null};
            domain.execute(() -> result[0] = state.snapshot().get());
            return result[0];
        }

        void expect(SceneEditState state, String expected) {
            require(expected.equals(snapshot(state).text), "草稿预期=" + expected
                    + " 实际=" + snapshot(state).text);
        }

        void fresh(String text, String expected) {
            SceneCompositionSource source = begin("新候选");
            edit(SceneEditEvent.compositionCommit(source, text));
            expect(first, expected);
            require(!snapshot(first).composing, "新提交未清理候选呈现");
        }

        public void close() {
            session.close();
        }
    }

    protected void runTests() throws Exception {
        for (Mode mode : Mode.values()) {
            scenario(mode + " 焦点往返旧联系与正常BLUR", () -> focusRoundTrip(mode));
            scenario(mode + " 替换独立状态和同节点重挂", () -> replaceAndRemount(mode));
            scenario(mode + " reset cancel及只读禁用hidden失效", () -> invalidation(mode));
            scenario(mode + " 合法重排普通草稿修改空preedit与原子普通文本", () -> continuity(mode));
            scenario(mode + " 无合法目标BEGIN不可复活且ESC取消不取消草稿", () -> unavailableAndEscape(mode));
            scenario(mode + " 跨页面不能复用旧来源", () -> acrossPages(mode));
        }
    }

    private void focusRoundTrip(Mode mode) {
        try (Page p = new Page(mode, SceneTextField.SubmitPolicy.BLUR)) {
            p.edit(SceneEditEvent.text("草稿"));
            SceneCompositionSource old = p.begin("未确认");
            p.session.navigation(SceneNavigationEvent.NEXT);
            p.session.edit(SceneEditEvent.compositionCommit(old, "旧候选"));
            p.session.frame();
            p.expect(p.first, "甲草稿");
            p.expect(p.second, "乙");
            require(p.submissions.size() == 1 && p.submissions.get(0).text.equals("甲草稿"),
                    "正常BLUR只应提交已有草稿，不确认候选");
            p.session.navigation(SceneNavigationEvent.PREVIOUS);
            p.session.edit(SceneEditEvent.compositionUpdate(old, "迟到", true));
            p.session.edit(SceneEditEvent.compositionBegin(old, "重复BEGIN"));
            p.session.frame();
            require(!p.snapshot(p.first).composing, "返回焦点复活旧候选");
            SceneCompositionSource current = p.begin("新候选");
            p.edit(SceneEditEvent.compositionCommit(old, "旧提交不能结束新候选"));
            require(p.snapshot(p.first).composing, "旧提交清掉新来源候选");
            p.edit(SceneEditEvent.compositionCommit(current, "新"));
            p.expect(p.first, "甲草稿新");
        }
    }

    private void replaceAndRemount(Mode mode) {
        try (Page p = new Page(mode, SceneTextField.SubmitPolicy.BLUR)) {
            p.edit(SceneEditEvent.text("保留"));
            SceneCompositionSource old = p.begin("旧");
            p.domain.execute(() -> p.children.set(Collections.singletonList(p.secondNode)));
            p.session.edit(SceneEditEvent.compositionCommit(old, "旧候选"));
            p.session.frame();
            p.expect(p.first, "甲保留");
            p.expect(p.second, "乙");
            require(p.submissions.isEmpty(), "结构退出不得隐含BLUR提交");
            p.focus(p.secondNode);
            p.edit(SceneEditEvent.text("新对象"));
            p.expect(p.second, "乙新对象");
            p.domain.execute(() -> p.children.set(Arrays.asList(p.firstNode, p.secondNode)));
            p.session.frame();
            p.focus(p.firstNode);
            p.edit(SceneEditEvent.compositionCommit(old, "重挂迟到"));
            p.expect(p.first, "甲保留");
            p.edit(SceneEditEvent.command(SceneEditEvent.Command.UNDO, false, null));
            p.expect(p.first, "甲");
            p.fresh("新", "甲新");
        }
    }

    private void invalidation(Mode mode) {
        try (Page p = new Page(mode, SceneTextField.SubmitPolicy.CONFIRM)) {
            SceneCompositionSource old = p.begin("重置前");
            p.domain.execute(() -> p.first.reset("新基线"));
            p.edit(SceneEditEvent.compositionCommit(old, "旧候选"));
            p.expect(p.first, "新基线");
            p.fresh("新", "新基线新");
            old = p.begin("取消前");
            p.domain.execute(() -> p.first.cancel());
            p.edit(SceneEditEvent.compositionCommit(old, "旧候选"));
            p.expect(p.first, "新基线");
            p.fresh("新", "新基线新");
            String expected = "新基线新";
            for (Signal<Boolean> eligibility : Arrays.asList(p.readOnly, p.enabled, p.visible)) {
                old = p.begin("失活前");
                boolean locked = eligibility == p.readOnly;
                p.domain.execute(() -> eligibility.set(locked));
                p.session.frame();
                require(!p.snapshot(p.first).composing, "失活未清候选呈现");
                p.domain.execute(() -> eligibility.set(!locked));
                p.session.frame();
                p.focus(p.firstNode);
                p.edit(SceneEditEvent.compositionCommit(old, "迟到"));
                p.expect(p.first, expected);
                SceneCompositionSource current = p.begin("可继续");
                p.edit(SceneEditEvent.compositionCancel(current));
                p.edit(SceneEditEvent.compositionCommit(current, "取消后迟到"));
                p.expect(p.first, expected);
                p.fresh("续", expected + "续");
                expected += "续";
            }
            require(p.submissions.isEmpty(), "失活/reset/cancel不得请求业务提交");
        }
    }

    private void continuity(Mode mode) {
        try (Page p = new Page(mode, SceneTextField.SubmitPolicy.CONFIRM)) {
            SceneCompositionSource source = p.begin("连续");
            p.domain.execute(() -> {
                p.children.set(Arrays.asList(p.secondNode, p.firstNode));
                p.first.replaceSelection("普通", false);
            });
            p.edit(SceneEditEvent.compositionUpdate(source, "", false));
            p.edit(SceneEditEvent.compositionCommit(source, "a\r\nb\rc\n"));
            String expected = mode == Mode.MULTI_LINE ? "甲普通a\nb\nc\n" : "甲普通abc";
            p.expect(p.first, expected);
            p.edit(SceneEditEvent.text("常规"));
            p.edit(SceneEditEvent.committed("原子"));
            p.expect(p.first, expected + "常规原子");
            p.edit(SceneEditEvent.command(SceneEditEvent.Command.UNDO, false, null));
            p.expect(p.first, expected + "常规");
            require(p.submissions.isEmpty(), "连续组字不得隐含确认业务");
        }
    }

    private void acrossPages(Mode mode) {
        SceneCompositionSource old;
        try (Page first = new Page(mode, SceneTextField.SubmitPolicy.CONFIRM)) {
            first.edit(SceneEditEvent.text("保留"));
            old = first.begin("原页候选");
        }
        try (Page next = new Page(mode, SceneTextField.SubmitPolicy.CONFIRM)) {
            next.edit(SceneEditEvent.compositionBegin(old, "跨页重复BEGIN"));
            next.edit(SceneEditEvent.compositionCommit(old, "跨页旧提交"));
            next.expect(next.first, "甲");
            next.fresh("新", "甲新");
        }
    }

    private void unavailableAndEscape(Mode mode) {
        try (Page p = new Page(mode, SceneTextField.SubmitPolicy.CONFIRM)) {
            p.session.focusLost();
            SceneCompositionSource absent = new SceneCompositionSource();
            p.session.edit(SceneEditEvent.compositionBegin(absent, "无目标"));
            p.session.frame();
            p.focus(p.firstNode);
            p.edit(SceneEditEvent.compositionBegin(absent, "重复"));
            p.edit(SceneEditEvent.compositionCommit(absent, "旧"));
            p.expect(p.first, "甲");
            p.edit(SceneEditEvent.text("草稿"));
            SceneCompositionSource source = p.begin("取消候选");
            p.session.escape(() -> { throw new AssertionError("候选ESC不应进入外层"); });
            p.session.frame();
            p.edit(SceneEditEvent.compositionCommit(source, "旧"));
            p.expect(p.first, "甲草稿");
            require(p.snapshot(p.first).active && !p.snapshot(p.first).composing,
                    "候选ESC不能取消独立草稿会话");
            p.fresh("新", "甲草稿新");
            require(p.submissions.isEmpty(), "候选ESC不得业务提交");
        }
    }

    public static void main(String[] args) throws Exception {
        new CompositionIdentityScenario().run(args);
    }
}
