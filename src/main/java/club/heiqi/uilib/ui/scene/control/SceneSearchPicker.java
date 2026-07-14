package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.SearchPickerPresentation;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneEventContext;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing;
import club.heiqi.uilib.ui.scene.overlay.AnchorProvider;
import club.heiqi.uilib.ui.scene.overlay.AnchoredPortalLayout;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;

/** 通用、平台无关的受控搜索选择器。 */
public final class SceneSearchPicker {
    /** 搜索选择器浮层阶段。 */
    public enum State { CLOSED, CANDIDATES, VARIANTS }

    /** LIST_MEMBERS portal 生命周期之间可回放的焦点意图。 */
    private enum FocusIntent { NONE, MANAGE, CANDIDATES, VARIANTS }

    private static final int ICON_SIZE = 18;
    private static final int PLACEHOLDER_COLOR = 0xFF454B54;
    private static final int VISIBLE_ROWS = 8;
    private static final int CURRENT_MEMBER_ROWS = 8;
    private static final int LIST_CANDIDATE_ROWS = 12;
    private static final int ROW_HEIGHT = 34;
    private static final int MANAGE_BUTTON_WIDTH = 96;
    private static final int MEMBER_ISSUE_WIDTH = 136;
    private static final int MEMBER_ACTIONS_WIDTH = 174;
    private static final AnchoredPortalLayout LIST_MEMBERS_PORTAL_LAYOUT =
            new AnchoredPortalLayout(480, 360, 8);

    private SceneSearchPicker() { }

    /** 搜索选择器输入契约。 */
    public static final class Props {
        private final ReadableSignal<String> query;
        private final ReadableSignal<SearchPickerData.SearchResult> results;
        private final ReadableSignal<Boolean> enabled;
        private final ReadableSignal<SearchPickerData.Selection> currentSelection;
        private final Consumer<String> onQuery;
        private final Consumer<SearchPickerData.Selection> onSelect;
        private final Predicate<SearchPickerData.Selection> selectionCommit;
        private final VisualAdapter visualAdapter;
        private final SearchPickerPresentation presentation;
        private final ReadableSignal<String> error;
        private final ReadableSignal<List<SearchPickerData.CurrentMember>> currentMembers;
        private final LongConsumer onEditCurrent;
        private final LongPredicate onRemoveCurrent;
        private final Runnable onBeginAdd;
        private final Runnable onCancel;
        private final boolean listMembers;

        /** 创建受控搜索选择器属性。 */
        public Props(ReadableSignal<String> query,
                     ReadableSignal<SearchPickerData.SearchResult> results,
                     ReadableSignal<Boolean> enabled,
                     Consumer<String> onQuery,
                     Consumer<SearchPickerData.Selection> onSelect,
                     VisualAdapter visualAdapter) {
            this.query = Objects.requireNonNull(query, "query");
            this.results = Objects.requireNonNull(results, "results");
            this.enabled = Objects.requireNonNull(enabled, "enabled");
            this.currentSelection = Signal.create(null);
            this.onQuery = Objects.requireNonNull(onQuery, "onQuery");
            this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
            this.selectionCommit = selection -> { this.onSelect.accept(selection); return true; };
            this.visualAdapter = Objects.requireNonNull(visualAdapter, "visualAdapter");
            this.presentation = SearchPickerPresentation.defaultEnglish();
            this.error = Signal.create("");
            this.currentMembers = Signal.create(Collections.<SearchPickerData.CurrentMember>emptyList());
            this.onEditCurrent = ignored -> { };
            this.onRemoveCurrent = ignored -> false;
            this.onBeginAdd = () -> { };
            this.onCancel = () -> { };
            this.listMembers = false;
        }

        private Props(Builder builder) {
            query = builder.query; results = builder.results; enabled = builder.enabled;
            onQuery = builder.onQuery; onSelect = builder.onSelect; selectionCommit = builder.selectionCommit;
            visualAdapter = builder.visualAdapter;
            currentSelection = builder.currentSelection;
            presentation = builder.presentation; error = builder.error;
            currentMembers = builder.currentMembers; onEditCurrent = builder.onEditCurrent;
            onRemoveCurrent = builder.onRemoveCurrent;
            onBeginAdd = builder.onBeginAdd; onCancel = builder.onCancel; listMembers = builder.listMembers;
        }

        /** 创建保留旧六参必填项的 builder。 */
        public static Builder builder(ReadableSignal<String> query,
                                      ReadableSignal<SearchPickerData.SearchResult> results,
                                      ReadableSignal<Boolean> enabled, Consumer<String> onQuery,
                                      Consumer<SearchPickerData.Selection> onSelect,
                                      VisualAdapter visualAdapter) {
            return new Builder(query, results, enabled, onQuery, onSelect, visualAdapter);
        }

        /** 搜索选择器可选属性 builder。 */
        public static final class Builder {
            private final ReadableSignal<String> query;
            private final ReadableSignal<SearchPickerData.SearchResult> results;
            private final ReadableSignal<Boolean> enabled;
            private final Consumer<String> onQuery;
            private final Consumer<SearchPickerData.Selection> onSelect;
            private Predicate<SearchPickerData.Selection> selectionCommit;
            private final VisualAdapter visualAdapter;
            private ReadableSignal<SearchPickerData.Selection> currentSelection = Signal.create(null);
            private SearchPickerPresentation presentation = SearchPickerPresentation.defaultEnglish();
            private ReadableSignal<String> error = Signal.create("");
            private ReadableSignal<List<SearchPickerData.CurrentMember>> currentMembers =
                    Signal.create(Collections.<SearchPickerData.CurrentMember>emptyList());
            private LongConsumer onEditCurrent = ignored -> { };
            private LongPredicate onRemoveCurrent = ignored -> false;
            private Runnable onBeginAdd = () -> { };
            private Runnable onCancel = () -> { };
            private boolean listMembers;

            private Builder(ReadableSignal<String> query, ReadableSignal<SearchPickerData.SearchResult> results,
                            ReadableSignal<Boolean> enabled, Consumer<String> onQuery,
                            Consumer<SearchPickerData.Selection> onSelect, VisualAdapter visualAdapter) {
                this.query = Objects.requireNonNull(query, "query");
                this.results = Objects.requireNonNull(results, "results");
                this.enabled = Objects.requireNonNull(enabled, "enabled");
                this.onQuery = Objects.requireNonNull(onQuery, "onQuery");
                this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
                this.selectionCommit = selection -> { this.onSelect.accept(selection); return true; };
                this.visualAdapter = Objects.requireNonNull(visualAdapter, "visualAdapter");
            }

            /** 设置受控当前选择。 */
            public Builder currentSelection(ReadableSignal<SearchPickerData.Selection> value) {
                currentSelection = Objects.requireNonNull(value, "currentSelection"); return this;
            }

            /** 设置不可变领域文案。 */
            public Builder presentation(SearchPickerPresentation value) {
                presentation = Objects.requireNonNull(value, "presentation"); return this;
            }

            /** 设置本地错误信号。 */
            public Builder error(ReadableSignal<String> value) {
                error = Objects.requireNonNull(value, "error"); return this;
            }

