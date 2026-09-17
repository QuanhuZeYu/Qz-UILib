package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.env.UiEnvironment;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.function.Consumer;

/**
 * 保留式 scene 宿主窗口：把一棵由调用方构建的内容树装进统一外壳，配一套独立 runtime 与帧管线，
 * 逐帧推进并绘制到给定放置盒。
 *
 * <p>它是「屏幕级虚拟窗口」的通用形态——HUD 注册项（{@code client.hud.SceneHudHost}）与
 * headless 出图页共用同一份装配语义，不再各自手搭第二套外壳/管线。</p>
 *
 * <h3>表达什么</h3>
 * <ul>
 *   <li><b>内容由工厂决定</b>：{@link ContentFactory} 在构造期调用<b>一次</b>，用与 UI 页面完全
 *       相同的 scene 代码建树；之后的内容变化走 signal，由帧管线物化，窗口不重建；</li>
 *   <li><b>外壳由调用方决定</b>：{@link Shell} 给出「有没有外壳、内边距多少、底色什么」——
 *       设计令牌是调用方的事实，窗口不假定任何全局默认（{@link Shell#HUD_DEFAULT} 只是
 *       提供一份可共用的默认皮肤）；</li>
 *   <li><b>装饰层可选</b>：{@link ContentDecorator} 在内容根外侧包一层（如外接工具栏），
 *       其尺寸参与外框测量；无装饰时内容根即外框。</li>
 * </ul>
 *
 * <h3>不变量</h3>
 * <ol>
 *   <li><b>一次装配、多帧推进</b>：外壳与内容树只在构造期建立，帧推进不重建（{@link #dispose()}
 *       之后不可再用）；</li>
 *   <li><b>空内容 = 整窗隐藏</b>：内容根布局盒宽或高 ≤ 0 时{@link #isEmptyContent()} 为真，
 *       投放方跳过绘制即整窗（含外壳与装饰层）不出现；但 {@link #settleWithoutPaint} 仍照常
 *       flush/layout，signal 物化不被跳帧锁死；</li>
 *   <li><b>纯 scene 依赖</b>：不读 mod 主类、client 单例与静态配置——度量、环境端口、外壳、
 *       装饰层、失败上报全部经构造参数注入，故同一份代码在客户端与无游戏进程下都可运行；</li>
 *   <li><b>放置盒硬裁剪</b>：{@link #frame} 以给定窗口盒裁剪，内容超长不溢出窗口；</li>
 *   <li><b>环境根成对</b>：构造期 {@link SceneHostAssembly#attachTree}，{@link #dispose()} 期
 *       摘除并释放 runtime。</li>
 * </ol>
 *
 * <h3>不做什么（边界）</h3>
 * <ul>
 *   <li>不算锚定 / 堆叠 / 倍率：调用方算好放置盒，{@code backend} 已按倍率缩放；</li>
 *   <li>不管注册表生命周期：谁创建谁 {@link #dispose()}；</li>
 *   <li>不持有输入源（无输入退化模式 {@code inputSource = null}），也不参与命中仲裁；</li>
 *   <li>不做「空内容自动隐藏」以外的可见性判断（in-world / 屏幕开合等宿主条件留在投放方）。</li>
 * </ul>
 *
 * <p>{@link AbstractSceneHostWidget}（页面宿主）与本类共用 {@link SceneHostAssembly} 的装配口径，
 * 但生命周期模型不同：前者挂在 Widget/GuiScreen 树上、有输入源与 attach/detach，后者是保留式、
 * 无输入、内容空即隐。二者互不继承。</p>
 */
public final class SceneHostWindow {

    /** 内容工厂：构造期调用一次，返回内容根；返回 null 视为装配失败。 */
    @FunctionalInterface
    public interface ContentFactory {
        /**
         * @param runtime 窗口专属场景运行时（signal 绑定、组件挂载与 UI 页面同源）
         * @return 内容根节点（非 null）
         */
        SceneNode build(SceneRuntime runtime);
    }

    /**
     * 外接装饰层：在内容根外侧包一层（外接工具栏等），返回的外框节点参与外框测量与放置。
     *
     * <p>抛出的运行时异常由窗口单点隔离：{@code failureSink} 上报后回退为内容直通，
     * 窗口主体照常工作（与「工具栏工厂失败只丢工具栏」的既有语义一致）。</p>
     */
    @FunctionalInterface
    public interface ContentDecorator {
        /**
         * @param runtime 窗口专属场景运行时（可见性绑定等归它）
         * @param content 内容根（必须作为返回节点的后代，否则内容不会显示）
         * @return 外框节点（非 null）；返回 null 视为装配失败
         */
        SceneNode decorate(SceneRuntime runtime, SceneNode content);
    }

