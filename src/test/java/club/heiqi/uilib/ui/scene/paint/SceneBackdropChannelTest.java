package club.heiqi.uilib.ui.scene.paint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 声明式玻璃通道（{@code SceneNode.setBackdrop} → BACKDROP 命令）契约测试。
 *
 * <p>动机：backdrop 长期只有宿主侧命令式入口，scene 侧无声明通道；聊天等"每块气泡
 * 都是一块玻璃"的形态必须在布局完成后才知道矩形，宿主侧预画必然晚一帧。本测试锁住
 * 通道的三条不可让语义：</p>
 * <ol>
 *   <li><strong>顺序</strong>：BACKDROP 必须先于同节点 BACKGROUND——玻璃是"背景被改色"，
 *   节点半透明底色要叠在玻璃之上；反序会把玻璃整个盖住（等价没接，且不报错）。</li>
 *   <li><strong>平移透传</strong>：命令经 fragment 偏移重建（translatedBy），backdrop 声明
 *   若漏传则玻璃静默消失。</li>
 *   <li><strong>关闭态零污染</strong>：未设/非活跃 backdrop 不得产出 BACKDROP 命令，
 *   既有纯背景节点的命令序列必须一字不变。</li>
 * </ol>
 */
public class SceneBackdropChannelTest {

    private final FixedTextMeasurer measurer = new FixedTextMeasurer();
    private final SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
    private final ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);

    private List<PaintCommand> commandsOfType(PaintPlan plan, PaintCommandType type) {
        List<PaintCommand> out = new ArrayList<PaintCommand>();
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == type) {
                out.add(command);
            }
        }
        return out;
    }

    private int firstIndexOfType(PaintPlan plan, PaintCommandType type) {
        List<PaintCommand> commands = plan.getCommands();
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).getType() == type) {
                return i;
            }
        }
        return -1;
    }

    /** 玻璃必须先于自身底色：否则半透明填充把玻璃盖住，观感等价"没接"。 */
    @Test
    public void backdropCommandPrecedesBackgroundOfSameNode() {
        SceneNode root = new SceneNode();
        SceneNode bubble = new SceneNode();
        bubble.setBackgroundColor(0x8C272F3A);
        bubble.setCornerRadius(8);
        bubble.setBackdrop(UiBackdrop.liquidGlass(UiGlassMaterial.DARK_REGULAR, 8, 0.5F));
        root.appendChild(bubble);

        layoutEngine.layout(root, new Constraints(200, 60));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        int backdropIndex = firstIndexOfType(plan, PaintCommandType.BACKDROP);
        int backgroundIndex = firstIndexOfType(plan, PaintCommandType.BACKGROUND);
        assertTrue("必须产出 BACKDROP 命令", backdropIndex >= 0);
        assertTrue("BACKGROUND 仍应存在（半透明底叠在玻璃上）", backgroundIndex >= 0);
        assertTrue("BACKDROP 必须先于 BACKGROUND，实际 backdrop=" + backdropIndex
                + " background=" + backgroundIndex, backdropIndex < backgroundIndex);

        PaintCommand backdrop = plan.getCommands().get(backdropIndex);
        assertNotNull("命令必须携带配方", backdrop.getBackdrop());
        assertEquals("半径取自节点圆角", 8, backdrop.getCornerRadius());
        assertEquals(0.5F, backdrop.getBackdrop().getEffect().getLensStrength(), 1.0e-6F);
    }

    /** translatedBy 重建命令时必须透传 backdrop，漏传即静默丢玻璃。 */
    @Test
    public void translatedByPreservesBackdropDeclaration() {
        PaintCommand original = PaintCommand.backdrop(0, 0, 100, 40,
                UiBackdrop.of(UiGlassMaterial.REGULAR, 12), 8, -1, -1, -1, -1);

        PaintCommand moved = original.translatedBy(35, 70);

        assertSame("backdrop 声明必须随平移透传", original.getBackdrop(), moved.getBackdrop());
        assertEquals(35, moved.getLeft());
        assertEquals(70, moved.getTop());
        assertEquals("类型不得在平移中丢失", PaintCommandType.BACKDROP, moved.getType());
    }

    /** 未设玻璃的既有节点：命令序列必须与引入本通道前完全一致（零污染）。 */
    @Test
    public void nodesWithoutBackdropEmitNoBackdropCommand() {
        SceneNode root = new SceneNode();
        SceneNode plain = new SceneNode();
        plain.setBackgroundColor(0xFF336699);
        plain.setCornerRadius(4);
        root.appendChild(plain);

        layoutEngine.layout(root, new Constraints(200, 60));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        assertEquals("无 backdrop 不得产出该命令", 0, commandsOfType(plan, PaintCommandType.BACKDROP).size());
        assertEquals(1, commandsOfType(plan, PaintCommandType.BACKGROUND).size());
    }

    /** blur=0 且无材质档 = 无可见产出，不得发命令（省掉一次无意义快照捕获）。 */
    @Test
    public void inactiveBackdropEmitsNothing() {
        SceneNode root = new SceneNode();
        SceneNode node = new SceneNode();
        node.setBackgroundColor(0x8C272F3A);
        node.setBackdrop(UiBackdrop.of((UiGlassMaterial) null, 0));
        root.appendChild(node);

        layoutEngine.layout(root, new Constraints(200, 60));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        assertEquals("非活跃 backdrop 不发命令", 0, commandsOfType(plan, PaintCommandType.BACKDROP).size());
    }

    /** 四角独立圆角的气泡（聊天分级圆角）必须把四角一并带进命令。 */
    @Test
    public void perCornerRadiiTravelWithBackdropCommand() {
        SceneNode root = new SceneNode();
        SceneNode bubble = new SceneNode();
        bubble.setBackgroundColor(0x8C272F3A);
        bubble.setCornerRadius(12, 4, 12, 4);
        bubble.setBackdrop(UiBackdrop.of(UiGlassMaterial.DARK_THIN, 8));
        root.appendChild(bubble);

        layoutEngine.layout(root, new Constraints(200, 60));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        List<PaintCommand> backdrops = commandsOfType(plan, PaintCommandType.BACKDROP);
        assertEquals(1, backdrops.size());
        PaintCommand command = backdrops.get(0);
        assertTrue("必须标记为分角", command.hasPerCornerRadii());
        assertEquals(12, command.getCornerRadiusTopLeft());
        assertEquals(4, command.getCornerRadiusTopRight());
        assertEquals(12, command.getCornerRadiusBottomRight());
        assertEquals(4, command.getCornerRadiusBottomLeft());
    }

    /** setter 同值不脏化：聊天每帧重设同一配方不得反复触发 PAINT。 */
    @Test
    public void settingSameBackdropIsNoOp() {
        SceneNode node = new SceneNode();
        UiBackdrop backdrop = UiBackdrop.of(UiGlassMaterial.DARK_REGULAR, 8);
        node.setBackdrop(backdrop);
        assertEquals(backdrop, node.getBackdrop());

        node.clearPaintDirty();
        node.setBackdrop(UiBackdrop.of(UiGlassMaterial.DARK_REGULAR, 8));
        assertTrue("等值重复设置不得打 PAINT 脏标记", !node.__isSelfPaintDirty());

        node.setBackdrop(null);
        assertNull(node.getBackdrop());
    }
}