            /** 设置可拒绝的原子提交边界；返回 false 时选择器保持展开。 */
            public Builder selectionCommit(Predicate<SearchPickerData.Selection> value) {
                selectionCommit = Objects.requireNonNull(value, "selectionCommit"); return this;
            }

            /** 启用当前列表成员区，并提供稳定 memberId 点击回调。 */
            public Builder currentMembers(ReadableSignal<List<SearchPickerData.CurrentMember>> value,
                                          LongConsumer onEdit) {
                currentMembers = Objects.requireNonNull(value, "currentMembers");
                onEditCurrent = Objects.requireNonNull(onEdit, "onEditCurrent");
                listMembers = true;
                return this;
            }

            /** 设置可拒绝的稳定成员删除提交边界。 */
            public Builder onRemoveCurrent(LongPredicate value) {
                onRemoveCurrent = Objects.requireNonNull(value, "onRemoveCurrent"); return this;
            }

            /** 设置打开候选时的新增目标回调。 */
            public Builder onBeginAdd(Runnable value) { onBeginAdd = Objects.requireNonNull(value, "onBeginAdd"); return this; }

            /** 设置取消、Escape 与 dismiss 的状态闭合回调。 */
            public Builder onCancel(Runnable value) { onCancel = Objects.requireNonNull(value, "onCancel"); return this; }

            /** 构建不可变属性。 */
            public Props build() { return new Props(this); }
        }
    }

    /** 构建搜索选择器组件。 */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            Signal<Boolean> candidatesOpen = Signal.create(Boolean.FALSE);
            Signal<Boolean> variantsOpen = Signal.create(Boolean.FALSE);
            Signal<Integer> highlighted = Signal.create(Integer.valueOf(-1));
            Signal<Integer> windowStart = Signal.create(Integer.valueOf(0));
            Signal<SearchPickerData.Candidate> activeCandidate = Signal.create(null);
            Signal<SearchPickerData.SelectionMode> mode = Signal.create(SearchPickerData.SelectionMode.ALL);
            Signal<List<String>> selectedKeys = Signal.create(Collections.<String>emptyList());
            Signal<Long> pendingDeleteMemberId = Signal.create(null);
            Signal<Boolean> addingMember = Signal.create(Boolean.FALSE);
            Signal<FocusIntent> focusIntent = Signal.create(FocusIntent.NONE);
            ReadableSignal<ListMemberIssues> memberIssues = Computed.create(() ->
                    analyzeMemberIssues(safeCurrentMembers(props)));
            SceneNode[] manageFocusTarget = new SceneNode[1];
            SceneNode[] candidateFocusTarget = new SceneNode[1];
            SceneNode[] variantFocusTarget = new SceneNode[1];

            SceneNode root = SceneNode.column();
            root.appendChild(text(props.presentation.title()));
            SceneNode anchorNode;
            if (props.listMembers) {
                SceneNode management = SceneNode.row();
                management.setGap(SceneChromeTokens.GAP_MD);
                management.setCrossAxisAlign(CrossAxisAlign.CENTER);
                SceneNode summary = SceneNode.row();
                summary.setGap(4);
                summary.setFlexGrow(1);
                SceneNode configured = text("");
                rt.bindText(configured, Computed.create(() -> props.presentation.configuredSummary(
                        safeCurrentMembers(props).size())));
                summary.appendChild(configured);
                SceneNode issues = text("");
                rt.bindText(issues, Computed.create(() -> {
                    ListMemberIssues snapshot = memberIssues.get();
                    return props.presentation.memberIssueSummary(
                            snapshot.invalidCount, snapshot.duplicateMemberIds.size());
                }));
                summary.appendChild(issues);
                Runnable openManagement = () -> {
                    props.onQuery.accept("");
                    beginAdd(props, pendingDeleteMemberId, addingMember);
                    highlighted.set(Integer.valueOf(-1));
                    windowStart.set(Integer.valueOf(0));
                    candidatesOpen.set(Boolean.TRUE);
                    variantsOpen.set(Boolean.FALSE);
                    focusIntent.set(FocusIntent.CANDIDATES);
                };
                SceneNode manage = SceneButton.create(rt, new SceneButton.Props(
                        Signal.create(props.presentation.manage()), props.enabled, openManagement)).get();
                manageFocusTarget[0] = manage;
                manage.setWidthSizing(WidthSizing.SHRINK);
                manage.setPreferredWidth(MANAGE_BUTTON_WIDTH);
                rt.on(manage, SceneEventType.KEY_DOWN, (ev, ctx) -> {
                    if (!Boolean.TRUE.equals(props.enabled.get()) || ev.getKeyAction() != SceneKeyAction.PRESSED
                            || ev.isRepeat() || ev.getKey() != SceneKey.ARROW_DOWN
                            && ev.getKey() != SceneKey.ARROW_UP) return;
                    props.onQuery.accept("");
                    beginAdd(props, pendingDeleteMemberId, addingMember);
                    int delta = ev.getKey() == SceneKey.ARROW_DOWN ? 1 : -1;
                    int next = nextHighlight(-1, delta, safeResults(props).candidates().size());
                    highlighted.set(Integer.valueOf(next));
                    windowStart.set(Integer.valueOf(windowFor(next, 0,
                            safeResults(props).candidates().size(), LIST_CANDIDATE_ROWS)));
                    candidatesOpen.set(Boolean.TRUE);
                    variantsOpen.set(Boolean.FALSE);
                    focusIntent.set(FocusIntent.CANDIDATES);
                    ctx.stopPropagation();
                });
                management.appendChild(manage);
                management.appendChild(summary);
                root.appendChild(management);
                anchorNode = manage;
            } else {
                SceneNode input = searchInput(rt, props, activeCandidate, candidatesOpen, variantsOpen,
                        highlighted, windowStart, mode, selectedKeys, pendingDeleteMemberId, addingMember, focusIntent);
                root.appendChild(input);
                SceneNode error = text("");
                rt.bindText(error, props.error);
                root.appendChild(error);
                anchorNode = input;
            }