    /**
     * 外壳皮肤：外壳开关与几何（设计令牌由调用方注入，窗口不假定全局默认）。
     *
     * <p>{@link #HUD_DEFAULT} 是全仓唯一的一份「HUD 默认外壳」事实——客户端宿主与 headless
     * 出图页取同一份，避免两处各自写一份内边距。</p>
     */
    public static final class Shell {
        /** HUD 默认外壳：内边距 7/6 + 半透明底。 */
        public static final Shell HUD_DEFAULT = new Shell(true, 7, 6, SceneChromeTokens.HUD_SHELL_BG);
        /** 无外壳：内容直接浮在画面上（无背景、无内边距）。 */
        public static final Shell BARE = new Shell(false, 0, 0, 0);

        private final boolean chrome;
        private final int paddingX;
        private final int paddingY;
        private final int backgroundArgb;

        private Shell(boolean chrome, int paddingX, int paddingY, int backgroundArgb) {
            this.chrome = chrome;
            this.paddingX = paddingX;
            this.paddingY = paddingY;
            this.backgroundArgb = backgroundArgb;
        }

        /**
         * 自定义外壳。
         *
         * @param paddingX       水平内边距（px）
         * @param paddingY       垂直内边距（px）
         * @param backgroundArgb 外壳底色（ARGB；0 表示不画底）
         * @return 外壳皮肤
         */
        public static Shell chrome(int paddingX, int paddingY, int backgroundArgb) {
            return new Shell(true, Math.max(0, paddingX), Math.max(0, paddingY), backgroundArgb);
        }

        /** @return 是否绘制外壳（背景 + 内边距） */
        public boolean isChrome() {
            return chrome;
        }

        /** @return 水平内边距（px；无外壳时为 0） */
        public int getPaddingX() {
            return paddingX;
        }

        /** @return 垂直内边距（px；无外壳时为 0） */
        public int getPaddingY() {
            return paddingY;
        }

        /** @return 外壳底色（ARGB；无外壳时为 0） */
        public int getBackgroundArgb() {
            return backgroundArgb;
        }
    }

    private final SceneNode root;
    private final SceneNode content;
    private final SceneRuntime runtime;
    private final SceneLayoutEngine layoutEngine;
    private final SceneFramePipeline pipeline;

    /**
     * 装配一个宿主窗口。
     *
     * @param measurer    文本度量端口，不可为 null
     * @param environment 宿主环境端口，不可为 null；无环境事实传 {@link UiEnvironment#empty()}
     * @param shell       外壳皮肤，不可为 null（无外壳传 {@link Shell#BARE}）
     * @param factory     内容工厂，不可为 null
     * @param decorator   外接装饰层，可为 null（无装饰，内容根即外框）
     * @param failureSink 装配期失败上报口（当前用于装饰层隔离），可为 null（不上报，调用方自担诊断缺失）
     * @throws IllegalArgumentException 度量/环境/外壳/工厂为 null
     * @throws IllegalStateException    内容工厂或装饰层返回 null
     */
    public SceneHostWindow(SceneTextMeasurer measurer, UiEnvironment environment, Shell shell,
            ContentFactory factory, ContentDecorator decorator, Consumer<RuntimeException> failureSink) {
        if (measurer == null) {
            throw new IllegalArgumentException("measurer must not be null");
        }
        if (environment == null) {
            throw new IllegalArgumentException(
                    "environment must not be null；无环境事实请传 UiEnvironment.empty()");
        }
        if (shell == null) {
            throw new IllegalArgumentException("shell must not be null；无外壳请传 Shell.BARE");
        }
        if (factory == null) {
            throw new IllegalArgumentException("factory must not be null");
        }
        // 五件套唯一装配点（A4）；无输入退化模式 inputSource=null。
        // 构造期不强制 flush：首帧物化由投放方合同保证（measure 空 → settleWithoutPaint
        // 同帧 flush+relayout → 次帧绘制），signal 绑定内容至多晚一帧可见。
        SceneHostAssembly.Bundle bundle = SceneHostAssembly.assemble(measurer, null, environment);
        runtime = bundle.getRuntime();
        layoutEngine = bundle.getLayoutEngine();
        pipeline = bundle.getPipeline();
        SceneNode shellNode = SceneNode.column().setHitTestable(false).setClipChildren(true)
                .setWidthSizing(SceneNode.WidthSizing.SHRINK);
        if (shell.isChrome()) {
            shellNode.setPadding(shell.getPaddingY(), shell.getPaddingX(),
                    shell.getPaddingY(), shell.getPaddingX());
            shellNode.setBackgroundColor(shell.getBackgroundArgb());
        }
        root = shellNode;
        try {
            SceneNode contentRoot = factory.build(runtime);
            if (contentRoot == null) {
                throw new IllegalStateException("SceneHostWindow content factory must return a node");
            }
            content = contentRoot;
            root.appendChild(decorateOrPassthrough(decorator, contentRoot, failureSink));
        } catch (RuntimeException failure) {
            // 装配失败的半成品不留 runtime（reactive 资源随 dispose 回收）；此时尚未 attachTree
            runtime.dispose();
            throw failure;
        }
        // 字号环境写入（装配点）：本窗口自建 runtime，建树路径不经 SceneRuntime.mount，
        // 必须在此把外框根交给 runtime。覆盖范围 = 外壳 + 内容 + 装饰层整棵树
        // （装饰层 wrapper 是 root 的后代，沿父链继承）。
        SceneHostAssembly.attachTree(runtime, root);
    }

