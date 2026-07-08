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
}