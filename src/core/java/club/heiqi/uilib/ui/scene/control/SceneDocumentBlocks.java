package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.DocumentContent;
import club.heiqi.uilib.ui.scene.text.TextStyle;

import java.util.Collections;
import java.util.function.Consumer;

/** 文档块语义转为同一scene树；不在转换器内保存排版、输入或资源生命周期。 */
final class SceneDocumentBlocks {
    private SceneDocumentBlocks() {}

    static SceneNode create(
            ScenePageContext context,
            ReadableSignal<DocumentContent.Block> source,
            ReadableSignal<SceneTheme> theme,
            DocumentResources resources,
            Consumer<String> activate) {
        DocumentContent.Block block = source.get();
        switch (block.kind) {
            case PARAGRAPH:
                return paragraph(context, source, theme, resources, activate);
            case HEADING:
                return paragraph(
                        context,
                        source,
                        () -> {
                            TextStyle base = theme.get().textStyle;
                            float scale = headingScale(source.get().headingLevel);
                            TextStyle style =
                                    new TextStyle(
                                            base.font,
                                            base.logicalEm * scale,
                                            base.color,
                                            base.candidates(),
                                            base.effects,
                                            base.decoration,
                                            new club.heiqi.uilib.ui.scene.text.FontStyle(
                                                    700, base.fontStyle.posture));
                            return theme.get().withTextStyle(style);
                        },
                        resources,
                        activate);
            case MATH:
                return SceneMath.create(
                        context,
                        () -> source.get().math,
                        theme,
                        block.style,
                        Collections.emptyMap());
            case RULE:
                SceneNode rule = new SceneNode().size(Size.fill(1), Size.fixed(1));
                rule.bind(
                        NodeBinding.value(
                                theme, (node, value) -> node.background(value.disabledText)));
                return rule;
            case CODE:
                SceneNode code =
                        paragraph(context, source, theme, resources, activate)
                                .size(Size.natural(), Size.natural())
                                .textPolicy(club.heiqi.uilib.ui.scene.text.TextPolicy.NO_WRAP);
                SceneNode viewport =
                        new SceneNode()
                                .control(new DocumentCodeViewport())
                                .clip(true)
                                .size(Size.fill(1), Size.natural());
                viewport.bind(
                        NodeBinding.value(
                                theme,
                                (node, value) ->
                                        node.padding(value.padding).background(value.button)));
                viewport.add(code);
                return viewport;
            case LIST:
                SceneNode list = new SceneNode().size(Size.fill(1), Size.natural());
                list.bind(
                        NodeBinding.value(
                                theme,
                                (node, value) -> node.gap(source.get().tight ? 0 : value.gap)));
                list.bind(
                        NodeBinding.repeat(
                                () -> source.get().children(),
                                item -> item,
                                item ->
                                        listItem(
                                                context, source, item, theme, resources,
                                                activate)));
                return list;
            case QUOTE:
                SceneNode quote =
                        new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural());
                SceneNode stripe = new SceneNode().size(Size.fixed(3), Size.fill(1));
                stripe.bind(
                        NodeBinding.value(
                                theme, (node, value) -> node.background(value.disabledText)));
                quote.bind(NodeBinding.value(theme, (node, value) -> node.gap(value.padding)));
                quote.add(stripe).add(children(context, source, theme, resources, activate));
                return quote;
            case LIST_ITEM:
                return children(context, source, theme, resources, activate);
            default:
                throw new IllegalStateException("未知文档块语义：" + block.kind);
        }
    }

    private static float headingScale(int level) {
        switch (level) {
            case 1:
                return 2f;
            case 2:
                return 1.65f;
            case 3:
                return 1.4f;
            case 4:
                return 1.2f;
            case 5:
                return 1.1f;
            default:
                return 1f;
        }
    }

    private static SceneNode paragraph(
            ScenePageContext context,
            ReadableSignal<DocumentContent.Block> source,
            ReadableSignal<SceneTheme> theme,
            DocumentResources resources,
            Consumer<String> activate) {
        return SceneDocument.paragraph(
                        context, () -> source.get().paragraph, theme, resources, activate)
                .size(Size.fill(1), Size.natural());
    }

    private static SceneNode children(
            ScenePageContext context,
            ReadableSignal<DocumentContent.Block> source,
            ReadableSignal<SceneTheme> theme,
            DocumentResources resources,
            Consumer<String> activate) {
        SceneNode root = new SceneNode().size(Size.fill(1), Size.natural());
        root.bind(NodeBinding.value(theme, (node, value) -> node.gap(value.gap)));
        root.bind(
                NodeBinding.repeat(
                        () -> source.get().children(),
                        child -> child,
                        child -> create(context, child, theme, resources, activate)));
        return root;
    }

    private static SceneNode listItem(
            ScenePageContext context,
            ReadableSignal<DocumentContent.Block> list,
            ReadableSignal<DocumentContent.Block> item,
            ReadableSignal<SceneTheme> theme,
            DocumentResources resources,
            Consumer<String> activate) {
        SceneNode row = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural());
        row.bind(NodeBinding.value(theme, (node, value) -> node.gap(value.gap)));
        // 编号是独立标记列，层级来自树布局；正文原文中不插入空格模拟缩进。
        SceneNode marker =
                SceneLabel.create(
                        context,
                        () -> {
                            DocumentContent.Block owner = list.get();
                            int index = owner.children().indexOf(item.get());
                            return owner.ordered
                                    ? Long.toString((long) owner.orderedStart + index) + "."
                                    : "•";
                        },
                        theme,
                        SceneTextPolicy.OVERFLOW);
        row.add(marker).add(children(context, item, theme, resources, activate));
        return row;
    }
}
