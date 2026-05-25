package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.control.DocumentSegmentedSelectionEvent;
import club.heiqi.uilib.ui.control.DocumentSegmentedSelectionHandler;
import club.heiqi.uilib.ui.control.DocumentSegmentedSelectorControl;
import club.heiqi.uilib.ui.control.DocumentSliderChangeEvent;
import club.heiqi.uilib.ui.control.DocumentSliderChangeHandler;
import club.heiqi.uilib.ui.control.DocumentSliderControl;
import club.heiqi.uilib.ui.control.DocumentTextInputChangeEvent;
import club.heiqi.uilib.ui.control.DocumentTextInputChangeHandler;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.control.DocumentToggleChangeEvent;
import club.heiqi.uilib.ui.control.DocumentToggleChangeHandler;
import club.heiqi.uilib.ui.control.DocumentToggleSwitchControl;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import net.minecraftforge.common.config.Property;

/**
 * Forge 配置模板默认属性绑定的工厂与共享数值工具。
 */
final class ConfigTemplatePropertyBindings {

    static final double NUMERIC_EPSILON = 1.0E-9D;
    private static final AtomicBoolean DEFAULT_VALUE_PARSE_FAILURE_LOGGED = new AtomicBoolean(false);

    private ConfigTemplatePropertyBindings() {
    }

