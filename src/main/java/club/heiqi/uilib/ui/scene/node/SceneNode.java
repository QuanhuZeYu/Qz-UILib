package club.heiqi.uilib.ui.scene.node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.scene.image.SceneImageRect;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;

import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.layout.AlignSelf;
import club.heiqi.uilib.ui.scene.text.SceneTextMode;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;

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
 *
 * <h3>禁止重写 equals/hashCode（identity 语义锚定）</h3>
 * <p><b>禁止重写 equals/hashCode</b>：{@code SceneLayoutEngine.measuredTextNodes} 用
 * {@code ConcurrentHashMap.newKeySet()} 做引用去重，失效链依赖引用相等。一旦重写 equals，
 * 去重语义从 identity 漂移到值相等，失效链会丢节点。ConcurrentHashMap.newKeySet() 的桶定位
 * 走 equals/hashCode，SceneNode 默认 Object.equals = 引用相等，故与原 IdentityHashMap 行为等价；
 * 此等价前提即「SceneNode 永不重写 equals/hashCode」，任何子类亦不得重写。</p>
 */
public class SceneNode {

    /**
     * 宽轴尺寸策略。高轴无对称枚举——COLUMN 容器高度默认 shrink-to-fit（由子节点累加决定），
     * 高轴的"shrink"是默认态无需开关。详见 NORTH_STAR.md §4 视口条款。
     * fillParentHeight(true) 是 COLUMN 主轴 grow 桥，与 WidthSizing 语义正交。
     *
     * <p>{@link #FILL} 保持默认填满父约束宽度；{@link #SHRINK} 让容器在未设置
     * preferredWidth 时按已布局子节点内容回收宽度。该策略仅影响有子节点的容器，
     * 叶节点仍沿用文本 shrink / 装饰 fill 的既有语义。</p>
     */
    public enum WidthSizing {
        /** 容器宽度填满父级下传的可用宽度。 */
        FILL,
        /** 容器宽度按子内容 shrink-to-fit，并被父级可用宽度 clamp。 */
        SHRINK
    }

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

    /** 后代中存在合成脏节点，合成遍历需下沉的路标 */
    boolean descendantCompositeDirty;

    /**
     * 自身位置/尺寸变化（layout 引擎产出）或 internal presentation offset 变化，paint 遍历需下沉更新 offset。
     * <p>这是独立的 COMPOSITE 子级别标记，语义单一：显示几何变，不重绘但需重定位。
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

    /** 文本行计划缓存（布局引擎写入、绘制引擎消费，同 cachedLayout 生命周期；
     *  <b>强类型</b>：拆行/clamp/行高/链接区域一次性产物的权威副本，审查报告 §8 B2-4）。 */
    private club.heiqi.uilib.ui.scene.text.TextLinePlan cachedTextPlan;

    /** 链接命中区域缓存（绘制引擎投影写入、控件层命中测试读取，同 cachedPaint 生命周期；
     *  <b>强类型</b>：命中数据正式化载体，审查报告 §8 B2-5）。 */
    private java.util.List<club.heiqi.uilib.ui.scene.text.LinkHitRegion> cachedLinkHitRegions;

    /** 上一次 layoutInternal 传入本节点的约束快照。null=从未布局过。
     *  仅布局引擎读写,作为「约束变更」这一自身布局输入的订阅缓存。
     *  语义类比引擎的 lastRootConstraints,但下放到每个节点,使深层节点
     *  也能感知收到的约束变化。绝不参与脏标记冒泡,绝不触碰后代。 */
    private Constraints lastConstraints;   // 默认 null

    public Constraints __getLastConstraints() { return lastConstraints; }
    public void __setLastConstraints(Constraints c) { this.lastConstraints = c; }

    /** 上一次完成文本测量时的字体 epoch。-1=从未测量。仅布局引擎读写，
     *  作为「epoch 变化」这一布局输入的订阅快照。语义类比 lastConstraints，
     *  下放到每个文本叶节点。绝不参与脏标记冒泡、绝不触碰后代。 */
    private int lastMeasuredEpoch = -1;

    public int __getLastMeasuredEpoch() { return lastMeasuredEpoch; }
    public void __setLastMeasuredEpoch(int epoch) { this.lastMeasuredEpoch = epoch; }

    // ==================== 强类型属性槽 ====================

    /** 文本内容属性值容器（对称 ScenePaintProps/SceneLayoutProps）。去重与脏标记仍由本类 setter 负责。 */
    private final SceneTextProps textProps = new SceneTextProps();

    /** 当前指针悬停命中的链接 URL；null 表示无悬停链接。
     *  <b>交互投影组字段</b>（与 cursor/hitTestable 同级，审查报告 §8 B2-2：不入 SceneTextProps）：
     *  交互层写入，绘制层读以高亮命中区域；仅标 PAINT，不影响布局。 */
    private String activeLinkUrl;

