package club.heiqi.uilib.ui.scene.layout;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 一个节点在本次呈现测量、最终排列及备用捕获共享的布局请求。
 * 不跨呈现代际保留结果；字体/声明失效仍由既有管线生成下一批。
 */
final class BatchTextMeasurer implements SceneTextMeasurer {
    private final SceneTextMeasurer delegate;
    private final Map<Request, TextLayout> layouts = new HashMap<>();

    BatchTextMeasurer(SceneTextMeasurer delegate) {
        this.delegate = delegate;
    }

    @Override
    public TextLayout layout(String text, TextStyle style, int width) {
        Request request = new Request(text, style, width, null);
        return layouts.computeIfAbsent(request, key -> delegate.layout(text, style, width));
    }

    @Override
    public TextLayout layout(String text, TextStyle style, int width, TextPolicy policy) {
        Request request = new Request(text, style, width, policy);
        return layouts.computeIfAbsent(request, key -> delegate.layout(text, style, width, policy));
    }

    @Override
    public TextLayout layout(AttributedText text, float width, TextPolicy policy) {
        Request request = new Request(text, null, width, policy);
        return layouts.computeIfAbsent(request, key -> delegate.layout(text, width, policy));
    }

    private static final class Request {
        final Object content;
        final TextStyle style;
        final double width;
        final TextPolicy policy;

        Request(Object content, TextStyle style, double width, TextPolicy policy) {
            this.content = content;
            this.style = style;
            this.width = width;
            this.policy = policy;
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof Request)) return false;
            Request other = (Request) value;
            // AttributedText 不定义值相等：同批声明派生的不可变实例就是事实身份。
            return content.equals(other.content)
                    && Objects.equals(style, other.style)
                    && Double.compare(width, other.width) == 0
                    && Objects.equals(policy, other.policy);
        }

        @Override
        public int hashCode() {
            return Objects.hash(content, style, width, policy);
        }
    }
}
