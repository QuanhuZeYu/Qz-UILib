package club.heiqi.config.ui.field;

import club.heiqi.config.schema.ValueKind;
import club.heiqi.config.schema.ValueSpec;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.StructuredListSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.editor.Registry;
import club.heiqi.config.ui.editor.CurrentValuePresenter;
import club.heiqi.config.ui.editor.ValueEditorProvider;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneCheckbox;
import club.heiqi.uilib.ui.scene.control.SceneInputType;
import club.heiqi.uilib.ui.scene.control.SceneSelect;
import club.heiqi.uilib.ui.scene.control.SceneSegmented;
import club.heiqi.uilib.ui.scene.control.SceneSimpleList;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.control.SceneToggle;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.form.FormFieldShell;
import club.heiqi.uilib.ui.scene.form.FormLabeledControl;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * STRUCTURED_LIST 默认 renderer。
 *
 * <p>行通过 {@link SceneRuntime#forEach} keyed 渲染，编辑只复制命中 row/member 并经
 * {@link DraftSignalAdapter#onFieldEdit} 写回。排序采用明确的上移/下移命令，避免把拖拽瞬态
 * 引入配置 core；增删、String/Number/Boolean/Choice、List&lt;String&gt; 及 List&lt;Choice&gt;
 * 成员均可编辑。</p>
 *
 * <p><b>外观归属（G15/StructuredList 迁移后）</b>：字段壳经 {@code FormFieldShell.buildBorderless}
 * 的 theme-aware 无边框重载跟随来源主题（无边框模板保持无边框，契约 §4.1 FormFieldShell 行）；
 * 行卡是动态复用行，按契约 §4.1（G13 裁决）轻量口径只做主题缘色派生——零行滤镜、不装角色
 * 表面配方、不调表面绑定器；行缘色 / member error 语义色由
 * {@link SceneThemes#resolve} 来源主题的 {@code borderDefault}/{@code errorText} 经
 * {@code rt.bindComputed} 独占写入（深色档两值与旧 {@code SceneChromeTokens.BORDER_DEFAULT}/
 * {@code ConfigTheme.ERROR_COLOR} 同源，默认外观不变，换主题自动重派生）；member 标签行经
 * {@code FormLabeledControl.vertical(rt, ...)} 主题感知重载取正文/次要前景（G11）。布局与几何
 * 纯 int（GAP/PAD/边框宽/圆角/视口高度）保留，主题不接管布局（契约 §4.2）。SimpleList/Checkbox/
 * Button/TextInput/Toggle/Segmented/Select 等已迁移控件外观由控件自持，本类只挂载不复制样式。
 * 草稿事务、增删排序、dirty/error、保存/撤销/重置语义零改动。</p>
 */
public final class StructuredListFieldRenderer implements FieldRenderer {
    private static final int ROW_GAP = 5;
    private static final int MEMBER_GAP = 4;
    private static final int PRESENTATION_IMAGE_SIZE = 18;
    private static final int HEADER_TITLE_MAX_WIDTH = 260;
    private final Registry editorRegistry;

    /** 使用冻结空 editor registry 创建 renderer。 */
    public StructuredListFieldRenderer() {
        this(frozenEmptyRegistry());
    }

    /** 使用指定的已冻结 editor registry 创建 renderer。 */
    public StructuredListFieldRenderer(Registry editorRegistry) {
        if (editorRegistry == null || !editorRegistry.isFrozen()) {
            throw new IllegalArgumentException("editorRegistry must be non-null and frozen");
        }
        this.editorRegistry = editorRegistry;
    }

    @Override
    public SceneNode render(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        final String path = spec.path();
        final ValueSpec listSpec = spec.valueSpec();
        final ValueSpec objectSpec = listSpec.element();
        final ReadableSignal<Object> draftSignal = adapter.draftSignal(path);
        final Signal<List<StructuredListModel.Row>> rows =
                Signal.create(StructuredListModel.fromValue(draftSignal.get()));
        final StructuredListModel.IdentityLineage lineage =
                new StructuredListModel.IdentityLineage(objectSpec.identityMember());
        lineage.observe(rows.get());

        // reset/reload 只替换发生变化的列表投影，未变化时保持 keyed row identity。
        rt.bind(draftSignal, value -> {
            if (!StructuredListModel.valuesEqual(rows.get(), value)) {
                rows.set(StructuredListModel.sync(rows.get(), value, objectSpec, lineage));
            }
        });

        // 控件树必须在字段壳（FormFieldShell 内部 rt.mount）的 mount owner 内构建，使按钮、
        // forEach 和 bind 的输入/响应式绑定随字段外壳一起拥有正确生命周期。
        // G15/StructuredList 销账（Support 衔接要点 4）：原显式路径喂无参 asFormTheme()（缓存的
        // FormTheme.defaultDark() 常量，契约 §3 显式覆盖语义）切到 FormFieldShell 已存在的
        // theme-aware 无边框重载——helper 语义色/状态点随来源主题重派生，不做任何 .get() 快照，
        // 也不给主题信号喂第二套写入者；无边框模板保持无边框（契约 §4.1）。
        return FormFieldShell.buildBorderless(rt, FieldRenderSupport.labelOf(spec), spec.helper(),
                adapter.errorSignal(path), adapter.dirtySignal(path),
                () -> buildControl(rt, spec, adapter, rows, objectSpec, lineage));
    }

    private SceneNode buildControl(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter,
                                   Signal<List<StructuredListModel.Row>> rows, ValueSpec objectSpec,
                                   StructuredListModel.IdentityLineage lineage) {
        SceneNode control = SceneNode.column();
        control.setGap(ROW_GAP);
        Signal<Long> newlyAddedKey = Signal.create(Long.valueOf(-1L));
        SceneNode listViewport = SceneNode.column();
        listViewport.setGap(ROW_GAP);
        listViewport.setScrollable(true);
        listViewport.setClipChildren(true);
        listViewport.setPreferredHeight(viewportHeight(spec));
        SceneScrolls.attach(rt, listViewport);
        // forEach 独占列表视口；操作栏作为兄弟节点，不能追加到 keyed 容器内部。
        rt.forEach(listViewport, rows, StructuredListModel.Row::key,
                 row -> buildRow(rt, spec, adapter, rows, objectSpec, lineage, newlyAddedKey, row));
        control.appendChild(listViewport);
        control.appendChild(actionButton(rt, "添加", () -> {
            List<StructuredListModel.Row> next = StructuredListModel.add(rows.get(), defaultObject(objectSpec));
            newlyAddedKey.set(Long.valueOf(next.get(next.size() - 1).key()));
            publish(adapter, spec.path(), rows, lineage, next);
        }));
        return control;
    }

    /** 返回当前字段声明的视口高度；未声明时保持历史 320px 默认值。 */
    private static int viewportHeight(FieldSpec spec) {
        return spec.valueSpec().widget() instanceof StructuredListSpec
                ? ((StructuredListSpec) spec.valueSpec().widget()).viewportHeight()
                : StructuredListSpec.DEFAULT_VIEWPORT_HEIGHT;
    }

    private SceneNode buildRow(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter,
                                Signal<List<StructuredListModel.Row>> rows, ValueSpec objectSpec,
                                StructuredListModel.IdentityLineage lineage,
                                Signal<Long> newlyAddedKey,
                                StructuredListModel.Row row) {
        String path = spec.path();
        // 构造期捕获来源主题（forEach 的项 builder 内 Owner.current() 是来源作用域的子作用域）。
        ReadableSignal<SceneTheme> sourceTheme = SceneThemes.resolve(rt);
        SceneNode root = SceneNode.column();
        root.setGap(MEMBER_GAP);
        root.setPadding(6);
        root.setBorderWidth(1);
        root.setCornerRadius(4);
        // 动态复用行轻量口径（契约 §4.1 G13 裁决）：不装滤镜、不装角色表面，唯一外观写入点是
        // 缘色——由来源主题 borderDefault 经 bindComputed 独占重派生（深色档值与旧
        // SceneChromeTokens.BORDER_DEFAULT 同源，默认外观不变）。边框宽/圆角是纯 int 几何，
        // 主题不接管布局（契约 §4.2「尺寸/间距常量继续使用」）。
        rt.bindComputed(() -> Integer.valueOf(sourceTheme.get().borderDefault()), root::setBorderColor);
        Signal<Boolean> userExpanded = Signal.create(Boolean.valueOf(indexOf(rows.get(), row.key()) == 0
                || newlyAddedKey.get().longValue() == row.key()));
        ReadableSignal<String> rowError = adapter.errorSignalForPathAndDescendants(
                () -> path + "[" + indexOf(rows.get(), row.key()) + "]");
        ReadableSignal<Boolean> expanded = Computed.create(() -> Boolean.valueOf(
                Boolean.TRUE.equals(userExpanded.get()) || rowError != null
                        && rowError.get() != null && !rowError.get().isEmpty()));
        SceneNode header = SceneNode.row();
        header.setGap(MEMBER_GAP);
        SceneNode titleSlot = SceneNode.row();
        titleSlot.setFlexGrow(1);
        titleSlot.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        titleSlot.setMaxWidth(HEADER_TITLE_MAX_WIDTH);
        titleSlot.setClipChildren(true);
        SceneNode title = headerTitle(StructuredListModel.rowHeader(
                rows.get(), row.key(), objectSpec.identityMember()));
        rt.bind(Computed.create(() -> StructuredListModel.rowHeader(
                rows.get(), row.key(), objectSpec.identityMember())), title::setText);
        titleSlot.appendChild(title);
        header.appendChild(titleSlot);
        header.appendChild(actionButton(rt, "展开", () -> userExpanded.set(!Boolean.TRUE.equals(userExpanded.get()))));
        header.appendChild(actionButton(rt, "↑", () -> publish(adapter, path, rows, lineage,
                 StructuredListModel.moveUp(rows.get(), row.key()))));
        header.appendChild(actionButton(rt, "↓", () -> publish(adapter, path, rows, lineage,
                 StructuredListModel.moveDown(rows.get(), row.key()))));
        header.appendChild(actionButton(rt, "删除", () -> publish(adapter, path, rows, lineage,
                 StructuredListModel.remove(rows.get(), row.key()))));
        root.appendChild(header);

        SceneNode members = SceneNode.column();
        members.setGap(MEMBER_GAP);
        members.setPadding(0, 0, 0, 10);
        for (ValueSpec.Member member : objectSpec.members().values())
            members.appendChild(buildMember(rt, adapter, rows, lineage, row.key(), path, member));
        rt.show(root, expanded, () -> members);
        return root;
    }

    private SceneNode buildMember(SceneRuntime rt, DraftSignalAdapter adapter,
                                   Signal<List<StructuredListModel.Row>> rows,
                                   StructuredListModel.IdentityLineage lineage, long key,
                                   String rootPath, ValueSpec.Member member) {
        final String memberName = member.name();
        // 构造期捕获来源主题：member 行由 buildRow（forEach builder）同步构建，Owner 上下文有效。
        ReadableSignal<SceneTheme> sourceTheme = SceneThemes.resolve(rt);
        SceneNode wrapper = SceneNode.column();
        wrapper.setGap(2);
        ValueSpec valueSpec = member.spec();
        ReadableSignal<Object> memberValue = Computed.create(() -> value(rows, key, memberName));
        SceneNode presentation = buildPresentation(rt, valueSpec, memberValue);
        if (presentation != null) wrapper.appendChild(presentation);
        SceneNode editor;
        if (valueSpec.kind() == ValueKind.LIST && valueSpec.element().kind() == ValueKind.STRING) {
            SceneNode editorColumn = SceneNode.column();
            editorColumn.setGap(MEMBER_GAP);
            editorColumn.setFlexGrow(1);
            Signal<List<SceneSimpleList.ListItem>> local = Signal.create(toItems(value(rows, key, memberName)));
            rt.bind(Computed.create(() -> toStrings(value(rows, key, memberName))), values -> {
                if (!toStrings(local.get()).equals(values)) local.set(syncItems(local.get(), values));
            });
            SceneSimpleList.Props props = SceneSimpleList.Props.builder(local)
                    .placeholder("").maxItems(0).minItems(0)
                      .onItemsChanged(items -> publishMember(adapter, rootPath, rows, lineage, key, memberName,
                               toStrings(items))).build();
            club.heiqi.config.schema.SearchPickerSpec pickerSpec = valueSpec.widget()
                    instanceof club.heiqi.config.schema.SearchPickerSpec
                    ? (club.heiqi.config.schema.SearchPickerSpec) valueSpec.widget() : null;
            boolean listMembers = pickerSpec != null && pickerSpec.bindingMode()
                    == club.heiqi.config.schema.SearchPickerSpec.BindingMode.LIST_MEMBERS;
            if (listMembers) {
                SceneNode picker = SearchPickerFieldSupport.createListMembersIfPresent(rt, valueSpec, memberValue,
                        local, editorRegistry,
                        next -> publishMember(adapter, rootPath, rows, lineage, key, memberName, next));
                if (picker != null) editorColumn.appendChild(picker);
                Signal<Boolean> rawExpanded = Signal.create(Boolean.FALSE);
                ValueEditorProvider provider = editorRegistry.find(pickerSpec.editorId());
                String advancedLabel = provider == null ? "Advanced: edit raw values"
                        : provider.presentation().advancedRaw();
                editorColumn.appendChild(actionButton(rt, advancedLabel,
                        () -> rawExpanded.set(Boolean.valueOf(!Boolean.TRUE.equals(rawExpanded.get())))));
                rt.show(editorColumn, rawExpanded, () -> SceneSimpleList.create(rt, props).get());
            } else {
                editorColumn.appendChild(SceneSimpleList.create(rt, props).get());
                SceneNode picker = SearchPickerFieldSupport.createControlledIfPresent(rt, valueSpec, memberValue,
                        editorRegistry,
                        next -> publishMember(adapter, rootPath, rows, lineage, key, memberName, next));
                if (picker != null) editorColumn.appendChild(picker);
            }
            editor = editorColumn;
        } else if (valueSpec.kind() == ValueKind.LIST
                && valueSpec.element().kind() == ValueKind.CHOICE) {
            editor = buildChoiceList(rt, adapter, rows, lineage, key, rootPath,
                    memberName, valueSpec.element().choices());
        } else {
            SceneNode picker = SearchPickerFieldSupport.createControlledIfPresent(rt, valueSpec,
                    memberValue, editorRegistry,
                    next -> publishMember(adapter, rootPath, rows, lineage, key, memberName, next));
            editor = picker != null ? picker
                    : buildScalar(rt, adapter, rows, lineage, key, rootPath, member);
        }
        // G15/StructuredList 额外授权行：旧三参显式静态路径切到 theme-aware 重载（G11 已交付），
        // member 标签取来源主题正文前景、helper 取次要前景，结构/布局/命中语义完全一致。
        wrapper.appendChild(FormLabeledControl.vertical(rt, member.displayLabel(), member.helper(), editor));
        SceneNode error = new SceneNode();
        error.setHitTestable(false);
        // error 语义色由来源主题 errorText 经 bindComputed 独占写入（深色档值与旧
        // ConfigTheme.ERROR_COLOR 0xFFFFB4AB 同源，默认外观不变，换主题自动重派生）。
        rt.bindComputed(() -> Integer.valueOf(sourceTheme.get().errorText()), error::setTextColor);
        // ValueSpec validator 会把 List<String> 元素错误写成 members[index]；聚合到 member 行，
        // 同时让 prefix 依赖当前 row index，排序/删除后不会把错误黏在旧位置。
        ReadableSignal<String> errorSignal = adapter.errorSignalForPathAndDescendants(
                () -> rootPath + "[" + indexOf(rows.get(), key) + "]." + memberName);
        if (errorSignal != null) rt.bind(errorSignal, message -> error.setText(message == null ? "" : message));
        wrapper.appendChild(error);
        return wrapper;
    }

    private SceneNode buildPresentation(SceneRuntime rt, ValueSpec spec, ReadableSignal<Object> value) {
        if (!(spec.widget() instanceof club.heiqi.config.schema.SearchPickerSpec)) return null;
        String id = ((club.heiqi.config.schema.SearchPickerSpec) spec.widget()).editorId();
        ValueEditorProvider provider = editorRegistry.find(id);
        CurrentValuePresenter presenter = provider == null ? null : provider.currentValuePresenter();
        if (presenter == null) return null;
        SceneNode summary = SceneNode.column();
        summary.setGap(2);
        SceneNode image = new SceneNode();
        image.setPreferredWidth(PRESENTATION_IMAGE_SIZE);
        image.setPreferredHeight(PRESENTATION_IMAGE_SIZE);
        image.setHitTestable(false);
        SceneNode title = label("");
        SceneNode detail = label("");
        rt.bind(value, current -> {
            CurrentValuePresenter.Presentation shown = presenter.present(current);
            image.setImageSource(shown == null ? null : shown.image());
            title.setText(shown == null ? "" : shown.title());
            detail.setText(shown == null ? "" : shown.summary());
        });
        summary.appendChild(image);
        summary.appendChild(title);
        summary.appendChild(detail);
        return summary;
    }

    /** 构建受控 choice 多选列表；未知字符串保留为可删除的失效项。 */
    private SceneNode buildChoiceList(SceneRuntime rt, DraftSignalAdapter adapter,
                                      Signal<List<StructuredListModel.Row>> rows,
                                      StructuredListModel.IdentityLineage lineage, long key,
                                      String rootPath, String memberName, List<String> options) {
        SceneNode choices = SceneNode.column();
        choices.setGap(2);
        ReadableSignal<List<String>> items = Computed.create(() ->
                StructuredListModel.choiceDisplayItems(value(rows, key, memberName), options));
        rt.forEach(choices, items, item -> item, item -> {
            boolean known = options.contains(item);
            ReadableSignal<Boolean> checked = Computed.create(() -> Boolean.valueOf(
                    StructuredListModel.isChoiceSelected(value(rows, key, memberName), item)));
            String text = known ? item : item + "（已失效）";
            return SceneCheckbox.create(rt, new SceneCheckbox.Props(
                    checked, Signal.create(text), Signal.create(Boolean.TRUE), next ->
                    publishMember(adapter, rootPath, rows, lineage, key, memberName,
                            StructuredListModel.updateChoiceSelection(
                                    value(rows, key, memberName), options, item,
                                    Boolean.TRUE.equals(next))))).get();
        });
        return choices;
    }

    private SceneNode buildScalar(SceneRuntime rt, DraftSignalAdapter adapter,
                                   Signal<List<StructuredListModel.Row>> rows,
                                   StructuredListModel.IdentityLineage lineage, long key,
                                   String rootPath, ValueSpec.Member member) {
        String name = member.name();
        ValueSpec valueSpec = member.spec();
        ReadableSignal<Object> value = Computed.create(() -> value(rows, key, name));
        switch (valueSpec.kind()) {
            case STRING:
                return SceneTextInput.create(rt, new SceneTextInput.Props(
                        FieldRenderSupport.toStringSignal(value), Signal.create(Boolean.TRUE),
                        Signal.create(Boolean.FALSE), "", Integer.MAX_VALUE, SceneInputType.TEXT,
                          next -> publishMember(adapter, rootPath, rows, lineage, key, name, next))).get();
            case NUMBER:
                return SceneTextInput.create(rt, new SceneTextInput.Props(
                        FieldRenderSupport.toNumberStringSignal(value), Signal.create(Boolean.TRUE),
                        Signal.create(Boolean.FALSE), "", Integer.MAX_VALUE, SceneInputType.NUMBER,
                          next -> publishMember(adapter, rootPath, rows, lineage, key, name, parseNumber(next)))).get();
            case BOOLEAN:
                return SceneToggle.create(rt, new SceneToggle.Props(
                        Computed.create(() -> Boolean.valueOf(Boolean.TRUE.equals(value.get()))),
                        Signal.create(""), Signal.create(Boolean.TRUE),
                         next -> publishMember(adapter, rootPath, rows, lineage, key, name, next))).get();
            case CHOICE:
                List<String> options = valueSpec.choices();
                ReadableSignal<Integer> selected = Computed.create(() -> {
                    int index = options.indexOf(String.valueOf(value.get()));
                    return Integer.valueOf(index < 0 ? 0 : index);
                });
                if (options.size() <= 4) {
                    return SceneSegmented.create(rt, new SceneSegmented.Props(selected, options,
                            Signal.create(Boolean.TRUE), index -> publishMember(adapter, rootPath,
                                    rows, lineage, key, name, options.get(index)))).get();
                }
                return SceneSelect.create(rt, new SceneSelect.Props(selected, options,
                        Signal.create(Boolean.TRUE), index -> publishMember(adapter, rootPath,
                        rows, lineage, key, name, options.get(index)))).get();
            default:
                SceneNode unsupported = label("对象/复杂列表请展开专用 editor");
                return unsupported;
        }
    }

    private static void publishMember(DraftSignalAdapter adapter, String path,
                                      Signal<List<StructuredListModel.Row>> rows,
                                      StructuredListModel.IdentityLineage lineage, long key,
                                      String member, Object value) {
        List<StructuredListModel.Row> next = StructuredListModel.updateMember(rows.get(), key, member, value);
        // DraftSignalAdapter 是配置草稿的权威提交点；它拒绝写入时不得先推进 UI 派生 rows。
        adapter.onFieldEdit(path, StructuredListModel.toValue(next));
        rows.set(next);
        lineage.observe(next);
    }

    private static void publish(DraftSignalAdapter adapter, String path,
                                 Signal<List<StructuredListModel.Row>> rows,
                                 StructuredListModel.IdentityLineage lineage,
                                 List<StructuredListModel.Row> next) {
        rows.set(next);
        lineage.observe(next);
        adapter.onFieldEdit(path, StructuredListModel.toValue(next));
    }

    private static Object value(Signal<List<StructuredListModel.Row>> rows, long key, String member) {
        return StructuredListModel.memberValue(rows.get(), key, member);
    }

    private static int indexOf(List<StructuredListModel.Row> rows, long key) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).key() == key) {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, Object> defaultObject(ValueSpec objectSpec) {
        Object value = objectSpec.defaultValue();
        if (!(value instanceof Map)) {
            return new java.util.LinkedHashMap<String, Object>();
        }
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static List<String> toStrings(Object value) {
        List<String> result = new ArrayList<String>();
        if (value instanceof List) for (Object item : (List<?>) value) result.add(item == null ? "" : String.valueOf(item));
        return result;
    }

    private static List<String> toStrings(List<SceneSimpleList.ListItem> items) {
        List<String> result = new ArrayList<String>();
        if (items != null) for (SceneSimpleList.ListItem item : items) result.add(item.getValue());
        return result;
    }

    private static List<SceneSimpleList.ListItem> toItems(Object value) {
        List<SceneSimpleList.ListItem> result = new ArrayList<SceneSimpleList.ListItem>();
        for (String item : toStrings(value)) result.add(new SceneSimpleList.ListItem(item));
        return result;
    }

    /** 按文本匹配复用稳定 id；重排与重复值分别消费旧行，新增值才分配新 id。 */
    private static List<SceneSimpleList.ListItem> syncItems(List<SceneSimpleList.ListItem> previous,
                                                            List<String> values) {
        List<SceneSimpleList.ListItem> remaining = new ArrayList<SceneSimpleList.ListItem>(previous);
        List<SceneSimpleList.ListItem> result = new ArrayList<SceneSimpleList.ListItem>();
        for (String value : values) {
            SceneSimpleList.ListItem matched = null;
            for (SceneSimpleList.ListItem item : remaining) {
                if (item.getValue().equals(value)) { matched = item; break; }
            }
            if (matched == null) result.add(new SceneSimpleList.ListItem(value));
            else { result.add(matched); remaining.remove(matched); }
        }
        return result;
    }

    private static Object parseNumber(String value) {
        try { return Double.valueOf(Double.parseDouble(value)); }
        catch (NumberFormatException e) { return value; }
    }

    private static SceneNode label(String text) {
        SceneNode node = new SceneNode();
        node.setText(text);
        node.setHitTestable(false);
        return node;
    }

    /** 构建由标题槽裁剪的自然宽度文本，不向 header 申请固定宽度。 */
    private static SceneNode headerTitle(String text) {
        SceneNode node = new SceneNode();
        node.setText(text);
        node.setHitTestable(false);
        return node;
    }

    private static SceneNode actionButton(SceneRuntime rt, String text, Runnable action) {
        SceneNode button = SceneButton.create(rt, new SceneButton.Props(
                Signal.create(text), Signal.create(Boolean.TRUE), action)).get();
        SceneNode label = button.__getChildren().get(0);
        // 仿照 SceneSegmented：静态标签在构建期按真实字体度量并固化外宽，供父 ROW 先验扣除。
        button.setPreferredWidth(rt.measureTextWidth(text, label.getFontSize())
                + button.getPaddingLeft() + button.getPaddingRight());
        return button;
    }

    private static Registry frozenEmptyRegistry() {
        Registry registry = new Registry();
        registry.freeze();
        return registry;
    }
}
