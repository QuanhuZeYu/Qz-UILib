package club.heiqi.uilib.ui.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 物理像素圆角带覆盖率。只扫描条带端点，不遍历挖空区域或填充跨度内的像素。 */
final class UiRoundedBandRasterizer {
    // 沿物理像素 Y 积分，X 方向用解析跨度的精确交集；与 logical px 密度无关。
    private static final int SAMPLES = 8;

    private UiRoundedBandRasterizer() {}

    interface SpanConsumer {
        void fillRect(int left, int top, int right, int bottom, int color);
    }

    static void draw(UiRenderBackend backend, int left, int top, int right, int bottom,
            int[] outerValues, int[] innerValues, int[] boundsValues, int[] colors, float scale) {
        draw((SpanConsumer) backend::fillRect, left, top, right, bottom,
                outerValues, innerValues, boundsValues, colors, scale);
    }

    static void draw(SpanConsumer backend, int left, int top, int right, int bottom,
            int[] outerValues, int[] innerValues, int[] boundsValues, int[] colors, float scale) {
        if (!(scale > 0) || !Float.isFinite(scale) || left >= right || top >= bottom) return;
        double clipLeft = left * (double) scale;
        double clipTop = top * (double) scale;
        double clipRight = right * (double) scale;
        double clipBottom = bottom * (double) scale;
        Shape outer = new Shape(outerValues, clipLeft, clipTop, scale);
        Shape inner = innerValues == null ? null : new Shape(innerValues, clipLeft, clipTop, scale);
        Shape bounds = boundsValues == null ? null : new Shape(boundsValues, clipLeft, clipTop, scale);
        List<Span> previous = new ArrayList<Span>();
        int firstRow = (int) Math.floor(clipTop);
        int runTop = firstRow;
        int lastRow = (int) Math.ceil(clipBottom);
        for (int y = firstRow; y < lastRow; y++) {
            TreeMap<Integer, double[]> events = new TreeMap<Integer, double[]>();
            for (int sample = 0; sample < SAMPLES; sample++) {
                double sampleTop = Math.max(Math.max(clipTop, outer.top), y + sample / (double) SAMPLES);
                double sampleBottom = Math.min(Math.min(clipBottom, outer.bottom),
                        y + (sample + 1) / (double) SAMPLES);
                if (bounds != null) {
                    sampleTop = Math.max(sampleTop, bounds.top);
                    sampleBottom = Math.min(sampleBottom, bounds.bottom);
                }
                // 分数缩放的直边也须精确裁开；不能用子样本中心把整段误判进/出内孔。
                while (sampleTop < sampleBottom) {
                    double end = sampleBottom;
                    if (inner != null) {
                        if (sampleTop < inner.top) end = Math.min(end, inner.top);
                        else if (sampleTop < inner.bottom) end = Math.min(end, inner.bottom);
                    }
                    double py = (sampleTop + end) * 0.5;
                    double l = Math.max(clipLeft, outer.leftAt(py));
                    double r = Math.min(clipRight, outer.rightAt(py));
                    if (bounds != null) {
                        l = Math.max(l, bounds.leftAt(py));
                        r = Math.min(r, bounds.rightAt(py));
                    }
                    double weight = end - sampleTop;
                    if (inner == null || !inner.hasRow(py)) {
                        int color = inner == null || py < inner.top ? colors[0] : colors[2];
                        addSpan(events, l, r, color, weight);
                    } else {
                        addSpan(events, l, Math.min(r, inner.leftAt(py)), colors[1], weight);
                        addSpan(events, Math.max(l, inner.rightAt(py)), r, colors[3], weight);
                    }
                    sampleTop = end;
                }
            }
            List<Span> current = resolve(events);
            if (!same(previous, current)) {
                flush(backend, previous, runTop, y);
                previous = current;
                runTop = y;
            }
        }
        flush(backend, previous, runTop, lastRow);
    }

