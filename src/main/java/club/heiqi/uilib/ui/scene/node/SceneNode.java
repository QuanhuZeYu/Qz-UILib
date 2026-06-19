package club.heiqi.uilib.ui.scene.node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.scene.input.SceneCursor;

/**
 * 场景树节点 —— 新 UI 数据模型的地基，承载"反转脏标记方向"的灵魂设计。
 *
 * <h3>核心原则：脏标记只向上冒泡（O(深度)），绝不向下递归（O(子树)）</h3>
 *
 * <p>旧 DOM 模型（{@code DocumentNode.markSubtreeLayoutMutation}）在容器增删时
 * 无条件向下递归刷新全部后代的布局版本号，导致未变的稳定子节点被布局层判定复用失败、
 * 全量重算——这是 I7 债的根源。本类的设计正面翻转此方向：</p>
 * <ul>
 *   <li>节点自身变化时，只标自己（{@code selfLayoutDirty / selfPaintDirty / compositeDirty}）
 *       并通过祖先链向上点亮路标（{@code descendantLayoutDirty / descendantPaintDirty}），
 *       告知布局/绘制/合成遍历"需要下沉到我这里"。</li>
 *   <li>绝对不触碰任何兄弟节点、任何后代节点。</li>
 *   <li>稳定复用的子节点（如列表 keyed diff 中未变的项）零标脏，保证 I7：
 *       干净子树在布局、绘制、合成三阶段都被跳过。</li>
 * </ul>
 *
 * <h3>属性 setter 的设计意图</h3>
 * <p>每个 setter（{@link #setText}, {@link #setBackgroundColor}, {@link #setOpacity},
 * {@link #setTransform}）内部自动打出对应失效级别，调用方无需手选级别，
 * 从而降低 I4"打错级别"的风险。setter 先去重（值与当前相等则直接 return），
 * 对齐 reactive 层"对已应用值去重"的铁律，避免无谓标脏。</p>
 *
 * <h3>批量结构变更（{@link #applyChildReconcile}）</h3>
 * <p>这是根除 I7 债的关键 API。reconciler 一次性提交最终子节点序列 + 其中新增/移动的项。
 * 容器自身因子序列变化标一次脏；稳定复用节点零标脏——正面翻转旧
 * {@code markSubtreeLayoutMutation} 的递归全标行为。</p>
 */
public class SceneNode {

    // ==================== 树关系 ====================

    /** 父节点，根节点为 null */
    SceneNode parent;

    /** 子节点列表，用 ArrayList 初始化 */
    List<SceneNode> children;

    // ==================== 脏标记 ====================

    /** 自身布局输入变化（尺寸/约束/文本/子节点集合变化） */
    boolean selfLayoutDirty;

    /**
     * 后代中存在布局脏节点，布局遍历需下沉到本节点。
     * 这是"路标"而非"脏标记"——本节点自己不一定要重算。
     */
    boolean descendantLayoutDirty;

    /** 自身绘制属性变化（颜色/背景/边框等） */
    boolean selfPaintDirty;

    /** 后代中存在绘制脏节点，绘制遍历需下沉的路标 */
    boolean descendantPaintDirty;

    /** 自身合成属性变化（transform/opacity） */
    boolean compositeDirty;

    /**
     * 自身位置/尺寸变化（layout 引擎产出），paint 遍历需下沉更新 offset。
     * <p>这是独立的 COMPOSITE 子级别标记，语义单一：位置变，不重绘但需重定位。
     * 与 compositeDirty（transform/opacity）严格分离，保证 Phase 3 语义纯净。</p>
     */
    boolean selfGeometryDirty;

    /**
     * 后代中存在几何变化节点，paint 遍历需下沉的路标。
     * {@link #markGeometryDirty()} 沿祖先链向上点亮。
     */
    boolean descendantGeometryDirty;

    // ==================== 缓存占位（T4/T5 填充） ====================

    /** 布局结果缓存，无效时为 null */
    Object cachedLayout;

    /** 绘制结果缓存，无效时为 null */
    Object cachedPaint;

    // ==================== 强类型属性槽 ====================

    /** 文本内容，默认 null */
    private String text;

    /** 背景颜色（ARGB），默认 0（透明） */
    private int backgroundColor;

    /** 不透明度，默认 1.0f（完全不透明） */
    private float opacity = 1.0f;

