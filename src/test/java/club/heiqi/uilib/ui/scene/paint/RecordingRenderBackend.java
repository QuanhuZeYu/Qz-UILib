package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;

/**
 * 记录型渲染后端 —— {@link UiRenderBackend} 的纯 mock 实现，零 GL 副作用。
 *
 * <p>每条接口调用被快照为一个 {@link RenderCall}（方法名 + 参数值），追加到内部列表。
 * 不画任何图，只记录。用于契约测试端到端验证 scene 核心（paint engine → replayer）
 * 纯靠 {@link UiRenderBackend} 接口方法工作，不依赖任何 GL 语义或 UiRenderContext 特有
 * 能力——这是「换渲染后端零改动」承诺的测试锚点（宪章信条六）。</p>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>{@link RenderCall} 用通用结构存参数：{@code methodName} + {@code Object[] args}，
 *       primitive 自动装箱。提供 typed getter（{@link RenderCall#getInt} 等）便于断言。</li>
 *   <li>参数数组防御性拷贝：构造时克隆入参，accessor 返回克隆，避免外部篡改记录。</li>
 *   <li>所有方法体只记录不画图（零副作用），可在纯 JUnit 沙箱运行。</li>
 * </ul>
 */
public class RecordingRenderBackend implements UiRenderBackend {

    /** 按调用顺序记录的渲染调用快照 */
    private final List<RenderCall> calls = new ArrayList<RenderCall>();

    /** 记录一条调用 */
    private void record(String methodName, Object... args) {
        calls.add(new RenderCall(methodName, args));
    }

    // ==================== UiRenderBackend 实现（只记录不画图） ====================

    @Override
    public void fillRect(int left, int top, int right, int bottom, int color) {
        record("fillRect", left, top, right, bottom, color);
    }

    @Override
    public void drawImage(SceneImageSource source, int left, int top, int right, int bottom) {
        record("drawImage", source, left, top, right, bottom);
    }

    @Override
    public void drawSurface(int left, int top, int right, int bottom, int fillColor, int borderColor,
            int cornerRadius) {
        record("drawSurface", left, top, right, bottom, fillColor, borderColor, cornerRadius);
    }

    @Override
    public void drawBorder(int left, int top, int right, int bottom, int color) {
        record("drawBorder", left, top, right, bottom, color);
    }

    @Override
    public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {
        record("pushClip", left, top, right, bottom, cornerRadius);
    }

    @Override
    public void popClip() {
        record("popClip");
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow) {
        record("drawText", text, x, y, color, shadow);
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow, int fontSizePx) {
        record("drawText", text, x, y, color, shadow, fontSizePx);
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow, int fontSizePx, int textMode) {
        record("drawText", text, x, y, color, shadow, fontSizePx, textMode);
    }

    @Override
    public void pushGroupOpacity(int left, int top, int right, int bottom, float opacity) {
        record("pushGroupOpacity", left, top, right, bottom, opacity);
    }

    @Override
    public void popGroupOpacity() {
        record("popGroupOpacity");
    }

    @Override
    public void pushTransform(float translateX, float translateY, float rotateDegrees,
            float scaleX, float scaleY, float originXRatio, float originYRatio,
            int left, int top, int right, int bottom) {
        record("pushTransform", translateX, translateY, rotateDegrees,
                scaleX, scaleY, originXRatio, originYRatio,
                left, top, right, bottom);
    }

    @Override
    public void popTransform() {
        record("popTransform");
    }

    @Override
    public void pushTransformLayer(float translateX, float translateY, float rotateDegrees,
            float scaleX, float scaleY, float originXRatio, float originYRatio,
            int left, int top, int right, int bottom) {
        record("pushTransformLayer", translateX, translateY, rotateDegrees,
                scaleX, scaleY, originXRatio, originYRatio,
                left, top, right, bottom);
    }

    @Override
    public void popTransformLayer() {
        record("popTransformLayer");
    }

    // ==================== 记录访问 API ====================

    /**
     * 返回按调用顺序排列的不可变调用记录列表。
     *
     * @return 不可变列表
     */
    public List<RenderCall> getCalls() {
        return Collections.unmodifiableList(calls);
    }

    /**
     * 返回指定索引处的调用记录。
     *
     * @param index 索引
     * @return 调用记录
     */
    public RenderCall getCall(int index) {
        return calls.get(index);
    }

    /**
     * 返回已记录的调用总数。
     *
     * @return 调用总数
     */
    public int getCallCount() {
        return calls.size();
    }

    /**
     * 返回按调用顺序排列的方法名列表（便于做调用序列顺序断言）。
     *
     * @return 方法名列表
     */
    public List<String> getMethodNames() {
        List<String> names = new ArrayList<String>(calls.size());
        for (RenderCall c : calls) {
            names.add(c.methodName());
        }
        return names;
    }

    /**
     * 清空所有记录（跨测试复用同一 backend 实例时调用）。
     */
    public void clear() {
        calls.clear();
    }

    // ==================== RenderCall 值类 ====================

    /**
     * 单条渲染调用记录：方法名 + 参数快照。
     *
     * <p>参数以 {@code Object[]} 存储（primitive 自动装箱）。提供 typed getter
     * 便于测试断言读取。参数数组防御性拷贝，构造后不可变。</p>
     */
    public static final class RenderCall {
        private final String methodName;
        private final Object[] args;

        /**
         * @param methodName 方法名
         * @param args        参数（primitive 自动装箱；null 视为空数组）
         */
        public RenderCall(String methodName, Object[] args) {
            this.methodName = methodName;
            this.args = args == null ? new Object[0] : args.clone();
        }

        /** @return 方法名 */
        public String methodName() {
            return methodName;
        }

        /** @return 参数数组的防御性拷贝 */
        public Object[] args() {
            return args.clone();
        }

        /** @return 第 i 个参数（int 语义） */
        public int getInt(int i) {
            return ((Number) args[i]).intValue();
        }

        /** @return 第 i 个参数（float 语义） */
        public float getFloat(int i) {
            return ((Number) args[i]).floatValue();
        }

        /** @return 第 i 个参数（boolean 语义） */
        public boolean getBoolean(int i) {
            return (Boolean) args[i];
        }

        /** @return 第 i 个参数（String 语义） */
        public String getString(int i) {
            return (String) args[i];
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(methodName).append('(');
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                Object a = args[i];
                if (a instanceof Float) {
                    sb.append(a).append('f');
                } else if (a instanceof String) {
                    sb.append('"').append(a).append('"');
                } else {
                    sb.append(a);
                }
            }
            return sb.append(')').toString();
        }
    }
}
