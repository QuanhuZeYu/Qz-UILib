package club.heiqi.uilib.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.dom.control.DocumentButtonControl;
import club.heiqi.uilib.ui.dom.control.DocumentSegmentedSelectionEvent;
import club.heiqi.uilib.ui.dom.control.DocumentSegmentedSelectionHandler;
import club.heiqi.uilib.ui.dom.control.DocumentSegmentedSelectorControl;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputChangeEvent;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputChangeHandler;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.dom.control.DocumentToggleChangeEvent;
import club.heiqi.uilib.ui.dom.control.DocumentToggleChangeHandler;
import club.heiqi.uilib.ui.dom.control.DocumentToggleSwitchControl;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.screen.BaseScreen;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * 使用 HTML-like 文档模板渲染的 Forge 配置页面。
 *
 * <p>该屏幕会直接读取 {@link Configuration} 中已经注册好的分类与属性，
 * 为布尔、数字、文本和列表属性生成统一的游戏内配置表单，供宿主模组复用。</p>
 */
public class ForgeConfigTemplateScreen extends BaseScreen {

    private final GuiScreen parentScreen;
    private final Spec spec;
    private final HtmlLikeDocumentWidget documentWidget;
    private final UiRuntimeAdapters runtimeAdapters;
    private final List<PropertyBinding> bindings = new ArrayList<PropertyBinding>();
    private final Map<String, PropertyBinding> bindingsByKey = new LinkedHashMap<String, PropertyBinding>();
    private final DocumentButtonControl saveButton;
    private final DocumentButtonControl restoreCurrentButton;
    private final DocumentButtonControl restoreDefaultsButton;
    private final DocumentButtonControl backButton;
    private final TextNode statusText;
    private final TextMeasureService textMeasureService;
    private final List<String> missingCategories = new ArrayList<String>();
    private int visibleCategoryCount;

    /**
     * 创建一个可复用的 Forge 配置模板页面。
     *
     * @param parentScreen 父界面
     * @param spec 模板规格
     */
    public ForgeConfigTemplateScreen(GuiScreen parentScreen, Spec spec) {
        this(parentScreen, spec, DefaultTextMeasureService.getInstance());
    }

    ForgeConfigTemplateScreen(GuiScreen parentScreen, Spec spec, TextMeasureService textMeasureService) {
        this(parentScreen, spec, textMeasureService, UiRuntimeAdapters.minecraftDefaults());
    }