    /** 绘制/合成属性值容器。去重与脏标记仍由 SceneNode setter 负责。 */
    private final ScenePaintProps paintProps = new ScenePaintProps();

    /** 布局/滚动属性值容器。去重与脏标记仍由 SceneNode setter 负责。 */
    private final SceneLayoutProps layoutProps = new SceneLayoutProps();

    /**
     * 字号（UI 像素），默认 16。
     *
     * <p>默认 16 保零回归（原绘制层 fontSize hack 的默认值即 16）。字号既影响布局
     * 几何（文本叶 shrink-to-fit 宽度与行高），又影响绘制输出（TEXT 命令 fontSize），
     * 故 {@link #setFontSize} 与 {@link #setText} 同级，标 LAYOUT+PAINT。</p>
     */
    private int fontSizePx = 16;

    /** 光标样式声明，纯交互投影，setter 不标脏。 */
    private SceneCursor cursor;

    /** 是否参与命中测试，默认 true；纯输入路由投影，setter 不标脏。 */
    private boolean hitTestable = true;

    /** internal input gate；false 时 hit-test 与 Tab traversal 跳过整棵子树。 */
    private boolean hitTestSubtreeEnabled = true;

    /** internal reveal 位移；只改变 paint 绝对坐标，LayoutBox 与 hit-test 保持终态。 */
    private int presentationOffsetY;

    // ==================== 构造器 ====================

    /** 创建一个空的场景树节点 */
    public SceneNode() {
        this.children = new ArrayList<>();
    }

    // ==================== 容器静态工厂（5 行容器样板塌成 1 行） ====================

    /** 创建 COLUMN（纵向）容器。 */
    public static SceneNode column() {
        SceneNode n = new SceneNode();
        n.setFlexDirection(FlexDirection.COLUMN);
        return n;
    }

    /** 创建 ROW（横向）容器。 */
    public static SceneNode row() {
        SceneNode n = new SceneNode();
        n.setFlexDirection(FlexDirection.ROW);
        return n;
    }

    /** 创建带主轴间距的 COLUMN 容器。 */
    public static SceneNode column(int gap) {
        return column().setGap(gap);
    }

    /** 创建带主轴间距的 ROW 容器。 */
    public static SceneNode row(int gap) {
        return row().setGap(gap);
    }

    // ==================== 树操作 ====================

    /** 在末尾追加子节点；只标本容器布局脏，不递归标后代。 */
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

    /** 移除子节点；只标本容器布局脏，不递归标后代。 */
    public void removeChild(SceneNode child) {
        if (child == null) return;
        if (children.remove(child)) {
            child.parent = null;
            markSelfLayout();
        }
    }