    /**
     * 按属性类型创建配置模板内置编辑器绑定。
     *
     * @param owner 宿主配置页面
     * @param spec 配置模板规格
     * @param document 目标文档
     * @param categorySpec 分类规格
     * @param property Forge 配置属性
     * @return 匹配的默认属性绑定
     */
    static ForgeConfigTemplateScreen.PropertyBinding createDefault(ForgeConfigTemplateScreen owner,
            ForgeConfigTemplateScreen.Spec spec, UiDocument document,
            ForgeConfigTemplateScreen.CategorySpec categorySpec, Property property) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(spec, "spec");
        if (property.getType() == Property.Type.BOOLEAN && !property.isList()) {
            return new TogglePropertyBinding(owner, document, categorySpec, property);
        }
        if (ForgeConfigTemplatePropertyDrafts.shouldUseDiscreteValidValuesEditor(property)) {
            return new ChoicePropertyBinding(owner, document, categorySpec, property);
        }
        if (!property.isList() && (property.getType() == Property.Type.INTEGER
                || property.getType() == Property.Type.DOUBLE)) {
            NumericControlOptions numericOptions = spec.getNumericControlOptions(categorySpec.getCategoryName(),
                    property.getName());
            boolean authorDeclared = numericOptions != null;
            if (!authorDeclared) {
                numericOptions = property.getType() == Property.Type.DOUBLE
                        ? NumericControlOptions.sliderWithInput()
                        : NumericControlOptions.sliderWithLabel();
            }
            return new NumericPropertyBinding(owner, document, categorySpec, property, numericOptions, authorDeclared);
        }
        return new TextPropertyBinding(owner, document, categorySpec, property);
    }

    /**
     * 创建字体排序配置项的专用绑定。
     *
     * @param owner 宿主配置页面
     * @param document 目标文档
     * @param categorySpec 分类规格
     * @param property Forge 配置属性
     * @return 字体排序属性绑定
     */
    static ForgeConfigTemplateScreen.PropertyBinding createFontSort(ForgeConfigTemplateScreen owner,
            UiDocument document, ForgeConfigTemplateScreen.CategorySpec categorySpec, Property property) {
        Objects.requireNonNull(owner, "owner");
        return new FontSortPropertyBinding(owner, document, categorySpec, property);
    }

    static double parsePropertyMinValue(Property property, boolean integerType) {
        if (property == null) {
            return integerType ? Integer.MIN_VALUE : -Double.MAX_VALUE;
        }
        String raw = property.getMinValue();
        if (raw == null || raw.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }
        try {
            return integerType ? Integer.parseInt(raw.trim()) : Double.parseDouble(raw.trim());
        } catch (NumberFormatException exception) {
            return Double.NEGATIVE_INFINITY;
        }
    }

    static double parsePropertyMaxValue(Property property, boolean integerType) {
        if (property == null) {
            return integerType ? Integer.MAX_VALUE : Double.MAX_VALUE;
        }
        String raw = property.getMaxValue();
        if (raw == null || raw.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        try {
            return integerType ? Integer.parseInt(raw.trim()) : Double.parseDouble(raw.trim());
        } catch (NumberFormatException exception) {
            return Double.POSITIVE_INFINITY;
        }
    }

    static double readPropertyAsDouble(Property property, boolean integerType) {
        if (property == null) {
            return 0.0D;
        }
        try {
            return integerType ? property.getInt() : property.getDouble();
        } catch (RuntimeException exception) {
            try {
                return Double.parseDouble(property.getString());
            } catch (NumberFormatException ignored) {
                return 0.0D;
            }
        }
    }

    static double parsePropertyDefaultValue(Property property, boolean integerType) {
        if (property == null) {
            return 0.0D;
        }
        try {
            return integerType ? Integer.parseInt(property.getDefault()) : Double.parseDouble(property.getDefault());
        } catch (RuntimeException exception) {
            if (DEFAULT_VALUE_PARSE_FAILURE_LOGGED.compareAndSet(false, true)) {
                MyMod.LOG.debug("UILib 配置模板解析 default 数值失败，已回退到当前值：propertyName={} default={}",
                        property.getName(), property.getDefault(), exception);
            }
            return readPropertyAsDouble(property, integerType);
        }
    }

    static double resolveSliderStep(double declaredStep, boolean integerType) {
        if (declaredStep > 0.0D) {
            return declaredStep;
        }
        return integerType ? 1.0D : 0.0D;
    }

    static String formatDoubleValue(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}

/**
 * 布尔配置项绑定。
 */
final class TogglePropertyBinding extends ForgeConfigTemplateScreen.PropertyBinding {

    private final DocumentToggleSwitchControl control;

    TogglePropertyBinding(ForgeConfigTemplateScreen owner, UiDocument document,
            ForgeConfigTemplateScreen.CategorySpec categorySpec, Property property) {
        owner.super(document, categorySpec, property);
        this.control = new DocumentToggleSwitchControl(document)
                .setTrackColors(0xFF475569, 0xFF22C55E, 0xFF334155)
                .setFocusBorderColor(0xFFBFDBFE)
                .setChangeHandler(new DocumentToggleChangeHandler() {
                    @Override
                    public void onToggleChanged(DocumentToggleChangeEvent event) {
                        getOwnerScreen().requestStatusRefresh();
                    }
                });
        this.control.getElement().setAttribute("data-config-control", "toggle");
        restoreCurrentValue();
        initializeCard(document, control.getElement());
    }

    DocumentToggleSwitchControl getControl() {
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

    @Override
    public String exportDraftValue() {
        return Boolean.toString(control.isToggled());
    }

    @Override
    public void applyRemoteDraftValue(String draftValue) {
        control.setToggled(Boolean.parseBoolean(draftValue));
    }
}

/**
 * 文本型配置项绑定。
 */
final class TextPropertyBinding extends ForgeConfigTemplateScreen.PropertyBinding {

    private final DocumentTextInputControl control;

    TextPropertyBinding(ForgeConfigTemplateScreen owner, UiDocument document,
            ForgeConfigTemplateScreen.CategorySpec categorySpec, Property property) {
        owner.super(document, categorySpec, property);
        this.control = new DocumentTextInputControl(document)
                .setPlaceholder(ForgeConfigTemplatePropertyDrafts.resolvePlaceholder(property))
                .setMaxLength(ForgeConfigTemplatePropertyDrafts.resolveMaxLength(property))
                .setChangeHandler(new DocumentTextInputChangeHandler() {
                    @Override
                    public void onTextChanged(DocumentTextInputChangeEvent event) {
                        getOwnerScreen().requestStatusRefresh();
                    }
                });
        this.control.getElement().setAttribute("data-config-control", "text");
        this.control.getElement().style().setWidth(UiStyleLength.percent(1.0F));
        restoreCurrentValue();
        initializeCard(document, control.getElement());
    }

    DocumentTextInputControl getControl() {
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

    @Override
    public String exportDraftValue() {
        return control.getText();
    }

    @Override
    public void applyRemoteDraftValue(String draftValue) {
        control.setText(draftValue == null ? "" : draftValue);
    }

    private String readCurrentDisplayValue() {
        if (getProperty().isList()) {
            return ForgeConfigTemplatePropertyDrafts.readFullListDisplayValue(getProperty());
        }
        return ForgeConfigTemplatePropertyDrafts.readCurrentDisplayValue(getProperty());
    }

    private String readDefaultDisplayValue() {
        if (getProperty().isList()) {
            return ForgeConfigTemplatePropertyDrafts.readFullDefaultListDisplayValue(getProperty());
        }
        return ForgeConfigTemplatePropertyDrafts.readDefaultDisplayValue(getProperty());
    }
}

/**
 * 字体排序配置项绑定。
 */
final class FontSortPropertyBinding extends ForgeConfigTemplateScreen.PropertyBinding {

    private final List<String> draftOrder = new ArrayList<String>();
    private final FontSortOrderControl orderControl;

    FontSortPropertyBinding(ForgeConfigTemplateScreen owner, UiDocument document,
            ForgeConfigTemplateScreen.CategorySpec categorySpec, Property property) {
        owner.super(document, categorySpec, property);

        ElementNode editor = document.div();
        editor.setAttribute("data-config-control", "font-sort");
        editor.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8));

        HtmlLikeDocumentWidget documentWidget = getOwnerScreen().getDocumentWidgetForBindings();
        final FontSortPropertyBinding binding = this;
        this.orderControl = new FontSortOrderControl(document, documentWidget,
                FontSortOrderControl.toItemList(property.getStringList()),
                new FontSortOrderControl.FontSortOrderChangeListener() {
                    @Override
                    public void onOrderChanged(List<String> orderedItems) {
                        binding.applyDraftOrder(orderedItems);
                        getOwnerScreen().requestStatusRefresh();
                    }
                });
        editor.append(orderControl.getElement());

        restoreCurrentValue();
        initializeCard(document, editor);
    }

    ElementNode getFontSortOrderElementForTesting() {
        return orderControl.getElement();
    }

    @Override
    protected String buildHelperText() {
        String inherited = super.buildHelperText();
        String suffix = "拖拽字体行或直接输入目标序号调整顺序。";
        return inherited.isEmpty() ? suffix : inherited + " " + suffix;
    }

    @Override
    public boolean isDirty() {
        return !Arrays.equals(getProperty().getStringList(), toDraftArray());
    }

    @Override
    public void restoreCurrentValue() {
        applyToControl(FontSortOrderControl.toItemList(getProperty().getStringList()));
    }

    @Override
    public void restoreDefaultValue() {
        applyToControl(FontSortOrderControl.toItemList(getProperty().getDefaults()));
    }

    @Override
    public String validateDraft() {
        return null;
    }

    @Override
    public void applyDraft() {
        getProperty().set(toDraftArray());
    }

    @Override
    public String exportDraftValue() {
        StringBuilder builder = new StringBuilder();
        for (String value : draftOrder) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.trim());
        }
        return builder.toString();
    }

    @Override
    public void applyRemoteDraftValue(String draftValue) {
        applyDraftOrder(java.util.Arrays.asList((draftValue == null ? "" : draftValue).split("[,，;；\\n\\r]+")));
        if (orderControl != null) {
            orderControl.setItems(new ArrayList<String>(draftOrder));
        }
    }

    private void applyToControl(List<String> updatedOrder) {
        applyDraftOrder(updatedOrder);
        if (orderControl != null) {
            orderControl.setItems(new ArrayList<String>(draftOrder));
        }
    }

    private void applyDraftOrder(List<String> updatedOrder) {
        draftOrder.clear();
        if (updatedOrder != null) {
            for (String item : updatedOrder) {
                String normalized = item == null ? "" : item.trim();
                if (!normalized.isEmpty() && !draftOrder.contains(normalized)) {
                    draftOrder.add(normalized);
                }
            }
        }
    }

    private String[] toDraftArray() {
        return draftOrder.toArray(new String[draftOrder.size()]);
    }
}

