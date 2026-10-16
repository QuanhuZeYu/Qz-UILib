package club.heiqi.uilib.ui.media;

/** 累计计数与内部持有峰值。主动切代跳过另计；解码绝不冒充 GPU 提交。 */
public final class MediaStatistics {
    public final long decodedVideoFrames, decodedAudioBlocks, presentedFrames, droppedFrames, skippedFrames;
    public final long peakVideoBytes, peakVideoFrames, peakVideoNanos, peakAudioBytes, peakAudioBlocks, peakAudioNanos;
    public final long peakDeviceBytes, peakDeviceBuffers, peakDeviceNanos;
    public final long peakDeferredBytes, peakDeferredPackets, peakDeferredNanos, pendingPresentationFrames;
    public final long peakDeferredDecodedBytes,peakDeferredDecodedItems,peakDeferredDecodedNanos;
    public final long firstVideoDecodedNanos, firstAudioStartedNanos, firstPresentedNanos, openedNanos;
    MediaStatistics(long[] n) {
        decodedVideoFrames=n[0]; decodedAudioBlocks=n[1]; presentedFrames=n[2]; droppedFrames=n[3]; skippedFrames=n[4];
        peakVideoBytes=n[5]; peakVideoFrames=n[6]; peakVideoNanos=n[7]; peakAudioBytes=n[8]; peakAudioBlocks=n[9]; peakAudioNanos=n[10];
        peakDeviceBytes=n[11]; peakDeviceBuffers=n[12]; peakDeviceNanos=n[13];
        peakDeferredBytes=n[18]; peakDeferredPackets=n[19]; peakDeferredNanos=n[20]; pendingPresentationFrames=n[21];
        peakDeferredDecodedBytes=n[22];peakDeferredDecodedItems=n[23];peakDeferredDecodedNanos=n[24];
        firstVideoDecodedNanos=n[14]; firstAudioStartedNanos=n[15]; firstPresentedNanos=n[16]; openedNanos=n[17];
    }
}
