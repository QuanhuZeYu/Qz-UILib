package club.heiqi.uilib.ui.scene.control.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneScrollContainer;
import club.heiqi.uilib.ui.scene.control.SceneSegmented;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * VariantChooser —— 变体选择浮层（模块 D）。
 *
 * <h3>定位</h3>
 * <p>把 {@link club.heiqi.uilib.ui.scene.control.ScenePickerPanel} 里的 {@code variantPanel}
 * 抽成独立可复用的受控浮层组件。选择模式与已选 key 采用受控所有权（与网格
 * highlighted/onHighlightChange 同款）：外壳持有 {@link Props#mode()} / {@link Props#selectedKeys()}
 * 的 signal 真值，本模块只读并通过 {@link Props#onModeChange()} / {@link Props#onKeysChange()}
 * 上抛期望新值；过滤查询文本保持模块内部状态，浮层打开时清空。</p>
 *
 * <h3>浮层结构</h3>
 * <pre>
 * scrim (ROW, 全屏遮罩 + 居中)
 *   └─ card (COLUMN, OVERLAY 玻璃浮层面板)
 *        ├─ header (标题 + 候选名)
 *        ├─ search (查询输入, 前缀过滤)
 *        ├─ segmented (ALL / SELECTED 模式切换)
 *        ├─ list (滚动视口, 勾选行列表)
 *        └─ footer (取消 / 确认)
 * </pre>
 *
 * <h3>外观归属（液态玻璃迁移，G13 虚拟化复用行口径，契约 §4.1）</h3>
 * <p><b>浮层面板</b>（card）走 {@link SceneThemes#surface} 的 {@link SceneTheme.Role#OVERLAY}
 * 配方：background/border/borderWidth/cornerRadius/backdrop/surfaceElevation 六项由
 * {@link SceneSurfaceBinder} 独占，面板自身恰装一颗滤镜；旧 {@code SceneChromeTokens.applyPanelChrome}
 * 实色四件套写入者已删除。clip 不属于绑定器六项属性，仍由本组件自持。scrim 是全屏遮罩，
 * 只负责遮罩（契约 §4.1「遮罩只负责遮罩」），保持静态半透明底、不装玻璃。卡片内部的查询输入、
 * 分段、按钮是已主题化的独立控件实例（G04/G09/G03），各自的表面归其自身，与本面板表面互不竞争。</p>
 *
 * <p><b>每一变体行</b>是动态复用行（keyed {@code forEach}），消费
 * {@link SceneThemes#selectableSurface} 的 {@link SceneTheme.Role#INDICATOR} 配方状态档，
 * 但只做<b>只写 {@code backgroundColor} 一个属性的轻量覆盖</b>（与 CategoryNavPane 同构）：
 * 行不装滤镜、不写边框/圆角/实体高度，选中档 RGB 取主题强调色、强度取统一选中强度 0x59
 * （明显强于 hover 档，选中不只靠透明度）；重绑/复用时行状态全部由该行自己的信号重派生，
 * 不与面板竞争属性槽，也不给浮层新增 BACKDROP 采样（G13「虚拟化复用行轻量零滤镜」裁决）。</p>
 *
 * <p><b>文字三件套</b>：标题与行标签取 {@link SceneThemes#foreground}、候选名（次要信息）取
 * {@link SceneThemes#mutedForeground}，禁用一律取 {@link SceneThemes#disabledForeground}。
 * <b>变体物品图像不改色</b>（契约 §4.1「物品图像不改色」+ §7.3「内容图片不迁移」）：图标节点的
 * 占位底色 / 透明底与图片源均为渲染协议静态值，不随主题重染。右端勾选圆点是控件自持的选中
 * 标记（与 {@code SceneRadioGroup} dot 同一口径）：启用且选中取 {@link SceneThemes#onAccentForeground}，
 * 其余（未选中/禁用）透明；其圆角是标记自身几何、非绑定六项，保持组件自持。</p>
 *
 * <h3>受控语义</h3>
 * <p>行点击严格复刻现状 {@code variantRow}：SELECTED 模式点击 → {@code onKeysChange(含则移除否则加入)}；
 * ALL 模式点击 → 无任何副作用（只读）。模式切换走 {@link SceneSegmented}（ALL/SELECTED 两段）
 * 回写 {@code onModeChange}。提交 {@code onCommit(new Selection(candidate.key(), mode.get(),
 * orderedKeys(...)))}；取消/back {@code onCancel}。两者都<b>不写 open</b>（open 由外壳持有、回调里关闭）。</p>
 *
 * <h3>生命周期</h3>
 * <p>本模块的所有 effect / portal / interactionState / on 均在 {@code create()} 的调用者
 * Owner 作用域内注册，组件卸载时一并回收；内部 portal 每次可见挂载创建独立子 Owner，
 * overlay 子树随关闭卸载，无残留。</p>
 */
public final class VariantChooser {

    /** 浮层卡片宽度（像素）。 */
    private static final int VARIANT_CARD_WIDTH = 440;
    /** 变体列表视口高度（像素）。 */
    private static final int VARIANT_LIST_HEIGHT = 240;
    /** 变体勾选行高度（像素）。 */
    private static final int VARIANT_ROW_HEIGHT = 34;
    /** 变体图标尺寸（像素）。 */
    private static final int VARIANT_ICON_SIZE = 18;
    /** 全屏遮罩底色（半透明黑）：遮罩语义静态值，只负责遮罩、不装玻璃（契约 §4.1）。 */
    private static final int OVERLAY_SCRIM = 0xCC000000;
    /** 无图占位底色（与 SceneVirtualGrid 占位同色）：物品图像渲染协议静态值，不随主题重染。 */
    private static final int PLACEHOLDER_COLOR = 0xFF454B54;
    /** ALL / SELECTED 分段文案（对齐 SearchPickerPresentation 默认英文文案）。 */
    private static final List<String> SEGMENT_LABELS =
            Arrays.asList("All", "Selected");
    /** 恒真 enabled：浮层面板自身没有禁用语义（禁用由内部控件各自表达），外观走 idle 档。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;

    /** 纯静态工厂，禁止实例化。 */
    private VariantChooser() {
    }

    /**
     * 变体选择浮层输入契约（不可变，显式校验构造器）。
     *
     * @param open          浮层开合只读信号（外壳持有，本模块不写）
     * @param candidate     当前候选（可能为 null，须有变体才展示）
     * @param enabled       是否启用
     * @param title         面板标题（null → "选择变体"）
     * @param visualAdapter 视觉适配器（图/文案来源）
     * @param mode          选择模式受控信号（只读）
     * @param onModeChange  模式回写回调（外壳写入自己持有的 signal）
     * @param selectedKeys  已选 key 受控信号（只读）
     * @param onKeysChange  已选 key 回写回调（外壳写入自己持有的 signal）
     * @param onCommit      提交回调（Selection 上抛）
     * @param onCancel      取消回调
     */
    @Desugar
    public record Props(
            ReadableSignal<Boolean> open,
            ReadableSignal<SearchPickerData.Candidate> candidate,
            ReadableSignal<Boolean> enabled,
            boolean variantSearchEnabled,
            String title,
            VisualAdapter visualAdapter,
            ReadableSignal<SearchPickerData.SelectionMode> mode,
            Consumer<SearchPickerData.SelectionMode> onModeChange,
            ReadableSignal<List<String>> selectedKeys,
            Consumer<List<String>> onKeysChange,
            Consumer<SearchPickerData.Selection> onCommit,
            Runnable onCancel) {

        /** 显式校验构造器。 */
        public Props {
            Objects.requireNonNull(open, "open");
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(enabled, "enabled");
            Objects.requireNonNull(visualAdapter, "visualAdapter");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(onModeChange, "onModeChange");
            Objects.requireNonNull(selectedKeys, "selectedKeys");
            Objects.requireNonNull(onKeysChange, "onKeysChange");
            Objects.requireNonNull(onCommit, "onCommit");
            Objects.requireNonNull(onCancel, "onCancel");
        }

        /** @return 面板标题（null → 默认「选择变体」）。 */
        public String effectiveTitle() {
            return title == null ? "选择变体" : title;
        }
    }

    /**
     * 构建变体选择浮层组件：返回一个空挂点节点（无视觉、不可命中），浮层本体经
     * {@link SceneRuntime#portal} 内部管理。
     *
     * @param rt    场景运行时
     * @param props 输入契约（非 null）
     * @return 空挂点节点（挂到宿主布局树）
     */
    public static SceneNode create(SceneRuntime rt, Props props) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(props, "props");

        SceneNode anchor = new SceneNode();
        anchor.setHitTestable(false);

        // 过滤查询文本：模块内部状态，在 create() 调用者 Owner 作用域内创建，随组件卸载回收。
        Signal<String> variantQuery = Signal.create("");

        // 浮层可见性：open 且候选存在且候选有变体（空变体恒不挂载）。
        ReadableSignal<Boolean> showSignal = Computed.create(() -> {
            boolean open = Boolean.TRUE.equals(props.open().get());
            if (!open) return Boolean.FALSE;
            SearchPickerData.Candidate candidate = props.candidate().get();
            if (candidate == null) return Boolean.FALSE;
            return Boolean.valueOf(!candidate.variants().isEmpty());
        });

        // 打开边沿：查询文本清空。
        boolean[] prevShown = {false};
        Effect.create(() -> {
            boolean shown = Boolean.TRUE.equals(showSignal.get());
            if (shown && !prevShown[0]) {
                variantQuery.set("");
            }
            prevShown[0] = shown;
        });

        rt.portal(showSignal, () -> buildOverlay(rt, props, variantQuery),
                OverlayDismissPolicy.NONE, props.onCancel());

        return anchor;
    }

    /**
     * 构建浮层根：全屏遮罩 + 居中卡片（标题 / 查询输入 / 分段 / 勾选列表 / 取消-确认）。
     *
     * <p>在 portal 的独立子 Owner 作用域内执行，内部所有 bind/effect/on 随浮层关闭一并回收；
     * 提交/取消只回调、不写 open。</p>
     */
    private static SceneNode buildOverlay(SceneRuntime rt, Props props,
                                          Signal<String> variantQuery) {
        SceneNode scrim = SceneNode.row();
        scrim.setFillParentWidth(true);
        scrim.setFillParentHeight(true);
        scrim.setBackgroundColor(OVERLAY_SCRIM);
        scrim.setMainAxisAlign(MainAxisAlign.CENTER);
        scrim.setCrossAxisAlign(CrossAxisAlign.CENTER);
        scrim.setPadding(SceneChromeTokens.PAD_MD);

        SceneNode card = SceneNode.column();
        card.setPreferredWidth(VARIANT_CARD_WIDTH);
        // 浮层面板：OVERLAY 角色配方是面板外观唯一写入者（background/border/borderWidth/
        // cornerRadius/backdrop/surfaceElevation 六项独占，面板自身恰装一颗滤镜）；
        // 旧 SceneChromeTokens.applyPanelChrome 实色四件套写入者已删除。
        // 时序契约：Router 的 writeHovered/writePressed/writeFocused 对未创建的 signal 直接
        // 短路，故在构建期声明关心，保证后续状态档可用。
        SceneInteractionState cardInteraction = rt.interactionState(card);
        cardInteraction.hovered();
        cardInteraction.pressed();
        cardInteraction.focused();
        SceneSurfaceBinder.bind(rt, card, SceneThemes.surface(rt, SceneTheme.Role.OVERLAY),
                ALWAYS_ENABLED, cardInteraction);
        // clip 不属于绑定器六项属性：保持原 applyPanelChrome 的裁剪语义，由组件自持。
        card.setClipChildren(true);
        card.setPadding(SceneChromeTokens.PAD_MD);
        card.setGap(SceneChromeTokens.GAP_MD);
        scrim.appendChild(card);

        // 主题语义前景在构造期捕获一次（portal builder 作用域继承来源主题，契约 §6 路径 5）；
        // 标题/候选名/行标签共享同一派生信号，主题切换只重派生、不重建节点。
        ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
        ReadableSignal<Integer> mutedForeground = SceneThemes.mutedForeground(rt);
        ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);
        ReadableSignal<Integer> onAccentForeground = SceneThemes.onAccentForeground(rt);
        ReadableSignal<Integer> labelForeground = () -> Boolean.TRUE.equals(props.enabled().get())
                ? foreground.get() : disabledForeground.get();
        ReadableSignal<Integer> secondaryForeground = () -> Boolean.TRUE.equals(props.enabled().get())
                ? mutedForeground.get() : disabledForeground.get();

        // header = [title + candidateLabel(flexGrow)]
        SceneNode header = SceneNode.row();
        header.setCrossAxisAlign(CrossAxisAlign.CENTER);
        header.setGap(SceneChromeTokens.GAP_SM);
        header.setHitTestable(false);
        SceneNode title = text(props.effectiveTitle());
        title.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        rt.bind(labelForeground, title::setTextColor);
        header.appendChild(title);
        SceneNode candidateLabel = text("");
        candidateLabel.setFlexGrow(1);
        candidateLabel.setClipChildren(true);
        rt.bind(secondaryForeground, candidateLabel::setTextColor);
        rt.bindText(candidateLabel, Computed.create(() -> {
            SearchPickerData.Candidate candidate = props.candidate().get();
            return candidate == null ? "" : props.visualAdapter().candidateLabel(candidate);
        }));
        header.appendChild(candidateLabel);
        card.appendChild(header);

        // 查询输入（前缀匹配、大小写不敏感，绑定内部 variantQuery；打开后由模块请求焦点）
        if (props.variantSearchEnabled()) {
            SceneNode search = SceneTextInput.create(rt, SceneTextInput.Props.builder(variantQuery)
                    .enabled(props.enabled())
                    .placeholder("搜索变体")
                    .onChange(variantQuery::set).build()).get();
            card.appendChild(search);
            // 浮层首次挂载时请求聚焦查询输入（buildOverlay 每次可见挂载执行一次）。
            rt.requestFocus(search);
        }

        // 模式切换分段（受控：selectedIndex 由 mode.ordinal() 派生，选择回写 onModeChange）
        SceneNode segmented = SceneSegmented.create(rt, new SceneSegmented.Props(
                Computed.create(() -> Integer.valueOf(props.mode().get().ordinal())),
                SEGMENT_LABELS,
                props.enabled(),
                index -> props.onModeChange().accept(
                        SearchPickerData.SelectionMode.values()[index.intValue()]))).get();
        card.appendChild(segmented);

        // 勾选列表：标准滚动结构走 SceneScrollContainer 工厂（默认滚动条视觉），不再手写样板。
        SceneScrollContainer.Result sc = SceneScrollContainer.createDefault(rt, 0, 0, 0, 0);
        SceneNode list = sc.viewport();
        list.setHitTestable(false);
        // 渲染分级回退：已分级不可渲染的变体回退占位样式（与结果列表同款共享装配）。
        Signal<Set<Object>> unrenderableKeys = ItemRenderFallbackKeys.track(
                VariantChooser::variantKeyForRegistryKey);

        ReadableSignal<List<SearchPickerData.Variant>> shownVariants = Computed.create(() ->
                displayVariants(safeCandidate(props), props.selectedKeys().get(), variantQuery.get()));
        rt.forEach(sc.content(), shownVariants, SearchPickerData.Variant::key,
                variant -> variantRow(rt, props, variant, unrenderableKeys,
                        labelForeground, onAccentForeground));

        SceneNode listHost = sc.container();
        listHost.setPreferredHeight(VARIANT_LIST_HEIGHT);
        card.appendChild(listHost);

        // 底部操作：取消 / 确认（只回调，不写 open）
        SceneNode footer = SceneNode.row();
        footer.setGap(SceneChromeTokens.GAP_MD);
        footer.setMainAxisAlign(MainAxisAlign.END);
        footer.setHitTestable(false);
        SceneNode back = SceneButton.create(rt, new SceneButton.Props(
                Signal.create("取消"), props.enabled(), () -> props.onCancel().run())).get();
        back.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        footer.appendChild(back);
        SceneNode confirm = SceneButton.create(rt, new SceneButton.Props(
                Signal.create("确认"),
                Computed.create(() -> Boolean.valueOf(canConfirm(
                        props.mode().get(), props.selectedKeys().get()))),
                () -> {
                    SearchPickerData.Candidate candidate = safeCandidate(props);
                    if (candidate == null) return;
                    props.onCommit().accept(new SearchPickerData.Selection(
                            candidate.key(), props.mode().get(),
                            orderedKeys(candidate.variants(), props.selectedKeys().get())));
                })).get();
        confirm.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        footer.appendChild(confirm);
        card.appendChild(footer);

        return scrim;
    }

    /**
     * 变体勾选行：严格复刻现状 {@code variantRow} 受控语义。
     *
     * <ul>
     *   <li>SELECTED 模式点击 → {@code onKeysChange(含则移除否则加入)}；</li>
     *   <li>ALL 模式点击 → 无任何副作用（只读展示）。</li>
     * </ul>
     */
    private static SceneNode variantRow(SceneRuntime rt, Props props, SearchPickerData.Variant variant,
                                        ReadableSignal<Set<Object>> unrenderableKeys,
                                        ReadableSignal<Integer> labelForeground,
                                        ReadableSignal<Integer> onAccentForeground) {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(VARIANT_ROW_HEIGHT);
        row.setCrossAxisAlign(CrossAxisAlign.CENTER);
        row.setGap(SceneChromeTokens.GAP_MD);
        row.setPadding(SceneChromeTokens.PAD_MD);
        SceneInteractionState interaction = rt.interactionState(row);
        // 时序契约：构造期声明关心，Router 后续写入才会落到已创建的 signal。
        interaction.hovered();
        interaction.pressed();

        ReadableSignal<Boolean> checked = Computed.create(() -> Boolean.valueOf(
                props.selectedKeys().get().contains(variant.key())));
        ReadableSignal<Boolean> selectable = Computed.create(() -> Boolean.valueOf(
                props.mode().get() == SearchPickerData.SelectionMode.SELECTED));
        // 动态复用行轻量档（G13「复用行零滤镜」裁决）：INDICATOR selectableSurface 配方只作
        // 取色来源，行只写 backgroundColor 一个属性，不装滤镜、不写边框/圆角/实体高度。
        bindRowTint(rt, props.enabled(), checked, interaction, row);

        // 图标：无图或已分级不可渲染 → 占位底色（与结果列表同款回退语义，随分级变更响应式更新）。
        // 图像渲染协议不改色（契约 §4.1「物品图像不改色」）：占位底/透明底为静态值、不随主题重染。
        SceneNode icon = new SceneNode();
        icon.setPreferredWidth(VARIANT_ICON_SIZE).setPreferredHeight(VARIANT_ICON_SIZE)
                .setHitTestable(false);
        ReadableSignal<SceneImageSource> effectiveImage = Computed.create(() -> {
            if (unrenderableKeys.get().contains(variant.key())) {
                return null;
            }
            return props.visualAdapter().variantImage(variant);
        });
        rt.bind(effectiveImage, src -> {
            icon.setBackgroundColor(src == null ? PLACEHOLDER_COLOR : SceneChromeTokens.TRANSPARENT);
            icon.setImageSource(src);
        });
        row.appendChild(icon);

        // label：主题正文前景（禁用取 disabledForeground，经构造期捕获的派生信号）。
        SceneNode label = text(props.visualAdapter().variantLabel(variant));
        label.setFlexGrow(1);
        label.setHitTestable(false);
        rt.bind(labelForeground, label::setTextColor);
        row.appendChild(label);

        // 右端勾选圆点：控件自持选中标记（SceneRadioGroup dot 同口径）——启用且选中取主题
        // 强调底前景，其余（未选中/禁用）透明露出面板玻璃底；圆角是标记自身几何、非绑定六项。
        SceneNode indicator = new SceneNode();
        indicator.setPreferredWidth(16).setPreferredHeight(16).setCornerRadius(8).setHitTestable(false);
        rt.bindComputed(() -> Boolean.TRUE.equals(props.enabled().get())
                        && Boolean.TRUE.equals(checked.get())
                        ? onAccentForeground.get() : SceneChromeTokens.TRANSPARENT,
                indicator::setBackgroundColor);
        row.appendChild(indicator);

        rt.on(row, SceneEventType.CLICK, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())
                    || !Boolean.TRUE.equals(selectable.get())) return;
            // SELECTED：toggle 勾选；ALL 模式只读无副作用（复刻现状 variantRow）
            props.onKeysChange().accept(toggleVariant(props.selectedKeys().get(), variant.key()));
            ctx.stopPropagation();
        });
        return row;
    }

    /**
     * 绑定行背景：消费 {@link SceneThemes#selectableSurface}(INDICATOR, checked) 配方的状态档，
     * 按契约 §2.5 优先级 disabled &gt; pressed &gt; hovered &gt; idle 取 tint，只写
     * {@code backgroundColor} 一个属性。
     *
     * <p>行不装滤镜、不写边框/圆角/实体高度（保持普通绘制路径，{@code __getSurfaceElevation()}
     * 恒为 -1），动态复用重绑时选中/hover/禁用均由各行自己的信号重派生，不残留上一项状态。
     * 全部取值发生在派生函数内，构造期不解引用未求值的 Computed。</p>
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

    /** registryKey（注册名:meta）→ 变体 key（注册名@meta）；非法返回 null。 */
    static Object variantKeyForRegistryKey(String registryKey) {
        String[] parts = ItemRenderFallbackKeys.splitRegistryKey(registryKey);
        return parts == null ? null : parts[0] + "@" + parts[1];
    }

    /** 勾选/取消一个变体 key（含则移除，否则加入）。 */
    private static List<String> toggleVariant(List<String> keys, String key) {
        ArrayList<String> next = new ArrayList<String>(keys);
        if (next.contains(key)) next.remove(key); else next.add(key);
        return Collections.unmodifiableList(next);
    }

    /** 按候选声明顺序重排已选 key，未声明但已选的 key 保序补在尾部（复刻 ScenePickerPanelNav.orderedKeys）。 */
    private static List<String> orderedKeys(List<SearchPickerData.Variant> variants, List<String> keys) {
        ArrayList<String> ordered = new ArrayList<String>();
        for (SearchPickerData.Variant variant : variants) {
            if (keys.contains(variant.key())) ordered.add(variant.key());
        }
        for (String key : keys) {
            if (!ordered.contains(key)) ordered.add(key);
        }
        return Collections.unmodifiableList(ordered);
    }

    /** 派生展示列表：已选 key 恒显示，其余按 query 对 key/label 大小写不敏感过滤。 */
    private static List<SearchPickerData.Variant> displayVariants(
            SearchPickerData.Candidate candidate, List<String> keys, String query) {
        List<SearchPickerData.Variant> variants = candidate == null
                ? Collections.<SearchPickerData.Variant>emptyList() : candidate.variants();
        ArrayList<SearchPickerData.Variant> displayed = new ArrayList<SearchPickerData.Variant>();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (SearchPickerData.Variant variant : variants) {
            if (keys.contains(variant.key())) {
                displayed.add(variant);
                continue;
            }
            if (!needle.isEmpty() && !variant.key().toLowerCase(Locale.ROOT).contains(needle)
                    && !variant.label().toLowerCase(Locale.ROOT).contains(needle)) continue;
            displayed.add(variant);
        }
        return Collections.unmodifiableList(displayed);
    }

    /** SELECTED 至少勾选一个才可确认；ALL 恒可确认。 */
    private static boolean canConfirm(SearchPickerData.SelectionMode mode, List<String> keys) {
        if (mode == SearchPickerData.SelectionMode.SELECTED) return !keys.isEmpty();
        return true;
    }

    private static SearchPickerData.Candidate safeCandidate(Props props) {
        return props.candidate().get();
    }

    private static SceneNode text(String value) {
        SceneNode node = new SceneNode();
        node.setText(value == null ? "" : value);
        node.setHitTestable(false);
        return node;
    }
}
