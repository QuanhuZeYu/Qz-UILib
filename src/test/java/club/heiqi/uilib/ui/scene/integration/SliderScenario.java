package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneSlider;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
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
    }
}
