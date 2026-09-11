package club.heiqi.uilib.ui.hud.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;

/**
 * HUD 用户布局服务（会话内唯一事实源）：`hudId -> {@link HudPlacement}`。
 *
 * <p>宿主（`SceneHudHost`）与打开态聊天容器都从这里取同一份放置，保证「打开与关闭
 * 聊天视图共享会话内布局状态」。</p>
 *
 * <p><b>编辑会话（草稿）语义</b>：{@link #beginEdit()} 开始编辑，拖动只写草稿
 * （{@link #setDraft}），正常宿主继续读已提交布局；{@link #commitEdit()} 一次性提交草稿，
 * {@link #cancelEdit()} 丢弃整次会话修改。草稿值可为 {@code null} = 该项「恢复默认」，
 * 与「没有草稿覆盖」用 {@link #clearDraft} 区分。所有方法限客户端主线程；结构变更串行，
 * 读取用同步快照。</p>
 *
 * <p><b>持久化语义（{@link HudLayoutStore} 端口）</b>：位置不落绝对坐标，只落
 * 「四角锚点 + 该轴行程百分比（分母 = 可用空间 − 内容物理盒，口径见
 * {@link HudLayoutResolver#travelSpan}）+ 缩放百分比」。挂载端口后：加载解码持久记录
 * （含缩放）；编辑提交时按窗口中心自动选择最近角锚点并编码百分比；宿主经
 * {@link #observe} 上报每帧放置度量，度量变化时按百分比重算已提交偏移（视口动态化跟随）；
 * 缩放变更与提交合并为一次写盘。</p>
 *
 * <p><b>未接线零变化</b>：未挂端口时所有持久化路径直通——{@link #observe} 单次 volatile 读后返回，
 * 提交/重置保持既有内存语义（锚点不切换、偏移不改写、不产生 IO），行为与纯内存版本逐位一致。</p>
 */
public final class HudLayoutService {

    private static final Logger LOG = LogManager.getLogger("QzUILib HudLayout");

    private static final HudLayoutService INSTANCE = new HudLayoutService();

    /** 已提交布局（注册顺序）。 */
    private final Map<String, HudPlacement> committed = new LinkedHashMap<String, HudPlacement>();
    /** 编辑会话草稿：值 null = 该项恢复默认；无键 = 无草稿覆盖。 */
    private final Map<String, HudPlacement> draft = new LinkedHashMap<String, HudPlacement>();
    /** 相对持久记录（仅挂端口时存在）：hudId -> 锚点 + 百分比 + 缩放。 */
    private final Map<String, HudLayoutPreference> preferences =
            new LinkedHashMap<String, HudLayoutPreference>();
    /** 最近一次上报的放置度量（仅挂端口时存在）。 */
    private final Map<String, HudLayoutMetrics> metrics = new LinkedHashMap<String, HudLayoutMetrics>();
    private boolean editing;
    /** 变更版本（Signal 驱动 UI 重算；值变才写，防每帧唤醒下游）。 */
    private final Signal<Integer> revision = Signal.create(Integer.valueOf(0));
    private int revisionValue;

    /** 持久化端口；null = 未接线。 */
    private volatile HudLayoutStore store;
    /** 未接线快速路径标志（值 = store != null）：避免每帧 observe 进入同步块。 */
    private volatile boolean persistenceActive;
    /** 是否允许写回：加载文本无法识别（未知 schemaVersion/损坏/读取异常）时禁用，保护无法识别的数据。 */
    private boolean storeWritable;
    /** 有未写回变更（缩放变更/提交/重置）；下一次 observe 合并写一次，渲染路径不无条件写盘。 */
    private boolean dirty;

    private HudLayoutService() {
    }

    /** @return 全局服务单例 */
    public static HudLayoutService getInstance() {
        return INSTANCE;
    }

    /** @return 变更版本（Signal/Computed 依赖点） */
    public ReadableSignal<Integer> revision() {
        return revision;
    }

    /** @return 编辑会话是否进行中 */
    public synchronized boolean isEditing() {
        return editing;
    }

