package club.heiqi.uilib.host.client.font;

import club.heiqi.uilib.host.client.font.compat.FontHostOrderQueue;
import club.heiqi.uilib.host.client.font.context.FontPlanSampling;
import club.heiqi.uilib.host.client.font.context.FontProjection;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.host.client.font.context.RasterPolicy;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.RasterSampling;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import java.lang.reflect.Field;
import java.util.Map;

/** 真实逻辑字形、生产采样器和正式队列；不执行GL，不把复用次数等同FPS。 */
public final class WorldFontSamplingScenario {
    private static final RasterPolicy POLICY = new RasterPolicy(32,48,64,96,128,192,256,384,512);
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static FontRenderContext context(float xScale, int viewportX, int width, long revision) {
        float[] p={xScale,0,0,0,0,.01f,0,0,0,0,1,0,0,0,0,1};
        float[] mv={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
        return new FontRenderContext(new FontProjection(p,mv,new int[] {viewportX,0,width,600}),
                0,600,false,0x0203,false,false,new int[] {0,0,width,600},
                FontRenderContext.MatrixState.ui(p,mv),new Object(),revision,0);
    }
    private static PaintPlan plan(PaintCommand command) { return new PaintPlanBuilder().add(command).build(); }
    private static RasterSampling sample(WorldFontSampling facts, PaintCommand command,
            FontRenderContext context, boolean remember) {
        return facts.sample(command,context,WorldFontSampling.Source.capture(context),remember);
    }
    private static int size(WorldFontSampling facts) throws Exception {
        Field entries=WorldFontSampling.class.getDeclaredField("entries"); entries.setAccessible(true);
        return ((Map<?,?>)entries.get(facts)).size();
    }
    private static void sameValue(RasterSampling actual, PaintCommand command, FontRenderContext context) {
        RasterSampling expected=FontPlanSampling.sampling(command,context,POLICY);
        require(Float.floatToRawIntBits(actual.pixelsPerLayoutUnit)==Float.floatToRawIntBits(expected.pixelsPerLayoutUnit)
                &&actual.revision==expected.revision,"复用或miss改变实际采样值");
    }
    private static void memo(TextLayout logical) throws Exception {
        WorldFontSampling facts=new WorldFontSampling(POLICY,2);
        FontRenderContext first=context(.01f,0,800,1), equal=context(.01f,0,800,1);
        PaintCommand a=PaintCommand.text(new LayoutBox(1,2,300,30),null,logical);
        RasterSampling initial=sample(facts,a,first,true);
        facts.forget(plan(a)); facts.endFrame();
        PaintCommand next=PaintCommand.text(new LayoutBox(1,2,300,30),null,logical);
        require(sample(facts,next,equal,true)==initial,"下一帧相同数值输入未复用采样结果");
        require(sample(facts,next,equal,false)==initial&&size(facts)==0,"memo命中绕过票据消费");
        for(PaintCommand moved:new PaintCommand[] {
                PaintCommand.text(new LayoutBox(2,2,300,30),null,logical),
                PaintCommand.text(new LayoutBox(1,3,300,30),null,logical)}) {
            RasterSampling result=sample(facts,moved,equal,false);
            require(result!=initial,"位置变化复用旧墨迹采样"); sameValue(result,moved,equal);
        }
        WorldFontSamplingMemo memo=new WorldFontSamplingMemo(POLICY);
        WorldFontSampling.Source source=WorldFontSampling.Source.capture(first);
        RasterSampling oldest=memo.sample(a,first,source);
        for(int i=0;i<128;i++) memo.sample(PaintCommand.text(new LayoutBox(i+2,2,300,30),null,logical),first,source);
        Field values=WorldFontSamplingMemo.class.getDeclaredField("values"); values.setAccessible(true);
        require(((Map<?,?>)values.get(memo)).size()==128,"数值memo容量失控");
        require(memo.sample(a,first,source)!=oldest,"LRU退役后仍返回旧值对象");
        facts.clear(); require(sample(facts,a,first,false)!=initial,"资源通知或close未清纯CPU结果");
        facts.clear();
    }

    public static void verify(TextLayout logical) throws Exception {
        PaintCommand a=PaintCommand.text(new LayoutBox(1,2,300,30),null,logical);
        PaintCommand b=PaintCommand.text(new LayoutBox(1,12,300,30),null,logical);
        PaintCommand c=PaintCommand.text(new LayoutBox(1,22,300,30),null,logical);
        FontRenderContext first=context(.01f,0,800,1), equal=context(.01f,0,800,1);
        WorldFontSampling facts=new WorldFontSampling(POLICY,2);
        RasterSampling remembered=sample(facts,a,first,true);
        require(size(facts)==1,"登记未保留票据事实");
        require(sample(facts,a,equal,false)==remembered,"数值相同但context身份不同未复用原采样标量");
        require(size(facts)==0,"回放未消费事实");
        RasterSampling consumed=sample(facts,a,first,false);
        require(size(facts)==0,"memo命中不得重建已消费票据"); sameValue(consumed,a,first);
        for(FontRenderContext changed:new FontRenderContext[] {
                context(Math.nextUp(.01f),0,800,1),context(.01f,1,800,1),
                context(.01f,0,801,1),context(.01f,0,800,2)}) {
            RasterSampling old=sample(facts,a,first,true), fresh=sample(facts,a,changed,false);
            require(fresh!=old&&size(facts)==0,"矩阵、viewport或revision变化复用了旧事实");
            sameValue(fresh,a,changed);
        }
        RasterSampling keepA=sample(facts,a,first,true), keepB=sample(facts,b,first,true);
        sample(facts,c,first,true);
        require(size(facts)==2,"采样事实容量越界");
        sameValue(sample(facts,c,first,false),c,first);
        require(size(facts)==2,"数值memo命中改变票据容量");
        require(sample(facts,a,equal,false)==keepA&&sample(facts,b,equal,false)==keepB,
                "容量满破坏了既有票据事实");
        FontHostOrderQueue queue=new FontHostOrderQueue(2);
        sample(facts,a,first,true); sample(facts,b,first,true);
        queue.submit(()->{},()->facts.forget(plan(a)));
        queue.submit(()->{},()->facts.forget(plan(b)));
        queue.close(); queue.close(); require(size(facts)==0,"取消票据后仍保留采样事实");
        RuntimeException sentinel=new RuntimeException("采样事实绘制失败探针");
        sample(facts,a,first,true); sample(facts,b,first,true);
        queue.submit(()->{throw sentinel;},()->facts.forget(plan(a)));
        queue.submit(()->{},()->facts.forget(plan(b)));
        try { queue.drainTail(); throw new AssertionError("绘制失败未传播"); }
        catch(RuntimeException expected) { require(expected==sentinel,"绘制失败被替换"); }
        require(size(facts)==0&&queue.pending()==0,"异常后当前与后续票据事实未清空");
        FontHostOrderQueue bounded=new FontHostOrderQueue(1);
        RasterSampling accepted=sample(facts,a,first,true);
        bounded.submit(()->{},()->facts.forget(plan(a)));
        sample(facts,b,first,true);
        AutoCloseable rejected=()->facts.forget(plan(b));
        try { bounded.submit(()->{},rejected); throw new AssertionError("容量溢出未拒绝"); }
        catch(IllegalStateException expected) { FontHostOrderQueue.release(rejected); }
        require(size(facts)==1&&sample(facts,a,equal,false)==accepted,"拒绝票据污染或释放了已接收事实");
        bounded.close(); require(size(facts)==0,"有界票据关闭残留");
        sample(facts,a,first,true); sample(facts,a,first,true);
        facts.forget(plan(a)); facts.forget(plan(a));
        sameValue(sample(facts,a,equal,false),a,equal);
        sample(facts,b,first,true); facts.clear(); require(size(facts)==0,"帧或owner清理未退役事实");
        memo(logical);
        System.out.println("WORLD_FONT_SAMPLING_PASS equivalentContextReuse=true changedSourceMiss=true consumedOnce=true bounded=true queueCancelFailureOverflow=true numericMemo=true scalarOnly=true shaderExecuted=false");
    }
}
