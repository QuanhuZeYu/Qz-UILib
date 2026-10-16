package club.heiqi.uilib.testing.media;

import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaStatistics;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaSession;


import java.util.function.Predicate;

/** 纯中性测试辅助，不连接adapter或native类。 */
final class MediaTestSupport {
    static MediaSnapshot await(MediaSession session,Predicate<MediaSnapshot> condition,long millis) throws Exception {
        long end=System.nanoTime()+millis*1_000_000L; MediaSnapshot snapshot;
        do { snapshot=session.snapshot(); if(condition.test(snapshot)) return snapshot; checked(snapshot); Thread.sleep(5); }
        while(System.nanoTime()<end);
        print("TIMEOUT",snapshot); throw new AssertionError("媒体条件超时");
    }
    static void checked(MediaSnapshot s) {
        if(s.failure!=null) throw new AssertionError("媒体失败 stage="+s.failure.stage+" source="+s.failure.source,s.failure.cause);
    }
    static void print(String name,MediaSnapshot s) {
        MediaStatistics q=s.statistics;
        System.out.println(name+" state="+s.state+" gen="+s.generation+" posNs="+s.positionNanos+" video="+(s.video==null?"none":s.video.key+"@"+s.video.presentationNanos)
                +" decodedV="+q.decodedVideoFrames+" decodedA="+q.decodedAudioBlocks+" presented="+q.presentedFrames+" dropped="+q.droppedFrames+" skipped="+q.skippedFrames
                +" peakV="+q.peakVideoBytes+"/"+q.peakVideoFrames+"/"+q.peakVideoNanos+" peakA="+q.peakAudioBytes+"/"+q.peakAudioBlocks+"/"+q.peakAudioNanos
                +" peakDevice="+q.peakDeviceBytes+"/"+q.peakDeviceBuffers+"/"+q.peakDeviceNanos
                +" peakDeferred="+q.peakDeferredBytes+"/"+q.peakDeferredPackets+"/"+q.peakDeferredNanos+" pendingPresentation="+q.pendingPresentationFrames
                +" peakDeferredDecoded="+q.peakDeferredDecodedBytes+"/"+q.peakDeferredDecodedItems+"/"+q.peakDeferredDecodedNanos);
    }
    static void budget(MediaStatistics s,MediaBudget b) {
        require(s.peakVideoBytes<=b.videoBytes && s.peakVideoFrames<=b.videoFrames && s.peakVideoNanos<=b.videoNanos,"视频预算超限");
        require(s.peakAudioBytes<=b.audioBytes && s.peakAudioBlocks<=b.audioBlocks && s.peakAudioNanos<=b.audioNanos,"PCM预算超限");
        require(s.peakDeviceBytes<=b.deviceBytes && s.peakDeviceBuffers<=b.deviceBuffers && s.peakDeviceNanos<=b.deviceNanos,"设备预算超限");
        require(s.peakDeferredBytes<=b.deferredBytes && s.peakDeferredPackets<=b.deferredPackets && s.peakDeferredNanos<=b.deferredNanos,"压缩packet预算超限");
        require(s.peakDeferredDecodedBytes<=b.deferredDecodedBytes && s.peakDeferredDecodedItems<=b.deferredDecodedItems
                &&s.peakDeferredDecodedNanos<=b.deferredDecodedNanos,"raw延后存储预算超限");
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
