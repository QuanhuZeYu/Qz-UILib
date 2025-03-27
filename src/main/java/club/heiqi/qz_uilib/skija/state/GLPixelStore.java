package club.heiqi.qz_uilib.skija.state;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL42.*;

public class GLPixelStore {
    // Pack parameters
    private int packSwapBytes;
    private int packLsbFirst;
    private int packRowLength;
    private int packImageHeight;
    private int packSkipRows;
    private int packSkipPixels;
    private int packSkipImages;
    private int packAlignment;

    // Unpack parameters
    private int unpackSwapBytes;
    private int unpackLsbFirst;
    private int unpackRowLength;
    private int unpackImageHeight;
    private int unpackSkipRows;
    private int unpackSkipPixels;
    private int unpackSkipImages;
    private int unpackAlignment;
    private int unpackCompressedBlockWidth;
    private int unpackCompressedBlockHeight;
    private int unpackCompressedBlockDepth;
    private int unpackCompressedBlockSize;

    /**
     * 备份当前OpenGL状态
     * <p>
     * 执行流程：
     * 1. 保存客户端和服务端的属性堆栈（深度测试、面剔除等状态）
     * 2. 备份纹理相关状态（激活纹理单元、着色器程序等）
     * 3. 备份所有像素存储参数（像素传输设置）
     * 4. 备份混合状态参数（混合因子和混合方程）
     */
    public void backup() {
        // Backup pack parameters
        packSwapBytes = glGetInteger(GL_PACK_SWAP_BYTES);
        packLsbFirst = glGetInteger(GL_PACK_LSB_FIRST);
        packRowLength = glGetInteger(GL_PACK_ROW_LENGTH);
        packImageHeight = glGetInteger(GL_PACK_IMAGE_HEIGHT);
        packSkipRows = glGetInteger(GL_PACK_SKIP_ROWS);
        packSkipPixels = glGetInteger(GL_PACK_SKIP_PIXELS);
        packSkipImages = glGetInteger(GL_PACK_SKIP_IMAGES);
        packAlignment = glGetInteger(GL_PACK_ALIGNMENT);

        // Backup unpack parameters
        unpackSwapBytes = glGetInteger(GL_UNPACK_SWAP_BYTES);
        unpackLsbFirst = glGetInteger(GL_UNPACK_LSB_FIRST);
        unpackRowLength = glGetInteger(GL_UNPACK_ROW_LENGTH);
        unpackImageHeight = glGetInteger(GL_UNPACK_IMAGE_HEIGHT);
        unpackSkipRows = glGetInteger(GL_UNPACK_SKIP_ROWS);
        unpackSkipPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS);
        unpackSkipImages = glGetInteger(GL_UNPACK_SKIP_IMAGES);
        unpackAlignment = glGetInteger(GL_UNPACK_ALIGNMENT);

        // Compression parameters (OpenGL 4.2+)
        if (glGetInteger(GL_UNPACK_COMPRESSED_BLOCK_WIDTH) != -1) {
            unpackCompressedBlockWidth = glGetInteger(GL_UNPACK_COMPRESSED_BLOCK_WIDTH);
            unpackCompressedBlockHeight = glGetInteger(GL_UNPACK_COMPRESSED_BLOCK_HEIGHT);
            unpackCompressedBlockDepth = glGetInteger(GL_UNPACK_COMPRESSED_BLOCK_DEPTH);
            unpackCompressedBlockSize = glGetInteger(GL_UNPACK_COMPRESSED_BLOCK_SIZE);
        }
    }

    /**
     * 恢复之前备份的OpenGL状态
     * <p>
     * 执行流程：
     * 1. 恢复属性堆栈
     * 2. 恢复纹理相关状态
     * 3. 恢复混合状态
     * 4. 恢复像素存储参数
     */
    public void restore() {
        // Restore pack parameters
        glPixelStorei(GL_PACK_SWAP_BYTES, packSwapBytes);
        glPixelStorei(GL_PACK_LSB_FIRST, packLsbFirst);
        glPixelStorei(GL_PACK_ROW_LENGTH, packRowLength);
        glPixelStorei(GL_PACK_IMAGE_HEIGHT, packImageHeight);
        glPixelStorei(GL_PACK_SKIP_ROWS, packSkipRows);
        glPixelStorei(GL_PACK_SKIP_PIXELS, packSkipPixels);
        glPixelStorei(GL_PACK_SKIP_IMAGES, packSkipImages);
        glPixelStorei(GL_PACK_ALIGNMENT, packAlignment);

        // Restore unpack parameters
        glPixelStorei(GL_UNPACK_SWAP_BYTES, unpackSwapBytes);
        glPixelStorei(GL_UNPACK_LSB_FIRST, unpackLsbFirst);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, unpackRowLength);
        glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, unpackImageHeight);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, unpackSkipRows);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
        glPixelStorei(GL_UNPACK_SKIP_IMAGES, unpackSkipImages);
        glPixelStorei(GL_UNPACK_ALIGNMENT, unpackAlignment);

        // Restore compression parameters
        if (unpackCompressedBlockWidth != -1) {
            glPixelStorei(GL_UNPACK_COMPRESSED_BLOCK_WIDTH, unpackCompressedBlockWidth);
            glPixelStorei(GL_UNPACK_COMPRESSED_BLOCK_HEIGHT, unpackCompressedBlockHeight);
            glPixelStorei(GL_UNPACK_COMPRESSED_BLOCK_DEPTH, unpackCompressedBlockDepth);
            glPixelStorei(GL_UNPACK_COMPRESSED_BLOCK_SIZE, unpackCompressedBlockSize);
        }
    }
}
