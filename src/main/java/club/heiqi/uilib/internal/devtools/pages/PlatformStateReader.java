package club.heiqi.uilib.internal.devtools.pages;

/**
 * 平台当前态读取接口 —— 适配层与差分算法之间的解耦边界。
 *
 * <p>生产实现（{@link LwjglStateReader}）通过 LWJGL 反射桥读取当前态（非破坏性），
 * 差分算法（{@link LwjglInputSource}）只依赖本接口，完全可纯沙箱测试。</p>
 *
 * <h3>坐标系契约</h3>
 * <p>{@link #mouseX()} / {@link #mouseY()} 返回的值必须是<b>逻辑像素</b>，
 * 且 Y 已由实现层翻转（原点在左上角）。RAW 坐标的下游不得再做 Y 翻转。</p>
 *
 * <h3>非破坏性语义</h3>
 * <p>所有方法只读当前平台状态，不消费任何事件队列。
 * 同一帧多次调用返回相同值。</p>
 */
public interface PlatformStateReader {

    /** @return 当前鼠标逻辑 X 坐标（Y 已翻转，原点左上角） */
    int mouseX();

    /** @return 当前鼠标逻辑 Y 坐标（Y 已翻转，原点左上角） */
    int mouseY();

    /**
     * 查询指定按钮是否按下（非破坏性当前态）。
     *
     * @param button LWJGL button code（0=左, 1=右, 2=中）
     * @return true 表示按下中
     */
    boolean buttonDown(int button);

    /**
     * 累计滚轮量（非破坏性，不清零）。
     *
     * <p>返回的是平台累计总量，调用方做帧间差分产 wheelDelta。</p>
     *
     * @return 累计滚轮值
     */
    double scrollAccum();

    /**
     * 滚轮单帧增量（破坏性读取，读后清零）—— fallback 路径。
     *
     * <p>对应平台原生滚轮增量 API（如 LWJGL {@code Mouse.getDWheel()}）：返回自上次调用以来的滚轮增量，
     * <b>读取后内部计数清零</b>。仅在 {@link #scrollAccum()} 差分路径无效
     * （真机上累计总量不更新）时由调用方使用，避免每帧清零影响其他层。</p>
     *
     * <p>默认返回 0（不破坏既有实现）。生产实现 {@link LwjglStateReader} 反射
     * 平台原生滚轮增量 API；测试 mock 可覆盖以注入非零值。具体平台 API 名见实现类 Javadoc。</p>
     *
     * @return 自上次调用以来的滚轮增量（正=向上，负=向下），不可用时 0
     */
    default int dWheelDelta() {
        return 0;
    }

    /** @return Ctrl 当前是否按下 */
    boolean control();

    /** @return Shift 当前是否按下 */
    boolean shift();

    /** @return Alt 当前是否按下 */
    boolean alt();

    /** @return Meta 当前是否按下 */
    boolean meta();

    /** @return 逻辑视口宽度（像素） */
    int logicalWidth();

    /** @return 逻辑视口高度（像素） */
    int logicalHeight();

    /** @return 单调递增纳秒时间戳 */
    long nowNanos();

    /**
     * 查询当前窗口是否处于焦点状态（非破坏性当前态）。
     *
     * <p>窗口失焦是合成 POINTER_CANCEL 的关键触发条件。
     * 生产实现通过 LWJGL Display.isActive() 反射获取；
     * 不可用时降级返回 true（保守：不误伤合成 cancel）。</p>
     *
     * @return true 表示窗口当前拥有焦点
     */
    boolean windowFocused();
}
