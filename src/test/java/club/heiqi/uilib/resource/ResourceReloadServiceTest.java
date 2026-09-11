package club.heiqi.uilib.resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;

/**
 * {@link ResourceReloadService} 契约测试：代际单调推进、监听器隔离、注册幂等、有界性。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §2.1 #1 / A-07。</p>
 */
public class ResourceReloadServiceTest {

    private final ResourceReloadService service = ResourceReloadService.getInstance();

    @Before
    public void setUp() {
        service.__resetForTests();
        service.__resetRegistrationForTests();
    }

    @After
    public void tearDown() {
        service.__resetForTests();
        service.__resetRegistrationForTests();
    }

    /** 每次重载 +1 且监听器收到推进后的代际；回调内不得重建缓存（本服务只发号）。 */
    @Test
    public void reloadAdvancesEpochAndNotifiesListeners() {
        Assert.assertEquals(0L, service.resourceEpoch());
        final List<Long> seen = new ArrayList<Long>();
        service.addListener(seen::add);

        service.onResourceManagerReload(null);
        service.onResourceManagerReload(null);

        Assert.assertEquals(2L, service.resourceEpoch());
        Assert.assertEquals(java.util.Arrays.asList(Long.valueOf(1L), Long.valueOf(2L)), seen);
    }

    /** 单个监听器异常被隔离：代际继续推进，其余监听器仍被通知。 */
    @Test
    public void listenerFailureIsIsolated() {
        final List<Long> seen = new ArrayList<Long>();
        service.addListener(epoch -> {
            throw new IllegalStateException("boom");
        });
        service.addListener(seen::add);

        service.onResourceManagerReload(null);

        Assert.assertEquals(1L, service.resourceEpoch());
        Assert.assertEquals(1, seen.size());
    }

    /** 监听列表有界且可注销（重复注册同实例为幂等空操作）。 */
    @Test
    public void listenersAreDeduplicatedAndRemovable() {
        ResourceReloadService.Listener listener = epoch -> { };
        service.addListener(listener);
        service.addListener(listener);
        Assert.assertEquals(1, service.listenerCount());

        service.removeListener(listener);
        Assert.assertEquals(0, service.listenerCount());
    }

    /** 注册幂等：同一管理器只挂一次，且注册即回调一次（1.7.10 语义）→ 代际 0 → 1。 */
    @Test
    public void registrationIsIdempotentAndImmediateCallbackIsCountedOnce() {
        RecordingResourceManager manager = new RecordingResourceManager();
        Assert.assertTrue(service.register(manager));
        Assert.assertEquals("注册即回调一次", 1, manager.reloadCallbacks);
        Assert.assertEquals(1L, service.resourceEpoch());

        Assert.assertFalse("重复注册必须直接返回 false（幂等）", service.register(manager));
        Assert.assertEquals("不得重复挂载", 1, manager.registeredListeners);
        Assert.assertEquals(1, manager.reloadCallbacks);
    }

    /** 无客户端实例（headless 测试/服务端）时注册失败但不抛异常。 */
    @Test
    public void registrationWithoutClientIsSafe() {
        Assert.assertFalse(service.registerToClient());
        Assert.assertFalse(service.isRegistered());
    }

    /** 可重载管理器最小替身：只记录注册与回调次数，不触碰真实资源。 */
    private static final class RecordingResourceManager implements IReloadableResourceManager {
        private int registeredListeners;
        private int reloadCallbacks;

        @Override
        public Set<String> getResourceDomains() {
            return Collections.emptySet();
        }

        @Override
        public IResource getResource(ResourceLocation location) throws IOException {
            throw new IOException("stub");
        }

        @Override
        public List<IResource> getAllResources(ResourceLocation location) throws IOException {
            throw new IOException("stub");
        }

        @Override
        public void reloadResources(List<IResourcePack> resourcePacks) {
        }

        @Override
        public void registerReloadListener(IResourceManagerReloadListener listener) {
            registeredListeners++;
            // 复刻 SimpleReloadableResourceManager 的「注册即回调一次」语义。
            listener.onResourceManagerReload(this);
            reloadCallbacks++;
        }
    }

    /** 编译期锚：IReloadableResourceManager 的 IResourceManager 契约必须被完整实现（防接口漂移）。 */
    @Test
    public void stubImplementsResourceManagerContract() {
        IResourceManager manager = new RecordingResourceManager();
        Assert.assertNotNull(manager);
    }
}
