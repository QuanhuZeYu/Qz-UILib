package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.config.schema.SearchPickerSpec;
import club.heiqi.config.schema.ValueSpec;
import club.heiqi.config.ui.editor.CategorizedValueEditorProvider;
import club.heiqi.config.ui.editor.CurrentValuePresenter;
import club.heiqi.config.ui.editor.ListMemberCodec;
import club.heiqi.config.ui.editor.Registry;
import club.heiqi.config.ui.editor.SearchPickerCategories;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.SearchPickerPresentation;
import club.heiqi.config.ui.editor.ValueEditorProvider;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel;
import club.heiqi.uilib.ui.scene.control.SceneSimpleList;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * 将 ValueSpec 搜索选择器元数据装配为受控场景行触发器与居中 70% {@link ScenePickerPanel}。
 *
 * <p>字段行不再内联搜索输入框：SINGLE_VALUE 行常驻 {@link CurrentValuePresenter} 紧凑展示
 * （图标 + 主文本 + 副文本），LIST_MEMBERS 行常驻「已配置/无效/重复」摘要与管理按钮。
 * 点击或 Enter 打开受控居中 70% 面板；面板确认后写回并关闭，ESC 先走 onCancel（清 query、
 * 复位列表绑定临时态）再请求关闭，关闭后焦点恢复到行触发器。</p>
 *
 * <p><b>G15/Support 外观口径</b>：本类只装配「行触发器 + 摘要」的自有表面——触发器行经
 * {@link SceneSurfaceBinder} 消费来源主题 INPUT 角色配方（契约 §4.1 Select 触发器行），
 * 文字前景取 {@link SceneThemes} 的 {@code foreground}/{@code mutedForeground} 语义信号，
 * 占位图标底取 {@code mutedForeground} 占位口径；面板与搜索配件（{@link ScenePickerPanel}
 * 及其 G13/G14 内部件）只读复用、不复制样式。旧 {@code SceneControlChrome.bindStandardBorder/
 * bindSelectableBackground} 接缝与静态色/圆角写入已按契约 §4.2 摘除；主题切换只重派生外观，
 * query、编解码与选择行为全部不变。</p>
 */
public final class SearchPickerFieldSupport {
    private static final Logger LOG = LogManager.getLogger("QzUiLib/ConfigUI");
    private static final int TRIGGER_ICON_SIZE = 18;
    private static final int TRIGGER_DETAIL_FONT_SIZE = 12;
    private static final int MANAGE_BUTTON_WIDTH = 96;
    /** 图片在场时占位图标底保持透明（无颜色语义，仅「不写占位底色」的字面零值）。 */
    private static final int ICON_BG_TRANSPARENT = 0x00000000;
    /** 行触发器表面恒定启用（控件自身无禁用态；面板开关不改触发器可用性）。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;

    private SearchPickerFieldSupport() { }

    /**
     * 按 widget 声明创建搜索选择器；非搜索 widget 返回 null。
     *
     * @param rt scene runtime
     * @param spec 值规格
     * @param value 当前值
     * @param registry 已冻结的 editor 注册表
     * @param onChange 编码后值回调
     * @return 搜索选择器节点，或 null
     */
    public static SceneNode createIfPresent(SceneRuntime rt, ValueSpec spec, Object value,
                                            Registry registry, Consumer<Object> onChange) {
        return createControlledIfPresent(rt, spec, Signal.create(value), registry, onChange);
    }

