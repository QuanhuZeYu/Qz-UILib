package club.heiqi.uilib.ui.scene.host;

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
 * （屏幕级虚拟窗口宿主）共用本装配语义；measurer 可注入（测试传自定义端口），生产默认走
 * {@link #defaultMeasurer()} 全仓唯一度量装配点。inputSource 可为 null（无输入退化模式）。
 * 宿主专属钩子（cursor/clipboard 绑定、构造期物化）留在宿主侧，不进水工厂。</p>
 */
public final class SceneHostAssembly {

    private SceneHostAssembly() {
    }

    /** 生产默认文本度量适配器：全仓唯一 {@code DefaultTextMeasureService} → scene 端口装配点。 */
    public static SceneTextMeasurer defaultMeasurer() {
        return new TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance());
    }

    /**
     * 装配一套宿主管线（各组件绑定同一 measurer，度量口径单源）。
     *
     * @param measurer    文本度量端口，不可为 null
     * @param inputSource 平台输入源，可为 null（无输入退化模式）
     * @return 自洽装配包
     */
    public static Bundle assemble(SceneTextMeasurer measurer, PlatformInputSource inputSource) {
        if (measurer == null) {
            throw new IllegalArgumentException("measurer must not be null");
        }
        SceneRuntime runtime = new SceneRuntime(measurer);
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