    /**
     * 生效放置：编辑中且该项有草稿时取草稿（含 null = 恢复默认），否则取已提交布局。
     *
     * @return 用户布局；无覆盖返回 null（调用方按注册规格算默认放置）
     */
    public synchronized HudPlacement placement(String hudId) {
        if (hudId == null) {
            return null;
        }
        if (editing && draft.containsKey(hudId)) {
            return draft.get(hudId);
        }
        return committed.get(hudId);
    }

    /** @return 是否有已提交覆盖（「恢复全部默认」可用性判定） */
    public synchronized boolean hasCommittedOverride(String hudId) {
        return hudId != null && committed.containsKey(hudId);
    }

    /** @return 是否存在任一已提交覆盖 */
    public synchronized boolean hasCommittedOverrides() {
        return !committed.isEmpty();
    }

    /** @return 编辑草稿是否有该项的非默认覆盖 */
    public synchronized boolean hasDraftOverride(String hudId) {
        return hudId != null && editing && draft.containsKey(hudId) && draft.get(hudId) != null;
    }

    /** 开始编辑会话：清空旧草稿，正常宿主仍读已提交布局。 */
    public synchronized void beginEdit() {
        draft.clear();
        editing = true;
        bump();
    }

    /** 写入草稿放置（拖动路径；非编辑态忽略）。 */
    public synchronized void setDraft(String hudId, HudPlacement placement) {
        if (!editing || hudId == null || placement == null) {
            return;
        }
        if (placement.equals(draft.get(hudId))) {
            return;
        }
        draft.put(hudId, placement);
        bump();
    }

    /** 草稿项恢复默认（提交时删除已提交覆盖；非编辑态忽略）。 */
    public synchronized void resetDraft(String hudId) {
        if (!editing || hudId == null) {
            return;
        }
        if (draft.containsKey(hudId) && draft.get(hudId) == null) {
            return;
        }
        draft.put(hudId, null);
        bump();
    }

    /** 全部已提交项在草稿中标记恢复默认（可取消；非编辑态忽略）。 */
    public synchronized void resetAllDraft() {
        if (!editing) {
            return;
        }
        for (String id : new ArrayList<String>(committed.keySet())) {
            draft.put(id, null);
        }
        bump();
    }

    /** 移除某项草稿覆盖（恢复「无覆盖」语义，拖动取消回滚用）。 */
    public synchronized void clearDraft(String hudId) {
        if (!editing || hudId == null) {
            return;
        }
        if (draft.containsKey(hudId)) {
            draft.remove(hudId);
            bump();
        }
    }

    /**
     * 提交编辑会话：草稿一次性落地（null 值 = 删除覆盖），随后一次性写回持久快照。
     *
     * <p>挂载端口且该 hudId 有度量时，提交会<b>自动选择最近角锚点</b>并把偏移编码为行程百分比；
     * 未挂端口/无度量时保持既有绝对偏移语义（零变化）。</p>
     */
    public synchronized void commitEdit() {
        if (!editing) {
            return;
        }
        for (Map.Entry<String, HudPlacement> entry : draft.entrySet()) {
            if (entry.getValue() == null) {
                committed.remove(entry.getKey());
                preferences.remove(entry.getKey());
            } else {
                commitInternal(entry.getKey(), entry.getValue());
            }
        }
        draft.clear();
        editing = false;
        if (persistenceActive) {
            saveLocked();
        }
        bump();
    }

    /** 取消编辑会话：丢弃整次会话修改，已提交布局不变。 */
    public synchronized void cancelEdit() {
        if (!editing && draft.isEmpty()) {
            return;
        }
        draft.clear();
        editing = false;
        bump();
    }

    /** 直接提交单项（非会话路径 / 持久化钩子；挂端口且有度量时同时编码相对记录）。 */
    public synchronized void commit(String hudId, HudPlacement placement) {
        if (hudId == null || placement == null) {
            return;
        }
        commitInternal(hudId, placement);
        if (persistenceActive) {
            saveLocked();
        }
        bump();
    }

