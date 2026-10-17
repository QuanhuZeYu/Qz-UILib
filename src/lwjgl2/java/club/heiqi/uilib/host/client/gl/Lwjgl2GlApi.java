package club.heiqi.uilib.host.client.gl;

import java.nio.ByteBuffer;

/** LWJGL2宿主binding，查询scratch符合2.9的至少16元素检查。 */
public final class Lwjgl2GlApi implements GlApi {
    private final ByteBuffer bytes =
            ByteBuffer.allocateDirect(16).order(java.nio.ByteOrder.nativeOrder());
    private final java.nio.FloatBuffer floats =
            ByteBuffer.allocateDirect(64).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
    private final java.nio.IntBuffer ints =
            ByteBuffer.allocateDirect(64).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer();
    private java.nio.IntBuffer indices =
            ByteBuffer.allocateDirect(0).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer();

    public void glDisableVertexAttribArray(int location) {
        org.lwjgl.opengl.GL20.glDisableVertexAttribArray(location);
    }

    public void glGetVertexAttribfv(int location, int pname, float[] values) {
        floats.clear();
        org.lwjgl.opengl.GL20.glGetVertexAttrib(location, pname, floats);
        for (int i = 0; i < values.length; i++) values[i] = floats.get(i);
    }

    public GlProgramAttribute glGetActiveAttribute(int program, int index) {
        String name =
                org.lwjgl.opengl.GL20.glGetActiveAttrib(
                        program, index, org.lwjgl.opengl.GL20.glGetProgrami(program, 35722));
        return new GlProgramAttribute(
                name,
                org.lwjgl.opengl.GL20.glGetAttribLocation(program, name),
                org.lwjgl.opengl.GL20.glGetActiveAttribType(program, index),
                org.lwjgl.opengl.GL20.glGetActiveAttribSize(program, index));
    }

    public GlCapabilities capabilities() {
        org.lwjgl.opengl.ContextCapabilities c = org.lwjgl.opengl.GLContext.getCapabilities();
        return new GlCapabilities(c, c.OpenGL32, c.OpenGL33 || c.GL_ARB_sampler_objects);
    }

    public void glActiveTexture(int a) {
        org.lwjgl.opengl.GL13.glActiveTexture(a);
    }

    public void glAttachShader(int a, int b) {
        org.lwjgl.opengl.GL20.glAttachShader(a, b);
    }

    public void glBindAttribLocation(int a, int b, String c) {
        org.lwjgl.opengl.GL20.glBindAttribLocation(a, b, c);
    }

    public void glBindBuffer(int a, int b) {
        org.lwjgl.opengl.GL15.glBindBuffer(a, b);
    }

    public void glBindFragDataLocation(int a, int b, String c) {
        org.lwjgl.opengl.GL30.glBindFragDataLocation(a, b, c);
    }

    public void glBindFramebuffer(int a, int b) {
        org.lwjgl.opengl.GL30.glBindFramebuffer(a, b);
    }

    public void glBindRenderbuffer(int target, int renderbuffer) {
        org.lwjgl.opengl.GL30.glBindRenderbuffer(target, renderbuffer);
    }

    public int glCheckFramebufferStatus(int target) {
        return org.lwjgl.opengl.GL30.glCheckFramebufferStatus(target);
    }

    public void glClearBufferfv(int buffer, int drawbuffer, float[] values) {
        floats.clear();
        floats.put(values).flip();
        org.lwjgl.opengl.GL30.glClearBuffer(buffer, drawbuffer, floats);
    }

    public void glClearBufferfi(int buffer, int drawbuffer, float depth, int stencil) {
        org.lwjgl.opengl.GL30.glClearBufferfi(buffer, drawbuffer, depth, stencil);
    }

    public void glDeleteFramebuffers(int framebuffer) {
        org.lwjgl.opengl.GL30.glDeleteFramebuffers(framebuffer);
    }

    public void glDeleteRenderbuffers(int renderbuffer) {
        org.lwjgl.opengl.GL30.glDeleteRenderbuffers(renderbuffer);
    }

    public void glFramebufferTexture2D(int target, int attachment, int textureTarget, int texture, int level) {
        org.lwjgl.opengl.GL30.glFramebufferTexture2D(target, attachment, textureTarget, texture, level);
    }

    public void glFramebufferRenderbuffer(int target, int attachment, int renderbufferTarget, int renderbuffer) {
        org.lwjgl.opengl.GL30.glFramebufferRenderbuffer(target, attachment, renderbufferTarget, renderbuffer);
    }

    public int glGenFramebuffers() {
        return org.lwjgl.opengl.GL30.glGenFramebuffers();
    }

    public int glGenRenderbuffers() {
        return org.lwjgl.opengl.GL30.glGenRenderbuffers();
    }

    public void glRenderbufferStorage(int target, int format, int width, int height) {
        org.lwjgl.opengl.GL30.glRenderbufferStorage(target, format, width, height);
    }

