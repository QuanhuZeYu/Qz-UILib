package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * 颜色选择器内容变更事件。
 *
 * <p>当用户在 HEX/RGB 输入框中编辑或通过 {@link DocumentColorPickerControl#setColor(int)}
 * 等方法更新颜色时触发。事件携带当前 ARGB 颜色与 HEX 文本，回调中拿到的是事件时刻的快照。</p>
 */
public final class DocumentColorPickerChangeEvent {

    private final DocumentColorPickerControl source;
    private final ElementNode element;
    private final int argb;
    private final String hex;

    DocumentColorPickerChangeEvent(DocumentColorPickerControl source, ElementNode element, int argb, String hex) {
        this.source = Objects.requireNonNull(source, "source");
        this.element = Objects.requireNonNull(element, "element");
        this.argb = argb;
        this.hex = hex == null ? "" : hex;
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
     * 返回事件触发时刻的 ARGB 颜色。
     *
     * @return ARGB 颜色
     */
    public int getArgb() {
        return argb;
    }

    /**
     * 返回事件触发时刻的 HEX 文本（大写、带 #）。
     *
     * @return HEX 文本
     */
    public String getHex() {
        return hex;
    }
}