    /**
     * 按 widget 声明创建受控搜索选择器；当前配置值变化时实时重新解码。
     *
     * @param rt scene runtime
     * @param spec 值规格
     * @param value 当前配置值信号
     * @param registry 已冻结的 editor 注册表
     * @param onChange 编码后值回调
     * @return 搜索选择器节点，或 null
     */
    public static SceneNode createControlledIfPresent(SceneRuntime rt, ValueSpec spec,
                                                       ReadableSignal<Object> value,
                                                       Registry registry, Consumer<Object> onChange) {
        if (!(spec.widget() instanceof SearchPickerSpec)) return null;
        SearchPickerSpec pickerSpec = (SearchPickerSpec) spec.widget();
        if (pickerSpec.bindingMode() == SearchPickerSpec.BindingMode.LIST_MEMBERS) {
            throw new IllegalArgumentException("LIST_MEMBERS requires explicit list item binding");
        }
        ValueEditorProvider provider = registry.find(pickerSpec.editorId());
        if (provider == null) {
            throw new IllegalStateException("missing value editor provider: " + pickerSpec.editorId());
        }
        SearchPickerPresentation presentation = provider.presentation();
        Signal<String> decodeError = Signal.create("");
        Signal<String> searchError = Signal.create("");
        Signal<String> encodeError = Signal.create("");
        Computed<SearchPickerData.Selection> current = Computed.create(() -> {
            try {
                SearchPickerData.Selection decoded = provider.codec().decode(value.get());
                if (decoded == null) return fail(decodeError, pickerSpec.editorId(), "decode", presentation.decodeError(), null);
                decodeError.set("");
                return decoded;
            } catch (RuntimeException exception) {
                return fail(decodeError, pickerSpec.editorId(), "decode", presentation.decodeError(), exception);
            }
        });
        // error 读取时顺带求值 current，保证 decode 失败在面板打开首帧即入账显示。
        Computed<String> error = Computed.create(() -> {
            current.get();
            return firstError(encodeError.get(), searchError.get(), decodeError.get());
        });
        Signal<String> query = Signal.create("");
        Computed<SearchPickerData.SearchResult> results = Computed.create(() -> {
            try {
                SearchPickerData.SearchResult searched = provider.searchFunction().search(query.get(), Integer.MAX_VALUE);
                if (searched == null) return fail(searchError, pickerSpec.editorId(), "search",
                        presentation.searchError(), null);
                searchError.set("");
                return searched;
            } catch (RuntimeException exception) {
                fail(searchError, pickerSpec.editorId(), "search", presentation.searchError(), exception);
                return SearchPickerData.SearchResult.empty();
            }
        });
        Signal<Boolean> open = Signal.create(Boolean.FALSE);
        ScenePickerPanel.Props.Builder panelBuilder = ScenePickerPanel.Props.builder(query, results,
                Signal.create(Boolean.TRUE),
                next -> {
                    decodeError.set(""); searchError.set(""); encodeError.set(""); query.set(next);
                },
                selection -> { }, provider.visualAdapter())
                .selectionCommit(selection -> {
                    try {
                        Object encoded = provider.codec().encode(value.get(), selection);
                        if (encoded != null) {
                            onChange.accept(encoded);
                            query.set("");
                            decodeError.set(""); searchError.set(""); encodeError.set("");
                            return true;
                        }
                        fail(encodeError, pickerSpec.editorId(), "encode", presentation.encodeError(), null);
                        return false;
                    } catch (RuntimeException exception) {
                        fail(encodeError, pickerSpec.editorId(), "encode", presentation.encodeError(), exception);
                        return false;
                    }
                })
                .currentSelection(current)
                .presentation(presentation)
                .panelPresentation(provider.panelPresentation())
                .variantSearchEnabled(true)
                .error(error)
                .open(open)
                .onCloseRequest(() -> open.set(Boolean.FALSE))
                .onCancel(() -> {
                    query.set(""); decodeError.set(""); searchError.set(""); encodeError.set("");
                });
        wireCategories(panelBuilder, provider);
        ScenePickerPanel.Props props = panelBuilder.build();
        ScenePickerPanel.Result panel = ScenePickerPanel.create(rt, props);

        SceneNode root = SceneNode.column();
        SceneNode trigger = valueTrigger(rt, provider, presentation, value,
                () -> open.set(Boolean.TRUE));
        root.appendChild(trigger);
        root.appendChild(panel.root());
        restoreFocusOnClose(rt, open, trigger);
        return root;
    }