    /** 合成级变换，默认 null（无变换） */
    private Transform transform;

    /**
     * 是否填充父容器高度。
     *
     * <p>默认 false：高度 = 内容高度（shrink-to-fit），兼容现有行为。
     * 设为 true 时，布局引擎在计算高度时取 max(内容高, 约束可用高度)，
     * 使容器至少填满父容器给定的高度空间。</p>
     *
     * <p><b>硬约束：fillParentHeight 只应用于容器节点，绝不用于文本叶节点。</b>
     * 因为 ScenePaintEngine.generateCommands 把 LayoutBox.height 当作文本 fontSize，
     * fill 文本会让 fontSize 炸成约束高，导致渲染异常。</p>
     */
    private boolean fillParentHeight = false;

    /**
     * 首选高度（像素），供无文本/无子节点的叶节点显式指定最小高度。
     *
     * <p>默认 0：回退到内容高度（文本行高或 0）。设非零值时，布局引擎对叶节点
     * 取 {@code Math.max(textHeight, preferredHeight)}，确保背景矩形/hit-test
     * 区域有足够高度。不影响容器节点（容器高度由子节点累加决定）。</p>
     */
    private int preferredHeight = 0;

    /**
     * 光标样式声明（I4c cursor 投影能力）。
     *
     * <p>默认 null：表示未声明，沿祖先链向上查找首个声明。null 即"继承"语义，
     * 最终回退到 {@link club.heiqi.uilib.ui.scene.input.SceneCursor#DEFAULT}。
     * 组件在 {@code SceneRuntime.mount} 构建阶段通过 {@link #setCursor} 声明。</p>
     *
     * <p><b>⚠ 唯一不标脏的属性槽 setter</b>：cursor 是纯交互投影，不影响
     * layout/paint/composite 任何阶段。{@link #setCursor} 内部不走
     * {@code markSelfLayout/markSelfPaint/markComposite}，是项目唯一例外。
     * 理由详见 I4c 设计（用户拍板 D6-A、oracle 纠偏①）。</p>
     */
    private SceneCursor cursor;

    // ==================== 构造器 ====================

    /** 创建一个空的场景树节点 */
    public SceneNode() {
        this.children = new ArrayList<>();
    }

    // ==================== 树操作 ====================

    /**
     * 在末尾追加一个子节点。
     *
     * <p>如果子节点已有父节点（且不是本节点），先从旧父移除。
     * 追加后本容器调用 {@link #markSelfLayout()}（子节点集合变化影响本容器布局），
     * 绝不递归标记 child 的后代。</p>
     *
     * @param child 要添加的子节点
     */
    public void appendChild(SceneNode child) {
        if (child == null) return;
        // 如果 child 已有父节点（且不是本节点），先从旧父移除，并标旧父脏
        if (child.parent != null && child.parent != this) {
            SceneNode oldParent = child.parent;
            oldParent.children.remove(child);
            oldParent.markSelfLayout();
        }
        children.add(child);
        child.parent = this;
        markSelfLayout();
    }

    /**
     * 移除一个子节点。
     *
     * <p>移除后子节点 parent 置 null，本容器调用 {@link #markSelfLayout()}。
     * 绝不递归标记 child 的后代。</p>
     *
     * @param child 要移除的子节点
     */
    public void removeChild(SceneNode child) {
        if (child == null) return;
        if (children.remove(child)) {
            child.parent = null;
            markSelfLayout();
        }
    }

    /**
     * 在指定锚点前插入一个子节点。
     *
     * <p>本容器调用 {@link #markSelfLayout()}，不递归标记后代。</p>
     *
     * @param child  要插入的子节点
     * @param anchor 锚点节点，child 将插在其之前
     */
    public void insertBefore(SceneNode child, SceneNode anchor) {
        if (child == null || anchor == null) return;
        int idx = children.indexOf(anchor);
        if (idx < 0) return;
        // 如果 child 已有父节点（且不是本节点），先从旧父移除，并标旧父脏
        if (child.parent != null && child.parent != this) {
            SceneNode oldParent = child.parent;
            oldParent.children.remove(child);
            oldParent.markSelfLayout();
        }
        children.add(idx, child);
        child.parent = this;
        markSelfLayout();
    }

