package club.heiqi.config.ui.field;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ColorSpec;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.FieldType;
import club.heiqi.config.schema.HexColorCodec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * 颜色字段（widget = {@link ColorSpec}）的无头行为契约 —— 真实 scene 树 + 真实 DraftBuffer，注入键击。
 *
 * <p><b>存在理由</b>：{@code SceneTextInput} 是完全受控控件（显示文本只由外部 value 派生、自己不缓存
 * 原文），「编辑期原文必须由字段渲染器自持」这条设计只在真正敲键时才成立。本类替代一次实机开屏，
 * 覆盖三件离线可见但真机才暴露的事：中间态不被吞（{@code "#"}、十进制首字符）、非法文本不静默改值、
 * 失焦回落规范形态。渲染走生产入口（{@code NumberFieldRenderer} 的 widget 分发），不直接 new 渲染器
 * 绕过接线；字段本身经 {@code SectionSpec.Builder.color(...)} 的 DSL 声明，故取值域 / 类型断言也在此钉住。</p>
 */
public class ColorFieldRendererEditTextTest {

    /** 字段 path */
    private static final String PATH = "a.tint";
    /** 初值 {@code 0x40E6FF}（= 4253439，存量十进制形态同值） */
    private static final double TINT = 0x40E6FF;
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
                    .color("tint").defaultValue(Double.valueOf(TINT)).label("Tint").build()
                .endSection()
                .build();
        FieldSpec spec = schema.field(PATH);
        Assert.assertEquals("前提：颜色字段是 NUMBER（值语义与校验沿既有通道）", FieldType.NUMBER, spec.type());
        Assert.assertTrue("前提：.color() 声明了 ColorSpec widget", spec.widget() instanceof ColorSpec);
        Assert.assertEquals("前提：未显式 range 时补齐颜色下界", 0.0D, spec.constraints().min(), 0.0D);
        Assert.assertEquals("前提：未显式 range 时补齐颜色上界",
                (double) HexColorCodec.MAX_RGB, spec.constraints().max(), 0.0D);

