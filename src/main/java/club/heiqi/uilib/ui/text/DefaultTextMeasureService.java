package club.heiqi.uilib.ui.text;

import java.util.List;

import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;

/**
 * 默认文本测量服务实现。
 *
 * <p>纯转发门面，零测量算法：全部委托 {@link TextLayoutService} 完成，
 * 避免布局阶段回拉完整渲染运行时；接口自身不再承载比例缩放等近似算法。</p>
 */
public final class DefaultTextMeasureService implements TextMeasureService {

    private static final DefaultTextMeasureService UILIB_RAW_INSTANCE =
            new DefaultTextMeasureService(TextContentMode.UILIB_RAW);
    private static final DefaultTextMeasureService MINECRAFT_FORMATTED_INSTANCE =
            new DefaultTextMeasureService(TextContentMode.MINECRAFT_FORMATTED);

    private final TextContentMode defaultTextContentMode;

    private DefaultTextMeasureService(TextContentMode defaultTextContentMode) {
        this.defaultTextContentMode = defaultTextContentMode;
    }

    /**
     * 获取默认文本测量服务单例。
     *
     * @return 默认文本测量服务
     */
    public static DefaultTextMeasureService getInstance() {
        return UILIB_RAW_INSTANCE;
    }

    /**
     * 获取默认的 Minecraft 格式文本测量服务单例。
     *
     * @return Minecraft 格式文本测量服务
     */
    public static DefaultTextMeasureService getMinecraftInstance() {
        return MINECRAFT_FORMATTED_INSTANCE;
    }

    @Override
    public int getEpoch() {
        return composeEpoch(FontService.getInstance().getTextMeasureEpoch(),
                getTextLayoutService().currentWidthConvergeEpoch());
    }

    /**
     * 复合页面层文本测量纪元：高 16 位 = 字体换代代，低 16 位 = 宽度收敛代。
     *
     * <p>{@link TextMeasureService#getEpoch()} 的契约只要求「变化时返回新值」，消费方
     * （{@code SceneNode.lastMeasuredEpoch}、{@code ChatLineLayouter.cachedEpoch}、
     * {@code TextLayoutEngine.cachedEpoch}）一律做 {@code !=} 比较，故复合值只需可区分。</p>
     *
     * <p>{@code FontService.getTextMeasureEpoch()} 参与 generation 构建协议不变量
     * （{@code textMeasureEpoch == baseTextMeasureEpoch + 1}），不得改其返回值；复合只发生在
     * 页面层端口，字体层协议不受影响。</p>
     *
     * @param generationEpoch    字体换代代（远小于 32768）
     * @param widthConvergeEpoch 宽度收敛代（一次会话内不会绕回 65536）
     * @return 复合纪元
     */
    static int composeEpoch(int generationEpoch, int widthConvergeEpoch) {
        return (generationEpoch << 16) | (widthConvergeEpoch & 0xFFFF);
    }

    @Override
    public int getStringWidth(String text) {
        return getStringWidth(text, defaultTextContentMode);
    }

    @Override
    public int getStringWidth(String text, TextContentMode textContentMode) {
        return getTextLayoutService().getStringWidth(text, resolveTextContentMode(textContentMode));
    }

    @Override
    public int getStringWidth(String text, TextContentMode textContentMode, UiFontWeight fontWeight,
                              UiFontStyle fontStyle) {
        return getTextLayoutService().getStringWidth(text, resolveTextContentMode(textContentMode), fontWeight,
                fontStyle);
    }

    @Override
    public int getStringWidth(String text, TextMeasureStyle style) {
        TextMeasureStyle resolvedStyle = resolveTextMeasureStyle(style);
        return getTextLayoutService().getStringWidth(text, resolvedStyle);
    }

    @Override
    public int getLineHeight() {
        return getTextLayoutService().getLineHeight();
    }

    @Override
    public int getLineHeight(TextMeasureStyle style) {
        return getTextLayoutService().getLineHeight(resolveTextMeasureStyle(style));
    }

    @Override
    public int getLineHeight(String text, TextMeasureStyle style) {
        return getTextLayoutService().getLineHeight(text, resolveTextMeasureStyle(style));
    }

    @Override
    public List<TextLinkRegion> getLinkRegions(String line, TextMeasureStyle style) {
        return getTextLayoutService().getLinkRegions(line, resolveTextMeasureStyle(style));
    }

    @Override
    public int getAscent(int fontSizePx) {
        return getTextLayoutService().getAscent(fontSizePx);
    }

    @Override
    public int getDescent(int fontSizePx) {
        return getTextLayoutService().getDescent(fontSizePx);
    }

    @Override
    public int getLineGap(int fontSizePx) {
        return getTextLayoutService().getLineGap(fontSizePx);
    }

    @Override
    public String trimStringToWidth(String text, int targetWidth) {
        return trimStringToWidth(text, targetWidth, defaultTextContentMode);
    }

    @Override
    public String trimStringToWidth(String text, int targetWidth, TextContentMode textContentMode) {
        return getTextLayoutService().trimStringToWidth(text, targetWidth, resolveTextContentMode(textContentMode));
    }

    @Override
    public String trimStringToWidth(String text, int targetWidth, TextContentMode textContentMode,
                                    UiFontWeight fontWeight, UiFontStyle fontStyle) {
        return getTextLayoutService().trimStringToWidth(text, targetWidth, resolveTextContentMode(textContentMode),
                fontWeight, fontStyle);
    }

    @Override
    public String trimStringToWidth(String text, int targetWidth, TextMeasureStyle style) {
        return getTextLayoutService().trimStringToWidth(text, targetWidth, resolveTextMeasureStyle(style));
    }

    @Override
    public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
        return listFormattedStringToWidth(text, wrapWidth, defaultTextContentMode);
    }

    @Override
    public List<String> listFormattedStringToWidth(String text, int wrapWidth, TextContentMode textContentMode) {
        return getTextLayoutService().listFormattedStringToWidth(text, wrapWidth,
                resolveTextContentMode(textContentMode));
    }

    @Override
    public List<String> listFormattedStringToWidth(String text, int wrapWidth, TextMeasureStyle style) {
        return getTextLayoutService().listFormattedStringToWidth(text, wrapWidth,
                resolveTextMeasureStyle(style));
    }

    /**
     * 获取布局期可用的文本布局服务。
     *
     * <p>无锁化（阶段 2 并行前置）：{@code ensureLayoutRuntimeReady} 内部已是 DCL，
     * {@code getTextLayoutService} 返回 final 字段，外层 {@code synchronized} 冗余已移除，
     * 避免把"已就绪后的纯读测量"也串行化，消除并行瓶颈。</p>
     *
     * @return 文本布局服务
     */
    private TextLayoutService getTextLayoutService() {
        FontService fontService = FontService.getInstance();
        fontService.ensureLayoutRuntimeReady();
        return fontService.getTextLayoutService();
    }

    private TextContentMode resolveTextContentMode(TextContentMode textContentMode) {
        return textContentMode == null ? defaultTextContentMode : textContentMode;
    }

    private TextMeasureStyle resolveTextMeasureStyle(TextMeasureStyle style) {
        TextMeasureStyle resolvedStyle = style == null
                ? TextMeasureStyle.DEFAULT.withTextContentMode(defaultTextContentMode)
                : style;
        TextContentMode resolvedMode = resolveTextContentMode(resolvedStyle.getTextContentMode());
        return new TextMeasureStyle(resolvedStyle.getFontSizePx(), resolvedMode, resolvedStyle.getFontWeight(),
                resolvedStyle.getFontStyle());
    }
}
