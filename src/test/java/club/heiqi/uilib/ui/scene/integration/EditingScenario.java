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
        final FontSource font = new FontSource("fixture", 0, "fixture", new byte[]{1}, 0, false, Collections.emptySet());
        @Override public int width() { return 420; }
        @Override public int height() { return 320; }
        @Override public long metricsRevision() { return 0; }
        @Override public void replay(PaintPlan plan) { }
        @Override public TextLayout layout(String text, TextStyle style, int width) {
            return layout(text, style, width, TextPolicy.NORMAL);
        }
        @Override public TextLayout layout(String text, TextStyle style, int width, TextPolicy policy) {
            List<TextLayout.Glyph> glyphs = new ArrayList<>();
            List<TextLayout.Cluster> clusters = new ArrayList<>();
            float x = 0, ascent = style.size * .75f;
            for (int start = 0; start < text.length();) {
                int end = text.startsWith("fi", start) ? start + 2 : start + Character.charCount(text.codePointAt(start));
                float advance = text.startsWith("fi", start) ? 23 : text.charAt(start) == 'W' ? 31 : 9;
                int glyph = glyphs.size();
                glyphs.add(new TextLayout.Glyph(font, style.size, text.codePointAt(start), start, end,
                    start, end, x, ascent, advance, 0, false, false));
                clusters.add(new TextLayout.Cluster(start, end, start, end, glyph, glyph + 1, x, advance, style));
                x += advance; start = end;
            }
            TextLayout.Line line = new TextLayout.Line(text, 0, 0, 0, text.length(), 0, text.length(),
                x, ascent, ascent, style.size - ascent, style.size + 2, glyphs, clusters, Collections.emptyList());
            return new TextLayout((int)Math.ceil(x), style.size + 2, x, style.size + 2, style,
                Collections.singletonList(line), text, text, 0, Collections.emptyList());
        }
    }

    private static final class Clipboard implements SceneEditEvent.Clipboard {
        String value = "";
        @Override public String read() { return value; }
        @Override public void write(String text) { value = text; }
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
            this.domain = domain; this.state = state;
            session = ScenePageSession.open(domain, host, host, ctx -> {
                readOnly = domain.signal(false); enabled = domain.signal(true); modalOpen = domain.signal(false);
                Signal<SceneTheme> theme = domain.signal(SceneTheme.light());
                field = SceneTextField.create(ctx, state, theme, readOnly, submissions::add, policy)
                    .size(Size.fixed(260), Size.natural()).bind(NodeBinding.value(enabled, SceneNode::enabled));
                button = SceneButton.create(ctx, () -> "按钮", theme, () -> clicks++);
                SceneNode modal = new SceneNode().modal(true).visible(false)
                    .bind(NodeBinding.value(modalOpen, SceneNode::visible))
                    .add(new SceneNode().text("模态").focusable(true));
                ctx.mount(new SceneNode().initialFocus(field).add(field).add(button).add(modal));
            });
            onClose(session::close); session.frame();
        }
        void edit(SceneEditEvent event) { session.edit(event); if (!session.ended()) session.frame(); }
        void command(Command command) { edit(SceneEditEvent.command(command, false, null)); }
        Snapshot snapshot() { return state.snapshot().get(); }
        void expect(String text) { domain.execute(() -> require(text.equals(state.text().get()), "草稿预期=" + text + " 实际=" + state.text().get())); }
        void focusField() { domain.execute(() -> session.runtime().focusRouter().request(session.runtime().captureMount(field))); }
    }

    private SceneEditState state(ReactiveDomain domain, String baseline) {
        SceneEditState[] result = new SceneEditState[1];
        domain.execute(() -> result[0] = new SceneEditState(domain, baseline));
        return result[0];
    }

    @Override protected void runTests() throws Exception {
        scenario("持键文字门与只读组字结束恢复选择导航", this::historyAndReadonlyComposition);
        scenario("未完成草稿及拒绝不改业务值，不因选择开启会话", this::draftAndRejection);
        scenario("旧异步结果记录原请求，重置同文及同代最新请求保持身份隔离", this::requestIdentity);
        scenario("完成规范化保留可用历史，随输入接受继续会话", this::normalizationHistory);
        scenario("外部值仅同步非活跃状态，关闭重开复用独立草稿", this::externalAndReopen);
        scenario("代理对组合符ZWJ整簇移动删除，连续输入删除与原子插入历史", this::unicodeAndHistory);
        scenario("正式页面文字空格确认Tab和IME及ESC沿唯一焦点序列", this::pageEditing);
        scenario("仅真实blur请求完成，禁用模态与关闭不自动提交", this::blurEligibility);
        scenario("只读选择复制拒绝修改，粘贴单步撤销与生产文字簇指针定位", this::clipboardAndPointer);
    }

    private void draftAndRejection() {
        ReactiveDomain d = ReactiveDomain.direct(); SceneEditState s = state(d, "12");
        String[] business = {"12"};
        d.execute(() -> {
            s.home(false); s.move(1, true);
            require(!s.snapshot().get().active, "移动选择不能开启会话");
            s.select(0, s.text().get().length()); s.replaceSelection("-", false);
            require("-".equals(s.text().get()) && "12".equals(business[0]), "负号不能自动保存业务值");
            Submission request = s.request(true);
            require(request.status().get().status == Status.PENDING && request.complete, "请求未待定");
            require(s.reject(request, "请输入完整整数"), "当前拒绝未应用");
            Snapshot rejected = s.snapshot().get();
            require(rejected.active && "-".equals(rejected.text) && "请输入完整整数".equals(rejected.error), "拒绝丢草稿或原因");
            s.select(0, 1); s.replaceSelection("", false);
            require(s.text().get().isEmpty() && s.snapshot().get().active && "12".equals(business[0]), "空串不能自动写业务");
            s.cancel(); require("12".equals(s.text().get()) && !s.snapshot().get().active, "取消未恢复基线");
        });
    }

    private void requestIdentity() {
        ReactiveDomain d = ReactiveDomain.direct(); SceneEditState s = state(d, "");
        d.execute(() -> {
            s.replaceSelection("a"); Submission old = s.request(true);
            s.replaceSelection("b"); s.select(0, 1); Snapshot current = s.snapshot().get();
            require(!s.accept(old, "OLD"), "旧接受覆盖新草稿");
            require(old.status().get().status == Status.ACCEPTED && "OLD".equals(old.status().get().text), "旧请求没记录接受结果");
            Snapshot after = s.snapshot().get();
            require(after.text.equals(current.text) && after.anchor == current.anchor && after.caret == current.caret
                && after.active == current.active && after.error == null, "旧接受改变当前选区或会话");
            Submission resetOld = s.request(true); long before = s.snapshot().get().revision;
            s.reset("ab"); require(s.snapshot().get().revision > before, "同文重置没有新代际证据");
            require(!s.reject(resetOld, "迟到错误") && s.snapshot().get().error == null, "同文重置仍收到旧错误");
            require(resetOld.status().get().status == Status.REJECTED && "迟到错误".equals(resetOld.status().get().reason), "旧拒绝结果失真");
            s.replaceSelection("c"); Submission first = s.request(true), latest = s.request(false);
            require(first != latest && first.revision == latest.revision, "请求必须独立于文本修订身份");
            require(!s.accept(first, "FIRST") && s.accept(latest, "abc"), "同修订的最新请求未隔离");
            require(!s.reject(latest, "重复结果") && latest.status().get().status == Status.ACCEPTED, "重复完成改写结果");
        });
    }

    private void normalizationHistory() {
        ReactiveDomain d = ReactiveDomain.direct(); SceneEditState s = state(d, "base");
        d.execute(() -> {
            s.select(0, 4); s.replaceSelection("  value  ", false);
            Submission request = s.request(true);
            require(s.accept(request, "value") && !s.snapshot().get().active, "完成接受未规范化并结束会话");
            s.undo(); require("base".equals(s.text().get()) && s.snapshot().get().active, "规范化清历史或撤销陷入格式修正");
            s.redo(); require("value".equals(s.text().get()), "重做未恢复规范化结果");
            Submission input = s.request(false); require(s.accept(input, "value") && s.snapshot().get().active, "随输入接受不应结束会话");
            s.reset("new object"); s.undo(); require("new object".equals(s.text().get()) && !s.snapshot().get().active, "重置可以撤销到旧对象");
        });
    }

    private void externalAndReopen() {
        ReactiveDomain d = ReactiveDomain.direct(); SceneEditState s = state(d, "first");
        d.execute(() -> {
            s.synchronizeExternal("second"); require("second".equals(s.text().get()), "无会话未同步外部值");
            s.replaceSelection(" draft"); s.synchronizeExternal("outside");
            require("second draft".equals(s.text().get()), "外部值覆盖活跃草稿");
        });
        Page first = new Page(d, s, SceneTextField.SubmitPolicy.CONFIRM);
        SceneNode oldNode = first.field; first.session.close();
        d.execute(() -> s.replaceSelection(" retained", false));
        Page reopened = new Page(d, s, SceneTextField.SubmitPolicy.CONFIRM);
        reopened.expect("second draft retained");
        d.execute(() -> {
            require(oldNode != reopened.field && first.submissions.isEmpty(), "重开复用旧节点或关闭自动提交");
            require(reopened.session.runtime().focusRouter().focused().get() == reopened.field, "新页面未建立字段焦点");
        });
        first.edit(SceneEditEvent.text(" stale")); reopened.expect("second draft retained");
        reopened.edit(SceneEditEvent.text("!")); reopened.expect("second draft retained!");
    }

    private void unicodeAndHistory() {
        ReactiveDomain d = ReactiveDomain.direct();
        SceneEditState ordinaryJoin=state(d,"a\u200Db");
        d.execute(()->{ordinaryJoin.deleteBackward();require("a\u200D".equals(ordinaryJoin.text().get()),"普通字母ZWJ误删前字");ordinaryJoin.reset("a\u200Db");ordinaryJoin.home(false);ordinaryJoin.move(1,false);require(ordinaryJoin.snapshot().get().caret==2,"普通字母ZWJ误跳后字");});
        String emoji = "😀", combining = "e\u0301", family = "👩‍👩‍👧‍👦";
        SceneEditState s = state(d, "A" + emoji + combining + family + "Z");
        d.execute(() -> {
            s.home(false); s.move(1, false); s.move(1, false);
            require(s.snapshot().get().caret == ("A" + emoji).length(), "移动拆开代理对");
            s.deleteForward(); require(("A" + emoji + family + "Z").equals(s.text().get()), "删除拆开组合符");
            s.move(1, false); require(s.snapshot().get().caret == ("A" + emoji + family).length(), "移动拆开ZWJ家庭");
            s.deleteBackward(); require(("A" + emoji + "Z").equals(s.text().get()), "删除拆开ZWJ家庭");
            s.undo(); require(("A" + emoji + family + "Z").equals(s.text().get()), "删除撤销没恢复整簇");
            s.reset(""); s.replaceSelection("a"); s.replaceSelection("b"); s.replaceSelection("c");
            s.undo(); require(s.text().get().isEmpty(), "连续输入未合组"); s.redo();
            s.replaceSelection("粘贴", false); s.undo(); require("abc".equals(s.text().get()), "原子插入混入输入历史");
            s.deleteBackward(); s.deleteBackward(); s.undo(); require("abc".equals(s.text().get()), "同向删除未合组");
            s.home(false); s.replaceSelection("x"); s.end(false); s.replaceSelection("y");
            s.undo(); require("xabc".equals(s.text().get()), "光标移动没有分隔输入组");
        });
    }

    private void pageEditing() {
        ReactiveDomain d = ReactiveDomain.direct(); Page p = new Page(d, state(d, ""), SceneTextField.SubmitPolicy.CONFIRM);
        // 同帧采集文字与空格，正式队列逐事件结算，不能只保留末项。
        p.session.edit(SceneEditEvent.text("甲\r\n乙"));
        p.session.edit(SceneEditEvent.command(Command.SPACE, false, null)); p.session.frame(); p.expect("甲乙 ");
        require(p.submissions.isEmpty() && p.clicks == 0, "普通文字或空格触发业务确认");
        p.edit(SceneEditEvent.composition("拼音", true)); p.command(Command.CONFIRM);
        require(p.submissions.isEmpty(), "IME确认同时提交字段");
        p.command(Command.LEFT);p.command(Command.BACKSPACE);p.command(Command.SPACE);p.command(Command.TAB);
        p.expect("甲乙 ");d.execute(()->require(p.snapshot().composing && p.snapshot().caret==3 && p.session.runtime().focusRouter().focused().get()==p.field,"IME候选键改了草稿或焦点"));
        int[] fallback = {0}; p.session.escape(() -> fallback[0]++); p.session.frame();
        d.execute(() -> require(!p.snapshot().composing && p.snapshot().active, "ESC未仅取消preedit"));
        require(fallback[0] == 0 && p.submissions.isEmpty(), "组字ESC误关闭或提交");
        p.edit(SceneEditEvent.composition("中", true)); p.edit(SceneEditEvent.committed("中文"));
        p.expect("甲乙 中文"); p.command(Command.UNDO); p.expect("甲乙 "); p.command(Command.REDO);
        p.command(Command.CONFIRM);
        require(p.submissions.size() == 1 && p.submissions.get(0).complete && "甲乙 中文".equals(p.submissions.get(0).text), "字段确认请求错误");
        p.session.navigation(SceneNavigationEvent.NEXT); p.session.frame();
        d.execute(() -> require(p.session.runtime().focusRouter().focused().get() == p.button, "Tab没有离开字段"));
        require(p.submissions.size() == 1, "默认Tab隐含提交");
        p.edit(SceneEditEvent.text("wrong target")); p.expect("甲乙 中文");
        p.session.navigation(SceneNavigationEvent.ACTIVATE); p.session.frame(); require(p.clicks == 1, "按钮导航激活失效");
        p.focusField(); p.session.escape(() -> fallback[0]++); p.session.frame();
        require(fallback[0] == 1 && p.submissions.size() == 1, "普通ESC不应确认或取消草稿");
    }

    private void blurEligibility() {
        ReactiveDomain d = ReactiveDomain.direct(); Page p = new Page(d, state(d, ""), SceneTextField.SubmitPolicy.BLUR);
        p.edit(SceneEditEvent.text("draft")); p.session.navigation(SceneNavigationEvent.NEXT); p.session.frame();
        require(p.submissions.size() == 1 && p.submissions.get(0).complete, "真实blur未请求完成");
        p.focusField(); d.execute(() -> p.enabled.set(false)); p.session.frame();
        require(p.submissions.size() == 1, "禁用字段隐含提交");
        d.execute(() -> p.enabled.set(true)); p.session.frame(); p.focusField();
        d.execute(() -> p.modalOpen.set(true)); p.session.frame();
        p.edit(SceneEditEvent.text("modal text")); p.expect("draft");
        require(p.submissions.size() == 1, "模态隔离隐含提交");
        d.execute(() -> p.modalOpen.set(false)); p.session.frame(); p.focusField(); p.session.close();
        require(p.submissions.size() == 1, "关闭隐含blur提交");
        Page input = new Page(d, state(d, ""), SceneTextField.SubmitPolicy.INPUT);
        input.edit(SceneEditEvent.text("-"));
        require(input.submissions.size() == 1 && !input.submissions.get(0).complete, "随输入请求应保持未完成");
        d.execute(() -> require(input.state.accept(input.submissions.get(0), "-") && input.snapshot().active, "随输入接受结束会话"));
    }

    private void clipboardAndPointer() {
        ReactiveDomain d = ReactiveDomain.direct(); Page p = new Page(d, state(d, "WfiQ"), SceneTextField.SubmitPolicy.CONFIRM);
        Clipboard clipboard = new Clipboard();
        d.execute(() -> p.readOnly.set(true)); p.command(Command.SELECT_ALL);
        p.edit(SceneEditEvent.command(Command.COPY, false, clipboard));
        require("WfiQ".equals(clipboard.value), "只读不能复制选区");
        clipboard.value = "替换";
        for (Command command : new Command[]{Command.PASTE, Command.CUT, Command.BACKSPACE, Command.DELETE, Command.CONFIRM})
            p.edit(SceneEditEvent.command(command, false, clipboard));
        p.edit(SceneEditEvent.text("不可写")); p.expect("WfiQ");
        d.execute(() -> require(!p.snapshot().active && p.submissions.isEmpty(), "只读修改或确认开启会话"));
        p.command(Command.RIGHT); p.session.frame();
        // 点击坐标从已呈现的生产文字簇读取；fi簇横跨两个UTF-16字符。
        int[] point = new int[3];
        d.execute(() -> {
            SceneGeometryView.Entry e = p.session.pipeline().current().geometry.get(p.field);
            TextLayout.Cluster cluster = e.text.lines().get(0).clusters().get(1);
            require(cluster.sourceStart == 1 && cluster.sourceEnd == 3 && !e.text.lines().get(0).glyphs().isEmpty(), "字体fixture未提供完整fi簇");
            point[0] = e.box.x + p.field.padding() + (int)(cluster.x + cluster.advance - 2);
            point[1] = e.box.y + p.field.padding() + 2;
            point[2] = e.box.x + p.field.padding() + 1;
        });
        p.session.navigation(SceneNavigationEvent.NEXT); p.session.frame();
        p.session.pointer(ScenePointerEvent.down(0, point[0], point[1])); p.session.frame();
        d.execute(() -> require(p.session.runtime().focusRouter().focused().get() == p.field
            && p.snapshot().anchor == 3 && p.snapshot().caret == 3, "DOWN没聚焦并选择最近文字簇边界"));
        p.session.pointer(ScenePointerEvent.move(point[2], point[1])); p.session.frame();
        d.execute(() -> require(p.snapshot().anchor == 3 && p.snapshot().caret == 0, "MOVE没沿原owner延伸选区"));
        p.session.pointer(ScenePointerEvent.up(0, point[2], point[1])); p.session.frame();
        d.execute(() -> require(p.snapshot().anchor == 3 && p.snapshot().caret == 0, "UP或click重置拖选"));
        d.execute(() -> p.readOnly.set(false)); p.command(Command.END);
        clipboard.value = "粘\r\n贴"; p.edit(SceneEditEvent.command(Command.PASTE, false, clipboard)); p.expect("WfiQ粘贴");
        p.command(Command.UNDO); p.expect("WfiQ"); p.command(Command.REDO); p.expect("WfiQ粘贴");
    }

    private void historyAndReadonlyComposition() {
        club.heiqi.uilib.ui.scene.host.SceneEditInputAdapter gate=new club.heiqi.uilib.ui.scene.host.SceneEditInputAdapter();
        gate.historyDrained(java.util.Collections.singleton(30));
        require(!gate.key(30,true,true) && !gate.available(),"持键repeat文本绕过开页尾部门");
        gate.key(30,false,false);require(gate.available(),"真实释放后文本门未恢复");
        require(gate.key(30,true,false) && gate.available(),"新普通按压误进入blocked");
        gate.availability(false);gate.availability(true);gate.historyDrained(java.util.Collections.singleton(30));
        require(!gate.available(),"失焦旧持键文本漏出");gate.key(30,false,false);require(gate.available(),"恢复窗口后真实释放无效");
        ReactiveDomain d=ReactiveDomain.direct();Page p=new Page(d,state(d,"ab"),SceneTextField.SubmitPolicy.CONFIRM);
        p.edit(SceneEditEvent.composition("拼音",true));d.execute(()->p.readOnly.set(true));
        p.edit(SceneEditEvent.composition("",false));p.command(Command.SELECT_ALL);p.command(Command.COPY);
        d.execute(()->require(!p.snapshot().composing && p.snapshot().anchor==0 && p.snapshot().caret==2,"只读转换不能结束组字或选择"));
        p.session.editOrNavigate(SceneEditEvent.command(Command.TAB,false,null),SceneNavigationEvent.NEXT);p.session.edit(SceneEditEvent.text("must not edit old field"));p.session.frame();
        d.execute(()->require(p.session.runtime().focusRouter().focused().get()==p.button,"只读字段被旧组字锁住Tab"));
    }

    public static void main(String[] args) throws Exception { new EditingScenario().run(args); }
}
