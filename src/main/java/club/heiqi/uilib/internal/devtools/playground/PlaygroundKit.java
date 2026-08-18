package club.heiqi.uilib.internal.devtools.playground;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneButtonVariant;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 测试场地页面通用配色与装配小工具。
 *
 * <p>只做「构建期一次性建树」的静态装配：返回的节点已设好不可命中、宽度尺寸等静态样式，
 * 动态随状态变化的外观一律由页面在 {@code build} 内用 {@code rt.bind/bindComputed} 派生
 * （守 scene 信条 R3/R4：组件函数只执行一次、外观随状态经 bind 派生）。</p>
 *
 * <p>本类位于 {@code internal.devtools} 下，属内部调试设施，不构成公共 API 承诺。</p>
 */
public final class PlaygroundKit {

    /** 页面根底色（比控件禁用底更深的暗色，衬托面板高差）。 */
    public static final int ROOT_BG = 0xFF17151B;
    /** 面板底色（与 scene 控件默认底同源）。 */
    public static final int PANEL_BG = SceneChromeTokens.BG_DEFAULT;
    /** 面板边框色。 */
    public static final int BORDER = SceneChromeTokens.BORDER_DEFAULT;
    /** 主文本色。 */
    public static final int TEXT = SceneChromeTokens.TEXT_PRIMARY;
    /** 次要/说明文本色。 */
    public static final int MUTED = SceneChromeTokens.TEXT_SECONDARY;
    /** 强调色（选中/主操作）。 */
    public static final int ACCENT = SceneChromeTokens.ACCENT;
    /** 危险操作色（与 SceneDialog DANGER 同源观感）。 */
    public static final int DANGER = 0xFFB3261E;

    /** 页面骨架最大内容宽（UI 像素）。 */
    public static final int MAX_CONTENT_WIDTH = 860;

    private PlaygroundKit() {
    }

    /**
     * 创建默认样式文本节点（TEXT 色，16px，不可命中）。
     *
     * @param value 文本
     * @return 文本节点
     */
    public static SceneNode text(String value) {
        return text(value, TEXT, 16);
    }

    /**
     * 创建指定样式文本节点（不可命中）。
     *
     * @param value    文本
     * @param color    文本色
     * @param fontSize 字号（UI 像素）
     * @return 文本节点
     */
    public static SceneNode text(String value, int color, int fontSize) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setTextColor(color);
        node.setFontSize(fontSize);
        node.setHitTestable(false);
        return node;
    }

    /**
     * 页面节标题（16px 主色）。
     *
     * @param value 标题文本
     * @return 标题节点
     */
    public static SceneNode title(String value) {
        return text(value, TEXT, 16);
    }

    /**
     * 次级说明文本（12px 次要色）。
     *
     * @param value 说明文本
     * @return 说明节点
     */
    public static SceneNode hint(String value) {
        return text(value, MUTED, 12);
    }

    /**
     * 强调说明文本（12px 主色）。
     *
     * @param value 说明文本
     * @return 说明节点
     */
    public static SceneNode strongHint(String value) {
        return text(value, TEXT, 12);
    }

    /**
     * 创建标准面板卡片：实底 + 1px 边框 + 圆角 + 内边距 + 纵向间距，宽度填满父轴最大宽。
     *
     * @return 卡片根节点（COLUMN）
     */
    public static SceneNode card() {
        SceneNode card = SceneNode.column();
        card.setFillParentWidth(true);
        card.setMaxWidth(MAX_CONTENT_WIDTH);
        card.setPadding(SceneChromeTokens.PAD_LG);
        card.setGap(SceneChromeTokens.GAP_MD);
        card.setBackgroundColor(PANEL_BG);
        card.setBorderWidth(1);
        card.setBorderColor(BORDER);
        card.setCornerRadius(SceneChromeTokens.RADIUS_MD);
        return card;
    }

    /**
     * 创建横向行容器（gap=10）。
     *
     * @return 行节点
     */
    public static SceneNode row(int gap) {
        SceneNode row = SceneNode.row(gap);
        row.setHitTestable(false);
        return row;
    }

    /**
     * 创建并挂载标准按钮（STANDARD 变体）。
     *
     * <p>组件经 {@code rt.mount(parent, ...)} 挂到父节点并返回其根节点（调用一次即完成装配，
     * 静态样式固化，动态交互走 bind/on）。</p>
     *
     * @param rt      场景运行时
     * @param parent  挂载父节点
     * @param label   按钮文本
     * @param onClick 点击回调（经 CLICK 事件触发）
     * @return 按钮根节点（已挂入 parent）
     */
    public static SceneNode button(SceneRuntime rt, SceneNode parent, String label, Runnable onClick) {
        return rt.mount(parent, SceneButton.create(rt, new SceneButton.Props(
                Signal.create(label), Signal.create(Boolean.TRUE), onClick, SceneButtonVariant.STANDARD))).getRoot();
    }

    /**
     * 创建并挂载主操作按钮（PRIMARY 变体）。
     *
     * @param rt      场景运行时
     * @param parent  挂载父节点
     * @param label   按钮文本
     * @param onClick 点击回调
     * @return 按钮根节点（已挂入 parent）
     */
    public static SceneNode primaryButton(SceneRuntime rt, SceneNode parent, String label, Runnable onClick) {
        return rt.mount(parent, SceneButton.create(rt, new SceneButton.Props(
                Signal.create(label), Signal.create(Boolean.TRUE), onClick, SceneButtonVariant.PRIMARY))).getRoot();
    }

    /**
     * 占位 spacer（flexGrow=1 的不可见节点），用于 ROW 布局撑开两侧间距。
     *
     * @return 占位节点
     */
    public static SceneNode spacer() {
        SceneNode spacer = new SceneNode();
        spacer.setFlexGrow(1);
        spacer.setHitTestable(false);
        return spacer;
    }
}