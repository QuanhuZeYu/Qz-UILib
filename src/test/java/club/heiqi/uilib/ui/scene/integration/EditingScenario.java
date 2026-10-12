package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.edit.SceneEditState.Snapshot;
import club.heiqi.uilib.ui.scene.edit.SceneEditState.Submission;
import club.heiqi.uilib.ui.scene.edit.SceneEditState.Status;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontSource;

/** 编辑规格的独立状态与正式页面验收；软件字体事实不冒称原生IME或GPU验证。 */
public final class EditingScenario extends TestApplication {
    /** 非等宽字体事实含一个fi文字簇，刻意使UTF-16偏移不能当作像素位置。 */
    private static final class TextHost implements SceneHost, SceneTextMeasurer {
        final FontSource font =
                new FontSource(
                        "fixture", 0, "fixture", new byte[] {1}, 0, false, Collections.emptySet());

        @Override
        public int width() {
            return 420;
        }

        @Override
        public int height() {
            return 320;
        }

        @Override
        public long metricsRevision() {
            return 0;
        }

        PaintPlan lastPlan;
        @Override
        public void replay(PaintPlan plan) { lastPlan = plan; }

        @Override
        public TextLayout layout(String text, TextStyle style, int width) {
            return layout(text, style, width, TextPolicy.NORMAL);
        }

        @Override
        public TextLayout layout(String text, TextStyle style, int width, TextPolicy policy) {
            List<TextLayout.Glyph> glyphs = new ArrayList<>();
            List<TextLayout.Cluster> clusters = new ArrayList<>();
            float x = 0, ascent = style.size * .75f;
            for (int start = 0; start < text.length(); ) {
                int end =
                        text.startsWith("fi", start)
                                ? start + 2
                                : start + Character.charCount(text.codePointAt(start));
                float advance =
                        text.startsWith("fi", start) ? 23 : text.charAt(start) == 'W' ? 31 : 9;
                int glyph = glyphs.size();
                glyphs.add(
                        new TextLayout.Glyph(
                                font,
                                style.size,
                                text.codePointAt(start),
                                start,
                                end,
                                start,
                                end,
                                x,
                                ascent,
                                advance,
                                0,
                                false,
                                false));
                clusters.add(
                        new TextLayout.Cluster(
                                start, end, start, end, glyph, glyph + 1, x, advance, style));
                x += advance;
                start = end;
            }
            TextLayout.Line line =
                    new TextLayout.Line(
                            text,
                            0,
                            0,
                            0,
                            text.length(),
                            0,
                            text.length(),
                            x,
                            ascent,
                            ascent,
                            style.size - ascent,
                            style.size + 2,
                            glyphs,
                            clusters,
                            Collections.emptyList());
            return new TextLayout(
                    (int) Math.ceil(x),
                    style.size + 2,
                    x,
                    style.size + 2,
                    style,
                    Collections.singletonList(line),
                    text,
                    text,
                    0,
                    Collections.emptyList());
        }
    }

    private static final class Clipboard implements SceneEditEvent.Clipboard {
        String value = "";

        @Override
        public String read() {
            return value;
        }

        @Override
        public void write(String text) {
            value = text;
        }
    }

    /** 页面只声明内容、绑定及业务接收；事件路由、布局和关闭由正式Session负责。 */
    private final class Page {
        final ReactiveDomain domain;
        final SceneEditState state;
        final List<Submission> submissions = new ArrayList<>();
        final TextHost host = new TextHost();
        Signal<Boolean> readOnly, enabled, modalOpen;
        SceneNode field, button;
        ScenePageSession session;
        int clicks;

