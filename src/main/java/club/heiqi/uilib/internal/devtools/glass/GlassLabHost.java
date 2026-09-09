package club.heiqi.uilib.internal.devtools.glass;

import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdropEffect;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.render.UiRenderBackends;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneSlider;
import club.heiqi.uilib.ui.scene.control.SceneToggle;
import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;
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
 *
 * <h3>外观归属（G18 外壳迁移）</h3>
 * <p>采样场面板、探针卡与参数/诊断卡的 background/border/borderWidth/cornerRadius/backdrop/
 * surfaceElevation 唯一写入者是 {@link SceneSurfaceBinder}，配方取来源主题的 PANEL/GROUP 角色；
 * 标题/说明/滑杆标签前景经 {@link SceneThemes#foreground}/{@link SceneThemes#mutedForeground}
 * 与 {@link SceneLabel} 主题跟随路径派生，随主题切换重算、不重建节点。场景根 {@code shell} 的
 * {@link PlaygroundKit#ROOT_BG} 是<b>全屏不透明（0xFF）诊断承托底</b>——作用同 G15/Shell 的
 * 世界遮罩（为玻璃采样提供稳定高对比基色，若随主题改透明度会让三档对照基线漂移），故按 §7.3
 * 「遮罩只负责遮罩」口径保留显式静态、不装玻璃配方。</p>
 *
 * <p><b>实验样本保持显式、不受主题影响</b：采样场色带 {@link #SAMPLE_COLORS}、圆角，材质阶梯
 * {@link #MATERIAL_LADDER}，玻璃 tint/亮边 {@link #GLASS_TINT}/{@link #GLASS_EDGE} 与被滑杆
 * 驱动的 blur/饱和/透镜/滤镜参数是实验室的被测对象，全部保留显式取值（契约 §7.2「样本区保留
 * classic/liquid/关闭滤镜对照」），主题更新不覆盖这些显式值。</p>
 *
 * <p><b>自建等效接缝</b>：{@code PlaygroundKit.installRuntime} 与 {@code RUNTIME_KEY} 是
 * playground 包级私有接缝，本宿主（glass 包）无法登记 runtime，故 {@code PlaygroundKit} 无
 * {@code rt} 形参的构件在此只会走静态回退。外壳因此直接复用 theme 包公开 API
 * （{@link SceneSurfaceBinder}/{@link SceneThemes}）与 {@code PlaygroundKit} 的公开主题前景
 * 文本重载装配，不复制 PlaygroundKit 源码、不引入第二真相源。</p>
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
    /** 材质档序列：旧语义置顶，便于从左到右逐级对比"糊一层彩"与 iOS 材质的差距；
     *  末位为 Liquid Glass（以 REGULAR 为底 + 透镜/随动缘光，强度由"液态强度"滑杆控制）。 */
    private static final UiGlassMaterial[] MATERIAL_LADDER = {
            null,
            UiGlassMaterial.ULTRA_THIN,
            UiGlassMaterial.THIN,
            UiGlassMaterial.REGULAR,
            UiGlassMaterial.THICK,
            UiGlassMaterial.DARK_ULTRA_THIN,
            UiGlassMaterial.DARK_THIN,
            UiGlassMaterial.DARK_REGULAR,
            UiGlassMaterial.DARK_THICK,
    };
    /** 液态玻璃档序号（滑杆末位）。 */
    private static final int LIQUID_INDEX = 9;
    /** 玻璃面高透白（旧语义兜底基调）。 */
    private static final int GLASS_TINT = 0x26FFFFFF;
    /** 玻璃描边（顶部渐亮近似）。 */
    private static final int GLASS_EDGE = 0x66FFFFFF;
    /** 采样场色带（高饱和，模糊后差异最直观）。 */
    private static final int[] SAMPLE_COLORS = {
            0xFFEC4899, 0xFF38BDF8, 0xFFFBBF24, 0xFF22C55E, 0xFFA855F7,
            0xFFF97316, 0xFF06B6D4, 0xFF84CC16, 0xFFEF4444, 0xFF6366F1,
    };

    /** 外壳表面 enabled：外壳/卡片表面不可禁用（表面绑定器只关心恒真）。 */
    private static final ReadableSignal<Boolean> SHELL_ENABLED = () -> Boolean.TRUE;

    /** runtime 默认主题信号：外壳表面与主题前景文字的共同来源，可整体切换。 */
    private final Signal<SceneTheme> themeSignal = Signal.create(SceneThemes.DEFAULT);
    /** 根节点。 */
    private final SceneNode root;
    /** 采样场色带 chip（§7.3 显式材质样本，构建期登记供反向钉断言）。 */
    private final java.util.List<SceneNode> sampleChips = new java.util.ArrayList<SceneNode>();
    /** 采样场节点（玻璃面板位置以其 SceneGeometry.absoluteBox 绝对盒为基准）。 */
    private SceneNode stage;
    /** 探针玻璃卡节点（卡内顶部玻璃带验证快照含本帧内容）。 */
    private SceneNode probeCard;
    /** 标题区节点（主/副标题）。 */
    private SceneNode header;
    /** 参数台卡片节点。 */
    private SceneNode controlsCard;
    /** 诊断卡片节点。 */
    private SceneNode diagnosticsCard;
    /** 实际渲染路径诊断文本。 */
    private final Signal<String> pathSignal = Signal.create("backdrop 路径: 等待首帧");
    /** 模糊半径（UI 像素，受控源）。 */
    private final Signal<Double> blurSignal = Signal.create(18.0D);
    /** 饱和度百分比（受控源）。材质档下为 vibrancy 乘子，100% = 严格采用材质配方值。 */
    private final Signal<Double> saturationSignal = Signal.create(100.0D);
    /** 当前材质档序号（0 = 旧线性饱和度语义；双精度以复用 sliderRow）。 */
    private final Signal<Double> materialIndexSignal = Signal.create(Double.valueOf(3.0D));
    /** 液态强度百分比（仅 Liquid Glass 档生效；受控源）。 */
    private final Signal<Double> lensStrengthSignal = Signal.create(60.0D);
    /** 液态强度文本。 */
    private final Signal<String> lensTextSignal = Signal.create("液态强度 60%");
    /** 材质档名称文本。 */
    private final Signal<String> materialTextSignal = Signal.create(describeMaterial(3));
    /** 圆角半径（UI 像素，受控源）。 */
    private final Signal<Double> radiusSignal = Signal.create(16.0D);
    /** 鼠标跟随玻璃开关。 */
    private final Signal<Boolean> followSignal = Signal.create(Boolean.TRUE);
    /** 暂停玻璃回贴开关（A/B 对比）。 */
    private final Signal<Boolean> frozenSignal = Signal.create(Boolean.FALSE);
    /**
     * 本帧请求后端做 backdrop 滤镜的矩形列表（每项 l,t,r,b）。
     *
     * <p>材质档生效时玻璃质感全在 shader 内合成，宿主不再叠 tint 面——headless 下
     * 滤镜请求本身没有可观测副作用。本字段把"宿主算出来的面板矩形"显式暴露为断言锚点，
     * 避免几何回归测试再次退化成只能靠伴随绘制物间接观察（2026-09-01 坐标根因的教训）。</p>
     */
    private final java.util.List<int[]> backdropRectsThisFrame = new java.util.ArrayList<int[]>();
    /** 滑杆值文本（受控源联动）。 */
    private final Signal<String> blurTextSignal = Signal.create("模糊半径 18");
    private final Signal<String> saturationTextSignal = Signal.create("饱和度 100%");
    private final Signal<String> radiusTextSignal = Signal.create("圆角 16");

    /**
     * 创建磨玻璃实验室宿主。
     *
     * @param input 平台输入源，可为 null（headless 测试退化模式）
     */
    public GlassLabHost(PlatformInputSource input) {
        super(input);
        this.root = buildThemedTree();
    }

    /**
     * measurer 可注入构造（headless 测试传入确定度量端口，同宿主基类 A4 口径）。
     *
     * @param measurer 文本度量端口
     * @param input    平台输入源，可为 null
     */
    public GlassLabHost(SceneTextMeasurer measurer, PlatformInputSource input) {
        super(measurer, input);
        this.root = buildThemedTree();
    }

    /**
     * 主题接线 + 建树（G16/外壳先例同型）：runtime 默认主题先于建树安装；外壳构建整体包进
     * {@link SceneRuntime#__runRoot(Runnable)}——构造期没有当前 Owner 时，主题解析与
     * bindComputed/SceneSurfaceBinder 创建的 Computed/Effect 不归属任何作用域、卸载无法回收
     * （P-01 effect 泄漏）。
     *
     * @return 场景树根节点
     */
    private SceneNode buildThemedTree() {
        runtime.__enableMotion();
        // 主题来源先于建树安装：外壳表面与工具控件都从 runtime 根作用域继承同一份主题信号。
        SceneThemes.install(runtime, themeSignal);
        final SceneNode[] holder = new SceneNode[1];
        runtime.__runRoot(() -> holder[0] = buildTree());
        return holder[0];
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
        backdropRectsThisFrame.clear();
        if (frozenSignal.get().booleanValue()) {
            pathSignal.set("backdrop 路径: 已暂停（玻璃回贴冻结）");
            return;
        }
        int blur = clampToInt(blurSignal.get(), 0, BLUR_MAX);
        int radius = clampToInt(radiusSignal.get(), 0, RADIUS_MAX);
        float saturation = clampToInt(saturationSignal.get(), 0, SATURATION_MAX) / 100.0F;
        int materialIndex = clampToInt(materialIndexSignal.get(), 0, LIQUID_INDEX);
        UiGlassMaterial material = materialAt(materialIndex);
        float lensStrength = clampToInt(lensStrengthSignal.get(), 0, 100) / 100.0F;
        // 末档 = Liquid Glass：以 REGULAR 为底材质 + 透镜/厚度 tint/随动缘光
        UiBackdropEffect effect = materialIndex == LIQUID_INDEX
                ? UiBackdropEffect.liquidGlass(UiGlassMaterial.REGULAR, lensStrength)
                : UiBackdropEffect.classic(material);

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
                    blur, saturation, radius, effect);
            backdropRectsThisFrame.add(new int[] { panelLeft, panelTop, panelRight, panelBottom });
            if (!effect.carriesMaterialTint()) {
                // 旧语义没有 shader 侧 tint/亮边，仍由宿主补一层白面。
                ctx.drawSurface(panelLeft, panelTop, panelRight, panelBottom, GLASS_TINT, GLASS_EDGE, radius);
            }

            // 鼠标跟随玻璃：需要后端报告指针位置（仅 MC 平台上下文支持）。
            if (followSignal.get().booleanValue() && ctx instanceof UiRenderContext) {
                UiRenderContext context = (UiRenderContext) ctx;
                int pointerX = context.getMouseX();
                int pointerY = context.getMouseY();
                if (pointerX >= stageBox.getX() && pointerX <= stageBox.getX() + stageBox.getWidth()
                        && pointerY >= stageBox.getY() && pointerY <= stageBox.getY() + stageBox.getHeight()) {
                    int followRadius = Math.max(radius, FOLLOW_HALF_HEIGHT);
                    UiRenderBackends.backdropFilter(ctx, pointerX - FOLLOW_HALF_WIDTH, pointerY - FOLLOW_HALF_HEIGHT,
                            pointerX + FOLLOW_HALF_WIDTH, pointerY + FOLLOW_HALF_HEIGHT, blur, saturation,
                            followRadius, effect);
                    backdropRectsThisFrame.add(new int[] { pointerX - FOLLOW_HALF_WIDTH,
                            pointerY - FOLLOW_HALF_HEIGHT, pointerX + FOLLOW_HALF_WIDTH,
                            pointerY + FOLLOW_HALF_HEIGHT });
                    if (!effect.carriesMaterialTint()) {
                        ctx.drawSurface(pointerX - FOLLOW_HALF_WIDTH, pointerY - FOLLOW_HALF_HEIGHT,
                                pointerX + FOLLOW_HALF_WIDTH, pointerY + FOLLOW_HALF_HEIGHT, GLASS_TINT, GLASS_EDGE,
                                followRadius);
                    }
                }
            }
        }

        AnchorRect probeBox = SceneGeometry.absoluteBox(probeCard, absX, absY);
        if (probeBox.getWidth() > 0 && probeBox.getHeight() > 0) {
            // 探针玻璃：固定在卡内顶部 56px 带，观察其下文字是否被采样模糊。
            int probeLeft = probeBox.getX() + 8;
            int probeTop = probeBox.getY() + 8;
            int probeRight = probeBox.getX() + probeBox.getWidth() - 8;
            int probeBottom = probeBox.getY() + 64;
            UiRenderBackends.backdropFilter(ctx, probeLeft, probeTop, probeRight, probeBottom, blur, saturation,
                    radius, effect);
            backdropRectsThisFrame.add(new int[] { probeLeft, probeTop, probeRight, probeBottom });
            if (!effect.carriesMaterialTint()) {
                ctx.drawSurface(probeLeft, probeTop, probeRight, probeBottom, GLASS_TINT, GLASS_EDGE, radius);
            }
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

    /** 测试访问器：标题区节点（主/副标题主题前景断言用）。 */
    SceneNode __getHeader() {
        return header;
    }

    /** 测试访问器：探针卡节点（GROUP 配方断言用）。 */
    SceneNode __getProbeCard() {
        return probeCard;
    }

    /** 测试访问器：参数台卡片节点（GROUP 配方断言用）。 */
    SceneNode __getControlsCard() {
        return controlsCard;
    }

    /** 测试访问器：诊断卡片节点（GROUP 配方断言用）。 */
    SceneNode __getDiagnosticsCard() {
        return diagnosticsCard;
    }

    /** 测试访问器：暂停玻璃开关（A/B 冻结态受控源）。 */
    Signal<Boolean> __getFrozenSignal() {
        return frozenSignal;
    }

    /** 测试访问器：模糊半径受控源。 */
    Signal<Double> __getBlurSignal() {
        return blurSignal;
    }

    /** 测试访问器：本帧请求 backdrop 滤镜的矩形列表（几何/坐标锁锚点）。 */
    java.util.List<int[]> __getBackdropRects() {
        return backdropRectsThisFrame;
    }

    /** 测试访问器：当前材质档受控源。 */
    Signal<Double> __getMaterialIndexSignal() {
        return materialIndexSignal;
    }

    /** 测试访问器：液态强度受控源。 */
    Signal<Double> __getLensStrengthSignal() {
        return lensStrengthSignal;
    }

    /** 测试访问器：渲染路径诊断文本源。 */
    Signal<String> __getPathSignal() {
        return pathSignal;
    }

    /** 测试访问器：runtime 默认主题信号（G16/外壳同型探针；测试 set(另一主题)+flush 验证外壳随主题更新）。 */
    Signal<SceneTheme> __getThemeSignal() {
        return themeSignal;
    }

    /** 测试访问器：采样场色带 chip 的底色逐值快照（§7.3 显式样本「换主题→不变」反向钉用）。 */
    int[] __getSampleChipColors() {
        int[] out = new int[sampleChips.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = sampleChips.get(i).getBackgroundColor();
        }
        return out;
    }

    private SceneNode buildTree() {
        SceneNode shell = SceneNode.column();
        shell.setFillParentWidth(true);
        shell.setFillParentHeight(true);
        shell.setCrossAxisAlign(CrossAxisAlign.CENTER);
        shell.setPadding(12);
        shell.setGap(10);
        // 场景根保留显式承托底（§7.3 遮罩口径，对照 G15/Shell「遮罩与玻璃分层」裁决）：
        // 全屏不透明暗底为玻璃面板提供稳定的采样基色，作用同世界遮罩——只负责承托/掩蔽，
        // 不装玻璃配方；若改为主题半透明表面，三档对照的采样基底会随主题漂移，实验不可比。
        shell.setBackgroundColor(PlaygroundKit.ROOT_BG);

        SceneNode column = SceneNode.column();
        column.setFillParentWidth(true);
        column.setMaxWidth(CONTENT_MAX_WIDTH);
        column.setGap(10);

        header = SceneNode.column();
        header.setFillParentWidth(true);
        header.setGap(2);
        header.setHitTestable(false);
        // 主/副标题走公开主题前景构件（PlaygroundKit.text(rt, …) 公共重载）：
        // 构建期不读值，主题切换只重派生、不重建节点。旧 PlaygroundKit.TEXT/MUTED 静态取色已删。
        header.appendChild(PlaygroundKit.text(runtime, "磨玻璃实验室（backdrop-filter）",
                SceneThemes.foreground(runtime), 22));
        header.appendChild(PlaygroundKit.text(runtime,
                "玻璃面板采样其下已绘制的 scene 内容；拖动滑杆即时调参，验证仿 iOS 磨玻璃观感",
                SceneThemes.mutedForeground(runtime), 12));
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
        // 采样场主面板：来源主题 PANEL 配方唯一写入者（旧 PANEL_BG/边框宽/边框色/圆角静态写入者已删；
        // 布局属性 padding/gap/高度保持不动，主题不接管布局）。
        bindLabSurface(stage, SceneTheme.Role.PANEL);
        stage.appendChild(PlaygroundKit.text(runtime,
                "采样场（玻璃面板覆盖此区域上半部）", SceneThemes.foreground(runtime), 16));
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            SceneNode band = SceneNode.row(6);
            band.setFillParentWidth(true);
            band.setPreferredHeight(34);
            band.setHitTestable(false);
            for (int i = 0; i < SAMPLE_COLORS.length; i++) {
                SceneNode chip = new SceneNode();
                chip.setFlexGrow(1);
                chip.setFillParentHeight(true);
                // §7.3 诊断材质样本保留：高饱和显式色带是玻璃滤镜的被采样对象，
                // 主题更新不得改样本色（反向钉用例 GlassLabHostThemeTest 逐值锁）。
                chip.setBackgroundColor(SAMPLE_COLORS[(rowIndex * 5 + i) % SAMPLE_COLORS.length]);
                chip.setCornerRadius(6);
                chip.setHitTestable(false);
                sampleChips.add(chip);
                band.appendChild(chip);
            }
            stage.appendChild(band);
        }
        stage.appendChild(PlaygroundKit.text(runtime,
                "模糊正确的判据：色带边界在玻璃下应连续柔化（高斯散开），而不是整体平移或重影；"
                        + "细密文字应仍可辨形但失去锐度。若玻璃区域出现明显过曝发白，记录为核能量异常。",
                SceneThemes.mutedForeground(runtime), 12));
        stage.appendChild(PlaygroundKit.text(runtime,
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789 abcdefghijklmnopqrstuvwxyz 你好世界 磨玻璃测试",
                SceneThemes.mutedForeground(runtime), 12));
        return stage;
    }

    private SceneNode buildControls() {
        controlsCard = buildLabCard();
        SceneNode card = controlsCard;
        card.appendChild(PlaygroundKit.text(runtime, "参数台", SceneThemes.foreground(runtime), 16));
        card.appendChild(PlaygroundKit.text(runtime,
                "材质档非「旧语义」时质感由 shader 合成（vibrancy / tint / 亮边 / 噪点），此时饱和度滑杆是 vibrancy 乘子：100%=配方原值，>100 更艳，<100 更哑",
                SceneThemes.mutedForeground(runtime), 12));
        card.appendChild(PlaygroundKit.text(runtime,
                "末档 LiquidGlass：在 REGULAR 底材上叠加边缘透镜折射 + 厚度 tint + 随动缘光；拖「液态强度」看缘带弯折，移鼠标看高光沿边缘游走",
                SceneThemes.mutedForeground(runtime), 12));
        card.appendChild(sliderRow(blurTextSignal, blurSignal, "模糊半径 ", "", 0.0D, BLUR_MAX, 1.0D,
                value -> String.valueOf(Math.round(value))));
        card.appendChild(sliderRow(saturationTextSignal, saturationSignal, "饱和度 ", "%", 0.0D,
                SATURATION_MAX, 5.0D, value -> String.valueOf(Math.round(value))));
        card.appendChild(sliderRow(radiusTextSignal, radiusSignal, "圆角 ", "", 0.0D, RADIUS_MAX, 1.0D,
                value -> String.valueOf(Math.round(value))));
        card.appendChild(sliderRow(materialTextSignal, materialIndexSignal, "材质 ", "", 0.0D,
                LIQUID_INDEX, 1.0D,
                value -> describeMaterial((int) Math.round(value))));
        card.appendChild(sliderRow(lensTextSignal, lensStrengthSignal, "液态强度 ", "%", 0.0D, 100.0D, 5.0D,
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
        // 滑杆标签走 SceneLabel 主题跟随默认路径（不调 color(…) 即 followTheme=true）：
        // 前景取来源主题正文色，字号保持 13 不回归。旧显式 PlaygroundKit.TEXT 取色已删。
        SceneNode label = runtime.mount(row, SceneLabel.create(runtime,
                SceneLabel.Props.builder(labelText).fontSizePx(13).build())).getRoot();
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
        // 探针卡底座：来源主题 GROUP 配方唯一写入者（旧 PANEL_BG/边框/圆角静态写入者已删；
        // clipChildren/padding/gap/高度是布局与实验裁剪行为，保持不动）。
        bindLabSurface(probeCard, SceneTheme.Role.GROUP);
        probeCard.setClipChildren(true);
        probeCard.appendChild(PlaygroundKit.text(runtime,
                "探针玻璃带（卡内顶部 56px）：其下文字必须被采样模糊——若清晰穿透说明快照未含本帧内容",
                SceneThemes.foreground(runtime), 12));
        probeCard.appendChild(PlaygroundKit.text(runtime,
                "The quick brown fox jumps over the lazy dog 0123456789 混排文本探针",
                SceneThemes.mutedForeground(runtime), 12));
        return probeCard;
    }

    private SceneNode buildDiagnostics() {
        diagnosticsCard = buildLabCard();
        SceneNode card = diagnosticsCard;
        SceneNode pathText = PlaygroundKit.text(runtime, "", SceneThemes.foreground(runtime), 12);
        card.appendChild(pathText);
        runtime.bindText(pathText, pathSignal);
        return card;
    }

    /**
     * 自建等效接缝（非复制 PlaygroundKit）：创建参数/诊断卡底座布局容器，表面配方由
     * {@link #bindLabSurface} 交给 {@link SceneSurfaceBinder} 取来源主题 GROUP 角色。
     *
     * <p>{@code PlaygroundKit.card()} 的主题路径依赖其包级私有 {@code RUNTIME_KEY} Owner 接缝
     * （{@code installRuntime} 同为 playground 包级），本 glass 宿主无法登记 runtime，故
     * {@code PlaygroundKit.card()} 在此只会走「无宿主上下文」静态回退。为避免复制 PlaygroundKit
     * 源码（双真相源，禁止），这里只复用 theme 包公开 API 装配一张跟随主题的卡片。</p>
     *
     * @return 卡片根节点（COLUMN）
     */
    private SceneNode buildLabCard() {
        SceneNode card = SceneNode.column();
        card.setFillParentWidth(true);
        card.setMaxWidth(CONTENT_MAX_WIDTH);
        card.setPadding(SceneChromeTokens.PAD_LG);
        card.setGap(SceneChromeTokens.GAP_MD);
        // 卡片非交互单元（交互在子控件上），退出叶命中目标资格，避免整卡随指针变色。
        card.setHitTestable(false);
        bindLabSurface(card, SceneTheme.Role.GROUP);
        return card;
    }

    /**
     * 绑定实验室表面：配方从来源主题解析（构建期在 rootOwner 作用域内，取宿主 runtime 默认主题），
     * background/border/borderWidth/cornerRadius/backdrop/surfaceElevation 唯一写入者是
     * {@link SceneSurfaceBinder}。
     *
     * @param node 表面节点
     * @param role 材质角色
     */
    private void bindLabSurface(SceneNode node, SceneTheme.Role role) {
        SceneInteractionState interaction = runtime.interactionState(node);
        // 时序契约：Router 的 writeHovered/writePressed/writeFocused 对未创建的 signal 短路，
        // 构建期先声明关心（这些容器非命中目标，实际写入恒 FALSE，配方停在 idle 档）。
        interaction.hovered();
        interaction.pressed();
        interaction.focused();
        SceneSurfaceBinder.bind(runtime, node, SceneThemes.surface(runtime, role), SHELL_ENABLED, interaction);
    }

    /** 按序号取材质档；越界、0 与液态档（以 REGULAR 为底，材质位表达在 describeMaterial）返回其底档或 null。 */
    private static UiGlassMaterial materialAt(int index) {
        if (index <= 0 || index >= MATERIAL_LADDER.length || index == LIQUID_INDEX) {
            return null;
        }
        return MATERIAL_LADDER[index];
    }

    /** 材质档滑杆文案：0=旧语义，1~8=经典档枚举名，末位=Liquid Glass。 */
    private static String describeMaterial(int index) {
        if (index == LIQUID_INDEX) {
            return "LiquidGlass(底REGULAR)";
        }
        UiGlassMaterial material = materialAt(index);
        return material == null ? "旧语义(线性饱和)" : material.name();
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