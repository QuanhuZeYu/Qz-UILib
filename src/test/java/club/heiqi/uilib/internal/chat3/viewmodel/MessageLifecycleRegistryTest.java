package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;

import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;

/**
 * MessageLifecycleRegistry 契约测试:ensure 复用同一实例 / get 缺失返回 null /
 * purge 只留快照内 / clear 清空。
 */
public class MessageLifecycleRegistryTest {

    private static final long DEFAULT_BUDGET = 5000L;

    private static ChatLineRecord record(long sequenceId) {
        return new ChatLineRecord(new ChatComponentText("m" + sequenceId), 1, 1000L, sequenceId);
    }

    @Test
    public void shouldReuseExistingInstanceOnEnsure() {
        MessageLifecycleRegistry registry = new MessageLifecycleRegistry();
        MessageLifecycle first = registry.ensure(7L, DEFAULT_BUDGET);
        MessageLifecycle second = registry.ensure(7L, 12_345L);
        Assert.assertSame("重复 ensure 复用同一实例", first, second);
        Assert.assertEquals("预算以首次创建为准", DEFAULT_BUDGET, first.getBudgetMillis());
        Assert.assertEquals(1, registry.size());
    }

    @Test
    public void shouldReturnNullForMissingKeys() {
        MessageLifecycleRegistry registry = new MessageLifecycleRegistry();
        Assert.assertNull(registry.get(1L));
        registry.ensure(2L, DEFAULT_BUDGET);
        Assert.assertNull("未登记的序列号返回 null", registry.get(1L));
        Assert.assertNotNull(registry.get(2L));
    }

    @Test
    public void shouldPurgeKeepingOnlySnapshotSequences() {
        MessageLifecycleRegistry registry = new MessageLifecycleRegistry();
        registry.ensure(1L, DEFAULT_BUDGET);
        registry.ensure(2L, DEFAULT_BUDGET);
        registry.ensure(3L, DEFAULT_BUDGET);
        List<ChatLineRecord> snapshot = Arrays.asList(record(2L), record(3L));
        registry.purge(snapshot);
        Assert.assertEquals(2, registry.size());
        Assert.assertNull("快照外的序列号被移除", registry.get(1L));
        Assert.assertNotNull(registry.get(2L));
        Assert.assertNotNull(registry.get(3L));
    }

    @Test
    public void shouldClearAllEntries() {
        MessageLifecycleRegistry registry = new MessageLifecycleRegistry();
        registry.ensure(1L, DEFAULT_BUDGET);
        registry.ensure(2L, DEFAULT_BUDGET);
        registry.clear();
        Assert.assertEquals(0, registry.size());
        Assert.assertNull(registry.get(1L));
        Assert.assertNull(registry.get(2L));
    }

    @Test
    public void shouldClearAllOnEmptySnapshotPurge() {
        MessageLifecycleRegistry registry = new MessageLifecycleRegistry();
        registry.ensure(1L, DEFAULT_BUDGET);
        registry.purge(Collections.<ChatLineRecord>emptyList());
        Assert.assertEquals(0, registry.size());
    }
}
