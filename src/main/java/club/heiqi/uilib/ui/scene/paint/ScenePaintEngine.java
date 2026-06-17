package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;

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
 *   <li><b>缓存存在 + paint 双false + geometry 双false → 整棵跳过</b>：零开销 I8。</li>
 *   <li><b>selfPaintDirty==true → 重新生成 fragment</b>：属性变化，重绘。</li>
 *   <li><b>selfPaintDirty==false + cache存在 + geometry脏 → 复用 fragment、用新 offset 叠加</b>：
 *       位置变化不重绘（方案 A 精髓）。</li>
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

    /** 默认行高（像素），当节点无布局高度时作为文本字号占位 */
    private static final int DEFAULT_FONT_SIZE = 16;

    // ==================== 测试探针 ====================

    /** 本次 paint 调用中重新生成的 fragment 数量，仅供测试 I8 断言 */
    private int regeneratedFragmentCount = 0;

    // ==================== 公开 API ====================

    /**
     * 对以 root 为根的子树执行增量绘制计算。
     *
     * <p>调用前应确保所有节点已完成 layout（cachedLayout 非空），否则无布局的节点被跳过。
     * 调用后所有被访问节点的 paint 脏标记和 geometry 脏标记均被清除。</p>
     *
     * @param root 场景树根节点
     * @return 扁平化的 Display List（PaintPlan，命令坐标为绝对屏幕坐标）
     */
    public PaintPlan paint(SceneNode root) {
        regeneratedFragmentCount = 0;
        PaintPlan plan = new PaintPlan();
        if (root != null) {
            paintNode(root, plan, 0, 0);
        }
        return plan;
    }

    // ==================== 内部递归 ====================

    /**
     * DFS 递归绘制单节点，实施 I8 双标记判定 + geometryDirty 下沉 + 相对坐标方案。
     *
     * @param node    当前节点
     * @param plan    输出目标 PaintPlan
     * @param offsetX 从 root 到当前节点父的累积 X 偏移
     * @param offsetY 从 root 到当前节点父的累积 Y 偏移
     */
    private void paintNode(SceneNode node, PaintPlan plan, int offsetX, int offsetY) {
        // 计算本节点的绝对坐标（cachedLayout 中的坐标是相对父的）
        LayoutBox box = (LayoutBox) node.getCachedLayout();
        int nodeAbsX = offsetX + (box != null ? box.getX() : 0);
        int nodeAbsY = offsetY + (box != null ? box.getY() : 0);

        PaintFragment cached = (PaintFragment) node.getCachedPaint();

        // ==== I8 核心判定：缓存有效 + paint 双false + geometry 双false → 整棵跳过 ====
        if (cached != null
                && !node.__isSelfPaintDirty()
                && !node.__isDescendantPaintDirty()
                && !node.__isSelfGeometryDirty()
                && !node.__isDescendantGeometryDirty()) {
            // 即使本节点跳过，也要带着当前 offset 把 fragment 加进 plan
            plan.addFragment(cached, nodeAbsX, nodeAbsY);
            // 不递归子节点，无需清除标记（标记本身已是 false）
            return;
        }

        // ==== 生成本节点 fragment（或复用） ====
        if (!node.__isSelfPaintDirty() && cached != null) {
            // 本节点 paint 属性未变，复用缓存 fragment（但用新的 offset）
            // 这包括 selfGeometryDirty==true 的场景：位置变但 paint 干净 → 只重定位不重绘
            plan.addFragment(cached, nodeAbsX, nodeAbsY);
        } else {
            // 需要重新生成 fragment（命令使用相对坐标）
            List<PaintCommand> commands = new ArrayList<>();
            generateCommands(node, commands);
            PaintFragment newFragment = new PaintFragment(commands);
            node.setCachedPaint(newFragment);
            plan.addFragment(newFragment, nodeAbsX, nodeAbsY);
            regeneratedFragmentCount++;
        }

        // ==== 递归子节点（paint 或 geometry 脏导致下沉） ====
        for (SceneNode child : node.__getChildren()) {
            paintNode(child, plan, nodeAbsX, nodeAbsY);
        }

        // ==== 清除本节点 paint + geometry 脏标记 ====
        node.clearPaintDirty();
        node.clearGeometryDirty();
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

        // 背景色非透明 → BACKGROUND 命令（相对坐标，从 0,0 起）
        int bgColor = node.getBackgroundColor();
        if (bgColor != 0) {
            out.add(PaintCommand.background(0, 0, width, height, bgColor));
        }

        // 有文本 → TEXT 命令（相对坐标，Phase 1 后接入真实文字色）
        String text = node.getText();
        if (text != null && !text.isEmpty()) {
            int fontSize = height > 0 ? height : DEFAULT_FONT_SIZE;
            TextStyle style = new TextStyle(0xFFFFFFFF, fontSize);
            out.add(PaintCommand.text(0, 0, text, style));
        }
    }

    // ==================== 测试探针 ====================

    /**
     * 返回最近一次 {@link #paint} 调用中重新生成的 fragment 数量。
     * 仅供测试断言 I8 跳过行为。
     *
     * @return 重新生成的 fragment 数量
     */
    public int __getRegeneratedFragmentCount() {
        return regeneratedFragmentCount;
    }
}
