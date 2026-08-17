package club.heiqi.uilib.ui.scene.input;

/**
 * ClipboardBackend 工厂解耦接口 —— 宿主基类据此判定输入源能否提供系统剪贴板后端，
 * 不再认识具体平台实现类（守 I10：基类不依赖平台侧端点）。
 *
 * <p>由平台适配层（如 {@code club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource}）实现，
 * 宿主基类构造时若 {@code inputSource instanceof ClipboardBackendProvider} 则调用
 * {@link #createClipboardBackend()} 绑定剪贴板，与 {@link CursorBackendProvider} 路径同构。</p>
 *
 * <h3>等价语义</h3>
 * <p>非本接口实现的输入源不绑剪贴板（runtime 侧 {@code getClipboardBackend()} 返回 null，
 * 控件快捷键静默降级），行为与 {@code instanceof} false 分支等价。</p>
 */
public interface ClipboardBackendProvider {

    /**
     * 创建平台剪贴板后端实例。
     *
     * @return 新的剪贴板后端
     */
    ClipboardBackend createClipboardBackend();
}
