package club.heiqi.uilib.internal.devtools.headless;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * headless 出图命令行出口：一条命令拿 PNG。
 *
 * <pre>
 * java -cp &lt;classpath&gt; club.heiqi.uilib.internal.devtools.headless.HeadlessShotMain \
 *      --page=playground --size=1280x720 --out=build/reports/headless/x.png
 *      [--frames=2] [--bg=0E1014|transparent] [--probe]
 * </pre>
 *
 * <p>退出码：0 成功；2 参数错误；3 能力/上下文/渲染失败；4 像素自检未通过（图写出来了但内容可疑）。
 * agent 默认按退出码分支，再读 stdout 的 artifact 摘要。</p>
 */
public final class HeadlessShotMain {

    private HeadlessShotMain() {
    }

    /**
     * 进程入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        System.exit(run(args));
    }

    /**
     * 可测试的运行体（不调用 System.exit）。
     *
     * @param args 命令行参数
     * @return 退出码
     */
    static int run(String[] args) {
        String page = "playground";
        int width = 1280;
        int height = 720;
        int frames = 2;
        int settle = 2;
        int maxFrames = 60;
        int background = HeadlessRequest.DEFAULT_BACKGROUND;
        String text = HeadlessRequest.DEFAULT_PROBE_TEXT;
        String script = "";
        String out = null;
        boolean probeOnly = false;
        try {
            for (String arg : args) {
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    printUsage(System.out);
                    return 0;
                } else if ("--probe".equals(arg)) {
                    probeOnly = true;
                } else if (arg.startsWith("--page=")) {
                    page = arg.substring("--page=".length());
                } else if (arg.startsWith("--out=")) {
                    out = arg.substring("--out=".length());
                } else if (arg.startsWith("--bg=")) {
                    background = parseBackground(arg.substring("--bg=".length()));
                } else if (arg.startsWith("--text=")) {
                    text = arg.substring("--text=".length());
                } else if (arg.startsWith("--actions=")) {
                    script = arg.substring("--actions=".length());
                } else if (arg.startsWith("--script=")) {
                    script = readScriptFile(arg.substring("--script=".length()));
                } else if (arg.startsWith("--frames=")) {
                    frames = Integer.parseInt(arg.substring("--frames=".length()));
                } else if (arg.startsWith("--settle=")) {
                    settle = Integer.parseInt(arg.substring("--settle=".length()));
                } else if (arg.startsWith("--max-frames=")) {
                    maxFrames = Integer.parseInt(arg.substring("--max-frames=".length()));
                } else if (arg.startsWith("--size=")) {
                    String[] parts = arg.substring("--size=".length()).split("[xX]");
                    if (parts.length != 2) {
                        throw new IllegalArgumentException("size 需要 WxH 形式：" + arg);
                    }
                    width = Integer.parseInt(parts[0]);
                    height = Integer.parseInt(parts[1]);
                } else {
                    System.err.println("[headless] 未知参数：" + arg);
                    printUsage(System.err);
                    return 2;
                }
            }
        } catch (RuntimeException e) {
            System.err.println("[headless] 参数解析失败：" + e.getMessage());
            printUsage(System.err);
            return 2;
        }
        Path output = out == null
                ? Paths.get("build", "reports", "headless", page + "-" + width + "x" + height + ".png")
                : Paths.get(out);
        HeadlessRequest request;
        try {
            request = HeadlessRequest.builder().page(page).size(width, height).frames(frames)
                    .background(background).text(text).script(script).settle(settle).maxFrames(maxFrames)
                    .output(output).build();
        } catch (RuntimeException e) {
            System.err.println("[headless] 请求非法：" + e.getMessage());
            return 2;
        }
        try (HeadlessSession session = HeadlessSession.open(request)) {
            if (probeOnly) {
                System.out.println("[headless] capabilities: " + session.capabilities().summary());
                return 0;
            }
            HeadlessArtifact artifact = session.capture();
            System.out.println(artifact.describe());
            return artifact.selfCheck().ok() ? 0 : 4;
        } catch (HeadlessFailure failure) {
            failure.printDiagnosis(System.err);
            return 3;
        }
    }

    /**
     * 解析宿主背景：{@code RRGGBB} / {@code #RRGGBB} / {@code transparent}。
     *
     * @param value 参数值
     * @return ARGB 颜色
     */
    private static int parseBackground(String value) {
        if ("transparent".equalsIgnoreCase(value)) {
            return 0x00000000;
        }
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (hex.length() != 6) {
            throw new IllegalArgumentException("--bg 需要 RRGGBB 或 transparent：" + value);
        }
        return 0xFF000000 | (int) Long.parseLong(hex, 16);
    }

    /**
     * 读取脚本文件（IO 失败转成参数错误，交由参数解析路径统一处理）。
     *
     * @param path 文件路径
     * @return 文件文本
     */
    private static String readScriptFile(String path) {
        try {
            return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("脚本文件读取失败：" + path + "（" + e.getMessage() + "）");
        }
    }

    private static void printUsage(PrintStream out) {
        out.println("用法: HeadlessShotMain [--page=playground|text-probe] [--size=WxH] [--out=path]"
                + " [--frames=N] [--settle=N] [--max-frames=N] [--bg=RRGGBB|transparent] [--text=…]"
                + " [--actions=\"…\"|--script=file] [--probe]");
    }
}
