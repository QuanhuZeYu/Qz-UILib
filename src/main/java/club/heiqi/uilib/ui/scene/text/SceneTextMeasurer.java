package club.heiqi.uilib.ui.scene.text;

/** 字体边界提供共享行布局；组件策略由请求声明，不由 renderer 覆盖。 */
public interface SceneTextMeasurer {
    TextLayout layout(String text, TextStyle style, int availableWidth);
    default TextLayout layout(AttributedText text, float availableWidth, TextPolicy policy) {
        throw new UnsupportedOperationException("此measurer尚不支持属性文本");
    }
    /** 旧实现只能表达原布局行为；不得悄悄忽略新策略。 */
    default TextLayout layout(String text, TextStyle style, int availableWidth, TextPolicy policy) {
        if (!TextPolicy.NORMAL.equals(policy)) throw new UnsupportedOperationException("此 measurer 尚不支持组件文本策略");
        return layout(text, style, availableWidth);
    }
}
