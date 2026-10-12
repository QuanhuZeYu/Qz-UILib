package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneRichText;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.StyledText;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.TextStyleOverride;
import club.heiqi.uilib.ui.scene.text.TextStyleOverrideSpan;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 输入声明和所有排版策略均由生产内容/字体管线解释，场景只选择数据。 */
final class TextScenarios {
    private static final String[] NAMES = {"基础样式", "高亮与嵌套", "字号与上下标", "安全簇字距", "Unicode与控制", "来源与非法内容", "软连字符", "损坏编码与控制符"};
    private static final String[] SAMPLES = {
        "继承 <b>粗体 <i>粗斜体</i></b> <i>斜体</i> <u>下划线 <s>删除线</s></u> <color value=\"#FF33BBEE\">独立颜色</color> <size value=\"28\">大字</size><br/><a href=\"first\">链接一 中文 English</a> / <a href=\"second\">链接二 العربية אבג</a>",
        "<mark>主题背景 <mark color=\"#885522AA\">半透明背景</mark> 恢复 <mark color=\"#00000000\">透明背景</mark> 恢复</mark><br/><mark color=\"#FF551122\"><color value=\"#FFFFFFFF\">白字与不透明底色<br/>第二行与前行墨迹</color></mark>",
        "CO<sub>2</sub> / m<sup>3</sup> / <sup>外<sup>内</sup>外</sup> / <sub>下<sup>上</sup>下</sub><br/><sup><size value=\"24\"><u>脚本后绝对字号</u></size></sup> / <size value=\"24\"><sup><s>绝对字号后脚本</s></sup></size>",
        "默认 office é 👩‍💻 / <spacing value=\"3\">疏排 office é 👩‍💻</spacing><br/><spacing value=\"-1\">紧排 ABC 中文 العربية אבג</spacing><br/><spacing value=\"2\">A</spacing><color value=\"#FF33BBEE\">B</color>C / <spacing value=\"2\">Tab A\tB 零宽 A\u200bB</spacing>",
        "中文 English العربية עברית ภาษาไทย हिन्दी বাংলা ਪੰਜਾਬੀ ગુજરાતી ଓଡ଼ିଆ தமிழ் తెలుగు ಕನ್ನಡ മലയാളം සිංහල\n默认😀 文本☀︎ 彩色☀️ 肤色👍🏽 旗帜🇨🇳 键帽1️⃣ 组合👩‍💻 é\n隔离：A\u2067אבג 123\u2069 B；不换行空格：A\u00a0B；连接符：A\u2060B\r\nTab：A\tB\tC；CR\rLF\n尾部",
        "<a href=\"unsafe\"><b>错配与&amp;来源</i></a>",
        "<mark><a href=\"word\">co\u00adoperate inter\u00adnational micro\u00adscope</a></mark><br/>末尾软连字符 a\u00ad / 连续软连字符 ab\u00ad\u00adcd / <sup>super\u00adscript</sup> / <spacing value=\"2\">letter\u00adspacing</spacing>",
        "损坏UTF-16：A\ud800B；控制符：BS\b / ESC\u001b；后续有效文字：中文 emoji 😀"
    };
    private TextScenarios() {}

