package club.heiqi.uilib.ui.text;

import java.util.List;

import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.layout.TextLayoutService;

/**
 * 默认文本测量服务实现。
 *
 * <p>该实现直接委托字体布局服务完成测量，避免布局阶段回拉完整渲染运行时。</p>
 */
public final class DefaultTextMeasureService implements TextMeasureService {

    private static final DefaultTextMeasureService INSTANCE = new DefaultTextMeasureService();

    private DefaultTextMeasureService() {}

    /**
     * 获取默认文本测量服务单例。
     *
     * @return 默认文本测量服务
     */
    public static DefaultTextMeasureService getInstance() {
        return INSTANCE;
    }

    @Override
    public int getEpoch() {
        return FontService.getInstance().getTextMeasureEpoch();
    }

    @Override
    public int getStringWidth(String text) {
        return getTextLayoutService().getStringWidth(text);
    }

    @Override
    public int getLineHeight() {
        return getTextLayoutService().getLineHeight();
    }

    @Override
    public String trimStringToWidth(String text, int targetWidth) {
        return getTextLayoutService().trimStringToWidth(text, targetWidth);
    }

    @Override
    public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
        return getTextLayoutService().listFormattedStringToWidth(text, wrapWidth);
    }

    /**
     * 获取布局期可用的文本布局服务。
     *
     * @return 文本布局服务
     */
    private TextLayoutService getTextLayoutService() {
        FontService fontService = FontService.getInstance();
        fontService.ensureLayoutRuntimeReady();
        return fontService.getTextLayoutService();
    }
}
