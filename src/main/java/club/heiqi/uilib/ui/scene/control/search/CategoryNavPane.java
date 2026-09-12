package club.heiqi.uilib.ui.scene.control.search;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p><b>每一行是真正的 pill 行</b>：消费 {@link SceneThemes#selectableSurface} 的
 * {@link SceneTheme.Role#INDICATOR} 配方状态档（未选中取角色极淡 idle tint，hover/pressed 取角色
 * 对应档，选中把 tint RGB 换成主题强调色、强度取主题统一选中强度，禁用仍走角色禁用档；
 * 优先级 disabled &gt; pressed &gt; hovered &gt; idle，契约 §2.5），再经本地字段级覆盖得到行配方
 * （见 {@link #rowSurface}）。行有真实形状：圆角/描边取角色配方（PILL_RADIUS / 1px，渲染层按
 * 盒子夹取，见渲染层 {@code UiRoundedRectGeometry.clampCornerRadius}），左右内缩与行间间距由
 * 生效字号派生（{@link PickerChrome#navRowInset} / {@link PickerChrome#navRowGap}）。
 * 属性归属：background/border/borderWidth/cornerRadius/backdrop/surfaceElevation 六项由
 * {@link SceneSurfaceBinder} 独占——与底座走同一条「配方 → 属性」通道；{@code backdrop} 显式关闭 ⇒
 * <b>行仍是零 BACKDROP 采样</b>（整族滤镜只由导航底座那一颗承担），{@code reliefDisabled} 让行恒走
 * 普通绘制路径（不消费四态 elevation）。虚拟化重绑时行状态全部由该行自己的信号重派生，
 * 不与底座竞争属性槽。</p>
 *
 * <p><b>hover/pressed 可见度（量化口径）</b>：角色配方的 hover tint 只比 idle 高 {@code 0x06}、
 * pressed 甚至比 idle 更淡，在「满宽 + 无圆角 + 无描边」的旧形态下几乎读不出。本组件不做全局
 * 主题调参（超出本控件范围），只把 tint 强度步进放大为 {@code +0x18}（hover）/ {@code +0x2C}
 * （pressed），缘色沿用主题 hovered（{@code 0x40 → 0x80}，2×）/ pressed 档（回落 = 「按下去」）。
 * 同屏可辨判据：hover − idle tint Δalpha ≥ {@code 0x12}（≥7.1pp）、缘 Δalpha ≥ {@code 0x40}；
 * pressed − hovered tint Δalpha ≥ {@code 0x10} 且缘色反向回落。选中行<b>不覆盖</b>：选中已是
 * 0x59 强调填充，色彩语义本身足够区分（{@link SceneThemes#selectableSurface} 原档保持不变）。</p>
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

    /**
     * hover 态 tint 强度步进（相对<b>主题 idle alpha</b> 的增量，单位 = alpha 0..255）。
     *
     * <p>不是颜色值：行配方在派生期读主题当前 idle alpha 再叠加本增量，故主题换档（深/浅/无滤镜）
     * 时行跟着变，无需第二份失效通道。失效条件：只有「本控件外观阶梯重新调参」时才改这个数
     * （届时 CategoryNavPaneTest 的量化判据同步更新）；它不随字号/密度/主题变化。</p>
     */
    private static final int NAV_ROW_HOVER_TINT_STEP = 0x18;

    /**
     * pressed 态 tint 强度步进（相对主题 idle alpha 的增量）：主题 pressed 档比 idle <b>更淡</b>
     * （不会读成「按下去」），本地改为明显强于 hovered 的填充，配合缘色回落表达按下。失效条件同上。
     */
    private static final int NAV_ROW_PRESS_TINT_STEP = 0x2C;

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

        // 行内缩（pill 左右留白）：同一个派生量同时喂给行 margin 与标题/状态行/空态 padding，
        // 文案与行标签左对齐不靠第二份魔数；有度量通道时随生效字号重派生。
        ReadableSignal<Integer> rowInset = rowInsetSignal(props.metrics());

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
                    props.widthPx(), rowInset);
            nav.appendChild(header);
        }
        nav.appendChild(sc.container());
        if (props.statusText() != null) {
            SceneNode status = navText(rt, "", foreground, secondaryForeground, true,
                    props.widthPx(), rowInset);
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
        // 行键 → 当前行快照 的响应式索引：行节点按 identityKey 复用（keyed reconcile），行内文本必须按
        // <b>当前</b>行快照解析 —— 否则同一行键的 count/label 换代刷不上屏（构建期 row 是旧代）。
        // rows 换代时 O(rows) 建一次索引、行内查找 O(1)；不可变 Map 按值记忆化 ⇒ 内容不变不向下游传播。
        ReadableSignal<Map<String, ScenePickerPanelNav.CategoryRow>> rowIndex = rowIndex(props);
        rt.forEach(rows, props.rows(), ScenePickerPanelNav.CategoryRow::identityKey,
                row -> categoryRow(rt, props, row, rowIndex, labelForeground, secondaryForeground,
                        viewport, sc.scrollSignal()));

        rt.show(viewport,
                Computed.create(() -> Boolean.valueOf(props.rows().get().isEmpty())),
                () -> emptyLabel(rt, props.emptyLabel(), secondaryForeground, rowInset));

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
     * @param insetPx          pill 行内缩派生信号（标题/状态行沿用同一内缩 ⇒ 与行标签左对齐）
     * @return 文字节点
     */
    private static SceneNode navText(SceneRuntime rt, String value,
                                     ReadableSignal<Integer> foreground,
                                     ReadableSignal<Integer> secondaryForeground, boolean muted,
                                     ReadableSignal<Integer> widthPx,
                                     ReadableSignal<Integer> insetPx) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setHitTestable(false);
        node.setFallbackFontSize(FONT_SIZE);
        node.setMaxLines(1);
        node.setEllipsis(true);
        rt.bind(muted ? secondaryForeground : foreground, node::setTextColor);
        // 文案宽预算 = 导航宽 - 左右内边距 - 行内缩（与导航宽同源；未给派生宽度时用常量兜底）。
        ReadableSignal<Integer> width = widthPx == null
                ? () -> Integer.valueOf(NAV_WIDTH) : widthPx;
        rt.bindComputed(() -> Integer.valueOf(Math.max(1,
                        width.get().intValue() - 2 * (SceneChromeTokens.PAD_MD
                                + insetPx.get().intValue()))),
                node::setMaxTextWidth);
        // 左右留白 = 行内缩 + 行内文本内边距 ⇒ 标题/状态行文案与行标签同一 x 起点（对齐只此一份真值）。
        applyTextInset(node, insetPx.get().intValue());
        rt.bind(insetPx, inset -> Effect.untrack(() -> applyTextInset(node, inset.intValue())));
        node.setPreferredHeight(rt.lineHeight(FONT_SIZE) + 2 * SceneChromeTokens.PAD_SM);
        return node;
    }

    /** 文本节点的内缩留白：左右同值（与 pill 行内缩同一派生量），垂直沿用既有语义。 */
    private static void applyTextInset(SceneNode node, int insetPx) {
        int horizontal = SceneChromeTokens.PAD_MD + Math.max(0, insetPx);
        node.setPadding(0, horizontal, 0, horizontal);
    }

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

    /**
     * 行键 → 当前行快照 的响应式索引（{@code identityKey} → {@link ScenePickerPanelNav.CategoryRow}）。
     *
     * <p>行节点按行键复用（keyed reconcile 的既有语义），行内文本不能读构建期快照；本索引是
     * 「当前行内容」的唯一读取口：rows 换代时 O(rows) 建一次，行内查找 O(1)。返回不可变 Map ⇒
     * 内容相同即 {@link Computed} 按值记忆化，不向下游传播、不触任何节点写入。</p>
     */
    private static ReadableSignal<Map<String, ScenePickerPanelNav.CategoryRow>> rowIndex(Props props) {
        return Computed.create(() -> {
            List<ScenePickerPanelNav.CategoryRow> current = props.rows().get();
            Map<String, ScenePickerPanelNav.CategoryRow> index =
                    new LinkedHashMap<String, ScenePickerPanelNav.CategoryRow>(
                            Math.max(4, current.size() * 2));
            for (ScenePickerPanelNav.CategoryRow row : current) {
                index.put(row.identityKey(), row);
            }
            return Collections.unmodifiableMap(index);
        });
    }

    /** 行内文本投影（标签 + 徽章计数文本）：值语义 ⇒ 内容不变即记忆化，不写节点。 */
    @Desugar
    private record RowText(String label, String countText) { }

    /**
     * 单分类行（pill）：INDICATOR 派生表面（{@link #bindRowSurface}）+ 标签(flexGrow) + 数量徽章，
     * 点击回调 onSelect；行几何（行高/内缩/行间间距）由生效字号派生。
     */
    private static SceneNode categoryRow(SceneRuntime rt, Props props,
                                         ScenePickerPanelNav.CategoryRow row,
                                         ReadableSignal<Map<String, ScenePickerPanelNav.CategoryRow>> rowIndex,
                                         ReadableSignal<Integer> labelForeground,
                                         ReadableSignal<Integer> secondaryForeground,
                                         SceneNode viewport,
                                         Signal<Integer> scrollSignal) {
        SceneNode rowNode = SceneNode.row();
        ReadableSignal<PickerMetrics> metrics = props.metrics();
        // 行高 / 内缩 / 行间间距同源派生：同一份 PickerMetrics 字号，缺度量通道时回落组件常量口径。
        int fontSizePx = metrics == null ? FONT_SIZE : metrics.get().fontSizePx();
        rowNode.setPreferredHeight(metrics == null ? ROW_HEIGHT : PickerChrome.navRowHeight(fontSizePx));
        applyPillGeometry(rowNode, fontSizePx);
        if (metrics != null) {
            rt.bind(metrics, m -> Effect.untrack(() -> {
                int fs = m.fontSizePx();
                rowNode.setPreferredHeight(PickerChrome.navRowHeight(fs));
                applyPillGeometry(rowNode, fs);
            }));
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
        bindRowSurface(rt, props.enabled(), selected, interaction, rowNode);

        SceneNode label = new SceneNode();
        label.setFlexGrow(1);
        // 私有常量降级为层 4a 回落值（有声明时跟随作用域，无声明时仍落 12）。
        label.setFallbackFontSize(FONT_SIZE);
        label.setHitTestable(false);
        // 构建期初值（首帧前同步读也有值）；真正的内容由下方 RowText 投影按当前行快照驱动。
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

        // 标签 + 徽章按「当前行快照」实时解析：行节点按 identityKey 复用，构建期 row 在行键不变而
        // count/label 换代时是旧代 —— 只读构建期快照会把旧文案永久留在屏上（与 SearchResultList
        // 单元标签同族）。单个 RowText 投影 + 单个绑定：值语义记录按值记忆化 ⇒ 内容不变时零节点写入
        // （SceneNode.setText 亦同值早退）；无命中键（行正在被回收）回落构建期快照，文本永不为 null、
        // 也不残留上一项的文案。
        ReadableSignal<RowText> rowText = Computed.create(() -> {
            ScenePickerPanelNav.CategoryRow live = rowIndex.get().get(row.identityKey());
            ScenePickerPanelNav.CategoryRow current = live == null ? row : live;
            return new RowText(current.label(), String.valueOf(current.count()));
        });
        rt.bind(rowText, text -> {
            label.setText(text.label());
            badge.setText(text.countText());
        });

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

    /** pill 行几何：左右内缩（不贴栏边）+ 行间纵向间距（相邻 pill 不粘连），全部由生效字号派生。 */
    private static void applyPillGeometry(SceneNode rowNode, int fontSizePx) {
        int inset = PickerChrome.navRowInset(fontSizePx);
        rowNode.setMargin(0, inset, PickerChrome.navRowGap(fontSizePx), inset);
    }

    /**
     * 行内缩派生信号：有度量通道时随生效字号重派生（字号倍率 / 密度档变化 ⇒ 内缩跟着变），
     * 否则按组件回落字号取常量缺省（与 {@link #ROW_HEIGHT} 同源的旧口径）。
     */
    private static ReadableSignal<Integer> rowInsetSignal(ReadableSignal<PickerMetrics> metrics) {
        if (metrics == null) {
            return () -> Integer.valueOf(PickerChrome.navRowInset(FONT_SIZE));
        }
        return Computed.create(Integer.valueOf(PickerChrome.navRowInset(metrics.get().fontSizePx())),
                () -> Integer.valueOf(PickerChrome.navRowInset(metrics.get().fontSizePx())));
    }

    /**
     * 绑定行表面：{@link SceneThemes#selectableSurface}(INDICATOR, selected) 提供「主题基线 +
     * 选中语义」，再经 {@link #rowSurface} 做字段级覆盖（只在非选中档放大 tint 步进、关滤镜、关浮雕），
     * 最后交给 {@link SceneSurfaceBinder} 独占六项写入 —— 行与底座共用同一条「配方 → 属性」通道，
     * 行的状态优先级（disabled &gt; pressed &gt; hovered &gt; idle）由绑定器按契约 §2.5 执行。
     *
     * <p><b>为什么不是 {@code SceneThemes.derivedSurface}</b>：derivedSurface 从 {@code surface(role)}
     * 起算，拿不到 {@code selectableSurface} 的「选中 = 主题 accent 0x59」语义；而选中强度
     * {@code SELECTED_TINT_ALPHA} 是主题私有真值，自带一份等于复制主题。故本组件保持
     * 「selectableSurface + 局部纯函数覆盖（{@code Computed}）」的组合：两条失效源（主题、选中信号）
     * 都由 Computed 承担，覆盖函数本身无状态。</p>
     */
    private static void bindRowSurface(SceneRuntime rt, ReadableSignal<Boolean> enabled,
                                       ReadableSignal<Boolean> selected,
                                       SceneInteractionState interaction, SceneNode rowNode) {
        ReadableSignal<SceneSurfaceStyle> selectable =
                SceneThemes.selectableSurface(rt, SceneTheme.Role.INDICATOR, selected);
        SceneSurfaceStyle initial = rowSurface(selectable.get(), Boolean.TRUE.equals(selected.get()));
        ReadableSignal<SceneSurfaceStyle> recipe = Computed.create(initial,
                () -> rowSurface(selectable.get(), Boolean.TRUE.equals(selected.get())));
        SceneSurfaceBinder.bind(rt, rowNode, recipe, enabled, interaction);
    }

    /**
     * 行配方 = 主题基线 + 本地字段级覆盖（纯函数，可在派生 effect 内执行）。
     *
     * <ul>
     *   <li>{@code backdrop(null)}：行不装滤镜 ⇒ <b>零新增 BACKDROP 采样</b>（行 tint 直接叠在
     *       底座玻璃上；无滤镜档下底座本身不透明可读）；</li>
     *   <li>{@code reliefDisabled(true)}：恒走普通绘制路径（不消费四态 elevation）；</li>
     *   <li>非选中档只改 hovered/pressed 的 tint 强度（缘色、elevation、lens 全取主题对应档）；</li>
     *   <li>选中档<b>原样保留</b> selectableSurface 的四个状态档（0x59 强调语义不经本方法改写）。</li>
     * </ul>
     */
    private static SceneSurfaceStyle rowSurface(SceneSurfaceStyle base, boolean selected) {
        SceneSurfaceStyle.Builder builder = base.toBuilder()
                .backdrop(null)
                .reliefDisabled(true);
        if (!selected) {
            builder.hovered(steppedTint(base.getIdle(), base.getHovered(), NAV_ROW_HOVER_TINT_STEP))
                    .pressed(steppedTint(base.getIdle(), base.getPressed(), NAV_ROW_PRESS_TINT_STEP));
        }
        return builder.build();
    }

    /**
     * 状态档 tint 步进：取主题对应档的 RGB / 缘 / elevation / lens，只把 tint alpha 换成
     * {@code 主题 idle alpha + 增量}。主题换档 ⇒ 派生期重算（本方法读的是主题当前基线，不是快照）。
     */
    private static SceneSurfaceStyle.StateStyle steppedTint(SceneSurfaceStyle.StateStyle idle,
                                                            SceneSurfaceStyle.StateStyle target,
                                                            int alphaStep) {
        return new SceneSurfaceStyle.StateStyle(
                withAlpha(target.getTint(), alphaOf(idle.getTint()) + alphaStep),
                target.getEdge(), target.getElevation(), target.getLensFactor());
    }

    /** 保留色 RGB、替换 alpha（0..255 夹取）。强度调整统一经此，控件内不出现色字面量。 */
    private static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(0xFF, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    /** 取 alpha 通道。 */
    private static int alphaOf(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    /** 空态/提示留白：垂直沿用既有 PAD_MD，左右 = 行内缩 + 文本内边距（与行标签同一 x 起点）。 */
    private static void applyEmptyInset(SceneNode node, int insetPx) {
        int horizontal = SceneChromeTokens.PAD_MD + Math.max(0, insetPx);
        node.setPadding(SceneChromeTokens.PAD_MD, horizontal, SceneChromeTokens.PAD_MD, horizontal);
    }

    /**
     * 空分类提示节点：emptyLabel 或兜底文案；次要前景（muted，禁用取 disabledForeground）经主题派生。
     * 左右留白与行标签同源（pill 内缩 + 行内文本内边距），空态与有行状态不跳位。
     */
    private static SceneNode emptyLabel(SceneRuntime rt, String value,
                                        ReadableSignal<Integer> secondaryForeground,
                                        ReadableSignal<Integer> insetPx) {
        SceneNode node = new SceneNode();
        node.setText(value == null || value.isEmpty() ? DEFAULT_EMPTY_LABEL : value);
        node.setFallbackFontSize(FONT_SIZE);
        node.setHitTestable(false);
        rt.bind(secondaryForeground, node::setTextColor);
        int inset = insetPx.get().intValue();
        applyEmptyInset(node, inset);
        rt.bind(insetPx, next -> Effect.untrack(() -> applyEmptyInset(node, next.intValue())));
        return node;
    }
}