    ForgeConfigTemplateScreen(GuiScreen parentScreen, Spec spec, TextMeasureService textMeasureService,
            UiRuntimeAdapters runtimeAdapters) {
        this.parentScreen = parentScreen;
        this.spec = Objects.requireNonNull(spec, "spec");
        this.runtimeAdapters = Objects.requireNonNull(runtimeAdapters, "runtimeAdapters");
        this.textMeasureService = Objects.requireNonNull(textMeasureService, "textMeasureService");

        UiDocument document = UiDocument.create();
        this.saveButton = createActionButton(document, spec.getTextSet().saveButtonLabel,
                spec.getTheme().primaryButtonColor, spec.getTheme().primaryButtonActiveColor,
                spec.getTheme().disabledButtonColor);
        this.restoreCurrentButton = createActionButton(document, spec.getTextSet().restoreCurrentButtonLabel,
                spec.getTheme().secondaryButtonColor, spec.getTheme().secondaryButtonActiveColor,
                spec.getTheme().disabledButtonColor);
        this.restoreDefaultsButton = createActionButton(document, spec.getTextSet().restoreDefaultsButtonLabel,
                spec.getTheme().warningButtonColor, spec.getTheme().warningButtonActiveColor,
                spec.getTheme().warningButtonDisabledColor);
        this.backButton = createActionButton(document, spec.getTextSet().backButtonLabel,
                spec.getTheme().neutralButtonColor, spec.getTheme().neutralButtonActiveColor,
                spec.getTheme().disabledButtonColor);

        configureActionButtons();
        this.statusText = buildDocument(document);
        this.documentWidget = new HtmlLikeDocumentWidget(document, 960, 720, this.textMeasureService);
        this.documentWidget.setViewportRootScrollingEnabled(true);
        this.documentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));
        refreshStatusText(null);
    }

    @Override
    protected UiRuntimeAdapters getRuntimeAdapters() {
        return runtimeAdapters;
    }

    @Override
    protected void buildUi(Widget root) {
        root.addChild(documentWidget);
    }

    @Override
    protected void onResize(int width, int height) {
        super.onResize(width, height);
        setRootPadding(0, 0, 0, 0);
        documentWidget.applyLayoutBounds(0, 0, Math.max(0, width), Math.max(0, height));
    }

    @Override
    public void handleInputFrame(UiInputFrame frame) {
        if (handleGlobalShortcuts(frame)) {
            return;
        }
        super.handleInputFrame(frame);
    }

    HtmlLikeDocumentWidget getDocumentWidgetForTesting() {
        return documentWidget;
    }

    DocumentToggleSwitchControl getToggleControl(String categoryName, String propertyName) {
        PropertyBinding binding = bindingsByKey.get(buildBindingKey(categoryName, propertyName));
        return binding instanceof TogglePropertyBinding ? ((TogglePropertyBinding) binding).getControl() : null;
    }

    DocumentTextInputControl getTextInputControl(String categoryName, String propertyName) {
        PropertyBinding binding = bindingsByKey.get(buildBindingKey(categoryName, propertyName));
        return binding instanceof TextPropertyBinding ? ((TextPropertyBinding) binding).getControl() : null;
    }

    DocumentButtonControl getFontSortOpenButtonForTesting(String categoryName, String propertyName) {
        PropertyBinding binding = bindingsByKey.get(buildBindingKey(categoryName, propertyName));
        return binding instanceof FontSortPropertyBinding ? ((FontSortPropertyBinding) binding).getOpenButton() : null;
    }

    String getStatusText() {
        return statusText == null ? "" : statusText.getText();
    }

    void saveDraft() {
        if (bindings.isEmpty()) {
            refreshStatusText("当前模板没有可保存的配置项。");
            return;
        }

        int dirtyCount = countDirtyBindings();
        if (dirtyCount <= 0) {
            refreshStatusText(spec.getTextSet().idleNoChangesText);
            return;
        }

        for (PropertyBinding binding : bindings) {
            String validationError = binding.validateDraft();
            if (validationError != null && !validationError.isEmpty()) {
                refreshStatusText(spec.getTextSet().formatValidationFailed(binding.getDisplayName(), validationError));
                return;
            }
        }

        try {
            ForgeConfigTemplatePropertyDrafts.runWithRollback(collectBoundProperties(), new Runnable() {
                @Override
                public void run() {
                    for (PropertyBinding binding : bindings) {
                        binding.applyDraft();
                    }
                }
            }, new Runnable() {
                @Override
                public void run() {
                    runSaveHandler();
                }
            });
        } catch (RuntimeException exception) {
            refreshStatusText(spec.getTextSet().formatSaveFailed(exception));
            for (PropertyBinding binding : bindings) {
                binding.restoreCurrentValue();
            }
            return;
        }
        for (PropertyBinding binding : bindings) {
            binding.restoreCurrentValue();
        }
        refreshStatusText(spec.getTextSet().formatSaveSucceeded(dirtyCount));
    }

    void restoreCurrentValues() {
        for (PropertyBinding binding : bindings) {
            binding.restoreCurrentValue();
        }
        refreshStatusText(spec.getTextSet().restoredCurrentValuesText);
    }

    void restoreDefaultValues() {
        for (PropertyBinding binding : bindings) {
            binding.restoreDefaultValue();
        }
        refreshStatusText(spec.getTextSet().restoredDefaultValuesText);
    }

    private void configureActionButtons() {
        saveButton.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                saveDraft();
            }
        });
        restoreCurrentButton.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                restoreCurrentValues();
            }
        });
        restoreDefaultsButton.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                restoreDefaultValues();
            }
        });
        backButton.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                requestClose();
            }
        });
    }

    private TextNode buildDocument(UiDocument document) {
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
        if (shouldRenderEmptyState()) {
            appendEmptyState(document, main);
        }
        return status;
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
        button.getElement().style().setFlexGrow(1.0F);
        toolbar.append(button.getElement());
    }

    private void appendCategoryCards(UiDocument document, ElementNode parent) {
        missingCategories.clear();
        List<CategorySpec> categories = spec.getResolvedCategories();
        for (CategorySpec categorySpec : categories) {
            ConfigCategory category = resolveCategory(categorySpec.getCategoryName());
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
                PropertyBinding binding = createBinding(document, categorySpec, property);
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

    private PropertyBinding createBinding(UiDocument document, CategorySpec categorySpec, Property property) {
        PropertyBinding customBinding = spec.createBinding(document, categorySpec, property, this);
        if (customBinding != null) {
            return customBinding;
        }
        if (property.getType() == Property.Type.BOOLEAN && !property.isList()) {
            return new TogglePropertyBinding(document, categorySpec, property);
        }
        if (ForgeConfigTemplatePropertyDrafts.shouldUseDiscreteValidValuesEditor(property)) {
            return new ChoicePropertyBinding(document, categorySpec, property);
        }
        return new TextPropertyBinding(document, categorySpec, property);
    }

    private ConfigCategory resolveCategory(String categoryName) {
        Configuration configuration = spec.getConfiguration();
        if (configuration == null || categoryName == null || categoryName.trim().isEmpty()) {
            return null;
        }
        String requestedName = categoryName.trim();
        if (configuration.hasCategory(requestedName)) {
            return configuration.getCategory(requestedName);
        }
        String lowerCaseName = requestedName.toLowerCase(Locale.ENGLISH);
        if (configuration.hasCategory(lowerCaseName)) {
            return configuration.getCategory(lowerCaseName);
        }
        return null;
    }

    private boolean handleGlobalShortcuts(UiInputFrame frame) {
        if (frame == null) {
            return false;
        }
        for (UiKeyEvent keyEvent : frame.getKeyEvents()) {
            if (keyEvent == null || keyEvent.getAction() != UiKeyEvent.Action.PRESSED) {
                continue;
            }
            if (keyEvent.getKeyCode() == Keyboard.KEY_ESCAPE) {
                requestClose();
                return true;
            }
            if (keyEvent.getKeyCode() == Keyboard.KEY_S && keyEvent.isControlPressed()) {
                saveDraft();
                return true;
            }
        }
        return false;
    }

    private void requestClose() {
        final GuiScreen targetScreen = parentScreen;
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                Minecraft minecraft = Minecraft.getMinecraft();
                if (minecraft != null) {
                    minecraft.displayGuiScreen(targetScreen);
                }
            }
        });
    }

    private void runSaveHandler() {
        SaveHandler saveHandler = spec.getSaveHandler();
        if (saveHandler != null) {
            saveHandler.onSave(spec.getConfiguration());
            return;
        }
        Configuration configuration = spec.getConfiguration();
        if (configuration != null && configuration.hasChanged()) {
            configuration.save();
        }
    }

    private int countDirtyBindings() {
        int dirtyCount = 0;
        for (PropertyBinding binding : bindings) {
            if (binding.isDirty()) {
                dirtyCount++;
            }
        }
        return dirtyCount;
    }

    private List<Property> collectBoundProperties() {
        List<Property> properties = new ArrayList<Property>(bindings.size());
        for (PropertyBinding binding : bindings) {
            properties.add(binding.getProperty());
        }
        return properties;
    }

    private boolean shouldRenderEmptyState() {
        return bindings.isEmpty() || !missingCategories.isEmpty();
    }

    private void refreshStatusText(String overrideMessage) {
        if (statusText == null) {
            return;
        }
        if (overrideMessage != null && !overrideMessage.isEmpty()) {
            statusText.setText(overrideMessage);
            return;
        }
        if (!missingCategories.isEmpty()) {
            statusText.setText(spec.getTextSet().formatMissingCategories(missingCategories));
            return;
        }
        int dirtyCount = countDirtyBindings();
        if (dirtyCount > 0) {
            statusText.setText(spec.getTextSet().formatDirtyState(dirtyCount));
            return;
        }
        statusText.setText(spec.getTextSet().formatReadyState(visibleCategoryCount, bindings.size()));
    }

    /**
     * 请求按当前绑定状态刷新状态区文案。
     */
    protected final void requestStatusRefresh() {
        refreshStatusText(null);
    }

    private DocumentButtonControl createActionButton(UiDocument document, String label, int normalColor, int activeColor,
            int disabledColor) {
        return new DocumentButtonControl(document, label)
                .setBackgroundColors(normalColor, activeColor, disabledColor)
                .setFocusBorderColor(spec.getTheme().focusBorderColor)
                .setTextColors(spec.getTheme().buttonTextColor, spec.getTheme().buttonDisabledTextColor);
    }

    private String mergeCategoryDescription(CategorySpec categorySpec, ConfigCategory category) {
        String specDescription = normalizeInlineText(categorySpec.getDescription());
        String categoryComment = category == null ? "" : normalizeInlineText(category.getComment());
        if (specDescription.isEmpty()) {
            return categoryComment;
        }
        if (categoryComment.isEmpty() || specDescription.equals(categoryComment)) {
            return specDescription;
        }
        return specDescription + " " + categoryComment;
    }

    private static String buildBindingKey(String categoryName, String propertyName) {
        return normalizeLookupKey(categoryName) + ":" + normalizeLookupKey(propertyName);
    }

    private static String normalizeLookupKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }

    private static String normalizeInlineText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\r', ' ').replace('\n', ' ').trim().replaceAll("\\s+", " ");
    }

    private static String formatDisplayLabel(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(trimmed.length() + 8);
        char previous = 0;
        for (int index = 0; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);
            if (current == '_' || current == '-' || current == '.') {
                if (builder.length() > 0 && builder.charAt(builder.length() - 1) != ' ') {
                    builder.append(' ');
                }
                previous = current;
                continue;
            }
            if (Character.isUpperCase(current) && builder.length() > 0 && previous != ' '
                    && Character.isLowerCase(previous)) {
                builder.append(' ');
            }
            builder.append(current);
            previous = current;
        }

        String[] words = builder.toString().trim().split("\\s+");
        StringBuilder result = new StringBuilder(builder.length() + 4);
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            if (word.length() == 1) {
                result.append(word.toUpperCase(Locale.ENGLISH));
                continue;
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            result.append(word.substring(1));
        }
        return result.toString();
    }

    /**
     * 配置模板规格。
     */
    public static final class Spec {

        private static final SaveHandler DEFAULT_SAVE_HANDLER = new SaveHandler() {
            @Override
            public void onSave(Configuration configuration) {
                if (configuration != null && configuration.hasChanged()) {
                    configuration.save();
                }
            }
        };

        private final String modId;
        private final String title;
        private final Configuration configuration;
        private String subtitle = "";
        private String description = "";
        private String configPath = "";
        private SaveHandler saveHandler = DEFAULT_SAVE_HANDLER;
        private Theme theme = Theme.defaultTheme();
        private TextSet textSet = TextSet.defaultTextSet();
        private final List<CategorySpec> categories = new ArrayList<CategorySpec>();
        private final List<PropertyEditorFactory> propertyEditorFactories = new ArrayList<PropertyEditorFactory>();

        /**
         * 创建模板规格。
         *
         * @param modId 模组 ID
         * @param title 页面标题
         * @param configuration Forge 配置对象
         */
        public Spec(String modId, String title, Configuration configuration) {
            this.modId = Objects.requireNonNull(modId, "modId").trim();
            this.title = Objects.requireNonNull(title, "title").trim();
            this.configuration = Objects.requireNonNull(configuration, "configuration");
            File configFile = configuration.getConfigFile();
            this.configPath = configFile == null ? "" : configFile.getAbsolutePath();
        }

        /**
         * 设置副标题。
         *
         * @param subtitle 副标题
         * @return 当前规格
         */
        public Spec setSubtitle(String subtitle) {
            this.subtitle = subtitle == null ? "" : subtitle.trim();
            return this;
        }

        /**
         * 设置描述文本。
         *
         * @param description 描述文本
         * @return 当前规格
         */
        public Spec setDescription(String description) {
            this.description = description == null ? "" : description.trim();
            return this;
        }

        /**
         * 设置配置文件路径展示文本。
         *
         * @param configPath 配置文件路径
         * @return 当前规格
         */
        public Spec setConfigPath(String configPath) {
            this.configPath = configPath == null ? "" : configPath.trim();
            return this;
        }

        /**
         * 设置保存回调。
         *
         * @param saveHandler 保存回调
         * @return 当前规格
         */
        public Spec setSaveHandler(SaveHandler saveHandler) {
            this.saveHandler = saveHandler == null ? DEFAULT_SAVE_HANDLER : saveHandler;
            return this;
        }

        /**
         * 设置模板主题。
         *
         * @param theme 模板主题
         * @return 当前规格
         */
        public Spec setTheme(Theme theme) {
            this.theme = theme == null ? Theme.defaultTheme() : theme;
            return this;
        }

        /**
         * 设置模板文案集合。
         *
         * @param textSet 模板文案集合
         * @return 当前规格
         */
        public Spec setTextSet(TextSet textSet) {
            this.textSet = textSet == null ? TextSet.defaultTextSet() : textSet;
            return this;
        }

        /**
         * 追加属性编辑器工厂。
         *
         * @param propertyEditorFactory 编辑器工厂
         * @return 当前规格
         */
        public Spec addPropertyEditorFactory(PropertyEditorFactory propertyEditorFactory) {
            if (propertyEditorFactory != null) {
                propertyEditorFactories.add(propertyEditorFactory);
            }
            return this;
        }

        /**
         * 追加一个配置分类描述。
         *
         * @param categorySpec 分类描述
         * @return 当前规格
         */
        public Spec addCategory(CategorySpec categorySpec) {
            if (categorySpec != null) {
                categories.add(categorySpec);
            }
            return this;
        }

        /**
         * 以默认展示标题追加一个分类。
         *
         * @param categoryName 分类名
         * @return 当前规格
         */
        public Spec addCategory(String categoryName) {
            return addCategory(new CategorySpec(categoryName));
        }

        public String getModId() {
            return modId;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public String getDescription() {
            return description;
        }

        public String getConfigPath() {
            return configPath;
        }

        public Configuration getConfiguration() {
            return configuration;
        }

        public SaveHandler getSaveHandler() {
            return saveHandler;
        }

        public Theme getTheme() {
            return theme;
        }

        public TextSet getTextSet() {
            return textSet;
        }

        PropertyBinding createBinding(UiDocument document, CategorySpec categorySpec, Property property,
                ForgeConfigTemplateScreen owner) {
            for (PropertyEditorFactory propertyEditorFactory : propertyEditorFactories) {
                PropertyBinding binding = propertyEditorFactory.create(document, categorySpec, property, owner);
                if (binding != null) {
                    return binding;
                }
            }
            return null;
        }

        List<CategorySpec> getResolvedCategories() {
            if (!categories.isEmpty()) {
                return Collections.unmodifiableList(categories);
            }

            List<String> categoryNames = new ArrayList<String>(configuration.getCategoryNames());
            Collections.sort(categoryNames);
            List<CategorySpec> resolvedCategories = new ArrayList<CategorySpec>(categoryNames.size());
            for (String categoryName : categoryNames) {
                resolvedCategories.add(new CategorySpec(categoryName));
            }
            return resolvedCategories;
        }
    }

    /**
     * 模板中的配置分类描述。
     */
    public static final class CategorySpec {

        private final String categoryName;
        private String title = "";
        private String description = "";

        /**
         * 创建分类描述。
         *
         * @param categoryName Forge 配置分类名
         */
        public CategorySpec(String categoryName) {
            this.categoryName = Objects.requireNonNull(categoryName, "categoryName").trim();
        }

        /**
         * 设置分类展示标题。
         *
         * @param title 展示标题
         * @return 当前分类描述
         */
        public CategorySpec setTitle(String title) {
            this.title = title == null ? "" : title.trim();
            return this;
        }

        /**
         * 设置分类描述文本。
         *
         * @param description 描述文本
         * @return 当前分类描述
         */
        public CategorySpec setDescription(String description) {
            this.description = description == null ? "" : description.trim();
            return this;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public String getDescription() {
            return description;
        }

        public String getDisplayTitle() {
            return title.isEmpty() ? formatDisplayLabel(categoryName) : title;
        }
    }

    /**
     * 保存动作回调。
     */
    public interface SaveHandler {

        /**
         * 当页面确认保存时执行。
         *
         * @param configuration 当前配置对象
         */
        void onSave(Configuration configuration);
    }

    /**
     * 属性编辑器工厂。
     */
    public interface PropertyEditorFactory {

        /**
         * 基于属性创建一个可选的模板绑定。
         *
         * @param document 当前文档
         * @param categorySpec 分类描述
         * @param property Forge 属性
         * @param owner 当前模板页面
         * @return 绑定实例；不处理时返回 null
         */
        PropertyBinding create(UiDocument document, CategorySpec categorySpec, Property property,
                ForgeConfigTemplateScreen owner);
    }

    /**
     * 模板文案集合。
     */
    public static final class TextSet {

        public final String saveButtonLabel;
        public final String restoreCurrentButtonLabel;
        public final String restoreDefaultsButtonLabel;
        public final String backButtonLabel;
        public final String statusCardTitle;
        public final String modIdPrefix;
        public final String configPathPrefix;
        public final String shortcutHintText;
        public final String idleNoChangesText;
        public final String restoredCurrentValuesText;
        public final String restoredDefaultValuesText;
        public final String emptyCategoryText;
        public final String emptyTemplateText;

        public TextSet(String saveButtonLabel, String restoreCurrentButtonLabel, String restoreDefaultsButtonLabel,
                String backButtonLabel, String statusCardTitle, String modIdPrefix, String configPathPrefix,
                String shortcutHintText, String idleNoChangesText, String restoredCurrentValuesText,
                String restoredDefaultValuesText, String emptyCategoryText, String emptyTemplateText) {
            this.saveButtonLabel = saveButtonLabel;
            this.restoreCurrentButtonLabel = restoreCurrentButtonLabel;
            this.restoreDefaultsButtonLabel = restoreDefaultsButtonLabel;
            this.backButtonLabel = backButtonLabel;
            this.statusCardTitle = statusCardTitle;
            this.modIdPrefix = modIdPrefix;
            this.configPathPrefix = configPathPrefix;
            this.shortcutHintText = shortcutHintText;
            this.idleNoChangesText = idleNoChangesText;
            this.restoredCurrentValuesText = restoredCurrentValuesText;
            this.restoredDefaultValuesText = restoredDefaultValuesText;
            this.emptyCategoryText = emptyCategoryText;
            this.emptyTemplateText = emptyTemplateText;
        }

        public static TextSet defaultTextSet() {
            return new TextSet(
                    "保存",
                    "恢复当前值",
                    "恢复默认值",
                    "返回",
                    "状态",
                    "Mod ID：",
                    "配置文件：",
                    "快捷键：Ctrl+S 保存，ESC 返回上一页。",
                    "没有需要保存的改动。按 Ctrl+S 可再次保存。",
                    "已恢复为当前配置文件中的值。未执行保存。",
                    "已恢复默认草稿。确认无误后请手动保存。",
                    "当前分类没有可展示的配置项。",
                    "当前模板没有找到可展示的 Forge 配置项。请检查分类名是否与 Configuration 中注册的一致。");
        }

        public String formatValidationFailed(String displayName, String validationError) {
            return "无法保存：" + displayName + " " + validationError;
        }

        public String formatSaveSucceeded(int dirtyCount) {
            return "已保存 " + dirtyCount + " 项改动。按 ESC 可返回上一页。";
        }

        public String formatSaveFailed(RuntimeException exception) {
            String message = exception == null ? "" : exception.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = exception == null ? "未知错误" : exception.getClass().getSimpleName();
            }
            return "保存失败：" + message;
        }

        public String formatDirtyState(int dirtyCount) {
            return "未保存变更 " + dirtyCount + " 项。按 Ctrl+S 保存，按 ESC 返回上一页。";
        }

        public String formatReadyState(int visibleCategoryCount, int propertyCount) {
            return "已载入 " + visibleCategoryCount + " 个分类 / " + propertyCount + " 个配置项。按 Ctrl+S 保存，按 ESC 返回上一页。";
        }

        public String formatMissingCategories(List<String> categoryNames) {
            return "以下分类未在 Configuration 中找到：" + String.valueOf(categoryNames) + "。";
        }
    }

    /**
     * 模板主题。
     */
    public static final class Theme {

        public final int rootBackgroundColor;
        public final int rootTextColor;
        public final int heroBackgroundColor;
        public final int heroBorderColor;
        public final int heroTextColor;
        public final int subtitleTextColor;
        public final int descriptionTextColor;
        public final int metadataTextColor;
        public final int statusCardBackgroundColor;
        public final int statusCardBorderColor;
        public final int statusCardTextColor;
        public final int toolbarBackgroundColor;
        public final int toolbarBorderColor;
        public final int categoryCardBackgroundColor;
        public final int categoryCardBorderColor;
        public final int categoryDescriptionTextColor;
        public final int emptyStateBackgroundColor;
        public final int emptyStateBorderColor;
        public final int emptyStateTextColor;
        public final int mutedTextColor;
        public final int primaryButtonColor;
        public final int primaryButtonActiveColor;
        public final int secondaryButtonColor;
        public final int secondaryButtonActiveColor;
        public final int neutralButtonColor;
        public final int neutralButtonActiveColor;
        public final int warningButtonColor;
        public final int warningButtonActiveColor;
        public final int warningButtonDisabledColor;
        public final int disabledButtonColor;
        public final int buttonTextColor;
        public final int buttonDisabledTextColor;
        public final int focusBorderColor;
        public final int selectedOptionBackgroundColor;
        public final int selectedOptionActiveBackgroundColor;
        public final int normalOptionBackgroundColor;
        public final int normalOptionActiveBackgroundColor;
        public final int disabledOptionBackgroundColor;
        public final int selectedOptionTextColor;
        public final int normalOptionTextColor;
        public final int disabledOptionTextColor;

        public Theme(int rootBackgroundColor, int rootTextColor, int heroBackgroundColor, int heroBorderColor,
                int heroTextColor, int subtitleTextColor, int descriptionTextColor, int metadataTextColor,
                int statusCardBackgroundColor, int statusCardBorderColor, int statusCardTextColor,
                int toolbarBackgroundColor, int toolbarBorderColor, int categoryCardBackgroundColor,
                int categoryCardBorderColor, int categoryDescriptionTextColor, int emptyStateBackgroundColor,
                int emptyStateBorderColor, int emptyStateTextColor, int mutedTextColor, int primaryButtonColor,
                int primaryButtonActiveColor, int secondaryButtonColor, int secondaryButtonActiveColor,
                int neutralButtonColor, int neutralButtonActiveColor, int warningButtonColor,
                int warningButtonActiveColor, int warningButtonDisabledColor, int disabledButtonColor,
                int buttonTextColor, int buttonDisabledTextColor, int focusBorderColor,
                int selectedOptionBackgroundColor, int selectedOptionActiveBackgroundColor,
                int normalOptionBackgroundColor, int normalOptionActiveBackgroundColor,
                int disabledOptionBackgroundColor, int selectedOptionTextColor, int normalOptionTextColor,
                int disabledOptionTextColor) {
            this.rootBackgroundColor = rootBackgroundColor;
            this.rootTextColor = rootTextColor;
            this.heroBackgroundColor = heroBackgroundColor;
            this.heroBorderColor = heroBorderColor;
            this.heroTextColor = heroTextColor;
            this.subtitleTextColor = subtitleTextColor;
            this.descriptionTextColor = descriptionTextColor;
            this.metadataTextColor = metadataTextColor;
            this.statusCardBackgroundColor = statusCardBackgroundColor;
            this.statusCardBorderColor = statusCardBorderColor;
            this.statusCardTextColor = statusCardTextColor;
            this.toolbarBackgroundColor = toolbarBackgroundColor;
            this.toolbarBorderColor = toolbarBorderColor;
            this.categoryCardBackgroundColor = categoryCardBackgroundColor;
            this.categoryCardBorderColor = categoryCardBorderColor;
            this.categoryDescriptionTextColor = categoryDescriptionTextColor;
            this.emptyStateBackgroundColor = emptyStateBackgroundColor;
            this.emptyStateBorderColor = emptyStateBorderColor;
            this.emptyStateTextColor = emptyStateTextColor;
            this.mutedTextColor = mutedTextColor;
            this.primaryButtonColor = primaryButtonColor;
            this.primaryButtonActiveColor = primaryButtonActiveColor;
            this.secondaryButtonColor = secondaryButtonColor;
            this.secondaryButtonActiveColor = secondaryButtonActiveColor;
            this.neutralButtonColor = neutralButtonColor;
            this.neutralButtonActiveColor = neutralButtonActiveColor;
            this.warningButtonColor = warningButtonColor;
            this.warningButtonActiveColor = warningButtonActiveColor;
            this.warningButtonDisabledColor = warningButtonDisabledColor;
            this.disabledButtonColor = disabledButtonColor;
            this.buttonTextColor = buttonTextColor;
            this.buttonDisabledTextColor = buttonDisabledTextColor;
            this.focusBorderColor = focusBorderColor;
            this.selectedOptionBackgroundColor = selectedOptionBackgroundColor;
            this.selectedOptionActiveBackgroundColor = selectedOptionActiveBackgroundColor;
            this.normalOptionBackgroundColor = normalOptionBackgroundColor;
            this.normalOptionActiveBackgroundColor = normalOptionActiveBackgroundColor;
            this.disabledOptionBackgroundColor = disabledOptionBackgroundColor;
            this.selectedOptionTextColor = selectedOptionTextColor;
            this.normalOptionTextColor = normalOptionTextColor;
            this.disabledOptionTextColor = disabledOptionTextColor;
        }

        public static Theme defaultTheme() {
            return new Theme(
                    0xF0080F1C,
                    0xFFE5EEFF,
                    0xFF0F172A,
                    0xFF60A5FA,
                    0xFFF8FAFC,
                    0xFFBFDBFE,
                    0xFFD7E4FF,
                    0xFF93C5FD,
                    0xFF111827,
                    0xFF8B5CF6,
                    0xFFEDE9FE,
                    0xCC111827,
                    0xFF334155,
                    0xFF101827,
                    0xFF38BDF8,
                    0xFFBFD0EE,
                    0xFF111827,
                    0xFF475569,
                    0xFFCBD5E1,
                    0xFF94A3B8,
                    0xFF2563EB,
                    0xFF1D4ED8,
                    0xFF475569,
                    0xFF334155,
                    0xFF374151,
                    0xFF1F2937,
                    0xFFEA580C,
                    0xFFC2410C,
                    0xFF7C2D12,
                    0xFF334155,
                    0xFFFFFFFF,
                    0xFFCBD5E1,
                    0xFFBFDBFE,
                    0xFF2563EB,
                    0xFF1D4ED8,
                    0xFF334155,
                    0xFF1E293B,
                    0xFF1E293B,
                    0xFFFFFFFF,
                    0xFFCBD5E1,
                    0xFF64748B);
        }
    }

    /**
     * 单个配置项编辑绑定。
     */
    public abstract class PropertyBinding {

        private final CategorySpec categorySpec;
        private final Property property;
        private final String displayName;
        private ElementNode cardElement;

        protected PropertyBinding(UiDocument document, CategorySpec categorySpec, Property property) {
            this.categorySpec = Objects.requireNonNull(categorySpec, "categorySpec");
            this.property = Objects.requireNonNull(property, "property");
            this.displayName = formatDisplayLabel(property.getName());
        }

        protected final void initializeCard(UiDocument document, ElementNode editorElement) {
            this.cardElement = createCardElement(document, editorElement);
        }

        private ElementNode createCardElement(UiDocument document, ElementNode editorElement) {
            ElementNode card = document.div();
            card.setAttribute("data-config-property", property.getName());
            card.setAttribute("data-config-category", categorySpec.getCategoryName());
            card.style()
                    .setPadding(UiStyleLength.px(14))
                    .setBackgroundColor(0xFF162132)
                    .setBorderColor(0xFF334155)
                    .setBorderWidth(UiStyleLength.px(1))
                    .setBorderRadius(UiStyleLength.px(14));

            ElementNode title = document.div();
            title.style().setTextColor(0xFFF8FAFC);
            title.appendText(displayName);
            card.append(title);

            ElementNode metadata = document.div();
            metadata.style().setMargin(UiStyleLength.px(6)).setTextColor(0xFF93C5FD);
            metadata.appendText(buildMetadataText());
            card.append(metadata);

            String helperText = buildHelperText();
            if (!helperText.isEmpty()) {
                ElementNode helper = document.div();
                helper.style().setMargin(UiStyleLength.px(6)).setTextColor(0xFFCBD5E1);
                helper.appendText(helperText);
                card.append(helper);
            }

            ElementNode editorShell = document.div();
            editorShell.style()
                    .setDisplay(UiDisplay.FLEX)
                    .setFlexDirection(UiFlexDirection.COLUMN)
                    .setRowGap(UiStyleLength.px(6))
                    .setMargin(UiStyleLength.px(8));
            editorShell.append(editorElement);
            card.append(editorShell);
            return card;
        }

        protected final Property getProperty() {
            return property;
        }

        protected final String getDisplayName() {
            return displayName;
        }

        protected final Spec getSpec() {
            return spec;
        }

        protected final Theme getTheme() {
            return spec.getTheme();
        }

        protected final TextSet getTextSet() {
            return spec.getTextSet();
        }

        protected final ForgeConfigTemplateScreen getOwnerScreen() {
            return ForgeConfigTemplateScreen.this;
        }

        final String getBindingKey() {
            return buildBindingKey(categorySpec.getCategoryName(), property.getName());
        }

        final ElementNode getCardElement() {
            return cardElement;
        }

        protected String buildMetadataText() {
            StringBuilder builder = new StringBuilder();
            builder.append("键：").append(property.getName());
            builder.append(" | 类型：").append(resolveTypeLabel(property));

            String defaultText = resolveDefaultText(property);
            if (!defaultText.isEmpty()) {
                builder.append(" | 默认：").append(defaultText);
            }

            String rangeText = resolveRangeText(property);
            if (!rangeText.isEmpty()) {
                builder.append(" | 范围：").append(rangeText);
            }

            if (property.requiresMcRestart()) {
                builder.append(" | 需重启 Minecraft");
            } else if (property.requiresWorldRestart()) {
                builder.append(" | 需重进世界");
            }
            return builder.toString();
        }

        protected String buildHelperText() {
            List<String> fragments = new ArrayList<String>();
            String comment = normalizeInlineText(property.comment);
            if (!comment.isEmpty()) {
                fragments.add(comment);
            }
            String[] validValues = property.getValidValues();
            if (validValues != null && validValues.length > 0) {
                fragments.add("可选值：" + Arrays.toString(validValues));
            }
            if (property.isList()) {
                fragments.add("列表输入使用英文逗号分隔。空白项会被自动忽略。");
            }
            return joinFragments(fragments);
        }

        public abstract boolean isDirty();

        public abstract void restoreCurrentValue();

        public abstract void restoreDefaultValue();

        public abstract String validateDraft();

        public abstract void applyDraft();
    }

    /**
     * 布尔配置项绑定。
     */
    private final class TogglePropertyBinding extends PropertyBinding {

        private final DocumentToggleSwitchControl control;

        private TogglePropertyBinding(UiDocument document, CategorySpec categorySpec, Property property) {
            super(document, categorySpec, property);
            this.control = new DocumentToggleSwitchControl(document)
                    .setTrackColors(0xFF475569, 0xFF22C55E, 0xFF334155)
                    .setFocusBorderColor(0xFFBFDBFE)
                    .setChangeHandler(new DocumentToggleChangeHandler() {
                        @Override
                        public void onToggleChanged(DocumentToggleChangeEvent event) {
                            refreshStatusText(null);
                        }
                    });
            this.control.getElement().setAttribute("data-config-control", "toggle");
            restoreCurrentValue();
            initializeCard(document, control.getElement());
        }

        private DocumentToggleSwitchControl getControl() {
            return control;
        }

        @Override
        public boolean isDirty() {
            return control.isToggled() != getProperty().getBoolean();
        }

        @Override
        public void restoreCurrentValue() {
            control.setToggled(getProperty().getBoolean());
        }

        @Override
        public void restoreDefaultValue() {
            control.setToggled(Boolean.parseBoolean(getProperty().getDefault()));
        }

        @Override
        public String validateDraft() {
            return null;
        }

        @Override
        public void applyDraft() {
            getProperty().set(control.isToggled());
        }
    }

    /**
     * 文本型配置项绑定。
     */
    private final class TextPropertyBinding extends PropertyBinding {

        private final DocumentTextInputControl control;

        private TextPropertyBinding(UiDocument document, CategorySpec categorySpec, Property property) {
            super(document, categorySpec, property);
            this.control = new DocumentTextInputControl(document)
                    .setPlaceholder(ForgeConfigTemplatePropertyDrafts.resolvePlaceholder(property))
                    .setMaxLength(ForgeConfigTemplatePropertyDrafts.resolveMaxLength(property))
                    .setChangeHandler(new DocumentTextInputChangeHandler() {
                        @Override
                        public void onTextChanged(DocumentTextInputChangeEvent event) {
                            refreshStatusText(null);
                        }
                    });
            this.control.getElement().setAttribute("data-config-control", "text");
            this.control.getElement().style().setWidth(UiStyleLength.percent(1.0F));
            restoreCurrentValue();
            initializeCard(document, control.getElement());
        }

        private DocumentTextInputControl getControl() {
            return control;
        }

        @Override
        public boolean isDirty() {
            return !Objects.equals(readCurrentDisplayValue(), control.getText());
        }

        @Override
        public void restoreCurrentValue() {
            control.setText(readCurrentDisplayValue());
        }

        @Override
        public void restoreDefaultValue() {
            control.setText(readDefaultDisplayValue());
        }

        @Override
        public String validateDraft() {
            return ForgeConfigTemplatePropertyDrafts.validateDraft(getProperty(), control.getText());
        }

        @Override
        public void applyDraft() {
            ForgeConfigTemplatePropertyDrafts.applyDraft(getProperty(), control.getText());
        }

        private String readCurrentDisplayValue() {
            return ForgeConfigTemplatePropertyDrafts.readCurrentDisplayValue(getProperty());
        }

        private String readDefaultDisplayValue() {
            return ForgeConfigTemplatePropertyDrafts.readDefaultDisplayValue(getProperty());
        }
    }

    /**
     * 字体排序配置项绑定。
     */
    final class FontSortPropertyBinding extends PropertyBinding {

        private final List<String> draftOrder = new ArrayList<String>();
        private final DocumentButtonControl openButton;
        private final TextNode summaryText;

        FontSortPropertyBinding(UiDocument document, CategorySpec categorySpec, Property property) {
            super(document, categorySpec, property);

            ElementNode editor = document.div();
            editor.style()
                    .setDisplay(UiDisplay.FLEX)
                    .setFlexDirection(UiFlexDirection.COLUMN)
                    .setRowGap(UiStyleLength.px(8));

            ElementNode summary = document.div();
            summary.style()
                    .setPadding(UiStyleLength.px(10))
                    .setBackgroundColor(0xFF0F172A)
                    .setBorderColor(0xFF334155)
                    .setBorderWidth(UiStyleLength.px(1))
                    .setBorderRadius(UiStyleLength.px(12))
                    .setTextColor(0xFFBAE6FD)
                    .setOverflowX(UiOverflow.HIDDEN)
                    .setOverflowY(UiOverflow.HIDDEN);
            this.summaryText = summary.appendText("");
            editor.append(summary);

            this.openButton = createActionButton(document, "打开字体排序", spec.getTheme().primaryButtonColor,
                    spec.getTheme().primaryButtonActiveColor, spec.getTheme().disabledButtonColor);
            this.openButton.getElement().setAttribute("data-config-control", "font-sort-open");
            this.openButton.getElement().style().setWidth(UiStyleLength.percent(1.0F));
            this.openButton.setActionHandler(new DocumentButtonActionHandler() {
                @Override
                public void onAction(DocumentButtonActionEvent event) {
                    openFontSortScreen();
                }
            });
            editor.append(openButton.getElement());

            restoreCurrentValue();
            initializeCard(document, editor);
        }

        DocumentButtonControl getOpenButton() {
            return openButton;
        }

        @Override
        protected String buildHelperText() {
            String inherited = super.buildHelperText();
            String suffix = "字体排序已改为二级页面编辑：拖拽字体行或直接输入目标序号调整顺序。";
            return inherited.isEmpty() ? suffix : inherited + " " + suffix;
        }

        @Override
        public boolean isDirty() {
            return !Arrays.equals(getProperty().getStringList(), toDraftArray());
        }

        @Override
        public void restoreCurrentValue() {
            setDraftOrder(FontSortOrderControl.toItemList(getProperty().getStringList()));
        }

        @Override
        public void restoreDefaultValue() {
            setDraftOrder(FontSortOrderControl.toItemList(getProperty().getDefaults()));
        }

        @Override
        public String validateDraft() {
            return null;
        }

        @Override
        public void applyDraft() {
            getProperty().set(toDraftArray());
        }

        private void openFontSortScreen() {
            final FontSortPropertyBinding binding = this;
            UiScreenManager.getInstance().enqueue(new Runnable() {
                @Override
                public void run() {
                    Minecraft minecraft = Minecraft.getMinecraft();
                    if (minecraft != null) {
                        minecraft.displayGuiScreen(new FontSortScreen(getOwnerScreen(), binding.getDraftOrderSnapshot(),
                                new FontSortScreen.FontSortDraftSink() {
                                    @Override
                                    public void onFontSortDraftChanged(List<String> orderedItems) {
                                        binding.setDraftOrder(orderedItems);
                                        requestStatusRefresh();
                                    }
                                }, getOwnerScreen().getTextMeasureService()));
                    }
                }
            });
        }

        private List<String> getDraftOrderSnapshot() {
            return new ArrayList<String>(draftOrder);
        }

        private void setDraftOrder(List<String> updatedOrder) {
            draftOrder.clear();
            if (updatedOrder != null) {
                for (String item : updatedOrder) {
                    String normalized = item == null ? "" : item.trim();
                    if (!normalized.isEmpty() && !draftOrder.contains(normalized)) {
                        draftOrder.add(normalized);
                    }
                }
            }
            updateSummaryText();
        }

        private void updateSummaryText() {
            summaryText.setText("当前字体数量：" + draftOrder.size() + "。优先级："
                    + FontSortOrderControl.summarizeItems(draftOrder, 5));
        }

        private String[] toDraftArray() {
            return draftOrder.toArray(new String[draftOrder.size()]);
        }
    }

    /**
     * 预定义选项属性绑定。
     */
    private final class ChoicePropertyBinding extends PropertyBinding {

        private final DocumentSegmentedSelectorControl control;
        private final String[] options;

        private ChoicePropertyBinding(UiDocument document, CategorySpec categorySpec, Property property) {
            super(document, categorySpec, property);
            this.options = ForgeConfigTemplatePropertyDrafts.getValidValuesSnapshot(property);
            this.control = new DocumentSegmentedSelectorControl(document, options)
                    .setBackgroundColors(spec.getTheme().selectedOptionBackgroundColor,
                            spec.getTheme().selectedOptionActiveBackgroundColor,
                            spec.getTheme().normalOptionBackgroundColor,
                            spec.getTheme().normalOptionActiveBackgroundColor,
                            spec.getTheme().disabledOptionBackgroundColor)
                    .setTextColors(spec.getTheme().selectedOptionTextColor,
                            spec.getTheme().normalOptionTextColor,
                            spec.getTheme().disabledOptionTextColor)
                    .setFocusBorderColor(spec.getTheme().focusBorderColor)
                    .setSelectionHandler(new DocumentSegmentedSelectionHandler() {
                        @Override
                        public void onSelectionChanged(DocumentSegmentedSelectionEvent event) {
                            refreshStatusText(null);
                        }
                    });
            this.control.getElement().setAttribute("data-config-control", "choice");
            restoreCurrentValue();
            initializeCard(document, control.getElement());
        }

        @Override
        public boolean isDirty() {
            return !Objects.equals(getProperty().getString(), control.getSelectedOption());
        }

        @Override
        public void restoreCurrentValue() {
            control.setSelectedIndex(ForgeConfigTemplatePropertyDrafts.resolveSelectedValidValueIndex(getProperty()));
        }

        @Override
        public void restoreDefaultValue() {
            int targetIndex = 0;
            String defaultValue = getProperty().getDefault();
            for (int index = 0; index < options.length; index++) {
                if (Objects.equals(options[index], defaultValue)) {
                    targetIndex = index;
                    break;
                }
            }
            control.setSelectedIndex(targetIndex);
        }

        @Override
        public String validateDraft() {
            return null;
        }

        @Override
        public void applyDraft() {
            getProperty().set(control.getSelectedOption());
        }
    }

    private static String resolveTypeLabel(Property property) {
        if (property == null) {
            return "未知";
        }
        String prefix = property.isList() ? "列表·" : "";
        if (property.getType() == Property.Type.BOOLEAN) {
            return prefix + "开关";
        }
        if (property.getType() == Property.Type.INTEGER) {
            return prefix + "整数";
        }
        if (property.getType() == Property.Type.DOUBLE) {
            return prefix + "小数";
        }
        if (property.getType() == Property.Type.COLOR) {
            return prefix + "颜色";
        }
        if (property.getType() == Property.Type.MOD_ID) {
            return prefix + "Mod ID";
        }
        return prefix + "文本";
    }

    private static String resolveDefaultText(Property property) {
        if (property == null) {
            return "";
        }
        return ForgeConfigTemplatePropertyDrafts.readDefaultDisplayValue(property);
    }

    private static String resolveRangeText(Property property) {
        if (property == null) {
            return "";
        }
        if (property.getType() != Property.Type.INTEGER && property.getType() != Property.Type.DOUBLE) {
            return "";
        }
        return property.getMinValue() + " ~ " + property.getMaxValue();
    }

    private static String joinFragments(List<String> fragments) {
        StringBuilder builder = new StringBuilder();
        for (String fragment : fragments) {
            if (fragment == null || fragment.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(fragment);
        }
        return builder.toString();
    }

    private TextMeasureService getTextMeasureService() {
        return textMeasureService;
    }
}
