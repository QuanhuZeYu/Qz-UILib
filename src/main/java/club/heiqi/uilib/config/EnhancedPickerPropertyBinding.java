package club.heiqi.uilib.config;

import java.util.Locale;
import java.util.Objects;

import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentColorPickerChangeEvent;
import club.heiqi.uilib.ui.control.DocumentColorPickerChangeHandler;
import club.heiqi.uilib.ui.control.DocumentColorPickerConfirmEvent;
import club.heiqi.uilib.ui.control.DocumentColorPickerConfirmHandler;
import club.heiqi.uilib.ui.control.DocumentColorPickerControl;
import club.heiqi.uilib.ui.control.DocumentTextInputChangeEvent;
import club.heiqi.uilib.ui.control.DocumentTextInputChangeHandler;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 增强选择器字段绑定：覆盖颜色 / 资源 / 声音三类字符串字段。
 *
 * <p>按 {@link ModernConfigTypeInference.Result#getPickerKind()} 选择内部控件：</p>
 * <ul>
 *   <li>{@code COLOR}：使用 {@link DocumentColorPickerControl}。颜色值为 ARGB int，写回 config 时
 *       序列化为 {@code #RRGGBB}（alpha 0xFF 时）或 {@code #AARRGGBB}（其他 alpha）字符串；</li>
 *   <li>{@code RESOURCE} / {@code SOUND}：5-B 未提供专用 autocomplete 选择器，暂以
 *       {@link DocumentTextInputControl} 兜底，5-D 收口时如已新增专用控件再切换；</li>
 *   <li>{@code null}：理论上不应出现（推断器只在命中 hint 时返回 ENHANCED_PICKER），
 *       出现时退回文本输入避免崩溃。</li>
 * </ul>
 *
 * <p>事件契约：</p>
 * <ul>
 *   <li>{@link DocumentColorPickerChangeHandler}：用户编辑颜色后触发，binding 把 ARGB 转 HEX 写回
 *       draft；{@link DocumentColorPickerConfirmHandler} 触发时若 {@link DocumentColorPickerConfirmEvent#isValid()}
 *       为 false，binding 保留旧 draft 值，<b>不</b>写回；</li>
 *   <li>{@link DocumentTextInputChangeHandler}：RESOURCE/SOUND 文本输入变化时触发，binding 把
 *       字符串写回 draft。</li>
 * </ul>
 *
 * <p>颜色选择器的 {@code setColor/setHex/setRgb} 是程序化设置但仍会触发 change 事件——为避免初始化与
 * restore 期间把初始值误判为脏，binding 在这两个阶段临时把 changeHandler 置 null，结束后再恢复。</p>
 */
final class EnhancedPickerPropertyBinding extends ModernConfigPropertyBindings.ConfigPropertyBinding {

    private final ModernConfigTypeInference.PickerKind pickerKind;

    /** COLOR 模式的 change handler，作为字段持有便于 restore 时临时摘除与恢复。 */
    private final DocumentColorPickerChangeHandler colorChangeHandler =
            new DocumentColorPickerChangeHandler() {
                @Override
                public void onColorChanged(DocumentColorPickerChangeEvent event) {
                    draftValue = formatArgbAsHex(event.getArgb());
                    lastErrorMessage = "";
                    notifyDraftChanged();
                }
            };
    /** COLOR 模式的 confirm handler。 */
    private final DocumentColorPickerConfirmHandler colorConfirmHandler =
            new DocumentColorPickerConfirmHandler() {
                @Override
                public void onColorConfirmed(DocumentColorPickerConfirmEvent event) {
                    if (!event.isValid()) {
                        lastErrorMessage = "颜色格式应为 #RRGGBB 或 #AARRGGBB";
                        return;
                    }
                    lastErrorMessage = "";
                    draftValue = formatArgbAsHex(event.getArgb());
                    notifyDraftChanged();
                }
            };
    /** RESOURCE/SOUND/null 模式的文本 change handler。 */
    private final DocumentTextInputChangeHandler textChangeHandler =
            new DocumentTextInputChangeHandler() {
                @Override
                public void onTextChanged(DocumentTextInputChangeEvent event) {
                    if (textControl == null) {
                        return;
                    }
                    draftValue = textControl.getText();
                    lastErrorMessage = "";
                    notifyDraftChanged();
                }
            };

    private DocumentColorPickerControl colorControl;
    private DocumentTextInputControl textControl;

    /** 初次渲染从 config 读取的字符串值，用作 isDirty 基线。 */
    private String initialValue;
    /** 当前 draft 字符串值。COLOR 为 HEX 文本，RESOURCE/SOUND/null 为原始字符串。 */
    private String draftValue;
    /** 最近一次确认（COLOR）或编辑（其他）时的错误文案；为空表示当前 draft 合法。 */
    private String lastErrorMessage = "";

    EnhancedPickerPropertyBinding(MutableConfig config, String path, ConfigNode node,
            ModernConfigTemplateScreen.FieldSpec fieldSpec, ModernConfigTypeInference.Result inference,
            ModernConfigPropertyBindings.ChangeListener changeListener) {
        super(config, path, node, fieldSpec, inference, changeListener);
        this.pickerKind = inference == null ? null : inference.getPickerKind();
    }

    @Override
    protected ElementNode createEditorElement(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
        initialValue = readCurrentStringValue();
        draftValue = initialValue;
        lastErrorMessage = "";
        if (pickerKind == ModernConfigTypeInference.PickerKind.COLOR) {
            return createColorEditor(document, theme);
        }
        return createTextEditor(document, theme);
    }

    private ElementNode createColorEditor(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
        colorControl = new DocumentColorPickerControl(document)
                .setPreviewBorderColor(theme.focusBorderColor);
        colorControl.getElement().setAttribute("data-modern-config-control", "color-picker");
        colorControl.getElement().style().setWidth(UiStyleLength.percent(1.0F));
        // 先把初始 HEX 推给控件解析、同步内部 ARGB；handler 尚未注册，fireChange 被丢弃，不会污染 draft。
        Integer initialArgb = parseHexQuiet(initialValue);
        if (initialArgb != null) {
            colorControl.setColor(initialArgb.intValue());
        } else {
            // 初始值非法（或为空）时退回默认颜色，并把 initialValue 重置为对应 HEX，避免误判为脏
            colorControl.setColor(DocumentColorPickerControl.DEFAULT_COLOR);
            initialValue = formatArgbAsHex(DocumentColorPickerControl.DEFAULT_COLOR);
            draftValue = initialValue;
        }
        // 初始同步完成后再注册 handler：用户后续编辑才会触发 draft 写回
        colorControl.setChangeHandler(colorChangeHandler);
        colorControl.setConfirmHandler(colorConfirmHandler);
        return colorControl.getElement();
    }

    private ElementNode createTextEditor(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
        textControl = new DocumentTextInputControl(document)
                .setPlaceholder(resolvePlaceholder())
                .setMaxLength(ModernConfigPropertyBindings.resolveMaxLength(getFieldSpec(),
                        ModernConfigPropertyBindings.DEFAULT_TEXT_MAX_LENGTH))
                .setNormalBackgroundColor(0xFF222233)
                .setNormalBorderColor(0xFF555577)
                .setFocusBorderColor(theme.focusBorderColor);
        textControl.getElement().setAttribute("data-modern-config-control",
                pickerKind == null ? "picker-fallback" : pickerKind.name().toLowerCase(Locale.ENGLISH));
        textControl.getElement().style().setWidth(UiStyleLength.percent(1.0F));
        // 程序化 setText 不触发 changeHandler
        textControl.setText(initialValue);
        textControl.setChangeHandler(textChangeHandler);
        return textControl.getElement();
    }

    @Override
    boolean isDirty() {
        return !Objects.equals(initialValue, draftValue);
    }

    @Override
    void restoreCurrentValue() {
        initialValue = readCurrentStringValue();
        draftValue = initialValue;
        lastErrorMessage = "";
        if (colorControl != null) {
            // setColor 会触发 changeHandler，先临时摘掉避免污染 draft
            colorControl.setChangeHandler(null);
            colorControl.setConfirmHandler(null);
            try {
                Integer argb = parseHexQuiet(initialValue);
                if (argb != null) {
                    colorControl.setColor(argb.intValue());
                } else {
                    colorControl.setColor(DocumentColorPickerControl.DEFAULT_COLOR);
                    initialValue = formatArgbAsHex(DocumentColorPickerControl.DEFAULT_COLOR);
                    draftValue = initialValue;
                }
            } finally {
                colorControl.setChangeHandler(colorChangeHandler);
                colorControl.setConfirmHandler(colorConfirmHandler);
            }
        } else if (textControl != null) {
            textControl.setText(initialValue);
        }
        notifyDraftChanged();
    }

    @Override
    void restoreDefaultValue() {
        Object defaultValue = getDefaultValue();
        String serialized = defaultValue == null ? "" : String.valueOf(defaultValue);
        initialValue = serialized;
        draftValue = serialized;
        lastErrorMessage = "";
        if (colorControl != null) {
            colorControl.setChangeHandler(null);
            colorControl.setConfirmHandler(null);
            try {
                Integer argb = parseHexQuiet(serialized);
                if (argb != null) {
                    colorControl.setColor(argb.intValue());
                } else {
                    colorControl.setColor(DocumentColorPickerControl.DEFAULT_COLOR);
                    initialValue = formatArgbAsHex(DocumentColorPickerControl.DEFAULT_COLOR);
                    draftValue = initialValue;
                }
            } finally {
                colorControl.setChangeHandler(colorChangeHandler);
                colorControl.setConfirmHandler(colorConfirmHandler);
            }
        } else if (textControl != null) {
            textControl.setText(serialized);
        }
        notifyDraftChanged();
    }

    @Override
    String validateDraft() {
        if (pickerKind == ModernConfigTypeInference.PickerKind.COLOR) {
            // 颜色字段最终写回必须可解析为 ARGB；非法时返回错误文案
            if (parseHexQuiet(draftValue) == null) {
                return lastErrorMessage == null || lastErrorMessage.isEmpty()
                        ? "颜色格式应为 #RRGGBB 或 #AARRGGBB" : lastErrorMessage;
            }
        }
        return null;
    }

    @Override
    void applyDraft() {
        getConfig().set(getPath(), draftValue);
    }

    /**
     * 返回当前 draft 字符串值（COLOR 为 HEX，其他为原始字符串）。仅供同包测试使用。
     *
     * @return draft 字符串
     */
    String getDraftValue() {
        return draftValue;
    }

    /**
     * 返回 binding 当前持有的颜色选择器控件；非 COLOR 模式或未渲染时为 null。
     *
     * @return 颜色选择器控件
     */
    DocumentColorPickerControl getColorControl() {
        return colorControl;
    }

    /**
     * 返回 binding 当前持有的文本输入控件；COLOR 模式或未渲染时为 null。
     *
     * @return 文本输入控件
     */
    DocumentTextInputControl getTextControl() {
        return textControl;
    }

    /**
     * 模拟用户改色：更新内部 ARGB 并触发 draft 写回。仅供测试使用。
     *
     * @param argb 新 ARGB 颜色
     */
    void simulateColorChange(int argb) {
        if (colorControl == null) {
            return;
        }
        colorControl.setColor(argb);
    }

    /**
     * 模拟用户改文本：更新文本并触发 draft 写回。仅供测试使用。
     *
     * @param text 新文本
     */
    void simulateTextChange(String text) {
        if (textControl == null) {
            return;
        }
        textControl.setText(text);
        // textControl.setText 不触发 changeHandler，需要主动同步 draftValue
        draftValue = textControl.getText();
        lastErrorMessage = "";
        notifyDraftChanged();
    }

    /**
     * 把 ARGB 颜色序列化为 HEX 文本。
     *
     * <p>alpha 为 0xFF 时返回紧凑的 {@code #RRGGBB}；否则返回完整 {@code #AARRGGBB}。
     * 与 {@link DocumentColorPickerControl#getHex()} 不同——后者始终返回 9 位形式。</p>
     *
     * @param argb ARGB 颜色
     * @return HEX 文本
     */
    static String formatArgbAsHex(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;
        if (alpha == 0xFF) {
            return String.format(Locale.ENGLISH, "#%06X", rgb);
        }
        return String.format(Locale.ENGLISH, "#%08X", argb);
    }

    /**
     * 解析 HEX 文本为 ARGB int；非法时返回 null。
     *
     * <p>支持 {@code #RRGGBB}（alpha 默认 0xFF）与 {@code #AARRGGBB} 两种形式，# 可选，
     * 不区分大小写。空串返回 null。</p>
     *
     * @param raw HEX 文本
     * @return ARGB int；非法返回 null
     */
    static Integer parseHexQuiet(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String withoutPrefix = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
        int length = withoutPrefix.length();
        if (length != 6 && length != 8) {
            return null;
        }
        try {
            long value = Long.parseLong(withoutPrefix, 16);
            if (length == 6) {
                return Integer.valueOf(0xFF000000 | (int) (value & 0xFFFFFF));
            }
            return Integer.valueOf((int) (value & 0xFFFFFFFFL));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String readCurrentStringValue() {
        ConfigNode node = getCurrentNode();
        if (node == null || node.isNull()) {
            Object defaultValue = getDefaultValue();
            return defaultValue == null ? "" : String.valueOf(defaultValue);
        }
        return node.asString("");
    }

    private String resolvePlaceholder() {
        ModernConfigTemplateScreen.FieldSpec fieldSpec = getFieldSpec();
        if (fieldSpec != null) {
            String placeholder = fieldSpec.getPlaceholder();
            if (placeholder != null && !placeholder.isEmpty()) {
                return placeholder;
            }
        }
        if (pickerKind == ModernConfigTypeInference.PickerKind.RESOURCE) {
            return "输入资源路径（5-D 收口时切换为资源选择器）";
        }
        if (pickerKind == ModernConfigTypeInference.PickerKind.SOUND) {
            return "输入声音事件（5-D 收口时切换为声音选择器）";
        }
        return "";
    }
}