    /**
     * 显式装配列表成员 picker；稳定成员身份由调用方持有的 ListItem signal 提供。
     *
     * @return LIST_MEMBERS picker，非 picker 或非 LIST_MEMBERS 时返回 null
     */
    public static SceneNode createListMembersIfPresent(SceneRuntime rt, ValueSpec spec,
                                                        ReadableSignal<Object> value,
                                                        Signal<List<SceneSimpleList.ListItem>> items,
                                                        Registry registry, Consumer<Object> onChange) {
        if (!(spec.widget() instanceof SearchPickerSpec)) return null;
        SearchPickerSpec pickerSpec = (SearchPickerSpec) spec.widget();
        if (pickerSpec.bindingMode() != SearchPickerSpec.BindingMode.LIST_MEMBERS) return null;
        ValueEditorProvider provider = registry.find(pickerSpec.editorId());
        if (provider == null) throw new IllegalStateException("missing value editor provider: " + pickerSpec.editorId());
        if (!(provider.codec() instanceof ListMemberCodec)) {
            throw new IllegalStateException("LIST_MEMBERS requires ListMemberCodec: " + pickerSpec.editorId());
        }
        SearchPickerPresentation presentation = provider.presentation();
        Signal<String> query = Signal.create("");
        Signal<String> searchError = Signal.create("");
        Signal<String> encodeError = Signal.create("");
        Computed<String> error = Computed.create(() -> firstError(encodeError.get(), searchError.get(), ""));
        Computed<SearchPickerData.SearchResult> queryResults = Computed.create(() -> {
            try {
                SearchPickerData.SearchResult searched = provider.searchFunction().search(query.get(), Integer.MAX_VALUE);
                if (searched == null) return fail(searchError, pickerSpec.editorId(), "search",
                        presentation.searchError(), null);
                searchError.set("");
                return searched;
            } catch (RuntimeException exception) {
                fail(searchError, pickerSpec.editorId(), "search", presentation.searchError(), exception);
                return SearchPickerData.SearchResult.empty();
            }
        });
        SearchPickerListBinding binding = new SearchPickerListBinding(value, items,
                (ListMemberCodec) provider.codec(), onChange);
        Computed<List<SearchPickerData.CurrentMember>> decodedMembers = Computed.create(
                () -> binding.currentMembers(SearchPickerData.SearchResult.empty()));
        Computed<List<SearchPickerData.CurrentMember>> currentMembers = Computed.create(() ->
                resolveCurrentMembers(decodedMembers.get(), provider.searchFunction()));
        Computed<SearchPickerData.SearchResult> addableResults = Computed.create(() ->
                excludeSelectedCandidates(queryResults.get(), currentMembers.get()));
        Computed<SearchPickerData.Selection> currentSelection = Computed.create(binding::currentSelection);
        Signal<Boolean> open = Signal.create(Boolean.FALSE);
        ScenePickerPanel.Props.Builder panelBuilder = ScenePickerPanel.Props.builder(query, addableResults,
                Signal.create(Boolean.TRUE),
                next -> { searchError.set(""); encodeError.set(""); query.set(next); },
                selection -> { }, provider.visualAdapter())
                .selectionCommit(selection -> {
                    Long target = binding.editingId().get();
                    boolean adding = target != null && target.longValue() < 0L;
                    if (!binding.confirm(selection)) {
                        fail(encodeError, pickerSpec.editorId(), "encode", presentation.encodeError(), null);
                        return false;
                    }
                    if (!adding) query.set("");
                    searchError.set(""); encodeError.set("");
                    return true;
                })
                .currentSelection(currentSelection)
                .presentation(presentation)
                .panelPresentation(provider.panelPresentation())
                .variantSearchEnabled(true)
                .error(error)
                .currentMembers(currentMembers, binding::edit)
                .onRemoveCurrent(memberId -> {
                    if (!binding.remove(memberId)) {
                        fail(encodeError, pickerSpec.editorId(), "remove", presentation.encodeError(), null);
                        return false;
                    }
                    searchError.set(""); encodeError.set("");
                    return true;
                })
                .onBeginAdd(binding::add)
                .onCancel(() -> {
                    binding.cancel();
                    query.set(""); searchError.set(""); encodeError.set("");
                })
                .open(open)
                .onCloseRequest(() -> open.set(Boolean.FALSE));
        wireCategories(panelBuilder, provider);
        ScenePickerPanel.Props props = panelBuilder.build();
        ScenePickerPanel.Result panel = ScenePickerPanel.create(rt, props);

        SceneNode root = SceneNode.column();
        SceneNode management = SceneNode.row();
        management.setGap(SceneChromeTokens.GAP_MD);
        management.setCrossAxisAlign(CrossAxisAlign.CENTER);
        SceneNode manage = SceneButton.create(rt, new SceneButton.Props(
                Signal.create(presentation.manage()), Signal.create(Boolean.TRUE),
                () -> open.set(Boolean.TRUE))).get();
        manage.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        manage.setPreferredWidth(MANAGE_BUTTON_WIDTH);
        management.appendChild(manage);
        SceneNode summary = SceneNode.row();
        summary.setGap(4);
        summary.setFlexGrow(1);
        SceneNode configured = text(rt, "", SceneThemes.foreground(rt));
        rt.bindText(configured, Computed.create(() -> {
            List<SearchPickerData.CurrentMember> members = currentMembers.get();
            return presentation.configuredSummary(members == null ? 0 : members.size());
        }));
        SceneNode issues = text(rt, "", SceneThemes.foreground(rt));
        rt.bindText(issues, Computed.create(() -> {
            List<SearchPickerData.CurrentMember> members = currentMembers.get();
            int[] counts = memberIssueCounts(members);
            return presentation.memberIssueSummary(counts[0], counts[1]);
        }));
        summary.appendChild(configured);
        summary.appendChild(issues);
        management.appendChild(summary);
        root.appendChild(management);
        root.appendChild(panel.root());
        restoreFocusOnClose(rt, open, manage);
        return root;
    }

