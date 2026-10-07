package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import club.heiqi.uilib.host.client.font.compat.FontHostOrderQueue;
import club.heiqi.uilib.host.client.font.compat.OptionalFontHostBridge;
import club.heiqi.uilib.host.client.font.context.FontProjection;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;

/** 直接运行生产queue与absent-host bridge callbacks；不将模型称为真实Mixin安装或world画面验收。 */
public final class FontHostOrderScenario extends TestApplication {

    /** 只给生产queue提供identity票据，不模拟BFR；同range故意equals相等以防退化到值映射。 */
    private static final class RangeSegment {
        final Object owner;final int start,end;
        RangeSegment(Object owner,int start,int end){this.owner=owner;this.start=start;this.end=end;}
        @Override public boolean equals(Object other){if(!(other instanceof RangeSegment))return false;RangeSegment value=(RangeSegment)other;return start==value.start&&end==value.end;}
        @Override public int hashCode(){return 31*start+end;}
    }
    public static void main(String[] args) throws Exception { new FontHostOrderScenario().run(args); }

    @Override protected void runTests() throws Exception {
        List<String> order=new ArrayList<String>();int[] closes={0};FontHostOrderQueue queue=new FontHostOrderQueue(4);
        onClose(queue::close);
        scenario("同range双owner按identity保序", () -> {
        RangeSegment oldA=new RangeSegment(new Object(),0,1),oldC=new RangeSegment(new Object(),0,1);int[] mixedLeases={0,0};
        require(oldA.owner!=oldC.owner&&oldA.equals(oldC),"双owner同range输入");
        queue.sealed(oldA);queue.submit(()->order.add("newB"),()->{mixedLeases[0]++;closes[0]++;});
        queue.sealed(oldC);queue.submit(()->order.add("newD"),()->{mixedLeases[1]++;closes[0]++;});
        require(queue.containsOld(oldA)&&queue.containsOld(oldC),"同range两identity独立登记");
        queue.beforeOld(oldA);order.add("oldA");require(!queue.containsOld(oldA)&&queue.containsOld(oldC),"消费A不能删除同range的C");queue.beforeOld(oldC);order.add("oldC");queue.drainTail();
        require(mixedLeases[0]==1&&mixedLeases[1]==1,"两mixed sourcelease各释放一次");
        require(order.equals(Arrays.asList("oldA","newB","oldC","newD")),"混合segment身份顺序");require(closes[0]==2,"sourceleases精确释放");
        });
        scenario("无旧segment尾部消费", () -> {
        queue.submit(()->order.add("newOnly"),()->closes[0]++);queue.drainTail();require(order.get(4).equals("newOnly"),"旧empty的新tail仍消费");
        });
        scenario("中止释放票据", () -> {
        queue.submit(()->{throw new AssertionError("abort不得回放");},()->closes[0]++);queue.close();require(queue.pending()==0&&closes[0]==4,"abort释放");
        });
        scenario("回放失败释放剩余队列", () -> {
        queue.submit(()->{throw new IllegalStateException("replay failed");},()->closes[0]++);queue.submit(()->{throw new AssertionError("失败后不可继续");},()->closes[0]++);
        try{queue.drainTail();throw new AssertionError("异常未传播");}catch(IllegalStateException expected){require(queue.pending()==0&&closes[0]==6,"异常路径清队列");}
        });
        scenario("可选宿主缺失共享sink即时回放", () -> {
        float[] identity=new float[16];identity[0]=identity[5]=identity[10]=identity[15]=1;
        FontRenderContext context=new FontRenderContext(new FontProjection(identity,identity,new int[]{0,0,800,600}),0,600,false,0x0203,false,false,new int[]{0,0,800,600},FontRenderContext.MatrixState.ui(identity,identity),new Object(),1,0);
        PaintPlan plan=new PaintPlanBuilder().build();int[] callbacks={0};ClassLoader absent=new ClassLoader(FontHostOrderScenario.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(name.startsWith("com.gtnewhorizons.angelica."))throw new ClassNotFoundException(name);return super.loadClass(name,resolve);}
        };
        OptionalFontHostBridge bridge=OptionalFontHostBridge.create(absent,new OptionalFontHostBridge.ReplaySink(){
            @Override public void draw(PaintPlan value,FontRenderContext snapshot){require(value==plan&&snapshot==context,"同plan/context");callbacks[0]++;}
            @Override public void registerVisible(PaintPlan value,FontRenderContext snapshot){callbacks[0]++;}
            @Override public FontRenderContext capture(){return context;}
        },4);
        onClose(bridge::close);
        bridge.submit(plan,context,()->closes[0]++);require(callbacks[0]==2&&closes[0]==7&&bridge.pending()==0,"absent host共享sink即时路径");
        });
        System.out.println("FONT_HOST_ORDER_PASS oldA,newB,oldC,newD twoOwnersSameRangeIdentity newOnly abort replayFailure directSharedSink leases="+closes[0]);
    }
}
