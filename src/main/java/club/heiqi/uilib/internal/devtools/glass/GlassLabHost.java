package club.heiqi.uilib.internal.devtools.glass;

import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.render.UiRenderBackends;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneSlider;
import club.heiqi.uilib.ui.scene.control.SceneToggle;
import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/**
 * 磨玻璃（backdrop-filter）展示实验室宿主 —— iOS 风格毛玻璃观感验收页。
 *
 * <p>链路：scene 主树先回放采样场（高饱和色带 + 细密文字 + 参数滑杆）→ 宿主在
 * {@link #render} 的 super.render 之后向后端追问 backdrop 增强能力，把玻璃面板
 * 回贴叠在已绘 UI 内容之上。面板覆盖区域采样的正是同帧场景内容，滑动参数即时
 * 反映模糊/饱和/圆角效果，并实时显示实际渲染路径（shader / fixed-pipeline /
 * tint-fallback）供诊断降级。</p>
 *
 * <p>「暂停玻璃」开关用于 A/B 对比：冻结帧跳过玻璃回贴，直接看采样场原貌。</p>
 */
public final class GlassLabHost extends AbstractSceneHostWidget {

    /** 内容最大宽（UI 像素）。 */
    private static final int CONTENT_MAX_WIDTH = PlaygroundKit.MAX_CONTENT_WIDTH;
    /** 采样场高度。 */
    private static final int STAGE_HEIGHT = 300;
    /** 探针玻璃卡高度。 */
    private static final int PROBE_CARD_HEIGHT = 120;
    /** 鼠标跟随玻璃半宽。 */
    private static final int FOLLOW_HALF_WIDTH = 130;
    /** 鼠标跟随玻璃半高。 */
    private static final int FOLLOW_HALF_HEIGHT = 64;
    /** 模糊半径滑杆上限。 */
    private static final int BLUR_MAX = 64;
    /** 圆角滑杆上限。 */
    private static final int RADIUS_MAX = 40;
    /** 饱和百分比滑杆上限。 */
    private static final int SATURATION_MAX = 300;
    /** 玻璃面高透白（iOS 材质基调）。 */
    private static final int GLASS_TINT = 0x26FFFFFF;
    /** 玻璃描边（顶部渐亮近似）。 */
    private static final int GLASS_EDGE = 0x66FFFFFF;
    /** 采样场色带（高饱和，模糊后差异最直观）。 */
    private static final int[] SAMPLE_COLORS = {
            0xFFEC4899, 0xFF38BDF8, 0xFFFBBF24, 0xFF22C55E, 0xFFA855F7,
            0xFFF97316, 0xFF06B6D4, 0xFF84CC16, 0xFFEF4444, 0xFF6366F1,
    };

    /** 根节点。 */
    private final SceneNode root;
    /** 采样场节点（玻璃面板位置以其 SceneGeometry.absoluteBox 绝对盒为基准）。 */
    private SceneNode stage;
    /** 探针玻璃卡节点（卡内顶部玻璃带验证快照含本帧内容）。 */
    private SceneNode probeCard;
    /** 实际渲染路径诊断文本。 */
    private final Signal<String> pathSignal = Signal.create("backdrop 路径: 等待首帧");
    /** 模糊半径（UI 像素，受控源）。 */
    private final Signal<Double> blurSignal = Signal.create(18.0D);
    /** 饱和度百分比（受控源）。 */
    private final Signal<Double> saturationSignal = Signal.create(125.0D);
    /** 圆角半径（UI 像素，受控源）。 */
    private final Signal<Double> radiusSignal = Signal.create(16.0D);
    /** 鼠标跟随玻璃开关。 */
    private final Signal<Boolean> followSignal = Signal.create(Boolean.TRUE);
    /** 暂停玻璃回贴开关（A/B 对比）。 */
    private final Signal<Boolean> frozenSignal = Signal.create(Boolean.FALSE);
    /** 滑杆值文本（受控源联动）。 */
    private final Signal<String> blurTextSignal = Signal.create("模糊半径 18");
    private final Signal<String> saturationTextSignal = Signal.create("饱和度 125%");
    private final Signal<String> radiusTextSignal = Signal.create("圆角 16");

