package club.heiqi.uilib.host.desktop;

import club.heiqi.uilib.host.media.IsolatedMediaAdapters;
import club.heiqi.uilib.host.media.MediaNativeResources;
import club.heiqi.uilib.host.client.gl.GlVideoResources;
import java.util.LinkedHashMap;
import java.util.Map;

/** 最终成品的消费者只读桥；不创建owner、不触发native初始化，也不以总数代替逐字段证据。 */
public final class FinalMediaResources implements MediaStabilityVerification.Resources {
    @Override public Map<String,Long> nativeCounts() {
        MediaNativeResources value=IsolatedMediaAdapters.resources();
        Map<String,Long> counts=new LinkedHashMap<>();
        counts.put("formatContexts",value.formatContexts);
        counts.put("codecContexts",value.codecContexts);
        counts.put("packets",value.packets);
        counts.put("frames",value.frames);
        counts.put("conversionContexts",value.conversionContexts);
        counts.put("nativeBuffers",value.nativeBuffers);
        counts.put("avioContexts",value.avioContexts);
        counts.put("callbacks",value.callbacks);
        counts.put("audioDevices",value.audioDevices);
        counts.put("audioContexts",value.audioContexts);
        counts.put("audioSources",value.audioSources);
        counts.put("audioBuffers",value.audioBuffers);
        return counts;
    }
    @Override public long runtimeLoaders() { return IsolatedMediaAdapters.resources().runtimeLoaders; }
    @Override public Map<String,Long> gpuCounts(DesktopWindow window) { return gpu(window.mediaResources()); }
    @Override public Map<String,Long> gpuClosedCounts(DesktopWindow window) {
        GlVideoResources.Snapshot value=window.mediaResources();
        if(!value.closed)throw new IllegalStateException("关闭后GPU owner仍活跃");
        return gpu(value);
    }
    private static Map<String,Long> gpu(GlVideoResources.Snapshot value) {
        Map<String,Long> counts=new LinkedHashMap<>();
        counts.put("videoTextures",(long)value.textureCount);
        counts.put("videoBytes",value.retainedBytes);
        counts.put("scratchBytes",(long)value.scratchBytes);
        counts.put("peakVideoTextures",(long)value.peakTextures);
        counts.put("peakVideoBytes",value.peakBytes);
        counts.put("videoTexturesLimit",(long)value.maxTextures);
        counts.put("videoBytesLimit",value.maxBytes);
        counts.put("scratchBytesLimit",(long)value.scratchLimit);
        return counts;
    }
}
