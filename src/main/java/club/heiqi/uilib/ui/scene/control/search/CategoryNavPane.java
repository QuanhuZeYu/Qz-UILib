package club.heiqi.uilib.ui.scene.control.search;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneControlChrome;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanelNav;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;

/**
 * CategoryNavPane —— 分类导航面板（实底圆角外壳 + 内嵌滚动视口 + 可选高亮行）。
 *
 * <h3>定位</h3>
 * <p>左栏竖向分类列表：首行恒为「全部」（key 为 {@code null}），其后按 rows 声明顺序排列。
 * 每行 = 选中高亮背景（{@link SceneControlChrome#bindSelectableBackground}）+ 标签 + 数量徽章；
 * 点击某行回调 {@code onSelect}（选「全部」时 accept(null)）。外壳实底圆角，绝不使用边框。</p>
 *
 * <h3>生命周期</h3>
 * <p>全部 bind/forEach/on/interactionState/show 均在 {@code create()} 调用者 Owner 作用域内注册，
 * 卸载随组件回收。</p>
 *
 * <h3>空态</h3>
 * <p>rows 为空时经 {@code show} 渲染空提示（emptyLabel 或「暂无分类」兜底）。</p>
 */
public final class CategoryNavPane {

    /** 导航面板宽度（像素）。 */
    public static final int NAV_WIDTH = 168;
    /** 单行高度（像素）。 */
    public static final int ROW_HEIGHT = 32;
    /** 行标签/徽章字号（像素）。 */
    public static final int FONT_SIZE = 12;
    /** 空分类兜底文案。 */
    public static final String DEFAULT_EMPTY_LABEL = "暂无分类";

    /** 纯静态组件工厂，禁止实例化。 */
    private CategoryNavPane() { }

    /** 分类导航面板输入契约。 */
    @Desugar
    public record Props(
            ReadableSignal<? extends List<ScenePickerPanelNav.CategoryRow>> rows,
            ReadableSignal<String> categoryKey,
            ReadableSignal<Boolean> enabled,
            Consumer<String> onSelect,
            String emptyLabel) {

        /** 显式校验构造器：rows / categoryKey / enabled / onSelect 非 null。 */
        public Props {
            Objects.requireNonNull(rows, "rows");
            Objects.requireNonNull(categoryKey, "categoryKey");
            Objects.requireNonNull(enabled, "enabled");
            Objects.requireNonNull(onSelect, "onSelect");
        }
    }

    /**
     * 创建分类导航面板。
     *
     * @param rt    场景运行时
     * @param props 导航面板属性
     * @return 导航面板根节点
     */
    public static SceneNode create(SceneRuntime rt, Props props) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(props, "props");

        SceneNode nav = SceneNode.column();
        nav.setPreferredWidth(NAV_WIDTH);
        nav.setFillParentHeight(true);
        nav.setBackgroundColor(SceneChromeTokens.BG_DEFAULT);
        nav.setCornerRadius(SceneChromeTokens.RADIUS_MD);
        nav.setPadding(1, 1, 1, 1);
        nav.setClipChildren(true);
        nav.setHitTestable(false);

        SceneNode viewport = SceneNode.column();
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        viewport.setFillParentHeight(true);
        viewport.setHitTestable(false);
        SceneScrolls.attach(rt, viewport);
        nav.appendChild(viewport);

        SceneNode rows = SceneNode.column();
        rows.setHitTestable(false);
        viewport.appendChild(rows);

        rt.forEach(rows, props.rows(), ScenePickerPanelNav.CategoryRow::identityKey,
                row -> categoryRow(rt, props, row));

        rt.show(viewport,
                Computed.create(() -> Boolean.valueOf(props.rows().get().isEmpty())),
                () -> emptyLabel(props.emptyLabel()));

        return nav;
    }

    /** 单分类行：选中高亮背景 + 标签(flexGrow) + 数量徽章，点击回调 onSelect。 */
    private static SceneNode categoryRow(SceneRuntime rt, Props props,
                                         ScenePickerPanelNav.CategoryRow row) {
        SceneNode rowNode = SceneNode.row();
        rowNode.setPreferredHeight(ROW_HEIGHT);
        rowNode.setCrossAxisAlign(CrossAxisAlign.CENTER);
        rowNode.setGap(SceneChromeTokens.GAP_SM);
        rowNode.setPadding(0, SceneChromeTokens.PAD_MD, 0, SceneChromeTokens.PAD_MD);

        SceneInteractionState interaction = rt.interactionState(rowNode);
        ReadableSignal<Boolean> selected = Computed.create(() -> {
            String current = props.categoryKey().get();
            boolean currentAll = current == null || current.isEmpty();
            return Boolean.valueOf(row.all() ? currentAll : row.key().equals(current));
        });
        SceneControlChrome.bindSelectableBackground(rt, rowNode, props.enabled(), selected, interaction);

        SceneNode label = new SceneNode();
        label.setFlexGrow(1);
        label.setFontSize(FONT_SIZE);
        label.setTextColor(SceneChromeTokens.TEXT_SECONDARY);
        label.setHitTestable(false);
        label.setText(row.label());
        rowNode.appendChild(label);

        SceneNode badge = new SceneNode();
        badge.setFontSize(FONT_SIZE);
        badge.setTextColor(SceneChromeTokens.TEXT_SECONDARY);
        badge.setHitTestable(false);
        badge.setText(String.valueOf(row.count()));
        rowNode.appendChild(badge);

        rt.on(rowNode, SceneEventType.CLICK, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) return;
            props.onSelect().accept(row.all() ? null : row.key());
            ctx.stopPropagation();
        });

        return rowNode;
    }

    /** 空分类提示节点：emptyLabel 或兜底文案。 */
    private static SceneNode emptyLabel(String value) {
        SceneNode node = new SceneNode();
        node.setText(value == null || value.isEmpty() ? DEFAULT_EMPTY_LABEL : value);
        node.setPadding(SceneChromeTokens.PAD_MD);
        node.setFontSize(FONT_SIZE);
        node.setTextColor(SceneChromeTokens.TEXT_SECONDARY);
        node.setHitTestable(false);
        return node;
    }
}
