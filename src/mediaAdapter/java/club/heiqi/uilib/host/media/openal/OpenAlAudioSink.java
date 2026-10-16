package club.heiqi.uilib.host.media.openal;

import club.heiqi.uilib.ui.media.spi.MediaPcm;
import club.heiqi.uilib.ui.media.spi.MediaAudioPosition;
import club.heiqi.uilib.ui.media.spi.MediaAudioSink;

import club.heiqi.uilib.ui.media.MediaBudget;
import static club.heiqi.uilib.host.media.NativeMediaResourceTracker.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.openal.EXTThreadLocalContext;
import org.lwjgl.openal.SOFTSourceLatency;

/** 专属 device/thread context/source。只在实际 unqueue 后移动 PCM 基准，STOPPED 不采用归零 raw offset。 */
final class OpenAlAudioSink implements MediaAudioSink {
    private final Thread owner=Thread.currentThread();
    private final MediaBudget budget;
    private final ArrayDeque<Integer> free=new ArrayDeque<>();
    private final ArrayDeque<Queued> queued=new ArrayDeque<>();
    private final ArrayList<Integer> buffers=new ArrayList<>();
    private final double[] latency=new double[2];
    private final ByteBuffer scratch;
    private long device,context,generation,base,lastPosition,lastEnd,bytes,nanos,drainedAt;
    private int source,sampleRate,channels;
    private boolean closed,attached,playIntent;
    private double gain=-1;
    private String description;
    OpenAlAudioSink(String name,MediaBudget budget) throws Exception {
        this.budget=budget;
        scratch=ByteBuffer.allocateDirect(Math.toIntExact(budget.deviceBytes));
        try {
            device=ALC10.alcOpenDevice(name);
            if (device==0) throw new IllegalStateException("alcOpenDevice returned null: "+name);
            acquired(DEVICE);
            ALCCapabilities caps=ALC.createCapabilities(device);
            if (!caps.ALC_EXT_thread_local_context) throw new IllegalStateException("缺少 ALC_EXT_thread_local_context");
            context=ALC10.alcCreateContext(device,(IntBuffer)null);
            if (context==0) throw new IllegalStateException("alcCreateContext returned null");
            acquired(CONTEXT);
            attached=EXTThreadLocalContext.alcSetThreadContext(context);
            if (!attached) throw new IllegalStateException("alcSetThreadContext failed");
            ALCapabilities al=AL.createCapabilities(caps);
            if (!al.AL_SOFT_source_latency) throw new IllegalStateException("缺少 AL_SOFT_source_latency");
            description=ALC10.alcGetString(device,ALC10.ALC_DEVICE_SPECIFIER)+" / "+AL10.alGetString(AL10.AL_RENDERER)
                    +" / "+AL10.alGetString(AL10.AL_VERSION);
            if (!description.toLowerCase(Locale.ROOT).contains("openal soft")) throw new IllegalStateException("设备不是 OpenAL Soft: "+description);
            source=AL10.alGenSources(); if(source!=0)acquired(SOURCE); check("gen source");
            AL10.alSourcei(source,AL10.AL_SOURCE_RELATIVE,AL10.AL_TRUE);
            for(int i=0;i<budget.deviceBuffers;i++) { int buffer=AL10.alGenBuffers(); if(buffer!=0)acquired(AUDIO_BUFFER); buffers.add(buffer); free.add(buffer); check("gen buffer"); }
        } catch(Throwable failure) {
            try { close(); } catch(Throwable next) { if(next!=failure) failure.addSuppressed(next); }
            rethrow(failure);
        }
    }
    @Override public boolean queue(MediaPcm block) {
        owner();
        if (block.generation!=generation) throw new IllegalArgumentException("PCM代际与设备不一致");
        if (free.isEmpty() || bytes+block.bytes()>budget.deviceBytes || nanos+block.durationNanos>budget.deviceNanos) return false;
        if (!queued.isEmpty() && (sampleRate!=block.sampleRate || channels!=block.channels))
            throw new IllegalStateException("PCM格式改变前必须清空本代输出");
        sampleRate=block.sampleRate; channels=block.channels;
        scratch.clear(); scratch.put(block.samples()); scratch.flip();
        int id=free.remove();
        AL10.alBufferData(id,channels==1?AL10.AL_FORMAT_MONO16:AL10.AL_FORMAT_STEREO16,scratch,sampleRate);
        AL10.alSourceQueueBuffers(source,id); check("queue PCM");
        if (queued.isEmpty()) { base=block.presentationNanos; drainedAt=0; }
        queued.add(new Queued(id,block.presentationNanos,block.durationNanos,block.bytes()));
        bytes+=block.bytes(); nanos+=block.durationNanos; lastEnd=block.presentationNanos+block.durationNanos;
        return true;
    }
    @Override public void playing(boolean playing) {
        owner(); playIntent=playing; int state=AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE);
        if (playing && !queued.isEmpty() && state!=AL10.AL_PLAYING) AL10.alSourcePlay(source);
        else if (!playing && state==AL10.AL_PLAYING) AL10.alSourcePause(source);
        check("play/pause");
    }
    @Override public void gain(double value) {
        owner();
        if (!Double.isFinite(value) || value<0 || value>1) throw new IllegalArgumentException("无效source gain");
        if (value!=gain) { AL10.alSourcef(source,AL10.AL_GAIN,(float)value); check("source gain"); gain=value; }
    }
    @Override public void reset(long generation,long positionNanos) {
        owner(); AL10.alSourceStop(source); check("reset stop");
        while(!queued.isEmpty()) {
            int id=AL10.alSourceUnqueueBuffers(source); check("reset unqueue"); queued.remove(); free.add(id);
        }
        AL10.alSourcei(source,AL10.AL_BUFFER,0); check("reset detach");
        this.generation=generation; base=lastPosition=lastEnd=positionNanos; bytes=nanos=drainedAt=0; playIntent=false;
    }
    @Override public MediaAudioPosition position() {
        owner();
        int processed=AL10.alGetSourcei(source,AL10.AL_BUFFERS_PROCESSED);
        for(int i=0;i<processed;i++) {
            int id=AL10.alSourceUnqueueBuffers(source); check("unqueue consumed");
            Queued item=queued.remove();
            if (id!=item.id) throw new IllegalStateException("OpenAL队列顺序不一致");
            base=item.pts+item.duration; bytes-=item.bytes; nanos-=item.duration; free.add(id);
        }
        // processed 仍在队列时 raw offset 已经包含它；只有上方实际移除才调整基准。
        if (!queued.isEmpty()) base=queued.peek().pts;
        long before=System.nanoTime();
        SOFTSourceLatency.alGetSourcedvSOFT(source,SOFTSourceLatency.AL_SEC_OFFSET_LATENCY_SOFT,latency);
        long after=System.nanoTime(),sampled=before+(after-before)/2;
        int state=AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE); check("source position");
        long latencyNanos=Math.max(0,Math.round(latency[1]*1_000_000_000d));
        boolean active=state==AL10.AL_PLAYING;
        long value;
        if (queued.isEmpty()) {
            if (drainedAt==0) drainedAt=sampled;
            value=lastEnd-latencyNanos+(playIntent?sampled-drainedAt:0);
        } else value=base+Math.round(latency[0]*1_000_000_000d)-latencyNanos;
        if (state==AL10.AL_PAUSED) value=lastPosition;
        lastPosition=Math.max(lastPosition,Math.min(lastEnd,value));
        return new MediaAudioPosition(lastPosition,sampled,latencyNanos,bytes,nanos,lastEnd,queued.size(),
                active || playIntent && queued.isEmpty() && lastPosition<lastEnd);
    }
    @Override public String deviceDescription() { return description; }
    private void owner() {
        if (Thread.currentThread()!=owner) throw new IllegalStateException("OpenAL线程归属错误");
        if(closed) throw new IllegalStateException("OpenAL sink已关闭");
    }
    private static void check(String stage) {
        int error=AL10.alGetError(); if(error!=AL10.AL_NO_ERROR) throw new IllegalStateException(stage+" AL error="+error);
    }
    @Override public void close() throws Exception {
        if(closed) return;
        if(Thread.currentThread()!=owner) throw new IllegalStateException("OpenAL释放线程错误");
        closed=true; Throwable failure=null;
        if(source!=0) {
            try { AL10.alSourceStop(source); check("close stop"); } catch(Throwable next) {failure=combine(failure,next);}
            try { AL10.alDeleteSources(source); check("delete source"); released(SOURCE); source=0; } catch(Throwable next) {failure=combine(failure,next);}
        }
        for(int buffer:buffers) try { AL10.alDeleteBuffers(buffer); check("delete buffer"); if(buffer!=0)released(AUDIO_BUFFER); } catch(Throwable next) {failure=combine(failure,next);}
        queued.clear(); buffers.clear(); free.clear();
        if(attached) {
            try { if(!EXTThreadLocalContext.alcSetThreadContext(0)) throw new IllegalStateException("detach thread context failed"); }
            catch(Throwable next) { failure=combine(failure,next); }
            try { AL.setCurrentThread(null); } catch(Throwable next) { failure=combine(failure,next); }
        }
        if(context!=0) try { ALC10.alcDestroyContext(context);
            if(ALC10.alcGetError(device)!=ALC10.ALC_NO_ERROR)throw new IllegalStateException("destroy context failed");
            released(CONTEXT); context=0; } catch(Throwable next) {failure=combine(failure,next);}
        if(device!=0) {
            try { int error=ALC10.alcGetError(device); if(error!=ALC10.ALC_NO_ERROR) throw new IllegalStateException("close ALC error="+error); }
            catch(Throwable next) {failure=combine(failure,next);}
            try { if(!ALC10.alcCloseDevice(device)) throw new IllegalStateException("alcCloseDevice failed"); released(DEVICE); device=0; }
            catch(Throwable next) {failure=combine(failure,next);}
        }
        if(failure!=null) rethrow(failure);
    }
    private static Throwable combine(Throwable first,Throwable next) {
        if(first==null) return next; if(first!=next) first.addSuppressed(next); return first;
    }
    private static void rethrow(Throwable failure) throws Exception {
        if(failure instanceof Error) throw (Error)failure;
        if(failure instanceof Exception) throw (Exception)failure;
        throw new RuntimeException(failure);
    }
    private static final class Queued {
        final int id,bytes; final long pts,duration;
        Queued(int id,long pts,long duration,int bytes) {this.id=id;this.pts=pts;this.duration=duration;this.bytes=bytes;}
    }
}
