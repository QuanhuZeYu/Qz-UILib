package club.heiqi.uilib.host.media;

/** 媒体适配实际拥有的活跃native资源。绑定加载器进程级驻留，与会话句柄分列。 */
public final class MediaNativeResources {
    public final long formatContexts, codecContexts, packets, frames, conversionContexts, nativeBuffers;
    public final long avioContexts, callbacks, audioDevices, audioContexts, audioSources, audioBuffers, runtimeLoaders;
    MediaNativeResources(long[] values, long loaders) {
        formatContexts=values[0]; codecContexts=values[1]; packets=values[2]; frames=values[3];
        conversionContexts=values[4]; nativeBuffers=values[5]; avioContexts=values[6]; callbacks=values[7];
        audioDevices=values[8]; audioContexts=values[9]; audioSources=values[10]; audioBuffers=values[11]; runtimeLoaders=loaders;
    }
    public long activeHandles() { return formatContexts+codecContexts+packets+frames+conversionContexts+nativeBuffers+avioContexts+callbacks+audioDevices+audioContexts+audioSources+audioBuffers; }
    @Override public String toString() { return "MediaNativeResources{format="+formatContexts+",codec="+codecContexts+",packets="+packets+",frames="+frames+",conversion="+conversionContexts+",buffers="+nativeBuffers+",avio="+avioContexts+",callbacks="+callbacks+",devices="+audioDevices+",contexts="+audioContexts+",sources="+audioSources+",audioBuffers="+audioBuffers+",loaders="+runtimeLoaders+"}"; }
}
