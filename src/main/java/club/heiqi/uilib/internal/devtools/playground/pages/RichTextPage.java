package club.heiqi.uilib.internal.devtools.playground.pages;

import java.util.function.Supplier;

import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPage;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextHorizontalAlign;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.paint.TextStyle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 富文本渲染演示页 —— SceneLabel 现代标签语法（color/b/i/u/s/size/br）与容错/换行行为。
 *
 * <p>覆盖：任意 24 位色与命名色、粗斜下删、任意嵌套与回退、字号混排（同基线）、
 * wrapWidth 自动换行（样式跨行续传）、未知标签/坏属性/未闭合的宽容解析、
 * SceneLabel 组件 signal 驱动的交互切换。</p>
 */
public final class RichTextPage implements PlaygroundPage {

    /** 交互演示的富文本源（按钮切换）。 */
    private final Signal<String> demoText = Signal.create(
            "<color=#4FC3F7>富文本</color>由 <b>SceneLabel</b> 渲染，<size=24>字号</size>与<color=gold>颜色</color>任意混排");

    @Override
    public String id() {
        return "rich-text";
    }

    @Override
    public String title() {
        return "富文本";
    }

    @Override
    public String description() {
        return "SceneLabel 现代富文本标签：颜色/粗斜下删/字号混排/换行/宽容解析";
    }

