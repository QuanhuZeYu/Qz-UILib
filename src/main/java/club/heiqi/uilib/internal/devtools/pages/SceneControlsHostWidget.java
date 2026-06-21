package club.heiqi.uilib.internal.devtools.pages;

import java.util.Arrays;
import java.util.List;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.control.SceneBreadcrumb;
import club.heiqi.uilib.ui.scene.control.SceneCheckbox;
import club.heiqi.uilib.ui.scene.control.SceneRadioGroup;
import club.heiqi.uilib.ui.scene.control.SceneSegmented;
import club.heiqi.uilib.ui.scene.control.SceneSlider;
import club.heiqi.uilib.ui.scene.control.SceneToggle;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.text.TextMeasureServiceSceneAdapter;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 新栈 ui.scene 控件 demo 宿主 Widget —— 演示受控双向控件 SceneCheckbox + SceneToggle。
 *
 * <h3>受控双向闭环演示</h3>
 * <p>各控件配一个本地可写 {@link Signal}{@code <Boolean>} 作 checked/on 受控源，
 * onChange 回调里读「期望新值」并 set 回该 signal，形成「外部状态唯一源 → 控件渲染」单向数据流
 * （控件零内部状态，绝不自己缓存/翻转，守契约 R7）。</p>
 *
 * <h3>端到端 pipeline（对照 SceneHostWidget）</h3>
 * <pre>
 *  drainFrame → layout① → route(queueWrite) → flush(apply+effect)
 *    → layout②(吸收LAYOUT脏) → paint → replay
 * </pre>
 */
public class SceneControlsHostWidget extends Widget {

    private final SceneRuntime runtime;
    private final SceneLayoutEngine layoutEngine;
    private final ScenePaintEngine paintEngine;
    private final ScenePaintReplayer replayer;
    private final SceneNode root;

    /** Checkbox 受控源（本地唯一状态源），onChange 回调 set 回它 */
    private final Signal<Boolean> checkedSignal;
    /** Toggle 受控源（本地唯一状态源），onChange 回调 set 回它 */
    private final Signal<Boolean> toggleSignal;
    /** Slider 受控源（本地唯一状态源，连续值），onChange 按 committing 策略 set 回它 */
    private final Signal<Double> sliderSignal;

    /** I3 平台输入源（允许 null：null 时 pipeline 退化为渲染纯驱动） */
    private final PlatformInputSource inputSource;

    /**
     * 创建控件 demo 宿主 Widget，注入平台输入源。
     *
     * @param inputSource 平台输入源，可为 null（退化模式）
     */
    public SceneControlsHostWidget(PlatformInputSource inputSource) {
        this.inputSource = inputSource;
        this.runtime = new SceneRuntime();
        // 用框架默认度量服务包成 scene 窄端口注入布局引擎（I6 复用渲染层度量）
        this.layoutEngine = new SceneLayoutEngine(
                new TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance()));
        this.paintEngine = new ScenePaintEngine();
        this.replayer = new ScenePaintReplayer();
        this.root = new SceneNode();
        // root 为纵向容器，铺满 host 全高，子控件自上而下排列
        root.setFillParentHeight(true);
        root.setFlexDirection(FlexDirection.COLUMN);
        root.setGap(20);
        root.setPadding(20);

        // ===== Checkbox 受控双向闭环 =====
        this.checkedSignal = Signal.create(Boolean.FALSE);
        SceneCheckbox.Props checkboxProps = new SceneCheckbox.Props(
                checkedSignal,
                Signal.create("启用音效"),
                Signal.create(Boolean.TRUE),
                // 受控：把期望新值 set 回本地唯一源（控件不自己翻转）
                next -> checkedSignal.set(next));
        runtime.mount(root, SceneCheckbox.create(runtime, checkboxProps));

        // ===== Toggle 受控双向闭环 =====
        this.toggleSignal = Signal.create(Boolean.FALSE);
        SceneToggle.Props toggleProps = new SceneToggle.Props(
                toggleSignal,
                Signal.create("夜间模式"),
                Signal.create(Boolean.TRUE),
                next -> toggleSignal.set(next));
        runtime.mount(root, SceneToggle.create(runtime, toggleProps));

        // ===== Slider 受控连续闭环 =====
        // 受控源初值 30（范围 [0,100]，step=5），onChange 按 committing 写回策略：
        // 本 demo 选「committing=true/false 都写回」做实时预览联动——拖拽中每次预览也 set 回 sliderSignal，
        // 使 fill/thumb 实时跟手（受控闭环：onChange→外部 signal→effectiveValue 回落外部值仍正确）。
        // 另一种实现是仅 committing=true 才写回（拖拽中靠控件内部 draggingValue 接管预览），二选一，此处取实时联动。
        this.sliderSignal = Signal.create(30.0D);
        SceneSlider.Props sliderProps = new SceneSlider.Props(
                sliderSignal,
                Signal.create(Boolean.TRUE),
                0.0D, 100.0D, 5.0D,
                (value, committing) -> sliderSignal.set(value));
        runtime.mount(root, SceneSlider.create(runtime, sliderProps));

        // I4c：仅生产模式注入 LWJGL cursor 后端；mock/null 退化模式跳过，避免沙箱触发 LWJGL 反射
        if (inputSource instanceof LwjglInputSource) {
            runtime.bindCursor(new LwjglCursorBackend());
        }

        // 首次 flush，确保首帧有初始值
        runtime.flush();
    }

    // ==================== Widget 生命周期 ====================

    /**
     * 每帧绘制 —— I3 输入层帧循环时序铁律（drainFrame→layout①→route→flush→layout②→paint→replay）。
     *
     * @param ctx 渲染上下文
     */
    @Override
    protected void drawSelf(UiRenderContext ctx) {
        int w = Math.max(0, getWidth());
        int h = Math.max(0, getHeight());

        // ① drainFrame：取本帧输入事件
        SceneInputFrame frame = (inputSource != null) ? inputSource.drainFrame() : SceneInputFrame.EMPTY;

        // ② layout①：route 的 hit-test 读当帧最新几何
        layoutEngine.layout(root, new Constraints(w, h));

        // ③ route：仅 queueWrite 写入 signal，不 flush
        if (!frame.isEmpty()) {
            runtime.route(root, frame, getAbsoluteX(), getAbsoluteY());
        }

        // ④ flush：唯一让 queueWrite 生效，重跑脏 effect
        runtime.flush();

        // ⑤ layout②：吸收 flush 产生的 LAYOUT 级变化
        layoutEngine.layout(root, new Constraints(w, h));

        // ⑥ paint + replay
        PaintPlan plan = paintEngine.paint(root);
        replayer.replay(plan, ctx, getAbsoluteX(), getAbsoluteY());
    }

    /**
     * 回收资源：dispose runtime 以退订所有 effect 与 mount 作用域。
     */
    public void dispose() {
        runtime.dispose();
    }
}
