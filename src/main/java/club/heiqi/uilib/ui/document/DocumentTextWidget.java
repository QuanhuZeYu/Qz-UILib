package club.heiqi.uilib.ui.document;

import java.util.Objects;

import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.control.UiControlTheme;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * 文档语义文本控件。
 */
public class DocumentTextWidget extends LabelWidget {

    /**
     * 文档文本语义角色。
     */
    public enum Role {
        TITLE,
        BODY,
        EMPHASIS,
        SECONDARY
    }

    public DocumentTextWidget(UiDocumentTheme theme, Role role, String text, int maxLines) {
        this(theme, role, text, maxLines, DefaultTextMeasureService.getInstance());
    }

    public DocumentTextWidget(UiDocumentTheme theme, Role role, String text, int maxLines,
            TextMeasureService textMeasureService) {
        super(text, resolveStyle(Objects.requireNonNull(theme, "theme"), role), textMeasureService);
        setWrap(true);
        setMaxLines(maxLines);
    }

    private static UiControlTheme.LabelStyle resolveStyle(UiDocumentTheme theme, Role role) {
        if (role == Role.TITLE) {
            return theme.getTitleLabelStyle();
        }
        if (role == Role.EMPHASIS) {
            return theme.getEmphasisLabelStyle();
        }
        if (role == Role.SECONDARY) {
            return theme.getSecondaryLabelStyle();
        }
        return theme.getBodyLabelStyle();
    }
}
