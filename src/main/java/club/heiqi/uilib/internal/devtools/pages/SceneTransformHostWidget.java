package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * 新栈 ui.scene Transform+Clip 可视化 demo 宿主 Widget。
 *
 * <p>本页集中验证 B6 FBO 离屏图层方案下 transform+clip 叠加渲染正确性：
 * 六张卡片覆盖 rotate/scale/translate 与矩形 clip 的组合，以及无 clip 对照组
 * 和 rotate+clip+opacity 三层嵌套。供真机验收旋转裁剪视觉与帧率。</p>
 *
 * <p>视觉验证点：clip 框始终是未旋转的轴对齐矩形，旋转/缩放后的内容超出框
 * 部分被裁掉；无 clip 卡片走 GL 矩阵路径非 FBO，作为对照基准。</p>
 */
public class SceneTransformHostWidget extends AbstractSceneHostWidget {

    /** 演示色块色 1（蓝） */
    private static final int SWATCH_BLUE = SceneChromeTokens.ACCENT;
    /** 演示色块色 2（青） */
    private static final int SWATCH_CYAN = SceneChromeTokens.ACCENT_PROGRESS;
    /** 演示色块色 3（紫，复用 hover 提亮） */
    private static final int SWATCH_PURPLE = SceneChromeTokens.ACCENT_HOVER;
    /** clip 容器边框色（聚焦边框，便于看清裁剪框） */
    private static final int CLIP_BORDER = SceneChromeTokens.BORDER_FOCUS;
    /** 标题条固定高度 */
    private static final int TITLE_BAR_HEIGHT = 38;
    /** 滚动容器内 viewport 与 scrollbar 列间距 */
    private static final int SCROLL_GAP = 3;

    private final SceneNode root;
    private final SceneNode viewport;
    private final SceneNode scrollContainer;
    private final SceneNode scrollbarColumn;
    private final SceneNode content;
    private final Signal<Integer> scrollSignal;

    /**
     * 创建 Transform+Clip demo 宿主 Widget，注入平台输入源。
     *
     * @param inputSource 平台输入源，可为 null（退化模式，无真机滚轮）
     */
    public SceneTransformHostWidget(PlatformInputSource inputSource) {
        super(inputSource);

        this.root = createRoot();
        root.appendChild(createTitleBar());

        this.viewport = createViewport();
        this.content = createContent();
        viewport.appendChild(content);
        this.scrollContainer = createScrollContainer();
        scrollContainer.appendChild(viewport);
        root.appendChild(scrollContainer);
        // 整页滚动：viewport fillParentHeight 吃满 host，attach 后挂兄弟 scrollbar 列
        this.scrollSignal = SceneScrolls.attach(runtime, viewport);

        // 六张卡片：覆盖 rotate/scale/translate 与 clip 的组合 + 对照组 + 嵌套叠加
        content.appendChild(createRotateClipCard());
        content.appendChild(createRotateLargeClipCard());
        content.appendChild(createScaleClipCard());
        content.appendChild(createTranslateClipCard());
        content.appendChild(createRotateNoClipCard());
        content.appendChild(createRotateClipOpacityCard());

        // 滚动条叠加在 viewport 右侧（scrollContainer ROW 内独立列），照 ConfigScreen 范式。
        SceneScrollbar.Props sbProps = new SceneScrollbar.Props(
                viewport, scrollSignal, scrollSignal::set,
                SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                SceneScrollbar.DEFAULT_BAR_WIDTH, SceneScrollbar.DEFAULT_MIN_THUMB_HEIGHT);
        SceneScrollbar.Result sb = SceneScrollbar.create(runtime, sbProps);
        this.scrollbarColumn = sb.column();
        scrollContainer.appendChild(scrollbarColumn);

        runtime.flush();
    }

