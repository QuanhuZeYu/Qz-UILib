package club.heiqi.uilib.ui.media;

import club.heiqi.uilib.ui.media.spi.MediaCancellation;
import club.heiqi.uilib.ui.media.spi.MediaPcm;
import club.heiqi.uilib.ui.media.spi.MediaAudioSinkFactory;
import club.heiqi.uilib.ui.media.spi.MediaAudioPosition;
import club.heiqi.uilib.ui.media.spi.MediaAudioSink;
import club.heiqi.uilib.ui.media.spi.MediaDecodeRequest;
import club.heiqi.uilib.ui.media.spi.MediaDecoderBuffering;
import club.heiqi.uilib.ui.media.spi.MediaDecoded;
import club.heiqi.uilib.ui.media.spi.MediaInputState;
import club.heiqi.uilib.ui.media.spi.MediaDecoder;
import club.heiqi.uilib.ui.media.spi.MediaDecoderFactory;
import club.heiqi.uilib.ui.media.spi.MediaDecoderSource;


import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/** 共同播放权威。两个 owner 线程不进入 UI domain；native 调用不持有状态锁。 */
public final class MediaSession implements AutoCloseable {
    private final Object lock=new Object();
    private final MediaService service;
    public final long sessionId;
    private final MediaDecoderFactory decoders;
    private final MediaAudioSinkFactory audio;
    private final MediaBudget budget;
    private final Thread decoderThread,outputThread;
    private final ArrayDeque<MediaVideoFrame> videos=new ArrayDeque<>();
    private final ArrayDeque<MediaPcm> pcm=new ArrayDeque<>();
    private final ArrayDeque<MediaDecoded> decodedAhead=new ArrayDeque<>();
    private final LinkedHashMap<MediaFrameKey,PresentationRecord> records=new LinkedHashMap<>();
    private final long[] stats=new long[25];
    private volatile boolean closed;
    private volatile long generation=1;
    private Token token=new Token(),sourceToken=new Token();
    private long sourceVersion=1;
    private MediaSource source;
    private MediaInfo info=MediaInfo.UNKNOWN;
    private MediaState state=MediaState.LOADING;
    private MediaFailure failure;
    private Throwable cleanupFailure;
    private MediaVideoFrame current;
    private boolean playIntent,muted,eof,outputReady,seeking,seekPresented,videoTimeBlocked;
    private double volume=1;
    private long target,position,sampled=System.nanoTime(),videoBytes,videoNanos,audioBytes,audioNanos;
    private long resumePositionNanos=-1;
    private long sequence,decodedVideoEnd,lastVideoDuration,lastAudioBytes,lastAudioDuration,aheadBytes,aheadNanos,decodedAudioEnd,audioInputEnd=-1;
    private boolean audioInputEnded;
    private int leases;
    private MediaAudioPosition audioPosition;

