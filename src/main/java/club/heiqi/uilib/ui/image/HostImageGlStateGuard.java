package club.heiqi.uilib.ui.image;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.function.IntSupplier;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * 不可信 ItemStack renderer 的完整、可验证状态围栏。
 *
 * <p>测试通过 {@link StateAccess} 注入确定状态；生产访问器按当前 context 能力才调用可选 API。</p>
 */
public final class HostImageGlStateGuard {
    private static final int CLIENT_ALL_ATTRIB_BITS = -1;
    private static final AttribStackOperations SERVER_ATTRIB_OPERATIONS = new LwjglAttribStackOperations(false);
    private static final AttribStackOperations CLIENT_ATTRIB_OPERATIONS = new LwjglAttribStackOperations(true);
    private static final TextureBindingOperations TEXTURE_BINDING_OPERATIONS = new LwjglTextureBindingOperations();
    /** 不透明状态快照标记。 */
    public interface Snapshot { }

    /** 状态读取、恢复与验证缝。 */
    public interface StateAccess {
        boolean isTessellatorIdle();
        int consumeGlError();
        Snapshot capture();
        void restore(Snapshot snapshot);
        String findDrift(Snapshot snapshot);
        default String unavailableReason(Snapshot snapshot) { return null; }
    }

    private final StateAccess stateAccess;

    /** 创建生产 GL 访问器围栏。 */
    public HostImageGlStateGuard() { this(new LwjglStateAccess()); }

    /** 创建注入访问器的围栏。 */
    public HostImageGlStateGuard(StateAccess stateAccess) {
        if (stateAccess == null) throw new IllegalArgumentException("stateAccess");
        this.stateAccess = stateAccess;
    }

    /** 执行 renderer，并在返回前恢复及验证入口状态。 */
    public HostImageRenderOutcome run(Runnable renderer) {
        if (HostImageGlErrorTracker.isActive()) {
            throw new IllegalStateException("HostImage GL guard reentry");
        }
        if (renderer == null) {
            return HostImageRenderOutcome.unavailable("precheck", null, "missing-renderer");
        }
        if (!stateAccess.isTessellatorIdle()) {
            return HostImageRenderOutcome.hostStateLost("precheck", null, "tessellator-not-idle");
        }
        int entryError = consumeEntryErrors();
        if (entryError != GL11.GL_NO_ERROR) {
            return HostImageRenderOutcome.hostStateLost("precheck", null, "entry-gl-error=" + entryError);
        }
        HostImageGlErrorTracker.begin(stateAccess::consumeGlError);
        try {
            Snapshot snapshot;
            HostImageGlErrorTracker.enterPhase("capture");
            try {
                snapshot = stateAccess.capture();
            } catch (RuntimeException exception) {
                HostImageGlErrorTracker.checkpoint("capture.exception");
                return failureWithTrackedError("capture", exception, "capture-failed");
            } catch (LinkageError error) {
                HostImageGlErrorTracker.checkpoint("capture.exception");
                return failureWithTrackedError("capture", error, "capture-linkage");
            } catch (Error error) {
                checkpointSuppressingFailure("capture.exception", error);
                throw error;
            }
            try {
                HostImageGlErrorTracker.checkpoint("capture.complete");
            } catch (RuntimeException exception) {
                return failAfterCapturedSnapshot(snapshot, "capture", exception, "capture-checkpoint-failed");
            } catch (LinkageError error) {
                return failAfterCapturedSnapshot(snapshot, "capture", error, "capture-checkpoint-linkage");
            } catch (Error error) {
                rethrowFatalAfterCapturedSnapshot(snapshot, error);
                throw error;
            }

            HostImageGlErrorTracker.FirstError captureError = HostImageGlErrorTracker.firstError();
            String unavailableReason;
            try {
                unavailableReason = captureError == null ? stateAccess.unavailableReason(snapshot) : null;
            } catch (RuntimeException exception) {
                return failAfterCapturedSnapshot(snapshot, "capture", exception, "capability-check-failed");
            } catch (LinkageError error) {
                return failAfterCapturedSnapshot(snapshot, "capture", error, "capability-check-linkage");
            } catch (Error error) {
                rethrowFatalAfterCapturedSnapshot(snapshot, error);
                throw error;
            }
            if (captureError != null || unavailableReason != null) {
                HostImageGlErrorTracker.enterPhase("restore");
                Throwable restoreFailure = null;
                try {
                    stateAccess.restore(snapshot);
                    HostImageGlErrorTracker.checkpoint("restore.rejected-capture");
                } catch (RuntimeException exception) {
                    restoreFailure = exception;
                    HostImageGlErrorTracker.checkpoint("restore.rejected-capture-exception");
                } catch (LinkageError error) {
                    restoreFailure = error;
                    HostImageGlErrorTracker.checkpoint("restore.rejected-capture-exception");
                } catch (Error error) {
                    checkpointSuppressingFailure("restore.rejected-capture-exception", error);
                    throw error;
                }
                HostImageGlErrorTracker.FirstError firstError = HostImageGlErrorTracker.firstError();
                if (captureError != null || restoreFailure != null || firstError != null) {
                    HostImageGlErrorTracker.FirstError evidence = captureError != null ? captureError : firstError;
                    return HostImageRenderOutcome.hostStateLost(
                            evidence == null ? "restore" : evidence.getPhase(), restoreFailure,
                            evidence == null ? "rejected-capture-restore-failed" : evidence.detail());
                }
                return HostImageRenderOutcome.unavailable("capture", null, unavailableReason);
            }

            Throwable renderFailure = null;
            Error fatalRenderError = null;
            HostImageGlErrorTracker.enterPhase("delegate");
            try {
                renderer.run();
            } catch (RuntimeException exception) {
                renderFailure = exception;
            } catch (LinkageError error) {
                renderFailure = error;
            } catch (Error error) {
                renderFailure = error;
                fatalRenderError = error;
            }
            try {
                HostImageGlErrorTracker.checkpoint("delegate.complete");
            } catch (RuntimeException exception) {
                if (fatalRenderError != null) {
                    fatalRenderError.addSuppressed(exception);
                    renderFailure = fatalRenderError;
                } else {
                    if (renderFailure != null && renderFailure != exception) exception.addSuppressed(renderFailure);
                    renderFailure = exception;
                }
            } catch (LinkageError error) {
                if (fatalRenderError != null) {
                    if (fatalRenderError != error) fatalRenderError.addSuppressed(error);
                    renderFailure = fatalRenderError;
                } else {
                    if (renderFailure != null && renderFailure != error) error.addSuppressed(renderFailure);
                    renderFailure = error;
                }
            } catch (Error error) {
                if (fatalRenderError != null) {
                    if (fatalRenderError != error) fatalRenderError.addSuppressed(error);
                    renderFailure = fatalRenderError;
                } else {
                    if (renderFailure != null && renderFailure != error) error.addSuppressed(renderFailure);
                    renderFailure = error;
                    fatalRenderError = error;
                }
            }

            HostImageGlErrorTracker.enterPhase("restore");
            try {
                stateAccess.restore(snapshot);
                HostImageGlErrorTracker.checkpoint("restore.complete");
            } catch (RuntimeException exception) {
                if (fatalRenderError != null) {
                    fatalRenderError.addSuppressed(exception);
                    checkpointSuppressingFailure("restore.exception", fatalRenderError);
                    throw fatalRenderError;
                }
                if (renderFailure != null && renderFailure != exception) exception.addSuppressed(renderFailure);
                HostImageGlErrorTracker.checkpoint("restore.exception");
                return failureWithTrackedError("restore", exception, "restore-failed");
            } catch (LinkageError error) {
                if (fatalRenderError != null) {
                    if (fatalRenderError != error) fatalRenderError.addSuppressed(error);
                    checkpointSuppressingFailure("restore.exception", fatalRenderError);
                    throw fatalRenderError;
                }
                if (renderFailure != null && renderFailure != error) error.addSuppressed(renderFailure);
                HostImageGlErrorTracker.checkpoint("restore.exception");
                return failureWithTrackedError("restore", error, "restore-linkage");
            } catch (Error error) {
                if (fatalRenderError != null) {
                    if (fatalRenderError != error) fatalRenderError.addSuppressed(error);
                    checkpointSuppressingFailure("restore.exception", fatalRenderError);
                    throw fatalRenderError;
                }
                if (renderFailure != null && renderFailure != error) error.addSuppressed(renderFailure);
                checkpointSuppressingFailure("restore.exception", error);
                throw error;
            }

            HostImageGlErrorTracker.enterPhase("verify");
            String drift;
            boolean tessellatorIdle;
            try {
                drift = stateAccess.findDrift(snapshot);
                HostImageGlErrorTracker.checkpoint("verify.find-drift");
                tessellatorIdle = stateAccess.isTessellatorIdle();
                HostImageGlErrorTracker.checkpoint("verify.complete");
            } catch (RuntimeException exception) {
                if (fatalRenderError != null) {
                    fatalRenderError.addSuppressed(exception);
                    checkpointSuppressingFailure("verify.exception", fatalRenderError);
                    throw fatalRenderError;
                }
                HostImageGlErrorTracker.checkpoint("verify.exception");
                return failureWithTrackedError("verify", exception, "verify-failed");
            } catch (LinkageError error) {
                if (fatalRenderError != null) {
                    if (fatalRenderError != error) fatalRenderError.addSuppressed(error);
                    checkpointSuppressingFailure("verify.exception", fatalRenderError);
                    throw fatalRenderError;
                }
                HostImageGlErrorTracker.checkpoint("verify.exception");
                return failureWithTrackedError("verify", error, "verify-linkage");
            } catch (Error error) {
                if (fatalRenderError != null) {
                    if (fatalRenderError != error) fatalRenderError.addSuppressed(error);
                    checkpointSuppressingFailure("verify.exception", fatalRenderError);
                    throw fatalRenderError;
                }
                if (renderFailure != null && renderFailure != error) error.addSuppressed(renderFailure);
                checkpointSuppressingFailure("verify.exception", error);
                throw error;
            }
            HostImageGlErrorTracker.FirstError firstError = HostImageGlErrorTracker.firstError();
            boolean recovered = drift == null && firstError == null && tessellatorIdle;
            if (fatalRenderError != null) {
                if (firstError != null) {
                    fatalRenderError.addSuppressed(new IllegalStateException(firstError.detail()));
                } else if (!recovered) {
                    fatalRenderError.addSuppressed(new IllegalStateException(
                            drift != null ? drift : "tessellator-not-idle"));
                }
                throw fatalRenderError;
            }
            if (firstError != null) {
                return HostImageRenderOutcome.hostStateLost(firstError.getPhase(), renderFailure,
                        firstError.detail());
            }
            if (!recovered) {
                return HostImageRenderOutcome.hostStateLost("verify", renderFailure,
                        drift != null ? drift : "tessellator-not-idle");
            }
            if (renderFailure != null) {
                return HostImageRenderOutcome.unavailable("render", renderFailure, "renderer-failed");
            }
            return HostImageRenderOutcome.publishable();
        } finally {
            HostImageGlErrorTracker.end();
        }
    }

