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
 * 控制字符统一口径演示页 —— Unicode 控制/格式字符的实际解析行为。
 *
 * <p>覆盖：换行类（NEL/LS/PS/VT/FF/CRLF 折叠）、\t 4 空格列宽、空白家族断词折叠、
 * ZWSP 软断行、软连字符断行补字、变体选择符/组合标记粘合、剥离类静默不可见。
 * 全部口径锚定 {@code font.util.UnicodeTextClassifier}（默认开启，无开关）。</p>
 */
public final class ControlCharPage implements PlaygroundPage {

    @Override
    public String id() {
        return "control-chars";
    }

    @Override
    public String title() {
        return "控制字符";
    }

    @Override
    public String description() {
        return "Unicode 控制字符实际解析：换行类/tab列宽/空白家族/ZWSP软断行/软连字符/粘合/剥离类";
    }

    @Override
    public Supplier<SceneNode> build(final SceneRuntime rt) {
        return () -> {
            SceneNode root = SceneNode.column();
            root.setFillParentWidth(true);
            root.setGap(10);

            // ===== 卡片1：换行类 =====
            SceneNode newlineCard = PlaygroundKit.card();
            newlineCard.appendChild(PlaygroundKit.title("换行类（统一折叠为换行，CRLF 只算一个）"));
            newlineCard.appendChild(rawText("裸 \\n：第一行\n第二行", 14));
            newlineCard.appendChild(rawText("NEL(U+0085)：甲\u0085乙", 14));
            newlineCard.appendChild(rawText("LS(U+2028)：甲\u2028乙", 14));
            newlineCard.appendChild(rawText("PS(U+2029)：甲\u2029乙", 14));
            newlineCard.appendChild(rawText("VT(U+000B)/FF(U+000C)：甲\u000B乙\u000C丙", 14));
            newlineCard.appendChild(rawText("CRLF：甲\r\n乙（\r\n 折叠为一个换行，不产生空行）", 14));
            newlineCard.appendChild(PlaygroundKit.hint(
                    "\\n \\r \\v \\f NEL LS PS 全部视作换行；这些字符零宽、不产生字形。"));
            root.appendChild(newlineCard);

            // ===== 卡片2：Tab 列宽 =====
            SceneNode tabCard = PlaygroundKit.card();
            tabCard.appendChild(PlaygroundKit.title("制表符（固定 4 空格列宽）"));
            tabCard.appendChild(rawText("tab：a\tb", 14));
            tabCard.appendChild(rawText("空格：a    b", 14));
            tabCard.appendChild(rawText("混合：一\t二\t三", 14));
            tabCard.appendChild(PlaygroundKit.hint(
                    "\\t 按 4 个空格宽度推进（渲染为空格字形，下划线/高亮按列宽覆盖），与 4 个空格精确对齐。"));
            root.appendChild(tabCard);

            // ===== 卡片3：空白家族 =====
            SceneNode spaceCard = PlaygroundKit.card();
            spaceCard.appendChild(PlaygroundKit.title("空白家族（统一断词 / 行尾折叠 / 行首丢弃）"));
            spaceCard.appendChild(richLabel(rt,
                    "空白\u00A0家族\u2003统一\u202F断词\u205F折叠，"
                    + "行尾\u00A0空白\u2007会被剥掉，"
                    + "换行后行首空白自动丢弃，与普通空格语义完全一致。", 320));
            spaceCard.appendChild(PlaygroundKit.hint(
                    "UAX#14/CSS 口径：普通空格(U+0020)可断且行尾折叠；BA 类空格(U+1680/U+2000 系/U+205F/U+3000)"
                    + "可断但不折叠（行尾保留）；GL 胶水(NBSP/U+2007/U+2011/U+202F/U+180E)禁断——"
                    + "数字+单位等不可分组合不会在 NBSP 处断行。"));
            root.appendChild(spaceCard);

            // ===== 卡片4：软断行 =====
            SceneNode softCard = PlaygroundKit.card();
            softCard.appendChild(PlaygroundKit.title("软断行（ZWSP 断行零宽 / 软连字符断行补 '-'）"));
            softCard.appendChild(richLabel(rt,
                    "ZWSP：超长单词 abcdefghijklmnopqrstuvwxyz\u200Babcdefghijklmnopqrstuvwxyz "
                    + "在 ZWSP 处折行，断点不显示任何字符。", 320));
            softCard.appendChild(richLabel(rt,
                    "软连字符：supercalifragilistic\u00ADexpialidocious "
                    + "在软连字符处折行并显示连字符；行内不折行时完全不可见。", 320));
            softCard.appendChild(PlaygroundKit.hint(
                    "ZWSP(U+200B) 提供词内软断行机会（零宽）；软连字符(U+00AD) 只在断行处显示 '-'。"));
            root.appendChild(softCard);

            // ===== 卡片5：粘合 =====
            SceneNode clusterCard = PlaygroundKit.card();
            clusterCard.appendChild(PlaygroundKit.title("粘合（变体选择符 / 组合标记附着前字，不落行首）"));
            clusterCard.appendChild(richLabel(rt,
                    "变体选择符：字符\uFE0F\uFE0F 后跟足够长的文本演示折行，"
                    + "断行时变体选择符随前一字符走，不会出现在行首。", 200));
            clusterCard.appendChild(richLabel(rt,
                    "组合标记：e\u0301 组合尖音符与前字合并成簇，"
                    + "断行时不会与基字分离。", 200));
            clusterCard.appendChild(bigText(rt, "多层堆叠（金字塔）：a\u0301\u0300\u0308\u0303", 0));
            clusterCard.appendChild(bigText(rt, "NFC 对照：e\u0301 显示为 \u00E9", 0));
            clusterCard.appendChild(PlaygroundKit.hint(
                    "上方 32px 大字号：四层组合附加符逐层往上摞（层距贴每个标记自身字形高度，紧实堆叠）；"
                    + "下方 e+U+0301 在显示路径被 NFC 合并为预组合 é（字更完整，caret 仍按原始码点走）。"
                    + "方向按 CCC 完整语义：上方/下方逐层摞、Overlay 与包围标记原位覆盖、Nukta/Virama 下方、"
                    + "假名浊点右上。变体选择符(U+FE00..FE0F / U+E0100..E01EF) 零宽跳过渲染；"
                    + "组合 glyph 的可见性与堆叠高度取决于字体覆盖。"
                    + "粘贴进文本输入框（Ctrl+V/右键菜单）的组合序列同样按此口径显示（编辑保真、显示组合）。"));
            root.appendChild(clusterCard);

            // ===== 卡片6：剥离类 =====
            SceneNode stripCard = PlaygroundKit.card();
            stripCard.appendChild(PlaygroundKit.title("剥离类（静默不可见：零宽、无豆腐块）"));
            stripCard.appendChild(rawText("BOM\uFEFF前缀 + bidi 控制\u202E混入\u202C + C0\u0007控制\u001F残留：全部零宽", 14));
            stripCard.appendChild(rawText("WORD JOINER\u2060 / INVISIBLE SEPARATOR\u2063 / NUL\u0000 同样静默", 14));
            stripCard.appendChild(rawText("纯剥离：\u0000\u0001\u0008\u007F\u009F（本行除标题外无任何可见内容）", 14));
            stripCard.appendChild(PlaygroundKit.hint(
                    "其余 C0/C1、bidi 方向控制、BOM、WORD JOINER 等：测量零宽、渲染跳过（不是 U+FFFD、不是豆腐块）；"
                    + "字符保留在文本流中，文本域前缀宽度与 caret 几何不受影响。"));
            root.appendChild(stripCard);

            // ===== 卡片6b：网页灌水文本（真机对照网页渲染） =====
            SceneNode waterCard = PlaygroundKit.card();
            waterCard.appendChild(PlaygroundKit.title("网页灌水文本（贴吧水帖圣经，对照网页渲染效果）"));
            waterCard.appendChild(bigText(rt,
                    "\u0E34\u06D6\u0E34\u06E3 \u06E3\u06E3\u06D6\u06D6\u06D6\u06D6\u0E34\u06D6\u0E34\u0E34\u06E3\u06E3\u06D6\u06D6\u0E34 "
                    + "\u06D6\u0E34\u0E34\u06E3\u06E3\u06D6\u06D6\u0E34\u06E3 \u06E3\u06E3\u06D6\u06D6\u06D6\u0E34\u06D6\u0E34\u0E34 "
                    + "\u06E3\u06E3\u06D6\u06D6 \u06D6 \u06E3\u06E3\u06D6\u06D6\u0E34 "
                    + "\u06D6\u0E34\u0E34\u06E3\u06E3\u06D6\u06D6\u0E34\u06E3 \u06D6\n"
                    + "\u06E3\u06E3\u06D6\u06D6\u06D6\u0E34\u06D6\u0E34\u0E34 \u06E3\u06E3\u06D6\u06D6 \u06D6 "
                    + "\u06E3\u06E3\u06D6\u06D6\u0E34 \u4E0A\u8FB9\u7684\u5B57\u8D85\u8FC7700\u54E6", 0));
            waterCard.appendChild(PlaygroundKit.hint(
                    "泰语元音符号与阿拉伯高位/低位组合标记堆叠在空格上：UILIB 按 CCC 方向紧实堆叠——"
                    + "高位标记（U+06D6）向上摞、低位标记（U+06E3）向下摞、泰语符号（U+0E34）向上，层距贴字形；"
                    + "与网页的差异主要来自字体 glyph 覆盖：字体链缺少这些组合标记时静默跳过（不豆腐块），"
                    + "补装含泰语/阿拉伯组合标记的字体后观感接近网页。"
                    + "整段可粘贴进「多行文本」页观察（编辑保真、显示组合）。"));
            root.appendChild(waterCard);

            // ===== 卡片7：三种内容模式一致性 =====
            SceneNode modeCard = PlaygroundKit.card();
            modeCard.appendChild(PlaygroundKit.title("三种内容模式同口径（RAW / MINECRAFT / RICH）"));
            modeCard.appendChild(rawText("RAW：甲\u2028乙 + a\tb", 14));
            SceneNode minecraftLabel = SceneLabel.create(rt, new SceneLabel.Props(
                    Signal.create("MINECRAFT：§a甲\u2028§a乙 + §aa\t§ab（§ 续传）"),
                    PlaygroundKit.TEXT, 14, TextStyle.TEXT_MODE_MINECRAFT_FORMATTED,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, 0, 0.0D, 0, 0, false, null)).get();
            modeCard.appendChild(minecraftLabel);
            modeCard.appendChild(richLabel(rt, "RICH：<color=#4FC3F7>甲\u2028乙</color> + a\tb", 0));
            modeCard.appendChild(PlaygroundKit.hint(
                    "换行类/tab/零宽剥离在三种模式下行为一致；MINECRAFT 模式 § 格式码跨行续传保持原语义。"));
            root.appendChild(modeCard);
            return root;
        };
    }

    /** 创建 RAW 模式演示文本节点。 */
    private static SceneNode rawText(String value, int fontSize) {
        SceneNode node = PlaygroundKit.text(value, PlaygroundKit.TEXT, fontSize);
        node.setTextContentMode(TextStyle.TEXT_MODE_UILIB_RAW);
        return node;
    }

    /** 创建 RICH 模式 SceneLabel 演示节点（wrap 感知）。 */
    private static SceneNode richLabel(SceneRuntime rt, String text, int wrapWidth) {
        return SceneLabel.create(rt, new SceneLabel.Props(
                Signal.create(text), PlaygroundKit.TEXT, 14, TextStyle.TEXT_MODE_RICH_TAGS,
                TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, wrapWidth, 0.0D, 0, 0, false, null)).get();
    }

    /** 创建 32px 大字号 RICH 模式演示节点（不换行，组合堆叠目检用）。 */
    private static SceneNode bigText(SceneRuntime rt, String text, int wrapWidth) {
        return SceneLabel.create(rt, new SceneLabel.Props(
                Signal.create(text), PlaygroundKit.TEXT, 32, TextStyle.TEXT_MODE_RICH_TAGS,
                TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, wrapWidth, 0.0D, 0, 0, false, null)).get();
    }
}