    MediaSession(MediaService service,long id,MediaSource source,boolean autoplay,
            MediaDecoderFactory decoders,MediaAudioSinkFactory audio,MediaBudget budget) {
        this.service=service; sessionId=id; this.source=source; playIntent=autoplay;
        this.decoders=decoders; this.audio=audio; this.budget=budget; stats[17]=System.nanoTime();
        decoderThread=new Thread(this::decodeLoop,"uilib-media-decoder-"+id);
        outputThread=new Thread(this::outputLoop,"uilib-media-output-"+id);
        decoderThread.setDaemon(true); outputThread.setDaemon(true);
    }
    void start() {decoderThread.start();outputThread.start();}
    public MediaSnapshot snapshot() {
        synchronized(lock) {
            stats[21]=0; for(PresentationRecord r:records.values()) if(!r.presented) stats[21]++;
            MediaInfo facts=new MediaInfo(info.ready,info.hasVideo,info.hasAudio,info.durationNanos,info.seekable,buffered(),info.width,info.height);
            return new MediaSnapshot(state,source,facts,position,generation,sampled,playIntent,muted,outputReady,
                    volume,current,failure,new MediaStatistics(stats));
        }
    }
    private MediaRange buffered(){
        if(closed)return null;
        long start=0,end=Long.MAX_VALUE;
        if(info.hasVideo){
            MediaVideoFrame first=current==null?videos.peek():current;
            if(first==null)return null;
            start=Math.max(start,first.presentationNanos);end=Math.min(end,decodedVideoEnd);
        }
        if(info.hasAudio){
            long audioEnd=audioPosition==null?0:audioPosition.queuedEndNanos;
            if(!pcm.isEmpty())audioEnd=Math.max(audioEnd,pcm.peekLast().presentationNanos+pcm.peekLast().durationNanos);
            if(audioEnd<=0)return null;
            start=Math.max(start,position);end=Math.min(end,audioEnd);
        }
        return end==Long.MAX_VALUE||end<start?null:new MediaRange(Math.max(0,start),end);
    }
    public void play() {synchronized(lock){
        ensureOpen();
        // 在途刷新可能在意图改成play以后才返回；保留暂停位置，资格确认前不恢复真实输出。
        if(!playIntent&&failure==null&&!seeking&&info.durationNanos<0&&info.seekable!=null)resumePositionNanos=position;
        playIntent=true;lock.notifyAll();
    }}
    public void pause() {synchronized(lock){ensureOpen();playIntent=false;resumePositionNanos=-1;lock.notifyAll();}}
    private boolean outputPlay(){return playIntent&&resumePositionNanos<0;}
    public void volume(double value) {
        if(!Double.isFinite(value)||value<0||value>1) throw new IllegalArgumentException("音量必须为0..1");
        synchronized(lock){ensureOpen();volume=value;lock.notifyAll();}
    }
    public void muted(boolean value) {synchronized(lock){ensureOpen();muted=value;lock.notifyAll();}}
    public void source(MediaSource source) {Objects.requireNonNull(source,"source");synchronized(lock){ensureOpen();change(source,0,false);}}
    /** 在新来源的共同时间线上开始，播放/暂停、音量和静音意图沿用同一会话。 */
    public void source(MediaSource source,long positionNanos) {
        Objects.requireNonNull(source,"source");if(positionNanos<0)throw new IllegalArgumentException("来源起始位置必须非负");
        synchronized(lock){
            ensureOpen();if(positionNanos==0){change(source,0,false);return;}
            change(source,positionNanos,true);
            sourceToken.cancelled=true;sourceToken=new Token();sourceVersion++;info=MediaInfo.UNKNOWN;
        }
    }
    public void seek(long nanos) {
        synchronized(lock){
            ensureOpen(); if(info.seekable==null||!info.seekable.contains(nanos)) throw new IllegalArgumentException("定位目标不在当前可定位范围内");
            change(source,nanos,true);
        }
    }
    private void change(MediaSource source,long target,boolean seek) {
        token.cancelled=true;token=new Token();generation++;clearFrames();
        this.source=source;this.target=target;position=target;resumePositionNanos=-1;sampled=System.nanoTime();
        eof=outputReady=seekPresented=audioInputEnded=false;audioPosition=null;failure=null;seeking=seek;
        decodedVideoEnd=decodedAudioEnd=target;audioInputEnd=-1;lastVideoDuration=lastAudioDuration=lastAudioBytes=0;
        if(!seek){sourceToken.cancelled=true;sourceToken=new Token();sourceVersion++;info=MediaInfo.UNKNOWN;}
        state=seek?MediaState.SEEKING:MediaState.LOADING;lock.notifyAll();
    }
    private void ensureOpen(){if(closed)throw new IllegalStateException("媒体会话已关闭");}
    private boolean valid(long gen,Token operation){return !closed&&generation==gen&&!operation.cancelled;}
    private void clearFrames(){
        stats[4]+=videos.size();for(PresentationRecord r:records.values())if(!r.presented)stats[4]++;
        videos.clear();pcm.clear();decodedAhead.clear();records.clear();current=null;leases=0;videoTimeBlocked=false;
        videoBytes=videoNanos=audioBytes=audioNanos=aheadBytes=aheadNanos=0;
    }
    private void decodeLoop(){new DecoderOwner().run();}

    /** 锁内捕获的一次操作身份；不是另一份可写播放状态，迟到结果仍由 valid 裁决。 */
    private final class DecodeOperation {
        final long gen=generation,seekTarget=target,version=sourceVersion;
        final MediaSource selected=source;
        final Token operation=token,lifetime=sourceToken;
        final boolean selectedSeeking=seeking;
        String stage="open";
    }
    /** 来源句柄跨同来源定位复用，只由原 decoder 线程持有和释放。 */
    private final class DecoderOwner {
        private long seen,handleVersion=-1;
        private MediaDecoderSource handle;
        private boolean refreshFailed;

