package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.GalleryMediaResources;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.host.media.IsolatedMediaAdapters;
import club.heiqi.uilib.host.media.bilibili.BilibiliResolver;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaState;
import club.heiqi.uilib.ui.scene.control.SceneVideo;
import club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

/** 独立公网兼容验收：正式广场、GLFW、设备、回放和swap后GL_FRONT，不进入日常build。 */
public final class DesktopBilibiliVerification extends TestApplication {
    private DesktopWindow window;
    private DesktopTestInput input;
    private Path output;
    public static void main(String[] args)throws Exception{new DesktopBilibiliVerification().run(args);}
    protected void runTests()throws Exception{
        output=Paths.get(arguments()[0]);Files.createDirectories(output);
        scenario("Bilibili真实点播观看暂停定位和清晰度",()->{
            try(GalleryMediaResources media=new GalleryMediaResources(new MediaService(IsolatedMediaAdapters.decoderFactory(),IsolatedMediaAdapters.audioSinkFactory(),MediaBudget.DEFAULT),new BilibiliResolver());
                DesktopWindow opened=DesktopWindow.openWithServices(output.resolve("bilibili.yaml"),media.service(),false,services->DesktopGallery.createGallery(services,TestGallery.Page.MEDIA,media))){
                window=opened;input=new DesktopTestInput(window);glfwSetWindowSize(window.handle(),1200,900);DesktopTestFrames.settle(window);
                type(arguments().length>1?arguments()[1]:"BV1GJ411x7h7",true);
                await(()->snapshot()!=null&&snapshot().state==MediaState.PLAYING&&snapshot().statistics.presentedFrames>=8&&snapshot().statistics.decodedAudioBlocks>=8,45000,"点播没有实际音视频输出");
                require(text("索尼音乐中国"),"未显示真实作者");long session=sessionId();
                input.click("静音");input.click("暂停");await(()->snapshot().state==MediaState.PAUSED,5000,"点播暂停失败");long frozen=snapshot().positionNanos;
                for(int i=0;i<20;i++){window.frame();Thread.sleep(5);}require(snapshot().positionNanos==frozen,"暂停仍继续播放");capture("bilibili-vod.png");
                input.click("精确控制");DesktopTestFrames.settle(window);java.util.List<SceneNode> fields=fields();type(fields.get(fields.size()-1),"10",true);
                await(()->snapshot().positionNanos>=10_000_000_000L&&snapshot().state==MediaState.PAUSED,20000,"点播定位未恢复目标帧");input.click("收起精确控制");
                String quality=DesktopTestInput.geometry(window).ordered().stream().filter(e->e.node.clickAction()!=null&&e.node.text().startsWith("清晰度 · ")).map(e->e.node.text()).findFirst().get();input.click(quality);DesktopTestFrames.settle(window);
                String alternative=DesktopTestInput.geometry(window).ordered().stream().filter(e->e.node.clickAction()!=null&&inModal(e.node)&&!"取消选择".equals(e.node.text())&&!quality.contains(e.node.text())).map(e->e.node.text()).findFirst().orElseThrow(()->new AssertionError("匿名源没有可切换清晰度"));
                long generation=snapshot().generation;input.click(alternative);
                await(()->snapshot()!=null&&snapshot().generation>generation&&snapshot().video!=null&&snapshot().video.key.generation==snapshot().generation&&snapshot().state==MediaState.PAUSED&&snapshot().positionNanos>=10_000_000_000L,30000,"切清晰度未保持位置和暂停");
                DesktopTestFrames.settle(window);
                require(sessionId()==session&&snapshot().muted,"切清晰度更换了会话或丢失静音意图");capture("bilibili-quality.png");input.click("播放");await(()->snapshot().state==MediaState.PLAYING&&snapshot().positionNanos>10_200_000_000L,10000,"点播恢复无真实输出");
                System.out.println("BILIBILI_DESKTOP vod=true realAudio=true pause=true seek=true quality=true sameSession=true actualCallbacks=true actualSwap=true");
                input.click("停止");window.frame();require(media.service().activeSessions()==0,"停止未释放点播");
            }
        });
        scenario("Bilibili真实直播新帧和窗口刷新",()->{
            try(GalleryMediaResources media=new GalleryMediaResources(new MediaService(IsolatedMediaAdapters.decoderFactory(),IsolatedMediaAdapters.audioSinkFactory(),MediaBudget.DEFAULT),new BilibiliResolver());
                DesktopWindow opened=DesktopWindow.openWithServices(output.resolve("bilibili-live.yaml"),media.service(),false,services->DesktopGallery.createGallery(services,TestGallery.Page.MEDIA,media))){
                window=opened;input=new DesktopTestInput(window);glfwSetWindowSize(window.handle(),1200,900);DesktopTestFrames.settle(window);type(arguments().length>2?arguments()[2]:"https://live.bilibili.com/6",true);
                await(()->snapshot()!=null&&snapshot().state==MediaState.PLAYING&&snapshot().statistics.presentedFrames>=8&&snapshot().statistics.decodedAudioBlocks>=8,45000,"直播没有实际音视频输出");
                input.click("静音");MediaSnapshot before=snapshot();long start=before.info.seekable.startNanos,frames=before.statistics.presentedFrames,position=before.positionNanos;
                await(()->snapshot().statistics.presentedFrames>frames+30&&snapshot().positionNanos>position+3_000_000_000L&&snapshot().info.seekable.startNanos>start,30000,"直播没有持续新帧和真实窗口滑动");
                capture("bilibili-live.png");System.out.println("BILIBILI_DESKTOP live=true realAudio=true newFrames="+(snapshot().statistics.presentedFrames-frames)+" windowStart="+start+"->"+snapshot().info.seekable.startNanos+" actualSwap=true");
                input.click("返回场景概览");window.frame();require(media.service().activeSessions()==0,"离页未释放直播");
            }
        });
    }
    private java.util.List<SceneNode> fields(){return DesktopTestInput.geometry(window).ordered().stream().filter(e->e.node.control() instanceof SceneCompositionTarget).map(e->e.node).collect(Collectors.toList());}
    private void type(String value,boolean enter){type(fields().get(0),value,enter);}
    private void type(SceneNode field,String value,boolean enter){input.moveTo(field);input.mouse.invoke(window.handle(),0,GLFW_PRESS,0);input.mouse.invoke(window.handle(),0,GLFW_RELEASE,0);input.key.invoke(window.handle(),GLFW_KEY_A,0,GLFW_PRESS,GLFW_MOD_CONTROL);value.codePoints().forEach(c->input.character.invoke(window.handle(),c));if(enter)input.key.invoke(window.handle(),GLFW_KEY_ENTER,0,GLFW_PRESS,0);window.frame();}
    private MediaSnapshot snapshot(){final MediaSnapshot[] value={null};window.domain.execute(()->DesktopTestInput.geometry(window).ordered().stream().filter(e->e.node.control() instanceof SceneVideo).findFirst().ifPresent(e->value[0]=SceneVideo.snapshot(e.node).get()));return value[0];}
    private long sessionId(){return window.lastPlan.commands().stream().filter(c->c.kind==PaintCommand.Kind.VIDEO).map(c->c.video.frame.key.sessionId).findFirst().orElse(-1L);}
    private boolean text(String value){return window.lastPlan.commands().stream().anyMatch(c->c.kind==PaintCommand.Kind.TEXT&&c.text.logicalText.contains(value));}
    private static boolean inModal(SceneNode n){for(SceneNode at=n;at!=null;at=at.parent())if(at.modal())return true;return false;}
    private void capture(String name)throws Exception{DesktopTestFrames.settle(window);ImageIO.write(DesktopTestReadback.readFront(window),"png",output.resolve(name).toFile());DesktopGalleryEvidence.saveGeometry(window,output.resolve(name+".geometry.json"));}
    private void await(BooleanSupplier condition,long millis,String reason)throws Exception{
        long until=System.nanoTime()+millis*1_000_000L;
        while(!condition.getAsBoolean()&&System.nanoTime()<until){window.frame();MediaSnapshot s=snapshot();if(s!=null&&s.failure!=null)throw new AssertionError("媒体失败 "+s.failure.stage,s.failure.cause);Thread.sleep(5);}
        if(!condition.getAsBoolean()){capture("failure.png");System.out.println("BILIBILI_FAILURE "+window.lastPlan.commands().stream().filter(c->c.kind==PaintCommand.Kind.TEXT).map(c->c.text.logicalText).collect(Collectors.joining(" | ")));}
        require(condition.getAsBoolean(),reason);
    }
}