    private static void addSpan(TreeMap<Integer, double[]> events, double left, double right,
            int color, double weight) {
        if (left >= right || (color >>> 24) == 0) return;
        int first = (int) Math.floor(left);
        int last = (int) Math.floor(right);
        if (first == last) {
            addRange(events, first, first + 1, color, weight * (right - left));
            return;
        }
        addRange(events, first, first + 1, color, weight * (first + 1 - left));
        addRange(events, first + 1, last, color, weight);
        addRange(events, last, last + 1, color, weight * (right - last));
    }

    private static void addRange(TreeMap<Integer, double[]> events, int left, int right,
            int color, double coverage) {
        if (left >= right || coverage <= 0) return;
        double alpha = (color >>> 24) * coverage;
        addEvent(events, left, color, alpha);
        addEvent(events, right, color, -alpha);
    }

    private static void addEvent(TreeMap<Integer, double[]> events, int x, int color, double alpha) {
        double[] delta = events.get(x);
        if (delta == null) {
            delta = new double[4];
            events.put(x, delta);
        }
        delta[0] += alpha;
        delta[1] += ((color >>> 16) & 255) * alpha;
        delta[2] += ((color >>> 8) & 255) * alpha;
        delta[3] += (color & 255) * alpha;
    }

    private static List<Span> resolve(TreeMap<Integer, double[]> events) {
        List<Span> result = new ArrayList<Span>();
        double[] sum = new double[4];
        int left = 0;
        for (Map.Entry<Integer, double[]> event : events.entrySet()) {
            int right = event.getKey();
            int alpha = Math.min(255, (int) Math.round(sum[0]));
            if (alpha > 0 && left < right) {
                int color = (alpha << 24) | (channel(sum[1], sum[0]) << 16)
                        | (channel(sum[2], sum[0]) << 8) | channel(sum[3], sum[0]);
                Span last = result.isEmpty() ? null : result.get(result.size() - 1);
                if (last != null && last.right == left && last.color == color) last.right = right;
                else result.add(new Span(left, right, color));
            }
            double[] delta = event.getValue();
            for (int i = 0; i < sum.length; i++) sum[i] += delta[i];
            left = right;
        }
        return result;
    }

    private static int channel(double premultiplied, double alpha) {
        return Math.max(0, Math.min(255, (int) Math.round(premultiplied / alpha)));
    }

    private static boolean same(List<Span> a, List<Span> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Span x = a.get(i);
            Span y = b.get(i);
            if (x.left != y.left || x.right != y.right || x.color != y.color) return false;
        }
        return true;
    }

    private static void flush(SpanConsumer backend, List<Span> spans, int top, int bottom) {
        if (top >= bottom) return;
        for (Span span : spans) backend.fillRect(span.left, top, span.right, bottom, span.color);
    }

    private static final class Span {
        final int left;
        int right;
        final int color;

        Span(int left, int right, int color) {
            this.left = left;
            this.right = right;
            this.color = color;
        }
    }

    private static final class Shape {
        final double left;
        final double top;
        final double right;
        final double bottom;
        final double tl;
        final double tr;
        final double br;
        final double bl;

        Shape(int[] values, double originX, double originY, double scale) {
            left = originX + values[0] * scale;
            top = originY + values[1] * scale;
            right = originX + values[2] * scale;
            bottom = originY + values[3] * scale;
            // paint 或调用方已按各自规则归一化半径；不能再强压到短边一半，
            // 否则会破坏普通表面合法的 CSS 相邻边 scaleToFit 非对称圆角。
            tl = Math.max(0, values[4] * scale);
            tr = Math.max(0, values[5] * scale);
            br = Math.max(0, values[6] * scale);
            bl = Math.max(0, values[7] * scale);
        }

        boolean hasRow(double y) { return y >= top && y < bottom; }

        double leftAt(double y) {
            return left + Math.max(inset(tl, y - top), inset(bl, bottom - y));
        }

        double rightAt(double y) {
            return right - Math.max(inset(tr, y - top), inset(br, bottom - y));
        }

        private static double inset(double radius, double distance) {
            if (radius <= 0 || distance >= radius) return 0;
            double dy = radius - distance;
            return radius - Math.sqrt(Math.max(0, radius * radius - dy * dy));
        }
    }
}