        void run(){
            try{
                for(;;){
                    DecodeOperation work;
                    synchronized(lock){
                        if(!closed&&seen==generation)waitForChange(refreshFailed?0:sourceWait(handle));
                        if(closed)return;
                        work=new DecodeOperation();
                    }
                    if(seen==work.gen){refresh(work);continue;}
                    seen=work.gen;refreshFailed=false;
                    selectSource(work.version);
                    decode(work);
                }
            }finally{if(handle!=null)try{handle.close();}catch(Throwable error){synchronized(lock){cleanupFailure=MediaService.combine(cleanupFailure,error);recordCleanup(error);}}}
        }
        private void refresh(DecodeOperation work){
            if(refreshFailed)return;
            try{refreshSource(handle,work.version,work.lifetime);}
            catch(Throwable error){
                refreshFailed=true;
                synchronized(lock){if(sourceVersion==work.version&&!work.lifetime.cancelled)fail(work.selected,"source-refresh",error);}
            }
        }
        private void selectSource(long version){
            if(handleVersion==version)return;
            if(handle!=null){try{handle.close();}catch(Throwable error){synchronized(lock){recordCleanup(error);}}handle=null;}
            handleVersion=version;
        }
        private void decode(DecodeOperation work){
            MediaDecoder decoder=null;Throwable problem=null;
            try{
                if(handle==null)handle=decoders.openSource(work.selected,work.lifetime);
                decoder=handle.open(work.seekTarget,work.selectedSeeking,work.operation);work.operation.check();
                long seekTarget=initializeGeneration(work,decoder);
                work.stage="decode";
                decodeItems(work,decoder,seekTarget);
            }catch(Throwable error){
                if(!(error instanceof CancellationException)&&!work.operation.cancelled){problem=error;refreshFailed=true;}
            }finally{
                if(decoder!=null)try{decoder.close();}catch(Throwable next){
                    problem=MediaService.combine(problem,next);synchronized(lock){cleanupFailure=MediaService.combine(cleanupFailure,next);}
                }
                if(problem!=null)synchronized(lock){
                    if(valid(work.gen,work.operation))fail(work.selected,work.stage,problem);
                    else if(closed&&failure!=null)MediaService.combine(failure.cause,problem);
                }
            }
        }
        private long initializeGeneration(DecodeOperation work,MediaDecoder decoder){
            long seekTarget=work.seekTarget;
            synchronized(lock){if(valid(work.gen,work.operation)){
                if(!work.selectedSeeking){seekTarget=handle.initialPositionNanos();target=position=seekTarget;decodedVideoEnd=decodedAudioEnd=seekTarget;}
                MediaInfo facts=decoder.info();qualifyVideo(facts);info=facts;
            }}
            return seekTarget;
        }
        private void decodeItems(DecodeOperation work,MediaDecoder decoder,long seekTarget)throws Exception{
            boolean inputConsumed=false;
            for(;;){
                work.stage="source-refresh";refreshSource(handle,work.version,work.lifetime);work.operation.check();work.stage="decode";
                MediaDecodeRequest request;
                synchronized(lock){
                    if(!valid(work.gen,work.operation))throw new CancellationException();
                    boolean video=requestVideo(),audio=requestAudio();
                    MediaDecoded delayed=takeDecodedAhead(seekTarget);
                    if(delayed!=null){enqueueDecoded(delayed,work.gen,seekTarget,work.operation);continue;}
                    if(inputConsumed){
                        if(decodedAhead.isEmpty()){eof=true;lock.notifyAll();break;}
                        waitForChange(0);continue;
                    }
                    if(!video&&!audio){waitForChange(sourceWait(handle));continue;}
                    request=new MediaDecodeRequest(video,audio,budget.deferredBytes,budget.deferredPackets,budget.deferredNanos);
                }
                work.operation.check();MediaDecoded item=decoder.read(request);work.operation.check();
                MediaInfo facts=decoder.info();MediaDecoderBuffering held=decoder.buffering();MediaInputState terminal=decoder.inputState();
                synchronized(lock){
                    if(!valid(work.gen,work.operation))break;
                    qualifyVideo(facts);info=facts;acceptInput(terminal);
                    stats[18]=Math.max(stats[18],held.bytes);stats[19]=Math.max(stats[19],held.packets);stats[20]=Math.max(stats[20],held.nanos);
                    if(item==null){inputConsumed=true;audioInputEnded=true;audioInputEnd=Math.max(audioInputEnd,decodedAudioEnd);lock.notifyAll();}
                    else if(item==MediaDecoded.YIELD)waitForChange(2);
                    else acceptDecoded(item,work.gen,seekTarget,work.operation);
                }
            }
        }
    }
    private void acceptDecoded(MediaDecoded item,long gen,long seekTarget,Token operation){
        if(item.video&&(item.data.remaining()>budget.videoBytes||item.durationNanos>budget.videoNanos)
                ||!item.video&&(item.data.remaining()>budget.audioBytes||item.durationNanos>budget.audioNanos
                ||item.data.remaining()>budget.deviceBytes||item.durationNanos>budget.deviceNanos))
            throw new IllegalStateException("单个解码项超过输出预算");
        if(!item.video){decodedAudioEnd=Math.max(decodedAudioEnd,item.presentationNanos+item.durationNanos);
            lastAudioBytes=item.data.remaining();lastAudioDuration=item.durationNanos;}
        if(canQueueDecoded(item,seekTarget))enqueueDecoded(item,gen,seekTarget,operation);
        else deferDecoded(item);
    }
    private void enqueueDecoded(MediaDecoded item,long gen,long seekTarget,Token operation){
        if(item.video)enqueueVideo(item,gen,seekTarget,operation);else enqueueAudio(item,gen,seekTarget,operation);
    }
    private long sourceWait(MediaDecoderSource handle){
        if(resumePositionNanos>=0)return 1;
        long deadline=handle==null?Long.MAX_VALUE:handle.refreshDeadlineNanos();
        return deadline==Long.MAX_VALUE?0:Math.max(1,(deadline-System.nanoTime()+999_999L)/1_000_000L);
    }
    /** 刷新只在decoder owner，网络在锁外；源身份复核后才公布事实。 */
    private void refreshSource(MediaDecoderSource handle,long version,Token lifetime) throws Exception {
        if(handle==null)return;
        MediaInfo before;long refreshGeneration;boolean due;
        synchronized(lock){
            if(closed||version!=sourceVersion)return;
            due=System.nanoTime()>=handle.refreshDeadlineNanos();
            if(!due&&resumePositionNanos<0)return;
            before=info;refreshGeneration=generation;
        }
        MediaInfo refreshed=due?handle.refresh(before,lifetime):before;
        synchronized(lock){
            if(closed||version!=sourceVersion||lifetime.cancelled||generation!=refreshGeneration)return;
            info=refreshed;
            boolean checkPause=resumePositionNanos>=0||!playIntent&&(before.durationNanos<0||refreshed.durationNanos<0);
            long pausedPosition=resumePositionNanos>=0?resumePositionNanos:position;
            if(failure==null&&!seeking&&checkPause&&refreshed.seekable!=null&&!refreshed.seekable.contains(pausedPosition)){
                fail(source,"live-window",new IllegalStateException("暂停/当前位置已退出直播窗口: position="+pausedPosition
                        +" window="+refreshed.seekable.startNanos+".."+refreshed.seekable.endNanos));
            }
            resumePositionNanos=-1;lock.notifyAll();
        }
    }
    private void acceptInput(MediaInputState terminal){
        if(terminal.audioEnded&&terminal.audioEndNanos>=0){
            boolean changed=!audioInputEnded||audioInputEnd!=terminal.audioEndNanos;
            audioInputEnded=true;audioInputEnd=terminal.audioEndNanos;if(changed)lock.notifyAll();
        }
    }
    /** 准入与唤醒共用同一容量政策；调用者在会话锁内传入估算或实际待入队大小。 */
    private boolean videoStorageAvailable(long bytes){
        return videos.size()+(current==null?0:1)<budget.videoFrames&&videoBytes+bytes<=budget.videoBytes;
    }
    private boolean videoTimeAvailable(long duration){return remainingVideoNanos()+duration<=budget.videoNanos;}
    private boolean videoFits(long bytes,long duration){return videoStorageAvailable(bytes)&&videoTimeAvailable(duration);}
    private boolean audioFits(long bytes,long duration){
        return pcm.size()<budget.audioBlocks&&audioBytes+bytes<=budget.audioBytes&&audioNanos+duration<=budget.audioNanos;
    }
    private boolean requestVideo(){
        long bytes=qualifyVideo(info);
        boolean storage=videoStorageAvailable(bytes),time=videoTimeAvailable(nextVideoDuration());
        videoTimeBlocked=info.hasVideo&&storage&&!time;
        return info.hasVideo&&storage&&time&&!hasDeferredTrack(true);
    }
    private boolean requestAudio(){return info.hasAudio&&!hasDeferredTrack(false)&&audioFits(lastAudioBytes,lastAudioDuration);}
    private boolean canQueueDecoded(MediaDecoded item,long seekTarget){
        if(item.presentationNanos+item.durationNanos<=seekTarget)return true;
        if(item.video)return videoFits(item.data.remaining(),item.durationNanos);
        return audioFits(item.data.remaining(),item.durationNanos);
    }
    private MediaDecoded takeDecodedAhead(long seekTarget){
        boolean seenVideo=false,seenAudio=false;Iterator<MediaDecoded> iterator=decodedAhead.iterator();
        while(iterator.hasNext()){
            MediaDecoded item=iterator.next();boolean first=item.video?!seenVideo:!seenAudio;
            if(item.video)seenVideo=true;else seenAudio=true;
            if(first&&canQueueDecoded(item,seekTarget)){iterator.remove();aheadBytes-=item.data.remaining();aheadNanos-=item.durationNanos;return item;}
        }
        return null;
    }
    private void deferDecoded(MediaDecoded item){
        if(decodedAhead.size()>=budget.deferredDecodedItems||aheadBytes+item.data.remaining()>budget.deferredDecodedBytes
                ||aheadNanos+item.durationNanos>budget.deferredDecodedNanos)
            throw new IllegalStateException("顺序decoder忽略轨道需求，raw延后存储超过明确预算: bytes="
                    +(aheadBytes+item.data.remaining())+" items="+(decodedAhead.size()+1)+" nanos="+(aheadNanos+item.durationNanos));
        // 顺序SPI可复用借用数组；下一次read之前必须复制，不能把只读视图当所有权。
        byte[] copy=new byte[item.data.remaining()];item.data.duplicate().get(copy);
        MediaDecoded frozen=item.video?MediaDecoded.video(item.presentationNanos,item.durationNanos,item.width,item.height,ByteBuffer.wrap(copy))
                :MediaDecoded.audio(item.presentationNanos,item.sampleRate,item.channels,ByteBuffer.wrap(copy));
        decodedAhead.add(frozen);aheadBytes+=copy.length;aheadNanos+=frozen.durationNanos;
        stats[22]=Math.max(stats[22],aheadBytes);stats[23]=Math.max(stats[23],decodedAhead.size());stats[24]=Math.max(stats[24],aheadNanos);
    }
    private boolean hasDeferredTrack(boolean video){for(MediaDecoded item:decodedAhead)if(item.video==video)return true;return false;}
    private boolean hasDeferredAudio(){return hasDeferredTrack(false);}
    /** 已知/动态尺寸在需求资格处同样校验，避免等一个预算永远容不下的首帧。 */
    private long qualifyVideo(MediaInfo facts){
        if(!facts.hasVideo||facts.width==0||facts.height==0)return 0;
        long pixels=(long)facts.width*facts.height;
        if(pixels>Integer.MAX_VALUE/4||pixels>budget.videoBytes/8)
            throw new IllegalStateException("视频帧与下一帧共存超过预算或可寻址范围");
        long bytes=pixels*4;
        if(current!=null&&current.bytes()>budget.videoBytes-bytes)
            throw new IllegalStateException("动态视频帧与当前展示共存超过预算");
        return bytes;
    }
    /** VFR返回项进入延后后，以实际待入队头帧计算资格及唤醒，不能继续沿上一帧估算。 */
    private long nextVideoDuration(){
        for(MediaDecoded item:decodedAhead)if(item.video)return item.durationNanos;
        return lastVideoDuration;
    }
    /** 字节/条目保留完整 current，时长只计 current 尚未消费部分与后续帧。 */
    private long remainingVideoNanos(){
        if(current==null)return videoNanos;
        long consumed=Math.max(0,Math.min(current.durationNanos,position-current.presentationNanos));
        return Math.max(0,videoNanos-consumed);
    }
    private void enqueueVideo(MediaDecoded item,long gen,long seekTarget,Token operation){
        if(item.presentationNanos+item.durationNanos<=seekTarget&&item.presentationNanos<seekTarget){stats[4]++;return;}
        long bytes=(long)item.width*item.height*4;
        if(bytes>budget.videoBytes||item.durationNanos>budget.videoNanos)throw new IllegalStateException("单个视频帧超过预算");
        while(valid(gen,operation)&&!videoFits(bytes,item.durationNanos)){
            videoTimeBlocked=true;waitForChange(0);
        }
        if(!valid(gen,operation))return;
        MediaVideoFrame frame=new MediaVideoFrame(new MediaFrameKey(sessionId,gen,++sequence),item.presentationNanos,item.durationNanos,item.width,item.height,item.data);
        videos.add(frame);videoBytes+=frame.bytes();videoNanos+=frame.durationNanos;lastVideoDuration=frame.durationNanos;
        stats[0]++;if(stats[14]==0)stats[14]=System.nanoTime();decodedVideoEnd=Math.max(decodedVideoEnd,frame.presentationNanos+frame.durationNanos);
        stats[5]=Math.max(stats[5],videoBytes);stats[6]=Math.max(stats[6],videos.size()+(current==null?0:1));stats[7]=Math.max(stats[7],remainingVideoNanos());videoTimeBlocked=false;lock.notifyAll();
    }
    private void enqueueAudio(MediaDecoded item,long gen,long seekTarget,Token operation){
        ByteBuffer data=item.data.duplicate();long pts=item.presentationNanos;
        if(pts<seekTarget){
            long frames=((seekTarget-pts)*item.sampleRate+999_999_999L)/1_000_000_000L,bytes=frames*item.channels*2;
            if(bytes>=data.remaining())return;data.position(data.position()+(int)bytes);pts+=frames*1_000_000_000L/item.sampleRate;
        }
        long duration=(long)(data.remaining()/(item.channels*2))*1_000_000_000L/item.sampleRate;
        if(data.remaining()>budget.audioBytes||duration>budget.audioNanos||data.remaining()>budget.deviceBytes||duration>budget.deviceNanos)
            throw new IllegalStateException("单个PCM块超过预算");
        while(valid(gen,operation)&&!audioFits(data.remaining(),duration))waitForChange(0);
        if(!valid(gen,operation))return;
        MediaPcm block=new MediaPcm(gen,pts,item.sampleRate,item.channels,data);pcm.add(block);audioBytes+=block.bytes();audioNanos+=block.durationNanos;stats[1]++;
        stats[8]=Math.max(stats[8],audioBytes);stats[9]=Math.max(stats[9],pcm.size());stats[10]=Math.max(stats[10],audioNanos);lock.notifyAll();
    }
    private boolean queueMayFit(MediaAudioPosition measured){
        MediaPcm next=pcm.peek();
        return next!=null&&(measured==null||measured.queuedBuffers<budget.deviceBuffers
                &&measured.queuedBytes+next.bytes()<=budget.deviceBytes&&measured.queuedNanos+next.durationNanos<=budget.deviceNanos);
    }
    private long presentationWait(long now){
        long deadline=Long.MAX_VALUE;
        for(java.util.Map.Entry<MediaFrameKey,PresentationRecord> entry:records.entrySet()){
            PresentationRecord r=entry.getValue();
            boolean currentKey=current!=null&&current.key.equals(entry.getKey());
            boolean finished=eof&&videos.isEmpty()&&position>=r.pts+r.duration;
            if(r.pins==0&&!r.presented&&(!currentKey||finished))deadline=Math.min(deadline,r.selectedNanos+1_000_000_000L);
        }
        return deadline==Long.MAX_VALUE?0:Math.max(1,(deadline-now+999_999L)/1_000_000L);
    }
    private void outputLoop(){new OutputOwner().run();}