    /**
     * 创建根容器。
     *
     * @return 根场景节点
     */
    private SceneNode createRoot() {
        SceneNode node = new SceneNode();
        node.setFillParentHeight(true);
        node.setFlexDirection(FlexDirection.COLUMN);
        node.setPadding(SceneChromeTokens.PAD_LG);
        node.setGap(SceneChromeTokens.GAP_MD);
        node.setBackgroundColor(SceneDemoTokens.ROOT_BG);
        return node;
    }

    /**
     * 创建固定标题条。
     *
     * @return 标题条节点
     */
    private SceneNode createTitleBar() {
        SceneNode titleBar = SceneNode.column();
        titleBar.setPreferredHeight(TITLE_BAR_HEIGHT);
        titleBar.setGap(SceneChromeTokens.GAP_SM);
        titleBar.setHitTestable(false);
        titleBar.appendChild(text("Scene Transform+Clip demo", SceneDemoTokens.TITLE_COLOR));
        titleBar.appendChild(text("B6 FBO 离屏图层 · rotate/scale/translate 与矩形 clip 叠加验证", SceneDemoTokens.MUTED_COLOR));
        return titleBar;
    }

    /**
     * 创建滚动视口。
     *
     * @return 滚动视口节点
     */
    private SceneNode createViewport() {
        SceneNode node = SceneNode.column();
        node.setFillParentHeight(true);
        node.setFlexGrow(1);
        node.setScrollable(true);
        node.setClipChildren(true);
        node.setPadding(SceneChromeTokens.PAD_LG);
        node.setGap(SceneChromeTokens.GAP_MD);
        node.setBackgroundColor(SceneDemoTokens.VIEWPORT_BG);
        node.setCornerRadius(SceneChromeTokens.RADIUS_LG);
        return node;
    }

    /**
     * 创建滚动容器（ROW：viewport + scrollbar 列），照 ConfigScreen 范式。
     *
     * @return 滚动容器节点
     */
    private SceneNode createScrollContainer() {
        SceneNode node = SceneNode.row();
        node.setFillParentHeight(true);
        node.setGap(SCROLL_GAP);
        return node;
    }

    /**
     * 创建视口内内容容器。
     *
     * @return 内容容器节点
     */
    private SceneNode createContent() {
        SceneNode node = SceneNode.column();
        node.setGap(SceneChromeTokens.GAP_MD);
        return node;
    }

    // ==================== 六张卡片 ====================

    /**
     * 卡片 1：rotate + 矩形 clip（B6 核心验证格）。
     * 视觉验证点：旋转 15° 的色块被未旋转的轴对齐矩形视口裁剪。
     *
     * @return 卡片节点
     */
    private SceneNode createRotateClipCard() {
        SceneNode card = section("1. rotate(15°) + 矩形 clip", "B6 核心验证格：旋转色块被轴对齐矩形裁剪。");
        SceneNode clipBox = clipContainer(120, 80, SWATCH_BLUE);
        clipBox.appendChild(swatchWithLabel("rotate 15°", SWATCH_BLUE, Transform.rotate(15f)));
        card.appendChild(demoRow(clipBox));
        card.appendChild(readout("裁剪框是未旋转的 120×80 矩形，旋转色块四角被裁"));
        return card;
    }

    /**
     * 卡片 2：rotate 大角度 + clip（极端旋转）。
     * 视觉验证点：45° 旋转色块的角被矩形 clip 裁掉。
     *
     * @return 卡片节点
     */
    private SceneNode createRotateLargeClipCard() {
        SceneNode card = section("2. rotate(45°) + clip", "极端旋转：45° 色块角被矩形裁掉。");
        SceneNode clipBox = clipContainer(100, 100, SWATCH_CYAN);
        clipBox.appendChild(swatchWithLabel("rotate 45°", SWATCH_CYAN, Transform.rotate(45f)));
        card.appendChild(demoRow(clipBox));
        card.appendChild(readout("100×100 方框，45° 旋转后菱形角超出被裁"));
        return card;
    }

