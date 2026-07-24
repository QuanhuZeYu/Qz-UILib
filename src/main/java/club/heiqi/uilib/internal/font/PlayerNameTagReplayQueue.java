package club.heiqi.uilib.internal.font;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import net.minecraft.entity.Entity;

/**
 * 保存当前世界渲染批次中待延后的原版玩家名称标签调用。
 *
 * <p>排空时先从共享队列移除整个批次，再按 FIFO 回放。这样即使某项回放失败，未执行尾项也不会泄漏到
 * 下一帧；回放标记则保证经 Mixin 再次进入原方法时不会被递归排队。</p>
 */
public final class PlayerNameTagReplayQueue {

    private static final Object LOCK = new Object();
    private static final Deque<PendingReplay> PENDING = new ArrayDeque<PendingReplay>();
    private static boolean replaying;

    private PlayerNameTagReplayQueue() {}

    /**
     * 保存一次原版名称标签调用。
     *
     * @param target 原方法所属的 renderer 实例
     * @param entity 原方法的实体参数
     * @param text 原方法的文本参数
     * @param x 原方法的 X 坐标
     * @param y 原方法的 Y 坐标
     * @param z 原方法的 Z 坐标
     * @param maxDistance 原方法的最大绘制距离
     * @return 已入队时为 {@code true}；正在回放、应直接进入原方法时为 {@code false}
     */
    public static boolean defer(ReplayTarget target, Entity entity, String text,
            double x, double y, double z, int maxDistance) {
        synchronized (LOCK) {
            if (replaying) {
                return false;
            }
            PENDING.addLast(new PendingReplay(Objects.requireNonNull(target, "target"),
                    entity, text, x, y, z, maxDistance));
            return true;
        }
    }

    /**
     * 取走当前批次并按 FIFO 回放；回放失败时仍保证递归旁路状态复位。
     */
    public static void drain() {
        List<PendingReplay> batch;
        synchronized (LOCK) {
            if (replaying || PENDING.isEmpty()) {
                return;
            }
            batch = new ArrayList<PendingReplay>(PENDING);
            PENDING.clear();
            replaying = true;
        }

        try {
            for (PendingReplay replay : batch) {
                replay.invoke();
            }
        } finally {
            synchronized (LOCK) {
                replaying = false;
            }
        }
    }

    /** 清空所有尚未回放的调用，不中断正在执行的本地回放批次。 */
    public static void clear() {
        synchronized (LOCK) {
            PENDING.clear();
        }
    }

    /**
     * 判断队列当前是否正在执行回放。
     *
     * @return 正在回放时为 {@code true}
     */
    public static boolean isReplaying() {
        synchronized (LOCK) {
            return replaying;
        }
    }

    /** 由 Render Mixin 的 {@code @Invoker} 实现，用于调用同一个原版方法。 */
    public interface ReplayTarget {

        /**
         * 调用原版完整名称标签方法。
         *
         * @param entity 原方法的实体参数
         * @param text 原方法的文本参数
         * @param x 原方法的 X 坐标
         * @param y 原方法的 Y 坐标
         * @param z 原方法的 Z 坐标
         * @param maxDistance 原方法的最大绘制距离
         */
        void qzuilib$invokeNameTag(Entity entity, String text,
                double x, double y, double z, int maxDistance);
    }

    /** 一次原方法调用的完整不可变快照。 */
    private static final class PendingReplay {
        private final ReplayTarget target;
        private final Entity entity;
        private final String text;
        private final double x;
        private final double y;
        private final double z;
        private final int maxDistance;

        private PendingReplay(ReplayTarget target, Entity entity, String text,
                double x, double y, double z, int maxDistance) {
            this.target = target;
            this.entity = entity;
            this.text = text;
            this.x = x;
            this.y = y;
            this.z = z;
            this.maxDistance = maxDistance;
        }

        /** 使用保存的 target 与全部参数执行一次原方法。 */
        private void invoke() {
            target.qzuilib$invokeNameTag(entity, text, x, y, z, maxDistance);
        }
    }
}
