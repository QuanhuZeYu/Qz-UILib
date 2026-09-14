package club.heiqi.config.ui.field;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * NUMBER 文本输入框「编辑期原文」回归测试 —— 缺陷：值 0 时小数点输不进去。
 *
 * <h3>原缺陷根因（本类逐格证伪）</h3>
 * <p>{@code SceneTextInput} 是受控控件（显示文本只从外部 value 派生，自己不缓存原文），而
 * "值 ↔ 文本" 映射有损：{@code Double.parseDouble("0.")} 合法 = 0.0。旧实现把「draft 值 → 文本」
 * 的派生信号直接当 value，用户先输 {@code 0} 再敲 {@code .} 时 parse 出的 0.0 与 draft 现值相等，
 * 写回被帧末「无净变化」去重丢弃 ⇒ 上游 signal 不通知 ⇒ 文本不重算 ⇒ 小数点永不显示。</p>
 *
 * <h3>断言口径</h3>
 * <p>沿真实装配路径（{@code SceneInteractionHarness} → {@code runtime.requestFocus} +
 * 注入 TEXT 帧 → flush）读控件当前显示文本（B2 五槽的 prefix+highlight+suffix 拼接）：
 * {@code 0} 后敲 {@code .} 必须显示 {@code 0.}（原缺陷下显示 {@code 0}）；{@code 5.} 同源；
 * 失焦归位规范写法；外源改写回落规范写法。值/dirty 断言保证「等值编辑不脏、真值变化仍提交」。</p>
 */
public class NumberFieldRendererEditTextTest {

    /** 字段 path */
    private static final String PATH = "a.ratio";
    private static final int CANVAS_WIDTH = 320;
    private static final int CANVAS_HEIGHT = 120;

    private SceneInteractionHarness harness;
    private SceneRuntime runtime;
    private DraftBuffer draft;
    private DraftSignalAdapter adapter;
    private MountHandle cardHandle;
    /** 字段卡片（mount 根） */
    private SceneNode card;
    /** 输入框根（B2 五槽 + 独立占位层 = 6 子） */
    private SceneNode inputRoot;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        runtime = harness.getRuntime();
        ConfigSchema schema = ConfigSchema.builder("t")
                .section("a")
                    .number("ratio").defaultValue(0.0).label("Ratio").build()
                .endSection()
                .build();
        Authority authority = Authority.load(new File("nonexistent-number-edit-text.yaml"), schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        NumberFieldRenderer renderer = new NumberFieldRenderer();
        ReactiveScheduler.get().flush();

        SceneNode sceneRoot = new SceneNode();
        cardHandle = runtime.mount(sceneRoot, () -> renderer.render(runtime, schema.field(PATH), adapter));
        runtime.flush();
        card = sceneRoot.__getChildren().get(0);
        inputRoot = findTextInputRoot(card);
        Assert.assertNotNull("前提：NUMBER 字段必须渲染出文本输入框", inputRoot);
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
        runtime.flush();
    }

