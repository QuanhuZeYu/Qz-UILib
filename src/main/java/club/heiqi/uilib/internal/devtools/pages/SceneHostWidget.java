package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;

/**
 * 新栈 ui.scene 最小宿主 Widget —— 粘合层：同时认识 SceneNode 和 UiRenderContext（合法职责）。
 *
 * <h3>端到端 pipeline（信条五/六/I6）</h3>
 * <pre>
 *  signal.set → runtime.flush() → layout(layoutEngine) → paint(paintEngine)
 *    → Display List(PaintPlan) → replay(replayer, ctx, absX, absY)
 * </pre>
 *
 * <h3>I6 接缝硬约束</h3>
 * <p>SceneNode 只在 flush→layout→paint 三步内流转；replay 只传 plan（PaintCommand 列表）
 * + 两个 int offset；<b>任何 ctx 调用的参数中绝不出现 SceneNode</b>。</p>
 */
public class SceneHostWidget extends AbstractSceneHostWidget {


    private final SceneNode root;

    /** 背景色 signal（PAINT 级），驱动根节点背景矩形 */
    private final Signal<Integer> bgColorSignal;
    /** 文本内容 signal（LAYOUT 级），驱动子节点 bindText */
    private final Signal<String> labelSignal;

    /** 根节点下的文本子节点引用，供交互逻辑读取 */
    private SceneNode textNode;

    /** I4b 文本框①内容 signal（LAYOUT 级）：TEXT_INPUT/KEY_DOWN 只写它，由 bind 派生 setText */
    private final Signal<String> inputTextSignal;
    /** I4b 文本框②内容 signal（LAYOUT 级）：与①独立，验证 Tab 焦点切换后输入落到不同节点 */
    private final Signal<String> inputTextSignal2;
    /** 第一个可聚焦文本框节点引用 */
    private SceneNode textInput;
    /** 第二个可聚焦文本框节点引用 */
    private SceneNode textInput2;

    /**
     * 文本框①的权威当前文本模型（Bug2 后续修复）。
     *
     * <p>同帧 SDL onTextEvent 可能 push 多个 TEXT 事件，route 在 flush 之前连续调用 N 次 handler；
     * reactive Signal 的 {@code get()} 在 flush 前恒返回旧值（I9 帧末批处理的正确设计），
     * 若 handler 读 signal 累加，同帧多事件会互相覆盖（如"好好好"只剩一个"好"）。
     * 故引入即时可变字段作权威读写源，signal 只作"模型→渲染"单向派生（永远只 set 字段快照、永不 get）。</p>
     */
    private String inputModel1 = "";
    /** 文本框②的权威当前文本模型（语义同 {@link #inputModel1}，与②一一对应） */
    private String inputModel2 = "";

    /** I3.5 demo：click 计数器 */
    private int clickCount;

    /**
     * 创建场景宿主 Widget（无输入源，渲染纯驱动模式）。
     */
    public SceneHostWidget() {
        this(null);
    }

