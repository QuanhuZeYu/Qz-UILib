package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.screen.BaseScreen;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 基于现代 config 模块的配置模板页骨架。
 *
 * <p>当前实现支持基础类型、列表、普通 map 嵌套结构、动态 map 和预设模板。</p>
 */
public class ModernConfigTemplateScreen extends BaseScreen {

    private final GuiScreen parentScreen;
    private final Spec spec;
    private final UiDocument document;
    private final HtmlLikeDocumentWidget documentWidget;
    private final List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings;
    private final ModernConfigSearchIndex searchIndex;
    private final ModernConfigSearchFilter searchFilter;
    private final DocumentButtonControl saveButton;
    private final DocumentButtonControl restoreCurrentButton;
    private final DocumentButtonControl restoreDefaultsButton;
    private final DocumentButtonControl backButton;
    private final TextNode statusText;
    private final int visibleSectionCount;

    private static final long SEARCH_REFRESH_DEBOUNCE_MS = 150L;
    private long lastDraftChangeTime;
    private boolean searchRefreshScheduled;

    /**
     * 创建现代配置模板页。
     *
     * @param parentScreen 父界面
     * @param spec 模板规格
     */
    public ModernConfigTemplateScreen(GuiScreen parentScreen, Spec spec) {
        this.parentScreen = parentScreen;
        this.spec = Objects.requireNonNull(spec, "spec");

        this.document = UiDocument.create();
        this.documentWidget = new HtmlLikeDocumentWidget(document, 960, 720,
                DefaultTextMeasureService.getInstance());
        this.documentWidget.setViewportRootScrollingEnabled(true);
        this.documentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));

        this.bindings = ModernConfigPropertyBindings.createBindings(spec.getConfig(), spec.getFields(),
                new DraftChangeListener(this), documentWidget.getComponentRuntime());
        this.saveButton = createActionButton(document, spec.getTextSet().saveButtonLabel,
                spec.getTheme().primaryButtonColor, spec.getTheme().primaryButtonActiveColor,
                spec.getTheme().disabledButtonColor);
        this.restoreCurrentButton = createActionButton(document, spec.getTextSet().restoreCurrentButtonLabel,
                spec.getTheme().secondaryButtonColor, spec.getTheme().secondaryButtonActiveColor,
                spec.getTheme().disabledButtonColor);
        this.restoreDefaultsButton = createActionButton(document, spec.getTextSet().restoreDefaultsButtonLabel,
                spec.getTheme().warningButtonColor, spec.getTheme().warningButtonActiveColor,
                spec.getTheme().warningButtonDisabledColor).setEnabled(hasRestorableDefault());
        this.backButton = createActionButton(document, spec.getTextSet().backButtonLabel,
                spec.getTheme().neutralButtonColor, spec.getTheme().neutralButtonActiveColor,
                spec.getTheme().disabledButtonColor);

        configureActionButtons();
        this.searchIndex = new ModernConfigSearchIndex(new ScreenDirtyStateProvider(this),
                indexFieldsByPath(), spec.getConfig().asImmutable());
        this.searchFilter = new ModernConfigSearchFilter(document, searchIndex, new PathJumpConsumer(this),
                documentWidget.getComponentRuntime());
        ModernConfigDocumentBuilder.Result buildResult = new ModernConfigDocumentBuilder(spec, bindings,
                saveButton, restoreCurrentButton, restoreDefaultsButton, backButton, searchFilter).build(document);
        this.statusText = buildResult.getStatusText();
        this.visibleSectionCount = buildResult.getVisibleSectionCount();
        refreshStatusText(null);
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

    @Override
    public void onGuiClosed() {
        try {
            cleanupResources();
        } finally {
            super.onGuiClosed();
        }
    }

    private void cleanupResources() {
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindings) {
            if (binding != null) {
                binding.dispose();
            }
        }
        saveButton.setActionHandler(null);
        restoreCurrentButton.setActionHandler(null);
        restoreDefaultsButton.setActionHandler(null);
        backButton.setActionHandler(null);
        // 回收响应式运行时：dispose 搜索结果列表的 forEach reconcile effect 与各行作用域，
        // 否则 effect 泄漏在全局 ReactiveScheduler 中，被其它页面的帧循环 flush 持续重跑。
        documentWidget.close();
    }

    private void configureActionButtons() {
        saveButton.setActionHandler(new SaveActionHandler(this));
        restoreCurrentButton.setActionHandler(new RestoreCurrentActionHandler(this));
        restoreDefaultsButton.setActionHandler(new RestoreDefaultsActionHandler(this));
        backButton.setActionHandler(new BackActionHandler(this));
    }

    private static final class SaveActionHandler implements DocumentButtonActionHandler {
        private final ModernConfigTemplateScreen screen;

        SaveActionHandler(ModernConfigTemplateScreen screen) {
            this.screen = screen;
        }

        @Override
        public void onAction(DocumentButtonActionEvent event) {
            screen.saveDraft();
        }
    }

    private static final class RestoreCurrentActionHandler implements DocumentButtonActionHandler {
        private final ModernConfigTemplateScreen screen;

        RestoreCurrentActionHandler(ModernConfigTemplateScreen screen) {
            this.screen = screen;
        }

        @Override
        public void onAction(DocumentButtonActionEvent event) {
            screen.restoreCurrentValues();
        }
    }

    private static final class RestoreDefaultsActionHandler implements DocumentButtonActionHandler {
        private final ModernConfigTemplateScreen screen;

        RestoreDefaultsActionHandler(ModernConfigTemplateScreen screen) {
            this.screen = screen;
        }

        @Override
        public void onAction(DocumentButtonActionEvent event) {
            screen.restoreDefaultValues();
        }
    }

    private static final class BackActionHandler implements DocumentButtonActionHandler {
        private final ModernConfigTemplateScreen screen;

        BackActionHandler(ModernConfigTemplateScreen screen) {
            this.screen = screen;
        }

        @Override
        public void onAction(DocumentButtonActionEvent event) {
            screen.requestClose();
        }
    }

    private void saveDraft() {
        if (bindings.isEmpty()) {
            refreshStatusText("当前模板没有可保存的配置项。");
            return;
        }
        int dirtyCount = countDirtyBindings();
        if (dirtyCount <= 0) {
            refreshStatusText(spec.getTextSet().idleNoChangesText);
            return;
        }
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindings) {
            String validationError = binding.validateDraft();
            binding.setValidationError(validationError);
            if (validationError != null && !validationError.isEmpty()) {
                refreshStatusText(spec.getTextSet().formatValidationFailed(binding.getDisplayName(), validationError));
                return;
            }
        }
        ConfigNode previousSnapshot = spec.getConfig().asImmutable();
        boolean wasDirty = spec.getConfig().isDirty();
        try {
            for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindings) {
                if (binding.isDirty()) {
                    binding.applyDraft();
                }
            }
            spec.getSaveHandler().onSave(spec.getConfig());
            spec.getConfig().markClean();
        } catch (ConfigException exception) {
            restoreConfigSnapshot(previousSnapshot, wasDirty);
            restoreCurrentValuesFromConfig();
            refreshStatusText(formatSaveFailed(exception));
            return;
        } catch (RuntimeException exception) {
            restoreConfigSnapshot(previousSnapshot, wasDirty);
            restoreCurrentValuesFromConfig();
            refreshStatusText(formatSaveFailed(exception));
            return;
        }
        restoreCurrentValuesFromConfig();
        refreshStatusText(spec.getTextSet().formatSaveSucceeded(dirtyCount));
    }

    private void restoreCurrentValues() {
        if (spec.getConfig().getSource() != null) {
            try {
                spec.getConfig().reload();
            } catch (ConfigException exception) {
                refreshStatusText(formatRestoreFailed(exception));
                return;
            }
        }
        restoreCurrentValuesFromConfig();
        refreshStatusText(spec.getTextSet().restoredCurrentValuesText);
    }

    private void restoreDefaultValues() {
        boolean restored = false;
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindings) {
            if (binding.canRestoreDefaultValue()) {
                binding.restoreDefaultValue();
                restored = true;
            }
        }
        refreshStatusText(restored ? spec.getTextSet().restoredDefaultValuesText : "当前现代配置没有声明默认值。");
    }

    private boolean handleGlobalShortcuts(UiInputFrame frame) {
        if (frame == null) {
            return false;
        }
        for (UiKeyEvent keyEvent : frame.getKeyEvents()) {
            if (keyEvent == null || keyEvent.getAction() != UiKeyEvent.Action.PRESSED) {
                continue;
            }
            if (keyEvent.getKeyCode() == UiKeyCodes.KEY_ESCAPE) {
                requestClose();
                return true;
            }
            if (keyEvent.getKeyCode() == UiKeyCodes.KEY_S && keyEvent.isControlPressed()) {
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

    private int countDirtyBindings() {
        int dirtyCount = 0;
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindings) {
            dirtyCount += binding.getDirtyCount();
        }
        return dirtyCount;
    }

    private boolean hasRestorableDefault() {
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindings) {
            if (binding.canRestoreDefaultValue()) {
                return true;
            }
        }
        return false;
    }

    private void restoreCurrentValuesFromConfig() {
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindings) {
            binding.restoreCurrentValue();
            binding.setValidationError("");
        }
    }

    private void restoreConfigSnapshot(ConfigNode snapshot, boolean wasDirty) {
        spec.getConfig().clear();
        if (snapshot != null && snapshot.getType() == ConfigNode.NodeType.MAP) {
            Map<String, ConfigNode> rootMap = snapshot.asMap();
            if (rootMap != null) {
                for (Map.Entry<String, ConfigNode> entry : rootMap.entrySet()) {
                    spec.getConfig().set(entry.getKey(), convertSnapshotValue(entry.getValue()));
                }
            }
        }
        if (!wasDirty) {
            spec.getConfig().markClean();
        }
    }

    private Object convertSnapshotValue(ConfigNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.getType() == ConfigNode.NodeType.STRING) {
            return node.asString("");
        }
        if (node.getType() == ConfigNode.NodeType.BOOLEAN) {
            return Boolean.valueOf(node.asBoolean(false));
        }
        if (node.getType() == ConfigNode.NodeType.NUMBER) {
            String numberText = node.asString("").trim().toLowerCase();
            if (!numberText.contains(".") && !numberText.contains("e")) {
                try {
                    return Long.valueOf(node.asLong());
                } catch (ConfigException ignored) {
                }
            }
            return Double.valueOf(node.asDouble(0.0D));
        }
        if (node.getType() == ConfigNode.NodeType.LIST) {
            List<Object> values = new ArrayList<Object>();
            List<ConfigNode> children = node.asList();
            if (children != null) {
                for (ConfigNode child : children) {
                    values.add(convertSnapshotValue(child));
                }
            }
            return values;
        }
        if (node.getType() == ConfigNode.NodeType.MAP) {
            java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<String, Object>();
            Map<String, ConfigNode> children = node.asMap();
            if (children != null) {
                for (Map.Entry<String, ConfigNode> entry : children.entrySet()) {
                    values.put(entry.getKey(), convertSnapshotValue(entry.getValue()));
                }
            }
            return values;
        }
        return node.asString("");
    }

    private void refreshStatusText(String overrideMessage) {
        if (statusText == null) {
            return;
        }
        if (overrideMessage != null && !overrideMessage.isEmpty()) {
            statusText.setText(overrideMessage);
            return;
        }
        int dirtyCount = countDirtyBindings();
        if (dirtyCount > 0) {
            statusText.setText(spec.getTextSet().formatDirtyState(dirtyCount));
            return;
        }
        statusText.setText(spec.getTextSet().formatReadyState(visibleSectionCount, bindings.size()));
    }

    /**
     * 草稿变更统一入口：状态文本立即刷新，搜索索引与过滤结果延迟刷新（150ms 防抖）。
     */
    private void onDraftChangedInternal() {
        refreshStatusText(null);
        lastDraftChangeTime = System.currentTimeMillis();
        if (!searchRefreshScheduled) {
            searchRefreshScheduled = true;
            UiScreenManager.getInstance().enqueue(new Runnable() {
                @Override
                public void run() {
                    searchRefreshScheduled = false;
                    long elapsed = System.currentTimeMillis() - lastDraftChangeTime;
                    if (elapsed < SEARCH_REFRESH_DEBOUNCE_MS) {
                        searchRefreshScheduled = true;
                        UiScreenManager.getInstance().enqueue(this);
                        return;
                    }
                    refreshSearchState();
                }
            });
        }
    }

    /**
     * 刷新搜索索引脏标记与过滤组件结果。在草稿变更后调用，确保「只看已修改」反映最新状态。
     */
    private void refreshSearchState() {
        if (searchIndex != null) {
            searchIndex.refreshDirtyMarkers();
        }
        if (searchFilter != null) {
            searchFilter.refresh();
        }
    }

    /**
     * 跳转到指定配置路径对应的卡片，滚动到视口顶部。
     *
     * @param path 配置路径
     */
    private void onJumpToPath(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        ElementNode card = findCardByPath(document.getRootElement(), path);
        if (card == null) {
            return;
        }
        documentWidget.requestScrollIntoView(card);
    }

    /**
     * 递归查找携带 {@code data-modern-config-path} 属性且匹配目标路径的卡片元素。
     *
     * @param root 搜索根元素
     * @param path 目标配置路径
     * @return 匹配元素；未找到时返回 null
     */
    private static ElementNode findCardByPath(ElementNode root, String path) {
        if (root == null || path == null || path.isEmpty()) {
            return null;
        }
        if (path.equals(root.getAttribute("data-modern-config-path"))) {
            return root;
        }
        for (DocumentNode child : root.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findCardByPath((ElementNode) child, path);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * 收集搜索索引使用的脏状态，不强制展开嵌套分类的未渲染叶子绑定。
     *
     * @return path 到 dirty 状态的映射
     */
    private Map<String, Boolean> collectDirtyMarkers() {
        Map<String, Boolean> markers = new LinkedHashMap<String, Boolean>();
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindings) {
            if (binding instanceof ModernNestedCategoryBinding) {
                ((ModernNestedCategoryBinding) binding).collectDirtyMarkers(markers);
                continue;
            }
            markers.put(binding.getPath(), Boolean.valueOf(binding.isDirty()));
        }
        return markers;
    }

    /**
     * 按 path 索引当前模板的字段规格，供搜索索引解析 label/description/hint。
     *
     * @return path 到字段规格的映射
     */
    private Map<String, FieldSpec> indexFieldsByPath() {
        return ModernConfigPropertyBindings.indexFields(spec.getFields());
    }

    private DocumentButtonControl createActionButton(UiDocument document, String label, int normalColor,
            int activeColor, int disabledColor) {
        return new DocumentButtonControl(document, label)
                .setBackgroundColors(normalColor, activeColor, disabledColor)
                .setFocusBorderColor(spec.getTheme().focusBorderColor)
                .setTextColors(spec.getTheme().buttonTextColor, spec.getTheme().buttonDisabledTextColor);
    }

    private static String formatSaveFailed(Throwable exception) {
        StringBuilder sb = new StringBuilder(64);
        sb.append("保存失败：");
        sb.append(resolveExceptionMessage(exception));
        return sb.toString();
    }

    private static String formatRestoreFailed(Throwable exception) {
        StringBuilder sb = new StringBuilder(64);
        sb.append("恢复失败：");
        sb.append(resolveExceptionMessage(exception));
        return sb.toString();
    }

    private static String resolveExceptionMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception == null ? "未知错误" : exception.getClass().getSimpleName();
        }
        return message;
    }

    /**
     * 现代配置模板规格。
     */
    public static final class Spec {

        private static final SaveHandler DEFAULT_SAVE_HANDLER = new SaveHandler() {
            @Override
            public void onSave(MutableConfig config) throws ConfigException {
                if (config != null && config.isDirty()) {
                    config.save();
                }
            }
        };

        private final String modId;
        private final String title;
        private final MutableConfig config;
        private String subtitle = "";
        private String description = "";
        private String configPath = "";
        private ForgeConfigTemplateScreen.Theme theme = ForgeConfigTemplateScreen.Theme.defaultTheme();
        private ForgeConfigTemplateScreen.TextSet textSet = ForgeConfigTemplateScreen.TextSet.defaultTextSet();
        private SaveHandler saveHandler = DEFAULT_SAVE_HANDLER;
        private final List<FieldSpec> fields = new ArrayList<FieldSpec>();

        /**
         * 创建现代配置模板规格。
         *
         * @param modId 模组 ID
         * @param title 页面标题
         * @param config 可变配置对象
         */
        public Spec(String modId, String title, MutableConfig config) {
            this.modId = Objects.requireNonNull(modId, "modId").trim();
            this.title = Objects.requireNonNull(title, "title").trim();
            this.config = Objects.requireNonNull(config, "config");
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
         * 设置展示用配置路径。
         *
         * @param configPath 配置路径
         * @return 当前规格
         */
        public Spec setConfigPath(String configPath) {
            this.configPath = configPath == null ? "" : configPath.trim();
            return this;
        }

        /**
         * 设置模板主题。
         *
         * @param theme 模板主题
         * @return 当前规格
         */
        public Spec setTheme(ForgeConfigTemplateScreen.Theme theme) {
            this.theme = theme == null ? ForgeConfigTemplateScreen.Theme.defaultTheme() : theme;
            return this;
        }

        /**
         * 设置模板文案集合。
         *
         * @param textSet 模板文案集合
         * @return 当前规格
         */
        public Spec setTextSet(ForgeConfigTemplateScreen.TextSet textSet) {
            this.textSet = textSet == null ? ForgeConfigTemplateScreen.TextSet.defaultTextSet() : textSet;
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
         * 追加现代配置字段规格。
         *
         * @param field 字段规格
         * @return 当前规格
         */
        public Spec addField(FieldSpec field) {
            if (field != null && !field.getPath().isEmpty()) {
                fields.add(field);
            }
            return this;
        }

        /**
         * 批量替换现代配置字段规格。
         *
         * @param fields 字段规格列表
         * @return 当前规格
         */
        public Spec setFields(List<FieldSpec> fields) {
            this.fields.clear();
            if (fields != null) {
                for (FieldSpec field : fields) {
                    addField(field);
                }
            }
            return this;
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

        public MutableConfig getConfig() {
            return config;
        }

        public ForgeConfigTemplateScreen.Theme getTheme() {
            return theme;
        }

        public ForgeConfigTemplateScreen.TextSet getTextSet() {
            return textSet;
        }

        public SaveHandler getSaveHandler() {
            return saveHandler;
        }

        public List<FieldSpec> getFields() {
            return Collections.unmodifiableList(fields);
        }
    }

    /**
     * 现代配置字段的轻量 UI 规格。
     */
    public static final class FieldSpec {

        private final String path;
        private String label = "";
        private String description = "";
        private String templateHint = "";
        private String placeholder = "";
        private Integer maxLength;
        private Number minValue;
        private Number maxValue;
        private Number step;
        private Object defaultValue;
        private boolean hasDefaultValue;
        private final List<String> validValues = new ArrayList<String>();

        /**
         * 创建字段规格。
         *
         * @param path 配置路径
         */
        public FieldSpec(String path) {
            this.path = path == null ? "" : path.trim();
        }

        /**
         * 设置展示标签。
         *
         * @param label 展示标签
         * @return 当前字段规格
         */
        public FieldSpec setLabel(String label) {
            this.label = label == null ? "" : label.trim();
            return this;
        }

        /**
         * 设置说明文本。
         *
         * @param description 说明文本
         * @return 当前字段规格
         */
        public FieldSpec setDescription(String description) {
            this.description = description == null ? "" : description.trim();
            return this;
        }

        /**
         * 设置模板提示。
         *
         * @param templateHint 模板提示
         * @return 当前字段规格
         */
        public FieldSpec setTemplateHint(String templateHint) {
            this.templateHint = templateHint == null ? "" : templateHint.trim();
            return this;
        }

        /**
         * 设置占位文本。
         *
         * @param placeholder 占位文本
         * @return 当前字段规格
         */
        public FieldSpec setPlaceholder(String placeholder) {
            this.placeholder = placeholder == null ? "" : placeholder;
            return this;
        }

        /**
         * 设置最大文本长度。
         *
         * @param maxLength 最大文本长度
         * @return 当前字段规格
         */
        public FieldSpec setMaxLength(Integer maxLength) {
            this.maxLength = maxLength == null ? null : Integer.valueOf(Math.max(1, maxLength.intValue()));
            return this;
        }

        /**
         * 设置数值范围。
         *
         * @param minValue 最小值
         * @param maxValue 最大值
         * @return 当前字段规格
         */
        public FieldSpec setRange(Number minValue, Number maxValue) {
            this.minValue = minValue;
            this.maxValue = maxValue;
            return this;
        }

        /**
         * 设置数值步进。
         *
         * @param step 步进值
         * @return 当前字段规格
         */
        public FieldSpec setStep(Number step) {
            this.step = step;
            return this;
        }

        /**
         * 设置默认值。
         *
         * @param defaultValue 默认值
         * @return 当前字段规格
         */
        public FieldSpec setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            this.hasDefaultValue = true;
            return this;
        }

        /**
         * 批量设置离散可选值。
         *
         * @param values 可选值数组
         * @return 当前字段规格
         */
        public FieldSpec setValidValues(String... values) {
            this.validValues.clear();
            if (values != null) {
                for (String value : values) {
                    addValidValue(value);
                }
            }
            return this;
        }

        /**
         * 追加离散可选值。
         *
         * @param value 可选值
         * @return 当前字段规格
         */
        public FieldSpec addValidValue(String value) {
            String normalized = value == null ? "" : value.trim();
            if (!normalized.isEmpty() && !validValues.contains(normalized)) {
                validValues.add(normalized);
            }
            return this;
        }

        public String getPath() {
            return path;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }

        public String getTemplateHint() {
            return templateHint;
        }

        public String getPlaceholder() {
            return placeholder;
        }

        public Integer getMaxLength() {
            return maxLength;
        }

        public Number getMinValue() {
            return minValue;
        }

        public Number getMaxValue() {
            return maxValue;
        }

        public Number getStep() {
            return step;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }

        public boolean hasDefaultValue() {
            return hasDefaultValue;
        }

        public List<String> getValidValues() {
            return Collections.unmodifiableList(validValues);
        }
    }

    /**
     * 现代配置保存回调。
     */
    public interface SaveHandler {

        /**
         * 当页面确认保存时执行。
         *
         * @param config 当前可变配置对象
         * @throws ConfigException 保存失败
         */
        void onSave(MutableConfig config) throws ConfigException;
    }

    private static final class DraftChangeListener implements ModernConfigPropertyBindings.ChangeListener {
        private final ModernConfigTemplateScreen screen;

        DraftChangeListener(ModernConfigTemplateScreen screen) {
            this.screen = screen;
        }

        @Override
        public void onDraftChanged() {
            screen.onDraftChangedInternal();
        }
    }

    private static final class ScreenDirtyStateProvider implements ModernConfigSearchIndex.DirtyStateProvider {
        private final ModernConfigTemplateScreen screen;

        ScreenDirtyStateProvider(ModernConfigTemplateScreen screen) {
            this.screen = screen;
        }

        @Override
        public Map<String, Boolean> collectDirtyByPath() {
            return screen.collectDirtyMarkers();
        }
    }

    private static final class PathJumpConsumer implements Consumer<String> {
        private final ModernConfigTemplateScreen screen;

        PathJumpConsumer(ModernConfigTemplateScreen screen) {
            this.screen = screen;
        }

        @Override
        public void accept(String path) {
            screen.onJumpToPath(path);
        }
    }
}
