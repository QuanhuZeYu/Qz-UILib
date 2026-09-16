package club.heiqi.uilib.font.render.software;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * headless GL 出图与软光栅出图的文本几何对拍。
 *
 * <p>对拍口径是<b>几何</b>而不是逐像素：软光栅代表「既有验收通道」，GL 代表「真机路径」；
 * 两者的抗锯齿实现不同（软件侧默认不做多抽头 AA），但同一段文本、同一字号下的墨迹宽高必须同量级，
 * 否则说明字号解析、度量或布局在 headless 链路上发生了漂移。</p>
 *
 * <p>GL 侧走<b>进程外直启</b>（{@code HeadlessShotMain}），与 agent 真实使用路径一致；
 * 直启所需的 classpath 文件与 natives 目录由 Gradle 通过 system property 注入（见 build.gradle.kts）。</p>
 */
public class HeadlessTextParityTest {

    /** 对拍文本：中英数混排，覆盖 CJK 与拉丁字形。 */
    private static final String TEXT = "Qz UILib 对拍样本 Ag123";
    /** 对拍字号（UI 像素）：两侧共用同一语义。 */
    private static final int FONT_SIZE_PX = 32;
    /** GL 侧画布。 */
    private static final int CANVAS_WIDTH = 800;
    private static final int CANVAS_HEIGHT = 200;
    /** 两侧统一背景：软光栅侧固定 0xFF202020，GL 侧用 --bg=202020 对齐。 */
    private static final int BACKGROUND = 0xFF202020;

    @Test
    public void glShotMatchesSoftwareRasterGeometry() throws Exception {
        String classpathFile = System.getProperty("qz.headless.classpathFile", "");
        String nativesDir = System.getProperty("qz.headless.nativesDir", "");
        Assume.assumeTrue("未注入 headless 直启 classpath，跳过对拍",
                !classpathFile.isEmpty() && new File(classpathFile).isFile());

        Path glPng = runHeadlessShot(classpathFile, nativesDir);

        LatexSoftwareRenderKit.RenderResult software =
                LatexSoftwareRenderKit.render(TEXT, FONT_SIZE_PX, true);
        int[] softwareInk = inkBounds(software.pixels, software.width, software.height, BACKGROUND);
        Assert.assertTrue("软光栅侧没有墨迹，对拍前提不成立", softwareInk[2] >= softwareInk[0]);

        BufferedImage image = ImageIO.read(glPng.toFile());
        int[] glPixels = new int[image.getWidth() * image.getHeight()];
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), glPixels, 0, image.getWidth());
        int[] glInk = inkBounds(glPixels, image.getWidth(), image.getHeight(), BACKGROUND);
        Assert.assertTrue("GL 侧没有墨迹：出图链路可能又回到了「命令有、像素无」，先看自检输出",
                glInk[2] >= glInk[0]);

        double glWidth = glInk[2] - glInk[0] + 1;
        double softwareWidth = softwareInk[2] - softwareInk[0] + 1;
        double widthRatio = glWidth / softwareWidth;
        Assert.assertTrue("文本墨迹宽度应同量级：gl=" + glWidth + " 软光栅=" + softwareWidth
                + " 比值=" + widthRatio, widthRatio > 0.75d && widthRatio < 1.25d);

        double glHeight = glInk[3] - glInk[1] + 1;
        double softwareHeight = softwareInk[3] - softwareInk[1] + 1;
        double heightRatio = glHeight / softwareHeight;
        Assert.assertTrue("文本墨迹高度应同量级：gl=" + glHeight + " 软光栅=" + softwareHeight
                + " 比值=" + heightRatio, heightRatio > 0.6d && heightRatio < 1.4d);

        // 实测数据写进测试输出：对拍结论要可追溯，而不是只有「通过」两个字。
        System.out.println("[parity] glInk=" + glWidth + "x" + glHeight
                + " softwareInk=" + softwareWidth + "x" + softwareHeight
                + " widthRatio=" + widthRatio + " heightRatio=" + heightRatio
                + " softwareAdvance=" + software.advanceWidth);
    }

    /** 直启 headless 出图，返回 PNG 路径；失败即断言失败并带上进程输出。 */
    private static Path runHeadlessShot(String classpathFile, String nativesDir) throws Exception {
        Path output = Paths.get("build", "reports", "headless", "parity-gl.png").toAbsolutePath();
        Files.createDirectories(output.getParent());
        Files.deleteIfExists(output);

        String javaExecutable = Paths.get(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java").toString();
        String classpath = new String(Files.readAllBytes(Paths.get(classpathFile)), StandardCharsets.UTF_8).trim();
        List<String> command = new ArrayList<String>();
        command.add(javaExecutable);
        command.add("-Djava.library.path=" + nativesDir);
        command.add("-Xmx2g");
        command.add("-cp");
        command.add(classpath);
        command.add("club.heiqi.uilib.internal.devtools.headless.HeadlessShotMain");
        command.add("--page=text-probe");
        command.add("--size=" + CANVAS_WIDTH + "x" + CANVAS_HEIGHT);
        command.add("--bg=202020");
        command.add("--text=" + TEXT);
        command.add("--out=" + output);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String outputText;
        InputStream stream = process.getInputStream();
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) >= 0) {
                buffer.write(chunk, 0, read);
            }
            outputText = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            stream.close();
        }
        int exit = process.waitFor();
        Assert.assertEquals("headless 直启失败（exit=" + exit + "）：\n" + outputText, 0, exit);
        Assert.assertTrue("headless 未产出 PNG：\n" + outputText, Files.isRegularFile(output));
        return output;
    }

    /** 非背景像素的包围盒：{minX, minY, maxX, maxY}；无墨迹时 maxX < minX。 */
    private static int[] inkBounds(int[] argb, int width, int height, int background) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (argb[y * width + x] == background) {
                    continue;
                }
                if (x < minX) {
                    minX = x;
                }
                if (y < minY) {
                    minY = y;
                }
                if (x > maxX) {
                    maxX = x;
                }
                if (y > maxY) {
                    maxY = y;
                }
            }
        }
        return new int[] {minX, minY, maxX, maxY};
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
