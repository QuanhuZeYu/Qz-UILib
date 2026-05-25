package club.heiqi.uilib.font;

import java.util.List;

import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.config.FontConfig;

/**
 * 原版 FontRenderer 注入层的 UILib 字体管线调用器。
 */
public final class FontRendererFallbackInvoker {

    private static final FontRendererFallbackInvoker INSTANCE = new FontRendererFallbackInvoker();
    private static final InvocationResult<?> UNHANDLED = new InvocationResult<Object>(false, null);

    private boolean fontPipelineFailureLogged;

    private FontRendererFallbackInvoker() {}

    /**
     * 返回调用器单例。
     *
     * @return 单例
     */
    public static FontRendererFallbackInvoker getInstance() {
        return INSTANCE;
    }

    /**
     * 在需要替换原版字体时预热适配器。
     */
    public void warmUpAdapterIfNeeded() {
        if (FontConfig.replaceOrigin) {
            DefaultFontRendererAdapter.getInstance();
        }
    }

    /**
     * 尝试由 UILib 字体管线绘制自动换行文本。
     *
     * @param text 文本
     * @param x X 坐标
     * @param y Y 坐标
     * @param wrapWidth 换行宽度
     * @param textColor 文本颜色
     * @return 是否已接管调用
     */
    public boolean drawSplitString(String text, int x, int y, int wrapWidth, int textColor) {
        if (!FontConfig.replaceOrigin) {
            return false;
        }
        try {
            DefaultFontRendererAdapter.getInstance().drawSplitString(text, x, y, wrapWidth, textColor);
            return true;
        } catch (RuntimeException exception) {
            logFontPipelineFailure(exception);
            return false;
        }
    }

    /**
     * 尝试由 UILib 字体管线绘制文本。
     *
     * @param text 文本
     * @param x X 坐标
     * @param y Y 坐标
     * @param color 文本颜色
     * @param dropShadow 是否绘制阴影
     * @return 调用结果
     */
    public InvocationResult<Integer> drawString(String text, int x, int y, int color, boolean dropShadow) {
        if (!FontConfig.replaceOrigin) {
            return InvocationResult.unhandled();
        }
        try {
            int width = DefaultFontRendererAdapter.getInstance().drawString(text, x, y, color, dropShadow);
            applyVanillaDrawStringAlphaSideEffect();
            return InvocationResult.handled(Integer.valueOf(width));
        } catch (RuntimeException exception) {
            logFontPipelineFailure(exception);
            return InvocationResult.unhandled();
        }
    }

    /**
     * 尝试计算字符串宽度。
     *
     * @param text 文本
     * @return 调用结果
     */
    public InvocationResult<Integer> getStringWidth(String text) {
        if (!FontConfig.replaceOrigin) {
            return InvocationResult.unhandled();
        }
        try {
            return InvocationResult.handled(DefaultFontRendererAdapter.getInstance().getStringWidth(text));
        } catch (RuntimeException exception) {
            logFontPipelineFailure(exception);
            return InvocationResult.unhandled();
        }
    }

    /**
     * 尝试按宽度拆分格式化字符串。
     *
     * @param text 文本
     * @param wrapWidth 换行宽度
     * @return 调用结果
     */
    public InvocationResult<List<String>> listFormattedStringToWidth(String text, int wrapWidth) {
        if (!FontConfig.replaceOrigin) {
            return InvocationResult.unhandled();
        }
        try {
            return InvocationResult.handled(DefaultFontRendererAdapter.getInstance()
                    .listFormattedStringToWidth(text, wrapWidth));
        } catch (RuntimeException exception) {
            logFontPipelineFailure(exception);
            return InvocationResult.unhandled();
        }
    }

    /**
     * 尝试计算拆分字符串后的宽度。
     *
     * @param text 文本
     * @param wrapWidth 换行宽度
     * @return 调用结果
     */
    public InvocationResult<Integer> splitStringWidth(String text, int wrapWidth) {
        if (!FontConfig.replaceOrigin) {
            return InvocationResult.unhandled();
        }
        try {
            return InvocationResult.handled(DefaultFontRendererAdapter.getInstance().splitStringWidth(text, wrapWidth));
        } catch (RuntimeException exception) {
            logFontPipelineFailure(exception);
            return InvocationResult.unhandled();
        }
    }

    /**
     * 尝试按宽度裁剪字符串。
     *
     * @param text 文本
     * @param width 最大宽度
     * @return 调用结果
     */
    public InvocationResult<String> trimStringToWidth(String text, int width) {
        if (!FontConfig.replaceOrigin) {
            return InvocationResult.unhandled();
        }
        try {
            return InvocationResult.handled(DefaultFontRendererAdapter.getInstance().trimStringToWidth(text, width));
        } catch (RuntimeException exception) {
            logFontPipelineFailure(exception);
            return InvocationResult.unhandled();
        }
    }

    /**
     * 尝试按宽度裁剪字符串。
     *
     * @param text 文本
     * @param width 最大宽度
     * @param reverse 是否反向裁剪
     * @return 调用结果
     */
    public InvocationResult<String> trimStringToWidth(String text, int width, boolean reverse) {
        if (!FontConfig.replaceOrigin) {
            return InvocationResult.unhandled();
        }
        try {
            return InvocationResult.handled(DefaultFontRendererAdapter.getInstance()
                    .trimStringToWidth(text, width, reverse));
        } catch (RuntimeException exception) {
            logFontPipelineFailure(exception);
            return InvocationResult.unhandled();
        }
    }

    private synchronized void logFontPipelineFailure(RuntimeException exception) {
        if (fontPipelineFailureLogged) {
            return;
        }
        fontPipelineFailureLogged = true;
        MyMod.LOG.error("UILib 字体管线接管失败，本次调用回落原版 FontRenderer。", exception);
    }

    /**
     * 保留原版 drawString 会对调用链可见的最小固定管线副作用。
     */
    private static void applyVanillaDrawStringAlphaSideEffect() {
        GL11.glEnable(GL11.GL_ALPHA_TEST);
    }

    /**
     * 字体管线接管调用结果。
     *
     * @param <T> 返回值类型
     */
    public static final class InvocationResult<T> {

        private final boolean handled;
        private final T value;

        private InvocationResult(boolean handled, T value) {
            this.handled = handled;
            this.value = value;
        }

        /**
         * 创建已处理结果。
         *
         * @param value 返回值
         * @param <T> 返回值类型
         * @return 已处理结果
         */
        public static <T> InvocationResult<T> handled(T value) {
            return new InvocationResult<T>(true, value);
        }

        /**
         * 返回未处理结果。
         *
         * @param <T> 返回值类型
         * @return 未处理结果
         */
        @SuppressWarnings("unchecked")
        public static <T> InvocationResult<T> unhandled() {
            return (InvocationResult<T>) UNHANDLED;
        }

        /**
         * 判断调用是否已被 UILib 字体管线接管。
         *
         * @return 是否已处理
         */
        public boolean isHandled() {
            return handled;
        }

        /**
         * 返回接管后的返回值。
         *
         * @return 返回值
         */
        public T getValue() {
            return value;
        }
    }
}
