package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import club.heiqi.config.ui.editor.ListMemberCodec;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneSimpleList;

/**
 * 将 LIST&lt;STRING&gt; 原始值与 {@link SceneSimpleList.ListItem} 稳定身份绑定。
 *
 * <p>成员身份只取 {@code ListItem.id}；确认时重新按 id 定位槽位并读取最新 raw，
 * 因而候选 key 重复、确认前重排或目标删除都不会误写其它成员。</p>
 */
public final class SearchPickerListBinding {
    private static final long ADD_MEMBER_ID = -1L;

    private final ReadableSignal<Object> rawValue;
    private final Signal<List<SceneSimpleList.ListItem>> items;
    private final ListMemberCodec codec;
    private final Consumer<Object> onChange;
    private final Signal<Long> editingId = Signal.create(null);

    /** 创建列表成员绑定。 */
    public SearchPickerListBinding(ReadableSignal<Object> rawValue,
                                   Signal<List<SceneSimpleList.ListItem>> items,
                                   ListMemberCodec codec, Consumer<Object> onChange) {
        this.rawValue = Objects.requireNonNull(rawValue, "rawValue");
        this.items = Objects.requireNonNull(items, "items");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.onChange = Objects.requireNonNull(onChange, "onChange");
    }

    /** @return 当前编辑目标；null 表示未编辑，-1 表示新增。 */
    public ReadableSignal<Long> editingId() { return editingId; }

    /** 进入指定稳定成员的编辑模式。 */
    public void edit(long memberId) {
        if (memberId >= 0L) editingId.set(Long.valueOf(memberId));
    }

    /** 进入新增模式。 */
    public void add() { editingId.set(Long.valueOf(ADD_MEMBER_ID)); }

    /** 清除编辑目标，不改配置值。 */
    public void cancel() { editingId.set(null); }

    /**
     * 解码当前成员快照；未知候选保留 selection，格式错误保留 null selection。
     *
     * @param results 最新完整候选结果
     * @return 按 raw 顺序排列、以 ListItem.id 为身份的不可变快照
     */
    public List<SearchPickerData.CurrentMember> currentMembers(SearchPickerData.SearchResult results) {
        List<?> raw = rawList();
        if (raw == null) return Collections.emptyList();
        List<SceneSimpleList.ListItem> currentItems = safeItems();
        ArrayList<SearchPickerData.CurrentMember> members = new ArrayList<SearchPickerData.CurrentMember>();
        int count = Math.min(raw.size(), currentItems.size());
        for (int index = 0; index < count; index++) {
            SearchPickerData.Selection selection = decode(raw.get(index));
            SearchPickerData.Candidate candidate = findCandidate(results, selection);
            members.add(new SearchPickerData.CurrentMember(currentItems.get(index).getId(), selection,
                    candidate, candidate != null));
        }
        return Collections.unmodifiableList(members);
    }

    /** @return 当前编辑成员的最新选择；新增、失效或格式错误时为 null。 */
    public SearchPickerData.Selection currentSelection() {
        Long target = editingId.get();
        if (target == null || target.longValue() == ADD_MEMBER_ID) return null;
        int index = indexOf(target.longValue());
        List<?> raw = rawList();
        return raw == null || index < 0 || index >= raw.size() ? null : decode(raw.get(index));
    }

    /** @return 指定成员最新 raw 的可见回退文本。 */
    public String rawFallback(long memberId) {
        int index = indexOf(memberId);
        List<?> raw = rawList();
        return raw == null || index < 0 || index >= raw.size() ? "" : String.valueOf(raw.get(index));
    }

    /**
     * 按确认瞬间的稳定 id 精确替换或追加成员。
     *
     * <p>非 List 根值、stale id、codec 异常/null、最新 raw 非 String、编码结果非 String或提交回调
     * 拒绝写入时，items 与编辑目标均保持不变。items 是权威配置 signal 的派生投影，不在此处抢先写入；
     * 只有提交回调正常返回才清除编辑目标。</p>
     *
     * @param selection picker 选择
     * @return 是否成功写回
     */
    public boolean confirm(SearchPickerData.Selection selection) {
        Long target = editingId.get();
        if (target == null || selection == null) return false;
        List<?> raw = rawList();
        if (raw == null) return false;
        ArrayList<Object> next = new ArrayList<Object>(raw);
        List<SceneSimpleList.ListItem> currentItems = safeItems();
        try {
            if (target.longValue() == ADD_MEMBER_ID) {
                Object encoded = codec.encodeMember("", selection);
                if (!(encoded instanceof String)) return false;
                next.add(encoded);
            } else {
                int index = indexOf(target.longValue());
                if (index < 0 || index >= raw.size() || index >= currentItems.size()
                        || !(raw.get(index) instanceof String)) return false;
                Object encoded = codec.encodeMember(raw.get(index), selection);
                if (!(encoded instanceof String)) return false;
                next.set(index, encoded);
            }
            List<Object> published = Collections.unmodifiableList(next);
            onChange.accept(published);
            editingId.set(null);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 按确认瞬间的稳定成员 id 原子删除最新列表槽位。
     *
     * <p>事务只在 raw 与稳定 items 等长且目标 id 仍存在时构造一次不可变新列表并提交。
     * malformed 成员无需解码即可删除；提交回调拒绝（抛出异常）时不推进编辑态或派生 items。
     * 提交成功后按同一稳定 id 精确移除派生 item，避免重复 raw 回灌时按文本猜错幸存身份。</p>
     *
     * @param memberId 待删除成员的稳定 id
     * @return 是否成功提交删除
     */
    public boolean remove(long memberId) {
        List<?> raw = rawList();
        List<SceneSimpleList.ListItem> currentItems = safeItems();
        if (raw == null || raw.size() != currentItems.size()) return false;
        int index = indexOf(currentItems, memberId);
        if (index < 0) return false;
        ArrayList<Object> next = new ArrayList<Object>(raw);
        next.remove(index);
        ArrayList<SceneSimpleList.ListItem> nextItems = new ArrayList<SceneSimpleList.ListItem>(currentItems);
        nextItems.remove(index);
        try {
            onChange.accept(Collections.unmodifiableList(next));
            items.set(Collections.unmodifiableList(nextItems));
            Long target = editingId.get();
            if (target != null && target.longValue() == memberId) editingId.set(null);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private SearchPickerData.Selection decode(Object raw) {
        try { return codec.decodeMember(raw); }
        catch (RuntimeException exception) { return null; }
    }

    private int indexOf(long memberId) {
        return indexOf(safeItems(), memberId);
    }

    private static int indexOf(List<SceneSimpleList.ListItem> current, long memberId) {
        for (int index = 0; index < current.size(); index++) {
            if (current.get(index).getId() == memberId) return index;
        }
        return -1;
    }

    private List<?> rawList() {
        Object value = rawValue.get();
        return value instanceof List ? (List<?>) value : null;
    }

    private List<SceneSimpleList.ListItem> safeItems() {
        List<SceneSimpleList.ListItem> value = items.get();
        return value == null ? Collections.<SceneSimpleList.ListItem>emptyList() : value;
    }

    private static SearchPickerData.Candidate findCandidate(SearchPickerData.SearchResult results,
                                                             SearchPickerData.Selection selection) {
        if (results == null || selection == null) return null;
        for (SearchPickerData.Candidate candidate : results.candidates()) {
            if (candidate.key().equals(selection.candidateKey())) return candidate;
        }
        return null;
    }
}
