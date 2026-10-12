package club.heiqi.uilib.ui.scene.text;

import club.heiqi.uilib.ui.scene.text.markdown.MarkdownAdapter;

import java.util.Objects;

/** 显式 Markdown 输入入口；转换一次，呈现继续消费共同文档，不增加 TextContent.Mode。 */
public final class MarkdownDocument {
    private MarkdownDocument() {}

    public static DocumentContent parse(String source) {
        return parse(TextInput.string(source));
    }

    public static DocumentContent parse(TextInput input) {
        return new MarkdownAdapter(Objects.requireNonNull(input, "input")).parse();
    }
}
