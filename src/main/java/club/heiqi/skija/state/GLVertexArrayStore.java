package club.heiqi.skija.state;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.GL_TRUE;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glGetVertexAttribI;

public class GLVertexArrayStore {
    // 存储顶点属性数组的启用状态
    private final boolean[] vertexAttribArrayStates;

    public GLVertexArrayStore() {
        // 获取支持的顶点属性最大数量
        IntBuffer maxAttribs = BufferUtils.createIntBuffer(1);
        glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS, maxAttribs);
        vertexAttribArrayStates = new boolean[maxAttribs.get(0)];
    }

    public void backup() {
        IntBuffer enable = BufferUtils.createIntBuffer(1);
        for (int i = 0; i < vertexAttribArrayStates.length; i++) {
            glGetVertexAttrib(i,GL_VERTEX_ATTRIB_ARRAY_ENABLED, enable);
            if (enable.get(0) == GL_TRUE) {
                vertexAttribArrayStates[i] = true;
            }
            else {
                vertexAttribArrayStates[i] = false;
            }
        }
    }

    public void restore() {
        for (int i = 0; i < vertexAttribArrayStates.length; i++) {
            if (vertexAttribArrayStates[i]) {
                glEnableVertexAttribArray(i);
            }
            else {
                glDisableVertexAttribArray(i);
            }
        }
    }
}
