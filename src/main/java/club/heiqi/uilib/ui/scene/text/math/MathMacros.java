package club.heiqi.uilib.ui.scene.text.math;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 显式不可变文档宏上下文；创建新文档使用 empty，公式结果通过 nextMacros 串联。 */
public final class MathMacros {
    static final class Definition {
        final List<MathLexer.Token> body;
        final int numArgs;
        final List<List<String>> delimiters;
        final String function;
        final boolean unexpandable;

        Definition(
                List<MathLexer.Token> body,
                int args,
                List<List<String>> delimiters,
                String function,
                boolean unexpandable) {
            this.body = Collections.unmodifiableList(new ArrayList<MathLexer.Token>(body));
            this.numArgs = args;
            List<List<String>> ds = new ArrayList<List<String>>();
            if (delimiters != null)
                for (List<String> d : delimiters)
                    ds.add(Collections.unmodifiableList(new ArrayList<String>(d)));
            this.delimiters = Collections.unmodifiableList(ds);
            this.function = function;
            this.unexpandable = unexpandable;
        }

        static Definition string(String value) {
            // 固定 KaTeX 使用连续出现的 #1、#2… 计数，先移除 ##，不是最大 #n。
            String stripped = value.replace("##", "");
            int n = 0;
            while (n < 9 && stripped.contains("#" + (n + 1))) n++;
            return new Definition(MathLexer.lex(value), n, null, null, false);
        }
    }

    final Map<String, Definition> definitions;

    MathMacros(Map<String, Definition> definitions) {
        this.definitions =
                Collections.unmodifiableMap(new LinkedHashMap<String, Definition>(definitions));
    }

    public static MathMacros empty() {
        return new MathMacros(Collections.<String, Definition>emptyMap());
    }

    public static MathMacros of(Map<String, String> macros) {
        Objects.requireNonNull(macros, "macros");
        Map<String, Definition> values = new LinkedHashMap<String, Definition>();
        for (Map.Entry<String, String> entry : macros.entrySet())
            values.put(
                    Objects.requireNonNull(entry.getKey(), "name"),
                    Definition.string(entry.getValue()));
        return new MathMacros(values);
    }

    public boolean contains(String name) {
        return definitions.containsKey(name);
    }

    public int size() {
        return definitions.size();
    }
}
