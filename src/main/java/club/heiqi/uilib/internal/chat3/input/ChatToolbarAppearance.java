package club.heiqi.uilib.internal.chat3.input;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.control.SceneGlassButtonStyle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 聊天动作与公共缩放按钮共用的配方；仅桥接既有进程设置，不持有节点或 runtime。 */
public final class ChatToolbarAppearance {
    // 设置可能从 UI 线程之外写入；只在现有 UI 帧内发布 Signal 通知。
    // Signal 按配方值去重，无变化帧不会唤醒按钮样式，也不会新增计时器。
    private static final Signal<UiBackdrop> OBSERVED_BACKDROP = Signal.create(readBackdrop());
    private static final ReadableSignal<SceneGlassButtonStyle> STYLE = () -> {
        OBSERVED_BACKDROP.get();
        // 同步读取设置，保证构建期和首次 flush 前也拿到当前配方。
        return SceneGlassButtonStyle.builder().backdrop(readBackdrop()).build();
    };

    private ChatToolbarAppearance() {}

    /** 动作按钮与公共工具显式共享此设置源，各自保留变体及布局。 */
    public static ReadableSignal<SceneGlassButtonStyle> style() { return STYLE; }

    /** 随当前挂载 Owner/runtime 清理；多个投放共享设置通知，不共享交互状态。 */
    static void observe(SceneRuntime rt) {
        rt.bind(rt.__frameTimeNanos(), frame -> OBSERVED_BACKDROP.set(readBackdrop()));
    }

    private static UiBackdrop readBackdrop() {
        return ChatMarkdownSettings.isGlassEnabled()
                ? UiBackdrop.liquidGlass(UiGlassMaterial.DARK_THIN,
                        Math.min(6, ChatMarkdownSettings.getGlassBlurRadiusPx()),
                        ChatMarkdownSettings.getGlassLensStrength())
                : null;
    }
}
