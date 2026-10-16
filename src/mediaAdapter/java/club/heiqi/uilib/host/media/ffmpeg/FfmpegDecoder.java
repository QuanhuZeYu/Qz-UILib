package club.heiqi.uilib.host.media.ffmpeg;

import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.MediaRange;
import club.heiqi.uilib.ui.media.MediaInfo;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.spi.MediaCancellation;
import club.heiqi.uilib.ui.media.spi.MediaDecodeRequest;
import club.heiqi.uilib.ui.media.spi.MediaDecoderBuffering;
import club.heiqi.uilib.ui.media.spi.MediaDecoded;
import club.heiqi.uilib.ui.media.spi.MediaInputState;
import club.heiqi.uilib.ui.media.spi.MediaDecoder;


import static club.heiqi.uilib.host.media.NativeMediaResourceTracker.*;

import java.nio.ByteBuffer;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOInterruptCB;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.ffmpeg.swresample.SwrContext;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.*;
import static org.bytedeco.ffmpeg.global.swresample.*;

/** native 读取/解码/归一化，所有资源在创建线程释放。共同调度、代际和设备均不在这里。 */
final class FfmpegDecoder implements MediaDecoder {
    private final Thread owner=Thread.currentThread();
    private final MediaCancellation cancellation;
    private AVFormatContext input;
    private boolean ownsFormat;
    private HlsSourceContext sourceContext;
    private InitialPlaylistIo initialIo;
    private AVCodecContext video,audio;
    private AVPacket packet;
    private AVFrame frame;
    private AVIOInterruptCB.Callback_Pointer callback;
    private SwsContext scale;
    private SwrContext resample;
    private BytePointer rgba,pcm;
    private PointerPointer<Pointer> rgbaPlanes,pcmPlanes;
    private IntPointer rgbaStride;
    private AVChannelLayout outputLayout;
    private int videoStream=-1,audioStream=-1,rgbaSize,pcmSize;
    private int inputRate,inputFormat=-1,inputChannels,outputChannels;
    private long originNanos;
    private boolean demuxEof,closed,videoFlushed,audioFlushed,videoDone,audioDone;
    private final java.util.ArrayDeque<Held> deferred=new java.util.ArrayDeque<>();
    private long deferredBytes,deferredNanos,lastAudioPacketEnd=-1,lastVideoPacketEnd=-1;
    private MediaInputState terminal=MediaInputState.UNKNOWN;
    private volatile MediaInfo info=MediaInfo.UNKNOWN;

