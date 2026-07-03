package club.heiqi.uilib.ui.scene.form;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;

/**
 * 表单主题 token：字段卡片外壳共享的通用视觉口径。
 *
 * <p>从 {@code config.ui.theme.ConfigTheme} 提炼下沉的「字段卡片相关」通用 token，
 * 供 {@link FormFieldShell} 与未来表单组合消费。不可变 record，由
 * {@link #defaultDark()} 工厂提供与 {@link SceneChromeTokens} + config 现有配色对齐的深色档。</p>
 *
 * <p><b>零 config 依赖</b>：本类不 import 任何 {@code club.heiqi.config.*}，
 * 仅依赖 uilib 内 {@link SceneChromeTokens} 与本地配色常量。config 侧通过
 * {@code ConfigTheme.asFormTheme()} 桥接获取实例。</p>
 *
 * @param cardBg         卡片背景色
 * @param cardBorder     卡片默认边框
 * @param cardBorderDirty 卡片脏态边框（蓝提亮）
 * @param cardBorderError 卡片错误边框（红）
 * @param cardRadius     卡片圆角
 * @param cardPad        卡片内边距
 * @param fieldGap       字段卡片间距
 * @param textColor      正文文本色
 * @param mutedColor     次要文本色（helper / 副标题 / 默认 dot）
 * @param errorColor     错误文本色
 * @param dirtyColor     脏态文本色（蓝）
 * @param fontLabel      字段 label 字号
 * @param fontHelper     helper text 字号
 * @param fontError      error text 字号
 * @param inputHeight    输入框行高
 * @param rootBg         页根背景色（与 {@code ConfigTheme.ROOT_BG} / {@code SceneDemoTokens.ROOT_BG} 同源）
 * @param viewportBg     视口背景色（与 {@code ConfigTheme.VIEWPORT_BG} / {@code SceneDemoTokens.VIEWPORT_BG} 同源）
 * @param titleColor     标题文本色（与 {@code ConfigTheme.TITLE_COLOR} / {@code SceneDemoTokens.TITLE_COLOR} 同源）
 */
@Desugar
public record FormTheme(
        int cardBg,
        int cardBorder,
        int cardBorderDirty,
        int cardBorderError,
        int cardRadius,
        int cardPad,
        int fieldGap,
        int textColor,
        int mutedColor,
        int errorColor,
        int dirtyColor,
        int fontLabel,
        int fontHelper,
        int fontError,
        int inputHeight,
        int rootBg,
        int viewportBg,
        int titleColor
) {

    /**
     * 深色档默认主题：值取 {@link SceneChromeTokens} + config 现有配色，
     * 与 {@code ConfigTheme} 字段卡片相关常量一一对应。
     *
     * @return 深色档 FormTheme 实例
     */
    public static FormTheme defaultDark() {
        // 卡片相关 token（cardBg/cardBorder*/cardRadius/cardPad）已下沉为本类独有，
        // 不再与 ConfigTheme 共享常量；其余 9 个字段（fieldGap/textColor/mutedColor/
        // errorColor/dirtyColor/fontLabel/fontHelper/fontError/inputHeight）仍与
        // ConfigTheme 同名常量物理重复，由 ConfigThemeAlignmentTest 守护对齐。
        return new FormTheme(
                0xFF0D1728,                  // cardBg
                0xFF2F4D87,                  // cardBorder
                0xFF3B5BA5,                  // cardBorderDirty
                0xFFF87171,                  // cardBorderError
                SceneChromeTokens.RADIUS_LG, // cardRadius
                SceneChromeTokens.PAD_LG,    // cardPad
                SceneChromeTokens.GAP_MD,    // fieldGap     = ConfigTheme.FIELD_GAP
                0xFFEAF1FF,                  // textColor    = ConfigTheme.TEXT_COLOR
                0xFF8AA0C8,                  // mutedColor   = ConfigTheme.MUTED_COLOR
                0xFFF87171,                  // errorColor   = ConfigTheme.ERROR_COLOR
                0xFF60A5FA,                  // dirtyColor   = ConfigTheme.DIRTY_COLOR
                16,                          // fontLabel    = ConfigTheme.FONT_LABEL
                13,                          // fontHelper   = ConfigTheme.FONT_HELPER
                13,                          // fontError    = ConfigTheme.FONT_ERROR
                SceneChromeTokens.INPUT_HEIGHT, // inputHeight = ConfigTheme.INPUT_HEIGHT
                0xFF0B1424,                  // rootBg       = ConfigTheme.ROOT_BG / SceneDemoTokens.ROOT_BG
                0xFF081120,                  // viewportBg   = ConfigTheme.VIEWPORT_BG / SceneDemoTokens.VIEWPORT_BG
                0xFFC9D8F8                   // titleColor   = ConfigTheme.TITLE_COLOR / SceneDemoTokens.TITLE_COLOR
        );
    }
}