    /** 装饰层装配：失败只丢装饰层，内容直通（单点隔离）。 */
    private SceneNode decorateOrPassthrough(ContentDecorator decorator, SceneNode contentRoot,
            Consumer<RuntimeException> failureSink) {
        if (decorator == null) {
            return contentRoot;
        }
        try {
            SceneNode outer = decorator.decorate(runtime, contentRoot);
            if (outer == null) {
                throw new IllegalStateException(
                        "SceneHostWindow decorator must return a node");
            }
            return outer;
        } catch (RuntimeException failure) {
            if (failureSink != null) {
                failureSink.accept(failure);
            }
            return contentRoot;
        }
    }

    /** 测量（含外壳）：layout 后返回外壳盒。 */
    public LayoutBox measure(int width, int height) {
        layoutEngine.layout(root, new Constraints(Math.max(1, width), Math.max(1, height)));
        return (LayoutBox) root.getCachedLayout();
    }

    /**
     * 内容子树是否无可见尺寸（signal 卸载 / 空文本）→ 投放方据此整窗隐藏。
     *
     * <p>只看内容、不看装饰层：装饰层是内容的附属，内容为空时整窗（含装饰层）都不出现。
     * 这条同时避免了聊天「双形态」下的重复渲染——聊天输入屏打开期间 HUD 树为空，
     * 若工具栏可见就单独渲染，工具栏会在屏幕与 HUD 各画一次。</p>
     *
     * @return 内容无宽或无高（或尚未布局）时为 true
     */
    public boolean isEmptyContent() {
        Object box = content.getCachedLayout();
        return box == null || ((LayoutBox) box).getWidth() <= 0 || ((LayoutBox) box).getHeight() <= 0;
    }

    /**
     * 空窗帧推进：flush / layout / settle 照常，不 paint 不 replay。
     *
     * <p>投放方合同：空窗也必须走这里，signal 物化才不被跳帧锁死，下一帧有内容即恢复绘制；
     * 投放方因此无须在宿主栈外强制 flush。</p>
     *
     * @param width           逻辑宽（px）
     * @param height          逻辑高（px）
     * @param frameTimeNanos  本帧时刻（ns；时间源由投放方提供，窗口不读静态时钟）
     */
    public void settleWithoutPaint(int width, int height, long frameTimeNanos) {
        runtime.__tickFrame(frameTimeNanos);
        pipeline.settleWithoutPaint(root, width, height);
    }

    /**
     * 窗口帧循环：与 UI 页面同源的帧管线，并以放置盒硬裁剪（内容超长不溢出窗口）。
     *
     * @param backend        渲染后端（已按倍率缩放）
     * @param x              逻辑原点 x
     * @param y              逻辑原点 y
     * @param width          逻辑宽
     * @param height         逻辑高
     * @param frameTimeNanos 本帧时刻（ns）
     */
    public void frame(UiRenderBackend backend, int x, int y, int width, int height,
            long frameTimeNanos) {
        runtime.__tickFrame(frameTimeNanos);
        pipeline.run(root, width, height, backend, x, y, frameTimeNanos,
                new AnchorRect(0, 0, width, height));
    }

    /** @return 窗口运行时（探针/诊断用；生命周期归本窗口） */
    public SceneRuntime runtime() {
        return runtime;
    }

    /** @return 外壳根（挂载与几何自检用） */
    public SceneNode root() {
        return root;
    }

    /** @return 内容根（工厂产物；装饰层存在时它是外框的后代） */
    public SceneNode content() {
        return content;
    }

    /** 释放：摘除环境根登记（与构造期成对）并释放 runtime；调用后本窗口不可再成帧。 */
    public void dispose() {
        SceneHostAssembly.detachTree(runtime, root);
        runtime.dispose();
    }
}
