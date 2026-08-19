package club.heiqi.uilib.ui.scene.text;

/**
 * scene 层文本内容解析模式的唯一语义锚。
 *
 * <p>scene 核心（node/layout/paint）因 I10 边界不得 import {@code ui.text.*}，历史上以原始 int
 * 编码传递内容模式（0/1/2），三模式语义散落于 paint.TextStyle 常量、SceneNode setter、
 * measurer 接缝与两套 switch（normalizeTextMode / mapTextMode）。本枚举收敛全部语义：</p>
 *
 * <ul>
 *   <li>{@link #UILIB_RAW}（code 0）：按 UILib 原始文本处理，{@code §} 等字符不再被当作格式码解析。</li>
 *   <li>{@link #MINECRAFT_FORMATTED}（code 1）：按 Minecraft {@code §} 颜色与样式码解析（兼容遗产路径）。</li>
 *   <li>{@link #RICH_TAGS}（code 2）：按 UILib 现代富文本标签语法解析（{@code <color=...>} 等）。</li>
 * </ul>
 *
 * <p><b>编码契约</b>：各常量的 {@link #getCode()} 与 {@link club.heiqi.uilib.ui.scene.paint.TextStyle}
 * 的 {@code TEXT_MODE_*} 常量、渲染层 {@code ui.text.TextContentMode} 的序数值逐位对齐，
 * 由 {@code SceneTextModeTest} 编译期守卫锁死；越界编码经 {@link #fromCode} 回落
 * {@link #UILIB_RAW}（吸收原 paint.TextStyle.normalizeTextMode / SceneNode setter 的归一语义）。</p>
 */
public enum SceneTextMode {

    /** 原始文本：{@code §} 等字符按字面量处理。 */
    UILIB_RAW(0),

    /** Minecraft {@code §} 格式码（兼容遗产路径）。 */
    MINECRAFT_FORMATTED(1),

    /** UILib 现代富文本标签语法（{@code <color=...>}、{@code <b>}、{@code <size=N>} 等）。 */
    RICH_TAGS(2);

    /** 稳定编码（与 paint.TextStyle TEXT_MODE_* 常量、ui.text.TextContentMode 序数值对齐）。 */
    private final int code;

    /** 枚举实例缓存（fromCode 热路径避免每次 values() 克隆）。 */
    private static final SceneTextMode[] VALUES = values();

    SceneTextMode(int code) {
        this.code = code;
    }

    /** @return 稳定编码（0/1/2） */
    public int getCode() {
        return code;
    }

    /**
     * 按稳定编码解析；越界回落 {@link #UILIB_RAW}（原始文本）。
     *
     * @param code 稳定编码（0/1/2）
     * @return 对应模式，越界时返回 {@link #UILIB_RAW}
     */
    public static SceneTextMode fromCode(int code) {
        if (code < 0 || code >= VALUES.length) {
            return UILIB_RAW;
        }
        return VALUES[code];
    }
}
