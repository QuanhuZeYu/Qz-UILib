package club.heiqi.uilib.host.media.ffmpeg;

import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaInfo;
import club.heiqi.uilib.ui.media.MediaRange;
import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.spi.MediaCancellation;
import club.heiqi.uilib.ui.media.spi.MediaDecodeRequest;
import club.heiqi.uilib.ui.media.spi.MediaDecoded;
import club.heiqi.uilib.ui.media.spi.MediaDecoder;
import club.heiqi.uilib.ui.media.spi.MediaInputState;

/** 两个输入按需求读取到同一会话；无第二时钟、raw队列或页面推进。 */
final class SplitMediaDecoder implements MediaDecoder {
    private FfmpegDecoder video,audio;
    private boolean videoDone,audioDone,preferAudio=true,closed;
    SplitMediaDecoder(MediaSource source,MediaCancellation cancellation)throws Exception{
        try{
            video=new FfmpegDecoder(source.videoInput(),cancellation,null,null,0,true);
            audio=new FfmpegDecoder(source.audio,cancellation,null,null,0,true);
            if(!video.info().hasVideo||video.info().hasAudio||!audio.info().hasAudio||audio.info().hasVideo)
                throw new IllegalArgumentException("分轨来源须分别只包含视频和音频轨道");
        }catch(Throwable failure){
            try{close();}catch(Throwable cleanup){failure.addSuppressed(cleanup);}rethrow(failure);
        }
    }
    @Override public MediaInfo info(){
        MediaInfo v=video.info(),a=audio.info();
        long duration=v.durationNanos<0||a.durationNanos<0?-1:Math.max(v.durationNanos,a.durationNanos);
        MediaRange range=null;
        if(v.seekable!=null&&a.seekable!=null){
            long start=Math.max(v.seekable.startNanos,a.seekable.startNanos),end=Math.min(v.seekable.endNanos,a.seekable.endNanos);
            if(start<=end)range=new MediaRange(start,end);
        }
        return new MediaInfo(v.ready&&a.ready,true,true,duration,range,null,v.width,v.height);
    }
    @Override public MediaDecoded read()throws Exception{
        MediaBudget b=MediaBudget.DEFAULT;return read(new MediaDecodeRequest(true,true,b.deferredBytes,b.deferredPackets,b.deferredNanos));
    }
    @Override public MediaDecoded read(MediaDecodeRequest request)throws Exception{
        for(int i=0;i<2;i++){
            boolean wantVideo=request.video&&!videoDone,wantAudio=request.audio&&!audioDone;
            if(!wantVideo&&!wantAudio)return videoDone&&audioDone?null:MediaDecoded.YIELD;
            boolean takeAudio=wantAudio&&(!wantVideo||preferAudio);preferAudio=!takeAudio;
            MediaDecoded item=(takeAudio?audio:video).read(new MediaDecodeRequest(!takeAudio,takeAudio,
                    request.deferredBytes,request.deferredPackets,request.deferredNanos));
            if(item!=null)return item;
            if(takeAudio)audioDone=true;else videoDone=true;
        }
        return videoDone&&audioDone?null:MediaDecoded.YIELD;
    }
    @Override public MediaInputState inputState(){
        MediaInputState v=video.inputState(),a=audio.inputState();
        return new MediaInputState(v.inputEnded&&a.inputEnded,a.audioEnded,v.videoEnded,a.audioEndNanos,v.videoEndNanos);
    }
    @Override public void seek(long positionNanos)throws Exception{
        video.seek(positionNanos);audio.seek(positionNanos);videoDone=audioDone=false;preferAudio=true;
    }
    @Override public void close()throws Exception{
        if(closed)return;closed=true;Throwable failure=null;
        if(audio!=null)try{audio.close();}catch(Throwable next){failure=next;}
        if(video!=null)try{video.close();}catch(Throwable next){if(failure==null)failure=next;else failure.addSuppressed(next);}
        if(failure!=null)rethrow(failure);
    }
    private static void rethrow(Throwable failure)throws Exception{
        if(failure instanceof Error)throw(Error)failure;if(failure instanceof Exception)throw(Exception)failure;throw new RuntimeException(failure);
    }
}