    FfmpegDecoder(MediaSource source,MediaCancellation cancellation) throws Exception {
        this(source,cancellation,null,null,0);
    }
    FfmpegDecoder(MediaSource source,MediaCancellation cancellation,HlsSourceContext context,byte[] initial,int index) throws Exception {
        this.cancellation=cancellation;this.sourceContext=context;
        try {
            cancellation.check();
            input=avformat_alloc_context();
            if (input==null || input.isNull()) throw new IllegalStateException("avformat_alloc_context returned null");
            acquired(FORMAT); ownsFormat=true;
            callback=new AVIOInterruptCB.Callback_Pointer() {
                @Override public int call(Pointer opaque) { return cancellation.cancelled()?1:0; }
            };
            acquired(CALLBACK);
            input.interrupt_callback().callback(callback);
            AVDictionary options=new AVDictionary(null);
            try {
                check(av_dict_set(options,"live_start_index",Integer.toString(index),0),"live_start_index");
                if(initial!=null){initialIo=new InitialPlaylistIo(initial);initialIo.attach(input);
                    check(av_dict_set(options,"protocol_whitelist","http,https,tcp,tls,crypto",0),"protocol_whitelist");}
                int opened=avformat_open_input(input,source.uri.toString(),initial==null?null:av_find_input_format("hls"),options);
                // FFmpeg失败会自行释放format并清空指针，计数在该真实释放边界同步。
                if(input.isNull()) { released(FORMAT); ownsFormat=false; }
                check(opened,"avformat_open_input");
            } finally { av_dict_free(options); }
            check(avformat_find_stream_info(input,(PointerPointer)null),"avformat_find_stream_info");
            cancellation.check();
            originNanos=input.start_time()==AV_NOPTS_VALUE ? 0 : Math.multiplyExact(input.start_time(),1000L);
            if(sourceContext!=null)originNanos=sourceContext.anchor(originNanos);
            for (int i=0;i<input.nb_streams();i++) {
                int type=input.streams(i).codecpar().codec_type();
                if (type==AVMEDIA_TYPE_VIDEO && videoStream<0) videoStream=i;
                if (type==AVMEDIA_TYPE_AUDIO && audioStream<0) audioStream=i;
            }
            if (videoStream<0 && audioStream<0) throw new IllegalStateException("来源没有音视频轨道");
            if (videoStream>=0) video=openCodec(videoStream);
            if (audioStream>=0) audio=openCodec(audioStream);
            packet=av_packet_alloc();
            if(packet==null || packet.isNull())throw new IllegalStateException("av_packet_alloc returned null");
            acquired(PACKET); frame=av_frame_alloc();
            if(frame==null || frame.isNull())throw new IllegalStateException("av_frame_alloc returned null");
            acquired(FRAME);
            rgbaPlanes=buffer(new PointerPointer<Pointer>(4)); pcmPlanes=buffer(new PointerPointer<Pointer>(1)); rgbaStride=buffer(new IntPointer(4));
            long duration=input.duration()==AV_NOPTS_VALUE || input.duration()<0 ? -1:Math.multiplyExact(input.duration(),1000L);
            info=new MediaInfo(true,video!=null,audio!=null,duration,duration<0?null:new MediaRange(0,duration),null,
                    video==null?0:video.width(),video==null?0:video.height());
        } catch(Throwable error) {
            try { close(); } catch(Throwable next) { if (next!=error) error.addSuppressed(next); }
            rethrow(error);
        }
    }
    private AVCodecContext openCodec(int stream) {
        AVCodecParameters parameters=input.streams(stream).codecpar();
        AVCodec codec=avcodec_find_decoder(parameters.codec_id());
        if (codec==null || codec.isNull()) throw new IllegalStateException("avcodec_find_decoder codec="+parameters.codec_id());
        AVCodecContext context=avcodec_alloc_context3(codec);
        if(context==null || context.isNull())throw new IllegalStateException("avcodec_alloc_context3 returned null");
        acquired(CODEC);
        try {
            check(avcodec_parameters_to_context(context,parameters),"avcodec_parameters_to_context");
            check(avcodec_open2(context,codec,(AVDictionary)null),"avcodec_open2");
            return context;
        } catch(Throwable error) { avcodec_free_context(context); released(CODEC); throw error; }
    }
    @Override public MediaInfo info() { return sourceContext==null?info:sourceContext.facts(info); }
    @Override public MediaDecoded read() {
        MediaBudget budget=MediaBudget.DEFAULT;
        return read(new MediaDecodeRequest(true,true,budget.deferredBytes,budget.deferredPackets,budget.deferredNanos));
    }
    @Override public MediaDecoderBuffering buffering() { return new MediaDecoderBuffering(deferredBytes,deferred.size(),deferredNanos); }
    @Override public MediaInputState inputState(){return terminal;}
    private static long packetEnd(AVFormatContext context,AVPacket packet,long origin){
        if(packet.pts()==AV_NOPTS_VALUE)return -1;
        return nanos(packet.pts()+Math.max(0,packet.duration()),context.streams(packet.stream_index()).time_base())-origin;
    }
    @Override public MediaDecoded read(MediaDecodeRequest request) {
        owner(); cancellation.check(); av_frame_unref(frame);
        for (;;) {
            cancellation.check();
            if (request.video && video!=null && !videoDone) {
                int result=avcodec_receive_frame(video,frame);
                if (result>=0) return convert(true);
                if (result==AVERROR_EOF) videoDone=true; else if (result!=-11) check(result,"avcodec_receive_frame video");
            }
            if (request.audio && audio!=null && !audioDone) {
                int result=avcodec_receive_frame(audio,frame);
                if (result>=0) return convert(false);
                if (result==AVERROR_EOF) audioDone=true; else if (result!=-11) check(result,"avcodec_receive_frame audio");
            }
            if ((video==null || videoDone) && (audio==null || audioDone)) return null;
            Held next=null;
            java.util.Iterator<Held> iterator=deferred.iterator();
            while (iterator.hasNext()) {
                Held held=iterator.next();
                if (held.video?request.video:request.audio) {
                    next=held; iterator.remove(); deferredBytes-=held.packet.size(); deferredNanos-=held.duration; break;
                }
            }
            if (next!=null) {
                try { check(avcodec_send_packet(next.video?video:audio,next.packet),"avcodec_send_packet deferred"); }
                finally { av_packet_free(next.packet); released(PACKET); }
                continue;
            }
            if (demuxEof) {
                if (request.video && video!=null && !videoFlushed) {
                    check(avcodec_send_packet(video,null),"avcodec_send_packet video drain"); videoFlushed=true; continue;
                }
                if (request.audio && audio!=null && !audioFlushed) {
                    check(avcodec_send_packet(audio,null),"avcodec_send_packet audio drain"); audioFlushed=true; continue;
                }
                return MediaDecoded.YIELD;
            }
            int result=av_read_frame(input,packet);
            cancellation.check(); // HLS中断可能以EOF向外传播，取消优先于终结事实。
            if (result==AVERROR_EOF) {
                demuxEof=true;terminal=new MediaInputState(true,true,true,lastAudioPacketEnd,lastVideoPacketEnd);continue;
            }
            check(result,"av_read_frame");
            try {
                boolean isVideo=packet.stream_index()==videoStream;
                AVCodecContext target=isVideo?video:packet.stream_index()==audioStream?audio:null;
                if (target==null) continue;
                long endpoint=packetEnd(input,packet,originNanos);
                if(isVideo)lastVideoPacketEnd=Math.max(lastVideoPacketEnd,endpoint);else lastAudioPacketEnd=Math.max(lastAudioPacketEnd,endpoint);
                if (isVideo?request.video:request.audio) check(avcodec_send_packet(target,packet),"avcodec_send_packet");
                else {
                    long duration=Math.max(0,nanos(packet.duration(),input.streams(packet.stream_index()).time_base()));
                    if (deferredBytes+packet.size()>request.deferredBytes || deferred.size()>=request.deferredPackets
                            || deferredNanos+duration>request.deferredNanos)
                        throw new IllegalStateException("轨道交织超过压缩packet延后预算");
                    AVPacket copy=av_packet_clone(packet);
                    if (copy==null || copy.isNull()) throw new IllegalStateException("av_packet_clone returned null");
                    acquired(PACKET);
                    deferred.add(new Held(copy,isVideo,duration)); deferredBytes+=packet.size(); deferredNanos+=duration;
                }
            } finally { av_packet_unref(packet); }
        }
    }
    private MediaDecoded convert(boolean isVideo) {
        int stream=isVideo?videoStream:audioStream;
        long timestamp=frame.best_effort_timestamp();
        if (timestamp==AV_NOPTS_VALUE) throw new IllegalStateException("解码帧没有可用PTS");
        long pts=nanos(timestamp,input.streams(stream).time_base())-originNanos;
        if (isVideo) {
            int width=frame.width(),height=frame.height();
            AVRational sar=frame.sample_aspect_ratio();
            if (sar.num()>0 && sar.den()>0) width=Math.max(1,(int)Math.round(width*(double)sar.num()/sar.den()));
            long bytes=(long)width*height*4;
            if (bytes>Integer.MAX_VALUE) throw new IllegalStateException("视频尺寸超过可寻址范围");
            boolean ownedScale=scale!=null && !scale.isNull();
            scale=sws_getCachedContext(scale,frame.width(),frame.height(),frame.format(),width,height,AV_PIX_FMT_RGBA,
                    SWS_BILINEAR,null,null,(DoublePointer)null);
            boolean hasScale=scale!=null && !scale.isNull();
            if(ownedScale && !hasScale)released(CONVERSION);
            if(!ownedScale && hasScale)acquired(CONVERSION);
            if(!hasScale)throw new IllegalStateException("sws_getCachedContext returned null");
            int colors=frame.colorspace();
            IntPointer table=sws_getCoefficients(colors==AVCOL_SPC_UNSPECIFIED ? SWS_CS_DEFAULT:colors);
            check(sws_setColorspaceDetails(scale,table,frame.color_range()==AVCOL_RANGE_JPEG?1:0,table,1,0,1<<16,1<<16),"sws_setColorspaceDetails");
            if (rgba==null || rgbaSize!=(int)bytes) {
                if (rgba!=null) { closeBuffer(rgba); rgba=null; } rgba=buffer(new BytePointer(bytes)); rgbaSize=(int)bytes;
                rgbaPlanes.put(0,rgba); rgbaStride.put(0,width*4);
            }
            int rows=sws_scale(scale,frame.data(),frame.linesize(),0,frame.height(),rgbaPlanes,rgbaStride);
            if (rows!=height) throw new IllegalStateException("sws_scale rows="+rows+" height="+height);
            long duration=frame.duration()>0 ? nanos(frame.duration(),input.streams(videoStream).time_base()):0;
            if (duration==0) {
                AVRational rate=av_guess_frame_rate(input,input.streams(videoStream),frame);
                if (rate.num()>0 && rate.den()>0) duration=1_000_000_000L*rate.den()/rate.num();
            }
            if (info.width!=width || info.height!=height) info=new MediaInfo(true,true,info.hasAudio,
                    info.durationNanos,info.seekable,info.buffered,width,height);
            return MediaDecoded.video(pts,duration,width,height,rgba.position(0).limit(bytes).asByteBuffer());
        }
        int rate=frame.sample_rate(),channels=frame.ch_layout().nb_channels();
        if (rate<1 || channels<1) throw new IllegalStateException("音频帧缺少采样格式");
        if (resample==null || inputRate!=rate || inputFormat!=frame.format() || inputChannels!=channels) {
            if (resample!=null) { swr_free(resample); released(CONVERSION); resample=null; }
            if (outputLayout!=null) { av_channel_layout_uninit(outputLayout); closeBuffer(outputLayout); outputLayout=null; }
            inputRate=rate; inputFormat=frame.format(); inputChannels=channels; outputChannels=Math.min(2,channels);
            outputLayout=buffer(new AVChannelLayout()); av_channel_layout_default(outputLayout,outputChannels);
            resample=new SwrContext(null);
            int allocated=swr_alloc_set_opts2(resample,outputLayout,AV_SAMPLE_FMT_S16,rate,frame.ch_layout(),frame.format(),rate,0,null);
            if(!resample.isNull())acquired(CONVERSION); else resample=null;
            check(allocated,"swr_alloc_set_opts2");
            check(swr_init(resample),"swr_init");
        }
        int frames=Math.toIntExact(frame.nb_samples()+swr_get_delay(resample,rate)+32);
        int bytes=Math.multiplyExact(Math.multiplyExact(frames,outputChannels),2);
        if (pcm==null || pcmSize<bytes) {
            if (pcm!=null) { closeBuffer(pcm); pcm=null; } pcm=buffer(new BytePointer(bytes)); pcmSize=bytes; pcmPlanes.put(0,pcm);
        }
        int produced=swr_convert(resample,pcmPlanes,frames,frame.extended_data(),frame.nb_samples());
        check(produced,"swr_convert"); int actual=produced*outputChannels*2;
        return MediaDecoded.audio(pts,rate,outputChannels,pcm.position(0).limit(actual).asByteBuffer());
    }
    @Override public void seek(long positionNanos) {
        owner(); cancellation.check();
        long timestamp=(originNanos+positionNanos)/1000;
        check(avformat_seek_file(input,-1,Long.MIN_VALUE,timestamp,timestamp,AVSEEK_FLAG_BACKWARD),"avformat_seek_file");
        if (video!=null) avcodec_flush_buffers(video);
        if (audio!=null) avcodec_flush_buffers(audio);
        if (resample!=null) { swr_free(resample); released(CONVERSION); resample=null; }
        clearDeferred(); demuxEof=videoFlushed=audioFlushed=videoDone=audioDone=false;
        lastAudioPacketEnd=lastVideoPacketEnd=-1;terminal=MediaInputState.UNKNOWN;
        av_packet_unref(packet); av_frame_unref(frame);
    }
    private static long nanos(long value,AVRational base) {
        return Math.round(value*(double)base.num()*1_000_000_000d/base.den());
    }
    private void owner() {
        if (Thread.currentThread()!=owner) throw new IllegalStateException("FFmpeg线程归属错误");
        if (closed) throw new IllegalStateException("FFmpeg decoder已关闭");
    }
    private void check(int result,String stage) {
        cancellation.check();
        if (result<0) {
            byte[] text=new byte[256]; av_strerror(result,text,text.length);
            int length=0; while(length<text.length && text[length]!=0) length++;
            throw new IllegalStateException(stage+"="+result+" "+new String(text,0,length,java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    @Override public void close() throws Exception {
        if (closed) return;
        if (Thread.currentThread()!=owner) throw new IllegalStateException("FFmpeg释放线程错误");
        closed=true; Throwable failure=null;
        AutoCloseable[] releases={
            this::clearDeferred, ()->{if (frame!=null && !frame.isNull()) { av_frame_free(frame); released(FRAME); }}, ()->{if (packet!=null && !packet.isNull()) { av_packet_free(packet); released(PACKET); }},
            ()->{if (video!=null) { avcodec_free_context(video); released(CODEC); }}, ()->{if (audio!=null) { avcodec_free_context(audio); released(CODEC); }},
            ()->{if (scale!=null && !scale.isNull()) { sws_freeContext(scale); released(CONVERSION); }}, ()->{if (resample!=null) { swr_free(resample); released(CONVERSION); }},
            ()->{if (rgba!=null) closeBuffer(rgba);}, ()->{if (pcm!=null) closeBuffer(pcm);},
            ()->{if (rgbaPlanes!=null) closeBuffer(rgbaPlanes);}, ()->{if (pcmPlanes!=null) closeBuffer(pcmPlanes);},
            ()->{if (rgbaStride!=null) closeBuffer(rgbaStride);},
            ()->{if (outputLayout!=null) {av_channel_layout_uninit(outputLayout); closeBuffer(outputLayout);}},
            ()->{if (ownsFormat && input!=null && !input.isNull()) { avformat_close_input(input); released(FORMAT); ownsFormat=false; }},
            ()->{if (initialIo!=null) initialIo.close();},
            ()->{if (callback!=null) { callback.close(); released(CALLBACK); }}
        };
        for (AutoCloseable release:releases) try { release.close(); } catch(Throwable next) {
            if (failure==null) failure=next; else if (failure!=next) failure.addSuppressed(next);
        }
        if (failure!=null) rethrow(failure);
    }
    private void clearDeferred() {
        while(!deferred.isEmpty()) { av_packet_free(deferred.remove().packet); released(PACKET); }
        deferredBytes=deferredNanos=0;
    }
    private static <T extends Pointer> T buffer(T value) {
        if(value==null || value.isNull())throw new IllegalStateException("媒体native buffer分配失败");
        acquired(BUFFER);return value;
    }
    private static void closeBuffer(Pointer value) { value.close(); released(BUFFER); }
    private static final class Held {
        final AVPacket packet; final boolean video; final long duration;
        Held(AVPacket packet,boolean video,long duration) {this.packet=packet;this.video=video;this.duration=duration;}
    }
    private static void rethrow(Throwable error) throws Exception {
        if (error instanceof Error) throw (Error)error;
        if (error instanceof Exception) throw (Exception)error;
        throw new RuntimeException(error);
    }
}
