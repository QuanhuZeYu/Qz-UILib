package club.heiqi.uilib.internal.devtools.headless;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 场景树投影：把上一帧布局后的节点树摊平成「可寻址、可读」的事实表。
 *
 * <h3>它解决什么</h3>
 * <p>agent 不开游戏出图时，唯一能拿到坐标的途径此前是<b>自己数像素</b>：没有树结构、没有节点身份、
 * 没有绝对坐标，{@code SceneInputRouter.describeNode} 只给「类名@identityHashCode」（跨进程不稳）。
 * 于是「点到某个按钮」只能硬编码 {@code move 315 88}，页面稍有改动即失效。</p>
 *
 * <h3>它是只读视图，不是第二套真值</h3>
 * <p>本类<b>只读</b>节点既有状态（{@code __getChildren} / {@code getCachedLayout} / {@code getText} /
 * {@code effectiveFontSize} 等），不缓存、不写回、不建立索引副本：每次调用现算，产物即当帧事实。
 * 绝对坐标由局部 {@link LayoutBox} 沿路径累加得到（{@code SceneHitTester} 内部同法），故与命中测试
 * 的坐标口径天然一致 —— 投影里报出的坐标，就是点击会命中的位置。</p>
 *
 * <h3>交互事实为何必须筛</h3>
 * <p>树里绝大多数节点是布局容器与装饰叶（{@code hitTestable=false}），全部列出会让 agent 淹没在噪声里。
 * 默认只投影 {@code hitTestable && !collapsed} 的节点（即「点得到的东西」），其祖先链保留以给出路径。
 * {@code --nodes=all} 可要完整树（调试用）。</p>
 */
public final class HeadlessTreeProjection {

    /** 投影深度上限：防御异常深树把输出撑爆（正常页面 &lt; 30 层）。 */
    private static final int MAX_DEPTH = 64;

    private HeadlessTreeProjection() {
    }

    /** 一个可寻址节点的事实行。 */
    public static final class Row {

        private final HeadlessNodePath path;
        private final int depth;
        private final String type;
        private final String text;
        private final int absX;
        private final int absY;
        private final int width;
        private final int height;
        private final int fontSizePx;
        private final boolean hitTestable;
        private final int childCount;

        Row(HeadlessNodePath path, int depth, String type, String text, int absX, int absY,
                int width, int height, int fontSizePx, boolean hitTestable, int childCount) {
            this.path = path;
            this.depth = depth;
            this.type = type;
            this.text = text;
            this.absX = absX;
            this.absY = absY;
            this.width = width;
            this.height = height;
            this.fontSizePx = fontSizePx;
            this.hitTestable = hitTestable;
            this.childCount = childCount;
        }

        /** @return 地址（可直接写进输入脚本的 {@code click} 目标） */
        public HeadlessNodePath path() {
            return path;
        }

        /** @return 相对树根的深度（根为 0） */
        public int depth() {
            return depth;
        }

        /** @return 节点类名 */
        public String type() {
            return type;
        }

        /** @return 节点文本；无文本为 null */
        public String text() {
            return text;
        }

        /** @return 画布绝对左边界 */
        public int absX() {
            return absX;
        }

        /** @return 画布绝对上边界 */
        public int absY() {
            return absY;
        }

        /** @return 盒宽（布局结果；未布局为 0） */
        public int width() {
            return width;
        }

        /** @return 盒高（布局结果；未布局为 0） */
        public int height() {
            return height;
        }

        /** @return 生效字号（已含用户倍率） */
        public int fontSizePx() {
            return fontSizePx;
        }

        /** @return 是否可命中（可点击/可悬停） */
        public boolean hitTestable() {
            return hitTestable;
        }

        /** @return 直接子节点数 */
        public int childCount() {
            return childCount;
        }

        /** @return 中心点 X（点击默认落点） */
        public int centerX() {
            return absX + width / 2;
        }

        /** @return 中心点 Y（点击默认落点） */
        public int centerY() {
            return absY + height / 2;
        }

        /** @return 单行摘要：{@code <path> <type> "<text>" @x,y wxh fs=N} */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append(path).append(' ').append(type);
            if (text != null && !text.isEmpty()) {
                sb.append(" \"").append(abbreviate(text)).append('"');
            }
            sb.append(" @").append(absX).append(',').append(absY)
                    .append(' ').append(width).append('x').append(height)
                    .append(" fs=").append(fontSizePx);
            if (!hitTestable) {
                sb.append(" [non-interactive]");
            }
            return sb.toString();
        }

