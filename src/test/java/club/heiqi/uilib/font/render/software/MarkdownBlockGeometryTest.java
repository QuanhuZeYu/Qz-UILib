package club.heiqi.uilib.font.render.software;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
import club.heiqi.uilib.ui.markdown.MarkdownPainter;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;

/**
 * M7 方案乙块级几何（引用竖条 / 真横线 / 围栏底色）机器断言——不看图，规划 §二之三 M7 注记。
 *
 * <p>三条硬断言（用户验收 2）各配正对照 + 反空跑地板（命中数 &gt;= N）（事故档
 * ERROR-20260905 第八节「立此规矩」）：</p>
 * <ol>
 *   <li>{@link #quoteIndentMonotonicEndToEnd()}——引用第 N 层的 SEGMENTS.left 与竖条
 *       BACKGROUND 的 x 随层数严格单调增（L1 行盒 + L2 命令两级证据；反向对照 = 无引用
 *       文档零 BACKGROUND）；</li>
 *   <li>{@link #thematicBreakEmitsOneSolidBackground()}——THEMATIC_BREAK 恰产一条高 &gt; 0、
 *       宽 &gt; 0 的 BACKGROUND，且命令流不再以字面 dash 段冒充横线（旋钮两态各一，反空跑
 *       地板 &gt;= 2）；</li>
 *   <li>{@link #codeBackdropCoversAllDisplayLines()}——围栏块被窄容器折出多显示行时，
 *       单条 BACKGROUND 罩住全部 CODE 显示行；反向对照 = 同文不作围栏零底色。</li>
 * </ol>
 *
 * <p>装配纪律与 {@code MarkdownSoftwareRenderTest} 同：复用 {@code LatexSoftwareRenderKit}
 * 共享装配（严禁另 new FontService）；测量期解除宽度 miss 预算。</p>
 */
public class MarkdownBlockGeometryTest {

    private static final char LF = (char) 0x0A;
    private static final int BASE = 16;
    private static final int WIDTH = 480;
    private static final int NARROW = 120;

    private int savedWidthMissBudget = -1;

    @Before
    public void liftWidthMissBudget() {
        savedWidthMissBudget = FontConfig.widthCacheMissBudgetPerWindow;
        FontConfig.widthCacheMissBudgetPerWindow = 0;
    }

    @After
    public void restoreWidthMissBudget() {
        FontConfig.widthCacheMissBudgetPerWindow = savedWidthMissBudget;
    }

    @AfterClass
    public static void releaseShared() {
        LatexSoftwareRenderKit.resetShared();
    }

    private static TextStyle base() {
        TextStyle s = new TextStyle();
        s.setColor(0xFFFFFFFF);
        return s;
    }

