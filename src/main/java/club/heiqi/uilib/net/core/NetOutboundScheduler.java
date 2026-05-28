package club.heiqi.uilib.net.core;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import club.heiqi.uilib.net.api.NetRealtimeDropPolicy;

/**
 * 网络出站优先级调度器。
 *
 * <p>当前实现仍在调用线程中排空队列，但会在每一帧发送之间重新仲裁优先级，
 * 让 realtime/control 帧有机会插队到后续 bulk 帧之前。</p>
 */
public final class NetOutboundScheduler {

    private final Queue<PendingFrame> realtimeQueue = new ConcurrentLinkedQueue<PendingFrame>();
    private final Queue<PendingFrame> controlQueue = new ConcurrentLinkedQueue<PendingFrame>();
    private final Queue<PendingFrame> bulkQueue = new ConcurrentLinkedQueue<PendingFrame>();
    private final Map<String, RealtimeQueueState> realtimeStates = new ConcurrentHashMap<String, RealtimeQueueState>();
    private final AtomicBoolean draining = new AtomicBoolean(false);

    /**
     * 入队普通控制帧。
     *
     * @param priority 优先级
     * @param dispatch 发送动作
     */
    public void enqueue(Priority priority, Runnable dispatch) {
        enqueueInternal(new PendingFrame(priority, dispatch, 0L, null));
    }

    /**
     * 入队可丢弃的实时帧。
     *
     * @param queueKey 逻辑 lane key
     * @param maxQueuedFrames 最大排队帧数
     * @param dropPolicy 队满丢弃策略
     * @param expireAtMillis 到期时间戳
     * @param dispatch 发送动作
     */
    public void enqueueRealtime(String queueKey, int maxQueuedFrames, NetRealtimeDropPolicy dropPolicy,
            long expireAtMillis, Runnable dispatch) {
        RealtimeQueueState state = realtimeStates.get(queueKey);
        if (state == null) {
            RealtimeQueueState created = new RealtimeQueueState(queueKey);
            RealtimeQueueState existing = realtimeStates.putIfAbsent(queueKey, created);
            state = existing == null ? created : existing;
        }
        PendingFrame frame = new PendingFrame(Priority.REALTIME, dispatch, expireAtMillis, state);
        if (!state.offer(frame, maxQueuedFrames, dropPolicy)) {
            return;
        }
        enqueueInternal(frame);
    }

    /**
     * 清空全部待发送状态。
     */
    public void clear() {
        realtimeQueue.clear();
        controlQueue.clear();
        bulkQueue.clear();
        realtimeStates.clear();
    }

    private void enqueueInternal(PendingFrame frame) {
        queueOf(frame.priority).add(frame);
        drain();
    }

    private void drain() {
        while (draining.compareAndSet(false, true)) {
            try {
                PendingFrame next;
                while ((next = pollNext()) != null) {
                    if (next.shouldSkip(System.currentTimeMillis())) {
                        next.onDequeued();
                        continue;
                    }
                    next.onDequeued();
                    next.dispatch.run();
                }
            } finally {
                draining.set(false);
            }
            if (!hasPendingFrames()) {
                return;
            }
        }
    }

    private PendingFrame pollNext() {
        PendingFrame frame = realtimeQueue.poll();
        if (frame != null) {
            return frame;
        }
        frame = controlQueue.poll();
        if (frame != null) {
            return frame;
        }
        return bulkQueue.poll();
    }

    private boolean hasPendingFrames() {
        return !realtimeQueue.isEmpty() || !controlQueue.isEmpty() || !bulkQueue.isEmpty();
    }

    private Queue<PendingFrame> queueOf(Priority priority) {
        if (priority == Priority.REALTIME) {
            return realtimeQueue;
        }
        if (priority == Priority.CONTROL) {
            return controlQueue;
        }
        return bulkQueue;
    }

    /**
     * 出站优先级。
     */
    public enum Priority {
        REALTIME,
        CONTROL,
        BULK
    }

    private final class RealtimeQueueState {

        private final String queueKey;
        private final ArrayDeque<PendingFrame> frames = new ArrayDeque<PendingFrame>();

        private RealtimeQueueState(String queueKey) {
            this.queueKey = queueKey;
        }

        private synchronized boolean offer(PendingFrame incoming, int maxQueuedFrames, NetRealtimeDropPolicy dropPolicy) {
            while (frames.size() >= maxQueuedFrames) {
                if (dropPolicy == NetRealtimeDropPolicy.DROP_NEWEST) {
                    return false;
                }
                PendingFrame dropped = frames.pollFirst();
                if (dropped != null) {
                    dropped.markDropped();
                }
            }
            frames.addLast(incoming);
            return true;
        }

        private synchronized void onDequeued(PendingFrame frame) {
            if (!frames.remove(frame)) {
                return;
            }
            if (frames.isEmpty()) {
                realtimeStates.remove(queueKey, this);
            }
        }
    }

    private static final class PendingFrame {

        private final Priority priority;
        private final Runnable dispatch;
        private final long expireAtMillis;
        private final RealtimeQueueState realtimeState;
        private volatile boolean dropped;

        private PendingFrame(Priority priority, Runnable dispatch, long expireAtMillis,
                RealtimeQueueState realtimeState) {
            this.priority = priority;
            this.dispatch = dispatch;
            this.expireAtMillis = expireAtMillis;
            this.realtimeState = realtimeState;
        }

        private boolean shouldSkip(long nowMillis) {
            return dropped || expireAtMillis > 0L && nowMillis >= expireAtMillis;
        }

        private void markDropped() {
            this.dropped = true;
        }

        private void onDequeued() {
            if (realtimeState != null) {
                realtimeState.onDequeued(this);
            }
        }
    }
}
