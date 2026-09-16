package club.heiqi.uilib.internal.devtools.headless;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 文本探针宿主：把一行指定文本按固定字号渲染到左上角，供字体路径对照与诊断使用。
 *
 * <p>存在理由：字体侧缺陷（度量漂移、字号解析错、字形不上屏）在整页出图里很难归因——
 * 页面一多就无法判断「差在哪」。探针把变量收敛到「一段文本 + 一个字号 + 一个固定起点」，
 * 于是软光栅通道与 GL 通道可以在同一输入上做几何对拍。</p>
 *
 * <p>文本经生产 {@link SceneLabel} 渲染（不另造文本控件），字号即控件字号，与软光栅侧的
 * {@code baseFontSizePx} 同语义。</p>
 */
final class TextProbeHost extends AbstractSceneHostWidget {

    /** 探针固定字号（UI 像素）：与软光栅对拍时使用同一值。 */
    static final int FONT_SIZE_PX = 32;
    /** 探针文本左上内边距：给墨迹包围盒一个可预期的起点。 */
    private static final int PADDING = 8;
    /** 探针前景色：不透明白，保证与深色背景的对比度稳定。 */
    private static final int FOREGROUND = 0xFFFFFFFF;

    private final String text;
    private final int width;
    private final int height;
    private SceneNode root;

    TextProbeHost(String text, int width, int height, PlatformInputSource input) {
        super(input);
        this.text = text;
        this.width = width;
        this.height = height;
        // 构造期建树（与 playground 宿主同惯例）：文本在装配阶段即参与布局测量，
        // 字形生成与后续帧推进重叠，避免「首帧才建树 → 出图时字形还没就绪」。
        this.root = buildRoot();
    }

    private SceneNode buildRoot() {
        SceneNode column = SceneNode.column();
        column.setFillParentWidth(true);
        column.setFillParentHeight(true);
        column.setPadding(PADDING);
        // 必须给定可用宽度：SceneLabel 在零宽约束下会把文本裁到几乎不可见。
        // 原始文本模式 = 0（scene paint 契约 TEXT_MODE_*）。
        SceneLabel.Props props = new SceneLabel.Props(Signal.create(text), FOREGROUND, FONT_SIZE_PX, 0,
                width - 2 * PADDING);
        SceneNode label = SceneLabel.create(runtime, props).get();
        label.setFillParentWidth(true);
        column.appendChild(label);
        return column;
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }
}
