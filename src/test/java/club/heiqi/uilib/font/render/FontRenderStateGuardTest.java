package club.heiqi.uilib.font.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** 字体渲染状态保护器的 per-unit TEXTURE_2D enable 守恒与整体状态恢复测试。 */
public class FontRenderStateGuardTest {

    /** push 时 active unit ≠ unit0：flush 在 unit1 启用 TEXTURE_2D 后必须把两个 unit 的 enable 恢复原状。 */
    @Test
    public void restoresPerUnitTexture2dEnableWhenPushActiveUnitIsNotUnit0() {
        FakeGlAccess gl = new FakeGlAccess();
        gl.activeTexture = GL13.GL_TEXTURE1;
        gl.textureEnabled.put(GL13.GL_TEXTURE0, false);
        gl.textureEnabled.put(GL13.GL_TEXTURE1, false);
        gl.textureBindings.put(GL13.GL_TEXTURE0, 61);
        gl.textureBindings.put(GL13.GL_TEXTURE1, 62);
        EntryState entry = new EntryState(gl);

        new FontRenderStateGuard(gl).run(new Runnable() {
            @Override
            public void run() {
                mutateLikeFlush(gl);
            }
        });

        entry.assertRestored(gl);
        assertEquals(GL13.GL_TEXTURE1, gl.activeTexture);
        assertFalse("unit0 TEXTURE_2D 必须恢复为 push 时状态", gl.textureEnabled.get(GL13.GL_TEXTURE0));
        assertFalse("unit1 TEXTURE_2D 泄漏必须被清除", gl.textureEnabled.get(GL13.GL_TEXTURE1));
    }

    /** push 时 active unit ≠ unit0 且 unit0 原本启用：attrib pop 错位不得把 unit0 误关。 */
    @Test
    public void keepsUnit0Texture2dEnabledWhenPushActiveUnitIsNotUnit0() {
        FakeGlAccess gl = new FakeGlAccess();
        gl.activeTexture = GL13.GL_TEXTURE1;
        gl.textureEnabled.put(GL13.GL_TEXTURE0, true);
        gl.textureEnabled.put(GL13.GL_TEXTURE1, false);
        EntryState entry = new EntryState(gl);

        new FontRenderStateGuard(gl).run(new Runnable() {
            @Override
            public void run() {
                mutateLikeFlush(gl);
            }
        });

        entry.assertRestored(gl);
        assertEquals(GL13.GL_TEXTURE1, gl.activeTexture);
        assertTrue("unit0 TEXTURE_2D 应保持启用", gl.textureEnabled.get(GL13.GL_TEXTURE0));
        assertFalse("unit1 TEXTURE_2D 泄漏必须被清除", gl.textureEnabled.get(GL13.GL_TEXTURE1));
    }

    /** push 时 active unit == unit0 的常规路径：enable 与绑定完整恢复。 */
    @Test
    public void restoresTexture2dEnableOnUnit0WhenPushActiveUnitIsUnit0() {
        FakeGlAccess gl = new FakeGlAccess();
        gl.activeTexture = GL13.GL_TEXTURE0;
        gl.textureEnabled.put(GL13.GL_TEXTURE0, true);
        EntryState entry = new EntryState(gl);

        new FontRenderStateGuard(gl).run(new Runnable() {
            @Override
            public void run() {
                mutateLikeFlush(gl);
            }
        });

        entry.assertRestored(gl);
        assertEquals(GL13.GL_TEXTURE0, gl.activeTexture);
        assertTrue(gl.textureEnabled.get(GL13.GL_TEXTURE0));
    }

