package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.TextPolicy;

/** 独立草稿与外部来源接入同一声明；模式校验和视口/导航实现仍归字段控件。 */
final class SceneEditBindings {
    private SceneEditBindings() {}

    static SceneNode create(SceneControl control, SceneEditState state,
            ReadableSignal<SceneTheme> theme, ReadableSignal<Boolean> readOnly, TextPolicy textPolicy) {
        SceneNode node = new SceneNode().control(control).textPolicy(textPolicy)
                .size(Size.fill(1), Size.natural()).focusable(true).clickFocus(true).onClick(() -> {});
        // snapshot同时通知光标、组字与文本代际，只在正式挂载刷新中更新节点。
        node.bind(NodeBinding.value(state.snapshot(),
                (target, value) -> target.text(SceneEditCommands.display(value))));
        // 字体/字号由正式节点样式分类；配色只影响绘制，同值padding不能扩大为重新测量。
        node.bind(NodeBinding.value(theme, (target, value) -> {
            target.style(value.textStyle);
            if(target.padding()!=value.padding)target.padding(value.padding);
        }));
        node.bind(NodeBinding.value(readOnly, (target, locked) -> {
            if (locked) target.runtime().invalidateComposition(target);
        }));
        return node;
    }
}
