package club.heiqi.uilib.internal.font.tesr.angelica;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import club.heiqi.uilib.internal.font.tesr.TesrTextReplayCoordinator;

/**
 * 宿主 TESR 探针的 fail-open 与 pass 观测契约。
 *
 * <p>钉死两件事：宿主缺席（无 Angelica）时探针静默返回「无待提交几何 / pass 未知」而不抛异常；
 * pass 键标签与宿主语义一致（0/1 主 pass、2 光影 shadow pass），观测日志才可读。</p>
 */
public class AngelicaTesrBatchProbeTest {

    private static final String RENDERER_CLASS = "com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer";

    /** 无宿主组合（本仓常态）：探针静默 fail-open，不抛异常，也不假装拿得到 pass 身份。 */
    @Test
    public void absentHostKeepsProbeSilentAndUnknown() {
        Assume.assumeFalse("宿主存在时本用例不适用", hostPresent());
        AngelicaTesrBatchProbe probe = new AngelicaTesrBatchProbe();

        Assert.assertFalse(probe.hasPendingDeferredGeometry());
        Assert.assertEquals(TesrTextReplayCoordinator.HostProbe.PASS_UNKNOWN, probe.activePassKey());
        Assert.assertEquals(TesrTextReplayCoordinator.HostProbe.PASS_UNKNOWN, probe.activePassKeyOf(new Object()));
        Assert.assertEquals("unknown", probe.activePassLabel(TesrTextReplayCoordinator.HostProbe.PASS_UNKNOWN));
    }

    /** pass 键标签：0/1 主 pass、2 shadow pass，其余未知。 */
    @Test
    public void passLabelsFollowHostSemantics() {
        Assert.assertEquals("main0", AngelicaTesrBatchProbe.describePass(0));
        Assert.assertEquals("main1", AngelicaTesrBatchProbe.describePass(1));
        Assert.assertEquals("shadow", AngelicaTesrBatchProbe.describePass(2));
        Assert.assertEquals("unknown", AngelicaTesrBatchProbe.describePass(-1));
        Assert.assertEquals("unknown", AngelicaTesrBatchProbe.describePass(Integer.MIN_VALUE));
    }

    /**
     * 宿主类是否可完整加载。
     *
     * <p>测试 classpath 可能只带了宿主主类而缺其依赖（如 joml）：此时 {@code Class.forName} 抛的是
     * {@code NoClassDefFoundError} 而非 {@code ClassNotFoundException}，两者都算「宿主不可用」，
     * 与运行期探针的 fail-open 判定一致。</p>
     *
     * @return 宿主类是否可用
     */
    private static boolean hostPresent() {
        try {
            Class.forName(RENDERER_CLASS);
            return true;
        } catch (Throwable absentOrUnresolvable) {
            return false;
        }
    }
}
