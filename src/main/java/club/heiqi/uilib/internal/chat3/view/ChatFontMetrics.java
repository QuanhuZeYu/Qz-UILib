package club.heiqi.uilib.internal.chat3.view;

import club.heiqi.uilib.font.layout.FontSizeLimits;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * chat3 设计字号 → 用户倍率作用后的<b>有效像素</b>换算（几何口径唯一换算点）。
 *
 * <h3>为什么需要它（RC-06）</h3>
 * <p>chat3 的字号来自 {@link club.heiqi.uilib.internal.chat3.ChatMarkdownSettings}（设计值 12/13/14），
 * 而用户级无障碍倍率由 scene 在<b>解析出口</b>作用一次：{@code effectiveFontSize() = clamp(round(声明值 × fontScale))}。
 * 因此：</p>
 * <ul>
 *   <li>节点的<b>层 1 声明</b>必须保持<b>设计值</b>（否则解析出口会再乘一次倍率 ⇒ 2×2 重复缩放）；</li>
 *   <li>而<b>几何</b>（换行宽、行框高、气泡高、段宽测量）必须用本类换算出的<b>有效值</b>，
 *       否则 150% 下会出现「文字放大、行框与气泡不变」（重叠/裁切）。</li>
 * </ul>
 *
 * <p>换算公式与 {@code SceneNode.effectiveFontSize()} 的出口完全同式
 * （{@code Math.round(px * scale)} 再钳到 {@link FontSizeLimits} 域），保证两处永不漂移。</p>
 */
final class ChatFontMetrics {

    private ChatFontMetrics() {
    }

    /**
     * 把设计像素值换算为「用户倍率作用后」的有效像素值。
     *
     * @param rt       场景运行时（null = 无倍率上下文，原值返回）
     * @param designPx 设计字号/行高（UI 逻辑像素）
     * @return 有效像素（已钳到合法域）
     */
    static int scalePx(SceneRuntime rt, int designPx) {
        if (rt == null) {
            return designPx;
        }
        float scale = rt.fontScale();
        return FontSizeLimits.clampFontSize(Math.round(designPx * scale));
    }

    /** @param rt 场景运行时（null = 无倍率上下文） @return 气泡正文有效字号 */
    static int chatFontSizePx(SceneRuntime rt) {
        return scalePx(rt, ChatMarkdownSettings.getChatFontSizePx());
    }

    /** @param rt 场景运行时（null = 无倍率上下文） @return 系统消息有效字号 */
    static int systemFontSizePx(SceneRuntime rt) {
        return scalePx(rt, ChatMarkdownSettings.getSystemFontSizePx());
    }

    /** @param rt 场景运行时（null = 无倍率上下文） @return 气泡正文有效行高（换行/行框/气泡高同源） */
    static int chatLineHeightPx(SceneRuntime rt) {
        return scalePx(rt, ChatMarkdownSettings.getChatLineHeightPx());
    }

    /** @param rt 场景运行时（null = 无倍率上下文） @return 系统消息有效行高 */
    static int systemLineHeightPx(SceneRuntime rt) {
        return scalePx(rt, ChatMarkdownSettings.getSystemLineHeightPx());
    }
}
