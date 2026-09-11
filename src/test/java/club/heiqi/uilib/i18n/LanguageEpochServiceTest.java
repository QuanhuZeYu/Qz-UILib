package club.heiqi.uilib.i18n;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.resource.ResourceReloadService;

/**
 * {@link LanguageEpochService} 契约测试：只有语言码变化才推进文本代际；资源重载但语言不变时保持恒等。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §2.1 #2 / A-04。</p>
 */
public class LanguageEpochServiceTest {

    private final LanguageEpochService service = LanguageEpochService.getInstance();
    private final ResourceReloadService reload = ResourceReloadService.getInstance();

    @Before
    public void setUp() {
        reload.__resetForTests();
        service.__resetForTests();
    }

    @After
    public void tearDown() {
        service.__resetForTests();
        reload.__resetForTests();
    }

    /** 语言码未变：重载不推进文本代际（资源包切换 ≠ 文本代际变化的伪失效被挡住）。 */
    @Test
    public void unchangedLanguageKeepsEpochIdentity() {
        service.__installLanguageCodeSupplierForTests(() -> "en_US");
        service.install();
        Assert.assertEquals(0L, service.nameEpoch());

        Assert.assertFalse(service.onResourceReload());
        Assert.assertEquals(0L, service.nameEpoch());
    }

    /** 语言码变化：恰好推进一次；重复读取同码不再推进（幂等）。 */
    @Test
    public void changedLanguageAdvancesExactlyOnce() {
        final String[] code = { "en_US" };
        service.__installLanguageCodeSupplierForTests(() -> code[0]);
        service.install();

        code[0] = "zh_CN";
        Assert.assertTrue(service.onResourceReload());
        Assert.assertEquals(1L, service.nameEpoch());
        Assert.assertFalse(service.onResourceReload());
        Assert.assertEquals(1L, service.nameEpoch());

        code[0] = "ja_JP";
        Assert.assertTrue(service.onResourceReload());
        Assert.assertEquals(2L, service.nameEpoch());
    }

    /** 安装是幂等的，且安装后语言码变化经资源重载通道自动推进（单次订阅）。 */
    @Test
    public void installIsIdempotentAndDrivenByReloadChannel() {
        final String[] code = { "en_US" };
        service.__installLanguageCodeSupplierForTests(() -> code[0]);
        Assert.assertTrue(service.install());
        Assert.assertFalse(service.install());
        Assert.assertEquals(1, reload.listenerCount());

        code[0] = "zh_CN";
        reload.onResourceManagerReload(null);

        Assert.assertEquals(1L, service.nameEpoch());
        Assert.assertEquals(1L, reload.resourceEpoch());
    }

    /** 语言码不可得（无客户端实例）：按未变化处理，不推进、不抛异常。 */
    @Test
    public void missingLanguageCodeDoesNotAdvance() {
        service.__installLanguageCodeSupplierForTests(null);
        service.install();
        Assert.assertNull(LanguageEpochService.currentLanguageCode());
        Assert.assertFalse(service.onResourceReload());
        Assert.assertEquals(0L, service.nameEpoch());
    }
}
