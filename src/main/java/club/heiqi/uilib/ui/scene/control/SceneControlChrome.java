package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;


public final class SceneControlChrome {

    /** 纯静态工具类，禁止实例化。 */
    private SceneControlChrome() {
    }

    /**
     * 绑定 cursor 二态切换：enabled 用 enabledCursor，否则用 disabledCursor。
     *
     * <p>等价样板：
     * <pre>{@code
     * rt.bind(enabled,
     *     e -> node.setCursor(Boolean.TRUE.equals(e) ? enabledCursor : disabledCursor));
     * }</pre>
     * cursor 对按控件语义参数化（如 POINTER/NOT_ALLOWED、TEXT/NOT_ALLOWED、POINTER/DEFAULT），
     * setter {@code setCursor} 内部自动打出对应失效级别。</p>
     *
     * @param rt             场景运行时（提供 bind）
     * @param node           目标节点（cursor 写入槽）
     * @param enabled        是否启用信号
     * @param enabledCursor  启用时光标
     * @param disabledCursor 禁用时光标
     */
    public static void bindCursor(SceneRuntime rt, SceneNode node,
            ReadableSignal<Boolean> enabled, SceneCursor enabledCursor, SceneCursor disabledCursor) {
        rt.bind(enabled, e -> node.setCursor(Boolean.TRUE.equals(e) ? enabledCursor : disabledCursor));
    }

}
