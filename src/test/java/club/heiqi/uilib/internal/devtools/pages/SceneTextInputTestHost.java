package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 文本输入字段模型回归测试专用最小宿主 fixture（Bug2 同帧多 TEXT 事件回归）。
 *
 * <p>从已删的早期 scene demo 宿主抽取文本输入 handler 模式——
 * <b>私有字段作即时权威读写源，signal 只作"模型→渲染"单向派生（永远只 set 字段快照、永不 get）</b>。
 * 保留同帧多 TEXT 事件累积 + codepoint-aware BACKSPACE 回归覆盖，不依赖具体 demo 宿主。</p>
 *
 * <h3>为何不用标准 {@code SceneTextInput}</h3>
 * <p>标准 {@code SceneTextInput} 走受控 value+onChange 契约（handler 读 {@code props.value().get()} 累积），
 * 与本 fixture 验证的"字段权威"模式是不同实现路径。本 fixture 锁定的是 Bug2 修复时引入的
 * "字段中转"范式本身，作为该范式的回归锚点独立保留。</p>
 */
final class SceneTextInputTestHost extends AbstractSceneHostWidget {

    private final SceneNode root;

    /** 文本框①内容 signal（LAYOUT 级）：TEXT_INPUT/KEY_DOWN 只写它，由 bind 派生 setText */
    private final Signal<String> inputTextSignal;
    /** 文本框②内容 signal（LAYOUT 级）：与①独立，验证 Tab 焦点切换后输入落到不同节点 */
    private final Signal<String> inputTextSignal2;

    /** 第一个可聚焦文本框节点引用 */
    private SceneNode textInput;
    /** 第二个可聚焦文本框节点引用 */
    private SceneNode textInput2;

    /** 文本框①的权威当前文本模型（handler 的唯一读写源，绝不读 signal） */
    private String inputModel1 = "";
    /** 文本框②的权威当前文本模型（与①一一对应） */
    private String inputModel2 = "";

    SceneTextInputTestHost() {
        super(null);
        this.root = new SceneNode();
        root.setFillParentHeight(true);

        // ===== 文本框①（验文本输入 + 焦点 + 字段权威模型） =====
        this.inputTextSignal = Signal.create("");
        this.textInput = new SceneNode();
        textInput.setPreferredHeight(SceneChromeTokens.INPUT_HEIGHT);
        root.appendChild(textInput);
        runtime.focusable(textInput);
        runtime.bind(inputTextSignal, t -> textInput.setText("Input1: " + t));
        runtime.on(textInput, SceneEventType.TEXT_INPUT, (event, ctx) -> {
            inputModel1 = inputModel1 + event.getText();
            inputTextSignal.set(inputModel1);
        });
        runtime.on(textInput, SceneEventType.KEY_DOWN, (event, ctx) -> {
            if (event.getKey() == SceneKey.BACKSPACE && !inputModel1.isEmpty()) {
                int newLen = inputModel1.offsetByCodePoints(inputModel1.length(), -1);
                inputModel1 = inputModel1.substring(0, newLen);
                inputTextSignal.set(inputModel1);
            }
        });

        // ===== 文本框②（独立 signal，验 Tab 焦点切换落点） =====
        this.inputTextSignal2 = Signal.create("");
        this.textInput2 = new SceneNode();
        textInput2.setPreferredHeight(SceneChromeTokens.INPUT_HEIGHT);
        root.appendChild(textInput2);
        runtime.focusable(textInput2);
        runtime.bind(inputTextSignal2, t -> textInput2.setText("Input2: " + t));
        runtime.on(textInput2, SceneEventType.TEXT_INPUT, (event, ctx) -> {
            inputModel2 = inputModel2 + event.getText();
            inputTextSignal2.set(inputModel2);
        });
        runtime.on(textInput2, SceneEventType.KEY_DOWN, (event, ctx) -> {
            if (event.getKey() == SceneKey.BACKSPACE && !inputModel2.isEmpty()) {
                int newLen = inputModel2.offsetByCodePoints(inputModel2.length(), -1);
                inputModel2 = inputModel2.substring(0, newLen);
                inputTextSignal2.set(inputModel2);
            }
        });

        runtime.flush();
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /** 测试探针：暴露内部 runtime，供测试 route/flush/requestFocus。 */
    SceneRuntime __getRuntime() {
        return runtime;
    }

    /** 测试探针：暴露主树根节点（route 入口）。 */
    SceneNode __getRoot() {
        return root;
    }

    /** 测试探针：暴露文本框①节点，供测试 requestFocus。 */
    SceneNode __getTextInput1() {
        return textInput;
    }

    /** 测试探针：暴露文本框②节点，供测试 requestFocus。 */
    SceneNode __getTextInput2() {
        return textInput2;
    }

    /** 测试探针：读文本框①权威字段模型（handler 的唯一读写源）。 */
    String __getInputModel1() {
        return inputModel1;
    }

    /** 测试探针：读文本框②权威字段模型。 */
    String __getInputModel2() {
        return inputModel2;
    }

    /** 测试探针：读文本框① signal flush 后的当前值（验证模型→渲染派生一致）。 */
    String __getInputSignal1() {
        return inputTextSignal.get();
    }

    /** 测试探针：读文本框② signal flush 后的当前值。 */
    String __getInputSignal2() {
        return inputTextSignal2.get();
    }
}
