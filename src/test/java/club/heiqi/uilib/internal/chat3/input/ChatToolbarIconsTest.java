package club.heiqi.uilib.internal.chat3.input;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.image.DocumentRemoteImageCache;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache.Entry;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache.Status;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 离线验证真实缓存条目发布与 scene 帧消费，不启动网络请求。 */
public class ChatToolbarIconsTest {

    private DocumentRemoteImageCache cache;
    private SceneRuntime rt;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        cache = DocumentRemoteImageCache.getInstance();
        cache.clearForTesting();
        rt = new SceneRuntime();
    }

    @After
    public void tearDown() {
        rt.dispose();
        cache.clearForTesting();
        ReactiveScheduler.get().reset();
    }

    @Test
    public void lateCompletionUpdatesAllInstancesOnlyOnUiFrameAndStopsWatching() throws Exception {
        BufferedImage original = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        original.setRGB(0, 0, 0x7F123456);
        original.setRGB(1, 0, 0xFF123456);
        Entry entry = pendingEntry("edit", original);
        SceneNode first = icon();
        SceneNode second = icon();
        int before = effectCount();
        ChatToolbarIcons.attach(rt, first, "edit");
        ChatToolbarIcons.attach(rt, second, "edit");
        rt.flush();
        Assert.assertEquals(before + 2, effectCount());
        Assert.assertEquals("E", first.getText());
        Assert.assertNull(first.getImageSource());

        publishFromWorker(entry, "markLoaded", original);
        Assert.assertNull("下载完成不能直接写节点", first.getImageSource());
        rt.flush();
        Assert.assertNull("已完成但没有新帧，绑定不应自行运行", first.getImageSource());
        rt.__tickFrame(1L);
        rt.flush();

        HostImageSource firstSource = (HostImageSource) first.getImageSource();
        HostImageSource secondSource = (HostImageSource) second.getImageSource();
        Assert.assertNotNull(firstSource);
        Assert.assertNotNull("同 URL 的后续消费者也必须刷新", secondSource);
        Assert.assertEquals("chat-toolbar:white:" + ChatToolbarIcons.urlFor("edit"), firstSource.getImageKey());
        Assert.assertEquals(firstSource.getImageKey(), secondSource.getImageKey());
        Assert.assertNotSame("不得原地白化共享缓存", original, firstSource.getBufferedImage());
        Assert.assertEquals(0x7FFFFFFF, firstSource.getBufferedImage().getRGB(0, 0));
        Assert.assertEquals(0xFFFFFFFF, firstSource.getBufferedImage().getRGB(1, 0));
        Assert.assertEquals(0, firstSource.getBufferedImage().getRGB(0, 1) >>> 24);
        Assert.assertEquals(0x7F123456, original.getRGB(0, 0));
        Assert.assertEquals("", first.getText());
        Assert.assertEquals(16, first.getPreferredWidth());
        Assert.assertEquals(16, first.getPreferredHeight());
        Assert.assertEquals("成功后不残留帧订阅", before, effectCount());
        rt.__tickFrame(2L);
        rt.flush();
        Assert.assertSame("完成后不重复转换位图", firstSource, first.getImageSource());
        rt.dispose();
        Assert.assertNull("卸载释放节点上的图片引用", first.getImageSource());
    }

    @Test
    public void ownerUnmountBeforeCompletionPreventsLateNodeWrites() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        Entry entry = pendingEntry("cancel", image);
        Owner owner = new Owner();
        SceneNode icon = icon();
        int before = effectCount();
        try {
            owner.run(() -> ChatToolbarIcons.attach(rt, icon, "cancel"));
            rt.flush();
            Assert.assertEquals("X", icon.getText());
            owner.dispose();
            Assert.assertEquals(before, effectCount());
            Assert.assertNull(icon.getImageSource());
            String disposedText = icon.getText();
            publishFromWorker(entry, "markLoaded", image);
            rt.__tickFrame(1L);
            rt.flush();
            Assert.assertNull("卸载节点不能被迟到完成回写", icon.getImageSource());
            Assert.assertEquals(disposedText, icon.getText());
            Assert.assertEquals(before, effectCount());
        } finally {
            owner.dispose();
        }
    }

    @Test
    public void failureKeepsRecognizableFallbackAndStopsWatching() throws Exception {
        int before = effectCount();
        String[] names = {"edit", "finish", "cancel", "reset-current", "reset-all", "action"};
        String[] fallback = {"E", "V", "X", "<", "R", "*"};
        for (int i = 0; i < names.length; i++) {
            cache.putFailedForTesting(ChatToolbarIcons.urlFor(names[i]));
            SceneNode icon = icon();
            ChatToolbarIcons.attach(rt, icon, names[i]);
            Assert.assertEquals(fallback[i], icon.getText());
            rt.flush();
            Assert.assertNull(icon.getImageSource());
            Assert.assertEquals(fallback[i], icon.getText());
            Assert.assertEquals(0xFFFFFFFF, icon.getTextColor());
            Assert.assertEquals(before, effectCount());
        }
    }

    @Test
    public void lateFailureStopsFrameSubscriptionWithoutRemovingFallback() throws Exception {
        Entry entry = pendingEntry("reset-current", new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB));
        SceneNode icon = icon();
        int before = effectCount();
        ChatToolbarIcons.attach(rt, icon, "reset-current");
        rt.flush();
        publishFromWorker(entry, "markFailed", null);
        rt.__tickFrame(1L);
        rt.flush();
        Assert.assertEquals("<", icon.getText());
        Assert.assertNull(icon.getImageSource());
        Assert.assertEquals(before, effectCount());
    }

    private SceneNode icon() {
        return new SceneNode().setPreferredWidth(16).setPreferredHeight(16);
    }

    private Entry pendingEntry(String name, BufferedImage image) throws Exception {
        String url = ChatToolbarIcons.urlFor(name);
        cache.putForTesting(url, image);
        Entry entry = cache.request(url, null);
        // 缓存没有 pending 测试入口：只在测试中将已预热真实条目置为 LOADING。
        // attach 的 request 会共享该条目，绝不会提交下载任务；生产接口无需扩大。
        Field imageField = Entry.class.getDeclaredField("image");
        imageField.setAccessible(true);
        imageField.set(entry, null);
        Field statusField = Entry.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(entry, Status.LOADING);
        return entry;
    }

    private void publishFromWorker(Entry entry, String methodName, BufferedImage image) throws Exception {
        Method method = image == null ? Entry.class.getDeclaredMethod(methodName)
                : Entry.class.getDeclaredMethod(methodName, BufferedImage.class);
        method.setAccessible(true);
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try {
                if (image == null) method.invoke(entry);
                else method.invoke(entry, image);
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "toolbar-icon-test-publisher");
        worker.start();
        worker.join(5000L);
        Assert.assertFalse("测试发布线程必须已结束", worker.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private int effectCount() throws Exception {
        Method method = ReactiveScheduler.class.getDeclaredMethod("registeredEffectCount");
        method.setAccessible(true);
        return ((Integer) method.invoke(ReactiveScheduler.get())).intValue();
    }
}
