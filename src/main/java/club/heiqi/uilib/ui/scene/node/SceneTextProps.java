package club.heiqi.uilib.ui.scene.node;

import club.heiqi.uilib.ui.scene.text.SceneTextMode;

/**
 * SceneNode 文本内容属性值容器（对称 {@link ScenePaintProps} / {@link SceneLayoutProps}）。
 *
 * <p>本类只保存字段值，不持有 {@link SceneNode} 引用，不做去重判断，不打脏标记
 * （去重与脏标记仍由 SceneNode setter 负责）。</p>
 *
 * <p>边界（审查报告 §8 B2-2）：悬停命中链接 {@code activeLinkUrl} 属交互投影组
 * （与 cursor/hitTestable 同级），不入本容器；字号 fontSizePx 同时驱动布局几何与
 * 绘制输出（跨组属性），亦不入本容器。</p>
 */
final class SceneTextProps {

    /** 文本内容，默认 null */
    String text;

    /** 文本内容模式（唯一语义锚），默认原始文本 */
    SceneTextMode textContentMode = SceneTextMode.UILIB_RAW;

    /** 最大换行宽度（UI 像素），{@code <=0} 表示不换行 */
    int maxTextWidth = 0;

    /** 行距倍数（0=未设置，自动行高；&gt;0 时行高 = 自动行高 × 倍数），优先于 {@link #lineHeightPx} */
    double lineHeightMultiplier = 0.0D;

    /** 绝对行高（UI 像素，0=未设置，自动行高），仅在 {@link #lineHeightMultiplier} 未设置时生效 */
    int lineHeightPx = 0;

    /** 最大显示行数，{@code <=0} 表示不限行 */
    int maxLines = 0;

    /** 是否在 maxLines 截断的末行追加省略号 */
    boolean ellipsis = false;
}
