package club.heiqi.uilib.ui.scene.layout;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * LayoutBox 局部坐标读取守卫测试 —— 跨层坐标换算单点契约。
 *
 * <p>{@link LayoutBox#getX()}/{@link LayoutBox#getY()} 是<b>相对父容器</b>的局部坐标
 * （类 javadoc 明示）。宿主/控件层把它当屏幕坐标直接用必然错位——2026-09-01 磨玻璃
 * 实验室真机首验即此根因：玻璃面板画到了屏幕左缘（stage 局部 x≈0），而采样场在
 * 居中列内（绝对 x≈222）。</p>
 *
 * <p>跨层取绝对盒的权威单点是 {@link SceneGeometry#absoluteBox(SceneNode, int, int)}
 * （hit test / 事件坐标 / 拖拽全部走它，且注入祖先滚动偏移）。本守卫把"坐标换算
 * 只有一个权威"从口头约定升级为 CI 拦截：main 源码中读取 LayoutBox 变量 x/y 的
 * 文件必须在白名单内（递归累加器本体）。</p>
 *
 * <p>纯字符串扫描（零正则——反斜杠多层传递教训，见踩坑记录 2026-09 条目）。</p>
 */
public class LayoutBoxLocalCoordGuardTest {

    /** 扫描根（项目相对路径，与其他源码契约测试同口径）。 */
    private static final Path MAIN_ROOT = Paths.get("src", "main", "java");

    /**
     * 白名单：按设计做父→子递归累加局部坐标的权威实现本体。
     * SceneGeometry=absoluteBox 权威；ScenePaintEngine=display list 累加；SceneHitTester=命中累加。
     */
    private static final Set<String> SANCTIONED_ACCUMULATORS = new HashSet<String>(Arrays.asList(
            "club/heiqi/uilib/ui/scene/layout/SceneGeometry.java",
            "club/heiqi/uilib/ui/scene/paint/ScenePaintEngine.java",
            "club/heiqi/uilib/ui/scene/input/SceneHitTester.java"));

    /** 白名单外读取 LayoutBox 局部 x/y 即失败，并给出权威替代方案提示。 */
    @Test
    public void layoutBoxLocalCoordinatesOnlyReadBySanctionedAccumulators() throws IOException {
        final List<Path> javaFiles = new ArrayList<Path>();
        Files.walkFileTree(MAIN_ROOT, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        assertTrue("未扫描到任何 main 源文件（工作目录异常？）", !javaFiles.isEmpty());

        List<String> violations = new ArrayList<String>();
        for (Path file : javaFiles) {
            String relative = MAIN_ROOT.relativize(file).toString().replace((char) 92, '/');
            String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            int reads = countLocalCoordReads(source);
            if (reads > 0 && !SANCTIONED_ACCUMULATORS.contains(relative)) {
                violations.add(relative + " (x/y 读取 " + reads + " 处)");
            }
        }
        if (!violations.isEmpty()) {
            fail("LayoutBox.getX()/getY() 是相对父容器的局部坐标，跨层当屏幕坐标用必然错位"
                    + "（2026-09-01 磨玻璃实验室真机首验根因：面板画到屏幕左缘）。"
                    + "取绝对盒一律走 SceneGeometry.absoluteBox(node, rootAbsX, rootAbsY)"
                    + "（hit test 同源，含滚动偏移注入）。违例文件：" + violations);
        }
    }

    /**
     * 统计源码中"LayoutBox 声明变量"的 getX()/getY() 读取次数。
     *
     * <p>变量名提取两种形态：{@code LayoutBox name =}/{@code LayoutBox name,)}（声明）
     * 与 {@code name = (LayoutBox) ...}（转型赋值）。AnchorRect 等其它类型的 x/y 读取
     * 不计数（那是绝对盒，合法消费）。</p>
     *
     * @param source Java 源码全文
     * @return LayoutBox 变量的局部坐标读取次数
     */
    private static int countLocalCoordReads(String source) {
        Set<String> names = new HashSet<String>();
        int index = 0;
        while (true) {
            int at = source.indexOf("LayoutBox ", index);
            if (at < 0) {
                break;
            }
            int cursor = at + "LayoutBox ".length();
            while (cursor < source.length() && source.charAt(cursor) == ' ') {
                cursor++;
            }
            int start = cursor;
            while (cursor < source.length() && isIdentifierPart(source.charAt(cursor))) {
                cursor++;
            }
            String name = source.substring(start, cursor);
            if (!name.isEmpty() && !Character.isDigit(name.charAt(0))) {
                names.add(name);
            }
            index = at + 1;
        }
        index = 0;
        while (true) {
            int at = source.indexOf("(LayoutBox)", index);
            if (at < 0) {
                break;
            }
            int lineStart = source.lastIndexOf('\n', at) + 1;
            String line = source.substring(lineStart, at);
            int equals = line.lastIndexOf('=');
            if (equals > 0) {
                String left = line.substring(0, equals).trim();
                if (isPlainIdentifier(left)) {
                    names.add(left);
                }
            }
            index = at + 1;
        }
        int reads = 0;
        for (String name : names) {
            reads += countOccurrences(source, name + ".getX(");
            reads += countOccurrences(source, name + ".getY(");
        }
        return reads;
    }

    /** @return 字符串在 source 中出现次数（plain scan） */
    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while (true) {
            int at = source.indexOf(needle, index);
            if (at < 0) {
                return count;
            }
            count++;
            index = at + needle.length();
        }
    }

    /** @return 标识符字符（字母/数字/下划线） */
    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** @return 是否为纯标识符（非数字开头、非空、无点号——排除 this.field 形态） */
    private static boolean isPlainIdentifier(String value) {
        if (value.isEmpty() || Character.isDigit(value.charAt(0))) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!isIdentifierPart(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