        Page(ReactiveDomain domain, SceneEditState state, SceneTextField.SubmitPolicy policy) {
            this.domain = domain;
            this.state = state;
            session =
                    ScenePageSession.open(
                            domain,
                            host,
                            host,
                            ctx -> {
                                readOnly = domain.signal(false);
                                enabled = domain.signal(true);
                                modalOpen = domain.signal(false);
                                Signal<SceneTheme> theme = domain.signal(SceneTheme.light());
                                field =
                                        SceneTextField.create(
                                                        ctx,
                                                        state,
                                                        theme,
                                                        readOnly,
                                                        submissions::add,
                                                        policy)
                                                .size(Size.fixed(260), Size.natural())
                                                .bind(
                                                        NodeBinding.value(
                                                                enabled, SceneNode::enabled));
                                button = SceneButton.create(ctx, () -> "按钮", theme, () -> clicks++);
                                SceneNode modal =
                                        new SceneNode()
                                                .modal(true)
                                                .visible(false)
                                                .bind(
                                                        NodeBinding.value(
                                                                modalOpen, SceneNode::visible))
                                                .add(new SceneNode().text("模态").focusable(true));
                                ctx.mount(
                                        new SceneNode()
                                                .initialFocus(field)
                                                .add(field)
                                                .add(button)
                                                .add(modal));
                            });
            onClose(session::close);
            session.frame();
        }

        void edit(SceneEditEvent event) {
            session.edit(event);
            if (!session.ended()) session.frame();
        }

        void command(Command command) {
            edit(SceneEditEvent.command(command, false, null));
        }

        Snapshot snapshot() {
            return state.snapshot().get();
        }

        void expect(String text) {
            domain.execute(
                    () ->
                            require(
                                    text.equals(state.text().get()),
                                    "草稿预期=" + text + " 实际=" + state.text().get()));
        }

        void focusField() {
            domain.execute(
                    () ->
                            session.runtime()
                                    .focusRouter()
                                    .request(session.runtime().captureMount(field)));
        }
    }

    private SceneEditState state(ReactiveDomain domain, String baseline) {
        SceneEditState[] result = new SceneEditState[1];
        domain.execute(() -> result[0] = new SceneEditState(domain, baseline));
        return result[0];
    }

    @Override
    protected void runTests() throws Exception {
        scenario("持键文字门与只读组字结束恢复选择导航", this::historyAndReadonlyComposition);
        scenario("未完成草稿及拒绝不改业务值，不因选择开启会话", this::draftAndRejection);
        scenario("旧异步结果记录原请求，重置同文及同代最新请求保持身份隔离", this::requestIdentity);
        scenario("完成规范化保留可用历史，随输入接受继续会话", this::normalizationHistory);
        scenario("外部值仅同步非活跃状态，关闭重开复用独立草稿", this::externalAndReopen);
        scenario("代理对组合符ZWJ整簇移动删除，连续输入删除与原子插入历史", this::unicodeAndHistory);
        scenario("控制符和独立Extend保留强制字素边界", this::controlGraphemes);
        scenario("自定义字素规则换代保留草稿并校正编辑与历史位置", this::profileSelectionUpgrade);
        scenario("正式页面文字空格确认Tab和IME及ESC沿唯一焦点序列", this::pageEditing);
        scenario("空白主键正常blur仅提交一次，同批文本隔离且失活不提交", this::blurEligibility);
        scenario("只读选择复制拒绝修改，粘贴单步撤销与生产文字簇指针定位", this::clipboardAndPointer);
        scenario("选区只读变化复用度量且当前光标重新呈现", this::presentationInvalidation);
    }

    private int caretX(PaintPlan plan) {
        int result = Integer.MIN_VALUE;
        for (club.heiqi.uilib.ui.scene.paint.PaintCommand command : plan.commands())
            if (command.kind == club.heiqi.uilib.ui.scene.paint.PaintCommand.Kind.RECT
                    && command.box.width == 1 && command.box.height > 1
                    && command.color == SceneTheme.light().focus) result = command.box.x;
        require(result != Integer.MIN_VALUE, "正式计划包含当前光标");
        return result;
    }

