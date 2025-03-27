package club.heiqi.qz_uilib.nativeWrap;

public class NativeSetGLState {
    public native void glUseProgram(int program);

    public native void glEnableVertexAttribArray(int i);
    public native void glDisableVertexAttribArray(int i);
    public native void glBindVertexArray(int vao);
    public native void glBindBuffer(int target, int buffer);

    public native void glActiveTexture(int texture);
    public native void glBindTexture(int target, int texture);

    public native void glEnable(int cap);
    public native void glDisable(int cap);

    public native void glCullFace(int face);
    public native void glFrontFace(int face);
}
