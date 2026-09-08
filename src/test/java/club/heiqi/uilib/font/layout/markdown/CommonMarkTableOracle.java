package club.heiqi.uilib.font.layout.markdown;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/** 独立 Table 参考侧；不改变现行行接缝 oracle 的扩展、语料或归一。 */
public final class CommonMarkTableOracle {

    private static final Iterable<Extension> EXTENSIONS = Arrays.asList(
        TablesExtension.create(), StrikethroughExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer HTML = HtmlRenderer.builder().extensions(EXTENSIONS).build();

    private CommonMarkTableOracle() {}

    public static Node parse(String markdown) {
        return PARSER.parse(markdown);
    }

    public static String html(String markdown) {
        return HTML.render(parse(markdown));
    }

    public static Map<String, String> corpus() {
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("basic-align", "| a | b | c | d |\n| - | :- | :-: | -: |\n| 1 | 2 | 3 | 4 |");
        cases.put("header-only", "a|b\n-|-");
        cases.put("single-no-pipe", "a\n---\nb");
        cases.put("single-header-pipe", "| a |\n---\nb");
        cases.put("single-delimiter-pipe", "a\n|---|\nb");
        cases.put("setext-multi", "a|b\n---\nx|y");
        cases.put("paragraph-prefix", "before\na|b\n-|-\nx|y");
        cases.put("blank-prefix", "before\n\na|b\n-|-\nx|y");
        cases.put("header-too-few", "a\n-|-\nx|y");
        cases.put("header-too-many", "a|b|c\n-|-\nx|y");
        cases.put("body-short-long", "a|b\n-|-\nx\ny|z|discard\nplain\n\nafter");
        cases.put("body-pipe-empty", "a|b\n-|-\n|\n||\n| |");
        cases.put("body-no-pipe", "a|b\n-|-\nplain\nx|y");
        cases.put("body-heading", "a|b\n-|-\n# stop\nx|y");
        cases.put("body-thematic", "a|b\n-|-\n---\nx|y");
        cases.put("quote-table", "> a|b\n> -|-\n> x|y");
        cases.put("nested-quote-blank-tables", "> > a|b\n> > -|-\n\n> > c|d\n> > -|-");
        cases.put("quote-lazy-delimiter", "> a|b\n-|-\nx|y");
        cases.put("quote-lazy-body", "> a|b\n> -|-\nx|y");
        cases.put("quote-lazy-header", "> before\na|b\n> -|-\n> x|y");
        cases.put("quote-lazy-pipe-paragraph", "> before\nx|y\n> z");
        cases.put("quote-unmarked-blank", "> a|b\n> -|-\n\n> x|y");
        cases.put("list-table", "- a|b\n  -|-\n  x|y");
        cases.put("list-lazy-delimiter", "- a|b\n-|-\nx|y");
        cases.put("escaped-pipe", "a|b\n-|-\nx\\|y|z");
        cases.put("escaped-code-pipe", "a|b\n-|-\n`x\\|y`|z");
        cases.put("bare-code-pipe", "a|b\n-|-\n`x|y`|z");
        cases.put("double-backslash-pipe", "a|b\n-|-\nx\\\\|y|z");
        cases.put("triple-backslash-pipe", "a|b\n-|-\nx\\\\\\|y|z");
        cases.put("escaped-header-pipe", "a\\|b|c\n-|-\nx|y");
        cases.put("escaped-delimiter-pipe", "a|b\n-\\|-\nx|y");
        cases.put("delimiter-spaces", "a|b\n- -|---\nx|y");
        cases.put("delimiter-colon-only", "a|b\n:|:--:\nx|y");
        cases.put("indent-three", "   a|b\n   -|-\n   x|y");
        cases.put("indent-four", "    a|b\n    -|-\n    x|y");
        cases.put("inline", "a|b\n-|-\n**bold** ~~strike~~ `code` *italic* [link](https://example.test)|§a plain");
        cases.put("inline-cross-cell", "a|b\n-|-\n**left|right**");
        cases.put("multiple-tables", "a|b\n-|-\nx|y\n\nc|d\n-|-\nz|w");
        cases.put("single-dash", "|a|\n-");
        cases.put("single-colon", "|a|\n:-");
        cases.put("single-outer", "|a|\n|-|");
        cases.put("body-empty-double", "a|b\n-| -\n||");
        cases.put("body-empty-space", "a|b\n-| -\n| |");
        cases.put("body-bullet", "a|b\n-|-\n- item");
        cases.put("body-ordered-two", "a|b\n-|-\n2. item");
        cases.put("body-quote", "a|b\n-|-\n> quote");
        cases.put("body-fence", "a|b\n-|-\n```\ncode\n```");
        cases.put("body-indent-four", "a|b\n-|-\n    x|y");
        cases.put("quote-lazy-marked", "> a|b\n> -|-\nplain\n> x|y");
        cases.put("quote-blank-second-table", "> a|b\n> -|-\n\n> c|d\n> -|-");
        cases.put("double-backslash-code-pipe", "a|b\n-|-\n`x\\\\|y`|z");
        cases.put("triple-backslash-code-pipe", "a|b\n-|-\n`x\\\\\\|y`|z");
        cases.put("emphasis-escaped-pipe", "a|b\n-|-\n**x\\|y**|z");
        cases.put("emphasis-double-backslash-pipe", "a|b\n-|-\n**x\\\\|y**|z");
        cases.put("link-label-escaped-pipe", "a|b\n-|-\n[x\\|y](https://example.test)|z");
        cases.put("link-label-double-backslash-pipe", "a|b\n-|-\n[x\\\\|y](https://example.test)|z");
        cases.put("link-destination-escaped-pipe", "a|b\n-|-\n[x](https://example.test/a\\|b)|z");
        return cases;
    }

    public static void main(String[] args) {
        for (Map.Entry<String, String> sample : corpus().entrySet()) {
            System.out.println("CASE " + sample.getKey());
            System.out.println("SOURCE " + sample.getValue().replace("\\", "\\\\").replace("\n", "\\n"));
            System.out.print(html(sample.getValue()));
            printTree(parse(sample.getValue()), "");
        }
        System.out.println("CASES=" + corpus().size());
    }

    private static void printTree(Node node, String indent) {
        System.out.println(indent + node.toString());
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            printTree(child, indent + "  ");
        }
    }
}
