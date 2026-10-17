package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.GalleryMediaResources;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.testing.media.MediaNetworkFixture;
import club.heiqi.uilib.host.media.IsolatedMediaAdapters;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaResolver;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaState;
import club.heiqi.uilib.ui.media.spi.MediaCancellation;
import club.heiqi.uilib.ui.scene.control.SceneVideo;
import club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

/** 受控解析事实+真实HTTP分轨与正式GLFW，日常build不依赖公网。 */
public final class DesktopNetworkMediaVerification extends TestApplication {
    private DesktopWindow window;private DesktopTestInput input;private SceneNode address;private Path output;
    private static final class Resolver implements MediaResolver {
        final MediaNetworkFixture fixture;final AtomicInteger finished=new AtomicInteger();
        Resolver(MediaNetworkFixture fixture){this.fixture=fixture;}
        public boolean supports(String address){return address.startsWith("fixture:");}
        public Result resolve(Request request,MediaCancellation token)throws Exception{
            try{
                if(request.address.endsWith("slow")){
                    long until=System.nanoTime()+300_000_000L;
                    while(System.nanoTime()<until)try{Thread.sleep(5);}catch(InterruptedException ignored){/* 模拟不能及时结束的外部读取，验证调用方的当前性守卫。 */}
                }
                if(request.address.endsWith("error"))throw new java.io.IOException("当前内容需要登录");
                boolean offline=request.address.endsWith("offline");String part=request.part.isEmpty()?"1":request.part,quality=request.quality.isEmpty()?"high":request.quality;
                MediaSource source=MediaSource.split(MediaSource.http(fixture.uri("/video.avi?part="+part+"&quality="+quality),fixture.headers()),fixture.source("/audio.wav"));
                return new Result(offline?null:source,URI.create("fixture:ready"),"受控网络视频 P"+part,"测试作者",null,offline,
                        offline?Collections.emptyList():Arrays.asList(new Option("1","第一部分"),new Option("2","第二部分")),
                        offline?Collections.emptyList():Arrays.asList(new Option("high","高清"),new Option("low","流畅")),part,quality,offline?"直播间尚未开播":"受控网络来源");
            }finally{finished.incrementAndGet();}
        }
    }
    public static void main(String[] args)throws Exception{new DesktopNetworkMediaVerification().run(args);}
    protected void runTests()throws Exception{
        output=Paths.get(arguments()[0]);Files.createDirectories(output);
        scenario("受控网络来源草稿迟到停止错误和选择交互",()->{
            try(MediaNetworkFixture fixture=new MediaNetworkFixture()){
                Resolver resolver=new Resolver(fixture);
                try(GalleryMediaResources media=new GalleryMediaResources(new MediaService(IsolatedMediaAdapters.decoderFactory(),IsolatedMediaAdapters.audioSinkFactory(),MediaBudget.DEFAULT),resolver);
                    DesktopWindow opened=DesktopWindow.openWithServices(output.resolve("network.yaml"),media.service(),false,services->DesktopGallery.createGallery(services,TestGallery.Page.MEDIA,media))){
                    window=opened;input=new DesktopTestInput(window);glfwSetWindowSize(window.handle(),1000,900);DesktopTestFrames.settle(window);address=fields().get(0);
                    int done=resolver.finished.get();typeAddress("fixture:slow",true);typeAddress("fixture:new-draft",false);
                    final int stale=done;await(()->resolver.finished.get()>stale&&text("地址已修改"),3000,"迟到解析覆盖了新草稿或未结束");require(editText(address).equals("fixture:new-draft")&&media.service().activeSessions()==0,"旧解析打开会话或覆盖新草稿");
                    done=resolver.finished.get();typeAddress("fixture:slow",true);input.click("停止");final int stopped=done;
                    await(()->resolver.finished.get()>stopped,3000,"被取消的读取没有结束");window.frame();require(media.service().activeSessions()==0&&!text("受控网络视频"),"停止后旧结果恢复播放");
                    typeAddress("fixture:error",true);await(()->text("当前内容需要登录"),3000,"权限错误没有明确显示");
                    typeAddress("fixture:offline",true);await(()->text("直播间尚未开播"),3000,"未开播提示缺失");require(media.service().activeSessions()==0,"未开播仍打开媒体");
                    typeAddress("fixture:ready",true);await(()->snapshot()!=null&&snapshot().video!=null&&snapshot().state==MediaState.PLAYING,5000,"受控来源没有真实播放");input.click("静音");input.click("暂停");await(()->snapshot().state==MediaState.PAUSED,3000,"受控暂停失败");long session=sessionId();
                    input.click("精确控制");DesktopTestFrames.settle(window);java.util.List<SceneNode> editors=fields();type(editors.get(editors.size()-1),"0.5",true);
                    await(()->snapshot().state==MediaState.PAUSED&&snapshot().positionNanos==500_000_000L,5000,"受控定位失败");input.click("收起精确控制");
                    input.click("清晰度 · 高清");DesktopTestFrames.settle(window);input.click("流畅");
                    await(()->text("清晰度 · 流畅")&&snapshot().video!=null&&snapshot().video.key.generation==snapshot().generation&&snapshot().state==MediaState.PAUSED,5000,"受控清晰度切换未恢复目标帧");
                    require(snapshot().positionNanos==500_000_000L&&sessionId()==session&&snapshot().muted,"清晰度未保持位置、会话或静音");
                    input.click("选择分P");DesktopTestFrames.settle(window);input.key.invoke(window.handle(),GLFW_KEY_ESCAPE,0,GLFW_PRESS,0);window.frame();require(DesktopTestInput.geometry(window).ordered().stream().noneMatch(e->e.node.modal()),"ESC未关闭选择层");
                    input.click("选择分P");DesktopTestFrames.settle(window);input.click("第二部分");
                    await(()->text("受控网络视频 P2")&&snapshot().video!=null&&snapshot().state==MediaState.PAUSED&&snapshot().positionNanos==0,5000,"分P切换未从起点开始");require(sessionId()==session,"分P切换创建第二会话");
                    DesktopTestFrames.settle(window);ImageIO.write(DesktopTestReadback.readFront(window),"png",output.resolve("network-part2.png").toFile());
                    long generation=snapshot().generation;input.click("刷新地址");await(()->snapshot().generation>generation&&snapshot().video!=null&&snapshot().video.key.generation==snapshot().generation,5000,"显式刷新未取得新播放代际");
                    typeAddress("fixture:slow",true);input.click("返回场景概览");await(()->media.service().activeSessions()==0,3000,"离页未释放会话");
                    require(fixture.rejected.get()==0,"播放器请求丢失HTTP头");
                    System.out.println("NETWORK_DESKTOP staleDraft=true stoppedResult=true permission=true offline=true split=true qualityPosition=true partFromStart=true sameSession=true refresh=true modalEscape=true leave=true");
                }
                long until=System.nanoTime()+3_000_000_000L;while(Thread.getAllStackTraces().keySet().stream().anyMatch(t->t.isAlive()&&t.getName().equals("uilib-media-resolver"))&&System.nanoTime()<until)Thread.sleep(5);
                require(Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&t.getName().equals("uilib-media-resolver")),"离页残留解析worker");
            }
        });
    }
    private java.util.List<SceneNode> fields(){return DesktopTestInput.geometry(window).ordered().stream().filter(e->e.node.control() instanceof SceneCompositionTarget).map(e->e.node).collect(Collectors.toList());}
    private void typeAddress(String text,boolean enter){if(DesktopTestInput.geometry(window).get(address)==null){input.click("更换来源");window.frame();}type(address,text,enter);}
    private void type(SceneNode field,String text,boolean enter){input.moveTo(field);input.mouse.invoke(window.handle(),0,GLFW_PRESS,0);input.mouse.invoke(window.handle(),0,GLFW_RELEASE,0);input.key.invoke(window.handle(),GLFW_KEY_A,0,GLFW_PRESS,GLFW_MOD_CONTROL);text.codePoints().forEach(c->input.character.invoke(window.handle(),c));if(enter)input.key.invoke(window.handle(),GLFW_KEY_ENTER,0,GLFW_PRESS,0);window.frame();}
    private String editText(SceneNode field){final String[] value={null};window.domain.execute(()->value[0]=((SceneCompositionTarget)field.control()).compositionState().snapshot().get().text);return value[0];}
    private MediaSnapshot snapshot(){final MediaSnapshot[] value={null};window.domain.execute(()->DesktopTestInput.geometry(window).ordered().stream().filter(e->e.node.control() instanceof SceneVideo).findFirst().ifPresent(e->value[0]=SceneVideo.snapshot(e.node).get()));return value[0];}
    private long sessionId(){return window.lastPlan.commands().stream().filter(c->c.kind==PaintCommand.Kind.VIDEO).map(c->c.video.frame.key.sessionId).findFirst().orElse(-1L);}
    private boolean text(String value){return window.lastPlan.commands().stream().anyMatch(c->c.kind==PaintCommand.Kind.TEXT&&c.text.logicalText.contains(value));}
    private void await(BooleanSupplier condition,long millis,String reason)throws Exception{long until=System.nanoTime()+millis*1_000_000L;while(!condition.getAsBoolean()&&System.nanoTime()<until){window.frame();MediaSnapshot s=snapshot();if(s!=null&&s.failure!=null)throw new AssertionError(s.failure.stage,s.failure.cause);Thread.sleep(5);}if(!condition.getAsBoolean()){System.out.println(window.lastPlan.commands().stream().filter(c->c.kind==PaintCommand.Kind.TEXT).map(c->c.text.logicalText).collect(Collectors.joining(" | ")));ImageIO.write(DesktopTestReadback.readFront(window),"png",output.resolve("failure.png").toFile());}require(condition.getAsBoolean(),reason);}
}
