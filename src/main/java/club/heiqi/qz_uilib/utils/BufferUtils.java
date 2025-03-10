package club.heiqi.qz_uilib.utils;

import java.nio.ByteBuffer;

public class BufferUtils {

    /**
     * 转换图像缓冲区的通道顺序（例如 RGBA ↔ BGRA）
     * @param inputBuffer 原始缓冲区（必须是 RGBA/BGRA 格式，每像素 4 字节）
     * @return 转换后的缓冲区（直接缓冲区，与原数据 R/B 互换）
     */
    public static ByteBuffer swapRedBlueChannels(ByteBuffer inputBuffer) {
        // 创建新的直接缓冲区（与原缓冲区大小相同）
        ByteBuffer outputBuffer = ByteBuffer.allocateDirect(inputBuffer.capacity());

        // 将输入缓冲区位置重置到起始点
        inputBuffer.rewind();

        // 遍历所有像素（每4字节为一组）
        while (inputBuffer.hasRemaining()) {
            byte r = inputBuffer.get(); // 原 R 分量
            byte g = inputBuffer.get(); // 原 G 分量
            byte b = inputBuffer.get(); // 原 B 分量
            byte a = inputBuffer.get(); // 原 A 分量

            // 交换 R 和 B 通道
            outputBuffer.put(b); // 新 R 分量（原 B）
            outputBuffer.put(g); // 新 G 分量（不变）
            outputBuffer.put(r); // 新 B 分量（原 R）
            outputBuffer.put(a); // 新 A 分量（不变）
        }

        // 重置缓冲区位置以便后续使用
        inputBuffer.rewind();
        outputBuffer.flip();

        return outputBuffer;
    }
}
