package club.heiqi.uilib.font.render.software;

/**
 * 一页字符页纹理的 CPU 侧快照。
 */
public final class SoftwarePageTexture {

    private final int size;
    private final int[] argb;

    /**
     * @param size 纹理边长（像素）
     * @param argb ARGB 像素，长度必须为 size×size
     */
    public SoftwarePageTexture(int size, int[] argb) {
        if (size <= 0 || argb == null || argb.length != size * size) {
            throw new IllegalArgumentException("软件页纹理尺寸与像素长度不一致");
        }
        this.size = size;
        this.argb = argb;
    }

    /** 纹理边长。 */
    public int getSize() {
        return size;
    }

    /** ARGB 像素缓冲（只读语义，调用方不得修改）。 */
    public int[] getArgb() {
        return argb;
    }
}
