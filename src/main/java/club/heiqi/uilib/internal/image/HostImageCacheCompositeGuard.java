package club.heiqi.uilib.internal.image;

import java.lang.reflect.Field;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import club.heiqi.uilib.ui.image.HostImageGlStateGuard;
import club.heiqi.uilib.ui.image.HostImageRenderOutcome;

/**
 * 缓存纹理回贴的窄 GL 围栏。
 *
 * <p>只保护 quad/Tessellator 实际触碰的 server enable/blend/color/texture、client vertex array
 * 与现代 program/buffer binding，不读取矩阵、viewport、FBO 或完整 item renderer 状态。</p>
 */
public final class HostImageCacheCompositeGuard {
    private final HostImageGlStateGuard delegate;

    public HostImageCacheCompositeGuard() {
        this(new LwjglCacheStateAccess());
    }

    HostImageCacheCompositeGuard(HostImageGlStateGuard.StateAccess stateAccess) {
        delegate = new HostImageGlStateGuard(stateAccess);
    }

    public HostImageRenderOutcome run(Runnable composite) {
        return delegate.run(composite);
    }

    /** cache composite 所需的最小固定管线与 Tessellator 状态访问面。 */
    static final class LwjglCacheStateAccess implements HostImageGlStateGuard.StateAccess {
        private static final int SERVER_MASK = GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT;
        private Field tessellatorDrawingField;

        @Override
        public boolean isTessellatorIdle() {
            try {
                if (tessellatorDrawingField == null) {
                    try {
                        tessellatorDrawingField = Tessellator.class.getDeclaredField("isDrawing");
                    } catch (NoSuchFieldException deobfuscatedMissing) {
                        tessellatorDrawingField = Tessellator.class.getDeclaredField("field_78415_z");
                    }
                    tessellatorDrawingField.setAccessible(true);
                }
                return !tessellatorDrawingField.getBoolean(Tessellator.instance);
            } catch (NoSuchFieldException failure) {
                return false;
            } catch (IllegalAccessException failure) {
                return false;
            } catch (SecurityException failure) {
                return false;
            }
        }

        @Override
        public int consumeGlError() {
            return GL11.glGetError();
        }

        @Override
        public HostImageGlStateGuard.Snapshot capture() {
            ContextCapabilities capabilities = GLContext.getCapabilities();
            CacheSnapshot snapshot = new CacheSnapshot();
            snapshot.hasGl13 = capabilities.OpenGL13;
            snapshot.hasGl15 = capabilities.OpenGL15;
            snapshot.hasGl20 = capabilities.OpenGL20;
            snapshot.hasGl30 = capabilities.OpenGL30;

            snapshot.serverAttribDepth = GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH);
            int probeError = consumeFirstGlError();
            if (isLegacyUnavailable(probeError)) {
                snapshot.unavailableReason = "server-attrib-stack-unavailable";
                return snapshot;
            }
            throwForGlError(probeError, "server-attrib-depth");

            snapshot.clientAttribDepth = GL11.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH);
            probeError = consumeFirstGlError();
            if (isLegacyUnavailable(probeError)) {
                snapshot.unavailableReason = "client-attrib-stack-unavailable";
                return snapshot;
            }
            throwForGlError(probeError, "client-attrib-depth");

