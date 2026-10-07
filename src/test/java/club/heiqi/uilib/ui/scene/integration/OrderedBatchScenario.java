package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.TestResources;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Draw;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Quad;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.State;
import club.heiqi.uilib.ui.render.batch.OrderedQuadCollector;

/** 无MC真实收集器集成：透明跨资源重叠、裁剪、容量及委托屏障；出图用于agent直接检查。 */
public final class OrderedBatchScenario extends TestApplication {
    private static BufferedImage image() { return new BufferedImage(128, 96, BufferedImage.TYPE_INT_ARGB); }
    private static void draw(Graphics2D g, State state, Quad q) {
        if (state.clip == null) g.setClip(null);
        else g.setClip(state.clip.x, state.clip.y, state.clip.width, state.clip.height);
        g.setComposite(AlphaComposite.SrcOver); g.setColor(new Color(q.color, true));
        g.fillRect((int) q.x, (int) q.y, (int) q.width, (int) q.height);
    }
    public static void main(String[] args) throws Exception { new OrderedBatchScenario().run(args); }

    @Override protected void runTests() throws Exception {
        String[] args = arguments();
        File output = TestResources.outputDirectory(args, 0, "build/font-machine/batch").toFile();
        BufferedImage reference = image(), actual = image();
        Graphics2D original = reference.createGraphics(), replay = actual.createGraphics();
        onClose(original::dispose); onClose(replay::dispose);
        List<OrderedQuadBatch> captured = new ArrayList<>();
        OrderedQuadCollector collector = new OrderedQuadCollector(17, segment -> {
            captured.add(segment);
            for (Draw batch : segment.draws())
                for (int i = batch.firstQuad; i < batch.firstQuad + batch.quadCount; i++)
                    draw(replay, batch.state, segment.quads().get(i));
        });
        State a = new State(0, 1, 1, null), b = new State(0, 2, 1, null);
        State c = new State(0, 1, 1, new LayoutBox(20, 15, 40, 40));
        State empty = new State(0, 1, 1, new LayoutBox(0, 0, 0, 0));
        scenario("透明跨资源A-B-A保序", () -> {
        State[] firstStates = {a, b, a};
        int[] firstColors = {0x80ff0000, 0x8000ff00, 0x800000ff};
        for (int i = 0; i < firstStates.length; i++) {
            Quad q = new Quad(5, 5, 25, 25, 0, 0, 1, 1, 0, firstColors[i]);
            draw(original, firstStates[i], q); collector.add(firstStates[i], q);
        }
        collector.flush();
        if (captured.size() != 1 || captured.get(0).draws().size() != 3)
            throw new AssertionError("跨页A/B/A必须保留三个有序范围");
        });
        scenario("同数组不同层不拆批", () -> {
        // 数组层只是顶点数据，不拆批；模拟同资源页层连续混排。
        for (int i = 0; i < 6; i++) {
            Quad q = new Quad(70 + i * 3, 5, 15, 15, 0, 0, 1, 1, i, 0x7080ffff);
            draw(original, a, q); collector.add(a, q);
        }
        collector.flush();
        if (captured.get(1).draws().size() != 1) throw new AssertionError("同数组资源的层变化不应拆批");
        });
        scenario("随机透明重叠裁剪容量与委托屏障", () -> {
        Random random = new Random(20261007L);
        State[] states = {a, b, c, empty};
        for (int i = 0; i < 500; i++) {
            State state = states[random.nextInt(states.length)];
            Quad q = new Quad(random.nextInt(160) - 20, random.nextInt(120) - 15,
                random.nextInt(30), random.nextInt(30), 0, 0, 1, 1, 0, random.nextInt());
            draw(original, state, q); collector.add(state, q);
            if (i % 71 == 0) {
                collector.flush();
                // 真正插入外部绘制。若本段被推迟到外部绘制后，透明重叠的像素会改变。
                Quad delegated = new Quad(0, 20, 128, 24, 0, 0, 1, 1, 0, 0x60ff30ff);
                draw(original, a, delegated); draw(replay, a, delegated);
            }
        }
        collector.flush(); original.dispose();
        });
        scenario("完整提交范围及像素一致性", () -> {
        int quadCount = 0, drawCount = 0;
        for (OrderedQuadBatch segment : captured) {
            if (segment.quads().size() > 17) throw new AssertionError("容量穿透");
            int covered = 0;
            for (Draw batch : segment.draws()) {
                if (batch.firstQuad != covered || batch.quadCount <= 0) throw new AssertionError("提交范围不连续");
                covered += batch.quadCount; drawCount++;
            }
            if (covered != segment.quads().size()) throw new AssertionError("图元遗漏");
            quadCount += covered;
        }
        replay.dispose();
        int mismatch = 0;
        for (int y = 0; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++)
            if (actual.getRGB(x, y) != reference.getRGB(x, y)) mismatch++;
        ImageIO.write(reference, "png", new File(output, "ordered-reference.png"));
        ImageIO.write(actual, "png", new File(output, "ordered-batched.png"));
        if (mismatch != 0) throw new AssertionError("合批改变画面像素: " + mismatch);

        System.out.println("有序合批出图通过：像素差=" + mismatch + "，可见图元=" + quadCount
            + "，draw范围=" + drawCount + "，提交段=" + captured.size());
        });
        scenario("合批载荷不可变", () -> {
            try { captured.get(0).quads().clear(); throw new AssertionError("载荷可变"); }
            catch (UnsupportedOperationException expected) { }
        });
    }
}