            AnchorProvider anchor = AnchorProvider.forNode(anchorNode);
            AnchoredPortalLayout portalLayout = props.listMembers
                    ? LIST_MEMBERS_PORTAL_LAYOUT : AnchoredPortalLayout.DEFAULT;
            java.util.Set<SceneNode> protectedNodes = Collections.singleton(anchorNode);
            rt.portalAnchored(candidatesOpen,
                    () -> rememberPortalFocusTarget(candidateFocusTarget,
                            candidatePortal(rt, props, activeCandidate, candidatesOpen, variantsOpen, highlighted,
                                     windowStart, mode, selectedKeys, pendingDeleteMemberId, addingMember,
                                     focusIntent, memberIssues), true),
                     OverlayDismissPolicy.DEFAULT, () -> cancel(props, candidatesOpen, variantsOpen,
                               pendingDeleteMemberId, addingMember, focusIntent), anchor, protectedNodes, portalLayout);
            OverlayDismissPolicy variantDismissPolicy = props.listMembers
                    ? new OverlayDismissPolicy(false, true, true) : OverlayDismissPolicy.DEFAULT;
            rt.portalAnchored(variantsOpen,
                    () -> rememberPortalFocusTarget(variantFocusTarget,
                            variantPortal(rt, props, activeCandidate, candidatesOpen, variantsOpen, highlighted,
                                     windowStart, mode, selectedKeys, pendingDeleteMemberId, addingMember, focusIntent),
                             props.listMembers),
                     variantDismissPolicy, () -> cancel(props, candidatesOpen, variantsOpen,
                               pendingDeleteMemberId, addingMember, focusIntent), anchor, protectedNodes, portalLayout);
            bindFocusIntent(rt, focusIntent, manageFocusTarget, candidateFocusTarget, variantFocusTarget);
            return root;
        };
    }

    /** 构建主树或管理 portal 复用的受控搜索输入及键盘行为。 */
    private static SceneNode searchInput(SceneRuntime rt, Props props,
                                         Signal<SearchPickerData.Candidate> activeCandidate,
                                         Signal<Boolean> candidatesOpen, Signal<Boolean> variantsOpen,
                                         Signal<Integer> highlighted, Signal<Integer> windowStart,
                                         Signal<SearchPickerData.SelectionMode> mode,
                                           Signal<List<String>> selectedKeys,
                                           Signal<Long> pendingDeleteMemberId,
                                           Signal<Boolean> addingMember,
                                           Signal<FocusIntent> focusIntent) {
        SceneNode input = SceneTextInput.create(rt, SceneTextInput.Props.builder(props.query)
                .enabled(props.enabled).placeholder(props.presentation.placeholder()).onChange(value -> {
                    if (props.listMembers && pendingDeleteMemberId.get() != null) {
                        beginAdd(props, pendingDeleteMemberId, addingMember);
                    }
                    props.onQuery.accept(value);
                    highlighted.set(Integer.valueOf(-1));
                    windowStart.set(Integer.valueOf(0));
                    candidatesOpen.set(Boolean.TRUE);
                    variantsOpen.set(Boolean.FALSE);
                    if (props.listMembers) focusIntent.set(FocusIntent.CANDIDATES);
                }).build()).get();
        rt.on(input, SceneEventType.CLICK, (ev, ctx) -> {
            if (Boolean.TRUE.equals(props.enabled.get())) {
                candidatesOpen.set(Boolean.TRUE);
                variantsOpen.set(Boolean.FALSE);
                if (props.listMembers) focusIntent.set(FocusIntent.CANDIDATES);
            }
        });
        rt.on(input, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled.get()) || ev.getKeyAction() != SceneKeyAction.PRESSED
                    || ev.isRepeat()) return;
            List<SearchPickerData.Candidate> values = safeResults(props).candidates();
            SearchPickerData.Candidate active = activeCandidate.get();
            List<SearchPickerData.Variant> variants = active == null
                    ? Collections.<SearchPickerData.Variant>emptyList() : active.variants();
            if (ev.getKey() == SceneKey.ARROW_DOWN || ev.getKey() == SceneKey.ARROW_UP) {
                int delta = ev.getKey() == SceneKey.ARROW_DOWN ? 1 : -1;
                if (Boolean.TRUE.equals(variantsOpen.get())) {
                    highlighted.set(Integer.valueOf(nextHighlight(
                            highlighted.get().intValue(), delta, variants.size())));
                } else {
                    candidatesOpen.set(Boolean.TRUE);
                    variantsOpen.set(Boolean.FALSE);
                    int next = nextHighlight(highlighted.get().intValue(), delta, values.size());
                    highlighted.set(Integer.valueOf(next));
                    windowStart.set(Integer.valueOf(windowFor(next, windowStart.get().intValue(), values.size(),
                            props.listMembers ? LIST_CANDIDATE_ROWS : VISIBLE_ROWS)));
                }
                ctx.stopPropagation();
            } else if (ev.getKey() == SceneKey.ENTER && Boolean.TRUE.equals(variantsOpen.get())
                    && active != null && canConfirm(mode.get(), selectedKeys.get())) {
                if (props.selectionCommit.test(new SearchPickerData.Selection(active.key(), mode.get(),
                        orderedKeys(variants, selectedKeys.get())))) finishSelection(props, candidatesOpen,
                                variantsOpen, highlighted, pendingDeleteMemberId, addingMember, focusIntent);
                ctx.stopPropagation();
            } else if (ev.getKey() == SceneKey.SPACE && Boolean.TRUE.equals(variantsOpen.get())
                    && active != null && !variants.isEmpty()) {
                String key = variants.get(clamp(highlighted.get().intValue(), variants.size())).key();
                updateVariant(mode.get(), selectedKeys, key, !selectedKeys.get().contains(key));
                ctx.stopPropagation();
            } else if (ev.getKey() == SceneKey.ENTER && Boolean.TRUE.equals(candidatesOpen.get())
                    && highlighted.get().intValue() >= 0 && highlighted.get().intValue() < values.size()) {
                chooseCandidate(values.get(highlighted.get().intValue()), props, activeCandidate,
                        candidatesOpen, variantsOpen, highlighted, mode, selectedKeys,
                        pendingDeleteMemberId, addingMember, focusIntent);
                ctx.stopPropagation();
            } else if (ev.getKey() == SceneKey.ESCAPE
                    && (Boolean.TRUE.equals(candidatesOpen.get()) || Boolean.TRUE.equals(variantsOpen.get()))) {
                if (props.listMembers && Boolean.TRUE.equals(variantsOpen.get())) {
                    returnToCandidates(candidatesOpen, variantsOpen, pendingDeleteMemberId, focusIntent);
                } else {
                    cancel(props, candidatesOpen, variantsOpen, pendingDeleteMemberId, addingMember, focusIntent);
                }
                ctx.stopPropagation();
            }
        });
        return input;
    }

    private static SceneNode candidatePortal(SceneRuntime rt, Props props,
                                               Signal<SearchPickerData.Candidate> activeCandidate,
                                                Signal<Boolean> candidatesOpen, Signal<Boolean> variantsOpen,
                                                Signal<Integer> highlighted,
                                                  Signal<Integer> windowStart,
                                                  Signal<SearchPickerData.SelectionMode> mode,
                                                Signal<List<String>> selectedKeys,
                                                Signal<Long> pendingDeleteMemberId,
                                                Signal<Boolean> addingMember,
                                                Signal<FocusIntent> focusIntent,
                                                  ReadableSignal<ListMemberIssues> memberIssues) {
        if (props.listMembers) return listMembersCandidatePortal(rt, props, activeCandidate,
                candidatesOpen, variantsOpen, highlighted, windowStart, mode, selectedKeys,
                pendingDeleteMemberId, addingMember, focusIntent, memberIssues);
        SceneNode list = portalRoot(rt, props.listMembers);
        SceneNode itemsContainer = SceneNode.column();
        itemsContainer.setPreferredHeight(VISIBLE_ROWS * ROW_HEIGHT);
        itemsContainer.setClipChildren(true);
        SceneNode footerContainer = SceneNode.column();
        itemsContainer.setWidthSizing(WidthSizing.SHRINK);
        footerContainer.setWidthSizing(WidthSizing.SHRINK);
        list.appendChild(itemsContainer);
        list.appendChild(footerContainer);
        ReadableSignal<List<SearchPickerData.Candidate>> items = Computed.create(() -> window(
                safeResults(props).candidates(), windowStart.get().intValue(), VISIBLE_ROWS));
        rt.forEach(itemsContainer, items, SearchPickerData.Candidate::key, candidate -> item(rt,
                props.visualAdapter.candidateImage(candidate), props.visualAdapter.candidateLabel(candidate),
                Computed.create(() -> Integer.valueOf(indexOf(safeResults(props).candidates(), candidate.key()))
                        .equals(highlighted.get())), () ->
                         chooseCandidate(candidate, props, activeCandidate, candidatesOpen, variantsOpen, highlighted,
                                 mode, selectedKeys, pendingDeleteMemberId, addingMember, focusIntent)));
        rt.on(itemsContainer, SceneEventType.SCROLL, (ev, ctx) -> {
            int rows = VISIBLE_ROWS;
            int max = Math.max(0, safeResults(props).candidates().size() - rows);
            int direction = ev.getWheelDelta() < 0 ? 1 : ev.getWheelDelta() > 0 ? -1 : 0;
            int next = Math.max(0, Math.min(max, windowStart.get().intValue() + direction));
            if (next != windowStart.get().intValue()) {
                windowStart.set(Integer.valueOf(next));
                ctx.stopPropagation();
            }
        });
        rt.show(footerContainer, Computed.create(() -> Boolean.valueOf(safeResults(props).candidates().isEmpty())),
                () -> text(props.presentation.empty()));
        rt.show(footerContainer, Computed.create(() -> Boolean.valueOf(!safeResults(props).candidates().isEmpty())),
                () -> {
                    SceneNode summary = text("");
                    rt.bindText(summary, Computed.create(() -> props.presentation.resultSummary(
                            safeResults(props).candidates().size())));
                    return summary;
                });
        rt.show(footerContainer, Computed.create(() -> Boolean.valueOf(safeResults(props).truncated())),
                () -> text(props.presentation.truncated()));
        return list;
    }

    /** 构建 LIST_MEMBERS 管理 portal，搜索输入与两个动态高度分区按固定物理顺序排列。 */
    private static SceneNode listMembersCandidatePortal(SceneRuntime rt, Props props,
                                                         Signal<SearchPickerData.Candidate> activeCandidate,
                                                         Signal<Boolean> candidatesOpen,
                                                         Signal<Boolean> variantsOpen,
                                                         Signal<Integer> highlighted,
                                                         Signal<Integer> windowStart,
                                                          Signal<SearchPickerData.SelectionMode> mode,
                                                           Signal<List<String>> selectedKeys,
                                                           Signal<Long> pendingDeleteMemberId,
                                                           Signal<Boolean> addingMember,
                                                           Signal<FocusIntent> focusIntent,
                                                           ReadableSignal<ListMemberIssues> memberIssues) {
        SceneNode list = portalRoot(rt, true);
        SceneNode input = searchInput(rt, props, activeCandidate, candidatesOpen, variantsOpen,
                highlighted, windowStart, mode, selectedKeys, pendingDeleteMemberId, addingMember, focusIntent);
        list.appendChild(input);

        SceneNode currentTitle = text("");
        rt.bindText(currentTitle, Computed.create(() -> props.presentation.currentMembersTitle(
                safeCurrentMembers(props).size())));
        list.appendChild(currentTitle);
        SceneNode currentRows = currentMembersRows(rt, props, activeCandidate, candidatesOpen,
                variantsOpen, highlighted, mode, selectedKeys, pendingDeleteMemberId, addingMember,
                focusIntent, memberIssues);
        list.appendChild(currentRows);

        SceneNode resultsTitle = text("");
        rt.bindText(resultsTitle, Computed.create(() -> props.presentation.searchResultsTitle(
                safeResults(props).candidates().size())));
        list.appendChild(resultsTitle);
        SceneNode resultSection = SceneNode.column();
        resultSection.setWidthSizing(WidthSizing.SHRINK);
        resultSection.setClipChildren(true);
        resultSection.setScrollable(true);
        SceneScrolls.attach(rt, resultSection);
        bindScrollClamp(rt, windowStart, Computed.create(() -> Integer.valueOf(
                Math.max(0, safeResults(props).candidates().size() - LIST_CANDIDATE_ROWS))));
        rt.bind(Computed.create(() -> Integer.valueOf(sectionHeight(
                safeResults(props).candidates().size(), LIST_CANDIDATE_ROWS))),
                height -> resultSection.setPreferredHeight(height.intValue()));
        SceneNode resultRows = SceneNode.column();
        resultRows.setWidthSizing(WidthSizing.SHRINK);
        resultSection.appendChild(resultRows);
        ReadableSignal<List<SearchPickerData.Candidate>> shown = Computed.create(() -> window(
                safeResults(props).candidates(), windowStart.get().intValue(), LIST_CANDIDATE_ROWS));
        rt.forEach(resultRows, shown, SearchPickerData.Candidate::key, candidate -> item(rt,
                props.visualAdapter.candidateImage(candidate), props.visualAdapter.candidateLabel(candidate),
                Computed.create(() -> Integer.valueOf(indexOf(safeResults(props).candidates(), candidate.key()))
                        .equals(highlighted.get())), () -> chooseCandidate(candidate, props, activeCandidate,
                        candidatesOpen, variantsOpen, highlighted, mode, selectedKeys,
                        pendingDeleteMemberId, addingMember, focusIntent)));
        rt.show(resultSection, Computed.create(() -> Boolean.valueOf(
                safeResults(props).candidates().isEmpty())),
                () -> emptyRow(props.presentation.emptySearchResults()));
        rt.on(resultSection, SceneEventType.SCROLL, (ev, ctx) -> scrollWindow(
                ev.getWheelDelta(), safeResults(props).candidates().size(), LIST_CANDIDATE_ROWS,
                windowStart, ctx));
        list.appendChild(resultSection);

        SceneNode error = text("");
        rt.bindText(error, props.error);
        list.appendChild(error);
        return list;
    }

    private static SceneNode variantPortal(SceneRuntime rt, Props props,
                                            Signal<SearchPickerData.Candidate> activeCandidate,
                                            Signal<Boolean> candidatesOpen, Signal<Boolean> variantsOpen,
                                             Signal<Integer> highlighted, Signal<Integer> windowStart,
                                             Signal<SearchPickerData.SelectionMode> mode,
                                              Signal<List<String>> selectedKeys,
                                              Signal<Long> pendingDeleteMemberId,
                                              Signal<Boolean> addingMember,
                                              Signal<FocusIntent> focusIntent) {
        SceneNode list = portalRoot(rt, props.listMembers);
        SceneNode search = null;
        if (props.listMembers) {
            search = searchInput(rt, props, activeCandidate, candidatesOpen, variantsOpen,
                    highlighted, windowStart, mode, selectedKeys, pendingDeleteMemberId, addingMember, focusIntent);
            list.appendChild(search);
        }
        SceneNode modes = SceneSegmented.create(rt, new SceneSegmented.Props(
                Computed.create(() -> Integer.valueOf(mode.get().ordinal())),
                Arrays.asList(props.presentation.all(), props.presentation.selected()),
                props.enabled, index -> {
                    SearchPickerData.Candidate candidate = activeCandidate.get();
                    if (candidate == null) return;
                    SearchPickerData.SelectionMode next = SearchPickerData.SelectionMode.values()[index.intValue()];
                    mode.set(next);
                })).get();
        list.appendChild(modes);
        SceneNode itemsContainer = SceneNode.column();
        itemsContainer.setWidthSizing(WidthSizing.SHRINK);
        list.appendChild(itemsContainer);
        ReadableSignal<List<SearchPickerData.Variant>> items = Computed.create(() -> {
            SearchPickerData.Candidate candidate = activeCandidate.get();
            return candidate == null ? Collections.<SearchPickerData.Variant>emptyList()
                    : displayVariants(candidate.variants(), selectedKeys.get(), props.presentation);
        });
        rt.forEach(itemsContainer, items, SearchPickerData.Variant::key, variant -> variantItem(rt,
                props.visualAdapter.variantImage(variant), props.visualAdapter.variantLabel(variant),
                Computed.create(() -> Boolean.valueOf(selectedKeys.get().contains(variant.key()))),
                Computed.create(() -> Boolean.valueOf(mode.get() != SearchPickerData.SelectionMode.ALL)),
                checked -> updateVariant(mode.get(), selectedKeys, variant.key(), checked)));
        SceneNode actions = SceneNode.row();
        actions.setGap(SceneChromeTokens.GAP_MD);
        SceneNode cancel = SceneButton.create(rt, new SceneButton.Props(Signal.create(props.presentation.cancel()),
                Signal.create(Boolean.TRUE), () -> cancel(props, candidatesOpen, variantsOpen,
                        pendingDeleteMemberId, addingMember, focusIntent))).get();
        cancel.setWidthSizing(WidthSizing.SHRINK);
        actions.appendChild(cancel);
        SceneNode confirm = SceneButton.create(rt, new SceneButton.Props(Signal.create(props.presentation.confirm()),
                Computed.create(() -> Boolean.valueOf(canConfirm(mode.get(), selectedKeys.get()))), () -> {
                    SearchPickerData.Candidate candidate = activeCandidate.get();
                    if (candidate != null && props.selectionCommit.test(new SearchPickerData.Selection(
                            candidate.key(), mode.get(), orderedKeys(candidate.variants(), selectedKeys.get())))) {
                        finishSelection(props, candidatesOpen, variantsOpen, highlighted,
                                pendingDeleteMemberId, addingMember, focusIntent);
                    }
                })).get();
        confirm.setWidthSizing(WidthSizing.SHRINK);
        actions.appendChild(confirm);
        list.appendChild(actions);
        if (props.listMembers) {
            rt.on(list, SceneEventType.KEY_DOWN, (ev, ctx) -> {
                if (ev.getKeyAction() == SceneKeyAction.PRESSED && !ev.isRepeat()
                        && ev.getKey() == SceneKey.ESCAPE && Boolean.TRUE.equals(variantsOpen.get())) {
                    returnToCandidates(candidatesOpen, variantsOpen, pendingDeleteMemberId, focusIntent);
                    ctx.stopPropagation();
                }
            });
        }
        return list;
    }

    /** 构建管理 portal 的 keyed 当前成员区。 */
    private static SceneNode currentMembersRows(SceneRuntime rt, Props props,
                                                Signal<SearchPickerData.Candidate> activeCandidate,
                                                Signal<Boolean> candidatesOpen, Signal<Boolean> variantsOpen,
                                                 Signal<Integer> highlighted,
                                                 Signal<SearchPickerData.SelectionMode> mode,
                                                   Signal<List<String>> selectedKeys,
                                                   Signal<Long> pendingDeleteMemberId,
                                                   Signal<Boolean> addingMember,
                                                   Signal<FocusIntent> focusIntent,
                                                  ReadableSignal<ListMemberIssues> memberIssues) {
        SceneNode section = SceneNode.column();
        section.setClipChildren(true);
        section.setScrollable(true);
        Signal<Integer> scrollOffset = SceneScrolls.attach(rt, section);
        bindScrollClamp(rt, scrollOffset, Computed.create(() -> Integer.valueOf(
                Math.max(0, safeCurrentMembers(props).size() - CURRENT_MEMBER_ROWS) * ROW_HEIGHT)));
        rt.bind(Computed.create(() -> Integer.valueOf(sectionHeight(
                safeCurrentMembers(props).size(), CURRENT_MEMBER_ROWS))),
                height -> section.setPreferredHeight(height.intValue()));
        SceneNode rows = SceneNode.column();
        section.appendChild(rows);
        ReadableSignal<List<SearchPickerData.CurrentMember>> shown = Computed.create(() -> safeCurrentMembers(props));
        rt.forEach(rows, shown, SearchPickerData.CurrentMember::memberId, member -> {
            long memberId = member.memberId();
            ReadableSignal<SearchPickerData.CurrentMember> currentMember = Computed.create(() ->
                    currentMemberById(props, memberId, member));
            return currentMemberRow(rt, props, memberId, member, currentMember,
                    pendingDeleteMemberId, memberIssues, () -> {
                    SearchPickerData.CurrentMember current = currentMember.get();
                    pendingDeleteMemberId.set(null);
                    addingMember.set(Boolean.FALSE);
                    props.onEditCurrent.accept(memberId);
                    SearchPickerData.Candidate candidate = current.candidate();
                    if (candidate != null && !candidate.variants().isEmpty()) {
                        SearchPickerData.Selection selection = current.selection();
                        mode.set(selection == null ? SearchPickerData.SelectionMode.ALL : selection.mode());
                        selectedKeys.set(selection == null ? Collections.<String>emptyList()
                                : immutableKeys(selection.variantKeys()));
                        activeCandidate.set(candidate);
                        highlighted.set(Integer.valueOf(0));
                        candidatesOpen.set(Boolean.FALSE);
                        variantsOpen.set(Boolean.TRUE);
                        focusIntent.set(FocusIntent.VARIANTS);
                    } else {
                        activeCandidate.set(null);
                        mode.set(SearchPickerData.SelectionMode.ALL);
                        selectedKeys.set(Collections.<String>emptyList());
                        highlighted.set(Integer.valueOf(-1));
                        candidatesOpen.set(Boolean.TRUE);
                        variantsOpen.set(Boolean.FALSE);
                        focusIntent.set(FocusIntent.CANDIDATES);
                    }
                });
        });
        rt.show(section, Computed.create(() -> Boolean.valueOf(safeCurrentMembers(props).isEmpty())),
                () -> emptyRow(props.presentation.emptyCurrentMembers()));
        return section;
    }

    /** 创建拥有独立编辑/删除交互根的当前成员行。 */
    private static SceneNode currentMemberRow(SceneRuntime rt, Props props,
                                               long memberId,
                                               SearchPickerData.CurrentMember initialMember,
                                               ReadableSignal<SearchPickerData.CurrentMember> currentMember,
                                               Signal<Long> pendingDeleteMemberId,
                                               ReadableSignal<ListMemberIssues> memberIssues,
                                               Runnable editAction) {
        SceneNode row = SceneNode.row();
        row.setWidthSizing(WidthSizing.SHRINK);
        row.setCrossAxisAlign(CrossAxisAlign.CENTER);
        row.setGap(2);
        row.setPadding(2);
        row.setPreferredHeight(ROW_HEIGHT);
        SceneNode icon = new SceneNode();
        icon.setPreferredWidth(ICON_SIZE).setPreferredHeight(ICON_SIZE).setHitTestable(false);
        SceneImageSource image = initialMember.candidate() == null ? null
                : props.visualAdapter.candidateImage(initialMember.candidate());
        if (image == null) icon.setBackgroundColor(PLACEHOLDER_COLOR); else icon.setImageSource(image);
        row.appendChild(icon);
        SceneNode label = text("");
        rt.bindText(label, Computed.create(() -> props.presentation.currentMember(currentMember.get())));
        label.setFlexGrow(1);
        row.appendChild(label);

        ReadableSignal<Boolean> malformed = Computed.create(() ->
                Boolean.valueOf(currentMember.get().selection() == null));
        ReadableSignal<Boolean> duplicate = Computed.create(() -> Boolean.valueOf(
                !Boolean.TRUE.equals(malformed.get())
                        && memberIssues.get().duplicateMemberIds.contains(Long.valueOf(memberId))));
        SceneNode issueBadge = text("");
        issueBadge.setWidthSizing(WidthSizing.SHRINK);
        rt.bindComputed(() -> Boolean.TRUE.equals(malformed.get())
                        || Boolean.TRUE.equals(duplicate.get()) ? MEMBER_ISSUE_WIDTH : 0,
                issueBadge::setPreferredWidth);
        rt.bindText(issueBadge, Computed.create(() -> Boolean.TRUE.equals(malformed.get())
                ? props.presentation.invalidMemberBadge()
                : Boolean.TRUE.equals(duplicate.get()) ? props.presentation.duplicateMemberBadge() : ""));
        rt.bindComputed(() -> Boolean.TRUE.equals(malformed.get())
                        ? SceneChromeTokens.DANGER_BG_SUBTLE : 0x00000000,
                issueBadge::setBackgroundColor);
        rt.bindComputed(() -> Boolean.TRUE.equals(duplicate.get()) ? SceneChromeTokens.WARNING_TEXT
                : SceneChromeTokens.TEXT_PRIMARY, issueBadge::setTextColor);
        ReadableSignal<Boolean> pending = Computed.create(() -> Boolean.valueOf(
                pendingDeleteMemberId.get() != null
                        && pendingDeleteMemberId.get().longValue() == memberId));
        SceneNode actions = SceneNode.row();
        actions.setGap(2);
        actions.setPreferredWidth(MEMBER_ACTIONS_WIDTH);
        actions.appendChild(actionButton(rt, Computed.create(() -> Boolean.TRUE.equals(pending.get())
                ? props.presentation.cancelRemove() : props.presentation.edit()), () -> {
            if (Boolean.TRUE.equals(pending.get())) pendingDeleteMemberId.set(null); else editAction.run();
        }));
        actions.appendChild(actionButton(rt, Computed.create(() -> Boolean.TRUE.equals(pending.get())
                ? props.presentation.confirmRemove() : props.presentation.remove()), () -> {
            if (!Boolean.TRUE.equals(pending.get())) {
                pendingDeleteMemberId.set(Long.valueOf(memberId));
            } else if (props.onRemoveCurrent.test(memberId)) {
                pendingDeleteMemberId.set(null);
            }
        }));
        row.appendChild(actions);
        row.appendChild(issueBadge);
        rt.on(row, SceneEventType.CLICK, (ev, ctx) -> {
            editAction.run();
            ctx.stopPropagation();
        });
        return row;
    }

    private static SceneNode actionButton(SceneRuntime rt, String label, Runnable action) {
        return actionButton(rt, Signal.create(label), action);
    }

    private static SceneNode actionButton(SceneRuntime rt, ReadableSignal<String> label, Runnable action) {
        SceneNode button = SceneButton.create(rt, new SceneButton.Props(
                label, Signal.create(Boolean.TRUE), action)).get();
        rt.on(button, SceneEventType.CLICK, (ev, ctx) -> ctx.stopPropagation());
        button.setWidthSizing(WidthSizing.SHRINK);
        button.setPadding(2);
        return button;
    }

    private static SceneNode item(SceneRuntime rt, SceneImageSource image, String label,
                                  ReadableSignal<Boolean> keyboardHighlighted, Runnable activate) {
        SceneNode item = SceneNode.row();
        item.setWidthSizing(WidthSizing.SHRINK);
        item.setCrossAxisAlign(CrossAxisAlign.CENTER);
        item.setGap(SceneChromeTokens.GAP_MD);
        item.setPadding(SceneChromeTokens.PAD_MD);
        item.setPreferredHeight(ROW_HEIGHT);
        SceneInteractionState interaction = rt.interactionState(item);
        SceneControlChrome.bindSelectableBackground(rt, item, Signal.create(Boolean.TRUE),
                keyboardHighlighted, interaction);
        SceneNode icon = new SceneNode();
        icon.setPreferredWidth(ICON_SIZE).setPreferredHeight(ICON_SIZE).setHitTestable(false);
        if (image == null) icon.setBackgroundColor(PLACEHOLDER_COLOR); else icon.setImageSource(image);
        SceneNode text = text(label);
        item.appendChild(icon);
        item.appendChild(text);
        rt.on(item, SceneEventType.CLICK, (ev, ctx) -> { activate.run(); ctx.stopPropagation(); });
        return item;
    }

    /** 创建整行唯一承接 hover/click 的变体项，所有可见子节点仅作装饰。 */
    private static SceneNode variantItem(SceneRuntime rt, SceneImageSource image, String label,
                                         ReadableSignal<Boolean> checked, ReadableSignal<Boolean> enabled,
                                         Consumer<Boolean> onChange) {
        SceneNode row = SceneNode.row();
        row.setWidthSizing(WidthSizing.SHRINK);
        row.setCrossAxisAlign(CrossAxisAlign.CENTER);
        row.setGap(SceneChromeTokens.GAP_MD);
        row.setPadding(SceneChromeTokens.PAD_MD);
        SceneInteractionState interaction = rt.interactionState(row);
        SceneControlChrome.bindSelectableBackground(rt, row, enabled, checked, interaction);
        SceneNode icon = new SceneNode();
        icon.setPreferredWidth(ICON_SIZE).setPreferredHeight(ICON_SIZE).setHitTestable(false);
        if (image == null) icon.setBackgroundColor(PLACEHOLDER_COLOR); else icon.setImageSource(image);
        SceneNode indicator = new SceneNode();
        indicator.setPreferredWidth(16).setPreferredHeight(16).setBorderWidth(1).setHitTestable(false);
        rt.bindComputed(() -> Boolean.TRUE.equals(checked.get()) ? SceneChromeTokens.TEXT_ON_ACCENT
                : PLACEHOLDER_COLOR, indicator::setBackgroundColor);
        row.appendChild(icon);
        row.appendChild(text(label));
        row.appendChild(indicator);
        rt.on(row, SceneEventType.CLICK, (ev, ctx) -> {
            if (Boolean.TRUE.equals(enabled.get())) onChange.accept(!Boolean.TRUE.equals(checked.get()));
            ctx.stopPropagation();
        });
        return row;
    }

    private static SceneNode portalRoot(SceneRuntime rt, boolean constrained) {
        SceneNode list = SceneNode.column();
        if (constrained) {
            list.setWidthSizing(WidthSizing.FILL);
            list.setScrollable(true);
            list.setClipChildren(true);
            list.setGap(SceneChromeTokens.GAP_MD);
            list.setPadding(SceneChromeTokens.PAD_MD);
            SceneScrolls.attach(rt, list);
        } else {
            list.setWidthSizing(WidthSizing.SHRINK);
        }
        list.setBackgroundColor(SceneStateColors.inputBackground(true));
        list.setBorderWidth(1);
        list.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);
        return list;
    }

    private static SceneNode text(String value) {
        SceneNode text = new SceneNode();
        text.setText(value == null ? "" : value);
        text.setHitTestable(false);
        return text;
    }

    /** 构建占据一行高度的非交互空态。 */
    private static SceneNode emptyRow(String value) {
        SceneNode row = text(value);
        row.setPreferredHeight(ROW_HEIGHT);
        return row;
    }

    private static void chooseCandidate(SearchPickerData.Candidate candidate, Props props,
                                        Signal<SearchPickerData.Candidate> activeCandidate,
                                        Signal<Boolean> candidatesOpen, Signal<Boolean> variantsOpen,
                                        Signal<Integer> highlighted,
                                         Signal<SearchPickerData.SelectionMode> mode,
                                         Signal<List<String>> selectedKeys,
                                         Signal<Long> pendingDeleteMemberId,
                                         Signal<Boolean> addingMember,
                                         Signal<FocusIntent> focusIntent) {
        if (candidate.variants().isEmpty()) {
            if (props.selectionCommit.test(new SearchPickerData.Selection(candidate.key(),
                    SearchPickerData.SelectionMode.ALL, Collections.<String>emptyList()))) {
                finishSelection(props, candidatesOpen, variantsOpen, highlighted,
                        pendingDeleteMemberId, addingMember, focusIntent);
            }
        } else {
            pendingDeleteMemberId.set(null);
            SearchPickerData.Selection current = props.currentSelection.get();
            boolean restore = current != null && candidate.key().equals(current.candidateKey());
            mode.set(restore ? current.mode() : SearchPickerData.SelectionMode.ALL);
            selectedKeys.set(restore ? immutableKeys(current.variantKeys())
                    : Collections.<String>emptyList());
            activeCandidate.set(candidate);
            highlighted.set(Integer.valueOf(0));
            candidatesOpen.set(Boolean.FALSE);
            variantsOpen.set(Boolean.TRUE);
            if (props.listMembers) focusIntent.set(FocusIntent.VARIANTS);
        }
    }

    private static void updateVariant(SearchPickerData.SelectionMode mode, Signal<List<String>> keys,
                                      String key, Boolean checked) {
        if (mode == SearchPickerData.SelectionMode.ALL) return;
        ArrayList<String> next = new ArrayList<String>(keys.get());
        if (Boolean.TRUE.equals(checked)) { if (!next.contains(key)) next.add(key); } else next.remove(key);
        keys.set(Collections.unmodifiableList(next));
    }

    private static boolean canConfirm(SearchPickerData.SelectionMode mode, List<String> keys) {
        return mode == SearchPickerData.SelectionMode.ALL || !keys.isEmpty();
    }

    private static List<String> orderedKeys(List<SearchPickerData.Variant> variants, List<String> keys) {
        ArrayList<String> ordered = new ArrayList<String>();
        for (SearchPickerData.Variant variant : variants) if (keys.contains(variant.key())) ordered.add(variant.key());
        for (String key : keys) if (!ordered.contains(key)) ordered.add(key);
        return ordered;
    }

    private static List<SearchPickerData.Variant> displayVariants(List<SearchPickerData.Variant> variants,
                                                                   List<String> keys,
                                                                   SearchPickerPresentation presentation) {
        ArrayList<SearchPickerData.Variant> displayed = new ArrayList<SearchPickerData.Variant>(variants);
        ArrayList<String> known = new ArrayList<String>();
        for (SearchPickerData.Variant variant : variants) known.add(variant.key());
        for (String key : keys) if (!known.contains(key)) {
            displayed.add(new SearchPickerData.Variant(key, presentation.unavailableVariant(key)));
        }
        return Collections.unmodifiableList(displayed);
    }

    private static List<String> immutableKeys(List<String> keys) {
        return Collections.unmodifiableList(new ArrayList<String>(keys));
    }

    private static SearchPickerData.SearchResult safeResults(Props props) {
        SearchPickerData.SearchResult value = props.results.get();
        return value == null ? SearchPickerData.SearchResult.empty() : value;
    }

    private static List<SearchPickerData.CurrentMember> safeCurrentMembers(Props props) {
        List<SearchPickerData.CurrentMember> value = props.currentMembers.get();
        return value == null ? Collections.<SearchPickerData.CurrentMember>emptyList() : value;
    }

    /** 按稳定 id 读取 keyed 行当前对应的成员快照。 */
    private static SearchPickerData.CurrentMember currentMemberById(
            Props props, long memberId, SearchPickerData.CurrentMember fallback) {
        for (SearchPickerData.CurrentMember member : safeCurrentMembers(props)) {
            if (member.memberId() == memberId) return member;
        }
        return fallback;
    }

    /**
     * 统计仅供展示的成员问题；malformed 不进入重复计算，重复数按成员数而非 key 组数计。
     */
    private static ListMemberIssues analyzeMemberIssues(List<SearchPickerData.CurrentMember> members) {
        Map<String, Integer> keyCounts = new HashMap<String, Integer>();
        int invalidCount = 0;
        for (SearchPickerData.CurrentMember member : members) {
            if (member.selection() == null) {
                invalidCount++;
            } else {
                String key = member.selection().candidateKey();
                Integer count = keyCounts.get(key);
                keyCounts.put(key, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
            }
        }
        Set<Long> duplicateMemberIds = new HashSet<Long>();
        for (SearchPickerData.CurrentMember member : members) {
            if (member.selection() != null
                    && keyCounts.get(member.selection().candidateKey()).intValue() > 1) {
                duplicateMemberIds.add(Long.valueOf(member.memberId()));
            }
        }
        return new ListMemberIssues(invalidCount, duplicateMemberIds);
    }

    /** 单次响应式统计得到的只读成员问题快照。 */
    private static final class ListMemberIssues {
        private final int invalidCount;
        private final Set<Long> duplicateMemberIds;

        private ListMemberIssues(int invalidCount, Set<Long> duplicateMemberIds) {
            this.invalidCount = invalidCount;
            this.duplicateMemberIds = Collections.unmodifiableSet(duplicateMemberIds);
        }
    }

    private static int sectionHeight(int count, int cap) {
        return Math.max(1, Math.min(count, cap)) * ROW_HEIGHT;
    }

    /** 数据收缩时经 owner-scoped effect 将局部滚动信号回夹，增长时保留仍合法的位置。 */
    private static void bindScrollClamp(SceneRuntime rt, Signal<Integer> scroll,
                                        ReadableSignal<Integer> maxScroll) {
        rt.bind(Computed.create(() -> Integer.valueOf(Math.max(0,
                Math.min(maxScroll.get().intValue(), scroll.get().intValue())))), clamped -> {
            if (!clamped.equals(scroll.get())) scroll.set(clamped);
        });
    }

    private static void scrollWindow(int wheelDelta, int size, int rows,
                                     Signal<Integer> windowStart, SceneEventContext ctx) {
        int max = Math.max(0, size - rows);
        int direction = wheelDelta < 0 ? 1 : wheelDelta > 0 ? -1 : 0;
        int next = Math.max(0, Math.min(max, windowStart.get().intValue() + direction));
        if (next != windowStart.get().intValue()) {
            windowStart.set(Integer.valueOf(next));
            ctx.stopPropagation();
        }
    }

    private static int clamp(int value, int size) {
        return size == 0 ? 0 : Math.max(0, Math.min(size - 1, value));
    }

    private static int nextHighlight(int current, int delta, int size) {
        if (size == 0) return -1;
        if (current < 0) return delta > 0 ? 0 : size - 1;
        return clamp(current + delta, size);
    }

    private static int windowFor(int highlighted, int currentStart, int size, int visibleRows) {
        if (highlighted < 0) return Math.min(currentStart, Math.max(0, size - visibleRows));
        if (highlighted < currentStart) return highlighted;
        if (highlighted >= currentStart + visibleRows) return highlighted - visibleRows + 1;
        return Math.min(currentStart, Math.max(0, size - visibleRows));
    }

    private static <T> List<T> window(List<T> values, int start, int visibleRows) {
        int from = Math.max(0, Math.min(start, Math.max(0, values.size() - visibleRows)));
        return values.subList(from, Math.min(values.size(), from + visibleRows));
    }

    private static int indexOf(List<SearchPickerData.Candidate> values, String key) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).key().equals(key)) return i;
        return -1;
    }

    /** 记录 portal 顶部控件，并在该 portal Owner 卸载时清理陈旧节点引用。 */
    private static SceneNode rememberPortalFocusTarget(SceneNode[] holder, SceneNode portal,
                                                       boolean firstChild) {
        SceneNode target = portal;
        if (firstChild && !portal.__getChildren().isEmpty()) {
            target = portal.__getChildren().get(0);
        }
        holder[0] = target;
        Owner owner = Owner.current();
        if (owner != null) {
            SceneNode remembered = target;
            owner.onCleanup(() -> {
                if (holder[0] == remembered) holder[0] = null;
            });
        }
        return portal;
    }

    /**
     * 在组件 Owner 下消费独立焦点意图；portal effect 先完成注册，本 effect 再请求权威焦点。
     * 初始 NONE 保证组件与 portal builder 阶段均不改变焦点。
     */
    private static void bindFocusIntent(SceneRuntime rt, Signal<FocusIntent> intent,
                                        SceneNode[] manage, SceneNode[] candidates, SceneNode[] variants) {
        rt.bind(intent, value -> {
            SceneNode target = null;
            if (value == FocusIntent.MANAGE) target = manage[0];
            else if (value == FocusIntent.CANDIDATES) target = candidates[0];
            else if (value == FocusIntent.VARIANTS) target = variants[0];
            if (target != null && rt.requestFocus(target)) intent.set(FocusIntent.NONE);
        });
    }

    /** 关闭全部 picker portal，并清除 LIST_MEMBERS 临时态。 */
    private static void close(Props props, Signal<Boolean> candidatesOpen, Signal<Boolean> variantsOpen,
                              Signal<Long> pendingDeleteMemberId, Signal<Boolean> addingMember,
                              Signal<FocusIntent> focusIntent) {
        pendingDeleteMemberId.set(null);
        addingMember.set(Boolean.FALSE);
        candidatesOpen.set(Boolean.FALSE);
        variantsOpen.set(Boolean.FALSE);
        if (props.listMembers) focusIntent.set(FocusIntent.MANAGE);
    }

    /** LIST_MEMBERS variants 的 Escape 只退回 candidates，并恢复其顶部搜索焦点。 */
    private static void returnToCandidates(Signal<Boolean> candidatesOpen, Signal<Boolean> variantsOpen,
                                           Signal<Long> pendingDeleteMemberId,
                                           Signal<FocusIntent> focusIntent) {
        pendingDeleteMemberId.set(null);
        candidatesOpen.set(Boolean.TRUE);
        variantsOpen.set(Boolean.FALSE);
        focusIntent.set(FocusIntent.CANDIDATES);
    }

    private static void beginAdd(Props props, Signal<Long> pendingDeleteMemberId,
                                 Signal<Boolean> addingMember) {
        pendingDeleteMemberId.set(null);
        addingMember.set(Boolean.TRUE);
        props.onBeginAdd.run();
    }

    /** 新增成功后留在候选 portal 并重新武装；编辑与普通 picker 沿用关闭行为。 */
    private static void finishSelection(Props props, Signal<Boolean> candidatesOpen,
                                        Signal<Boolean> variantsOpen, Signal<Integer> highlighted,
                                        Signal<Long> pendingDeleteMemberId, Signal<Boolean> addingMember,
                                        Signal<FocusIntent> focusIntent) {
        if (props.listMembers && Boolean.TRUE.equals(addingMember.get())) {
            beginAdd(props, pendingDeleteMemberId, addingMember);
            highlighted.set(Integer.valueOf(-1));
            candidatesOpen.set(Boolean.TRUE);
            variantsOpen.set(Boolean.FALSE);
            focusIntent.set(FocusIntent.CANDIDATES);
            return;
        }
        close(props, candidatesOpen, variantsOpen, pendingDeleteMemberId, addingMember, focusIntent);
    }

    private static void cancel(Props props, Signal<Boolean> candidatesOpen, Signal<Boolean> variantsOpen,
                               Signal<Long> pendingDeleteMemberId, Signal<Boolean> addingMember,
                               Signal<FocusIntent> focusIntent) {
        if (props.listMembers) props.onQuery.accept("");
        props.onCancel.run();
        close(props, candidatesOpen, variantsOpen, pendingDeleteMemberId, addingMember, focusIntent);
    }
}