    /** 入口必须完整排空既有错误队列，只保留首错作为本次拒绝证据。 */
    private int consumeEntryErrors() {
        int first = GL11.GL_NO_ERROR;
        int error;
        while ((error = stateAccess.consumeGlError()) != GL11.GL_NO_ERROR) {
            if (first == GL11.GL_NO_ERROR) {
                first = error;
            }
        }
        return first;
    }

    /** capture 已成功后发生协议异常时仍必须先恢复快照，再返回不可恢复结果。 */
    private HostImageRenderOutcome failAfterCapturedSnapshot(Snapshot snapshot, String stage,
            Throwable failure, String detail) {
        HostImageGlErrorTracker.enterPhase("restore");
        try {
            stateAccess.restore(snapshot);
            HostImageGlErrorTracker.checkpoint("restore.after-capture-failure");
        } catch (RuntimeException restoreFailure) {
            if (restoreFailure != failure) restoreFailure.addSuppressed(failure);
            HostImageGlErrorTracker.checkpoint("restore.after-capture-failure-exception");
            return failureWithTrackedError("restore", restoreFailure, "restore-failed");
        } catch (LinkageError restoreFailure) {
            if (restoreFailure != failure) restoreFailure.addSuppressed(failure);
            HostImageGlErrorTracker.checkpoint("restore.after-capture-failure-exception");
            return failureWithTrackedError("restore", restoreFailure, "restore-linkage");
        } catch (Error restoreFailure) {
            if (restoreFailure != failure) restoreFailure.addSuppressed(failure);
            checkpointSuppressingFailure("restore.after-capture-failure-exception", restoreFailure);
            throw restoreFailure;
        }
        return failureWithTrackedError(stage, failure, detail);
    }

