package club.heiqi.uilib.ui.scene.integration;

/** Markdown来源经共同段落的真实字体、数学、图像和GL检验，复用正式文档设备场景。 */
public final class MarkdownGlScenario {
    private MarkdownGlScenario() {}
    public static void main(String[] args) throws Exception {
        DocumentGlScenario.runMarkdown(args);
    }
}