    /** 删除单项覆盖（恢复默认布局）；挂端口时同步写回。 */
    public synchronized void reset(String hudId) {
        if (hudId == null) {
            return;
        }
        boolean removed = committed.remove(hudId) != null;
        removed |= preferences.remove(hudId) != null;
        if (!removed) {
            return;
        }
        if (persistenceActive) {
            saveLocked();
        }
        bump();
    }

    /** 删除全部位置覆盖；挂端口时同步写回（非默认缩放记录保留）。 */
    public synchronized void resetAll() {
        if (committed.isEmpty() && preferences.isEmpty()) {
            return;
        }
        committed.clear();
        preferences.clear();
        if (persistenceActive) {
            saveLocked();
        }
        bump();
    }

    /**
     * 清空全部会话状态（测试与断线/世界卸载清理用）。
     *
     * <p>只清内存：不写回、不摘除持久化端口——世界卸载不应把用户已落盘的布局清空。</p>
     */
    public synchronized void clear() {
        committed.clear();
        draft.clear();
        preferences.clear();
        metrics.clear();
        editing = false;
        dirty = false;
        bump();
    }

    // ==================== 持久化端口 ====================

    /**
     * 挂载持久化端口并立即加载（幂等：重复挂载以最后一次为准）。
     *
     * <p>加载语义：空文本/null = 首次运行 → 默认布局、允许写回；文本无法识别
     * （未知 schemaVersion/损坏/读取异常）→ 降级为默认布局并<b>禁用本次会话自动写回</b>
     * （不覆写无法识别的数据），不抛异常、不清空其它 hudId 的有效记录。位置覆盖需要度量
     * （{@link #observe}）才能解码，缺失时该项等同于「无覆盖 = 默认布局」，首次观测后生效。</p>
     *
     * @param value 宿主端口（不可为 null）
     */
    public void attachStore(HudLayoutStore value) {
        if (value == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        synchronized (this) {
            store = value;
            persistenceActive = true;
            storeWritable = false;
            HudScaleRegistry.getInstance().setChangeSink(this::onScaleChanged);
            reloadLocked();
        }
    }

    /**
     * 卸载持久化端口（幂等）：保留已提交内存放置，清除相对记录与度量缓存并解除缩放变更回调
     * （回到零开销路径）。
     */
    public void detachStore() {
        synchronized (this) {
            if (store == null) {
                return;
            }
            store = null;
            persistenceActive = false;
            storeWritable = false;
            dirty = false;
            preferences.clear();
            metrics.clear();
            HudScaleRegistry.getInstance().setChangeSink(null);
        }
    }

    /** @return 当前是否已挂载持久化端口（未挂载时持久化路径零开销直通） */
    public boolean hasStore() {
        return persistenceActive;
    }

    /** 重新加载持久数据（启动/手工刷新）；语义同 {@link #attachStore}，已生效内存覆盖由持久记录重建。 */
    public void reload() {
        if (!persistenceActive) {
            return;
        }
        synchronized (this) {
            reloadLocked();
        }
    }

    /** 立即写回当前快照（显式落盘点）；未挂端口或不允许写回时为 no-op。 */
    public void save() {
        if (!persistenceActive) {
            return;
        }
        synchronized (this) {
            saveLocked();
        }
    }

    /**
     * 上报某 HUD 当前帧的放置度量（视口/内容物理盒/安全区/偏移系数）。
     *
     * <p>未挂端口时立即返回（零额外开销）。挂载后：度量盒变化且该 hudId 有相对记录时，按已保存的
     * 行程百分比用新行程重算已提交偏移（视口动态化跟随）；同时合并写回未落盘变更（缩放变更去重，
     * 同一帧多次变更只写一次，未变更的帧不做 IO）。</p>
     *
     * @param hudId 目标 HUD id
     * @param value 本帧度量（不可为 null；须与宿主交给 {@link HudLayoutResolver#resolve} 的参数一致）
     */
    public void observe(String hudId, HudLayoutMetrics value) {
        if (!persistenceActive || hudId == null || value == null) {
            return;
        }
        synchronized (this) {
            HudLayoutMetrics previous = metrics.get(hudId);
            if (previous == null || !previous.sameBox(value)) {
                metrics.put(hudId, value);
                HudLayoutPreference preference = preferences.get(hudId);
                if (preference != null && preference.hasPlacement()) {
                    HudPlacement decoded = decode(preference, value);
                    if (!decoded.equals(committed.get(hudId))) {
                        committed.put(hudId, decoded);
                        bump();
                    }
                }
            }
            if (dirty) {
                saveLocked();
            }
        }
    }

    /**
     * @return 当前内存状态的持久化快照（位置记录 + 非默认缩放的仅缩放记录）；测试与写回共用
     *
     * <p>必须在持有本对象锁的主线程路径调用（{@link #saveLocked()} 内部使用）。</p>
     */
    HudLayoutData snapshot() {
        HudLayoutData.Builder builder = HudLayoutData.builder();
        for (Map.Entry<String, HudLayoutPreference> entry : preferences.entrySet()) {
            builder.put(entry.getValue().withScalePercent(
                    currentPercent(entry.getKey(), entry.getValue().getScalePercent())));
        }
        for (String hudId : HudScaleRegistry.getInstance().ids()) {
            if (preferences.containsKey(hudId)) {
                continue;
            }
            int percent = currentPercent(hudId, HudScaleState.DEFAULT_PERCENT);
            if (percent != HudScaleState.DEFAULT_PERCENT) {
                builder.put(HudLayoutPreference.scaleOnly(hudId, percent));
            }
        }
        return builder.build();
    }

    /** 落地单项已提交放置：有度量时自动选锚点 + 编码百分比，否则保持既有绝对偏移语义。 */
    private void commitInternal(String hudId, HudPlacement placement) {
        HudLayoutMetrics metric = persistenceActive ? metrics.get(hudId) : null;
        if (metric == null) {
            committed.put(hudId, placement);
            preferences.remove(hudId);
            return;
        }
        // 位置解析唯一走 HudLayoutResolver：先解析盒 → 选最近角锚点 → 逆换算得新锚点偏移
        AnchorRect box = HudLayoutResolver.resolve(placement, metric.getViewportWidth(),
                metric.getViewportHeight(), metric.getContentWidth(), metric.getContentHeight(),
                metric.getInsets());
        HudAnchor anchor = HudLayoutResolver.nearestAnchor(box, metric.getViewportWidth(),
                metric.getViewportHeight(), metric.getInsets());
        HudPlacement resolved = HudLayoutResolver.anchored(anchor, box, metric.getViewportWidth(),
                metric.getViewportHeight(), metric.getInsets());
        committed.put(hudId, HudPlacement.of(anchor,
                storeOffset(resolved.getOffsetX(), metric), storeOffset(resolved.getOffsetY(), metric)));
        preferences.put(hudId, HudLayoutPreference.of(hudId, anchor,
                fraction(resolved.getOffsetX(), travelSpanX(metric)),
                fraction(resolved.getOffsetY(), travelSpanY(metric)),
                currentPercent(hudId, HudScaleState.DEFAULT_PERCENT)));
    }

    /** 按相对记录与最新度量还原放置（视口/内容变化后按百分比跟随）。 */
    private static HudPlacement decode(HudLayoutPreference preference, HudLayoutMetrics metric) {
        int offsetX = decodeOffset(preference.getFractionX(), travelSpanX(metric));
        int offsetY = decodeOffset(preference.getFractionY(), travelSpanY(metric));
        double scale = metric.getOffsetScale();
        return HudPlacement.of(preference.getAnchor(),
                (int) Math.round(offsetX / scale), (int) Math.round(offsetY / scale));
    }

    private static int travelSpanX(HudLayoutMetrics metric) {
        return HudLayoutResolver.travelSpan(metric.getViewportWidth(), metric.getContentWidth(),
                metric.getInsets().getLeft(), metric.getInsets().getRight());
    }

    private static int travelSpanY(HudLayoutMetrics metric) {
        return HudLayoutResolver.travelSpan(metric.getViewportHeight(), metric.getContentHeight(),
                metric.getInsets().getTop(), metric.getInsets().getBottom());
    }

    /** 解析空间偏移 → 行程百分比（分母为 0 时恒 0；越界偏移按 [0, travelSpan] 收敛）。 */
    private static double fraction(int offset, int span) {
        if (span <= 0) {
            return 0.0;
        }
        int bounded = offset < 0 ? 0 : (offset > span ? span : offset);
        return bounded / (double) span;
    }

    /** 行程百分比 → 解析空间偏移（分母为 0 或百分比 NaN 时恒 0；结果钳进 [0, travelSpan]）。 */
    private static int decodeOffset(double fraction, int span) {
        if (span <= 0 || Double.isNaN(fraction)) {
            return 0;
        }
        double bounded = fraction < 0.0 ? 0.0 : (fraction > 1.0 ? 1.0 : fraction);
        int offset = (int) Math.round(bounded * span);
        return offset < 0 ? 0 : (offset > span ? span : offset);
    }

    private static int storeOffset(int resolveOffset, HudLayoutMetrics metric) {
        return (int) Math.round(resolveOffset / (double) metric.getOffsetScale());
    }

    private static int currentPercent(String hudId, int fallback) {
        HudScaleState state = HudScaleRegistry.getInstance().peek(hudId);
        return state == null ? fallback : state.percent().get().intValue();
    }

    /** 缩放百分比实际变化 → 置脏；下一次 observe 合并写一次（不在点击回调里做 IO）。 */
    private synchronized void onScaleChanged() {
        if (persistenceActive) {
            dirty = true;
        }
    }

    private void reloadLocked() {
        HudLayoutStore current = store;
        if (current == null) {
            return;
        }
        String text = null;
        boolean readFailed = false;
        try {
            text = current.load();
        } catch (RuntimeException failure) {
            readFailed = true;
            LOG.warn("HUD 布局持久化读取异常，已降级为默认布局且本次会话不自动写回", failure);
        }
        if (readFailed) {
            storeWritable = false;
            dirty = false;
            return;
        }
        if (text != null && !text.trim().isEmpty()) {
            HudLayoutData data = HudLayoutData.parse(text);
            if (data == null) {
                LOG.warn("HUD 布局持久化数据无法识别（版本或格式不符），已降级为默认布局且本次会话不自动写回");
                storeWritable = false;
                dirty = false;
                return;
            }
            applyLocked(data);
        }
        storeWritable = true;
        dirty = false;
    }

    private void applyLocked(HudLayoutData data) {
        for (HudLayoutPreference preference : data.getEntries().values()) {
            String hudId = preference.getHudId();
            if (preference.hasPlacement()) {
                preferences.put(hudId, preference);
                HudLayoutMetrics metric = metrics.get(hudId);
                if (metric == null) {
                    // 度量未知：等同于「无覆盖 = 默认布局」，首次 observe 时解码生效
                    committed.remove(hudId);
                } else {
                    committed.put(hudId, decode(preference, metric));
                }
            } else {
                preferences.remove(hudId);
            }
            applyScale(hudId, preference.getScalePercent());
        }
        bump();
    }

    private static void applyScale(String hudId, int scalePercent) {
        HudScaleState state = HudToolbarService.getInstance().scale(hudId);
        if (state != null) {
            state.setPercent(scalePercent);
        }
    }

    private void saveLocked() {
        dirty = false;
        HudLayoutStore current = store;
        if (current == null || !storeWritable) {
            return;
        }
        String text = snapshot().toText();
        try {
            current.save(text);
        } catch (RuntimeException failure) {
            LOG.warn("HUD 布局写回失败，本次变更仅保留在内存", failure);
        }
    }

    private void bump() {
        revisionValue++;
        revision.set(Integer.valueOf(revisionValue));
    }
}
