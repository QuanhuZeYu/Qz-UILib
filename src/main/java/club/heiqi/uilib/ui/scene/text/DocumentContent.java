package club.heiqi.uilib.ui.scene.text;

import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 共同文档的块声明；输入转换入口产生同一种段落/公式块，不保存布局或节点。 */
public final class DocumentContent {
    public static final class Block {
        public final TextContent paragraph;
        public final MathContent math;
        public final MathStyle style;

        private Block(TextContent text, MathContent math, MathStyle style) {
            paragraph = text;
            this.math = math;
            this.style = style;
        }

        public static Block paragraph(TextContent text) {
            return new Block(Objects.requireNonNull(text, "text"), null, null);
        }

        public static Block formula(MathContent math, MathStyle style) {
            return new Block(
                    null,
                    Objects.requireNonNull(math, "math"),
                    Objects.requireNonNull(style, "style"));
        }
    }

    private final List<Block> blocks;

    public DocumentContent(List<Block> blocks) {
        ArrayList<Block> copy = new ArrayList<>(blocks);
        for (Block block : copy) Objects.requireNonNull(block, "block");
        this.blocks = Collections.unmodifiableList(copy);
    }

    public static DocumentContent paragraph(TextContent text) {
        return new DocumentContent(Collections.singletonList(Block.paragraph(text)));
    }

    public List<Block> blocks() {
        return blocks;
    }
}
