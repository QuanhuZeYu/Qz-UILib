package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextHorizontalAlign;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneParallelExecutor;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import com.github.bsideup.jabel.Desugar;

/**
 * 场景树绘制引擎 —— 将节点树 + 布局结果转换为纯数据 Display List。
 *
 * <h3>方案 A：相对坐标 + offset 解耦</h3>
 * <p>每个节点产出的 PaintFragment 内命令存储<b>相对节点局部原点</b>的坐标（从
 * {@code (0,0)} 起）。组装 PaintPlan 时，通过 {@link PaintPlan#addFragment(PaintFragment, int, int)}
 * 叠加该节点的当前绝对偏移（来自 cachedLayout.x/y + 祖先累加），得到最终屏幕坐标。</p>
 *
 * <p>这使 fragment 可跨帧复用（节点位置变化 → fragment 引用不变、仅叠加的 offset 变）。</p>
 *
 * <h3>几何变化检测（geometryDirty 标记）</h3>
 * <p>layout 引擎产出新的 LayoutBox 时，若位置/尺寸变化则调 {@link SceneNode#markGeometryDirty()}
 * 置 selfGeometryDirty + 沿祖先冒泡 descendantGeometryDirty。paint 遍历读取此标记
 * 下沉到位置变化节点：selfPaintDirty==false 时复用 fragment、仅用新 offset 重新叠加坐标；
 * selfPaintDirty==true 时正常重生成 fragment。</p>
 *
 * <h3>I8 缓存复用（单节点 PaintFragment 按 selfPaintDirty 判定）</h3>
 * <ul>
 *   <li><b>selfPaintDirty==false && cache存在 → 复用 fragment</b>：包括 geometry 脏场景（仅 offset 不同），
 *       也包括 paint/geometry 双 false 场景。复用后仍递归子节点（每帧 O(N) 遍历重拼 display list
 *       是保留式渲染正常代价，plan 级跨帧缓存是 Phase 3+ 的事）。</li>
 *   <li><b>selfPaintDirty==true → 重新生成 fragment</b>：属性变化，重绘。</li>
 * </ul>
 *
 * <h3>绝对禁止</h3>
 * <ul>
 *   <li>任何 version 号比较</li>
 *   <li>向下递归刷脏（mark* 式行为）</li>
 *   <li>import 旧栈 ui.dom / ui.paint / ui.layout / ui.component / ui.control</li>
 * </ul>
 */
public class ScenePaintEngine {

    /** opacity 接近 1.0 的容差：差值小于此值视为完全不透明，走快速路径跳过 group 边界 */
    private static final float OPACITY_EPSILON = 1e-4f;

    // ==================== 阶段 2.5 paint 并行化：fork 门槛 ====================
    //
    // fork 决策门槛已统一集中到 {@link SceneParallelExecutor} 管理（阶段 2 第三批），
    // 运行时可通过 SceneParallelExecutor.setPaintForkThreshold(...) 等动态调，
    // 供 demo 页 slider 真机校准。本类不再持有阈值常量，改读 SceneParallelExecutor。
    //
    // ★ worker render-scoped 不变量（NORTH_STAR 硬约束）：worker 必须 render 内 fork、
    //   返回前 join，不跨帧存活。pool.invoke 同步等是此不变量的落地形式。
    // ★ fork 粒度=整棵子树（含该子树根的 PUSH/POP 边界），天然保证 PUSH/POP 边界
    //   整段落同一 worker，无跨节点配对竞态。

    // ==================== 阶段 2.5 paint 并行化：子树结果 + fork-join 任务 ====================
    //
    // 以下 record + RecursiveTask 是步骤 2.5 的核心：把 paintNode 子循环从串行递归
    // 改成 ForkJoinPool 分治。达阈值的子树 fork 为 PaintSubtreeTask，worker 内各自
    // 产独立 localPlan（无共享可变 plan），join 点主线程串行 appendAll 合并到父 localPlan。
    //
    // ★ 与 layout 2.4 同构：fork 决策门槛、worker render-scoped、join 按 fork 顺序
    //   （= children 顺序）保证 z-order 确定性。
    // ★ 与 layout 2.4 不同：paint 无 bubble 信号（paint 脏标记在 worker 内直接清自己
    //   子树节点，无跨祖先冒泡写），故 PaintSubtreeResult 只需 (plan, regenerated)。
    // ★ PUSH/POP 嵌套天然保证：本节点 PUSH → fragment → 子片段们（appendAll）→ POP，
    //   与现状 DFS 前序完全一致；fork 粒度=整棵子树，PUSH/POP 边界整段落同一 worker。

