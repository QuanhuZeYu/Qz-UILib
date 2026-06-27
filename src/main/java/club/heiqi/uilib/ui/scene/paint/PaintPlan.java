package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 整棵场景树的绘制计划 —— Display List 的顶层载体。
 *
 * <p>{@code PaintPlan} 是数据层产出的最终绘制命令序列，渲染层只按顺序消费
 * 其中的 {@link PaintCommand}，不认识任何上游概念（宪章信条六/I6）。
 * 这是数据层与渲染层之间唯一的合同交付物。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * PaintPlan plan = new PaintPlan();
 * plan.addCommand(PaintCommand.background(0, 0, 100, 50, 0xFFFFFFFF));
 * plan.addCommand(PaintCommand.text(5, 10, "Hello", style));
 *
 * // 或从片段导入
 * plan.addFragment(fragment);
 *
 * // 渲染层：顺序消费扁平化命令列表
 * for (PaintCommand cmd : plan.getCommands()) {
 *     renderer.draw(cmd);
 * }
 * }</pre>
 */
public final class PaintPlan {

    /** 扁平化的有序绘制命令列表 */
    private final List<PaintCommand> commands;

    /**
     * 创建空的绘制计划。
     */
    public PaintPlan() {
        this.commands = new ArrayList<>();
    }