    /** 跳过矩阵栈保存时（push(false)）不得触碰任何矩阵栈。 */
    @Test
    public void skipsMatrixStateWhenRequested() {
        FakeGlAccess gl = new FakeGlAccess();
        int entryMode = gl.matrixMode;
        int modelviewDepth = gl.modelviewDepth;
        int projectionDepth = gl.projectionDepth;
        int textureDepth = gl.textureDepth;

        new FontRenderStateGuard(gl).run(new Runnable() {
            @Override
            public void run() {
                mutateLikeFlush(gl);
            }
        }, false);

        assertEquals(entryMode, gl.matrixMode);
        assertEquals(modelviewDepth, gl.modelviewDepth);
        assertEquals(projectionDepth, gl.projectionDepth);
        assertEquals(textureDepth, gl.textureDepth);
    }

    /** 缺少对应 push 的 pop 必须失败。 */
    @Test
    public void rejectsPopWithoutMatchingPush() {
        FakeGlAccess gl = new FakeGlAccess();
        FontRenderStateGuard guard = new FontRenderStateGuard(gl);
        try {
            guard.pop();
            fail("缺少 push 边界的 pop 必须失败");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("保存边界"));
        }
    }

    /** 模拟 flush 对 per-unit 状态的触碰：在原 active unit 上启用 TEXTURE_2D，再切到 unit0 绑定纹理。 */
    private static void mutateLikeFlush(FakeGlAccess gl) {
        gl.setEnabled(GL11.GL_TEXTURE_2D, true);
        gl.activeTexture(GL13.GL_TEXTURE0);
        gl.bindTexture2d(71);
        gl.useProgram(12);
        gl.bindVertexArray(13);
        gl.bindBuffer(GL15.GL_ARRAY_BUFFER, 14);
        gl.bindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 15);
        gl.viewport(9, 9, 320, 180);
    }

    /** 捕获 fake 的完整入口状态，供状态守恒断言使用。 */
    private static final class EntryState {
        private final Map<Integer, Boolean> textureEnabled;
        private final Map<Integer, Integer> textureBindings;
        private final int matrixMode;
        private final int activeTexture;
        private final int program;
        private final int vertexArray;
        private final int arrayBuffer;
        private final int elementBuffer;
        private final int[] viewport;

        EntryState(FakeGlAccess gl) {
            textureEnabled = new HashMap<Integer, Boolean>(gl.textureEnabled);
            textureBindings = new HashMap<Integer, Integer>(gl.textureBindings);
            matrixMode = gl.matrixMode;
            activeTexture = gl.activeTexture;
            program = gl.program;
            vertexArray = gl.vertexArray;
            arrayBuffer = gl.arrayBuffer;
            elementBuffer = gl.elementBuffer;
            viewport = gl.viewport.clone();
        }

        void assertRestored(FakeGlAccess gl) {
            assertEquals(textureEnabled, gl.textureEnabled);
            assertEquals(textureBindings, gl.textureBindings);
            assertEquals(matrixMode, gl.matrixMode);
            assertEquals(activeTexture, gl.activeTexture);
            assertEquals(program, gl.program);
            assertEquals(vertexArray, gl.vertexArray);
            assertEquals(arrayBuffer, gl.arrayBuffer);
            assertEquals(elementBuffer, gl.elementBuffer);
            org.junit.Assert.assertArrayEquals(viewport, gl.viewport);
        }
    }

    /** 不触发 LWJGL 初始化的记录型状态桩，attrib 按 per-unit 语义模拟。 */
    private static final class FakeGlAccess implements FontRenderStateGuard.GlAccess {
        final Map<Integer, Boolean> textureEnabled = new HashMap<Integer, Boolean>();
        final Map<Integer, Integer> textureBindings = new HashMap<Integer, Integer>();
        final Deque<Boolean> attribStack = new ArrayDeque<Boolean>();
        int matrixMode = GL11.GL_MODELVIEW;
        int activeTexture = GL13.GL_TEXTURE0;
        int program = 51;
        int vertexArray = 52;
        int arrayBuffer = 53;
        int elementBuffer = 54;
        final int[] viewport = { 4, 5, 640, 360 };
        int modelviewDepth = 2;
        int projectionDepth = 1;
        int textureDepth = 1;
        int attribPushes;
        int attribPops;
        int clientAttribPushes;
        int clientAttribPops;

        FakeGlAccess() {
            textureEnabled.put(GL13.GL_TEXTURE0, true);
            textureEnabled.put(GL13.GL_TEXTURE1, true);
            textureBindings.put(GL13.GL_TEXTURE0, 61);
            textureBindings.put(GL13.GL_TEXTURE1, 62);
        }

        @Override
        public void pushAttrib(int mask) {
            attribPushes++;
            attribStack.push(textureEnabled.get(activeTexture));
        }

        @Override
        public void pushClientAttrib(int mask) {
            clientAttribPushes++;
        }

        @Override
        public void popAttrib() {
            attribPops++;
            Boolean saved = attribStack.pop();
            textureEnabled.put(activeTexture, saved);
        }

        @Override
        public void popClientAttrib() {
            clientAttribPops++;
        }

        @Override
        public int getInteger(int name) {
            if (name == GL11.GL_MATRIX_MODE) {
                return matrixMode;
            }
            if (name == GL13.GL_ACTIVE_TEXTURE) {
                return activeTexture;
            }
            if (name == GL11.GL_TEXTURE_BINDING_2D) {
                return textureBindings.get(activeTexture);
            }
            if (name == GL20.GL_CURRENT_PROGRAM) {
                return program;
            }
            if (name == GL30.GL_VERTEX_ARRAY_BINDING) {
                return vertexArray;
            }
            if (name == GL15.GL_ARRAY_BUFFER_BINDING) {
                return arrayBuffer;
            }
            if (name == GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING) {
                return elementBuffer;
            }
            throw new AssertionError("unexpected integer query " + name);
        }

        @Override
        public void readIntegers(int name, int[] target) {
            if (name != GL11.GL_VIEWPORT) {
                throw new AssertionError("unexpected integer vector " + name);
            }
            System.arraycopy(viewport, 0, target, 0, 4);
        }

        @Override
        public boolean isEnabled(int capability) {
            if (capability != GL11.GL_TEXTURE_2D) {
                throw new AssertionError("unexpected capability " + capability);
            }
            return textureEnabled.get(activeTexture);
        }

        @Override
        public void setEnabled(int capability, boolean enabled) {
            if (capability != GL11.GL_TEXTURE_2D) {
                throw new AssertionError("unexpected capability " + capability);
            }
            textureEnabled.put(activeTexture, enabled);
        }

        @Override
        public void matrixMode(int mode) {
            matrixMode = mode;
        }

        @Override
        public void pushMatrix() {
            if (matrixMode == GL11.GL_MODELVIEW) {
                modelviewDepth++;
            } else if (matrixMode == GL11.GL_PROJECTION) {
                projectionDepth++;
            } else if (matrixMode == GL11.GL_TEXTURE) {
                textureDepth++;
            }
        }

        @Override
        public void popMatrix() {
            if (matrixMode == GL11.GL_MODELVIEW) {
                modelviewDepth--;
            } else if (matrixMode == GL11.GL_PROJECTION) {
                projectionDepth--;
            } else if (matrixMode == GL11.GL_TEXTURE) {
                textureDepth--;
            }
        }

        @Override
        public void activeTexture(int unit) {
            activeTexture = unit;
        }

        @Override
        public void bindTexture2d(int texture) {
            textureBindings.put(activeTexture, texture);
        }

        @Override
        public void useProgram(int value) {
            program = value;
        }

        @Override
        public void bindVertexArray(int value) {
            vertexArray = value;
        }

        @Override
        public void bindBuffer(int target, int value) {
            if (target == GL15.GL_ARRAY_BUFFER) {
                arrayBuffer = value;
            } else {
                elementBuffer = value;
            }
        }

        @Override
        public void viewport(int x, int y, int width, int height) {
            viewport[0] = x;
            viewport[1] = y;
            viewport[2] = width;
            viewport[3] = height;
        }
    }
}
