package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 整棵场景树的绘制计划 —— Display List 的顶层载体。
 *
 * <p>{@code PaintPlan} 是数据层产出的最终绘制命令序列，渲染层只按顺序消费
 * 其中的 {@link PaintCommand}，不认识任何上游概念。
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

    /**
     * 有序条目序列：每个元素是 {@link PaintCommand}（绝对坐标的单命令，含全部作用域边界命令）
     * 或 {@link FragmentSlot}（片段 + 绝对偏移）。
     *
     * <p><b>P1-3</b>：片段不再在组装期逐命令 {@code translatedBy}（N 个节点 ≈ 2N 个新命令对象/帧），
     * 而是整片入表、把平移推迟到 replay。条目数 = 节点数（而不是命令数），组装期分配随之降一个量级。</p>
     */
    private final List<Object> slots;

    /**
     * {@link #getCommands()} 的物化结果（懒建缓存）；任何写入后置 {@code null} 失效。
     *
     * <p>公开读取面（测试与诊断按值消费绝对坐标命令）语义逐位不变：物化时对片段内每条命令
     * 调 {@link PaintCommand#translatedBy(int, int)}，与旧组装期行为完全一致。</p>
     */
    private List<PaintCommand> materialized;

    /** 命令总条数（写入口增量维护；片段按其命令数计入），使 {@link #size()} 保持 O(1) 且零分配。 */
    private int commandCount;

    /**
     * 创建空的绘制计划。
     */
    public PaintPlan() {
        this.slots = new ArrayList<>();
    }

    /**
     * 追加单条绘制命令（坐标已是绝对屏幕坐标）。
     *
     * @param command 绘制命令
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addCommand(PaintCommand command) {
        Objects.requireNonNull(command, "command");
        slots.add(command);
        commandCount++;
        materialized = null;
        return this;
    }

    /**
     * 把另一个计划的全部条目追加到本计划末尾（保留「片段 + 偏移」形态，不物化命令）。
     *
     * <p>用途：宿主级包装计划（如窗口裁剪盒）需要在不重建绝对命令的前提下前后插入边界命令。
     * 纯加法，不改变任何既有语义。</p>
     *
     * @param source 源计划（非 null；等于自身时原样返回）
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addPlan(PaintPlan source) {
        Objects.requireNonNull(source, "source");
        if (source == this) {
            return this;
        }
        slots.addAll(source.slots);
        commandCount += source.commandCount;
        materialized = null;
        return this;
    }

    /**
     * 追加一个节点绘制片段的所有命令，叠加绝对偏移后存入命令序列。
     *
     * <p>fragment 内的命令存储相对节点局部原点的坐标（方案 A）：<b>P1-3 起本方法只登记
     * 「片段 + 偏移」条目，平移推迟到 {@code ScenePaintReplayer} 回放时叠加</b>
     * （回放器已支持逐命令偏移），组装期不再逐命令 new PaintCommand。对外语义不变：
     * {@link #getCommands()} 仍返回叠加偏移后的绝对坐标命令，回放结果逐像素等价。</p>
     *
     * @param fragment 节点绘制片段（命令为相对坐标）
     * @param offsetX  节点在屏幕上的绝对 X 偏移
     * @param offsetY  节点在屏幕上的绝对 Y 偏移
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addFragment(PaintFragment fragment, int offsetX, int offsetY) {
        Objects.requireNonNull(fragment, "fragment");
        slots.add(new FragmentSlot(fragment, offsetX, offsetY));
        commandCount += fragment.size();
        materialized = null;
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
     * <p>由 {@link ScenePaintEngine#paintNode} 递归骨架在「本节点 + 全部后代命令」
     * 外层调用，与 {@link #addPopOpacity()} 严格配对。区域为<b>子树内容包围盒</b>的
     * 绝对屏幕坐标（绘制引擎已叠加完累计 offset，不再经 fragment 相对坐标通路平移）。
     * 离屏层全屏分配、pop 按本区域做回贴 UV 采样窗口——若钉死节点自身盒会把
     * opacity&lt;1 期间溢出盒外的后代内容隐式硬裁（P10 修复）。<b>group opacity
     * 不裁剪子树内容</b>：显式裁剪（CLIP/scissor）在离屏层内照常生效，与放大后的
     * 回贴窗口正交。</p>
     *
     * @param left    子树内容包围盒左边界（绝对屏幕坐标，像素）
     * @param top     子树内容包围盒上边界（绝对屏幕坐标，像素）
     * @param right   子树内容包围盒右边界（绝对屏幕坐标，像素）
     * @param bottom  子树内容包围盒下边界（绝对屏幕坐标，像素）
     * @param opacity 该层局部不透明度 [0,1]
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addPushOpacity(int left, int top, int right, int bottom, float opacity) {
        addCommand(PaintCommand.pushOpacity(left, top, right, bottom, opacity));
        return this;
    }

    /**
     * 追加「退出 group opacity 合成作用域」边界命令（Phase 3B）。
     *
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addPopOpacity() {
        addCommand(PaintCommand.popOpacity());
        return this;
    }

    /**
     * 追加「进入裁剪作用域」边界命令（Phase 4，任务 B）。
     *
     * <p>由 {@link ScenePaintEngine#paintNode} 递归骨架在「本节点 + 全部后代命令」
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
        addCommand(PaintCommand.clipPush(left, top, right, bottom, cornerRadius));
        return this;
    }

    /**
     * 追加「退出裁剪作用域」边界命令（Phase 4，任务 B）。
     *
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addClipPop() {
        addCommand(PaintCommand.clipPop());
        return this;
    }

    /**
     * 追加「进入 transform 顶点变换作用域」边界命令（方案甲，合成级动画完整矩阵）。
     *
     * <p>由 {@link ScenePaintEngine#paintNode} 递归骨架在「本节点 + 全部后代命令」
     * 外层调用，与 {@link #addPopTransform()} 严格配对。坐标为<b>绝对屏幕坐标</b>，
     * transform 分量全 primitive（不含 scene 侧类型），每帧从 node 实时读，绝不进 fragment。</p>
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
        addCommand(PaintCommand.pushTransform(left, top, right, bottom,
                translateX, translateY, rotateDegrees, scaleX, scaleY, originXRatio, originYRatio));
        return this;
    }

    /**
     * 追加「退出 transform 顶点变换作用域」边界命令（方案甲，与 {@link #addPushTransform} 配对）。
     *
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addPopTransform() {
        addCommand(PaintCommand.popTransform());
        return this;
    }

    /**
     * 追加「进入 transform 离屏图层作用域」边界命令（B6 FBO 方案，transform+clip 叠加正确处理）。
     *
     * <p>由 {@link ScenePaintEngine#paintNode} 递归骨架在节点 transform 非恒等<b>且</b>
     * （needClip 或 preferTransformLayer）时于「本节点 + 全部后代命令」外层调用，与
     * {@link #addPopTransformLayer()} 严格配对。区域为<b>子树内容包围盒</b>的绝对屏幕坐标
     * （P10b：pop 回贴窗口与 opacity 同源——离屏层全屏分配，窗口钉节点盒会把溢出后代隐式硬裁）。
     * transform 分量全 primitive（不含 scene 侧类型），每帧从 node 实时读，绝不进 fragment；origin 分量已由
     * 引擎折算为包围盒坐标系下的等价比率，绝对变换原点仍锚定节点自身盒（box 归一化语义不变）。</p>
     *
     * @param left          子树内容包围盒左边界（绝对屏幕坐标，像素）
     * @param top           子树内容包围盒上边界（绝对屏幕坐标，像素）
     * @param right         子树内容包围盒右边界（绝对屏幕坐标，像素）
     * @param bottom        子树内容包围盒下边界（绝对屏幕坐标，像素）
     * @param translateX    X 轴平移量（浮点像素）
     * @param translateY    Y 轴平移量（浮点像素）
     * @param rotateDegrees 绕 Z 轴顺时针旋转角度（度）
     * @param scaleX        X 轴缩放倍率
     * @param scaleY        Y 轴缩放倍率
     * @param originXRatio  变换原点 X 比率（已折算到包围盒坐标系，绝对原点锚定节点盒）
     * @param originYRatio  变换原点 Y 比率（已折算到包围盒坐标系，绝对原点锚定节点盒）
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addPushTransformLayer(int left, int top, int right, int bottom,
                                           float translateX, float translateY, float rotateDegrees,
                                           float scaleX, float scaleY,
                                           float originXRatio, float originYRatio) {
        addCommand(PaintCommand.pushTransformLayer(left, top, right, bottom,
                translateX, translateY, rotateDegrees, scaleX, scaleY, originXRatio, originYRatio));
        return this;
    }

    /**
     * 追加「退出 transform 离屏图层作用域」边界命令（B6 FBO 方案，与 {@link #addPushTransformLayer} 配对）。
     *
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addPopTransformLayer() {
        addCommand(PaintCommand.popTransformLayer());
        return this;
    }

    /**
     * 返回扁平化的、供回放器顺序消费的命令序列（绝对屏幕坐标）。
     *
     * <p>渲染层只认识这个列表，每条命令自身就是绘制操作的完整描述，
     * 无需反查任何上游概念。</p>
     *
     * <p>P1-3：片段条目在此<b>按需物化</b>（叠加偏移得到绝对坐标），结果按计划实例缓存，
     * 任何写入后失效。回放路径不走本方法（它直接消费条目，见 {@code ScenePaintReplayer}），
     * 故每帧稳态不产生这份物化分配。</p>
     *
     * @return 不可变命令列表
     */
    public List<PaintCommand> getCommands() {
        if (materialized == null) {
            List<PaintCommand> flat = new ArrayList<PaintCommand>(commandCount);
            for (int i = 0; i < slots.size(); i++) {
                Object slot = slots.get(i);
                if (slot instanceof PaintCommand) {
                    flat.add((PaintCommand) slot);
                } else {
                    FragmentSlot fragmentSlot = (FragmentSlot) slot;
                    List<PaintCommand> commands = fragmentSlot.fragment.getCommands();
                    for (int j = 0; j < commands.size(); j++) {
                        flat.add(commands.get(j).translatedBy(fragmentSlot.offsetX, fragmentSlot.offsetY));
                    }
                }
            }
            materialized = Collections.unmodifiableList(flat);
        }
        return materialized;
    }

    /**
     * 返回命令总条数（片段按其命令数计入）。
     *
     * @return 命令数量
     */
    public int size() {
        return commandCount;
    }

    /**
     * 清空所有命令。
     */
    public void clear() {
        slots.clear();
        commandCount = 0;
        materialized = null;
    }

    /**
     * 条目序列（包内视图，回放器按「单命令 / 片段+偏移」分派消费；不做防御性拷贝）。
     *
     * @return 条目列表（元素为 {@link PaintCommand} 或 {@link FragmentSlot}）
     */
    List<Object> __slots() {
        return slots;
    }

    @Override
    public String toString() {
        return "PaintPlan{commands=" + commandCount + ", entries=" + slots.size() + "}";
    }

    /**
     * 「片段 + 绝对偏移」条目：把片段内每条命令的平移推迟到 replay。
     *
     * <p>片段自身按节点缓存跨帧复用，偏移是每帧的绝对位置，二者在此解耦——
     * 这正是原 {@code translatedBy} 每帧重建命令所付出的分配被消除的原因。</p>
     */
    static final class FragmentSlot {

        /** 节点绘制片段（命令为节点局部坐标）。 */
        final PaintFragment fragment;

        /** 节点在屏幕上的绝对 X 偏移。 */
        final int offsetX;

        /** 节点在屏幕上的绝对 Y 偏移。 */
        final int offsetY;

        FragmentSlot(PaintFragment fragment, int offsetX, int offsetY) {
            this.fragment = fragment;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }
}