    /**
     * 子树绘制结果：worker 产出的独立 {@link PaintPlan} + 本子树重生成 fragment 数。
     *
     * <ul>
     *   <li>{@code plan}：本子树独立 plan（含本子树根的 PUSH/POP 边界 + fragment + 后代片段），
     *       命令已是绝对坐标，父 join 点通过 {@link PaintPlan#appendAll} 按顺序并入父 plan。</li>
     *   <li>{@code regenerated}：本子树重新生成的 fragment 数量（含后代），供测试探针归并。</li>
     * </ul>
     */
    @Desugar
    private record PaintSubtreeResult(PaintPlan plan, int regenerated) {}

    /**
     * fork-join 子树绘制任务（worker render-scoped，不跨帧）。
     *
     * <p>worker 内调用 {@link #paintSubtree} 产自己的 localPlan，<b>不写共享 plan</b>，
     * 从根上消除多 worker 并发写共享 ArrayList 的竞态。worker 返回
     * {@link PaintSubtreeResult}，由父 join 点串行 appendAll 合并。</p>
     *
     * <p><b>worker render-scoped 不变量</b>：任务生命周期严格限定在单次 paint 调用内，
     * pool.invoke 同步等返回前所有任务 join 完成，不跨帧存活、不跨帧缓存任务对象。</p>
     *
     * <p><b>非 static 内部类</b>：需访问外部类 private {@link #paintSubtree}，
     * 故持有外部类引用（fork 后 worker 线程通过此引用调用 paintSubtree）。</p>
     */
    private final class PaintSubtreeTask extends RecursiveTask<PaintSubtreeResult> {
        private static final long serialVersionUID = 1L;

        private final SceneNode node;
        private final int offsetX;
        private final int offsetY;

        /**
         * 构造子树绘制任务。
         *
         * @param node    子树根节点
         * @param offsetX 从 root 到本子树根父的累积 X 偏移
         * @param offsetY 从 root 到本子树根父的累积 Y 偏移
         */
        PaintSubtreeTask(SceneNode node, int offsetX, int offsetY) {
            this.node = node;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        /**
         * worker 内执行：调用 paintSubtree 产独立 localPlan，返回子树结果。
         *
         * @return 子树绘制结果（独立 plan + 重生成 fragment 数）
         */
        @Override
        protected PaintSubtreeResult compute() {
            return paintSubtree(node, offsetX, offsetY);
        }
    }

    /** 文本度量服务，用于计算绘制阶段文本行框高度。 */
    private final SceneTextMeasurer measurer;

    // ==================== 构造器 ====================

    /**
     * 使用指定文本度量服务创建绘制引擎。
     *
     * @param measurer 文本度量服务（非 null）
     */
    public ScenePaintEngine(SceneTextMeasurer measurer) {
        if (measurer == null) {
            throw new IllegalArgumentException("SceneTextMeasurer 不可为 null");
        }
        this.measurer = measurer;
    }

    // ==================== 公开 API ====================

