package club.heiqi.uilib.ui.hud.api;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * 单个 HUD 的统一缩放状态（独立于外接工具栏注册）；客户端主线程使用，不改变宿主全局倍率或持久配置。
 *
 * <p>由 {@code HudScaleRegistry} 按 hudId 持有，{@code HudToolbarService.scale(String)} 是公开
 * 读取入口；关闭态宿主、打开态聊天屏与编辑态预览浮层共用同一实例，倍率口径只有一处真相。</p>
 */
public final class HudScaleState {
    public static final int MIN_PERCENT = 50;
    public static final int MAX_PERCENT = 200;
    public static final int DEFAULT_PERCENT = 100;
    public static final int STEP_PERCENT = 10;

    private int value = DEFAULT_PERCENT;
    private final Signal<Integer> changed = Signal.create(DEFAULT_PERCENT);
    // 同帧多次点击必须累计；通知延迟到 flush，读取始终返回最新请求值。
    private final ReadableSignal<Integer> percent = () -> {
        changed.get();
        return Integer.valueOf(value);
    };

    public ReadableSignal<Integer> percent() { return percent; }
    public float factor() { return percent.get().intValue() / 100.0f; }

    /** 设置百分比，越界夹取到 50–200；不限制调用方必须使用按钮步长。 */
    public void setPercent(int requested) {
        int next = Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, requested));
        if (next == value) return;
        value = next;
        changed.set(Integer.valueOf(value));
    }
    public void zoomOut() { setPercent(value - STEP_PERCENT); }
    public void zoomIn() { setPercent(value + STEP_PERCENT); }
    public void reset() { setPercent(DEFAULT_PERCENT); }
}
