package club.heiqi.skija.state;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public enum PixelStoreParam {
    /**
     * 控制像素打包时是否交换字节顺序（GL_PACK_SWAP_BYTES）
     * 类型：布尔值（0或1），默认0（不交换）
     */
    PACK_SWAP_BYTES(GL11.GL_PACK_SWAP_BYTES),

    /**
     * 控制像素打包时是否低位优先（GL_PACK_LSB_FIRST）
     * 类型：布尔值，默认0（高位优先）
     */
    PACK_LSB_FIRST(GL11.GL_PACK_LSB_FIRST),

    /**
     * 指定像素数据在内存中的行长度（GL_PACK_ROW_LENGTH）
     * 类型：整数，默认0（使用实际宽度）
     */
    PACK_ROW_LENGTH(GL11.GL_PACK_ROW_LENGTH),

    /**
     * 指定3D纹理图像的高度（GL_PACK_IMAGE_HEIGHT）
     * 类型：整数，默认0（使用实际高度）
     */
    PACK_IMAGE_HEIGHT(GL12.GL_PACK_IMAGE_HEIGHT),

    /**
     * 指定每行起始跳过的像素数（GL_PACK_SKIP_PIXELS）
     * 类型：整数，默认0
     */
    PACK_SKIP_PIXELS(GL11.GL_PACK_SKIP_PIXELS),

    /**
     * 指定数据起始跳过的行数（GL_PACK_SKIP_ROWS）
     * 类型：整数，默认0
     */
    PACK_SKIP_ROWS(GL11.GL_PACK_SKIP_ROWS),

    /**
     * 指定3D纹理跳过的图像层数（GL_PACK_SKIP_IMAGES）
     * 类型：整数，默认0
     */
    PACK_SKIP_IMAGES(GL12.GL_PACK_SKIP_IMAGES),

    /**
     * 指定内存行对齐方式（GL_PACK_ALIGNMENT）
     * 类型：整数（1,2,4,8），默认4
     */
    PACK_ALIGNMENT(GL11.GL_PACK_ALIGNMENT),

    /**
     * 控制像素解包时是否交换字节顺序（GL_UNPACK_SWAP_BYTES）
     * 类型：布尔值，默认0
     */
    UNPACK_SWAP_BYTES(GL11.GL_UNPACK_SWAP_BYTES),

    /**
     * 控制像素解包时是否低位优先（GL_UNPACK_LSB_FIRST）
     * 类型：布尔值，默认0
     */
    UNPACK_LSB_FIRST(GL11.GL_UNPACK_LSB_FIRST),

    /**
     * 指定像素数据在内存中的行长度（GL_UNPACK_ROW_LENGTH）
     * 类型：整数，默认0（使用实际宽度）
     */
    UNPACK_ROW_LENGTH(GL11.GL_UNPACK_ROW_LENGTH),

    /**
     * 指定3D纹理图像的高度（GL_UNPACK_IMAGE_HEIGHT）
     * 类型：整数，默认0（使用实际高度）
     */
    UNPACK_IMAGE_HEIGHT(GL12.GL_UNPACK_IMAGE_HEIGHT),

    /**
     * 指定每行起始跳过的像素数（GL_UNPACK_SKIP_PIXELS）
     * 类型：整数，默认0
     */
    UNPACK_SKIP_PIXELS(GL11.GL_UNPACK_SKIP_PIXELS),

    /**
     * 指定数据起始跳过的行数（GL_UNPACK_SKIP_ROWS）
     * 类型：整数，默认0
     */
    UNPACK_SKIP_ROWS(GL11.GL_UNPACK_SKIP_ROWS),

    /**
     * 指定3D纹理跳过的图像层数（GL_UNPACK_SKIP_IMAGES）
     * 类型：整数，默认0
     */
    UNPACK_SKIP_IMAGES(GL12.GL_UNPACK_SKIP_IMAGES),

    /**
     * 指定内存行对齐方式（GL_UNPACK_ALIGNMENT）
     * 类型：整数（1,2,4,8），默认4
     */
    UNPACK_ALIGNMENT(GL11.GL_UNPACK_ALIGNMENT);

    private final int value;

    /**
     * 构造像素存储参数枚举
     * @param value 对应的OpenGL常量值
     */
    PixelStoreParam(int value) {
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }
}
