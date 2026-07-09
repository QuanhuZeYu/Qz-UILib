package club.heiqi.uilib.ui.render;

import java.nio.IntBuffer;

/**
 * clip 栈对 OpenGL 的最小读写面，便于纯 JVM 测试替换实现。
 *
 * <p>生产默认 {@link RealClipGlOps}；测试可安装记录型替身，验证
 * clear 与 restore baseline 语义分叉。</p>
 */
interface ClipGlOps {

    boolean isEnabled(int cap);

    void enable(int cap);

    void disable(int cap);

    /**
     * 向量查询（如 {@code GL_SCISSOR_BOX}）。
     *
     * @param pname 参数名
     * @param params 输出缓冲
     */
    void getIntegers(int pname, IntBuffer params);

    /**
     * 标量查询（如 stencil func/ref/mask/op）。
     *
     * @param pname 参数名
     * @return 当前值
     */
    int getInteger(int pname);

    void scissor(int x, int y, int width, int height);

    void stencilFunc(int func, int ref, int mask);

    void stencilOp(int fail, int zfail, int zpass);

    void stencilMask(int mask);

    void colorMask(boolean red, boolean green, boolean blue, boolean alpha);

    void depthMask(boolean flag);
}