    /**
     * 追加单条绘制命令。
     *
     * @param command 绘制命令
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addCommand(PaintCommand command) {
        Objects.requireNonNull(command, "command");
        commands.add(command);
        return this;
    }

    /**
     * 追加一个节点绘制片段的所有命令，叠加绝对偏移后存入命令序列。
     *
     * <p>fragment 内的命令存储相对节点局部原点的坐标（方案 A），
     * 本方法将每条命令通过 {@link PaintCommand#translatedBy(int, int)}
     * 叠加 (offsetX, offsetY) 后得到最终屏幕绝对坐标。</p>
     *
     * @param fragment 节点绘制片段（命令为相对坐标）
     * @param offsetX  节点在屏幕上的绝对 X 偏移
     * @param offsetY  节点在屏幕上的绝对 Y 偏移
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addFragment(PaintFragment fragment, int offsetX, int offsetY) {
        Objects.requireNonNull(fragment, "fragment");
        for (PaintCommand cmd : fragment.getCommands()) {
            commands.add(cmd.translatedBy(offsetX, offsetY));
        }
        return this;
    }

    /**
     * 追加一个节点绘制片段的所有命令（offset 为 0 的便捷方法）。
     *
     * @param fragment 节点绘制片段
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addFragment(PaintFragment fragment) {
        return addFragment(fragment, 0, 0);
    }

    /**
     * 追加「进入 group opacity 合成作用域」边界命令（Phase 3B）。
     *
     * <p>由 {@link ScenePaintEngine#paintSubtree} 递归骨架在「本节点 + 全部后代命令」
     * 外层调用，与 {@link #addPopOpacity()} 严格配对。坐标为<b>绝对屏幕坐标</b>
     * （绘制引擎已叠加完累计 offset），不再经 fragment 相对坐标通路平移。</p>
     *
     * @param left    绝对左边界（像素）
     * @param top     绝对上边界（像素）
     * @param right   绝对右边界（像素）
     * @param bottom  绝对下边界（像素）
     * @param opacity 该层局部不透明度 [0,1]
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addPushOpacity(int left, int top, int right, int bottom, float opacity) {
        commands.add(PaintCommand.pushOpacity(left, top, right, bottom, opacity));
        return this;
    }

    /**
     * 追加「退出 group opacity 合成作用域」边界命令（Phase 3B）。
     *
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addPopOpacity() {
        commands.add(PaintCommand.popOpacity());
        return this;
    }

    /**
     * 追加「进入裁剪作用域」边界命令（Phase 4，任务 B）。
     *
     * <p>由 {@link ScenePaintEngine#paintSubtree} 递归骨架在「本节点 + 全部后代命令」
     * 外层调用，与 {@link #addClipPop()} 严格配对。坐标为<b>绝对屏幕坐标</b>
     * （绘制引擎已叠加完累计 offset），不再经 fragment 相对坐标通路平移。</p>
     *
     * @param left         绝对左边界（像素）
     * @param top          绝对上边界（像素）
     * @param right        绝对右边界（像素）
     * @param bottom       绝对下边界（像素）
     * @param cornerRadius 圆角半径（像素，0=矩形裁剪）
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addClipPush(int left, int top, int right, int bottom, int cornerRadius) {
        commands.add(PaintCommand.clipPush(left, top, right, bottom, cornerRadius));
        return this;
    }

    /**
     * 追加「退出裁剪作用域」边界命令（Phase 4，任务 B）。
     *
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addClipPop() {
        commands.add(PaintCommand.clipPop());
        return this;
    }

    /**
     * 追加「进入 transform 顶点变换作用域」边界命令（方案甲，合成级动画完整矩阵）。
     *
     * <p>由 {@link ScenePaintEngine#paintSubtree} 递归骨架在「本节点 + 全部后代命令」
     * 外层调用，与 {@link #addPopTransform()} 严格配对。坐标为<b>绝对屏幕坐标</b>，
     * transform 分量全 primitive（守 I6），每帧从 node 实时读，绝不进 fragment。</p>
     *
     * @param left          绝对左边界（像素）
     * @param top           绝对上边界（像素）
     * @param right         绝对右边界（像素）
     * @param bottom        绝对下边界（像素）
     * @param translateX    X 轴平移量（浮点像素）
     * @param translateY    Y 轴平移量（浮点像素）
     * @param rotateDegrees 绕 Z 轴顺时针旋转角度（度）
     * @param scaleX        X 轴缩放倍率
     * @param scaleY        Y 轴缩放倍率
     * @param originXRatio  变换原点 X 比率（box 归一化坐标）
     * @param originYRatio  变换原点 Y 比率（box 归一化坐标）
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addPushTransform(int left, int top, int right, int bottom,
                                      float translateX, float translateY, float rotateDegrees,
                                      float scaleX, float scaleY,
                                      float originXRatio, float originYRatio) {
        commands.add(PaintCommand.pushTransform(left, top, right, bottom,
                translateX, translateY, rotateDegrees, scaleX, scaleY, originXRatio, originYRatio));
        return this;
    }

    /**
     * 追加「退出 transform 顶点变换作用域」边界命令（方案甲，与 {@link #addPushTransform} 配对）。
     *
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addPopTransform() {
        commands.add(PaintCommand.popTransform());
        return this;
    }

    /**
     * 追加「进入 transform 离屏图层作用域」边界命令（B6 FBO 方案，transform+clip 叠加正确处理）。
     *
     * <p>由 {@link ScenePaintEngine#paintSubtree} 递归骨架在节点 transform 非恒等<b>且</b>有 clip 时
     * 于「本节点 + 全部后代命令」外层调用，与 {@link #addPopTransformLayer()} 严格配对。
     * 坐标为<b>绝对屏幕坐标</b>，transform 分量全 primitive（守 I6），每帧从 node 实时读，绝不进 fragment。</p>
     *
     * @param left          绝对左边界（像素）
     * @param top           绝对上边界（像素）
     * @param right         绝对右边界（像素）
     * @param bottom        绝对下边界（像素）
     * @param translateX    X 轴平移量（浮点像素）
     * @param translateY    Y 轴平移量（浮点像素）
     * @param rotateDegrees 绕 Z 轴顺时针旋转角度（度）
     * @param scaleX        X 轴缩放倍率
     * @param scaleY        Y 轴缩放倍率
     * @param originXRatio  变换原点 X 比率（box 归一化坐标）
     * @param originYRatio  变换原点 Y 比率（box 归一化坐标）
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addPushTransformLayer(int left, int top, int right, int bottom,
                                           float translateX, float translateY, float rotateDegrees,
                                           float scaleX, float scaleY,
                                           float originXRatio, float originYRatio) {
        commands.add(PaintCommand.pushTransformLayer(left, top, right, bottom,
                translateX, translateY, rotateDegrees, scaleX, scaleY, originXRatio, originYRatio));
        return this;
    }

    /**
     * 追加「退出 transform 离屏图层作用域」边界命令（B6 FBO 方案，与 {@link #addPushTransformLayer} 配对）。
     *
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addPopTransformLayer() {
        commands.add(PaintCommand.popTransformLayer());
        return this;
    }

    /**
     * 把另一个 plan 的所有命令按序追加到本 plan 尾部（子片段并入父片段）。
     *
     * <p>命令已是绝对坐标（{@link #addFragment(PaintFragment, int, int)} 时已叠加 offset），
     * 直接搬运不再平移。用于阶段 2 paint 并行：子树各产独立 plan 片段，父按 children 顺序
     * 合并，保持 PUSH→子片段→POP 嵌套与 DFS 前序 z-order 完全一致。</p>
     *
     * <p>线程安全语义：{@code other.getCommands()} 返回不可变视图，本方法只读 other、
     * 只写本 plan 的 {@code commands}。fork-join 中每个 worker 写自己的 localPlan，
     * join 点主线程串行 appendAll 子片段到父 localPlan，无并发写同一 list。</p>
     *
     * @param other 另一个 plan（命令按序并入本 plan 尾部）
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan appendAll(PaintPlan other) {
        Objects.requireNonNull(other, "other");
        this.commands.addAll(other.getCommands());
        return this;
    }

    /**
     * 返回扁平化的、供回放器顺序消费的命令序列。
     *
     * <p>渲染层只认识这个列表，每条命令自身就是绘制操作的完整描述，
     * 无需反查任何上游概念。</p>
     *
     * @return 不可变命令列表
     */
    public List<PaintCommand> getCommands() {
        return Collections.unmodifiableList(commands);
    }

    /**
     * 返回命令总条数。
     *
     * @return 命令数量
     */
    public int size() {
        return commands.size();
    }

    /**
     * 清空所有命令。
     */
    public void clear() {
        commands.clear();
    }

    @Override
    public String toString() {
        return "PaintPlan{size=" + commands.size() + "}";
    }
}
