package club.heiqi.uilib.internal.devtools.headless;

import java.nio.file.Path;
import java.nio.file.Paths;

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
    /** 文本探针默认文本：中英数混排，覆盖 CJK 与拉丁字形。 */
    public static final String DEFAULT_PROBE_TEXT = "Qz UILib 对拍样本 Ag123";

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
    private final Path output;

    private HeadlessRequest(String pageId, int pageIndex, int width, int height, int frames, int settleFrames, int maxFrames,
            int background, String text, String script, Path output) {
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
            return new HeadlessRequest(pageId, pageIndex, width, height, frames, settleFrames, maxFrames,
                    background,
                    text == null ? "" : text, script == null ? "" : script, output);
        }
    }
}
