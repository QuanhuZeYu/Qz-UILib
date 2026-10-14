package club.heiqi.uilib.testing.gallery;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/** 结果在测量窗口结束后序列化；进程堆差值不冒充本负载分配量。 */
public final class PerformanceReport {
    private PerformanceReport() {}
    public static final class Snapshot {
        public final long heap, gcCount, gcMillis;
        private Snapshot(long heap, long gcCount, long gcMillis) {
            this.heap = heap; this.gcCount = gcCount; this.gcMillis = gcMillis;
        }
        public static Snapshot capture() {
            long count = 0, millis = 0;
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (gc.getCollectionCount() >= 0) count += gc.getCollectionCount();
                if (gc.getCollectionTime() >= 0) millis += gc.getCollectionTime();
            }
            Runtime rt = Runtime.getRuntime();
            return new Snapshot(rt.totalMemory() - rt.freeMemory(), count, millis);
        }
    }
    public static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') result.append('\\').append(c);
            else if (c < 32) result.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int)c));
            else result.append(c);
        }
        return result.append('"').toString();
    }
    static String json(String host, String boundary, String version, String load, int scale,
            int warmup, int target, boolean complete, boolean path, int observedChanges, int contentLength, int width, int height,
            long mount, long first, PerformanceSamples samples, Snapshot before, Snapshot after) {
        return "{\"schema\":1,\"host\":" + quote(host) + ",\"boundary\":" + quote(boundary)
                + ",\"version\":" + quote(version) + ",\"load\":" + quote(load)
                + ",\"scale\":" + scale + ",\"contentUtf16\":" + contentLength
                + ",\"warmupFrames\":" + warmup + ",\"targetFrames\":" + target
                + ",\"complete\":" + complete + ",\"pathObserved\":" + path
                + ",\"observedChanges\":" + observedChanges
                + ",\"width\":" + width + ",\"height\":" + height
                + ",\"declarationNanos\":" + mount + ",\"firstFrameNanos\":" + first
                + ",\"java\":" + quote(System.getProperty("java.version"))
                + ",\"vm\":" + quote(System.getProperty("java.vm.name"))
                + ",\"os\":" + quote(System.getProperty("os.name") + " " + System.getProperty("os.arch"))
                + ",\"processGcCountDelta\":" + (after.gcCount - before.gcCount)
                + ",\"processGcMillisDelta\":" + (after.gcMillis - before.gcMillis)
                + ",\"processHeapBytesDelta\":" + (after.heap - before.heap)
                + ",\"samples\":" + samples.toJson() + "}";
    }
}
