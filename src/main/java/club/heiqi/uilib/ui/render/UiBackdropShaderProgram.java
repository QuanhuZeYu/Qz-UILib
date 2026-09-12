package club.heiqi.uilib.ui.render;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import club.heiqi.uilib.gl.shader.ShaderProgramSupport;
import org.lwjgl.opengl.GL20;

/**
 * UI 磨玻璃专用着色器程序。
 *
 * <h3>抽头预算变体（同一个 frag 源、两种卷积核）</h3>
 * <p>{@code uiBackdropF.frag} 用 {@code #if UIB_TAP_BUDGET >= 13} 把抽头段分成两段：
 * 完整档 13 抽头（与引入档位前逐字节同一段代码）与省电档 9 抽头变体。
 * 宿主的接法是<b>在 {@code #version} 之后注入</b> {@code #define UIB_TAP_BUDGET <n>}——
 * 位置固定、早于源文件里任何 {@code #if}，因此两个档位各自得到一份语义确定、
 * 与 {@code #if} 包裹前等价的 GLSL（13 档的全部抽头行原样参与预处理，未被削弱）。</p>
 *
 * <p>实例按预算缓存：同一预算永远返回同一实例（进程级最多两个 GL program 对象），
 * 预算取值只来自 {@link BackdropQuality#tapBudget()}。未知预算一律回落完整档——
 * 宁可按完整档多采样，也不因为预算参数错值静默降低观感。</p>
 */
final class UiBackdropShaderProgram {

    /** 完整档抽头预算：对应 frag 的 {@code #if UIB_TAP_BUDGET >= 13} 分支。 */
    private static final int FULL_TAP_BUDGET = 13;

    /** 省电档抽头预算：对应 frag 的 {@code #else} 分支（9 抽头向日葵螺旋核）。 */
    private static final int ECO_TAP_BUDGET = 9;

    /**
     * 抽头预算宏名。宿主在 {@code #version} 之后注入
     * {@code #define UIB_TAP_BUDGET <n>}，frag 用它选抽头段；frag 内另有
     * {@code #ifndef} 默认块，保证资源被直接编译时仍是完整档。
     */
    static final String TAP_BUDGET_DEFINE = "UIB_TAP_BUDGET";

    /** 完整档程序：进程级唯一实例。 */
    private static final UiBackdropShaderProgram FULL_BUDGET_PROGRAM =
            new UiBackdropShaderProgram(FULL_TAP_BUDGET);

    /** 省电档程序：进程级唯一实例。 */
    private static final UiBackdropShaderProgram ECO_BUDGET_PROGRAM =
            new UiBackdropShaderProgram(ECO_TAP_BUDGET);

    /** 本实例的抽头预算（决定注入进 frag 的 define 值）。 */
    private final int tapBudget;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Map<String, Integer> uniformLocations = new LinkedHashMap<String, Integer>();
    private final Set<String> missingUniforms = new HashSet<String>();
    private boolean unavailable;
    private String lastFailureMessage = "";
    private int shaderProgramId;

    private UiBackdropShaderProgram(int tapBudget) {
        this.tapBudget = tapBudget;
    }

    /**
     * 按抽头预算取着色器程序实例。
     *
     * @param tapBudget 抽头预算（含中心样本）；13 = 完整档，9 = 省电档
     * @return 该预算对应的进程级唯一实例；未知预算回落完整档实例
     */
    static UiBackdropShaderProgram programFor(int tapBudget) {
        return tapBudget == ECO_TAP_BUDGET ? ECO_BUDGET_PROGRAM : FULL_BUDGET_PROGRAM;
    }

    /**
     * 返回本实例的抽头预算（诊断与离线测试用）。
     *
     * @return 13 或 9
     */
    int getTapBudget() {
        return tapBudget;
    }

    /**
     * 在 GLSL 源里注入抽头预算宏：位置固定在首个非空行（{@code #version}）之后。
     *
     * <p>必须早于源文件里任何 {@code #if UIB_TAP_BUDGET}：{@code #version} 之后是 GLSL 允许
     * 插入 define 的最早位置，也是唯一能保证"先定义后求值"的位置。首个非空行不是
     * {@code #version} 时抛异常（随后被 {@link #ensureInitialized()} 转换为"程序不可用"，
     * 不会拿着错误预算静默编译）。</p>
     *
     * @param source GLSL 源
     * @param tapBudget 抽头预算
     * @return 注入后的源
     */
    static String withTapBudgetDefine(String source, int tapBudget) {
        String define = "#define " + TAP_BUDGET_DEFINE + " " + tapBudget;
        int offset = 0;
        while (offset <= source.length()) {
            int lineEnd = source.indexOf('\n', offset);
            String line = (lineEnd < 0 ? source.substring(offset) : source.substring(offset, lineEnd)).trim();
            if (!line.isEmpty()) {
                if (!line.startsWith("#version")) {
                    throw new IllegalStateException("着色器首个非空行必须是 #version，实际=" + line);
                }
                if (lineEnd < 0) {
                    return source + "\n" + define + "\n";
                }
                return source.substring(0, lineEnd + 1) + define + "\n" + source.substring(lineEnd + 1);
            }
            if (lineEnd < 0) {
                break;
            }
            offset = lineEnd + 1;
        }
        throw new IllegalStateException("着色器缺少 #version 指令");
    }

