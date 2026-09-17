package club.heiqi.uilib.internal.devtools.headless;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * headless 出图命令行出口：一条命令拿 PNG（单张、分辨率矩阵、页面矩阵）。
 *
 * <pre>
 * qz-shot.bat --page=playground --size=1280x720 --out=out/shot.png
 * qz-shot.bat --page=playground --sizes=640x360,1280x720,1920x1080,2560x1440 --out=out/shot.png
 * qz-shot.bat --page=playground --page-indexes=0,1,2,3,4,5,6,7,8 --out=out/page.png
 * qz-shot.bat --page=playground --actions="move 315 88; frame; click; wait 4"
 * qz-shot.bat --page=chat --sizes=640x360,1280x720,2560x1440 --out=out/chat.png
 * qz-shot.bat --page=chat --size=1280x720 --font-scales=100,150,200 --out=out/chat.png
 * qz-shot.bat --page=chat --themes=liquid-glass-dark,liquid-glass-light --out=out/theme.png
 * qz-shot.bat --page=hud --size=1920x1080 --debug
 * </pre>

 * <p><b>三个批处理轴</b>：{@code --page-indexes} × {@code --font-scales} × {@code --sizes} 按笛卡尔积
 * 出图，多档时 {@code --out} 自动追加轴后缀（见 {@link #resolveOutput}），末尾输出汇总行。</p>
 *
 * <p><b>环境轴与尺寸轴同级</b>：字号倍率是 runtime 侧的环境量（见 {@code HeadlessRequest#fontScalePercent()}），
 * 诊断采样同理 —— 二者都不是「渲染选项」而是本次出图的环境声明，故与尺寸一样可按档位扫。</p>
 *
 * <p>批处理维度：`--sizes` 与 `--page-indexes` 可同时给出，按「页面 × 尺寸」笛卡尔积出图；
 * 多档时 {@code --out} 自动追加 {@code -p<下标>-<W>x<H>} 后缀，末尾输出汇总行。
 * 退出码：0 成功；2 参数错误；3 能力/上下文/渲染失败；4 像素自检未通过或批量中存在失败档位。</p>
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
        int code;
        try {
            code = run(args);
        } finally {
            // 出图完成即释放 GL 上下文与承载它的隐藏窗口容器：AWT 的退出钩子要等窗口资源回收，
            // 不显式释放会让 System.exit 停在该钩子里 —— 实测表现为「图已经写出来了，进程却不退出」。
            GlOffscreenSurface.shutdownContext();
        }
        System.exit(code);
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
        String pageIndexesArg = null;
        int pageIndex = -1;
        int width = 1280;
        int height = 720;
        int frames = 2;
        int settle = 2;
        int maxFrames = 60;
        int background = HeadlessRequest.DEFAULT_BACKGROUND;
        String text = HeadlessRequest.DEFAULT_PROBE_TEXT;
        String script = "";
        String out = null;
        long clockMillis = HeadlessRequest.DEFAULT_CLOCK_MILLIS;
        int fontScalePercent = SceneRuntime.FONT_SCALE_NONE_PERCENT;
        String fontScales = null;
        String theme = null;
        String themes = null;
        boolean diagnostics = false;
        boolean probeOnly = false;
        boolean framesGiven = false;
        try {
            for (String arg : args) {
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    printUsage(System.out);
                    return 0;
                } else if ("--probe".equals(arg)) {
                    probeOnly = true;
                } else if (arg.startsWith("--page=")) {
                    page = arg.substring("--page=".length());
                } else if (arg.startsWith("--page-indexes=")) {
                    pageIndexesArg = arg.substring("--page-indexes=".length());
                } else if (arg.startsWith("--page-index=")) {
                    pageIndex = Integer.parseInt(arg.substring("--page-index=".length()));
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
                    framesGiven = true;
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
                } else if (arg.startsWith("--themes=")) {
                    themes = arg.substring("--themes=".length());
                } else if (arg.startsWith("--theme=")) {
                    theme = arg.substring("--theme=".length());
                } else if (arg.startsWith("--font-scales=")) {
                    fontScales = arg.substring("--font-scales=".length());
                } else if (arg.startsWith("--font-scale=")) {
                    fontScalePercent = Integer.parseInt(arg.substring("--font-scale=".length()));
                } else if ("--debug".equals(arg)) {
                    diagnostics = true;
                } else if (arg.startsWith("--clock=")) {
                    clockMillis = Long.parseLong(arg.substring("--clock=".length()));
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

        // 聊天页默认：命令行未显式给 --text 时用演示消息集（省得每次出图都拼长参数串），
        // 未显式给 --frames 时把最小帧数提到 20——消息组首次合成有 180ms 入场动画（16ms/帧 → 12 帧），
        // 动画期间整树 opacity=0 且像素逐帧不变，稳定判据会把这段误判成「已收敛」而提前停帧出空图。
        if (HeadlessRequest.CHAT_PAGE.equals(page) || HeadlessRequest.HUD_PAGE.equals(page)) {
            if (HeadlessRequest.DEFAULT_PROBE_TEXT.equals(text)) {
                text = HeadlessRequest.CHAT_DEFAULT_TEXT;
            }
            if (!framesGiven) {
                frames = 20;
            }
        }

        List<Integer> pageTargets = new ArrayList<Integer>();
        List<int[]> sizeTargets = new ArrayList<int[]>();
        List<Integer> fontScaleTargets = new ArrayList<Integer>();
        List<String> themeTargets = new ArrayList<String>();
        try {
            if (pageIndexesArg == null || pageIndexesArg.trim().isEmpty()) {
                pageTargets.add(Integer.valueOf(pageIndex));
            } else {
                for (String part : pageIndexesArg.split(",")) {
                    pageTargets.add(Integer.valueOf(part.trim()));
                }
            }
            if (sizes == null || sizes.trim().isEmpty()) {
                sizeTargets.add(new int[] {width, height});
            } else {
                for (String part : sizes.split(",")) {
                    sizeTargets.add(parseSize(part.trim()));
                }
            }
            if (fontScales == null || fontScales.trim().isEmpty()) {
                fontScaleTargets.add(Integer.valueOf(fontScalePercent));
            } else {
                for (String part : fontScales.split(",")) {
                    fontScaleTargets.add(Integer.valueOf(part.trim()));
                }
            }
            // 主题档允许 null（= 不干预，各页面用自己生产装配的默认），故用「空串即缺省」表达。
            if (themes == null || themes.trim().isEmpty()) {
                themeTargets.add(theme);
            } else {
                for (String part : themes.split(",")) {
                    String name = part.trim();
                    themeTargets.add(name.isEmpty() ? null : name);
                }
            }
        } catch (RuntimeException e) {
            System.err.println("[headless] 批量参数非法：" + e.getMessage());
            return 2;
        }

        int total = pageTargets.size() * sizeTargets.size() * fontScaleTargets.size() * themeTargets.size();
        boolean multi = total > 1;
        int okCount = 0;
        int failedCount = 0;
        int environmentFailures = 0;
        List<String> labels = new ArrayList<String>();
        for (Integer targetPageIndex : pageTargets) {
            for (String targetTheme : themeTargets) {
                for (Integer targetFontScale : fontScaleTargets) {
                    for (int[] size : sizeTargets) {
                        int scalePercent = targetFontScale.intValue();
                        Path output = resolveOutput(out, page, targetPageIndex.intValue(), targetTheme,
                                scalePercent, size[0], size[1], multi);
                        HeadlessRequest request;
                        try {
                            request = HeadlessRequest.builder().page(page).pageIndex(targetPageIndex.intValue())
                                    .size(size[0], size[1]).frames(frames).background(background).text(text)
                                    .script(script).settle(settle).maxFrames(maxFrames).clock(clockMillis)
                                    .fontScale(scalePercent).diagnostics(diagnostics).theme(targetTheme)
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
                            // 环境 / 上下文 / 装配 / 帧 / 读回 / 编码失败属于「设施没能出图」，与「图出来了但内容可疑」分开报，
                            // 否则 agent 无法按退出码区分「环境没准备好」和「UI 有问题」。
                            environmentFailures++;
                            ok = false;
                        }
                        if (ok) {
                            okCount++;
                        } else {
                            failedCount++;
                        }
                        labels.add(labelOf(page, targetPageIndex.intValue(), targetTheme, scalePercent,
                                size[0], size[1]) + "=" + (ok ? "ok" : "FAILED"));
                    }
                }
            }
        }

        if (multi) {
            System.out.println("[headless] batch: " + okCount + "/" + total + " ok — " + String.join(" ", labels));
        }
        if (environmentFailures > 0) {
            return 3;
        }
        return failedCount == 0 ? 0 : 4;
    }

    /**
     * 汇总行标签：页 + 尺寸，偏离缺省的环境维度补一段。
     *
     * @param page 页面标识
     * @param pageIndex 页下标；-1 表示不指定
     * @param theme 外观档名；{@code null} 表示不干预（不显示）
     * @param fontScalePercent 字号缩放百分比；缺省水位不显示，避免逐条汇总被重复信息淹没
     * @param width 宽
     * @param height 高
     * @return 标签
     */
    private static String labelOf(String page, int pageIndex, String theme, int fontScalePercent, int width,
            int height) {
        return (pageIndex >= 0 ? page + "#" + pageIndex : page) + "@" + width + "x" + height
                + (fontScalePercent == SceneRuntime.FONT_SCALE_NONE_PERCENT ? ""
                        : " fs" + fontScalePercent + "%")
                + (theme == null ? "" : " theme=" + theme);
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
     * 解析输出路径：批量时在文件名里插入各轴后缀，避免互相覆盖。
     *
     * <p>后缀的组成规则是「<b>偏离缺省取值的维度</b> + 目标尺寸」：尺寸恒进（同一页面的不同尺寸是
     * 不同产物，没有「缺省尺寸」可言），页下标在指定时进，字号倍率只在非缺省水位时进 —— 于是既有
     * 命令的产物路径逐字不变，新增维度也不会与既有命名撞车。</p>
     *
     * @param out       命令行给出的输出路径；null 表示用默认路径
     * @param page      页面标识
     * @param pageIndex 页下标；-1 表示不指定
     * @param theme     外观档名；{@code null} 表示不干预（不进后缀）
     * @param fontScalePercent 字号缩放百分比
     * @param width     宽
     * @param height    高
     * @param multi     是否批量
     * @return 目标路径
     */
    private static Path resolveOutput(String out, String page, int pageIndex, String theme,
            int fontScalePercent, int width, int height, boolean multi) {
        String suffix = (pageIndex >= 0 ? "-p" + pageIndex : "")
                + (theme == null ? "" : "-th" + theme)
                + (fontScalePercent == SceneRuntime.FONT_SCALE_NONE_PERCENT ? "" : "-fs" + fontScalePercent)
                + "-" + width + "x" + height;
        if (out == null) {
            return Paths.get("build", "reports", "headless", page + suffix + ".png");
        }
        Path base = Paths.get(out);
        if (!multi) {
            return base;
        }
        String name = base.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        String renamed = stem + suffix + extension;
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
        out.println("用法: HeadlessShotMain [--page=playground|text-probe|chat|hud]"
                + " [--page-index=N | --page-indexes=N,N,…]"
                + " [--size=WxH | --sizes=WxH,WxH,…] [--out=path] [--frames=N] [--settle=N] [--max-frames=N]"
                + " [--bg=RRGGBB|transparent] [--text=…] [--actions=\"…\"|--script=file]"
                + " [--clock=epochMillis]"
                + " [--theme=NAME | --themes=NAME,…] [--font-scale=P | --font-scales=P,P,…] [--debug]"
                + " [--probe]");
        out.println("主题档: " + HeadlessThemes.names() + "（不给 = 各页面用自己的默认外观）");
    }
}
