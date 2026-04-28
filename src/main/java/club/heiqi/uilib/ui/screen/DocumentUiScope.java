package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.control.UiControlRuntimeAdapters;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * HTML-like 文档屏幕的运行时上下文。
 *
 * <p>该作用域只保留 HTML-like 页面控制器仍需要的主题、文本测量与运行时适配器能力，
 * 不再暴露旧 retained widget 作者工厂。</p>
 */
public final class DocumentUiScope {

    private final UiDocumentTheme documentTheme;
    private final TextMeasureService textMeasureService;
    private final UiControlRuntimeAdapters runtimeAdapters;

    public DocumentUiScope(UiDocumentTheme documentTheme, TextMeasureService textMeasureService,
            UiControlRuntimeAdapters runtimeAdapters) {
        this.documentTheme = Objects.requireNonNull(documentTheme, "documentTheme");
        this.textMeasureService = Objects.requireNonNull(textMeasureService, "textMeasureService");
        this.runtimeAdapters = Objects.requireNonNull(runtimeAdapters, "runtimeAdapters");
    }

    /**
     * 获取当前作用域主题。
     *
     * @return 主题
     */
    public UiDocumentTheme theme() {
        return documentTheme;
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
    public UiControlRuntimeAdapters getRuntimeAdapters() {
        return runtimeAdapters;
    }
}
