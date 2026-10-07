package club.heiqi.uilib.host.client.font.compat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** MC可选桥的小型宿主反射参数，不链接任一LWJGL BufferUtils。 */
final class CompatBuffers {
    private CompatBuffers(){}
    static FloatBuffer floats(int count){if(count<0||count>256)throw new IllegalArgumentException("兼容状态buffer越界");return ByteBuffer.allocateDirect(count*4).order(ByteOrder.nativeOrder()).asFloatBuffer();}
}
