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
import club.heiqi.config.ui.editor.CandidateSourceValueEditorProvider;
import club.heiqi.config.ui.editor.CategorizedValueEditorProvider;
import club.heiqi.config.ui.editor.CurrentValuePresenter;
import club.heiqi.config.ui.editor.ListMemberCodec;
import club.heiqi.config.ui.editor.PickerCandidateSource;
import club.heiqi.config.ui.editor.PickerQuery;
import club.heiqi.config.ui.editor.Registry;
import club.heiqi.config.ui.editor.SearchPickerCategories;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.SearchPickerPresentation;
import club.heiqi.config.ui.editor.ValueEditorProvider;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel;
import club.heiqi.uilib.ui.scene.control.SceneSimpleList;
import club.heiqi.uilib.ui.scene.control.search.PickerChrome;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneRenderProtocolTokens;
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
    /**
     * 触发器图标边长下限（逻辑 px）。
     *
     * <p>P5 §2.5：图标边长 = {@code round(fs*1.5)}，此处只登记"可读性下限"夹取边界；
     * 实际值在 {@link #bindTriggerIconSize} 里按节点生效字号派生（字号变化即时重派生）。</p>
     */
    private static final int TRIGGER_ICON_MIN_PX = PickerChrome.triggerIconSide(11);
    /** 图片在场时占位图标底保持透明（无颜色语义，仅「不写占位底色」的字面零值）。 */
    /** 图标底透明：取协议令牌表的透明值（同值双写已消除，P5 U-P5-3；非主题槽位）。 */
    private static final int ICON_BG_TRANSPARENT = SceneRenderProtocolTokens.TRANSPARENT_ARGB;
    /** 行触发器表面恒定启用（控件自身无禁用态；面板开关不改触发器可用性）。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;
    /**
     * SPI 路径的兼容结果入参（P4）：候选切片、总量与截断全部归面板的 {@code pageProvider} 闭包
     * （ADR §3.2「唯一实现 = ScenePickerPanel」），字段侧不再物化候选；本信号恒为空结果、零物化，
     * 面板在 SPI 路径不读它（T-1 旧路径才消费 {@code Props.results()}）。
     */
    private static final ReadableSignal<SearchPickerData.SearchResult> NO_RESULTS =
            SearchPickerData.SearchResult::empty;

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
        Signal<Boolean> open = Signal.create(Boolean.FALSE);
        // 数据源路径探测（纯加法）：实现 SPI 且给出惰性 source 时走查询式路径；否则保持旧全量路径（T-1）。
        PickerCandidateSource source = candidateSourceOf(provider);
        CategoryQueryState categoryState = new CategoryQueryState();
        // 旧路径：结果 Computed 以 open 为信号级前置条件（关闭时不求值，ADR §4.1）；
        // SPI 路径：字段侧零候选物化，results 只是兼容入参。
        ReadableSignal<SearchPickerData.SearchResult> results = source == null
                ? legacySearchResults(provider, pickerSpec, presentation, query, searchError, open)
                : NO_RESULTS;
        ScenePickerPanel.Props.Builder panelBuilder = ScenePickerPanel.Props.builder(query, results,
                Signal.create(Boolean.TRUE),
                next -> {
                    decodeError.set(""); searchError.set(""); encodeError.set(""); query.set(next);
                },
                selection -> { }, visualAdapterOf(rt, provider))
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
                })
                // SPI 路径下分类过滤归查询层（PickerQuery.categoryDimension/categoryKey）：面板侧关闭二次过滤（T-6）。
                .resultsCategoryFiltered(source != null)
                // 密度档位（P5 §1.4）：装配层接线缝提供的进程级偏好源；未接线 = null = 面板按 AUTO，
                // 与接线前逐值一致。偏好是信号而非构造期常量 ⇒ 改档位只重派生几何、不重建面板。
                .densityPreference(PickerDensityPreferenceSource.installed());
        wireRevisionAndQuery(rt, panelBuilder, source, query, categoryState);
        wireCategories(panelBuilder, provider, categoryState);
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
        Signal<Boolean> open = Signal.create(Boolean.FALSE);
        // 数据源路径探测（同 SINGLE_VALUE；T-1 回退保留）。
        PickerCandidateSource source = candidateSourceOf(provider);
        CategoryQueryState categoryState = new CategoryQueryState();
        SearchPickerListBinding binding = new SearchPickerListBinding(value, items,
                (ListMemberCodec) provider.codec(), onChange);
        Computed<List<SearchPickerData.CurrentMember>> decodedMembers = Computed.create(
                () -> binding.currentMembers(SearchPickerData.SearchResult.empty()));
        Computed<List<SearchPickerData.CurrentMember>> currentMembers = source == null
                ? Computed.create(() -> resolveCurrentMembers(decodedMembers.get(), provider.searchFunction()))
                : Computed.create(() -> resolveCurrentMembers(decodedMembers.get(), source));
        // 旧路径：排除已配置候选（全量结果形态可表达集合差）；SPI 路径：窗口切片是全局序列的连续区间，
        // 对切片做集合差会让「全局下标 ↔ 项」错位（高亮/ENTER/滚动位置失真），故 SPI 路径不再排除
        // 已配置项（候选本体仍可经 exact 解析）——见 P4 实现记录的偏差 D-P4-3。
        ReadableSignal<SearchPickerData.SearchResult> panelResults;
        if (source == null) {
            Computed<SearchPickerData.SearchResult> queryResults = legacySearchResults(provider, pickerSpec,
                    presentation, query, searchError, open);
            panelResults = Computed.create(() ->
                    excludeSelectedCandidates(queryResults.get(), currentMembers.get()));
        } else {
            panelResults = NO_RESULTS;
        }
        Computed<SearchPickerData.Selection> currentSelection = Computed.create(binding::currentSelection);
        ScenePickerPanel.Props.Builder panelBuilder = ScenePickerPanel.Props.builder(query, panelResults,
                Signal.create(Boolean.TRUE),
                next -> { searchError.set(""); encodeError.set(""); query.set(next); },
                selection -> { }, visualAdapterOf(rt, provider))
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
                // 删除撤销闸口（P5 §5.5 E1/E3 甲形态）：删除即生效 + 5s 撤销条。
                // 宿主持有唯一 tombstone（原下标 + 原始 raw），撤销按原序原值插回；
                // 窗口到期/面板关闭/被新删除替换时由面板回调释放。
                .onRestoreCurrent(binding::restoreRemoved, binding::discardRemoved)
                .onCancel(() -> {
                    binding.cancel();
                    query.set(""); searchError.set(""); encodeError.set("");
                })
                .open(open)
                .onCloseRequest(() -> open.set(Boolean.FALSE))
                .resultsCategoryFiltered(source != null)
                // 密度档位（P5 §1.4）：与 SINGLE_VALUE 路径同源同缝（未接线 = AUTO）。
                .densityPreference(PickerDensityPreferenceSource.installed());
        wireRevisionAndQuery(rt, panelBuilder, source, query, categoryState);
        wireCategories(panelBuilder, provider, categoryState);
        ScenePickerPanel.Props props = panelBuilder.build();
        ScenePickerPanel.Result panel = ScenePickerPanel.create(rt, props);

        SceneNode root = SceneNode.column();
        // A1：LIST_MEMBERS 的「整行」就是触发器（与 SINGLE_VALUE 行同构）——行可聚焦、可点击、Enter 同效；
        // 行内「管理」按钮保留自身命中与自身表面（嵌套表面：行 INPUT 配方 + 按钮自身配方），
        // 点击按钮时 CLICK 冒泡到行只做一次同值 open 写入（Signal 同值去重 ⇒ 不会双开）。
        SceneNode management = SceneNode.row();
        management.setGap(SceneChromeTokens.GAP_MD);
        management.setPadding(SceneChromeTokens.PAD_MD);
        management.setCrossAxisAlign(CrossAxisAlign.CENTER);
        // 交互状态先声明关心再绑定：Router 对未创建的 signal 短路，不声明则事件驱动不了配方状态档。
        SceneInteractionState managementInteraction = rt.interactionState(management);
        managementInteraction.hovered();
        managementInteraction.pressed();
        managementInteraction.focused();
        SceneSurfaceBinder.bind(rt, management, SceneThemes.surface(rt, SceneTheme.Role.INPUT),
                ALWAYS_ENABLED, managementInteraction);
        SceneNode manage = SceneButton.create(rt, new SceneButton.Props(
                Signal.create(presentation.manage()), Signal.create(Boolean.TRUE),
                () -> open.set(Boolean.TRUE))).get();
        manage.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        // 管理按钮宽 = 文本实测宽 + 内边距（SHRINK 由控件自算，不再有 96 硬编码；P5 §4.1）。
        manage.setWidthSizing(SceneNode.WidthSizing.SHRINK);
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
        // 整行命中：点击行内任意非按钮区域（摘要/间隙）与 Enter 均打开面板；
        // stopPropagation 只截断向祖先（宿主表单行）的冒泡，不影响行内按钮已完成的自身响应。
        rt.focusable(management);
        rt.on(management, SceneEventType.CLICK, (ev, ctx) -> {
            open.set(Boolean.TRUE);
            ctx.stopPropagation();
        });
        rt.on(management, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (ev.getKeyAction() == SceneKeyAction.PRESSED && !ev.isRepeat()
                    && ev.getKey() == SceneKey.ENTER) {
                open.set(Boolean.TRUE);
                ctx.stopPropagation();
            }
        });
        root.appendChild(management);
        root.appendChild(panel.root());
        // 关闭后焦点回行触发器（A5：触发器/管理入口；行内按钮是同一触发器的子命中）。
        restoreFocusOnClose(rt, open, management);
        return root;
    }

    /**
     * 按每个唯一 candidate key 独立精确解析当前成员；失败只保留 unknown，不污染 query 搜索错误。
     *
     * <p><b>旧路径（T-1 回退）</b>：每个唯一 key 一次完整 {@code searchFunction.search(key, MAX_VALUE)}。</p>
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
        return resolvedMembers(decoded, exactCandidates);
    }

    /**
     * 按每个唯一 candidate key 独立精确解析当前成员（<b>惰性候选源路径</b>）。
     *
     * <p>每个唯一 key 至多一次 {@link PickerCandidateSource#exact(String)}（O(1) 定位 + 至多一次分片物化），
     * 取代「每个 key 一次 O(N) 全表搜索」。失败只保留 unknown，不污染 query 搜索错误。</p>
     *
     * @param decoded 已解码成员
     * @param source  惰性候选源
     * @return 解析后的成员快照
     */
    static List<SearchPickerData.CurrentMember> resolveCurrentMembers(
            List<SearchPickerData.CurrentMember> decoded, PickerCandidateSource source) {
        Map<String, SearchPickerData.Candidate> exactCandidates =
                new HashMap<String, SearchPickerData.Candidate>();
        Set<String> searchedKeys = new HashSet<String>();
        for (SearchPickerData.CurrentMember member : decoded) {
            SearchPickerData.Selection selection = member.selection();
            if (selection == null || !searchedKeys.add(selection.candidateKey())) continue;
            try {
                PickerSourceGuard.requireMainThread("exact");
                SearchPickerData.Candidate candidate = source.exact(selection.candidateKey());
                if (candidate != null && candidate.key().equals(selection.candidateKey())) {
                    exactCandidates.put(selection.candidateKey(), candidate);
                }
            } catch (RuntimeException ignored) {
                // 单个当前成员解析失败是合法 unknown（含 SPI 抛出的线程断言/宿主异常）。
            }
        }
        return resolvedMembers(decoded, exactCandidates);
    }

    /** 用「key → 候选」结果表重建成员快照（两条路径共享尾部，保证 unknown 语义一致）。 */
    private static List<SearchPickerData.CurrentMember> resolvedMembers(
            List<SearchPickerData.CurrentMember> decoded,
            Map<String, SearchPickerData.Candidate> exactCandidates) {
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

    // ==================== 候选源 SPI 接线（探测式；旧路径零改动保留为 T-1） ====================

    /**
     * 面板展示适配器：provider 给出图标源（SPI）时接上 UILib 有界图标缓存（纯加法），
     * 否则原样使用 provider 自己的适配器；缓存释放挂到当前 Owner 作用域（屏级释放链）。
     */
    private static club.heiqi.config.ui.editor.VisualAdapter visualAdapterOf(SceneRuntime rt, ValueEditorProvider provider) {
        PickerIconResolver resolver = PickerIconResolver.of(provider, rt.environment().resources());
        if (resolver == null) {
            return provider.visualAdapter();
        }
        rt.__onCleanup(resolver::release);
        return resolver;
    }

    /**
     * @return 惰性候选源；provider 未实现 SPI 或返回 null 时为 null（⇒ 旧全量路径）
     *
     * <p>顺带把源登记进 {@link PickerSourceLifecycle} 的会话账本（客户端断连 / 退出世界时统一
     * {@code release()}）。登记点是这里而不是 {@code Registry#register}：后者发生在装配更早的步骤，
     * 但「UILib 真正拿到 source 引用」的唯一点是字段侧接线；登记为 O(1) 弱引用写入，不触碰候选数据、
     * 不改变注册期「零候选读取」语义（A-05）。</p>
     */
    private static PickerCandidateSource candidateSourceOf(ValueEditorProvider provider) {
        if (!(provider instanceof CandidateSourceValueEditorProvider)) {
            return null;
        }
        PickerCandidateSource source = ((CandidateSourceValueEditorProvider) provider).candidateSource();
        PickerSourceLifecycle.track(source);
        return source;
    }

    /**
     * 旧路径搜索结果（全量，{@code truncated} 恒 false）：T-1 回退。
     *
     * <p><b>signal 级 open 前置</b>（ADR §4.1）：面板关闭时返回共享空结果、不调用 searchFunction ——
     * 关闭状态下的查询变化不再触发一次全表搜索。这是「订阅 open」而不是逐帧 if 门控。</p>
     */
    private static Computed<SearchPickerData.SearchResult> legacySearchResults(
            ValueEditorProvider provider, SearchPickerSpec pickerSpec, SearchPickerPresentation presentation,
            ReadableSignal<String> query, Signal<String> searchError, ReadableSignal<Boolean> open) {
        return Computed.create(() -> {
            if (!Boolean.TRUE.equals(open.get())) {
                return SearchPickerData.SearchResult.empty();
            }
            try {
                SearchPickerData.SearchResult searched =
                        provider.searchFunction().search(query.get(), Integer.MAX_VALUE);
                if (searched == null) return fail(searchError, pickerSpec.editorId(), "search",
                        presentation.searchError(), null);
                searchError.set("");
                return searched;
            } catch (RuntimeException exception) {
                fail(searchError, pickerSpec.editorId(), "search", presentation.searchError(), exception);
                return SearchPickerData.SearchResult.empty();
            }
        });
    }

    /**
     * SPI 路径接线（ADR §3.2「唯一实现 = ScenePickerPanel」）：把惰性源、
     * <b>查询条件信号</b>与源版本信号交给面板；窗口切片的拉取与总量判定全部在面板内容 Owner 内完成
     * （搜索 lane 窗口总量 = 真实命中数，无窗口上限）。
     *
     * <p>版本通道：桥（推环境代际 + 拉三段版本）在字段侧建立并挂宿主帧信号，其
     * {@code versionSignal()} 作为面板 lane 求值的依赖（语言/资源/注册表变化 → 自动重查，无逐帧 if）。
     * 查询条件由字段侧唯一构造（{@link #queryFor(String, CategoryQueryState)} 消费受控维度/分类键），
     * 面板不重复推导维度语义。</p>
     *
     * @param rt             场景运行时
     * @param builder        面板 builder
     * @param source         惰性候选源；null = 旧路径，不接线
     * @param query          原始查询文本信号
     * @param categoryState  分类查询状态（wireCategories 注入受控维度/分类键）
     */
    private static void wireRevisionAndQuery(SceneRuntime rt, ScenePickerPanel.Props.Builder builder,
                                             PickerCandidateSource source,
                                             ReadableSignal<String> query, CategoryQueryState categoryState) {
        if (source == null) {
            return;
        }
        PickerRevisionBridge bridge = PickerRevisionBridge.forSource(source, rt.environment());
        bridge.bindTo(rt);
        Computed<PickerQuery> sourceQuery = Computed.create(() -> queryFor(query.get(), categoryState));
        builder.candidateSource(source, sourceQuery, bridge.versionSignal());
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
    private static void wireCategories(ScenePickerPanel.Props.Builder builder, ValueEditorProvider provider,
                                       CategoryQueryState state) {
        if (!(provider instanceof CategorizedValueEditorProvider)) {
            builder.categories(categoriesOf(provider)).categoryOf(categoryOf(provider));
            return;
        }
        CategorizedValueEditorProvider categorized = (CategorizedValueEditorProvider) provider;
        if (categorized.categoryDimensionCount() <= 1) {
            // 单维度：分类列表/分类器静态透传，但分类键仍受控注入——查询层据此携带 categoryKey
            // （PickerQuery.categoryDimension/categoryKey），面板侧不再二次过滤（T-6）。
            Signal<String> singleCategoryKey = Signal.create(null);
            state.categoryKey = singleCategoryKey;
            builder.currentCategoryKey(singleCategoryKey, singleCategoryKey::set);
            builder.categories(categoriesOf(provider)).categoryOf(categoryOf(provider));
            return;
        }
        Signal<Integer> dimensionIndex = Signal.create(Integer.valueOf(0));
        Signal<String> currentCategoryKey = Signal.create(null);
        state.dimension = dimensionIndex;
        state.categoryKey = currentCategoryKey;
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
     * 分类查询状态：字段侧持有的受控维度/分类键读入口（由 {@link #wireCategories} 注入，面板写入）。
     *
     * <p>未注入（provider 无分组，或注入发生在首次求值之后）时读默认值：维度 0、分类键 null
     * （= 不过滤），与旧全量路径语义一致。</p>
     */
    static final class CategoryQueryState {
        /** 受控分类维度信号；null = 未注入（按维度 0）。包内可见：装配端注入 + 单测直接锚定。 */
        ReadableSignal<Integer> dimension;
        /** 受控分类键信号；null = 未注入（不做分类过滤）。 */
        ReadableSignal<String> categoryKey;
    }

    /**
     * 按当前查询文本 + 分类维度/键构造查询条件（包内可见以便单测直接锚定注入）。
     *
     * @param rawText 原始查询文本（可为 null；归一化由 PickerQuery 负责）
     * @param state   分类查询状态（非 null）
     * @return 查询条件值类型
     */
    static PickerQuery queryFor(String rawText, CategoryQueryState state) {
        return PickerQuery.text(rawText, dimensionOf(state), categoryKeyOf(state));
    }

    /** 当前分类维度（未注入 = 0）。 */
    private static int dimensionOf(CategoryQueryState state) {
        ReadableSignal<Integer> dimension = state.dimension;
        Integer value = dimension == null ? null : dimension.get();
        return value == null ? 0 : Math.max(0, value.intValue());
    }

    /** 当前分类键（未注入/null = 不做分类过滤）。 */
    private static String categoryKeyOf(CategoryQueryState state) {
        ReadableSignal<String> key = state.categoryKey;
        return key == null ? null : key.get();
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
            icon.setHitTestable(false);
            // 图标边长 = round(生效字号 * 1.5)（P5 §2.5）：字号变化经 layoutDone 重派生，
            // 不再有 18px 硬编码；下限由 TRIGGER_ICON_MIN_PX 夹取。
            bindTriggerIconSize(rt, icon);
            trigger.appendChild(icon);
            SceneNode info = SceneNode.column();
            info.setFlexGrow(1);
            info.setGap(2);
            info.setHitTestable(false);
            SceneNode title = text(rt, "", SceneThemes.foreground(rt));
            // 副文本字号随触发器（继承声明），不再有独立的 12px 常量（P5 §2.5「去掉 TRIGGER_DETAIL_FONT_SIZE」）。
            SceneNode detail = text(rt, "", SceneThemes.mutedForeground(rt));
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
    /**
     * 触发器图标边长绑定：{@code max(TRIGGER_ICON_MIN_PX, round(生效字号 * 1.5))}。
     *
     * <p>用「布局纪元 + 节点自身生效字号」这一既有派生口径（与字号声明/倍率的失效通道同源），
     * 不新造第二套字号事实；首帧即按声明值算好。</p>
     */
    private static void bindTriggerIconSize(SceneRuntime rt, SceneNode icon) {
        rt.bind(rt.layoutDoneSignal(), epoch -> Effect.untrack(() -> {
            int side = Math.max(TRIGGER_ICON_MIN_PX,
                    PickerChrome.triggerIconSide(icon.effectiveFontSize()));
            icon.setPreferredWidth(side);
            icon.setPreferredHeight(side);
        }));
    }

    private static SceneNode text(SceneRuntime rt, String value, ReadableSignal<Integer> color) {
        SceneNode node = new SceneNode();
        node.setText(value == null ? "" : value);
        node.setHitTestable(false);
        rt.bind(color, node::setTextColor);
        return node;
    }
}
