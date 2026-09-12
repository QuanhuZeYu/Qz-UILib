package club.heiqi.uilib.ui.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.After;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;

/**
 * 玻璃档位（{@link BackdropQuality} / {@link BackdropQualityService}）的纯 JVM 语义测试。
 *
 * <p>钉住四件事：取值集合与抽头预算冻结；{@code null}/空白/未知取值一律回落完整档；
 * 热路径 {@code current()} 写入即刻可见（渲染不会晚一帧）；订阅通道 {@code quality()}
 * 按既有调度器语义帧末生效（主题重派生不重建节点）。</p>
 */
public class BackdropQualityTest {

    @After
    public void resetQuality() {
        BackdropQualityService.getInstance().resetForTest();
        ReactiveScheduler.get().flush();
    }

    /** 取值集合 = full / eco / solid；solid 是配置字面量，不是 off（YAML 1.1 会把裸词 off 当布尔 false）。 */
    @Test
    public void enumExposesFrozenConfigValuesAndTapBudgets() {
        assertEquals("full", BackdropQuality.FULL.configValue());
        assertEquals("eco", BackdropQuality.ECO.configValue());
        assertEquals("solid（不是 off：YAML 1.1 把裸词 off 解析成布尔 false，会让配置页打不开）",
                "solid", BackdropQuality.OFF.configValue());
        assertEquals(BackdropQuality.FULL.tapBudget(), 13);
        assertEquals(BackdropQuality.ECO.tapBudget(), 9);
        assertEquals("关闭档不换核（链路早退，抽头代码不执行），预算与完整档一致",
                13, BackdropQuality.OFF.tapBudget());
        assertEquals("general.backdropQuality", BackdropQualityService.CONFIG_PATH);
    }

    /** 非法输入回落完整档 = 引入档位前的观感；旧值 off 不再被接受。 */
    @Test
    public void parseFallsBackToFullForEveryInvalidInput() {
        assertSame(BackdropQuality.FULL, BackdropQuality.parse(null));
        assertSame(BackdropQuality.FULL, BackdropQuality.parse(""));
        assertSame(BackdropQuality.FULL, BackdropQuality.parse("   "));
        assertSame(BackdropQuality.FULL, BackdropQuality.parse("bogus"));
        assertSame(BackdropQuality.FULL, BackdropQuality.parse("-1"));
        assertSame("旧取值 off 已废弃（YAML 布尔陷阱）", BackdropQuality.FULL, BackdropQuality.parse("off"));
    }

    /** 合法取值大小写不敏感、忽略首尾空白。 */
    @Test
    public void parseAcceptsKnownValuesCaseInsensitively() {
        assertSame(BackdropQuality.FULL, BackdropQuality.parse("full"));
        assertSame(BackdropQuality.FULL, BackdropQuality.parse("  FULL  "));
        assertSame(BackdropQuality.ECO, BackdropQuality.parse("Eco"));
        assertSame(BackdropQuality.OFF, BackdropQuality.parse("SOLID"));
    }

    /** 未配置时默认完整档：热路径与订阅通道都是 FULL。 */
    @Test
    public void defaultsToFullBeforeAnyConfiguration() {
        BackdropQualityService service = BackdropQualityService.getInstance();
        assertSame(BackdropQuality.FULL, service.current());
        assertSame(BackdropQuality.FULL, service.quality().get());
    }

    /** 写入口：热路径立即生效，订阅通道帧末 flush 生效（两条通道都可观测）。 */
    @Test
    public void applyConfiguredUpdatesHotPathImmediatelyAndSignalAfterFlush() {
        BackdropQualityService service = BackdropQualityService.getInstance();
        service.applyConfigured("eco");

        assertSame("渲染热路径必须立即按新档位工作（不得等调度器）", BackdropQuality.ECO, service.current());
        assertSame("flush 前订阅通道仍是旧值", BackdropQuality.FULL, service.quality().get());

        ReactiveScheduler.get().flush();
        assertSame("flush 后订阅方拿到新档位（主题据此重派生）", BackdropQuality.ECO, service.quality().get());

        service.resetForTest();
        ReactiveScheduler.get().flush();
        assertSame(BackdropQuality.FULL, service.current());
    }

    /** 非法值同样必须落到完整档，且两条通道一致。 */
    @Test
    public void applyConfiguredFallsBackToFullForInvalidValue() {
        BackdropQualityService service = BackdropQualityService.getInstance();
        service.applyConfigured("eco");
        ReactiveScheduler.get().flush();

        service.applyConfigured("bogus");
        assertSame(BackdropQuality.FULL, service.current());
        ReactiveScheduler.get().flush();
        assertSame(BackdropQuality.FULL, service.quality().get());
    }
}