    /**
     * 按每个唯一 candidate key 独立精确解析当前成员；失败只保留 unknown，不污染 query 搜索错误。
     */
    private static List<SearchPickerData.CurrentMember> resolveCurrentMembers(
            List<SearchPickerData.CurrentMember> decoded,
            ValueEditorProvider.SearchFunction searchFunction) {
        Map<String, SearchPickerData.Candidate> exactCandidates =
                new HashMap<String, SearchPickerData.Candidate>();
        Set<String> searchedKeys = new HashSet<String>();
        for (SearchPickerData.CurrentMember member : decoded) {
            SearchPickerData.Selection selection = member.selection();
            if (selection == null || !searchedKeys.add(selection.candidateKey())) continue;
            try {
                SearchPickerData.SearchResult result = searchFunction.search(
                        selection.candidateKey(), Integer.MAX_VALUE);
                if (result == null) continue;
                for (SearchPickerData.Candidate candidate : result.candidates()) {
                    if (candidate.key().equals(selection.candidateKey())) {
                        exactCandidates.put(selection.candidateKey(), candidate);
                        break;
                    }
                }
            } catch (RuntimeException ignored) {
                // 单个当前成员解析失败是合法 unknown，不得覆盖 query 搜索错误或阻断其它成员。
            }
        }
        ArrayList<SearchPickerData.CurrentMember> resolved =
                new ArrayList<SearchPickerData.CurrentMember>(decoded.size());
        for (SearchPickerData.CurrentMember member : decoded) {
            SearchPickerData.Selection selection = member.selection();
            SearchPickerData.Candidate candidate = selection == null ? null
                    : exactCandidates.get(selection.candidateKey());
            resolved.add(new SearchPickerData.CurrentMember(member.memberId(), selection,
                    candidate, candidate != null));
        }
        return java.util.Collections.unmodifiableList(resolved);
    }

    /** 按精确 candidate key 排除合法当前成员；malformed 成员不参与过滤。 */
    private static SearchPickerData.SearchResult excludeSelectedCandidates(
            SearchPickerData.SearchResult complete,
            List<SearchPickerData.CurrentMember> currentMembers) {
        Set<String> selectedKeys = new HashSet<String>();
        for (SearchPickerData.CurrentMember member : currentMembers) {
            if (member.selection() != null) selectedKeys.add(member.selection().candidateKey());
        }
        ArrayList<SearchPickerData.Candidate> addable = new ArrayList<SearchPickerData.Candidate>();
        for (SearchPickerData.Candidate candidate : complete.candidates()) {
            if (!selectedKeys.contains(candidate.key())) addable.add(candidate);
        }
        return new SearchPickerData.SearchResult(addable);
    }

    /** 面板从打开变为关闭时把焦点恢复到行触发器（首帧不抢焦点）。 */
    private static void restoreFocusOnClose(SceneRuntime rt, ReadableSignal<Boolean> open,
                                            SceneNode trigger) {
        final boolean[] wasOpen = { Boolean.TRUE.equals(open.get()) };
        rt.bind(open, o -> {
            boolean now = Boolean.TRUE.equals(o);
            if (wasOpen[0] && !now) rt.requestFocus(trigger);
            wasOpen[0] = now;
        });
    }

