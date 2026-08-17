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
 * @param listHeight     多行字段（如 SIMPLE_LIST）默认视口高度；表单壳按字段自带高度传入，
 *                       单行字段传 inputHeight，多行字段传 listHeight，由字段决定自身高度下界。
 *                       取值 220（约 6~7 行可见），与表单视口空间同源。
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
        int listHeight,
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
        // Material dark tonal surface。默认边框与底色同色，保持 Setting Row 低噪声；
        // dirty/error 时再显式提亮边框和状态点。
        return new FormTheme(
                0xFF2B2930,                  // cardBg
                0xFF2B2930,                  // cardBorder
                0xFFD0BCFF,                  // cardBorderDirty
                0xFFFFB4AB,                  // cardBorderError
                SceneChromeTokens.RADIUS_LG, // cardRadius
                SceneChromeTokens.PAD_LG,    // cardPad
                SceneChromeTokens.GAP_MD,    // fieldGap     = ConfigTheme.FIELD_GAP
                0xFFE6E1E5,                  // textColor    = ConfigTheme.TEXT_COLOR
                0xFFCAC4D0,                  // mutedColor   = ConfigTheme.MUTED_COLOR
                0xFFFFB4AB,                  // errorColor   = ConfigTheme.ERROR_COLOR
                0xFFD0BCFF,                  // dirtyColor   = ConfigTheme.DIRTY_COLOR
                16,                          // fontLabel    = ConfigTheme.FONT_LABEL
                13,                          // fontHelper   = ConfigTheme.FONT_HELPER
                13,                          // fontError    = ConfigTheme.FONT_ERROR
                SceneChromeTokens.INPUT_HEIGHT, // inputHeight = ConfigTheme.INPUT_HEIGHT
                220,                          // listHeight  多行字段默认视口高度
                0xFF111318,                  // rootBg       = ConfigTheme.ROOT_BG
                0xFF1B1B1F,                  // viewportBg   = ConfigTheme.VIEWPORT_BG
                0xFFE6E1E5                   // titleColor   = ConfigTheme.TITLE_COLOR
        );
    }
}