    static SceneNode create(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        Signal<Integer> sample = context.domain().signal(0);
        Signal<Integer> mode = context.domain().signal(1);
        Signal<Integer> em = context.domain().signal(20);
        Signal<Integer> width = context.domain().signal(480);
        Signal<Integer> wrap = context.domain().signal(0);
        Signal<Integer> direction = context.domain().signal(0);
        Signal<Integer> height = context.domain().signal(0);
        Signal<Integer> face = context.domain().signal(0);
        Signal<Integer> weight = context.domain().signal(400);
        Signal<Integer> posture = context.domain().signal(0);
        Signal<String> activated = context.domain().signal("尚未点击链接");
        ReadableSignal<SceneTheme> scoped = () -> {
            TextStyle base = theme.get().textStyle.withEm(em.get()).withFontStyle(
                    new FontStyle(weight.get(), FontStyle.Posture.values()[posture.get()]));
            if (face.get() == 1) base = TextStyleOverride.builder().font("unifont").build().resolve(base);
            return theme.get().withTextStyle(base);
        };
        ReadableSignal<TextContent> content = context.domain().computed(context.scope(), () -> {
            String source = SAMPLES[sample.get()];
            if (sample.get() == 7 && mode.get() == 2) {
                byte[] tail = "后续有效文字：中文 emoji 😀 BS\b ESC\u001b".getBytes(StandardCharsets.UTF_8);
                byte[] damaged = new byte[tail.length + 5];
                damaged[0] = 'A'; damaged[1] = (byte) 0xe2; damaged[2] = (byte) 0x82;
                damaged[3] = 'B'; damaged[4] = ' ' ;
                System.arraycopy(tail, 0, damaged, 5, tail.length);
                return TextContent.of(TextContent.Mode.PLAIN, TextInput.utf8(damaged));
            }
            if (mode.get() == 0) return TextContent.plain(source);
            if (mode.get() == 2) return TextContent.of(TextContent.Mode.RICH_TAGS, TextInput.utf8(source.getBytes(StandardCharsets.UTF_8)));
            if (mode.get() == 3) return typed();
            return TextContent.richTags(source);
        });
        SceneNode page = new SceneNode().size(Size.fill(1), Size.fill(1)).gap(12).scrollable(true);
        page.add(GalleryStyle.label(context, () -> "文本与排版", theme, 26, false));
        page.add(GalleryStyle.label(context, () -> "切换样本与输入方式，再改变字号、字体、宽度和断行。预期：文字、背景、装饰和链接一起更新；PLAIN保留标签字面，非法DSL整段原文且链接不激活。", theme, 14, true));
        SceneNode controls = GalleryStyle.card(theme);
        controls.add(GalleryStyle.button(context, () -> "文本样本：" + NAMES[sample.get()], theme, () -> sample.set((sample.get() + 1) % NAMES.length)));
        controls.add(GalleryStyle.button(context, () -> "输入方式：" + new String[]{"PLAIN", "DSL", "UTF-8 DSL", "typed完整样式"}[mode.get()], theme, () -> mode.set((mode.get() + 1) % 4)));
        controls.add(GalleryStyle.button(context, () -> "预览字号：" + em.get(), theme, () -> em.set(em.get() == 20 ? 32 : 20)));
        controls.add(GalleryStyle.button(context, () -> "预览宽度：" + width.get(), theme, () -> width.set(width.get() == 480 ? 160 : width.get() == 160 ? 48 : 480)));
        controls.add(GalleryStyle.button(context, () -> "断行：" + new String[]{"正常", "不软换行", "紧急换行"}[wrap.get()], theme, () -> wrap.set((wrap.get() + 1) % 3)));
        controls.add(GalleryStyle.button(context, () -> "段落方向：" + TextPolicy.Direction.values()[direction.get()], theme, () -> direction.set((direction.get() + 1) % 3)));
        controls.add(GalleryStyle.button(context, () -> "行高：" + (height.get() == 0 ? "真实度量" : "显式5"), theme, () -> height.set(height.get() == 0 ? 5 : 0)));
        controls.add(GalleryStyle.button(context, () -> "字体：" + (face.get() == 0 ? "主题候选" : "unifont"), theme, () -> face.set(1 - face.get())));
        controls.add(GalleryStyle.button(context, () -> "字重请求：" + weight.get(), theme, () -> weight.set(weight.get() == 400 ? 700 : weight.get() == 700 ? 100 : 400)));
        controls.add(GalleryStyle.button(context, () -> "字姿请求：" + FontStyle.Posture.values()[posture.get()], theme, () -> posture.set((posture.get() + 1) % FontStyle.Posture.values().length)));
        page.add(GalleryStyle.frame(controls, theme));
        SceneNode preview = SceneRichText.create(context, content, scoped, target -> activated.set("已激活：" + target));
        preview.bind(NodeBinding.value(width, (node, value) -> node.size(Size.fill(1).bounds(0, value), Size.natural())));
        preview.bind(NodeBinding.value(() -> new TextPolicy(TextPolicy.Direction.values()[direction.get()], new TextPolicy.Wrap[]{TextPolicy.Wrap.NORMAL, TextPolicy.Wrap.NONE, TextPolicy.Wrap.EMERGENCY}[wrap.get()], "und", 0, height.get()), (node, policy) -> node.textPolicy(policy)));
        // 受限区域只声明clip；超宽安全簇与显式小行高仍由字体和scene决定。
        SceneNode viewport = new SceneNode().size(Size.fill(1), Size.fixed(190)).clip(true).scrollable(true).add(preview);
        page.add(GalleryStyle.frame(viewport, theme));
        page.add(GalleryStyle.label(context, activated, theme, 14, false));
        page.add(GalleryStyle.label(context, () -> "损坏编码样本：字符串模式有孤立代理项，UTF-8模式含截断多字节序列；预期显示替代字符、BS/ESC可辨识标记与原来源诊断，后续有效中文和emoji保留。", theme, 13, true));
        page.add(GalleryStyle.label(context, () -> "软连字符观察：宽栏和不软换行时隐藏；采用该断点时行尾显示连字符。切换宽度、字体、字号和主题后重新选择断点；行尾连字符仍属于原软连字符来源，点击它应激活原链接。", theme, 13, true));
        page.add(GalleryStyle.label(context, () -> diagnostics(content.get().resolve(scoped.get().textStyle)), theme, 13, true));
        page.add(GalleryStyle.label(context, () -> "typed通过公开样式意图构建同一段落；上下标与字号的先后顺序、高亮关闭/透明与恢复都在预览中。字族缺少真实样式时允许明确降级，不合成粗斜体。小行高下背景不能覆盖其他行字形；祖先裁剪外不能点击链接。", theme, 13, true));
        SceneNode source = GalleryStyle.card(theme);
        source.add(GalleryStyle.label(context, () -> "内容声明与来源", theme, 18, false));
        source.add(SceneRichText.create(context, () -> TextContent.plain(mode.get() == 3 ? "typed属性意图不提供DSL往返序列化；原文：" + content.get().resolve(scoped.get().textStyle).text.input.text : content.get().resolve(scoped.get().textStyle).serializeRichTags()), theme, target -> {}));
        page.add(GalleryStyle.frame(source, theme));
        return page;
    }