    /** 在指定锚点前插入子节点；只标本容器布局脏，不递归标后代。 */
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
     * 批量提交最终子节点序列；稳定复用节点零标脏，容器自身只调一次 {@link #markSelfLayout()}。
     *
     * <p><b>insertedOrMoved 仅是声明性元数据，本方法不得读取或消费它。</b>几何变化唯一权威源
     * 仍是 layout 阶段的 {@code newBox.equals(childBox)} 闸门。</p>
     *
     * @param finalOrder 最终子节点序列
     * @param insertedOrMoved 声明性元数据，本方法体不读取
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

    /** 标记自身布局脏，并向祖先点亮布局路标。 */
    public void markSelfLayout() {
        if (selfLayoutDirty) return; // 已标脏，跳过重复冒泡
        selfLayoutDirty = true;
        cachedLayout = null;
        bubbleDescendantLayout();
    }

    /** 标记自身绘制脏，并向祖先点亮绘制路标。 */
    public void markSelfPaint() {
        selfPaintDirty = true;
        cachedPaint = null;
        bubbleDescendantPaint();
    }

    /** 标记合成脏；opacity/transform 专用，不污染 layout/paint。 */
    public void markComposite() {
        if (compositeDirty) return; // 已标脏，跳过重复冒泡（与 markSelfLayout/markGeometryDirty 对齐）
        compositeDirty = true;
        bubbleDescendantComposite();
    }

    /** 标记几何变化；只向上冒泡，绝不向下递归。 */
    public void markGeometryDirty() {
        if (selfGeometryDirty) return; // 已标脏，跳过重复冒泡
        selfGeometryDirty = true;
        bubbleDescendantGeometry();
    }

    /** @return 是否实际点亮 selfGeometryDirty（无向上冒泡） */
    public boolean __setSelfGeometryDirtyNoBubble() {
        if (selfGeometryDirty) return false;
        selfGeometryDirty = true;
        return true;
    }

    /** 从本节点向上点亮几何路标。 */
    public void __bubbleDescendantGeometryFromSelf() {
        bubbleDescendantGeometry();
    }

    /** @return 恒 true；保留 markSelfPaint 无短路清缓存语义。 */
    public boolean __setSelfPaintDirtyNoBubble() {
        selfPaintDirty = true;
        cachedPaint = null;
        return true;
    }

    /** 从本节点向上点亮绘制路标。 */
    public void __bubbleDescendantPaintFromSelf() {
        bubbleDescendantPaint();
    }

    /** 沿祖先链点亮布局路标，遇已点亮即停。 */
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

    /** 沿祖先链点亮绘制路标，遇已点亮即停。 */
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

    /** 沿祖先链点亮几何路标，遇已点亮即停。 */
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

    /** 沿祖先链点亮合成路标，遇已点亮即停。 */
    private void bubbleDescendantComposite() {
        SceneNode current = parent;
        while (current != null) {
            if (current.descendantCompositeDirty) {
                break;
            }
            current.descendantCompositeDirty = true;
            current = current.parent;
        }
    }

    // ==================== 清除方法 ====================

    /** 清除所有脏标记。 */
    public void clearDirtyFlags() {
        selfLayoutDirty = false;
        descendantLayoutDirty = false;
        selfPaintDirty = false;
        descendantPaintDirty = false;
        compositeDirty = false;
        descendantCompositeDirty = false;
    }

    /** 清除布局相关脏标记。 */
    public void clearLayoutDirty() {
        selfLayoutDirty = false;
        descendantLayoutDirty = false;
    }

    /** 清除绘制相关脏标记；不清合成标记。 */
    public void clearPaintDirty() {
        selfPaintDirty = false;
        descendantPaintDirty = false;
    }

    /** 清除合成相关脏标记。 */
    public void clearCompositeDirty() {
        compositeDirty = false;
        descendantCompositeDirty = false;
    }

    /** 清除几何变化脏标记。 */
    public void clearGeometryDirty() {
        selfGeometryDirty = false;
        descendantGeometryDirty = false;
    }

    // ==================== 缓存管理 ====================

    /** @return 布局缓存，可能为 null */
    public Object getCachedLayout() { return cachedLayout; }

    /** @param cachedLayout 布局缓存值 */
    public SceneNode setCachedLayout(Object cachedLayout) {
        this.cachedLayout = cachedLayout;
        return this;
    }

    /** @return 绘制缓存，可能为 null */
    public Object getCachedPaint() { return cachedPaint; }

    /** @param cachedPaint 绘制缓存值 */
    public SceneNode setCachedPaint(Object cachedPaint) {
        this.cachedPaint = cachedPaint;
        return this;
    }

    /** @return 文本行计划缓存，可能为 null（布局后写入） */
    public club.heiqi.uilib.ui.scene.text.TextLinePlan getCachedTextPlan() { return cachedTextPlan; }

    /** @param cachedTextPlan 文本行计划缓存值（布局引擎写入） */
    public SceneNode setCachedTextPlan(club.heiqi.uilib.ui.scene.text.TextLinePlan cachedTextPlan) {
        this.cachedTextPlan = cachedTextPlan;
        return this;
    }

    /** @return 链接命中区域缓存，可能为 null（绘制后写入） */
    public java.util.List<club.heiqi.uilib.ui.scene.text.LinkHitRegion> getCachedLinkHitRegions() {
        return cachedLinkHitRegions;
    }

    /** @param cachedLinkHitRegions 链接命中区域缓存值（绘制引擎写入） */
    public SceneNode setCachedLinkHitRegions(
            java.util.List<club.heiqi.uilib.ui.scene.text.LinkHitRegion> cachedLinkHitRegions) {
        this.cachedLinkHitRegions = cachedLinkHitRegions;
        return this;
    }

    // ==================== 属性访问器（强类型，自动打失效级别） ====================

    /** 设置文本内容；变化时标 LAYOUT + PAINT。 */
    public SceneNode setText(String text) {
        if (Objects.equals(textProps.text, text)) return this;
        textProps.text = text;
        markSelfLayout();
        markSelfPaint();
        return this;
    }

    /** @return 当前文本内容 */
    public String getText() { return textProps.text; }

    /**
     * 设置文本内容模式；变化时标 LAYOUT + PAINT。
     *
     * <p>遗留 int 编码入口（0=原始文本 / 1=MINECRAFT_FORMATTED / 2=RICH_TAGS，见
     * {@link SceneTextMode}），越界回落原始文本；新代码优先 {@link #setTextMode(SceneTextMode)}。</p>
     *
     * @param textContentMode 模式编码（0/1/2），越界回落到 0
     */
    public SceneNode setTextContentMode(int textContentMode) {
        return setTextMode(SceneTextMode.fromCode(textContentMode));
    }

    /** @return 文本内容模式编码（0=原始文本 / 1=MINECRAFT_FORMATTED / 2=RICH_TAGS） */
    public int getTextContentMode() { return textProps.textContentMode.getCode(); }

    /**
     * 设置文本内容模式（枚举语义锚）；变化时标 LAYOUT + PAINT。
     *
     * @param textContentMode 内容模式（非 null）
     */
    public SceneNode setTextMode(SceneTextMode textContentMode) {
        if (textContentMode == null) {
            throw new IllegalArgumentException("textContentMode 不可为 null");
        }
        if (textProps.textContentMode == textContentMode) {
            return this;
        }
        textProps.textContentMode = textContentMode;
        markSelfLayout();
        markSelfPaint();
        return this;
    }

    /** @return 文本内容模式（枚举语义锚） */
    public SceneTextMode getTextMode() { return textProps.textContentMode; }

    /**
     * 设置最大换行宽度（UI 像素）；{@code <=0} 表示不换行。变化时标 LAYOUT + PAINT。
     *
     * @param maxTextWidth 最大换行宽度
     */
    public SceneNode setMaxTextWidth(int maxTextWidth) {
        if (textProps.maxTextWidth == maxTextWidth) {
            return this;
        }
        textProps.maxTextWidth = maxTextWidth;
        markSelfLayout();
        markSelfPaint();
        return this;
    }

    /** @return 最大换行宽度（UI 像素），{@code <=0} 表示不换行 */
    public int getMaxTextWidth() { return textProps.maxTextWidth; }

    /**
     * 设置行距倍数（0=自动行高）；变化时标 LAYOUT + PAINT。
     *
     * <p>行高 = 自动行高（行内最大字号对应行高）× 倍数，向上取整到像素；
     * 设置后优先于 {@link #setLineHeightPx}。</p>
     *
     * @param lineHeightMultiplier 行距倍数，负值归 0
     */
    public SceneNode setLineHeightMultiplier(double lineHeightMultiplier) {
        double normalized = lineHeightMultiplier < 0.0D ? 0.0D : lineHeightMultiplier;
        if (textProps.lineHeightMultiplier == normalized) {
            return this;
        }
        textProps.lineHeightMultiplier = normalized;
        markSelfLayout();
        markSelfPaint();
        return this;
    }

    /** @return 行距倍数（0=自动行高） */
    public double getLineHeightMultiplier() { return textProps.lineHeightMultiplier; }

    /**
     * 设置绝对行高（UI 像素，0=自动行高）；变化时标 LAYOUT + PAINT。
     *
     * <p>仅当行距倍数未设置时生效；小于自动行高时压缩行距。</p>
     *
     * @param lineHeightPx 绝对行高，负值归 0
     */
    public SceneNode setLineHeightPx(int lineHeightPx) {
        int normalized = Math.max(0, lineHeightPx);
        if (textProps.lineHeightPx == normalized) {
            return this;
        }
        textProps.lineHeightPx = normalized;
        markSelfLayout();
        markSelfPaint();
        return this;
    }

    /** @return 绝对行高（UI 像素，0=自动行高） */
    public int getLineHeightPx() { return textProps.lineHeightPx; }

    /**
     * 按节点显式行距设置解析最终行高（UI 像素，至少 1）。
     *
     * <p>优先级：行距倍数 &gt; 0 时按 {@code ceil(base × 倍数)}；否则绝对行高 &gt; 0 时取绝对行高；
     * 均未设置返回 base（自动行高）。绘制与布局共用本方法，保证同口径。</p>
     *
     * @param baseLineHeight 自动行高（行内最大字号对应行高，UI 像素）
     * @return 最终行高（UI 像素）
     */
    public int resolveLineHeight(int baseLineHeight) {
        if (textProps.lineHeightMultiplier > 0.0D) {
            return Math.max(1, (int) Math.ceil(baseLineHeight * textProps.lineHeightMultiplier));
        }
        if (textProps.lineHeightPx > 0) {
            return Math.max(1, textProps.lineHeightPx);
        }
        return baseLineHeight;
    }

    /**
     * 设置最大显示行数（{@code <=0} 不限行）；变化时标 LAYOUT + PAINT。
     *
     * @param maxLines 最大显示行数
     */
    public SceneNode setMaxLines(int maxLines) {
        int normalized = Math.max(0, maxLines);
        if (textProps.maxLines == normalized) {
            return this;
        }
        textProps.maxLines = normalized;
        markSelfLayout();
        markSelfPaint();
        return this;
    }

    /** @return 最大显示行数（{@code <=0} 表示不限行） */
    public int getMaxLines() { return textProps.maxLines; }

    /**
     * 设置是否在 maxLines 截断的末行追加省略号（仅换行宽度有效时生效）；变化时标 LAYOUT + PAINT。
     *
     * @param ellipsis 是否追加省略号
     */
    public SceneNode setEllipsis(boolean ellipsis) {
        if (textProps.ellipsis == ellipsis) {
            return this;
        }
        textProps.ellipsis = ellipsis;
        markSelfLayout();
        markSelfPaint();
        return this;
    }

    /** @return 是否在截断末行追加省略号 */
    public boolean isEllipsis() { return textProps.ellipsis; }

    /**
     * 设置当前悬停命中的链接 URL（交互层写入）；变化时仅标 PAINT（命中高亮不改变布局）。
     *
     * @param activeLinkUrl 悬停链接 URL，null 表示无悬停链接
     */
    public SceneNode setActiveLinkUrl(String activeLinkUrl) {
        if (java.util.Objects.equals(this.activeLinkUrl, activeLinkUrl)) {
            return this;
        }
        this.activeLinkUrl = activeLinkUrl;
        markSelfPaint();
        return this;
    }

    /** @return 当前悬停命中的链接 URL；null 表示无悬停链接 */
    public String getActiveLinkUrl() { return activeLinkUrl; }

    /** 设置字号；变化时标 LAYOUT + PAINT。 */
    public SceneNode setFontSize(int fontSizePx) {
        if (this.fontSizePx == fontSizePx) return this;
        this.fontSizePx = fontSizePx;
        markSelfLayout();
        markSelfPaint();
        return this;
    }

    /** @return 当前字号（UI 像素），默认 16 */
    public int getFontSize() {
        return fontSizePx;
    }

    /** @see ScenePaintProps#backgroundColor */
    public SceneNode setBackgroundColor(int backgroundColor) {
        if (paintProps.backgroundColor == backgroundColor) return this;
        paintProps.backgroundColor = backgroundColor;
        markSelfPaint();
        return this;
    }

    /** @see ScenePaintProps#backgroundColor */
    public int getBackgroundColor() { return paintProps.backgroundColor; }

    /** 设置平台中立图片源；按对象身份去重，变化时仅标 PAINT。 */
    public SceneNode setImageSource(SceneImageSource imageSource) {
        if (paintProps.imageSource == imageSource) return this;
        paintProps.imageSource = imageSource;
        markSelfPaint();
        return this;
    }

    /** @return 当前图片源，可能为 null */
    public SceneImageSource getImageSource() { return paintProps.imageSource; }

    /** 设置图片局部目标矩形；null 表示铺满节点布局盒，变化时仅标 PAINT。 */
    public SceneNode setImageRect(SceneImageRect imageRect) {
        if (paintProps.imageRect == imageRect) return this;
        paintProps.imageRect = imageRect;
        markSelfPaint();
        return this;
    }

    /** @return 图片局部目标矩形，null 表示铺满节点 */
    public SceneImageRect getImageRect() { return paintProps.imageRect; }

    /** @see ScenePaintProps#opacity */
    public SceneNode setOpacity(float opacity) {
        if (Float.compare(paintProps.opacity, opacity) == 0) return this;
        paintProps.opacity = opacity;
        markComposite();
        return this;
    }

    /** @see ScenePaintProps#opacity */
    public float getOpacity() { return paintProps.opacity; }

    /** @see ScenePaintProps#transform */
    public SceneNode setTransform(Transform transform) {
        if (Objects.equals(paintProps.transform, transform)) return this;
        paintProps.transform = transform;
        markComposite();
        return this;
    }

    /** @see ScenePaintProps#transform */
    public Transform getTransform() { return paintProps.transform; }

    /**
     * 设置 internal、像素对齐的 presentation Y 位移。
     *
     * <p>该值统一平移本节点 fragment、clip/opacity/transform bounds 与全部后代，但不改
     * LayoutBox 或输入几何。仅供输入已门禁的短时 reveal 使用；变化属于 GEOMETRY 级。</p>
     */
    public SceneNode __setPresentationOffsetY(int offsetY) {
        if (presentationOffsetY == offsetY) return this;
        presentationOffsetY = offsetY;
        markGeometryDirty();
        return this;
    }

    /** @return internal presentation Y 位移（像素） */
    public int __getPresentationOffsetY() { return presentationOffsetY; }

    /** @see SceneLayoutProps#fillParentHeight */
    public SceneNode setFillParentHeight(boolean fillParentHeight) {
        if (layoutProps.fillParentHeight == fillParentHeight) return this;
        layoutProps.fillParentHeight = fillParentHeight;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#fillParentHeight */
    public boolean isFillParentHeight() { return layoutProps.fillParentHeight; }

    /** @see SceneLayoutProps#fillParentWidth */
    public SceneNode setFillParentWidth(boolean fillParentWidth) {
        if (layoutProps.fillParentWidth == fillParentWidth) return this;
        layoutProps.fillParentWidth = fillParentWidth;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#fillParentWidth */
    public boolean isFillParentWidth() { return layoutProps.fillParentWidth; }

    /** @see SceneLayoutProps#preferredHeight */
    public SceneNode setPreferredHeight(int preferredHeight) {
        if (layoutProps.preferredHeight == preferredHeight) return this;
        layoutProps.preferredHeight = preferredHeight;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#preferredHeight */
    public int getPreferredHeight() { return layoutProps.preferredHeight; }

    /** @see SceneLayoutProps#preferredWidth */
    public SceneNode setPreferredWidth(int preferredWidth) {
        if (layoutProps.preferredWidth == preferredWidth) return this;
        layoutProps.preferredWidth = preferredWidth;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#preferredWidth */
    public int getPreferredWidth() { return layoutProps.preferredWidth; }

    /** @see SceneLayoutProps#maxHeight */
    public SceneNode setMaxHeight(int maxHeight) {
        if (layoutProps.maxHeight == maxHeight) return this;
        layoutProps.maxHeight = maxHeight;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#maxHeight */
    public int getMaxHeight() { return layoutProps.maxHeight; }

    /** @see SceneLayoutProps#maxWidth */
    public SceneNode setMaxWidth(int maxWidth) {
        if (layoutProps.maxWidth == maxWidth) return this;
        layoutProps.maxWidth = maxWidth;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#maxWidth */
    public int getMaxWidth() { return layoutProps.maxWidth; }

    /** @see SceneLayoutProps#percentHeight */
    public SceneNode setPercentHeight(int percentHeight) {
        if (layoutProps.percentHeight == percentHeight) return this;
        layoutProps.percentHeight = percentHeight;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#percentHeight */
    public int getPercentHeight() { return layoutProps.percentHeight; }

    /** @see SceneLayoutProps#percentWidth */
    public SceneNode setPercentWidth(int percentWidth) {
        if (layoutProps.percentWidth == percentWidth) return this;
        layoutProps.percentWidth = percentWidth;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#percentWidth */
    public int getPercentWidth() { return layoutProps.percentWidth; }

    /** @see SceneLayoutProps#widthSizing */
    public SceneNode setWidthSizing(WidthSizing widthSizing) {
        WidthSizing normalized = widthSizing == null ? WidthSizing.FILL : widthSizing;
        if (layoutProps.widthSizing == normalized) return this;
        layoutProps.widthSizing = normalized;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#widthSizing */
    public WidthSizing getWidthSizing() { return layoutProps.widthSizing; }

    /** 设置光标样式；纯交互投影，不标脏。 */
    public SceneNode setCursor(SceneCursor cursor) {
        if (this.cursor == cursor) return this;
        this.cursor = cursor;
        return this;
    }

    /** @return 当前光标样式声明，null 表示未声明/继承 */
    public SceneCursor getCursor() { return cursor; }

    /** 设置是否参与命中测试；纯输入路由投影，不标脏。 */
    public SceneNode setHitTestable(boolean hitTestable) {
        if (this.hitTestable == hitTestable) return this;
        this.hitTestable = hitTestable;
        return this;
    }

    /** @return 是否参与命中测试，默认 true */
    public boolean isHitTestable() { return hitTestable; }

    /** internal：临时启停整棵子树的用户输入，不改变各节点原有 hitTestable 状态。 */
    public void __setHitTestSubtreeEnabled(boolean enabled) {
        this.hitTestSubtreeEnabled = enabled;
    }

    /** @return internal input gate 当前是否开放 */
    public boolean __isHitTestSubtreeEnabled() { return hitTestSubtreeEnabled; }

    // ==================== flex 布局属性访问器（LAYOUT 级） ====================

    /** @see SceneLayoutProps#flexDirection */
    public SceneNode setFlexDirection(FlexDirection flexDirection) {
        if (layoutProps.flexDirection == flexDirection) return this;
        layoutProps.flexDirection = flexDirection;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#flexDirection */
    public FlexDirection getFlexDirection() { return layoutProps.flexDirection; }

    /** @see SceneLayoutProps#flexGrow */
    public int getFlexGrow() { return layoutProps.flexGrow; }

    /** @see SceneLayoutProps#flexGrow */
    public SceneNode setFlexGrow(int flexGrow) {
        if (layoutProps.flexGrow == flexGrow) return this;
        layoutProps.flexGrow = flexGrow;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#paddingTop */
    public SceneNode setPadding(int top, int right, int bottom, int left) {
        if (layoutProps.paddingTop == top && layoutProps.paddingRight == right
            && layoutProps.paddingBottom == bottom && layoutProps.paddingLeft == left) {
            return this;
        }
        layoutProps.paddingTop = top;
        layoutProps.paddingRight = right;
        layoutProps.paddingBottom = bottom;
        layoutProps.paddingLeft = left;
        markSelfLayout();
        return this;
    }

    /** 设置四向相等的内边距。 */
    public SceneNode setPadding(int all) {
        setPadding(all, all, all, all);
        return this;
    }

    /** @see SceneLayoutProps#paddingTop */
    public int getPaddingTop() { return layoutProps.paddingTop; }

    /** @see SceneLayoutProps#paddingRight */
    public int getPaddingRight() { return layoutProps.paddingRight; }

    /** @see SceneLayoutProps#paddingBottom */
    public int getPaddingBottom() { return layoutProps.paddingBottom; }

    /** @see SceneLayoutProps#paddingLeft */
    public int getPaddingLeft() { return layoutProps.paddingLeft; }

    /** @see SceneLayoutProps#marginTop */
    public SceneNode setMargin(int top, int right, int bottom, int left) {
        if (layoutProps.marginTop == top && layoutProps.marginRight == right
            && layoutProps.marginBottom == bottom && layoutProps.marginLeft == left) {
            return this;
        }
        layoutProps.marginTop = top;
        layoutProps.marginRight = right;
        layoutProps.marginBottom = bottom;
        layoutProps.marginLeft = left;
        markSelfLayout();
        return this;
    }

    /** 设置四向相等的外边距。 */
    public SceneNode setMargin(int all) {
        setMargin(all, all, all, all);
        return this;
    }

    /** @see SceneLayoutProps#marginTop */
    public int getMarginTop() { return layoutProps.marginTop; }

    /** @see SceneLayoutProps#marginRight */
    public int getMarginRight() { return layoutProps.marginRight; }

    /** @see SceneLayoutProps#marginBottom */
    public int getMarginBottom() { return layoutProps.marginBottom; }

    /** @see SceneLayoutProps#marginLeft */
    public int getMarginLeft() { return layoutProps.marginLeft; }

    /** @return 垂直方向外边距合计 */
    public int marginV() { return layoutProps.marginTop + layoutProps.marginBottom; }

    /** @return 水平方向外边距合计 */
    public int marginH() { return layoutProps.marginLeft + layoutProps.marginRight; }

    /** @see SceneLayoutProps#gap */
    public SceneNode setGap(int gap) {
        if (layoutProps.gap == gap) return this;
        layoutProps.gap = gap;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#gap */
    public int getGap() { return layoutProps.gap; }

    /** @see SceneLayoutProps#mainAxisAlign */
    public SceneNode setMainAxisAlign(MainAxisAlign mainAxisAlign) {
        if (layoutProps.mainAxisAlign == mainAxisAlign) return this;
        layoutProps.mainAxisAlign = mainAxisAlign;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#mainAxisAlign */
    public MainAxisAlign getMainAxisAlign() { return layoutProps.mainAxisAlign; }

    /** @see SceneLayoutProps#crossAxisAlign */
    public SceneNode setCrossAxisAlign(CrossAxisAlign crossAxisAlign) {
        if (layoutProps.crossAxisAlign == crossAxisAlign) return this;
        layoutProps.crossAxisAlign = crossAxisAlign;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#crossAxisAlign */
    public CrossAxisAlign getCrossAxisAlign() { return layoutProps.crossAxisAlign; }

    /** @see SceneLayoutProps#alignSelf */
    public SceneNode setAlignSelf(AlignSelf alignSelf) {
        if (layoutProps.alignSelf == alignSelf) return this;
        layoutProps.alignSelf = alignSelf;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#alignSelf */
    public AlignSelf getAlignSelf() { return layoutProps.alignSelf; }

    // ==================== 绘制属性访问器（PAINT 级） ====================

    /** @see ScenePaintProps#borderColor */
    public SceneNode setBorderColor(int borderColor) {
        if (paintProps.borderColor == borderColor) return this;
        paintProps.borderColor = borderColor;
        markSelfPaint();
        return this;
    }

    /** @see ScenePaintProps#borderColor */
    public int getBorderColor() { return paintProps.borderColor; }

    /** @see ScenePaintProps#borderWidth */
    public SceneNode setBorderWidth(int borderWidth) {
        if (paintProps.borderWidth == borderWidth) return this;
        paintProps.borderWidth = borderWidth;
        markSelfPaint();
        return this;
    }

    /** @see ScenePaintProps#borderWidth */
    public int getBorderWidth() { return paintProps.borderWidth; }

    /** @see ScenePaintProps#cornerRadius */
    public SceneNode setCornerRadius(int cornerRadius) {
        if (paintProps.cornerRadius == cornerRadius) return this;
        paintProps.cornerRadius = cornerRadius;
        markSelfPaint();
        return this;
    }

    /** @see ScenePaintProps#cornerRadius */
    public int getCornerRadius() { return paintProps.cornerRadius; }

    /** @see ScenePaintProps#clipChildren */
    public SceneNode setClipChildren(boolean clipChildren) {
        if (paintProps.clipChildren == clipChildren) return this;
        paintProps.clipChildren = clipChildren;
        markSelfPaint();
        return this;
    }

    /** @see ScenePaintProps#clipChildren */
    public boolean isClipChildren() { return paintProps.clipChildren; }

    /** @see ScenePaintProps#textColor */
    public SceneNode setTextColor(int textColor) {
        if (paintProps.textColor == textColor) return this;
        paintProps.textColor = textColor;
        markSelfPaint();
        return this;
    }

    /** @see ScenePaintProps#textColor */
    public int getTextColor() { return paintProps.textColor; }

    /** @see ScenePaintProps#textVerticalAlign */
    public SceneNode setTextVerticalAlign(TextVerticalAlign textVerticalAlign) {
        if (textVerticalAlign == null) {
            throw new IllegalArgumentException("TextVerticalAlign 不可为 null");
        }
        if (paintProps.textVerticalAlign == textVerticalAlign) return this;
        paintProps.textVerticalAlign = textVerticalAlign;
        markSelfPaint();
        return this;
    }

    /** @see ScenePaintProps#textVerticalAlign */
    public TextVerticalAlign getTextVerticalAlign() { return paintProps.textVerticalAlign; }

    /** @see ScenePaintProps#textHorizontalAlign */
    public SceneNode setTextHorizontalAlign(TextHorizontalAlign textHorizontalAlign) {
        if (textHorizontalAlign == null) {
            throw new IllegalArgumentException("TextHorizontalAlign 不可为 null");
        }
        if (paintProps.textHorizontalAlign == textHorizontalAlign) return this;
        paintProps.textHorizontalAlign = textHorizontalAlign;
        markSelfPaint();
        return this;
    }

    /** @see ScenePaintProps#textHorizontalAlign */
    public TextHorizontalAlign getTextHorizontalAlign() { return paintProps.textHorizontalAlign; }

    // ==================== 滚动属性访问器（scrollOffsetY=GEOMETRY 级；scrollable=LAYOUT 级） ====================

    /** @see SceneLayoutProps#scrollOffsetY */
    public SceneNode setScrollOffsetY(int scrollOffsetY) {
        if (layoutProps.scrollOffsetY == scrollOffsetY) return this;
        layoutProps.scrollOffsetY = scrollOffsetY;
        markGeometryDirty();
        return this;
    }

    /** @see SceneLayoutProps#scrollOffsetY */
    public int getScrollOffsetY() { return layoutProps.scrollOffsetY; }

    /** @see SceneLayoutProps#scrollOffsetX */
    public SceneNode setScrollOffsetX(int scrollOffsetX) {
        if (layoutProps.scrollOffsetX == scrollOffsetX) return this;
        layoutProps.scrollOffsetX = scrollOffsetX;
        markGeometryDirty();
        return this;
    }

    /** @see SceneLayoutProps#scrollOffsetX */
    public int getScrollOffsetX() { return layoutProps.scrollOffsetX; }

    /** @see SceneLayoutProps#scrollable */
    public SceneNode setScrollable(boolean scrollable) {
        if (layoutProps.scrollable == scrollable) return this;
        layoutProps.scrollable = scrollable;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#scrollable */
    public boolean isScrollable() { return layoutProps.scrollable; }

    /** @see SceneLayoutProps#scrollableX */
    public SceneNode setScrollableX(boolean scrollableX) {
        if (layoutProps.scrollableX == scrollableX) return this;
        layoutProps.scrollableX = scrollableX;
        markSelfLayout();
        return this;
    }

    /** @see SceneLayoutProps#scrollableX */
    public boolean isScrollableX() { return layoutProps.scrollableX; }

    /** @return 是否为 paint 与 hit-test 共用的裁剪窗口 */
    public boolean isClipWindow() { return paintProps.clipChildren || layoutProps.scrollable; }

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

    /** @return 自身合成脏标记（opacity/transform 变化） */
    public boolean __isCompositeDirty() {
        return compositeDirty;
    }

    /** @return 后代合成路标 */
    public boolean __isDescendantCompositeDirty() {
        return descendantCompositeDirty;
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