    public void glBindSampler(int a, int b) {
        if (org.lwjgl.opengl.GLContext.getCapabilities().OpenGL33)
            org.lwjgl.opengl.GL33.glBindSampler(a, b);
        else org.lwjgl.opengl.ARBSamplerObjects.glBindSampler(a, b);
    }

    public void glBindTexture(int a, int b) {
        org.lwjgl.opengl.GL11.glBindTexture(a, b);
    }

    public void glBindVertexArray(int a) {
        org.lwjgl.opengl.GL30.glBindVertexArray(a);
    }

    public void glBlendColor(float a, float b, float c, float d) {
        org.lwjgl.opengl.GL14.glBlendColor(a, b, c, d);
    }

    public void glBlendEquationSeparate(int a, int b) {
        org.lwjgl.opengl.GL20.glBlendEquationSeparate(a, b);
    }

    public void glBlendFuncSeparate(int a, int b, int c, int d) {
        org.lwjgl.opengl.GL14.glBlendFuncSeparate(a, b, c, d);
    }

    public void glBufferData(int a, long b, int c) {
        org.lwjgl.opengl.GL15.glBufferData(a, b, c);
    }

    public void glBufferData(int a, int[] b, int c) {
        if (indices.capacity() < b.length)
            indices =
                    java.nio.ByteBuffer.allocateDirect(Math.multiplyExact(b.length, 4))
                            .order(java.nio.ByteOrder.nativeOrder())
                            .asIntBuffer();
        indices.clear();
        indices.put(b).flip();
        org.lwjgl.opengl.GL15.glBufferData(a, indices, c);
    }

    public void glBufferSubData(int a, long b, ByteBuffer c) {
        org.lwjgl.opengl.GL15.glBufferSubData(a, b, c);
    }

    public void glColorMask(boolean a, boolean b, boolean c, boolean d) {
        org.lwjgl.opengl.GL11.glColorMask(a, b, c, d);
    }

    public void glCompileShader(int a) {
        org.lwjgl.opengl.GL20.glCompileShader(a);
    }

    public int glCreateProgram() {
        return org.lwjgl.opengl.GL20.glCreateProgram();
    }

    public int glCreateShader(int a) {
        return org.lwjgl.opengl.GL20.glCreateShader(a);
    }

    public void glCullFace(int a) {
        org.lwjgl.opengl.GL11.glCullFace(a);
    }

    public void glDeleteBuffers(int a) {
        org.lwjgl.opengl.GL15.glDeleteBuffers(a);
    }

    public void glDeleteProgram(int a) {
        org.lwjgl.opengl.GL20.glDeleteProgram(a);
    }

    public void glDeleteShader(int a) {
        org.lwjgl.opengl.GL20.glDeleteShader(a);
    }

    public void glDeleteTextures(int a) {
        org.lwjgl.opengl.GL11.glDeleteTextures(a);
    }

    public void glDeleteVertexArrays(int a) {
        org.lwjgl.opengl.GL30.glDeleteVertexArrays(a);
    }

    public void glDepthFunc(int a) {
        org.lwjgl.opengl.GL11.glDepthFunc(a);
    }

    public void glDepthMask(boolean a) {
        org.lwjgl.opengl.GL11.glDepthMask(a);
    }

    public void glDepthRange(double a, double b) {
        org.lwjgl.opengl.GL11.glDepthRange(a, b);
    }

    public void glDisable(int a) {
        org.lwjgl.opengl.GL11.glDisable(a);
    }

    public void glDrawElements(int a, int b, int c, long d) {
        org.lwjgl.opengl.GL11.glDrawElements(a, b, c, d);
    }

    public void glEnable(int a) {
        org.lwjgl.opengl.GL11.glEnable(a);
    }

    public void glEnableVertexAttribArray(int a) {
        org.lwjgl.opengl.GL20.glEnableVertexAttribArray(a);
    }

    public void glFrontFace(int a) {
        org.lwjgl.opengl.GL11.glFrontFace(a);
    }

    public int glGenBuffers() {
        return org.lwjgl.opengl.GL15.glGenBuffers();
    }

    public int glGenTextures() {
        return org.lwjgl.opengl.GL11.glGenTextures();
    }

    public int glGenVertexArrays() {
        return org.lwjgl.opengl.GL30.glGenVertexArrays();
    }

    public boolean glGetBoolean(int a) {
        return org.lwjgl.opengl.GL11.glGetBoolean(a);
    }

    public void glGetBooleanv(int a, ByteBuffer b) {
        bytes.clear();
        org.lwjgl.opengl.GL11.glGetBoolean(a, bytes);
        for (int n = 0; n < b.remaining(); n++) b.put(b.position() + n, bytes.get(n));
    }

    public float glGetFloat(int a) {
        return org.lwjgl.opengl.GL11.glGetFloat(a);
    }

    public void glGetFloatv(int a, float[] b) {
        floats.clear();
        org.lwjgl.opengl.GL11.glGetFloat(a, floats);
        for (int n = 0; n < b.length; n++) b[n] = floats.get(n);
    }

