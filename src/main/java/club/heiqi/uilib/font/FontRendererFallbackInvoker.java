package club.heiqi.uilib.font;

import java.util.List;

import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.render.FontBatchRenderer;

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
     * <p>本入口是原时机接管路径：MixinFontRenderer 在原版 drawString 调用位置同步完成 flush 后，
     * 补回原版 FontRenderer 对调用链可见的尾状态（见 {@link #applyVanillaDrawStringTailState}）。</p>
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
            FontBatchRenderer batchRenderer = FontService.getInstance().getBatchRenderer();
            long flushSequenceBefore = batchRenderer.getLastFlushSequence();
            int width = DefaultFontRendererAdapter.getInstance().drawBaselineAlignedString(text, x, y, color,
                    dropShadow);
            applyVanillaDrawStringTailState(batchRenderer, flushSequenceBefore, color);
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
     * 在原版 drawString 调用位置补齐原版渲染遗留的尾状态，使接管路径对调用链幂等于原版。
     *
     * <p>幂等基线 = 原版 FontRenderer 本体尾状态：{@code enableAlpha()} 无条件启用 ALPHA_TEST；
     * {@code renderString} 逐字形 setColor（glColor4f）；{@code renderDefaultChar/renderUnicodeChar}
     * 保持最后使用的字体页纹理绑定。三项都在本次 draw 的守卫 pop 之后执行。</p>
     *
     * <p>路径判别：本补丁只存在于 invoker 的同步 drawString 路径（MixinFontRenderer 原时机接管）。
     * 位移时机路径不经过、也不补本尾状态，各自保持守卫还原进入态：</p>
     * <ul>
     *   <li>延迟标签回放（PlayerNameTagRenderCoordinator.runReplayBatch）：coordinator 自身只做
     *       lightmap 配对恢复；回放批次重放的是原版 {@code func_147906_a} 调用链，其内部 drawString
     *       仍走本 invoker 同步路径，coordinator 不额外补尾状态。</li>
     *   <li>HUD（UiHudRenderListener）：经 UiRenderContext 直调 adapter，不经过本 invoker。</li>
     *   <li>deferred flush scope（DefaultFontRendererAdapter.begin/endDeferredFlushScope）：收集与
     *       flush 均在本 invoker 之外，flush 由守卫还原进入态。</li>
     * </ul>
     *
     * <p>末字形色选择：取 FontBatchRenderer flush 侧记录的最后一个字形色（收集侧按字形顺序更新，
     * 已解析 § 颜色码，比 drawString 入口颜色更精确）。仅当本次 flush 有内容但没有任何字形 quad
     * （如纯装饰线）时回落到入口颜色近似，并忽略 § 颜色码末段差异。若本次调用未发生 flush
     * （空文本等），与原版一致不改变颜色与纹理绑定，只保留 ALPHA_TEST enable。</p>
     *
     * @param batchRenderer 字体批渲染器
     * @param flushSequenceBefore draw 开始前的 flush 序号，用于判别本次调用是否发生过 flush
     * @param entryColor drawString 入口颜色，无字形时的近似回落值
     */
    private static void applyVanillaDrawStringTailState(FontBatchRenderer batchRenderer, long flushSequenceBefore,
            int entryColor) {
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        if (batchRenderer.getLastFlushSequence() == flushSequenceBefore) {
            return;
        }
        int lastGlyphColor = batchRenderer.getLastFlushGlyphColor();
        int tailColor = lastGlyphColor != FontBatchRenderer.NO_GLYPH_COLOR ? lastGlyphColor : normalizeAlpha(entryColor);
        float alpha = (float) (tailColor >> 24 & 255) / 255.0F;
        float red = (float) (tailColor >> 16 & 255) / 255.0F;
        float green = (float) (tailColor >> 8 & 255) / 255.0F;
        float blue = (float) (tailColor & 255) / 255.0F;
        GL11.glColor4f(red, green, blue, alpha);
        int lastTextureId = batchRenderer.getLastFlushBoundTextureId();
        if (lastTextureId != FontBatchRenderer.NO_TEXTURE) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, lastTextureId);
        }
    }

    /** 与原版 renderString 一致：alpha 为 0 的入口颜色视为不透明。 */
    private static int normalizeAlpha(int color) {
        if ((color & 0xFC000000) == 0) {
            return color | 0xFF000000;
        }
        return color;
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