    /**
     * 尝试确保着色器已初始化。
     *
     * @return 是否可用
     */
    boolean ensureInitialized() {
        if (unavailable) {
            return false;
        }
        if (initialized.get()) {
            return shaderProgramId != 0;
        }
        if (!initialized.compareAndSet(false, true)) {
            return shaderProgramId != 0;
        }

        try {
            shaderProgramId = GL20.glCreateProgram();
            uniformLocations.clear();
            missingUniforms.clear();
            lastFailureMessage = "";
            loadProgram();
            return true;
        } catch (RuntimeException exception) {
            unavailable = true;
            lastFailureMessage = exception.getMessage() == null ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            closeProgram();
            return false;
        }
    }

    /**
     * 返回最近一次 shader 初始化失败原因。
     *
     * @return 失败原因；没有失败时为空字符串
     */
    String getLastFailureMessage() {
        return lastFailureMessage;
    }

    /**
     * 绑定着色器程序。
     */
    void bind() {
        GL20.glUseProgram(shaderProgramId);
    }

    /**
     * 解绑着色器程序。
     */
    void unbind() {
        GL20.glUseProgram(0);
    }

    /**
     * 设置整型 uniform。
     *
     * @param name uniform 名称
     * @param value 数值
     */
    void setUniformI(String name, int value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform1i(location, value);
        }
    }

    /**
     * 设置浮点 uniform。
     *
     * @param name uniform 名称
     * @param value 数值
     */
    void setUniformF(String name, float value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform1f(location, value);
        }
    }

    /**
     * 设置二维向量 uniform。
     *
     * @param name uniform 名称
     * @param x X 分量
     * @param y Y 分量
     */
    void setUniform2f(String name, float x, float y) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform2f(location, x, y);
        }
    }

    /**
     * 设置三维向量 uniform。
     *
     * @param name uniform 名称
     * @param x X 分量
     * @param y Y 分量
     * @param z Z 分量
     */
    void setUniform3f(String name, float x, float y, float z) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform3f(location, x, y, z);
        }
    }

    /**
     * 设置四维向量 uniform。
     *
     * @param name uniform 名称
     * @param x X 分量
     * @param y Y 分量
     * @param z Z 分量
     * @param w W 分量
     */
    void setUniform4f(String name, float x, float y, float z, float w) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform4f(location, x, y, z, w);
        }
    }

    private void closeProgram() {
        unbind();
        if (shaderProgramId != 0) {
            GL20.glDeleteProgram(shaderProgramId);
            shaderProgramId = 0;
        }
        uniformLocations.clear();
        missingUniforms.clear();
    }

    private void loadProgram() {
        int vertexShaderId = ShaderProgramSupport.compileShader(
                ShaderProgramSupport.readText(getClass(), "shader/uiBackdropV.vert", "读取 UI backdrop 着色器失败: "),
                GL20.GL_VERTEX_SHADER,
                "UI backdrop 着色器编译失败: ");
        // 顶点着色器与抽头预算无关；片元着色器注入 UIB_TAP_BUDGET 决定抽头段。
        String fragmentSource = ShaderProgramSupport.readText(getClass(), "shader/uiBackdropF.frag",
                "读取 UI backdrop 着色器失败: ");
        int fragmentShaderId = ShaderProgramSupport.compileShader(
                withTapBudgetDefine(fragmentSource, tapBudget),
                GL20.GL_FRAGMENT_SHADER,
                "UI backdrop 着色器编译失败: ");

        GL20.glAttachShader(shaderProgramId, vertexShaderId);
        GL20.glAttachShader(shaderProgramId, fragmentShaderId);
        ShaderProgramSupport.linkAndValidateProgram(shaderProgramId, "UI backdrop 着色器链接失败: ",
                "UI backdrop 着色器验证失败: ");

        GL20.glDeleteShader(vertexShaderId);
        GL20.glDeleteShader(fragmentShaderId);
    }

    private int getUniformLocation(String name) {
        if (uniformLocations.containsKey(name)) {
            return uniformLocations.get(name).intValue();
        }
        if (missingUniforms.contains(name)) {
            return -1;
        }

        int location = GL20.glGetUniformLocation(shaderProgramId, name);
        if (location == -1) {
            missingUniforms.add(name);
            return -1;
        }
        uniformLocations.put(name, Integer.valueOf(location));
        return location;
    }

}