    /**
     * 卡片 3：scale + clip。
     * 视觉验证点：放大 1.5 倍后的内容超出 clip 框被裁。
     *
     * @return 卡片节点
     */
    private SceneNode createScaleClipCard() {
        SceneNode card = section("3. scale(1.5) + clip", "放大内容超出 clip 框被裁。");
        SceneNode clipBox = clipContainer(100, 60, SWATCH_PURPLE);
        clipBox.appendChild(swatchWithLabel("scale 1.5", SWATCH_PURPLE, Transform.scale(1.5f, 1.5f)));
        card.appendChild(demoRow(clipBox));
        card.appendChild(readout("100×60 clip 框，1.5× 放大后边缘超出被裁"));
        return card;
    }

    /**
     * 卡片 4：translate + clip（对照组，无需 FBO）。
     * 视觉验证点：平移后内容被 clip 裁，此格走 GL 矩阵路径非 FBO。
     *
     * @return 卡片节点
     */
    private SceneNode createTranslateClipCard() {
        SceneNode card = section("4. translate(20,10) + clip", "对照组：平移裁剪走 GL 矩阵非 FBO。");
        SceneNode clipBox = clipContainer(100, 60, SWATCH_BLUE);
        clipBox.appendChild(swatchWithLabel("translate 20,10", SWATCH_BLUE, Transform.translate(20f, 10f)));
        card.appendChild(demoRow(clipBox));
        card.appendChild(readout("100×60 clip 框，平移后右下溢出被裁，不走 FBO 路径"));
        return card;
    }

    /**
     * 卡片 5：rotate 无 clip（对照组，走 GL 矩阵非 FBO）。
     * 视觉验证点：旋转内容无裁剪，完整呈现。
     *
     * @return 卡片节点
     */
    private SceneNode createRotateNoClipCard() {
        SceneNode card = section("5. rotate(20°) 无 clip", "对照组：无裁剪旋转走 GL 矩阵非 FBO。");
        // 无 clip 容器：固定尺寸但不开启 clipChildren，仅作尺寸锚点
        SceneNode box = SceneNode.column();
        box.setPreferredWidth(120);
        box.setPreferredHeight(80);
        box.setPadding(SceneChromeTokens.PAD_SM);
        box.setGap(SceneChromeTokens.GAP_SM);
        box.setBorderColor(CLIP_BORDER);
        box.setBorderWidth(1);
        box.setCornerRadius(SceneChromeTokens.RADIUS_MD);
        box.setHitTestable(false);
        box.appendChild(swatchWithLabel("rotate 20° no clip", SWATCH_CYAN, Transform.rotate(20f)));
        card.appendChild(demoRow(box));
        card.appendChild(readout("无 clipChildren，旋转色块完整呈现不裁剪"));
        return card;
    }

    /**
     * 卡片 6：rotate + clip + opacity 三层叠加（嵌套验证）。
     * 视觉验证点：旋转 + 半透明 + 裁剪三者叠加正确。
     *
     * @return 卡片节点
     */
    private SceneNode createRotateClipOpacityCard() {
        SceneNode card = section("6. rotate(10°) + clip + opacity 0.7", "三层叠加：旋转 + 半透明 + 裁剪。");
        // 容器本身带 transform + opacity + clipChildren，验证嵌套合成
        SceneNode clipBox = clipContainer(120, 80, SWATCH_PURPLE);
        clipBox.setTransform(Transform.rotate(10f));
        clipBox.setOpacity(0.7f);
        clipBox.appendChild(swatchWithLabel("nested rotate+opacity", SWATCH_PURPLE, null));
        card.appendChild(demoRow(clipBox));
        card.appendChild(readout("容器 rotate 10° + opacity 0.7 + clip，子内容被旋转半透明裁剪"));
        return card;
    }

    // ==================== 通用构建器 ====================

    /**
     * 创建标准 section 卡片（标题 + 说明，后续 append 演示行与读数）。
     *
     * @param title       卡片标题
     * @param description 卡片说明
     * @return section 节点
     */
    private SceneNode section(String title, String description) {
        return SceneDemoCards.cardShell(title, description);
    }

