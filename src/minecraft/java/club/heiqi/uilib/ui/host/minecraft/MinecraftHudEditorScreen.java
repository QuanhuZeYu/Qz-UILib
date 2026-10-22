package club.heiqi.uilib.ui.host.minecraft;

import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.ScenePage;

/**
 * HUD 编辑页的薄宿主。拖动、保存、放弃与返回策略由业务页面声明，
 * 输入、ESC fallback 和资源释放沿 MinecraftSceneScreen 的正式会话完成。
 */
public final class MinecraftHudEditorScreen extends MinecraftSceneScreen {
    public MinecraftHudEditorScreen(ReactiveDomain domain, ScenePage page) {
        super(domain, page);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