    /**
     * 批量结构变更入口 —— 根除 I7 债的关键 API。
     *
     * <h3>语义</h3>
     * <p>reconciler 一次性提交"最终子节点序列" + "其中哪些是新插入或被移动的项"。
     * 本方法将 {@link #children} 整体替换为 {@code finalOrder}，维护好每个节点的
     * parent 指针：出现在 finalOrder 中的节点 parent=this；不再出现的旧 child parent=null。</p>
     *
     * <h3>脏标记策略（与旧模型的核心差异）</h3>
     * <ul>
     *   <li>容器自身因子序列变化调一次 {@link #markSelfLayout()}（标容器自己 + 向上冒泡）。</li>
     *   <li><b>对 finalOrder 中不在 insertedOrMoved 里的稳定复用节点：零标脏</b>
     *       （既不标它们 selfLayoutDirty，也不碰它们的后代）。这是与旧
     *       {@code markSubtreeLayoutMutation} 递归全标行为的正面翻转。</li>
     *   <li>对 insertedOrMoved 中的节点：不在此方法内递归标其子树。
     *       它们位置变了需要后续布局重排，但这是它们各自的事，
     *       由 {@code markSelfLayout()} 的向上冒泡机制保证布局遍历会下沉到本容器。</li>
     * </ul>
     *
     * <h3>对照旧 DOM 的 I7 债</h3>
     * <p>旧 {@code DocumentNode.recordStructuralMutation} 在每次 appendChild/removeChild
     * 时调用 {@code markSubtreeLayoutMutation}，无条件向下递归刷新全部后代的脏版本，
     * 导致未变的稳定子节点被布局层判定复用失败、全量重算。
     * 本方法通过"稳定子项零标脏"正面消除此债。</p>
     *
     * <h3>insertedOrMoved 的定位：声明性元数据，不驱动标脏</h3>
     * <p>{@code insertedOrMoved} 当前仅为<b>声明性元数据</b>——本方法体<b>不读取它、
     * 不据此驱动任何标脏</b>（方法内对该参数零引用）。零标脏是靠"稳定子项不被
     * {@link #markSelfLayout()}"隐式达成的，与本参数无关。</p>
     * <p>几何变化的<b>唯一权威判定源</b>是 {@code SceneLayoutEngine} 的
     * {@code newBox.equals(childBox)} 几何闸门：layout 阶段会重新发现谁的几何真正发生变化，
     * 由它单独决定谁需要重排。</p>
     * <p><b>严禁</b>后续在本方法内补"读取 insertedOrMoved 并对其中节点
     * {@code markGeometryDirty / markSelfLayout}"之类的消费逻辑——那会与 layout 几何闸门
     * 形成两个独立的"谁移动了"权威源，迟早彼此漂移（语义双载反模式，对照
     * {@code NORTH_STAR} 反模式"万能脏标记"）。本参数的价值仅在于让调用方表达意图，
     * 不参与本方法的任何控制流。</p>
     *
     * <h3>前置约束</h3>
     * <p>本方法<b>假定 finalOrder 中的所有节点都同属当前容器（this）</b>。
     * 被移除的旧 child（不在 finalOrder）其旧父就是 this，已由方法末尾的
     * {@link #markSelfLayout()} 兜底标脏。<b>跨容器移动（child 旧父非 this）的标脏当前未处理</b>，
     * 是已知边界：forEach 单容器场景不会触发；若 Phase 3 复用本 API 做跨容器移动，
     * 需先补"旧父标脏"逻辑后再使用。</p>
     *
     * @param finalOrder       最终子节点序列（顺序有意义）
     * @param insertedOrMoved  声明性元数据：标注哪些是新插入或被移动的节点（可为空集）。
     *                         <b>本方法体不读取、不消费此参数</b>，详见上方"声明性元数据"小节。
     */
    public void applyChildReconcile(List<SceneNode> finalOrder, Set<SceneNode> insertedOrMoved) {
        if (finalOrder == null) return;

        // 1. 将不再出现在 finalOrder 中的旧 child 的 parent 置 null
        //    用基于引用相等的 IdentityHashMap 集合做 O(1) 判定，避免 List.contains 的 O(n²) 线性查找
        Set<SceneNode> finalOrderSet = Collections.newSetFromMap(new IdentityHashMap<>());
        finalOrderSet.addAll(finalOrder);
        for (SceneNode child : children) {
            if (!finalOrderSet.contains(child)) {
                child.parent = null;
            }
        }

        // 2. 替换子节点列表
        children.clear();
        children.addAll(finalOrder);

        // 3. 维护 finalOrder 中每个节点的 parent 指针
        for (SceneNode child : finalOrder) {
            child.parent = this;
        }

        // 4. 容器自身因子序列变化标脏一次（只标自己 + 向上冒泡）
        //    绝不递归标记任何子节点或后代
        markSelfLayout();
    }

