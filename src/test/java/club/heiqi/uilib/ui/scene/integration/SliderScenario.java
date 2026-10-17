package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneAppearance;
import club.heiqi.uilib.ui.scene.control.SceneSlider;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.ElasticOptions;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.ArrayList;
import java.util.List;

/** 实际页面捕获反例：移动只预览、UP一次提交、最新范围/代际失效和键盘。 */
public final class SliderScenario extends TestApplication {
    public static void main(String[] args)throws Exception{new SliderScenario().run(args);}
    protected void runTests()throws Exception{
        scenario("拖动预览、最新范围与单次提交",()->{
            ReactiveDomain domain=ReactiveDomain.direct();SoftwareSceneScenario.SoftwareHost host=new SoftwareSceneScenario.SoftwareHost();host.width=200;host.height=100;int[] neighbor={0};
            Signal<SceneSlider.Model>[] model=new Signal[1];SceneNode[] slider={null};List<Double> changes=new ArrayList<>(),commits=new ArrayList<>();
            try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
                model[0]=domain.signal(new SceneSlider.Model(0,100,1,20,true).token(1));
                slider[0]=SceneSlider.create(ctx,model[0],changes::add,commits::add,()->SceneTheme.light()).size(Size.fill(1),Size.fixed(40));ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(slider[0]).add(new SceneNode().size(Size.fill(1),Size.fixed(40)).onClick(()->neighbor[0]++)));
            })){
                page.frame();page.pointer(ScenePointerEvent.down(0,45,20));page.pointer(ScenePointerEvent.move(150,20));page.frame();
                require(!changes.isEmpty()&&commits.isEmpty(),"拖动过程中提前提交");
                domain.execute(()->{require(SceneSlider.preview(slider[0]).get().active,"拖动无独立预览");model[0].set(new SceneSlider.Model(0,50,1,20,true).token(1));});page.frame();
                page.pointer(ScenePointerEvent.up(0,250,20));page.frame();require(commits.size()==1&&commits.get(0)==50,"释放未按最新范围提交一次："+commits);
                page.pointer(ScenePointerEvent.up(0,250,20));page.frame();require(commits.size()==1,"重复UP再次提交");
                page.pointer(ScenePointerEvent.down(0,40,20));page.pointer(ScenePointerEvent.move(100,20));page.frame();int beforeKey=commits.size();
                page.edit(SceneEditEvent.command(SceneEditEvent.Command.RIGHT,false,null));page.frame();require(commits.size()==beforeKey,"活动拖动被键盘提前提交");
                page.pointer(ScenePointerEvent.up(0,140,20));page.frame();require(commits.size()==beforeKey+1,"拖动与键盘交错在UP重复提交");commits.remove(commits.size()-1);
                page.pointer(ScenePointerEvent.down(0,30,20));page.frame();domain.execute(()->model[0].set(new SceneSlider.Model(0,100,1,0,true).token(2)));page.frame();
                page.pointer(ScenePointerEvent.up(0,170,20));page.frame();require(commits.size()==1,"新代际收到旧拖动提交");
                page.edit(SceneEditEvent.command(SceneEditEvent.Command.END,false,null));page.frame();require(commits.size()==2&&commits.get(1)==100,"键盘End没有提交上限");
                page.pointer(ScenePointerEvent.down(0,45,20));page.frame();page.pipeline().escape(()->{throw new AssertionError("捕获ESC穿透");});page.frame();page.pointer(ScenePointerEvent.up(0,160,20));page.frame();require(commits.size()==2,"ESC取消拖动后仍提交");
                page.pointer(ScenePointerEvent.down(0,50,60));page.pointer(ScenePointerEvent.up(0,50,60));page.frame();require(neighbor[0]==1&&commits.size()==2,"滑杆接管了自身盒子外的相邻操作");
            }
        });
        scenario("弹性两端阻力、共同轨道与释放回弹",this::elasticEdges);
        scenario("正常、减少与关闭采用不同弹性呈现",this::elasticPolicies);
        scenario("弹性取消、接管、声明撤销与卸载",this::elasticLifecycle);
        scenario("窗口变化更新弹性几何且外部值保持权威",this::elasticResize);
        scenario("回弹中的可见 thumb 原地重抓与连续拖动",this::elasticRegrab);
    }

    private void elasticEdges(){
        try(ElasticFixture f=new ElasticFixture(MotionPolicy.NORMAL,true)){
            Visual rest=f.visual();int y=f.box().y+f.box().height/2,center=(rest.left+rest.right)/2;
            f.pointer(ScenePointerEvent.down(0,center,y));
            f.pointer(ScenePointerEvent.move(rest.right+20,y));double first=f.offset();Visual small=f.visual();
            f.pointer(ScenePointerEvent.move(rest.right+40,y));double second=f.offset();
            f.pointer(ScenePointerEvent.move(rest.right+60,y));double third=f.offset();Visual large=f.visual();
            require(first>0&&second>first&&third>second&&second-first>third-second,"右端阻力未逐渐增加");
            require(large.left==rest.left&&large.right>small.right&&large.thickness<=small.thickness,
                    "右端拉伸未固定左端或厚度未收缩");
            require(Math.abs(large.thumb-large.right)<=1&&f.value()==100,"thumb 未跟随右端或预览越界");
            require(f.commits.isEmpty(),"越界拖动提前提交");
            f.pointer(ScenePointerEvent.up(0,rest.right+60,y));
            require(f.offset()>third*.9&&f.commits.size()==1&&f.commits.get(0)==100,"UP 未保留当前形变或提交错误");
            f.advance(40_000_000L);double middle=f.offset();
            require(middle>0&&middle<third,"释放未经过中间形变");
            f.advance(2_000_000_000L);require(f.offset()==0&&f.commits.size()==1,"回弹未归零或再次提交");
            f.pointer(ScenePointerEvent.down(0,center,y));
            double[] offsets=new double[3];
            for(int i=0;i<offsets.length;i++){f.pointer(ScenePointerEvent.move(rest.left-(i+1)*20,y));offsets[i]=-f.offset();}
            Visual left=f.visual();
            require(offsets[0]>0&&offsets[1]>offsets[0]&&offsets[2]>offsets[1]
                    &&offsets[1]-offsets[0]>offsets[2]-offsets[1],"左端阻力未逐渐增加");
            require(left.left<rest.left&&left.right==rest.right&&Math.abs(left.thumb-left.left)<=1&&f.value()==0,
                    "左端轨道与 thumb 分离或预览越界");
            f.pointer(ScenePointerEvent.up(0,rest.left-60,y));require(f.offset()<0,"左端释放立即清空形变");
            f.advance(300_000_000L);Visual returning=f.visual();
            require(returning.right==rest.right&&Math.abs(returning.thumb-returning.left)<=1,"回弹穿过零点时切换到了另一端");
            f.advance(2_000_000_000L);require(f.offset()==0&&f.commits.size()==2&&f.commits.get(1)==0,"左端回弹或单次提交错误");
            for(double value:f.changes)require(value>=0&&value<=100,"onChange 发布了越界值："+value);
            PaintPlan settled=f.plan();f.advance(2_000_000_000L);require(settled==f.plan(),"弹性结束后仍使静态计划失效");
            System.out.println("Slider 右端阻力="+first+","+second+","+third+"；释放40ms="+middle);
        }
    }

    private void elasticPolicies(){
        double normal=0,reduced=0;
        for(MotionPolicy policy:MotionPolicy.values()){
            try(ElasticFixture f=new ElasticFixture(policy,true)){
                Visual rest=f.visual();int y=f.box().y+f.box().height/2;
                f.pointer(ScenePointerEvent.down(0,(rest.left+rest.right)/2,y));
                f.pointer(ScenePointerEvent.move(rest.right+60,y));double pulled=f.offset();
                if(policy==MotionPolicy.NORMAL)normal=pulled;
                else if(policy==MotionPolicy.REDUCED){reduced=pulled;require(reduced>0&&reduced<normal,"减少策略等同关闭或未缩小形变");}
                else require(pulled==0,"关闭策略仍保留越界形变");
                f.pointer(ScenePointerEvent.up(0,rest.right+60,y));
                f.advance(40_000_000L);double first=f.offset();f.advance(40_000_000L);double second=f.offset();
                if(policy==MotionPolicy.REDUCED)require(first>second&&second>0,"减少策略没有可见的单调回位");
                if(policy==MotionPolicy.OFF)require(first==0&&second==0,"关闭策略创建了回弹");
                f.advance(120_000_000L);
                require(policy==MotionPolicy.NORMAL?f.offset()!=0:f.offset()==0,"三种策略的结束行为未区分");
                require(f.commits.size()==1&&f.commits.get(0)==100,"策略改变了合法值或动作次数");
                System.out.println("Slider 策略="+policy+"；拖动形变="+pulled+"；释放40/80ms="+first+"/"+second);
            }
        }
        try(ElasticFixture f=new ElasticFixture(MotionPolicy.NORMAL,true)){
            Visual rest=f.visual();int y=f.box().y+f.box().height/2;
            f.pointer(ScenePointerEvent.down(0,(rest.left+rest.right)/2,y));f.pointer(ScenePointerEvent.move(rest.right+60,y));
            f.domain.execute(()->f.policy.set(MotionPolicy.REDUCED));f.page.frame();
            require(f.offset()>0&&f.offset()<normal,"拖动中的策略替换未生效");
            f.pointer(ScenePointerEvent.up(0,rest.right+60,y));
            f.domain.execute(()->f.policy.set(MotionPolicy.OFF));f.page.frame();
            require(f.offset()==0&&f.commits.size()==1,"回弹途中关闭仍保留轨迹或产生额外提交");
        }
    }

    private void elasticLifecycle(){
        try(ElasticFixture f=new ElasticFixture(MotionPolicy.NORMAL,true)){
            Visual rest=f.visual();int y=f.box().y+f.box().height/2,center=(rest.left+rest.right)/2;
            f.pointer(ScenePointerEvent.down(0,center,y));f.pointer(ScenePointerEvent.move(rest.left-60,y));
            f.pointer(ScenePointerEvent.move(center,y));require(f.offset()==0&&f.value()==50,"反向进入范围仍积存越界距离");
            f.pointer(ScenePointerEvent.move(rest.left-60,y));f.pointer(ScenePointerEvent.cancel());f.pointer(ScenePointerEvent.up(0,rest.left-60,y));
            require(f.offset()==0&&!f.active()&&f.commits.isEmpty(),"取消保留形变或补发提交");
            f.pointer(ScenePointerEvent.down(0,center,y));f.pointer(ScenePointerEvent.move(rest.right+60,y));
            f.pointer(ScenePointerEvent.up(0,rest.right+60,y));require(f.offset()>0,"接管前缺少真实回弹");
            f.pointer(ScenePointerEvent.down(0,center,y));require(f.offset()==0&&f.active()&&f.value()==50,"新指针未立即接管旧轨迹");
            f.advance(80_000_000L);require(f.offset()==0,"旧回弹抢回了新指针通道");
            f.pointer(ScenePointerEvent.move(rest.left-40,y));
            f.domain.execute(()->f.model.set(new SceneSlider.Model(0,100,1,70,true).elastic(f.elastic).token(2)));
            f.page.frame();f.pointer(ScenePointerEvent.up(0,rest.left-40,y));
            require(f.offset()==0&&!f.active()&&f.value()==70&&f.commits.size()==1,"新 token 继承旧手势或忽略权威值");
            f.pointer(ScenePointerEvent.down(0,center,y));f.pointer(ScenePointerEvent.move(rest.right+60,y));
            f.domain.execute(()->f.model.set(f.model.get().elastic(null)));f.page.frame();
            require(f.offset()==0&&f.active(),"动态撤销弹性取消了原普通拖动");
            f.pointer(ScenePointerEvent.up(0,rest.right+60,y));require(f.commits.size()==2,"撤销弹性改变了 UP 提交次数");
            f.domain.execute(()->f.model.set(f.model.get().elastic(f.elastic)));f.page.frame();
            f.pointer(ScenePointerEvent.down(0,center,y));f.pointer(ScenePointerEvent.move(rest.right+60,y));
            f.pointer(ScenePointerEvent.up(0,rest.right+60,y));
            f.domain.execute(()->f.model.set(f.model.get().elastic(null)));f.page.frame();
            require(f.offset()==0&&f.commits.size()==3,"释放途中撤销弹性留下轨迹");
            f.domain.execute(()->f.model.set(f.model.get().elastic(f.elastic)));f.page.frame();
            f.pointer(ScenePointerEvent.down(0,center,y));f.pointer(ScenePointerEvent.move(rest.left-60,y));
            f.domain.execute(()->f.rootEnabled.set(false));f.page.frame();f.pointer(ScenePointerEvent.up(0,rest.left-60,y));
            require(f.offset()==0&&!f.active()&&f.commits.size()==3,"祖先失格后仍保留手势或提交");
            f.domain.execute(()->f.rootEnabled.set(true));f.page.frame();
            f.pointer(ScenePointerEvent.down(0,center,y));f.pointer(ScenePointerEvent.move(rest.right+60,y));
            f.pointer(ScenePointerEvent.up(0,rest.right+60,y));require(f.offset()>0,"卸载前回弹未开始");
            f.domain.execute(()->f.mount.detach());require(f.offset()==0,"卸载未清除回弹呈现");
            f.advance(2_000_000_000L);require(f.commits.size()==4,"卸载后旧轨迹发布提交");
            try(ScenePageSession remounted=ScenePageSession.open(f.domain,f.host,f.host,ctx->ctx.mount(f.root))){
                remounted.frame();remounted.pointer(ScenePointerEvent.down(0,center,y));
                remounted.pointer(ScenePointerEvent.move(rest.left-60,y));remounted.frame();
                require(f.offset()<0&&f.active(),"跨页面重挂仍使用旧运行时");
                remounted.pointer(ScenePointerEvent.cancel());remounted.pointer(ScenePointerEvent.up(0,rest.left-60,y));remounted.frame();
                require(f.offset()==0&&f.commits.size()==4,"重挂后的取消恢复旧手势");
            }
        }
    }

    private void elasticResize(){
        try(ElasticFixture f=new ElasticFixture(MotionPolicy.NORMAL,false)){
            Visual rest=f.visual();int y=f.box().y+f.box().height/2;
            f.pointer(ScenePointerEvent.down(0,(rest.left+rest.right)/2,y));f.pointer(ScenePointerEvent.move(rest.right+60,y));
            int changes=f.changes.size();f.host.width=240;f.page.frame();
            Visual narrow=f.visual();require(f.offset()>0&&Double.isFinite(f.offset())&&narrow.right<rest.right+60,
                    "缩窄窗口仍消费旧轨道");
            require(f.changes.size()==changes,"窗口重排向业务回写数值");
            f.host.width=600;f.page.frame();require(f.offset()==0,"窗口扩大后指针已入范围却残留形变");
            f.pointer(ScenePointerEvent.up(0,rest.right+60,y));
            f.domain.execute(()->f.model.set(new SceneSlider.Model(0,100,1,25,true).elastic(f.elastic)));f.page.frame();
            require(f.value()==25&&!f.active()&&f.commits.size()==1,"外部模型未在释放后接管数值");
        }
    }

    private void elasticRegrab(){
        for(int width:new int[]{200,320})for(int direction:new int[]{-1,1}){
            try(ElasticFixture f=new ElasticFixture(MotionPolicy.NORMAL,true,width)){
                Visual rest=f.visual();LayoutBox box=f.box();int y=box.y+box.height/2;
                int outside=direction<0?box.x-100:box.x+box.width+100;
                f.pointer(ScenePointerEvent.down(0,rest.thumb,y));f.pointer(ScenePointerEvent.move(outside,y));
                f.pointer(ScenePointerEvent.up(0,outside,y));f.advance(40_000_000L);
                // thumb 之外仍由原背景目标接收，不能把整段溢出带都变成 Slider 命中。
                int blank=direction<0?box.x-20:box.x+box.width+20;
                f.pointer(ScenePointerEvent.down(0,blank,box.y+2));f.pointer(ScenePointerEvent.up(0,blank,box.y+2));
                require(f.outsideClicks==1&&f.commits.size()==1,"溢出 thumb 扩大成了整段输入区域");
                double before=f.offset();Visual shown=f.visual();
                require(direction*before>0,"重抓前缺少当前回弹形变");
                require(width==200?!box.contains(shown.thumb,y):box.contains(shown.thumb,y),"未覆盖盒外/盒内的两种可见 thumb");
                f.pointer(ScenePointerEvent.down(0,shown.thumb,y));
                require(f.active()&&Math.abs(f.offset()-before)<.001&&f.visual().thumb==shown.thumb,
                        "原地重抓清空形变、二次施加阻力或未捕获：width="+width+" direction="+direction);
                f.advance(80_000_000L);
                require(Math.abs(f.offset()-before)<.001&&f.visual().thumb==shown.thumb&&f.commits.size()==1,
                        "原回弹继续抢占被拾取的 thumb");
                f.pointer(ScenePointerEvent.move(shown.thumb+direction*8,y));
                require(direction*(f.offset()-before)>0&&direction*(f.visual().thumb-shown.thumb)>0,
                        "重抓后端点未继续跟随指针");
                f.pointer(ScenePointerEvent.move(shown.thumb,y));
                require(f.visual().thumb==shown.thumb,"反向回到拾取点时形变跳变");
                f.pointer(ScenePointerEvent.move(rest.thumb,y));
                require(f.offset()==0&&f.value()==50&&Math.abs(f.visual().thumb-rest.thumb)<=1,
                        "重抓反向进入合法范围后未直接跟手");
                f.pointer(ScenePointerEvent.up(0,rest.thumb,y));
                require(f.commits.size()==2&&f.commits.get(1)==50,"重抓引入额外提交或未按当前值提交");
                System.out.println("Slider 重抓 width="+width+" direction="+direction+" thumb="+shown.thumb+" offset="+before);
            }
        }
    }

    private static final class ElasticFixture implements AutoCloseable {
        final ReactiveDomain domain=ReactiveDomain.direct();
        final SoftwareSceneScenario.SoftwareHost host=new SoftwareSceneScenario.SoftwareHost();
        final ElasticOptions elastic=new ElasticOptions(48,MotionSpec.spring(.58,.52));
        final List<Double> changes=new ArrayList<>(),commits=new ArrayList<>();
        final boolean deterministic;
        Signal<SceneSlider.Model> model;
        Signal<MotionPolicy> policy;
        Signal<Boolean> rootEnabled;
        SceneNode slider,root;
        int outsideClicks;
        MountHandle mount;
        final ScenePageSession page;
        ElasticFixture(MotionPolicy initial,boolean deterministic){this(initial,deterministic,0);}
        ElasticFixture(MotionPolicy initial,boolean deterministic,int width){
            this.deterministic=deterministic;host.width=width>0?600:400;host.height=width>0?180:160;
            page=ScenePageSession.open(domain,host,host,ctx->{
                model=domain.signal(new SceneSlider.Model(0,100,1,50,true).elastic(elastic));
                policy=domain.signal(initial);ctx.motionPolicy(policy);rootEnabled=domain.signal(true);
                slider=SceneSlider.create(ctx,model,changes::add,commits::add,()->SceneTheme.light()).size(width>0?Size.fixed(width):Size.fill(1),Size.fixed(48));
                root=new SceneNode().size(Size.fill(1),Size.fill(1)).padding(width>0?60:40)
                        .bind(NodeBinding.value(rootEnabled,SceneNode::enabled)).add(slider);
                if(width>0)root.onClick(()->outsideClicks++);mount=ctx.mount(root);
            });page.frame();
        }
        void pointer(ScenePointerEvent event){
            long[] now={0};domain.execute(()->now[0]=page.runtime().inputNanos());
            long time=deterministic?Math.max(now[0],System.nanoTime())+10_000_000L:System.nanoTime();
            page.pointer(event.at(time));page.frame();
        }
        void advance(long elapsed){domain.execute(()->{page.runtime().preparePresentation(page.runtime().inputNanos()+elapsed);page.pipeline().current();});}
        double offset(){double[] result={0};domain.execute(()->result[0]=SceneSlider.elasticOffset(slider).get());return result[0];}
        double value(){double[] result={0};domain.execute(()->result[0]=SceneSlider.preview(slider).get().value);return result[0];}
        boolean active(){boolean[] result={false};domain.execute(()->result[0]=SceneSlider.preview(slider).get().active);return result[0];}
        LayoutBox box(){LayoutBox[] result={null};domain.execute(()->result[0]=page.pipeline().current().geometry.get(slider).box);return result[0];}
        PaintPlan plan(){PaintPlan[] result={null};domain.execute(()->result[0]=page.pipeline().current().paint);return result[0];}
        Visual visual(){return new Visual(plan());}
        public void close(){page.close();}
    }

    /** 从正式 PaintPlan 汇总轨道和 thumb 的像素边界，避免另算一套预期轨道。 */
    private static final class Visual {
        final int left,right,thickness,thumb;
        Visual(PaintPlan plan){
            SceneAppearance.Palette colors=new SceneAppearance.Palette(SceneTheme.light());
            int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
            int thumbLeft=Integer.MAX_VALUE,thumbRight=Integer.MIN_VALUE;
            for(PaintCommand command:plan.commands()){
                LayoutBox box=command.box;if(command.kind!=PaintCommand.Kind.RECT||box.width==0||box.height==0)continue;
                if((command.color&0xffffff)==(colors.border&0xffffff)){
                    minX=Math.min(minX,box.x);maxX=Math.max(maxX,box.x+box.width);
                    minY=Math.min(minY,box.y);maxY=Math.max(maxY,box.y+box.height);
                }else if((command.color&0xffffff)==(colors.background&0xffffff)){
                    thumbLeft=Math.min(thumbLeft,box.x);thumbRight=Math.max(thumbRight,box.x+box.width);
                }
            }
            require(minX<maxX&&thumbLeft<thumbRight,"正式计划缺少轨道或 thumb");
            left=minX;right=maxX;thickness=maxY-minY;thumb=(thumbLeft+thumbRight)/2;
        }
    }
}
