package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.testing.gallery.PerformanceSamples;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;

/** 压测消费者复用完整消息行，观测/导出在动作与宿主完整帧的计时区外。 */
public final class MailboxWorkload implements ScenePage {
    public interface Driver {
        void frame();
        void scroll(int x,int y,int delta);
        void resize(int width,int height);
        SceneGeometryView geometry();
        PaintPlan paint();
        int width();int height();
    }
    public final int scale;
    private final boolean paged=Boolean.parseBoolean(System.getProperty("mailbox.paged","true"));
    private ReactiveDomain domain;
    private MailboxModel model;
    private SceneNode root;
    private long declarationNanos;
    public MailboxWorkload(int scale){if(scale<2)throw new IllegalArgumentException("压测至少两条消息");this.scale=scale;}
    @Override public void build(ScenePageContext page){
        long start=System.nanoTime();domain=page.domain();model=new MailboxModel(domain,MailSamples.messages(scale));
        root=MailboxPage.create(page,page.theme(),model,paged).own(model);page.mountOwned(root);declarationNanos=System.nanoTime()-start;
    }
    private static final class Facts {int offset,maximum,mounted,visible,x,y,width,height,commands,visibleInkGlyphs,unknownInkGlyphs;}
    private Facts facts(Driver driver){
        Facts f=new Facts();domain.execute(()->{
            SceneGeometryView g=driver.geometry();
            SceneGeometryView.Entry scroll=g.ordered().stream().filter(e->e.node.scrollable()).findFirst().get();
            f.width=driver.width();f.height=driver.height();f.offset=scroll.scrollOffset;f.maximum=scroll.scrollMaximum;
            f.x=scroll.box.x+2;f.y=scroll.box.y+Math.min(20,scroll.box.height/2);f.mounted=count(root);
            f.visible=(int)g.ordered().stream().filter(e->e.clip==null||e.box.intersect(e.clip).height>0&&e.box.intersect(e.clip).width>0).count();
            f.commands=driver.paint().commands().size();
            LayoutBox viewport=new LayoutBox(0,0,f.width,f.height);
            for(PaintCommand command:driver.paint().commands())if(command.kind==PaintCommand.Kind.TEXT) {
                LayoutBox clip=command.clip==null?viewport:command.clip.intersect(viewport);
                for(club.heiqi.uilib.ui.scene.text.TextLayout.Line line:command.text.lines())
                    for(club.heiqi.uilib.ui.scene.text.TextLayout.Glyph glyph:line.glyphs()) {
                        club.heiqi.uilib.ui.scene.text.font.nativefont.InkBounds b=glyph.inkBounds;
                        if(!b.known) {f.unknownInkGlyphs++;continue;}
                        float left=command.box.x+glyph.x+b.left,top=command.box.y+glyph.y+b.top;
                        float right=command.box.x+glyph.x+b.right,bottom=command.box.y+glyph.y+b.bottom;
                        if(right>left&&bottom>top&&right>clip.x&&bottom>clip.y&&left<clip.x+clip.width&&top<clip.y+clip.height)f.visibleInkGlyphs++;
                    }
            }
        });return f;
    }
    private static int count(SceneNode n){int count=1;for(SceneNode child:n.children())count+=count(child);return count;}
    private String visibleMessage(Driver driver){
        String[] result={null};domain.execute(()->{
            Map<String,String> names=new HashMap<>();
            for(MailboxModel.Item item:model.results.get()){
                MailboxModel.Message m=item.value.get();names.put(m.sender+"\n"+m.subject,item.id);
            }
            SceneGeometryView g=driver.geometry();
            for(SceneGeometryView.Entry e:g.ordered()){
                LayoutBox visible=e.box.intersect(g.viewport());if(e.clip!=null)visible=visible.intersect(e.clip);
                if(e.node.focusable()&&visible.width==e.box.width&&visible.height==e.box.height&&visible.width>0&&visible.height>0
                        &&names.containsKey(e.node.text())){result[0]=names.get(e.node.text());break;}
            }
        });if(result[0]==null)throw new AssertionError("UPDATE没有完整可见消息主体");return result[0];
    }
    private boolean painted(Driver driver,String subject){
        return driver.paint().commands().stream().anyMatch(c->{
            if(c.kind!=PaintCommand.Kind.TEXT||!c.text.logicalText.contains(subject))return false;
            LayoutBox visible=c.box.intersect(new LayoutBox(0,0,driver.width(),driver.height()));if(c.clip!=null)visible=visible.intersect(c.clip);
            return visible.width>0&&visible.height>0;
        });
    }
    public void run(Driver driver,String host,String version,int warmup,int frames,Consumer<String> save){
        if(warmup<0||frames<=0)throw new IllegalArgumentException("非法采样规模");
        long enter=System.nanoTime();driver.frame();long first=System.nanoTime()-enter;Facts initial=facts(driver);
        String selected=System.getProperty("mailbox.loads","IDLE,SCROLL,UPDATE,RESIZE");
        for(String load:selected.split(",")){
            if(!java.util.Arrays.asList("IDLE","SCROLL","UPDATE","RESIZE").contains(load))throw new IllegalArgumentException("未知负载: "+load);
            PerformanceSamples samples=new PerformanceSamples(frames);long previous=0;int changes=0,visiblePaintUpdates=0;
            int[] updates={0,0};Facts before=facts(driver);String[] targets={null,null};
            if(load.equals("UPDATE")){
                targets[0]=visibleMessage(driver);
                domain.execute(()->targets[1]=model.results.get().get(model.results.get().size()-1).id);
                if(targets[0].equals(targets[1]))throw new AssertionError("UPDATE没有独立页外目标");
            }
            for(int i=0;i<warmup+frames;i++){
                final int step=i;String expected="当前更新 #"+step;long start=System.nanoTime();
                if(load.equals("SCROLL"))driver.scroll(before.x,before.y,(i/12)%2==0?-2:2);
                else if(load.equals("UPDATE"))domain.execute(()->{
                    MailboxModel.Item item=model.results.get().stream().filter(v->v.id.equals(targets[step%2])).findFirst().get();
                    MailboxModel.Message m=item.value.get();model.update(new MailboxModel.Message(m.id,m.sender,expected,m.preview,m.body,m.time,m.starred,m.read,m.inbox));
                });
                else if(load.equals("RESIZE"))driver.resize(i%2==0?1100:960,800);
                driver.frame();long end=System.nanoTime();if(i>=warmup)samples.add(end-start,previous==0?0:start-previous);previous=start;
                Facts after=facts(driver);
                if(load.equals("UPDATE")){
                    domain.execute(()->{MailboxModel.Item item=model.results.get().stream().filter(v->v.id.equals(targets[step%2])).findFirst().get();
                        if(!item.value.get().subject.equals(expected))throw new AssertionError("当前数据更新未生效");});
                    updates[step%2]++;
                    if(step%2==0){if(!painted(driver,expected))throw new AssertionError("可见UPDATE未进入有效PaintPlan");visiblePaintUpdates++;changes++;}
                    else if(painted(driver,expected))throw new AssertionError("页外UPDATE目标实际可见");
                }else if(load.equals("SCROLL")&&after.offset!=before.offset||load.equals("RESIZE")&&(after.width!=before.width||after.height!=before.height))changes++;
                before=after;
            }
            boolean observed=load.equals("IDLE")||changes>0;
            if(load.equals("UPDATE"))observed=observed&&updates[0]>0&&updates[1]>0&&visiblePaintUpdates==updates[0];
            if(!observed)throw new AssertionError("收件箱负载未命中: "+load);Facts last=facts(driver);
            String json="{\"schema\":\"uilib-mailbox-v2\",\"host\":\""+host+"\",\"version\":\""+version
                +"\",\"paged\":"+paged+",\"scale\":"+scale+",\"load\":\""+load+"\",\"complete\":true,\"pathObserved\":"+observed
                +",\"warmup\":"+warmup+",\"frames\":"+frames+",\"firstFrameNanos\":"+first+",\"declarationNanos\":"+declarationNanos
                +",\"mounted\":"+initial.mounted+",\"visible\":"+initial.visible+",\"lastMounted\":"+last.mounted
                +",\"insideUpdates\":"+updates[0]+",\"outsideUpdates\":"+updates[1]+",\"visiblePaintUpdates\":"+visiblePaintUpdates
                +",\"lastVisible\":"+last.visible+",\"scrollMaximum\":"+last.maximum
                +",\"paintCommands\":"+initial.commands+",\"visibleInkGlyphs\":"+initial.visibleInkGlyphs+",\"unknownInkGlyphs\":"+initial.unknownInkGlyphs
                +",\"lastPaintCommands\":"+last.commands+",\"lastVisibleInkGlyphs\":"+last.visibleInkGlyphs+",\"lastUnknownInkGlyphs\":"+last.unknownInkGlyphs
                +",\"samples\":"+samples.toJson()+"}";
            save.accept(json);System.out.println("MAILBOX_PERFORMANCE host="+host+" scale="+scale+" load="+load+" p95ns="+samples.frame().p95Nanos);
        }
    }
}
