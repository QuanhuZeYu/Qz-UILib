package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.testing.gallery.PerformanceSamples;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import java.util.List;
import java.util.function.Consumer;

/** 压测消费者复用完整消息行，采样区只包含动作和所属宿主完整帧。 */
public final class MailboxWorkload implements ScenePage {
    public interface Driver {
        void frame();
        void scroll(int x,int y,int delta);
        void resize(int width,int height);
        SceneGeometryView geometry();
        int width();int height();
    }
    public final int scale;
    private final boolean paged = Boolean.parseBoolean(System.getProperty("mailbox.paged","true"));
    private ReactiveDomain domain;
    private MailboxModel model;
    private SceneNode root;
    private long declarationNanos;
    public MailboxWorkload(int scale) { if(scale<2)throw new IllegalArgumentException("压测至少两条消息");this.scale=scale; }
    @Override public void build(ScenePageContext page) {
        long start=System.nanoTime();
        domain=page.domain();model=new MailboxModel(domain,MailSamples.messages(scale));
        root=MailboxPage.create(page,page.theme(),model,paged).own(model);page.mountOwned(root);
        declarationNanos=System.nanoTime()-start;
    }
    private static final class Facts { int offset,maximum,mounted,visible,x,y,width,height;String changedText; }
    private Facts facts(Driver driver) {
        Facts f=new Facts();domain.execute(()->{
            SceneGeometryView geometry=driver.geometry();
            SceneGeometryView.Entry scroller=geometry.ordered().stream().filter(e->e.node.scrollable()).findFirst().get();
            f.width=driver.width();f.height=driver.height();
            f.changedText=geometry.ordered().stream().filter(e->e.text!=null && e.text.logicalText.contains("当前更新 #")).map(e->e.text.logicalText).findFirst().orElse("");
            f.offset=scroller.scrollOffset;f.maximum=scroller.scrollMaximum;
            f.x=scroller.box.x+2;f.y=scroller.box.y+Math.min(20,scroller.box.height/2);
            f.mounted=count(root);f.visible=(int)geometry.ordered().stream().filter(e->e.clip==null
                    || e.box.intersect(e.clip).height>0 && e.box.intersect(e.clip).width>0).count();
        });return f;
    }
    private static int count(SceneNode node){int count=1;for(SceneNode child:node.children())count+=count(child);return count;}
    public void run(Driver driver,String host,String version,int warmup,int frames,Consumer<String> save) {
        if(warmup<0||frames<=0)throw new IllegalArgumentException("非法采样规模");
        long enter=System.nanoTime();driver.frame();long first=System.nanoTime()-enter;
        Facts initial=facts(driver);
        for(String load:new String[]{"IDLE","SCROLL","UPDATE","RESIZE"}){
            PerformanceSamples samples=new PerformanceSamples(frames);long previous=0;int changes=0;int[] updates={0,0};
            Facts before=facts(driver);
            for(int i=0;i<warmup+frames;i++){
                final int step=i;long start=System.nanoTime();
                if(load.equals("SCROLL")){Facts position=before;driver.scroll(position.x,position.y,(i/12)%2==0?-2:2);}
                else if(load.equals("UPDATE"))domain.execute(()->{
                    List<MailboxModel.Item> items=model.results.get();MailboxModel.Item item=items.get(step%2==0?0:items.size()-1);
                    MailboxModel.Message m=item.value.get();model.update(new MailboxModel.Message(m.id,m.sender,
                        "当前更新 #"+step,m.preview,m.body,m.time,m.starred,m.read,m.inbox));
                    if(!item.value.get().subject.equals("当前更新 #"+step))throw new AssertionError("当前数据更新未生效");
                    updates[step%2]++;
                });
                else if(load.equals("RESIZE"))driver.resize((i%2==0?1100:960),800);
                driver.frame();long end=System.nanoTime();
                if(i>=warmup)samples.add(end-start,previous==0?0:start-previous);
                previous=start;
                Facts after=facts(driver);
                if(load.equals("SCROLL")&&after.offset!=before.offset || load.equals("UPDATE")&&!after.changedText.equals(before.changedText)
                        || load.equals("RESIZE")&&(after.width!=before.width||after.height!=before.height))changes++;
                before=after;
            }
            boolean observed=load.equals("IDLE")||changes>0;
            if(load.equals("UPDATE"))observed=observed&&updates[0]>0&&updates[1]>0;
            if(!observed)throw new AssertionError("收件箱负载未命中: "+load);
            Facts last=facts(driver);
            String json="{\"schema\":\"uilib-mailbox-v1\",\"host\":\""+host+"\",\"version\":\""+version
                +"\",\"paged\":"+paged+",\"scale\":"+scale+",\"load\":\""+load+"\",\"complete\":true,\"pathObserved\":"+observed
                +",\"warmup\":"+warmup+",\"frames\":"+frames+",\"firstFrameNanos\":"+first
                +",\"declarationNanos\":"+declarationNanos+",\"mounted\":"+initial.mounted+",\"visible\":"+initial.visible+",\"lastMounted\":"+last.mounted
                +",\"insideUpdates\":"+updates[0]+",\"outsideUpdates\":"+updates[1]+",\"lastVisible\":"+last.visible+",\"scrollMaximum\":"+last.maximum+",\"samples\":"+samples.toJson()+"}";
            save.accept(json);System.out.println("MAILBOX_PERFORMANCE host="+host+" scale="+scale+" load="+load+" p95ns="+samples.frame().p95Nanos);
        }
    }
}
