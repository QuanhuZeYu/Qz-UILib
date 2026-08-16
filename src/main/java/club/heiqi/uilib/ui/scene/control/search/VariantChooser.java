package club.heiqi.uilib.ui.scene.control.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneControlChrome;
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
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;

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
 *   └─ card (COLUMN, 实底圆角卡片)
 *        ├─ header (标题)
 *        ├─ search (查询输入, 前缀过滤)
 *        ├─ segmented (ALL / SELECTED 模式切换)
 *        ├─ list (滚动视口, 勾选行列表)
 *        └─ footer (取消 / 确认)
 * </pre>
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
    /** 全屏遮罩底色（半透明黑）。 */
    private static final int OVERLAY_SCRIM = 0xCC000000;
    /** 无图占位底色（与 SceneVirtualGrid 占位同色）。 */
    private static final int PLACEHOLDER_COLOR = 0xFF454B54;
    /** ALL / SELECTED 分段文案（对齐 SearchPickerPresentation 默认英文文案）。 */
    private static final List<String> SEGMENT_LABELS =
            Arrays.asList("All", "Selected");

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
        card.setClipChildren(true);
        card.setBackgroundColor(SceneChromeTokens.BG_DEFAULT);
        card.setBorderWidth(1);
        card.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);
        card.setCornerRadius(SceneChromeTokens.RADIUS_LG);
        card.setPadding(SceneChromeTokens.PAD_MD);
        card.setGap(SceneChromeTokens.GAP_MD);
        scrim.appendChild(card);

        // header = [title + candidateLabel(flexGrow)]
        SceneNode header = SceneNode.row();
        header.setCrossAxisAlign(CrossAxisAlign.CENTER);
        header.setGap(SceneChromeTokens.GAP_SM);
        header.setHitTestable(false);
        SceneNode title = text(props.effectiveTitle());
        title.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        header.appendChild(title);
        SceneNode candidateLabel = text("");
        candidateLabel.setFlexGrow(1);
        candidateLabel.setClipChildren(true);
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

        // 勾选列表（滚动视口）
        SceneNode list = SceneNode.column();
        list.setScrollable(true);
        list.setClipChildren(true);
        list.setPreferredHeight(VARIANT_LIST_HEIGHT);
        list.setHitTestable(false);
        SceneScrolls.attach(rt, list);

        ReadableSignal<List<SearchPickerData.Variant>> shownVariants = Computed.create(() ->
                displayVariants(safeCandidate(props), props.selectedKeys().get(), variantQuery.get()));
        rt.forEach(list, shownVariants, SearchPickerData.Variant::key,
                variant -> variantRow(rt, props, variant));
        card.appendChild(list);

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
    private static SceneNode variantRow(SceneRuntime rt, Props props, SearchPickerData.Variant variant) {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(VARIANT_ROW_HEIGHT);
        row.setCrossAxisAlign(CrossAxisAlign.CENTER);
        row.setGap(SceneChromeTokens.GAP_MD);
        row.setPadding(SceneChromeTokens.PAD_MD);
        SceneInteractionState interaction = rt.interactionState(row);

        ReadableSignal<Boolean> checked = Computed.create(() -> Boolean.valueOf(
                props.selectedKeys().get().contains(variant.key())));
        ReadableSignal<Boolean> selectable = Computed.create(() -> Boolean.valueOf(
                props.mode().get() == SearchPickerData.SelectionMode.SELECTED));
        SceneControlChrome.bindSelectableBackground(rt, row, props.enabled(), checked, interaction);

        // 图标：无图占位底色
        SceneNode icon = new SceneNode();
        icon.setPreferredWidth(VARIANT_ICON_SIZE).setPreferredHeight(VARIANT_ICON_SIZE)
                .setHitTestable(false);
        SceneImageSource image = props.visualAdapter().variantImage(variant);
        if (image == null) icon.setBackgroundColor(PLACEHOLDER_COLOR); else icon.setImageSource(image);
        row.appendChild(icon);

        // label
        SceneNode label = text(props.visualAdapter().variantLabel(variant));
        label.setFlexGrow(1);
        label.setHitTestable(false);
        row.appendChild(label);

        // 右端指示器：选中态 TEXT_ON_ACCENT 否则占位色
        SceneNode indicator = new SceneNode();
        indicator.setPreferredWidth(16).setPreferredHeight(16).setCornerRadius(8).setHitTestable(false);
        rt.bindComputed(() -> Boolean.TRUE.equals(checked.get())
                ? Integer.valueOf(SceneChromeTokens.TEXT_ON_ACCENT)
                : Integer.valueOf(PLACEHOLDER_COLOR), indicator::setBackgroundColor);
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
