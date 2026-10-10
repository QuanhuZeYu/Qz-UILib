package club.heiqi.uilib.ui.scene.text.math;

/** 环境合同按固定 33 登记项分族，模式资格留给唯一布局入口。 */
final class MathEnvironmentSpec {
    final String name, style, separation, left, right;
    final boolean displayRequired,
            inlineAutoDisplay,
            addJot,
            outerSpacing,
            singleRow,
            tags,
            automaticTags;
    final double stretch;
    final int maxColumns;

    private MathEnvironmentSpec(
            String name,
            String style,
            String separation,
            String left,
            String right,
            boolean display,
            boolean autoDisplay,
            boolean jot,
            boolean outer,
            boolean single,
            boolean tags,
            boolean automatic,
            double stretch,
            int maxColumns) {
        this.name = name;
        this.style = style;
        this.separation = separation;
        this.left = left;
        this.right = right;
        this.displayRequired = display;
        this.inlineAutoDisplay = autoDisplay;
        this.addJot = jot;
        this.outerSpacing = outer;
        this.singleRow = single;
        this.tags = tags;
        this.automaticTags = automatic;
        this.stretch = stretch;
        this.maxColumns = maxColumns;
    }

    static MathEnvironmentSpec find(String name) {
        if (MathCommandCatalog.findEnvironment(name) == null) return null;
        String base = name.replace("*", "");
        boolean starred = name.endsWith("*");
        if (base.endsWith("matrix")) {
            String left = null, right = null, style = "text", separation = "matrix";
            double stretch = 1;
            if (base.equals("pmatrix")) {
                left = "(";
                right = ")";
            }
            if (base.equals("bmatrix")) {
                left = "[";
                right = "]";
            }
            if (base.equals("Bmatrix")) {
                left = "{";
                right = "}";
            }
            if (base.equals("vmatrix")) {
                left = "|";
                right = "|";
            }
            if (base.equals("Vmatrix")) {
                left = "‖";
                right = "‖";
            }
            if (base.equals("smallmatrix")) {
                style = "script";
                separation = "small";
                stretch = .5;
            }
            return new MathEnvironmentSpec(
                    name,
                    style,
                    separation,
                    left,
                    right,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    stretch,
                    0);
        }
        if (base.endsWith("cases")) {
            boolean r = base.contains("r");
            return new MathEnvironmentSpec(
                    name,
                    base.startsWith("d") ? "display" : "text",
                    "cases",
                    r ? "." : "{",
                    r ? "}" : ".",
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    1.2,
                    0);
        }
        if (base.equals("array") || base.equals("darray") || base.equals("subarray")) {
            boolean sub = base.equals("subarray");
            return new MathEnvironmentSpec(
                    name,
                    sub ? "script" : base.startsWith("d") ? "display" : "text",
                    sub ? "subarray" : "array",
                    null,
                    null,
                    false,
                    false,
                    false,
                    !sub,
                    false,
                    false,
                    false,
                    sub ? .5 : 1,
                    sub ? 1 : 0);
        }
        boolean aligned = base.equals("aligned") || base.equals("alignedat");
        boolean align =
                base.equals("align") || base.equals("alignat") || aligned || base.equals("split");
        boolean gathered = base.equals("gathered");
        String separation =
                align
                        ? base.contains("at") ? "alignat" : "align"
                        : base.equals("CD")
                                ? "CD"
                                : base.equals("equation") ? "equation" : "gather";
        boolean single = base.equals("equation");
        boolean tags = !aligned && !gathered && !base.equals("split") && !base.equals("CD");
        return new MathEnvironmentSpec(
                name,
                "display",
                separation,
                null,
                null,
                !aligned && !gathered,
                !starred
                        && (base.equals("align")
                                || base.equals("alignat")
                                || base.equals("equation")
                                || base.equals("gather")
                                || base.equals("CD")),
                !single,
                false,
                single,
                tags,
                tags && !starred,
                1,
                single ? 1 : base.equals("split") ? 2 : 0);
    }
}
