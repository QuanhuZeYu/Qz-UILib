package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.text.InlineContent;
import club.heiqi.uilib.ui.scene.text.ParagraphContent;
import club.heiqi.uilib.ui.scene.text.StyledText;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.TextStyleOverride;
import club.heiqi.uilib.ui.scene.text.TextStyleOverrideSpan;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 共同声明的模型探针；正式 scene/字体/输入验收在文档场景中完成。 */
public final class TypedParagraphScenario extends club.heiqi.uilib.testing.TestApplication {
    private static final TextStyle BASE = new TextStyle("default", 20, 0xff123456);

    /** 父集成可直接用此声明交给 SceneDocument，输入没有 DSL 依赖。 */
    public static TextContent typedContent(boolean bytes) {
        String source = "甲[math][img]链";
        TextInput original =
                bytes
                        ? TextInput.utf8(source.getBytes(StandardCharsets.UTF_8))
                        : TextInput.string(source);
        String logical = "甲\ufffc\ufffc链";
        int mathStart = source.indexOf("[math]"), imageStart = source.indexOf("[img]");
        int linkStart = source.indexOf("链");
        TextInput input =
                TextInput.fromMapped(
                        original,
                        logical,
                        new int[] {0, mathStart, imageStart, linkStart},
                        new int[] {mathStart, imageStart, linkStart, source.length()},
                        Collections.<TextInput.SourceSegment>emptyList());
        StyledText styled = styled(input, source);
        InlineContent formula =
                InlineContent.formula(
                        1,
                        input.sourceStart(1),
                        input.sourceEnd(1),
                        MathContent.parse("x^2"),
                        MathStyle.TEXT);
        InlineContent image =
                InlineContent.image(
                        2,
                        input.sourceStart(2),
                        input.sourceEnd(2),
                        "test:typed",
                        "图",
                        24,
                        18,
                        ImageOptions.Fit.CONTAIN);
        List<TextContent.LinkSpan> links = new ArrayList<>();
        links.add(new TextContent.LinkSpan(0, logical.length(), new TextContent.Link("target")));
        List<InlineContent> inline = new ArrayList<>(Arrays.asList(formula, image));
        ParagraphContent paragraph = new ParagraphContent(styled, links, inline);
        links.clear();
        inline.clear();
        require(
                paragraph.links().size() == 1 && paragraph.inlineContents().size() == 2,
                "共同声明必须防御性复制");
        return TextContent.paragraph(paragraph);
    }

    private static StyledText styled(TextInput input, String source) {
        return new StyledText(
                input,
                source,
                Collections.singletonList(
                        new TextStyleOverrideSpan(
                                0,
                                input.text.length(),
                                TextStyleOverride.INHERIT.toBuilder().weight(700).build())),
                TextStyleOverride.INHERIT,
                Collections.<TextLayout.Diagnostic>emptyList());
    }

    public static void main(String[] args) throws Exception {
        new TypedParagraphScenario().run(args);
    }

    @Override
    protected void runTests() throws Exception {
        scenario("共同typed内容来源范围与DSL单向转换", TypedParagraphScenario::model);
    }

