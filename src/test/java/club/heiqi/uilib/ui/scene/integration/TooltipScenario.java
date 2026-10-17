package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneAppearance;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneTooltip;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import java.util.ArrayList;
import java.util.List;

/** 正式PageSession/唯一输入上的提示反例；软件度量只用于确定空间，不冒充GPU字形。 */
public final class TooltipScenario extends TestApplication {
    static final class Host implements SceneHost,SceneTextMeasurer {
        int width=440,height=240;PaintPlan last;
        public int width(){return width;}public int height(){return height;}public long metricsRevision(){return 1;}
        public void replay(PaintPlan plan){last=plan;}
        public TextLayout layout(String text,TextStyle style,int available,club.heiqi.uilib.ui.scene.text.TextPolicy policy){return layout(text,style,available);}
        public TextLayout layout(String text,TextStyle style,int available){
            int per=Math.max(1,available/7),w=0;List<TextLayout.Line> lines=new ArrayList<>();
            for(int i=0;i<text.length();i+=per){String part=text.substring(i,Math.min(text.length(),i+per));w=Math.max(w,part.length()*7);lines.add(new TextLayout.Line(part,0,lines.size()*18));}
            return new TextLayout(w,lines.size()*18,style,lines);
        }
    }
    public static void main(String[] args)throws Exception{new TooltipScenario().run(args);}
    @Override protected void runTests()throws Exception{
        scenario("原触发器说明、悬停保持、点击与ESC不反弹",()->{
            ReactiveDomain domain=ReactiveDomain.direct();Host host=new Host();SceneNode[] button={null};int[] calls={0},fallback={0};
            try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
                button[0]=SceneButton.create(ctx,()->"操作",()->SceneAppearance.theme(false),()->calls[0]++).size(Size.fixed(100),Size.fixed(40));
                SceneNode same=SceneTooltip.attach(ctx,button[0],()->SceneTooltip.Content.text("补充说明","Space"),()->new SceneTooltip.Options(),()->SceneAppearance.theme(false));
                require(same==button[0],"Tooltip替换了触发器");ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).padding(60).add(same));
            })){
                page.frame();LayoutBox box=box(page,button[0]);point(page,box.x+10,box.y+10);
                require(text(host,"补充说明"),"正式悬停未显示提示");
                require(text(host,"Space"),"独立快捷键说明未显示");
                PaintCommand popup=host.last.commands().stream().filter(c->c.kind==PaintCommand.Kind.TEXT&&rendered(c).equals("补充说明")).findFirst().get();
                point(page,popup.box.x+2,popup.box.y+2);require(text(host,"补充说明"),"移入提示后不能保持阅读");
                page.pipeline().escape(()->fallback[0]++);page.frame();require(!text(host,"补充说明")&&fallback[0]==0,"ESC没有只关闭当前提示");page.frame();require(!text(host,"补充说明"),"ESC后原hover立即反弹");
                point(page,430,230);point(page,box.x+10,box.y+10);require(text(host,"补充说明"),"新进入未重新开启");
                page.pointer(ScenePointerEvent.down(0,box.x+10,box.y+10));page.pointer(ScenePointerEvent.up(0,box.x+10,box.y+10));page.frame();
                require(calls[0]==1&&!text(host,"补充说明"),"点击关闭拦截了原业务或提示反弹");
            }
        });
        scenario("有限延迟取消、焦点即时、动态内容与卸载",()->{
            ReactiveDomain domain=ReactiveDomain.direct();Host host=new Host();SceneNode[] button={null};Signal<String>[] content=new Signal[1];Signal<Boolean>[] visible=new Signal[1];
            try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
                content[0]=domain.signal("最初内容");visible[0]=domain.signal(true);button[0]=SceneButton.create(ctx,()->"延迟",()->SceneAppearance.theme(false),()->{}).size(Size.fixed(100),Size.fixed(40));
                button[0].bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(visible[0],SceneNode::visible));
                ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).padding(30).add(SceneTooltip.attach(ctx,button[0],()->SceneTooltip.Content.text(content[0].get()),()->new SceneTooltip.Options().timing(60_000_000L,0,0),()->SceneAppearance.theme(false))));
            })){
                page.frame();page.pointer(ScenePointerEvent.down(0,430,230));page.pointer(ScenePointerEvent.up(0,430,230));page.frame();
                LayoutBox box=box(page,button[0]);point(page,box.x+5,box.y+5);require(!text(host,"最初内容"),"打开延迟被忽略");
                point(page,430,230);long end=System.nanoTime()+85_000_000L;while(System.nanoTime()<end)page.frame();require(!text(host,"最初内容"),"离开后迟到显示");
                page.navigation(SceneNavigationEvent.NEXT);page.frame();require(text(host,"最初内容"),"键盘焦点没有即时显示");
                domain.execute(()->content[0].set("新内容"));page.frame();require(text(host,"新内容")&&!text(host,"最初内容"),"说明未随来源更新");
                domain.execute(()->visible[0].set(false));page.frame();require(!text(host,"新内容"),"隐藏来源留下提示");
                domain.execute(()->visible[0].set(true));page.frame();require(!text(host,"新内容"),"来源恢复复活了旧提示");
            }
        });
        scenario("移入自动提示后仍更新来源事实与失效",()->{
            ReactiveDomain domain=ReactiveDomain.direct();Host host=new Host();SceneNode[] icon={null};Signal<String>[] name=new Signal[1];
            try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
                name[0]=domain.signal("旧名称");icon[0]=club.heiqi.uilib.ui.scene.control.SceneIconButton.create(ctx,()->club.heiqi.uilib.ui.scene.control.SceneIconButton.Symbol.CHEVRON_LEFT,name[0],()->SceneAppearance.theme(false),()->{});
                ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).padding(60).add(icon[0]));
            })){
                page.frame();page.pointer(ScenePointerEvent.down(0,430,230));page.pointer(ScenePointerEvent.up(0,430,230));page.frame();LayoutBox anchor=box(page,icon[0]);point(page,anchor.x+5,anchor.y+5);require(text(host,"旧名称"),"自动图标提示未打开");
                PaintCommand popup=host.last.commands().stream().filter(c->c.kind==PaintCommand.Kind.TEXT&&rendered(c).equals("旧名称")).findFirst().get();point(page,popup.box.x+2,popup.box.y+2);
                domain.execute(()->name[0].set("新名称"));page.frame();require(text(host,"新名称")&&!text(host,"旧名称"),"移入提示后仍显示旧的自动来源事实");
                domain.execute(()->name[0].set(""));page.frame();require(!text(host,"新名称"),"自动来源已无提示却仍保持旧内容");
            }
        });
        scenario("非零延迟自然到期、关闭延迟与快速切换期限",()->{
            ReactiveDomain domain=ReactiveDomain.direct();Host host=new Host();SceneNode[] nodes=new SceneNode[2];
            try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
                SceneNode row=new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.fill(1)).padding(60).gap(12);
                for(int i=0;i<2;i++){final int index=i;nodes[i]=new SceneNode().size(Size.fixed(80),Size.fixed(40));row.add(SceneTooltip.attach(ctx,nodes[i],()->SceneTooltip.Content.text("定时说明"+index),()->new SceneTooltip.Options().timing(100_000_000L,120_000_000L,150_000_000L),()->SceneAppearance.theme(false)));}ctx.mount(row);
            })){
                page.frame();LayoutBox a=box(page,nodes[0]),b=box(page,nodes[1]);long open=System.nanoTime();point(page,a.x+5,a.y+5);require(!text(host,"定时说明0"),"非零打开延迟未等待");
                waitUntil(page,()->text(host,"定时说明0"));long opened=System.nanoTime()-open;require(opened>=100_000_000L,"提示早于打开期限");
                point(page,b.x+5,b.y+5);require(text(host,"定时说明1")&&!text(host,"定时说明0"),"组内快速切换再次完整等待");
                long close=System.nanoTime();point(page,430,230);require(text(host,"定时说明1"),"关闭延迟没有保持内容");waitUntil(page,()->!text(host,"定时说明1"));long closed=System.nanoTime()-close;require(closed>=120_000_000L,"提示早于关闭期限");
                long until=System.nanoTime()+200_000_000L;while(System.nanoTime()<until){page.frame();Thread.sleep(2);}open=System.nanoTime();point(page,a.x+5,a.y+5);require(!text(host,"定时说明0"),"组内期限过期后仍跳过打开延迟");waitUntil(page,()->text(host,"定时说明0"));long reopened=System.nanoTime()-open;require(reopened>=100_000_000L,"过期后的打开未重新等待");
                System.out.println("TOOLTIP_TIMING naturalOpenNanos="+opened+" delayedCloseNanos="+closed+" expiredSkipReopenNanos="+reopened+" quickSwitchImmediate=true");
            }
        });
        scenario("边缘避让、同组切换和模态ESC",()->{
            ReactiveDomain domain=ReactiveDomain.direct();Host host=new Host();SceneNode[] trigger=new SceneNode[3];Signal<Boolean>[] modal=new Signal[1];int[] closes={0};
            try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
                modal[0]=domain.signal(false);SceneNode row=new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural()).gap(8);
                for(int i=0;i<2;i++){final int index=i;trigger[i]=SceneButton.create(ctx,()->"来源"+index,()->SceneAppearance.theme(false),()->{}).size(Size.fixed(90),Size.fixed(40));
                    row.add(SceneTooltip.attach(ctx,trigger[i],()->SceneTooltip.Content.text("说明"+index),()->new SceneTooltip.Options().timing(80_000_000L,0,400_000_000L),()->SceneAppearance.theme(false)));}
                ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(row));
                trigger[2]=SceneButton.create(ctx,()->"内层",()->SceneAppearance.theme(false),()->{}).size(Size.fixed(100),Size.fixed(40));
                SceneNode layer=new SceneNode().size(Size.fill(1),Size.fill(1)).padding(30).modal(true).initialFocus(trigger[2]).onEscape(()->{closes[0]++;modal[0].set(false);return true;})
                    .bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(modal[0],SceneNode::visible));
                layer.add(SceneTooltip.attach(ctx,trigger[2],()->SceneTooltip.Content.text("内层说明"),()->new SceneTooltip.Options(),()->SceneAppearance.theme(false)));ctx.mount(layer);
            })){
                page.frame();require(text(host,"说明0"),"初始键盘提示缺失");
                PaintCommand tip=host.last.commands().stream().filter(c->c.kind==PaintCommand.Kind.TEXT&&rendered(c).equals("说明0")).findFirst().get();
                require(tip.box.y>=box(page,trigger[0]).height,"顶部没有空间时未反侧");
                LayoutBox second=box(page,trigger[1]);point(page,second.x+5,second.y+5);require(text(host,"说明1")&&!text(host,"说明0"),"同组快速切换或唯一提示失败");
                domain.execute(()->modal[0].set(true));page.frame();require(text(host,"内层说明")&&!text(host,"说明1"),"模态未隔离后台提示");
                page.pipeline().escape(()->closes[0]+=100);page.frame();require(!text(host,"内层说明")&&closes[0]==0,"第一次ESC关闭了模态");
                page.pipeline().escape(()->closes[0]+=100);page.frame();require(closes[0]==1,"提示关闭后ESC未交还模态");
                host.width=70;host.height=100;page.frame();
                for(PaintCommand c:host.last.commands())if(c.kind==PaintCommand.Kind.TEXT&&rendered(c).startsWith("说明")){
                    LayoutBox visible=c.clip==null?c.box:c.box.intersect(c.clip);require(visible.x>=0&&visible.y>=0&&visible.x+visible.width<=host.width&&visible.y+visible.height<=host.height,"极窄视口提示越界");}
            }
        });
    }
    private static void waitUntil(ScenePageSession page,java.util.function.BooleanSupplier condition)throws Exception{long deadline=System.nanoTime()+2_000_000_000L;while(!condition.getAsBoolean()&&System.nanoTime()<deadline){page.frame();Thread.sleep(2);}require(condition.getAsBoolean(),"有限Tooltip期限未自然完成");}
    private static LayoutBox box(ScenePageSession page,SceneNode node){LayoutBox[] result={null};page.domain().execute(()->result[0]=page.pipeline().current().geometry.get(node).box);return result[0];}
    private static void point(ScenePageSession page,int x,int y){page.pointer(ScenePointerEvent.move(x,y));page.frame();}
    private static String rendered(PaintCommand command){return command.text.lines().stream().map(line->line.text).collect(java.util.stream.Collectors.joining());}
    private static boolean text(Host host,String expected){return host.last.commands().stream().anyMatch(c->c.kind==PaintCommand.Kind.TEXT&&rendered(c).equals(expected));}
}