    /**
     * 创建场景宿主 Widget，注入平台输入源。
     *
     * @param inputSource 平台输入源，可为 null（退化模式）
     */
    public SceneHostWidget(PlatformInputSource inputSource) {
        super(inputSource);
        this.root = new SceneNode();
        // root 为容器节点，设置 fillParentHeight 使其背景矩形铺满 host 全高
        root.setFillParentHeight(true);

        // 初始化 signal（I1：signal 是唯一数据驱动力）
        this.bgColorSignal = Signal.create(0xFF333333);  // 深灰
        this.labelSignal = Signal.create("Scene Demo: Hello");

        // 手动构建场景树——不用 mount，因为 root 本身就是场景根（layout/paint 入口），不应作为子节点挂到任何 parent。
        // bind 不依赖 mount：当前无 mount childOwner 作用域时，effect 自动归属 runtime 的 rootOwner，由 dispose() 统一回收。
        runtime.bind(Invalidation.PAINT, bgColorSignal, root::setBackgroundColor);
        SceneNode child = new SceneNode();
        root.appendChild(child);
        runtime.bind(Invalidation.LAYOUT, labelSignal, child::setText);
        this.textNode = child;

        // ===== I3.5 demo：hover/click 验证按钮 =====
        SceneNode btn = new SceneNode();
        btn.setPreferredHeight(SceneChromeTokens.INPUT_HEIGHT); // 显式高度：无文本叶节点默认高度 0，必须设此否则不可见
        btn.setCursor(SceneCursor.POINTER); // I4c：声明手型光标，hover 进按钮时 cursor 投影派生为 POINTER
        root.appendChild(btn);
        // hover 绑定：hover 进 → 亮青色，hover 出 → 恢复灰色
        runtime.bind(Invalidation.PAINT, runtime.interactionState(btn).hovered(),
                hovered -> btn.setBackgroundColor(hovered ? 0xFF00CCCC : 0xFF555555));
        // 初始背景色
        btn.setBackgroundColor(0xFF555555);
        // click 绑定：点击时更新 label + 计数
        runtime.on(btn, SceneEventType.CLICK, (event, ctx) -> {
            int count = clickCount++;
            labelSignal.set("Clicked! " + count);
        });

        // ===== I4b/I4c demo：可聚焦文本框①（验文本输入 + 焦点高亮 + 文本光标） =====
        this.inputTextSignal = Signal.create("");
        this.textInput = new SceneNode();
        textInput.setPreferredHeight(SceneChromeTokens.INPUT_HEIGHT); // 显式高度：叶节点默认 0 高不可见（真机踩过的坑）
        textInput.setCursor(SceneCursor.TEXT); // I4c：声明 I-beam 文本光标，区别于 btn 的 POINTER 手型
        root.appendChild(textInput);
        runtime.focusable(textInput); // I4b：登记进 Tab 焦点环
        // 文本显示走 signal→bind→setText 这条响应式链（I11：handler 内严禁直接 setText）
        runtime.bind(Invalidation.LAYOUT, inputTextSignal, t -> textInput.setText("Input1: " + t));
        // 焦点高亮：focused signal → bind → setBackgroundColor（聚焦亮蓝，失焦暗蓝）
        runtime.bind(Invalidation.PAINT, runtime.interactionState(textInput).focused(),
                focused -> textInput.setBackgroundColor(focused ? 0xFF2266DD : 0xFF223355));
        // TEXT_INPUT：把输入字符追加进字段模型再 set 进 signal（字段是唯一权威源，绝不读 signal）
        runtime.on(textInput, SceneEventType.TEXT_INPUT, (event, ctx) -> {
            inputModel1 = inputModel1 + event.getText();
            inputTextSignal.set(inputModel1);
        });
        // KEY_DOWN：BACKSPACE 删末字符（codepoint-aware，数据源为字段模型，不读 signal）
        runtime.on(textInput, SceneEventType.KEY_DOWN, (event, ctx) -> {
            if (event.getKey() == SceneKey.BACKSPACE && !inputModel1.isEmpty()) {
                // codepoint-aware：emoji 等补充平面字符占 2 个 char，按 codepoint 回退一个完整字符
                int newLen = inputModel1.offsetByCodePoints(inputModel1.length(), -1);
                inputModel1 = inputModel1.substring(0, newLen);
                inputTextSignal.set(inputModel1);
            }
        });

        // ===== I4b/I4c demo：可聚焦文本框②（独立 signal，验 Tab 焦点切换落点） =====
        this.inputTextSignal2 = Signal.create("");
        this.textInput2 = new SceneNode();
        textInput2.setPreferredHeight(SceneChromeTokens.INPUT_HEIGHT);
        textInput2.setCursor(SceneCursor.TEXT); // I4c：同样 I-beam 文本光标
        root.appendChild(textInput2);
        runtime.focusable(textInput2); // I4b：第二个 Tab 焦点目标
        // 文本显示链（给 ② 不同前缀，肉眼区分输入落到哪个框）
        runtime.bind(Invalidation.LAYOUT, inputTextSignal2, t -> textInput2.setText("Input2: " + t));
        // 焦点高亮：聚焦亮绿，失焦暗绿（与 ① 配色不同，便于辨认当前焦点）
        runtime.bind(Invalidation.PAINT, runtime.interactionState(textInput2).focused(),
                focused -> textInput2.setBackgroundColor(focused ? 0xFF22AA44 : 0xFF224433));
        runtime.on(textInput2, SceneEventType.TEXT_INPUT, (event, ctx) -> {
            inputModel2 = inputModel2 + event.getText();
            inputTextSignal2.set(inputModel2);
        });
        runtime.on(textInput2, SceneEventType.KEY_DOWN, (event, ctx) -> {
            if (event.getKey() == SceneKey.BACKSPACE && !inputModel2.isEmpty()) {
                // codepoint-aware：emoji 等补充平面字符占 2 个 char，按 codepoint 回退一个完整字符
                int newLen = inputModel2.offsetByCodePoints(inputModel2.length(), -1);
                inputModel2 = inputModel2.substring(0, newLen);
                inputTextSignal2.set(inputModel2);
            }
        });

        // 首次 flush，确保首帧有初始值
        runtime.flush();
    }

