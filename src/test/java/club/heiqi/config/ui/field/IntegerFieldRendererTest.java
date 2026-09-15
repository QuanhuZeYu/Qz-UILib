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
 * INTEGER 字段的 UI 编辑语义回归。
 *
 * <p>钉住两件真实回归：</p>
 * <ol>
 *   <li><b>显示与写回都是整数</b>：初值 16777216 显示 {@code "16777216"}（不是 {@code "16777216.0"}），
 *       合法输入写回 {@link Long} 而不是 {@code Double}（写回 {@code Double} 会让落盘退回浮点形态）。</li>
 *   <li><b>小数原文不被静默取整</b>：输入 {@code "1.5"} 时原文照留、草稿值就是该原文、草稿校验报整数错误；
 *       截断成 1 或夹取都是静默改值，本用例专门钉住它不发生。</li>
 * </ol>
 */
public class IntegerFieldRendererTest {

    private static final String PATH = "a.maxBytes";
    private static final int CANVAS_WIDTH = 320;
    private static final int CANVAS_HEIGHT = 120;

    private SceneInteractionHarness harness;
    private SceneRuntime runtime;
    private DraftBuffer draft;
    private DraftSignalAdapter adapter;
    private ConfigSchema schema;
    private MountHandle cardHandle;
    private MountHandle sliderHandle;
    private SceneNode card;
    /** 输入框根（B2 五槽 + 独立占位层 = 6 子） */
    private SceneNode inputRoot;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        runtime = harness.getRuntime();
        schema = ConfigSchema.builder("t")
                .section("a")
                    .integer("maxBytes").defaultValue(16777216L).range(0, 2147483647).label("Max Bytes").build()
                    .integer("level").defaultValue(7L).range(0, 10).slider(1).label("Level").build()
                .endSection()
                .build();
        Authority authority = Authority.load(new File("nonexistent-integer-field.yaml"), schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        ReactiveScheduler.get().flush();

        SceneNode sceneRoot = new SceneNode();
        cardHandle = runtime.mount(sceneRoot, () -> new IntegerFieldRenderer()
                .render(runtime, schema.field(PATH), adapter));
        runtime.flush();
        card = sceneRoot.__getChildren().get(0);
        inputRoot = findTextInputRoot(card);
        Assert.assertNotNull("前提：无 slider 声明的 INTEGER 字段必须渲染出文本输入框", inputRoot);
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
        runtime.flush();
    }

    @After
    public void tearDown() throws Exception {
        if (cardHandle != null) {
            cardHandle.dispose();
        }
        if (sliderHandle != null) {
            sliderHandle.dispose();
        }
        if (adapter != null) {
            adapter.dispose();
        }
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 初值显示整数形态，合法整数输入写回 Long（不是 Double）。 */
    @Test
    public void integerValueShowsWithoutDecimalPointAndWritesBackLong() {
        Assert.assertEquals("初值必须显示十进制整数形态", "16777216", shownText());

        adapter.onFieldEdit(PATH, "");
        runtime.flush();
        focusInputAtEnd();
        harness.typeText("2048");
        runtime.flush();

        Assert.assertEquals("整数输入原样显示", "2048", shownText());
        Assert.assertEquals("合法整数输入必须写回 Long", Long.valueOf(2048L), draft.getDraft(PATH));
        Assert.assertFalse("合法整数不应有校验错误", draft.hasError());
    }

    /** 输入 {@code "1.5"}：原文照留、草稿值是原文、校验报整数错误（不静默取整成 1）。 */
    @Test
    public void fractionalInputStaysRawAndIsRejectedByDraftValidation() {
        adapter.onFieldEdit(PATH, "");
        runtime.flush();
        focusInputAtEnd();

        harness.typeText("1.5");
        runtime.flush();

        Assert.assertEquals("小数原文必须留在输入框里（用户看得见自己写了什么）", "1.5", shownText());
        Assert.assertEquals("读不出整数的原文按原文落草稿，不得静默取整",
                "1.5", draft.getDraft(PATH));
        String error = draft.error(PATH);
        Assert.assertTrue("草稿校验必须报整数错误，实际: " + error,
                error != null && error.contains("有效整数"));
    }

    /** slider 声明的 INTEGER 字段渲染不崩，读数是整数形态。 */
    @Test
    public void sliderWidgetRendersWithIntegerReadout() {
        adapter.onFieldEdit("a.level", Long.valueOf(7L));
        runtime.flush();
        SceneNode sceneRoot = new SceneNode();
        sliderHandle = runtime.mount(sceneRoot, () -> new IntegerFieldRenderer()
                .render(runtime, schema.field("a.level"), adapter));
        runtime.flush();

        Assert.assertTrue("前提：level 声明了 slider", schema.field("a.level").widget() != null);
        SceneNode sliderCard = sceneRoot.__getChildren().get(0);
        SceneNode control = findControlRoot(sliderCard);
        Assert.assertNotNull("slider 声明的 INTEGER 字段必须产出 slider 控件", control);
        Assert.assertEquals("读数是整数形态", "7", readoutText(control));
    }

    // ==================== 辅助 ====================

    private void focusInputAtEnd() {
        runtime.requestFocus(inputRoot);
        runtime.flush();
        harness.pressKey(SceneKey.END);
    }

    /** 控件当前显示文本 = prefix + highlight + suffix（无选区时即全文）。 */
    private String shownText() {
        return text(inputRoot.__getChildren().get(0))
                + text(inputRoot.__getChildren().get(2))
                + text(inputRoot.__getChildren().get(4));
    }

    /** slider 控件根 = ROW(sliderRoot + readout)，第 2 子即读数文本。 */
    private static String readoutText(SceneNode control) {
        return text(control.__getChildren().get(1));
    }

    private static String text(SceneNode node) {
        String value = node.getText();
        return value == null ? "" : value;
    }

    /** 找文本输入框根：卡片子节点里结构为 B2 五槽 + 独立占位层（6 子）的那个。 */
    private static SceneNode findTextInputRoot(SceneNode card) {
        for (int i = 1; i < card.__getChildren().size(); i++) {
            SceneNode child = card.__getChildren().get(i);
            if (child.__getChildren().size() == 6) {
                return child;
            }
        }
        return null;
    }

    /** 找 slider 控件根：卡片子节点里含 2 子（sliderRoot + 读数）的那个（header 同为 2 子，故跳过 index 0）。 */
    private static SceneNode findControlRoot(SceneNode card) {
        for (int i = 1; i < card.__getChildren().size(); i++) {
            SceneNode child = card.__getChildren().get(i);
            if (child.__getChildren().size() == 2) {
                return child;
            }
        }
        return null;
    }
}