        Authority authority = Authority.load(new File("nonexistent-color-edit-text.yaml"), schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        NumberFieldRenderer renderer = new NumberFieldRenderer();
        ReactiveScheduler.get().flush();

        SceneNode sceneRoot = new SceneNode();
        cardHandle = runtime.mount(sceneRoot, () -> renderer.render(runtime, schema.field(PATH), adapter));
        runtime.flush();
        card = sceneRoot.__getChildren().get(0);
        inputRoot = findTextInputRoot(card);
        Assert.assertNotNull("前提：颜色字段必须渲染出文本输入框", inputRoot);
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

    /** 开屏显示规范 HEX 形态（而不是 4253439），且显示始终从草稿值派生（外部改写跟着走）。 */
    @Test
    public void rendersCanonicalHexAndFollowsDraftValue() {
        Assert.assertEquals("初值必须显示为 #RRGGBB，不再是十进制数字", "#40E6FF", shownText());

        adapter.onFieldEdit(PATH, Double.valueOf(0x000000));
        runtime.flush();
        Assert.assertEquals("草稿变化必须驱动显示重派生", "#000000", shownText());
        Assert.assertFalse("合法颜色不得留错误", hasError());
    }

    /** 逐位敲十六进制：第六位之前全是非法中间态，必须原样显示、不写坏值；第六位才提交，失焦才规范化。 */
    @Test
    public void typingKeepsIntermediateStatesAndCommitsOnlyLegalColors() {
        clearField();
        focusInputAtEnd();
        Assert.assertEquals("前提：清空后原文为空", "", shownText());

        harness.typeText("#");
        runtime.flush();
        Assert.assertEquals("'#' 是颜色语法的一部分，不得被输入过滤吃掉", "#", shownText());
        Assert.assertEquals("残缺原文原样留在草稿（走 DraftBuffer 校验）", "#", draft.getDraft(PATH));
        Assert.assertTrue("残缺 HEX 必须报错并锁保存", hasError());

        StringBuilder typed = new StringBuilder("#");
        for (char c : "40e6f".toCharArray()) {
            harness.typeText(String.valueOf(c));
            runtime.flush();
            typed.append(c);
            Assert.assertEquals("中间态必须原样显示（不得被解析吃掉或规范化）", typed.toString(), shownText());
            Assert.assertEquals("非法中间态不得被静默改值", typed.toString(), draft.getDraft(PATH));
            Assert.assertTrue("非法中间态必须经 DraftBuffer 报错: " + typed, hasError());
        }

        harness.typeText("f");
        runtime.flush();
        Assert.assertEquals("第六位落地、尚未失焦 ⇒ 保留用户原文（不中途规范化）", "#40e6ff", shownText());
        Assert.assertEquals("完整六位必须提交为颜色值", Double.valueOf(TINT), draft.getDraft(PATH));
        Assert.assertFalse("合法颜色不得留错误", hasError());

        blurByClickingNonFocusableHeader();
        Assert.assertEquals("失焦后回落规范大写形态", "#40E6FF", shownText());
        Assert.assertEquals("失焦不改值", Double.valueOf(TINT), draft.getDraft(PATH));
    }

    /**
     * 十进制旧形态逐位仍可用：中途每个前缀本身都是合法颜色，若显示直接派生自值，首字符 {@code 4}
     * 就会被规范化成 {@code #000004}，后续输入全废——本用例正是那条回归的证伪点。
     */
    @Test
    public void plainDecimalInputStillWorksAndNormalizesOnBlur() {
        clearField();
        focusInputAtEnd();

        String typed = "";
        for (char c : "4253439".toCharArray()) {
            harness.typeText(String.valueOf(c));
            runtime.flush();
            typed += c;
            Assert.assertEquals("逐位十进制不得被中途规范化", typed, shownText());
        }
        Assert.assertEquals("十进制必须落成同一个颜色值", Double.valueOf(TINT), draft.getDraft(PATH));
        Assert.assertFalse("合法颜色不得留错误", hasError());

        blurByClickingNonFocusableHeader();
        Assert.assertEquals("失焦后规范化为 HEX 形态", "#40E6FF", shownText());
    }

    /** 非法文本必须报错并原样留在草稿里（不静默改值、不产生半截颜色值）；删空后合法输入仍可恢复。 */
    @Test
    public void illegalTextIsReportedNotSilentlyRewritten() {
        clearField();
        focusInputAtEnd();

        harness.typeText("zz");
        runtime.flush();
        Assert.assertEquals("非法原文必须原样显示", "zz", shownText());
        Assert.assertEquals("非法原文原样留在草稿", "zz", draft.getDraft(PATH));
        Assert.assertTrue("必须报错并锁保存", hasError());

        harness.pressKey(SceneKey.BACKSPACE);
        harness.pressKey(SceneKey.BACKSPACE);
        runtime.flush();
        harness.typeText("40e6ff");
        runtime.flush();
        Assert.assertEquals("改用合法形态后值落地", Double.valueOf(TINT), draft.getDraft(PATH));
        Assert.assertFalse("合法后错误消失", hasError());
    }

    // ==================== 辅助 ====================

    /** 清空字段：直接写草稿空串（控件受控，显示原文随之派生为空）。 */
    private void clearField() {
        adapter.onFieldEdit(PATH, "");
        runtime.flush();
    }

    /** 聚焦输入框并把 caret 显式移到文尾（注入文本按 caret 插入）。 */
    private void focusInputAtEnd() {
        runtime.requestFocus(inputRoot);
        runtime.flush();
        harness.pressKey(SceneKey.END);
    }

    /** 失焦：点击字段卡片 header（非 focusable）⇒ Router 隐式 clearFocus，权威焦点只经 Router 改写。 */
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

    private boolean hasError() {
        String error = adapter.errorSignal(PATH).get();
        return error != null && !error.isEmpty();
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
