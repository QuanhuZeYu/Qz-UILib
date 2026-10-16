package club.heiqi.uilib.host.desktop;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaFrameKey;
import club.heiqi.uilib.ui.media.MediaPresentation;
import club.heiqi.uilib.ui.media.MediaRange;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.MediaState;
import club.heiqi.uilib.ui.media.MediaStatistics;
import club.heiqi.uilib.ui.media.MediaVideoFrame;
import club.heiqi.uilib.ui.media.spi.MediaAudioPosition;
import club.heiqi.uilib.ui.media.spi.MediaAudioSink;
import club.heiqi.uilib.ui.media.spi.MediaAudioSinkFactory;
import club.heiqi.uilib.ui.media.spi.MediaDecoderFactory;
import club.heiqi.uilib.ui.media.spi.MediaPcm;
import club.heiqi.uilib.ui.scene.control.SceneVideo;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 最终成品的正式SceneVideo/DesktopWindow量化消费者，原始记录与最终汇总分别保留。
 * 启动器在build冻结后注入IsolatedMediaAdapters的中性factory及正式资源只读观察器。
 */
public final class MediaStabilityVerification extends TestApplication {
    /** 消费者自己的桥，不是新增生产API。最终字段映射须对应真实owner，不得用null/零假装通过。 */
    public interface Resources {
        Map<String,Long> nativeCounts();
        long runtimeLoaders();
        Map<String,Long> gpuCounts(DesktopWindow window);
        Map<String,Long> gpuClosedCounts(DesktopWindow closedWindow);
    }
    private static final long SECOND = 1_000_000_000L;
    private final MediaDecoderFactory decoders;
    private final ObservedAudio audio;
    private final Resources resources;
    private final Config config;
    private final Path output;
    private final List<Path> finalArtifacts;
    private RecordFile records;

