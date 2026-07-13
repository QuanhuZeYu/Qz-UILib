package club.heiqi.uilib.ui.scene.form;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 构建标签、辅助说明与控件组成的安全表单行。
 *
 * <p>当前默认使用纵向布局，不要求调用方猜测标签像素宽度。标签和辅助说明由各自的
 * 裁剪槽承载，控件在独立内容槽中接收父布局下沉的实际可用宽度，因此窄视口下不会与
 * 标签相交。后续只有在布局 API 能按实际可用宽度可靠分流时，才应增加横向变体。</p>
 */
public final class FormLabeledControl {
    private static final int GAP = 2;

    private FormLabeledControl() { }

    /**
     * 构建纵向标签控件行。
     *
     * @param label 显示标签；为空时不创建标签槽
     * @param helper 可选辅助说明；为空时不创建说明槽
     * @param control 控件节点
     * @return 纵向、安全裁剪的表单行
     */
    public static SceneNode vertical(String label, String helper, SceneNode control) {
        if (control == null) throw new IllegalArgumentException("control must not be null");
        SceneNode root = SceneNode.column();
        root.setGap(GAP);
        if (label != null && !label.isEmpty()) root.appendChild(textSlot(label));
        if (helper != null && !helper.isEmpty()) root.appendChild(textSlot(helper));
        SceneNode content = SceneNode.column();
        content.setClipChildren(true);
        content.appendChild(control);
        root.appendChild(content);
        return root;
    }

    /** 构建占满可用宽度并裁剪溢出文本的只读槽。 */
    private static SceneNode textSlot(String text) {
        SceneNode slot = SceneNode.row();
        slot.setClipChildren(true);
        SceneNode node = new SceneNode();
        node.setText(text);
        node.setHitTestable(false);
        slot.appendChild(node);
        return slot;
    }
}