    public int glGetInteger(int a) {
        return org.lwjgl.opengl.GL11.glGetInteger(a);
    }

    public void glGetIntegerv(int a, int[] b) {
        ints.clear();
        org.lwjgl.opengl.GL11.glGetInteger(a, ints);
        for (int n = 0; n < b.length; n++) b[n] = ints.get(n);
    }

    public String glGetProgramInfoLog(int a) {
        return org.lwjgl.opengl.GL20.glGetProgramInfoLog(
                a, org.lwjgl.opengl.GL20.glGetProgrami(a, 35716));
    }

    public int glGetProgrami(int a, int b) {
        return org.lwjgl.opengl.GL20.glGetProgrami(a, b);
    }

    public String glGetShaderInfoLog(int a) {
        return org.lwjgl.opengl.GL20.glGetShaderInfoLog(
                a, org.lwjgl.opengl.GL20.glGetShaderi(a, 35716));
    }

    public int glGetShaderi(int a, int b) {
        return org.lwjgl.opengl.GL20.glGetShaderi(a, b);
    }

    public int glGetUniformLocation(int a, String b) {
        return org.lwjgl.opengl.GL20.glGetUniformLocation(a, b);
    }

    public boolean glIsEnabled(int a) {
        return org.lwjgl.opengl.GL11.glIsEnabled(a);
    }

    public void glLinkProgram(int a) {
        org.lwjgl.opengl.GL20.glLinkProgram(a);
    }

    public void glPixelStorei(int a, int b) {
        org.lwjgl.opengl.GL11.glPixelStorei(a, b);
    }

    public void glPolygonOffset(float a, float b) {
        org.lwjgl.opengl.GL11.glPolygonOffset(a, b);
    }

    public void glScissor(int a, int b, int c, int d) {
        org.lwjgl.opengl.GL11.glScissor(a, b, c, d);
    }

    public void glShaderSource(int a, String b) {
        org.lwjgl.opengl.GL20.glShaderSource(a, b);
    }

    public void glStencilFuncSeparate(int a, int b, int c, int d) {
        org.lwjgl.opengl.GL20.glStencilFuncSeparate(a, b, c, d);
    }

    public void glStencilMaskSeparate(int a, int b) {
        org.lwjgl.opengl.GL20.glStencilMaskSeparate(a, b);
    }

    public void glStencilOpSeparate(int a, int b, int c, int d) {
        org.lwjgl.opengl.GL20.glStencilOpSeparate(a, b, c, d);
    }

    public void glTexImage2D(int a, int b, int c, int d, int e, int f, int g, int h, ByteBuffer i) {
        org.lwjgl.opengl.GL11.glTexImage2D(a, b, c, d, e, f, g, h, i);
    }

    public void glTexImage3D(
            int a, int b, int c, int d, int e, int f, int g, int h, int i, ByteBuffer j) {
        org.lwjgl.opengl.GL12.glTexImage3D(a, b, c, d, e, f, g, h, i, j);
    }

    public void glTexParameteri(int a, int b, int c) {
        org.lwjgl.opengl.GL11.glTexParameteri(a, b, c);
    }

    public void glTexSubImage2D(
            int a, int b, int c, int d, int e, int f, int g, int h, ByteBuffer i) {
        org.lwjgl.opengl.GL11.glTexSubImage2D(a, b, c, d, e, f, g, h, i);
    }

    public void glTexSubImage3D(
            int a, int b, int c, int d, int e, int f, int g, int h, int i, int j, ByteBuffer k) {
        org.lwjgl.opengl.GL12.glTexSubImage3D(a, b, c, d, e, f, g, h, i, j, k);
    }

    public void glUniform1f(int a, float b) {
        org.lwjgl.opengl.GL20.glUniform1f(a, b);
    }

    public void glUniform1i(int a, int b) {
        org.lwjgl.opengl.GL20.glUniform1i(a, b);
    }

    public void glUniform2f(int a, float b, float c) {
        org.lwjgl.opengl.GL20.glUniform2f(a, b, c);
    }

    public void glUniform3f(int a, float b, float c, float d) {
        org.lwjgl.opengl.GL20.glUniform3f(a, b, c, d);
    }

    public void glUniform3fv(int a, float[] b) {
        floats.clear();
        floats.put(b).flip();
        org.lwjgl.opengl.GL20.glUniform3(a, floats);
    }

    public void glUniformMatrix4fv(int a, boolean b, float[] c) {
        floats.clear();
        floats.put(c).flip();
        org.lwjgl.opengl.GL20.glUniformMatrix4(a, b, floats);
    }

    public void glUseProgram(int a) {
        org.lwjgl.opengl.GL20.glUseProgram(a);
    }

    public void glVertexAttribPointer(int a, int b, int c, boolean d, int e, long f) {
        org.lwjgl.opengl.GL20.glVertexAttribPointer(a, b, c, d, e, f);
    }

    public void glViewport(int a, int b, int c, int d) {
        org.lwjgl.opengl.GL11.glViewport(a, b, c, d);
    }
}
