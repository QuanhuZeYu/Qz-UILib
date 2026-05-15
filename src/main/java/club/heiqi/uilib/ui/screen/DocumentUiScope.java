package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * HTML-like 文档屏幕的运行时上下文。
 *
 * <p>该作用域只保留 HTML-like 页面控制器仍需要的文本测量与运行时适配器能力，
 * 不再暴露旧 retained widget 作者工厂或兼容主题。</p>
 */
public final class DocumentUiScope {

    private final TextMeasureService textMeasureService;
    private final UiRuntimeAdapters runtimeAdapters;
    private final TextContentMode defaultTextContentMode;

    /**
     * 创建 HTML-like 页面运行时上下文。
     *
     * @param textMeasureService 文本测量服务
     * @param runtimeAdapters 运行时适配器集合
     */
    public DocumentUiScope(TextMeasureService textMeasureService, UiRuntimeAdapters runtimeAdapters) {
        this(textMeasureService, runtimeAdapters, TextContentMode.UILIB_RAW);
    }

    /**
     * 创建 HTML-like 页面运行时上下文。
     *
     * @param textMeasureService 文本测量服务
     * @param runtimeAdapters 运行时适配器集合
     * @param defaultTextContentMode 新建文本节点默认解析模式
     */
    public DocumentUiScope(TextMeasureService textMeasureService, UiRuntimeAdapters runtimeAdapters,
            TextContentMode defaultTextContentMode) {
        this.textMeasureService = Objects.requireNonNull(textMeasureService, "textMeasureService");
        this.runtimeAdapters = Objects.requireNonNull(runtimeAdapters, "runtimeAdapters");
        this.defaultTextContentMode = defaultTextContentMode == null
                ? TextContentMode.UILIB_RAW : defaultTextContentMode;
    }

    /**
     * 获取当前作用域文本测量服务。
     *
     * @return 文本测量服务
     */
    public TextMeasureService getTextMeasureService() {
        return textMeasureService;
    }

    /**
     * 获取当前作用域运行时适配器集合。
     *
     * @return 运行时适配器集合
     */
    public UiRuntimeAdapters getRuntimeAdapters() {
        return runtimeAdapters;
    }

    /**
     * 获取当前作用域中新建文本节点使用的默认解析模式。
     *
     * @return 默认文本内容解析模式
     */
    public TextContentMode getDefaultTextContentMode() {
        return defaultTextContentMode;
    }
}