    private static String joinLF(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(LF);
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /** 预装配语料全部码点（与出图/门禁同纪律：先喂字形，再断度量）。 */
    private static TextLayoutService assemble(String... sources) {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        List<TextSegment> all = new ArrayList<TextSegment>();
        for (String src : sources) {
            all.addAll(MarkdownDocument.parse(src)
                    .toSegments(MarkdownStyleTable.defaults(), base()));
        }
        LatexSoftwareRenderKit.assembleGlyphs(shared, all);
        return shared.service;
    }

    private static List<MarkdownLayoutLine> logical(String src, MarkdownStyleTable table) {
        return MarkdownDocument.parse(src).toLayoutLines(table, base());
    }

    // ==================== 断言①：引用 x 偏移端到端单调增 ====================

    @Test
    public void quoteIndentMonotonicEndToEnd() {
        String src = joinLF("> 一层", ">> 二层", ">>> 三层");
        TextLayoutService service = assemble(src, "普通段落");
        List<MarkdownLayoutLine> visual = MarkdownPainter.wrapLayoutLines(
                logical(src, MarkdownStyleTable.defaults()), service, WIDTH, BASE);
        Assert.assertEquals(3, visual.size());
        Assert.assertTrue("L1 行盒：一层 < 二层",
                visual.get(0).getLeftInsetPx() < visual.get(1).getLeftInsetPx());
        Assert.assertTrue("L1 行盒：二层 < 三层",
                visual.get(1).getLeftInsetPx() < visual.get(2).getLeftInsetPx());

        List<PaintCommand> commands = MarkdownPainter.toLayoutPaintCommands(
                logical(src, MarkdownStyleTable.defaults()), service, WIDTH, BASE);
        List<Integer> textLefts = new ArrayList<Integer>();
        for (PaintCommand command : commands) {
            if (command.getType() == PaintCommandType.SEGMENTS) {
                textLefts.add(Integer.valueOf(command.getLeft()));
            }
        }
        Assert.assertEquals("3 文本行 SEGMENTS", 3, textLefts.size());
        Assert.assertTrue("SEGMENTS.left 严格单调: " + textLefts,
                textLefts.get(0).intValue() < textLefts.get(1).intValue()
                        && textLefts.get(1).intValue() < textLefts.get(2).intValue());
        List<PaintCommand> bars = backgrounds(commands);
        Assert.assertEquals("竖条数 = 1+2+3 = 6，实测 " + bars.size(), 6, bars.size());
        int step = visual.get(0).getIndentStepPx();
        Assert.assertTrue("步长 > 0: " + step, step > 0);
        int thirdSlotBars = 0;
        int thirdTop = nthLineTop(visual, 2, service);
        for (PaintCommand bar : bars) {
            if (bar.getTop() == thirdTop && bar.getLeft() == 2 * step) {
                thirdSlotBars++;
            }
        }
        Assert.assertEquals("三层行必须有第 3 槽竖条（x = 2*step），命中 " + thirdSlotBars,
                1, thirdSlotBars);
        Assert.assertTrue("竖条命中地板 >= 6（反空跑）", bars.size() >= 6);

        List<PaintCommand> plain = MarkdownPainter.toLayoutPaintCommands(
                logical("普通段落", MarkdownStyleTable.defaults()), service, WIDTH, BASE);
        Assert.assertEquals("无引用文档零 BACKGROUND（反向对照）: " + backgrounds(plain),
                0, backgrounds(plain).size());
    }

    // ==================== 断言②：THEMATIC_BREAK 恰一条实线 ====================

    /**
     * C3b2（2026-09-06 对齐裁定）改空行隔开式：段落紧邻的 {@code ---} 已按 CommonMark 判 setext
     * 下划线（不再是分隔线，见 {@code MarkdownBlockParserTest} setext 三例），本锁钉的
     * 「THEMATIC_BREAK 恰一条实线」不变量本身不动。
     */
    @Test
    public void thematicBreakEmitsOneSolidBackground() {
        String src = joinLF("上句", "", "---", "下句");
        TextLayoutService service = assemble(src);
        MarkdownStyleTable solidOnly = MarkdownStyleTable.defaults();
        solidOnly.setThematicBreakText(""); // 既有旋钮（用户指定用法，不新加）
        List<PaintCommand> commands = MarkdownPainter.toLayoutPaintCommands(
                logical(src, solidOnly), service, WIDTH, BASE);
        List<PaintCommand> rules = backgrounds(commands);
        Assert.assertEquals("恰一条 BACKGROUND", 1, rules.size());
        PaintCommand rule = rules.get(0);
        Assert.assertTrue("高 > 0: " + (rule.getBottom() - rule.getTop()),
                rule.getBottom() - rule.getTop() > 0);
        Assert.assertTrue("宽 > 0: " + (rule.getRight() - rule.getLeft()),
                rule.getRight() - rule.getLeft() > 0);
        for (PaintCommand command : commands) {
            if (command.getType() == PaintCommandType.SEGMENTS && command.getSegments() != null) {
                for (TextSegment segment : command.getSegments()) {
                    Assert.assertTrue("命令流不得再以字面 dash 段冒充横线: <" + segment.getText() + ">",
                            !segment.getText().matches("-{3,}"));
                }
            }
        }
        List<PaintCommand> dashed = MarkdownPainter.toLayoutPaintCommands(
                logical(src, MarkdownStyleTable.defaults()), service, WIDTH, BASE);
        Assert.assertEquals("默认表（有文本）下横线同样成线：线文共存由旋钮决定", 1,
                backgrounds(dashed).size());
        Assert.assertTrue("横线命令地板 >= 2（旋钮两态各一，反空跑）",
                rules.size() + backgrounds(dashed).size() >= 2);
    }

    // ==================== 断言③：CODE 底色覆盖全部显示行 ====================

    @Test
    public void codeBackdropCoversAllDisplayLines() {
        char tick = (char) 0x60;
        String fence = String.valueOf(tick) + tick + tick;
        String longToken = "averyveryverylongunbreakstokenwhichcannotfitatanarrowwidthatall";
        String src = joinLF(fence + "java", "short line one", longToken, fence);
        TextLayoutService service = assemble(src, "没有围栏的普通段落文字");
        List<MarkdownLayoutLine> logical = logical(src, MarkdownStyleTable.defaults());
        List<MarkdownLayoutLine> visual = MarkdownPainter.wrapLayoutLines(logical, service, NARROW, BASE);
        int codeVisualLines = 0;
        int codeBg = 0;
        for (MarkdownLayoutLine line : visual) {
            if (line.getKind() == MarkdownLayoutLine.Kind.CODE) {
                codeVisualLines++;
                codeBg = line.getBackgroundArgb();
            }
        }
        Assert.assertTrue("CODE 显示行地板 >= 3（长 token 必折断）: " + codeVisualLines,
                codeVisualLines >= 3);
        Assert.assertNotEquals("CODE 行底色必须来自样式表登记", 0, codeBg);
        List<PaintCommand> commands = MarkdownPainter.toLayoutPaintCommands(logical, service, NARROW, BASE);
        List<PaintCommand> backdrops = new ArrayList<PaintCommand>();
        for (PaintCommand command : backgrounds(commands)) {
            if (command.getColor() == codeBg) {
                backdrops.add(command);
            }
        }
        Assert.assertEquals("围栏块底色恰一条（连续同 blockId 合并），实测 " + backdrops.size(),
                1, backdrops.size());
        Assert.assertTrue("底色命中地板 >= 1（反空跑）", backdrops.size() >= 1);
        PaintCommand rect = backdrops.get(0);
        int cursor = 0;
        for (MarkdownLayoutLine line : visual) {
            int height = MarkdownPainter.lineHeightPx(line.getSegments(), service, BASE);
            if (line.getKind() == MarkdownLayoutLine.Kind.CODE) {
                Assert.assertTrue("底色罩住每条 CODE 显示行（top " + rect.getTop() + " <= " + cursor + "）",
                        rect.getTop() <= cursor);
                Assert.assertTrue("底色罩住每条 CODE 显示行（bottom " + rect.getBottom()
                        + " >= " + (cursor + height) + "）",
                        rect.getBottom() >= cursor + height);
            }
            cursor += height;
        }
        String plain = joinLF("short line one", longToken);
        TextLayoutService service2 = assemble(plain);
        List<PaintCommand> plainCommands = MarkdownPainter.toLayoutPaintCommands(
                logical(plain, MarkdownStyleTable.defaults()), service2, NARROW, BASE);
        Assert.assertEquals("非围栏同文零底色（反向对照）: " + backgrounds(plainCommands),
                0, backgrounds(plainCommands).size());
    }

    // ==================== 工具 ====================

    private static List<PaintCommand> backgrounds(List<PaintCommand> commands) {
        List<PaintCommand> out = new ArrayList<PaintCommand>();
        for (PaintCommand command : commands) {
            if (command.getType() == PaintCommandType.BACKGROUND) {
                out.add(command);
            }
        }
        return out;
    }

    private static int nthLineTop(List<MarkdownLayoutLine> lines, int index,
            TextLayoutService service) {
        int top = 0;
        for (int i = 0; i < index; i++) {
            top += MarkdownPainter.lineHeightPx(lines.get(i).getSegments(), service, BASE);
        }
        return top;
    }
}
