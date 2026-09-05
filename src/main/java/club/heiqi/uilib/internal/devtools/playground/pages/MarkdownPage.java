package club.heiqi.uilib.internal.devtools.playground.pages;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPage;
import club.heiqi.uilib.internal.devtools.playground.TestPlaygroundHost;
import club.heiqi.uilib.ui.markdown.MarkdownPainter;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * Markdown 渲染演示页（M3 验收面之一：游戏内可视页，规划 §五之二）。
 *
 * <p>链路 = 完整 L1→L2→scene 既有抽象（M7 方案乙后）：{@code MarkdownDocument.parse} →
 * {@code toLayoutLines}（块身份行接缝）→ {@link MarkdownPainter#wrapLayoutLines}（度量同源换行，
 * 折行宽度按行扣除引用缩进与列表正文列）→ 每视觉行一个段流 {@code SceneNode.setSegments}
 * 节点（chat3 消息行同款钉宽钉高范式），引用嵌套竖条 / 真横线 / 围栏底色用既有背景色节点表达。
 * 页面零 GL、零块模型引用。</p>
 *
 * <p><b>M10a 引用竖条连续化（2026-09-05 裁定 1）</b>：{@code sampleCard} 两趟装配——第一趟逐行
 * 产出「装配单元」（叶子节点 + 引用层级 + 装饰色），第二趟把<b>连续同引用段的极大区间</b>
 * 递归分组成套容器 {@code row[竖条(fillParentHeight) + 内层 column(card.gap)]}，每层一根
 * 贯穿全组的连续竖条。旧版按「每条行各挂一层 row[bar,content]」装配时，卡片列 gap=8
 * 被当成行距 → 竖条只有单行 14px 高、行间留 8px 缝（聊天面板与 L2 出图路本无此缺陷：
 * 它们行距恒等于行高）。分组内列 gap <b>恒取 {@code card.getGap()}</b>——组容器高 =
 * Σ行高 + gap×(n-1) 与平铺时逐字节相同，文字位置一字不动，只有竖条变连续。</p>
 *
 * <p><b>M10b 列表续行对齐正文列（2026-09-05 裁定 2）</b>：正文列宽由 L2 写进续行视觉行的
 * {@code leftInsetPx}（唯一行左偏移真相）；页面用 {@code extra = leftInsetPx - quoteLevel ×
 * indentStepPx} 反解列表专属偏移并以 {@code setPadding} 平移文字——页面不自算第二份标记宽。</p>
 *
 * <p>解析/换行在 mount 时一次性完成（零每帧解析裁定，规划 §六 3）；观感与手感由真机验收，
 * headless 对拍见 {@code MarkdownSoftwareRenderTest}（build/reports/markdown-render/）。
 * 滚动由 {@link TestPlaygroundHost} 视口统一提供（与其它页同构，不自建滚动容器）。</p>
 */
public final class MarkdownPage implements PlaygroundPage {

    /** 正文基准字号（UI 像素）。 */
    private static final int BASE_FONT_PX = 14;

    /** 演示段流的换行宽（UI 像素），与 LatexPage 混排行同值，避免长行横向溢出宿主视口。 */
    private static final int WRAP_WIDTH_PX = 600;

    /** M7 演示几何常量（与 chat3 设计稿同源：竖条 2 + 间隙 6、code 底色内衬 3）。 */
    private static final int QUOTE_BAR_WIDTH_PX = 2;
    private static final int QUOTE_BAR_RADIUS_PX = 1;
    private static final int QUOTE_GAP_PX = 6;
    private static final int CODE_BG_PAD_PX = 3;

    /** 样本：{卡标题, 说明, markdown 源}。覆盖验收要求的标题/围栏代码/嵌套引用/列表续行/硬换行/行内公式/链接段。 */
    private static final String[][] SAMPLES = {
            {"标题（ATX 1..6 级）", "字号增量走 MarkdownStyleTable 唯一登记面；闭序列 # 也要剥",
                    "# 一级标题\n## 二级标题 ###\n### 三级标题\n#### 四级\n##### 五级\n###### 六级标题\n正文段落回到基准字号。"},
            {"围栏代码块（字面不解析）", "``` 与 ~~~ 两种围栏；块内 // $ > 一律字面",
                    "```java\nclass Hello {\n    // 注释里的 **星号** 与 $公式$ 都不解析\n}\n```\n\n~~~\n波浪围栏块 echo $HOME\n~~~"},
            {"嵌套引用", "> 可嵌套；引用样式位（斜体开关）叠加在段样式上",
                    "> 一层引用\n>> 二层引用\n>>> 三层引用\n> 惰性续行（下一行不带 > 仍属本引用块）\n\n> 引用里套列表：\n> - 子项甲\n> - 子项乙"},
            {"列表与续行", "无序归一圆点符号、有序保留源序号；缩进续行并入同项并对齐正文列",
                    "- 第一项首行\n  第一项的缩进续行（懒延续）\n- 第二项\n  - 嵌套子项\n    - 更深层子项\n3. 有序三\n4) 有序四（右括号定界）\n   有序四项的续行"},
            {"硬换行与软换行", "行尾两空格 / 反斜杠 = 硬换行；超长行按容器宽软折",
                    "硬换行第一行  \n硬换行第二行（上行为行尾两空格）\\\n第三行（反斜杠硬换行）\n\n这是一段足够长的中文正文用于演示软换行在容器宽度处的折行行为，混合 English words 与 Punctuation, and even averyveryverylongunbreakstoken 时按词边界回退、超长 token 字符级硬断。"},
            {"行内公式", "$...$ 走 TextSegment.forLatex 原子段，与文本共享基线",
                    "行内公式：质量能量等价 $e = mc^2$ 收尾。\n分数与根号混排：$\\frac{1}{2} + \\sqrt{x^2 + y^2}$ 在文本流中。\n求和：$\\sum_{i=1}^{n} i = \\frac{n(n+1)}{2}$ 与 $\\alpha\\beta\\gamma$ 希腊字母。"},
            {"链接与行内样式", "[text](url) 产 setLink+下划线段；![图片] 只产字面 ! + 链接段",
                    "访问 [Qz-UILib 主页](https://example.com/qz) 看详情。\n组合：**粗体里的[链接](https://a.test)** 与 ~~删除线~~ 和 `code span 字面`。\n图片刻意不支持：![alt 文本](https://img.test/x.png) 输出字面感叹号加链接段。"},
            {"分隔线与刻意不支持面", "--- 产真横线（1px 铺内容宽）；表格/任务列表整行字面保留",
                    "上文与下文被 --- 分开。\n---\n表格不解析：| 列一 | 列二 |\n|---|---|\n任务列表不解析：- [ ] 未完成项"},
    };

    @Override
    public String id() {
        return "markdown";
    }

    @Override
    public String title() {
        return "Markdown 渲染";
    }

    @Override
    public String description() {
        return "L1 块身份行接缝 → L2 layoutLines 换行(引用缩进/列表正文列) → 段流/连续竖条/真横线/围栏底色（8 张样本卡）";
    }

    @Override
    public Supplier<SceneNode> build(final SceneRuntime rt) {
        return () -> {
            TextLayoutService measurer = FontService.getInstance().getTextLayoutService();
            SceneNode shell = SceneNode.column();
            shell.setFillParentWidth(true);
            shell.setGap(10);
            for (int i = 0; i < SAMPLES.length; i++) {
                shell.appendChild(sampleCard(SAMPLES[i], measurer));
            }
            return shell;
        };
    }

    /**
     * 一个围栏块 = 一个带底色的 COLUMN 容器（gap=0），内部逐行挂段流节点。
     *
     * <p>底色宽（横向）恒取 L2 的 {@code getBlockContentWidthPx()}（M8 单一真相）；
     * 底色<b>纵向连续</b>由「容器盒覆盖全部子行」保证（M9 修「三截条」）——
     * 容器上下内衬保持 0，块外缘观感与旧版逐字节相同，只消掉块内那条 8px 缝。</p>
     */
    private static SceneNode codeBlockNode(List<MarkdownLayoutLine> lines, int from, int to,
            TextLayoutService measurer) {
        MarkdownLayoutLine head = lines.get(from);
        int contentWidthPx = head.getBlockContentWidthPx();
        SceneNode block = SceneNode.column(0)
                .setHitTestable(false)
                .setBackgroundColor(head.getBackgroundArgb())
                .setPadding(0, CODE_BG_PAD_PX, 0, CODE_BG_PAD_PX);
        for (int k = from; k <= to; k++) {
            MarkdownLayoutLine row = lines.get(k);
            List<TextSegment> segments = row.getSegments();
            SceneNode rowNode = new SceneNode()
                    .setHitTestable(false)
                    .setFontSize(BASE_FONT_PX)
                    .setTextVerticalAlign(TextVerticalAlign.TOP)
                    .setPreferredHeight(Math.max(1,
                            MarkdownPainter.lineHeightPx(segments, measurer, BASE_FONT_PX)));
            if (!segments.isEmpty()) {
                rowNode.setSegments(segments);
                rowNode.setPreferredWidth(Math.max(1,
                        MarkdownPainter.lineWidthPx(segments, measurer, BASE_FONT_PX)));
                contentWidthPx = Math.max(contentWidthPx, rowNode.getPreferredWidth());
            } else {
                rowNode.setWidthSizing(SceneNode.WidthSizing.FILL);
            }
            block.appendChild(rowNode);
        }
        block.setPreferredWidth(Math.max(1, contentWidthPx + CODE_BG_PAD_PX * 2));
        return block;
    }

    /** 第一趟的装配单元：叶子节点 + 它所属块的引用层级与装饰色（第二趟分组入料）。 */
    private static final class Unit {
        final SceneNode node;
        final int quoteLevel;
        final int accentArgb;

        Unit(SceneNode node, int quoteLevel, int accentArgb) {
            this.node = node;
            this.quoteLevel = quoteLevel;
            this.accentArgb = accentArgb;
        }
    }

    /**
     * 第二趟的递归分组容器（M10a）：{@code [from,to)} 内每个单元引用层级 {@code >= level}，
     * 产 {@code row[本层连续竖条 + 内层 column]}；层级 {@code > level} 的极大连续段递归成
     * 更深一层的分组容器并入内层列，其余单元节点直接入内层列。
     *
     * <p>内层列 gap <b>恒取卡片列 gap（{@code rowGapPx} 参数由调用方传 {@code card.getGap()}）</b>：
     * 组容器高 = Σ行高 + gap×(n-1)，与这些行平铺在卡列时占据的高度逐字节相同——文字位置
     * 一字不动，竖条经 {@code fillParentHeight} 从「每行一截」变成「一组一根连续条」。
     * 跨引用组不连条：组间由 {@code blankLine()} 产的 {@code quoteLevel=0} 单元天然断开。</p>
     */
    private static SceneNode quoteGroup(List<Unit> units, int from, int toExclusive, int level,
            int rowGapPx) {
        SceneNode inner = SceneNode.column(rowGapPx).setHitTestable(false);
        int k = from;
        while (k < toExclusive) {
            if (units.get(k).quoteLevel > level) {
                int m = k;
                while (m < toExclusive && units.get(m).quoteLevel > level) {
                    m++;
                }
                inner.appendChild(quoteGroup(units, k, m, level + 1, rowGapPx));
                k = m;
            } else {
                inner.appendChild(units.get(k).node);
                k++;
            }
        }
        SceneNode quoteRow = SceneNode.row(QUOTE_GAP_PX)
                .setHitTestable(false)
                .setWidthSizing(SceneNode.WidthSizing.SHRINK);
        quoteRow.appendChild(new SceneNode()
                .setHitTestable(false)
                .setPreferredWidth(QUOTE_BAR_WIDTH_PX)
                .setFillParentHeight(true)
                .setBackgroundColor(units.get(from).accentArgb)
                .setCornerRadius(QUOTE_BAR_RADIUS_PX));
        quoteRow.appendChild(inner);
        return quoteRow;
    }

    /**
     * 一张样本卡 = 标题 + 渲染出的视觉行（两趟装配，M10a）+ 说明。
     *
     * <p>第一趟按现状逐行/逐围栏产装配单元（围栏合并、真横线、普通文本行与 M9 前口径
     * 一字不动）；第二趟把「连续 quoteLevel&gt;=1」的极大段调
     * {@link #quoteGroup(java.util.List, int, int, int, int)} 成套容器，quoteLevel==0 的单元
     * 原样入卡列。列表续行偏移（M10b）在第一趟用 {@code leftInsetPx} 反解为节点 padding。</p>
     */
    private static SceneNode sampleCard(String[] sample, TextLayoutService measurer) {
        SceneNode card = PlaygroundKit.card();
        card.appendChild(PlaygroundKit.title(sample[0]));

        MarkdownStyleTable styles = MarkdownStyleTable.defaults();
        styles.setDefaultFontSizePx(BASE_FONT_PX);
        styles.setHeadingFontSizeDeltaPx(1, 10);
        styles.setHeadingFontSizeDeltaPx(2, 7);
        styles.setHeadingFontSizeDeltaPx(3, 4);
        styles.setHeadingFontSizeDeltaPx(4, 2);
        styles.setHeadingFontSizeDeltaPx(5, 1);
        styles.setHeadingFontSizeDeltaPx(6, 0);
        // M7 方案乙：真横线取代字面 dash（既有旋钮）；本页走块身份行接缝
        styles.setThematicBreakText("");
        TextStyle base = new TextStyle();
        base.setColor(PlaygroundKit.TEXT);
        List<MarkdownLayoutLine> lines = MarkdownPainter.wrapLayoutLines(
                MarkdownDocument.parse(sample[2]).toLayoutLines(styles, base),
                measurer, WRAP_WIDTH_PX, BASE_FONT_PX);
        // 第一趟：逐行/逐围栏产「装配单元」，不套引用容器
        List<Unit> units = new ArrayList<Unit>();
        int i = 0;
        while (i < lines.size()) {
            MarkdownLayoutLine line = lines.get(i);
            // M9 围栏块 = 一个块级容器节点（既有 COLUMN + 背景色 + 内衬能力，零新图元）：
            // 底色矩形天然覆盖块内全部行（与 L2「连续同 blockId 合并为单矩形」同口径）。
            if (line.getKind() == MarkdownLayoutLine.Kind.CODE
                    && line.getBlockId() != MarkdownLayoutLine.NO_BLOCK) {
                int j = i;
                while (j + 1 < lines.size()
                        && lines.get(j + 1).getKind() == MarkdownLayoutLine.Kind.CODE
                        && lines.get(j + 1).getBlockId() == line.getBlockId()) {
                    j++;
                }
                units.add(new Unit(codeBlockNode(lines, i, j, measurer),
                        line.getQuoteLevel(), line.getAccentArgb()));
                i = j + 1;
                continue;
            }
            List<TextSegment> segments = line.getSegments();
            SceneNode lineNode;
            if (line.getKind() == MarkdownLayoutLine.Kind.THEMATIC_BREAK) {
                // 真横线：既有背景条能力，1px 逻辑厚 + 上下留气
                lineNode = new SceneNode()
                        .setHitTestable(false)
                        .setPreferredHeight(Math.max(1, line.getRuleThicknessPx()))
                        .setBackgroundColor(line.getAccentArgb())
                        .setPreferredWidth(Math.max(1, WRAP_WIDTH_PX - line.getLeftInsetPx()))
                        .setMargin(2, 0, 2, 0);
            } else {
                lineNode = new SceneNode()
                        .setHitTestable(false)
                        .setFontSize(BASE_FONT_PX)
                        .setTextVerticalAlign(TextVerticalAlign.TOP)
                        .setPreferredHeight(Math.max(1,
                                MarkdownPainter.lineHeightPx(segments, measurer, BASE_FONT_PX)));
                if (!segments.isEmpty()) {
                    lineNode.setSegments(segments);
                }
                int contentWidthPx = MarkdownPainter.lineWidthPx(segments, measurer, BASE_FONT_PX);
                if (!segments.isEmpty()) {
                    lineNode.setPreferredWidth(Math.max(1, contentWidthPx));
                } else {
                    lineNode.setWidthSizing(SceneNode.WidthSizing.FILL);
                }
                // M10b：leftInsetPx 是唯一「行左偏移」真相；扣掉引用份额后剩下的就是列表
                // 正文列偏移（M10a 文字位置一字不动 ⇒ 引用部分仍由分组结构表达，不进 padding）。
                // padding 会真实平移文字（M9 的 CODE_BG_PAD_PX 左内衬已证实），页面不自算标记宽。
                int listExtra = line.getLeftInsetPx()
                        - line.getQuoteLevel() * line.getIndentStepPx();
                if (listExtra > 0) {
                    lineNode.setPadding(0, 0, 0, listExtra);
                }
            }
            units.add(new Unit(lineNode, line.getQuoteLevel(), line.getAccentArgb()));
            i++;
        }
        // 第二趟：连续同引用段递归分组，竖条自组首贯穿组尾（M10a）
        int gapPx = card.getGap();
        int p = 0;
        while (p < units.size()) {
            Unit unit = units.get(p);
            if (unit.quoteLevel <= 0) {
                card.appendChild(unit.node);
                p++;
                continue;
            }
            int q = p;
            while (q < units.size() && units.get(q).quoteLevel >= 1) {
                q++;
            }
            card.appendChild(quoteGroup(units, p, q, 1, gapPx));
            p = q;
        }
        card.appendChild(PlaygroundKit.hint(sample[1]));
        return card;
    }
}