    private void rethrowFatalAfterCapturedSnapshot(Snapshot snapshot, Error fatalFailure) {
        HostImageGlErrorTracker.enterPhase("restore");
        try {
            stateAccess.restore(snapshot);
            HostImageGlErrorTracker.checkpoint("restore.after-fatal");
        } catch (RuntimeException cleanupFailure) {
            fatalFailure.addSuppressed(cleanupFailure);
            checkpointSuppressingFailure("restore.after-fatal-exception", fatalFailure);
        } catch (Error cleanupFailure) {
            if (cleanupFailure != fatalFailure) fatalFailure.addSuppressed(cleanupFailure);
            checkpointSuppressingFailure("restore.after-fatal-exception", fatalFailure);
        }
        throw fatalFailure;
    }

    private static void checkpointSuppressingFailure(String operation, Throwable primaryFailure) {
        try {
            HostImageGlErrorTracker.checkpoint(operation);
        } catch (RuntimeException checkpointFailure) {
            if (checkpointFailure != primaryFailure) primaryFailure.addSuppressed(checkpointFailure);
        } catch (Error checkpointFailure) {
            if (checkpointFailure != primaryFailure) primaryFailure.addSuppressed(checkpointFailure);
        }
    }

    private static boolean isFatal(Throwable failure) {
        return failure instanceof Error && !(failure instanceof LinkageError);
    }

    /** GL 首错优先于普通阶段说明，且一律标记为宿主状态丢失。 */
    private static HostImageRenderOutcome failureWithTrackedError(String stage, Throwable failure, String detail) {
        HostImageGlErrorTracker.FirstError firstError = HostImageGlErrorTracker.firstError();
        return firstError == null
                ? HostImageRenderOutcome.hostStateLost(stage, failure, detail)
                : HostImageRenderOutcome.hostStateLost(firstError.getPhase(), failure, firstError.detail());
    }

