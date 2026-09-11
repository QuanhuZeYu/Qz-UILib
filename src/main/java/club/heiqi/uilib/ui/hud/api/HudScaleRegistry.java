package club.heiqi.uilib.ui.hud.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HUD 统一缩放注册表（包内实现，不是公共 API）：{@code hudId -> HudScaleState}。
 *
 * <p>缩放是 HUD 自身的能力，不属于「外接工具栏」：本表把每 HUD 的倍率状态从
 * {@link HudToolbarService} 的工具栏注册项里独立出来，未注册外接工具栏的 HUD 同样拥有自己的
 * 倍率。宿主 {@code SceneHudHost}（关闭态 HUD）、打开态聊天屏与编辑态预览浮层都从这里取同一份
 * 状态，因此显示、布局与命中的倍率口径只有一处真相，不会出现「打开态缩放、关闭态不缩放」。</p>
 *
 * <p>契约：客户端主线程使用；惰性创建（首次读取即建默认 100% 状态）；工具栏注册/注销不重置
 * 倍率（生命周期独立于工具栏）；整体收口见 {@link HudToolbarService#clear()}（测试与整体关闭）。</p>
 */
final class HudScaleRegistry {

    private static final HudScaleRegistry INSTANCE = new HudScaleRegistry();

    private final Map<String, HudScaleState> states = new LinkedHashMap<String, HudScaleState>();

    private HudScaleRegistry() {
    }

    /** @return 全局注册表单例 */
    static HudScaleRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 取（必要时惰性创建）该 HUD 的统一缩放状态。
     *
     * @param hudId 目标 HUD id
     * @return 统一缩放状态；hudId 为 null/空白时返回 null
     */
    synchronized HudScaleState get(String hudId) {
        if (hudId == null || hudId.trim().isEmpty()) {
            return null;
        }
        HudScaleState state = states.get(hudId);
        if (state == null) {
            state = new HudScaleState();
            states.put(hudId, state);
        }
        return state;
    }

    /** 移除单项（某个 HUD 生命周期结束时清理；不影响其它 HUD 的倍率）。 */
    synchronized void remove(String hudId) {
        if (hudId != null) {
            states.remove(hudId);
        }
    }

    /** 清空全部（测试与整体关闭用）。 */
    synchronized void clear() {
        states.clear();
    }
}
