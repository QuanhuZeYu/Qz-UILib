package club.heiqi.uilib.ui.scene.control;

import java.util.List;
import java.util.Objects;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.ScenePortalHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * SceneDialog —— scene 模态对话框。
 *
 * <h3>能力</h3>
 * <ul>
 *   <li>{@code visible} 驱动经 portalAnchored（无锚点）挂全屏 overlay：80% 暗色遮罩拦截指针
 *       （模态，主树不可命中），居中卡片承载标题/正文/按钮行；</li>
 *   <li>焦点陷阱零成本：active overlay 存在时 router 的 Tab 环自动限定在栈顶 overlay root 内
 *       （{@code SceneInputRouter.resolveFocusScope}）；打开时焦点落在第一个按钮；</li>
 *   <li>ESC 经 {@link OverlayDismissPolicy#DEFAULT} 请求关闭（回调 onDismiss，由调用方 set visible=false）；</li>
 *   <li>按钮：label + kind（PRIMARY/NORMAL/DANGER 上色）+ onClick；默认点击后请求关闭，
 *       closesDialog=false 只执行回调；焦点态按钮支持 Enter/Space 激活。</li>
 * </ul>
 */
public final class SceneDialog {

    /** 卡片宽度（像素）。 */
    private static final int CARD_WIDTH = 320;
    /** 卡片内边距。 */
    private static final int CARD_PADDING = SceneChromeTokens.PAD_LG;
    /** 卡片纵向间距。 */
    private static final int CARD_GAP = SceneChromeTokens.PAD_MD;
    /** 按钮行间距。 */
    private static final int BUTTON_GAP = 8;
    /** 按钮内边距。 */
    private static final int BUTTON_PAD_V = 6;
    /** 按钮内边距。 */
    private static final int BUTTON_PAD_H = 12;
    /** 遮罩色：80% 不透明暗色（与 modernconfig 遮罩同源观感）。 */
    private static final int SCRIM_ARGB = 0xCC121016;
    /** 危险按钮背景（Material error）。 */
    private static final int DANGER_BG = 0xFFB3261E;

    /** 纯静态工厂，禁止实例化。 */
    private SceneDialog() {
    }

    /**
     * 按钮样式。
     */
    public enum ButtonKind {
        /** 主操作（focus 色底 + 反白字）。 */
        PRIMARY,
        /** 普通（hover 底 + 主文本色）。 */
        NORMAL,
        /** 危险（error 底 + 反白字）。 */
        DANGER
    }

    /**
     * 对话框按钮。
     *
     * @param label         按钮文本
     * @param kind          样式
     * @param closesDialog  点击后是否请求关闭（onDismiss）
     * @param onClick       点击回调（可为 null）
     */
    @Desugar
    public record Button(String label, ButtonKind kind, boolean closesDialog, Runnable onClick) {
        public Button {
            label = label == null ? "" : label;
            kind = kind == null ? ButtonKind.NORMAL : kind;
        }

        /**
         * 创建普通按钮（点击后关闭）。
         *
         * @param label   按钮文本
         * @param onClick 点击回调（可为 null）
         * @return 按钮
         */
        public static Button of(String label, Runnable onClick) {
            return new Button(label, ButtonKind.NORMAL, true, onClick);
        }
    }

    /**
     * 对话框输入契约。
     *
     * @param visible   可见性信号（调用方持有；onDismiss 中 set false）
     * @param title     标题文本
     * @param message   正文文本
     * @param buttons   按钮列表（防御性复制，可为空）
     * @param onDismiss 关闭请求回调（ESC/遮罩按钮关闭时调用；实现必须把 visible 置 false）
     */
    @Desugar
    public record Props(
            ReadableSignal<Boolean> visible,
            String title,
            String message,
            List<Button> buttons,
            Runnable onDismiss
    ) {
        public Props {
            Objects.requireNonNull(visible, "visible");
            buttons = buttons == null ? java.util.Collections.<Button>emptyList()
                    : SceneListOps.immutableCopy(buttons);
        }
    }

    /**
     * 创建受控模态对话框。
     *
     * @param rt    场景运行时
     * @param props 对话框输入契约
     * @return portal 句柄（dispose 卸载；visible 控制挂载）
     */
    public static ScenePortalHandle create(SceneRuntime rt, Props props) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(props, "props");
        return rt.portalAnchored(props.visible(),
                () -> buildDialog(rt, props),
                OverlayDismissPolicy.DEFAULT,
                () -> {
                    if (props.onDismiss() != null) {
                        props.onDismiss().run();
                    }
                },
                null);
    }

    /**
     * 构建对话框 overlay root（全屏遮罩 + 居中卡片）。
     */
    private static SceneNode buildDialog(SceneRuntime rt, Props props) {
        SceneNode scrim = SceneNode.column();
        scrim.setMainAxisAlign(MainAxisAlign.CENTER);
        scrim.setCrossAxisAlign(CrossAxisAlign.CENTER);
        scrim.setBackgroundColor(SCRIM_ARGB);
        scrim.setClipChildren(true);

        SceneNode card = SceneNode.column();
        card.setPreferredWidth(CARD_WIDTH);
        card.setPadding(CARD_PADDING);
        card.setGap(CARD_GAP);
        card.setBackgroundColor(SceneChromeTokens.BG_DEFAULT);
        card.setBorderWidth(1);
        card.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);
        card.setCornerRadius(SceneChromeTokens.RADIUS_MD);
        card.setClipChildren(true);
        scrim.appendChild(card);

        SceneNode title = new SceneNode();
        title.setText(SceneTextUtils.nullSafe(props.title()));
        title.setHitTestable(false);
        card.appendChild(title);

        SceneNode message = new SceneNode();
        message.setText(SceneTextUtils.nullSafe(props.message()));
        message.setHitTestable(false);
        card.appendChild(message);

        SceneNode buttonRow = SceneNode.row();
        buttonRow.setMainAxisAlign(MainAxisAlign.END);
        buttonRow.setGap(BUTTON_GAP);
        card.appendChild(buttonRow);

        boolean first = true;
        for (Button button : props.buttons()) {
            SceneNode buttonNode = buildButton(rt, button, props.onDismiss());
            buttonRow.appendChild(buttonNode);
            if (first) {
                // 打开即聚焦第一个按钮（Tab 环在 active overlay 内循环）
                rt.requestFocus(buttonNode);
                first = false;
            }
        }
        return scrim;
    }

    /**
     * 构建单个按钮节点。
     */
    private static SceneNode buildButton(SceneRuntime rt, Button button, Runnable onDismiss) {
        SceneNode buttonNode = SceneNode.row();
        buttonNode.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        buttonNode.setPadding(BUTTON_PAD_V, BUTTON_PAD_H, BUTTON_PAD_V, BUTTON_PAD_H);
        buttonNode.setCornerRadius(SceneChromeTokens.RADIUS_SM);
        buttonNode.setBackgroundColor(resolveButtonBackground(button.kind()));
        // 声明关心 focused（writeFocused 对未声明节点 null 短路）+ 建容器，再进 Tab 环
        rt.interactionState(buttonNode).focused();
        rt.focusable(buttonNode, Signal.create(Boolean.TRUE));

        SceneNode label = new SceneNode();
        label.setText(button.label());
        label.setHitTestable(false);
        label.setTextColor(resolveButtonTextColor(button.kind()));
        buttonNode.appendChild(label);

        Runnable activate = () -> {
            if (button.onClick() != null) {
                button.onClick().run();
            }
            if (button.closesDialog() && onDismiss != null) {
                onDismiss.run();
            }
        };
        rt.on(buttonNode, SceneEventType.CLICK, (ev, ctx) -> {
            activate.run();
            ctx.stopPropagation();
        });
        rt.on(buttonNode, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (ev.getKeyAction() != SceneKeyAction.PRESSED) {
                return;
            }
            if (ev.getKey() == SceneKey.ENTER || ev.getKey() == SceneKey.SPACE) {
                activate.run();
                ctx.stopPropagation();
            }
        });
        return buttonNode;
    }

    /**
     * 解析按钮背景色（按 kind）。
     */
    private static int resolveButtonBackground(ButtonKind kind) {
        switch (kind) {
            case PRIMARY:
                return SceneChromeTokens.BORDER_FOCUS;
            case DANGER:
                return DANGER_BG;
            case NORMAL:
            default:
                return SceneChromeTokens.BG_HOVER;
        }
    }

    /**
     * 解析按钮文本色（按 kind）。
     */
    private static int resolveButtonTextColor(ButtonKind kind) {
        switch (kind) {
            case PRIMARY:
            case DANGER:
                return SceneChromeTokens.TEXT_ON_ACCENT;
            case NORMAL:
            default:
                return SceneChromeTokens.TEXT_PRIMARY;
        }
    }
}