    @Override
    public Supplier<SceneNode> build(final SceneRuntime rt) {
        return () -> {
            SceneNode root = SceneNode.column();
            root.setFillParentWidth(true);
            root.setGap(10);

            // ===== 卡片1：样式标签 =====
            SceneNode styleCard = PlaygroundKit.card();
            styleCard.appendChild(PlaygroundKit.title("样式标签（color / b / i / u / s）"));
            styleCard.appendChild(richText(
                    "<color=#FF5533>任意 24 位颜色</color>　<color=gold>命名色</color>　"
                    + "<b>粗体</b> <i>斜体</i> <u>下划线</u> <s>删除线</s>", 15));
            styleCard.appendChild(richText(
                    "任意嵌套：<color=#4FC3F7><b>蓝粗<i>蓝粗斜</i></b>回蓝粗</color>　关闭标签后回退父样式", 15));
            styleCard.appendChild(richText(
                    "8 位 ARGB：<color=#80FF5533>半透明红</color>（底层背景透出）", 15));
            styleCard.appendChild(richText(
                    "行内高亮：<mark>默认黄底</mark>　<mark=#80FF5533>半透明红底</mark>　"
                    + "<mark=#4FC3F7><b>蓝底加粗</b></mark>", 15));
            styleCard.appendChild(richText(
                    "上下标：x<sup>2</sup> + y<sub>n</sub>　<size=20>大<sup>号上标</sup></size>（字号缩至 0.75×，基线偏移）", 15));
            styleCard.appendChild(richText(
                    "字距：<spacing=4>宽松字距</spacing>　<spacing=-1>紧凑字距</spacing>（advance 追加，换行/裁剪同步感知）", 15));
            styleCard.appendChild(PlaygroundKit.hint(
                    "标签不占测量宽度；§ 原版格式码在富文本模式下不参与解析。"));
            root.appendChild(styleCard);

            // ===== 卡片2：字号混排与换行 =====
            SceneNode sizeCard = PlaygroundKit.card();
            sizeCard.appendChild(PlaygroundKit.title("字号混排与自动换行（size / br / wrapWidth）"));
            sizeCard.appendChild(richText("混排：<size=24>大字</size><size=12>小字</size>共享同一行基线，advance 按各自字号推进", 15));
            sizeCard.appendChild(richText(
                    "硬换行：<b>第一行<br>第二行</b>　（br 前后样式续传）", 15));
            SceneNode wrapHint = PlaygroundKit.hint(
                    "下方 SceneLabel 以 wrapWidth=320 自动换行：<color=#4FC3F7>换行切在样式片段中间时，"
                    + "行尾显式闭合、行首自动重开标签，跨行样式续传零特判。</color>");
            sizeCard.appendChild(wrapHint);
            SceneNode wrapDemo = SceneLabel.create(rt, new SceneLabel.Props(
                    Signal.create("<color=#4FC3F7>这是一段用于演示自动换行的富文本，"
                            + "<b>加粗片段</b>横跨换行边界，样式在换行前后保持一致。</color>"
                            + "换行宽度 320 像素，标签本身不占任何测量宽度。"),
                    PlaygroundKit.TEXT, 14, TextStyle.TEXT_MODE_RICH_TAGS,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, 320, 0.0D, 0, 0, false, null)).get();
            sizeCard.appendChild(wrapDemo);
            root.appendChild(sizeCard);

            // ===== 卡片3：宽容解析 =====
            SceneNode toleranceCard = PlaygroundKit.card();
            toleranceCard.appendChild(PlaygroundKit.title("宽容解析（现代组件惯例：宽容失败）"));
            toleranceCard.appendChild(richText(
                    "未知标签原样保留：<foo>尖括号</foo>按字面输出", 15));
            toleranceCard.appendChild(richText(
                    "坏属性忽略继承父样式：<color=不是颜色>这段</color>回到默认色；<size=abc>x</size>保持基准字号", 15));
            toleranceCard.appendChild(richText(
                    "未闭合自动闭合到文本末尾：<b>这段粗体没有闭合标签", 15));
            toleranceCard.appendChild(richText(
                    "转义实体：&lt;color=red&gt; 会显示为字面尖括号而不是标签", 15));
            root.appendChild(toleranceCard);

            // ===== 卡片4：SceneLabel 组件 + signal =====
            SceneNode liveCard = PlaygroundKit.card();
            liveCard.appendChild(PlaygroundKit.title("SceneLabel 组件（signal 驱动，按钮切换源文本）"));
            SceneNode ops = PlaygroundKit.row(8);
            PlaygroundKit.button(rt, ops, "样式", () -> demoText.set(
                    "<color=#4FC3F7>富文本</color>由 <b>SceneLabel</b> 渲染，"
                    + "<size=24>字号</size>与<color=gold>颜色</color>任意混排"));
            PlaygroundKit.button(rt, ops, "换行", () -> demoText.set(
                    "<color=#FF5533>自动换行演示：</color><b>这一段粗体文本足够长，"
                    + "会在 320 像素处折行，粗体与颜色样式跨行续传。</b>"));
            PlaygroundKit.button(rt, ops, "容错", () -> demoText.set(
                    "未知标签 <foo>x</foo> 与坏属性 <color=zzz>y</color> 都按字面/继承处理"));
            liveCard.appendChild(ops);
            liveCard.appendChild(SceneLabel.create(rt, new SceneLabel.Props(
                    demoText, PlaygroundKit.TEXT, 15, TextStyle.TEXT_MODE_RICH_TAGS,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, 320, 0.0D, 0, 0, false, null)).get());
            liveCard.appendChild(PlaygroundKit.hint(
                    "限行演示（maxLines=2 + ellipsis）：长文最多两行，末行追加省略号"));
            liveCard.appendChild(SceneLabel.create(rt, new SceneLabel.Props(
                    Signal.create("<color=#4FC3F7>这是一段足够长的富文本，用于演示限行截断："
                            + "<b>加粗内容</b>会被截掉一部分，超出两行的部分全部丢弃，"
                            + "末行以省略号收尾，布局高度只按两行计算，"
                            + "第三行、第四行以及更往后的所有内容都不可见。</color>"),
                    PlaygroundKit.TEXT, 14, TextStyle.TEXT_MODE_RICH_TAGS,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, 320, 0.0D, 0, 2, true, null)).get());
            final Signal<String> linkFeedback = Signal.create("（点击下方链接，回调写入这里）");
            liveCard.appendChild(SceneLabel.create(rt, new SceneLabel.Props(
                    Signal.create("链接演示：访问 <a=https://github.com>GitHub</a> 或 "
                            + "<a=https://example.com>示例站</a>（自动下划线，可点击）"),
                    PlaygroundKit.TEXT, 14, TextStyle.TEXT_MODE_RICH_TAGS,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, 0, 0.0D, 0, 0, false,
                    url -> linkFeedback.set("点击了链接：" + url))).get());
            liveCard.appendChild(SceneLabel.create(rt, new SceneLabel.Props(
                    linkFeedback, PlaygroundKit.TEXT, 13, TextStyle.TEXT_MODE_UILIB_RAW)).get());
            root.appendChild(liveCard);
            return root;
        };
    }

    /**
     * 创建富文本模式的静态演示文本节点。
     *
     * @param value    富文本（含标签）
     * @param fontSize 字号
     * @return 文本节点
     */
    private static SceneNode richText(String value, int fontSize) {
        SceneNode node = PlaygroundKit.text(value, PlaygroundKit.TEXT, fontSize);
        node.setTextContentMode(TextStyle.TEXT_MODE_RICH_TAGS);
        return node;
    }
}
