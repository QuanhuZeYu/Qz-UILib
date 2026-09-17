package club.heiqi.uilib.font.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 零字号语义守卫：字号域下界放开到 {@code 0}，而 {@code 0} 的含义由「两条边界短路」定义。
 *
 * <h3>为什么这组断言值钱</h3>
 * <p>下界从 1 改成 0 只是「允许写 0」；若没有边界短路，{@code 0} 会在下游被 {@code Math.max(1, …)}
 * 重新解释成 1 —— 现象是「明明设了 0 字号，文字还占 1px」。故真正要守的是：</p>
 * <ul>
 *   <li><b>域</b>：0 合法、负数仍归一到 0、<b>非零输入逐位不变</b>（等价判据）；</li>
 *   <li><b>度量边界</b>（{@code TextMeasureServiceSceneAdapter}）：字号 ≤ 0 ⇒ 不占空间；</li>
 *   <li><b>绘制边界</b>（{@code ScenePaintEngine}）：字号 ≤ 0 ⇒ 不上屏（TEXT 与 SEGMENTS 两条路径）。</li>
 * </ul>

 * <p>端到端证据（倍率 0 出图里 text/segments 命令数归零）由出图验收给出，不在单测里重复搭渲染栈。</p>
 */
public class ZeroFontSizeSemanticsTest {

    private static final Path ADAPTER = Paths.get(
            "src/main/java/club/heiqi/uilib/ui/scene/text/TextMeasureServiceSceneAdapter.java");
    private static final Path PAINT = Paths.get(
            "src/main/java/club/heiqi/uilib/ui/scene/paint/ScenePaintEngine.java");

    /** 域：0 合法；负数仍归一到下界；非零输入恒等（本轮等价判据）。 */
    @Test
    public void domainAcceptsZeroAndKeepsNonZeroInputsIdentical() {
        assertEquals("下界必须是 0（本轮放开的正是它）", 0, FontSizeLimits.MIN_FONT_SIZE_PX);
        assertEquals("0 必须是域内值", 0, FontSizeLimits.clampFontSize(0));
        assertEquals("0 必须通过调用点校验", 0, FontSizeLimits.requireValidFontSize(0));
        assertEquals("负数仍被归一（下界是 0，不是「无下界」）", 0, FontSizeLimits.clampFontSize(-5));
        try {
            FontSizeLimits.requireValidFontSize(-1);
            fail("调用点参数校验对负数仍应快速失败");
        } catch (IllegalArgumentException expected) {
            assertTrue("报错需带合法区间", expected.getMessage().contains("合法区间"));
        }
        for (int size = 1; size <= FontSizeLimits.MAX_FONT_SIZE_PX; size++) {
            assertEquals("非零字号必须逐位不变：" + size, size, FontSizeLimits.clampFontSize(size));
        }
        assertEquals("倍率下界必须与字号下界一起放开（否则倍率 0 折算出 0 仍被抬到 1）",
                0, SceneRuntime.FONT_SCALE_MIN_PERCENT);
        assertEquals("不缩放水位不变", 100, SceneRuntime.FONT_SCALE_NONE_PERCENT);
    }

    /**
     * 两条边界短路必须都在。
     *
     * <p>这条用源码结构而非行为断言：行为侧要搭真度量服务与渲染栈才能覆盖两条路径，而这里要守的恰恰是
     * 「短路还在不在」——删掉它不会有任何编译错误，只会让 0 悄悄退回 1px 文字。</p>
     */
    @Test
    public void bothBoundariesShortCircuitAtZero() throws Exception {
        String adapter = stripComments(read(ADAPTER));
        assertTrue("正锚：必须读到度量适配器真源码", adapter.contains("class TextMeasureServiceSceneAdapter"));
        assertTrue("度量边界必须逐方法短路 fontSizePx <= 0（实测 9 处入口）",
                occurrences(adapter, "fontSizePx <= 0") >= 9);
        // 断言对象必须是代码而非注释：下面的源码已剥注释，把判据写成注释文字会永远绿。
        assertTrue("零字号必须整段单行返回（不拆行）",
                adapter.contains("java.util.Collections.singletonList(safeText)"));

        String paint = stripComments(read(PAINT));
        assertTrue("正锚：必须读到绘制引擎真源码", paint.contains("class ScenePaintEngine"));
        assertTrue("SEGMENTS 路径必须短路零字号", paint.contains("segmentsFontSize <= 0"));
        assertTrue("TEXT 路径必须短路零字号（条件里带 fontSize > 0）", paint.contains("fontSize > 0"));
    }

    /** 读取 UTF-8 生产源码。 */
    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** 统计固定源码片段出现次数。 */
    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(needle, index)) >= 0; index += needle.length()) {
            count++;
        }
        return count;
    }

    /** 剥离行注释与块注释（保留换行以免拼接出假匹配）。 */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean inBlock = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (inBlock) {
                if (current == '*' && next == '/') {
                    inBlock = false;
                    index++;
                } else if (current == '\n') {
                    out.append('\n');
                }
                continue;
            }
            if (current == '/' && next == '/') {
                while (index < source.length() && source.charAt(index) != '\n') {
                    index++;
                }
                out.append('\n');
                continue;
            }
            if (current == '/' && next == '*') {
                inBlock = true;
                index++;
                continue;
            }
            out.append(current);
        }
        return out.toString();
    }
}