    /** provider 注册快照实现分组契约时透传分类列表；否则空列表（面板退化单分类）。 */
    private static ReadableSignal<List<SearchPickerCategories.Category>> categoriesOf(
            ValueEditorProvider provider) {
        if (provider instanceof CategorizedValueEditorProvider) {
            return Signal.create(SearchPickerCategories.immutableCopy(
                    ((CategorizedValueEditorProvider) provider).categories()));
        }
        return Signal.create(Collections.<SearchPickerCategories.Category>emptyList());
    }

    /** provider 注册快照实现分组契约时透传分类器；否则 null（全部候选视为未分类）。 */
    private static Function<String, String> categoryOf(ValueEditorProvider provider) {
        return provider instanceof CategorizedValueEditorProvider
                ? ((CategorizedValueEditorProvider) provider)::categoryOf : null;
    }

    /**
     * 装配面板分类输入：多维度 provider（快照 {@code categoryDimensionCount() > 1}）时注入
     * 受控维度下标与受控当前分类 key，分类列表与分类器按当前维度重派生，切换维度时分类 key
     * 复位为「全部」；单维度/无分组保持静态透传（不传 dimension/currentCategoryKey，行为不变）。
     */
    private static void wireCategories(ScenePickerPanel.Props.Builder builder, ValueEditorProvider provider) {
        if (!(provider instanceof CategorizedValueEditorProvider)
                || ((CategorizedValueEditorProvider) provider).categoryDimensionCount() <= 1) {
            builder.categories(categoriesOf(provider)).categoryOf(categoryOf(provider));
            return;
        }
        CategorizedValueEditorProvider categorized = (CategorizedValueEditorProvider) provider;
        Signal<Integer> dimensionIndex = Signal.create(Integer.valueOf(0));
        Signal<String> currentCategoryKey = Signal.create(null);
        Computed<List<SearchPickerCategories.Category>> categories = Computed.create(() ->
                SearchPickerCategories.immutableCopy(categorized.categories(dimensionIndex.get().intValue())));
        Computed<Function<String, String>> categoryOf = Computed.create(() ->
                candidateKey -> categorized.categoryOf(dimensionIndex.get().intValue(), candidateKey));
        builder.dimension(dimensionIndex, next -> {
            currentCategoryKey.set(null);
            dimensionIndex.set(next);
        });
        builder.currentCategoryKey(currentCategoryKey, currentCategoryKey::set);
        builder.categories(categories);
        builder.categoryOf(candidateKey -> categoryOf.get().apply(candidateKey));
    }

