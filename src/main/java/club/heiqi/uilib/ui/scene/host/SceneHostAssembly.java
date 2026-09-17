package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.env.ProcessUiEnvironment;
import club.heiqi.uilib.ui.env.UiEnvironment;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextMeasureServiceSceneAdapter;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;

/**
 * scene 宿主五件套唯一装配点（投放职责聚合方案 A4）：runtime / layoutEngine / paintEngine /
 * replayer / pipeline 一次装配成自洽 {@link Bundle}，杜绝各宿主手搭第二份。
 *
 * <p>{@link AbstractSceneHostWidget}（UI 页面宿主）与 {@code client.hud.SceneHudHost.RetainedWindow}
 * （屏幕级虚拟窗口宿主）共用本装配语义；measurer 与环境端口均可注入（headless / 测试传确定实现），
 * 生产默认走 {@link #defaultMeasurer()} 与 {@link #defaultEnvironment()} 两个全仓唯一装配点。
 * inputSource 可为 null（无输入退化模式）。宿主专属钩子（cursor/clipboard 绑定、构造期物化）
 * 留在宿主侧，不进水工厂。</p>
 */
public final class SceneHostAssembly {

    private SceneHostAssembly() {
    }

    /** 生产默认文本度量适配器：全仓唯一 {@code DefaultTextMeasureService} → scene 端口装配点。 */
    public static SceneTextMeasurer defaultMeasurer() {
        return new TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance());
    }

    /**
     * 生产默认环境端口：把 UILib 既有进程级环境量（{@code Config.useDebug}、语言代际、资源代际）
     * 适配进 {@link UiEnvironment} 的唯一装配点。
     *
     * <p>与 {@link #defaultMeasurer()} 对称：宿主不自定义环境时取此实现。返回的适配器是
     * <b>进程单例</b>而非可复制的无状态对象 —— 诊断域持有调试浮层的订阅源，复制实例会让订阅通道
     * 分裂（派生方订阅到的那条可能永远不更新），故不得自行 new。</p>
     *
     * @return 生产环境端口
     */
    public static UiEnvironment defaultEnvironment() {
        return ProcessUiEnvironment.INSTANCE;
    }

    /**
     * 装配一套宿主管线（各组件绑定同一 measurer，度量口径单源；环境端口注入 runtime）。
     *
     * @param measurer    文本度量端口，不可为 null
     * @param inputSource 平台输入源，可为 null（无输入退化模式）
     * @param environment 宿主环境端口，不可为 null；无环境事实传 {@link UiEnvironment#empty()}
     * @return 自洽装配包
     */
    public static Bundle assemble(SceneTextMeasurer measurer, PlatformInputSource inputSource,
            UiEnvironment environment) {
        if (measurer == null) {
            throw new IllegalArgumentException("measurer must not be null");
        }
        if (environment == null) {
            throw new IllegalArgumentException(
                    "environment must not be null；无环境事实请传 UiEnvironment.empty()");
        }
        SceneRuntime runtime = new SceneRuntime(measurer, environment);
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
        ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);
        ScenePaintReplayer replayer = new ScenePaintReplayer();
        SceneFramePipeline pipeline = new SceneFramePipeline(runtime, layoutEngine, paintEngine,
                replayer, measurer, inputSource);
        return new Bundle(measurer, runtime, layoutEngine, paintEngine, replayer, pipeline);
    }

    /**
     * 宿主装配的<b>唯一口径</b>：把一棵已构建的树交给指定 runtime
     * （写树根字号环境引用 + 登记环境根，供层 3 默认字号与解析出口倍率层使用）。
     *
     * <p>为什么必须走这一处：宿主的建树路径不止 mount —— HUD 虚拟窗口在
     * {@code SceneHudHost.RetainedWindow} 里自建 runtime、直接建 shell 并
     * {@code root.appendChild(layer.root())} 后交给 {@code layoutEngine.layout(...)}，
     * 完全不经过 {@code SceneRuntime.mount}。若把环境写入散落在各宿主，
     * 「新宿主漏挂环境」将无法被守卫枚举；收敛到本方法后，守卫只需钉
     * 「除 SceneHostAssembly 外不得直接调 SceneNode.__setFontEnvironment」
     * 加「各装配路径必须出现 attachTree」。</p>
     *
     * <p>环境引用随<b>树根装配</b>设置（不随节点构造设置）：摘除即父链断开、环境自然失效；
     * 重挂由新宿主的本方法重设或沿新父链继承，故不存在跨 runtime 陈旧。</p>
     *
     * @param runtime 目标 runtime，不可为 null
     * @param root    已构建的树根；null = no-op
     * @throws IllegalArgumentException runtime 为 null
     */
    public static void attachTree(SceneRuntime runtime, SceneNode root) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        runtime.__adoptFontEnvironmentRoot(root);
        // 寻址身份与广播身份分开登记：本方法是「宿主装配」的唯一口径，因此也是「这是一棵独立的树」
        // 的权威声明点。用几何判据（无父）反推装配根会把卸载后的 show 内容根也当成树根（独立复核实测）。
        runtime.__registerAssemblyRoot(root);
    }

    /**
     * 摘除环境根登记（宿主卸载时调用，与 {@link #attachTree} 成对）。
     *
     * @param runtime 目标 runtime；null = no-op
     * @param root    已卸载的树根；null = no-op
     */
    public static void detachTree(SceneRuntime runtime, SceneNode root) {
        if (runtime == null || root == null) {
            return;
        }
        runtime.__releaseFontEnvironmentRoot(root);
        runtime.__unregisterAssemblyRoot(root);
        // 清掉树根的环境引用：后代沿父链只看树根，因此清一处即整树脱离该 runtime，
        // 杜绝「已卸载的树仍指向旧 runtime」的跨 runtime 陈旧。
        if (root.__getFontEnvironment() == runtime) {
            root.__setFontEnvironment(null);
        }
    }

    /** 装配产物：五件套 + 共用 measurer，全部不可变。 */
    public static final class Bundle {
        private final SceneTextMeasurer measurer;
        private final SceneRuntime runtime;
        private final SceneLayoutEngine layoutEngine;
        private final ScenePaintEngine paintEngine;
        private final ScenePaintReplayer replayer;
        private final SceneFramePipeline pipeline;

        private Bundle(SceneTextMeasurer measurer, SceneRuntime runtime, SceneLayoutEngine layoutEngine,
                ScenePaintEngine paintEngine, ScenePaintReplayer replayer, SceneFramePipeline pipeline) {
            this.measurer = measurer;
            this.runtime = runtime;
            this.layoutEngine = layoutEngine;
            this.paintEngine = paintEngine;
            this.replayer = replayer;
            this.pipeline = pipeline;
        }

        /** @return 度量端口（宿主侧组件共用） */
        public SceneTextMeasurer getMeasurer() { return measurer; }
        /** @return 场景运行时 */
        public SceneRuntime getRuntime() { return runtime; }
        /** @return 主树布局引擎 */
        public SceneLayoutEngine getLayoutEngine() { return layoutEngine; }
        /** @return 绘制计划生成器 */
        public ScenePaintEngine getPaintEngine() { return paintEngine; }
        /** @return 绘制计划回放器 */
        public ScenePaintReplayer getReplayer() { return replayer; }
        /** @return 帧管线 */
        public SceneFramePipeline getPipeline() { return pipeline; }
    }
}
