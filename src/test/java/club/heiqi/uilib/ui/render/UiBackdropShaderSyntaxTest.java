package club.heiqi.uilib.ui.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * 磨玻璃着色器的离线语法守卫。
 *
 * <p>动机：构建链路只做字符串契约检查，<strong>不会真正编译 GLSL</strong>——shader
 * 语法错误要到真机运行时才暴露，而运行时的表现是"编译失败后静默退回固定管线"，
 * 观感变差却没有任何异常与日志。这类静默降级会让一次质感升级在用户机器上悄悄失效。
 * 本测试在离线阶段拦住其中可静态判定的几类：</p>
 *
 * <ol>
 *   <li>剥注释后括号必须配平（写歪一处在 GLSL 里是编译错误）；</li>
 *   <li>首行必须是 {@code #version}（缺它会被按 1.10 编译，LOD 类内建不可用）；</li>
 *   <li>不得使用 GLSL 1.20 非内建、需扩展的采样函数——{@code texture2Dbias} 属
 *       {@code ARB_shader_texture_lod}，依赖它会在缺扩展的机器上整体编译失败并静默降级，
 *       比不写更糟；大半径的预降采样已由快照 downsample + separable filter pass 承担。</li>
 * </ol>
 *
 * <p>刻意不用正则与反斜杠，纯 indexOf/charAt 扫描（见仓库踩坑记录：跨语言写正则会把
 * 转义吃掉）。注意黑名单必须在<strong>剥注释之后</strong>的文本上匹配，否则文档注释里
 * 提到函数名会误报。</p>
 */
public class UiBackdropShaderSyntaxTest {

    private static final String FRAG = "src/main/resources/shader/uiBackdropF.frag";
    private static final String VERT = "src/main/resources/shader/uiBackdropV.vert";

    /** 需扩展才可用的采样函数：出现在代码区即视为移植性风险。 */
    private static final String[] EXTENSION_ONLY_BUILTINS = {
            "texture2Dbias", "texture2DLod", "texture2DProjLod", "texture2DGradEXT", "textureGather",
    };

    @Test
    public void bracketsBalancedAfterStrippingComments() throws IOException {
        assertBalanced(FRAG);
        assertBalanced(VERT);
    }

    @Test
    public void versionDirectiveComesFirst() throws IOException {
        assertVersionFirst(FRAG);
        assertVersionFirst(VERT);
    }

    @Test
    public void noExtensionOnlySamplingBuiltins() throws IOException {
        assertNoBanned(FRAG);
        assertNoBanned(VERT);
    }

    /** 兜底锁：磨玻璃的可见产出必须来自 shader 分支，否则整个材质档是死代码。 */
    @Test
    public void fragmentWritesFragColor() throws IOException {
        String code = stripComments(read(FRAG));
        assertTrue("片元着色器必须写 gl_FragColor", code.indexOf("gl_FragColor") >= 0);
        assertTrue("材质分支必须真实存在（iosMaterial 判定）", code.indexOf("iosMaterial >") >= 0);
    }

    /**
     * 白 tint 亮度门控必须在位。
     *
     * <p>2026-09-01 真机反馈"light 档发灰、DARK 系列更有苹果味"的根因：无门控的
     * mix(c, 白, a) 把黑场抬到 a、压掉动态范围，暗背景必洗成脏灰。门控（whiteGate）
     * 让白 tint 只在亮背景吃满、暗背景近乎不叠，深色档 gate 恒 1。将来若有人"简化"
     * 回裸 mix，暗背景发灰会回归——本锁拦住。</p>
     */
    @Test
    public void whiteTintIsLumaGated() throws IOException {
        String code = stripComments(read(FRAG));
        assertTrue("白 tint 必须按背景亮度门控（whiteGate），否则暗背景洗灰",
                code.indexOf("whiteGate") >= 0);
        assertTrue("材质合成必须引用门控后的 tint",
                code.indexOf("materialTint.a + edgeTint * lensBevel) * whiteGate") >= 0);
    }

    /**
     * 液态缘带必须随面板尺寸收敛，且必须留出平坦中心。
     *
     * <p>2026-09-02 真机反馈「液态效果还能加强一点」的根因不是强度参数，而是缘带
     * 下限 8px 在小面板上饱和：聊天气泡短边 28px、半高 14，8px 缘带占满半高的 57%，
     * lensBevel 在气泡内部几乎恒为 1。恒 1 意味着整块面板做同一位移，而「一致的平移」
     * 在视觉上不可见——透镜的可辨识度全部来自位移的<b>梯度</b>。将来若有人把下限调回
     * 固定像素或去掉平坦中心约束，小面板液态会再次静默退化成普通磨砂。</p>
     */
    @Test
    public void liquidLensBandScalesWithPanelSize() throws IOException {
        String code = stripComments(read(FRAG));
        assertTrue("缘带必须以面板短边半宽为基准（比例项）",
                code.indexOf("lensShortHalf * 0.35") >= 0);
        assertTrue("缘带必须被短边半宽封顶，保证中心区 lensBevel 恒 0（平坦中心）",
                code.indexOf("lensShortHalf * 0.5") >= 0);
        assertTrue("折射位移必须以缘带梯度为前提（lensShift 乘 lensBevel）",
                code.indexOf("lensBevel * refraction") >= 0);
    }

    private static void assertBalanced(String relative) throws IOException {
        String code = stripComments(read(relative));
        List<Character> stack = new ArrayList<Character>();
        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch == '(' || ch == '{' || ch == '[') {
                stack.add(Character.valueOf(ch));
            } else if (ch == ')' || ch == '}' || ch == ']') {
                assertFalse("括号提前闭合: " + relative, stack.isEmpty());
                char open = stack.remove(stack.size() - 1).charValue();
                char want = ch == ')' ? '(' : (ch == '}' ? '{' : '[');
                assertEquals("括号类型不匹配: " + relative, want, open);
            }
        }
        assertTrue("括号未闭合，数量差=" + stack.size() + " (" + relative + ")", stack.isEmpty());
    }

    private static void assertVersionFirst(String relative) throws IOException {
        String[] lines = read(relative).split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            assertTrue(relative + " 首个非空行必须是 #version，实际=" + trimmed,
                    trimmed.startsWith("#version"));
            return;
        }
        assertTrue(relative + " 为空", false);
    }

    private static void assertNoBanned(String relative) throws IOException {
        String code = stripComments(read(relative));
        List<String> hits = new ArrayList<String>();
        for (String banned : EXTENSION_ONLY_BUILTINS) {
            if (code.indexOf(banned) >= 0) {
                hits.add(banned);
            }
        }
        assertTrue("使用了需扩展支持的采样函数，缺扩展机器上会编译失败并静默降级: " + hits + " (" + relative + ")",
                hits.isEmpty());
    }

    /** 剥掉行注释与块注释（字符串字面量在 shader 里不存在，无需处理转义）。 */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char ch = source.charAt(i);
            if (ch == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                while (i < source.length() && source.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (ch == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < source.length() && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                    if (source.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i += 2;
                continue;
            }
            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static String read(String relative) throws IOException {
        Path path = resolveExisting(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** 兼容不同 Gradle 测试工作目录：优先相对路径，必要时逐级向上查找。 */
    private static Path resolveExisting(String relativePath) {
        Path direct = Paths.get(relativePath);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("找不到 " + relativePath);
    }
}
