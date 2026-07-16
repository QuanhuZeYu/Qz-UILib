package club.heiqi.uilib.ui.screen;

import club.heiqi.uilib.ui.render.UiRenderFrameAbortException;

/** 当前 screen 渲染帧的专用中止边界，不依赖 Minecraft 或 GL。 */
final class SceneFrameAbortBoundary {

    private SceneFrameAbortBoundary() { }

    /**
     * 执行当前帧渲染，只消费专用帧中止信号。
     *
     * @param renderAction 当前帧渲染动作
     * @return 是否安全中止了当前帧
     */
    static boolean run(Runnable renderAction) {
        if (renderAction == null) return false;
        try {
            renderAction.run();
            return false;
        } catch (UiRenderFrameAbortException expected) {
            return true;
        }
    }
}