    public MediaStabilityVerification(MediaDecoderFactory decoders, MediaAudioSinkFactory sinks,
            Resources resources, Path inputs, Path acceptance, Path output, List<Path> finalArtifacts) throws Exception {
        this.decoders=Objects.requireNonNull(decoders,"最终隔离decoderFactory");
        this.audio=new ObservedAudio(Objects.requireNonNull(sinks,"最终隔离audioSinkFactory"));
        this.resources=Objects.requireNonNull(resources,"尚缺真实native/GPU资源只读观测");
        this.config=new Config(inputs,acceptance); this.output=output;
        if(finalArtifacts.isEmpty())throw new IllegalArgumentException("缺最终成品路径及SHA输入");
        this.finalArtifacts=new ArrayList<>(finalArtifacts);
    }
    @Override protected void runTests() throws Exception {
        Files.createDirectories(output);
        String mode=arguments()[0];
        records=own(new RecordFile(output.resolve(mode+".jsonl"),250_000,128L*1024*1024));
        records.add("environment","mode",mode,"java",System.getProperty("java.runtime.version"),"os",System.getProperty("os.name"),"arch",System.getProperty("os.arch"));
        for(Path artifact:finalArtifacts)records.add("artifact","path",artifact.toAbsolutePath().toString(),"sha256",sha256(artifact));
        origin(MediaService.class);origin(SceneVideo.class);origin(DesktopWindow.class);origin(decoders.getClass());origin(audio.delegate.getClass());
        records.add("input","uri",arguments()[1],"inputsSha256",config.inputsSha,"acceptanceSha256",config.acceptanceSha);
        // 整个进程只构造一套隔离adapter owner/factory，不为20cycle重复创建classloader/native库。
        try(MediaService service=new MediaService(decoders,audio,MediaBudget.DEFAULT)) {
            switch(mode) {
                case "calibration": scenario("最终成品短校准",()->continuous(service,arguments()[1],false));break;
                case "stable": scenario("600秒完整帧分类",()->continuous(service,arguments()[1],true));break;
                case "seek": scenario("20个固定目标实际恢复",()->seeks(service,arguments()[1]));break;
                case "cycles": scenario("20轮真实播放后归所属基线",()->cycles(service,arguments()[1]));break;
                default:throw new IllegalArgumentException("未知测量mode: "+mode);
            }
        }
        records.flush();
    }
    private void origin(Class<?> type)throws Exception {
        java.security.CodeSource source=type.getProtectionDomain().getCodeSource();
        records.add("class-origin","class",type.getName(),"loader",String.valueOf(type.getClassLoader()),
                "location",source==null?null:source.getLocation().toExternalForm());
    }
    private Opened open(MediaService service,String uri,String operation)throws Exception {
        long hostRequest=System.nanoTime();
        club.heiqi.uilib.ui.scene.host.ScenePageContext[] context={null};
        java.util.concurrent.atomic.AtomicReference<club.heiqi.uilib.ui.reactive.Signal<List<SceneNode>>> children=new java.util.concurrent.atomic.AtomicReference<>();
        // 先建立正常空媒体页；此时尚无媒体session，不可能推进音频或损失启动视频帧。
        DesktopWindow window=DesktopWindow.openWithServices(output.resolve(operation+".yaml"),service,false,services->page->{
            context[0]=page;
            children.set(page.domain().signal(Collections.emptyList()));
            SceneNode root=new SceneNode().size(Size.fill(1),Size.fill(1));
            root.bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.ownedChildren(children.get()));
            page.mountOwned(root);
        });
        MediaSession session=null;
        try {
            window.frame();
            records.add("host-ready","operation",operation,"requestNanos",n(hostRequest),"readyNanos",n(System.nanoTime()),
                    "activeMediaSessions",service.activeSessions(),"mediaOpenDeferred",true);
            // 计时边界仍在真正公开open调用之前；媒体后端首次打开成本全部保留。
            long request=System.nanoTime();
            session=service.open(MediaSource.of(URI.create(uri)),false);
            MediaSession opened=session;
            opened.muted(true);
            records.add("open","operation",operation,"requestNanos",n(request),"session",n(opened.sessionId),"initialPlayIntent",false);
            window.domain.execute(()->{
                context[0].own(opened);
                SceneNode video=SceneVideo.create(context[0],opened).size(Size.fill(1),Size.fill(1));
                children.get().set(Collections.singletonList(video));
            });
            // paused只覆盖声明装配的同步短边界；记录真实play调用，不等解码或用sleep预热。
            long playRequest=System.nanoTime();opened.play();
            records.add("play","operation",operation,"requestNanos",n(playRequest),"generation",n(opened.snapshot().generation));
            return new Opened(service,opened,window,operation,request);
        }catch(Throwable failure){
            try{window.close();}catch(Throwable next){if(next!=failure)failure.addSuppressed(next);}
            if(session!=null)try{session.close();}catch(Throwable next){if(next!=failure)failure.addSuppressed(next);}
            throw failure;
        }
    }
    private final class Opened implements AutoCloseable {
        final MediaService service; final MediaSession session; final DesktopWindow window;
        final String operation; final long request;
        final Map<MediaFrameKey,Long> submitted=new LinkedHashMap<>();
        MediaState previous; long stateSince;
        String lastBounds="";
        boolean firstSwap, steadyStarted;
        Opened(MediaService service,MediaSession session,DesktopWindow window,String operation,long request){
            this.service=service;this.session=session;this.window=window;this.operation=operation;this.request=request;
        }
        MediaSnapshot tick(String currentOperation)throws Exception {
            window.frame();long observed=System.nanoTime();
            Map<MediaFrameKey,MediaVideoFrame> planned=new HashMap<>();
            if(window.lastPlan!=null)for(PaintCommand command:window.lastPlan.commands())if(command.kind==PaintCommand.Kind.VIDEO)planned.put(command.video.frame.key,command.video.frame);
            for(MediaPresentation receipt:window.mediaPresentations()) {
                MediaVideoFrame frame=planned.get(receipt.key);
                if(frame==null)throw new AssertionError("实际swap回执缺少同计划帧身份/时长");
                if(submitted.size()>=config.expectedFrames+1024&&!submitted.containsKey(receipt.key))throw new AssertionError("呈现身份记录超声明上限");
                if(submitted.putIfAbsent(receipt.key,receipt.sampledNanos)!=null)throw new AssertionError("同key重复回执 "+receipt.key);
                AudioSample device=audio.find(receipt.key.generation,receipt.deviceSampledNanos);
                records.add("presented","operation",currentOperation,"source",session.snapshot().source.toString(),"key",receipt.key.toString(),
                        "session",n(receipt.key.sessionId),"generation",n(receipt.key.generation),"sequence",n(receipt.key.sequence),
                        "videoPtsNanos",n(frame.presentationNanos),"durationNanos",n(frame.durationNanos),"submittedNanos",n(receipt.sampledNanos),"boundary","desktop-swap",
                        "audioPtsNanos",receipt.hasAudio?n(receipt.audioPositionNanos):null,"deviceSampledNanos",n(receipt.deviceSampledNanos),
                        "outputLatencyNanos",device==null?null:n(device.position.outputLatencyNanos),"devicePlaying",device==null?null:device.position.playing);
                if(!firstSwap){firstSwap=true;records.add("first-frame","operation",operation,"elapsedNanos",n(receipt.sampledNanos-request),"decodedFirst",n(session.snapshot().statistics.firstVideoDecodedNanos),"deviceStart",n(session.snapshot().statistics.firstAudioStartedNanos));}
            }
            MediaSnapshot state=session.snapshot();
            if(state.state!=previous){
                if(previous!=null)records.add("state-interval","operation",currentOperation,"state",previous.toString(),"startNanos",n(stateSince),"endNanos",n(observed));
                if(state.state==MediaState.BUFFERING)records.add("buffering","operation",currentOperation,"unexpected",steadyStarted&&currentOperation.startsWith("stable"),"atNanos",n(observed));
                records.add("state","operation",currentOperation,"state",state.state.toString(),"generation",n(state.generation),"atNanos",n(observed));
                previous=state.state;stateSince=observed;
            }
            String bounds=range(state.info.buffered)+"/"+range(state.info.seekable);
            if(!lastBounds.equals(bounds)){records.add("ranges","operation",currentOperation,"buffered",range(state.info.buffered),"seekable",range(state.info.seekable),"atNanos",n(observed));lastBounds=bounds;}
            if(firstSwap&&state.statistics.firstAudioStartedNanos>0&&state.state==MediaState.PLAYING)steadyStarted=true;
            if(state.failure!=null)throw new AssertionError("正式媒体失败 "+state.failure.stage,state.failure.cause);
            return state;
        }
        @Override public void close(){
            Throwable failure=null;
            try{if(previous!=null)records.add("state-interval","operation",operation,"state",previous.toString(),"startNanos",n(stateSince),"endNanos",n(System.nanoTime()));}
            catch(Throwable next){failure=next;}
            try{window.close();}catch(Throwable next){if(failure==null)failure=next;else if(next!=failure)failure.addSuppressed(next);}
            try{records.add("owner-closed","operation",operation,"activeSessions",service.activeSessions(),"nativeCounts",resources.nativeCounts(),"runtimeLoaders",resources.runtimeLoaders(),"gpu",resources.gpuClosedCounts(window));records.flush();}
            catch(Throwable next){if(failure==null)failure=next;else if(next!=failure)failure.addSuppressed(next);}
            if(failure instanceof Error)throw(Error)failure;if(failure instanceof RuntimeException)throw(RuntimeException)failure;
            if(failure!=null)throw new IllegalStateException("measurement关闭",failure);
        }
    }
    private void continuous(MediaService service,String uri,boolean complete)throws Exception {
        String operation=complete?"stable":"calibration";
        try(Opened opened=open(service,uri,operation)) {
            long deadline=opened.request+(complete?config.stableSeconds+15:config.calibrationSeconds+8)*SECOND;
            long endShort=opened.request+config.calibrationSeconds*SECOND,nextBudget=opened.request,nextLog=opened.request+60*SECOND;
            MediaSnapshot snapshot=null;
            while(System.nanoTime()<deadline){
                snapshot=opened.tick(operation);
                if(System.nanoTime()>=nextBudget){budget(snapshot.statistics,MediaBudget.DEFAULT,operation);gpuBudget(opened.window,operation);
                    records.add("progress","operation",operation,"positionNanos",n(snapshot.positionNanos),"state",snapshot.state.toString(),"decoded",n(snapshot.statistics.decodedVideoFrames),"presented",n(snapshot.statistics.presentedFrames),"dropped",n(snapshot.statistics.droppedFrames),"pending",n(snapshot.statistics.pendingPresentationFrames),"audioDevice",audio.description);
                    records.flush();nextBudget=System.nanoTime()+SECOND;}
                if(System.nanoTime()>=nextLog){System.out.println("PROGRESS operation="+operation+" positionNanos="+snapshot.positionNanos+" presented="+snapshot.statistics.presentedFrames+" dropped="+snapshot.statistics.droppedFrames+" state="+snapshot.state);nextLog+=60*SECOND;}
                if(!complete&&System.nanoTime()>=endShort&&opened.firstSwap)break;
                if(complete&&snapshot.state==MediaState.ENDED&&snapshot.statistics.pendingPresentationFrames==0)break;
                Thread.sleep(1);
            }
            if(snapshot==null||!opened.firstSwap)throw new AssertionError("无正式实际首帧");
            records.add("segment-final","operation",operation,"positionNanos",n(snapshot.positionNanos),"state",snapshot.state.toString(),"decoded",n(snapshot.statistics.decodedVideoFrames),"corePresented",n(snapshot.statistics.presentedFrames),"coreDropped",n(snapshot.statistics.droppedFrames),"skipped",n(snapshot.statistics.skippedFrames),"pending",n(snapshot.statistics.pendingPresentationFrames),"actualSwapCount",opened.submitted.size(),"audioDevice",audio.description);
            if(complete){
                MediaStatistics s=snapshot.statistics;
                records.add("final-accounting","operation",operation,"decoded",n(s.decodedVideoFrames),"corePresented",n(s.presentedFrames),"coreDropped",n(s.droppedFrames),"skipped",n(s.skippedFrames),"pending",n(s.pendingPresentationFrames),"state",snapshot.state.toString());
                // 只有输入全部确实解码后，补集才可作为drop身份分类；缺输入直接失败。
                if(snapshot.state!=MediaState.ENDED||s.decodedVideoFrames!=config.expectedFrames||s.skippedFrames!=0||s.pendingPresentationFrames!=0)throw new AssertionError("连续播放编码输入未完整终结，禁止把未解码项叫drop");
                if(s.presentedFrames!=opened.submitted.size()||s.presentedFrames+s.droppedFrames!=config.expectedFrames)throw new AssertionError("实际swap/core drop/expected分类不闭合");
                Set<Long> sequence=new HashSet<>();
                for(MediaFrameKey key:opened.submitted.keySet()){
                    if(key.sessionId!=opened.session.sessionId||key.generation!=snapshot.generation||key.sequence<1||key.sequence>config.expectedFrames)throw new AssertionError("非同源连续帧序列");
                    sequence.add(key.sequence);
                }
                long dropped=0;
                for(long i=1;i<=config.expectedFrames;i++)if(!sequence.contains(i)){dropped++;records.add("dropped","operation",operation,"key",new MediaFrameKey(opened.session.sessionId,snapshot.generation,i).toString(),"classification","decoded-sequence-complement-crosschecked-core");}
                if(dropped!=s.droppedFrames)throw new AssertionError("独立补集与core dropped不符");
            }
        }
    }
    private void seeks(MediaService service,String uri)throws Exception {
        try(Opened opened=open(service,uri,"seek-open")){
            awaitFrameAndAudio(opened,"seek-open",0,opened.session.snapshot().generation,opened.request,config.startupMax+3*SECOND,false);
            for(int index=0;index<config.seekTargets.length;index++){
                long target=config.seekTargets[index],request=System.nanoTime();String operation="seek-"+(index+1);
                opened.session.seek(target);long generation=opened.session.snapshot().generation;
                records.add("seek","operation",operation,"requestNanos",n(request),"targetNanos",n(target),"generation",n(generation));
                awaitFrameAndAudio(opened,operation,target,generation,request,4*SECOND,true);
            }
        }
    }
    private void awaitFrameAndAudio(Opened opened,String operation,long target,long generation,long request,long timeout,boolean seeking)throws Exception {
        long videoAt=0,audioAt=0,deadline=request+timeout;MediaVideoFrame selected=null;
        while(System.nanoTime()<deadline&&(videoAt==0||audioAt==0)){
            opened.tick(operation);
            for(MediaPresentation receipt:opened.window.mediaPresentations())if(videoAt==0&&receipt.key.generation==generation){
                MediaVideoFrame frame=null;for(PaintCommand command:opened.window.lastPlan.commands())if(command.kind==PaintCommand.Kind.VIDEO&&command.video.frame.key.equals(receipt.key)){frame=command.video.frame;break;}
                if(frame!=null&&frame.presentationNanos+frame.durationNanos>target){selected=frame;videoAt=receipt.sampledNanos;}
            }
            AudioSample played=audio.firstPlayedAfter(generation,request,target);
            if(played!=null)audioAt=played.position.sampledNanos;
            Thread.sleep(1);
        }
        if(videoAt==0||audioAt==0)throw new AssertionError("缺实际新代swap/设备播放恢复 operation="+operation);
        if(seeking)records.add("seek-complete","operation",operation,"videoSubmittedNanos",n(videoAt),"audioResumedNanos",n(audioAt),"targetNanos",n(target),
                "actualFrameStart",n(selected.presentationNanos),"actualFrameEnd",n(selected.presentationNanos+selected.durationNanos),"coversTarget",selected.presentationNanos<=target,
                "elapsedNanos",n(Math.max(videoAt,audioAt)-request));
    }
    private static final String[] NATIVE_FIELDS={"formatContexts","codecContexts","packets","frames","conversionContexts","nativeBuffers",
            "avioContexts","callbacks","audioDevices","audioContexts","audioSources","audioBuffers"};
    private static boolean nativeZero(Map<String,Long> counts) {
        if(counts.size()!=NATIVE_FIELDS.length)throw new IllegalStateException("native快照字段不完整");
        boolean zero=true;
        for(String name:NATIVE_FIELDS){Long value=counts.get(name);if(value==null||value<0)throw new IllegalStateException("缺实际native字段: "+name);zero&=value==0;}
        return zero;
    }
    private void cycles(MediaService service,String uri)throws Exception {
        Map<String,Long> nativeBaseline=required(resources.nativeCounts(),"native baseline");
        if(!nativeZero(nativeBaseline))throw new AssertionError("首轮之前已有所属native会话资源");
        long loaderBefore=resources.runtimeLoaders(),initializedLoader=-1;
        if(loaderBefore<0||loaderBefore>1)throw new AssertionError("初始runtimeLoaders超上限");
        records.add("runtime-loader-baseline","runtimeLoaders",loaderBefore,"nativeSessionBaseline",nativeBaseline);
        Set<Long> workerBaseline=workers();
        for(int index=0;index<config.cycles;index++){
            String operation="cycle-"+(index+1);
            long before=resources.runtimeLoaders();
            if(before<0||before>1||initializedLoader!=-1&&before!=initializedLoader)throw new AssertionError("runtime loader反复构造或丢失");
            records.add("runtime-loader-before","operation",operation,"runtimeLoaders",before);
            Opened opened=open(service,uri,operation);Throwable failure=null;long activeLoaders=-1;
            try{
                awaitFrameAndAudio(opened,operation,0,opened.session.snapshot().generation,opened.request,config.startupMax+3*SECOND,false);
                activeLoaders=resources.runtimeLoaders();
                records.add("native-active","operation",operation,"counts",required(resources.nativeCounts(),"native active"),"runtimeLoaders",activeLoaders);
                if(activeLoaders!=1||initializedLoader!=-1&&activeLoaders!=initializedLoader)throw new AssertionError("实际播放runtime loader数量不稳定");
                initializedLoader=activeLoaders;gpuBudget(opened.window,operation);
            }catch(Throwable next){failure=next;}
            finally{try{opened.close();}catch(Throwable next){if(failure==null)failure=next;else if(next!=failure)failure.addSuppressed(next);}}
            try {
                Map<String,Long> after=required(resources.nativeCounts(),"native after close"),gpu=required(resources.gpuClosedCounts(opened.window),"GPU after close");
                long loaderAfter=resources.runtimeLoaders();
                Set<Long> remaining=workers();remaining.removeAll(workerBaseline);
                boolean nativeGood=nativeZero(after)&&after.equals(nativeBaseline);
                boolean gpuZero=Objects.equals(gpu.get("videoTextures"),0L)&&Objects.equals(gpu.get("videoBytes"),0L)&&Objects.equals(gpu.get("scratchBytes"),0L);
                boolean loaderGood=initializedLoader==1&&loaderAfter==initializedLoader;
                records.add("closed","operation",operation,"activeSessions",service.activeSessions(),"workers",remaining.size(),"deviceHandles",after.get("audioDevices"),
                        "videoTextures",gpu.get("videoTextures"),"nativeBaseline",nativeBaseline,"nativeAfter",after,"gpuAfter",gpu,"nativeObserved",true,
                        "runtimeLoadersBefore",before,"runtimeLoadersActive",activeLoaders,"runtimeLoadersAfter",loaderAfter,
                        "heapUsed",n(java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()),"nonHeapUsed",n(java.lang.management.ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed()));
                if(!nativeGood||!gpuZero||!loaderGood||!remaining.isEmpty()||service.activeSessions()!=0)throw new AssertionError("close所属资源未归实际baseline或进程loader不稳定");
            }catch(Throwable next){if(failure==null)failure=next;else if(next!=failure)failure.addSuppressed(next);}
            if(failure instanceof Exception)throw(Exception)failure;if(failure instanceof Error)throw(Error)failure;
        }
    }
    private void gpuBudget(DesktopWindow window,String operation)throws Exception {
        Map<String,Long> values=required(resources.gpuCounts(window),"GPU current");records.add("gpu-resource","operation",operation,"values",values);
        for(String name:new String[]{"videoBytes","videoTextures","scratchBytes"}){
            Long actual=values.get(name),limit=values.get(name+"Limit");
            if(actual==null||limit==null)throw new IllegalStateException("GPU观测缺实际/预算字段: "+name);
            records.add("budget","operation",operation,"resource",name,"actual",n(actual),"limit",n(limit));
        }
    }
    private void budget(MediaStatistics s,MediaBudget b,String operation)throws Exception {
        long[] actual={s.peakVideoFrames,s.peakVideoBytes,s.peakVideoNanos,s.peakAudioBlocks,s.peakAudioBytes,s.peakAudioNanos,s.peakDeviceBuffers,s.peakDeviceBytes,s.peakDeviceNanos,s.peakDeferredPackets,s.peakDeferredBytes,s.peakDeferredNanos};
        long[] limit={b.videoFrames,b.videoBytes,b.videoNanos,b.audioBlocks,b.audioBytes,b.audioNanos,b.deviceBuffers,b.deviceBytes,b.deviceNanos,b.deferredPackets,b.deferredBytes,b.deferredNanos};
        String[] names={"videoFrames","videoBytes","videoNanos","audioBlocks","audioBytes","audioNanos","deviceBuffers","deviceBytes","deviceNanos","deferredPackets","deferredBytes","deferredNanos"};
        for(int i=0;i<actual.length;i++)records.add("budget","operation",operation,"resource",names[i],"actual",n(actual[i]),"limit",n(limit[i]));
        records.add("decoder-deferred-decoded","operation",operation,"peakBytes",n(s.peakDeferredDecodedBytes),"peakItems",n(s.peakDeferredDecodedItems),"peakNanos",n(s.peakDeferredDecodedNanos));
    }
    private static Map<String,Long> required(Map<String,Long> value,String name){if(value==null||value.isEmpty())throw new IllegalStateException("缺真实观测: "+name);return new LinkedHashMap<>(value);}
    private static Set<Long> workers(){Set<Long> ids=new HashSet<>();for(Thread t:Thread.getAllStackTraces().keySet())if(t.isAlive()&&(t.getName().startsWith("uilib-media-decoder-")||t.getName().startsWith("uilib-media-output-")))ids.add(t.getId());return ids;}
    private static String n(long value){return Long.toString(value);}
    private static String range(MediaRange value){return value==null?null:value.startNanos+":"+value.endNanos;}
    private static String sha256(Path path)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(path)){byte[] b=new byte[65536];int count;while((count=in.read(b))!=-1)d.update(b,0,count);}StringBuilder out=new StringBuilder();for(byte v:d.digest())out.append(String.format(Locale.ROOT,"%02x",v&255));return out.toString();}

    private static final class AudioSample {final long generation;final MediaAudioPosition position;AudioSample(long g,MediaAudioPosition p){generation=g;position=p;}}
    /** 只包现有SPI，仍由core音频owner线程调用；不输出声音、不另建音频引擎或时钟。 */
    private static final class ObservedAudio implements MediaAudioSinkFactory {
        final MediaAudioSinkFactory delegate;final AudioSample[] ring=new AudioSample[1024];int cursor;volatile String description;
        ObservedAudio(MediaAudioSinkFactory delegate){this.delegate=delegate;}
        synchronized void add(long generation,MediaAudioPosition position){ring[cursor++%ring.length]=new AudioSample(generation,position);}
        synchronized AudioSample find(long generation,long at){for(AudioSample s:ring)if(s!=null&&s.generation==generation&&s.position.sampledNanos==at)return s;return null;}
        synchronized AudioSample firstPlayedAfter(long generation,long request,long target){AudioSample first=null;for(AudioSample s:ring)if(s!=null&&s.generation==generation&&s.position.sampledNanos>=request&&s.position.playing&&s.position.queuedBuffers>0&&s.position.positionNanos>target&&(first==null||s.position.sampledNanos<first.position.sampledNanos))first=s;return first;}
        public MediaAudioSink open(MediaBudget budget)throws Exception {
            MediaAudioSink sink=delegate.open(budget);description=sink.deviceDescription();
            return new MediaAudioSink(){long generation;
                public boolean queue(MediaPcm pcm)throws Exception{return sink.queue(pcm);}
                public void playing(boolean value)throws Exception{sink.playing(value);}
                public void gain(double value)throws Exception{sink.gain(value);}
                public void reset(long generation,long position)throws Exception{sink.reset(generation,position);this.generation=generation;}
                public MediaAudioPosition position()throws Exception{MediaAudioPosition p=sink.position();add(generation,p);return p;}
                public String deviceDescription(){return sink.deviceDescription();}
                public void close()throws Exception{sink.close();}
            };
        }
    }
    private static final class Config {
        final long[] seekTargets;final int expectedFrames,stableSeconds,cycles,calibrationSeconds=25;final long startupMax;final String inputsSha,acceptanceSha;
        Config(Path inputs,Path acceptance)throws Exception{
            String raw=new String(Files.readAllBytes(inputs),StandardCharsets.UTF_8),target=new String(Files.readAllBytes(acceptance),StandardCharsets.UTF_8);
            Matcher sequence=Pattern.compile("\"seekNanos\"\\s*:\\s*\\[([^]]*)\\]",Pattern.DOTALL).matcher(raw);
            if(!sequence.find())throw new IllegalArgumentException("固定seekNanos输入缺失");String[] values=sequence.group(1).trim().split(",");seekTargets=new long[values.length];for(int i=0;i<values.length;i++)seekTargets[i]=Long.parseLong(values[i].trim());
            expectedFrames=(int)field(target,"eligibleFrames");stableSeconds=(int)field(target,"stableSeconds");cycles=(int)field(target,"openCloseCycles");startupMax=field(target,"startupMaxNanos");
            if(seekTargets.length!=20||expectedFrames!=18000||stableSeconds!=600||cycles!=20)throw new IllegalArgumentException("固定验收输入不符，不自动换目标");inputsSha=sha256(inputs);acceptanceSha=sha256(acceptance);
        }
        static long field(String json,String key){Matcher m=Pattern.compile("\""+key+"\"\\s*:\\s*([0-9]+)").matcher(json);if(!m.find())throw new IllegalArgumentException("缺验收字段 "+key);return Long.parseLong(m.group(1));}
    }
    private static final class RecordFile implements AutoCloseable {
        final BufferedWriter writer;final long maxBytes;final int maxRows;long bytes;int rows;
        RecordFile(Path file,int rows,long bytes)throws IOException{writer=Files.newBufferedWriter(file,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);maxRows=rows;maxBytes=bytes;}
        void add(String kind,Object...pairs)throws IOException{Map<String,Object> row=new LinkedHashMap<>();row.put("kind",kind);for(int i=0;i<pairs.length;i+=2)row.put((String)pairs[i],pairs[i+1]);String value=json(row);long size=value.getBytes(StandardCharsets.UTF_8).length+1L;if(rows>=maxRows||bytes+size>maxBytes)throw new IOException("raw记录超过明确上限");writer.write(value);writer.write('\n');rows++;bytes+=size;if(rows%256==0)writer.flush();}
        void flush()throws IOException{writer.flush();}public void close()throws IOException{writer.close();}
        static String json(Object value){if(value==null)return"null";if(value instanceof Number||value instanceof Boolean)return value.toString();if(value instanceof Map){StringBuilder b=new StringBuilder("{");for(Object raw:((Map<?,?>)value).entrySet()){Map.Entry<?,?> e=(Map.Entry<?,?>)raw;if(b.length()>1)b.append(',');b.append(json(String.valueOf(e.getKey()))).append(':').append(json(e.getValue()));}return b.append('}').toString();}String text=value.toString();StringBuilder b=new StringBuilder("\"");for(int i=0;i<text.length();i++){char c=text.charAt(i);switch(c){case '\\':b.append("\\\\");break;case '\"':b.append("\\\"");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;default:if(c<32)b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else b.append(c);}}return b.append('\"').toString();}
    }
}
