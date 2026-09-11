package club.heiqi.uilib.ui.scene.layout;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;

/**
 * P1-2 守卫：{@code layoutEpoch}（批计数）与 {@code layoutChangeEpoch}（几何变更纪元）语义拆分。
 *
 * <p>拆分动机：宿主每帧固定两批 + settle 批次，批计数<b>必然</b>每帧自增；桥接值若用它，
 * 零几何变化的帧也会发布 layoutDoneSignal，使全部几何派生订阅点每帧空转。
 * 变更纪元的 bump 判据 = 本批 {@code relayoutCount > 0 || constraintRelayoutedNodes 非空}。</p>
 *
 * <p>本类同时是「漏 bump / 多 bump」的双向守卫：前 5 例证明该 bump 的场景必须 bump，
 * 第 6 例证明<b>只改绘制</b>的场景不得 bump（P1-0 §2.3 反例审查第 4/5 条）。</p>
 */
public class SceneLayoutChangeEpochTest {

    private final FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
    private final SceneLayoutEngine engine = new SceneLayoutEngine(measurer);

    /** ① 同约束第二次 layout：批计数 +1，变更纪元不动（干净帧零变化）。 */
    @Test
    public void cleanBatchAdvancesPassCountButNotChangeEpoch() {
        SceneNode root = new SceneNode();
        SceneNode label = new SceneNode();
        label.setText("hello");
        root.appendChild(label);
        Constraints constraints = new Constraints(200);

        engine.layout(root, constraints);
        int pass = engine.layoutEpoch();
        int change = engine.layoutChangeEpoch();
        Assert.assertEquals("首帧必有几何变化", 1, change);

        LayoutResult second = engine.layout(root, constraints);

        Assert.assertEquals("批计数必须照旧每批自增", pass + 1, engine.layoutEpoch());
        Assert.assertEquals("零几何变化的批不得步进变更纪元", change, engine.layoutChangeEpoch());
        Assert.assertEquals("同批 relayoutCount 为 0", 0, second.getRelayoutCount());
    }

    /** ② 脏一个叶 → 变更纪元 +1。 */
    @Test
    public void dirtyLeafBumpsChangeEpoch() {
        SceneNode root = new SceneNode();
        SceneNode label = new SceneNode();
        label.setText("hello");
        root.appendChild(label);
        Constraints constraints = new Constraints(200);
        engine.layout(root, constraints);
        engine.layout(root, constraints);
        int change = engine.layoutChangeEpoch();

        label.setPreferredHeight(30);
        engine.layout(root, constraints);

        Assert.assertEquals("叶属性变化必须步进变更纪元", change + 1, engine.layoutChangeEpoch());
    }

    /** ③ 结构变更（挂载/卸载）→ 变更纪元 +1。 */
    @Test
    public void structuralChangeBumpsChangeEpoch() {
        SceneNode root = new SceneNode();
        Constraints constraints = new Constraints(200);
        engine.layout(root, constraints);
        engine.layout(root, constraints);
        int change = engine.layoutChangeEpoch();

        SceneNode panel = new SceneNode();
        SceneNode label = new SceneNode();
        label.setText("row");
        panel.appendChild(label);
        root.appendChild(panel);
        engine.layout(root, constraints);
        Assert.assertEquals("挂载必须步进变更纪元", change + 1, engine.layoutChangeEpoch());

        int afterMount = engine.layoutChangeEpoch();
        root.removeChild(panel);
        engine.layout(root, constraints);
        Assert.assertEquals("卸载必须步进变更纪元", afterMount + 1, engine.layoutChangeEpoch());
    }

    /** ④ 字体 measurer epoch 变化 → 变更纪元 +1（失效链传导后文本叶重排）。 */
    @Test
    public void fontEpochChangeBumpsChangeEpoch() {
        SceneNode root = new SceneNode();
        SceneNode label = new SceneNode();
        label.setText("hello");
        root.appendChild(label);
        Constraints constraints = new Constraints(200);
        engine.layout(root, constraints);
        engine.layout(root, constraints);
        int change = engine.layoutChangeEpoch();

        measurer.bumpEpoch();
        engine.layout(root, constraints);

        Assert.assertEquals("字体 epoch 变化必须步进变更纪元", change + 1, engine.layoutChangeEpoch());
    }

    /** ⑤ 根约束变化（窗口 resize）→ 变更纪元 +1。 */
    @Test
    public void constraintChangeBumpsChangeEpoch() {
        SceneNode root = new SceneNode();
        SceneNode label = new SceneNode();
        label.setText("hello");
        root.appendChild(label);
        engine.layout(root, new Constraints(200));
        engine.layout(root, new Constraints(200));
        int change = engine.layoutChangeEpoch();

        engine.layout(root, new Constraints(240));

        Assert.assertEquals("约束变化必须步进变更纪元", change + 1, engine.layoutChangeEpoch());
    }

    /** ⑥ 仅绘制类变化（opacity/transform/scroll/presentation offset）不得步进变更纪元。 */
    @Test
    public void drawOnlyChangesDoNotBumpChangeEpoch() {
        SceneNode root = new SceneNode();
        SceneNode label = new SceneNode();
        label.setText("hello");
        root.appendChild(label);
        Constraints constraints = new Constraints(200);
        engine.layout(root, constraints);
        engine.layout(root, constraints);
        int change = engine.layoutChangeEpoch();

        label.setOpacity(0.5f);
        label.setTransform(Transform.translate(3.0f, 4.0f));
        root.setScrollOffsetY(7);
        label.__setPresentationOffsetY(-5);
        engine.layout(root, constraints);

        Assert.assertEquals("绘制通道的变化不是布局变化，不得步进变更纪元（否则发布语义失真）",
                change, engine.layoutChangeEpoch());
    }
}
