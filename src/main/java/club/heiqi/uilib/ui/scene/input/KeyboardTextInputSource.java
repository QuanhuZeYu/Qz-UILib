package club.heiqi.uilib.ui.scene.input;

/**
 * 键盘/文本输入旁路源解耦接口 —— 宿主基类据此判定输入源是否支持键盘/文本旁路，
 * 不再认识具体平台实现类（守 I10：基类不依赖平台侧端点）。
 *
 * <p>由平台适配层（如 {@code club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource}）实现，
 * 宿主基类只持本接口引用，等价于原 {@code instanceof LwjglInputSource} 判定。</p>
 *
 * <h3>等价重构说明</h3>
 * <p>本接口方法签名与原 {@code LwjglInputSource} 公开方法一一对应，
 * 行为零变：非本接口实现的输入源仍不推键盘/文本（与原 {@code instanceof LwjglInputSource} false 分支等价）。</p>
 */
public interface KeyboardTextInputSource {

    /**
     * 推入键盘按下事件（含字符与原生键码）。
     *
     * @param typedChar     输入字符（'\0' 表示无字符）
     * @param nativeKeyCode 平台原生键码
     * @param timeNanos     事件时间戳（纳秒）
     */
    void pushKeyTyped(char typedChar, int nativeKeyCode, long timeNanos);

    /**
     * 推入完整文本（外部文本旁路，含 IME/补充平面 emoji）。
     *
     * @param text      完整文本内容
     * @param timeNanos 事件时间戳（纳秒）
     */
    void pushText(String text, long timeNanos);

    /**
     * 切换外部文本模式。
     *
     * @param external true=外部 onTextEvent 接管文本；false=降级 char 路径
     */
    void setExternalTextMode(boolean external);
}
