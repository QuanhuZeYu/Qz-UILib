package club.heiqi.uilib.internal.devtools.playground.pages;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
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
 * <p>链路 = 完整 L1→L2→scene 既有抽象：{@code MarkdownDocument.parse} →
 * {@code toSegments}（裁定 B 唯一段流接缝）→ {@link MarkdownPainter#wrapLines}（度量同源换行）→
 * 每视觉行一个段流 {@code SceneNode.setSegments} 节点（chat3 消息行同款钉宽钉高范式），
 * 由 ScenePaintEngine 产 SEGMENTS 绘制命令回放。页面零 GL、零块模型引用。</p>
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

    /** 样本：{卡标题, 说明, markdown 源}。覆盖验收要求的标题/围栏代码/嵌套引用/列表续行/硬换行/行内公式/链接段。 */
    private static final String[][] SAMPLES = {
            {"标题（ATX 1..6 级）", "字号增量走 MarkdownStyleTable 唯一登记面；闭序列 # 也要剥",
                    "# 一级标题\n## 二级标题 ###\n### 三级标题\n#### 四级\n##### 五级\n###### 六级标题\n正文段落回到基准字号。"},
            {"围栏代码块（字面不解析）", "``` 与 ~~~ 两种围栏；块内 // $ > 一律字面",
                    "```java\nclass Hello {\n    // 注释里的 **星号** 与 $公式$ 都不解析\n}\n```\n\n~~~\n波浪围栏块 echo $HOME\n~~~"},
            {"嵌套引用", "> 可嵌套；引用样式位（斜体开关）叠加在段样式上",
                    "> 一层引用\n>> 二层引用\n>>> 三层引用\n> 惰性续行（下一行不带 > 仍属本引用块）\n\n> 引用里套列表：\n> - 子项甲\n> - 子项乙"},
            {"列表与续行", "无序归一圆点符号、有序保留源序号；缩进续行并入同项",
                    "- 第一项首行\n  第一项的缩进续行（懒延续）\n- 第二项\n  - 嵌套子项\n    - 更深层子项\n3. 有序三\n4) 有序四（右括号定界）\n   有序四项的续行"},
            {"硬换行与软换行", "行尾两空格 / 反斜杠 = 硬换行；超长行按容器宽软折",
                    "硬换行第一行  \n硬换行第二行（上行为行尾两空格）\\\n第三行（反斜杠硬换行）\n\n这是一段足够长的中文正文用于演示软换行在容器宽度处的折行行为，混合 English words 与 Punctuation, and even averyveryverylongunbreakstoken 时按词边界回退、超长 token 字符级硬断。"},
            {"行内公式", "$...$ 走 TextSegment.forLatex 原子段，与文本共享基线",
                    "行内公式：质量能量等价 $e = mc^2$ 收尾。\n分数与根号混排：$\\frac{1}{2} + \\sqrt{x^2 + y^2}$ 在文本流中。\n求和：$\\sum_{i=1}^{n} i = \\frac{n(n+1)}{2}$ 与 $\\alpha\\beta\\gamma$ 希腊字母。"},
            {"链接与行内样式", "[text](url) 产 setLink+下划线段；![图片] 只产字面 ! + 链接段",
                    "访问 [Qz-UILib 主页](https://example.com/qz) 看详情。\n组合：**粗体里的[链接](https://a.test)** 与 ~~删除线~~ 和 `code span 字面`。\n图片刻意不支持：![alt 文本](https://img.test/x.png) 输出字面感叹号加链接段。"},
            {"分隔线与刻意不支持面", "--- 产样式表分隔线文本；表格/任务列表整行字面保留",
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
        return "L1 解析 → 段流接缝 → L2 MarkdownPainter 换行 → SEGMENTS 段流节点（8 张样本卡）";
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

    /** 一张样本卡 = 标题 + 渲染出的视觉行节点（每行一个段流节点，钉实测宽与行高）+ 说明。 */
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
        TextStyle base = new TextStyle();
        base.setColor(PlaygroundKit.TEXT);
        List<TextSegment> segments =
                MarkdownDocument.parse(sample[2]).toSegments(styles, base);
        List<List<TextSegment>> lines =
                MarkdownPainter.wrapLines(segments, measurer, WRAP_WIDTH_PX, BASE_FONT_PX);
        List<SceneNode> lineNodes = new ArrayList<SceneNode>();
        for (int i = 0; i < lines.size(); i++) {
            List<TextSegment> line = lines.get(i);
            SceneNode lineNode = new SceneNode()
                    .setHitTestable(false)
                    .setFontSize(BASE_FONT_PX)
                    .setTextVerticalAlign(TextVerticalAlign.TOP)
                    .setPreferredHeight(Math.max(1, MarkdownPainter.lineHeightPx(line, measurer, BASE_FONT_PX)));
            if (!line.isEmpty()) {
                lineNode.setSegments(line);
                lineNode.setPreferredWidth(Math.max(1, MarkdownPainter.lineWidthPx(line, measurer, BASE_FONT_PX)));
            } else {
                lineNode.setWidthSizing(SceneNode.WidthSizing.FILL);
            }
            lineNodes.add(lineNode);
        }
        for (int i = 0; i < lineNodes.size(); i++) {
            card.appendChild(lineNodes.get(i));
        }
        card.appendChild(PlaygroundKit.hint(sample[1]));
        return card;
    }
}
