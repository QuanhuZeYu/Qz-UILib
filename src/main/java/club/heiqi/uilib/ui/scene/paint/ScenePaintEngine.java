package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextHorizontalAlign;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.image.SceneImageRect;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.text.SceneLineClamp;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.SceneTextMode;
import club.heiqi.uilib.ui.scene.text.TextLinkRegion;

/**
 * 场景树绘制引擎 —— 将节点树 + 布局结果转换为纯数据 Display List。
 *
 * <h3>方案 A：相对坐标 + offset 解耦</h3>
 * <p>每个节点产出的 PaintFragment 内命令存储<b>相对节点局部原点</b>的坐标（从
 * {@code (0,0)} 起）。组装 PaintPlan 时，通过 {@link PaintPlan#addFragment(PaintFragment, int, int)}
 * 叠加该节点的当前绝对偏移（来自 cachedLayout.x/y + 祖先累加），得到最终屏幕坐标。</p>
 *
 * <p>这使 fragment 可跨帧复用（布局位置或 internal presentation offset 变化 → fragment 引用不变、
 * 仅叠加的绝对 offset 变）。</p>
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

    /** 链接悬停命中高亮色（半透明青，垫在字形之下）。 */
    private static final int LINK_HOVER_HIGHLIGHT = 0x334FC3F7;

    /** opacity 接近 1.0 的容差：差值小于此值视为完全不透明，走快速路径跳过 group 边界 */
    private static final float OPACITY_EPSILON = 1e-4f;

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
        int regeneratedFragmentCount = 0;
        PaintPlan plan = new PaintPlan();
        if (root != null) {
            regeneratedFragmentCount = paintNode(root, plan, 0, 0);
        }
        return new PaintResult(plan, regeneratedFragmentCount);
    }

    // ==================== 内部递归 ====================

    /**
     * DFS 递归绘制单节点，实施 I8 双标记判定 + geometryDirty 下沉 + 相对坐标方案 +
     * Phase 3B 合成级 opacity/transform 通路。
     *
     * <p>所有命令直接写入共享 {@code plan}（由调用方传入），方法返回本子树重生成
     * fragment 数。子节点串行递归调用本方法，沿用同一共享 plan，保证 DFS 前序
     * z-order 与 PUSH/POP 嵌套天然正确。</p>
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
     * @param plan    共享绘制计划，所有命令直接写入此 plan
     * @param offsetX 从 root 到当前节点父的累积 X 偏移
     * @param offsetY 从 root 到当前节点父的累积 Y 偏移
     * @return 本子树重新生成的 fragment 数（含后代）
     */
    private int paintNode(SceneNode node, PaintPlan plan, int offsetX, int offsetY) {
        int regenerated = 0;
        // 计算本节点的绘制绝对坐标：LayoutBox 保持终态，internal reveal offset 只在 paint 几何叠加。
        LayoutBox box = (LayoutBox) node.getCachedLayout();
        int nodeAbsX = offsetX + (box != null ? box.getX() : 0);
        int nodeAbsY = offsetY + (box != null ? box.getY() : 0) + node.__getPresentationOffsetY();

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
                plan.addPushTransformLayer(nodeAbsX, nodeAbsY, nodeAbsX + width, nodeAbsY + height,
                        transform.translateX, transform.translateY, transform.rotateDegrees,
                        transform.scaleX, transform.scaleY, transform.originXRatio, transform.originYRatio);
            } else {
                // 无 clip：走 GL 矩阵纯顶点变换（零重栅格化，守信条五铁律）
                plan.addPushTransform(nodeAbsX, nodeAbsY, nodeAbsX + width, nodeAbsY + height,
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
            plan.addPushOpacity(nodeAbsX, nodeAbsY, nodeAbsX + width, nodeAbsY + height, opacity);
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
            plan.addClipPush(nodeAbsX, nodeAbsY, nodeAbsX + clipWidth, nodeAbsY + clipHeight,
                    node.getCornerRadius());
        }

        PaintFragment cached = (PaintFragment) node.getCachedPaint();

        // ==== 缓存有效 + selfPaintDirty==false → 复用 fragment（不管 geometry/composite 是否脏） ====
        if (!node.__isSelfPaintDirty() && cached != null) {
            // 本节点 paint 属性未变，复用缓存 fragment（但用新的 offset）
            // 这包括 selfGeometryDirty==true（布局位置/presentation offset 变）与
            // compositeDirty==true（opacity/transform 变）场景：
            // 均只重定位/重合成不重绘 —— 纯 composite 帧 fragment 引用不变，守信条五铁律
            plan.addFragment(cached, nodeAbsX, nodeAbsY);
        } else {
            // 需要重新生成 fragment（命令使用相对坐标，不含 presentation offset/opacity/transform）
            List<PaintCommand> commands = new ArrayList<>();
            generateCommands(node, commands);
            PaintFragment newFragment = new PaintFragment(commands);
            node.setCachedPaint(newFragment);
            plan.addFragment(newFragment, nodeAbsX, nodeAbsY);
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
        // ★ 横向滚动偏移注入（与纵向对称）：scrollOffsetX != 0 时后代整体左移显示
        int childOffsetX = SceneGeometry.childXBase(node, nodeAbsX);
        List<SceneNode> children = node.__getChildren();
        for (int i = 0; i < children.size(); i++) {
            SceneNode child = children.get(i);
            regenerated += paintNode(child, plan, childOffsetX, childOffsetY);
        }

        // ==== 子树命令全部产出后，先闭合裁剪作用域（与 CLIP_PUSH 严格配对，内层先关） ====
        if (needClip) {
            plan.addClipPop();
        }

        // ==== 子树命令全部产出后，闭合本节点 group opacity 作用域（与 PUSH 严格配对） ====
        if (needGroup) {
            plan.addPopOpacity();
        }

        // ==== 子树命令全部产出后，闭合 transform 作用域（最外层，与 PUSH 严格配对） ====
        // B6 FBO 方案：needTransform && needClip → POP_TRANSFORM_LAYER，否则 POP_TRANSFORM
        if (needTransform) {
            if (needClip) {
                plan.addPopTransformLayer();
            } else {
                plan.addPopTransform();
            }
        }

        // ==== 清除本节点 paint + geometry + composite 脏标记 ====
        // composite 必须在此清除：Phase 3A 解耦后 clearPaintDirty 不再顺手清 composite，
        // 否则 compositeDirty 永久累积（3A+3B 同单元交付的硬约束）。
        node.clearPaintDirty();
        node.clearGeometryDirty();
        node.clearCompositeDirty();
        return regenerated;
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

        SceneImageSource imageSource = node.getImageSource();
        if (imageSource != null) {
            SceneImageRect rect = node.getImageRect();
            int left = rect == null ? 0 : rect.getLeft();
            int top = rect == null ? 0 : rect.getTop();
            int right = rect == null ? width : rect.getRight();
            int bottom = rect == null ? height : rect.getBottom();
            out.add(PaintCommand.image(imageSource, left, top, right, bottom));
        }

        // 有文本 → TEXT 命令（相对坐标，文字色读 node.getTextColor()，默认白零回归）
        // fontSize 直接读 node.getFontSize()（不再用 height 做 hack 回退）：
        // 字号是节点自有属性，与布局盒高度解耦，fill 文本节点不再炸 fontSize。
        // 拆行统一走 measurer.splitLines：maxTextWidth>0 软换行，<=0 仍按硬换行（<br>/\n）
        // 拆行（富文本感知：标签不占宽、样式跨行续传），每行一条 TEXT 命令。
        String text = node.getText();
        if (text != null && !text.isEmpty()) {
            int fontSize = node.getFontSize();
            SceneTextMode textMode = node.getTextMode();
            int wrapWidth = node.getMaxTextWidth();
            List<String> lines = measurer.splitLines(text, fontSize, wrapWidth, textMode);
            // maxLines 截断 + 可选省略号（与布局测量共用 SceneLineClamp，口径一致）
            List<String> displayedLines = SceneLineClamp.clamp(lines, node.getMaxLines(), node.isEllipsis(),
                    measurer, fontSize, wrapWidth, textMode);
            int baseLineHeight = measurer.lineHeight(fontSize);
            int lineCount = displayedLines.size();
            int[] lineHeights = new int[lineCount];
            int totalHeight = 0;
            boolean hasOversizedLine = false;
            boolean hasExplicitLineHeight = node.getLineHeightMultiplier() > 0.0D || node.getLineHeightPx() > 0;
            for (int index = 0; index < lineCount; index++) {
                int lineHeight = node.resolveLineHeight(
                        measurer.lineHeight(displayedLines.get(index), fontSize, textMode));
                lineHeights[index] = lineHeight;
                totalHeight += lineHeight;
                hasOversizedLine |= lineHeight > baseLineHeight;
            }
            // 单行且无显式大字段且无显式行距：保持 em-box=fontSize 的旧对齐（零回归）；
            // 多行、混排行或显式行距：块高按逐行行高累计（行距放大/压缩与大字行高生效）。
            int emHeight = (lineCount <= 1 && !hasOversizedLine && !hasExplicitLineHeight) ? fontSize : totalHeight;
            int textTop = calculateTextTop(node, box, emHeight);
            int cursorY = textTop;
            int lineIndex = 0;
            for (String line : displayedLines) {
                TextStyle style = new TextStyle(node.getTextColor(), fontSize, textMode);
                int textLeft = calculateTextLeft(node, box, fontSize, line);
                out.add(PaintCommand.text(textLeft, cursorY, line, style));
                int lineBottom = cursorY + lineHeights[lineIndex];
                // 链接命中区域：与 TEXT 同批产出（相对节点局部坐标），供控件层 CLICK 命中测试；
                // 悬停命中的链接画半透明高亮背景（BACKGROUND 先于 TEXT 输出，垫在字形之下）。
                String activeLinkUrl = node.getActiveLinkUrl();
                for (TextLinkRegion region : measurer.linkRegions(line, fontSize, textMode)) {
                    int regionLeft = textLeft + region.getStartX();
                    int regionRight = regionLeft + region.getWidth();
                    if (region.getUrl().equals(activeLinkUrl)) {
                        out.add(PaintCommand.background(regionLeft, cursorY, regionRight, lineBottom,
                                LINK_HOVER_HIGHLIGHT));
                    }
                    out.add(PaintCommand.linkRegion(regionLeft, cursorY, regionRight, lineBottom,
                            region.getUrl()));
                }
                cursorY = lineBottom;
                lineIndex++;
            }
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
     * 否则与 em-box 锚点错配导致文字垂直偏移（预防通则见 错误预防.md 字体系统类）。</p>
     *
     * <p>em-box 显示高 == 字号：烘焙 em=64、{@code glyphScale=fontSize/64}，故 {@code 64*glyphScale=fontSize}。
     * 字号到渲染器 charSize 全链路 1:1 透传（scene 文本不经 UI_TEXT_SCALE），该等式严格成立。</p>
     *
     * <p>多行/混排模型：调用方传入已累计的行块总高（逐行行高求和，混排行取该行最大字号行高）；
     * 单行无混排时传入字号保持原 em-box 对齐行为（零回归）。</p>
     *
     * @param node     当前节点
     * @param box      当前节点布局盒
     * @param emHeight 行块总高（UI 像素，>=1）
     * @return 文本绘制起点（em-box 顶）相对节点局部原点的 Y 偏移
     */
    private int calculateTextTop(SceneNode node, LayoutBox box, int emHeight) {
        int paddingTop = node.getPaddingTop();
        int paddingBottom = node.getPaddingBottom();
        int innerHeight = box.getHeight() - paddingTop - paddingBottom;
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
