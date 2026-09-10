package club.heiqi.uilib.internal.chat3.view;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * S5-3（task-21）RC-06 可证伪断言：倍率/字号必须进入 markdown 段流缓存 key 与换行几何。
 *
 * <p>同一 text、同一定行宽，仅基准字号不同 ⇒ L2 视觉行必须重排（断行点变），
 * 且缓存不得把 100% 的结果回给 150%（{@code ChatMarkdownPipeline#layout} 的 key 含 fontSizePx）。</p>
 */
public class ChatMarkdownPipelineFontScaleTest {

    private static final String BODY = buildBody();

    private static String buildBody() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            builder.append("word ");
        }
        return builder.toString();
    }

    /** 换算公式必须与 scene 解析出口同式：clamp(round(设计值 × 倍率))。 */
    @Test
    public void effectiveFontSizeFormulaMatchesSceneResolutionExit() {
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        try {
            Assert.assertEquals("100%：有效值 = 设计值",
                    ChatMarkdownSettings.getChatFontSizePx(), ChatFontMetrics.chatFontSizePx(rt));
            Assert.assertEquals("100%：行高 = 设计行高",
                    ChatMarkdownSettings.getChatLineHeightPx(), ChatFontMetrics.chatLineHeightPx(rt));

            rt.setFontScale(150);
            Assert.assertEquals("150%：有效字号 = round(设计字号 × 1.5)",
                    Math.round(ChatMarkdownSettings.getChatFontSizePx() * 1.5F),
                    ChatFontMetrics.chatFontSizePx(rt));
            Assert.assertEquals("150%：有效行高 = round(设计行高 × 1.5)",
                    Math.round(ChatMarkdownSettings.getChatLineHeightPx() * 1.5F),
                    ChatFontMetrics.chatLineHeightPx(rt));

            rt.setFontScale(200);
            Assert.assertEquals("200%：有效字号 = 设计字号 × 2",
                    Math.round(ChatMarkdownSettings.getChatFontSizePx() * 2.0F),
                    ChatFontMetrics.chatFontSizePx(rt));
        } finally {
            rt.dispose();
        }
    }

    /** 字号入 key：同参命中同一对象；仅字号变化必须重算并重排（行数变多）。 */
    @Test
    public void fontSizeIsPartOfLayoutCacheKeyAndRewraps() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        int design = ChatMarkdownSettings.getChatFontSizePx();
        int scaled = Math.round(design * 1.5F);

        List<ChatMarkdownPipeline.RenderedLine> first =
                pipeline.layout(BODY, -1, 200, design, null, null);
        List<ChatMarkdownPipeline.RenderedLine> again =
                pipeline.layout(BODY, -1, 200, design, null, null);
        Assert.assertSame("同参必须命中同一缓存对象（缓存仍生效）", first, again);

        List<ChatMarkdownPipeline.RenderedLine> bigger =
                pipeline.layout(BODY, -1, 200, scaled, null, null);
        Assert.assertNotSame("字号不同不得命中旧缓存（字号/倍率必须入 key）", first, bigger);
        Assert.assertTrue("字号变大必须重排：行数 " + first.size() + " → " + bigger.size(),
                bigger.size() > first.size());
    }
}