    private void presentationInvalidation() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Page page = new Page(domain, state(domain, ""), SceneTextField.SubmitPolicy.CONFIRM);
        page.edit(SceneEditEvent.text("abcd"));
        long[] revision = {0};
        domain.execute(() -> revision[0] = page.session.runtime().measureRevision());
        int previous = caretX(page.host.lastPlan);
        page.command(Command.LEFT);
        require(caretX(page.host.lastPlan) < previous, "复用度量后绘制光标仍跟随当前选区");
        domain.execute(() -> require(page.session.runtime().measureRevision() == revision[0],
                "仅选区变化不增加内容测量代际"));
        domain.execute(() -> page.readOnly.set(true));
        page.session.frame();
        domain.execute(() -> require(page.session.runtime().measureRevision() == revision[0],
                "仅只读变化不增加内容测量代际"));
        domain.execute(() -> page.readOnly.set(false));
        page.edit(SceneEditEvent.text("x"));
        page.expect("abcxd");
        domain.execute(() -> require(page.session.runtime().measureRevision() > revision[0],
                "实际显示文字变化仍失效度量"));
    }

    private void draftAndRejection() {
        ReactiveDomain d = ReactiveDomain.direct();
        SceneEditState s = state(d, "12");
        String[] business = {"12"};
        d.execute(
                () -> {
                    s.home(false);
                    s.move(1, true);
                    require(!s.snapshot().get().active, "移动选择不能开启会话");
                    s.select(0, s.text().get().length());
                    s.replaceSelection("-", false);
                    require("-".equals(s.text().get()) && "12".equals(business[0]), "负号不能自动保存业务值");
                    Submission request = s.request(true);
                    require(
                            request.status().get().status == Status.PENDING && request.complete,
                            "请求未待定");
                    require(s.reject(request, "请输入完整整数"), "当前拒绝未应用");
                    Snapshot rejected = s.snapshot().get();
                    require(
                            rejected.active
                                    && "-".equals(rejected.text)
                                    && "请输入完整整数".equals(rejected.error),
                            "拒绝丢草稿或原因");
                    s.select(0, 1);
                    s.replaceSelection("", false);
                    require(
                            s.text().get().isEmpty()
                                    && s.snapshot().get().active
                                    && "12".equals(business[0]),
                            "空串不能自动写业务");
                    s.cancel();
                    require("12".equals(s.text().get()) && !s.snapshot().get().active, "取消未恢复基线");
                });
    }

    private void requestIdentity() {
        ReactiveDomain d = ReactiveDomain.direct();
        SceneEditState s = state(d, "");
        d.execute(
                () -> {
                    s.replaceSelection("a");
                    Submission old = s.request(true);
                    s.replaceSelection("b");
                    s.select(0, 1);
                    Snapshot current = s.snapshot().get();
                    require(!s.accept(old, "OLD"), "旧接受覆盖新草稿");
                    require(
                            old.status().get().status == Status.ACCEPTED
                                    && "OLD".equals(old.status().get().text),
                            "旧请求没记录接受结果");
                    Snapshot after = s.snapshot().get();
                    require(
                            after.text.equals(current.text)
                                    && after.anchor == current.anchor
                                    && after.caret == current.caret
                                    && after.active == current.active
                                    && after.error == null,
                            "旧接受改变当前选区或会话");
                    Submission resetOld = s.request(true);
                    long before = s.snapshot().get().revision;
                    s.reset("ab");
                    require(s.snapshot().get().revision > before, "同文重置没有新代际证据");
                    require(
                            !s.reject(resetOld, "迟到错误") && s.snapshot().get().error == null,
                            "同文重置仍收到旧错误");
                    require(
                            resetOld.status().get().status == Status.REJECTED
                                    && "迟到错误".equals(resetOld.status().get().reason),
                            "旧拒绝结果失真");
                    s.replaceSelection("c");
                    Submission first = s.request(true), latest = s.request(false);
                    require(first != latest && first.revision == latest.revision, "请求必须独立于文本修订身份");
                    require(!s.accept(first, "FIRST") && s.accept(latest, "abc"), "同修订的最新请求未隔离");
                    require(
                            !s.reject(latest, "重复结果")
                                    && latest.status().get().status == Status.ACCEPTED,
                            "重复完成改写结果");
                });
    }

    private void normalizationHistory() {
        ReactiveDomain d = ReactiveDomain.direct();
        SceneEditState s = state(d, "base");
        d.execute(
                () -> {
                    s.select(0, 4);
                    s.replaceSelection("  value  ", false);
                    Submission request = s.request(true);
                    require(
                            s.accept(request, "value") && !s.snapshot().get().active,
                            "完成接受未规范化并结束会话");
                    s.undo();
                    require(
                            "base".equals(s.text().get()) && s.snapshot().get().active,
                            "规范化清历史或撤销陷入格式修正");
                    s.redo();
                    require("value".equals(s.text().get()), "重做未恢复规范化结果");
                    Submission input = s.request(false);
                    require(s.accept(input, "value") && s.snapshot().get().active, "随输入接受不应结束会话");
                    s.reset("new object");
                    s.undo();
                    require(
                            "new object".equals(s.text().get()) && !s.snapshot().get().active,
                            "重置可以撤销到旧对象");
                });
    }

    private void externalAndReopen() {
        ReactiveDomain d = ReactiveDomain.direct();
        SceneEditState s = state(d, "first");
        d.execute(
                () -> {
                    s.synchronizeExternal("second");
                    require("second".equals(s.text().get()), "无会话未同步外部值");
                    s.replaceSelection(" draft");
                    s.synchronizeExternal("outside");
                    require("second draft".equals(s.text().get()), "外部值覆盖活跃草稿");
                });
        Page first = new Page(d, s, SceneTextField.SubmitPolicy.CONFIRM);
        SceneNode oldNode = first.field;
        first.session.close();
        d.execute(() -> s.replaceSelection(" retained", false));
        Page reopened = new Page(d, s, SceneTextField.SubmitPolicy.CONFIRM);
        reopened.expect("second draft retained");
        d.execute(
                () -> {
                    require(
                            oldNode != reopened.field && first.submissions.isEmpty(),
                            "重开复用旧节点或关闭自动提交");
                    require(
                            reopened.session.runtime().focusRouter().focused().get()
                                    == reopened.field,
                            "新页面未建立字段焦点");
                });
        first.edit(SceneEditEvent.text(" stale"));
        reopened.expect("second draft retained");
        reopened.edit(SceneEditEvent.text("!"));
        reopened.expect("second draft retained!");
    }

    private void unicodeAndHistory() {
        ReactiveDomain d = ReactiveDomain.direct();
        SceneEditState ordinaryJoin = state(d, "a\u200Db");
        d.execute(
                () -> {
                    ordinaryJoin.deleteBackward();
                    require("a\u200D".equals(ordinaryJoin.text().get()), "普通字母ZWJ误删前字");
                    ordinaryJoin.reset("a\u200Db");
                    ordinaryJoin.home(false);
                    ordinaryJoin.move(1, false);
                    require(ordinaryJoin.snapshot().get().caret == 2, "普通字母ZWJ误跳后字");
                });
        String emoji = "😀", combining = "e\u0301", family = "👩‍👩‍👧‍👦";
        SceneEditState s = state(d, "A" + emoji + combining + family + "Z");
        d.execute(
                () -> {
                    s.home(false);
                    s.move(1, false);
                    s.move(1, false);
                    require(s.snapshot().get().caret == ("A" + emoji).length(), "移动拆开代理对");
                    s.deleteForward();
                    require(("A" + emoji + family + "Z").equals(s.text().get()), "删除拆开组合符");
                    s.move(1, false);
                    require(
                            s.snapshot().get().caret == ("A" + emoji + family).length(),
                            "移动拆开ZWJ家庭");
                    s.deleteBackward();
                    require(("A" + emoji + "Z").equals(s.text().get()), "删除拆开ZWJ家庭");
                    s.undo();
                    require(("A" + emoji + family + "Z").equals(s.text().get()), "删除撤销没恢复整簇");
                    s.reset("");
                    s.replaceSelection("a");
                    s.replaceSelection("b");
                    s.replaceSelection("c");
                    s.undo();
                    require(s.text().get().isEmpty(), "连续输入未合组");
                    s.redo();
                    s.replaceSelection("粘贴", false);
                    s.undo();
                    require("abc".equals(s.text().get()), "原子插入混入输入历史");
                    s.deleteBackward();
                    s.deleteBackward();
                    s.undo();
                    require("abc".equals(s.text().get()), "同向删除未合组");
                    s.home(false);
                    s.replaceSelection("x");
                    s.end(false);
                    s.replaceSelection("y");
                    s.undo();
                    require("xabc".equals(s.text().get()), "光标移动没有分隔输入组");
                });
    }

    private void pageEditing() {
        ReactiveDomain d = ReactiveDomain.direct();
        Page p = new Page(d, state(d, ""), SceneTextField.SubmitPolicy.CONFIRM);
        // 同帧采集文字与空格，正式队列逐事件结算，不能只保留末项。
        p.session.edit(SceneEditEvent.text("甲\r\n乙"));
        p.session.edit(SceneEditEvent.command(Command.SPACE, false, null));
        p.session.frame();
        p.expect("甲乙 ");
        require(p.submissions.isEmpty() && p.clicks == 0, "普通文字或空格触发业务确认");
        p.edit(SceneEditEvent.composition("拼音", true));
        p.command(Command.CONFIRM);
        require(p.submissions.isEmpty(), "IME确认同时提交字段");
        p.command(Command.LEFT);
        p.command(Command.BACKSPACE);
        p.command(Command.SPACE);
        p.command(Command.TAB);
        p.expect("甲乙 ");
        d.execute(
                () ->
                        require(
                                p.snapshot().composing
                                        && p.snapshot().caret == 3
                                        && p.session.runtime().focusRouter().focused().get()
                                                == p.field,
                                "IME候选键改了草稿或焦点"));
        int[] fallback = {0};
        p.session.escape(() -> fallback[0]++);
        p.session.frame();
        d.execute(() -> require(!p.snapshot().composing && p.snapshot().active, "ESC未仅取消preedit"));
        require(fallback[0] == 0 && p.submissions.isEmpty(), "组字ESC误关闭或提交");
        p.edit(SceneEditEvent.composition("中", true));
        p.edit(SceneEditEvent.committed("中文"));
        p.expect("甲乙 中文");
        p.command(Command.UNDO);
        p.expect("甲乙 ");
        p.command(Command.REDO);
        p.command(Command.CONFIRM);
        require(
                p.submissions.size() == 1
                        && p.submissions.get(0).complete
                        && "甲乙 中文".equals(p.submissions.get(0).text),
                "字段确认请求错误");
        p.session.navigation(SceneNavigationEvent.NEXT);
        p.session.frame();
        d.execute(
                () ->
                        require(
                                p.session.runtime().focusRouter().focused().get() == p.button,
                                "Tab没有离开字段"));
        require(p.submissions.size() == 1, "默认Tab隐含提交");
        p.edit(SceneEditEvent.text("wrong target"));
        p.expect("甲乙 中文");
        p.session.navigation(SceneNavigationEvent.ACTIVATE);
        p.session.frame();
        require(p.clicks == 1, "按钮导航激活失效");
        p.focusField();
        p.session.escape(() -> fallback[0]++);
        p.session.frame();
        require(fallback[0] == 1 && p.submissions.size() == 1, "普通ESC不应确认或取消草稿");
    }

    private void profileSelectionUpgrade() throws Exception {
        String properties = "1F600..1F603 ; Emoji\n1F600..1F603 ; Emoji_Presentation\n";
        String singles = "1F600 ; Basic_Emoji\n1F603 ; Basic_Emoji\n";
        club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes.Profile oldRules =
                new club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes.Profile(
                        club.heiqi.uilib.ui.scene.text.unicode.EmojiData.read(
                                "old",
                                new java.io.StringReader(properties),
                                new java.io.StringReader(singles)));
        club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes.Profile newRules =
                new club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes.Profile(
                        club.heiqi.uilib.ui.scene.text.unicode.EmojiData.read(
                                "new",
                                new java.io.StringReader(properties),
                                new java.io.StringReader(
                                        singles + "1F600 1F603 ; fully-qualified\n")));
        club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes.Profile[] current = {oldRules};
        ReactiveDomain domain = ReactiveDomain.direct();
        String pair = "😀😃";
        domain.execute(
                () -> {
                    SceneEditState value =
                            new SceneEditState(
                                    domain,
                                    pair,
                                    SceneEditState.Mode.SINGLE_LINE,
                                    () -> current[0]);
                    value.select(2, 2);
                    current[0] = newRules;
                    value.replaceSelection("X", false);
                    require(value.text().get().equals("X" + pair), "规则换代后插入切开新字素");
                    value.undo();
                    require(
                            value.text().get().equals(pair) && value.snapshot().get().caret == 0,
                            "撤销新操作恢复非法位置或丢原文");
                    value.redo();
                    require(value.text().get().equals("X" + pair), "重做新操作丢文本");

                    current[0] = oldRules;
                    value.reset(pair);
                    value.select(2, 2);
                    value.replaceSelection("X", false);
                    value.select(3, 3);
                    value.replaceSelection("", false); // 不增加历史，保留既有插入位置。
                    value.undo();
                    require(
                            value.text().get().equals(pair) && value.snapshot().get().caret == 2,
                            "旧规则历史准备错误");
                    current[0] = newRules;
                    value.redo();
                    value.undo();
                    require(
                            value.text().get().equals(pair)
                                    && value.snapshot().get().anchor == 0
                                    && value.snapshot().get().caret == 0,
                            "历史恢复没有按当前规则归位");

                    current[0] = oldRules;
                    value.reset(pair);
                    value.select(2, 2);
                    current[0] = newRules;
                    value.deleteBackward();
                    require(
                            value.text().get().equals(pair) && value.snapshot().get().caret == 0,
                            "规则换代后Backspace误删部分新簇");
                    current[0] = oldRules;
                    value.select(2, 2);
                    current[0] = newRules;
                    value.deleteForward();
                    require(value.text().get().isEmpty(), "规则换代后Delete未删除完整新簇");
                    value.undo();
                    require(
                            value.text().get().equals(pair) && value.snapshot().get().caret == 0,
                            "删除撤销恢复旧非法位置");

                    current[0] = oldRules;
                    value.reset(pair);
                    value.select(2, 2);
                    current[0] = newRules;
                    long revision = value.snapshot().get().revision;
                    value.refreshUnicodeRules();
                    require(
                            value.text().get().equals(pair)
                                    && value.snapshot().get().caret == 0
                                    && value.snapshot().get().revision == revision,
                            "显式规则刷新静默编辑正文或未归位");
                });
    }

    private void controlGraphemes() {
        ReactiveDomain domain = ReactiveDomain.direct();
        domain.execute(
                () -> {
                    for (String prefix : new String[] {"\n", "\b", "\u0085"}) {
                        SceneEditState value =
                                new SceneEditState(
                                        domain, prefix + "\u0301", SceneEditState.Mode.MULTI_LINE);
                        value.move(-1, false);
                        require(
                                value.snapshot().get().caret == prefix.length(),
                                "控制字符后的独立Extend被合并");
                        value.end(false);
                        value.deleteBackward();
                        require(value.text().get().equals(prefix), "删除组合标记误删控制字符");
                        value.undo();
                        require(value.text().get().equals(prefix + "\u0301"), "控制字素删除历史丢内容");
                    }
                });
    }

    private void blurEligibility() {
        ReactiveDomain redirectDomain = ReactiveDomain.direct();
        Page redirected = new Page(redirectDomain, state(redirectDomain, ""), SceneTextField.SubmitPolicy.BLUR);
        redirected.edit(SceneEditEvent.text("同步返回仍需提交"));
        redirectDomain.execute(() -> {
            club.heiqi.uilib.ui.reactive.Owner observerOwner = redirectDomain.owner();
            club.heiqi.uilib.ui.reactive.Observation observer = redirectDomain.observe(observerOwner,
                    () -> redirected.session.runtime().focusRouter().focused().get(), () -> {
                        if (redirected.session.runtime().focusRouter().focused().get() == redirected.button)
                            redirected.session.runtime().focusRouter().request(
                                    redirected.session.runtime().captureMount(redirected.field));
                    });
            observer.refresh();
            try {
                redirected.session.runtime().focusRouter().request(
                        redirected.session.runtime().captureMount(redirected.button));
            } finally { observerOwner.dispose(); }
            require(redirected.session.runtime().focusRouter().focused().get() == redirected.field,
                    "同步订阅返回字段由唯一Router保留");
            require(redirected.submissions.size() == 1 && redirected.submissions.get(0).complete,
                    "实际焦点离开后同步返回仍依序执行一次BLUR提交");
        });
        redirected.edit(SceneEditEvent.text("后续输入"));
        redirected.expect("同步返回仍需提交后续输入");
        ReactiveDomain d = ReactiveDomain.direct();
        Page p = new Page(d, state(d, ""), SceneTextField.SubmitPolicy.BLUR);
        p.edit(SceneEditEvent.text("draft"));
        p.session.pointer(ScenePointerEvent.down(0, p.host.width() - 1, p.host.height() - 1));
        p.session.edit(SceneEditEvent.text("must not edit blurred field"));
        p.session.pointer(ScenePointerEvent.up(0, p.host.width() - 1, p.host.height() - 1));
        p.session.frame();
        p.expect("draft");
        d.execute(() -> require(p.session.runtime().focusRouter().focused().get() == null
                && p.snapshot().active, "背景失焦没有清焦点或擅自结束独立草稿会话"));
        require(p.submissions.size() == 1 && p.submissions.get(0).complete
                && "draft".equals(p.submissions.get(0).text), "空白正常blur未单次提交已有草稿");
        p.session.pointer(ScenePointerEvent.down(0, p.host.width() - 1, p.host.height() - 1));
        p.session.pointer(ScenePointerEvent.up(0, p.host.width() - 1, p.host.height() - 1));
        p.session.frame();
        require(p.submissions.size() == 1, "已无焦点的后续空白点击重复提交");
        p.focusField();
        d.execute(() -> p.enabled.set(false));
        p.session.frame();
        require(p.submissions.size() == 1, "禁用字段隐含提交");
        d.execute(() -> p.enabled.set(true));
        p.session.frame();
        p.focusField();
        d.execute(() -> p.modalOpen.set(true));
        p.session.frame();
        p.edit(SceneEditEvent.text("modal text"));
        p.expect("draft");
        require(p.submissions.size() == 1, "模态隔离隐含提交");
        d.execute(() -> p.modalOpen.set(false));
        p.session.frame();
        p.focusField();
        p.session.close();
        require(p.submissions.size() == 1, "关闭隐含blur提交");
        Page input = new Page(d, state(d, ""), SceneTextField.SubmitPolicy.INPUT);
        input.edit(SceneEditEvent.text("-"));
        require(input.submissions.size() == 1 && !input.submissions.get(0).complete, "随输入请求应保持未完成");
        d.execute(
                () ->
                        require(
                                input.state.accept(input.submissions.get(0), "-")
                                        && input.snapshot().active,
                                "随输入接受结束会话"));
    }

    private void clipboardAndPointer() {
        ReactiveDomain d = ReactiveDomain.direct();
        Page p = new Page(d, state(d, "WfiQ"), SceneTextField.SubmitPolicy.CONFIRM);
        Clipboard clipboard = new Clipboard();
        d.execute(() -> p.readOnly.set(true));
        p.command(Command.SELECT_ALL);
        p.edit(SceneEditEvent.command(Command.COPY, false, clipboard));
        require("WfiQ".equals(clipboard.value), "只读不能复制选区");
        clipboard.value = "替换";
        for (Command command :
                new Command[] {
                    Command.PASTE, Command.CUT, Command.BACKSPACE, Command.DELETE, Command.CONFIRM
                }) p.edit(SceneEditEvent.command(command, false, clipboard));
        p.edit(SceneEditEvent.text("不可写"));
        p.expect("WfiQ");
        d.execute(() -> require(!p.snapshot().active && p.submissions.isEmpty(), "只读修改或确认开启会话"));
        p.command(Command.RIGHT);
        p.session.frame();
        // 点击坐标从已呈现的生产文字簇读取；fi簇横跨两个UTF-16字符。
        int[] point = new int[3];
        d.execute(
                () -> {
                    SceneGeometryView.Entry e =
                            p.session.pipeline().current().geometry.get(p.field);
                    TextLayout.Cluster cluster = e.text.lines().get(0).clusters().get(1);
                    require(
                            cluster.sourceStart == 1
                                    && cluster.sourceEnd == 3
                                    && !e.text.lines().get(0).glyphs().isEmpty(),
                            "字体fixture未提供完整fi簇");
                    point[0] =
                            e.box.x + p.field.padding() + (int) (cluster.x + cluster.advance - 2);
                    point[1] = e.box.y + p.field.padding() + 2;
                    point[2] = e.box.x + p.field.padding() + 1;
                });
        p.session.navigation(SceneNavigationEvent.NEXT);
        p.session.frame();
        p.session.pointer(ScenePointerEvent.down(0, point[0], point[1]));
        p.session.frame();
        d.execute(
                () ->
                        require(
                                p.session.runtime().focusRouter().focused().get() == p.field
                                        && p.snapshot().anchor == 3
                                        && p.snapshot().caret == 3,
                                "DOWN没聚焦并选择最近文字簇边界"));
        p.session.pointer(ScenePointerEvent.move(point[2], point[1]));
        p.session.frame();
        d.execute(
                () ->
                        require(
                                p.snapshot().anchor == 3 && p.snapshot().caret == 0,
                                "MOVE没沿原owner延伸选区"));
        p.session.pointer(ScenePointerEvent.up(0, point[2], point[1]));
        p.session.frame();
        d.execute(
                () -> require(p.snapshot().anchor == 3 && p.snapshot().caret == 0, "UP或click重置拖选"));
        d.execute(() -> p.readOnly.set(false));
        p.command(Command.END);
        clipboard.value = "粘\r\n贴";
        p.edit(SceneEditEvent.command(Command.PASTE, false, clipboard));
        p.expect("WfiQ粘贴");
        p.command(Command.UNDO);
        p.expect("WfiQ");
        p.command(Command.REDO);
        p.expect("WfiQ粘贴");
    }

    private void historyAndReadonlyComposition() {
        club.heiqi.uilib.ui.scene.host.SceneEditInputAdapter gate =
                new club.heiqi.uilib.ui.scene.host.SceneEditInputAdapter();
        gate.historyDrained(java.util.Collections.singleton(30));
        require(!gate.key(30, true, true) && !gate.available(), "持键repeat文本绕过开页尾部门");
        gate.key(30, false, false);
        require(gate.available(), "真实释放后文本门未恢复");
        require(gate.key(30, true, false) && gate.available(), "新普通按压误进入blocked");
        gate.availability(false);
        gate.availability(true);
        gate.historyDrained(java.util.Collections.singleton(30));
        require(!gate.available(), "失焦旧持键文本漏出");
        gate.key(30, false, false);
        require(gate.available(), "恢复窗口后真实释放无效");
        ReactiveDomain d = ReactiveDomain.direct();
        Page p = new Page(d, state(d, "ab"), SceneTextField.SubmitPolicy.CONFIRM);
        p.edit(SceneEditEvent.composition("拼音", true));
        d.execute(() -> p.readOnly.set(true));
        p.edit(SceneEditEvent.composition("", false));
        p.command(Command.SELECT_ALL);
        p.command(Command.COPY);
        d.execute(
                () ->
                        require(
                                !p.snapshot().composing
                                        && p.snapshot().anchor == 0
                                        && p.snapshot().caret == 2,
                                "只读转换不能结束组字或选择"));
        p.session.editOrNavigate(
                SceneEditEvent.command(Command.TAB, false, null), SceneNavigationEvent.NEXT);
        p.session.edit(SceneEditEvent.text("must not edit old field"));
        p.session.frame();
        d.execute(
                () ->
                        require(
                                p.session.runtime().focusRouter().focused().get() == p.button,
                                "只读字段被旧组字锁住Tab"));
    }

    public static void main(String[] args) throws Exception {
        new EditingScenario().run(args);
    }
}