    /** LWJGL2 固定管线与现代 binding 的能力感知访问器。 */
    static final class LwjglStateAccess implements StateAccess {
        private static final TextureMatrixOperations TEXTURE_MATRIX_OPERATIONS = new LwjglTextureMatrixOperations();
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
            } catch (NoSuchFieldException exception) {
                return false;
            } catch (IllegalAccessException exception) {
                return false;
            } catch (SecurityException exception) {
                return false;
            }
        }

        @Override public int consumeGlError() { return GL11.glGetError(); }

        @Override
        public Snapshot capture() {
            ContextCapabilities caps = GLContext.getCapabilities();
            LwjglSnapshot state = new LwjglSnapshot();
            state.hasGl13 = caps.OpenGL13;
            state.hasGl15 = caps.OpenGL15;
            state.hasGl20 = caps.OpenGL20;
            state.hasGl30 = caps.OpenGL30;
            state.textureMatrix = probeTextureMatrix(TEXTURE_MATRIX_OPERATIONS);
            if (!state.textureMatrix.supported) {
                return state;
            }
            state.attribStack = probeAttribStack(SERVER_ATTRIB_OPERATIONS, "server-attrib");
            if (!state.attribStack.supported) {
                return state;
            }
            state.clientAttribStack = probeAttribStack(CLIENT_ATTRIB_OPERATIONS, "client-attrib");
            if (!state.clientAttribStack.supported) {
                return state;
            }
            state.matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            state.modelviewDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
            state.projectionDepth = GL11.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH);
            HostImageGlErrorTracker.checkpoint("capture.matrix-depths");
            readInts(GL11.GL_VIEWPORT, state.viewport);
            readInts(GL11.GL_SCISSOR_BOX, state.scissor);
            readFloats(GL11.GL_MODELVIEW_MATRIX, state.modelview);
            readFloats(GL11.GL_PROJECTION_MATRIX, state.projection);
            HostImageGlErrorTracker.checkpoint("capture.matrices");
            if (state.hasGl13) {
                state.textureBindings = captureTextureBindings(TEXTURE_BINDING_OPERATIONS);
                if (!state.textureBindings.clientActiveTexture.supported) {
                    return state;
                }
            }
            if (state.hasGl20) state.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            if (state.hasGl15) {
                state.arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
                state.elementBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
            }
            if (state.hasGl30) {
                state.vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
                state.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                state.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
                state.renderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
            }
            HostImageGlErrorTracker.checkpoint("capture.modern-bindings");
            throwIfCaptureError();
            state.fullStateCaptured = true;
            try {
                TEXTURE_MATRIX_OPERATIONS.readMatrix(state.textureMatrix.matrix);
                checkpointCapture("capture.texture-matrix-read");
                TEXTURE_MATRIX_OPERATIONS.pushMatrix();
                checkpointCapture("capture.texture-matrix-push");
                state.textureMatrixPushed = true;
                captureAttribStack(SERVER_ATTRIB_OPERATIONS, state.attribStack);
                checkpointCapture("capture.server-attrib");
                state.serverAttribPushed = true;
                captureAttribStack(CLIENT_ATTRIB_OPERATIONS, state.clientAttribStack);
                checkpointCapture("capture.client-attrib");
                state.clientAttribPushed = true;
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                checkpointCapture("capture.modelview-mode");
                GL11.glPushMatrix();
                checkpointCapture("capture.modelview-push");
                state.modelviewPushed = true;
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                checkpointCapture("capture.projection-mode");
                GL11.glPushMatrix();
                checkpointCapture("capture.projection-push");
                state.projectionPushed = true;
                GL11.glMatrixMode(state.matrixMode);
                checkpointCapture("capture.matrix-mode");
                return state;
            } catch (RuntimeException failure) {
                rollbackCapture(state, failure);
                throw failure;
            } catch (LinkageError failure) {
                rollbackCapture(state, failure);
                throw failure;
            } catch (Error failure) {
                rollbackCapture(state, failure);
                throw failure;
            }
        }

        @Override
        public void restore(Snapshot snapshot) {
            LwjglSnapshot state = (LwjglSnapshot) snapshot;
            if (!state.fullStateCaptured) {
                return;
            }
            Throwable[] failure = new Throwable[1];
            boolean modelviewReady = !state.modelviewPushed || restoreStep(failure, () ->
                    normalizeMatrixDepth(GL11.GL_MODELVIEW, GL11.GL_MODELVIEW_STACK_DEPTH,
                            state.modelviewDepth + 1, "modelview"));
            boolean projectionReady = !state.projectionPushed || restoreStep(failure, () ->
                    normalizeMatrixDepth(GL11.GL_PROJECTION, GL11.GL_PROJECTION_STACK_DEPTH,
                            state.projectionDepth + 1, "projection"));
            boolean serverAttribReady = !state.serverAttribPushed || restoreStep(failure,
                    () -> normalizeAttribStack(SERVER_ATTRIB_OPERATIONS, state.attribStack));
            boolean clientAttribReady = !state.clientAttribPushed || restoreStep(failure,
                    () -> normalizeAttribStack(CLIENT_ATTRIB_OPERATIONS, state.clientAttribStack));
            if (projectionReady && state.projectionPushed) {
                restoreStep(failure, () -> popMatrix(GL11.GL_PROJECTION));
            }
            if (modelviewReady && state.modelviewPushed) {
                restoreStep(failure, () -> popMatrix(GL11.GL_MODELVIEW));
            }
            if (clientAttribReady && state.clientAttribPushed) {
                restoreStep(failure, () -> popAttribStack(CLIENT_ATTRIB_OPERATIONS, state.clientAttribStack));
            }
            if (serverAttribReady && state.serverAttribPushed) {
                restoreStep(failure, () -> popAttribStack(SERVER_ATTRIB_OPERATIONS, state.attribStack));
            }
            if (state.textureMatrixPushed) {
                if (state.hasGl13) {
                    restoreStep(failure, () -> restoreTextureMatrixOnUnit(
                            TEXTURE_BINDING_OPERATIONS, TEXTURE_MATRIX_OPERATIONS,
                            state.textureMatrix, state.textureBindings.activeTexture));
                } else {
                    restoreStep(failure,
                            () -> restoreTextureMatrix(TEXTURE_MATRIX_OPERATIONS, state.textureMatrix));
                }
            }
            restoreStep(failure, () -> HostImageGlErrorTracker.checkpoint("restore.stacks"));
            if (state.hasGl20) restoreStep(failure, () -> GL20.glUseProgram(state.program));
            if (state.hasGl13) restoreStep(failure,
                    () -> restoreTextureBindings(TEXTURE_BINDING_OPERATIONS, state.textureBindings));
            if (state.hasGl30) {
                restoreStep(failure, () -> GL30.glBindVertexArray(state.vao));
                restoreStep(failure,
                        () -> GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, state.drawFramebuffer));
                restoreStep(failure,
                        () -> GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, state.readFramebuffer));
                restoreStep(failure,
                        () -> GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, state.renderbuffer));
            }
            if (state.hasGl15) {
                restoreStep(failure, () -> GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, state.arrayBuffer));
                restoreStep(failure,
                        () -> GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, state.elementBuffer));
            }
            restoreStep(failure, () -> HostImageGlErrorTracker.checkpoint("restore.modern-bindings"));
            restoreStep(failure, () ->
                    GL11.glViewport(state.viewport[0], state.viewport[1], state.viewport[2], state.viewport[3]));
            restoreStep(failure, () ->
                    GL11.glScissor(state.scissor[0], state.scissor[1], state.scissor[2], state.scissor[3]));
            restoreStep(failure, () -> GL11.glMatrixMode(state.matrixMode));
            restoreStep(failure,
                    () -> HostImageGlErrorTracker.checkpoint("restore.viewport-matrix-mode"));
            rethrowRestoreFailure(failure[0]);
        }

        @Override
        public String unavailableReason(Snapshot snapshot) {
            LwjglSnapshot state = (LwjglSnapshot) snapshot;
            return state.textureMatrix != null && state.textureMatrix.supported
                    && state.attribStack != null && state.attribStack.supported
                    && state.clientAttribStack != null && state.clientAttribStack.supported
                    && (!state.hasGl13 || state.textureBindings != null
                            && state.textureBindings.clientActiveTexture.supported)
                    ? null : "legacy-state-fence-unavailable";
        }

        @Override
        public String findDrift(Snapshot snapshot) {
            LwjglSnapshot state = (LwjglSnapshot) snapshot;
            boolean modelviewDepthDrift = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH)
                    != state.modelviewDepth;
            HostImageGlErrorTracker.checkpoint("verify.modelview-depth");
            if (modelviewDepthDrift) return "modelview-depth";
            boolean projectionDepthDrift = GL11.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH)
                    != state.projectionDepth;
            HostImageGlErrorTracker.checkpoint("verify.projection-depth");
            if (projectionDepthDrift) return "projection-depth";
            boolean serverAttribDepthDrift = SERVER_ATTRIB_OPERATIONS.getStackDepth()
                    != state.attribStack.depth;
            HostImageGlErrorTracker.checkpoint("verify.server-attrib-depth");
            if (serverAttribDepthDrift) return "server-attrib-depth";
            boolean clientAttribDepthDrift = CLIENT_ATTRIB_OPERATIONS.getStackDepth()
                    != state.clientAttribStack.depth;
            HostImageGlErrorTracker.checkpoint("verify.client-attrib-depth");
            if (clientAttribDepthDrift) return "client-attrib-depth";
            boolean matrixModeDrift = GL11.glGetInteger(GL11.GL_MATRIX_MODE) != state.matrixMode;
            HostImageGlErrorTracker.checkpoint("verify.matrix-mode");
            if (matrixModeDrift) return "matrix-mode";
            boolean viewportDrift = !equalInts(GL11.GL_VIEWPORT, state.viewport);
            HostImageGlErrorTracker.checkpoint("verify.viewport");
            if (viewportDrift) return "viewport";
            boolean scissorDrift = !equalInts(GL11.GL_SCISSOR_BOX, state.scissor);
            HostImageGlErrorTracker.checkpoint("verify.scissor");
            if (scissorDrift) return "scissor";
            boolean modelviewDrift = !equalFloats(GL11.GL_MODELVIEW_MATRIX, state.modelview);
            HostImageGlErrorTracker.checkpoint("verify.modelview-matrix");
            if (modelviewDrift) return "modelview";
            boolean projectionDrift = !equalFloats(GL11.GL_PROJECTION_MATRIX, state.projection);
            HostImageGlErrorTracker.checkpoint("verify.projection-matrix");
            if (projectionDrift) return "projection";
            boolean textureMatrixDrift = hasRestoredTextureMatrixDrift(
                    TEXTURE_MATRIX_OPERATIONS, state.textureMatrix);
            HostImageGlErrorTracker.checkpoint("verify.texture-matrix");
            if (textureMatrixDrift) return "texture-matrix";
            boolean textureBindingDrift = state.hasGl13
                    && hasServerTextureBindingDrift(TEXTURE_BINDING_OPERATIONS, state.textureBindings);
            HostImageGlErrorTracker.checkpoint("verify.server-texture-bindings");
            if (textureBindingDrift) return "texture-binding";
            boolean clientActiveTextureDrift = state.hasGl13
                    && TEXTURE_BINDING_OPERATIONS.getClientActiveTexture()
                    != state.textureBindings.clientActiveTexture.unit;
            HostImageGlErrorTracker.checkpoint("verify.client-active-texture");
            if (clientActiveTextureDrift) return "client-active-texture";
            boolean programDrift = state.hasGl20 && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != state.program;
            HostImageGlErrorTracker.checkpoint("verify.program-binding");
            if (programDrift) return "program";
            boolean vaoDrift = state.hasGl30
                    && GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) != state.vao;
            HostImageGlErrorTracker.checkpoint("verify.vao-binding");
            if (vaoDrift) return "vao";
            boolean arrayBufferDrift = state.hasGl15
                    && GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) != state.arrayBuffer;
            HostImageGlErrorTracker.checkpoint("verify.array-buffer-binding");
            if (arrayBufferDrift) return "array-buffer";
            boolean elementBufferDrift = state.hasGl15
                    && GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING) != state.elementBuffer;
            HostImageGlErrorTracker.checkpoint("verify.element-buffer-binding");
            if (elementBufferDrift) return "element-buffer";
            boolean drawFboDrift = state.hasGl30
                    && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != state.drawFramebuffer;
            HostImageGlErrorTracker.checkpoint("verify.draw-fbo-binding");
            if (drawFboDrift) return "draw-fbo";
            boolean readFboDrift = state.hasGl30
                    && GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) != state.readFramebuffer;
            HostImageGlErrorTracker.checkpoint("verify.read-fbo-binding");
            if (readFboDrift) return "read-fbo";
            boolean renderbufferDrift = state.hasGl30
                    && GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING) != state.renderbuffer;
            HostImageGlErrorTracker.checkpoint("verify.renderbuffer-binding");
            if (renderbufferDrift) return "renderbuffer";
            return null;
        }

        private static void normalizeMatrixDepth(int mode, int name, int expected, String label) {
            int actual = GL11.glGetInteger(name);
            if (actual < expected) throw new IllegalStateException(label + " stack underflow " + actual + " < " + expected);
            GL11.glMatrixMode(mode);
            while (actual-- > expected) GL11.glPopMatrix();
        }

        private static void pushMatrix(int mode) { GL11.glMatrixMode(mode); GL11.glPushMatrix(); }
        private static void popMatrix(int mode) { GL11.glMatrixMode(mode); GL11.glPopMatrix(); }

        private static void checkpointCapture(String operation) {
            HostImageGlErrorTracker.checkpoint(operation);
            throwIfCaptureError();
        }

        private static void throwIfCaptureError() {
            HostImageGlErrorTracker.FirstError error = HostImageGlErrorTracker.firstError();
            if (error != null) throw new IllegalStateException(error.detail());
        }

        private static void rollbackCapture(LwjglSnapshot state, Throwable primaryFailure) {
            Throwable[] failure = new Throwable[] {primaryFailure};
            if (state.projectionPushed) restoreStep(failure, () -> popMatrix(GL11.GL_PROJECTION));
            if (state.modelviewPushed) restoreStep(failure, () -> popMatrix(GL11.GL_MODELVIEW));
            if (state.clientAttribPushed) {
                restoreStep(failure, () -> popAttribStack(CLIENT_ATTRIB_OPERATIONS, state.clientAttribStack));
            }
            if (state.serverAttribPushed) {
                restoreStep(failure, () -> popAttribStack(SERVER_ATTRIB_OPERATIONS, state.attribStack));
            }
            if (state.textureMatrixPushed) {
                if (state.hasGl13 && state.textureBindings != null) {
                    restoreStep(failure, () -> restoreTextureMatrixOnUnit(
                            TEXTURE_BINDING_OPERATIONS, TEXTURE_MATRIX_OPERATIONS,
                            state.textureMatrix, state.textureBindings.activeTexture));
                } else {
                    restoreStep(failure,
                            () -> restoreTextureMatrix(TEXTURE_MATRIX_OPERATIONS, state.textureMatrix));
                }
            }
            restoreStep(failure, () -> GL11.glMatrixMode(state.matrixMode));
            if (failure[0] != primaryFailure) rethrowRestoreFailure(failure[0]);
        }

        private static boolean restoreStep(Throwable[] firstFailure, Runnable step) {
            try {
                step.run();
                return true;
            } catch (RuntimeException failure) {
                rememberRestoreFailure(firstFailure, failure);
            } catch (LinkageError failure) {
                rememberRestoreFailure(firstFailure, failure);
            } catch (Error failure) {
                rememberRestoreFailure(firstFailure, failure);
            }
            return false;
        }

        private static void rememberRestoreFailure(Throwable[] firstFailure, Throwable failure) {
            if (firstFailure[0] == null) {
                firstFailure[0] = failure;
            } else if (isFatal(failure) && !isFatal(firstFailure[0])) {
                if (firstFailure[0] != failure) failure.addSuppressed(firstFailure[0]);
                firstFailure[0] = failure;
            } else if (firstFailure[0] != failure) {
                firstFailure[0].addSuppressed(failure);
            }
        }

        private static void rethrowRestoreFailure(Throwable failure) {
            if (failure == null) return;
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            if (failure instanceof LinkageError) throw (LinkageError) failure;
            if (failure instanceof Error) throw (Error) failure;
            throw new IllegalStateException("state restore failed", failure);
        }
        private static void readInts(int name, int[] target) {
            IntBuffer buffer = BufferUtils.createIntBuffer(target.length);
            GL11.glGetInteger(name, buffer);
            for (int i = 0; i < target.length; i++) target[i] = buffer.get(i);
        }
        private static boolean equalInts(int name, int[] expected) {
            int[] actual = new int[expected.length]; readInts(name, actual);
            for (int i = 0; i < expected.length; i++) if (actual[i] != expected[i]) return false;
            return true;
        }
        private static void readFloats(int name, float[] target) {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(target.length);
            GL11.glGetFloat(name, buffer);
            for (int i = 0; i < target.length; i++) target[i] = buffer.get(i);
        }
        private static boolean equalFloats(int name, float[] expected) {
            float[] actual = new float[expected.length]; readFloats(name, actual);
            for (int i = 0; i < expected.length; i++) if (Math.abs(actual[i] - expected[i]) > 0.0001F) return false;
            return true;
        }
    }

    /** server texture binding 与 legacy client-active texture 的最小可测试操作面。 */
    interface TextureBindingOperations {
        int getActiveTexture();
        void setActiveTexture(int unit);
        int getClientActiveTexture();
        void setClientActiveTexture(int unit);
        int getTexture2dBinding();
        void bindTexture2d(int texture);
        int consumeGlError();
    }

    /** legacy client-active texture 能力快照。 */
    static final class ClientActiveTextureSnapshot implements Snapshot {
        private final boolean supported;
        private final int unit;

        private ClientActiveTextureSnapshot(boolean supported, int unit) {
            this.supported = supported;
            this.unit = unit;
        }

        boolean isSupported() { return supported; }
    }

    /** server texture binding 与可选 client-active texture 的联合快照。 */
    static final class TextureBindingSnapshot implements Snapshot {
        private final int activeTexture;
        private final int texture0;
        private final int activeTextureBinding;
        private final ClientActiveTextureSnapshot clientActiveTexture;

        private TextureBindingSnapshot(int activeTexture, int texture0, int activeTextureBinding,
                ClientActiveTextureSnapshot clientActiveTexture) {
            this.activeTexture = activeTexture;
            this.texture0 = texture0;
            this.activeTextureBinding = activeTextureBinding;
            this.clientActiveTexture = clientActiveTexture;
        }

        boolean isClientActiveTextureSupported() { return clientActiveTexture.supported; }
    }

    /**
     * 用原值查询并回设 legacy client-active texture；Core Profile 明确拒绝时只关闭该子能力。
     */
    static ClientActiveTextureSnapshot probeClientActiveTexture(TextureBindingOperations operations) {
        int unit = operations.getClientActiveTexture();
        int queryError = drainClientProbeErrors(operations, true);
        if (queryError == GL11.GL_INVALID_ENUM) return new ClientActiveTextureSnapshot(false, 0);
        if (queryError != GL11.GL_NO_ERROR) {
            HostImageGlErrorTracker.recordConsumedError("client-active-query", queryError);
            throw new IllegalStateException("client-active-query-gl-error=" + queryError);
        }

        operations.setClientActiveTexture(unit);
        int setterError = drainClientProbeErrors(operations, false);
        if (setterError == GL11.GL_INVALID_ENUM || setterError == GL11.GL_INVALID_OPERATION) {
            return new ClientActiveTextureSnapshot(false, 0);
        }
        if (setterError != GL11.GL_NO_ERROR) {
            HostImageGlErrorTracker.recordConsumedError("client-active-setter", setterError);
            throw new IllegalStateException("client-active-setter-gl-error=" + setterError);
        }
        return new ClientActiveTextureSnapshot(true, unit);
    }

    /** 排空本次 probe 的错误；任一未知错误优先返回，防止被能力降级掩盖。 */
    private static int drainClientProbeErrors(TextureBindingOperations operations, boolean query) {
        int recognized = GL11.GL_NO_ERROR;
        int unknown = GL11.GL_NO_ERROR;
        int error;
        while ((error = operations.consumeGlError()) != GL11.GL_NO_ERROR) {
            boolean expected = query
                    ? error == GL11.GL_INVALID_ENUM
                    : error == GL11.GL_INVALID_ENUM || error == GL11.GL_INVALID_OPERATION;
            if (expected && recognized == GL11.GL_NO_ERROR) recognized = error;
            else if (!expected && unknown == GL11.GL_NO_ERROR) unknown = error;
        }
        return unknown != GL11.GL_NO_ERROR ? unknown : recognized;
    }

    /** 捕获 server active texture/binding，并独立探测 legacy client-active texture。 */
    static TextureBindingSnapshot captureTextureBindings(TextureBindingOperations operations) {
        int activeTexture = operations.getActiveTexture();
        HostImageGlErrorTracker.checkpoint("capture.server-active-texture");
        try {
            ClientActiveTextureSnapshot client = probeClientActiveTexture(operations);
            int texture0 = textureBinding(operations, GL13.GL_TEXTURE0);
            int activeBinding = activeTexture == GL13.GL_TEXTURE0
                    ? texture0 : textureBinding(operations, activeTexture);
            selectActiveTexture(operations, activeTexture, "capture.server-active-texture-restore");
            return new TextureBindingSnapshot(activeTexture, texture0, activeBinding, client);
        } catch (RuntimeException failure) {
            restoreActiveTextureAfterFailure(operations, activeTexture, failure);
            throw failure;
        } catch (Error failure) {
            restoreActiveTextureAfterFailure(operations, activeTexture, failure);
            throw failure;
        }
    }

    /** 恢复 server texture binding；仅能力探测成功时恢复 client-active texture。 */
    static void restoreTextureBindings(TextureBindingOperations operations, TextureBindingSnapshot snapshot) {
        Throwable[] failure = new Throwable[1];
        boolean texture0Ready = textureRestoreStep(failure,
                () -> selectActiveTexture(operations, GL13.GL_TEXTURE0,
                        "restore.server-texture0-select"));
        if (texture0Ready) {
            textureRestoreStep(failure, () -> operations.bindTexture2d(snapshot.texture0));
        }
        if (snapshot.activeTexture != GL13.GL_TEXTURE0) {
            boolean activeReady = textureRestoreStep(failure,
                    () -> selectActiveTexture(operations, snapshot.activeTexture,
                            "restore.server-active-texture-select"));
            if (activeReady) {
                textureRestoreStep(failure,
                        () -> operations.bindTexture2d(snapshot.activeTextureBinding));
            }
        }
        textureRestoreStep(failure,
                () -> selectActiveTexture(operations, snapshot.activeTexture,
                        "restore.server-active-texture-final"));
        textureRestoreStep(failure,
                () -> HostImageGlErrorTracker.checkpoint("restore.server-texture-bindings"));
        if (snapshot.clientActiveTexture.supported) {
            textureRestoreStep(failure,
                    () -> operations.setClientActiveTexture(snapshot.clientActiveTexture.unit));
            textureRestoreStep(failure,
                    () -> HostImageGlErrorTracker.checkpoint("restore.client-active-texture"));
        }
        rethrowTextureFailure(failure[0]);
    }

    /** 比较 server active texture 与 texture0/入口 active unit 的 2D binding。 */
    static boolean hasServerTextureBindingDrift(TextureBindingOperations operations,
            TextureBindingSnapshot snapshot) {
        int activeTexture = operations.getActiveTexture();
        try {
            int texture0 = textureBinding(operations, GL13.GL_TEXTURE0);
            int activeBinding = snapshot.activeTexture == GL13.GL_TEXTURE0
                    ? texture0 : textureBinding(operations, snapshot.activeTexture);
            selectActiveTexture(operations, activeTexture, "verify.server-active-texture-restore");
            return activeTexture != snapshot.activeTexture
                    || texture0 != snapshot.texture0
                    || activeBinding != snapshot.activeTextureBinding;
        } catch (RuntimeException failure) {
            restoreActiveTextureAfterFailure(operations, activeTexture, failure);
            throw failure;
        } catch (Error failure) {
            restoreActiveTextureAfterFailure(operations, activeTexture, failure);
            throw failure;
        }
    }

    /** 读取指定 server texture unit 的 2D binding。 */
    private static int textureBinding(TextureBindingOperations operations, int unit) {
        selectActiveTexture(operations, unit, "texture-binding-select");
        return operations.getTexture2dBinding();
    }

    /** same-value 验证避免 active texture 切换失败后继续操作错误 unit。 */
    private static void selectActiveTexture(TextureBindingOperations operations, int unit, String operation) {
        operations.setActiveTexture(unit);
        int actual = operations.getActiveTexture();
        HostImageGlErrorTracker.checkpoint(operation);
        if (actual != unit) {
            throw new IllegalStateException(operation + " failed " + actual + " != " + unit);
        }
    }

    private static void restoreActiveTextureAfterFailure(TextureBindingOperations operations,
            int activeTexture, Throwable primaryFailure) {
        try {
            selectActiveTexture(operations, activeTexture, "texture-binding-exception-restore");
        } catch (RuntimeException cleanupFailure) {
            if (cleanupFailure != primaryFailure) primaryFailure.addSuppressed(cleanupFailure);
        } catch (Error cleanupFailure) {
            if (isFatal(cleanupFailure) && !isFatal(primaryFailure)) {
                if (cleanupFailure != primaryFailure) cleanupFailure.addSuppressed(primaryFailure);
                throw cleanupFailure;
            }
            if (cleanupFailure != primaryFailure) primaryFailure.addSuppressed(cleanupFailure);
        }
    }

    private static boolean textureRestoreStep(Throwable[] firstFailure, Runnable step) {
        try {
            step.run();
            return true;
        } catch (RuntimeException failure) {
            rememberTextureFailure(firstFailure, failure);
        } catch (Error failure) {
            rememberTextureFailure(firstFailure, failure);
        }
        return false;
    }

    private static void rememberTextureFailure(Throwable[] firstFailure, Throwable failure) {
        if (firstFailure[0] == null) {
            firstFailure[0] = failure;
        } else if (isFatal(failure) && !isFatal(firstFailure[0])) {
            if (firstFailure[0] != failure) failure.addSuppressed(firstFailure[0]);
            firstFailure[0] = failure;
        } else if (firstFailure[0] != failure) {
            firstFailure[0].addSuppressed(failure);
        }
    }

    private static void rethrowTextureFailure(Throwable failure) {
        if (failure == null) return;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
        throw new IllegalStateException("texture state restore failed", failure);
    }

    /** 生产 LWJGL server/client texture 操作适配器。 */
    private static final class LwjglTextureBindingOperations implements TextureBindingOperations {
        @Override public int getActiveTexture() { return GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE); }
        @Override public void setActiveTexture(int unit) { GL13.glActiveTexture(unit); }
        @Override public int getClientActiveTexture() {
            return GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        }
        @Override public void setClientActiveTexture(int unit) { GL13.glClientActiveTexture(unit); }
        @Override public int getTexture2dBinding() { return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); }
        @Override public void bindTexture2d(int texture) { GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); }
        @Override public int consumeGlError() { return GL11.glGetError(); }
    }

    /** texture matrix 固定管线能力的最小可测试操作面。 */
    interface TextureMatrixOperations {
        int getStackDepth();
        int consumeGlError();
        void readMatrix(float[] target);
        void pushMatrix();
        void popMatrix();
    }

    /** texture matrix 子围栏快照；不支持时不执行任何后续固定管线操作。 */
    static final class TextureMatrixSnapshot implements Snapshot {
        private final boolean supported;
        private final int depth;
        private final float[] matrix = new float[16];

        private TextureMatrixSnapshot(boolean supported, int depth) {
            this.supported = supported;
            this.depth = depth;
        }

        boolean isSupported() { return supported; }
    }

    /**
     * 在入口 GL error 已清洁的前提下探测 texture matrix 能力。
     * 探测查询产生的全部错误会被消费，避免污染其余状态围栏。
     */
    static TextureMatrixSnapshot probeTextureMatrix(TextureMatrixOperations operations) {
        int depth = operations.getStackDepth();
        int error = drainLegacyProbeErrors(operations::consumeGlError, "texture-matrix-query");
        if (error != GL11.GL_NO_ERROR) {
            return new TextureMatrixSnapshot(false, 0);
        }
        return new TextureMatrixSnapshot(depth >= 1, depth);
    }

    /** 支持时读取 texture matrix 并压入围栏帧。 */
    static void captureTextureMatrix(TextureMatrixOperations operations, TextureMatrixSnapshot snapshot) {
        if (!snapshot.supported) return;
        operations.readMatrix(snapshot.matrix);
        operations.pushMatrix();
    }

    /** 支持时规范化并弹出 texture matrix 围栏帧。 */
    static void restoreTextureMatrix(TextureMatrixOperations operations, TextureMatrixSnapshot snapshot) {
        if (!snapshot.supported) return;
        int actual = operations.getStackDepth();
        int expected = snapshot.depth + 1;
        if (actual < expected) {
            throw new IllegalStateException("texture stack underflow " + actual + " < " + expected);
        }
        while (actual-- > expected) operations.popMatrix();
        operations.popMatrix();
    }

    /** 切回 capture 时的 server texture unit 后再恢复该 unit 独立的 texture matrix stack。 */
    static void restoreTextureMatrixOnUnit(TextureBindingOperations bindingOperations,
            TextureMatrixOperations matrixOperations, TextureMatrixSnapshot snapshot, int textureUnit) {
        selectActiveTexture(bindingOperations, textureUnit, "restore.texture-matrix-unit");
        restoreTextureMatrix(matrixOperations, snapshot);
    }

    /** 支持时比较 texture matrix；不支持时该子围栏不参与 drift 判定。 */
    static boolean hasTextureMatrixDrift(TextureMatrixOperations operations, TextureMatrixSnapshot snapshot) {
        if (!snapshot.supported) return false;
        float[] actual = new float[16];
        operations.readMatrix(actual);
        for (int i = 0; i < actual.length; i++) {
            if (Math.abs(actual[i] - snapshot.matrix[i]) > 0.0001F) return true;
        }
        return false;
    }

    /** restore 后同时校验原始 stack depth 与矩阵值，避免弹错 texture unit 仍误判成功。 */
    static boolean hasRestoredTextureMatrixDrift(TextureMatrixOperations operations,
            TextureMatrixSnapshot snapshot) {
        return snapshot.supported
                && (operations.getStackDepth() != snapshot.depth
                        || hasTextureMatrixDrift(operations, snapshot));
    }

    /** 生产 LWJGL texture matrix 操作适配器。 */
    private static final class LwjglTextureMatrixOperations implements TextureMatrixOperations {
        @Override public int getStackDepth() { return GL11.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH); }
        @Override public int consumeGlError() { return GL11.glGetError(); }
        @Override public void readMatrix(float[] target) { LwjglStateAccess.readFloats(GL11.GL_TEXTURE_MATRIX, target); }
        @Override public void pushMatrix() { LwjglStateAccess.pushMatrix(GL11.GL_TEXTURE); }
        @Override public void popMatrix() { LwjglStateAccess.popMatrix(GL11.GL_TEXTURE); }
    }

    /** server/client attribute stack 的最小可测试操作面。 */
    interface AttribStackOperations {
        int getStackDepth();
        int consumeGlError();
        void push();
        void pop();
    }

    /** attribute stack 子围栏快照；server/client 能力彼此独立。 */
    static final class AttribStackSnapshot implements Snapshot {
        private final boolean supported;
        private final int depth;
        private final String label;

        private AttribStackSnapshot(boolean supported, int depth, String label) {
            this.supported = supported;
            this.depth = depth;
            this.label = label;
        }

        boolean isSupported() { return supported; }
    }

    /**
     * 在入口 GL error 已清洁的前提下探测单个 attribute stack 能力。
     * 查询错误会被完整消费，使 server/client 探测及退出检查互不污染。
     */
    static AttribStackSnapshot probeAttribStack(AttribStackOperations operations, String label) {
        int depth = operations.getStackDepth();
        int error = drainLegacyProbeErrors(operations::consumeGlError, label + "-query");
        if (error != GL11.GL_NO_ERROR) {
            return new AttribStackSnapshot(false, 0, label);
        }
        // Compatibility Profile 的合法入口深度通常就是 0；查询无错即表示该 stack 可用。
        return new AttribStackSnapshot(true, depth, label);
    }

    /**
     * 排空 legacy probe 错误；只有 Core Profile 常见的 INVALID_ENUM/INVALID_OPERATION 可降级。
     */
    private static int drainLegacyProbeErrors(IntSupplier errorSource, String operation) {
        int recognized = GL11.GL_NO_ERROR;
        int unknown = GL11.GL_NO_ERROR;
        int error;
        while ((error = errorSource.getAsInt()) != GL11.GL_NO_ERROR) {
            if (error == GL11.GL_INVALID_ENUM || error == GL11.GL_INVALID_OPERATION) {
                if (recognized == GL11.GL_NO_ERROR) {
                    recognized = error;
                }
            } else if (unknown == GL11.GL_NO_ERROR) {
                unknown = error;
            }
        }
        if (unknown != GL11.GL_NO_ERROR) {
            HostImageGlErrorTracker.recordConsumedError(operation, unknown);
            throw new IllegalStateException(operation + "-gl-error=" + unknown);
        }
        return recognized;
    }

    /** 支持时压入 attribute stack 围栏帧。 */
    static void captureAttribStack(AttribStackOperations operations, AttribStackSnapshot snapshot) {
        if (snapshot.supported) operations.push();
    }

    /** 支持时移除 renderer 额外压入的帧，并对真实下溢保持 fail-closed。 */
    static void normalizeAttribStack(AttribStackOperations operations, AttribStackSnapshot snapshot) {
        if (!snapshot.supported) return;
        int actual = operations.getStackDepth();
        int expected = snapshot.depth + 1;
        if (actual < expected) {
            throw new IllegalStateException(snapshot.label + " stack underflow " + actual + " < " + expected);
        }
        while (actual-- > expected) operations.pop();
    }

    /** 支持时弹出 attribute stack 围栏帧。 */
    static void popAttribStack(AttribStackOperations operations, AttribStackSnapshot snapshot) {
        if (snapshot.supported) operations.pop();
    }

    /** 生产 LWJGL server/client attribute stack 操作适配器。 */
    private static final class LwjglAttribStackOperations implements AttribStackOperations {
        private final boolean client;

        private LwjglAttribStackOperations(boolean client) { this.client = client; }

        @Override public int getStackDepth() {
            return GL11.glGetInteger(client ? GL11.GL_CLIENT_ATTRIB_STACK_DEPTH : GL11.GL_ATTRIB_STACK_DEPTH);
        }
        @Override public int consumeGlError() { return GL11.glGetError(); }
        @Override public void push() {
            if (client) GL11.glPushClientAttrib(CLIENT_ALL_ATTRIB_BITS);
            else GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        }
        @Override public void pop() {
            if (client) GL11.glPopClientAttrib();
            else GL11.glPopAttrib();
        }
    }

    private static final class LwjglSnapshot implements Snapshot {
        private boolean hasGl13, hasGl15, hasGl20, hasGl30;
        private boolean fullStateCaptured;
        private int matrixMode, modelviewDepth, projectionDepth;
        private int program, vao, arrayBuffer, elementBuffer, drawFramebuffer, readFramebuffer, renderbuffer;
        private final int[] viewport = new int[4];
        private final int[] scissor = new int[4];
        private final float[] modelview = new float[16];
        private final float[] projection = new float[16];
        private TextureMatrixSnapshot textureMatrix;
        private TextureBindingSnapshot textureBindings;
        private AttribStackSnapshot attribStack, clientAttribStack;
        private boolean textureMatrixPushed, serverAttribPushed, clientAttribPushed;
        private boolean modelviewPushed, projectionPushed;
    }
}
