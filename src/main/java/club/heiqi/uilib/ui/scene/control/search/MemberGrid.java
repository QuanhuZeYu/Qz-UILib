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
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

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
 */
public final class MemberGrid {

    /** 单元图标边长（像素）。 */
    public static final int ICON_SIZE = 24;
    /** 副文本/徽章字号（像素）。 */
    public static final int FONT_SIZE = 12;
    /** 单元内边距（像素）。 */
    public static final int CELL_PADDING = 6;
    /** 无图标占位底色（与结果网格同色）。 */
    public static final int PLACEHOLDER_COLOR = 0xFF454B54;

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
            Runnable onRemoveConfirmed,
            int cellWidth,
            int cellHeight,
            int gapX,
            int gapY) {

        /** 显式校验构造器。 */
        public Props {
            Objects.requireNonNull(members, "members");
            Objects.requireNonNull(enabled, "enabled");
            Objects.requireNonNull(presentation, "presentation");
            Objects.requireNonNull(visualAdapter, "visualAdapter");
            Objects.requireNonNull(issues, "issues");
            Objects.requireNonNull(onEdit, "onEdit");
            Objects.requireNonNull(onRemove, "onRemove");
            Objects.requireNonNull(onRemoveConfirmed, "onRemoveConfirmed");
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
        rt.bind(rt.layoutDoneSignal(), epoch -> Effect.untrack(() -> {
            Object cached = viewport.getCachedLayout();
            if (!(cached instanceof LayoutBox)) {
                return;
            }
            int innerWidth = ((LayoutBox) cached).getWidth();
            int derived = SceneVirtualGridNav.deriveColumns(innerWidth, props.cellWidth(), props.gapX());
            if (derived != effectiveColumns.get().intValue()) {
                effectiveColumns.set(Integer.valueOf(derived));
            }
        }));

        // 渲染分级回退：不可渲染图标回退占位样式（与结果列表同款共享装配）。
        Signal<Set<Object>> unrenderableKeys = ItemRenderFallbackKeys.track(
                registryKey -> candidateKeyForRegistryKey(safeMembers(props.members()), registryKey));

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
        rowNode.setPreferredHeight(props.cellHeight());
        rowNode.setMargin(0, 0, props.gapY(), 0);
        rowNode.setGap(props.gapX());
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
        cell.setPreferredWidth(props.cellWidth());
        cell.setPreferredHeight(props.cellHeight());
        cell.setClipChildren(true);
        cell.setPadding(CELL_PADDING);
        cell.setGap(2);
        cell.setCornerRadius(SceneChromeTokens.RADIUS_SM);
        cell.setHitTestable(false);

        // 顶行：图标 + 主文本 + 无效/重复徽章
        SceneNode top = SceneNode.row();
        top.setCrossAxisAlign(CrossAxisAlign.CENTER);
        top.setGap(4);
        top.setHitTestable(false);

        SceneNode icon = new SceneNode();
        icon.setPreferredWidth(ICON_SIZE).setPreferredHeight(ICON_SIZE).setHitTestable(false);
        ReadableSignal<SceneImageSource> effectiveImage = Computed.create(() -> {
            SearchPickerData.CurrentMember member = currentMember.get();
            if (member.candidate() == null
                    || unrenderableKeys.get().contains(member.candidate().key())) {
                return null;
            }
            return props.visualAdapter().candidateImage(member.candidate());
        });
        rt.bind(effectiveImage, src -> {
            icon.setBackgroundColor(src == null ? PLACEHOLDER_COLOR : SceneChromeTokens.TRANSPARENT);
            icon.setImageSource(src);
        });
        top.appendChild(icon);

        SceneNode primary = text("");
        primary.setFlexGrow(1);
        primary.setClipChildren(true);
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
        badge.setFontSize(FONT_SIZE);
        rt.bindText(badge, Computed.create(() -> Boolean.TRUE.equals(malformed.get())
                ? props.presentation().invalidMemberBadge()
                : Boolean.TRUE.equals(duplicate.get()) ? props.presentation().duplicateMemberBadge() : ""));
        rt.bindComputed(() -> Boolean.TRUE.equals(malformed.get())
                ? SceneChromeTokens.DANGER_BG_SUBTLE : SceneChromeTokens.TRANSPARENT,
                badge::setBackgroundColor);
        rt.bindComputed(() -> Boolean.TRUE.equals(duplicate.get()) ? SceneChromeTokens.WARNING_TEXT
                : SceneChromeTokens.TEXT_PRIMARY, badge::setTextColor);
        top.appendChild(badge);
        cell.appendChild(top);

        // 副文本：canonical 摘要
        SceneNode secondary = text("");
        secondary.setFontSize(FONT_SIZE);
        secondary.setTextColor(SceneChromeTokens.TEXT_SECONDARY);
        secondary.setClipChildren(true);
        rt.bindText(secondary, Computed.create(() -> props.presentation().currentMemberSecondary(
                currentMember.get())));
        cell.appendChild(secondary);

        // 底部操作：编辑 / 删除（一步直达，无二次确认）
        SceneNode actions = SceneNode.row();
        actions.setMainAxisAlign(MainAxisAlign.END);
        actions.setGap(2);
        actions.setHitTestable(false);
        SceneNode edit = SceneButton.create(rt, new SceneButton.Props(
                Computed.create(() -> props.presentation().edit()),
                props.enabled(), () -> props.onEdit().accept(Long.valueOf(memberId)))).get();
        edit.setWidthSizing(WidthSizing.SHRINK);
        actions.appendChild(edit);
        SceneNode remove = SceneButton.create(rt, new SceneButton.Props(
                Computed.create(() -> props.presentation().remove()),
                props.enabled(), () -> {
                    if (props.onRemove().test(memberId)) {
                        props.onRemoveConfirmed().run();
                    }
                })).get();
        remove.setWidthSizing(WidthSizing.SHRINK);
        actions.appendChild(remove);
        cell.appendChild(actions);

        return cell;
    }

    /** 按 registryKey（注册名:meta）反查候选 key（注册名）。 */
    private static Object candidateKeyForRegistryKey(
            List<SearchPickerData.CurrentMember> members, String registryKey) {
        String[] parts = ItemRenderFallbackKeys.splitRegistryKey(registryKey);
        if (parts == null) {
            return null;
        }
        for (SearchPickerData.CurrentMember member : members) {
            if (member.candidate() != null && parts[0].equals(member.candidate().key())) {
                return member.candidate().key();
            }
        }
        return null;
    }

    private static List<SearchPickerData.CurrentMember> safeMembers(
            ReadableSignal<? extends List<SearchPickerData.CurrentMember>> signal) {
        List<SearchPickerData.CurrentMember> members = signal.get();
        return members == null ? Collections.<SearchPickerData.CurrentMember>emptyList() : members;
    }

    private static SceneNode text(String value) {
        SceneNode node = new SceneNode();
        node.setText(value == null ? "" : value);
        node.setHitTestable(false);
        return node;
    }
}
