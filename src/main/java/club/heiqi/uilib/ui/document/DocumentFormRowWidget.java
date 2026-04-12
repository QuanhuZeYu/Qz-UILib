package club.heiqi.uilib.ui.document;

import java.util.Objects;

import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 文档表单行容器。
 */
public class DocumentFormRowWidget extends DivWidget {

    public DocumentFormRowWidget(UiDocumentTheme theme, String labelText, Widget field) {
        UiDocumentTheme resolvedTheme = Objects.requireNonNull(theme, "theme");
        setDirection(Direction.ROW)
                .setAlignItems(AlignItems.CENTER)
                .setJustifyContent(JustifyContent.START)
                .setWrap(Wrap.WRAP)
                .setOverflowX(Overflow.VISIBLE)
                .setOverflowY(Overflow.VISIBLE)
                .setGap(resolvedTheme.getFormRowGap());
        setLayoutSpec(new UiLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()));

        DocumentTextWidget label = new DocumentTextWidget(resolvedTheme, DocumentTextWidget.Role.EMPHASIS, labelText, 2);
        label.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.px(resolvedTheme.getFormLabelWidth()))
                .setMinWidth(resolvedTheme.getFormLabelWidth())
                .setMaxWidth(resolvedTheme.getFormLabelWidth()));
        addChild(label);

        if (field != null) {
            UiLayoutSpec layoutSpec = field.getLayoutSpec();
            if (layoutSpec == null) {
                layoutSpec = new UiLayoutSpec();
            }
            layoutSpec.setGrow(1.0F).setShrink(1.0F);
            field.setLayoutSpec(layoutSpec);
            addChild(field);
        }
    }
}