    /**
     * 创建演示行（ROW 容器，承载 clip 演示容器）。
     *
     * @param clipBox clip 演示容器
     * @return 演示行节点
     */
    private SceneNode demoRow(SceneNode clipBox) {
        SceneNode row = SceneNode.row();
        row.setGap(SceneChromeTokens.GAP_MD);
        row.setHitTestable(false);
        row.appendChild(clipBox);
        return row;
    }

    /**
     * 创建 clip 演示容器（矩形 clip，固定尺寸，带聚焦边框便于看清裁剪框）。
     *
     * @param width  容器固定宽
     * @param height 容器固定高
     * @param accent 强调色（仅用于边框提示，实际背景透明以突出子节点）
     * @return clip 容器节点
     */
    private SceneNode clipContainer(int width, int height, int accent) {
        SceneNode node = SceneNode.column();
        node.setClipChildren(true);
        node.setPreferredWidth(width);
        node.setPreferredHeight(height);
        node.setPadding(SceneChromeTokens.PAD_SM);
        node.setGap(SceneChromeTokens.GAP_SM);
        node.setBorderColor(CLIP_BORDER);
        node.setBorderWidth(1);
        node.setCornerRadius(SceneChromeTokens.RADIUS_MD);
        node.setHitTestable(false);
        // 背景用强调色低透明，便于看清 clip 框范围
        node.setBackgroundColor((accent & 0x00FFFFFF) | 0x22000000);
        return node;
    }

    /**
     * 创建带标签的色块子节点，可附加 transform。
     *
     * @param label     色块上的文本
     * @param color     色块背景色
     * @param transform 变换；null 表示不设置
     * @return 色块节点
     */
    private SceneNode swatchWithLabel(String label, int color, Transform transform) {
        SceneNode box = SceneNode.column();
        box.setPadding(SceneChromeTokens.PAD_MD);
        box.setGap(SceneChromeTokens.GAP_SM);
        box.setBackgroundColor(color);
        box.setCornerRadius(SceneChromeTokens.RADIUS_SM);
        box.setHitTestable(false);
        // 给一个比 clip 框更大的首选尺寸，确保旋转/缩放后能溢出触发裁剪
        box.setPreferredWidth(140);
        box.setPreferredHeight(90);
        if (transform != null) {
            box.setTransform(transform);
        }
        box.appendChild(text(label, SceneChromeTokens.TEXT_ON_ACCENT));
        return box;
    }

    /**
     * 创建文字节点。
     *
     * @param value 文本内容
     * @param color 文本颜色
     * @return 文本节点
     */
    private SceneNode text(String value, int color) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setTextColor(color);
        node.setHitTestable(false);
        return node;
    }

    /**
     * 创建读数说明节点。
     *
     * @param value 读数文本
     * @return 读数节点
     */
    private SceneNode readout(String value) {
        SceneNode node = SceneNode.row();
        node.setPadding(SceneChromeTokens.PAD_SM);
        node.setBackgroundColor(SceneDemoTokens.READOUT_BG);
        node.setCornerRadius(SceneChromeTokens.RADIUS_SM);
        node.setHitTestable(false);
        node.appendChild(text(value, SceneDemoTokens.MUTED_COLOR));
        return node;
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /** @return 内部场景运行时 */
    SceneRuntime __getRuntime() {
        return runtime;
    }

    /** @return 内部布局引擎 */
    SceneLayoutEngine __getLayoutEngine() {
        return layoutEngine;
    }

    /** @return 场景树根节点 */
    SceneNode __getRoot() {
        return root;
    }

    /** @return 滚动视口节点 */
    SceneNode __getViewport() {
        return viewport;
    }

    /** @return 滚动容器节点（ROW：viewport + scrollbarColumn） */
    SceneNode __getScrollContainer() {
        return scrollContainer;
    }

    /** @return 滚动条列节点（scrollContainer 内 viewport 右侧独立列） */
    SceneNode __getScrollbarColumn() {
        return scrollbarColumn;
    }

    /** @return 视口内容容器节点 */
    SceneNode __getContent() {
        return content;
    }
}