/**
 * 预定义选项属性绑定。
 */
final class ChoicePropertyBinding extends ForgeConfigTemplateScreen.PropertyBinding {

    private final DocumentSegmentedSelectorControl control;
    private final String[] options;

    ChoicePropertyBinding(ForgeConfigTemplateScreen owner, UiDocument document,
            ForgeConfigTemplateScreen.CategorySpec categorySpec, Property property) {
        owner.super(document, categorySpec, property);
        this.options = ForgeConfigTemplatePropertyDrafts.getValidValuesSnapshot(property);
        this.control = new DocumentSegmentedSelectorControl(document, options)
                .setBackgroundColors(getTheme().selectedOptionBackgroundColor,
                        getTheme().selectedOptionActiveBackgroundColor,
                        getTheme().normalOptionBackgroundColor,
                        getTheme().normalOptionActiveBackgroundColor,
                        getTheme().disabledOptionBackgroundColor)
                .setTextColors(getTheme().selectedOptionTextColor,
                        getTheme().normalOptionTextColor,
                        getTheme().disabledOptionTextColor)
                .setFocusBorderColor(getTheme().focusBorderColor)
                .setSelectionHandler(new DocumentSegmentedSelectionHandler() {
                    @Override
                    public void onSelectionChanged(DocumentSegmentedSelectionEvent event) {
                        getOwnerScreen().requestStatusRefresh();
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

    @Override
    public String exportDraftValue() {
        String selected = control.getSelectedOption();
        return selected == null ? "" : selected;
    }

    @Override
    public void applyRemoteDraftValue(String draftValue) {
        int targetIndex = 0;
        for (int index = 0; index < options.length; index++) {
            if (Objects.equals(options[index], draftValue)) {
                targetIndex = index;
                break;
            }
        }
        control.setSelectedIndex(targetIndex);
    }
}

/**
 * 数值（整数/小数）属性绑定。
 */
final class NumericPropertyBinding extends ForgeConfigTemplateScreen.PropertyBinding {

    private final NumericControlOptions options;
    private final boolean integerType;
    private final NumericControlMode resolvedMode;
    private final boolean sliderActive;
    private final boolean inlineInputActive;
    private final double sliderMin;
    private final double sliderMax;
    private final double sliderStep;
    private final DocumentSliderControl sliderControl;
    private final DocumentTextInputControl textControl;
    private final DocumentTextInputControl inlineInputControl;
    private final TextNode sliderValueLabel;
    private final String sliderValueLabelFormat;
    private boolean suppressInlineInputSync;
    private boolean suppressSliderSync;

    NumericPropertyBinding(ForgeConfigTemplateScreen owner, UiDocument document,
            ForgeConfigTemplateScreen.CategorySpec categorySpec, Property property,
            NumericControlOptions options, boolean authorDeclared) {
        owner.super(document, categorySpec, property);
        this.options = options;
        this.integerType = property.getType() == Property.Type.INTEGER;

        double minValue = ConfigTemplatePropertyBindings.parsePropertyMinValue(property, integerType);
        double maxValue = ConfigTemplatePropertyBindings.parsePropertyMaxValue(property, integerType);
        boolean hasFiniteRange = !Double.isInfinite(minValue) && !Double.isInfinite(maxValue) && maxValue > minValue;
        double range = hasFiniteRange ? (maxValue - minValue) : Double.POSITIVE_INFINITY;
        double maxSliderRange = options.getMaxSliderRange();
        boolean withinThreshold = Double.isInfinite(maxSliderRange) || range <= maxSliderRange;

        NumericControlMode requestedMode = options.getMode();
        boolean wantSlider = requestedMode == NumericControlMode.SLIDER
                || requestedMode == NumericControlMode.SLIDER_WITH_LABEL
                || requestedMode == NumericControlMode.SLIDER_WITH_INPUT;
        if (wantSlider && !hasFiniteRange) {
            if (authorDeclared) {
                MyMod.LOG.warn(
                        "数值属性 [{}/{}] 声明了滑块模式但缺少上下界，已降级为文本输入框。",
                        categorySpec.getCategoryName(), property.getName());
            }
            wantSlider = false;
        } else if (wantSlider && !withinThreshold) {
            if (authorDeclared) {
                MyMod.LOG.warn(
                        "数值属性 [{}/{}] 数值跨度 {} 超过阈值 {}，已降级为文本输入框。",
                        categorySpec.getCategoryName(), property.getName(), range, maxSliderRange);
            }
            wantSlider = false;
        }
        this.sliderActive = wantSlider;
        this.resolvedMode = wantSlider ? requestedMode : NumericControlMode.TEXT_INPUT;
        this.inlineInputActive = wantSlider && requestedMode == NumericControlMode.SLIDER_WITH_INPUT;
        this.sliderMin = wantSlider ? minValue : 0.0D;
        this.sliderMax = wantSlider ? maxValue : 0.0D;
        this.sliderStep = wantSlider ? ConfigTemplatePropertyBindings.resolveSliderStep(options.getSliderStep(),
                integerType) : 0.0D;
        this.sliderValueLabelFormat = options.getLabelFormat();

        if (sliderActive) {
            this.textControl = null;
            this.sliderControl = new DocumentSliderControl(document)
                    .setRange(sliderMin, sliderMax)
                    .setStep(sliderStep)
                    .setChangeHandler(new DocumentSliderChangeHandler() {
                        @Override
                        public void onSliderChanged(DocumentSliderChangeEvent event) {
                            if (suppressSliderSync) {
                                return;
                            }
                            updateSliderValueLabel();
                            syncInlineInputFromSlider();
                            getOwnerScreen().requestStatusRefresh();
                        }
                    });
            this.sliderControl.getElement().setAttribute("data-config-control", "numeric-slider");
            this.sliderControl.getElement().style().setWidth(UiStyleLength.percent(1.0F));

            ElementNode editor = document.div();
            editor.style()
                    .setDisplay(UiDisplay.FLEX)
                    .setFlexDirection(UiFlexDirection.ROW)
                    .setAlignItems(UiAlignItems.CENTER)
                    .setColumnGap(UiStyleLength.px(8));

            ElementNode sliderShell = document.div();
            sliderShell.style()
                    .setDisplay(UiDisplay.FLEX)
                    .setFlexDirection(UiFlexDirection.ROW)
                    .setAlignItems(UiAlignItems.CENTER)
                    .setWidth(UiStyleLength.percent(1.0F));
            sliderShell.append(sliderControl.getElement());
            editor.append(sliderShell);

            if (resolvedMode == NumericControlMode.SLIDER_WITH_LABEL) {
                ElementNode labelElement = document.div();
                labelElement.setAttribute("data-config-control", "numeric-slider-label");
                labelElement.style()
                        .setPadding(UiStyleLength.px(4))
                        .setBorderColor(0xFF334155)
                        .setBorderWidth(UiStyleLength.px(1))
                        .setBorderRadius(UiStyleLength.px(8))
                        .setBackgroundColor(0xFF0F172A)
                        .setTextColor(0xFFE2E8F0)
                        .setOverflowX(UiOverflow.HIDDEN)
                        .setOverflowY(UiOverflow.HIDDEN);
                this.sliderValueLabel = labelElement.appendText("");
                editor.append(labelElement);
            } else {
                this.sliderValueLabel = null;
            }

            if (inlineInputActive) {
                this.inlineInputControl = new DocumentTextInputControl(document)
                        .setMaxLength(ForgeConfigTemplatePropertyDrafts.resolveMaxLength(property))
                        .setChangeHandler(new DocumentTextInputChangeHandler() {
                            @Override
                            public void onTextChanged(DocumentTextInputChangeEvent event) {
                                if (suppressInlineInputSync) {
                                    return;
                                }
                                syncSliderFromInlineInput();
                                getOwnerScreen().requestStatusRefresh();
                            }
                        });
                this.inlineInputControl.getElement().setAttribute("data-config-control",
                        "numeric-slider-input");
                this.inlineInputControl.getElement().style()
                        .setWidth(UiStyleLength.px(80));
                editor.append(inlineInputControl.getElement());
            } else {
                this.inlineInputControl = null;
            }

            restoreCurrentValue();
            initializeCard(document, editor);
        } else {
            this.sliderControl = null;
            this.sliderValueLabel = null;
            this.inlineInputControl = null;
            this.textControl = new DocumentTextInputControl(document)
                    .setPlaceholder(ForgeConfigTemplatePropertyDrafts.resolvePlaceholder(property))
                    .setMaxLength(ForgeConfigTemplatePropertyDrafts.resolveMaxLength(property))
                    .setChangeHandler(new DocumentTextInputChangeHandler() {
                        @Override
                        public void onTextChanged(DocumentTextInputChangeEvent event) {
                            getOwnerScreen().requestStatusRefresh();
                        }
                    });
            this.textControl.getElement().setAttribute("data-config-control", "numeric-text");
            this.textControl.getElement().style().setWidth(UiStyleLength.percent(1.0F));
            restoreCurrentValue();
            initializeCard(document, textControl.getElement());
        }
    }

    @Override
    public boolean isDirty() {
        if (sliderActive) {
            if (inlineInputActive) {
                Double parsed = parseInlineDraft();
                if (parsed == null) {
                    return true;
                }
                double current = ConfigTemplatePropertyBindings.readPropertyAsDouble(getProperty(), integerType);
                return Math.abs(current - parsed.doubleValue()) > ConfigTemplatePropertyBindings.NUMERIC_EPSILON;
            }
            double current = ConfigTemplatePropertyBindings.readPropertyAsDouble(getProperty(), integerType);
            return Math.abs(current - sliderControl.getValue()) > ConfigTemplatePropertyBindings.NUMERIC_EPSILON;
        }
        return !Objects.equals(readCurrentDisplayValue(), textControl.getText());
    }

    @Override
    public void restoreCurrentValue() {
        if (sliderActive) {
            applySliderValueQuiet(ConfigTemplatePropertyBindings.readPropertyAsDouble(getProperty(), integerType));
        } else {
            textControl.setText(readCurrentDisplayValue());
        }
    }

    @Override
    public void restoreDefaultValue() {
        if (sliderActive) {
            applySliderValueQuiet(ConfigTemplatePropertyBindings.parsePropertyDefaultValue(getProperty(),
                    integerType));
        } else {
            textControl.setText(readDefaultDisplayValue());
        }
    }

    @Override
    public String validateDraft() {
        if (sliderActive) {
            if (inlineInputActive) {
                return ForgeConfigTemplatePropertyDrafts.validateDraft(getProperty(),
                        inlineInputControl.getText());
            }
            return null;
        }
        return ForgeConfigTemplatePropertyDrafts.validateDraft(getProperty(), textControl.getText());
    }

    @Override
    public void applyDraft() {
        if (sliderActive) {
            if (inlineInputActive) {
                ForgeConfigTemplatePropertyDrafts.applyDraft(getProperty(), inlineInputControl.getText());
                return;
            }
            if (integerType) {
                getProperty().set((int) Math.round(sliderControl.getValue()));
            } else {
                getProperty().set(sliderControl.getValue());
            }
            return;
        }
        ForgeConfigTemplatePropertyDrafts.applyDraft(getProperty(), textControl.getText());
    }

    @Override
    public String exportDraftValue() {
        if (sliderActive) {
            if (inlineInputActive && inlineInputControl != null) {
                return inlineInputControl.getText();
            }
            return formatSliderValueForInput(sliderControl.getValue());
        }
        return textControl.getText();
    }

    @Override
    public void applyRemoteDraftValue(String draftValue) {
        String resolved = draftValue == null ? "" : draftValue;
        if (sliderActive) {
            if (inlineInputActive && inlineInputControl != null) {
                inlineInputControl.setText(resolved);
                syncSliderFromInlineInput();
                return;
            }
            try {
                double parsed = integerType ? Integer.parseInt(resolved.trim()) : Double.parseDouble(resolved.trim());
                applySliderValueQuiet(parsed);
            } catch (NumberFormatException ignored) {
                applySliderValueQuiet(ConfigTemplatePropertyBindings.readPropertyAsDouble(getProperty(), integerType));
            }
            return;
        }
        textControl.setText(resolved);
    }

    private void applySliderValueQuiet(double value) {
        suppressSliderSync = true;
        try {
            sliderControl.setValue(value);
        } finally {
            suppressSliderSync = false;
        }
        updateSliderValueLabel();
        syncInlineInputFromSlider();
    }

    private void syncInlineInputFromSlider() {
        if (!inlineInputActive) {
            return;
        }
        suppressInlineInputSync = true;
        try {
            inlineInputControl.setText(formatSliderValueForInput(sliderControl.getValue()));
        } finally {
            suppressInlineInputSync = false;
        }
    }

    private void syncSliderFromInlineInput() {
        Double parsed = parseInlineDraft();
        if (parsed == null) {
            return;
        }
        double clamped = Math.max(sliderMin, Math.min(parsed.doubleValue(), sliderMax));
        suppressSliderSync = true;
        try {
            sliderControl.setValue(clamped);
        } finally {
            suppressSliderSync = false;
        }
        updateSliderValueLabel();
    }

    private Double parseInlineDraft() {
        if (!inlineInputActive || inlineInputControl == null) {
            return null;
        }
        String trimmed = inlineInputControl.getText().trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return integerType ? Double.valueOf(Integer.parseInt(trimmed))
                    : Double.valueOf(Double.parseDouble(trimmed));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatSliderValueForInput(double value) {
        if (integerType) {
            return Long.toString(Math.round(value));
        }
        return ConfigTemplatePropertyBindings.formatDoubleValue(value);
    }

    private void updateSliderValueLabel() {
        if (sliderValueLabel == null) {
            return;
        }
        double value = sliderControl.getValue();
        if (sliderValueLabelFormat != null && !sliderValueLabelFormat.isEmpty()) {
            try {
                sliderValueLabel.setText(String.format(sliderValueLabelFormat,
                        integerType ? Long.valueOf(Math.round(value)) : Double.valueOf(value)));
                return;
            } catch (RuntimeException exception) {
                MyMod.LOG.warn("数值标签格式化失败：{}", exception.getMessage());
            }
        }
        if (integerType) {
            sliderValueLabel.setText(Long.toString(Math.round(value)));
        } else {
            sliderValueLabel.setText(ConfigTemplatePropertyBindings.formatDoubleValue(value));
        }
    }

    private String readCurrentDisplayValue() {
        return ForgeConfigTemplatePropertyDrafts.readCurrentDisplayValue(getProperty());
    }

    private String readDefaultDisplayValue() {
        return ForgeConfigTemplatePropertyDrafts.readDefaultDisplayValue(getProperty());
    }
}
