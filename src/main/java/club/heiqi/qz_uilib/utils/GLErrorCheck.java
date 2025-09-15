package club.heiqi.qz_uilib.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.*;

public class GLErrorCheck {
    public static Logger LOG = LogManager.getLogger();

    public static void check() {
        int errorCode;
        while ((errorCode = GL11.glGetError()) != GL11.GL_NO_ERROR) {
            String errorName = getGLErrorName(errorCode);
            String errorDescription = getGLErrorDescription(errorCode);
            LOG.error("OpenGL Error: [{}] {} - {}", errorName, errorCode, errorDescription);
        }
    }

    private static String getGLErrorName(int errorCode) {
        switch (errorCode) {
            case GL11.GL_INVALID_ENUM:      return "GL_INVALID_ENUM";
            case GL11.GL_INVALID_VALUE:     return "GL_INVALID_VALUE";
            case GL11.GL_INVALID_OPERATION: return "GL_INVALID_OPERATION";
            case GL11.GL_STACK_OVERFLOW:    return "GL_STACK_OVERFLOW";
            case GL11.GL_STACK_UNDERFLOW:   return "GL_STACK_UNDERFLOW";
            case GL11.GL_OUT_OF_MEMORY:     return "GL_OUT_OF_MEMORY";
            case GL30.GL_INVALID_FRAMEBUFFER_OPERATION: return "GL_INVALID_FRAMEBUFFER_OPERATION";
            case GL31.GL_INVALID_INDEX:      return "GL_INVALID_INDEX";
            default:                        return "UNKNOWN_ERROR";
        }
    }

    private static String getGLErrorDescription(int errorCode) {
        switch (errorCode) {
            case GL11.GL_INVALID_ENUM:      return "枚举参数不合法";
            case GL11.GL_INVALID_VALUE:     return "数值参数超出范围";
            case GL11.GL_INVALID_OPERATION: return "指令在当前状态无效";
            case GL11.GL_STACK_OVERFLOW:    return "栈压入操作超出限制";
            case GL11.GL_STACK_UNDERFLOW:   return "栈弹出操作超出限制";
            case GL11.GL_OUT_OF_MEMORY:     return "内存不足无法执行指令";
            case GL30.GL_INVALID_FRAMEBUFFER_OPERATION: return "帧缓冲操作不完整";
            case GL31.GL_INVALID_INDEX:      return "无效的索引值（着色器位置）";
            default:                        return "未定义的错误类型";
        }
    }
}
