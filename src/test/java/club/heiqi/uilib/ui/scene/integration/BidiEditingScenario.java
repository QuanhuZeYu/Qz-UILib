package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayList;
import java.util.List;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;

/** 真实 FT/HB → 正式页面 pointer → 实际 caret 绘制；不复制控件命中算法。 */
public final class BidiEditingScenario extends TestApplication {
    private FontTextService fonts;
    private static final class Host implements SceneHost {
        int width = 420, height = 260;
        PaintPlan plan;
        public int width() { return width; }
        public int height() { return height; }
        public long metricsRevision() { return 0; }
        public void replay(PaintPlan plan) { this.plan = plan; }
    }
    private final class Page {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final Host host = new Host();
        final ScenePageSession session;
        SceneEditState state;
        SceneNode field, outer, button;
        Signal<SceneTheme> theme;
        Signal<Boolean> readOnly;
        Signal<Integer> beforeHeight;
        final List<SceneEditState.Submission> submissions = new ArrayList<>();
        Page(String text, boolean multi) {
            session = ScenePageSession.open(domain, host, fonts, ctx -> {
                SceneTheme base = SceneTheme.light();
                theme = domain.signal(new SceneTheme(new TextStyle("default", 24, base.textStyle.color),
                        base.panel, base.button, base.hover, base.pressed, base.focus,
                        base.disabled, base.disabledText, base.tooltip, base.tooltipText, 0, 0));
                readOnly = domain.signal(false);
                beforeHeight = domain.signal(0);
                state = new SceneEditState(domain, text, multi ? SceneEditState.Mode.MULTI_LINE : SceneEditState.Mode.SINGLE_LINE);
                field = (multi ? SceneTextArea.create(ctx, state, theme, readOnly, submissions::add)
                        : SceneTextField.create(ctx, state, theme, readOnly, submissions::add))
                        .size(Size.fill(1), Size.fixed(100));
                button = SceneButton.create(ctx, () -> "下一项", theme, () -> {});
                outer = new SceneNode().size(Size.fill(1), Size.fixed(140)).scrollable(true)
                        .initialFocus(field)
                        .add(new SceneNode().size(Size.fixed(1), Size.fixed(0))
                                .bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(beforeHeight,
                                        (node, value) -> node.size(Size.fixed(1), Size.fixed(value)))))
                        .add(field).add(button);
                ctx.mount(outer);
            });
            onClose(session::close);
            session.frame();
        }
        SceneGeometryView.Entry entry() {
            SceneGeometryView.Entry[] e = {null};
            domain.execute(() -> e[0] = session.pipeline().current().geometry.get(field));
            return e[0];
        }
        SceneEditState.Snapshot snapshot() {
            SceneEditState.Snapshot[] s = {null};
            domain.execute(() -> s[0] = state.snapshot().get());
            return s[0];
        }
        TextLayout.Cluster cluster(int start, int end) {
            for (TextLayout.Line line : entry().text.lines())
                for (TextLayout.Cluster c : line.clusters())
                    if (c.sourceStart == start && c.sourceEnd == end) return c;
            throw new AssertionError("缺少真实来源簇 " + start + ".." + end);
        }
        TextLayout.Line line(int start, int end) {
            for (TextLayout.Line line : entry().text.lines())
                for (TextLayout.Cluster c : line.clusters())
                    if (c.sourceStart == start && c.sourceEnd == end) return line;
            throw new AssertionError("缺少真实行");
        }
        int x(int start, int end, boolean leading) {
            TextLayout.Cluster c = cluster(start, end);
            return entry().box.x + field.padding() + Math.round(leading ? c.leadingX() : c.trailingX());
        }
        int y(int start, int end) {
            TextLayout.Line line = line(start, end);
            return entry().box.y + field.padding() + line.y + (int) (line.lineHeight / 2);
        }
        void pointer(ScenePointerEvent event) { session.pointer(event); session.frame(); }
        void edit(SceneEditEvent event) { session.edit(event); session.frame(); }
        void command(SceneEditEvent.Command command, boolean shift) {
            edit(SceneEditEvent.command(command, shift, null));
        }
        void navigation(SceneNavigationEvent event) { session.navigation(event); session.frame(); }
        void visibleCaret() {
            PaintCommand c = caret();
            LayoutBox visible = c.clip == null ? c.box : c.box.intersect(c.clip);
            require(visible.width == c.box.width && visible.height == c.box.height, "caret没有被共同视口显露");
        }
        void click(int start, int end, boolean leading) {
            int x = x(start, end, leading), y = y(start, end);
            pointer(ScenePointerEvent.down(0, x, y));
            pointer(ScenePointerEvent.up(0, x, y));
        }
        PaintCommand caret() {
            PaintCommand caret = null;
            for (PaintCommand c : host.plan.commands())
                if (c.kind == PaintCommand.Kind.RECT && c.box.width == 1 && c.box.height > 1)
                    caret = c;
            require(caret != null, "实际绘制计划缺少caret");
            return caret;
        }
        void expectEdge(int start, int end, boolean leading, String step) {
            TextLayout.Cluster c = cluster(start, end);
            int expected = entry().box.x + field.padding() + (int) Math.floor(leading ? c.leadingX() : c.trailingX());
            require(caret().box.x == expected, step + " caret=" + caret().box.x + " expected=" + expected);
        }
    }
    @Override protected void setup() throws Exception {
        fonts = own(new FontTextService(FontTextService.defaultSources(getClass().getClassLoader(), 1),
                new LwjglFontEngine(), 4, 8, 4096));
    }
    private void boundaries() {
        for (boolean multi : new boolean[] {false, true})
            for (String text : new String[] {"abc אבג def", "abc العربية def"}) {
                Page p = new Page(text, multi);
                // 空格末端是 LTR/RTL 交界的独立真实字体边界，旧实现在此跳到RTL起边。
                require(!p.cluster(3, 4).rtl && p.cluster(4, 5).rtl, "反例必须实际跨方向");
                p.click(3, 4, false);
                require(p.snapshot().caret == 4 && p.snapshot().anchor == 4, "逻辑定位必须仍是UTF16 4");
                p.expectEdge(3, 4, false, "点击方向交界 multi=" + multi + " text=" + text);
                // 同逻辑offset换到RTL起边，再返回LTR末边，select可能没有新逻辑值。
                p.click(4, 5, true);
                require(p.snapshot().caret == 4, "另一视觉边应仍是同一offset");
                p.expectEdge(4, 5, true, "同offset换方向归属");
                p.click(3, 4, false);
                p.expectEdge(3, 4, false, "同offset返回方向归属");
                int x0 = p.x(0, 1, true), y0 = p.y(0, 1);
                p.pointer(ScenePointerEvent.down(0, x0, y0));
                p.pointer(ScenePointerEvent.move(p.x(3, 4, false), p.y(3, 4)));
                p.expectEdge(3, 4, false, "拖选MOVE");
                p.pointer(ScenePointerEvent.up(0, p.x(3, 4, false), p.y(3, 4)));
                require(p.snapshot().anchor == 0 && p.snapshot().caret == 4, "拖选逻辑选区改变");
                p.expectEdge(3, 4, false, "拖选UP");
                final String[] copied = {null};
                SceneEditEvent.Clipboard clipboard = new SceneEditEvent.Clipboard() {
                    public String read() { return copied[0]; }
                    public void write(String text) { copied[0] = text; }
                };
                p.domain.execute(() -> p.readOnly.set(true));
                p.session.frame();
                p.edit(SceneEditEvent.command(SceneEditEvent.Command.COPY, false, clipboard));
                require("abc ".equals(copied[0]) && !p.snapshot().active, "复制应按逻辑源文且不开会话");
                p.expectEdge(3, 4, false, "只读COPY保持边界");
                p.navigation(SceneNavigationEvent.NEXT);
                p.navigation(SceneNavigationEvent.PREVIOUS);
                p.expectEdge(3, 4, false, "Tab返回保持边界");
                p.visibleCaret();
                require(p.submissions.isEmpty(), "指针复制Tab不得隐含提交");
                p.session.close();
            }
    }
    private void reflowAndProjection() {
        for (boolean multi : new boolean[] {false, true}) {
            Page p = new Page("abc אבג def e\u0301 👩🏽‍💻 tail", multi);
            p.click(3, 4, false);
            p.host.width = 105;
            p.session.frame();
            if (multi) require(p.entry().text.lines().size() > 1, "反例必须发生软换行");
            p.expectEdge(3, 4, false, "宽度重排重验簇边");
            p.domain.execute(() -> {
                SceneTheme b = p.theme.get();
                p.theme.set(new SceneTheme(b.textStyle.withEm(30), b.panel, b.button, b.hover,
                        b.pressed, b.focus, b.disabled, b.disabledText, b.tooltip, b.tooltipText, 0, 0));
            });
            p.session.frame();
            p.expectEdge(3, 4, false, "字号重排使用新几何");
            p.visibleCaret();
            p.host.width = 420;
            p.session.frame();
            p.expectEdge(3, 4, false, "窗口恢复保持边界");
            p.domain.execute(() -> p.state.select(4, 7));
            p.session.frame();
            p.edit(SceneEditEvent.composition("xyz", true));
            require(p.snapshot().text.equals("abc אבג def e\u0301 👩🏽‍💻 tail"), "preedit不能改草稿");
            p.expectEdge(6, 7, false, "组字替换选区投影");
            p.edit(SceneEditEvent.composition("العربية", true));
            p.expectEdge(11, 12, true, "组字长度方向变化后重定位投影caret");
            p.edit(SceneEditEvent.composition("", false));
            p.expectEdge(7, 8, true, "结束组字不能复活旧簇边");
            p.session.close();
        }
    }

    private void visualNavigation() {
        Page p = new Page("abc אבג def\nאבג abc דהו\nabc אבג def", true);
        p.command(SceneEditEvent.Command.DOCUMENT_HOME, false);
        for (SceneEditEvent.Command command : new SceneEditEvent.Command[] {
                SceneEditEvent.Command.END, SceneEditEvent.Command.HOME}) {
            p.command(command, false);
            TextLayout.Line line = p.entry().text.lines().get(0);
            float edge = command == SceneEditEvent.Command.END ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
            for (TextLayout.Cluster c : line.clusters()) {
                edge = command == SceneEditEvent.Command.END
                        ? Math.max(edge, Math.max(c.leadingX(), c.trailingX()))
                        : Math.min(edge, Math.min(c.leadingX(), c.trailingX()));
            }
            require(p.caret().box.x == p.entry().box.x + (int) Math.floor(edge), "真实Home/End没有到视觉端点");
        }
        // 从真实已点边界上下移动，Shift维持逻辑anchor；目标行有RTL→LTR交界。
        p.click(3, 4, false);
        int originX = p.caret().box.x;
        p.command(SceneEditEvent.Command.DOWN, true);
        require(p.snapshot().anchor == 4 && p.snapshot().caret > 4, "Shift+DOWN丢逻辑anchor");
        p.command(SceneEditEvent.Command.DOWN, true);
        p.expectEdge(27, 28, false, "连续上下保留首个视觉水平目标");
        require(p.caret().box.x == originX, "上下移动水平目标来自另一视觉边");
        p.session.close();
        // 显式RTL段落两侧命中仍画在所点的真实边界。
        Page rtl = new Page("אבג abc דהו", false);
        for (int start : new int[] {3, 4, 6, 7}) {
            int x = rtl.x(start, start + 1, true), y = rtl.y(start, start + 1);
            rtl.pointer(ScenePointerEvent.down(0, x, y));
            rtl.pointer(ScenePointerEvent.up(0, x, y));
            require(Math.abs(rtl.caret().box.x - x) <= 1, "RTL→LTR交界点击跳边");
        }
        rtl.session.close();
    }

    private void revealBoundary() {
        for (boolean multi : new boolean[] {false, true}) {
            Page p = new Page("abc אבג def", multi);
            p.click(3, 4, false);
            p.domain.execute(() -> p.beforeHeight.set(200));
            p.session.frame();
            require(p.caret().box.intersect(p.caret().clip).height == 0, "前置重排必须实际移开caret");
            p.navigation(SceneNavigationEvent.NEXT);
            p.navigation(SceneNavigationEvent.PREVIOUS);
            p.expectEdge(3, 4, false, "重排后Tab祖先显露仍取所选边");
            p.visibleCaret();
            p.domain.execute(() -> require(p.session.pipeline().current().geometry.get(p.outer).scrollOffset > 0,
                    "实际祖先未滚动显露"));
            p.session.close();
        }
        Page p = new Page(String.join("\n", java.util.Collections.nCopies(12, "abc אבג def")), true);
        p.command(SceneEditEvent.Command.DOCUMENT_HOME, false);
        p.click(3, 4, false);
        p.pointer(ScenePointerEvent.scroll(2, 2, -5));
        require(p.caret().box.intersect(p.caret().clip).height == 0, "滚轮必须实际移开caret");
        p.navigation(SceneNavigationEvent.NEXT);
        p.navigation(SceneNavigationEvent.PREVIOUS);
        p.expectEdge(3, 4, false, "内部滚轮后Tab显露保持所选边");
        p.visibleCaret();
        p.session.close();
    }

    @Override protected void runTests() throws Exception {
        scenario("单多行真实字体点击同offset换边拖选复制与Tab", this::boundaries);
        scenario("宽度字号重排和组字投影沿当前簇边定位", this::reflowAndProjection);
        scenario("真实视觉HomeEnd上下目标及RTL段落交界", this::visualNavigation);
        scenario("内部滚轮与重排后Tab显露同一方向边界", this::revealBoundary);
    }
    public static void main(String[] args) throws Exception { new BidiEditingScenario().run(args); }
}
