package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.edit.SceneEditState.Mode;
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

/** editing spec 1.1.2：正式页面、状态和路由回归；不冒称宿主原生键盘或真实字体验收。 */
public final class MultilineEditingScenario extends TestApplication {
    /** 只提供无native的字体边界事实；没有光标、选区、历史或编辑算法。真实字体另在GPU应用验收。 */
    private static final class PlainHost implements SceneHost, SceneTextMeasurer {
        int width = 360, height = 280;
        public int width() { return width; }
        public int height() { return height; }
        public long metricsRevision() { return 0; }
        public void replay(PaintPlan plan) { }
        public TextLayout layout(String text, TextStyle style, int width) { return layout(text, style, width, TextPolicy.NORMAL); }
        public TextLayout layout(String text, TextStyle style, int width, TextPolicy policy) {
            List<TextLayout.Line> lines = new ArrayList<>();
            float cell = style.size / 2f, height = style.size + 2, max = 0;
            int capacity = Math.max(1, (int)(width / cell));
            int start = 0;
            for (;;) {
                int newline = text.indexOf('\n', start), end = newline < 0 ? text.length() : newline;
                int part = start;
                do {
                    int stop = Math.min(end, part + capacity);
                    List<TextLayout.Cluster> clusters = new ArrayList<>();
                    for (int i = part; i < stop; i++) clusters.add(new TextLayout.Cluster(i, i+1, i, i+1, 0, 0, (i-part)*cell, cell, style));
                    float advance = (stop-part)*cell;
                    int y = Math.round(lines.size()*height);
                    lines.add(new TextLayout.Line(text.substring(part,stop),0,y,part,stop,part,stop,advance,
                        y+style.size*.75f,style.size*.75f,style.size*.25f,height,Collections.emptyList(),clusters,Collections.emptyList()));
                    max = Math.max(max,advance); part = stop;
                } while (part < end);
                if (newline < 0) break;
                start = newline+1;
            }
            return new TextLayout((int)Math.ceil(max),(int)Math.ceil(lines.size()*height),max,lines.size()*height,
                style,lines,text,text,0,Collections.emptyList());
        }
    }
    private static final class Clipboard implements SceneEditEvent.Clipboard {
        String value = "";
        public String read() { return value; }
        public void write(String text) { value = text; }
    }
    private final class Page {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final PlainHost host = new PlainHost();
        final SceneEditState state;
        final List<Submission> submissions = new ArrayList<>();
        Signal<Boolean> readOnly, enabled;
        SceneNode area, button, outer;
        final ScenePageSession session;
        Page(String baseline) { this(baseline,SceneTextField.SubmitPolicy.CONFIRM); }
        Page(String baseline, SceneTextField.SubmitPolicy policy) {
            state = state(domain,baseline,Mode.MULTI_LINE);
            session = ScenePageSession.open(domain,host,host,ctx -> {
                readOnly=domain.signal(false); enabled=domain.signal(true);
                Signal<SceneTheme> theme=domain.signal(SceneTheme.light());
                area=SceneTextArea.create(ctx,state,theme,readOnly,submissions::add,policy)
                    .size(Size.fill(1),Size.fixed(96)).bind(NodeBinding.value(enabled,SceneNode::enabled));
                button=SceneButton.create(ctx,()->"下一项",theme,()->{});
                outer=new SceneNode().size(Size.fill(1),Size.fixed(180)).scrollable(true)
                    .initialFocus(area).add(area).add(button).add(new SceneNode().size(Size.fixed(20),Size.fixed(240)));
                ctx.mount(outer);
            });
            onClose(session::close); session.frame();
        }
        void edit(SceneEditEvent event) { session.edit(event); session.frame(); }
        void command(Command command) { edit(SceneEditEvent.command(command,false,null)); }
        void command(Command command, boolean shift) { edit(SceneEditEvent.command(command,shift,null)); }
        Snapshot snapshot() { final Snapshot[] value={null};domain.execute(()->value[0]=state.snapshot().get());return value[0]; }
        SceneGeometryView.Entry entry(SceneNode node) { final SceneGeometryView.Entry[] value={null};domain.execute(()->value[0]=session.pipeline().current().geometry.get(node));return value[0]; }
        void expect(String expected) { require(expected.equals(snapshot().text),"草稿预期="+expected+" 实际="+snapshot().text); }
        void click(int lineIndex, int clusterIndex) {
            SceneGeometryView.Entry e=entry(area);TextLayout.Line line=e.text.lines().get(lineIndex);
            TextLayout.Cluster c=line.clusters().get(clusterIndex);
            int x=e.box.x+area.padding()+Math.round(c.x),y=e.box.y+area.padding()+line.y+Math.round(line.lineHeight/2);
            session.pointer(ScenePointerEvent.down(0,x,y));session.frame();
            session.pointer(ScenePointerEvent.up(0,x,y));session.frame();
            require(snapshot().caret==c.sourceStart && snapshot().anchor==c.sourceStart,"点击未使用共享行簇源几何");
        }
    }
    private SceneEditState state(ReactiveDomain d,String text,Mode mode) {
        final SceneEditState[] s={null};d.execute(()->s[0]=new SceneEditState(d,text,mode));return s[0];
    }
    protected void runTests() throws Exception {
        scenario("固定模式所有文本入口与接受历史保留LF",this::modeEntries);
        scenario("正式Area换行原子历史明确提交旧结果及IME候选消费",this::confirmAndIme);
        scenario("跨行剪贴板只读Tab与禁用释放不提交",this::clipboardAndEligibility);
        scenario("共享排版的上下水平目标视觉HomeEnd及全文选择",this::lineNavigation);
        scenario("内部滚轮端点消费稳定帧及resize保留状态",this::scrollAndResize);
    }
    private void modeEntries() {
        ReactiveDomain d=ReactiveDomain.direct();
        for(Mode mode:Mode.values()) {
            boolean multiline=mode==Mode.MULTI_LINE;
            SceneEditState s=state(d,"a\r\nb\rc\n",mode);
            d.execute(()->{
                require(s.mode()==mode && s.text().get().equals(multiline?"a\nb\nc\n":"abc"),"构造模式规则错误");
                s.synchronizeExternal("x\r\ny\r");require(s.text().get().equals(multiline?"x\ny\n":"xy"),"外部值未按固定模式归一");
                s.reset("base\r\n");s.select(0,s.text().get().length());s.replaceSelection("draft\r\nlast\r",false);
                String draft=multiline?"draft\nlast\n":"draftlast";
                require(s.text().get().equals(draft),"普通插入丢换行合同");
                s.composition("pre\r\nedit\r",true);
                require(s.snapshot().get().composition.equals(multiline?"pre\nedit\n":"preedit"),"组字入口未归一");
                s.composition("",false);Submission request=s.request(true);
                require(s.accept(request,"accepted\r\nlast\r") && !s.snapshot().get().active,"接受未完成");
                require(s.text().get().equals(multiline?"accepted\nlast\n":"acceptedlast"),"接受文本丢末尾空行");
                s.undo();require(s.text().get().equals(multiline?"base\n":"base") && s.snapshot().get().active,"完成接受清掉历史");
                s.redo();s.cancel();require(s.text().get().equals(multiline?"accepted\nlast\n":"acceptedlast"),"取消未恢复归一基线");
                s.reset("reset\r\n");s.undo();require(s.text().get().equals(multiline?"reset\n":"reset"),"reset历史串旧对象");
            });
        }
        final SceneEditState[] legacy={null};d.execute(()->legacy[0]=new SceneEditState(d,"a\r\nb"));
        d.execute(()->require(legacy[0].mode()==Mode.SINGLE_LINE && legacy[0].text().get().equals("ab"),"旧构造契约改变"));
    }
    private void confirmAndIme() {
        Page p=new Page("");p.edit(SceneEditEvent.text("ab"));p.command(Command.CONFIRM);p.expect("ab\n");
        require(p.submissions.isEmpty(),"Enter错误提交");p.command(Command.UNDO);p.expect("ab");p.command(Command.REDO);p.expect("ab\n");
        p.command(Command.SUBMIT);require(p.submissions.size()==1 && p.submissions.get(0).complete && p.submissions.get(0).text.equals("ab\n"),"明确SUBMIT请求错误");
        Submission old=p.submissions.get(0);p.edit(SceneEditEvent.text("c"));Snapshot current=p.snapshot();
        p.domain.execute(()->{require(!p.state.reject(old,"旧错误") && old.status().get().status==Status.REJECTED,"旧请求没记录自身结果");});
        require(p.snapshot().error==null && p.snapshot().caret==current.caret && p.snapshot().text.equals(current.text),"旧错误覆盖新草稿");
        p.command(Command.SUBMIT);Submission latest=p.submissions.get(1);
        p.domain.execute(()->require(p.state.reject(latest,"请补完整"),"当前拒绝无效"));p.session.frame();
        require(p.snapshot().active && "请补完整".equals(p.snapshot().error),"当前拒绝丢原因或草稿");
        p.edit(SceneEditEvent.composition("候选",true));current=p.snapshot();
        for(Command c:new Command[]{Command.CONFIRM,Command.SUBMIT,Command.UP,Command.DOWN,Command.HOME,Command.END,Command.TAB}) p.command(c);
        require(p.snapshot().composing && p.snapshot().text.equals(current.text) && p.snapshot().caret==current.caret && p.submissions.size()==2,"IME候选键修改草稿或提交");
        p.domain.execute(()->require(p.session.runtime().focusRouter().focused().get()==p.area,"IME候选Tab泄漏导航"));
        p.edit(SceneEditEvent.committed("中文\r\n"));p.expect("ab\nc中文\n");p.command(Command.UNDO);p.expect("ab\nc");
        p.command(Command.REDO);p.command(Command.CONFIRM);p.expect("ab\nc中文\n\n");
        require(p.submissions.size()==2,"组字结束后Enter应换行");
    }
    private void clipboardAndEligibility() {
        Page p=new Page("first\nsecond\nthird");Clipboard clip=new Clipboard();
        p.command(Command.DOCUMENT_HOME);p.command(Command.DOWN,true);p.edit(SceneEditEvent.command(Command.CUT,false,clip));
        require(clip.value.equals("first\n"),"跨行剪切丢LF");p.expect("second\nthird");p.command(Command.UNDO);p.expect("first\nsecond\nthird");
        p.command(Command.DOCUMENT_END);clip.value="\r\npaste\r";p.edit(SceneEditEvent.command(Command.PASTE,false,clip));p.expect("first\nsecond\nthird\npaste\n");
        p.command(Command.UNDO);p.expect("first\nsecond\nthird");p.command(Command.REDO);
        p.domain.execute(()->p.readOnly.set(true));p.session.frame();p.command(Command.SELECT_ALL);p.edit(SceneEditEvent.command(Command.COPY,false,clip));
        require(clip.value.equals(p.snapshot().text),"只读跨行复制失败");Snapshot before=p.snapshot();
        for(Command c:new Command[]{Command.CONFIRM,Command.SUBMIT,Command.CUT,Command.PASTE,Command.DELETE,Command.UNDO}) p.edit(SceneEditEvent.command(c,false,clip));
        p.edit(SceneEditEvent.text("禁止"));require(p.snapshot().text.equals(before.text) && p.snapshot().revision==before.revision && p.submissions.isEmpty(),"只读修改或提交");
        p.command(Command.DOCUMENT_HOME);p.command(Command.DOWN);require(p.snapshot().caret>0,"只读不能上下查看");
        p.session.editOrNavigate(SceneEditEvent.command(Command.TAB,false,null),SceneNavigationEvent.NEXT);p.session.edit(SceneEditEvent.text("旧焦点漏写"));p.session.frame();
        p.domain.execute(()->require(p.session.runtime().focusRouter().focused().get()==p.button,"Tab没离开只读Area"));p.expect(before.text);
        p.session.editOrNavigate(SceneEditEvent.command(Command.TAB,true,null),SceneNavigationEvent.PREVIOUS);p.session.frame();
        p.domain.execute(()->require(p.session.runtime().focusRouter().focused().get()==p.area,"Shift+Tab没返回Area"));
        Page blur=new Page("draft\n",SceneTextField.SubmitPolicy.BLUR);
        blur.domain.execute(()->blur.enabled.set(false));blur.session.frame();blur.edit(SceneEditEvent.text("禁用漏写"));
        blur.domain.execute(()->require(blur.session.runtime().focusRouter().focused().get()!=blur.area,"禁用没有释放焦点"));
        require(blur.submissions.isEmpty(),"禁用被当作blur提交");blur.expect("draft\n");
    }
    private void lineNavigation() {
        Page p=new Page("abcdefghij\nx\nabcdefghij");p.click(0,7);
        int sameColumn=p.entry(p.area).text.lines().get(2).clusters().get(7).sourceStart;p.command(Command.DOWN);require(p.snapshot().caret==p.entry(p.area).text.lines().get(1).sourceEnd,"短行未夹取");
        p.command(Command.DOWN);require(p.snapshot().caret==sameColumn,"连续上下没有保留原水平目标");
        p.command(Command.UP,true);require(p.snapshot().anchor==sameColumn,"Shift上下丢anchor");
        p.command(Command.DOCUMENT_END,true);require(p.snapshot().caret==p.snapshot().text.length(),"文档末尾没扩选");
        p.command(Command.UP);p.command(Command.LEFT);p.command(Command.DOWN);
        require(p.snapshot().caret==p.entry(p.area).text.lines().get(2).sourceStart,"水平移动未结束旧水平目标");
        p.command(Command.DOCUMENT_HOME);require(p.snapshot().caret==0 && p.snapshot().anchor==0,"文档首页错误");
        p.domain.execute(()->p.state.reset("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"));p.host.width=130;p.session.frame();
        TextLayout text=p.entry(p.area).text;require(text.lines().size()>2,"默认没有按视口软换行");
        p.click(1,2);TextLayout.Line visual=text.lines().get(1);p.command(Command.HOME);require(p.snapshot().caret==visual.sourceStart,"Home错误跳全文首");
        p.command(Command.END,true);require(p.snapshot().anchor==visual.sourceStart && p.snapshot().caret==visual.sourceEnd,"视觉End或软边界归属错误");
        p.command(Command.DOCUMENT_END);require(p.snapshot().caret==p.snapshot().text.length() && p.snapshot().text.equals(text.originalText),"软换行写回草稿或全文End错误");
    }
    private void scrollAndResize() {
        StringBuilder value=new StringBuilder();for(int i=0;i<24;i++)value.append("row ").append(i).append('\n');
        Page p=new Page(value.toString());p.command(Command.DOCUMENT_HOME);Snapshot before=p.snapshot();
        SceneGeometryView.Entry e=p.entry(p.area);int x=e.box.x+p.area.padding()+2,y=e.box.y+p.area.padding()+2;
        int outer=p.entry(p.outer).scrollOffset;
        int originalY=e.text.lines().get(0).y;
        p.session.pointer(ScenePointerEvent.scroll(x,y,-1));p.session.frame();int offset=originalY-p.entry(p.area).text.lines().get(0).y;
        require(offset>0,"内部滚轮没有发布几何offset");p.session.frame();p.session.frame();
        require(originalY-p.entry(p.area).text.lines().get(0).y==offset,"稳定帧把用户滚动拉回光标");
        p.session.pointer(ScenePointerEvent.scroll(x,y,Integer.MIN_VALUE));p.session.frame();e=p.entry(p.area);
        int endY=e.text.lines().get(0).y;require(endY<originalY,"正文未滚到末端");
        p.session.pointer(ScenePointerEvent.scroll(x,y,-1));p.session.frame();require(p.entry(p.area).text.lines().get(0).y==endY && p.entry(p.outer).scrollOffset==outer,"正文端点未夹取或泄漏外层滚轮");
        require(p.snapshot().text.equals(before.text) && p.snapshot().anchor==before.anchor && p.snapshot().caret==before.caret,"滚动修改编辑状态");
        SceneEditState identity=p.state;p.host.width=170;p.session.frame();e=p.entry(p.area);
        TextLayout.Line last=e.text.lines().get(e.text.lines().size()-1);
        require(p.state==identity && p.snapshot().text.equals(before.text) && last.y+last.lineHeight>=e.box.height-p.area.padding()*2,"resize丢state或正文滚过末端");
        p.domain.execute(()->p.state.reset("a\n"));p.session.frame();require(p.entry(p.area).text.lines().get(0).y==0,"内容缩短没夹取内部滚动");
    }
    public static void main(String[] args) throws Exception { new MultilineEditingScenario().run(args); }
}
