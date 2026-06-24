package club.heiqi.uilib.ui.scene.input;

/**
 * 光标后端抽象 —— 接收解析后的 {@link SceneCursor} 并应用到平台光标系统。
 *
 * <h3>设计理由（入核心包而非适配层）</h3>
 * <p>本接口仅声明 {@code void apply(SceneCursor)}，零平台依赖、零 LWJGL/Minecraft import，
 * 满足 I10 红线。置于核心包 {@code ui.scene.input} 使 {@code SceneRuntime.bindCursor(CursorBackend)}
 * 能直接引用本类型；实现类（{@code LwjglCursorBackend}）落适配层
 * {@code internal/devtools/pages}，在宿主接线时通过多态注入。</p>
 *
 * <h3>实现合同</h3>
 * <p>实现方必须静默 no-op 降级：反射全失败时不得抛异常打断输入链路（I4c 叫停关口⑤）。</p>
 */
public interface CursorBackend {

    /**
     * 将解析后的光标样式应用到平台光标系统。
     *
     * @param cursor 解析后的光标样式，不会为 null（Router 始终写 parsed 值）
     */
    void apply(SceneCursor cursor);

    /**
     * 强制把平台宿主光标同步到当前 Scene 期望值。
     *
     * <p>默认委托普通应用路径，供不需要区分缓存漂移的后端保持兼容。</p>
     *
     * @param cursor 当前 Scene 期望光标，不会为 null（Router 始终写 parsed 值）
     */
    default void forceApply(SceneCursor cursor) {
        apply(cursor);
    }
}
