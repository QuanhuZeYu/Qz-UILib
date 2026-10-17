package club.heiqi.uilib.host.desktop;

import static org.lwjgl.opengl.GL11.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/** 只负责当前GL_BACK的同步读回与RGBA/上下翻转；回放、场景像素断言及文件IO由调用者负责。 */
final class DesktopTestReadback {
    private DesktopTestReadback() {}
    static BufferedImage readBack(DesktopWindow window) {return read(window,GL_BACK);}
    static BufferedImage readFront(DesktopWindow window) {return read(window,GL_FRONT);}
    private static BufferedImage read(DesktopWindow window,int buffer) {
        int previous=glGetInteger(GL_READ_BUFFER);
        int width = window.width(), height = window.height();
        glFinish();
        ByteBuffer bytes = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 4));
        try {
            glReadBuffer(buffer);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("GL回放/读回报错");
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int p = (y * width + x) * 4;
                int argb = ((bytes.get(p + 3) & 255) << 24) | ((bytes.get(p) & 255) << 16)
                        | ((bytes.get(p + 1) & 255) << 8) | (bytes.get(p + 2) & 255);
                image.setRGB(x, height - y - 1, argb);
            }
            return image;
        } finally { glReadBuffer(previous);MemoryUtil.memFree(bytes); }
    }
}
