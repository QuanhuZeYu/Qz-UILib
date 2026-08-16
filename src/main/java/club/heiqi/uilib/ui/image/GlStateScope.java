package club.heiqi.uilib.ui.image;

import java.util.Objects;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * 物品图标渲染的通用 GL 状态 scope：入口态快照与 {@code finally} 恢复。
 *
 * <p>LWJGL2 固定管线下 {@code glPushAttrib(GL_ALL_ATTRIB_BITS)} 覆盖服务器端属性组
 * （enable 状态、当前颜色、blend/alpha 函数、cull、shade model、光照参数、viewport 等），
 * 但不含纹理绑定、active texture、client-active texture 与矩阵栈内容。本 scope 参照
 * {@code FontRenderStateGuard} 的做法，手动快照并恢复：unit0 与入口 active unit 的
 * TEXTURE_2D 绑定、active texture、client-active texture 与矩阵模式；client 属性组
 * （顶点数组、array/element buffer 绑定、pixel store）经 {@code glPushClientAttrib} 覆盖。</p>
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

        void clientActiveTexture(int unit);

        void matrixMode(int mode);
    }

    /** 入口态快照：attrib 栈之外需要手动恢复的状态。 */
    private static final class SavedState {

        private int matrixMode;
        private int activeTexture;
        private int clientActiveTexture;
        private int textureBinding2DOnTexture0;
        private int textureBinding2DOnActiveTexture;
    }

    private final GlAccess gl;
    private final SavedState saved = new SavedState();
    private boolean entered;
    private int ambientDepthBeforeEnter = -1;

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
        boolean attribPushed = false;
        boolean clientAttribPushed = false;
        ambientDepthBeforeEnter = club.heiqi.uilib.util.GlAttribDepth.current();
        try {
            gl.pushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            attribPushed = true;
            gl.pushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT | GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            clientAttribPushed = true;
            saved.matrixMode = gl.getInteger(GL11.GL_MATRIX_MODE);
            saved.activeTexture = gl.getInteger(GL13.GL_ACTIVE_TEXTURE);
            saved.clientActiveTexture = -1;
            try {
                saved.clientActiveTexture = gl.getInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
            } catch (RuntimeException ignored) {
                // core profile 后端可能不支持 GL_CLIENT_ACTIVE_TEXTURE 查询：
                // 保留 -1，exit 时跳过恢复。
            } catch (LinkageError ignored) {
                // 同上。
            }
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
        } catch (RuntimeException exception) {
            rollbackEnter(clientAttribPushed, attribPushed);
            throw exception;
        } catch (LinkageError error) {
            rollbackEnter(clientAttribPushed, attribPushed);
            throw error;
        } catch (Error error) {
            rollbackEnter(clientAttribPushed, attribPushed);
            throw error;
        }
    }

    /** enter 中途失败时回滚已压入的 attrib/client attrib 栈，避免状态泄漏。 */
    private void rollbackEnter(boolean clientAttribPushed, boolean attribPushed) {
        if (clientAttribPushed) {
            try {
                gl.popClientAttrib();
            } catch (RuntimeException ignored) {
                // 回滚失败保留原始异常，不再抛出。
            } catch (LinkageError ignored) {
                // 同上。
            }
        }
        if (attribPushed) {
            try {
                gl.popAttrib();
            } catch (RuntimeException ignored) {
                // 同上。
            } catch (LinkageError ignored) {
                // 同上。
            }
        }
    }

    private void exit() {
        if (!entered) {
            throw new IllegalStateException("GL 状态恢复缺少对应的进入边界");
        }
        Throwable failure = null;
        // 栈平衡优先：popClientAttrib / popAttrib 必须先于其它恢复步骤执行；
        // 后续步骤失败只记录，绝不阻断弹出（防止 attrib 栈泄漏累积）。
        failure = recordFailure(failure, new Runnable() {
            @Override
            public void run() {
                gl.popClientAttrib();
            }
        });
        failure = recordFailure(failure, new Runnable() {
            @Override
            public void run() {
                gl.popAttrib();
            }
        });
        failure = recordFailure(failure, new Runnable() {
            @Override
            public void run() {
                gl.activeTexture(GL13.GL_TEXTURE0);
            }
        });
        failure = recordFailure(failure, new Runnable() {
            @Override
            public void run() {
                gl.bindTexture2d(saved.textureBinding2DOnTexture0);
            }
        });
        if (saved.activeTexture != GL13.GL_TEXTURE0) {
            failure = recordFailure(failure, new Runnable() {
                @Override
                public void run() {
                    gl.activeTexture(saved.activeTexture);
                }
            });
            failure = recordFailure(failure, new Runnable() {
                @Override
                public void run() {
                    gl.bindTexture2d(saved.textureBinding2DOnActiveTexture);
                }
            });
        }
        failure = recordFailure(failure, new Runnable() {
            @Override
            public void run() {
                gl.activeTexture(saved.activeTexture);
            }
        });
        if (saved.clientActiveTexture >= 0) {
            failure = recordFailure(failure, new Runnable() {
                @Override
                public void run() {
                    gl.clientActiveTexture(saved.clientActiveTexture);
                }
            });
        }
        failure = recordFailure(failure, new Runnable() {
            @Override
            public void run() {
                gl.matrixMode(saved.matrixMode);
            }
        });
        // 围堵第三方渲染路径（如 FFP 变体编译）泄漏的 attrib 栈深度。
        club.heiqi.uilib.util.GlAttribDepth.popExcess(ambientDepthBeforeEnter);
        ambientDepthBeforeEnter = -1;
        entered = false;
        rethrow(failure);
    }

    /** 执行一步恢复并记录失败（不中断后续步骤）。 */
    private Throwable recordFailure(Throwable failure, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException exception) {
            return appendFailure(failure, exception);
        } catch (LinkageError error) {
            return appendFailure(failure, error);
        }
        return failure;
    }

    private static Throwable appendFailure(Throwable primary, Throwable additional) {
        if (primary == null) {
            return additional;
        }
        primary.addSuppressed(additional);
        return primary;
    }

    private static void rethrow(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof LinkageError) {
            throw (LinkageError) failure;
        }
        throw new RuntimeException(failure);
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
        public void clientActiveTexture(int unit) {
            GL13.glClientActiveTexture(unit);
        }

        @Override
        public void matrixMode(int mode) {
            GL11.glMatrixMode(mode);
        }
    }
}
