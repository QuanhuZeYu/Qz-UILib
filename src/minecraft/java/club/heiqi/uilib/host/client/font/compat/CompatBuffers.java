package club.heiqi.uilib.host.client.font.compat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** MC可选桥的小型宿主反射参数，不链接任一LWJGL BufferUtils。 */
public final class CompatBuffers {
    private CompatBuffers() {}

    // 每个兼容GL线程只保留一个有界scratch；调用方立即查询/上传，快照只能保存复制后的值。
    private static final ThreadLocal<FloatBuffer> FLOATS = ThreadLocal.withInitial(
            () -> ByteBuffer.allocateDirect(256 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer());

    public static FloatBuffer floats(int count) {
        if (count < 0 || count > 256) throw new IllegalArgumentException("兼容状态buffer越界");
        FloatBuffer buffer = FLOATS.get();
        buffer.clear();
        // LWJGL2的glGetFloat按最大状态长度检查remaining，小颜色向量也要求16元素。
        buffer.limit(Math.max(16, count));
        return buffer;
    }
}