        private static String abbreviate(String text) {
            String flat = text.replace('\n', ' ');
            return flat.length() <= 40 ? flat : flat.substring(0, 39) + "…";
        }
    }

    /**
     * 投影一棵树。
     *
     * @param root           树根；null 返回空表
     * @param rootIndex      根序号（用于地址）
     * @param interactiveOnly true = 只留可命中节点（其祖先链保留以给出路径）
     * @return 事实行（深度优先，同级按子节点顺序 = z-order）
     */
    public static List<Row> project(SceneNode root, int rootIndex, boolean interactiveOnly) {
        List<Row> rows = new ArrayList<Row>();
        if (root == null) {
            return rows;
        }
        collect(root, HeadlessNodePath.root(rootIndex), 0, interactiveOnly, rows);
        return rows;
    }

    /**
     * 深度优先收集。
     *
     * <p><b>绝对坐标必须走 {@link SceneGeometry#absoluteBox}</b>（跨层几何换算的权威单点）：它沿 parent
     * 链累加局部 {@code LayoutBox} 偏移，并注入祖先滚动偏移（{@code childXBase}/{@code childYBase}）。
     * 自己写一份「父绝对 + 局部偏移」的累加器会漏掉滚动注入 —— 这正是
     * {@code LayoutBoxLocalCoordGuardTest} 拦下的那类错位（滚动容器里的节点会报出未滚动的坐标），
     * 且该测试把「读 LayoutBox 局部 x/y」限制在白名单内。</p>
     *
     * <p>未布局的节点（cachedLayout == null）得到零盒，如实报 0 —— <b>不猜</b>。</p>
     */
    private static void collect(SceneNode node, HeadlessNodePath path, int depth,
            boolean interactiveOnly, List<Row> rows) {
        if (depth > MAX_DEPTH) {
            return;
        }
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        int absX = box.getX();
        int absY = box.getY();
        int width = box.getWidth();
        int height = box.getHeight();
        boolean keep = !interactiveOnly || node.isHitTestable();
        List<SceneNode> children = node.__getChildren();
        if (keep) {
            rows.add(new Row(path, depth, typeOf(node), nameOf(node), absX, absY, width, height,
                    node.effectiveFontSize(), node.isHitTestable(), children.size()));
        }
        for (int i = 0; i < children.size(); i++) {
            List<Integer> childIndexes = new ArrayList<Integer>(path.childIndexes());
            childIndexes.add(Integer.valueOf(i));
            collect(children.get(i), HeadlessNodePath.of(path.rootIndex(), childIndexes), depth + 1,
                    interactiveOnly, rows);
        }
    }

    /** 后代文本聚合的深度上限：可访问名称只取自浅层，避免深子树里翻出无关文案。 */
    private static final int NAME_SEARCH_DEPTH = 3;

    /**
     * 节点的<b>可访问名称</b>：自身文本优先，为空时向下取第一个非空文本。
     *
     * <p>为什么必须聚合：可交互节点（按钮 / 列表项）在场景里通常<b>自己不持文本</b> —— 文案挂在
     * 子 label 上，而 label 往往 {@code hitTestable=false}、在「仅可命中」投影里被滤掉。只读自身
     * 文本会让 agent 看到一排 {@code r0/0 @0,0 57x40} 而完全不知道是哪个按钮（实测 playground
     * 导航 9 个标签全无名称）。这正是「按可见文本寻址」要解决的问题，故按无障碍树的口径
     * （名称从子树计算）补齐。</p>
     */
    private static String nameOf(SceneNode node) {
        String own = textOf(node);
        if (own != null && !own.isEmpty()) {
            return own;
        }
        return firstDescendantText(node, NAME_SEARCH_DEPTH);
    }

    /** 深度优先找第一个非空后代文本；超过深度上限返回 null。 */
    private static String firstDescendantText(SceneNode node, int depthLeft) {
        if (depthLeft <= 0) {
            return null;
        }
        for (SceneNode child : node.__getChildren()) {
            String text = textOf(child);
            if (text != null && !text.isEmpty()) {
                return text;
            }
            String deeper = firstDescendantText(child, depthLeft - 1);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }

    /**
     * 节点可见文本：单文本叶走 {@code getText()}，富文本叶走段流拼接。
     *
     * <p>只读 {@code getText()} 会漏掉绝大多数可见文字 —— 场景里画出来的文案多数是段流
     * （{@code setSegments}，绘制引擎段流优先），实测 playground 首页的可命中节点文本全空。
     * 这不是「没有文本」，是<b>文本载体不同</b>，投影必须两者都读，否则「按可见文本寻址」
     * 这条主用途直接失效。</p>
     */
    private static String textOf(SceneNode node) {
        String text = node.getText();
        if (text != null && !text.isEmpty()) {
            return text;
        }
        List<club.heiqi.uilib.font.layout.TextSegment> segments = node.getSegments();
        if (segments == null || segments.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (club.heiqi.uilib.font.layout.TextSegment segment : segments) {
            if (segment != null && segment.getText() != null) {
                sb.append(segment.getText());
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** 节点类名（匿名类回落到固定文案，便于输出稳定）。 */
    private static String typeOf(SceneNode node) {
        String name = node.getClass().getSimpleName();
        return name == null || name.isEmpty() ? "(anonymous)" : name;
    }

    /** 文本匹配：大小写不敏感的子串命中（供「按可见文本找控件」用）。 */
    public static boolean matchesText(Row row, String needle) {
        if (needle == null || needle.isEmpty()) {
            return false;
        }
        String text = row.text();
        if (text == null) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
