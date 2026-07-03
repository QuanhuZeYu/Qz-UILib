package club.heiqi.uilib.ui.scene.form;

import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 字段卡片外壳共享构建器：label + helper + 控件 mount 槽 + error 提示 + dirty 标记。
 *
 * <p>从 {@code config.ui.field.FieldShell} 提炼下沉的通用工具，照
 * {@code SceneFormHostWidget.createFieldShell} 范式，供表单消费方复用，避免每个字段
 * renderer 重复外壳样板。外壳结构：</p>
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
 *
 * <p><b>零 config 依赖</b>：本类只吃 {@link java.lang.String} /
 * {@link ReadableSignal} / {@link Supplier} / {@link FormTheme}，
 * 不感知任何 config 业务类型。caller 负责把 {@code FieldSpec} / {@code DraftSignalAdapter}
 * 拆解为 title / helper / errorSignal / dirtySignal 后传入。</p>
 *
 * <p>外观随状态变化只经 {@code rt.bind/bindComputed} 派生（守 I1/I11/R4），
 * 控件 mount 槽由 caller 以 {@code Supplier<SceneNode>} 注入，本类不建业务控件。</p>
 */
public final class FormFieldShell {

    /** 工具类，禁止实例化 */
    private FormFieldShell() {
    }

    /**
     * 构建字段外壳并挂载控件。
     *
     * @param rt          场景运行时
     * @param title       字段标题（caller 已做回退，例如 label 为空时回退 path）
     * @param helper      帮助文本，{@code null} 或空串时不渲染 helper 区
     * @param errorSignal 错误文案 signal（{@code null} 文案视为无错误）
     * @param dirtySignal 脏态 signal
     * @param controlFn   控件构建函数（{@code SceneXxx.create(rt, props)} 产物）
     * @param theme       主题 token
     * @return 字段卡片节点（已挂载控件 + error 文本）
     */
    public static SceneNode build(SceneRuntime rt, String title, String helper,
                                  ReadableSignal<String> errorSignal, ReadableSignal<Boolean> dirtySignal,
                                  Supplier<SceneNode> controlFn, FormTheme theme) {
        SceneNode card = SceneNode.column();
        card.setBackgroundColor(theme.cardBg());
        card.setBorderWidth(1);
        card.setCornerRadius(theme.cardRadius());
        card.setPadding(theme.cardPad());
        card.setGap(theme.fieldGap());

        // 边框色由 error / dirty 派生：error > dirty > default
        rt.bindComputed(() -> resolveCardBorder(errorSignal.get(), dirtySignal.get(), theme),
                card::setBorderColor);

        // header：状态圆点 + 标题
        SceneNode header = SceneNode.row();
        header.setGap(theme.fieldGap());
        SceneNode dot = text("●", theme.mutedColor(), theme.fontLabel());
        // dot 三态：error 优先 > dirty > normal（修正旧逻辑 dirty+error 同时为真时显示蓝的小不一致）
        rt.bindComputed(() -> {
                    if (!safe(errorSignal.get()).isEmpty()) {
                        return theme.errorColor();
                    }
                    if (Boolean.TRUE.equals(dirtySignal.get())) {
                        return theme.dirtyColor();
                    }
                    return theme.mutedColor();
                },
                dot::setTextColor);
        SceneNode titleNode = text(safe(title), theme.textColor(), theme.fontLabel());
        header.appendChild(dot);
        header.appendChild(titleNode);
        card.appendChild(header);

        // helper 文本
        if (helper != null && !helper.isEmpty()) {
            card.appendChild(text(helper, theme.mutedColor(), theme.fontHelper()));
        }

        // 控件 mount 槽
        MountHandle handle = rt.mount(card, controlFn);
        SceneNode controlRoot = handle.getRoot();
        if (controlRoot != null) {
            controlRoot.setPreferredHeight(theme.inputHeight());
        }

        // error 文本
        SceneNode errorNode = text("", theme.errorColor(), theme.fontError());
        rt.bind(errorSignal, errorNode::setText);
        rt.bindComputed(() -> safe(errorSignal.get()).isEmpty() ? theme.mutedColor()
                        : theme.errorColor(),
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
     * @param theme 主题 token
     * @return 边框色
     */
    private static int resolveCardBorder(String error, Boolean dirty, FormTheme theme) {
        if (!safe(error).isEmpty()) {
            return theme.cardBorderError();
        }
        if (Boolean.TRUE.equals(dirty)) {
            return theme.cardBorderDirty();
        }
        return theme.cardBorder();
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
}
