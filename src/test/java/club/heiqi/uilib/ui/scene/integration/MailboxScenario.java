package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.mail.MailboxModel;
import club.heiqi.uilib.testing.gallery.mail.MailboxPage;
import club.heiqi.uilib.testing.gallery.mail.MailSamples;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneCheckBox;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
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
                        .filter(e -> e.node.clickAction()!=null&&e.node.text().equals(contains)
                                ||e.node.text().contains("\n")&&e.node.text().contains(contains)
                                ||e.text!=null&&e.text.logicalText.contains(contains))
                        .filter(e -> e.node.control() == null || !e.node.control().acceptsTextInput(e.node))
                        .filter(e -> {
                            for (club.heiqi.uilib.ui.scene.node.SceneNode n=e.node;n!=null;n=n.parent())
                                if (n.focusable() && n.clickAction()!=null) return true;
                            return false;
                        })
                        .filter(e -> { LayoutBox v=e.clip==null?e.box:e.box.intersect(e.clip); return v.width>0&&v.height>0; })
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
        scenario("点击保留唯一逻辑焦点且键盘导航显示线索",this::focusCues);
        scenario("有限搜索过渡仅重绘且结束复用计划",this::finiteTransition);
        scenario("真实分类切换跨分类搜索归档与动态计数",this::categories);
        scenario("空态混排提示方向动态换代",this::placeholderPolicy);
        scenario("搜索空态提示与组字复制隔离及工具区不跳位",this::searchPresentation);
        scenario("复合邮件主体文字与空白命中同一当前对象",this::compositeInput);
        scenario("现代中英真实字重与显式字体重载",this::modernFonts);
        scenario("独立行操作批量归档与物理尾部",this::independentOperations);
        scenario("正式复选三态Space和禁用释放",this::checkBoxInput);
        scenario("真实语言资料换代同语言重载与字体尺寸变化",this::resources);
        scenario("关闭后持有runtime仍释放UI树",this::released);
        scenario("分页保留完整数据跨页身份与旧物理尾部",this::paged);
        scenario("末页归档后新增消息不复活旧翻页意图",this::pageClamp);
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
    private void focusCues() {
        try(View view=new View()) {
            view.search("");
            club.heiqi.uilib.ui.scene.input.SceneCompositionSource old=new club.heiqi.uilib.ui.scene.input.SceneCompositionSource();
            view.session.edit(SceneEditEvent.compositionBegin(old,"未确认"));view.frame();
            view.named("添加星标：周五");
            view.session.edit(SceneEditEvent.compositionUpdate(old,"迟到",true));
            view.session.edit(SceneEditEvent.compositionCommit(old,"旧提交"));view.frame();
            view.domain.execute(() -> {
                club.heiqi.uilib.ui.scene.runtime.SceneFocusRouter focus=view.session.runtime().focusRouter();
                require(focus.focused().get()!=null&&!focus.focusVisible(focus.focused().get()),"鼠标点击未保留逻辑焦点或仍显示键盘框");
            });
            view.session.navigation(SceneNavigationEvent.NEXT);view.frame();
            view.domain.execute(() -> {
                club.heiqi.uilib.ui.scene.runtime.SceneFocusRouter focus=view.session.runtime().focusRouter();
                require(focus.focusVisible(focus.focused().get()),"Tab没有显示当前合法焦点线索");
            });
            view.search("Café");
            view.domain.execute(() -> {
                club.heiqi.uilib.ui.scene.runtime.SceneFocusRouter focus=view.session.runtime().focusRouter();
                require(focus.focusVisible(focus.focused().get())&&focus.focused().get().control().acceptsTextInput(focus.focused().get()),
                        "鼠标进入字段丢失编辑焦点线索");
            });
        }
    }
    private void finiteTransition() throws Exception {
        try(View view=new View()) {
            int[] point=new int[2];view.domain.execute(() -> {
                SceneGeometryView.Entry field=view.geometry().ordered().stream().filter(v -> v.node.control()!=null&&v.node.control().acceptsTextInput(v.node)).findFirst().get();
                point[0]=field.box.x+5;point[1]=field.box.y+field.box.height/2;
            });
            view.session.pointer(ScenePointerEvent.down(0,point[0],point[1]));view.session.pointer(ScenePointerEvent.up(0,point[0],point[1]));view.frame();
            SceneGeometryView[] geometry={null};view.domain.execute(() -> geometry[0]=view.geometry());java.util.Set<PaintPlan> plans=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<PaintPlan,Boolean>());
            long end=System.nanoTime()+300000000L;
            do {Thread.sleep(8);view.frame();plans.add(view.plan);view.domain.execute(() -> require(view.geometry()==geometry[0],"搜索颜色过渡重新布局或改变了输入几何"));}while(System.nanoTime()<end);
            require(plans.size()>1,"搜索过渡没有实际更新绘制计划");PaintPlan settled=view.plan;
            view.frame();require(view.plan==settled,"有限过渡结束后仍每帧重建计划");
            view.session.pointer(ScenePointerEvent.down(0,1,view.height-1));view.session.pointer(ScenePointerEvent.up(0,1,view.height-1));view.frame();
            int[] icon=new int[3];LayoutBox[] bounds={null};club.heiqi.uilib.ui.scene.node.SceneNode[] readNode={null};
            view.domain.execute(() -> {
                SceneGeometryView.Entry read=view.geometry().ordered().stream().filter(v -> v.node.focusable()&&v.node.text().equals("标为已读：周五的设计评审：收件箱与阅读体验")).findFirst().get();
                icon[0]=read.box.x+read.box.width/2;icon[1]=read.box.y+read.box.height/2;icon[2]=read.node.style().color&0xffffff;bounds[0]=read.box;readNode[0]=read.node;
            });
            view.session.pointer(ScenePointerEvent.move(icon[0],icon[1]));view.frame();
            view.domain.execute(() -> {
                club.heiqi.uilib.ui.scene.runtime.SceneFocusRouter focus=view.session.runtime().focusRouter();
                for(int i=0;i<100&&focus.focused().get()!=readNode[0];i++)focus.navigate(false);
                require(focus.focused().get()==readNode[0],"键盘导航未达到次级动作");
            });view.frame();
            require(view.plan.commands().stream().anyMatch(c -> c.kind==PaintCommand.Kind.RECT&&(c.color&0xffffff)==icon[2]&&(c.color>>>24)==255
                    &&c.box.x>=bounds[0].x&&c.box.y>=bounds[0].y&&c.box.x+c.box.width<=bounds[0].x+bounds[0].width
                    &&c.box.y+c.box.height<=bounds[0].y+bounds[0].height),"hover渐显途中聚焦同一动作仍未立即可见");
        }
    }
    private void categories() {
        try(View view=new View()) {
            view.domain.execute(() -> {
                for(int i=0;i<30;i++)view.model.receive(new MailboxModel.Message("promo-"+i,"工坊推广",
                        "分类核验推广 "+i,"动态摘要","推广正文","昨天",false,false,true,MailboxModel.Category.PROMOTIONS));
                view.model.receive(new MailboxModel.Message("social","社区","分类核验社交","摘要","正文","昨天",false,false,true,MailboxModel.Category.SOCIAL));
            });view.frame();
            view.named("分类：推广");
            view.domain.execute(() -> require(view.model.results.get().size()==30&&view.model.unread(MailboxModel.Category.PROMOTIONS)==30,
                    "分类按钮未筛选真实数据或计数不是当前来源"));
            view.domain.execute(() -> view.model.movePage(1));view.frame();
            view.named("选择邮件：分类核验推广");view.named("分类：社交");
            view.domain.execute(() -> require(view.model.pageIndex.get()==0&&view.model.checked.get().isEmpty()
                    &&view.model.results.get().get(0).id.equals("social"),"分类切换没有结束原分页与选择"));
            view.search("分类核验推广");
            view.domain.execute(() -> require(view.model.results.get().size()==30,"搜索被隐藏分类过滤"));
            view.click("清空搜索");view.named("分类：推广");
            view.named("选择邮件：分类核验推广");view.named("归档已选邮件");
            view.domain.execute(() -> {
                require(view.model.results.get().size()==29&&view.model.unread(MailboxModel.Category.PROMOTIONS)==29,"归档未同步分类与计数");
                view.model.update(new MailboxModel.Message("promo-1","社区","分类核验迁移","摘要","正文","昨天",false,false,true,MailboxModel.Category.SOCIAL));
            });view.frame();
            view.named("分类：社交");
            view.domain.execute(() -> require(view.model.results.get().size()==2&&view.model.unread(MailboxModel.Category.SOCIAL)==2,"同id分类更新未进入当前集合"));
            view.click("所有邮件");
            view.domain.execute(() -> require(view.model.results.get().size()==55,"其他文件夹没有覆盖全部分类"));
            view.click("收件箱");
            view.domain.execute(() -> require(view.model.category.get()==MailboxModel.Category.SOCIAL&&view.model.results.get().size()==2,"返回收件箱丢失分类"));
        }
    }
    /** 保护实际评审发现：空草稿不改变时只切文本方向，提示仍须重新呈现。 */
    private void placeholderPolicy() throws Exception {
        try(View view=new View()) {
            club.heiqi.uilib.ui.scene.node.SceneNode[] field=new club.heiqi.uilib.ui.scene.node.SceneNode[1];
            club.heiqi.uilib.ui.reactive.Signal<club.heiqi.uilib.ui.scene.text.TextPolicy>[] policy=new club.heiqi.uilib.ui.reactive.Signal[1];
            try(ScenePageSession page=ScenePageSession.open(view.domain,view,fonts,ctx -> {
                club.heiqi.uilib.ui.scene.edit.SceneEditState state=new club.heiqi.uilib.ui.scene.edit.SceneEditState(view.domain,"");
                policy[0]=view.domain.signal(new club.heiqi.uilib.ui.scene.text.TextPolicy(club.heiqi.uilib.ui.scene.text.TextPolicy.Direction.LTR,
                        club.heiqi.uilib.ui.scene.text.TextPolicy.Wrap.NONE,"he",0,0,club.heiqi.uilib.ui.scene.text.TextPolicy.HardBreak.MARKER));
                field[0]=SceneTextField.create(ctx,state,ctx.theme(),() -> false,request -> state.accept(request,request.text),
                        SceneTextField.SubmitPolicy.CONFIRM,() -> "ABC אבג 123",false)
                        .bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(policy[0],club.heiqi.uilib.ui.scene.node.SceneNode::textPolicy));
                ctx.mountOwned(field[0]);
            })) {
                page.frame();
                club.heiqi.uilib.ui.scene.text.TextLayout left=view.plan.commands().stream()
                        .filter(c -> c.kind==PaintCommand.Kind.TEXT&&c.text.logicalText.equals("ABC אבג 123")).findFirst().get().text;
                view.domain.execute(() -> policy[0].set(new club.heiqi.uilib.ui.scene.text.TextPolicy(club.heiqi.uilib.ui.scene.text.TextPolicy.Direction.RTL,
                        club.heiqi.uilib.ui.scene.text.TextPolicy.Wrap.NONE,"he",0,0,club.heiqi.uilib.ui.scene.text.TextPolicy.HardBreak.MARKER)));
                page.frame();
                club.heiqi.uilib.ui.scene.text.TextLayout right=view.plan.commands().stream()
                        .filter(c -> c.kind==PaintCommand.Kind.TEXT&&c.text.logicalText.equals("ABC אבג 123")).findFirst().get().text;
                require(right!=left&&right.lines().get(0).glyphs().get(0).logicalStart!=left.lines().get(0).glyphs().get(0).logicalStart,
                        "只改变排版策略的空态提示仍保留旧方向");
                view.domain.execute(() -> require(field[0].text().isEmpty(),"提示方向变化改写了草稿"));
            }
        }
    }

    private void searchPresentation() {
        try(View view=new View()) {
            int[] point=new int[2];
            view.domain.execute(() -> {
                SceneGeometryView.Entry field=view.geometry().ordered().stream()
                        .filter(e -> e.node.control()!=null&&e.node.control().acceptsTextInput(e.node)).findFirst().get();
                require(field.node.text().isEmpty()&&field.text.logicalText.isEmpty(),"占位提示进入真实编辑内容");
                point[0]=field.box.x+8;point[1]=field.box.y+field.box.height/2;
            });
            require(view.plan.commands().stream().anyMatch(c -> c.kind==PaintCommand.Kind.TEXT&&c.text.logicalText.equals("搜索邮件")),"空态提示没有真实绘制");
            view.session.pointer(ScenePointerEvent.down(0,point[0],point[1]));view.session.pointer(ScenePointerEvent.up(0,point[0],point[1]));view.frame();
            final String[] copied={"unchanged"};
            SceneEditEvent.Clipboard clipboard=new SceneEditEvent.Clipboard() {
                public String read(){return copied[0];} public void write(String value){copied[0]=value;}
            };
            view.session.edit(SceneEditEvent.command(SceneEditEvent.Command.SELECT_ALL,false,null));
            view.session.edit(SceneEditEvent.command(SceneEditEvent.Command.COPY,false,clipboard));view.frame();
            require(!"搜索邮件".equals(copied[0]),"占位提示进入复制内容");
            club.heiqi.uilib.ui.scene.input.SceneCompositionSource source=new club.heiqi.uilib.ui.scene.input.SceneCompositionSource();
            view.session.edit(SceneEditEvent.compositionBegin(source,"拼音"));view.frame();
            require(view.plan.commands().stream().noneMatch(c -> c.kind==PaintCommand.Kind.TEXT&&c.text.logicalText.equals("搜索邮件")),"组字被占位提示遮盖");
            view.session.edit(SceneEditEvent.compositionCommit(source,"Café"));view.frame();
            view.domain.execute(() -> require(view.model.query.get().equals("Café")&&view.model.results.get().size()==5,"真实组字提交未形成查询"));
            view.session.edit(SceneEditEvent.command(SceneEditEvent.Command.SELECT_ALL,false,null));
            view.session.edit(SceneEditEvent.command(SceneEditEvent.Command.COPY,false,clipboard));view.frame();
            require("Café".equals(copied[0]),"搜索复制没有保留真实草稿");
            view.click("清空搜索");
            view.domain.execute(() -> require(view.model.query.get().isEmpty(),"清空图标未发布空查询"));
            int[] firstTop=new int[1];view.domain.execute(() -> firstTop[0]=view.geometry().ordered().stream()
                    .filter(e -> e.node.focusable()&&e.node.text().contains("\n")).findFirst().get().box.y);
            view.click("全选全部筛选结果：24 封");
            view.domain.execute(() -> require(view.geometry().ordered().stream().filter(e -> e.node.focusable()&&e.node.text().contains("\n"))
                    .findFirst().get().box.y==firstTop[0],"批量工具出现推移了首行"));
            view.click("取消选择");
            view.domain.execute(() -> require(view.geometry().ordered().stream().filter(e -> e.node.focusable()&&e.node.text().contains("\n"))
                    .findFirst().get().box.y==firstTop[0],"取消工具推移了首行"));
        }
    }
    private void compositeInput() {
        try(View view=new View()) {
            for(int part=0;part<4;part++) {
                final int selected=part;int[] point=new int[2];
                view.domain.execute(() -> {
                    MailboxModel.Message m=view.model.results.get().get(0).value.get();
                    SceneGeometryView.Entry action=view.geometry().ordered().stream()
                            .filter(e -> e.node.focusable() && e.node.text().equals(m.sender+"\n"+m.subject)).findFirst().get();
                    SceneGeometryView.Entry content=view.geometry().ordered().stream()
                            .filter(e -> e.text!=null && e.text.logicalText.equals(selected==0?m.sender:selected==1?m.subject:" — "+m.preview))
                            .filter(e -> {LayoutBox b=e.clip==null?e.box:e.box.intersect(e.clip);return b.width>0&&b.height>0;}).findFirst().get();
                    LayoutBox b=content.clip==null?content.box:content.box.intersect(content.clip);
                    point[0]=b.x+b.width/2;point[1]=b.y+b.height/2;
                    if(selected==3) {
                        SceneGeometryView.Entry sender=view.geometry().get(action.node.children().get(0));
                        point[0]=sender.box.x+sender.box.width+action.node.gap()/2;
                        point[1]=action.box.y+action.box.height/2;
                    }
                });
                view.session.pointer(ScenePointerEvent.down(0,point[0],point[1]));
                view.session.pointer(ScenePointerEvent.up(0,point[0],point[1]));view.frame();
                view.domain.execute(() -> require(view.model.reading.get() && view.model.opened.get().id.equals("mail-0")
                        &&view.model.checked.get().isEmpty(),"主体不同内容未打开同一对象或串入勾选"));
                view.click("返回列表");
            }
            view.click("周五的设计评审");view.click("切换当前邮件星标");
            view.domain.execute(() -> {
                require(view.model.opened.get().starred,"阅读星标未修改当前消息");
                view.model.toggleChecked("mail-1");
            });view.frame();
            view.click("归档当前邮件");
            view.domain.execute(() -> require(!view.model.opened.get().inbox
                    &&view.model.opened.get().id.equals("mail-0")&&view.model.checked.get().contains("mail-1"),
                    "阅读归档串改其他勾选或跳到邻行"));
            view.click("返回列表");
        }
    }
    private void modernFonts() throws Exception {
        try (View view = new View()) {
            view.domain.execute(() -> {
                MailboxModel.Message m = view.model.results.get().get(0).value.get();
                view.model.update(new MailboxModel.Message(m.id, m.sender, "界面 Café 10:28 😀",
                        m.preview, m.body, m.time, m.starred, false, m.inbox));
            });
            view.frame();
            boolean normal = false, bold = false, colorEmoji = false;
            java.util.Set<String> faces = new java.util.LinkedHashSet<>();
            for (PaintCommand command : view.plan.commands()) {
                if (command.kind != PaintCommand.Kind.TEXT) continue;
                for (club.heiqi.uilib.ui.scene.text.TextLayout.Line line : command.text.lines()) {
                    for (club.heiqi.uilib.ui.scene.text.TextLayout.Glyph glyph : line.glyphs()) {
                        faces.add(glyph.source.metadata().family + ":" + glyph.source.actualStyle().weight);
                        if (glyph.source.metadata().family.equals("Noto Sans SC")) {
                            normal |= glyph.source.actualStyle().weight == 400;
                            bold |= glyph.source.actualStyle().weight == 700;
                        }
                        colorEmoji |= glyph.color && !glyph.replacement;
                    }
                }
            }
            require(normal && bold && colorEmoji, "正式页面未使用真实常规/粗体及彩色emoji: " + faces);
            java.util.List<club.heiqi.uilib.ui.scene.text.font.FontSource> replacement = new java.util.ArrayList<>();
            replacement.add(club.heiqi.uilib.ui.scene.text.font.FontSource.fromResource("mail-font-reload", 2,
                    "assets/qz_uilib/gallery/fonts/noto-sans-sc/NotoSansSC-VF.ttf", false));
            replacement.addAll(FontTextService.defaultSources(getClass().getClassLoader(), 2));
            view.domain.execute(() -> view.theme.set(SceneTheme.light().withTextStyle(
                    new club.heiqi.uilib.ui.scene.text.TextStyle("default", 21, 0xff202124, replacement))));
            view.frame();
            boolean current = view.plan.commands().stream().filter(c -> c.kind == PaintCommand.Kind.TEXT)
                    .flatMap(c -> c.text.lines().stream()).flatMap(l -> l.glyphs().stream())
                    .anyMatch(g -> g.source.id.equals("mail-font-reload") && g.source.revision == 2 && g.logicalEm == 21);
            require(current, "显式字体来源换代未更新当前页面的真实字形");
            System.out.println("MAILBOX_FONT actualFaces=" + faces + " explicitReload=true samePage=true");
        }
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
    private void pageClamp() {
        try(View view=new View(26,null)) {
            view.click("下一页");view.named("选择邮件：周五");view.named("归档已选邮件");
            view.domain.execute(()->require(view.model.pageIndex.get()==0&&view.model.results.get().size()==25,
                    "末页归档后没有限制到有效页"));
            view.click("接收示例邮件");
            view.domain.execute(()->require(view.model.pageIndex.get()==0&&view.model.pageItems.get().get(0).id.equals("mail-0"),
                    "新增消息使旧越界翻页意图复活"));
            require(view.painted("1–25 / 26"),"新增后当前分页范围错误");
        }
    }
    public static void main(String[] args) throws Exception { new MailboxScenario().run(args); }
}