    // ==================== 核心失效方法 ====================

    /**
     * 标记自身布局脏。
     *
     * <p>设置 {@code selfLayoutDirty = true}，使本节点 layout 缓存失效，
     * 然后沿祖先链向上点亮 {@code descendantLayoutDirty} 路标（O(深度)）。</p>
     */
    public void markSelfLayout() {
        if (selfLayoutDirty) return; // 已标脏，跳过重复冒泡
        selfLayoutDirty = true;
        cachedLayout = null;
        bubbleDescendantLayout();
    }

    /**
     * 标记自身绘制脏。
     *
     * <p>设置 {@code selfPaintDirty = true}，使本节点 paint 缓存失效，
     * 然后沿祖先链向上点亮 {@code descendantPaintDirty} 路标（O(深度)）。</p>
     */
    public void markSelfPaint() {
        selfPaintDirty = true;
        cachedPaint = null;
        bubbleDescendantPaint();
    }

    /**
     * 标记合成脏。
     *
     * <p>设置 {@code compositeDirty = true}，然后复用 {@link #bubbleDescendantPaint()}
     * 沿祖先链向上点亮 paint 路标。选择理由：composite 级变更最终也需要重新合成，
     * 而绘制遍历通过 paint 路标下沉即可覆盖 composite 需求，保持简单正确。
     * T4/T5 的遍历逻辑会更精细区分 paint 与 composite 遍历范围。</p>
     */
    public void markComposite() {
        compositeDirty = true;
        bubbleDescendantPaint();
    }

    /**
     * 标记几何变化（位置/尺寸变化，layout 引擎产出）。
     *
     * <p>设置 {@code selfGeometryDirty = true}，然后沿祖先链向上点亮
     * {@code descendantGeometryDirty} 路标（O(深度)）。paint 遍历据此
     * 下沉到本节点，复用 fragment（selfPaintDirty==false 时不重绘）
     * 但用新的绝对偏移重新叠加坐标。</p>
     *
     * <p>核心不变量：<b>绝不向下递归触碰任何后代</b>。</p>
     */
    public void markGeometryDirty() {
        if (selfGeometryDirty) return; // 已标脏，跳过重复冒泡
        selfGeometryDirty = true;
        bubbleDescendantGeometry();
    }

    /**
     * 沿祖先链向上点亮 {@code descendantLayoutDirty} 路标。
     *
     * <p>核心不变量：<b>绝对不触碰任何 children / 任何后代</b>。
     * 从 parent 开始沿祖先链向上，遇某祖先的 {@code descendantLayoutDirty}
     * 已为 true 则立即停止（路标已点亮，上面都已知晓），否则置 true 继续上溯。
     * 复杂度 O(深度)。</p>
     */
    private void bubbleDescendantLayout() {
        SceneNode current = parent;
        while (current != null) {
            if (current.descendantLayoutDirty) {
                // 路标已点亮，祖先链上面都已经知道有后代脏了，停止
                break;
            }
            current.descendantLayoutDirty = true;
            current = current.parent;
        }
    }

    /**
     * 沿祖先链向上点亮 {@code descendantPaintDirty} 路标。
     *
     * <p>与 {@link #bubbleDescendantLayout()} 同理：遇已点亮即停，O(深度)。
     * 绝对不触碰任何后代。</p>
     */
    private void bubbleDescendantPaint() {
        SceneNode current = parent;
        while (current != null) {
            if (current.descendantPaintDirty) {
                break;
            }
            current.descendantPaintDirty = true;
            current = current.parent;
        }
    }

    /**
     * 沿祖先链向上点亮 {@code descendantGeometryDirty} 路标。
     *
     * <p>与 {@link #bubbleDescendantLayout()} 同构：遇已点亮即停，O(深度)。
     * 绝对不触碰任何后代。paint 遍历读取此路标决定下沉范围，
     * 到达 selfGeometryDirty==true 的节点时复用 fragment 但更新 offset。</p>
     */
    private void bubbleDescendantGeometry() {
        SceneNode current = parent;
        while (current != null) {
            if (current.descendantGeometryDirty) {
                break;
            }
            current.descendantGeometryDirty = true;
            current = current.parent;
        }
    }

