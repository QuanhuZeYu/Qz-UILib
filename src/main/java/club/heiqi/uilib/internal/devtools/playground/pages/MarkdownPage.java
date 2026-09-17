package club.heiqi.uilib.internal.devtools.playground.pages;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.layout.FontSizeLimits;
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
 * <p><b>M10b 列表续行对齐正文列（2026-09-05 裁定 2）→ M10d「做全」（同日追加裁定）</b>：
 * 正文列（沿列表项标记链求和，L2 唯一算列处）由 L2 写进列表项名下<b>全部</b>视觉行的
 * {@code leftInsetPx}（唯一行左偏移真相）——含嵌套项标记行自身（它吃祖先份额）与项内
 * 后续段落/标题/引用；页面用 {@code extra = leftInsetPx - quoteLevel × indentStepPx}
 * 反解列表专属偏移并以 {@code setPadding} 平移文字——页面不自算第二份标记宽。</p>
 *
 * <p>既有行样本在 mount 时解析/换行。表格卡每次挂载只解析一次，实际内容宽或字体度量纪元
 * 变化时才重新布局，稳态复用消费层计划与节点；表格链接沿本页既有语义仅作非交互样式预览。
 * headless 见 {@code MarkdownSoftwareRenderTest} 与 {@code MarkdownTableSoftwareRenderTest}，
 * 观感与手感仍由真机验收。
 * 滚动由 {@link TestPlaygroundHost} 视口统一提供（与其它页同构，不自建滚动容器）。</p>
 *
 * <p><b>外观归属（G16/MarkdownPage）</b>：卡片标题与说明全部复用公共文本构件
 * {@link PlaygroundKit#title(String)}/{@link PlaygroundKit#hint(String)}、卡片底座复用
 * {@link PlaygroundKit#card()}——宿主上下文内默认跟随来源主题（G16/公共构件实例已迁移，
 * 本页不改 {@code PlaygroundKit}），刻意不回退静态取色。页内保留的显式颜色全部是
 * <b>markdown 渲染协议样本</b>（契约 §7.3「markdown/LaTeX 样本」不迁移清单），逐处说明：
 * <ul>
 *   <li>{@code sampleCard}/{@code tableCard} 的 {@code base.setColor(PlaygroundKit.TEXT)} 是
 *       <b>markdown 正文渲染默认色</b>：{@code base} 经 {@code MarkdownDocument.toLayoutLines}/
 *       {@code toLayoutContent} 传入 L1 内联解析（{@code MarkdownInlineParser.parse(markdown,
 *       baseStyle)}），未加标签的正文段逐字吃该色作为渲染像素——为「匹配主题」改它即改写
 *       markdown 渲染输出，摧毁 {@code MarkdownSoftwareRenderTest} 目检基线（任务单 G16 禁止项），
 *       故与 LatexPage 公式正文同理刻意保留显式，由 {@code MarkdownPageTest} 反向钉住不被主题接管。
 *       它不是页面 chrome 容器前景：chrome（标题/说明）不经过这里。</li>
 *   <li>围栏块底色 {@code head.getBackgroundArgb()}、引用竖条与真横线色
 *       {@code line.getAccentArgb()}（含 {@code Unit.accentArgb} 搬运）是由被展示的 markdown
 *       内容 + {@code MarkdownStyleTable} 登记项（chat3 出货口径）解析出的<b>数据驱动色</b>，
 *       同属渲染协议样本，逐处保留、不与主题色板发生关系；{@code MarkdownPageContent} 的
 *       {@code PaintCommand} 底色搬运同理（见该类的归属注记）。</li>
 *   <li>{@code QUOTE_BAR_WIDTH_PX}/{@code QUOTE_BAR_RADIUS_PX}/{@code QUOTE_GAP_PX}/
 *       {@code CODE_BG_PAD_PX} 与 {@code WRAP_WIDTH_PX} 是几何/布局常量，按契约 §4.2
 *       「尺寸/间距常量继续使用、主题不接管布局」保留。</li>
 * </ul>
 * 字号（{@code BASE_FONT_PX} 与各标题/说明字号）与 markdown 解析、换行、分组、表格宽度算法
 * 全部不变。</p>
 */
public final class MarkdownPage implements PlaygroundPage {

    /** 正文基准字号（UI 像素）。 */
    private static final int BASE_FONT_PX = 14;

    /** 演示段流的换行宽（UI 像素），与 LatexPage 混排行同值，避免长行横向溢出宿主视口。 */
    private static final int WRAP_WIDTH_PX = 600;

    /** M7 演示几何常量（与 chat3 设计稿同源：竖条 2 + 间隙 6、code 底色内衬 3）。
     *  均为几何/布局常量：契约 §4.2 主题不接管布局，G16 迁移原样保留。 */
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
            {"列表与续行", "无序归一圆点符号；有序按主流续排——首项源序号 = 列表 start，其后各项渲染为" + " start+下标 且定界统一句点（源里的 4) 也显示成 4.，换定界符即另起一列表）；" + "缩进续行并入同项并对齐正文列；嵌套子项的标记从父项正文列起笔，其下全部行再递进一级列",
                    "- 第一项首行\n  第一项的缩进续行（懒延续）\n- 第二项\n  - 嵌套子项\n    - 更深层子项\n3. 有序三\n4) 有序四（右括号定界）\n   有序四项的续行"},
            {"列表项名下全部块（M10d）", "松散项的第二段/第三段、项内子标题、项内引用与项内有序子项，全部与首段正文同列；有序子项自身及其内容再递进一级",
                    "- 松散项的第一段\n\n  松散项的第二段（空行分开的兄弟块）\n\n  松散项的第三段\n\n  #### 项内子标题\n\n  > 项内引用行\n\n  1. 项内有序子项\n  2. 次一个\n\n- 下一个顶层项"},
            {"硬换行与软换行", "行尾两空格 / 反斜杠 = 硬换行；超长行按容器宽软折",
                    "硬换行第一行  \n硬换行第二行（上行为行尾两空格）\\\n第三行（反斜杠硬换行）\n\n这是一段足够长的中文正文用于演示软换行在容器宽度处的折行行为，混合 English words 与 Punctuation, and even averyveryverylongunbreakstoken 时按词边界回退、超长 token 字符级硬断。"},
            {"行内公式", "$...$ 走 TextSegment.forLatex 原子段，与文本共享基线",
                    "行内公式：质量能量等价 $e = mc^2$ 收尾。\n分数与根号混排：$\\frac{1}{2} + \\sqrt{x^2 + y^2}$ 在文本流中。\n求和：$\\sum_{i=1}^{n} i = \\frac{n(n+1)}{2}$ 与 $\\alpha\\beta\\gamma$ 希腊字母。"},
            {"链接与行内样式", "[text](url) 产 setLink+下划线段；![图片] 只产字面 ! + 链接段",
                    "访问 [Qz-UILib 主页](https://example.com/qz) 看详情。\n组合：**粗体里的[链接](https://a.test)** 与 ~~删除线~~ 和 `code span 字面`。\n图片刻意不支持：![alt 文本](https://img.test/x.png) 输出字面感叹号加链接段。"},
            {"分隔线与刻意不支持面", "空行隔开的 --- 产真横线（1px 铺内容宽）；段落紧邻的一行 ---/=== 是" + " setext 标题下划线（C3b2 主流语义，故这里隔了空行）；任务列表整行字面保留；表格另见下方演示",
                    "上文与下文被 --- 分开。\n\n---\n任务列表不解析：- [ ] 未完成项"},
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
        return "标题、代码、引用、列表、公式与表格；支持对齐和自动换行";
    }

    /**
     * 本页<b>几何</b>用的生效字号：声明值 × 当前用户倍率，再归一到字号域。
     *
     * <p>与 {@code SceneNode.effectiveFontSize()} 的解析出口同式（float 乘法 → {@code Math.round} →
     * 域内夹取）。本页的几何（换行宽、行高、首选尺寸）必须与内容节点真正渲染的字号<b>同源</b>，否则任何
     * 非 100% 倍率下都会「几何按声明字号、渲染按生效字号」分叉：实测 fs=200 时出图宽 1592 已溢出 1280
     * 视口；fs=0 时装饰按 14px 排布而文字塌陷为 0，卡片被拉长（bounds 885，与 fs=200 同高）。</p>
     *
     * <p>倍率 100% 时返回值恒等于 {@link #BASE_FONT_PX}，故默认出图逐像素不变。</p>
     */
    private static int layoutFontPx(SceneRuntime rt) {
        return FontSizeLimits.effectiveFontSizePx(BASE_FONT_PX, rt.fontScale());
    }

    /**
     * 标题增量（<b>设计</b> px，相对基准字号）：h1..h6。与 {@link #BASE_FONT_PX} 同属设计量，
     * 进样式表前必须经 {@link #headingDeltaPx(int, float)} 换算成生效量。
     */
    private static final int[] HEADING_DELTA_PX = {10, 7, 4, 2, 1, 0};

    /** 设计增量 → 生效增量（与几何字号同一倍率口径）。 */
    private static int headingDeltaPx(int designDeltaPx, float scale) {
        return Math.round(designDeltaPx * scale);
    }

    /**
     * 内容布局缓存的失效纪元：字体运行时纪元（字形/度量变化）与字号代际（用户倍率变化）的组合。
     *
     * <p>只用 {@code rt.textMeasureEpoch()} 不够 —— 那是 {@code TextMeasureService.epoch()}，</p>
     * <p>不含用户倍率；倍率变化时 {@code SceneRuntime.fontEpoch()} 才 ++，必须一并进入纪元，否则字号
     * 变了内容布局不重算（缓存键里的 font 收不到新值）。</p>
     */
    private static java.util.function.IntSupplier layoutEpochs(final SceneRuntime rt) {
        return () -> (int) (rt.textMeasureEpoch() * 31L + rt.fontEpoch());
    }

    @Override
    public Supplier<SceneNode> build(final SceneRuntime rt) {
        return () -> {
            TextLayoutService measurer = FontService.getInstance().getTextLayoutService();
            SceneNode shell = SceneNode.column();
            shell.setFillParentWidth(true);
            shell.setGap(10);
            for (int i = 0; i < SAMPLES.length; i++) {
                shell.appendChild(sampleCard(SAMPLES[i], measurer, rt));
            }
            shell.appendChild(tableCard(rt));
            return shell;
        };
    }

    /** 表格首消费者；其它样本保留原装配，引用组与列表的既有观感不变。 */
    private static SceneNode tableCard(SceneRuntime rt) {
        SceneNode card = PlaygroundKit.card();
        card.appendChild(PlaygroundKit.title("表格：对齐与自动换行"));
        MarkdownStyleTable styles = MarkdownStyleTable.defaults();
        int layoutFont = layoutFontPx(rt);
        styles.setDefaultFontSizePx(layoutFont);
        TextStyle base = new TextStyle();
        // 保留显式（契约 §7.3 markdown 样本）：base 色经 L1 内联解析成为表格正文段的渲染像素默认色，
        // 非 chrome 容器前景；主题接管即改写 markdown 渲染输出（任务单 G16 禁止项），口径同 sampleCard。
        base.setColor(PlaygroundKit.TEXT);
        String source = "表格前的说明。\n\n"
                + "| 项目 | 说明 | 数量 |\n| :--- | :---: | ---: |\n"
                + "| **基础材料** | 随窗口宽度自动换行的长说明，含中文与 English words。 | 128 |\n"
                + "| [使用指南](https://example.com/guide) | `code` 与 ~~旧名称~~ | 16 |\n"
                + "| 公式 | $e = mc^2$ | 1 |\n\n"
                + "表格后的正文继续显示。";
        card.appendChild(MarkdownPageContent.create(rt, source, styles, base,
                BASE_FONT_PX, layoutFont,
                () -> FontService.getInstance().getTextLayoutService(),
                layoutEpochs(rt)));
        card.appendChild(PlaygroundKit.hint("左对齐、居中和右对齐；缩窄窗口可观察单元格换行。"));
        return card;
    }

    /**
     * 一个围栏块 = 一个带底色的 COLUMN 容器（gap=0），内部逐行挂段流节点。
     *
     * <p>底色宽（横向）恒取 L2 的 {@code getBlockContentWidthPx()}（M8 单一真相）；
     * 底色<b>纵向连续</b>由「容器盒覆盖全部子行」保证（M9 修「三截条」）——
     * 容器上下内衬保持 0，块外缘观感与旧版逐字节相同，只消掉块内那条 8px 缝。</p>
     */
    private static SceneNode codeBlockNode(List<MarkdownLayoutLine> lines, int from, int to,
            TextLayoutService measurer, int layoutFontPx) {
        MarkdownLayoutLine head = lines.get(from);
        int contentWidthPx = head.getBlockContentWidthPx();
        // M10d：项内围栏整块随正文列平移（同块各行同链同列 ⇒ 矩形仍连续，M9 语义不变；
        // 顶层围栏 extra 恒 0 ⇒ 与旧行为逐字节相同）。反解式与普通行同式，不另发明。
        int listExtra = Math.max(0, head.getLeftInsetPx()
                - head.getQuoteLevel() * head.getIndentStepPx());
        SceneNode block = SceneNode.column(0)
                .setHitTestable(false)
                // 保留显式（契约 §7.3 markdown 样本）：底色是 L2 从 MarkdownStyleTable 登记项
                // （chat3 出货口径 code 衬底）解析进本行数据对象的块底色，随被展示的 markdown
                // 内容变化，属渲染协议样本，不随主题切换。
                .setBackgroundColor(head.getBackgroundArgb())
                .setPadding(0, CODE_BG_PAD_PX, 0, CODE_BG_PAD_PX + listExtra);
        for (int k = from; k <= to; k++) {
            MarkdownLayoutLine row = lines.get(k);
            List<TextSegment> segments = row.getSegments();
            SceneNode rowNode = new SceneNode()
                    .setHitTestable(false)
                    // 声明层恒写设计基准（进场景后乘一次倍率）；几何另用 layoutFontPx。
                    .setFontSize(BASE_FONT_PX)
                    .setTextVerticalAlign(TextVerticalAlign.TOP)
                    .setPreferredHeight(Math.max(1,
                            MarkdownPainter.lineHeightPx(segments, measurer, layoutFontPx)));
            if (!segments.isEmpty()) {
                rowNode.setSegments(segments);
                rowNode.setPreferredWidth(Math.max(1,
                        MarkdownPainter.lineWidthPx(segments, measurer, layoutFontPx)));
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
     *
     * <p><b>宽度语义（#7 修复，C9·7）</b>：内层列显式 {@code SHRINK}——引用组宽 =
     * 竖条（{@code QUOTE_BAR_WIDTH_PX}）+ gap（{@code QUOTE_GAP_PX}）+ 内容列实测宽
     * （{@code SizingCalculator.computeShrinkContainerWidth} COLUMN 分支：子行最大宽 +
     * 水平 padding，被可用宽 clamp），不再伪装拉满。旧形内列随容器默认 FILL：SHRINK 行在
     * 约束下传阶段无己宽先验（{@code computeWidth(c, false)} 对 SHRINK 保守回退外层可用宽），
     * FILL 子列照单全收整行宽（720 画布实测 628），定位侧却又叠上条+gap 的 8px 主轴偏移，
     * 子右缘 636 恒超行宽 8px——纯结构缺陷，与字体无关，两平台逐字同数；此前未被看见只因
     * 切页夹具点击静默 miss（见规划 §二之八 C9 #7 与 {@code PlaygroundButtonRowLayoutTest}）。
     * 「与平铺在卡列时逐字节相同」的承诺维度不受影响：COLUMN 容器高与子定位只吃行高与 gap，
     * 组高恒 = Σ行高 + gap×(n-1)；行 y、文字 x（条+gap 起算）、竖条像素全同——变的只是
     * 隐形容器盒的右缘（引用组无底色，该边缘无像素表现）。组内各行共享内容列宽（取最宽
     * 子行），无参差；这正是「量出该组最宽子行显式 preferredWidth」设想的引擎原生形，
     * 页面不自算第二套宽度真相。</p>
     */
    private static SceneNode quoteGroup(List<Unit> units, int from, int toExclusive, int level,
            int rowGapPx) {
        // #7 修复（C9·7）：内层列显式 SHRINK——宽度语义见方法 javadoc。
        SceneNode inner = SceneNode.column(rowGapPx)
                .setHitTestable(false)
                .setWidthSizing(SceneNode.WidthSizing.SHRINK);
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
                // 保留显式（契约 §7.3 markdown 样本）：accentArgb 是第一趟从该行 L2 数据对象搬运的
                // 引用块装饰色（MarkdownStyleTable 登记项解析所得），属渲染协议样本，不随主题切换；
                // QUOTE_BAR_RADIUS_PX 为几何常量（契约 §4.2 主题不接管布局）。
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
    private static SceneNode sampleCard(String[] sample, TextLayoutService measurer, SceneRuntime rt) {
        SceneNode card = PlaygroundKit.card();
        card.appendChild(PlaygroundKit.title(sample[0]));

        MarkdownStyleTable styles = MarkdownStyleTable.defaults();
        int layoutFontPx = layoutFontPx(rt);
        // 样式表字号取生效值：段样式字号决定 L2 产出的段流（命令坐标与命令字号都在生效尺度上），
        // 而节点声明层仍写设计基准 BASE_FONT_PX —— 两侧分工见 FontSizeLimits#effectiveFontSizePx。
        styles.setDefaultFontSizePx(layoutFontPx);
        // 标题增量是<b>设计量</b>（相对基准的 px 增量，见 HEADING_DELTA_PX），必须与基准同源换算成
        // 生效量：L2 按 {@code anchor + delta} 算标题字号，anchor 已在生效尺度上，delta 若留设计值
        // 则标题层级会随倍率被压缩 —— 实测 h1 字号 24/31/38（fs=100/150/200，即 14×scale+10），
        // h1/正文 由 1.71 掉到 1.48、1.36；等比换算后 h1 恒为 24×倍率。
        float scale = rt.fontScale();
        for (int level = 0; level < HEADING_DELTA_PX.length; level++) {
            styles.setHeadingFontSizeDeltaPx(level + 1, headingDeltaPx(HEADING_DELTA_PX[level], scale));
        }
        // M7 方案乙：真横线取代字面 dash（既有旋钮）；本页走块身份行接缝
        styles.setThematicBreakText("");
        TextStyle base = new TextStyle();
        // 保留显式（契约 §7.3 markdown 样本）：本行文本的基础前景是 markdown 正文渲染默认色
        // （L1 把 base 铺进未加样式段的 TextSegment 色通道，直接决定出字像素），刻意不被主题接管；
        // 卡片标题/说明等 chrome 文字走 PlaygroundKit.title/hint 主题路径，与这里无关。
        base.setColor(PlaygroundKit.TEXT);
        List<MarkdownLayoutLine> lines = MarkdownPainter.wrapLayoutLines(
                MarkdownDocument.parse(sample[2]).toLayoutLines(styles, base),
                measurer, WRAP_WIDTH_PX, layoutFontPx);
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
                units.add(new Unit(codeBlockNode(lines, i, j, measurer, layoutFontPx),
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
                        // 保留显式（契约 §7.3 markdown 样本）：横线色与引用竖条共用该行 L2 数据对象的
                        // 装饰色（样式表登记项解析），属渲染协议样本，不随主题切换。
                        .setBackgroundColor(line.getAccentArgb())
                        .setPreferredWidth(Math.max(1, WRAP_WIDTH_PX - line.getLeftInsetPx()))
                        .setMargin(2, 0, 2, 0);
            } else {
                lineNode = new SceneNode()
                        .setHitTestable(false)
                        // 声明层恒写设计基准（进场景后乘一次倍率）；几何另用 layoutFontPx。
                        .setFontSize(BASE_FONT_PX)
                        .setTextVerticalAlign(TextVerticalAlign.TOP)
                        .setPreferredHeight(Math.max(1,
                                MarkdownPainter.lineHeightPx(segments, measurer, layoutFontPx)));
                if (!segments.isEmpty()) {
                    lineNode.setSegments(segments);
                }
                int contentWidthPx = MarkdownPainter.lineWidthPx(segments, measurer, layoutFontPx);
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