    /**
     * 对以 root 为根的子树执行增量绘制计算。
     *
     * <p>调用前应确保所有节点已完成 layout（cachedLayout 非空），否则无布局的节点被跳过。
     * 调用后所有被访问节点的 paint 脏标记和 geometry 脏标记均被清除。</p>
     *
     * @param root 场景树根节点
     * @return paint 产出的不可变结果，携带 Display List 与测试探针
     */
    public PaintResult paint(SceneNode root) {
        if (root == null) {
            return new PaintResult(new PaintPlan(), 0);
        }
        // ==== 阶段 2.5 paint 并行化入口 ====
        // 整树门槛：root 子树节点数 < paintWholeTreeThreshold（默认 256）→ 全串行
        // （走现状路径完全不变）。仅当 PARALLEL_ENABLED 开 + root 子树达阈值才走并行路径。
        //
        // ★ worker render-scoped 不变量：并行路径用 pool.invoke 同步等，
        //   render 内 fork、返回前 join，不跨帧存活。
        boolean parallelEligible = SceneParallelExecutor.isParallelEnabled()
                && root.__getCachedSubtreeNodeCount() >= SceneParallelExecutor.getPaintWholeTreeThreshold();

        PaintSubtreeResult outcome;
        if (parallelEligible) {
            // 并行路径：root 包成 task，pool.invoke 同步等
            PaintSubtreeTask rootTask = new PaintSubtreeTask(root, 0, 0);
            outcome = SceneParallelExecutor.getPool().invoke(rootTask);
        } else {
            // 串行路径（现状完全不变，PARALLEL_ENABLED 默认 false 时全套测试零回归）
            outcome = paintSubtree(root, 0, 0);
        }
        return new PaintResult(outcome.plan(), outcome.regenerated());
    }

    // ==================== 内部递归 ====================