    // ==================== 清除方法 ====================

    /**
     * 清除所有脏标记（自身 + 后代路标 + 合成标记）。
     *
     * <p>布局/绘制遍历完成后调用，将节点恢复"干净"状态。
     * T4/T5 将需要更细粒度的分别清除，当前提供统一入口。</p>
     */
    public void clearDirtyFlags() {
        selfLayoutDirty = false;
        descendantLayoutDirty = false;
        selfPaintDirty = false;
        descendantPaintDirty = false;
        compositeDirty = false;
    }

    /**
     * 清除布局相关脏标记。
     *
     * <p>清除 {@code selfLayoutDirty} 和 {@code descendantLayoutDirty}，
     * 供布局遍历完成后调用。</p>
     */
    public void clearLayoutDirty() {
        selfLayoutDirty = false;
        descendantLayoutDirty = false;
    }

    /**
     * 清除绘制和合成相关脏标记。
     *
     * <p>清除 {@code selfPaintDirty}、{@code descendantPaintDirty} 和 {@code compositeDirty}，
     * 供绘制遍历完成后调用。</p>
     */
    public void clearPaintDirty() {
        selfPaintDirty = false;
        descendantPaintDirty = false;
        compositeDirty = false;
    }

    /**
     * 清除几何变化脏标记。
     *
     * <p>清除 {@code selfGeometryDirty} 和 {@code descendantGeometryDirty}，
     * 供 paint 遍历完成（位置→offset 叠加）后调用。
     * 不碰 layout/paint/composite 任何其他标记类别。</p>
     */
    public void clearGeometryDirty() {
        selfGeometryDirty = false;
        descendantGeometryDirty = false;
    }

    // ==================== 缓存管理 ====================

    /** 使布局缓存失效 */
    public void invalidateLayoutCache() {
        cachedLayout = null;
    }

    /** 使绘制缓存失效 */
    public void invalidatePaintCache() {
        cachedPaint = null;
    }

    /** @return 布局缓存，可能为 null */
    public Object getCachedLayout() {
        return cachedLayout;
    }

    /** @param cachedLayout 布局缓存值 */
    public void setCachedLayout(Object cachedLayout) {
        this.cachedLayout = cachedLayout;
    }

    /** @return 绘制缓存，可能为 null */
    public Object getCachedPaint() {
        return cachedPaint;
    }

    /** @param cachedPaint 绘制缓存值 */
    public void setCachedPaint(Object cachedPaint) {
        this.cachedPaint = cachedPaint;
    }

    // ==================== 属性访问器（强类型，自动打失效级别） ====================

    /**
     * 设置文本内容。
     *
     * <p>先判值是否真变化（与当前值相等则直接 return），
     * 变化时自动调用 {@link #markSelfLayout()} + {@link #markSelfPaint()}：
     * 文本既影响布局盒尺寸/行高（LAYOUT），又影响绘制输出/实际字符串（PAINT）。</p>
     *
     * @param text 新的文本内容
     */
    public void setText(String text) {
        if (Objects.equals(this.text, text)) return;
        this.text = text;
        markSelfLayout();
        markSelfPaint();
    }

    /** @return 当前文本内容 */
    public String getText() {
        return text;
    }

    /**
     * 设置背景颜色（ARGB）。
     *
     * <p>值不变则跳过，变化时调用 {@link #markSelfPaint()}。</p>
     *
     * @param backgroundColor ARGB 颜色值
     */
    public void setBackgroundColor(int backgroundColor) {
        if (this.backgroundColor == backgroundColor) return;
        this.backgroundColor = backgroundColor;
        markSelfPaint();
    }

