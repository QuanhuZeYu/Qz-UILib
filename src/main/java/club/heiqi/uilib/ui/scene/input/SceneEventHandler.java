package club.heiqi.uilib.ui.scene.input;

/**
 * 函数式接口：场景事件处理器。
 *
 * <p>handler 接收 {@link SceneEvent} 和 {@link SceneEventContext}，
 * 通过 context 可调用 {@link SceneEventContext#stopPropagation()} 阻止冒泡。</p>
 */
@FunctionalInterface
public interface SceneEventHandler {
    /**
     * 处理场景事件。
     *
     * @param event   事件数据对象（不可变）
     * @param context 派发上下文（可调用 stopPropagation 等）
     */
    void handle(SceneEvent event, SceneEventContext context);
}
