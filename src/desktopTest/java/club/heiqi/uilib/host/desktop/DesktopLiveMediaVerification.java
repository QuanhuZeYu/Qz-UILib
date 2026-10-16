package club.heiqi.uilib.host.desktop;

import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.MediaState;
import club.heiqi.uilib.ui.media.MediaVideoFrame;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaPresentation;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.host.media.IsolatedMediaAdapters;

import club.heiqi.uilib.ui.scene.control.SceneVideo;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** 只消费正式DesktopWindow/SceneVideo和媒体会话；不自行绘制或伪造呈现回执。 */
public final class DesktopLiveMediaVerification extends TestApplication {
    private final List<MediaPresentation> samples=new ArrayList<>();
    private final StringBuilder observations=new StringBuilder("stage,mono,gen,state,position,start,end,width,height,presented\n");
    private Path output;private MediaSession session;private SceneNode measuredVideo;
    private final StringBuilder sizeTrace=new StringBuilder("mono,key,pts,frameWidth,frameHeight,boxWidth,boxHeight,swapped\n");
    private void frame(DesktopWindow window){
        window.frame();if(samples.size()>8192)throw new IllegalStateException("短验证回执预算");samples.addAll(window.mediaPresentations());
        if(measuredVideo!=null){
            club.heiqi.uilib.ui.scene.layout.LayoutBox box=DesktopTestInput.geometry(window,measuredVideo).box;
            for(club.heiqi.uilib.ui.scene.paint.PaintCommand command:window.lastPlan.commands())if(command.kind==club.heiqi.uilib.ui.scene.paint.PaintCommand.Kind.VIDEO){
                MediaVideoFrame f=command.video.frame;boolean swapped=false;for(MediaPresentation p:window.mediaPresentations())if(p.key.equals(f.key))swapped=true;
                if(sizeTrace.length()>600000)throw new IllegalStateException("尺寸trace预算");
                sizeTrace.append(System.nanoTime()).append(',').append(f.key).append(',').append(f.presentationNanos).append(',').append(f.width).append(',').append(f.height).append(',').append(box.width).append(',').append(box.height).append(',').append(swapped).append('\n');
                require(box.width!=320||box.height==(int)Math.round(320d*f.height/f.width),"正式plan旧尺寸帧对应了提前变化的布局: key="+f.key+" frame="+f.width+"x"+f.height+" box="+box.width+"x"+box.height);
            }
        }
    }
    private void state(String stage){MediaSnapshot s=session.snapshot();observations.append(stage).append(',').append(System.nanoTime()).append(',').append(s.generation).append(',').append(s.state).append(',').append(s.positionNanos).append(',').append(s.info.seekable==null?-1:s.info.seekable.startNanos).append(',').append(s.info.seekable==null?-1:s.info.seekable.endNanos).append(',').append(s.info.width).append(',').append(s.info.height).append(',').append(s.statistics.presentedFrames).append('\n');System.out.println("LIVE "+stage+" gen="+s.generation+" state="+s.state+" position="+s.positionNanos+" range="+(s.info.seekable==null?"null":s.info.seekable.startNanos+".."+s.info.seekable.endNanos)+" failure="+(s.failure==null?"none":s.failure.stage+":"+s.failure.cause));}
    private void await(DesktopWindow window,BooleanSupplier condition,String reason)throws Exception{
        long until=System.nanoTime()+8_000_000_000L;while(!condition.getAsBoolean()&&System.nanoTime()<until){frame(window);MediaSnapshot s=session.snapshot();if(s.failure!=null)throw new AssertionError(reason+": "+s.failure.stage,s.failure.cause);Thread.sleep(5);}if(!condition.getAsBoolean())state("timeout");require(condition.getAsBoolean(),reason);
    }
    private void runFor(DesktopWindow window,long millis,boolean allowFailure)throws Exception{long end=System.nanoTime()+millis*1_000_000L,next=0;while(System.nanoTime()<end){frame(window);if(!allowFailure&&session.snapshot().failure!=null)throw new AssertionError("正式live失败",session.snapshot().failure.cause);if(System.nanoTime()>=next){state("sample");next=System.nanoTime()+1_000_000_000L;}Thread.sleep(5);}}
    private static long target(MediaSnapshot s){return Math.max(s.info.seekable.startNanos,s.info.seekable.endNanos-4_000_000_000L);}
    @Override protected void runTests()throws Exception{
        output=Paths.get(arguments()[1]);Files.createDirectories(output);
        try{
        scenario("正式live跨初始窗口、定位、过期暂停与来源独立",()->{
            MediaService service=own(new MediaService(IsolatedMediaAdapters.decoderFactory(),IsolatedMediaAdapters.audioSinkFactory(),MediaBudget.DEFAULT));
            MediaSource source=MediaSource.of(URI.create(arguments()[0]));session=service.open(source,false);session.muted(true);
            DesktopWindow window=own(DesktopWindow.openWithServices(output.resolve("live.yaml"),service,false,services->page->{page.own(session);page.mountOwned(SceneVideo.create(page,session).size(Size.fill(1),Size.fill(1)));}));
            await(window,()->session.snapshot().video!=null&&session.snapshot().statistics.presentedFrames>0,"live首帧未实际交换");
            MediaSnapshot initial=session.snapshot();require(!initial.info.durationKnown()&&initial.info.seekable!=null,"live未知时长/有限窗口错误");state("initial");
            session.play();runFor(window,20_000,false);MediaSnapshot played=session.snapshot();
            require(played.info.seekable.startNanos>initial.info.seekable.startNanos&&played.positionNanos>initial.info.seekable.endNanos,"未跨初始窗口持续输出");
            long gen=played.generation,seek=target(played);session.seek(seek);
            await(window,()->session.snapshot().generation>gen&&session.snapshot().state==MediaState.PLAYING,"播放seek无真实恢复");state("playing-seek");
            session.pause();await(window,()->session.snapshot().state==MediaState.PAUSED,"live暂停失败");
            long pausedGen=session.snapshot().generation;session.seek(target(session.snapshot())-500_000_000L);
            await(window,()->session.snapshot().generation>pausedGen&&session.snapshot().state==MediaState.PAUSED,"暂停预览seek无真实回执");state("paused-seek");
            long fixed=session.snapshot().positionNanos,oldGen=session.snapshot().generation;
            runFor(window,14_500,true);MediaSnapshot expired=session.snapshot();state("expired");
            require(expired.positionNanos==fixed&&expired.state==MediaState.FAILED&&expired.failure.stage.equals("live-window")&&expired.info.seekable.startNanos>fixed,"过期暂停未明确保留窗口并失败");
            session.play();runFor(window,150,true);require(session.snapshot().generation==oldGen&&session.snapshot().positionNanos==fixed&&session.snapshot().state==MediaState.FAILED,"play自动跳到新窗口");
            long restore=target(session.snapshot());session.seek(restore);await(window,()->session.snapshot().state==MediaState.PLAYING,"显式有效定位未恢复");state("restored");
            long stale=session.snapshot().info.seekable.startNanos-1,before=session.snapshot().generation;boolean rejected=false;try{session.seek(stale);}catch(IllegalArgumentException expected){rejected=true;}require(rejected&&session.snapshot().generation==before,"过期目标更改输出代");
            MediaSession other=service.open(source,false);other.muted(true);
            long until=System.nanoTime()+5_000_000_000L;while(other.snapshot().video==null&&System.nanoTime()<until){frame(window);Thread.sleep(5);}require(other.snapshot().video!=null&&other.snapshot().video.presentationNanos<8_000_000_000L,"同URL第二session未独立从自己的原点开始");
            long otherGeneration=other.snapshot().generation;session.source(source);await(window,()->session.snapshot().video!=null&&session.snapshot().video.presentationNanos<8_000_000_000L,"显式source未建立新来源原点");require(other.snapshot().generation==otherGeneration,"换源污染独立session");other.close();state("new-source");
            session.close();require(service.activeSessions()==0,"live来源会话未释放");
        });
        if(arguments().length>2)scenario("实际帧尺寸改变沿正式view更新",()->{
            MediaService service=own(new MediaService(IsolatedMediaAdapters.decoderFactory(),IsolatedMediaAdapters.audioSinkFactory(),MediaBudget.DEFAULT));session=service.open(MediaSource.of(URI.create(arguments()[2])),false);session.muted(true);
            DesktopWindow window=own(DesktopWindow.openWithServices(output.resolve("sizes.yaml"),service,false,services->page->{page.own(session);measuredVideo=SceneVideo.create(page,session).size(Size.fixed(320),Size.natural());SceneNode root=new SceneNode().axis(SceneNode.Axis.COLUMN).size(Size.fill(1),Size.fill(1));root.add(measuredVideo);page.mountOwned(root);}));
            await(window,()->session.snapshot().statistics.presentedFrames>0,"尺寸源无首帧");require(session.snapshot().info.width==320&&session.snapshot().info.height==180,"初始尺寸不符");state("size-first");session.play();await(window,()->session.snapshot().info.width==640&&session.snapshot().info.height==480&&session.snapshot().video.width==640&&session.snapshot().statistics.presentedFrames>2,"实际尺寸未随frame传播");state("size-changed");frame(window);require(DesktopTestInput.geometry(window,measuredVideo).box.height==240,"新尺寸没有对应320x240正式布局");session.close();measuredVideo=null;
        });
        }finally{
            Files.write(output.resolve("live-observations.csv"),observations.toString().getBytes(StandardCharsets.UTF_8));
            Files.write(output.resolve("size-trace.csv"),sizeTrace.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder csv=new StringBuilder("session,generation,sequence,swapNanos,videoPTS,audioPosition,error,deviceSampleNanos\n");for(MediaPresentation p:samples)csv.append(p.key.sessionId).append(',').append(p.key.generation).append(',').append(p.key.sequence).append(',').append(p.sampledNanos).append(',').append(p.videoPresentationNanos).append(',').append(p.audioPositionNanos).append(',').append(p.synchronizationErrorNanos).append(',').append(p.deviceSampledNanos).append('\n');Files.write(output.resolve("presentation.csv"),csv.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    public static void main(String[] args)throws Exception{new DesktopLiveMediaVerification().run(args);}
}
