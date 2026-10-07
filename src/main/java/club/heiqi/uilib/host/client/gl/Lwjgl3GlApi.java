package club.heiqi.uilib.host.client.gl;

import java.nio.ByteBuffer;

/** LWJGL3宿主binding。
 * 直接调用LWJGL3：使用宿主官方逐类标记，防止LWJGL3ify把GL/GL*C误映射到LWJGL2 shim。
 * 标记止于此适配；其他旧GL11/Display调用仍须由宿主转换，不能给整个发行jar标记Aware。
 */
@me.eigenraven.lwjgl3ify.api.Lwjgl3Aware
public final class Lwjgl3GlApi implements GlApi {
    private final java.nio.IntBuffer attributeSize=java.nio.ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer(),attributeType=java.nio.ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer();
    public void glDisableVertexAttribArray(int location){org.lwjgl.opengl.GL20C.glDisableVertexAttribArray(location);}
    public void glGetVertexAttribfv(int location,int pname,float[] values){org.lwjgl.opengl.GL20C.glGetVertexAttribfv(location,pname,values);}
    public GlProgramAttribute glGetActiveAttribute(int program,int index){attributeSize.clear();attributeType.clear();String name=org.lwjgl.opengl.GL20C.glGetActiveAttrib(program,index,attributeSize,attributeType);return new GlProgramAttribute(name,org.lwjgl.opengl.GL20C.glGetAttribLocation(program,name),attributeType.get(0),attributeSize.get(0));}
    public GlCapabilities capabilities(){org.lwjgl.opengl.GLCapabilities c;try{c=org.lwjgl.opengl.GL.getCapabilities();}catch(IllegalStateException missing){c=org.lwjgl.opengl.GL.createCapabilities();}return new GlCapabilities(c,c.OpenGL32,c.OpenGL33||c.GL_ARB_sampler_objects);}
    public void glActiveTexture(int a){org.lwjgl.opengl.GL13C.glActiveTexture(a);}
    public void glAttachShader(int a,int b){org.lwjgl.opengl.GL20C.glAttachShader(a,b);}
    public void glBindAttribLocation(int a,int b,String c){org.lwjgl.opengl.GL20C.glBindAttribLocation(a,b,c);}
    public void glBindBuffer(int a,int b){org.lwjgl.opengl.GL15C.glBindBuffer(a,b);}
    public void glBindFragDataLocation(int a,int b,String c){org.lwjgl.opengl.GL30C.glBindFragDataLocation(a,b,c);}
    public void glBindFramebuffer(int a,int b){org.lwjgl.opengl.GL30C.glBindFramebuffer(a,b);}
    public void glBindSampler(int a,int b){org.lwjgl.opengl.GL33C.glBindSampler(a,b);}
    public void glBindTexture(int a,int b){org.lwjgl.opengl.GL11C.glBindTexture(a,b);}
    public void glBindVertexArray(int a){org.lwjgl.opengl.GL30C.glBindVertexArray(a);}
    public void glBlendColor(float a,float b,float c,float d){org.lwjgl.opengl.GL14C.glBlendColor(a,b,c,d);}
    public void glBlendEquationSeparate(int a,int b){org.lwjgl.opengl.GL20C.glBlendEquationSeparate(a,b);}
    public void glBlendFuncSeparate(int a,int b,int c,int d){org.lwjgl.opengl.GL14C.glBlendFuncSeparate(a,b,c,d);}
    public void glBufferData(int a,long b,int c){org.lwjgl.opengl.GL15C.glBufferData(a,b,c);}
    public void glBufferData(int a,int[] b,int c){org.lwjgl.opengl.GL15C.glBufferData(a,b,c);}
    public void glBufferSubData(int a,long b,ByteBuffer c){org.lwjgl.opengl.GL15C.glBufferSubData(a,b,c);}
    public void glColorMask(boolean a,boolean b,boolean c,boolean d){org.lwjgl.opengl.GL11C.glColorMask(a,b,c,d);}
    public void glCompileShader(int a){org.lwjgl.opengl.GL20C.glCompileShader(a);}
    public int glCreateProgram(){return org.lwjgl.opengl.GL20C.glCreateProgram();}
    public int glCreateShader(int a){return org.lwjgl.opengl.GL20C.glCreateShader(a);}
    public void glCullFace(int a){org.lwjgl.opengl.GL11C.glCullFace(a);}
    public void glDeleteBuffers(int a){org.lwjgl.opengl.GL15C.glDeleteBuffers(a);}
    public void glDeleteProgram(int a){org.lwjgl.opengl.GL20C.glDeleteProgram(a);}
    public void glDeleteShader(int a){org.lwjgl.opengl.GL20C.glDeleteShader(a);}
    public void glDeleteTextures(int a){org.lwjgl.opengl.GL11C.glDeleteTextures(a);}
    public void glDeleteVertexArrays(int a){org.lwjgl.opengl.GL30C.glDeleteVertexArrays(a);}
    public void glDepthFunc(int a){org.lwjgl.opengl.GL11C.glDepthFunc(a);}
    public void glDepthMask(boolean a){org.lwjgl.opengl.GL11C.glDepthMask(a);}
    public void glDepthRange(double a,double b){org.lwjgl.opengl.GL11C.glDepthRange(a,b);}
    public void glDisable(int a){org.lwjgl.opengl.GL11C.glDisable(a);}
    public void glDrawElements(int a,int b,int c,long d){org.lwjgl.opengl.GL11C.glDrawElements(a,b,c,d);}
    public void glEnable(int a){org.lwjgl.opengl.GL11C.glEnable(a);}
    public void glEnableVertexAttribArray(int a){org.lwjgl.opengl.GL20C.glEnableVertexAttribArray(a);}
    public void glFrontFace(int a){org.lwjgl.opengl.GL11C.glFrontFace(a);}
    public int glGenBuffers(){return org.lwjgl.opengl.GL15C.glGenBuffers();}
    public int glGenTextures(){return org.lwjgl.opengl.GL11C.glGenTextures();}
    public int glGenVertexArrays(){return org.lwjgl.opengl.GL30C.glGenVertexArrays();}
    public boolean glGetBoolean(int a){return org.lwjgl.opengl.GL11C.glGetBoolean(a);}
    public void glGetBooleanv(int a,ByteBuffer b){org.lwjgl.opengl.GL11C.glGetBooleanv(a,b);}
    public float glGetFloat(int a){return org.lwjgl.opengl.GL11C.glGetFloat(a);}
    public void glGetFloatv(int a,float[] b){org.lwjgl.opengl.GL11C.glGetFloatv(a,b);}
    public int glGetInteger(int a){return org.lwjgl.opengl.GL11C.glGetInteger(a);}
    public void glGetIntegerv(int a,int[] b){org.lwjgl.opengl.GL11C.glGetIntegerv(a,b);}
    public String glGetProgramInfoLog(int a){return org.lwjgl.opengl.GL20C.glGetProgramInfoLog(a);}
    public int glGetProgrami(int a,int b){return org.lwjgl.opengl.GL20C.glGetProgrami(a,b);}
    public String glGetShaderInfoLog(int a){return org.lwjgl.opengl.GL20C.glGetShaderInfoLog(a);}
    public int glGetShaderi(int a,int b){return org.lwjgl.opengl.GL20C.glGetShaderi(a,b);}
    public int glGetUniformLocation(int a,String b){return org.lwjgl.opengl.GL20C.glGetUniformLocation(a,b);}
    public boolean glIsEnabled(int a){return org.lwjgl.opengl.GL11C.glIsEnabled(a);}
    public void glLinkProgram(int a){org.lwjgl.opengl.GL20C.glLinkProgram(a);}
    public void glPixelStorei(int a,int b){org.lwjgl.opengl.GL11C.glPixelStorei(a,b);}
    public void glPolygonOffset(float a,float b){org.lwjgl.opengl.GL11C.glPolygonOffset(a,b);}
    public void glScissor(int a,int b,int c,int d){org.lwjgl.opengl.GL11C.glScissor(a,b,c,d);}
    public void glShaderSource(int a,String b){org.lwjgl.opengl.GL20C.glShaderSource(a,b);}
    public void glStencilFuncSeparate(int a,int b,int c,int d){org.lwjgl.opengl.GL20C.glStencilFuncSeparate(a,b,c,d);}
    public void glStencilMaskSeparate(int a,int b){org.lwjgl.opengl.GL20C.glStencilMaskSeparate(a,b);}
    public void glStencilOpSeparate(int a,int b,int c,int d){org.lwjgl.opengl.GL20C.glStencilOpSeparate(a,b,c,d);}
    public void glTexImage2D(int a,int b,int c,int d,int e,int f,int g,int h,ByteBuffer i){org.lwjgl.opengl.GL11C.glTexImage2D(a,b,c,d,e,f,g,h,i);}
    public void glTexImage3D(int a,int b,int c,int d,int e,int f,int g,int h,int i,ByteBuffer j){org.lwjgl.opengl.GL12C.glTexImage3D(a,b,c,d,e,f,g,h,i,j);}
    public void glTexParameteri(int a,int b,int c){org.lwjgl.opengl.GL11C.glTexParameteri(a,b,c);}
    public void glTexSubImage2D(int a,int b,int c,int d,int e,int f,int g,int h,ByteBuffer i){org.lwjgl.opengl.GL11C.glTexSubImage2D(a,b,c,d,e,f,g,h,i);}
    public void glTexSubImage3D(int a,int b,int c,int d,int e,int f,int g,int h,int i,int j,ByteBuffer k){org.lwjgl.opengl.GL12C.glTexSubImage3D(a,b,c,d,e,f,g,h,i,j,k);}
    public void glUniform1f(int a,float b){org.lwjgl.opengl.GL20C.glUniform1f(a,b);}
    public void glUniform1i(int a,int b){org.lwjgl.opengl.GL20C.glUniform1i(a,b);}
    public void glUniform2f(int a,float b,float c){org.lwjgl.opengl.GL20C.glUniform2f(a,b,c);}
    public void glUniform3f(int a,float b,float c,float d){org.lwjgl.opengl.GL20C.glUniform3f(a,b,c,d);}
    public void glUniform3fv(int a,float[] b){org.lwjgl.opengl.GL20C.glUniform3fv(a,b);}
    public void glUniformMatrix4fv(int a,boolean b,float[] c){org.lwjgl.opengl.GL20C.glUniformMatrix4fv(a,b,c);}
    public void glUseProgram(int a){org.lwjgl.opengl.GL20C.glUseProgram(a);}
    public void glVertexAttribPointer(int a,int b,int c,boolean d,int e,long f){org.lwjgl.opengl.GL20C.glVertexAttribPointer(a,b,c,d,e,f);}
    public void glViewport(int a,int b,int c,int d){org.lwjgl.opengl.GL11C.glViewport(a,b,c,d);}
}
