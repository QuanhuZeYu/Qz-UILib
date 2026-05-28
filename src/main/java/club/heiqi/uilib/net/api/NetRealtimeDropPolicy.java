package club.heiqi.uilib.net.api;

/**
 * 实时帧队列满时的丢弃策略。
 */
public enum NetRealtimeDropPolicy {

    /**
     * 丢弃队列中最旧的待发送帧，优先保留新鲜帧。
     */
    DROP_OLDEST,

    /**
     * 丢弃当前新入队的帧，保留队列中已等待的帧。
     */
    DROP_NEWEST
}
