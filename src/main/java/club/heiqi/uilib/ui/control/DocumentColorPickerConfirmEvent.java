package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * 颜色选择器确认事件。
 *
 * <p>当用户在 HEX/RGB 输入框失焦或按回车时触发，表示当前编辑值「提交」。
 * 业务侧可在该回调中执行真正的写回操作，避免每次按键都触发持久化。</p>
 */
public final class DocumentColorPickerConfirmEvent {

    private final DocumentColorPickerControl source;
    private final ElementNode element;
    private final int argb;
    private final String hex;
    private final boolean valid;

    DocumentColorPickerConfirmEvent(DocumentColorPickerControl source, ElementNode element, int argb, String hex,
            boolean valid) {
        this.source = Objects.requireNonNull(source, "source");
        this.element = Objects.requireNonNull(element, "element");
        this.argb = argb;
        this.hex = hex == null ? "" : hex;
        this.valid = valid;
    }

    /**
     * 返回触发事件的颜色选择器控件。
     *
     * @return 颜色选择器控件
     */
    public DocumentColorPickerControl getSource() {
        return source;
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
     * 返回提交时刻的 ARGB 颜色；非法输入时返回上一次有效值。
     *
     * @return ARGB 颜色
     */
    public int getArgb() {
        return argb;
    }

    /**
     * 返回提交时刻的 HEX 文本。
     *
     * @return HEX 文本
     */
    public String getHex() {
        return hex;
    }

    /**
     * 判断提交时输入是否合法。
     *
     * @return true 表示合法；false 表示输入非法，业务侧应保留旧值
     */
    public boolean isValid() {
        return valid;
    }
}
