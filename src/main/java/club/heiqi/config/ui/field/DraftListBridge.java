package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 草稿列表本地 SSOT 桥：统一 SimpleList / CharacterRule 两份通用列表样板的
 * {@code localItems} 初始化、外部 reset 守卫与 commit 写回。
 *
 * <h3>职责边界</h3>
 * <ul>
 *   <li>初始化：draft 或局部 prefill 初值 → {@code List<T>} 经 {@code toItems} 建本地 signal 一次。</li>
 *   <li>reset 守卫：{@code rt.bind(draftSig)} 内用 {@link Effect#untrack} 读当前投影，
 *       与 incoming（可选 normalize）比较，不等才 {@code localItems.set(toItems(...))}。
 *       局部 prefill 活跃且 draft 仍空时，守卫不得用空 draft 冲掉本地可见列表。</li>
 *   <li>commit：按 {@link CommitMode} 区分——自建列表先 set 再 onFieldEdit；
 *       控件已 set（SimpleList）时只 onFieldEdit 不再二次 set（守 R7）。
 *       首次用户交互经 onFieldEdit 把完整可见列表写入 draft 并 dirty=true，
 *       同时结束局部 prefill 保护。</li>
 * </ul>
 *
 * <p><b>不合并</b> {@code ListItem} / {@code FontSortItem} / {@code CharacterRuleItem} 类型；
 * 不把 scrollbar 塞进桥。keyFn 由 caller 在 forEach 时传入（守 I5）。</p>
 *
 * <h3>合规</h3>
 * <ul>
 *   <li>R3：reset 守卫落 rt.bind effect，禁止进 Supplier 建树体。</li>
 *   <li>R7：{@link CommitMode#CONTROL_ALREADY_SET} 不二次 set localItems。</li>
 *   <li>I1/I11：守卫内读投影包 Effect.untrack，避免订阅环。</li>
 *   <li>I3：render 构建期不写 adapter signal / DraftBuffer；prefill 仅局部初值。</li>
 * </ul>
 *
 * @param <T> 行数据类型（ListItem / CharacterRuleItem 等）
 */
public final class DraftListBridge<T> {

    /**
     * commit 写回模式。
     */
    public enum CommitMode {
        /**
         * 自建列表（FontSort / CharacterRule）：handler 先 {@code localItems.set} 再 onFieldEdit。
         */
        SET_THEN_EDIT,
        /**
         * 控件已 set（SimpleList onItemsChanged）：只 onFieldEdit，不二次 set（守 R7）。
         */
        CONTROL_ALREADY_SET
    }

    /** 本地 SSOT 行列表。 */
    private final Signal<List<T>> localItems;
    /** draft Object → List&lt;String&gt;。 */
    private final Function<Object, List<String>> toDraftList;
    /** List&lt;String&gt; → List&lt;T&gt;（首次 / reset）。 */
    private final Function<List<String>, List<T>> toItems;
    /** List&lt;T&gt; → List&lt;String&gt; 投影。 */
    private final Function<List<T>, List<String>> projectValues;
    /** 可选：incoming 规范化（CharacterRule round-trip）；null 表示不规范化。 */
    private final Function<List<String>, List<String>> normalizeIncoming;
    /**
     * 关联适配器（可选）：用于 presentation seed 守卫——draft 仍空且仍有 seed 时，
     * 不用空 draft 冲掉本地可见列表。null 时仅依赖局部 prefill 标志。
     */
    private final DraftSignalAdapter adapter;
    /** 字段 path（与 adapter 配对）；null 时无 presentation 守卫。 */
    private final String fieldPath;
    /**
     * 局部 prefill 保护：initial 比 draft 更「满」时为 true，
     * 首次 commit 后清除；保护期间空 draft 不得冲本地列表。
     */
    private final AtomicBoolean localPrefillActive;

    private DraftListBridge(Signal<List<T>> localItems,
                            Function<Object, List<String>> toDraftList,
                            Function<List<String>, List<T>> toItems,
                            Function<List<T>, List<String>> projectValues,
                            Function<List<String>, List<String>> normalizeIncoming,
                            DraftSignalAdapter adapter,
                            String fieldPath,
                            boolean localPrefillActive) {
        this.localItems = localItems;
        this.toDraftList = toDraftList;
        this.toItems = toItems;
        this.projectValues = projectValues;
        this.normalizeIncoming = normalizeIncoming;
        this.adapter = adapter;
        this.fieldPath = fieldPath;
        this.localPrefillActive = new AtomicBoolean(localPrefillActive);
    }

    /**
     * 从 draft 首值建桥，并注册 reset 守卫（无 presentation 感知，向后兼容）。
     *
     * @param rt                场景运行时
     * @param draftSig          字段 draft signal
     * @param initialDraftList  已解析的初始 List&lt;String&gt;（caller 可先做局部 prefill）
     * @param toDraftList       draft Object → List&lt;String&gt;
     * @param toItems           List&lt;String&gt; → List&lt;T&gt;
     * @param projectValues     List&lt;T&gt; → List&lt;String&gt;
     * @param normalizeIncoming 可选 incoming 规范化；null 表示直接比对
     * @param <T>               行类型
     * @return 桥实例
     */
    public static <T> DraftListBridge<T> create(SceneRuntime rt,
                                                ReadableSignal<Object> draftSig,
                                                List<String> initialDraftList,
                                                Function<Object, List<String>> toDraftList,
                                                Function<List<String>, List<T>> toItems,
                                                Function<List<T>, List<String>> projectValues,
                                                Function<List<String>, List<String>> normalizeIncoming) {
        return create(rt, draftSig, initialDraftList, toDraftList, toItems, projectValues,
                normalizeIncoming, null, null);
    }

    /**
     * 从 draft / 局部 prefill 初值建桥，并注册 reset 守卫。
     *
     * <p>当 {@code initialDraftList} 非空而 draft signal 仍空时，开启局部 prefill 保护：
     * reset 守卫不会用空 draft 冲掉本地可见列表（守展示态与 draft 分离；不写 adapter signal）。</p>
     *
     * @param rt                场景运行时
     * @param draftSig          字段 draft signal
     * @param initialDraftList  已解析的初始 List&lt;String&gt;（caller 可先做局部 prefill）
     * @param toDraftList       draft Object → List&lt;String&gt;
     * @param toItems           List&lt;String&gt; → List&lt;T&gt;
     * @param projectValues     List&lt;T&gt; → List&lt;String&gt;
     * @param normalizeIncoming 可选 incoming 规范化；null 表示直接比对
     * @param adapter           草稿适配器（presentation 守卫兼容）；可为 null
     * @param fieldPath         字段 path；adapter 非 null 时建议非 null
     * @param <T>               行类型
     * @return 桥实例
     */
    public static <T> DraftListBridge<T> create(SceneRuntime rt,
                                                ReadableSignal<Object> draftSig,
                                                List<String> initialDraftList,
                                                Function<Object, List<String>> toDraftList,
                                                Function<List<String>, List<T>> toItems,
                                                Function<List<T>, List<String>> projectValues,
                                                Function<List<String>, List<String>> normalizeIncoming,
                                                DraftSignalAdapter adapter,
                                                String fieldPath) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(draftSig, "draftSig");
        Objects.requireNonNull(toDraftList, "toDraftList");
        Objects.requireNonNull(toItems, "toItems");
        Objects.requireNonNull(projectValues, "projectValues");
        List<String> seed = initialDraftList != null ? initialDraftList : Collections.emptyList();
        // 局部 prefill：initial 非空而 draft 仍空 → 保护本地列表直至首次 commit
        List<String> fromDraft = toDraftList.apply(draftSig.get());
        boolean prefillActive = !seed.isEmpty()
                && (fromDraft == null || fromDraft.isEmpty());
        Signal<List<T>> localItems = Signal.create(toItems.apply(seed));
        DraftListBridge<T> bridge = new DraftListBridge<>(
                localItems, toDraftList, toItems, projectValues, normalizeIncoming,
                adapter, fieldPath, prefillActive);
        bridge.installResetGuard(rt, draftSig);
        return bridge;
    }

    /**
     * @return 本地 SSOT 行列表 signal
     */
    public Signal<List<T>> localItems() {
        return localItems;
    }

    /**
     * 投影当前 localItems 为 List&lt;String&gt;。
     *
     * @return 投影列表
     */
    public List<String> project() {
        return projectValues.apply(localItems.get());
    }

    /**
     * 提交列表变更。
     *
     * @param path   字段 path
     * @param adapter 草稿适配器
     * @param next   下一版行列表
     * @param mode   写回模式
     */
    public void commit(String path, DraftSignalAdapter adapter, List<T> next, CommitMode mode) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(mode, "mode");
        List<T> safeNext = next == null ? Collections.emptyList() : next;
        List<T> immutable = Collections.unmodifiableList(new ArrayList<>(safeNext));
        if (mode == CommitMode.SET_THEN_EDIT) {
            localItems.set(immutable);
        }
        // 首次用户交互：结束局部 prefill 保护
        localPrefillActive.set(false);
        // CONTROL_ALREADY_SET：控件已 set，只写 draft
        adapter.onFieldEdit(path, projectValues.apply(immutable));
    }

    /**
     * 安装外部 reset 守卫（R3：rt.bind effect；投影读包 Effect.untrack）。
     *
     * <p>局部 prefill 或 presentation seed 期间：draft 仍为空时，跳过用空 draft 冲本地列表，
     * 保持用户可见的 prefill；用户首次 commit 后保护清除，守卫恢复正常。</p>
     *
     * @param rt       场景运行时
     * @param draftSig draft signal
     */
    private void installResetGuard(SceneRuntime rt, ReadableSignal<Object> draftSig) {
        rt.bind(draftSig, draftValue -> {
            List<String> incoming = toDraftList.apply(draftValue);
            boolean draftEmpty = incoming == null || incoming.isEmpty();
            // 局部 prefill 保护
            if (localPrefillActive.get() && draftEmpty) {
                return;
            }
            // 兼容：若仍有 presentation seed（非 render 路径）
            if (adapter != null && fieldPath != null
                    && adapter.hasPresentationSeed(fieldPath)
                    && draftEmpty) {
                return;
            }
            List<String> incomingForCompare = normalizeIncoming != null
                    ? normalizeIncoming.apply(incoming) : incoming;
            AtomicReference<List<String>> currentProjection =
                    new AtomicReference<>(Collections.emptyList());
            // 一律 untrack 读 localItems 投影，对齐 FontSort，避免 reset 守卫订阅环
            Effect.untrack(() -> currentProjection.set(projectValues.apply(localItems.get())));
            if (!incomingForCompare.equals(currentProjection.get())) {
                // reset 语义：用原始 incoming（非规范化）重建，保留用户字面输入
                localItems.set(toItems.apply(incoming));
            }
        });
    }
}
