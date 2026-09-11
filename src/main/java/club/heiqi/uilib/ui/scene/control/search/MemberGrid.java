package club.heiqi.uilib.ui.scene.control.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongPredicate;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.SearchPickerPresentation;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.Config;
import club.heiqi.uilib.ui.diagnostic.UiPerfMarkers;
import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanelNav.MemberIssues;
import club.heiqi.uilib.ui.scene.control.SceneScrollContainer;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGridNav;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneRenderProtocolTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * MemberGrid —— 已选择成员的多列网格（模块化：替代面板内联的单列成员行）。
 *
 * <h3>定位</h3>
 * <p>底部「已选择」横带的成员区：自动列数网格（每成员一张小卡片：图标 + 主文本 + 无效/重复徽章 +
 * 副文本 + 编辑/删除操作，删除一步直达无需二次确认），标准滚动结构带可见滚动条
 *（{@link SceneScrollContainer} 工厂）。</p>
 *
 * <h3>受控语义</h3>
 * <p>成员列表、问题统计、编辑/删除回调全部受控；本模块只读上抛，不持有业务状态。</p>
 *
 * <h3>外观归属（液态玻璃迁移，G13 网格族口径）</h3>
 * <p>网格底座即 {@link SceneScrollContainer} 工厂的 viewport，background/border/borderWidth/
 * cornerRadius/backdrop/surfaceElevation 六项已由容器按主题 {@link SceneTheme.Role#GROUP} 配方
 * 独占（契约 §4.1 网格族「容器 GROUP」），本模块<b>不再</b>对底座二次绑定、也不给单元格装滤镜。
 * 单元格自身零表面写入（无底色、无边框、无圆角，保持 hitTestable=false 让点击落到卡内按钮）；
 * 成员卡片上没有选中/hover 交互态，故也没有轻量状态覆盖需要绑定的属性。单元格文字取主题语义
 * 前景：主文本 {@code foreground}、副文本 {@code mutedForeground}、徽章文字 duplicate 取
 * {@code warningText}、其余取 {@code foreground}（与 SceneToast / SceneObjectField 同一口径）。
 * 无效徽章底（{@code DANGER_BG_SUBTLE}）与图标占位底色属于状态徽标 / 物品图像渲染协议
 *（契约 §7.3「状态徽标、内容图片不迁移」与 §4.1「物品图像不改色」），保持静态、不随主题重染。
 * 卡内编辑/删除按钮复用已主题化的 {@link SceneButton}，本模块不重复绑定。主题切换只重派生、
 * 不重建节点，虚拟化式的 keyed 行/单元复用行为不变。</p>
 */
public final class MemberGrid {

    /** 单元图标边长（像素）。 */
    public static final int ICON_SIZE = 24;
    /** 副文本/徽章字号（像素）。 */
    public static final int FONT_SIZE = 12;
    /** 单元内边距（像素）。 */
    public static final int CELL_PADDING = 6;
    /** 无图标占位底色（与结果网格同色；集中定义处 = {@link SceneRenderProtocolTokens}）。 */
    public static final int PLACEHOLDER_COLOR = SceneRenderProtocolTokens.IMAGE_PLACEHOLDER_ARGB;

    /** 默认单元宽（像素）。 */
    public static final int DEFAULT_CELL_WIDTH = 200;
    /** 默认单元高（像素）：顶行 24 + 副文本 16 + 按钮 34 + padding 12 + gap 4 ≈ 90，取 96 防按钮溢出裁剪。 */
    public static final int DEFAULT_CELL_HEIGHT = 96;
    /** 默认列间距（像素）。 */
    public static final int DEFAULT_GAP_X = 8;
    /** 默认行间距（像素）。 */
    public static final int DEFAULT_GAP_Y = 8;

    private MemberGrid() {
    }

    /**
     * 成员网格输入契约（全部受控，无业务状态）。
     */
    @Desugar
    public record Props(
            ReadableSignal<List<SearchPickerData.CurrentMember>> members,
            ReadableSignal<Boolean> enabled,
            SearchPickerPresentation presentation,
            VisualAdapter visualAdapter,
            ReadableSignal<MemberIssues> issues,
            Consumer<Long> onEdit,
            LongPredicate onRemove,
            int cellWidth,
            int cellHeight,
            int gapX,
            int gapY,
            ReadableSignal<PickerMetrics> metrics) {

        /**
         * 旧 11 参形态（P5 兼容，纯加法保留）：卡尺寸取调用方传入的确定值。
         *
         * @param members      成员信号
         * @param enabled      是否启用
         * @param presentation 文案
         * @param visualAdapter 视觉适配器
         * @param issues       成员问题
         * @param onEdit       编辑回调
         * @param onRemove     删除回调
         * @param cellWidth    单元宽
         * @param cellHeight   单元高
         * @param gapX         列间距
         * @param gapY         行间距
         */
        public Props(ReadableSignal<List<SearchPickerData.CurrentMember>> members,
                     ReadableSignal<Boolean> enabled,
                     SearchPickerPresentation presentation,
                     VisualAdapter visualAdapter,
                     ReadableSignal<MemberIssues> issues,
                     Consumer<Long> onEdit,
                     LongPredicate onRemove,
                     int cellWidth, int cellHeight, int gapX, int gapY) {
            this(members, enabled, presentation, visualAdapter, issues, onEdit, onRemove,
                    cellWidth, cellHeight, gapX, gapY, null);
        }

        /** @return P5 派生度量（null = 沿用调用方传入的确定尺寸，P5 前口径） */
        public ReadableSignal<PickerMetrics> metrics() { return metrics; }

        /** 生效单元宽：有度量通道时由字号派生（P5 §2.5），否则取调用方值。 */
        public int effectiveCellWidth() {
            return metrics == null ? cellWidth : PickerChrome.memberCardWidth(metrics.get().fontSizePx());
        }

        /** 生效单元高：有度量通道时由字号派生（P5 §2.5），否则取调用方值。 */
        public int effectiveCellHeight() {
            return metrics == null ? cellHeight : PickerChrome.memberCardHeight(metrics.get().fontSizePx());
        }

        /** 生效列间距：与行间距同源（P5 §2.4 同源口径）。 */
        public int effectiveGapX() {
            return metrics == null ? gapX : metrics.get().grid().gapX();
        }

        /** 生效行间距：与列间距同源。 */
        public int effectiveGapY() {
            return metrics == null ? gapY : metrics.get().grid().gapY();
        }

        /** @return 卡内图标边长（有度量通道时 {@code round(fs*2)}，否则 {@link #ICON_SIZE}） */
        public int effectiveIconSide() {
            return metrics == null ? ICON_SIZE : PickerChrome.memberIconSide(metrics.get().fontSizePx());
        }

        /** @return 卡内边距（与结果单元同源：{@code clamp(round(fs/3),2,6)}） */
        public int effectivePadding() {
            return metrics == null ? CELL_PADDING : metrics.get().grid().paddingPx();
        }

        /** 显式校验构造器。 */
        public Props {
            Objects.requireNonNull(members, "members");
            Objects.requireNonNull(enabled, "enabled");
            Objects.requireNonNull(presentation, "presentation");
            Objects.requireNonNull(visualAdapter, "visualAdapter");
            Objects.requireNonNull(issues, "issues");
            Objects.requireNonNull(onEdit, "onEdit");
            Objects.requireNonNull(onRemove, "onRemove");
            if (cellWidth <= 0) throw new IllegalArgumentException("cellWidth 必须 > 0");
            if (cellHeight <= 0) throw new IllegalArgumentException("cellHeight 必须 > 0");
            if (gapX < 0 || gapY < 0) throw new IllegalArgumentException("gap 不可为负数");
        }
    }

    /**
     * 成员网格创建结果（root = 标准滚动容器 container，viewport = 可滚动视口）。
     */
    @Desugar
    public record Result(SceneNode root, SceneNode viewport) {
    }

    /** 全量行模型：成员按生效列数分行（行键 = 行首成员 memberId，稳定唯一）。 */
    @Desugar
    public record Row(long firstId, List<SearchPickerData.CurrentMember> members) {
    }

    /**
     * 构建成员网格。须在组件构建作用域（mount/portal builder）内调用。
     *
     * @param rt    场景运行时
     * @param props 输入契约（非 null）
     * @return 创建结果（root 挂到宿主布局树；viewport 供焦点/滚动观察）
     */
    public static Result create(SceneRuntime rt, Props props) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(props, "props");

        // 标准滚动结构 + 可见滚动条：SceneScrollContainer 工厂（默认视觉）。
        // 缺省显式实色参时，工厂已把 viewport 按主题 GROUP 配方绑定为网格底座（唯一写入者）；
        // 本模块不再对底座重复绑定，也不给每个单元格装滤镜（契约 §4.1 网格族口径）。
        SceneScrollContainer.Result sc = SceneScrollContainer.createDefault(rt, 0, 0, 0, 0);
        SceneNode viewport = sc.viewport();
        Signal<Integer> scroll = sc.scrollSignal();

        // 数据收缩/视口变化回夹：布局完成后把 scroll 夹回 maxScrollY。
        rt.bind(rt.layoutDoneSignal(), epoch -> Effect.untrack(() -> {
            int max = SceneGeometry.maxScrollY(viewport);
            int clamped = Math.max(0, Math.min(max, scroll.get().intValue()));
            if (clamped != scroll.get().intValue()) {
                scroll.set(Integer.valueOf(clamped));
            }
        }));

        // 生效列数：按 viewport 可用宽度自动推导（布局完成后读 cachedLayout 宽）。
        Signal<Integer> effectiveColumns = Signal.create(Integer.valueOf(1));
        // P5 §2.5：卡宽/卡高/图标/内边距由生效字号派生（同一份 PickerMetrics；null = 旧常量口径）。
        // 度量变化（字号倍率/密度档）时重派生列数与卡几何，无需关闭重开面板。
        if (props.metrics() != null) {
            rt.bind(props.metrics(), m -> Effect.untrack(() -> {
                Object cached = viewport.getCachedLayout();
                if (!(cached instanceof LayoutBox)) {
                    return;
                }
                int innerWidth = ((LayoutBox) cached).getWidth();
                int derived = SceneVirtualGridNav.deriveColumns(innerWidth,
                        PickerChrome.memberCardWidth(m.fontSizePx()), m.grid().gapX());
                if (derived != effectiveColumns.get().intValue()) {
                    effectiveColumns.set(Integer.valueOf(derived));
                }
            }));
        }
        rt.bind(rt.layoutDoneSignal(), epoch -> Effect.untrack(() -> {
            Object cached = viewport.getCachedLayout();
            if (!(cached instanceof LayoutBox)) {
                return;
            }
            int innerWidth = ((LayoutBox) cached).getWidth();
            int derived = SceneVirtualGridNav.deriveColumns(innerWidth,
                    props.effectiveCellWidth(), props.effectiveGapX());
            if (derived != effectiveColumns.get().intValue()) {
                effectiveColumns.set(Integer.valueOf(derived));
            }
        }));

        // 渲染分级回退：不可渲染图标回退占位样式（与结果列表同款共享装配）。
        // 键空间 = 候选域键：成员选择携带的变体 key 本身即 "candidateKey@meta"
        // （PickerIconKey.variant 的返回值）；无 meta 的成员按候选级键（PickerIconKey.candidate）判定。
        Signal<Set<Object>> unrenderableKeys = ItemRenderFallbackKeys.track(registryKey -> registryKey);

        ReadableSignal<List<Row>> rowsSignal = Computed.create(() ->
                toRows(safeMembers(props.members()), Math.max(1, effectiveColumns.get().intValue())));
        rt.forEach(sc.content(), rowsSignal, Row::firstId,
                row -> rowComponent(rt, props, row, effectiveColumns, unrenderableKeys));

        return new Result(sc.container(), viewport);
    }

    /** 全量行：成员按列数分行（无上限、无虚拟化，与结果列表同一取舍）。 */
    private static List<Row> toRows(List<SearchPickerData.CurrentMember> members, int columns) {
        if (members.isEmpty()) {
            return Collections.emptyList();
        }
        List<Row> rows = new ArrayList<>((members.size() + columns - 1) / columns);
        for (int i = 0; i < members.size(); i += columns) {
            int to = Math.min(members.size(), i + columns);
            rows.add(new Row(members.get(i).memberId(),
                    new ArrayList<>(members.subList(i, to))));
        }
        return rows;
    }

    /** 构建一个完整网格行（ROW 容器，行高钉定，行间距经 marginBottom 计入主轴占位）。 */
    private static SceneNode rowComponent(SceneRuntime rt, Props props, Row row,
                                          ReadableSignal<Integer> effectiveColumns,
                                          ReadableSignal<Set<Object>> unrenderableKeys) {
        SceneNode rowNode = SceneNode.row();
        rowNode.setPreferredHeight(props.effectiveCellHeight());
        rowNode.setMargin(0, 0, props.effectiveGapY(), 0);
        rowNode.setGap(props.effectiveGapX());
        rowNode.setHitTestable(false);
        // 行内容按实时数据源 + 实时列数派生（复用行不吃创建时陈旧快照）。
        ReadableSignal<List<SearchPickerData.CurrentMember>> rowMembers = Computed.create(() -> {
            List<SearchPickerData.CurrentMember> current = safeMembers(props.members());
            int columns = Math.max(1, effectiveColumns.get().intValue());
            int start = indexOfMember(current, row.firstId());
            if (start < 0) {
                return Collections.emptyList();
            }
            int to = Math.min(current.size(), start + columns);
            return new ArrayList<>(current.subList(start, to));
        });
        rt.forEach(rowNode, rowMembers, SearchPickerData.CurrentMember::memberId,
                member -> cellComponent(rt, props, member, unrenderableKeys));
        return rowNode;
    }

    /** @return 生效字号（有度量通道时取派生字号，否则回落卡内文本回落值） */
    private static int metricsFontSize(Props props) {
        return props.metrics() == null ? FONT_SIZE : props.metrics().get().fontSizePx();
    }

    /** 按 memberId 在实时列表中定位下标。 */
    private static int indexOfMember(List<SearchPickerData.CurrentMember> members, long memberId) {
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).memberId() == memberId) {
                return i;
            }
        }
        return -1;
    }

    /** 构建单个成员卡片（图标 + 主文本 + 徽章 + 副文本 + 编辑/删除一步操作）。 */
    private static SceneNode cellComponent(SceneRuntime rt, Props props,
                                           SearchPickerData.CurrentMember initialMember,
                                           ReadableSignal<Set<Object>> unrenderableKeys) {
        recordMemberCell();
        long memberId = initialMember.memberId();
        ReadableSignal<SearchPickerData.CurrentMember> currentMember = Computed.create(() -> {
            for (SearchPickerData.CurrentMember member : safeMembers(props.members())) {
                if (member.memberId() == memberId) {
                    return member;
                }
            }
            return initialMember;
        });

        SceneNode cell = SceneNode.column();
        cell.setPreferredWidth(props.effectiveCellWidth());
        cell.setPreferredHeight(props.effectiveCellHeight());
        cell.setClipChildren(true);
        cell.setPadding(props.effectivePadding());
        cell.setGap(2);
        // 单元格零表面写入：不装滤镜、不写底色/边框/圆角（网格底座六项归容器 GROUP 配方独占）。
        cell.setHitTestable(false);
        // G19/P-02 收编：语义色一律经 SceneThemes 公共派生入口取（构造期在 forEach 项
        // 作用域内捕获来源主题；派生期主题切换只重派生前景，不重建单元节点）。

        // 顶行：图标 + 主文本 + 无效/重复徽章
        SceneNode top = SceneNode.row();
        top.setCrossAxisAlign(CrossAxisAlign.CENTER);
        top.setGap(4);
        top.setHitTestable(false);

        SceneNode icon = new SceneNode();
        icon.setPreferredWidth(props.effectiveIconSide()).setPreferredHeight(props.effectiveIconSide())
                .setHitTestable(false);
        // 生效图标：不可渲染项回退占位样式。图像渲染协议不改色（契约 §4.1「物品图像不改色」
        // + §7.3「内容图片不迁移」）：占位底色与透明底均为静态值，不随主题重染。
        ReadableSignal<SceneImageSource> effectiveImage = Computed.create(() -> {
            SearchPickerData.CurrentMember member = currentMember.get();
            if (member.candidate() == null || isMemberUnrenderable(member, unrenderableKeys.get())) {
                return null;
            }
            return props.visualAdapter().candidateImage(member.candidate());
        });
        // 两态可区分（ADR A-19）：UNRENDERABLE = 主题派生状态色；「无图」= 静态协议占位色。
        // 只有"平台判定不可渲染"才是主题派生的状态语义；"无候选/无效成员"属「无图」协议态，
        // 仍走静态协议占位色（两态不得混为一类，ADR §5.4 R-04）。
        ReadableSignal<Boolean> unrenderable = Computed.create(() -> {
            SearchPickerData.CurrentMember member = currentMember.get();
            return Boolean.valueOf(member.candidate() != null
                    && isMemberUnrenderable(member, unrenderableKeys.get()));
        });
        ReadableSignal<Integer> unrenderableTint = ItemRenderFallbackKeys.unrenderableTint(rt);
        rt.bindComputed(() -> {
            if (Boolean.TRUE.equals(unrenderable.get())) {
                return unrenderableTint.get();
            }
            return effectiveImage.get() == null
                    ? Integer.valueOf(PLACEHOLDER_COLOR)
                    : Integer.valueOf(SceneChromeTokens.TRANSPARENT);
        }, icon::setBackgroundColor);
        rt.bind(effectiveImage, icon::setImageSource);
        top.appendChild(icon);

        SceneNode primary = text("");
        primary.setFlexGrow(1);
        primary.setClipChildren(true);
        // 主文本取主题正文前景（主题切换只重派生，不重建节点）。
        rt.bind(SceneThemes.foreground(rt), primary::setTextColor);
        rt.bindText(primary, Computed.create(() -> props.presentation().currentMemberPrimary(
                currentMember.get())));
        top.appendChild(primary);

        ReadableSignal<Boolean> malformed = Computed.create(() -> Boolean.valueOf(
                currentMember.get().selection() == null));
        ReadableSignal<Boolean> duplicate = Computed.create(() -> Boolean.valueOf(
                !Boolean.TRUE.equals(malformed.get())
                        && props.issues().get().duplicateMemberIds().contains(Long.valueOf(memberId))));
        SceneNode badge = text("");
        badge.setWidthSizing(WidthSizing.SHRINK);
        badge.setFallbackFontSize(FONT_SIZE);
        // F5（P5 §5.6）：徽章不受字号档影响地溢出卡片 —— 单行省略 + 宽度上限
        // （卡宽 − 图标 − 内边距 − 间距），字号放大时截断而不是把卡片撑破。
        badge.setMaxLines(1);
        badge.setEllipsis(true);
        badge.setMaxTextWidth(Math.max(1, props.effectiveCellWidth() - props.effectiveIconSide()
                - 2 * props.effectivePadding() - SceneChromeTokens.GAP_SM));
        // 徽章内边距/圆角随字号派生（P5 §3.3 分类徽章同口径）。
        badge.setPadding(PickerChrome.badgePadding(metricsFontSize(props)));
        badge.setCornerRadius(SceneChromeTokens.RADIUS_SM);
        rt.bindText(badge, Computed.create(() -> Boolean.TRUE.equals(malformed.get())
                ? props.presentation().invalidMemberBadge()
                : Boolean.TRUE.equals(duplicate.get()) ? props.presentation().duplicateMemberBadge() : ""));
        // F1（P5 §5.6）：无效成员 = 错误级底色（DANGER_BG_SUBTLE 协议弱底）；
        // 重复成员 = 警告级底色，走主题语义槽 SceneThemes.warningSubtle（P5 §4.2 C：不得进静态色板）。
        ReadableSignal<Integer> warningSubtle = SceneThemes.warningSubtle(rt);
        rt.bindComputed(() -> Integer.valueOf(
                        Boolean.TRUE.equals(malformed.get()) ? SceneChromeTokens.DANGER_BG_SUBTLE
                                : Boolean.TRUE.equals(duplicate.get()) ? warningSubtle.get()
                                        : SceneChromeTokens.TRANSPARENT),
                badge::setBackgroundColor);
        // 徽章文字取主题语义前景：duplicate 取 warningText，其余取正文（与 SceneToast/
        // SceneObjectField 同口径）。G19/P-02 收编：两分支均经 SceneThemes 公共派生入口取色。
        ReadableSignal<Integer> badgeWarning = SceneThemes.warningText(rt);
        ReadableSignal<Integer> badgeNormal = SceneThemes.foreground(rt);
        rt.bindComputed(() -> Integer.valueOf(Boolean.TRUE.equals(duplicate.get())
                ? badgeWarning.get() : badgeNormal.get()), badge::setTextColor);
        top.appendChild(badge);
        cell.appendChild(top);

        // 副文本：canonical 摘要，取主题次要前景。
        SceneNode secondary = text("");
        secondary.setFallbackFontSize(FONT_SIZE);
        secondary.setClipChildren(true);
        rt.bind(SceneThemes.mutedForeground(rt), secondary::setTextColor);
        rt.bindText(secondary, Computed.create(() -> props.presentation().currentMemberSecondary(
                currentMember.get())));
        cell.appendChild(secondary);

        // 底部操作：编辑 / 删除（一步直达，无二次确认）
        SceneNode actions = SceneNode.row();
        actions.setMainAxisAlign(MainAxisAlign.END);
        actions.setGap(2);
        actions.setHitTestable(false);
        SceneNode edit = SceneButton.create(rt, new SceneButton.Props(
                Signal.create(props.presentation().edit()),
                props.enabled(), () -> props.onEdit().accept(Long.valueOf(memberId)))).get();
        edit.setWidthSizing(WidthSizing.SHRINK);
        actions.appendChild(edit);
        SceneNode remove = SceneButton.create(rt, new SceneButton.Props(
                Signal.create(props.presentation().remove()),
                props.enabled(), () -> props.onRemove().test(memberId))).get();
        remove.setWidthSizing(WidthSizing.SHRINK);
        actions.appendChild(remove);
        cell.appendChild(actions);

        return cell;
    }

    /**
     * 成员整体不可渲染判定（键空间 = 候选域键，见 {@code ItemRenderFallbackKeys}）：
     * ALL（候选整体）看 {@code PickerIconKey.candidate(key)}；单选变体看该变体键
     * （{@code candidateKey@meta}，即成员选择里携带的变体 key）；多选变体 = 全部键都不可渲染才算整体不可渲染。
     *
     * @param member         当前成员快照
     * @param unrenderable   已分级不可渲染键集合
     * @return 是否应回退占位样式
     */
    private static boolean isMemberUnrenderable(SearchPickerData.CurrentMember member,
                                                Set<Object> unrenderable) {
        SearchPickerData.Selection selection = member.selection();
        if (selection == null || member.candidate() == null || unrenderable == null) {
            return false;
        }
        if (selection.mode() == SearchPickerData.SelectionMode.ALL) {
            return unrenderable.contains(PickerIconKey.candidate(member.candidate().key()));
        }
        List<String> keys = selection.variantKeys();
        if (keys.isEmpty()) {
            return false;
        }
        for (String key : keys) {
            if (!unrenderable.contains(key)) {
                return false;
            }
        }
        return true;
    }

    private static List<SearchPickerData.CurrentMember> safeMembers(
            ReadableSignal<? extends List<SearchPickerData.CurrentMember>> signal) {
        List<SearchPickerData.CurrentMember> members = signal.get();
        return members == null ? Collections.<SearchPickerData.CurrentMember>emptyList() : members;
    }

    // ==================== 采样埋点（只加观测，不改渲染与交互语义） ====================

    /** 累计一个已挂载的成员卡片。 */
    private static void recordMemberCell() {
        if (!Config.useDebug) {
            return;
        }
        UiPerformanceMonitor.getInstance().recordCounter(UiPerfMarkers.COUNTER_PICKER_MEMBERS, 1L);
    }

    private static SceneNode text(String value) {
        SceneNode node = new SceneNode();
        node.setText(value == null ? "" : value);
        node.setHitTestable(false);
        return node;
    }
}
