package club.heiqi.uilib.internal.devtools.headless;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * headless 出图命令行出口：一条命令拿 PNG（单档或多档矩阵）。
 *
 * <pre>
 * qz-shot.bat --page=playground --size=1280x720 --out=build/reports/headless/x.png
 * qz-shot.bat --page=playground --sizes=640x360,1280x720,1920x1080,2560x1440 --out=out/shot.png
 * qz-shot.bat --page=playground --actions="move 315 88; frame; click; wait 4"
 * </pre>
 *
 * <p>多档时 {@code --out} 会按尺寸加后缀（{@code shot-640x360.png}），并在末尾输出矩阵汇总行。
 * 退出码：0 成功；2 参数错误；3 能力/上下文/渲染失败；4 像素自检未通过或矩阵中存在失败档位。</p>
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
        String sizes = null;
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
                } else if (arg.startsWith("--sizes=")) {
                    sizes = arg.substring("--sizes=".length());
                } else if (arg.startsWith("--size=")) {
                    int[] parsed = parseSize(arg.substring("--size=".length()));
                    width = parsed[0];
                    height = parsed[1];
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

        List<int[]> targets = new ArrayList<int[]>();
        try {
            if (sizes == null || sizes.trim().isEmpty()) {
                targets.add(new int[] {width, height});
            } else {
                for (String part : sizes.split(",")) {
                    targets.add(parseSize(part.trim()));
                }
            }
        } catch (RuntimeException e) {
            System.err.println("[headless] 尺寸列表非法：" + e.getMessage());
            return 2;
        }

        int okCount = 0;
        int failedCount = 0;
        List<String> matrixLines = new ArrayList<String>();
        for (int[] target : targets) {
            Path output = resolveOutput(out, page, target[0], target[1], targets.size() > 1);
            HeadlessRequest request;
            try {
                request = HeadlessRequest.builder().page(page).size(target[0], target[1]).frames(frames)
                        .background(background).text(text).script(script).settle(settle).maxFrames(maxFrames)
                        .output(output).build();
            } catch (RuntimeException e) {
                System.err.println("[headless] 请求非法：" + e.getMessage());
                return 2;
            }

            if (probeOnly) {
                try (HeadlessSession session = HeadlessSession.open(request)) {
                    System.out.println("[headless] capabilities: " + session.capabilities().summary());
                } catch (HeadlessFailure failure) {
                    failure.printDiagnosis(System.err);
                    return 3;
                }
                return 0;
            }

            boolean ok;
            try (HeadlessSession session = HeadlessSession.open(request)) {
                HeadlessArtifact artifact = session.capture();
                System.out.println(artifact.describe());
                ok = artifact.selfCheck().ok();
            } catch (HeadlessFailure failure) {
                failure.printDiagnosis(System.err);
                ok = false;
            }
            if (ok) {
                okCount++;
            } else {
                failedCount++;
            }
            matrixLines.add(target[0] + "x" + target[1] + "=" + (ok ? "ok" : "FAILED"));
        }

        if (targets.size() > 1) {
            System.out.println("[headless] matrix: " + okCount + "/" + targets.size() + " ok — "
                    + String.join(" ", matrixLines));
        }
        return failedCount == 0 ? 0 : 4;
    }

    /**
     * 解析 {@code WxH} 尺寸。
     *
     * @param value 尺寸文本
     * @return {宽, 高}
     */
    private static int[] parseSize(String value) {
        String[] parts = value.split("[xX]");
        if (parts.length != 2) {
            throw new IllegalArgumentException("尺寸需要 WxH 形式：" + value);
        }
        return new int[] {Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
    }

    /**
     * 解析输出路径：多档时在文件名里插入尺寸后缀，避免互相覆盖。
     *
     * @param out   命令行给出的输出路径；null 表示用默认路径
     * @param page  页面标识
     * @param width 宽
     * @param height 高
     * @param multi 是否多档
     * @return 目标路径
     */
    private static Path resolveOutput(String out, String page, int width, int height, boolean multi) {
        if (out == null) {
            return Paths.get("build", "reports", "headless", page + "-" + width + "x" + height + ".png");
        }
        Path base = Paths.get(out);
        if (!multi) {
            return base;
        }
        String name = base.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        String renamed = stem + "-" + width + "x" + height + extension;
        return base.getParent() == null ? Paths.get(renamed) : base.getParent().resolve(renamed);
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
        out.println("用法: HeadlessShotMain [--page=playground|text-probe] [--size=WxH | --sizes=WxH,WxH,…]"
                + " [--out=path] [--frames=N] [--settle=N] [--max-frames=N]"
                + " [--bg=RRGGBB|transparent] [--text=…] [--actions=\"…\"|--script=file] [--probe]");
    }
}
