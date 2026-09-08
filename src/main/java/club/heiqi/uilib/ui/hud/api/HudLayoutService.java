package club.heiqi.uilib.ui.hud.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * HUD 用户布局服务（会话内唯一事实源）：`hudId -> {@link HudPlacement}`。
 *
 * <p>宿主（{@code SceneHudHost}）与打开态聊天容器都从这里取同一份放置，保证「打开与关闭
 * 聊天视图共享会话内布局状态」。本阶段（P2）只做会话内存，不跨重启持久化；P3 在此之上接
 * 配置持久化与 schemaVersion，不改读写语义。</p>
 *
 * <p><b>编辑会话（草稿）语义</b>：{@link #beginEdit()} 开始编辑，拖动只写草稿
 * （{@link #setDraft}），正常宿主继续读已提交布局；{@link #commitEdit()} 一次性提交草稿，
 * {@link #cancelEdit()} 丢弃整次会话修改。草稿值可为 {@code null} = 该项「恢复默认」，
 * 与「没有草稿覆盖」用 {@link #clearDraft} 区分。所有方法限客户端主线程；结构变更串行，
 * 读取用同步快照。</p>
 */
public final class HudLayoutService {

    private static final HudLayoutService INSTANCE = new HudLayoutService();

    /** 已提交布局（注册顺序）。 */
    private final Map<String, HudPlacement> committed = new LinkedHashMap<String, HudPlacement>();
    /** 编辑会话草稿：值 null = 该项恢复默认；无键 = 无草稿覆盖。 */
    private final Map<String, HudPlacement> draft = new LinkedHashMap<String, HudPlacement>();
    private boolean editing;
    /** 变更版本（Signal 驱动 UI 重算；值变才写，防每帧唤醒下游）。 */
    private final Signal<Integer> revision = Signal.create(Integer.valueOf(0));
    private int revisionValue;

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

    /** 提交编辑会话：草稿一次性落盘（null 值 = 删除覆盖）。 */
    public synchronized void commitEdit() {
        if (!editing) {
            return;
        }
        for (Map.Entry<String, HudPlacement> entry : draft.entrySet()) {
            if (entry.getValue() == null) {
                committed.remove(entry.getKey());
            } else {
                committed.put(entry.getKey(), entry.getValue());
            }
        }
        draft.clear();
        editing = false;
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

    /** 直接提交单项（非会话路径 / P3 持久化钩子）。 */
    public synchronized void commit(String hudId, HudPlacement placement) {
        if (hudId == null || placement == null) {
            return;
        }
        committed.put(hudId, placement);
        bump();
    }

    /** 删除单项覆盖（恢复默认布局）。 */
    public synchronized void reset(String hudId) {
        if (hudId == null) {
            return;
        }
        if (committed.remove(hudId) != null) {
            bump();
        }
    }

    /** 删除全部覆盖。 */
    public synchronized void resetAll() {
        if (committed.isEmpty()) {
            return;
        }
        committed.clear();
        bump();
    }

    /** 清空全部状态（测试与断线/世界卸载清理用）。 */
    public synchronized void clear() {
        committed.clear();
        draft.clear();
        editing = false;
        bump();
    }

    private void bump() {
        revisionValue++;
        revision.set(Integer.valueOf(revisionValue));
    }
}
