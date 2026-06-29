package club.heiqi.config.ui.field;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.component.MountHandle;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

import java.util.function.Supplier;

/**
 * 字段卡片外壳共享构建器：label + helper + 控件 mount 槽 + error 提示 + dirty 标记。
 *
 * <p>照 {@code SceneFormHostWidget.createFieldShell} 范式，供 4 个 FieldRenderer 复用，
 * 避免每个 renderer 重复外壳样板。外壳结构：</p>
 * <pre>
 * card (COLUMN, bg, border, radius, padding, gap)
 *   ├ bind border color by error/dirty
 *   ├ header (ROW, gap)
 *   │   ├ dot (●, color by error/dirty)
 *   │   └ title (label)
 *   ├ helper text
 *   ├ 控件 (mount 槽，由 caller 提供 Supplier)
 *   └ error text
 * </pre>
 */
final class FieldShell {

    /** 包级工具类，禁止实例化 */
    private FieldShell() {
    }

    /**
     * 构建字段外壳并挂载控件。
     *
     * @param rt        场景运行时
     * @param spec      字段元数据（读 label / helper）
     * @param adapter   草稿适配器（读 errorSignal / dirtySignal）
     * @param controlFn 控件构建函数（{@code SceneXxx.create(rt, props)} 产物）
     * @return 字段卡片节点（已挂载控件 + error 文本）
     */
    static SceneNode build(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter,
                           Supplier<SceneNode> controlFn) {
        String path = spec.path();
        ReadableSignal<String> errorSig = adapter.errorSignal(path);
        ReadableSignal<Boolean> dirtySig = adapter.dirtySignal(path);

        SceneNode card = new SceneNode();
        card.setFlexDirection(FlexDirection.COLUMN);
        card.setBackgroundColor(ConfigTheme.CARD_BG);
        card.setBorderWidth(1);
        card.setCornerRadius(ConfigTheme.CARD_RADIUS);
        card.setPadding(ConfigTheme.CARD_PAD);
        card.setGap(ConfigTheme.FIELD_GAP);

        // 边框色由 error / dirty 派生：error > dirty > default
        rt.bind(Invalidation.PAINT,
                Computed.create(() -> resolveCardBorder(errorSig.get(), dirtySig.get())),
                card::setBorderColor);

        // header：状态圆点 + 标题
        SceneNode header = new SceneNode();
        header.setFlexDirection(FlexDirection.ROW);
        header.setGap(ConfigTheme.FIELD_GAP);
        SceneNode dot = text("●", ConfigTheme.MUTED_COLOR, ConfigTheme.FONT_LABEL);
        // dot 三态：error 优先 > dirty > normal（修正旧逻辑 dirty+error 同时为真时显示蓝的小不一致）
        rt.bind(Invalidation.PAINT,
                Computed.create(() -> {
                    if (!safe(errorSig.get()).isEmpty()) {
                        return ConfigTheme.ERROR_COLOR;
                    }
                    if (Boolean.TRUE.equals(dirtySig.get())) {
                        return ConfigTheme.DIRTY_COLOR;
                    }
                    return ConfigTheme.MUTED_COLOR;
                }),
                dot::setTextColor);
        SceneNode title = text(safe(spec.label(), path), ConfigTheme.TEXT_COLOR, ConfigTheme.FONT_LABEL);
        header.appendChild(dot);
        header.appendChild(title);
        card.appendChild(header);

        // helper 文本
        String helper = spec.helper();
        if (helper != null && !helper.isEmpty()) {
            card.appendChild(text(helper, ConfigTheme.MUTED_COLOR, ConfigTheme.FONT_HELPER));
        }

        // 控件 mount 槽
        MountHandle handle = rt.mount(card, controlFn);
        SceneNode controlRoot = handle.getRoot();
        if (controlRoot != null) {
            controlRoot.setPreferredHeight(ConfigTheme.INPUT_HEIGHT);
        }

        // error 文本
        SceneNode errorNode = text("", ConfigTheme.ERROR_COLOR, ConfigTheme.FONT_ERROR);
        rt.bind(Invalidation.LAYOUT, errorSig, errorNode::setText);
        rt.bind(Invalidation.PAINT,
                Computed.create(() -> safe(errorSig.get()).isEmpty() ? ConfigTheme.MUTED_COLOR
                        : ConfigTheme.ERROR_COLOR),
                errorNode::setTextColor);
        card.appendChild(errorNode);

        return card;
    }

    /**
     * 创建不可命中、带初始文本、颜色与字号的文字节点。
     *
     * @param value    文本
     * @param color    颜色
     * @param fontSize 字号（UI 像素）
     * @return 文字节点
     */
    private static SceneNode text(String value, int color, int fontSize) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setTextColor(color);
        node.setFontSize(fontSize);
        node.setHitTestable(false);
        return node;
    }

    /**
     * 解析卡片边框色：error > dirty > default。
     *
     * @param error 错误文案
     * @param dirty 是否脏
     * @return 边框色
     */
    private static int resolveCardBorder(String error, Boolean dirty) {
        if (!safe(error).isEmpty()) {
            return ConfigTheme.CARD_BORDER_ERROR;
        }
        if (Boolean.TRUE.equals(dirty)) {
            return ConfigTheme.CARD_BORDER_DIRTY;
        }
        return ConfigTheme.CARD_BORDER;
    }

    /**
     * null 安全文本。
     *
     * @param value 文本
     * @return 非 null 文本
     */
    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 带默认值的 null 安全文本。
     *
     * @param value        文本
     * @param defaultValue 默认值
     * @return 非 null 文本
     */
    private static String safe(String value, String defaultValue) {
        return value == null || value.isEmpty() ? defaultValue : value;
    }
}
