package club.heiqi.uilib.internal.devtools.headless;

import java.nio.file.Path;
import java.nio.file.Paths;

import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 一次出图请求：页面 + 尺寸 + 宿主背景 + 帧计划 + 产物路径，不可变。
 *
 * <p>语义：请求是设施的<b>唯一入口参数</b>——会话、CLI 与测试都只消费它，不允许旁路读取全局状态
 * （尺寸、缩放、帧数、背景一律来自本对象）。后续扩展（输入脚本、缩放/密度、产物集合）在此加字段，
 * 而不是给会话加隐藏开关。</p>
 *
 * <p><b>宿主背景是语义而非装饰</b>：UI 面板大量使用半透明玻璃配方，真机上叠在游戏世界之上才成立；
 * 从全透明开始时面板 alpha 会停在极低值（实测 meanAlpha≈18/255），导出 PNG 后看似「白底淡字」。
 * 默认给不透明中性深色（等价于屏幕底色），需要透明底时显式选择 {@code 0x00000000}。</p>
 *
 * <p><b>虚拟墙钟是语义而非便利</b>：聊天内容的时间戳（组头 {@code HH:mm}）与消息存活窗口都以
 * 「消息到达时刻」为基准，而到达时刻此前取进程当前时刻 ⇒ 跨分钟的两次出图必然像素不同，
 * 「同一命令出同一张图」不成立。故请求持有一个虚拟墙钟基准（{@link #clockMillis()}）：
 * 它是最后一条消息的到达时刻，也是帧时钟起点；更早的消息按固定节奏向前回推，缺省为
 * {@link #DEFAULT_CLOCK_MILLIS}。</p>
 *
 * <p><b>环境事实也是请求事实</b>：字号倍率（{@link #fontScalePercent()}，经
 * {@link SceneRuntime#setFontScale(int)} 投影到 runtime）、诊断采样开关（{@link #diagnostics()}，
 * 经 {@code HeadlessEnvironment} 投影到装配用的环境端口）与外观档（{@link #theme()}，
 * 经 {@code SceneThemes.install} 在<b>装配期</b>装到页面 runtime）同样由请求声明。三者此前无入口：
 * 字号倍率恒为不缩放、诊断开关恒取生产配置、外观恒取库默认 —— 于是「同一命令在不同机器/配置下出图
 * 不同」与「不开游戏就没法看一帧花在哪/换个配色长什么样」都无从解决。缺省值取各自的中性水位
 * （不缩放 / 不采样 / 不干预），与「未声明」逐位等价。</p>
 */
public final class HeadlessRequest {

    /** 最小可渲染边长：低于此值布局无意义，直接判为请求错误。 */
    private static final int MIN_EDGE = 16;
    /** 最大边长上限的保守值：真实上限由 GL_MAX_TEXTURE_SIZE 在会话期校验。 */
    private static final int MAX_EDGE = 16384;
    /** 默认宿主背景：不透明中性深色，代表 MC 屏幕底色。 */
    public static final int DEFAULT_BACKGROUND = 0xFF0E1014;
    /** 文本探针页面标识：渲染 {@link #text()} 一行文本，供字体路径对照与诊断。 */
    public static final String TEXT_PROBE_PAGE = "text-probe";
    /** 聊天页面标识：经生产内容构建入口渲染 chat3 内容树（气泡 / markdown / 公式 / 链接）。 */
    public static final String CHAT_PAGE = "chat";
    /**
     * HUD 页面标识：同一份聊天内容树走生产 HUD 宿主装配（{@code SceneHostWindow}：外壳 + 锚定放置）。
     *
     * <p>与 {@link #CHAT_PAGE} 的差别只有宿主装配——用于对照「同一份内容代码，换宿主」的观感，
     * 并核对 HUD 外壳几何、空窗隐藏与放置裁剪。</p>
     */
    public static final String HUD_PAGE = "hud";
    /**
     * 聊天消息分隔符：{@code --text} 用它切成多条消息。
     *
     * <p>不取 {@code |}：那是 Windows 命令行的管道符，写进 {@code --text} 会被 shell 先解释掉。</p>
     */
    public static final String CHAT_MESSAGE_SEPARATOR = ";;";
    /**
     * 聊天页面默认消息集（语法见 {@code ChatSceneProbeHost} 类注释）：
     * 玩家气泡 / markdown / 公式 / 链接自动识别 / markdown 系统行 / 纯系统文本 / 长文本折行。
     */
    public static final String CHAT_DEFAULT_TEXT = "Steve:**Markdown 粗体**与 `行内 code` 片段"
            + CHAT_MESSAGE_SEPARATOR + "Alex:行内公式 $E = mc^2$ 与分式 $\\frac{a}{b}$"
            + CHAT_MESSAGE_SEPARATOR + "Steve:链接 https://example.com/docs 自动识别"
            + CHAT_MESSAGE_SEPARATOR + "md:### Markdown 系统行\\n\\n- 列表项一\\n- 列表项二\\n\\n> 引用块"
            + CHAT_MESSAGE_SEPARATOR + "服务器：欢迎回到 Qz-UILib"
            + CHAT_MESSAGE_SEPARATOR + "Steve:长文本气泡按内容宽自动换行，超过气泡最大宽后继续折行显示。";

    /** 文本探针默认文本：中英数混排，覆盖 CJK 与拉丁字形。 */
    public static final String DEFAULT_PROBE_TEXT = "Qz UILib 对拍样本 Ag123";
    /**
     * 默认虚拟墙钟基准：{@code 2024-01-01T00:00:00Z} 的 epoch 毫秒。
     *
     * <p>固定基准让「同一命令在任何时刻出图」逐像素一致；显示值按<b>本机默认时区</b>格式化
     * （东八区为 {@code 08:00}）—— 同一台机器上确定，跨时区不同，这是有意的：UI 本就按本地时区显示。
     * 需要观察其它时刻的观感时用 {@code --clock=<epochMillis>} 覆盖。</p>
     */
    public static final long DEFAULT_CLOCK_MILLIS = 1_704_067_200_000L;

    private final String pageId;
    private final int pageIndex;
    private final int width;
    private final int height;
    private final int frames;
    private final int settleFrames;
    private final int maxFrames;
    private final int background;
    private final String text;
    private final String script;
    private final long clockMillis;
    private final int fontScalePercent;
    private final boolean diagnostics;
    private final String theme;
    private final Path output;

    private HeadlessRequest(String pageId, int pageIndex, int width, int height, int frames, int settleFrames, int maxFrames,
            int background, String text, String script, long clockMillis, int fontScalePercent, boolean diagnostics,
            String theme, Path output) {
        this.pageId = pageId;
        this.pageIndex = pageIndex;
        this.width = width;
        this.height = height;
        this.frames = frames;
        this.settleFrames = settleFrames;
        this.maxFrames = maxFrames;
        this.background = background;
        this.text = text;
        this.script = script;
        this.clockMillis = clockMillis;
        this.fontScalePercent = fontScalePercent;
        this.diagnostics = diagnostics;
        this.theme = theme;
        this.output = output;
    }

    /** @return 请求构建器（默认 playground / 1280x720 / 2 帧 / 不透明深色底） */
    public static Builder builder() {
        return new Builder();
    }

    /** @return 页面标识 */
    public String pageId() {
        return pageId;
    }

    /** @return 页面内下标；-1 表示由页面自身决定（如 playground 首页） */
    public int pageIndex() {
        return pageIndex;
    }

    /** @return 目标像素宽 */
    public int width() {
        return width;
    }

    /** @return 目标像素高 */
    public int height() {
        return height;
    }

    /** @return 最少推进帧数（首帧物化；此后才开始稳定判据） */
    public int frames() {
        return frames;
    }

    /** @return 稳定判据：连续多少帧像素指纹一致即停止推进 */
    public int settleFrames() {
        return settleFrames;
    }

    /** @return 帧数硬上限（不收敛时的兜底） */
    public int maxFrames() {
        return maxFrames;
    }

    /** @return 宿主背景色（ARGB） */
    public int background() {
        return background;
    }

    /** @return 文本探针页面渲染的文本（{@link #TEXT_PROBE_PAGE} 使用） */
    public String text() {
        return text;
    }

    /** @return 输入脚本（每条语句用换行或分号分隔）；空串表示无输入 */
    public String script() {
        return script;
    }

    /**
     * 虚拟墙钟基准（epoch 毫秒）：<b>最后一条</b>消息的到达时刻，同时是帧时钟起点。
     *
     * <p>更早的消息按真实到达节奏（1 秒一条）从本基准向前回推，故每条消息有互不相同的到达时刻；
     * 理由见 {@code ChatSceneProbeHost#ARRIVAL_SPACING_MILLIS}（同刻到达会让 HUD 高度裁剪一次
     * 剔空整树）。</p>
     *
     * @return 虚拟墙钟基准
     */
    public long clockMillis() {
        return clockMillis;
    }

    /**
     * 用户级字号缩放百分比（100 = 不缩放）。
     *
     * <p>作用点是解析出口的正交倍率层（见 {@code SceneFontEnvironment#fontScale()}），参与布局而非
     * 仅绘制。取值域由 {@link SceneRuntime} 单点声明，本类不复制字面量。</p>
     *
     * @return 字号缩放百分比
     */
    public int fontScalePercent() {
        return fontScalePercent;
    }

    /**
     * 是否打开诊断采样。
     *
     * @return true = 本次出图采集帧内事实（阶段耗时与计数），并在产物摘要中给出
     */
    public boolean diagnostics() {
        return diagnostics;
    }

    /**
     * 外观档名；{@code null} = 不干预（各页面维持自己生产装配的默认主题）。
     *
     * <p>合法档名清单与「为什么主题必须在装配期安装」见 {@code HeadlessThemes}。取字符串而非
     * {@code SceneTheme} 值对象：请求是数据，不该为了一个档名把外观值对象拉进它的依赖面。</p>
     *
     * @return 档名或 {@code null}
     */
    public String theme() {
        return theme;
    }

    /** @return PNG 产物路径 */
    public Path output() {
        return output;
    }

    /** @return 单行摘要（用于诊断与产物元信息） */
    public String summary() {
        return "page=" + pageId + (pageIndex >= 0 ? "#" + pageIndex : "") + " size=" + width + "x"
                + height + " frames=" + frames
                + " background=" + String.format("%08X", Integer.valueOf(background))
                + " settle=" + settleFrames + " maxFrames=" + maxFrames
                + " clock=" + clockMillis
                + " fontScale=" + fontScalePercent + "%"
                + " debug=" + diagnostics
                + " theme=" + (theme == null ? "(page default)" : theme)
                + (TEXT_PROBE_PAGE.equals(pageId) ? " text=\"" + text + "\"" : "")
                + " out=" + output;
    }

    /** 请求构建器：默认值集中在此，校验在 {@link #build()} 一次收口。 */
    public static final class Builder {

        private String pageId = "playground";
        private int pageIndex = -1;
        private int width = 1280;
        private int height = 720;
        private int frames = 2;
        private int settleFrames = 2;
        private int maxFrames = 60;
        private int background = DEFAULT_BACKGROUND;
        private String text = DEFAULT_PROBE_TEXT;
        private String script = "";
        private long clockMillis = DEFAULT_CLOCK_MILLIS;
        private int fontScalePercent = SceneRuntime.FONT_SCALE_NONE_PERCENT;
        private boolean diagnostics;
        private String theme;
        private Path output = Paths.get("build", "reports", "headless", "shot.png");

        private Builder() {
        }

        /** @param value 页面标识，不可为空 * @return this */
        public Builder page(String value) {
            this.pageId = value;
            return this;
        }

        /** @param value 页面内下标；-1 表示不指定 * @return this */
        public Builder pageIndex(int value) {
            this.pageIndex = value;
            return this;
        }

        /** @param w 宽 * @param h 高 * @return this */
        public Builder size(int w, int h) {
            this.width = w;
            this.height = h;
            return this;
        }

        /** @param value 最少帧数，至少 1 * @return this */
        public Builder frames(int value) {
            this.frames = value;
            return this;
        }

        /** @param value 稳定判据帧数，至少 1 * @return this */
        public Builder settle(int value) {
            this.settleFrames = value;
            return this;
        }

        /** @param value 帧数硬上限，需 >= 最少帧数 * @return this */
        public Builder maxFrames(int value) {
            this.maxFrames = value;
            return this;
        }

        /** @param argb 宿主背景色（ARGB），0x00000000 表示透明 * @return this */
        public Builder background(int argb) {
            this.background = argb;
            return this;
        }

        /** @param value 文本探针文本 * @return this */
        public Builder text(String value) {
            this.text = value;
            return this;
        }

        /** @param value 输入脚本 * @return this */
        public Builder script(String value) {
            this.script = value;
            return this;
        }

        /** @param value 虚拟墙钟基准（epoch 毫秒） * @return this */
        public Builder clock(long value) {
            this.clockMillis = value;
            return this;
        }

        /**
         * @param value 用户级字号缩放百分比；域为 {@link SceneRuntime#FONT_SCALE_MIN_PERCENT}
         *              ~{@link SceneRuntime#FONT_SCALE_MAX_PERCENT}
         * @return this
         */
        public Builder fontScale(int value) {
            this.fontScalePercent = value;
            return this;
        }

        /** @param value 是否打开诊断采样 * @return this */
        public Builder diagnostics(boolean value) {
            this.diagnostics = value;
            return this;
        }

        /**
         * @param value 外观档名；{@code null} = 不干预（各页面用自己的默认主题）
         * @return this
         */
        public Builder theme(String value) {
            this.theme = value;
            return this;
        }

        /** @param value PNG 路径 * @return this */
        public Builder output(Path value) {
            this.output = value;
            return this;
        }

        /** @return 校验通过的不可变请求 */
        public HeadlessRequest build() {
            if (pageId == null || pageId.isEmpty()) {
                throw new IllegalArgumentException("pageId 不可为空");
            }
            if (width < MIN_EDGE || height < MIN_EDGE || width > MAX_EDGE || height > MAX_EDGE) {
                throw new IllegalArgumentException("尺寸越界：" + width + "x" + height
                        + "（允许范围 " + MIN_EDGE + "~" + MAX_EDGE + "）");
            }
            if (frames < 1) {
                throw new IllegalArgumentException("frames 至少为 1");
            }
            if (settleFrames < 1) {
                throw new IllegalArgumentException("settle 至少为 1");
            }
            if (maxFrames < frames) {
                throw new IllegalArgumentException("maxFrames 不得小于 frames：" + maxFrames + " < " + frames);
            }
            if (output == null) {
                throw new IllegalArgumentException("output 不可为空");
            }
            // 字号倍率是参数错误而非运行期状态：越界直接失败，不静默钳制（与 SceneRuntime 的
            // requireValidFontScalePercent 同口径——信号路径才允许钳制，调用点参数不允许）。
            if (fontScalePercent < SceneRuntime.FONT_SCALE_MIN_PERCENT
                    || fontScalePercent > SceneRuntime.FONT_SCALE_MAX_PERCENT) {
                throw new IllegalArgumentException("fontScalePercent 越界：" + fontScalePercent
                        + "（合法区间 " + SceneRuntime.FONT_SCALE_MIN_PERCENT + "~"
                        + SceneRuntime.FONT_SCALE_MAX_PERCENT + "）");
            }
            // 档名合法性在请求构建期收口（参数错误 → 退出码 2），不等装配期才发现。
            HeadlessThemes.requireValid(theme);
            return new HeadlessRequest(pageId, pageIndex, width, height, frames, settleFrames, maxFrames,
                    background,
                    text == null ? "" : text, script == null ? "" : script, clockMillis, fontScalePercent,
                    diagnostics, theme, output);
        }
    }
}
