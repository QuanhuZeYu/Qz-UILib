package club.heiqi.skija;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class DirectBufferPool {
    public static DirectBufferPool instance = new DirectBufferPool(8192*8192*4,2);

    private final BlockingQueue<ByteBuffer> pool;
    private final int bufferSize;
    private final long maxTotalBytes;
    private volatile long totalAllocatedBytes;

    public DirectBufferPool(int bufferSize, int maxBuffers) {
        this.bufferSize = bufferSize;
        this.maxTotalBytes = bufferSize * (long) maxBuffers;
        this.pool = new LinkedBlockingQueue<>(maxBuffers);
    }

    /**
     * 从池中获取一个 Direct Buffer
     */
    public ByteBuffer borrowBuffer() {
        // 尝试从池中获取
        ByteBuffer buffer = pool.poll();
        if (buffer != null) {
            return buffer;
        }

        // 池为空时，按需新建
        synchronized (this) {
            if (totalAllocatedBytes + bufferSize > maxTotalBytes) {
                throw new IllegalStateException("Direct buffer pool exhausted");
            }
            buffer = ByteBuffer.allocateDirect(bufferSize);
            totalAllocatedBytes += bufferSize;
            return buffer;
        }
    }

    /**
     * 归还 Direct Buffer 到池中
     */
    public void returnBuffer(ByteBuffer buffer) {
        if (buffer.isDirect() && buffer.capacity() == bufferSize) {
            buffer.clear();
            pool.offer(buffer);
        }
    }
}