    /** @return 当前背景颜色（ARGB） */
    public int getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * 设置不透明度。
     *
     * <p>值不变则跳过，变化时调用 {@link #markComposite()}。</p>
     *
     * @param opacity 不透明度，范围 0.0f-1.0f
     */
    public void setOpacity(float opacity) {
        if (Float.compare(this.opacity, opacity) == 0) return;
        this.opacity = opacity;
        markComposite();
    }

    /** @return 当前不透明度 */
    public float getOpacity() {
        return opacity;
    }

    /**
     * 设置合成级变换。
     *
     * <p>值不变则跳过，变化时调用 {@link #markComposite()}。</p>
     *
     * @param transform 变换对象，可为 null
     */
    public void setTransform(Transform transform) {
        if (Objects.equals(this.transform, transform)) return;
        this.transform = transform;
        markComposite();
    }

    /** @return 当前合成级变换，可能为 null */
    public Transform getTransform() {
        return transform;
    }

    /**
     * 设置是否填充父容器高度。
     *
     * <p>遵循现有 setter 范式：值不变则直接 return（去重），
     * 值变化时调用 {@link #markSelfLayout()}（fill 意图变化影响自身布局）。</p>
     *
     * <p><b>硬约束：fillParentHeight 只应用于容器节点，绝不用于文本叶节点。</b></p>
     *
     * @param fillParentHeight 是否填充父容器高度
     */
    public void setFillParentHeight(boolean fillParentHeight) {
        if (this.fillParentHeight == fillParentHeight) return;
        this.fillParentHeight = fillParentHeight;
        markSelfLayout();
    }

    /** @return 是否填充父容器高度 */
    public boolean isFillParentHeight() {
        return fillParentHeight;
    }

    /**
     * 设置首选高度（像素），供无文本/无子节点的叶节点显式指定最小高度。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfLayout()}
     * （尺寸变化影响自身布局，级别 LAYOUT）。</p>
     *
     * @param preferredHeight 首选高度，非负整数，0 表示不设最小值
     */
    public void setPreferredHeight(int preferredHeight) {
        if (this.preferredHeight == preferredHeight) return;
        this.preferredHeight = preferredHeight;
        markSelfLayout();
    }

    /** @return 当前首选高度（像素），默认 0 */
    public int getPreferredHeight() {
        return preferredHeight;
    }

    /**
     * 设置光标样式（I4c cursor 投影能力）。
     *
     * <p><b>⚠ 项目唯一不标脏的属性 setter</b>：cursor 是纯交互投影，
     * 不影响 layout/paint/composite 任何阶段。此 setter 内部不走
     * {@code markSelfLayout/markSelfPaint/markComposite}，也不会点亮
     * 任何祖先路标。这是有意的设计例外（用户拍板 D6-A、oracle 纠偏①）。</p>
     *
     * <p>null 表示"未声明/继承"语义，祖先链上溯至根均无声明时回退
     * {@link SceneCursor#DEFAULT}。解析由 {@code SceneCursorResolver} 负责。</p>
     *
     * @param cursor 光标样式枚举值，可为 null 表示未声明
     */
    public void setCursor(SceneCursor cursor) {
        // ★ 去重但绝不标脏：cursor 不影响 layout/paint/composite（D6-A）
        if (this.cursor == cursor) return;
        this.cursor = cursor;
    }

    /** @return 当前光标样式声明，null 表示未声明/继承 */
    public SceneCursor getCursor() {
        return cursor;
    }

    // ==================== 只读探针（供单测断言，命名对齐项目 __ 前缀惯例） ====================

    /** @return 自身布局脏标记 */
    public boolean __isSelfLayoutDirty() {
        return selfLayoutDirty;
    }

    /** @return 后代布局路标 */
    public boolean __isDescendantLayoutDirty() {
        return descendantLayoutDirty;
    }

    /** @return 自身绘制脏标记 */
    public boolean __isSelfPaintDirty() {
        return selfPaintDirty;
    }

    /** @return 后代绘制路标 */
    public boolean __isDescendantPaintDirty() {
        return descendantPaintDirty;
    }

    /** @return 合成脏标记 */
    public boolean __isCompositeDirty() {
        return compositeDirty;
    }

    /** @return 自身几何脏标记（位置/尺寸变化） */
    public boolean __isSelfGeometryDirty() {
        return selfGeometryDirty;
    }

    /** @return 后代几何路标 */
    public boolean __isDescendantGeometryDirty() {
        return descendantGeometryDirty;
    }

    /**
     * @return 子节点列表的不可变视图
     */
    public List<SceneNode> __getChildren() {
        return Collections.unmodifiableList(children);
    }

    /** @return 父节点，可能为 null */
    public SceneNode __getParent() {
        return parent;
    }
}