    /**
     * 创建磨玻璃实验室宿主。
     *
     * @param input 平台输入源，可为 null（headless 测试退化模式）
     */
    public GlassLabHost(PlatformInputSource input) {
        super(input);
        runtime.__enableMotion();
        this.root = buildTree();
    }

    /**
     * measurer 可注入构造（headless 测试传入确定度量端口，同宿主基类 A4 口径）。
     *
     * @param measurer 文本度量端口
     * @param input    平台输入源，可为 null
     */
    public GlassLabHost(SceneTextMeasurer measurer, PlatformInputSource input) {
        super(measurer, input);
        runtime.__enableMotion();
        this.root = buildTree();
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /**
     * 渲染一帧：scene 主树回放后叠加玻璃面板。
     *
     * @param w 宿主宽度（原生像素）
     * @param h 宿主高度（原生像素）
     * @param ctx 渲染出口
     * @param absX 宿主绝对 X 偏移
     * @param absY 宿主绝对 Y 偏移
     */
    @Override
    public void render(int w, int h, UiRenderBackend ctx, int absX, int absY) {
        super.render(w, h, ctx, absX, absY);
        if (frozenSignal.get().booleanValue()) {
            pathSignal.set("backdrop 路径: 已暂停（玻璃回贴冻结）");
            return;
        }
        int blur = clampToInt(blurSignal.get(), 0, BLUR_MAX);
        int radius = clampToInt(radiusSignal.get(), 0, RADIUS_MAX);
        float saturation = clampToInt(saturationSignal.get(), 0, SATURATION_MAX) / 100.0F;

        // 坐标必须走 SceneGeometry.absoluteBox 权威单点（LayoutBox.x/y 是父相对局部坐标，
        // 直接用会把叠加层画到屏幕左缘——2026-09-01 真机首验根因）；absX/absY 同 hit test 口径。
        AnchorRect stageBox = SceneGeometry.absoluteBox(stage, absX, absY);
        if (stageBox.getWidth() > 0 && stageBox.getHeight() > 0) {
            // 主玻璃面板：覆盖采样场上半区，位置随布局派生（窗口缩放自动跟随）。
            int panelLeft = stageBox.getX() + 10;
            int panelTop = stageBox.getY() + 26;
            int panelRight = stageBox.getX() + stageBox.getWidth() - 10;
            int panelBottom = stageBox.getY() + 190;
            UiRenderBackends.backdropFilter(ctx, panelLeft, panelTop, panelRight, panelBottom,
                    blur, saturation, radius);
            ctx.drawSurface(panelLeft, panelTop, panelRight, panelBottom, GLASS_TINT, GLASS_EDGE, radius);

            // 鼠标跟随玻璃：需要后端报告指针位置（仅 MC 平台上下文支持）。
            if (followSignal.get().booleanValue() && ctx instanceof UiRenderContext) {
                UiRenderContext context = (UiRenderContext) ctx;
                int pointerX = context.getMouseX();
                int pointerY = context.getMouseY();
                if (pointerX >= stageBox.getX() && pointerX <= stageBox.getX() + stageBox.getWidth()
                        && pointerY >= stageBox.getY() && pointerY <= stageBox.getY() + stageBox.getHeight()) {
                    UiRenderBackends.backdropFilter(ctx, pointerX - FOLLOW_HALF_WIDTH, pointerY - FOLLOW_HALF_HEIGHT,
                            pointerX + FOLLOW_HALF_WIDTH, pointerY + FOLLOW_HALF_HEIGHT, blur, saturation,
                            Math.max(radius, FOLLOW_HALF_HEIGHT));
                    ctx.drawSurface(pointerX - FOLLOW_HALF_WIDTH, pointerY - FOLLOW_HALF_HEIGHT,
                            pointerX + FOLLOW_HALF_WIDTH, pointerY + FOLLOW_HALF_HEIGHT, GLASS_TINT, GLASS_EDGE,
                            Math.max(radius, FOLLOW_HALF_HEIGHT));
                }
            }
        }

        AnchorRect probeBox = SceneGeometry.absoluteBox(probeCard, absX, absY);
        if (probeBox.getWidth() > 0 && probeBox.getHeight() > 0) {
            // 探针玻璃：固定在卡内顶部 56px 带，观察其下文字是否被采样模糊。
            UiRenderBackends.backdropFilter(ctx, probeBox.getX() + 8, probeBox.getY() + 8,
                    probeBox.getX() + probeBox.getWidth() - 8, probeBox.getY() + 64, blur, saturation, radius);
            ctx.drawSurface(probeBox.getX() + 8, probeBox.getY() + 8, probeBox.getX() + probeBox.getWidth() - 8,
                    probeBox.getY() + 64, GLASS_TINT, GLASS_EDGE, radius);
        }
        pathSignal.set("backdrop 路径: " + UiRenderContext.getLastBackdropFilterRenderPath().getLabel()
                + " | 诊断: " + UiRenderContext.getLastBackdropFilterDetail());
    }

    /** 测试访问器：场景运行时（flush/路由用，同 TestPlaygroundHost 口径）。 */
    SceneRuntime __getRuntime() {
        return runtime;
    }

    /** 测试访问器：采样场节点（坐标基准断言用）。 */
    SceneNode __getStage() {
        return stage;
    }

    /** 测试访问器：暂停玻璃开关（A/B 冻结态受控源）。 */
    Signal<Boolean> __getFrozenSignal() {
        return frozenSignal;
    }

    /** 测试访问器：模糊半径受控源。 */
    Signal<Double> __getBlurSignal() {
        return blurSignal;
    }

    /** 测试访问器：渲染路径诊断文本源。 */
    Signal<String> __getPathSignal() {
        return pathSignal;
    }

    private SceneNode buildTree() {
        SceneNode shell = SceneNode.column();
        shell.setFillParentWidth(true);
        shell.setFillParentHeight(true);
        shell.setCrossAxisAlign(CrossAxisAlign.CENTER);
        shell.setPadding(12);
        shell.setGap(10);
        shell.setBackgroundColor(PlaygroundKit.ROOT_BG);

        SceneNode column = SceneNode.column();
        column.setFillParentWidth(true);
        column.setMaxWidth(CONTENT_MAX_WIDTH);
        column.setGap(10);

        SceneNode header = SceneNode.column();
        header.setFillParentWidth(true);
        header.setGap(2);
        header.setHitTestable(false);
        header.appendChild(PlaygroundKit.text("磨玻璃实验室（backdrop-filter）", PlaygroundKit.TEXT, 22));
        header.appendChild(PlaygroundKit.text(
                "玻璃面板采样其下已绘制的 scene 内容；拖动滑杆即时调参，验证仿 iOS 磨玻璃观感",
                PlaygroundKit.MUTED, 12));
        header.setPreferredHeight(measurer.lineHeight(22) + 2 + measurer.lineHeight(12));
        column.appendChild(header);

        column.appendChild(buildStage());
        column.appendChild(buildControls());
        column.appendChild(buildProbeCard());
        column.appendChild(buildDiagnostics());

        shell.appendChild(column);
        return shell;
    }

    private SceneNode buildStage() {
        stage = SceneNode.column();
        stage.setFillParentWidth(true);
        stage.setGap(8);
        stage.setPadding(10);
        stage.setPreferredHeight(STAGE_HEIGHT);
        stage.setBackgroundColor(PlaygroundKit.PANEL_BG);
        stage.setBorderWidth(1);
        stage.setBorderColor(PlaygroundKit.BORDER);
        stage.setCornerRadius(14);
        stage.appendChild(PlaygroundKit.title("采样场（玻璃面板覆盖此区域上半部）"));
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            SceneNode band = SceneNode.row(6);
            band.setFillParentWidth(true);
            band.setPreferredHeight(34);
            band.setHitTestable(false);
            for (int i = 0; i < SAMPLE_COLORS.length; i++) {
                SceneNode chip = new SceneNode();
                chip.setFlexGrow(1);
                chip.setFillParentHeight(true);
                chip.setBackgroundColor(SAMPLE_COLORS[(rowIndex * 5 + i) % SAMPLE_COLORS.length]);
                chip.setCornerRadius(6);
                chip.setHitTestable(false);
                band.appendChild(chip);
            }
            stage.appendChild(band);
        }
        stage.appendChild(PlaygroundKit.hint(
                "模糊正确的判据：色带边界在玻璃下应连续柔化（高斯散开），而不是整体平移或重影；"
                        + "细密文字应仍可辨形但失去锐度。若玻璃区域出现明显过曝发白，记录为核能量异常。"));
        stage.appendChild(PlaygroundKit.hint(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789 abcdefghijklmnopqrstuvwxyz 你好世界 磨玻璃测试"));
        return stage;
    }

    private SceneNode buildControls() {
        SceneNode card = PlaygroundKit.card();
        card.appendChild(PlaygroundKit.title("参数台"));
        card.appendChild(sliderRow(blurTextSignal, blurSignal, "模糊半径 ", "", 0.0D, BLUR_MAX, 1.0D,
                value -> String.valueOf(Math.round(value))));
        card.appendChild(sliderRow(saturationTextSignal, saturationSignal, "饱和度 ", "%", 0.0D,
                SATURATION_MAX, 5.0D, value -> String.valueOf(Math.round(value))));
        card.appendChild(sliderRow(radiusTextSignal, radiusSignal, "圆角 ", "", 0.0D, RADIUS_MAX, 1.0D,
                value -> String.valueOf(Math.round(value))));

        SceneNode switchRow = SceneNode.row(18);
        switchRow.setFillParentWidth(true);
        switchRow.setHitTestable(false);
        runtime.mount(switchRow, SceneToggle.create(runtime, new SceneToggle.Props(
                followSignal, Signal.create("鼠标跟随玻璃"), Signal.create(Boolean.TRUE),
                next -> followSignal.set(next))));
        runtime.mount(switchRow, SceneToggle.create(runtime, new SceneToggle.Props(
                frozenSignal, Signal.create("暂停玻璃（A/B 对比）"), Signal.create(Boolean.TRUE),
                next -> frozenSignal.set(next))));
        card.appendChild(switchRow);
        return card;
    }

    private SceneNode sliderRow(final Signal<String> labelText, final Signal<Double> valueSignal,
            String prefix, String suffix, double min, double max, double step,
            SliderFormatter formatter) {
        SceneNode row = SceneNode.row(10);
        row.setFillParentWidth(true);
        row.setHitTestable(false);
        SceneNode label = runtime.mount(row, SceneLabel.create(runtime,
                new SceneLabel.Props(labelText, PlaygroundKit.TEXT, 13))).getRoot();
        if (label != null) {
            label.setPreferredWidth(120);
        }
        SceneNode sliderRoot = runtime.mount(row, SceneSlider.create(runtime, SceneSlider.Props
                .builder(valueSignal)
                .min(min)
                .max(max)
                .step(step)
                .onChange((value, committing) -> {
                    valueSignal.set(Double.valueOf(value));
                    labelText.set(prefix + formatter.format(value) + suffix);
                })
                .build())).getRoot();
        if (sliderRoot != null) {
            sliderRoot.setFlexGrow(1);
        }
        return row;
    }

    private SceneNode buildProbeCard() {
        probeCard = SceneNode.column();
        probeCard.setFillParentWidth(true);
        probeCard.setPadding(10);
        probeCard.setGap(4);
        probeCard.setPreferredHeight(PROBE_CARD_HEIGHT);
        probeCard.setBackgroundColor(PlaygroundKit.PANEL_BG);
        probeCard.setBorderWidth(1);
        probeCard.setBorderColor(PlaygroundKit.BORDER);
        probeCard.setCornerRadius(14);
        probeCard.setClipChildren(true);
        probeCard.appendChild(PlaygroundKit.strongHint(
                "探针玻璃带（卡内顶部 56px）：其下文字必须被采样模糊——若清晰穿透说明快照未含本帧内容"));
        probeCard.appendChild(PlaygroundKit.hint(
                "The quick brown fox jumps over the lazy dog 0123456789 混排文本探针"));
        return probeCard;
    }

    private SceneNode buildDiagnostics() {
        SceneNode card = PlaygroundKit.card();
        SceneNode pathText = PlaygroundKit.strongHint("");
        card.appendChild(pathText);
        runtime.bindText(pathText, pathSignal);
        return card;
    }

    private static int clampToInt(Double value, int min, int max) {
        double raw = value == null ? 0.0D : value.doubleValue();
        return (int) Math.max(min, Math.min(max, Math.round(raw)));
    }

    /** 滑杆值格式化（避免 lambda 捕获格式串重复）。 */
    private interface SliderFormatter {
        String format(double value);
    }
}
