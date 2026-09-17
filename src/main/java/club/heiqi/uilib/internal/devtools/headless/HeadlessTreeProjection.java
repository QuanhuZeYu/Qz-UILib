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
 * 绝对坐标走 {@link SceneGeometry#absoluteBox}(node, 0, 0) —— 跨层几何换算的权威单点，含祖先滚动
 * 偏移注入，与命中测试同源 —— 故投影里报出的坐标，就是点击会命中的位置。</p>
 *
 * <h3>交互事实为何必须筛</h3>
 * <p>树里绝大多数节点是布局容器与装饰叶（{@code hitTestable=false}），全部列出会让 agent 淹没在噪声里。
 * 默认只投影 {@code hitTestable && !collapsed} 且<b>有可见尺寸</b>的节点，其祖先链保留以给出路径。
 * {@code --nodes=all} 可要完整树（含不可见尺寸 / 未激活浮层，调试用）。</p>
 *
 * <h3>两条已知边界（如实登记，不假装解决）</h3>
 * <ul>
 *   <li><b>尺寸 0 的节点不给中心点</b>：它不可点，中心点会误导。默认投影仍列出（结构事实），
 *       但 {@code centerX/Y} 的语义是「盒的中心」；调用方点之前应确认 {@code width>0 && height>0}。
 *       交互筛选下零尺寸节点被剔除。</li>
 *   <li><b>坐标是未裁剪的绝对盒</b>：被滚动容器裁掉、或属于未激活浮层的节点，坐标仍按几何算出。
 *       区分「在画面内 / 在树里」需要视口求交（{@code SceneGeometry.visibleBoxWithinScrollableAncestors}
 *       提供该能力），本类暂不引入 —— 引入后 {@code --nodes} 的语义会从「树里有什么」变成
 *       「画面上有什么」，那是另一个功能，不该偷偷改。</li>
 * </ul>
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
        private final boolean ownName;
        private final int hitTestableDescendants;
        private final int childCount;

        Row(HeadlessNodePath path, int depth, String type, String text, int absX, int absY,
                int width, int height, int fontSizePx, boolean hitTestable, boolean ownName,
                int hitTestableDescendants, int childCount) {
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
            this.ownName = ownName;
            this.hitTestableDescendants = hitTestableDescendants;
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

        /**
         * @return 名称是否取自本节点自身（false = 从子树聚合得到）。
         *
         * <p>聚合名会带来「容器与它的按钮同名」这类候选爆炸（实测 {@code --find=删除} 命中 4 个，
         * 含全屏遮罩与对话框卡片，真按钮只是其中之一）。</p>
         */
        public boolean ownName() {
            return ownName;
        }

        /**
         * @return 是否为<b>可点目标</b>：可命中、有可见尺寸、且没有同为可命中的后代。
         *
         * <p><b>判据是「最深可点节点」而不是「自身持名」</b>：场景里按钮与列表项普遍自己可命中、
         * 文案挂在不可命中的子 label 上，用「自身持名」会把真正的按钮也判成容器。反过来，全屏遮罩、
         * 对话框卡片这些可命中容器<b>有</b>可命中的后代，点它们的中心点会落在错误目标上 —— 这才
         * 是该剔除的那一类。</p>
         */
        public boolean actionableTarget() {
            return hitTestable && width > 0 && height > 0 && hitTestableDescendants == 0;
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
            if (actionableTarget()) {
                sb.append(" [target]");
            } else if (ownName) {
                sb.append(" [container]");
            } else {
                sb.append(" [aggregate-name]");
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
        // 交互筛选：可命中、未折叠、且有可见尺寸（零尺寸节点点了必落空，不该混进可点目标里）。
        boolean keep = !interactiveOnly
                || (node.isHitTestable() && !node.isCollapsed() && width > 0 && height > 0);
        List<SceneNode> children = node.__getChildren();
        if (keep) {
            String own = textOf(node);
            boolean ownName = own != null && !own.isEmpty();
            rows.add(new Row(path, depth, typeOf(node), ownName ? own : firstDescendantText(node,
                    NAME_SEARCH_DEPTH), absX, absY, width, height, node.effectiveFontSize(),
                    node.isHitTestable(), ownName, countHitTestableDescendants(node, 4), children.size()));
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


    /** 统计可命中的后代个数（深度上限内；用于区分「叶子可点目标」与「可点容器」）。 */
    private static int countHitTestableDescendants(SceneNode node, int depthLeft) {
        if (depthLeft <= 0) {
            return 0;
        }
        int count = 0;
        for (SceneNode child : node.__getChildren()) {
            if (child.isHitTestable()) {
                count++;
            }
            count += countHitTestableDescendants(child, depthLeft - 1);
        }
        return count;
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