    /**
     * DFS 递归绘制单节点，实施 I8 双标记判定 + geometryDirty 下沉 + 相对坐标方案 +
     * Phase 3B 合成级 opacity/transform 通路。
     *
     * <h3>阶段 2.5 paint 并行化改造</h3>
     * <p>本方法产自己的独立 {@link PaintPlan}（localPlan），不再写入共享 plan。
     * 子循环达阈值的子树 fork 为 {@link PaintSubtreeTask}（worker 内各产独立 plan），
     * join 点主线程串行 {@link PaintPlan#appendAll} 按 fork 顺序（= children 顺序）
     * 合并到 localPlan，保证 z-order 确定性。未达阈值的子树串行递归 + appendAll，
     * 与现状逐位等价。</p>
     *
     * <p>PUSH/POP 嵌套天然保证：本节点 PUSH → fragment → 子片段们（appendAll）→ POP，
     * 与现状 DFS 前序完全一致；fork 粒度=整棵子树（含子树根的 PUSH/POP），PUSH/POP
     * 边界整段落同一 worker，无跨节点配对竞态。</p>
     *
     * <h3>Phase 4C 合成传导（守宪章信条五：合成级动画绝不触碰布局/绘制层）</h3>
     * <ul>
     *   <li><b>transform（方案甲完整矩阵）</b>：{@code node.getTransform()} 非恒等时，在
     *       「本节点命令 + 全部后代命令」最外层包 PUSH_TRANSFORM/POP_TRANSFORM 边界命令，
     *       携带绝对屏幕边界 + 7 个浮点分量（translate/rotate/scale/origin），由 GL 矩阵栈做
     *       origin 三明治顶点变换。transform <b>绝不进 fragment</b>，每帧实时从 node 读取，
     *       守 I6：回放器只见 primitive getter，零 Transform/SceneNode 认知。</li>
     *   <li><b>opacity（D1，group 栈）</b>：{@code node.getOpacity()} {@code < 1.0} 时，在
     *       「本节点命令 + 全部后代命令」外层包 PUSH_OPACITY/POP_OPACITY 边界命令，由本递归骨架
     *       前后两句保证严格配对。回放器顺序转译为 {@code pushGroupOpacity/popGroupOpacity}，
     *       <b>嵌套相乘由渲染层离屏层栈天然完成</b>，传该层局部 opacity 不传累计值。</li>
     * </ul>
     *
     * <h3>纯 composite 帧零重建铁律</h3>
     * <p>opacity/transform <b>绝不存进 PaintFragment</b>——fragment 只持纯几何相对坐标命令。
     * opacity/transform 每帧实时从 node 读取（transform→PUSH_TRANSFORM 边界命令、opacity→边界命令），
     * 故纯 opacity/transform 变化帧 {@code selfPaintDirty==false} → fragment 引用复用、
     * 零重建（{@code regeneratedFragmentCount} 不增）。这是信条五铁律的实现根基。</p>
     *
     * @param node    当前节点
     * @param offsetX 从 root 到当前节点父的累积 X 偏移
     * @param offsetY 从 root 到当前节点父的累积 Y 偏移
     * @return 本子树绘制结果（独立 plan + 重生成 fragment 数）
     */
    private PaintSubtreeResult paintSubtree(SceneNode node, int offsetX, int offsetY) {
        int regenerated = 0;
        PaintPlan localPlan = new PaintPlan();
        // 计算本节点的绝对坐标（cachedLayout 中的坐标是相对父的）
        LayoutBox box = (LayoutBox) node.getCachedLayout();
        int nodeAbsX = offsetX + (box != null ? box.getX() : 0);
        int nodeAbsY = offsetY + (box != null ? box.getY() : 0);

        // ==== transform（方案甲完整矩阵 + B6 FBO 方案） ====
        // transform 绝不进 fragment（fragment 只持纯几何相对坐标命令），每帧从 node 实时读取。
        // 门控：needTransform && needClip → PUSH_TRANSFORM_LAYER（FBO 离屏图层，解决 rotate 下 scissor 错位）
        //       needTransform && !needClip → PUSH_TRANSFORM（GL 矩阵纯顶点变换，零重栅格化守信条五）
        Transform transform = node.getTransform();
        boolean needTransform = box != null && transform != null && !transform.isIdentity();
        boolean needClip = box != null && node.isClipWindow();
        if (needTransform) {
            int width = box.getWidth();
            int height = box.getHeight();
            if (needClip) {
                // B6 FBO 方案：transform+clip 叠加走离屏图层，FBO 内 MODELVIEW=I 使 scissor 轴对齐正确裁剪
                localPlan.addPushTransformLayer(nodeAbsX, nodeAbsY, nodeAbsX + width, nodeAbsY + height,
                        transform.translateX, transform.translateY, transform.rotateDegrees,
                        transform.scaleX, transform.scaleY, transform.originXRatio, transform.originYRatio);
            } else {
                // 无 clip：走 GL 矩阵纯顶点变换（零重栅格化，守信条五铁律）
                localPlan.addPushTransform(nodeAbsX, nodeAbsY, nodeAbsX + width, nodeAbsY + height,
                        transform.translateX, transform.translateY, transform.rotateDegrees,
                        transform.scaleX, transform.scaleY, transform.originXRatio, transform.originYRatio);
            }
        }

        // ==== opacity（D1）：< 1.0 且已布局则本节点子树进入 group opacity 合成作用域 ====
        // box==null（节点未布局）时不开 group：零面积离屏层无意义，且与「无布局节点跳过」语义对齐
        float opacity = node.getOpacity();
        boolean needGroup = box != null && opacity < 1.0f - OPACITY_EPSILON;
        if (needGroup) {
            // 区域用本节点绝对边界（含 transform 后的偏移），渲染层据此开离屏层做 group 合成
            int width = box != null ? box.getWidth() : 0;
            int height = box != null ? box.getHeight() : 0;
            localPlan.addPushOpacity(nodeAbsX, nodeAbsY, nodeAbsX + width, nodeAbsY + height, opacity);
        }

        // ==== clipChildren（Phase 4）：裁剪作用域包住「本节点命令 + 全部后代命令」 ====
        // 与 opacity 同款处理：CLIP_PUSH/POP 绝不进 fragment（fragment 只含本节点自己的命令），
        // 必须在递归骨架里用绝对坐标产出，否则裁剪框不会包住后代。严格嵌套在 opacity 作用域内层。
        //
        // ★ scrollable 视口同时是裁剪窗口（纵向滚动地基）：scrollable 节点必须裁剪超出视口的
        // 后代内容，否则滚动平移后超出视口的部分会画到视口外。CLIP 用本节点自己的绝对坐标
        // （nodeAbsX, nodeAbsY，★绝不含 scrollOffset），裁出一个固定不动的视口窗口；后代用
        // 注入的 nodeAbsY-scrollOffsetY 平移落在这个固定窗口内，超出部分被裁。滚动时 CLIP 坐标
        // 恒定、只有后代内容偏移变，这正是「视口框固定、内容滚动」的视觉语义。
        // ★ B6 FBO 方案：needClip 已提前到 needTransform 旁声明（门控判定需要），此处不再重复声明
        if (needClip) {
            int clipWidth = box.getWidth();
            int clipHeight = box.getHeight();
            localPlan.addClipPush(nodeAbsX, nodeAbsY, nodeAbsX + clipWidth, nodeAbsY + clipHeight,
                    node.getCornerRadius());
        }

        PaintFragment cached = (PaintFragment) node.getCachedPaint();

        // ==== 缓存有效 + selfPaintDirty==false → 复用 fragment（不管 geometry/composite 是否脏） ====
        if (!node.__isSelfPaintDirty() && cached != null) {
            // 本节点 paint 属性未变，复用缓存 fragment（但用新的 offset）
            // 这包括 selfGeometryDirty==true（位置变）与 compositeDirty==true（opacity/transform 变）场景：
            // 均只重定位/重合成不重绘 —— 纯 composite 帧 fragment 引用不变，守信条五铁律
            localPlan.addFragment(cached, nodeAbsX, nodeAbsY);
        } else {
            // 需要重新生成 fragment（命令使用相对坐标，不含 opacity/transform）
            List<PaintCommand> commands = new ArrayList<>();
            generateCommands(node, commands);
            PaintFragment newFragment = new PaintFragment(commands);
            node.setCachedPaint(newFragment);
            localPlan.addFragment(newFragment, nodeAbsX, nodeAbsY);
            regenerated++;
        }

        // ==== 递归子节点（paint 或 geometry 脏导致下沉；子树命令落在本节点 group 作用域内） ====
        // ★ scrollable 视口注入纵向滚动偏移：传给后代的 Y 基准改为 nodeAbsY - scrollOffsetY，
        // 使后代内容整体上移 scrollOffsetY 像素显示（向下为正语义：scrollOffsetY 越大越往下滚、
        // 内容越往上移）。★只在 paint 骨架注入，绝不在 layout 改子 y——否则会把 scrollOffset
        // 烤进 LayoutBox 导致滚动即重排破 I7。CLIP 窗口（上方 needClip 分支）用不含 offset 的
        // nodeAbsY 固定不动，后代用含 offset 的基准平移落在固定窗口内，超出被裁。后代 fragment
        // 复用通路自动正确：selfPaintDirty==false 时 addFragment 用的 nodeAbsY 已含注入偏移，
        // 复用 fragment + 新偏移与现有 geometry 重定位同构，无需特殊处理。
        int childOffsetY = SceneGeometry.childYBase(node, nodeAbsY);
        List<SceneNode> children = node.__getChildren();
        if (!children.isEmpty()) {
            // ==== 阶段 2.5 paint 并行化子循环（两遍遍历） ====
            // ★ P0 修复：appendAll 必须严格按 children 顺序，保证 z-order 确定性。
            //   旧实现串行 child 立即 appendAll、fork child 延迟到 join 阶段，混合时顺序错乱。
            //   现改为两遍遍历：第一遍启动 fork 后台并行，第二遍按 children 顺序处理
            //   （fork 的 join+appendAll，串行的 paintSubtree+appendAll）。
            //
            // ★ appendAll 按 children 顺序保证 z-order 确定性（命门 1）。
            // ★ fork 粒度=整棵子树（含子树根的 PUSH/POP），PUSH/POP 边界整段落同一 worker（命门 2）。
            // ★ 串行路径（shouldFork=false）直接调 paintSubtree + appendAll，与现状逐位等价（命门 3）。
            // ★ appendAll 不平移 offset（子片段内命令已是绝对坐标，命门 5）。
            //
            // 第一遍：fork 达阈值的 child（后台并行启动）
            List<PaintSubtreeTask> forkedTasks = null;
            boolean[] forkedFlags = new boolean[children.size()];
            for (int i = 0; i < children.size(); i++) {
                SceneNode child = children.get(i);
                boolean shouldFork = SceneParallelExecutor.isParallelEnabled()
                        && children.size() >= 2
                        && child.__getCachedSubtreeNodeCount() >= SceneParallelExecutor.getPaintForkThreshold();
                if (shouldFork) {
                    PaintSubtreeTask task = new PaintSubtreeTask(child, nodeAbsX, childOffsetY);
                    task.fork();
                    forkedFlags[i] = true;
                    if (forkedTasks == null) {
                        forkedTasks = new ArrayList<>();
                    }
                    forkedTasks.add(task);
                }
            }
            // 第二遍：按 children 顺序处理（保证 z-order 确定性）
            // fork 的 join+appendAll，串行的直接 paintSubtree+appendAll
            int forkIdx = 0;
            for (int i = 0; i < children.size(); i++) {
                SceneNode child = children.get(i);
                if (forkedFlags[i]) {
                    PaintSubtreeResult cr = forkedTasks.get(forkIdx++).join();
                    localPlan.appendAll(cr.plan());
                    regenerated += cr.regenerated();
                } else {
                    // 串行路径（现状不变）：直接调 paintSubtree，appendAll 到 localPlan
                    PaintSubtreeResult cr = paintSubtree(child, nodeAbsX, childOffsetY);
                    localPlan.appendAll(cr.plan());
                    regenerated += cr.regenerated();
                }
            }
        }

        // ==== 子树命令全部产出后，先闭合裁剪作用域（与 CLIP_PUSH 严格配对，内层先关） ====
        if (needClip) {
            localPlan.addClipPop();
        }

        // ==== 子树命令全部产出后，闭合本节点 group opacity 作用域（与 PUSH 严格配对） ====
        if (needGroup) {
            localPlan.addPopOpacity();
        }

        // ==== 子树命令全部产出后，闭合 transform 作用域（最外层，与 PUSH 严格配对） ====
        // B6 FBO 方案：needTransform && needClip → POP_TRANSFORM_LAYER，否则 POP_TRANSFORM
        if (needTransform) {
            if (needClip) {
                localPlan.addPopTransformLayer();
            } else {
                localPlan.addPopTransform();
            }
        }

        // ==== 清除本节点 paint + geometry + composite 脏标记 ====
        // composite 必须在此清除：Phase 3A 解耦后 clearPaintDirty 不再顺手清 composite，
        // 否则 compositeDirty 永久累积（3A+3B 同单元交付的硬约束）。
        // ★ worker 内清自己子树节点，无跨祖先冒泡写，安全。
        node.clearPaintDirty();
        node.clearGeometryDirty();
        node.clearCompositeDirty();
        return new PaintSubtreeResult(localPlan, regenerated);
    }

