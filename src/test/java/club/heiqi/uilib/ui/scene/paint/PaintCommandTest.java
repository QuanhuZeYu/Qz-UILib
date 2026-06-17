package club.heiqi.uilib.ui.scene.paint;

import org.junit.Test;
import org.junit.Assert;
import java.util.Arrays;
import java.util.List;

/**
 * PaintCommand / PaintFragment / PaintPlan 纯数据契约单元测试。
 *
 * <p>覆盖任务 T2 规定的四个测试用例，验证数据契约的正确性。</p>
 */
public class PaintCommandTest {

    // ===== 测试 1：背景命令字段正确性 =====

    @Test
    public void shouldCreateBackgroundCommandWithCorrectFields() {
        PaintCommand bg = PaintCommand.background(10, 20, 110, 70, 0xFF336699);

        Assert.assertEquals("命令类型应为 BACKGROUND",
                PaintCommandType.BACKGROUND, bg.getType());
        Assert.assertEquals("left 坐标", 10, bg.getLeft());
        Assert.assertEquals("top 坐标", 20, bg.getTop());
        Assert.assertEquals("right 坐标", 110, bg.getRight());
        Assert.assertEquals("bottom 坐标", 70, bg.getBottom());
        Assert.assertEquals("背景色", 0xFF336699, bg.getColor());
        // 非文本命令：text 为空字符串，textStyle 为 null
        Assert.assertEquals("非文本命令 text 应为空字符串", "", bg.getText());
        Assert.assertNull("非文本命令 textStyle 应为 null", bg.getTextStyle());
        // opacity 默认 1.0
        Assert.assertEquals("默认 opacity", 1.0f, bg.getOpacity(), 0.0f);
    }

    // ===== 测试 2：文本命令字段正确性 =====

    @Test
    public void shouldCreateTextCommandWithCorrectFields() {
        TextStyle style = new TextStyle(0xFFFF0000, 16);
        PaintCommand txt = PaintCommand.text(5, 30, "Hello World", style);

        Assert.assertEquals("命令类型应为 TEXT",
                PaintCommandType.TEXT, txt.getType());
        Assert.assertEquals("left 坐标", 5, txt.getLeft());
        Assert.assertEquals("top 坐标", 30, txt.getTop());
        Assert.assertEquals("文本内容", "Hello World", txt.getText());
        // TextStyle 的 equals 已重写，可直接断言
        Assert.assertEquals("textStyle 应等于传入值", style, txt.getTextStyle());
        Assert.assertEquals("textStyle.color", 0xFFFF0000, txt.getTextStyle().getColor());
        Assert.assertEquals("textStyle.fontSize", 16, txt.getTextStyle().getFontSize());
        // 非背景命令：color 为 0
        Assert.assertEquals("非背景命令 color 为 0", 0, txt.getColor());
        // opacity 默认 1.0
        Assert.assertEquals("默认 opacity", 1.0f, txt.getOpacity(), 0.0f);
    }

    // ===== 测试 3：PaintPlan 顺序组装 =====

    @Test
    public void shouldAssemblePaintPlanInOrder() {
        PaintPlan plan = new PaintPlan();

        PaintCommand bg1 = PaintCommand.background(0, 0, 100, 50, 0xFFAAAAAA);
        PaintCommand txt1 = PaintCommand.text(5, 10, "First",
                new TextStyle(0xFF000000, 12));
        PaintCommand bg2 = PaintCommand.background(0, 60, 100, 110, 0xFFBBBBBB);
        PaintCommand txt2 = PaintCommand.text(5, 70, "Second",
                new TextStyle(0xFF000000, 12));

        plan.addCommand(bg1);
        plan.addCommand(txt1);
        plan.addCommand(bg2);
        plan.addCommand(txt2);

        List<PaintCommand> result = plan.getCommands();

        Assert.assertEquals("命令总数应为 4", 4, result.size());
        // 验证顺序与加入顺序一致
        Assert.assertSame("第 0 条应为 bg1", bg1, result.get(0));
        Assert.assertSame("第 1 条应为 txt1", txt1, result.get(1));
        Assert.assertSame("第 2 条应为 bg2", bg2, result.get(2));
        Assert.assertSame("第 3 条应为 txt2", txt2, result.get(3));
    }

    // ===== 测试 4：PaintFragment 暴露命令列表 =====

    @Test
    public void shouldExposePaintFragmentCommands() {
        PaintCommand bg = PaintCommand.background(0, 0, 50, 50, 0xFFFF0000);
        PaintCommand txt = PaintCommand.text(5, 15, "Test",
                new TextStyle(0xFFFFFFFF, 14));

        List<PaintCommand> cmdList = Arrays.asList(bg, txt);
        PaintFragment fragment = new PaintFragment(cmdList);

        Assert.assertEquals("fragment 命令数量", 2, fragment.size());

        List<PaintCommand> commands = fragment.getCommands();
        Assert.assertEquals("getCommands 列表大小", 2, commands.size());
        Assert.assertSame("第 0 条应为 bg", bg, commands.get(0));
        Assert.assertSame("第 1 条应为 txt", txt, commands.get(1));
    }

    // ===== 附加：验证 equals / hashCode =====

    @Test
    public void shouldImplementEqualsAndHashCode() {
        PaintCommand bg1 = PaintCommand.background(0, 0, 100, 50, 0xFFFF0000);
        PaintCommand bg2 = PaintCommand.background(0, 0, 100, 50, 0xFFFF0000);
        PaintCommand bg3 = PaintCommand.background(0, 0, 100, 50, 0xFF00FF00);

        Assert.assertEquals("相同参数的背景命令应 equals", bg1, bg2);
        Assert.assertEquals("相同参数的 hashCode 应相等", bg1.hashCode(), bg2.hashCode());
        Assert.assertNotEquals("不同颜色的命令不应 equals", bg1, bg3);

        TextStyle s1 = new TextStyle(0xFF000000, 14);
        TextStyle s2 = new TextStyle(0xFF000000, 14);
        TextStyle s3 = new TextStyle(0xFFFFFFFF, 14);

        Assert.assertEquals("相同参数的 TextStyle 应 equals", s1, s2);
        Assert.assertEquals("相同 TextStyle hashCode", s1.hashCode(), s2.hashCode());
        Assert.assertNotEquals("不同颜色的 TextStyle 不应 equals", s1, s3);
    }

    // ===== 附加：PaintPlan.addFragment =====

    @Test
    public void shouldAddFragmentToPlan() {
        PaintCommand bg = PaintCommand.background(0, 0, 10, 10, 0xFF000000);
        PaintCommand txt = PaintCommand.text(0, 0, "Hi",
                new TextStyle(0xFFFFFFFF, 12));
        PaintFragment fragment = new PaintFragment(Arrays.asList(bg, txt));

        PaintPlan plan = new PaintPlan();
        plan.addFragment(fragment);

        Assert.assertEquals("通过 addFragment 添加后命令数", 2, plan.size());
        plan.addFragment(fragment);
        Assert.assertEquals("重复添加后命令数", 4, plan.size());
    }
}
