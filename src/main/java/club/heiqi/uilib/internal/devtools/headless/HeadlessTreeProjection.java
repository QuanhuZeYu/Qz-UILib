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
        private final String ownName;
        private final boolean centerHitsSelf;
        private final boolean onHitChain;
        private final String centerHitLabel;
        private final int childCount;

        Row(HeadlessNodePath path, int depth, String type, String text, int absX, int absY,
                int width, int height, int fontSizePx, boolean hitTestable, String ownName,
                boolean centerHitsSelf, boolean onHitChain, String centerHitLabel, int childCount) {
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
            this.centerHitsSelf = centerHitsSelf;
            this.onHitChain = onHitChain;
            this.centerHitLabel = centerHitLabel;
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

        /** @return 本节点自身的名称（无自身文本时为 null，此时 {@link #text()} 是聚合名） */
        public String ownName() {
            return ownName;
        }

        /**
         * @return 点本节点<b>中心</b>时，事件的目标是不是它自己。
         *
         * <p>这是「这个坐标点下去能不能如你所愿」的直接事实，与真实派发同一处实现
         * （{@code SceneInputRouter.__probeHitChain}，含浮层 top-first 与滚动裁剪）。</p>
         */
        public boolean centerHitsSelf() {
            return centerHitsSelf;
        }

        /**
         * @return 本节点是否出现在中心点的命中链上（即事件会冒泡到它）。
         *
         * <p>与 {@link #centerHitsSelf()} 的区别：本节点被<b>可命中的后代</b>盖住时，中心点到不了它，
         * 但后代是它的子节点，事件仍会冒泡经过它。这类节点（卡片、列表行）适合作为「整块区域」目标，
         * 不适合作为「精确点某个按钮」的目标。</p>
         */
        public boolean onHitChain() {
            return onHitChain;
        }

        /**
         * @return 中心点实际命中<b>谁</b>（{"地址 类型"}，未命中为 null）。
         *
         * <p>只报类型（如 {@code SceneNode}）对 agent 无用：27 行 {@code [blocked]} 全是同一个类型名，
         * 看不出被什么挡住。报地址才能给出可执行信息 —— 「被 {@code r1/0} 这层遮罩盖住了，先关它」。</p>
         */
        public String centerHitLabel() {
            return centerHitLabel;
        }

        /**
         * @return 是否为<b>可点目标</b>：有可见尺寸、可命中，且点它的中心<b>真的打到它自己</b>。
         *
         * <p><b>判据是真实命中，不是几何近似</b>。历史上用过「没有同为可命中的后代」，它等价不了
         * 「点得中」，独立复核据此给出四类反例：被滚动容器裁掉的目标（几何有尺寸、点下去越界）、
         * 被模态遮罩盖住的目标（点下去打到遮罩）、以及能命中自己的容器（反而被判成非目标）。
         * 这些差异只有命中测试能回答，所以判据直接取命中结果，不再叠加任何几何推导。</p>
         */
        public boolean actionableTarget() {
            return hitTestable && width > 0 && height > 0 && centerHitsSelf;
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

        /** @return 单行摘要：{@code <path> <type> "<text>" @x,y wxh fs=N} + 质量标注 */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append(path).append(' ').append(type);
            if (text != null && !text.isEmpty()) {
                sb.append(" \"").append(abbreviate(text)).append('"');
            }
            sb.append(" @").append(absX).append(',').append(absY)
                    .append(' ').append(width).append('x').append(height)
                    .append(" fs=").append(fontSizePx);
            sb.append(qualityTag());
            return sb.toString();
        }

        /**
         * 质量标注：调用方据此判断「这个地址该不该点」。
         *
         * <p>四档的语义边界（都不含几何推断，全部来自事实）：</p>
         * <ul>
         *   <li>{@code [target]}：中心点命中自己 —— 直接点，事件就到它。</li>
         *   <li>{@code [blocked]}：可命中、有尺寸，但中心点被别的东西接走了（裁剪 / 遮挡 / 更深的后代）。
         *       此时给出 {@code hit=<实际命中类型>} 说明被谁接走，调用方据此决定改点别处或先关浮层。</li>
         *   <li>{@code [container]}：中心点命中的是自己的后代（事件仍冒泡经过它）—— 适合整块区域，
         *       不适合「精确点某个控件」。</li>
         *   <li>{@code [non-interactive]}：自身不参与命中（布局容器 / 装饰叶）。</li>
         * </ul>
         */
        public String qualityTag() {
            if (actionableTarget()) {
                return " [target]";
            }
            if (!hitTestable) {
                return " [non-interactive]";
            }
            if (onHitChain) {
                return " [container]";
            }
            return " [blocked] hit=" + (centerHitLabel == null ? "NONE" : centerHitLabel);
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
        return project(root, rootIndex, interactiveOnly, null);
    }

    /**
     * 投影一棵树，并用<b>真实命中</b>标注每个节点的中心点质量。
     *
     * <p>命中源由调用方注入（{@link HitProbe}），而不是在本类内部自己拼一套几何判据：中心点
     * 「点到谁」只有 {@code SceneInputRouter} 的命中链能回答（含浮层 top-first、滚动裁剪与
     * 可命中祖先链），任何几何近似都会与真实派发漂移 —— 这正是本类此前 4 类标注反例的根因。</p>
     *
     * @param root            树根；null 返回空表
     * @param rootIndex       根序号（用于地址）
     * @param interactiveOnly true = 只留可命中节点（其祖先链保留以给出路径）
     * @param hitProbe        命中探针；null = 不标注（{@code centerHitsSelf}/{@code onHitChain}
     *                        恒为 false，{@code [target]} 不再出现）
     * @return 事实行（深度优先，同级按子节点顺序 = z-order）
     */
    public static List<Row> project(SceneNode root, int rootIndex, boolean interactiveOnly,
            HitProbe hitProbe) {
        List<Row> rows = new ArrayList<Row>();
        if (root == null) {
            return rows;
        }
        collect(root, root, HeadlessNodePath.root(rootIndex), 0, interactiveOnly, hitProbe, rows);
        return rows;
    }

    /**
     * 命中探针：把「某坐标的实际命中链」交给投影。
     *
     * <p>契约：只读、零副作用（与 {@code SceneHitTester} 的硬不变量一致）。返回空表表示该点未命中
     * 任何节点（越界 / 被完全裁掉）。</p>
     */
    public interface HitProbe {

        /**
         * @param tree 坐标所属的树根（投影正在遍历的那棵树）
         * @param x    该树坐标空间下的 X
         * @param y    该树坐标空间下的 Y
         * @return 命中链（root→最深目标）；未命中返回空表
         */
        List<SceneNode> hitChainAt(SceneNode tree, int x, int y);

        /**
         * 把「某棵树坐标空间下的盒」换算成<b>画布坐标</b>盒（{@code [x, y, w, h]}）。
         *
         * <p>投影报出的坐标必须是画布坐标 —— 它直接被 agent 拿去写 {@code move x y}。
         * 浮层根布局在自己的坐标空间里（锚点 + 相对倍率），局部 {@code (0,0)} 不在画布原点，
         * 若原样报出，锚定浮层里的每个节点都会指向画布上无关的位置。换算由本回调注入，
         * 与命中探针共用同一实现。</p>
         *
         * @param tree   坐标所属的树根
         * @param localX 局部 X
         * @param localY 局部 Y
         * @param width  局部宽
         * @param height 局部高
         * @return 画布坐标盒 {@code [x, y, w, h]}
         */
        int[] canvasBoxOf(SceneNode tree, int localX, int localY, int width, int height);

        /**
         * 把任意节点渲染成<b>可执行的目标描述</b>（形如 {@code r1/0/2 SceneNode}）。
         *
         * <p>为什么由调用方提供：地址空间由 runtime 持有（装配根 + 活跃浮层 + 子下标），投影只认
         * 「路径字符串」这一种表示。让本类自己反推地址会复制寻址规则，两处一旦不一致，报出的
         * {@code hit=…} 就指向不存在的目标 —— 而它正是用来指引 agent 下一步动作的。</p>
         *
         * @param node 目标节点；可为 null
         * @return 目标描述；节点不在任何已知地址空间内时返回 {@code null}
         */
        String labelOf(SceneNode node);
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
    private static void collect(SceneNode treeRoot, SceneNode node, HeadlessNodePath path, int depth,
            boolean interactiveOnly, HitProbe hitProbe, List<Row> rows) {
        if (depth > MAX_DEPTH) {
            return;
        }
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        int absX = box.getX();
        int absY = box.getY();
        int width = box.getWidth();
        int height = box.getHeight();
        // 浮层根布局在自己的坐标空间：报出的坐标必须是画布坐标，否则 agent 照抄 center= 会点错地方。
        // 命中判定仍用局部坐标（探针自己换算），两者取同一权威。
        int localCenterX = absX + width / 2;
        int localCenterY = absY + height / 2;
        if (hitProbe != null) {
            int[] canvas = hitProbe.canvasBoxOf(treeRoot, absX, absY, width, height);
            absX = canvas[0];
            absY = canvas[1];
            width = canvas[2];
            height = canvas[3];
        }
        // 交互筛选：可命中、未折叠、且有可见尺寸（零尺寸节点点了必落空，不该混进可点目标里）。
        boolean keep = !interactiveOnly
                || (node.isHitTestable() && !node.isCollapsed() && width > 0 && height > 0);
        List<SceneNode> children = node.__getChildren();
        if (keep) {
            String own = textOf(node);
            String ownText = (own != null && !own.isEmpty()) ? own : null;
            // 中心点用与 centerX/centerY 同一式子（+w/2），命中事实必须对应「会被点击的那个点」。
            String hitLabel = null;
            boolean hitsSelf = false;
            boolean onChain = false;
            if (hitProbe != null && width > 0 && height > 0) {
                List<SceneNode> chain = hitProbe.hitChainAt(treeRoot, localCenterX, localCenterY);
                if (chain != null && !chain.isEmpty()) {
                    hitsSelf = chain.get(chain.size() - 1) == node;
                    for (SceneNode hit : chain) {
                        if (hit == node) {
                            onChain = true;
                            break;
                        }
                    }
                    hitLabel = hitProbe.labelOf(chain.get(chain.size() - 1));
                }
            }
            rows.add(new Row(path, depth, typeOf(node), ownText != null ? ownText
                    : firstDescendantText(node, NAME_SEARCH_DEPTH), absX, absY, width, height,
                    node.effectiveFontSize(), node.isHitTestable(), ownText, hitsSelf, onChain,
                    hitLabel, children.size()));
        }
        for (int i = 0; i < children.size(); i++) {
            List<Integer> childIndexes = new ArrayList<Integer>(path.childIndexes());
            childIndexes.add(Integer.valueOf(i));
            collect(treeRoot, children.get(i), HeadlessNodePath.of(path.rootIndex(), childIndexes), depth + 1,
                    interactiveOnly, hitProbe, rows);
        }
    }

    /** 后代文本聚合的深度上限：可访问名称只取自浅层，避免深子树里翻出无关文案。 */
    private static final int NAME_SEARCH_DEPTH = 3;



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