    private static void model() {
        System.out.println("java=" + System.getProperty("java.version"));
        for (boolean bytes : new boolean[] {false, true}) {
            TextContent content = typedContent(bytes);
            TextContent.Resolved a = content.resolve(BASE);
            TextContent.Resolved b = content.resolve(new TextStyle("changed", 31, 0xffabcdef));
            require(
                    content.mode == TextContent.Mode.PLAIN
                            && content.source.equals("甲[math][img]链"),
                    "typed 入口必须保留原文与显式语法语义");
            require(
                    a.text.input == b.text.input
                            && a.linkAt(1) == b.linkAt(1)
                            && a.inlineContents().get(0) == b.inlineContents().get(0),
                    "派生不得重建声明身份");
            require(a.inlineContents().get(1) == content.inlineContents().get(1), "共同声明路径必须统一");
            require(
                    a.text.baseStyle != b.text.baseStyle
                            && b.text.styleAt(0).fontStyle.weight == 700
                            && b.text.styleAt(0).logicalEm == 31,
                    "typed 样式必须继承当前基础值");
            TextInput input = a.text.input;
            require(
                    input.sourceUnit
                            == (bytes ? TextInput.SourceUnit.BYTE : TextInput.SourceUnit.UTF16),
                    "来源单位不能丢失");
            int expected = bytes ? "甲".getBytes(StandardCharsets.UTF_8).length : "甲".length();
            require(input.sourceStart(1) == expected, "内联来源必须使用原单位");
            reject(
                    () ->
                            new ParagraphContent(
                                    styled(input, content.source),
                                    a.links(),
                                    Arrays.asList(
                                            a.inlineContents().get(0), a.inlineContents().get(0))));
            reject(
                    () ->
                            new ParagraphContent(
                                    styled(input, content.source),
                                    a.links(),
                                    Arrays.asList(
                                            a.inlineContents().get(1), a.inlineContents().get(0))));
            reject(
                    () ->
                            new ParagraphContent(
                                    styled(input, content.source),
                                    a.links(),
                                    Collections.singletonList(
                                            InlineContent.formula(
                                                    1,
                                                    0,
                                                    1,
                                                    MathContent.parse("x"),
                                                    MathStyle.TEXT))));
            try {
                a.serializeRichTags();
                throw new AssertionError("typed 不提供 DSL 序列化");
            } catch (UnsupportedOperationException expectedFailure) {
            }
            System.out.println(
                    "PASS typed text+math+image+link sourceUnit="
                            + input.sourceUnit
                            + " formulaSource="
                            + input.sourceStart(1)
                            + ".."
                            + input.sourceEnd(1));
        }
        TextInput simple = TextInput.string("a\ufffcb");
        StyledText styled = styled(simple, null);
        TextContent.Link link = new TextContent.Link("same");
        reject(
                () ->
                        new ParagraphContent(
                                styled,
                                Arrays.asList(
                                        new TextContent.LinkSpan(0, 2, link),
                                        new TextContent.LinkSpan(1, 3, link)),
                                Collections.<InlineContent>emptyList()));
        reject(
                () ->
                        new ParagraphContent(
                                styled,
                                Arrays.asList(
                                        new TextContent.LinkSpan(2, 3, link),
                                        new TextContent.LinkSpan(0, 1, link)),
                                Collections.<InlineContent>emptyList()));
        reject(
                () ->
                        new ParagraphContent(
                                styled,
                                Collections.<TextContent.LinkSpan>emptyList(),
                                Collections.singletonList(
                                        InlineContent.image(
                                                0,
                                                0,
                                                1,
                                                "src",
                                                "",
                                                null,
                                                null,
                                                ImageOptions.Fit.CONTAIN))));
        StyledText emoji = styled(TextInput.string("😀"), null);
        reject(
                () ->
                        new ParagraphContent(
                                emoji,
                                Collections.singletonList(new TextContent.LinkSpan(0, 1, link)),
                                Collections.<InlineContent>emptyList()));
        require(TextContent.styled(styled).resolve(BASE).inlineContents().isEmpty(), "styled 兼容");
        require(
                TextContent.plain("<b>\ufffc</b>§a")
                        .resolve(BASE)
                        .text
                        .input
                        .text
                        .equals("<b>\ufffc</b>§a"),
                "PLAIN 保留字面内容");
        TextContent rich = TextContent.richTags("<a href=\"outer\">A<a href=\"inner\">B</a>C</a>");
        TextContent.Resolved r = rich.resolve(BASE);
        require(r.linkAt(0) == r.linkAt(2) && r.linkAt(0) != r.linkAt(1), "嵌套链接恢复身份");
        require(r.linkAt(1) == rich.resolve(BASE.withColor(0xff000000)).linkAt(1), "重派生身份稳定");
        require(
                TextContent.richTags(r.serializeRichTags())
                        .resolve(BASE)
                        .text
                        .input
                        .text
                        .equals("ABC"),
                "DSL 语义往返");
        TextContent bad = TextContent.richTags("<a href=\"x\">ok<img src=\"y\"/><bad>");
        TextContent.Resolved failed = bad.resolve(BASE);
        require(
                failed.text.input.text.equals(bad.source)
                        && failed.links().isEmpty()
                        && bad.inlineContents().isEmpty()
                        && !failed.text.diagnostics().isEmpty(),
                "错误整段降为字面");
        TextContent dsl =
                TextContent.of(
                        TextContent.Mode.RICH_TAGS,
                        TextInput.utf8(
                                "甲<latex>x</latex><img src=\"p\"/>"
                                        .getBytes(StandardCharsets.UTF_8)));
        require(dsl.inlineContents().size() == 2, "DSL 必须生成共同声明");
        require(
                dsl.resolve(BASE).text.input.sourceUnit == TextInput.SourceUnit.BYTE,
                "DSL byte 来源保留");
        System.out.println(
                "PASS range validation, literal FFFC, styled compatibility, DSL"
                        + " fallback/identity/serialize");
    }

    private static void reject(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("违约声明必须拒绝");
        } catch (IllegalArgumentException expected) {
        }
    }
}
