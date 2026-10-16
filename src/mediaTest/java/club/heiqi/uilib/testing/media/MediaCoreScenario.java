package club.heiqi.uilib.testing.media;

import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.MediaState;
import club.heiqi.uilib.ui.media.MediaRange;
import club.heiqi.uilib.ui.media.MediaInfo;
import club.heiqi.uilib.ui.media.MediaVideoFrame;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaPresentation;
import club.heiqi.uilib.ui.media.MediaFrameLease;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.media.spi.MediaCancellation;
import club.heiqi.uilib.ui.media.spi.MediaPcm;
import club.heiqi.uilib.ui.media.spi.MediaAudioSinkFactory;
import club.heiqi.uilib.ui.media.spi.MediaAudioPosition;
import club.heiqi.uilib.ui.media.spi.MediaAudioSink;
import club.heiqi.uilib.ui.media.spi.MediaDecoded;
import club.heiqi.uilib.ui.media.spi.MediaDecoder;
import club.heiqi.uilib.ui.media.spi.MediaDecoderFactory;

import club.heiqi.uilib.testing.TestApplication;


import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 无native/classpath下通过正式SPI检验时间线、预算和迟到操作；不声称设备/GPU证据。 */
public final class MediaCoreScenario extends TestApplication {
    private static final MediaSource SOURCE=MediaSource.of(URI.create("test://media/current"));
    private static final MediaAudioSinkFactory NO_AUDIO=b->{throw new AssertionError("纯视频不应打开设备");};
    @Override protected void runTests() throws Exception {
        scenario("借用像素在发布边界冻结且回执去重",this::immutable);
        scenario("暂停背压与会话预算",this::bounded);
        scenario("旧代迟到不污染新来源",this::late);
        scenario("原始失败与释放suppressed及重复关闭",this::cleanup);
        scenario("读K后推进K加一仍接受K提交且租约有界",this::receiptRace);
        scenario("阻塞设备打开不持有共同状态锁",this::deviceGate);
        scenario("设备打开在途静音后play首次输出使用最新增益",this::mutedOpen);
        scenario("已知与动态尺寸超过预算立即失败",this::oversized);
        scenario("低帧率在未消费时长预算内持续推进",this::lowRate);
        scenario("VFR延后头帧按实际时长资格唤醒",this::vfrWake);
        scenario("合法闭区间末端定位以EOF结束",this::endpoint);
        scenario("暂停定位覆盖目标或首目标后帧回执后结算预览",this::pausedSeekPreview);
        scenario("确认EOF才推进较长视频尾部",this::audioTail);
        scenario("顺序SPI长视频尾部与借用帧延后有界冻结",this::longAudioTail);
        scenario("顺序SPI超过raw延后预算明确失败",this::rawDeferredLimit);
        scenario("暂停与结束不轮询空闲设备",this::idle);
    }
    private static MediaInfo videoInfo() {return new MediaInfo(true,true,false,2_000_000_000L,new MediaRange(0,2_000_000_000L),null,2,2);}
    private static final class Frames implements MediaDecoder {
        final byte[] borrowed=new byte[16]; final MediaCancellation cancellation; long index;
        Frames(MediaCancellation cancellation) {this.cancellation=cancellation;}
        public MediaInfo info(){return videoInfo();}
        public MediaDecoded read(){
            cancellation.check(); if(index==20) return null;
            java.util.Arrays.fill(borrowed,(byte)++index);
            return MediaDecoded.video((index-1)*100_000_000L,100_000_000L,2,2,ByteBuffer.wrap(borrowed));
        }
        public void seek(long nanos){index=nanos/100_000_000L;}
        public void close(){java.util.Arrays.fill(borrowed,(byte)99);}
    }
    private void immutable() throws Exception {
        MediaService service=own(new MediaService((s,c)->new Frames(c),NO_AUDIO,MediaBudget.DEFAULT));
        MediaSession session=service.open(SOURCE,false);
        MediaSnapshot s=MediaTestSupport.await(session,x->x.video!=null,2000); MediaVideoFrame old=s.video;
        byte value=old.pixels().get(0); Thread.sleep(50); require(old.pixels().get(0)==value,"借用writer泄漏到快照");
        MediaPresentation receipt=service.presented(old.key,System.nanoTime());
        require(receipt!=null && service.presented(old.key,System.nanoTime())==null,"同帧回执重复计数");
        require(session.snapshot().statistics.presentedFrames==1,"已确认帧计数错误");
        session.seek(1_000_000_000L); MediaSnapshot sought=MediaTestSupport.await(session,x->x.video!=null && x.generation!=s.generation,2000);
        require(service.presented(old.key,System.nanoTime())==null,"过期回执被接收");
        require(sought.video.presentationNanos==1_000_000_000L,"公开定位时间线错误");
        session.close(); require(old.pixels().get(0)==value,"关闭改变旧计划像素");
    }
    private void bounded() throws Exception {
        MediaBudget budget=new MediaBudget(1,3,48,300_000_000L,2,4096,100_000_000L,2,4096,100_000_000L);
        MediaService service=own(new MediaService((s,c)->new Frames(c),NO_AUDIO,budget)); MediaSession session=service.open(SOURCE,false);
        MediaTestSupport.await(session,x->x.statistics.peakVideoFrames==3,2000); Thread.sleep(100);
        MediaSnapshot s=session.snapshot(); MediaTestSupport.budget(s.statistics,budget);
        boolean full=false; try{service.open(SOURCE,false);}catch(IllegalStateException expected){full=true;}
        require(full && s.statistics.decodedVideoFrames==3,"暂停没有背压或会话预算失效");
        session.play(); MediaTestSupport.await(session,x->x.positionNanos>300_000_000L,2000);
    }
    private void late() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1); AtomicInteger closed=new AtomicInteger();
        MediaDecoderFactory factory=(s,c)->{
            if(s.uri.getPath().equals("/current")) return new MediaDecoder(){
                public MediaInfo info(){return videoInfo();}
                public MediaDecoded read() throws Exception {entered.countDown(); release.await(); return MediaDecoded.video(0,100_000_000L,2,2,ByteBuffer.wrap(new byte[16]));}
                public void seek(long n){} public void close(){closed.incrementAndGet();}
            };
            return new Frames(c);
        };
        MediaService service=own(new MediaService(factory,NO_AUDIO,MediaBudget.DEFAULT)); MediaSession session=service.open(SOURCE,false);
        require(entered.await(2,TimeUnit.SECONDS),"旧操作未进入真实等待边界");
        long gen=session.snapshot().generation; session.source(MediaSource.of(URI.create("test://media/new"))); release.countDown();
        MediaSnapshot s=MediaTestSupport.await(session,x->x.video!=null,2000);
        require(s.generation==gen+1 && s.video.key.generation==gen+1 && s.video.pixels().get(0)==1 && closed.get()==1,"迟到旧代进入新快照");
    }
    private void cleanup() throws Exception {
        Exception original=new Exception("original decode failure"),closing=new Exception("decoder close failure");
        AtomicInteger closes=new AtomicInteger();
        MediaService service=new MediaService((s,c)->new MediaDecoder(){
            public MediaInfo info(){return videoInfo();}
            public MediaDecoded read() throws Exception {throw original;}
            public void seek(long n){} public void close() throws Exception {closes.incrementAndGet();throw closing;}
        },NO_AUDIO,MediaBudget.DEFAULT);
        MediaSession session=service.open(SOURCE,false); long end=System.nanoTime()+2_000_000_000L;
        while(session.snapshot().state!=MediaState.FAILED && System.nanoTime()<end) Thread.sleep(5);
        require(session.snapshot().failure.cause==original,"首错身份被覆盖");
        require(original.getSuppressed().length==1 && original.getSuppressed()[0]==closing,"释放suppressed丢失");
        boolean cleanupReported=false; try{session.close();}catch(IllegalStateException expected){cleanupReported=expected.getCause()==closing;}
        session.close(); service.close();
        require(cleanupReported && closes.get()==1 && service.activeSessions()==0,"异常清理未完成或重复释放");
    }
    private void receiptRace() throws Exception {
        MediaService service=own(new MediaService((s,c)->new Frames(c),NO_AUDIO,MediaBudget.DEFAULT));
        MediaSession session=service.open(SOURCE,false);
        MediaVideoFrame first=MediaTestSupport.await(session,s->s.video!=null,2000).video;
        session.play();
        MediaTestSupport.await(session,s->s.video!=null&&!s.video.key.equals(first.key),2000);
        MediaPresentation oldReceipt=service.presented(first.key,System.nanoTime());
        require(oldReceipt!=null && oldReceipt.videoPresentationNanos==first.presentationNanos,"current前移抹掉已提交K");
        require(service.presented(first.key,System.nanoTime())==null,"迟到K重复计数");
        MediaVideoFrame held=session.snapshot().video;
        MediaFrameLease[] pins=new MediaFrameLease[MediaBudget.DEFAULT.presentationLeases];
        for(int i=0;i<pins.length;i++) {pins[i]=service.retainPresentation(held.key);require(pins[i]!=null,"合法呈现租约未取得");}
        require(session.retainPresentation(held.key)==null,"呈现租约超过有限预算");
        Thread.sleep(1150);
        require(service.presented(held.key,System.nanoTime())!=null,"持有中的K被时间宽限裁掉");
        for(MediaFrameLease pin:pins){pin.close();pin.close();}
        MediaFrameLease invalidated=session.retainPresentation(session.snapshot().video.key);
        require(invalidated!=null && invalidated.active(),"当前代租约未激活");
        session.seek(0);
        require(!invalidated.active() && service.presented(invalidated.key,System.nanoTime())==null,"切代未使旧租约/回执失效");
        invalidated.close();
    }
    private void deviceGate() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicInteger queues=new AtomicInteger(),closed=new AtomicInteger();
        MediaAudioSinkFactory gated=b->{
            entered.countDown();release.await();
            return new MediaAudioSink(){
                public boolean queue(MediaPcm p){queues.incrementAndGet();return true;}
                public void playing(boolean p){}public void gain(double g){}public void reset(long g,long p){}
                public MediaAudioPosition position(){return new MediaAudioPosition(0,System.nanoTime(),0,0,0,0,0,false);}
                public String deviceDescription(){return "gated SPI";}public void close(){closed.incrementAndGet();}
            };
        };
        MediaDecoderFactory decoders=(s,c)->s.uri.getPath().equals("/new")?new Frames(c):new MediaDecoder(){
            public MediaInfo info(){return new MediaInfo(true,false,true,1_000_000_000L,new MediaRange(0,1_000_000_000L),null,0,0);}
            public MediaDecoded read(){return MediaDecoded.audio(0,48000,1,ByteBuffer.allocate(2048));}
            public void seek(long p){}public void close(){}
        };
        MediaService service=new MediaService(decoders,gated,MediaBudget.DEFAULT);MediaSession session=service.open(SOURCE,false);
        try{
            require(entered.await(2,TimeUnit.SECONDS),"设备未进入阻塞边界");
            java.util.concurrent.CompletableFuture<Void> control=java.util.concurrent.CompletableFuture.runAsync(()->{
                session.snapshot();session.pause();session.source(MediaSource.of(URI.create("test://media/new")));session.snapshot();
            });
            try{control.get(200,TimeUnit.MILLISECONDS);}finally{release.countDown();}
            MediaTestSupport.await(session,s->s.video!=null,2000);
            require(queues.get()==0 && closed.get()==1,"迟到的设备打开仍排队旧代输出或未释放");
        }finally{release.countDown();service.close();}
    }
    private void mutedOpen() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),played=new CountDownLatch(1);
        List<String> order=Collections.synchronizedList(new ArrayList<>());double[] firstGain={Double.NaN};
        MediaDecoderFactory decoder=(source,cancel)->new MediaDecoder(){int next;
            public MediaInfo info(){return new MediaInfo(true,false,true,200_000_000L,new MediaRange(0,200_000_000L),null,0,0);}
            public MediaDecoded read(){cancel.check();return next<10?MediaDecoded.audio(next++*20_000_000L,48000,2,ByteBuffer.allocate(3840)):null;}
            public void seek(long position){}public void close(){}
        };
        MediaAudioSinkFactory audio=budget->{
            order.add("open.enter");entered.countDown();
            if(!release.await(2,TimeUnit.SECONDS))throw new AssertionError("设备打开gate未释放");
            order.add("open.return");
            return new MediaAudioSink(){double gain=Double.NaN;boolean playing;int count;long bytes,nanos,end;
                public boolean queue(MediaPcm pcm){if(count==budget.deviceBuffers)return false;count++;bytes+=pcm.bytes();nanos+=pcm.durationNanos;end=pcm.presentationNanos+pcm.durationNanos;return true;}
                public void playing(boolean value){playing=value;order.add("playing="+value+" gain="+gain);if(value&&Double.isNaN(firstGain[0])){firstGain[0]=gain;played.countDown();}}
                public void gain(double value){gain=value;order.add("gain="+value);}
                public void reset(long generation,long position){order.add("reset");}
                public MediaAudioPosition position(){return new MediaAudioPosition(0,System.nanoTime(),0,bytes,nanos,end,count,playing);}
                public String deviceDescription(){return "pure SPI; no device";}public void close(){}
            };
        };
        MediaService service=own(new MediaService(decoder,audio,MediaBudget.DEFAULT));MediaSession session=service.open(SOURCE,false);
        try{
            require(entered.await(2,TimeUnit.SECONDS),"设备没有进入open在途边界");
            session.muted(true);order.add("muted.return");session.play();order.add("play.return");release.countDown();
            require(played.await(2,TimeUnit.SECONDS),"未按公开play意图开始输出");
            require(firstGain[0]==0&&session.snapshot().muted,"静音已返回但首次play仍用旧增益");
            System.out.println("MUTED_OPEN firstPlayGain="+firstGain[0]+" order="+order);
        }finally{release.countDown();session.close();}
    }
    private void oversized() throws Exception {
        MediaBudget budget=new MediaBudget(1,3,16,300_000_000L,2,4096,100_000_000L,2,4096,100_000_000L);
        int[][] inputs={{3,2,0},{1,2,1},{2,2,0},{Integer.MAX_VALUE,Integer.MAX_VALUE,0}};
        for(int[] input:inputs){
            AtomicInteger reads=new AtomicInteger(),closes=new AtomicInteger();
            MediaService service=own(new MediaService((s,c)->new MediaDecoder(){
                public MediaInfo info(){
                    int width=input[2]==1&&reads.get()>0?3:input[0];
                    return new MediaInfo(true,true,false,-1,null,null,width,input[1]);
                }
                public MediaDecoded read(){reads.incrementAndGet();return MediaDecoded.YIELD;}
                public void seek(long position){}public void close(){closes.incrementAndGet();}
            },NO_AUDIO,budget));
            MediaSession session=service.open(SOURCE,false);
            MediaSnapshot failed=MediaTestSupport.await(session,s->s.state==MediaState.FAILED,1000);
            require(failed.failure!=null&&failed.failure.cause.getMessage().contains("超过预算"),"超预算尺寸没有正式失败");
            require(reads.get()==input[2]&&closes.get()==1,"已知/动态尺寸资格检查太迟或decoder未释放");
            System.out.println("EXPECTED_SIZE_FAILURE width="+input[0]+" height="+input[1]+" dynamic="+input[2]+" reads="+reads.get()+" stage="+failed.failure.stage);
            session.close();
        }
    }
    private static MediaDecoder slowVideo(long step,int count){
        return new MediaDecoder(){
            int index;
            public MediaInfo info(){return new MediaInfo(true,true,false,step*count,new MediaRange(0,step*count),null,2,2);}
            public MediaDecoded read(){return index==count?null:MediaDecoded.video(index++*step,step,2,2,ByteBuffer.allocate(16));}
            public void seek(long nanos){index=(int)(nanos/step);}public void close(){}
        };
    }
    private void lowRate() throws Exception {
        MediaService service=own(new MediaService((s,c)->slowVideo(200_000_000L,3),NO_AUDIO,MediaBudget.DEFAULT));
        MediaSession session=service.open(SOURCE,true);
        MediaSnapshot ended=MediaTestSupport.await(session,s->s.state==MediaState.ENDED,2000);
        require(ended.positionNanos==600_000_000L&&ended.statistics.decodedVideoFrames==3,"5fps被当前完整帧时长永久背压");
        MediaTestSupport.budget(ended.statistics,MediaBudget.DEFAULT);
        System.out.println("LOW_RATE position="+ended.positionNanos+" decoded="+ended.statistics.decodedVideoFrames+" remainingPeak="+ended.statistics.peakVideoNanos);
    }
    private void vfrWake() throws Exception {
        MediaService service=own(new MediaService((s,c)->new MediaDecoder(){
            int index;
            public MediaInfo info(){return new MediaInfo(true,true,false,350_000_000L,new MediaRange(0,350_000_000L),null,2,2);}
            public MediaDecoded read(){
                c.check();int i=index++;
                return i==0?MediaDecoded.video(0,100_000_000L,2,2,ByteBuffer.allocate(16))
                        :i==1?MediaDecoded.video(100_000_000L,250_000_000L,2,2,ByteBuffer.allocate(16)):null;
            }
            public void seek(long p){}public void close(){}
        },NO_AUDIO,MediaBudget.DEFAULT));
        MediaSession session=service.open(SOURCE,false);
        MediaTestSupport.await(session,s->s.statistics.peakDeferredDecodedNanos==250_000_000L,1000);
        session.play();
        MediaSnapshot ended=MediaTestSupport.await(session,s->s.state==MediaState.ENDED,1500);
        require(ended.positionNanos==350_000_000L&&ended.statistics.decodedVideoFrames==2,"VFR延后头帧仍等待上一帧估算的错误唤醒");
        require(ended.statistics.peakDeferredDecodedNanos==250_000_000L,"没有覆盖实际250ms延后头帧");
        require(ended.statistics.presentedFrames==0,"VFR消费伪造了呈现");
        MediaTestSupport.budget(ended.statistics,MediaBudget.DEFAULT);MediaTestSupport.print("VFR_WAKE",ended);
    }
    private void endpoint() throws Exception {
        MediaService service=own(new MediaService((s,c)->slowVideo(50_000_000L,3),NO_AUDIO,MediaBudget.DEFAULT));
        for(boolean autoplay:new boolean[]{false,true}){
            MediaSession session=service.open(SOURCE,autoplay);MediaTestSupport.await(session,s->s.info.ready,2000);
            session.seek(150_000_000L);MediaSnapshot ended=MediaTestSupport.await(session,s->s.state==MediaState.ENDED,2000);
            require(ended.positionNanos==150_000_000L&&ended.video==null&&ended.statistics.presentedFrames==0,"末端EOF伪造画面或呈现");
            System.out.println("ENDPOINT autoplay="+autoplay+" position="+ended.positionNanos+" presented="+ended.statistics.presentedFrames);session.close();
        }
    }
    private void pausedSeekPreview() throws Exception {
        for(long offset:new long[]{-12_000_000L,10_000_000L}){
            MediaService service=own(new MediaService((s,c)->new MediaDecoder(){
                int index;boolean sought;
                public MediaInfo info(){return new MediaInfo(true,true,true,2_000_000_000L,new MediaRange(0,2_000_000_000L),null,2,2);}
                public MediaDecoded read(){
                    c.check();int i=index++;long target=sought?1_000_000_000L:0;
                    if(i==0)return MediaDecoded.audio(target,48000,1,ByteBuffer.allocate(19200));
                    if(i==1)return MediaDecoded.video(sought?target+offset:0,33_333_333L,2,2,ByteBuffer.allocate(16));
                    return null;
                }
                public void seek(long p){sought=true;index=0;}public void close(){}
            },b->new HonestSink(),MediaBudget.DEFAULT));
            MediaSession session=service.open(SOURCE,false);
            MediaTestSupport.await(session,s->s.video!=null&&s.outputReady,2000);
            long oldGeneration=session.snapshot().generation;session.seek(1_000_000_000L);
            MediaSnapshot preview=MediaTestSupport.await(session,s->s.generation==oldGeneration+1&&s.video!=null&&s.outputReady,2000);
            require(preview.state==MediaState.SEEKING&&!preview.playIntent,"正式回执之前错误结算暂停定位");
            MediaVideoFrame frame=preview.video;
            require(frame.presentationNanos==1_000_000_000L+offset&&frame.presentationNanos+frame.durationNanos>1_000_000_000L,"未命中目标覆盖/首目标后帧");
            MediaPresentation receipt=service.presented(frame.key,System.nanoTime());
            require(receipt!=null&&receipt.videoPresentationNanos==frame.presentationNanos,"正式presented接口没有接收预览帧");
            MediaSnapshot paused=MediaTestSupport.await(session,s->s.state==MediaState.PAUSED,1000);
            require(!paused.playIntent&&paused.positionNanos==1_000_000_000L&&paused.video.key.equals(frame.key),"预览结算擅自播放或改变目标");
            Thread.sleep(50);require(session.snapshot().positionNanos==paused.positionNanos,"暂停预览结算后时间未冻结");
            System.out.println("PAUSED_SEEK pts="+frame.presentationNanos+" duration="+frame.durationNanos+" state="+paused.state+" pos="+paused.positionNanos+" presented="+paused.statistics.presentedFrames);
            session.close();
        }
    }
    private static MediaDecoder mixed(boolean video,CountDownLatch beforeEof,CountDownLatch release){
        return new MediaDecoder(){
            int index;
            public MediaInfo info(){long end=video?240_000_000L:200_000_000L;return new MediaInfo(true,video,true,end,new MediaRange(0,end),null,video?2:0,video?2:0);}
            public MediaDecoded read() throws Exception {
                if(index++==0)return MediaDecoded.audio(0,48000,1,ByteBuffer.allocate(19200));
                if(video&&index<=4)return MediaDecoded.video((index-2)*80_000_000L,80_000_000L,2,2,ByteBuffer.allocate(16));
                if(beforeEof!=null){beforeEof.countDown();release.await();}return null;
            }
            public void seek(long p){}public void close(){}
        };
    }
    private static final class HonestSink implements MediaAudioSink {
        final AtomicInteger positions=new AtomicInteger(),playingCalls=new AtomicInteger(),gains=new AtomicInteger();
        long last,end,anchor,base;boolean playing;
        void update(){if(playing)last=Math.min(end,base+System.nanoTime()-anchor);}
        public boolean queue(MediaPcm p){end=p.presentationNanos+p.durationNanos;return true;}
        public void playing(boolean value){playingCalls.incrementAndGet();update();if(value!=playing){playing=value;base=last;anchor=System.nanoTime();}}
        public void gain(double value){gains.incrementAndGet();}public void reset(long g,long p){last=base=end=p;playing=false;}
        public MediaAudioPosition position(){positions.incrementAndGet();update();boolean queued=last<end;return new MediaAudioPosition(last,System.nanoTime(),0,queued?19200:0,queued?200_000_000L:0,end,queued?1:0,playing&&queued);}
        public String deviceDescription(){return "公开SPI时钟反例，不是native";}public void close(){}
    }
    private void audioTail() throws Exception {
        CountDownLatch beforeEof=new CountDownLatch(1),release=new CountDownLatch(1);HonestSink sink=new HonestSink();
        MediaService service=new MediaService((s,c)->mixed(true,beforeEof,release),b->sink,MediaBudget.DEFAULT);
        try{
            MediaSession session=service.open(SOURCE,true);require(beforeEof.await(2,TimeUnit.SECONDS),"未到EOF判定边界");
            MediaSnapshot gap=MediaTestSupport.await(session,s->s.positionNanos==200_000_000L&&s.state==MediaState.BUFFERING,2000);
            Thread.sleep(100);require(session.snapshot().positionNanos==gap.positionNanos,"临时音频欠供被当作EOF视频尾部");
            release.countDown();MediaSnapshot ended=MediaTestSupport.await(session,s->s.state==MediaState.ENDED,2000);
            require(ended.positionNanos==240_000_000L&&ended.statistics.presentedFrames==0,"音频先EOF后的合法视频尾部未完成");
            System.out.println("AUDIO_TAIL gapPosition="+gap.positionNanos+" end="+ended.positionNanos);
        }finally{release.countDown();service.close();}
    }
    private void longAudioTail() throws Exception {
        HonestSink sink=new HonestSink();
        MediaService service=own(new MediaService((s,c)->new MediaDecoder(){
            int index;final byte[] borrowed=new byte[16];
            public MediaInfo info(){return new MediaInfo(true,true,true,640_000_000L,new MediaRange(0,640_000_000L),null,2,2);}
            public MediaDecoded read(){
                c.check();if(index++==0)return MediaDecoded.audio(0,48000,1,ByteBuffer.allocate(19200));
                if(index<=9){java.util.Arrays.fill(borrowed,(byte)(index-1));return MediaDecoded.video((index-2)*80_000_000L,80_000_000L,2,2,ByteBuffer.wrap(borrowed));}
                java.util.Arrays.fill(borrowed,(byte)99);return null;
            }
            public void seek(long p){}public void close(){java.util.Arrays.fill(borrowed,(byte)100);}
        },b->sink,MediaBudget.DEFAULT));
        MediaSession session=service.open(SOURCE,true);
        MediaSnapshot ended=MediaTestSupport.await(session,s->s.state==MediaState.ENDED,2000);
        require(ended.positionNanos==640_000_000L&&ended.statistics.decodedVideoFrames==8,"长于raw窗口的视频尾部仍循环等待输入EOF");
        require(ended.video.pixels().get(0)==8,"顺序SPI延后项仍引用下一次read改写的借用像素");
        require(ended.statistics.peakDeferredDecodedItems>0,"没有实际覆盖独立raw延后存储");
        MediaTestSupport.budget(ended.statistics,MediaBudget.DEFAULT);MediaTestSupport.print("LONG_AUDIO_TAIL",ended);
    }
    private void rawDeferredLimit() throws Exception {
        HonestSink sink=new HonestSink();AtomicInteger closed=new AtomicInteger();
        MediaService service=own(new MediaService((s,c)->new MediaDecoder(){
            int index;
            public MediaInfo info(){return new MediaInfo(true,true,true,3_200_000_000L,new MediaRange(0,3_200_000_000L),null,2,2);}
            public MediaDecoded read(){c.check();if(index++==0)return MediaDecoded.audio(0,48000,1,ByteBuffer.allocate(19200));
                return index<=41?MediaDecoded.video((index-2)*80_000_000L,80_000_000L,2,2,ByteBuffer.allocate(16)):null;}
            public void seek(long p){}public void close(){closed.incrementAndGet();}
        },b->sink,MediaBudget.DEFAULT));
        MediaSession session=service.open(SOURCE,false);
        MediaSnapshot failed=MediaTestSupport.await(session,s->s.state==MediaState.FAILED,2000);
        require(failed.failure.cause.getMessage().contains("raw延后存储超过明确预算"),"顺序SPI超限未给出可定位错误");
        session.close();require(closed.get()==1,"顺序SPI超限未释放所属decoder");
        MediaTestSupport.budget(failed.statistics,MediaBudget.DEFAULT);
        System.out.println("RAW_LIMIT cause="+failed.failure.cause+" actualPeak="+failed.statistics.peakDeferredDecodedNanos);
    }
    private void idle() throws Exception {
        HonestSink sink=new HonestSink();MediaService service=own(new MediaService((s,c)->mixed(false,null,null),b->sink,MediaBudget.DEFAULT));
        MediaSession session=service.open(SOURCE,false);MediaTestSupport.await(session,s->s.state==MediaState.PAUSED,2000);
        idleCounts("PAUSED",sink);session.play();MediaTestSupport.await(session,s->s.state==MediaState.ENDED,2000);idleCounts("ENDED",sink);
    }
    private void idleCounts(String state,HonestSink sink) throws Exception {
        int positions=sink.positions.get(),playing=sink.playingCalls.get(),gains=sink.gains.get();Thread.sleep(180);
        int p=sink.positions.get()-positions,c=sink.playingCalls.get()-playing,g=sink.gains.get()-gains;
        require(p==0&&c==0&&g==0,"空闲设备仍无期限native轮询 "+state+" "+p+"/"+c+"/"+g);
        System.out.println("IDLE state="+state+" positionCalls="+p+" playingCalls="+c+" gainCalls="+g);
    }
    public static void main(String[] args) throws Exception {new MediaCoreScenario().run(args);}
}
