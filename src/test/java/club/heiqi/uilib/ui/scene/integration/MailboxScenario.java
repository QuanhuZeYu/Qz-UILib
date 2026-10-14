package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.mail.MailboxModel;
import club.heiqi.uilib.testing.gallery.mail.MailboxPage;
import club.heiqi.uilib.testing.gallery.mail.MailSamples;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneCheckBox;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import java.util.List;

/** 正式字体与物理输入验证实际邮箱流程，不读取页面内部状态作为输入接缝。 */
public final class MailboxScenario extends TestApplication {
    private FontTextService fonts;
    @Override protected void setup() throws Exception { fonts = own(new FontTextService(new LwjglFontEngine())); }
    private final class View implements AutoCloseable, SceneHost {
        int width = 1400, height = 850;
        PaintPlan plan;
        final ReactiveDomain domain = ReactiveDomain.direct();
        MailboxModel model;
        Signal<SceneTheme> theme;
        final ScenePageSession session;
        View() { this(24,null); }
        View(club.heiqi.uilib.ui.scene.localization.MutableLocalizationSource resources) { this(24,resources); }
        View(int scale, club.heiqi.uilib.ui.scene.localization.MutableLocalizationSource resources) {
            session = ScenePageSession.open(domain, this, fonts, context -> {
                require(context.viewportWidth().get() == width, "初次声明没有宿主空间事实");
                model = new MailboxModel(domain, MailSamples.messages(scale)); context.own(model);
                if(resources != null) {
                    club.heiqi.uilib.ui.scene.localization.LocalizationBinding translation=
                        new club.heiqi.uilib.ui.scene.localization.LocalizationBinding(domain,context.scope(),resources);
                    club.heiqi.uilib.ui.reactive.ReadableSignal<String> subject=translation.text("subject");
                    domain.effect(context.scope(),()->{
                        String current=subject.get();
                        MailboxModel.Message m=model.results.get().stream().filter(i->i.id.equals("mail-0")).findFirst().get().value.get();
                        if(!m.subject.equals(current))model.update(new MailboxModel.Message(m.id,m.sender,current,m.preview,m.body,m.time,m.starred,m.read,m.inbox));
                    });
                }
                theme = domain.signal(SceneTheme.light());
                context.mountOwned(MailboxPage.create(context, theme, model));
            });
            frame();
        }
        public int width() { return width; }
        public int height() { return height; }
        public long metricsRevision() { return fonts.generation(); }
        public void replay(PaintPlan plan) { this.plan = plan; }
        void frame() { session.frame(); }
        SceneGeometryView geometry() { return session.pipeline().current().geometry; }
        void click(String contains) {
            int[] point = new int[2];
            domain.execute(() -> {
                SceneGeometryView.Entry target = geometry().ordered().stream()
                        .filter(e -> e.text != null && e.text.logicalText.contains(contains))
                        .filter(e -> e.node.focusable() && (e.node.control() == null || !e.node.control().acceptsTextInput(e.node)))
                        .filter(e -> (e.clip == null ? e.box : e.box.intersect(e.clip)).height > 0)
                        .findFirst().orElseThrow(() -> new AssertionError("找不到可见操作: " + contains));
                LayoutBox hit = target.clip == null ? target.box : target.box.intersect(target.clip);
                point[0] = hit.x + hit.width / 2; point[1] = hit.y + hit.height / 2;
            });
            session.pointer(ScenePointerEvent.down(0,point[0],point[1]));
            session.pointer(ScenePointerEvent.up(0,point[0],point[1])); frame();
        }
        int[] point(String name) {
            int[] point = new int[2];
            domain.execute(() -> {
                SceneGeometryView.Entry target = geometry().ordered().stream()
                    .filter(e -> e.node.focusable() && e.node.text() != null && e.node.text().startsWith(name))
                    .filter(e -> (e.clip == null ? e.box : e.box.intersect(e.clip)).height > 0)
                    .findFirst().orElseThrow(() -> new AssertionError("没有可见命名操作: " + name));
                LayoutBox hit = target.clip == null ? target.box : target.box.intersect(target.clip);
                point[0]=hit.x+hit.width/2;point[1]=hit.y+hit.height/2;
            });return point;
        }
        void named(String name) {
            int[] p=point(name);session.pointer(ScenePointerEvent.down(0,p[0],p[1]));
            session.pointer(ScenePointerEvent.up(0,p[0],p[1]));frame();
        }
        void search(String value) {
            int[] point = new int[2];
            domain.execute(() -> {
                SceneGeometryView.Entry target = geometry().ordered().stream()
                        .filter(e -> e.node.control() != null && e.node.control().acceptsTextInput(e.node)).findFirst().get();
                point[0] = target.box.x + 10; point[1] = target.box.y + target.box.height / 2;
            });
            session.pointer(ScenePointerEvent.down(0,point[0],point[1]));
            session.pointer(ScenePointerEvent.up(0,point[0],point[1]));
            session.edit(SceneEditEvent.command(SceneEditEvent.Command.SELECT_ALL,false,null));
            session.edit(SceneEditEvent.text(value)); frame();
        }
        boolean painted(String text) {
            return plan.commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.TEXT && c.text.logicalText.contains(text));
        }
        @Override public void close() { session.close(); }
    }
    @Override protected void runTests() throws Exception {
        scenario("独立行操作批量归档与物理尾部",this::independentOperations);
        scenario("正式复选三态Space和禁用释放",this::checkBoxInput);
        scenario("真实语言资料换代同语言重载与字体尺寸变化",this::resources);
        scenario("关闭后持有runtime仍释放UI树",this::released);
        scenario("分页保留完整数据跨页身份与旧物理尾部",this::paged);
        scenario("真实查询阅读返回及宿主空间自动变化", () -> {
            try (View view = new View()) {
                require(view.painted("设计评审"), "实际收件箱没有首条消息");
                view.search("Café");
                view.domain.execute(() -> require(view.model.results.get().stream().allMatch(i -> i.value.get().subject.contains("Café")), "文字输入未进入业务查询"));
                view.click("本周工坊精选");
                view.domain.execute(() -> require(view.model.opened.get().id.equals("mail-2") && view.model.opened.get().read, "打开阅读没有作用于当前对象"));
                require(view.painted("这封邮件记录"), "阅读正文未进入绘制计划");
                view.width = 720; view.frame();
                require(view.painted("这封邮件记录"), "窄窗口丢失阅读内容");
                view.click("返回列表");
                require(view.painted("本周工坊精选"), "返回列表没有查询结果");
                view.domain.execute(() -> require(!view.model.reading.get() && view.model.query.get().equals("Café"), "返回丢失查询或阅读状态"));
                view.search("不存在的查询"); require(view.painted("没有匹配"), "空查询结果无状态");
            }
        });
        scenario("同键更新重排与删除不打开位置替身", () -> {
            try (View view = new View()) {
                view.click("设计评审");
                view.domain.execute(() -> {
                    MailboxModel.Message m = view.model.opened.get();
                    view.model.update(new MailboxModel.Message(m.id,m.sender,"当前评审稿",m.preview,"更新后的正文",m.time,m.starred,m.read,m.inbox));
                    view.model.reverse();
                });
                view.frame(); require(view.painted("更新后的正文"), "同id当前正文未更新");
                view.domain.execute(() -> view.model.remove("mail-0")); view.frame();
                require(view.painted("邮件已不存在"), "删除后误选其他消息");
                view.domain.execute(() -> view.theme.set(SceneTheme.dark())); view.frame();
                require(view.painted("邮件已不存在"), "主题变化丢失业务状态");
            }
        });
    }
    private void independentOperations() {
        try (View view = new View()) {
            view.named("选择邮件：周五");
            view.domain.execute(() -> require(view.model.checked.get().contains("mail-0") && view.model.openedId.get() == null,
                    "复选触发了阅读或选错id"));
            view.named("添加星标：周五");
            view.domain.execute(() -> require(view.model.results.get().get(0).value.get().starred && view.model.checked.get().size()==1
                    && view.model.openedId.get()==null,"星标串入了勾选或打开"));
            view.named("选择邮件：构建已完成");
            view.named("归档已选邮件");
            view.domain.execute(() -> require(view.model.results.get().stream().noneMatch(i -> i.id.equals("mail-0") || i.id.equals("mail-1"))
                    && view.model.checked.get().isEmpty(),"批量归档对象或清选错误"));
            view.click("所有邮件");
            view.domain.execute(() -> require(view.model.results.get().stream().anyMatch(i -> i.id.equals("mail-0") && !i.value.get().inbox),
                    "归档对象未能在所有邮件找回"));
            view.named("选择邮件：周五");view.search("Café");
            view.domain.execute(() -> require(view.model.checked.get().isEmpty(),"查询变化保留隐藏勾选"));
            view.named("全选全部筛选结果");
            view.domain.execute(() -> require(view.model.checked.get().size()==view.model.results.get().size(),"全选不对应当前结果"));
            view.named("取消选择");view.click("清空搜索");view.click("收件箱");
            int[] down=view.point("选择邮件：Café");
            view.session.pointer(ScenePointerEvent.down(0,down[0],down[1]));view.frame();
            view.domain.execute(() -> view.model.remove("mail-2"));view.frame();
            view.session.pointer(ScenePointerEvent.up(0,down[0],down[1]));view.frame();
            view.domain.execute(() -> require(view.model.checked.get().isEmpty(),"删除后旧释放勾选位置替身"));
            view.named("选择邮件：字体与语言");
            view.domain.execute(() -> require(view.model.checked.get().contains("mail-3"),"新完整点击没有鲜活操作"));
        }
    }
    private void checkBoxInput() {
        ReactiveDomain domain=ReactiveDomain.direct();
        Signal<SceneCheckBox.State>[] state=new Signal[1];Signal<Boolean>[] enabled=new Signal[1];
        PaintPlan[] paint={null};
        SceneHost host=new SceneHost(){public int width(){return 500;}public int height(){return 200;}
            public long metricsRevision(){return fonts.generation();}public void replay(PaintPlan value){paint[0]=value;}};
        CountingText measured=new CountingText(fonts);
        try(ScenePageSession page=ScenePageSession.open(domain,host,measured,context->{
            state[0]=domain.signal(SceneCheckBox.State.MIXED);enabled[0]=domain.signal(true);
            context.mountOwned(SceneCheckBox.create(context,state[0],()->"复选示例",context.theme(),
                    checked->state[0].set(checked?SceneCheckBox.State.CHECKED:SceneCheckBox.State.UNCHECKED))
                    .bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(enabled[0],club.heiqi.uilib.ui.scene.node.SceneNode::enabled)));
        })) {
            page.frame();long before=measured.calls;
            page.pointer(ScenePointerEvent.down(0,10,10));page.pointer(ScenePointerEvent.up(0,10,10));page.frame();
            require(measured.calls==before,"复选状态变化重复请求文字度量");
            domain.execute(()->require(state[0].get()==SceneCheckBox.State.CHECKED,"MIXED激活没有请求true"));
            page.editOrNavigate(SceneEditEvent.command(SceneEditEvent.Command.SPACE,false,null),SceneNavigationEvent.ACTIVATE);page.frame();
            domain.execute(()->require(state[0].get()==SceneCheckBox.State.UNCHECKED,"标准Space没有发布反选"));
            page.pointer(ScenePointerEvent.down(0,10,10));page.frame();domain.execute(()->enabled[0].set(false));page.frame();
            page.pointer(ScenePointerEvent.up(0,10,10));page.frame();
            domain.execute(()->require(state[0].get()==SceneCheckBox.State.UNCHECKED,"禁用后尾部仍激活"));
            require(paint[0].commands().stream().anyMatch(c->c.kind==PaintCommand.Kind.TEXT && c.text.logicalText.contains("复选示例")),
                    "带名称复选框未使用真实文字");
        }
    }
    private static final class CountingText implements club.heiqi.uilib.ui.scene.text.SceneTextMeasurer {
        final club.heiqi.uilib.ui.scene.text.SceneTextMeasurer delegate;long calls;
        CountingText(club.heiqi.uilib.ui.scene.text.SceneTextMeasurer delegate){this.delegate=delegate;}
        public club.heiqi.uilib.ui.scene.text.TextStyle resolveStyle(club.heiqi.uilib.ui.scene.text.TextStyle style){return delegate.resolveStyle(style);}
        public club.heiqi.uilib.ui.scene.text.math.MathFont mathFont(club.heiqi.uilib.ui.scene.text.TextStyle style){return delegate.mathFont(style);}
        public club.heiqi.uilib.ui.scene.text.TextLayout layout(String value,club.heiqi.uilib.ui.scene.text.TextStyle style,int width){calls++;return delegate.layout(value,style,width);}
        public club.heiqi.uilib.ui.scene.text.TextLayout layout(String value,club.heiqi.uilib.ui.scene.text.TextStyle style,int width,club.heiqi.uilib.ui.scene.text.TextPolicy policy){calls++;return delegate.layout(value,style,width,policy);}
        public club.heiqi.uilib.ui.scene.text.TextLayout layout(club.heiqi.uilib.ui.scene.text.AttributedText value,float width,club.heiqi.uilib.ui.scene.text.TextPolicy policy){calls++;return delegate.layout(value,width,policy);}
    }
    private static club.heiqi.uilib.ui.scene.localization.LocalizationSnapshot words(String language,String subject) {
        return new club.heiqi.uilib.ui.scene.localization.LocalizationSnapshot(language,java.util.Collections.singletonMap("subject",subject),java.util.Locale.ROOT);
    }
    private void resources() throws Exception {
        club.heiqi.uilib.ui.scene.localization.MutableLocalizationSource source=
                own(new club.heiqi.uilib.ui.scene.localization.MutableLocalizationSource(words("zh-CN","资源通知初稿")));
        View view=new View(source);
        try {
            require(view.painted("资源通知初稿"),"初次语言资料没有实际文案");
            source.publish(words("en-US","Resource update / current copy"));view.frame();
            view.search("Resource update");
            view.domain.execute(()->require(view.model.results.get().size()==1&&view.model.results.get().get(0).id.equals("mail-0"),"语言变化后的搜索不是当前文案"));
            source.publish(words("en-US","Resource update / same-language revision"));view.frame();
            require(view.painted("same-language revision"),"同语言资料重载未更换文字");
            view.domain.execute(()->view.theme.set(SceneTheme.dark().withTextStyle(SceneTheme.dark().textStyle.withEm(23))));
            view.width=700;view.frame();view.click("same-language revision");
            require(view.painted("这封邮件记录"),"字号主题空间变化后消息不能阅读");
            fonts.reload(FontTextService.defaultSources(getClass().getClassLoader(),fonts.generation()+1));view.frame();
            require(view.painted("same-language revision"),"正式字体代际变化保留旧文字");
        } finally { view.close(); }
        source.publish(words("en-US","after closed"));
        require(source.current().format("subject").equals("after closed"),"页面关闭误销毁外部语言来源");
    }
    private void released() throws Exception {
        View view=new View();
        java.lang.ref.WeakReference<club.heiqi.uilib.ui.scene.node.SceneNode>[] ref=new java.lang.ref.WeakReference[1];
        view.domain.execute(()->ref[0]=new java.lang.ref.WeakReference<>(view.session.runtime().roots().get(0)));
        club.heiqi.uilib.ui.scene.runtime.SceneRuntime retained=view.session.runtime();
        view.close();
        for(int i=0;i<24&&ref[0].get()!=null;i++){System.gc();byte[][] pressure=new byte[8][];for(int j=0;j<pressure.length;j++)pressure[j]=new byte[128*1024];}
        require(ref[0].get()==null,"已关闭且被持有的runtime持续引用页面节点");
        view.domain.execute(()->require(!retained.alive(),"关闭后的runtime仍可路由输入"));
    }
    private void paged() {
        try(View view=new View(1000,null)) {
            view.named("选择邮件：周五");view.click("下一页");
            view.domain.execute(()->require(view.model.checked.get().contains("mail-0")&&view.model.pageItems.get().get(0).id.equals("mail-25"),
                    "翻页把勾选按位置转给其他消息"));
            view.domain.execute(()->{
                MailboxModel.Message m=view.model.results.get().stream().filter(i->i.id.equals("mail-0")).findFirst().get().value.get();
                view.model.update(new MailboxModel.Message(m.id,m.sender,"离页更新后的当前主题",m.preview,m.body,m.time,m.starred,m.read,m.inbox));
            });view.frame();view.click("上一页");require(view.painted("离页更新后的当前主题"),"返回页面未读当前内容");
            int[] p=view.point("选择邮件：离页更新");
            view.session.pointer(ScenePointerEvent.down(0,p[0],p[1]));view.frame();
            view.domain.execute(()->view.model.movePage(1));view.frame();
            view.session.pointer(ScenePointerEvent.up(0,p[0],p[1]));view.frame();
            view.domain.execute(()->require(view.model.checked.get().size()==1&&view.model.checked.get().contains("mail-0"),"跨页旧释放误改选择"));
            view.named("全选全部筛选结果");
            view.domain.execute(()->require(view.model.checked.get().size()==1000,"全选名称的跨页范围与实际不符"));
            view.named("归档已选邮件");
            require(view.painted("没有匹配"),"归档所有结果没有空状态");
            view.click("所有邮件");view.search("离页更新后的当前主题");
            view.click("离页更新后的当前主题");
            view.domain.execute(()->require(view.model.opened.get().id.equals("mail-0"),"离页更新后搜索打开错误对象"));
            view.click("返回列表");view.click("清空搜索");view.click("每页 25");
            view.domain.execute(()->require(view.model.pageItems.get().size()==50,"每页数量变化未进入动态声明"));
        }
    }
    public static void main(String[] args) throws Exception { new MailboxScenario().run(args); }
}
