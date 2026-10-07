package club.heiqi.uilib.ui.scene.text;
/** 字体/宿主边界提供行布局；核心不接触字体栅格或平台类。 */
public interface SceneTextMeasurer { TextLayout layout(String text, TextStyle style, int availableWidth); }