    /**
     * 构建 SINGLE_VALUE 行触发器：CurrentValuePresenter 紧凑展示，可聚焦，点击或 Enter 打开面板。
     */
    private static SceneNode valueTrigger(SceneRuntime rt, ValueEditorProvider provider,
                                          SearchPickerPresentation presentation,
                                          ReadableSignal<Object> value, Runnable openPanel) {
        CurrentValuePresenter presenter = provider.currentValuePresenter();
        SceneNode trigger = SceneNode.row();
        trigger.setGap(SceneChromeTokens.GAP_MD);
        trigger.setPadding(SceneChromeTokens.PAD_MD);
        trigger.setCrossAxisAlign(CrossAxisAlign.CENTER);
        // 行触发器表面（契约 §4.1 Select 触发器口径）：染色/边框/边框宽/圆角/滤镜/浮雕归
        // SceneSurfaceBinder 独占，配方取来源主题 INPUT 角色；旧 bindStandardBorder /
        // bindSelectableBackground 与静态 borderWidth/cornerRadius 写入已摘除（§4.2）。
        // 构建期先声明关心 hover/pressed/focus：Router 对未创建的 signal 直接短路，
        // 不声明则事件驱动不了配方状态档（与 FormFieldShell 默认路径同构）。
        SceneInteractionState interaction = rt.interactionState(trigger);
        interaction.hovered();
        interaction.pressed();
        interaction.focused();
        SceneSurfaceBinder.bind(rt, trigger, SceneThemes.surface(rt, SceneTheme.Role.INPUT),
                ALWAYS_ENABLED, interaction);

        if (presenter != null) {
            SceneNode icon = new SceneNode();
            icon.setPreferredWidth(TRIGGER_ICON_SIZE);
            icon.setPreferredHeight(TRIGGER_ICON_SIZE);
            icon.setHitTestable(false);
            trigger.appendChild(icon);
            SceneNode info = SceneNode.column();
            info.setFlexGrow(1);
            info.setGap(2);
            info.setHitTestable(false);
            SceneNode title = text(rt, "", SceneThemes.foreground(rt));
            SceneNode detail = text(rt, "", SceneThemes.mutedForeground(rt));
            detail.setFontSize(TRIGGER_DETAIL_FONT_SIZE);
            info.appendChild(title);
            info.appendChild(detail);
            trigger.appendChild(info);
            final Signal<Boolean> hasImage = Signal.create(Boolean.FALSE);
            rt.bind(value, current -> {
                CurrentValuePresenter.Presentation shown = presenter.present(current);
                hasImage.set(Boolean.valueOf(shown != null && shown.image() != null));
                icon.setImageSource(shown == null ? null : shown.image());
                title.setText(shown == null ? "" : shown.title());
                detail.setText(shown == null ? "" : shown.summary());
            });
            // 占位图标底：图片在场保持透明；无图时取主题占位口径（mutedForeground 信号），
            // 值×主题双输入合成后仍是「backgroundColor 单写入者」，旧静态 PLACEHOLDER_COLOR 已删。
            ReadableSignal<Integer> placeholderTint = SceneThemes.mutedForeground(rt);
            rt.bindComputed(() -> Integer.valueOf(Boolean.TRUE.equals(hasImage.get())
                    ? ICON_BG_TRANSPARENT : placeholderTint.get().intValue()),
                    icon::setBackgroundColor);
        } else {
            SceneNode label = text(rt, presentation.title(), SceneThemes.foreground(rt));
            trigger.appendChild(label);
        }
        rt.focusable(trigger);
        rt.on(trigger, SceneEventType.CLICK, (ev, ctx) -> {
            openPanel.run();
            ctx.stopPropagation();
        });
        rt.on(trigger, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (ev.getKeyAction() == SceneKeyAction.PRESSED && !ev.isRepeat()
                    && ev.getKey() == SceneKey.ENTER) {
                openPanel.run();
                ctx.stopPropagation();
            }
        });
        return trigger;
    }

    /** 按成员列表统计展示用无效/重复计数；malformed 不进入重复计算，重复按成员数计。 */
    private static int[] memberIssueCounts(List<SearchPickerData.CurrentMember> members) {
        if (members == null) return new int[] { 0, 0 };
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
        int duplicateCount = 0;
        for (SearchPickerData.CurrentMember member : members) {
            if (member.selection() != null
                    && keyCounts.get(member.selection().candidateKey()).intValue() > 1) {
                duplicateCount++;
            }
        }
        return new int[] { invalidCount, duplicateCount };
    }

    private static <T> T fail(Signal<String> error, String editorId, String phase, String message,
                              RuntimeException exception) {
        error.set(message);
        if (exception == null) LOG.warn("[QzUiLib/ConfigUI] search picker failed: editorId={}, phase={}, result=null",
                editorId, phase);
        else LOG.warn("[QzUiLib/ConfigUI] search picker failed: editorId={}, phase={}", editorId, phase, exception);
        return null;
    }

    private static String firstError(String first, String second, String third) {
        if (first != null && !first.isEmpty()) return first;
        if (second != null && !second.isEmpty()) return second;
        return third == null ? "" : third;
    }

    /**
     * 创建不可命中的文字节点，前景经语义信号绑定（构建期捕获来源主题，主题切换只重算色值）。
     *
     * @param rt    场景运行时
     * @param value 初始文本（{@code null} 视为空串）
     * @param color 主题语义前景信号（正文 {@code foreground} / 次要 {@code mutedForeground}）
     * @return 文字节点
     */
    private static SceneNode text(SceneRuntime rt, String value, ReadableSignal<Integer> color) {
        SceneNode node = new SceneNode();
        node.setText(value == null ? "" : value);
        node.setHitTestable(false);
        rt.bind(color, node::setTextColor);
        return node;
    }
}
