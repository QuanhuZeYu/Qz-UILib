package club.heiqi.uilib.ui.control;

import java.util.Locale;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 颜色选择器控件：色块预览 + HEX 输入 + RGB 输入，三者双向同步。
 *
 * <p>控件组合使用 {@link DocumentTextInputControl} 作为输入层，外层叠加色块预览与错误提示。
 * 内部维护一个 ARGB int 颜色（默认不透明黑 {@code 0xFF000000}）。</p>
 *
 * <p>HEX 输入支持以下格式（不区分大小写，# 可选）：</p>
 * <ul>
 *   <li>{@code #RRGGBB}：alpha 默认 0xFF；</li>
 *   <li>{@code #AARRGGBB}：完整 ARGB。</li>
 * </ul>
 *
 * <p>RGB 输入框接受 0-255 整数；超界或非数字时显示错误提示，<b>不抛异常</b>，ARGB 保留上一次有效值。</p>
 *
 * <p>事件契约：</p>
 * <ul>
 *   <li>{@link DocumentColorPickerChangeHandler}：HEX 或 RGB 任一输入导致 ARGB 变化时触发；</li>
 *   <li>{@link DocumentColorPickerConfirmHandler}：在任一输入框按 RETURN，或调用 {@link #commitNow()}
 *       时触发。如果业务需要「失焦提交」，可在失焦后调用 {@link #commitNow()}。</li>
 * </ul>
 */
public final class DocumentColorPickerControl {

    /** 默认颜色：不透明黑。 */
    public static final int DEFAULT_COLOR = 0xFF000000;

    private static final int CHANNEL_MIN = 0;
    private static final int CHANNEL_MAX = 255;

    private final UiDocument document;
    private final ElementNode element;
    private final ElementNode previewElement;
    private final DocumentTextInputControl hexInput;
    private final DocumentTextInputControl rInput;
    private final DocumentTextInputControl gInput;
    private final DocumentTextInputControl bInput;
    private final ElementNode errorElement;
    private final TextNode errorText;

    private DocumentColorPickerChangeHandler changeHandler;
    private DocumentColorPickerConfirmHandler confirmHandler;

    private int argb = DEFAULT_COLOR;
    private String lastError = "";

    // 配色
    private int previewBorderColor = 0xFF555577;
    private int errorTextColor = 0xFFFF8888;

    /**
     * 创建颜色选择器控件。
     *
     * @param document 所属 HTML-like 文档
     */
    public DocumentColorPickerControl(UiDocument document) {
        this.document = Objects.requireNonNull(document, "document");
        this.element = document.div();
        this.previewElement = document.div();
        this.hexInput = new DocumentTextInputControl(document)
                .setPlaceholder("#RRGGBB 或 #AARRGGBB")
                .setMaxLength(9);
        this.rInput = createChannelInput();
        this.gInput = createChannelInput();
        this.bInput = createChannelInput();
        this.errorElement = document.div();
        this.errorText = errorElement.appendText("");

        configureRoot();
        configurePreview();
        configureInputs();
        configureError();
        assembleLayout();
        syncInputsFromColor();
    }

    private DocumentTextInputControl createChannelInput() {
        DocumentTextInputControl input = new DocumentTextInputControl(document)
                .setType(DocumentInputType.NUMBER)
                .setMaxLength(3);
        input.getElement().style().setWidth(UiStyleLength.px(60));
        return input;
    }

    /**
     * 返回控件根元素。
     *
     * @return 根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回 HEX 输入框控件，便于业务侧调整样式。
     *
     * @return HEX 输入框控件
     */
    public DocumentTextInputControl getHexInput() {
        return hexInput;
    }

    /**
     * 返回红色通道输入框控件。
     *
     * @return 红色通道输入框控件
     */
    public DocumentTextInputControl getRedInput() {
        return rInput;
    }

    /**
     * 返回绿色通道输入框控件。
     *
     * @return 绿色通道输入框控件
     */
    public DocumentTextInputControl getGreenInput() {
        return gInput;
    }

    /**
     * 返回蓝色通道输入框控件。
     *
     * @return 蓝色通道输入框控件
     */
    public DocumentTextInputControl getBlueInput() {
        return bInput;
    }

    /**
     * 返回当前 ARGB 颜色。
     *
     * @return ARGB 颜色
     */
    public int getColor() {
        return argb;
    }

    /**
     * 设置 ARGB 颜色并同步所有输入框。
     *
     * @param argb ARGB 颜色
     * @return 当前控件
     */
    public DocumentColorPickerControl setColor(int argb) {
        if (this.argb == argb) {
            return this;
        }
        this.argb = argb;
        syncInputsFromColor();
        clearError();
        fireChange();
        return this;
    }

    /**
     * 返回当前 HEX 文本（大写、带 #、9 位 {@code #AARRGGBB} 形式）。
     *
     * @return HEX 文本
     */
    public String getHex() {
        return String.format(Locale.ENGLISH, "#%08X", argb);
    }

    /**
     * 通过 HEX 文本设置颜色；非法格式时显示错误，ARGB 保留旧值。
     *
     * @param hex HEX 文本
     * @return 当前控件
     */
    public DocumentColorPickerControl setHex(String hex) {
        Integer parsed = parseHex(hex);
        if (parsed == null) {
            showError("HEX 格式应为 #RRGGBB 或 #AARRGGBB");
            return this;
        }
        if (parsed == argb) {
            syncInputsFromColor();
            clearError();
            return this;
        }
        argb = parsed;
        syncInputsFromColor();
        clearError();
        fireChange();
        return this;
    }

    /**
     * 通过 RGB 三通道设置颜色（alpha 默认 0xFF）。
     *
     * @param r 红色 0-255
     * @param g 绿色 0-255
     * @param b 蓝色 0-255
     * @return 当前控件
     */
    public DocumentColorPickerControl setRgb(int r, int g, int b) {
        if (!isChannelInRange(r) || !isChannelInRange(g) || !isChannelInRange(b)) {
            showError("RGB 通道应在 0-255 之间");
            return this;
        }
        int next = 0xFF000000 | (clampChannel(r) << 16) | (clampChannel(g) << 8) | clampChannel(b);
        if (next == argb) {
            syncInputsFromColor();
            clearError();
            return this;
        }
        argb = next;
        syncInputsFromColor();
        clearError();
        fireChange();
        return this;
    }

    /**
     * 返回 RGB 三通道数组，索引 0/1/2 对应 R/G/B。
     *
     * @return RGB 数组
     */
    public int[] getRgb() {
        return new int[] {(argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF};
    }

    /**
     * 设置变更处理器。
     *
     * @param changeHandler 变更处理器；为 null 时清除
     * @return 当前控件
     */
    public DocumentColorPickerControl setChangeHandler(DocumentColorPickerChangeHandler changeHandler) {
        this.changeHandler = changeHandler;
        return this;
    }

    /**
     * 设置确认处理器。
     *
     * @param confirmHandler 确认处理器；为 null 时清除
     * @return 当前控件
     */
    public DocumentColorPickerControl setConfirmHandler(DocumentColorPickerConfirmHandler confirmHandler) {
        this.confirmHandler = confirmHandler;
        return this;
    }

    /**
     * 主动触发一次确认事件。
     *
     * <p>可由业务侧在「失焦」「保存按钮」等场景调用。事件携带当前 ARGB 与 {@link #isValidFlag()} 状态。</p>
     *
     * @return 当前控件
     */
    public DocumentColorPickerControl commitNow() {
        if (confirmHandler != null) {
            confirmHandler.onColorConfirmed(new DocumentColorPickerConfirmEvent(this, element, argb, getHex(),
                    lastError.isEmpty()));
        }
        return this;
    }

    /**
     * 返回当前错误提示文案（空串表示无错误）。
     *
     * @return 错误提示文案
     */
    public String getError() {
        return lastError;
    }

    /**
     * 设置色块预览边框颜色。
     *
     * @param previewBorderColor 边框颜色
     * @return 当前控件
     */
    public DocumentColorPickerControl setPreviewBorderColor(int previewBorderColor) {
        this.previewBorderColor = previewBorderColor;
        previewElement.style().setBorderColor(previewBorderColor);
        return this;
    }

    /**
     * 设置错误提示文本颜色。
     *
     * @param errorTextColor 错误文本颜色
     * @return 当前控件
     */
    public DocumentColorPickerControl setErrorTextColor(int errorTextColor) {
        this.errorTextColor = errorTextColor;
        errorElement.style().setTextColor(errorTextColor);
        return this;
    }

    private void configureRoot() {
        element.setAttribute("data-document-control", "color-picker");
        element.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setRowGap(UiStyleLength.px(8))
                .setWidth(UiStyleLength.px(280));
    }

    private void configurePreview() {
        previewElement.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.px(64))
                .setHeight(UiStyleLength.px(64))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(previewBorderColor)
                .setBorderRadius(UiStyleLength.px(4))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        previewElement.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {
                if (context == null || contentRight <= contentLeft || contentBottom <= contentTop) {
                    return;
                }
                context.fillRect(contentLeft, contentTop, contentRight, contentBottom, argb);
            }
        });
    }

    private void configureInputs() {
        hexInput.setChangeHandler(new DocumentTextInputChangeHandler() {
            @Override
            public void onTextChanged(DocumentTextInputChangeEvent event) {
                handleHexChanged();
            }
        });
        hexInput.setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                return handleConfirmKey(event);
            }
        });
        hexInput.getElement().style().setWidth(UiStyleLength.px(180));

        DocumentTextInputChangeHandler rgbHandler = new DocumentTextInputChangeHandler() {
            @Override
            public void onTextChanged(DocumentTextInputChangeEvent event) {
                handleRgbChanged();
            }
        };
        DocumentElementKeyHandler rgbKeyHandler = new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                return handleConfirmKey(event);
            }
        };
        rInput.setChangeHandler(rgbHandler);
        gInput.setChangeHandler(rgbHandler);
        bInput.setChangeHandler(rgbHandler);
        rInput.setKeyHandler(rgbKeyHandler);
        gInput.setKeyHandler(rgbKeyHandler);
        bInput.setKeyHandler(rgbKeyHandler);
    }

    private void configureError() {
        errorElement.style()
                .setDisplay(UiDisplay.BLOCK)
                .setTextColor(errorTextColor);
    }

    private void assembleLayout() {
        ElementNode previewRow = document.div();
        previewRow.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(12));
        previewRow.append(previewElement);
        ElementNode hexLabel = document.div();
        hexLabel.style().setTextColor(0xFFCBD5E1);
        hexLabel.appendText("HEX");
        previewRow.append(hexLabel);
        previewRow.append(hexInput.getElement());

        ElementNode rgbRow = document.div();
        rgbRow.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8));
        rgbRow.append(createLabeledChannel("R", rInput));
        rgbRow.append(createLabeledChannel("G", gInput));
        rgbRow.append(createLabeledChannel("B", bInput));

        element.append(previewRow);
        element.append(rgbRow);
        element.append(errorElement);
    }

    private ElementNode createLabeledChannel(String label, DocumentTextInputControl input) {
        ElementNode wrapper = document.div();
        wrapper.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(4));
        ElementNode labelElement = document.div();
        labelElement.style().setTextColor(0xFFCBD5E1);
        labelElement.appendText(label);
        wrapper.append(labelElement);
        wrapper.append(input.getElement());
        return wrapper;
    }

    private void handleHexChanged() {
        Integer parsed = parseHex(hexInput.getText());
        if (parsed == null) {
            showError("HEX 格式应为 #RRGGBB 或 #AARRGGBB");
            return;
        }
        if (parsed == argb) {
            return;
        }
        argb = parsed;
        syncRgbInputs();
        clearError();
        fireChange();
    }
    private void handleRgbChanged() {
        Integer r = parseChannel(rInput.getText());
        Integer g = parseChannel(gInput.getText());
        Integer b = parseChannel(bInput.getText());
        if (r == null || g == null || b == null) {
            showError("RGB 通道应在 0-255 之间");
            return;
        }
        int next = 0xFF000000 | (r << 16) | (g << 8) | b;
        if (next == argb) {
            return;
        }
        argb = next;
        hexInput.setText(getHex());
        clearError();
        fireChange();
    }

    private boolean handleConfirmKey(DocumentElementKeyEvent event) {
        if (event.getKeyCode() == UiKeyCodes.KEY_RETURN || event.getKeyCode() == UiKeyCodes.KEY_NUMPADENTER) {
            if (event.getAction() == UiKeyEvent.Action.PRESSED
                    || event.getAction() == UiKeyEvent.Action.REPEATED) {
                commitNow();
                return true;
            }
        }
        return false;
    }

    private void syncInputsFromColor() {
        hexInput.setText(getHex());
        syncRgbInputs();
    }

    private void syncRgbInputs() {
        rInput.setText(String.valueOf((argb >> 16) & 0xFF));
        gInput.setText(String.valueOf((argb >> 8) & 0xFF));
        bInput.setText(String.valueOf(argb & 0xFF));
    }

    private void showError(String message) {
        String next = message == null ? "" : message;
        if (next.equals(lastError)) {
            return;
        }
        lastError = next;
        errorText.setText(next);
    }

    private void clearError() {
        if (lastError.isEmpty()) {
            return;
        }
        lastError = "";
        errorText.setText("");
    }

    private void fireChange() {
        if (changeHandler != null) {
            changeHandler.onColorChanged(new DocumentColorPickerChangeEvent(this, element, argb, getHex()));
        }
    }

    /** HEX 解析：支持 {@code #RRGGBB} 与 {@code #AARRGGBB}（# 可选）。非法返回 null。 */
    private static Integer parseHex(String raw) {
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
                return 0xFF000000 | (int) (value & 0xFFFFFF);
            }
            return (int) (value & 0xFFFFFFFFL);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 通道解析：接受 0-255 整数。非法返回 null。 */
    private static Integer parseChannel(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < CHANNEL_MIN || parsed > CHANNEL_MAX) {
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean isChannelInRange(int value) {
        return value >= CHANNEL_MIN && value <= CHANNEL_MAX;
    }

    private static int clampChannel(int value) {
        if (value < CHANNEL_MIN) {
            return CHANNEL_MIN;
        }
        if (value > CHANNEL_MAX) {
            return CHANNEL_MAX;
        }
        return value;
    }
}
