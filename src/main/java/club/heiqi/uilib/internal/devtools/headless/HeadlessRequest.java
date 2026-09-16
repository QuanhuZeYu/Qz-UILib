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

    private final String pageId;
    private final int width;
    private final int height;
    private final int frames;
    private final int background;
    private final Path output;

    private HeadlessRequest(String pageId, int width, int height, int frames, int background, Path output) {
        this.pageId = pageId;
        this.width = width;
        this.height = height;
        this.frames = frames;
        this.background = background;
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

    /** @return 目标像素宽 */
    public int width() {
        return width;
    }

    /** @return 目标像素高 */
    public int height() {
        return height;
    }

    /** @return 推进帧数（首帧物化、后续帧稳定） */
    public int frames() {
        return frames;
    }

    /** @return 宿主背景色（ARGB） */
    public int background() {
        return background;
    }

    /** @return PNG 产物路径 */
    public Path output() {
        return output;
    }

    /** @return 单行摘要（用于诊断与产物元信息） */
    public String summary() {
        return "page=" + pageId + " size=" + width + "x" + height + " frames=" + frames
                + " background=" + String.format("%08X", Integer.valueOf(background))
                + " out=" + output;
    }

    /** 请求构建器：默认值集中在此，校验在 {@link #build()} 一次收口。 */
    public static final class Builder {

        private String pageId = "playground";
        private int width = 1280;
        private int height = 720;
        private int frames = 2;
        private int background = DEFAULT_BACKGROUND;
        private Path output = Paths.get("build", "reports", "headless", "shot.png");

        private Builder() {
        }

        /** @param value 页面标识，不可为空 * @return this */
        public Builder page(String value) {
            this.pageId = value;
            return this;
        }

        /** @param w 宽 * @param h 高 * @return this */
        public Builder size(int w, int h) {
            this.width = w;
            this.height = h;
            return this;
        }

        /** @param value 帧数，至少 1 * @return this */
        public Builder frames(int value) {
            this.frames = value;
            return this;
        }

        /** @param argb 宿主背景色（ARGB），0x00000000 表示透明 * @return this */
        public Builder background(int argb) {
            this.background = argb;
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
            if (output == null) {
                throw new IllegalArgumentException("output 不可为空");
            }
            return new HeadlessRequest(pageId, width, height, frames, background, output);
        }
    }
}
