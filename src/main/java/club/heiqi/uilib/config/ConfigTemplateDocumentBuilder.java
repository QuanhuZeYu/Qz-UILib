package club.heiqi.uilib.config;

import java.util.List;
import java.util.Map;

import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Property;

/**
 * Forge 配置模板的 DOM 文档构建器。
 *
 * <p>该类只负责将模板规格、分类和属性绑定渲染为 HTML-like DOM，
 * 页面生命周期、保存事务和状态刷新仍由 {@link ForgeConfigTemplateScreen} 协调。</p>
 */
final class ConfigTemplateDocumentBuilder {

    private final ForgeConfigTemplateScreen owner;
    private final ForgeConfigTemplateScreen.Spec spec;
    private final List<ForgeConfigTemplateScreen.PropertyBinding> bindings;
    private final Map<String, ForgeConfigTemplateScreen.PropertyBinding> bindingsByKey;
    private final List<String> missingCategories;
    private final DocumentButtonControl saveButton;
    private final DocumentButtonControl restoreCurrentButton;
    private final DocumentButtonControl restoreDefaultsButton;
    private final DocumentButtonControl backButton;
    private int visibleCategoryCount;

    ConfigTemplateDocumentBuilder(ForgeConfigTemplateScreen owner, ForgeConfigTemplateScreen.Spec spec,
            List<ForgeConfigTemplateScreen.PropertyBinding> bindings,
            Map<String, ForgeConfigTemplateScreen.PropertyBinding> bindingsByKey,
            List<String> missingCategories,
            DocumentButtonControl saveButton,
            DocumentButtonControl restoreCurrentButton,
            DocumentButtonControl restoreDefaultsButton,
            DocumentButtonControl backButton) {
        this.owner = owner;
        this.spec = spec;
        this.bindings = bindings;
        this.bindingsByKey = bindingsByKey;
        this.missingCategories = missingCategories;
        this.saveButton = saveButton;
        this.restoreCurrentButton = restoreCurrentButton;
        this.restoreDefaultsButton = restoreDefaultsButton;
        this.backButton = backButton;
    }

    /**
     * 构建配置模板 DOM 文档。
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
        appendCategoryCards(document, main);
        if (bindings.isEmpty() || !missingCategories.isEmpty()) {
            appendEmptyState(document, main);
        }
        return new Result(status, visibleCategoryCount);
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

    private void appendCategoryCards(UiDocument document, ElementNode parent) {
        missingCategories.clear();
        List<ForgeConfigTemplateScreen.CategorySpec> categories = spec.getResolvedCategories();
        for (ForgeConfigTemplateScreen.CategorySpec categorySpec : categories) {
            ConfigCategory category = owner.resolveCategory(categorySpec);
            if (category == null) {
                missingCategories.add(categorySpec.getCategoryName());
                continue;
            }
            if (!category.showInGui()) {
                continue;
            }

            ElementNode card = document.element("section");
            card.setAttribute("data-config-category", categorySpec.getCategoryName());
            card.style()
                    .setPadding(UiStyleLength.px(16))
                    .setBackgroundColor(spec.getTheme().categoryCardBackgroundColor)
                    .setBorderColor(spec.getTheme().categoryCardBorderColor)
                    .setBorderWidth(UiStyleLength.px(1))
                    .setBorderRadius(UiStyleLength.px(18));

            ElementNode header = document.div();
            header.style().setMargin(UiStyleLength.px(4));
            header.appendText(categorySpec.getDisplayTitle());

            String categoryDescription = mergeCategoryDescription(categorySpec, category);
            if (!categoryDescription.isEmpty()) {
                ElementNode description = document.div();
                description.style().setMargin(UiStyleLength.px(6))
                        .setTextColor(spec.getTheme().categoryDescriptionTextColor);
                description.appendText(categoryDescription);
                header.append(description);
            }

            card.append(header);
            ElementNode fields = document.div();
            fields.style()
                    .setDisplay(UiDisplay.FLEX)
                    .setFlexDirection(UiFlexDirection.COLUMN)
                    .setRowGap(UiStyleLength.px(10))
                    .setMargin(UiStyleLength.px(8));
            card.append(fields);

            int visiblePropertyCount = 0;
            for (Property property : category.getOrderedValues()) {
                if (property == null || !property.showInGui()) {
                    continue;
                }
                ForgeConfigTemplateScreen.PropertyBinding binding =
                        owner.createBinding(document, categorySpec, property);
                if (binding == null) {
                    continue;
                }
                bindings.add(binding);
                bindingsByKey.put(binding.getBindingKey(), binding);
                fields.append(binding.getCardElement());
                visiblePropertyCount++;
            }

            if (visiblePropertyCount <= 0) {
                ElementNode empty = document.div();
                empty.style().setMargin(UiStyleLength.px(8)).setTextColor(spec.getTheme().mutedTextColor);
                empty.appendText(spec.getTextSet().emptyCategoryText);
                fields.append(empty);
            }

            visibleCategoryCount++;
            parent.append(card);
        }
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
        String missingCategoriesMessage = missingCategories.isEmpty() ? ""
                : spec.getTextSet().formatMissingCategories(missingCategories);
        empty.appendText(ForgeConfigTemplateMessages.resolveEmptyStateMessage(spec.getTextSet().emptyTemplateText,
                missingCategoriesMessage));
        parent.append(empty);
    }

    private String mergeCategoryDescription(ForgeConfigTemplateScreen.CategorySpec categorySpec,
            ConfigCategory category) {
        String specDescription = ForgeConfigTemplateScreen.normalizeInlineText(categorySpec.getDescription());
        String categoryComment = category == null ? "" : ForgeConfigTemplateScreen.normalizeInlineText(
                category.getComment());
        if (specDescription.isEmpty()) {
            return categoryComment;
        }
        if (categoryComment.isEmpty() || specDescription.equals(categoryComment)) {
            return specDescription;
        }
        return specDescription + " " + categoryComment;
    }

    /**
     * 文档构建结果。
     */
    static final class Result {

        private final TextNode statusText;
        private final int visibleCategoryCount;

        private Result(TextNode statusText, int visibleCategoryCount) {
            this.statusText = statusText;
            this.visibleCategoryCount = visibleCategoryCount;
        }

        TextNode getStatusText() {
            return statusText;
        }

        int getVisibleCategoryCount() {
            return visibleCategoryCount;
        }
    }
}
