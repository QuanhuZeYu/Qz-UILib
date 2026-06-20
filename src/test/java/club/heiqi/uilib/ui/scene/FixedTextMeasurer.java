package club.heiqi.uilib.ui.scene;

import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/**
 * scene 布局/绘制测试统一共享的确定性文本度量替身。
 *
 * <p>度量值完全确定，绝不依赖真机字体运行时，使所有 scene 测试可在纯 JUnit 环境断言：</p>
 * <ul>
 *   <li>{@code measureWidth = text.length() * charWidth}（null 文本按 0 长度处理）。</li>
 *   <li>{@code lineHeight} 为固定值（与字号无关），便于沿用旧 16/32 行高断言。</li>
 *   <li>{@code epoch} 可由 {@link #setEpoch} / {@link #bumpEpoch} 改变，驱动失效链测试。</li>
 * </ul>
 */
public final class FixedTextMeasurer implements SceneTextMeasurer {

    /** 每字符固定宽度（UI 像素） */
    private final int charWidth;

    /** 固定行高（UI 像素），与字号无关 */
    private final int lineHeight;

    /** 当前字体运行时纪元（可变，供失效链测试驱动） */
    private int epoch;

    /**
     * 使用默认度量参数创建替身（charWidth=8，lineHeight=16，epoch=0）。
     */
    public FixedTextMeasurer() {
        this(8, 16);
    }

    /**
     * 使用指定每字符宽度与固定行高创建替身（epoch 初值 0）。
     *
     * @param charWidth  每字符固定宽度（UI 像素）
     * @param lineHeight 固定行高（UI 像素）
     */
    public FixedTextMeasurer(int charWidth, int lineHeight) {
        this.charWidth = charWidth;
        this.lineHeight = lineHeight;
        this.epoch = 0;
    }

    @Override
    public int measureWidth(String text, int fontSizePx) {
        int len = text == null ? 0 : text.length();
        return len * charWidth;
    }

    @Override
    public int lineHeight(int fontSizePx) {
        return lineHeight;
    }

    @Override
    public int epoch() {
        return epoch;
    }

    /**
     * 设置当前纪元（直接覆盖）。
     *
     * @param epoch 新的纪元值
     */
    public void setEpoch(int epoch) {
        this.epoch = epoch;
    }

    /**
     * 纪元自增 1，模拟字体运行时变化。
     */
    public void bumpEpoch() {
        this.epoch++;
    }
}
