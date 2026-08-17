package club.heiqi.uilib.ui.scene.input;

/**
 * 剪贴板后端抽象 —— 提供系统剪贴板的同步文本读写。
 *
 * <h3>设计理由（入核心包而非适配层）</h3>
 * <p>本接口仅声明 {@code getClipboardText}/{@code setClipboardText}，零平台依赖、
 * 零 LWJGL/Minecraft import，满足 I10 红线。置于核心包 {@code ui.scene.input} 使
 * {@code SceneRuntime.bindClipboard(ClipboardBackend)} 能直接引用本类型；实现类
 * （{@code LwjglClipboardBackend}）落平台适配层 {@code ui.scene.host.lwjgl}，
 * 在宿主接线时通过多态注入（与 {@link CursorBackend} 同构）。</p>
 *
 * <h3>实现合同</h3>
 * <p>实现方必须静默 no-op 降级：平台不可用时 get 返回 null、set 静默忽略，
 * 不得抛异常打断输入链路（I4c 叫停关口⑤）。读写均为同步调用（帧内快捷键路径）。</p>
 */
public interface ClipboardBackend {

    /**
     * 读取系统剪贴板文本。
     *
     * @return 剪贴板文本；平台不可用或读取失败时返回 null
     */
    String getClipboardText();

    /**
     * 写入系统剪贴板文本。
     *
     * <p>平台不可用或写入失败时静默忽略（不抛异常）。</p>
     *
     * @param text 要写入的文本（null 时静默忽略）
     */
    void setClipboardText(String text);
}
