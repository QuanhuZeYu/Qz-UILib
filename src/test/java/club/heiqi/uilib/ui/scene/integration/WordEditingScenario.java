package club.heiqi.uilib.ui.scene.integration;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneCompositionSource;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.unicode.EmojiData;
import club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes;

/** 真实字体与正式页面词命令；自动输入不冒称原生键盘或IME设备实测。 */
public final class WordEditingScenario extends TestApplication {
    private FontTextService fonts;
    private static final class Host implements SceneHost {
        PaintPlan plan;
        public int width() { return 240; }
        public int height() { return 120; }
        public long metricsRevision() { return 0; }
        public void replay(PaintPlan value) { plan = value; }
    }
    private final class Page {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final Host host = new Host();
        final List<SceneEditState.Submission> submissions = new ArrayList<>();
        SceneEditState state;
        SceneNode field;
        Signal<Boolean> readOnly, enabled;
        Signal<TextPolicy> policy;
        final ScenePageSession session;
        Page(boolean multi) {
            session = ScenePageSession.open(domain, host, fonts, ctx -> {
                state = new SceneEditState(domain, "", multi ? SceneEditState.Mode.MULTI_LINE : SceneEditState.Mode.SINGLE_LINE);
                readOnly = domain.signal(false);
                enabled = domain.signal(true);
                policy = domain.signal(new TextPolicy(TextPolicy.Direction.AUTO, TextPolicy.Wrap.NONE, "en", 0, 0));
                Signal<SceneTheme> theme = domain.signal(SceneTheme.light());
                field = (multi ? SceneTextArea.create(ctx, state, theme, readOnly, submissions::add, SceneTextField.SubmitPolicy.INPUT)
                        : SceneTextField.create(ctx, state, theme, readOnly, submissions::add, SceneTextField.SubmitPolicy.INPUT))
                        .size(Size.fixed(180), Size.fixed(48))
                        .bind(NodeBinding.value(policy, SceneNode::textPolicy))
                        .bind(NodeBinding.value(enabled, SceneNode::enabled));
                ctx.mount(new SceneNode().initialFocus(field).add(field));
            });
            onClose(session::close);
            session.frame();
        }
        SceneEditState.Snapshot snapshot() {
            SceneEditState.Snapshot[] result = {null};
            domain.execute(() -> result[0] = state.snapshot().get());
            return result[0];
        }
        void reset(String text, int anchor, int caret) {
            domain.execute(() -> { state.reset(text); state.select(anchor, caret); });
            session.frame();
            submissions.clear();
        }
        void command(Command command, boolean shift) {
            session.edit(SceneEditEvent.command(command, shift, null));
            session.frame();
        }
        void command(Command command) { command(command, false); }
        void expect(String text, int anchor, int caret) {
            SceneEditState.Snapshot s = snapshot();
            require(text.equals(s.text) && s.anchor == anchor && s.caret == caret,
                    "期望 " + text + " [" + anchor + "," + caret + "] 实际 " + s.text + " [" + s.anchor + "," + s.caret + "]");
        }
        void visibleCaret() {
            PaintCommand caret = null;
            for (PaintCommand c : host.plan.commands())
                if (c.kind == PaintCommand.Kind.RECT && c.box.width == 1 && c.box.height > 1) caret = c;
            require(caret != null, "实际PaintCommand必须绘制词移动光标");
            LayoutBox visible = caret.clip == null ? caret.box : caret.box.intersect(caret.clip);
            require(visible.width == caret.box.width && visible.height == caret.box.height, "词光标必须完整显露");
        }
    }
    @Override protected void setup() throws Exception {
        fonts = own(new FontTextService(FontTextService.defaultSources(getClass().getClassLoader(), 1),
                new LwjglFontEngine(), 4, 8, 4096));
    }
    private void pageLoop() {
        for (boolean multi : new boolean[] {false, true}) {
            Page p = new Page(multi);
            p.session.edit(SceneEditEvent.text("hello  world")); p.session.frame();
            p.expect("hello  world", 12, 12);
            require(p.submissions.size() == 1 && !p.submissions.get(0).complete, "实际输入只请求INPUT");
            p.command(Command.WORD_LEFT); p.expect("hello  world", 7, 7);
            p.command(Command.WORD_LEFT); p.expect("hello  world", 0, 0);
            p.command(Command.WORD_RIGHT); p.expect("hello  world", 7, 7);
            p.command(Command.WORD_RIGHT); p.expect("hello  world", 12, 12);
            p.command(Command.WORD_LEFT, true); p.expect("hello  world", 12, 7);
            p.command(Command.WORD_LEFT); p.expect("hello  world", 7, 7);
            p.reset("hello  world", 12, 7);
            p.command(Command.WORD_DELETE); p.expect("hello  ", 7, 7);
            require(p.submissions.size() == 1 && p.submissions.get(0).text.equals("hello  ")
                    && !p.submissions.get(0).complete, "词删除只沿INPUT请求当前草稿");
            p.command(Command.UNDO); p.expect("hello  world", 12, 7);
            p.command(Command.REDO); p.expect("hello  ", 7, 7);
            p.reset("hello  world", 12, 12);
            p.command(Command.WORD_BACKSPACE); p.expect("hello  ", 7, 7);
            p.command(Command.WORD_BACKSPACE); p.expect("", 0, 0);
            p.session.edit(SceneEditEvent.text("x")); p.session.frame();
            p.command(Command.UNDO); p.expect("", 0, 0);
            p.command(Command.UNDO); p.expect("hello  ", 7, 7);
            p.command(Command.UNDO); p.expect("hello  world", 12, 12);
            p.reset("", 0, 0);
            p.session.edit(SceneEditEvent.text("ab")); p.session.frame();
            p.command(Command.WORD_BACKSPACE); p.expect("", 0, 0);
            p.command(Command.UNDO); p.expect("ab", 2, 2);
            p.command(Command.UNDO); p.expect("", 0, 0);
            p.reset("ab cd", 5, 5);
            p.command(Command.BACKSPACE); p.expect("ab c", 4, 4);
            p.command(Command.WORD_BACKSPACE); p.expect("ab ", 3, 3);
            p.command(Command.BACKSPACE); p.expect("ab", 2, 2);
            p.command(Command.UNDO); p.expect("ab ", 3, 3);
            p.command(Command.UNDO); p.expect("ab c", 4, 4);
            p.command(Command.UNDO); p.expect("ab cd", 5, 5);
            p.reset("hello", 0, 0);
            long revision = p.snapshot().revision;
            p.command(Command.WORD_LEFT); p.command(Command.WORD_BACKSPACE);
            require(p.snapshot().revision == revision && !p.snapshot().active && p.submissions.isEmpty(), "端点不造修订会话或提交");
            p.command(Command.WORD_RIGHT); p.command(Command.WORD_DELETE);
            require(p.snapshot().revision == revision && p.submissions.isEmpty(), "尾端不造提交");
            p.session.close();
        }
    }
    private void unicodeAndLanguage() {
        Page p = new Page(true);
        // 各来源长度与目标显式给出，expected 不来自被测词段算法。
        String text = "e\u0301 😀 👩🏽‍💻,\t\n\u00a0世界";
        p.reset(text, 0, 0);
        for (int target : new int[] {3, 6, 13, 17, 19}) {
            p.command(Command.WORD_RIGHT);
            require(p.snapshot().caret == target, "组合字素/emoji/标点/空白目标 " + target + " 实际 " + p.snapshot().caret);
        }
        for (int target : new int[] {17, 13, 6, 3, 0}) {
            p.command(Command.WORD_LEFT);
            require(p.snapshot().caret == target, "词左目标 " + target);
        }
        p.reset("hello  world", 6, 6);
        p.command(Command.WORD_RIGHT); p.expect("hello  world", 7, 7);
        p.reset("hello  world", 6, 6);
        p.command(Command.WORD_LEFT); p.expect("hello  world", 0, 0);
        p.reset("你好世界", 0, 0);
        p.domain.execute(() -> p.policy.set(new TextPolicy(TextPolicy.Direction.AUTO, TextPolicy.Wrap.NONE, "zh", 0, 0)));
        p.session.frame();
        p.command(Command.WORD_RIGHT); p.expect("你好世界", 2, 2);
        p.command(Command.WORD_RIGHT); p.expect("你好世界", 4, 4);
        // 独立ICU76.1探针已确认 a:b：en/und/root 为0,1,2,3，fi/sv为0,3；
        // expected 显式固定，不调用UnicodeWords生成；同一页面换语言不得沿用旧词段。
        for (String language : new String[] {"en", "fi", "und", "root"}) {
            p.domain.execute(() -> p.policy.set(new TextPolicy(TextPolicy.Direction.AUTO, TextPolicy.Wrap.NONE, language, 0, 0)));
            p.session.frame(); p.reset("a:b", 0, 0);
            p.command(Command.WORD_RIGHT);
            p.expect("a:b", language.equals("fi") ? 3 : 1, language.equals("fi") ? 3 : 1);
        }
        p.session.close();
    }
    private void eligibilityAndGeometry() {
        for (boolean multi : new boolean[] {false, true}) {
            Page p = new Page(multi);
            p.reset("hello world", 11, 11);
            p.domain.execute(() -> p.readOnly.set(true)); p.session.frame();
            p.command(Command.WORD_LEFT, true); p.expect("hello world", 11, 6);
            p.command(Command.WORD_BACKSPACE); p.expect("hello world", 11, 6);
            require(p.submissions.isEmpty(), "只读词删除不提交");
            p.domain.execute(() -> p.readOnly.set(false)); p.session.frame();
            SceneCompositionSource source = new SceneCompositionSource();
            p.session.edit(SceneEditEvent.compositionBegin(source, "候选")); p.session.frame();
            p.command(Command.WORD_RIGHT); p.command(Command.WORD_DELETE);
            p.expect("hello world", 11, 6);
            require(p.snapshot().composing && p.submissions.isEmpty(), "IME优先保留候选与草稿");
            p.session.edit(SceneEditEvent.compositionCancel(source)); p.session.frame();
            p.domain.execute(() -> p.enabled.set(false)); p.session.frame();
            p.session.edit(SceneEditEvent.command(Command.WORD_BACKSPACE, false, null));
            p.session.frame();
            require(p.submissions.isEmpty(), "禁用词删除不得提交");
            p.expect("hello world", 11, 6);
            p.session.close();
            Page geometry = new Page(multi);
            String longText = "hello  world hello  world hello  world" + (multi ? "\nhello\nworld\nhello\nworld" : "");
            geometry.reset(longText, 0, 0);
            for (int i = 0; i < 10; i++) { geometry.command(Command.WORD_RIGHT); geometry.visibleCaret(); }
            for (int i = 0; i < 10; i++) { geometry.command(Command.WORD_LEFT); geometry.visibleCaret(); }
            require(geometry.submissions.isEmpty(), "真实字体词移动不提交");
            geometry.reset("abc אבג def", 0, 0);
            geometry.command(Command.WORD_RIGHT); geometry.expect("abc אבג def", 4, 4); geometry.visibleCaret();
            geometry.command(Command.WORD_RIGHT); geometry.expect("abc אבג def", 8, 8); geometry.visibleCaret();
            geometry.command(Command.WORD_LEFT); geometry.expect("abc אבג def", 4, 4); geometry.visibleCaret();
            geometry.session.close();
        }
    }
    private void currentProfile() throws Exception {
        ReactiveDomain domain = ReactiveDomain.direct();
        UnicodeGraphemes.Profile[] profile = {UnicodeGraphemes.bundled()};
        // 数据源合同允许替换不可变profile；此小型RGI样本制造一个跨ICU词断点的完整簇。
        UnicodeGraphemes.Profile joined = new UnicodeGraphemes.Profile(EmojiData.read("fixture",
                new StringReader("0061 ; Emoji\n0061 ; Emoji_Presentation\n"),
                new StringReader("0061 0020 0062 ; RGI_Emoji_ZWJ_Sequence\n")));
        domain.execute(() -> {
            SceneEditState state = new SceneEditState(domain, "a b", SceneEditState.Mode.MULTI_LINE, () -> profile[0]);
            state.select(0, 0); state.moveWord(1, false, "und");
            require(state.snapshot().get().caret == 2, "原profile按普通词段移动");
            profile[0] = joined;
            state.moveWord(1, false, "und");
            require(state.snapshot().get().caret == 3 && state.snapshot().get().anchor == 3, "新profile过滤簇内词断点");
            state.deleteWord(-1, "und"); require(state.text().get().isEmpty(), "词删除不可拆新完整簇");
            state.undo(); require(state.snapshot().get().caret == 3 && state.text().get().equals("a b"), "profile历史复用共同来源");
        });
    }
    @Override protected void runTests() throws Exception {
        scenario("正式单多行输入词命令选区独立历史INPUT及端点", this::pageLoop);
        scenario("完整字素标点Unicode空白中文及当次语言", this::unicodeAndLanguage);
        scenario("只读禁用IME优先及真实字体横纵显露逻辑双向移动", this::eligibilityAndGeometry);
        scenario("当次字素profile过滤词段并保持完整历史", this::currentProfile);
    }
    public static void main(String[] args) throws Exception { new WordEditingScenario().run(args); }
}
