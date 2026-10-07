package club.heiqi.uilib.internal.devtools;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
/** 开发宿主的矩形 GL 回放，消费 logical 几何；不借道原版 Tessellator。 */
final class CoreRectangleRenderer implements AutoCloseable {
    private int program, vao, buffer, color;
    private final FloatBuffer vertices = BufferUtils.createFloatBuffer(12);
    private void initialize() {
        if (program != 0) return;
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int vertex = compile(GL20.GL_VERTEX_SHADER, "#version 150\nin vec2 position; void main(){gl_Position=vec4(position,0,1);}");
        int fragment = 0;
        try {
            fragment = compile(GL20.GL_FRAGMENT_SHADER, "#version 150\nuniform vec4 tint; out vec4 result; void main(){result=tint;}");
            program = GL20.glCreateProgram(); GL20.glAttachShader(program, vertex); GL20.glAttachShader(program, fragment);
            GL20.glBindAttribLocation(program, 0, "position"); GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 4096));
            color = GL20.glGetUniformLocation(program, "tint");
            vao = GL30.glGenVertexArrays(); buffer = GL15.glGenBuffers();
            GL30.glBindVertexArray(vao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GL20.glEnableVertexAttribArray(0); GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0L);
        } catch (RuntimeException failure) { close(); throw failure; }
        finally {
            GL20.glDeleteShader(vertex); if (fragment != 0) GL20.glDeleteShader(fragment);
            GL30.glBindVertexArray(previousVao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
        }
    }
    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type); GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String error = GL20.glGetShaderInfoLog(shader, 4096); GL20.glDeleteShader(shader); throw new IllegalStateException(error);
        }
        return shader;
    }
    void draw(PaintCommand command, int width, int height) {
        if (width == 0 || height == 0) return;
        initialize();
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        float left = 2f * command.box.x / width - 1, right = 2f * (command.box.x + command.box.width) / width - 1;
        float top = 1 - 2f * command.box.y / height, bottom = 1 - 2f * (command.box.y + command.box.height) / height;
        vertices.clear(); vertices.put(new float[] {left, top, left, bottom, right, bottom, left, top, right, bottom, right, top}); vertices.flip();
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL20.glUseProgram(program); GL30.glBindVertexArray(vao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STREAM_DRAW);
            int tint = command.color;
            GL20.glUniform4f(color, ((tint >>> 16) & 255) / 255f, ((tint >>> 8) & 255) / 255f, (tint & 255) / 255f, (tint >>> 24) / 255f);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        } finally {
            GL20.glUseProgram(previousProgram); GL30.glBindVertexArray(previousVao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
            GL14.glBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            enabled(GL11.GL_DEPTH_TEST, depth); enabled(GL11.GL_CULL_FACE, cull); enabled(GL11.GL_BLEND, blend);
        }
    }
    private static void enabled(int capability, boolean value) { if (value) GL11.glEnable(capability); else GL11.glDisable(capability); }
    @Override public void close() {
        if (buffer != 0) { GL15.glDeleteBuffers(buffer); buffer = 0; }
        if (vao != 0) { GL30.glDeleteVertexArrays(vao); vao = 0; }
        if (program != 0) { GL20.glDeleteProgram(program); program = 0; }
    }
}
