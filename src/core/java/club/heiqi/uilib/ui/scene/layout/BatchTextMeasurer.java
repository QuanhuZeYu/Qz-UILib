package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 同节点有效测量代际内复用精确文字请求；不保留同批子测量入口。 */
final class BatchTextMeasurer implements SceneTextMeasurer {
    private final SceneTextMeasurer delegate;
    private static final int MAX_REQUESTS = 16;
    private final Map<Request, TextLayout> layouts = new LinkedHashMap<>(16, 0.75f, true);
    private boolean revisionKnown;
    private long measureRevision;
    private final Set<Request> activeRequests = new HashSet<>();

    BatchTextMeasurer(SceneTextMeasurer delegate) {
        this.delegate = delegate;
    }

    void prepareRevision(long revision) {
        if (!revisionKnown || measureRevision != revision) resetRequests();
        revisionKnown = true;
        measureRevision = revision;
    }

    private void remember(Request request, TextLayout layout) {
        layouts.put(request, layout);
        // 活跃集合也随淘汰收缩，控件在一批内发出许多约束时不能旁路容量边界。
        while (layouts.size() > MAX_REQUESTS) {
            Request eldest = layouts.keySet().iterator().next();
            layouts.remove(eldest);
            activeRequests.remove(eldest);
        }
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
    public TextStyle resolveStyle(TextStyle style) { return delegate.resolveStyle(style); }

    @Override
    public club.heiqi.uilib.ui.scene.text.math.MathFont mathFont(TextStyle style) {
        return delegate.mathFont(style);
    }

    @Override
    public TextLayout layout(String text, TextStyle style, int width) {
        Request request = new Request(text, style, width, null);
        activeRequests.add(request);
        TextLayout cached = layouts.get(request);
        if (cached != null) return cached.withColors(style.color, offset -> style.color);
        TextLayout layout = delegate.layout(text, style, width);
        remember(request, layout);
        return layout;
    }

    @Override
    public TextLayout layout(String text, TextStyle style, int width, TextPolicy policy) {
        Request request = new Request(text, style, width, policy);
        activeRequests.add(request);
        TextLayout cached = layouts.get(request);
        if (cached != null) return cached.withColors(style.color, offset -> style.color);
        TextLayout layout = delegate.layout(text, style, width, policy);
        remember(request, layout);
        return layout;
    }

    @Override
    public TextLayout layout(AttributedText text, float width, TextPolicy policy) {
        Request request = new Request(text, null, width, policy);
        activeRequests.add(request);
        TextLayout cached = layouts.get(request);
        if (cached != null) return cached.withColors(text.baseStyle.color, offset -> text.styleAt(offset).color);
        TextLayout layout = delegate.layout(text, width, policy);
        remember(request, layout);
        return layout;
    }

    private static final class Request {
        final Object content;
        final TextStyle style;
        final double width;
        final TextPolicy policy;

        Request(Object content, TextStyle style, double width, TextPolicy policy) {
            this.content = content instanceof AttributedText ? ((AttributedText) content).positionKey() : content;
            this.style = style == null ? null : style.withColor(0);
            this.width = width;
            this.policy = policy;
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof Request)) return false;
            Request other = (Request) value;
            // 同一定位请求复用度量；前景颜色由调用时的当前声明重新绑定。
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