            String bindingUnavailable = captureBindings(snapshot);
            if (bindingUnavailable != null) {
                snapshot.unavailableReason = bindingUnavailable;
                return snapshot;
            }
            try {
                GL11.glPushAttrib(SERVER_MASK);
                int pushError = consumeFirstGlError();
                if (isLegacyUnavailable(pushError)) {
                    snapshot.unavailableReason = "server-attrib-push-unavailable";
                    return snapshot;
                }
                throwForGlError(pushError, "server-attrib-push");
                snapshot.serverAttribPushed = true;

                GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
                pushError = consumeFirstGlError();
                if (pushError != GL11.GL_NO_ERROR) {
                    rollbackCapture(snapshot, null);
                    if (isLegacyUnavailable(pushError)) {
                        snapshot.unavailableReason = "client-attrib-push-unavailable";
                        return snapshot;
                    }
                    throwForGlError(pushError, "client-attrib-push");
                }
                snapshot.clientAttribPushed = true;
                snapshot.captured = true;
                return snapshot;
            } catch (RuntimeException failure) {
                rollbackCapture(snapshot, failure);
                throw failure;
            } catch (Error failure) {
                rollbackCapture(snapshot, failure);
                throw failure;
            }
        }

        @Override
        public String unavailableReason(HostImageGlStateGuard.Snapshot snapshot) {
            return ((CacheSnapshot) snapshot).unavailableReason;
        }

        @Override
        public void restore(HostImageGlStateGuard.Snapshot opaqueSnapshot) {
            CacheSnapshot snapshot = (CacheSnapshot) opaqueSnapshot;
            if (!snapshot.captured) {
                return;
            }
            Throwable[] failure = new Throwable[1];
            if (snapshot.clientAttribPushed) {
                restoreStep(failure, () -> normalizeAndPopClientAttrib(snapshot.clientAttribDepth));
            }
            if (snapshot.serverAttribPushed) {
                restoreStep(failure, () -> normalizeAndPopServerAttrib(snapshot.serverAttribDepth));
            }
            if (snapshot.hasGl30) {
                restoreStep(failure, () -> GL30.glBindVertexArray(snapshot.vao));
            }
            if (snapshot.hasGl15) {
                restoreStep(failure, () -> GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, snapshot.arrayBuffer));
                restoreStep(failure,
                        () -> GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, snapshot.elementBuffer));
            }
            if (snapshot.hasGl20) {
                restoreStep(failure, () -> GL20.glUseProgram(snapshot.program));
            }
            rethrow(failure[0]);
        }

        @Override
        public String findDrift(HostImageGlStateGuard.Snapshot opaqueSnapshot) {
            CacheSnapshot snapshot = (CacheSnapshot) opaqueSnapshot;
            if (GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH) != snapshot.serverAttribDepth) {
                return "server-attrib-depth";
            }
            if (GL11.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH) != snapshot.clientAttribDepth) {
                return "client-attrib-depth";
            }
            if (snapshot.hasGl13) {
                if (GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) != snapshot.activeTexture) {
                    return "active-texture";
                }
                if (GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE) != snapshot.clientActiveTexture) {
                    return "client-active-texture";
                }
            }
            if (GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) != snapshot.textureBinding) {
                return "texture-binding";
            }
            if (snapshot.hasGl20 && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != snapshot.program) {
                return "program";
            }
            if (snapshot.hasGl30 && GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) != snapshot.vao) {
                return "vao";
            }
            if (snapshot.hasGl15
                    && GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) != snapshot.arrayBuffer) {
                return "array-buffer";
            }
            if (snapshot.hasGl15
                    && GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING) != snapshot.elementBuffer) {
                return "element-buffer";
            }
            return null;
        }

        private static String captureBindings(CacheSnapshot snapshot) {
            if (snapshot.hasGl13) {
                snapshot.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
                throwForGlError(consumeFirstGlError(), "cache-server-active-texture-capture");
                snapshot.clientActiveTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
                int clientError = consumeFirstGlError();
                if (isLegacyUnavailable(clientError)) {
                    return "client-active-texture-unavailable";
                }
                throwForGlError(clientError, "cache-client-active-texture-capture");
            }
            snapshot.textureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            throwForGlError(consumeFirstGlError(), "cache-texture-binding-capture");
            if (snapshot.hasGl20) snapshot.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            if (snapshot.hasGl30) snapshot.vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            if (snapshot.hasGl15) {
                snapshot.arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
                snapshot.elementBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
            }
            throwForGlError(consumeFirstGlError(), "cache-binding-capture");
            return null;
        }

        private static void normalizeAndPopServerAttrib(int entryDepth) {
            int actual = GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH);
            int expected = entryDepth + 1;
            if (actual < expected) {
                throw new IllegalStateException("server attrib stack underflow " + actual + " < " + expected);
            }
            while (actual-- > expected) GL11.glPopAttrib();
            GL11.glPopAttrib();
        }

        private static void normalizeAndPopClientAttrib(int entryDepth) {
            int actual = GL11.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH);
            int expected = entryDepth + 1;
            if (actual < expected) {
                throw new IllegalStateException("client attrib stack underflow " + actual + " < " + expected);
            }
            while (actual-- > expected) GL11.glPopClientAttrib();
            GL11.glPopClientAttrib();
        }

        private static void rollbackCapture(CacheSnapshot snapshot, Throwable primaryFailure) {
            Throwable[] cleanupFailure = new Throwable[1];
            if (snapshot.clientAttribPushed) {
                restoreStep(cleanupFailure, () -> normalizeAndPopClientAttrib(snapshot.clientAttribDepth));
                snapshot.clientAttribPushed = false;
            }
            if (snapshot.serverAttribPushed) {
                restoreStep(cleanupFailure, () -> normalizeAndPopServerAttrib(snapshot.serverAttribDepth));
                snapshot.serverAttribPushed = false;
            }
            if (primaryFailure != null && cleanupFailure[0] != null && primaryFailure != cleanupFailure[0]) {
                if (isFatal(cleanupFailure[0]) && !isFatal(primaryFailure)) {
                    cleanupFailure[0].addSuppressed(primaryFailure);
                    rethrow(cleanupFailure[0]);
                }
                primaryFailure.addSuppressed(cleanupFailure[0]);
            } else if (primaryFailure == null && cleanupFailure[0] != null) {
                rethrow(cleanupFailure[0]);
            }
        }

        private static int consumeFirstGlError() {
            int first = GL11.GL_NO_ERROR;
            int error;
            while ((error = GL11.glGetError()) != GL11.GL_NO_ERROR) {
                if (first == GL11.GL_NO_ERROR || isLegacyUnavailable(first)
                        && !isLegacyUnavailable(error)) {
                    first = error;
                }
            }
            return first;
        }

        private static boolean isLegacyUnavailable(int error) {
            return error == GL11.GL_INVALID_ENUM || error == GL11.GL_INVALID_OPERATION;
        }

        private static void throwForGlError(int error, String operation) {
            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException(operation + " gl-error=" + error);
            }
        }

        private static void restoreStep(Throwable[] firstFailure, Runnable step) {
            try {
                step.run();
            } catch (RuntimeException failure) {
                remember(firstFailure, failure);
            } catch (Error failure) {
                remember(firstFailure, failure);
            }
        }

        private static void remember(Throwable[] firstFailure, Throwable failure) {
            if (firstFailure[0] == null) {
                firstFailure[0] = failure;
            } else if (isFatal(failure) && !isFatal(firstFailure[0])) {
                if (firstFailure[0] != failure) failure.addSuppressed(firstFailure[0]);
                firstFailure[0] = failure;
            } else if (firstFailure[0] != failure) {
                firstFailure[0].addSuppressed(failure);
            }
        }

        private static boolean isFatal(Throwable failure) {
            return failure instanceof Error && !(failure instanceof LinkageError);
        }

        private static void rethrow(Throwable failure) {
            if (failure == null) return;
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            if (failure instanceof Error) throw (Error) failure;
            throw new IllegalStateException("cache composite state restore failed", failure);
        }
    }

    private static final class CacheSnapshot implements HostImageGlStateGuard.Snapshot {
        private boolean hasGl13;
        private boolean hasGl15;
        private boolean hasGl20;
        private boolean hasGl30;
        private boolean captured;
        private boolean serverAttribPushed;
        private boolean clientAttribPushed;
        private String unavailableReason;
        private int serverAttribDepth;
        private int clientAttribDepth;
        private int activeTexture;
        private int clientActiveTexture;
        private int textureBinding;
        private int program;
        private int vao;
        private int arrayBuffer;
        private int elementBuffer;
    }
}
