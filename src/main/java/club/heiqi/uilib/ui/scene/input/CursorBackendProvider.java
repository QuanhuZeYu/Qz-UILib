package club.heiqi.uilib.ui.scene.input;

/**
 * CursorBackend 工厂解耦接口 —— 宿主基类据此判定输入源能否提供系统光标后端，
 * 不再认识具体平台实现类（守 I10：基类不依赖平台侧端点）。
 *
 * <p>由平台适配层（如 {@code club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource}）实现，
 * 宿主基类构造时若 {@code inputSource instanceof CursorBackendProvider} 则调用
 * {@link #createCursorBackend()} 绑定光标，等价于原 {@code instanceof LwjglInputSource} + {@code new LwjglCursorBackend()} 路径。</p>
 *
 * <h3>等价重构说明</h3>
 * <p>非本接口实现的输入源仍不绑 cursor（与原 {@code instanceof LwjglInputSource} false 分支等价），行为零变。</p>
 */
public interface CursorBackendProvider {

    /**
     * 创建平台光标后端实例。
     *
     * @return 新的光标后端
     */
    CursorBackend createCursorBackend();
}