    private static String diagnostics(TextContent.Resolved value) {
        StringBuilder out = new StringBuilder("来源单位：").append(value.text.input.sourceUnit).append("；内容诊断：").append(value.text.diagnostics().size());
        for (TextLayout.Diagnostic d : value.text.diagnostics()) out.append("\n").append(d.sourceStart).append("–").append(d.sourceEnd).append("：").append(d.message);
        return out.toString();
    }

    private static TextContent typed() {
        String[] text = {"继承 / ", "粗斜体 / ", "装饰 / ", "主题高亮 ", "关闭 ", "透明 ", "CO", "2", " m", "3", " office é 👩‍💻", " 恢复"};
        TextStyleOverride[] styles = {
            TextStyleOverride.INHERIT,
            TextStyleOverride.builder().weight(700).posture(FontStyle.Posture.ITALIC).build(),
            TextStyleOverride.builder().underline(true).strike(true).build(),
            TextStyleOverride.builder().highlight().build(),
            TextStyleOverride.builder().noHighlight().build(),
            TextStyleOverride.builder().highlight(0).build(),
            TextStyleOverride.INHERIT,
            TextStyleOverride.builder().subscript().build(),
            TextStyleOverride.INHERIT,
            TextStyleOverride.builder().superscript().em(24).underline(true).build(),
            TextStyleOverride.builder().letterSpacing(2).argb(0xff33bbee).build(),
            TextStyleOverride.INHERIT
        };
        StringBuilder source = new StringBuilder();
        List<TextStyleOverrideSpan> spans = new ArrayList<>();
        for (int i = 0; i < text.length; i++) {
            int start = source.length(); source.append(text[i]);
            spans.add(new TextStyleOverrideSpan(start, source.length(), styles[i]));
        }
        return TextContent.styled(new StyledText(TextInput.string(source.toString()), source.toString(), spans, TextStyleOverride.INHERIT, Collections.emptyList()));
    }
}