    @After
    public void tearDown() throws Exception {
        if (cardHandle != null) {
            cardHandle.dispose();
        }
        if (adapter != null) {
            adapter.dispose();
        }
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 用例 ====================

    /**
     * 核心回归（缺陷原场景）：初值 0 的字段里敲 {@code .} ⇒ 编辑期原文 {@code "0."}，
     * 值仍按 parse 提交为 0.0（等值 ⇒ 不 dirty）；继续敲 {@code 5} ⇒ {@code "0.5"} 且值真变化。
     */
    @Test
    public void decimalPointTypedAfterZeroStaysVisibleWhileEditing() {
        focusInputAtEnd();
        Assert.assertEquals("前提：初值 0.0 显示为 0", "0", shownText());

        harness.typeText(".");
        runtime.flush();

        Assert.assertEquals("敲 . 后编辑期原文必须是 0.（原缺陷：显示仍为 0，小数点被吃掉）",
                "0.", shownText());
        Assert.assertEquals("等值写回仍走 parse 提交（0. = 0.0）", Double.valueOf(0.0), draft.getDraft(PATH));
        Assert.assertFalse("等值编辑不产生 dirty", draft.isDirty(PATH));

        harness.typeText("5");
        runtime.flush();

        Assert.assertEquals("继续输入得到 0.5", "0.5", shownText());
        Assert.assertEquals("值确实变化时仍要提交", Double.valueOf(0.5), draft.getDraft(PATH));
        Assert.assertTrue("真值变化后 dirty", draft.isDirty(PATH));
    }

    /**
     * {@code 5.} 形态与失焦归位：空字段起步敲 {@code 5} + {@code .} ⇒ 原文 {@code "5."}（小数点不被
     * 规范化吃掉）；失焦 ⇒ 归位规范写法 {@code "5"}（未完成形态不长期滞留，值不变）；
     * 外源改写 ⇒ 显示回落规范写法；重新聚焦从规范写法起步（不复活旧原文）。
     */
    @Test
    public void unfinishedFormSurvivesEditingThenNormalizesOnBlur() {
        adapter.onFieldEdit(PATH, "");
        runtime.flush();
        focusInputAtEnd();
        Assert.assertEquals("前提：空原文显示空串", "", shownText());

        harness.typeText("5");
        runtime.flush();
        harness.typeText(".");
        runtime.flush();

        Assert.assertEquals("5 后敲 . ⇒ 编辑期原文 5.（原缺陷：被规范化为 5）", "5.", shownText());
        Assert.assertEquals("值仍按 parse 提交 5.0", Double.valueOf(5.0), draft.getDraft(PATH));

        blurByClickingNonFocusableHeader();
        Assert.assertEquals("失焦归位规范写法", "5", shownText());
        Assert.assertEquals("失焦不改值", Double.valueOf(5.0), draft.getDraft(PATH));

        adapter.onFieldEdit(PATH, 2.5);
        runtime.flush();
        Assert.assertEquals("外源改写（重置/撤销/他控件写同字段）回落规范写法", "2.5", shownText());
        Assert.assertEquals("外源改写即真值", Double.valueOf(2.5), draft.getDraft(PATH));

        focusInputAtEnd();
        Assert.assertEquals("重新聚焦从规范写法起步（不复活旧原文）", "2.5", shownText());
    }

    /**
     * 值真的变化时显示必须跟随（编辑期原文不得把外部改写挡在外面）：外部写 3.0 ⇒ 显示 {@code "3"}
     * （整数去 .0 的既有规范写法）。
     */
    @Test
    public void externalValueChangeStillUpdatesDisplayText() {
        adapter.onFieldEdit(PATH, 3.0);
        runtime.flush();
        Assert.assertEquals("外部写入更新显示文本（整数去 .0）", "3", shownText());
        Assert.assertEquals("外部写入即真值", Double.valueOf(3.0), draft.getDraft(PATH));
        Assert.assertTrue("与 current(0.0) 不同 ⇒ dirty", draft.isDirty(PATH));
    }

    // ==================== 辅助 ====================

    /** 聚焦输入框并把 caret 显式移到文尾（注入文本按 caret 插入）。 */
    private void focusInputAtEnd() {
        runtime.requestFocus(inputRoot);
        runtime.flush();
        harness.pressKey(SceneKey.END);
    }

    /**
     * 失焦：点击字段卡片 header（非 focusable）⇒ Router 隐式 clearFocus，权威焦点只经 Router 改写。
     */
    private void blurByClickingNonFocusableHeader() {
        harness.click(card.__getChildren().get(0));
        runtime.flush();
    }

    /** 控件当前显示文本 = prefix + highlight + suffix（无选区时即全文）。 */
    private String shownText() {
        return text(inputRoot.__getChildren().get(0))
                + text(inputRoot.__getChildren().get(2))
                + text(inputRoot.__getChildren().get(4));
    }

    private static String text(SceneNode node) {
        String value = node.getText();
        return value == null ? "" : value;
    }

    /**
     * 找输入框根：卡片子节点里结构为 B2 五槽 + 独立占位层（6 子）的那个。
     *
     * @param card 字段卡片
     * @return 输入框根，未找到返回 null
     */
    private static SceneNode findTextInputRoot(SceneNode card) {
        for (int i = 1; i < card.__getChildren().size(); i++) {
            SceneNode child = card.__getChildren().get(i);
            if (child.__getChildren().size() == 6) {
                return child;
            }
        }
        return null;
    }
}
