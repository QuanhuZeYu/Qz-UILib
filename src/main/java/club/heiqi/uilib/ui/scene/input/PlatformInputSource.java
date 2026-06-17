package club.heiqi.uilib.ui.scene.input;

/**
 * 平台输入源接口。
 *
 * <p>定义 UI 系统从平台获取标准化输入帧的契约。平台适配层实现此接口，
 * 将 LWJGL/GLFW 等原生事件转换为 {@link SceneInputFrame} 后供核心消费。</p>
 *
 * <h3>契约要点</h3>
 * <ol>
 *   <li>{@link #drainFrame()} 为一次性消费语义：每次调用返回当前累积的事件帧，
 *       同时清空内部缓冲。事件不跨帧重复投递。</li>
 *   <li>指针位置与修饰键状态为粘滞态：{@code drainFrame()} 后保留当前值到下帧，
 *       不因帧切换归零。</li>
 *   <li>无事件时 {@code drainFrame()} 必须返回 {@link SceneInputFrame#EMPTY} 单例，
 *       不得分配新对象。</li>
 *   <li>{@link #logicalWidth()} / {@link #logicalHeight()} 返回当前逻辑视口尺寸，
 *       供 UI 系统做坐标归一化与布局计算。</li>
 *   <li>此接口不依赖任何平台特定类型（LWJGL/GLFW/Minecraft），
 *       确保核心与平台完全解耦。</li>
 * </ol>
 */
public interface PlatformInputSource {

    /**
     * 排空当前累积事件帧。
     *
     * <p>一次性消费语义：返回当前累积的所有输入事件组成的不可变帧快照，
     * 同时清空内部事件缓冲。指针位置和修饰键状态保留到下帧。</p>
     *
     * @return 当前帧输入快照，无事件时返回 {@link SceneInputFrame#EMPTY}
     */
    SceneInputFrame drainFrame();

    /**
     * 当前逻辑视口宽度。
     *
     * @return 逻辑像素宽度
     */
    int logicalWidth();

    /**
     * 当前逻辑视口高度。
     *
     * @return 逻辑像素高度
     */
    int logicalHeight();
}
