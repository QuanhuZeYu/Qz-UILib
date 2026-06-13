package club.heiqi.uilib.config;

import java.util.List;

import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 现代配置模板的 DOM 文档构建器。
 */
final class ModernConfigDocumentBuilder {

    private final ModernConfigTemplateScreen.Spec spec;
    private final List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings;
    private final DocumentButtonControl saveButton;
    private final DocumentButtonControl restoreCurrentButton;
    private final DocumentButtonControl restoreDefaultsButton;
    private final DocumentButtonControl backButton;
    private int visibleSectionCount;

    ModernConfigDocumentBuilder(ModernConfigTemplateScreen.Spec spec,
            List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings, DocumentButtonControl saveButton,
            DocumentButtonControl restoreCurrentButton, DocumentButtonControl restoreDefaultsButton,
            DocumentButtonControl backButton) {
        this.spec = spec;
        this.bindings = bindings;
        this.saveButton = saveButton;
        this.restoreCurrentButton = restoreCurrentButton;
        this.restoreDefaultsButton = restoreDefaultsButton;
        this.backButton = backButton;
    }

    /**
     * 构建现代配置模板 DOM 文档。
     *
     * @param document 目标文档
     * @return 构建结果
     */
    Result build(UiDocument document) {
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(22))
                .setBackgroundColor(spec.getTheme().rootBackgroundColor)
                .setTextColor(spec.getTheme().rootTextColor)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);

        ElementNode main = document.element("main");
        main.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(14))
                .setWidth(UiStyleLength.percent(1.0F));
        root.append(main);

        appendHero(document, main);
        TextNode status = appendStatusCard(document, main);
        appendToolbar(document, main);
        appendBasicFieldCards(document, main);
        if (bindings.isEmpty()) {
            appendEmptyState(document, main);
        }
        return new Result(status, visibleSectionCount);
    }

    private void appendHero(UiDocument document, ElementNode parent) {
        ElementNode hero = document.element("header");
        hero.style()
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(spec.getTheme().heroBackgroundColor)
                .setBorderColor(spec.getTheme().heroBorderColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(18))
                .setTextColor(spec.getTheme().heroTextColor);
        hero.appendText(spec.getTitle());

        if (!spec.getSubtitle().isEmpty()) {
            ElementNode subtitle = document.div();
            subtitle.style().setTextColor(spec.getTheme().subtitleTextColor).setMargin(UiStyleLength.px(4));
            subtitle.appendText(spec.getSubtitle());
            hero.append(subtitle);
        }

        if (!spec.getDescription().isEmpty()) {
            ElementNode description = document.div();
            description.style().setTextColor(spec.getTheme().descriptionTextColor).setMargin(UiStyleLength.px(6));
            description.appendText(spec.getDescription());
            hero.append(description);
        }

        ElementNode metadata = document.div();
        metadata.style().setMargin(UiStyleLength.px(8)).setTextColor(spec.getTheme().metadataTextColor);
        metadata.appendText(spec.getTextSet().modIdPrefix + spec.getModId());
        if (!spec.getConfigPath().isEmpty()) {
            metadata.appendText(spec.getTextSet().configPathPrefix + spec.getConfigPath());
        }
        metadata.appendText(spec.getTextSet().shortcutHintText);
        hero.append(metadata);
        parent.append(hero);
    }

    private TextNode appendStatusCard(UiDocument document, ElementNode parent) {
        ElementNode card = document.element("section");
        card.style()
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(spec.getTheme().statusCardBackgroundColor)
                .setBorderColor(spec.getTheme().statusCardBorderColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(16))
                .setTextColor(spec.getTheme().statusCardTextColor);
        card.appendText(spec.getTextSet().statusCardTitle);
        TextNode status = card.appendText("");
        parent.append(card);
        return status;
    }

    private void appendToolbar(UiDocument document, ElementNode parent) {
        ElementNode toolbar = document.div();
        toolbar.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(10))
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(spec.getTheme().toolbarBackgroundColor)
                .setBorderColor(spec.getTheme().toolbarBorderColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(16));
        appendToolbarButton(toolbar, saveButton);
        appendToolbarButton(toolbar, restoreCurrentButton);
        appendToolbarButton(toolbar, restoreDefaultsButton);
        appendToolbarButton(toolbar, backButton);
        parent.append(toolbar);
    }

    private void appendToolbarButton(ElementNode toolbar, DocumentButtonControl button) {
        button.getElement().style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(0));
        toolbar.append(button.getElement());
    }

    private void appendBasicFieldCards(UiDocument document, ElementNode parent) {
        if (bindings.isEmpty()) {
            return;
        }
        ElementNode card = document.element("section");
        card.style()
                .setPadding(UiStyleLength.px(16))
                .setBackgroundColor(spec.getTheme().categoryCardBackgroundColor)
                .setBorderColor(spec.getTheme().categoryCardBorderColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(18));

        ElementNode header = document.div();
        header.style().setMargin(UiStyleLength.px(4));
        header.appendText("现代配置项");
        ElementNode description = document.div();
        description.style().setMargin(UiStyleLength.px(6))
                .setTextColor(spec.getTheme().categoryDescriptionTextColor);
        description.appendText("当前批次支持文本、数值、开关、空值、离散选项、长文本、primitive list、稳定列对象列表和普通 map 嵌套结构。");
        header.append(description);
        card.append(header);

        ElementNode fields = document.div();
        fields.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(10))
                .setMargin(UiStyleLength.px(8));
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindings) {
            if (binding instanceof ModernNestedCategoryBinding) {
                fields.append(((ModernNestedCategoryBinding) binding).createSection(document, spec.getTheme()));
            } else {
                fields.append(binding.createCard(document, spec.getTheme()));
            }
        }
        card.append(fields);
        visibleSectionCount++;
        parent.append(card);
    }

    private void appendEmptyState(UiDocument document, ElementNode parent) {
        ElementNode empty = document.element("section");
        empty.style()
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(spec.getTheme().emptyStateBackgroundColor)
                .setBorderColor(spec.getTheme().emptyStateBorderColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(16))
                .setTextColor(spec.getTheme().emptyStateTextColor);
        empty.appendText(spec.getTextSet().emptyTemplateText);
        parent.append(empty);
    }

    /**
     * 文档构建结果。
     */
    static final class Result {

        private final TextNode statusText;
        private final int visibleSectionCount;

        private Result(TextNode statusText, int visibleSectionCount) {
            this.statusText = statusText;
            this.visibleSectionCount = visibleSectionCount;
        }

        TextNode getStatusText() {
            return statusText;
        }

        int getVisibleSectionCount() {
            return visibleSectionCount;
        }
    }
}