    /** 仅原 output 线程持有设备与时钟映射；会话事实仍只在 lock 内结算。 */
    private final class OutputOwner {
        private MediaAudioSink sink;
        private MediaAudioPosition measured;
        private long seen,anchor,base;
        private boolean clockRunning,appliedPlay;
        private double appliedGain=Double.NaN;

        void run(){
            try{
                for(;;){
                    long gen,seekTarget;MediaInfo facts;boolean failed;
                    synchronized(lock){if(closed)break;gen=generation;seekTarget=target;facts=info;failed=failure!=null;}
                    try{
                        startGeneration(gen,seekTarget);
                        if(failed){
                            closeSink();
                            synchronized(lock){if(!closed&&generation==gen)waitForChange(0);}
                            continue;
                        }
                        if(facts.hasAudio&&!updateAudio(gen,seekTarget))continue;
                        synchronized(lock){
                            if(closed||generation!=gen||failure!=null)continue;
                            long now=System.nanoTime();
                            boolean tail=advanceClock(facts,now);
                            if(videoTimeBlocked&&videoTimeAvailable(nextVideoDuration()))lock.notifyAll();
                            while(!videos.isEmpty()&&(videos.peek().presentationNanos<=position||(current==null&&!playIntent)))select(videos.remove());
                            settlePlayback(tail,now);
                            // 时间线、选帧、状态和等待保持原有连续锁区间，不发布中间结果。
                            waitForChange(nextWait(now));
                        }
                    }catch(Throwable error){synchronized(lock){if(!closed&&generation==gen)fail(source,"audio/output",error);}}
                }
            }finally{if(sink!=null)try{closeAudio(sink);}catch(Throwable ignored){}}
        }
        private void startGeneration(long gen,long seekTarget)throws Exception{
            if(seen==gen)return;
            closeSink();
            measured=null;appliedGain=Double.NaN;appliedPlay=false;
            seen=gen;clockRunning=false;base=seekTarget;anchor=System.nanoTime();
        }
        private void closeSink()throws Exception{
            if(sink==null)return;
            MediaAudioSink old=sink;sink=null;closeAudio(old);
        }
        /** native open/queue/position/gain/play 全在锁外，阻塞返回后重新核验代际与意图。 */
        private boolean updateAudio(long gen,long seekTarget)throws Exception{
            if(sink==null){sink=audio.open(budget);sink.reset(gen,seekTarget);}
            boolean desired,refresh;double gain;
            synchronized(lock){
                if(closed||generation!=gen)return false;
                desired=outputPlay();gain=muted?0:volume;
                refresh=measured==null||desired!=appliedPlay||gain!=appliedGain||measured.playing
                        ||desired&&measured.queuedBuffers>0||queueMayFit(measured);
            }
            if(!refresh)return true;
            measured=sink.position();
            boolean queued=queueAudio(gen);
            // 设备open/queue可阻塞；播放前成对重读意图和增益，不能把旧gain配上新play。
            synchronized(lock){if(closed||generation!=gen)return false;desired=outputPlay();gain=muted?0:volume;}
            if(gain!=appliedGain){sink.gain(gain);appliedGain=gain;}
            if(desired!=appliedPlay||desired&&(queued||!measured.playing&&measured.queuedBuffers>0))sink.playing(desired);
            appliedPlay=desired;measured=sink.position();
            return true;
        }
        private boolean queueAudio(long gen)throws Exception{
            boolean queued=false;
            for(int i=0;i<budget.deviceBuffers;i++){
                MediaPcm block;synchronized(lock){if(closed||generation!=gen)break;block=pcm.peek();}
                if(block==null||!sink.queue(block))break;
                queued=true;
                synchronized(lock){if(generation==gen&&pcm.peek()==block){pcm.remove();audioBytes-=block.bytes();audioNanos-=block.durationNanos;lock.notifyAll();}}
            }
            return queued;
        }
        private boolean advanceClock(MediaInfo facts,long now){
            if(facts.hasAudio&&measured!=null){
                audioPosition=measured;position=Math.max(position,measured.positionNanos);sampled=measured.sampledNanos;
                outputReady|=measured.queuedBuffers>0;stats[11]=Math.max(stats[11],measured.queuedBytes);
                stats[12]=Math.max(stats[12],measured.queuedBuffers);stats[13]=Math.max(stats[13],measured.queuedNanos);
                if(measured.playing&&stats[15]==0)stats[15]=sampled;
                boolean tail=audioTail();
                if(tail&&outputPlay()){advanceMonotonic(now);sampled=now;}
                else pauseClock(now);
                return tail;
            }
            boolean available=info.ready&&(current!=null||!videos.isEmpty());
            if(outputPlay()&&available){
                advanceMonotonic(now);
                if(position>=decodedVideoEnd&&!eof)pauseClock(now);
            }else pauseClock(now);
            sampled=now;outputReady=available;
            return false;
        }
        /** 音轨输入终结且PCM已覆盖端点并实际耗尽，才独立推进视频尾部。 */
        private boolean audioTail(){
            return audioInputEnded&&audioInputEnd>=0&&decodedAudioEnd>=audioInputEnd&&!hasDeferredAudio()&&pcm.isEmpty()
                    &&measured.queuedBuffers==0&&measured.positionNanos>=measured.queuedEndNanos
                    &&info.hasVideo&&decodedVideoEnd>measured.queuedEndNanos;
        }
        private void advanceMonotonic(long now){
            if(!clockRunning){base=position;anchor=now;clockRunning=true;}
            position=Math.max(position,Math.min(decodedVideoEnd,base+now-anchor));
        }
        private void pauseClock(long now){clockRunning=false;base=position;anchor=now;}
        private void settlePlayback(boolean tail,long now){
            boolean audioActive=!info.hasAudio||audioPosition!=null&&audioPosition.playing||tail;
            boolean drainedAudio=!info.hasAudio||audioPosition!=null&&audioPosition.queuedBuffers==0&&audioPosition.positionNanos>=audioPosition.queuedEndNanos;
            // 已接受的闭区间末端可以没有目标画面；EOF不伪造提交或定位恢复。
            boolean endpoint=eof&&seeking&&videos.isEmpty()&&current==null&&info.seekable!=null&&target==info.seekable.endNanos;
            boolean drainedVideo=!info.hasVideo||endpoint||videos.isEmpty()&&current!=null&&position>=current.presentationNanos+current.durationNanos;
            // 暂停定位结算本代已准备且停止的输出；播放定位仍以真实设备消费为准。
            boolean seekAudioReady=playIntent?audioActive:!info.hasAudio||audioPosition!=null
                    &&!audioPosition.playing&&(audioPosition.queuedBuffers>0||tail);
            // 换来源并定位时轨道尚未发现；UNKNOWN不能按“没有音视频”提前完成本代定位。
            if(seeking&&info.ready&&(!info.hasVideo||seekPresented)&&seekAudioReady)seeking=false;
            expirePresentations(now);
            if(eof&&drainedAudio&&drainedVideo){state=MediaState.ENDED;seeking=false;}
            else if(seeking)state=MediaState.SEEKING;
            else if(!info.ready)state=MediaState.LOADING;
            else if(!outputPlay())state=outputReady?MediaState.PAUSED:MediaState.READY;
            else if(audioActive&&outputReady&&(info.hasAudio||position<decodedVideoEnd))state=MediaState.PLAYING;
            else state=MediaState.BUFFERING;
        }
        private long nextWait(long now){
            boolean commandDue=info.hasAudio&&(sink==null||appliedPlay!=outputPlay()||appliedGain!=(muted?0:volume)||queueMayFit(measured));
            boolean advancing=outputPlay()&&state!=MediaState.ENDED&&(state!=MediaState.BUFFERING||audioPosition!=null&&audioPosition.playing);
            // 无输出推进时只等待命令/数据通知或确切的呈现元数据退休期限。
            return commandDue||advancing?2:presentationWait(now);
        }
    }
    private void closeAudio(MediaAudioSink sink)throws Exception{
        try{sink.close();}catch(Throwable next){
            synchronized(lock){cleanupFailure=MediaService.combine(cleanupFailure,next);recordCleanup(next);}
            if(next instanceof Error)throw(Error)next;if(next instanceof Exception)throw(Exception)next;throw new RuntimeException(next);
        }
    }
    private void select(MediaVideoFrame next){
        if(current!=null){videoBytes-=current.bytes();videoNanos-=current.durationNanos;}
        current=next;
        records.put(next.key,new PresentationRecord(next.presentationNanos,next.durationNanos,System.nanoTime()));
        expirePresentations(System.nanoTime());lock.notifyAll();
    }
    /** 正式 Prepared 应取得租约并在 finally 释放，避免最新选帧前移使已提交身份消失。 */
    public MediaFrameLease retainPresentation(MediaFrameKey key){
        synchronized(lock){
            PresentationRecord record=records.get(key);
            if(closed||key.generation!=generation||record==null||leases>=budget.presentationLeases)return null;
            record.pins++;leases++;return new MediaFrameLease(this,key);
        }
    }
    boolean presentationActive(MediaFrameKey key){return !closed&&generation==key.generation&&sessionId==key.sessionId;}
    void releasePresentation(MediaFrameKey key){
        synchronized(lock){
            PresentationRecord record=records.get(key);if(record==null||record.pins==0)return;
            record.pins--;leases--;
            if(record.pins==0&&(current==null||!current.key.equals(key))){if(!record.presented)stats[3]++;records.remove(key);}
            lock.notifyAll();
        }
    }
    private void expirePresentations(long now){
        Iterator<java.util.Map.Entry<MediaFrameKey,PresentationRecord>> iterator=records.entrySet().iterator();
        while(iterator.hasNext()){
            java.util.Map.Entry<MediaFrameKey,PresentationRecord> entry=iterator.next();PresentationRecord r=entry.getValue();
            boolean isCurrent=current!=null&&current.key.equals(entry.getKey());
            boolean finished=eof&&videos.isEmpty()&&position>=r.pts+r.duration;
            // 未持有的提交联系只保留有限数量和1s宽限；租约内的GPU提交不按最新帧裁掉。
            if(r.pins==0&&((r.presented&&!isCurrent)||records.size()>budget.presentationRecords
                    ||((!isCurrent||finished)&&!r.presented&&now-r.selectedNanos>1_000_000_000L))){if(!r.presented)stats[3]++;iterator.remove();}
        }
    }
    MediaPresentation presented(MediaFrameKey key,long at){
        synchronized(lock){
            PresentationRecord record=records.get(key);
            if(closed||failure!=null||key.generation!=generation||record==null||record.presented)return null;
            record.presented=true;stats[2]++;if(stats[16]==0)stats[16]=at;
            if(seeking&&record.pts+record.duration>target)seekPresented=true;
            long device=audioPosition==null?sampled:audioPosition.sampledNanos;
            long pos=info.hasAudio&&audioPosition!=null?audioPosition.at(at):position;
            lock.notifyAll();return new MediaPresentation(key,at,record.pts,pos,device,info.hasAudio);
        }
    }
    private void fail(MediaSource source,String stage,Throwable error){
        if(failure==null)failure=new MediaFailure(source,stage,error);else MediaService.combine(failure.cause,error);
        state=MediaState.FAILED;token.cancelled=true;lock.notifyAll();
    }
    private void recordCleanup(Throwable error){if(failure==null)failure=new MediaFailure(source,"close",error);else MediaService.combine(failure.cause,error);}
    private void waitForChange(long millis){try{lock.wait(millis);}catch(InterruptedException ignored){}}
    @Override public void close(){
        boolean initiator;
        synchronized(lock){
            initiator=!closed;
            if(initiator){closed=true;generation++;token.cancelled=true;sourceToken.cancelled=true;clearFrames();outputReady=false;audioPosition=null;state=MediaState.CLOSED;lock.notifyAll();}
        }
        boolean interrupted=false;
        for(Thread thread:new Thread[]{decoderThread,outputThread}){
            if(thread==Thread.currentThread())continue;
            while(thread.isAlive())try{thread.join();}catch(InterruptedException ignored){interrupted=true;}
        }
        service.released(sessionId);if(interrupted)Thread.currentThread().interrupt();
        synchronized(lock){if(initiator&&cleanupFailure!=null)throw new IllegalStateException("媒体会话释放失败",cleanupFailure);}
    }
    private static final class Token implements MediaCancellation{volatile boolean cancelled;public boolean cancelled(){return cancelled;}}
    private static final class PresentationRecord{
        final long pts,duration,selectedNanos;boolean presented;int pins;
        PresentationRecord(long pts,long duration,long selectedNanos){this.pts=pts;this.duration=duration;this.selectedNanos=selectedNanos;}
    }
}
