package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 同节点、同字体度量代际的测量入口；本次排列及备用捕获共享请求。 纯视图位置重排复用当前请求；测量输入换代前清空，不积累动态内容。 */
final class BatchTextMeasurer implements SceneTextMeasurer, SceneChildMeasurer {
    private final SceneTextMeasurer delegate;
    private final java.util.function.BiFunction<
                    club.heiqi.uilib.ui.scene.node.SceneNode, Constraints, MeasuredSize>
            children;
    private final Map<Request, TextLayout> layouts = new HashMap<>();
    private final Set<Request> activeRequests = new HashSet<>();

    BatchTextMeasurer(
            SceneTextMeasurer delegate,
            java.util.function.BiFunction<
                            club.heiqi.uilib.ui.scene.node.SceneNode, Constraints, MeasuredSize>
                    children) {
        this.delegate = delegate;
        this.children = children;
    }

    @Override
    public MeasuredSize measureChild(
            club.heiqi.uilib.ui.scene.node.SceneNode child, Constraints constraints) {
        return children.apply(child, constraints);
    }

    void resetRequests() {
        layouts.clear();
        activeRequests.clear();
    }

    void beginRequests() {
        activeRequests.clear();
    }

    void endRequests() {
        // 自定义控件可每次创建新的属性文本，保留本次实际请求而非整代历史。
        layouts.keySet().retainAll(activeRequests);
        activeRequests.clear();
    }

    @Override
    public club.heiqi.uilib.ui.scene.text.math.MathFont mathFont(TextStyle style) {
        return delegate.mathFont(style);
    }

    @Override
    public TextLayout layout(String text, TextStyle style, int width) {
        Request request = new Request(text, style, width, null);
        activeRequests.add(request);
        return layouts.computeIfAbsent(request, key -> delegate.layout(text, style, width));
    }

    @Override
    public TextLayout layout(String text, TextStyle style, int width, TextPolicy policy) {
        Request request = new Request(text, style, width, policy);
        activeRequests.add(request);
        return layouts.computeIfAbsent(request, key -> delegate.layout(text, style, width, policy));
    }

    @Override
    public TextLayout layout(AttributedText text, float width, TextPolicy policy) {
        Request request = new Request(text, null, width, policy);
        activeRequests.add(request);
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
