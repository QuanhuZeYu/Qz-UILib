package club.heiqi.uilib.internal.image;

import java.util.concurrent.atomic.AtomicInteger;

/** Minecraft resource reload 与各 host-owned image cache 之间的内部失效纪元。 */
public final class HostImageResourceEpoch {

    private static final AtomicInteger CURRENT = new AtomicInteger();

    private HostImageResourceEpoch() { }

    /** @return 当前资源纪元 */
    public static int current() {
        return CURRENT.get();
    }

    /** 由 Minecraft resource reload 边界调用。 */
    public static void advance() {
        CURRENT.incrementAndGet();
    }
}
