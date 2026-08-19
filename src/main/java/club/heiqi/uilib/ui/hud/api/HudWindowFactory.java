package club.heiqi.uilib.ui.hud.api;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * HUD 虚拟窗口内容工厂：在窗口挂载时调用一次，用与 UI 页面完全相同的 scene 代码构建内容树。
 *
 * <p>契约：
 * <ul>
 *   <li>在客户端 render 主线程调用一次（窗口生命周期内不再重复调用）；</li>
 *   <li>内容变化走 signal（{@code Signal/Computed} + {@code rt.bind/mount/show}），宿主每帧
 *       经同一 {@link SceneFramePipeline} 帧管线物化，与 UI 页面同机制；</li>
 *   <li>返回内容根节点（非 null）；宿主提供外壳（背景/padding/clip/收缩宽度），
 *       内容树为空尺寸（signal 卸载/空文本）时整窗隐藏；</li>
 *   <li>宿主未注入输入源，窗口不接收输入；节点无需关心 HUD 宿主细节。</li>
 * </ul>
 */
@FunctionalInterface
public interface HudWindowFactory {
    /**
     * 构建 HUD 窗口内容树。
     *
     * @param runtime 窗口专属场景运行时（signal 绑定、组件挂载与 UI 页面同源）
     * @return 内容根节点（非 null）
     */
    SceneNode build(SceneRuntime runtime);
}