    /**
     * 根据节点属性槽和布局结果生成绘制命令（相对坐标）。
     *
     * <p>命令坐标从 {@code (0,0)} 起，使用节点 cachedLayout 的宽高计算右下角。
     * 组装 PaintPlan 时通过 {@link PaintPlan#addFragment(PaintFragment, int, int)}
     * 叠加节点的绝对偏移得到最终屏幕坐标。</p>
     *
     * <p>背景色非透明（{@code != 0}）→ 一条 BACKGROUND 命令；
     * 有文本内容（非空非 null）→ 一条 TEXT 命令。</p>
     *
     * @param node 节点
     * @param out  输出命令列表
     */
    private void generateCommands(SceneNode node, List<PaintCommand> out) {
        LayoutBox box = (LayoutBox) node.getCachedLayout();
        if (box == null) {
            return;
        }

        int width = box.getWidth();
        int height = box.getHeight();

        // 背景色非透明 → BACKGROUND 命令（相对坐标，从 0,0 起；带节点圆角半径）
        int bgColor = node.getBackgroundColor();
        if (bgColor != 0) {
            out.add(PaintCommand.background(0, 0, width, height, bgColor, node.getCornerRadius()));
        }

        // 边框宽度>0 → BORDER 命令（相对坐标，用节点边框色/宽度/圆角；编入 fragment 随 selfPaintDirty 复用）
        int borderW = node.getBorderWidth();
        if (borderW > 0) {
            out.add(PaintCommand.border(0, 0, width, height, node.getBorderColor(), borderW,
                    node.getCornerRadius()));
        }

        // 有文本 → TEXT 命令（相对坐标，文字色读 node.getTextColor()，默认白零回归）
        // fontSize 直接读 node.getFontSize()（不再用 height 做 hack 回退）：
        // 字号是节点自有属性，与布局盒高度解耦，fill 文本节点不再炸 fontSize。
        String text = node.getText();
        if (text != null && !text.isEmpty()) {
            int fontSize = node.getFontSize();
            TextStyle style = new TextStyle(node.getTextColor(), fontSize);
            int textLeft = calculateTextLeft(node, box, fontSize, text);
            int textTop = calculateTextTop(node, box, fontSize);
            out.add(PaintCommand.text(textLeft, textTop, text, style));
        }
    }

