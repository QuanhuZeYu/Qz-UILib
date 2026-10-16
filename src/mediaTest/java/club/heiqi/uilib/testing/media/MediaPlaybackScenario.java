package club.heiqi.uilib.testing.media;

import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.MediaState;
import club.heiqi.uilib.ui.media.MediaFrameKey;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.media.spi.MediaPcm;
import club.heiqi.uilib.ui.media.spi.MediaAudioPosition;
import club.heiqi.uilib.ui.media.spi.MediaAudioSink;

import club.heiqi.uilib.testing.TestApplication;


import club.heiqi.uilib.host.media.IsolatedMediaAdapters;
import static club.heiqi.uilib.testing.media.MediaTestSupport.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.function.Predicate;

/** 短真实链路；这里没有 GPU 回放，绝不制造已呈现回执。 */
public final class MediaPlaybackScenario extends TestApplication {
    private String base;
    @Override protected void setup() {
        base=arguments().length==0?"http://127.0.0.1:54423":arguments()[0];
        System.out.println("RUNTIME java="+System.getProperty("java.version")+" os="+System.getProperty("os.name")+" arch="+System.getProperty("os.arch"));
    }
    private MediaSource source(String path) { return MediaSource.of(URI.create(base+path)); }
    private MediaService service() { return new MediaService(IsolatedMediaAdapters.decoderFactory(),IsolatedMediaAdapters.audioSinkFactory(),MediaBudget.DEFAULT); }
    @Override protected void runTests() throws Exception {
        scenario("真实HTTP-HLS像素设备及公开控制",this::playback);
        scenario("OpenAL消费基准跨unqueue及STOPPED",this::deviceClock);
        scenario("404保留原因且仅显式换源重新打开",this::missing);
        scenario("阻塞HTTP原生取消与重复关闭",this::blocked);
        scenario("短重复开关返回所属线程基线",this::repeated);
        if(arguments().length>1) scenario("短点播EOF等待音视频尾部消费",()->ending(arguments()[1],true));
        if(arguments().length>2) scenario("纯音频EOF不伪造视频",()->ending(arguments()[2],false));
        if(arguments().length>3) scenario("真实音频先结束的视频长尾",()->ending(arguments()[3],true));
    }
    private void playback() throws Exception {
        MediaService service=own(service()); MediaSession session=service.open(source("/vod.m3u8"),false);
        session.volume(0); session.muted(true);
        MediaSnapshot ready=await(session,s->s.outputReady && s.video!=null,10_000);
        require(ready.info.ready && ready.info.hasAudio && ready.info.hasVideo && ready.info.durationKnown(),"轨道/时长事实错误");
        require(ready.video.width==1920 && ready.video.height==1080,"未命中固定1080p来源");
        require(ready.video.pixels().remaining()==1920*1080*4,"RGBA字节不符");
        ByteBuffer original=ready.video.pixels(); long checksum=checksum(original);
        require((original.get(3)&255)==255,"普通视频RGBA未归一为不透明像素");
        boolean varied=false; for(int i=4;i<original.remaining();i+=4096) varied|=original.get(i)!=original.get(0);
        require(varied,"实际测试视频像素没有读出内容变化");
        System.out.println("PIXELS bytes="+original.remaining()+" checksum="+checksum+" alpha="+(original.get(3)&255));
        boolean readOnly=false; try { original.put(0,(byte)1); } catch(ReadOnlyBufferException expected) { readOnly=true; }
        require(readOnly,"视频像素不是只读视图");
        session.play(); MediaSnapshot playing=await(session,s->s.positionNanos>=350_000_000L && s.state==MediaState.PLAYING,5000);
        print("PLAY",playing);
        session.pause(); MediaSnapshot paused=await(session,s->s.state==MediaState.PAUSED,2000);
        long frozen=paused.positionNanos; Thread.sleep(180);
        require(session.snapshot().positionNanos==frozen,"暂停后设备时间继续推进");
        require(session.snapshot().generation==ready.generation,"暂停制造了新代");
        session.volume(0.01); session.muted(false); Thread.sleep(30); session.muted(true);
        require(session.snapshot().volume==0.01 && session.snapshot().muted,"音量/静音意图未保留");
        session.play(); await(session,s->s.positionNanos>frozen+150_000_000L,3000);
        require(checksum(ready.video.pixels())==checksum,"旧帧被decoder覆盖");
        long oldGen=session.snapshot().generation; MediaFrameKey stale=session.snapshot().video.key;
        session.seek(3_000_000_000L);
        require(session.snapshot().generation==oldGen+1 && session.snapshot().video==null,"seek未立即使旧画面失效");
        MediaSnapshot sought=await(session,s->s.video!=null && s.video.key.generation==oldGen+1 && s.positionNanos>3_100_000_000L,6000);
        require(sought.video.presentationNanos+sought.video.durationNanos>3_000_000_000L,"定位未预滚至目标");
        require(sought.state==MediaState.SEEKING,"没有正式GPU回执却声称定位恢复");
        require(service.presented(stale,System.nanoTime())==null,"旧代回执污染新代");
        print("SEEK_PREROLL_NO_GPU",sought);
        long gen=sought.generation;
        boolean rejected=false; try {session.seek(-1);}catch(IllegalArgumentException expected){rejected=true;}
        require(rejected && session.snapshot().generation==gen,"无效定位更改代际");
        session.source(source("/vod.m3u8"));
        MediaSnapshot switched=await(session,s->s.video!=null && s.generation==gen+1 && s.positionNanos>100_000_000L,6000);
        require(switched.statistics.presentedFrames==0 && switched.statistics.firstPresentedNanos==0,"解码冒充呈现");
        print("SOURCE",switched); budget(switched.statistics,MediaBudget.DEFAULT);
        session.close(); session.close();
        require(service.activeSessions()==0 && session.snapshot().state==MediaState.CLOSED,"关闭未归还会话");
    }
    private void deviceClock() throws Exception {
        MediaAudioSink sink=own(IsolatedMediaAdapters.audioSinkFactory().open(MediaBudget.DEFAULT));
        sink.reset(17,0); sink.gain(0); System.out.println("DEVICE "+sink.deviceDescription());
        System.out.println("NATIVE "+IsolatedMediaAdapters.resources());
        long previous=0;
        for(int round=0;round<3;round++) {
            for(int i=0;i<4;i++) {
                long pts=(round*4L+i)*50_000_000L;
                require(sink.queue(new MediaPcm(17,pts,48000,1,ByteBuffer.allocate(2400*2))),"短PCM未进入有限设备队列");
            }
            sink.playing(true); long start=System.nanoTime(),deadline=start+3_000_000_000L;
            MediaAudioPosition p=sink.position(); boolean sawUnqueue=false;
            while(p.positionNanos<(round+1)*200_000_000L && System.nanoTime()<deadline) {
                Thread.sleep(5); p=sink.position();
                require(p.positionNanos>=previous,"STOPPED/raw offset归零使时钟倒退");
                require(p.positionNanos<=(round+1)*200_000_000L,"重复累计processed使时钟越过PCM终点");
                previous=p.positionNanos; sawUnqueue|=p.queuedBuffers<4;
                if(round==1 && p.queuedBuffers>0 && p.positionNanos>250_000_000L && sinkPausedAt==0) {
                    sink.playing(false); MediaAudioPosition stopped=sink.position(); sinkPausedAt=stopped.positionNanos;
                    Thread.sleep(80); require(sink.position().positionNanos==sinkPausedAt,"设备暂停没有冻结时钟"); sink.playing(true);
                }
            }
            require(p.positionNanos==(round+1)*200_000_000L && p.queuedBuffers==0 && sawUnqueue,"设备尾部没有全部消费");
            System.out.println("CLOCK round="+round+" endNs="+p.positionNanos+" elapsedNs="+(System.nanoTime()-start)+" latencyNs="+p.outputLatencyNanos+" queued="+p.queuedBuffers);
        }
    }
    private long sinkPausedAt;
    private void missing() throws Exception {
        MediaService service=own(service()); MediaSession session=service.open(source("/missing.m3u8"),false);
        MediaSnapshot failed=await(session,s->s.state==MediaState.FAILED,5000);
        require(failed.failure!=null && (failed.failure.cause.getMessage().contains("avformat_open_input")
                ||failed.failure.cause.getMessage().contains("HLS manifest HTTP 404")&&failed.failure.cause.getCause()!=null),"404原因未保留");
        Throwable original=failed.failure.cause; session.play(); Thread.sleep(100);
        require(session.snapshot().failure.cause==original && session.snapshot().generation==failed.generation,"失败后自动重试");
        System.out.println("EXPECTED_FAILURE source="+failed.failure.source+" stage="+failed.failure.stage+" cause="+original);
        session.muted(true); session.source(source("/vod.m3u8"));
        await(session,s->s.video!=null && s.failure==null,6000);
    }
    private void blocked() throws Exception {
        MediaService service=own(service()); MediaSession session=service.open(source("/blocked"),false);
        Thread.sleep(300); long started=System.nanoTime(); session.close(); long elapsed=System.nanoTime()-started;
        session.close(); require(elapsed<5_000_000_000L && service.activeSessions()==0,"原生阻塞读取未取消");
        System.out.println("CANCEL closeElapsedNs="+elapsed+" state="+session.snapshot().state);
    }
    private void repeated() throws Exception {
        int baseline=workers(); MediaService service=own(service());
        for(int i=0;i<3;i++) {
            MediaSession session=service.open(source("/vod.m3u8"),false); session.muted(true);
            await(session,s->s.video!=null && s.outputReady,6000); session.play();
            await(session,s->s.positionNanos>100_000_000L,3000); session.close();
            require(service.activeSessions()==0 && workers()==baseline,"短开关后worker/会话未归零");
            System.out.println("REOPEN iteration="+i+" active="+service.activeSessions()+" workers="+workers());
        }
    }
    private void ending(String url,boolean video) throws Exception {
        MediaService service=own(service()); MediaSession session=service.open(MediaSource.of(URI.create(url)),false);
        session.volume(0); session.muted(true); await(session,s->s.outputReady,6000); session.play();
        long previous=0,start=System.nanoTime(); MediaSnapshot s=session.snapshot();
        while(s.state!=MediaState.ENDED && System.nanoTime()-start<10_000_000_000L) {
            Thread.sleep(10); s=session.snapshot(); checked(s);
            require(s.positionNanos>=previous,"EOF倒退"); previous=s.positionNanos;
            if(!video) require(s.video==null && !s.info.hasVideo,"纯音频伪造视频");
        }
        require(s.state==MediaState.ENDED && s.positionNanos>2_000_000_000L,"EOF未消费尾部");
        require(s.statistics.presentedFrames==0,"EOF解码冒充GPU呈现"); print("EOF",s); budget(s.statistics,MediaBudget.DEFAULT);
    }
    private static long checksum(ByteBuffer data) { long hash=1; for(int i=0;i<data.remaining();i+=4096) hash=31*hash+(data.get(i)&255); return hash; }
    private static int workers() { int result=0; for(Thread t:Thread.getAllStackTraces().keySet()) if(t.isAlive() && t.getName().startsWith("uilib-media-")) result++; return result; }
    public static void main(String[] args) throws Exception { new MediaPlaybackScenario().run(args); }
}
