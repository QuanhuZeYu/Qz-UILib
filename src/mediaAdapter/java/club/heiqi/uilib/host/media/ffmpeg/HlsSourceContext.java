package club.heiqi.uilib.host.media.ffmpeg;

import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.MediaRange;
import club.heiqi.uilib.ui.media.MediaInfo;
import club.heiqi.uilib.ui.media.spi.MediaCancellation;
import club.heiqi.uilib.ui.media.spi.MediaDecoder;
import club.heiqi.uilib.ui.media.spi.MediaDecoderSource;



import io.lindstrom.m3u8.model.MediaPlaylist;
import io.lindstrom.m3u8.model.MediaSegment;
import io.lindstrom.m3u8.parser.MediaPlaylistParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 单个会话/来源的有限列表与媒体锚点。只由 decoder owner 使用；不扫描媒体流或共享 URL 游标。 */
final class HlsSourceContext implements MediaDecoderSource {
    private static final int MAX_BYTES=262144,MAX_SEGMENTS=2048,MAX_URL=8192;
    private final MediaSource source;
    private final MediaCancellation lifetime;
    private Snapshot window;
    private long windowStart,openingPosition,origin=Long.MIN_VALUE,deadline=Long.MAX_VALUE;
    private boolean closed;
    private final boolean liveTimeline;
    HlsSourceContext(MediaSource source,MediaCancellation lifetime) throws Exception {
        this.source=source;this.lifetime=lifetime;
        String scheme=source.uri.getScheme();
        if(("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme))
                &&source.uri.getPath()!=null&&source.uri.getPath().endsWith(".m3u8"))window=load(lifetime);
        liveTimeline=window!=null&&window.live;
    }
    @Override public MediaDecoder open(long position,boolean seeking,MediaCancellation operation) throws Exception {
        check(operation);
        if(!liveTimeline)return openVod(position,seeking,operation);
        if(origin!=Long.MIN_VALUE&&window.live)update(operation);
        // 初次join沿FFmpeg默认-3留出实际启动余量；不是对过期位置的自动跳转。
        int index=seeking?0:Math.max(window.durations.length-3,0);
        if(seeking) {
            MediaRange range=range();
            if(range==null||!range.contains(position))throw new IllegalArgumentException("live seek 目标已退出当前窗口: "+position);
            long start=windowStart;
            while(index<window.durations.length&&position>=start+window.durations[index])start+=window.durations[index++];
            if(index==window.durations.length)throw new IllegalArgumentException("live窗口末端没有可解码片段");
        }
        openingPosition=windowStart;for(int i=0;i<index;i++)openingPosition=Math.addExact(openingPosition,window.durations[i]);
        // ENDLIST只改变正常结束事实；同来源cursor仍用原origin，不能退回独立VOD零点。
        FfmpegDecoder decoder=new FfmpegDecoder(source,operation,this,window.bytes,window.live?index:0);
        try{if(!window.live&&seeking)decoder.seek(position);return decoder;}
        catch(Throwable error){try{decoder.close();}catch(Throwable next){error.addSuppressed(next);}if(error instanceof Error)throw(Error)error;if(error instanceof Exception)throw(Exception)error;throw new RuntimeException(error);}
    }
    private MediaDecoder openVod(long position,boolean seeking,MediaCancellation operation) throws Exception {
        FfmpegDecoder decoder=new FfmpegDecoder(source,operation);
        // 新建VOD cursor已位于首包；0应保留首音频。HLS首DTS可晚于format.start_time，
        // 再物理seek到0会被demuxer拒绝，钳到首视频又会丢失合法的前导音频。
        try{if(seeking&&position!=0)decoder.seek(position);return decoder;}
        catch(Throwable error){try{decoder.close();}catch(Throwable next){error.addSuppressed(next);}if(error instanceof Error)throw(Error)error;if(error instanceof Exception)throw(Exception)error;throw new RuntimeException(error);}
    }
    long anchor(long detected) {
        if(origin==Long.MIN_VALUE)origin=Math.subtractExact(detected,openingPosition);
        return origin;
    }
    MediaInfo facts(MediaInfo decoded) {
        if(!liveTimeline||origin==Long.MIN_VALUE)return decoded;
        MediaRange range=range();
        long duration=window.live?-1:Math.addExact(range.endNanos,1);
        return new MediaInfo(decoded.ready,decoded.hasVideo,decoded.hasAudio,duration,range,decoded.buffered,decoded.width,decoded.height);
    }
    private MediaRange range() {
        if(origin==Long.MIN_VALUE)return null;
        long end=windowStart;
        for(long duration:window.durations)end=Math.addExact(end,duration);
        return end<=windowStart?null:new MediaRange(windowStart,end-1);
    }
    @Override public long initialPositionNanos(){return openingPosition;}
    @Override public long refreshDeadlineNanos(){return deadline;}
    @Override public MediaInfo refresh(MediaInfo current,MediaCancellation cancellation) throws Exception {
        check(cancellation);
        if(window!=null&&window.live&&System.nanoTime()>=deadline)update(cancellation);
        return facts(current);
    }
    private void update(MediaCancellation cancellation) throws Exception {
        Snapshot next=load(cancellation);
        if(next.sequence<window.sequence)throw new IOException("HLS MEDIA-SEQUENCE倒退");
        long offset=next.sequence-window.sequence;
        if(offset>window.durations.length)throw new IOException("HLS刷新无重叠，缺少真实时长锚点");
        if(offset<window.durations.length) {
            int overlap=(int)offset;
            if(!window.uris[overlap].equals(next.uris[0])||window.discontinuities[overlap]!=next.discontinuities[0])
                throw new IOException("HLS重叠片段身份/PTS epoch改变");
        }
        for(int i=0;i<offset;i++)windowStart=Math.addExact(windowStart,window.durations[i]);
        window=next;
    }
    private Snapshot load(MediaCancellation cancellation) throws Exception {
        check(cancellation);
        if(source.uri.toString().length()>MAX_URL)throw new IOException("媒体URL超过预算");
        HttpURLConnection connection=(HttpURLConnection)source.uri.toURL().openConnection();
        connection.setConnectTimeout(5000);connection.setReadTimeout(5000);connection.setRequestProperty("Cache-Control","no-cache");
        source.headers.forEach(connection::setRequestProperty);
        byte[] bytes;
        try {
            int status=connection.getResponseCode();
            if(status<200||status>=300) {
                IOException cause;
                try(InputStream rejected=connection.getInputStream()){cause=new IOException(connection.getResponseMessage());}
                catch(IOException original){cause=original;}
                throw new IOException("HLS manifest HTTP "+status+" source="+source.uri,cause);
            }
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096];int count;
                while((count=input.read(buffer))>=0){check(cancellation);if(output.size()+count>MAX_BYTES)throw new IOException("HLS列表字节超过预算");output.write(buffer,0,count);}
                bytes=output.toByteArray();
            }
        }finally{connection.disconnect();}
        MediaPlaylist parsed=new MediaPlaylistParser().readPlaylist(new String(bytes,StandardCharsets.UTF_8));
        List<MediaSegment> segments=parsed.mediaSegments();
        if(segments.isEmpty()||segments.size()>MAX_SEGMENTS)throw new IOException("HLS片段数为空或超过预算");
        long[] durations=new long[segments.size()],epochs=new long[segments.size()];String[] uris=new String[segments.size()];
        long epoch=parsed.discontinuitySequence();
        for(int i=0;i<segments.size();i++){
            MediaSegment segment=segments.get(i);double value=segment.duration();
            if(!Double.isFinite(value)||value<=0||value>86400)throw new IOException("无效EXTINF");
            durations[i]=Math.round(value*1e9);
            if(segment.discontinuity())epoch++;
            epochs[i]=epoch;uris[i]=source.uri.resolve(segment.uri()).toString();
            if(uris[i].length()>MAX_URL)throw new IOException("片段URL超过预算");
            if(epoch!=parsed.discontinuitySequence())throw new IOException("首期来源出现未映射的PTS discontinuity");
        }
        deadline=parsed.ongoing()?System.nanoTime()+Math.max(1,parsed.targetDuration())*1_000_000_000L:Long.MAX_VALUE;
        check(cancellation);return new Snapshot(bytes,parsed.mediaSequence(),parsed.ongoing(),durations,uris,epochs);
    }
    private void check(MediaCancellation cancellation){if(closed)throw new IllegalStateException("来源上下文已关闭");lifetime.check();cancellation.check();}
    @Override public void close(){closed=true;window=null;deadline=Long.MAX_VALUE;}
    private static final class Snapshot {
        final byte[] bytes;final long sequence;final boolean live;final long[] durations,discontinuities;final String[] uris;
        Snapshot(byte[] bytes,long sequence,boolean live,long[] durations,String[] uris,long[] discontinuities){this.bytes=bytes;this.sequence=sequence;this.live=live;this.durations=durations;this.uris=uris;this.discontinuities=discontinuities;}
    }
}
