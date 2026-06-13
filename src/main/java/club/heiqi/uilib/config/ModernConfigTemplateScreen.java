package club.heiqi.uilib.config;

import java.util.List;
import java.util.Objects;

import club.heiqi.config.ConfigException;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
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
 * <p>Batch 0 仅建立生命周期、状态栏、按钮和只读路径预览，不实现具体字段编辑模板。</p>
 */
public class ModernConfigTemplateScreen extends BaseScreen {

    private final GuiScreen parentScreen;
    private final Spec spec;
    private final HtmlLikeDocumentWidget documentWidget;
    private final List<ModernConfigPropertyBindings.ReadOnlyPathBinding> pathBindings;
    private final DocumentButtonControl saveButton;
    private final DocumentButtonControl restoreCurrentButton;
    private final DocumentButtonControl restoreDefaultsButton;
    private final DocumentButtonControl backButton;
    private final TextNode statusText;
    private final int visibleSectionCount;

    /**
     * 创建现代配置模板页。
     *
     * @param parentScreen 父界面
     * @param spec 模板规格
     */
    public ModernConfigTemplateScreen(GuiScreen parentScreen, Spec spec) {
        this.parentScreen = parentScreen;
        this.spec = Objects.requireNonNull(spec, "spec");

        UiDocument document = UiDocument.create();
        this.documentWidget = new HtmlLikeDocumentWidget(document, 960, 720,
                DefaultTextMeasureService.getInstance());
        this.documentWidget.setViewportRootScrollingEnabled(true);
        this.documentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));

        this.pathBindings = ModernConfigPropertyBindings.createReadOnlyPathBindings(spec.getConfig());
        this.saveButton = createActionButton(document, spec.getTextSet().saveButtonLabel,
                spec.getTheme().primaryButtonColor, spec.getTheme().primaryButtonActiveColor,
                spec.getTheme().disabledButtonColor);
        this.restoreCurrentButton = createActionButton(document, spec.getTextSet().restoreCurrentButtonLabel,
                spec.getTheme().secondaryButtonColor, spec.getTheme().secondaryButtonActiveColor,
                spec.getTheme().disabledButtonColor);
        this.restoreDefaultsButton = createActionButton(document, spec.getTextSet().restoreDefaultsButtonLabel,
                spec.getTheme().warningButtonColor, spec.getTheme().warningButtonActiveColor,
                spec.getTheme().warningButtonDisabledColor).setEnabled(false);
        this.backButton = createActionButton(document, spec.getTextSet().backButtonLabel,
                spec.getTheme().neutralButtonColor, spec.getTheme().neutralButtonActiveColor,
                spec.getTheme().disabledButtonColor);

        configureActionButtons();
        ModernConfigDocumentBuilder.Result buildResult = new ModernConfigDocumentBuilder(spec, pathBindings,
                saveButton, restoreCurrentButton, restoreDefaultsButton, backButton).build(document);
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
        backButton.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                requestClose();
            }
        });
    }

    private void saveDraft() {
        int dirtyCount = countDirtyBindings();
        if (dirtyCount <= 0) {
            refreshStatusText(spec.getTextSet().idleNoChangesText);
            return;
        }
        try {
            spec.getSaveHandler().onSave(spec.getConfig());
            spec.getConfig().markClean();
        } catch (ConfigException exception) {
            refreshStatusText(formatSaveFailed(exception));
            return;
        } catch (RuntimeException exception) {
            refreshStatusText(formatSaveFailed(exception));
            return;
        }
        refreshStatusText(spec.getTextSet().formatSaveSucceeded(dirtyCount));
    }

    private void restoreCurrentValues() {
        if (spec.getConfig().getSource() == null) {
            refreshStatusText("当前现代配置没有绑定文件，暂无可恢复的配置项。");
            return;
        }
        try {
            spec.getConfig().reload();
        } catch (ConfigException exception) {
            refreshStatusText("恢复失败：" + resolveExceptionMessage(exception));
            return;
        }
        refreshStatusText(spec.getTextSet().restoredCurrentValuesText);
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
        return spec.getConfig().isDirty() ? 1 : 0;
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
        statusText.setText(spec.getTextSet().formatReadyState(visibleSectionCount, pathBindings.size()));
    }

    private DocumentButtonControl createActionButton(UiDocument document, String label, int normalColor,
            int activeColor, int disabledColor) {
        return new DocumentButtonControl(document, label)
                .setBackgroundColors(normalColor, activeColor, disabledColor)
                .setFocusBorderColor(spec.getTheme().focusBorderColor)
                .setTextColors(spec.getTheme().buttonTextColor, spec.getTheme().buttonDisabledTextColor);
    }

    private static String formatSaveFailed(Throwable exception) {
        return "保存失败：" + resolveExceptionMessage(exception);
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
}