    /**
     * 设置根节点背景色。
     *
     * @param color ARGB 颜色值
     */
    public void setBgColor(int color) {
        bgColorSignal.set(color);
    }

    /**
     * 设置文本内容。
     *
     * @param text 文本
     */
    public void setLabel(String text) {
        labelSignal.set(text);
    }

    /**
     * 获取文本节点引用，供外部诊断。
     *
     * @return 文本子节点
     */
    public SceneNode getTextNode() {
        return textNode;
    }

    /**
     * 获取当前文本内容（从 sceneNode 属性槽读取）。
     *
     * @return 当前文本
     */
    public String getLabel() {
        return textNode.getText();
    }

    /**
     * 获取当前背景色。
     *
     * @return ARGB 颜色值
     */
    public int getBgColor() {
        return root.getBackgroundColor();
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /**
     * 获取 paint 引擎引用（供测试断言 regeneratedFragmentCount）。
     *
     * @return paint 引擎
     */
    public SceneLayoutEngine getLayoutEngine() {
        return layoutEngine;
    }

    // ==================== 包级测试探针（Bug2 同帧多 TEXT 事件回归验证用） ====================

    /**
     * 测试探针：暴露内部 SceneRuntime，供测试经 {@code route} 注入输入帧、{@code requestFocus} 切焦点。
     *
     * @return 内部 runtime
     */
    SceneRuntime __getRuntime() {
        return runtime;
    }

    /**
     * 测试探针：暴露场景根节点（route 入口）。
     *
     * @return 根节点
     */
    SceneNode __getRoot() {
        return root;
    }

    /**
     * 测试探针：暴露文本框①节点，供测试 requestFocus。
     *
     * @return 文本框①节点
     */
    SceneNode __getTextInput1() {
        return textInput;
    }

    /**
     * 测试探针：暴露文本框②节点，供测试 requestFocus。
     *
     * @return 文本框②节点
     */
    SceneNode __getTextInput2() {
        return textInput2;
    }

    /**
     * 测试探针：读文本框①权威字段模型（handler 的唯一读写源）。
     *
     * @return inputModel1 当前值
     */
    String __getInputModel1() {
        return inputModel1;
    }

    /**
     * 测试探针：读文本框②权威字段模型。
     *
     * @return inputModel2 当前值
     */
    String __getInputModel2() {
        return inputModel2;
    }

    /**
     * 测试探针：读文本框① signal flush 后的当前值（验证模型→渲染派生一致性）。
     *
     * @return inputTextSignal 当前值
     */
    String __getInputSignal1() {
        return inputTextSignal.get();
    }

    /**
     * 测试探针：读文本框② signal flush 后的当前值。
     *
     * @return inputTextSignal2 当前值
     */
    String __getInputSignal2() {
        return inputTextSignal2.get();
    }

}
