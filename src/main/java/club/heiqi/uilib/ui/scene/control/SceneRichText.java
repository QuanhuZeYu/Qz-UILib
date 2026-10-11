package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.TextContent;

import java.util.function.Consumer;

/** 富文本是共同文档的段落入口；链接、数学和图像共用同代文字布局。 */
public final class SceneRichText {
    private SceneRichText() {}

    public static SceneNode create(
            ScenePageContext context,
            ReadableSignal<TextContent> content,
            ReadableSignal<SceneTheme> theme,
            Consumer<String> activate) {
        return SceneDocument.paragraph(context, content, theme, null, activate);
    }

    /** 图像声明使用正式图像服务及结构化来源引用；不让业务补下载或命中几何。 */
    public static SceneNode create(
            ScenePageContext context,
            ReadableSignal<TextContent> content,
            ReadableSignal<SceneTheme> theme,
            DocumentResources resources,
            Consumer<String> activate) {
        return SceneDocument.paragraph(context, content, theme, resources, activate);
    }
}
