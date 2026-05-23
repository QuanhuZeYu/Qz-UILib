package club.heiqi.uilib.net.core;

/**
 * 网络层大小策略。
 */
public final class NetPayloadLimits {

    /** 原生 C17 与无扩展环境的兼容物理帧下限。 */
    public static final int COMPAT_PHYSICAL_FRAME_LIMIT = 32766;

    /** 普通逻辑消息进入大消息提示路径的阈值。 */
    public static final int LARGE_MESSAGE_WARN_THRESHOLD = 8 * 1024 * 1024;

    /** Channel / Fetch / Store 普通逻辑消息默认上限。 */
    public static final int DEFAULT_LOGICAL_MESSAGE_LIMIT = 16 * 1024 * 1024;

    /** GTNH/Hodgepodge 环境默认可理解的物理包能力。 */
    public static final int GTNH_DEFAULT_PHYSICAL_LIMIT = 256 * 1024 * 1024;

    /** 物理包硬上限。 */
    public static final int GTNH_HARD_PHYSICAL_LIMIT = 1024 * 1024 * 1024;

    private NetPayloadLimits() {}

    /**
     * 校验普通逻辑消息大小。
     *
     * @param bytes 字节数
     */
    public static void requireLogicalMessageSize(int bytes) {
        if (bytes > DEFAULT_LOGICAL_MESSAGE_LIMIT) {
            throw new IllegalArgumentException("普通网络消息 " + bytes + " bytes 超过默认逻辑上限 "
                    + DEFAULT_LOGICAL_MESSAGE_LIMIT + " bytes，请改用 stream/chunk 大内容路径");
        }
    }

    /**
     * 归一化物理帧能力。
     *
     * @param requested 请求值
     * @return 限制后的值
     */
    public static int clampPhysicalLimit(int requested) {
        if (requested < COMPAT_PHYSICAL_FRAME_LIMIT) {
            return COMPAT_PHYSICAL_FRAME_LIMIT;
        }
        if (requested > GTNH_HARD_PHYSICAL_LIMIT) {
            return GTNH_HARD_PHYSICAL_LIMIT;
        }
        return requested;
    }
}