    /**
     * 按节点文本水平对齐方式计算文本行框左侧偏移。
     *
     * @param node     当前节点
     * @param box      当前节点布局盒
     * @param fontSize 字号（UI 像素）
     * @param text     文本内容
     * @return 文本行框左侧相对节点局部原点的 X 偏移
     */
    private int calculateTextLeft(SceneNode node, LayoutBox box, int fontSize, String text) {
        int paddingLeft = node.getPaddingLeft();
        int paddingRight = node.getPaddingRight();
        int innerWidth = box.getWidth() - paddingLeft - paddingRight;
        TextHorizontalAlign align = node.getTextHorizontalAlign();
        switch (align) {
            case LEFT:
                return paddingLeft;
            case CENTER: {
                // 惰性测量：LEFT（默认对齐）不量文本宽，避免每帧无谓 measureWidth
                int textWidth = measurer.measureWidth(text, fontSize);
                return paddingLeft + Math.max(0, (innerWidth - textWidth) / 2);
            }
            case RIGHT: {
                int textWidth = measurer.measureWidth(text, fontSize);
                return paddingLeft + Math.max(0, innerWidth - textWidth);
            }
            default:
                throw new UnsupportedOperationException("未支持的文本水平对齐方式: " + align);
        }
    }

    /**
     * 按节点文本垂直对齐方式计算文本绘制起点（em-box 顶）相对节点局部原点的 Y 偏移。
     *
     * <h3>对齐模型：em-box 居中（与字体渲染器锚点一致）</h3>
     * <p>本项目字体渲染器 {@code FontBatchRenderer} 把绘制起点 y 当作<b>字符格 em-box 顶</b>
     * （atlas 64 坐标系第 0 行），baseline 由其内部 {@code y + lineBaselineY*glyphScale} 推出。
     * 因此 paint 层只需把 em-box 在内高内对齐即可，不应再套 CSS half-leading（content-area）模型，
     * 否则与 em-box 锚点错配导致文字垂直偏移（见 DECISION-20260625 修订与
     * ERROR-20260625-glyph-coordinate-system-mismatch）。</p>
     *
     * <p>em-box 显示高 == 字号：烘焙 em=64、{@code glyphScale=fontSize/64}，故 {@code 64*glyphScale=fontSize}。
     * 字号到渲染器 charSize 全链路 1:1 透传（scene 文本不经 UI_TEXT_SCALE），该等式严格成立。</p>
     *
     * <p>仅单行模型：本方法按单个 em-box 高度对齐，不处理 {@code \n} 多行。</p>
     *
     * @param node     当前节点
     * @param box      当前节点布局盒
     * @param fontSize 字号（UI 像素），等于 em-box 显示高度
     * @return 文本绘制起点（em-box 顶）相对节点局部原点的 Y 偏移
     */
    private int calculateTextTop(SceneNode node, LayoutBox box, int fontSize) {
        int paddingTop = node.getPaddingTop();
        int paddingBottom = node.getPaddingBottom();
        int innerHeight = box.getHeight() - paddingTop - paddingBottom;
        // em-box 显示高度 == 字号（烘焙 em=64，glyphScale=fontSize/64）
        int emHeight = fontSize;
        TextVerticalAlign align = node.getTextVerticalAlign();
        switch (align) {
            case TOP:
                return paddingTop;
            case BOTTOM:
                return paddingTop + (innerHeight - emHeight);
            case CENTER:
                return paddingTop + (innerHeight - emHeight) / 2;
            default:
                throw new UnsupportedOperationException("未支持的文本垂直对齐方式: " + align);
        }
    }
}
