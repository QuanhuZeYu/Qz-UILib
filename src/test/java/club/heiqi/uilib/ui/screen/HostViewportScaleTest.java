package club.heiqi.uilib.ui.screen;

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

import club.heiqi.uilib.ui.scene.layout.LogicalBox;

/**
 * P5 §1.1 三分量铁律的宿主边界守卫。
 *
 * <p>「GUI Scale 不得混入内部闭环，缩放只在 host 边界成对转换」（Qz-UILib AGENTS.md:28）。
 * 本类钉住两件事：① 逻辑盒合成是<b>纯函数且默认零行为变化</b>（policy A）；
 * ② 全仓 {@code ui/scene/**} 内部闭环内<b>不存在</b>任何 GUI Scale / 物理分辨率符号 ——
 * 它只允许出现在 {@link HostViewportScale} 与宿主桥 {@code McScreenBridge}。</p>
 */
public class HostViewportScaleTest {

    @Test
    public void policyNativeIsIdentityAndIgnoresGuiScale() {
        for (int guiScale = 1; guiScale <= 4; guiScale++) {
            LogicalBox box = HostViewportScale.compose(1920, 1080, guiScale);
            assertEquals("policy A：逻辑盒 = 原生盒（不折算 GUI Scale）", 1920, box.widthPx());
            assertEquals("policy A：逻辑盒 = 原生盒（不折算 GUI Scale）", 1080, box.heightPx());
        }
        assertEquals("policy A 是默认策略", HostViewportScale.POLICY_NATIVE, HostViewportScale.POLICY);
    }

    @Test
    public void invalidInputsDegradeToPositiveBox() {
        LogicalBox box = HostViewportScale.compose(0, -5, 0);
        assertEquals(1, box.widthPx());
        assertEquals(1, box.heightPx());
        assertTrue("退化输入仍是可用的正尺寸盒", box.isPresent());
        assertFalse("空盒是未渲染态的唯一表示", LogicalBox.EMPTY.isPresent());
    }

    @Test
    public void policyFoldedNeverSqueezesLogicalBoxBelowMinimum() {
        // policy B 的规范语义：uiScaleHost = min(guiScale, max(1, min(floor(W/1280), floor(H/720))))
        assertEquals("1920x1080 折算被夹到 1（逻辑盒不得低于 1280x720）",
                1920, HostViewportScale.composeFolded(1920, 1080, 4).widthPx());
        assertEquals("2560x1440 + gs2 -> 1280x720",
                1280, HostViewportScale.composeFolded(2560, 1440, 2).widthPx());
        assertEquals(720, HostViewportScale.composeFolded(2560, 1440, 2).heightPx());
        assertEquals("3840x2160 + gs3 -> 1280x720",
                1280, HostViewportScale.composeFolded(3840, 2160, 3).widthPx());
        assertEquals("3840x2160 + gs2 -> 1920x1080",
                1920, HostViewportScale.composeFolded(3840, 2160, 2).widthPx());
        assertEquals("3840x2160 + gs1 -> 不折算",
                3840, HostViewportScale.composeFolded(3840, 2160, 1).widthPx());
    }

    @Test
    public void logicalBoxValueEqualityDrivesSignalDedup() {
        assertEquals(LogicalBox.of(800, 600), LogicalBox.of(800, 600));
        assertEquals(LogicalBox.of(800, 600).hashCode(), LogicalBox.of(800, 600).hashCode());
        assertFalse(LogicalBox.of(800, 600).equals(LogicalBox.of(800, 601)));
    }

    /**
     * 源码守卫：内部闭环（{@code ui/scene/**}）不得出现 GUI Scale / 物理分辨率符号。
     *
     * <p>扫描范围 = {@code ui/scene/**} <b>减去</b> {@code ui/scene/host/**}（宿主适配层，
     * 按定义在边界外侧，负责 GL/LWJGL 状态读取）。白名单只有两处，且都在宿主边界：
     * {@code ui/screen/HostViewportScale.java}（合成点）与 {@code ui/screen/McScreenBridge.java}
     * （唯一调用点，需要 {@code ScaledResolution} 做指针换算）。</p>
     */
    @Test
    public void internalSceneLoopNeverTouchesGuiScale() throws IOException {
        List<String> violations = new ArrayList<String>();
        collectViolations(Paths.get("src/main/java/club/heiqi/uilib/ui/scene"), violations);
        assertEquals("内部闭环出现 GUI Scale / 物理分辨率符号（AGENTS.md:28 铁律）：" + violations,
                0, violations.size());
        // 正锚：白名单文件确实存在且含 GUI Scale 符号，杜绝「扫描路径写错 -> 真空真」。
        String host = stripComments(read("src/main/java/club/heiqi/uilib/ui/screen/HostViewportScale.java"));
        assertTrue("正锚：合成点文件必须存在并处理 guiScale", host.contains("guiScaleFactor"));
        String bridge = stripComments(read("src/main/java/club/heiqi/uilib/ui/screen/McScreenBridge.java"));
        assertTrue("正锚：宿主桥必须调用合成点", bridge.contains("HostViewportScale.compose"));
    }

    private static void collectViolations(Path root, List<String> out) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        List<Path> stack = new ArrayList<Path>();
        stack.add(root);
        while (!stack.isEmpty()) {
            Path dir = stack.remove(stack.size() - 1);
            List<Path> children = new ArrayList<Path>();
            try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
                stream.forEach(children::add);
            }
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    stack.add(child);
                } else if (child.toString().endsWith(".java")) {
                    // ui/scene/host/** 是宿主适配层（LWJGL/GL 状态读取），按定义就在边界外侧；
                    // 内部闭环 = control / layout / node / paint / runtime / theme / image / input / overlay。
                    String path = child.toString().replace('\\', '/');
                    if (path.contains("/scene/host/")) {
                        continue;
                    }
                    String code = stripComments(new String(Files.readAllBytes(child),
                            StandardCharsets.UTF_8));
                    for (String needle : new String[] {"guiScale", "gameSettings", "ScaledResolution",
                            "displayWidth", "displayHeight"}) {
                        if (code.contains(needle)) {
                            out.add(child + "->" + needle);
                        }
                    }
                }
            }
        }
    }

    private static String read(String relative) throws IOException {
        Path path = Paths.get(relative);
        assertTrue("源码文件必须存在：" + relative, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** 去掉行注释与块注释后再扫描，避免注释里的说明性用词造成误报。 */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) i++;
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
