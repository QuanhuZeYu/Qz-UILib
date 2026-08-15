package club.heiqi.uilib.ui.image;

import java.util.Objects;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * 物品图标渲染的通用 GL 状态 scope：入口态快照与 {@code finally} 恢复。
 *
 * <p>LWJGL2 固定管线下 {@code glPushAttrib(GL_ALL_ATTRIB_BITS)} 覆盖服务器端属性组
 * （enable 状态、当前颜色、blend/alpha 函数、cull、shade model、光照参数、viewport 等），
 * 但不含纹理绑定、active texture 与矩阵栈内容。本 scope 参照
 * {@code FontRenderStateGuard} 的做法，手动快照并恢复：unit0 与入口 active unit 的
 * TEXTURE_2D 绑定、active texture 与矩阵模式；client 属性组
 * （顶点数组、array/element buffer 绑定、pixel store）经 {@code glPushClientAttrib} 覆盖。</p>
 *
 * <p>client-active texture 刻意不做保存与恢复：渲染核心只经 GLSM（GLStateManager）走 server 端
 * 纹理，不依赖 client-active unit；且该枚举在本环境（Angelica / Core Profile）的查询会走真实驱动
 * 返回 0 并产生 {@code GL_INVALID_ENUM}，写回 0 还会把 GLSM 内部 {@code clientActiveTextureUnit}
 * 污染为无效值，导致本帧后续 UV 属性路由失效（方块图标发黑的根因）。</p>
 *
 * <p>矩阵栈内容不在此 scope 内保存：绘制核心自身配对 push/pop matrix，异常路径由其
 * {@code finally} 恢复；本 scope 只负责把矩阵模式恢复到入口值。scope 不支持嵌套进入。</p>
 */
public final class GlStateScope {

    /** 可注入的最小 GL 状态访问面，供同包测试在不初始化 LWJGL 的情况下验证状态守恒。 */
    interface GlAccess {

        void pushAttrib(int mask);

        void popAttrib();

        void pushClientAttrib(int mask);

        void popClientAttrib();

        int getInteger(int name);

        void activeTexture(int unit);

        void bindTexture2d(int texture);

        void matrixMode(int mode);
    }

    /** 入口态快照：attrib 栈之外需要手动恢复的状态。 */
    private static final class SavedState {

        private int matrixMode;
        private int activeTexture;
        private int textureBinding2DOnTexture0;
        private int textureBinding2DOnActiveTexture;
    }

    private final GlAccess gl;
    private final SavedState saved = new SavedState();
    private boolean entered;

    /** 创建生产 LWJGL 状态 scope。 */
    public GlStateScope() {
        this(new LwjglGlAccess());
    }

    /** 创建使用指定状态访问面的 scope。 */
    GlStateScope(GlAccess gl) {
        if (gl == null) {
            throw new IllegalArgumentException("gl 不得为 null");
        }
        this.gl = gl;
    }

    /**
     * 在保护的 GL 状态边界中执行任务。
     *
     * <p>入口态在进入时快照，任务正常完成或抛出异常时都在 {@code finally} 恢复。</p>
     *
     * @param task 要执行的任务
     */
    public void run(Runnable task) {
        Objects.requireNonNull(task, "task");
        enter();
        try {
            task.run();
        } finally {
            exit();
        }
    }

    private void enter() {
        if (entered) {
            throw new IllegalStateException("GL 状态 scope 不支持嵌套进入");
        }
        gl.pushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        gl.pushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT | GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        saved.matrixMode = gl.getInteger(GL11.GL_MATRIX_MODE);
        saved.activeTexture = gl.getInteger(GL13.GL_ACTIVE_TEXTURE);
        gl.activeTexture(GL13.GL_TEXTURE0);
        saved.textureBinding2DOnTexture0 = gl.getInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (saved.activeTexture != GL13.GL_TEXTURE0) {
            gl.activeTexture(saved.activeTexture);
            saved.textureBinding2DOnActiveTexture = gl.getInteger(GL11.GL_TEXTURE_BINDING_2D);
        } else {
            saved.textureBinding2DOnActiveTexture = saved.textureBinding2DOnTexture0;
        }
        gl.activeTexture(saved.activeTexture);
        entered = true;
    }

    private void exit() {
        if (!entered) {
            throw new IllegalStateException("GL 状态恢复缺少对应的进入边界");
        }
        gl.activeTexture(GL13.GL_TEXTURE0);
        gl.bindTexture2d(saved.textureBinding2DOnTexture0);
        if (saved.activeTexture != GL13.GL_TEXTURE0) {
            gl.activeTexture(saved.activeTexture);
            gl.bindTexture2d(saved.textureBinding2DOnActiveTexture);
        }
        gl.activeTexture(saved.activeTexture);
        gl.popClientAttrib();
        gl.popAttrib();
        gl.matrixMode(saved.matrixMode);
        entered = false;
    }

    /** 生产 LWJGL2 状态访问器。 */
    private static final class LwjglGlAccess implements GlAccess {

        @Override
        public void pushAttrib(int mask) {
            GL11.glPushAttrib(mask);
        }

        @Override
        public void popAttrib() {
            GL11.glPopAttrib();
        }

        @Override
        public void pushClientAttrib(int mask) {
            GL11.glPushClientAttrib(mask);
        }

        @Override
        public void popClientAttrib() {
            GL11.glPopClientAttrib();
        }

        @Override
        public int getInteger(int name) {
            return GL11.glGetInteger(name);
        }

        @Override
        public void activeTexture(int unit) {
            GL13.glActiveTexture(unit);
        }

        @Override
        public void bindTexture2d(int texture) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        }

        @Override
        public void matrixMode(int mode) {
            GL11.glMatrixMode(mode);
        }
    }
}
