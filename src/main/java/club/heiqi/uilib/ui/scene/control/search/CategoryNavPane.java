package club.heiqi.uilib.ui.scene.control.search;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanelNav;
import club.heiqi.uilib.ui.scene.control.SceneScrollContainer;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * CategoryNavPane —— 分类导航面板（导航族底座 + 内嵌滚动视口 + 轻量选中行）。
 *
 * <h3>定位</h3>
 * <p>左栏竖向分类列表：首行恒为「全部」（key 为 {@code null}），其后按 rows 声明顺序排列。
 * 每行 = 轻量状态覆盖 + 标签 + 数量徽章；点击某行回调 {@code onSelect}（选「全部」时 accept(null)）。
 * 布局常量、行结构与命中策略不变，默认外观全部交还主题。</p>
 *
 * <h3>外观归属（液态玻璃迁移，导航族口径，契约 §4.1）</h3>
 * <p><b>底座</b>（nav 根节点）走 {@link SceneThemes#surface} 的 {@link SceneTheme.Role#TOOLBAR} 配方：
 * background/border/borderWidth/cornerRadius/backdrop/surfaceElevation 六项由
 * {@link SceneSurfaceBinder} 独占；旧 {@code SceneChromeTokens.applyPanelChrome} 静态四件套写入者
 * 已删除。clipChildren 不属于绑定器六项属性，仍由本组件自持（保持原外壳裁剪语义）。</p>
 *
 * <p><b>每一行</b>消费 {@link SceneThemes#selectableSurface} 的 {@link SceneTheme.Role#INDICATOR}
 * 配方状态档：未选中取角色极淡 idle tint，hover/pressed 取角色对应档，选中把 tint RGB 换成主题
 * 强调色（强度取主题统一选中强度，选中是色彩语义、不只靠透明度），禁用仍走角色禁用档
 * （优先级 disabled &gt; pressed &gt; hovered &gt; idle，契约 §2.5）。行只做<b>只写
 * {@code backgroundColor} 一个属性的轻量覆盖</b>（与 SimpleList/DataTable 轻量行同构）：
 * 行不装滤镜、不写边框/圆角/实体高度，故虚拟化重绑时行状态全部由该行自己的信号重派生，
 * 不与底座竞争属性槽，也不给整树新增 BACKDROP 采样。</p>
 *
 * <p><b>文字</b>：行标签启用取 {@link SceneThemes#foreground}（选中行同色，不取
 * {@code onAccentForeground}——选中底是叠在玻璃上的半透明染色中间调，参照已验收 SceneNavList
 * 的 WCAG 实算裁决），数量徽章与空态提示属次要信息取 {@link SceneThemes#mutedForeground}，
 * 禁用时一律取 {@link SceneThemes#disabledForeground}；旧 {@code TEXT_SECONDARY} 静态写入已删除。</p>
 *
 * <h3>生命周期</h3>
 * <p>全部 bind/forEach/on/interactionState/show 均在 {@code create()} 调用者 Owner 作用域内注册，
 * 卸载随组件回收；主题切换只重派生外观，不重建节点。</p>
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

    /** 恒真 enabled：底座自身没有禁用语义，外观只走 idle/hovered/pressed 三档。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;

    /** 纯静态组件工厂，禁止实例化。 */
    private CategoryNavPane() { }

    /** 分类导航面板输入契约。 */
    @Desugar
    public record Props(
            ReadableSignal<? extends List<ScenePickerPanelNav.CategoryRow>> rows,
            ReadableSignal<String> categoryKey,
            ReadableSignal<Boolean> enabled,
            Consumer<String> onSelect,
            String emptyLabel,
            ReadableSignal<Integer> widthPx,
            String title,
            ReadableSignal<String> statusText,
            ReadableSignal<PickerMetrics> metrics) {

        /** 显式校验构造器：rows / categoryKey / enabled / onSelect 非 null。 */
        public Props {
            Objects.requireNonNull(rows, "rows");
            Objects.requireNonNull(categoryKey, "categoryKey");
            Objects.requireNonNull(enabled, "enabled");
            Objects.requireNonNull(onSelect, "onSelect");
        }

        /**
         * 旧 6 参形态（P5 兼容，纯加法保留）：无标题与状态行。
         *
         * @param rows        分类行
         * @param categoryKey 当前分类 key
         * @param enabled     是否启用
         * @param onSelect    选择回调
         * @param emptyLabel  空态文案
         * @param widthPx     派生宽度信号（可 null）
         */
        public Props(ReadableSignal<? extends List<ScenePickerPanelNav.CategoryRow>> rows,
                     ReadableSignal<String> categoryKey, ReadableSignal<Boolean> enabled,
                     Consumer<String> onSelect, String emptyLabel,
                     ReadableSignal<Integer> widthPx) {
            this(rows, categoryKey, enabled, onSelect, emptyLabel, widthPx, null, null, null);
        }

        /**
         * 6 参 + 文案形态（P5 兼容，纯加法保留）：行高仍取 {@link #ROW_HEIGHT} 常量。
         *
         * @param rows         分类行
         * @param categoryKey  当前分类 key
         * @param enabled      是否启用
         * @param onSelect     选择回调
         * @param emptyLabel   空态文案
         * @param widthPx      派生宽度信号（可 null）
         * @param title        栏标题（可 null/空 = 不渲染）
         * @param statusText   状态行文案信号（可 null = 不渲染）
         */
        public Props(ReadableSignal<? extends List<ScenePickerPanelNav.CategoryRow>> rows,
                     ReadableSignal<String> categoryKey, ReadableSignal<Boolean> enabled,
                     Consumer<String> onSelect, String emptyLabel,
                     ReadableSignal<Integer> widthPx, String title,
                     ReadableSignal<String> statusText) {
            this(rows, categoryKey, enabled, onSelect, emptyLabel, widthPx, title, statusText, null);
        }

        /**
         * 旧 5 参形态（P5 兼容，纯加法保留）：导航宽取 {@link #NAV_WIDTH} 常量。
         *
         * @param rows        分类行
         * @param categoryKey 当前分类 key
         * @param enabled     是否启用
         * @param onSelect    选择回调
         * @param emptyLabel  空态文案
         */
        public Props(ReadableSignal<? extends List<ScenePickerPanelNav.CategoryRow>> rows,
                     ReadableSignal<String> categoryKey, ReadableSignal<Boolean> enabled,
                     Consumer<String> onSelect, String emptyLabel) {
            this(rows, categoryKey, enabled, onSelect, emptyLabel, null);
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
        // 导航宽由 P5 派生度量给（clamp(round(逻辑盒宽 * 0.13), 96, 188)）；未提供信号时回落常量。
        // 读值 null 安全：派生信号可能是「尚未求值的 Computed」（Computed 惰性，构建期 get() 无值），
        // 此时先用常量挂载，首次 flush 后由 bind 写入派生值。
        Integer derivedWidth = props.widthPx() == null ? null : props.widthPx().get();
        nav.setPreferredWidth(derivedWidth == null ? NAV_WIDTH : derivedWidth.intValue());
        if (props.widthPx() != null) {
            rt.bind(props.widthPx(), width -> {
                if (width != null) {
                    nav.setPreferredWidth(width.intValue());
                }
            });
        }
        nav.setFillParentHeight(true);

        // 底座：TOOLBAR 角色配方（导航族口径）。绑定器独占 background/border/borderWidth/
        // cornerRadius/backdrop/surfaceElevation；构造期不再静态设底色/边框/圆角。
        // 时序契约：Router 的 writeHovered/writePressed/writeFocused 对未创建的 signal 直接
        // 短路，故在构建期声明关心，保证后续状态档可用。
        SceneInteractionState baseInteraction = rt.interactionState(nav);
        baseInteraction.hovered();
        baseInteraction.pressed();
        baseInteraction.focused();
        SceneSurfaceBinder.bind(rt, nav, SceneThemes.surface(rt, SceneTheme.Role.TOOLBAR),
                ALWAYS_ENABLED, baseInteraction);
        // clip 不属于绑定器六项属性：保持原 applyPanelChrome 的裁剪语义，由组件自持。
        nav.setClipChildren(true);
        nav.setPadding(1, 1, 1, 1);
        nav.setHitTestable(false);

        // 主题语义前景在构造期捕获一次（来源作用域），所有行与空态共享同一派生信号；
        // 主题切换只重派生，不重建节点。禁用文字口径与 SceneNavList/SceneSegmented 一致。
        ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
        ReadableSignal<Integer> mutedForeground = SceneThemes.mutedForeground(rt);
        ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);
        ReadableSignal<Integer> labelForeground = () -> Boolean.TRUE.equals(props.enabled().get())
                ? foreground.get() : disabledForeground.get();
        ReadableSignal<Integer> secondaryForeground = () -> Boolean.TRUE.equals(props.enabled().get())
                ? mutedForeground.get() : disabledForeground.get();

        // 标准滚动结构走 SceneScrollContainer 工厂（默认滚动条视觉），不再手写样板。
        // P5 第六轮 U-P5-13：滚动条宽度接密度/字号派生（缺度量通道 ⇒ null ⇒ 常量缺省，逐值不变）。
        SceneScrollContainer.Result sc = SceneScrollContainer.createDefault(rt, 0, 0, 0, 0,
                PickerChrome.scrollbarWidthSignal(props.metrics()));
        SceneNode viewport = sc.viewport();
        viewport.setHitTestable(false);

        // 标题 / 状态行（U-P5-1）：由导航配件自持，避免宿主再包一层组合列
        //（导航根是 fillParentHeight 的列，外层再包列会破坏高度契约）。
        // 非空才挂载 ⇒ 未提供文案的调用方（含既有测试装置）结构零变化。
        boolean hasHeader = props.title() != null && !props.title().isEmpty();
        if (hasHeader) {
            SceneNode header = navText(rt, props.title(), foreground, secondaryForeground, false,
                    props.widthPx());
            nav.appendChild(header);
        }
        nav.appendChild(sc.container());
        if (props.statusText() != null) {
            SceneNode status = navText(rt, "", foreground, secondaryForeground, true,
                    props.widthPx());
            rt.bindText(status, props.statusText());
            nav.appendChild(status);
        }
        if (hasHeader || props.statusText() != null) {
            // 滚动区让出标题/状态行后占满剩余高度（不再 fillParentHeight 抢占整列）。
            sc.container().setFillParentHeight(false);
            sc.container().setFlexGrow(1);
        }

        SceneNode rows = sc.content();
        rows.setHitTestable(false);

        // 行高由生效字号派生（P5 §2.5：clamp(round(fs*2.67), 24, 36)）；未提供度量通道时
        // 保留旧常量（既有调用方零变化）。字号与行高经同一份 PickerMetrics，不存在第二真值。
        rt.forEach(rows, props.rows(), ScenePickerPanelNav.CategoryRow::identityKey,
                row -> categoryRow(rt, props, row, labelForeground, secondaryForeground,
                        viewport, sc.scrollSignal()));

        rt.show(viewport,
                Computed.create(() -> Boolean.valueOf(props.rows().get().isEmpty())),
                () -> emptyLabel(rt, props.emptyLabel(), secondaryForeground));

        return nav;
    }

    /**
     * 导航栏标题/状态行文字（单行省略、宽度随导航宽，不参与命中）。
     *
     * @param rt               场景运行时
     * @param value            初始文本（状态行传空串，由 bindText 驱动）
     * @param foreground       正文档前景（标题用）
     * @param secondaryForeground 次要档前景（状态行用）
     * @param muted            是否用次要档
     * @return 文字节点
     */
    private static SceneNode navText(SceneRuntime rt, String value,
                                     ReadableSignal<Integer> foreground,
                                     ReadableSignal<Integer> secondaryForeground, boolean muted,
                                     ReadableSignal<Integer> widthPx) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setHitTestable(false);
        node.setFallbackFontSize(FONT_SIZE);
        node.setMaxLines(1);
        node.setEllipsis(true);
        rt.bind(muted ? secondaryForeground : foreground, node::setTextColor);
        // 文案宽预算 = 导航宽 - 左右内边距（与导航宽同源；未给派生宽度时用常量兜底）。
        ReadableSignal<Integer> width = widthPx == null
                ? () -> Integer.valueOf(NAV_WIDTH) : widthPx;
        rt.bindComputed(() -> Integer.valueOf(Math.max(1,
                        width.get().intValue() - 2 * SceneChromeTokens.PAD_MD)),
                node::setMaxTextWidth);
        node.setPreferredHeight(rt.lineHeight(FONT_SIZE) + 2 * SceneChromeTokens.PAD_SM);
        return node;
    }

    /** 单分类行：INDICATOR 轻量选中覆盖 + 标签(flexGrow) + 数量徽章，点击回调 onSelect。 */
    /**
     * 键盘导航的滚动可见性：焦点行超出导航视口时按最小位移滚动（不改变居中语义、
     * 不引入第二份滚动事实 —— 复用视口自身的 scroll signal 与 {@code maxScrollY}）。
     *
     * @param target       目标行
     * @param viewport     导航滚动视口（可为 null）
     * @param scrollSignal 视口滚动偏移信号（可为 null = 不做滚动）
     */
    private static void scrollIntoView(SceneNode target, SceneNode viewport, Signal<Integer> scrollSignal) {
        if (viewport == null || scrollSignal == null) {
            return;
        }
        AnchorRect targetBox = SceneGeometry.absoluteBox(target, 0, 0);
        AnchorRect viewportBox = SceneGeometry.absoluteBox(viewport, 0, 0);
        int delta = 0;
        if (targetBox.getY() < viewportBox.getY()) {
            delta = targetBox.getY() - viewportBox.getY();
        } else if (targetBox.getY() + targetBox.getHeight()
                > viewportBox.getY() + viewportBox.getHeight()) {
            delta = targetBox.getY() + targetBox.getHeight()
                    - (viewportBox.getY() + viewportBox.getHeight());
        }
        if (delta == 0) {
            return;
        }
        int max = SceneGeometry.maxScrollY(viewport);
        int next = Math.max(0, Math.min(max, scrollSignal.get().intValue() + delta));
        if (next != scrollSignal.get().intValue()) {
            scrollSignal.set(Integer.valueOf(next));
        }
    }

    private static SceneNode categoryRow(SceneRuntime rt, Props props,
                                         ScenePickerPanelNav.CategoryRow row,
                                         ReadableSignal<Integer> labelForeground,
                                         ReadableSignal<Integer> secondaryForeground,
                                         SceneNode viewport,
                                         Signal<Integer> scrollSignal) {
        SceneNode rowNode = SceneNode.row();
        ReadableSignal<PickerMetrics> metrics = props.metrics();
        rowNode.setPreferredHeight(metrics == null ? ROW_HEIGHT
                : PickerChrome.navRowHeight(metrics.get().fontSizePx()));
        if (metrics != null) {
            rt.bind(metrics, m -> Effect.untrack(() ->
                    rowNode.setPreferredHeight(PickerChrome.navRowHeight(m.fontSizePx()))));
        }
        rowNode.setCrossAxisAlign(CrossAxisAlign.CENTER);
        rowNode.setGap(SceneChromeTokens.GAP_SM);
        rowNode.setPadding(0, SceneChromeTokens.PAD_MD, 0, SceneChromeTokens.PAD_MD);

        SceneInteractionState interaction = rt.interactionState(rowNode);
        // 时序契约：构造期声明关心，Router 后续写入才会落到已创建的 signal。
        interaction.hovered();
        interaction.pressed();

        ReadableSignal<Boolean> selected = Computed.create(() -> {
            String current = props.categoryKey().get();
            boolean currentAll = current == null || current.isEmpty();
            return Boolean.valueOf(row.all() ? currentAll : row.key().equals(current));
        });
        bindRowTint(rt, props.enabled(), selected, interaction, rowNode);

        SceneNode label = new SceneNode();
        label.setFlexGrow(1);
        // 私有常量降级为层 4a 回落值（有声明时跟随作用域，无声明时仍落 12）。
        label.setFallbackFontSize(FONT_SIZE);
        label.setHitTestable(false);
        label.setText(row.label());
        rt.bind(labelForeground, label::setTextColor);
        rowNode.appendChild(label);

        SceneNode badge = new SceneNode();
        // 计数徽标：与行标签同一默认字号口径，同样降级为层 4a 回落值。
        badge.setFallbackFontSize(FONT_SIZE);
        badge.setHitTestable(false);
        badge.setText(String.valueOf(row.count()));
        rt.bind(secondaryForeground, badge::setTextColor);
        rowNode.appendChild(badge);

        rt.on(rowNode, SceneEventType.CLICK, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) return;
            props.onSelect().accept(row.all() ? null : row.key());
            ctx.stopPropagation();
        });

        // C4（P5 §5.3）：分类导航键盘化 —— 行可聚焦，↑↓ 移焦点、HOME/END 首末、ENTER/SPACE 切换。
        // 焦点只在行之间移动（不跨出导航列），切换走与点击同一个 onSelect 单点。
        rt.focusable(rowNode, props.enabled());
        rt.on(rowNode, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())
                    || ev.getKeyAction() != SceneKeyAction.PRESSED) {
                return;
            }
            SceneKey key = ev.getKey();
            if (key == SceneKey.ENTER || key == SceneKey.SPACE) {
                ctx.stopPropagation();
                props.onSelect().accept(row.all() ? null : row.key());
                return;
            }
            boolean up = key == SceneKey.ARROW_UP;
            boolean down = key == SceneKey.ARROW_DOWN;
            boolean home = key == SceneKey.HOME;
            boolean end = key == SceneKey.END;
            if (!up && !down && !home && !end) {
                return;
            }
            SceneNode parent = rowNode.__getParent();
            if (parent == null) {
                return;
            }
            List<SceneNode> siblings = parent.__getChildren();
            int index = siblings.indexOf(rowNode);
            if (index < 0 || siblings.isEmpty()) {
                return;
            }
            int next = home ? 0
                    : end ? siblings.size() - 1
                            : Math.max(0, Math.min(siblings.size() - 1, index + (down ? 1 : -1)));
            ctx.stopPropagation();
            SceneNode target = siblings.get(next);
            rt.requestFocus(target);
            scrollIntoView(target, viewport, scrollSignal);
        });

        return rowNode;
    }

    /**
     * 绑定行背景：消费 {@link SceneThemes#selectableSurface}(INDICATOR, selected) 配方的状态档，
     * 按契约 §2.5 优先级 disabled &gt; pressed &gt; hovered &gt; idle 取 tint，只写
     * {@code backgroundColor} 一个属性。
     *
     * <p>行不装滤镜、不写边框/圆角/实体高度（保持普通绘制路径，{@code __getSurfaceElevation()}
     * 恒为 -1），虚拟化重绑时选中/hover/禁用均由各行自己的信号重派生，不残留上一项状态。
     * 全部取值发生在 effect 体内，构造期不解引用未求值的 Computed。</p>
     */
    private static void bindRowTint(SceneRuntime rt, ReadableSignal<Boolean> enabled,
                                    ReadableSignal<Boolean> selected,
                                    SceneInteractionState interaction, SceneNode rowNode) {
        ReadableSignal<SceneSurfaceStyle> recipe =
                SceneThemes.selectableSurface(rt, SceneTheme.Role.INDICATOR, selected);
        rt.__bindAnimatedColor(() -> rowTint(recipe.get(),
                        Boolean.TRUE.equals(enabled.get()),
                        Boolean.TRUE.equals(interaction.pressed().get()),
                        Boolean.TRUE.equals(interaction.hovered().get())),
                rowNode::setBackgroundColor,
                () -> recipe.get().getTransitionMillis());
    }

    /** 行状态档：disabled &gt; pressed &gt; hovered &gt; idle，全部取 INDICATOR 配方档。 */
    private static int rowTint(SceneSurfaceStyle recipe, boolean enabled, boolean pressed, boolean hovered) {
        if (!enabled) {
            return recipe.getDisabled().getTint();
        }
        if (pressed) {
            return recipe.getPressed().getTint();
        }
        if (hovered) {
            return recipe.getHovered().getTint();
        }
        return recipe.getIdle().getTint();
    }

    /** 空分类提示节点：emptyLabel 或兜底文案；次要前景（muted，禁用取 disabledForeground）经主题派生。 */
    private static SceneNode emptyLabel(SceneRuntime rt, String value,
                                        ReadableSignal<Integer> secondaryForeground) {
        SceneNode node = new SceneNode();
        node.setText(value == null || value.isEmpty() ? DEFAULT_EMPTY_LABEL : value);
        node.setPadding(SceneChromeTokens.PAD_MD);
        node.setFallbackFontSize(FONT_SIZE);
        node.setHitTestable(false);
        rt.bind(secondaryForeground, node::setTextColor);
        return node;
    }
}
