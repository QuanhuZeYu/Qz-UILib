package club.heiqi.config.ui.field;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * {@link FieldRenderSupport} 单元测试：收敛 8 个 renderer 重复样板的静态工具。
 *
 * <p>分两类测试：纯函数（{@link FieldRenderSupport#labelOf} / {@link FieldRenderSupport#toDouble} /
 * {@link FieldRenderSupport#formatReadout}）无需 scheduler；signal 转换（{@link FieldRenderSupport#toStringSignal}
 * / {@link FieldRenderSupport#toNumberStringSignal} / {@link FieldRenderSupport#toDoubleSignal}）需
 * {@link ReactiveScheduler#flush()} 推进派生。</p>
 */
public class FieldRenderSupportTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    // ===== labelOf =====

    /** label 非空时直接返回 label。 */
    @Test
    public void labelOfReturnsLabelWhenPresent() {
        FieldSpec spec = ConfigSchema.builder("t")
                .section("a")
                    .string("k").label("MyLabel").build()
                .endSection()
                .build()
                .field("a.k");
        Assert.assertEquals("label 非空时返回 label", "MyLabel", FieldRenderSupport.labelOf(spec));
    }

    /** label 为 null（未调用 label()）时回退 path。 */
    @Test
    public void labelOfFallsBackToPathWhenLabelNull() {
        FieldSpec spec = ConfigSchema.builder("t")
                .section("a")
                    .string("k").build()
                .endSection()
                .build()
                .field("a.k");
        Assert.assertEquals("label null 时回退 path", "a.k", FieldRenderSupport.labelOf(spec));
    }

    /** label 为空串时回退 path。 */
    @Test
    public void labelOfFallsBackToPathWhenLabelEmpty() {
        FieldSpec spec = ConfigSchema.builder("t")
                .section("a")
                    .string("k").label("").build()
                .endSection()
                .build()
                .field("a.k");
        Assert.assertEquals("label 空串时回退 path", "a.k", FieldRenderSupport.labelOf(spec));
    }

    // ===== toStringSignal（STRING 字段用） =====

    /** null → ""；String 透传；非 String 走 valueOf。
     *
     * <p>注：{@code Computed.create} 初值默认 null，需先 flush 物化派生值后再读。</p> */
    @Test
    public void toStringSignalHandlesNullAndValues() {
        Signal<Object> src = Signal.create(null);
        ReadableSignal<String> derived = FieldRenderSupport.toStringSignal(src);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("null → 空串", "", derived.get());

        src.set("hello");
        ReactiveScheduler.get().flush();
        Assert.assertEquals("String 透传", "hello", derived.get());

        src.set(Integer.valueOf(42));
        ReactiveScheduler.get().flush();
        Assert.assertEquals("非 String 走 valueOf", "42", derived.get());
    }

    // ===== toNumberStringSignal（NUMBER 文本输入用，Number 走 formatReadout） =====

    /** null → ""；Number 经 formatReadout 去整 .0；非 Number 走 valueOf。 */
    @Test
    public void toNumberStringSignalHandlesNumberAndNonNumber() {
        Signal<Object> src = Signal.create(null);
        ReadableSignal<String> derived = FieldRenderSupport.toNumberStringSignal(src);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("null → 空串", "", derived.get());

        // 整数值 Number 去掉 .0
        src.set(Double.valueOf(5.0));
        ReactiveScheduler.get().flush();
        Assert.assertEquals("Number 整数值去 .0", "5", derived.get());

        // 浮点值保留
        src.set(Double.valueOf(3.14));
        ReactiveScheduler.get().flush();
        Assert.assertEquals("Number 浮点保留", "3.14", derived.get());

        // 非 Number（如 parse 失败的 String）走 valueOf
        src.set("abc");
        ReactiveScheduler.get().flush();
        Assert.assertEquals("非 Number 走 valueOf", "abc", derived.get());
    }

    // ===== numberTextOf / isUnfinishedNumberText（NUMBER 文本输入编辑期原文判据，纯函数） =====

    /** numberTextOf 与 toNumberStringSignal 同口径：null → ""、Number 走 formatReadout、其余 valueOf。 */
    @Test
    public void numberTextOfMatchesDerivedSignalConvention() {
        Assert.assertEquals("null → 空串", "", FieldRenderSupport.numberTextOf(null));
        Assert.assertEquals("整数 Double 去 .0", "0", FieldRenderSupport.numberTextOf(Double.valueOf(0.0)));
        Assert.assertEquals("浮点保留", "0.5", FieldRenderSupport.numberTextOf(Double.valueOf(0.5)));
        Assert.assertEquals("parse 失败原文透出", "abc", FieldRenderSupport.numberTextOf("abc"));
    }

    /**
     * 未完成写法判据：原文解析值 == draft 值即命中（{@code 0.} / {@code 5.} / {@code .5} / {@code 1e2} /
     * {@code 1.50}）；值真的不同或原文不是数则不命中（回落规范写法）。
     *
     * <p><b>证伪原缺陷</b>：输 {@code 0} 后敲 {@code .} 得到原文 {@code "0."}、draft 值为 0.0——
     * 本判据必须为 true，编辑期原文才留得住；若判据按「原文 == 规范写法」实现（旧行为的事实口径），
     * 这一格立刻变红。</p>
     */
    @Test
    public void unfinishedNumberTextStaysTrueWhileRawTextStillRepresentsValue() {
        Assert.assertTrue("0. 与 0.0 同值 → 是未完成写法",
                FieldRenderSupport.isUnfinishedNumberText("0.", Double.valueOf(0.0)));
        Assert.assertTrue("5. 与 5.0 同值", FieldRenderSupport.isUnfinishedNumberText("5.", Double.valueOf(5.0)));
        Assert.assertTrue(".5 与 0.5 同值", FieldRenderSupport.isUnfinishedNumberText(".5", Double.valueOf(0.5)));
        Assert.assertTrue("1e2 与 100 同值",
                FieldRenderSupport.isUnfinishedNumberText("1e2", Double.valueOf(100.0)));
        Assert.assertTrue("1.50 与 1.5 同值",
                FieldRenderSupport.isUnfinishedNumberText("1.50", Double.valueOf(1.5)));
        Assert.assertTrue("Integer 型 draft 值同样按数值比对（不是 equals 类型比对）",
                FieldRenderSupport.isUnfinishedNumberText("7.", Integer.valueOf(7)));

        Assert.assertFalse("值真的不同 → 不命中（回落规范写法）",
                FieldRenderSupport.isUnfinishedNumberText("0.", Double.valueOf(2.0)));
        Assert.assertFalse("原文不是数且 draft 已是数值 → 不命中",
                FieldRenderSupport.isUnfinishedNumberText("abc", Double.valueOf(1.0)));
        Assert.assertFalse("原文为空且 draft 是数值 → 不命中",
                FieldRenderSupport.isUnfinishedNumberText("", Double.valueOf(1.0)));

        // parse 失败的原文会作为 String 落进 draft：此时原文与显示文本逐字相等，仍判命中
        Assert.assertTrue("draft 存 parse 失败原文时逐字相等 → 命中",
                FieldRenderSupport.isUnfinishedNumberText("-", "-"));
        Assert.assertTrue("空串 draft（用户清空）",
                FieldRenderSupport.isUnfinishedNumberText("", ""));
        Assert.assertFalse("String 型 draft 与原文不同 → 不命中",
                FieldRenderSupport.isUnfinishedNumberText("1", "abc"));
    }

    // ===== toDoubleSignal（NUMBER slider 用） =====

    /** Number 直接取 doubleValue；可解析 String 转换；不可解析 / null → 0.0。 */
    @Test
    public void toDoubleSignalHandlesNumberStringAndInvalid() {
        Signal<Object> src = Signal.create(Integer.valueOf(7));
        ReadableSignal<Double> derived = FieldRenderSupport.toDoubleSignal(src);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("Integer → double", 7.0, derived.get(), 0.0);

        src.set("3.14");
        ReactiveScheduler.get().flush();
        Assert.assertEquals("可解析 String → double", 3.14, derived.get(), 0.0001);

        src.set("abc");
        ReactiveScheduler.get().flush();
        Assert.assertEquals("不可解析 String → 0.0", 0.0, derived.get(), 0.0);

        src.set(null);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("null → 0.0", 0.0, derived.get(), 0.0);
    }

    // ===== toDouble（纯函数） =====

    /** Number / 可解析 String / 不可解析 String / null 四挡。 */
    @Test
    public void toDoubleHandlesAllBranches() {
        Assert.assertEquals("Integer", 7.0, FieldRenderSupport.toDouble(Integer.valueOf(7)), 0.0);
        Assert.assertEquals("Double", 3.14, FieldRenderSupport.toDouble(Double.valueOf(3.14)), 0.0001);
        Assert.assertEquals("可解析 String", 2.5, FieldRenderSupport.toDouble("2.5"), 0.0);
        Assert.assertEquals("不可解析 String → 0.0", 0.0, FieldRenderSupport.toDouble("abc"), 0.0);
        Assert.assertEquals("null → 0.0", 0.0, FieldRenderSupport.toDouble(null), 0.0);
    }

    // ===== formatReadout（纯函数） =====

    /** 整数去 .0（含 0 与负数），浮点保留。 */
    @Test
    public void formatReadoutStripsIntegerZero() {
        Assert.assertEquals("5.0 → '5'", "5", FieldRenderSupport.formatReadout(5.0));
        Assert.assertEquals("0.0 → '0'", "0", FieldRenderSupport.formatReadout(0.0));
        Assert.assertEquals("-3.0 → '-3'", "-3", FieldRenderSupport.formatReadout(-3.0));
        Assert.assertEquals("5.5 保留", "5.5", FieldRenderSupport.formatReadout(5.5));
        Assert.assertEquals("-2.25 保留", "-2.25", FieldRenderSupport.formatReadout(-2.25));
    }

    /** Infinite 不走整数分支（守 !Double.isInfinite），原值 toString。 */
    @Test
    public void formatReadoutHandlesInfinite() {
        Assert.assertEquals("Infinity",
                Double.toString(Double.POSITIVE_INFINITY),
                FieldRenderSupport.formatReadout(Double.POSITIVE_INFINITY));
    }

    // ===== G15/Support 源码守卫 =====

    /** 守卫：本类零外观写入（无主题/chrome/节点引用），非视觉职责保持不变。 */
    @Test
    public void sourceGuardStaysNonVisual() throws Exception {
        String code = FieldShellBinderTest.codeWithoutComments(new String(
                java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                        "src/main/java/club/heiqi/config/ui/field/FieldRenderSupport.java")),
                java.nio.charset.StandardCharsets.UTF_8));
        String[] banned = {
                "SceneChromeTokens", "SceneControlChrome", "SceneStateColors", "SceneSurface",
                "SceneNode", "SceneRuntime", "ConfigTheme", "FormTheme", "SceneTheme",
                "Color", "Backdrop",
        };
        for (String token : banned) {
            Assert.assertFalse("守卫：FieldRenderSupport 代码不得出现外观类型 " + token,
                    code.contains(token));
        }
    }
}