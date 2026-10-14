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
        View() {
            session = ScenePageSession.open(domain, this, fonts, context -> {
                require(context.viewportWidth().get() == width, "初次声明没有宿主空间事实");
                model = new MailboxModel(domain, MailSamples.messages(24)); context.own(model);
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
                        .filter(e -> e.node.focusable())
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
                    .filter(e -> e.node.focusable() && e.node.text().startsWith(name))
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
            view.named("全选当前结果");
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
        try(ScenePageSession page=ScenePageSession.open(domain,host,fonts,context->{
            state[0]=domain.signal(SceneCheckBox.State.MIXED);enabled[0]=domain.signal(true);
            context.mountOwned(SceneCheckBox.create(context,state[0],()->"复选示例",context.theme(),
                    checked->state[0].set(checked?SceneCheckBox.State.CHECKED:SceneCheckBox.State.UNCHECKED))
                    .bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(enabled[0],club.heiqi.uilib.ui.scene.node.SceneNode::enabled)));
        })) {
            page.frame();page.pointer(ScenePointerEvent.down(0,10,10));page.pointer(ScenePointerEvent.up(0,10,10));page.frame();
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
    public static void main(String[] args) throws Exception { new MailboxScenario().run(args); }
}
