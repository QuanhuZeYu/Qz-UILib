package club.heiqi.uilib.ui.container.experimental.storage;

import java.util.Arrays;
import java.util.Collections;
import club.heiqi.uilib.ui.container.experimental.model.EntryKey;
import club.heiqi.uilib.ui.container.experimental.model.ItemDescriptor;
import club.heiqi.uilib.ui.container.experimental.model.LongContainerSnapshot;
import club.heiqi.uilib.ui.container.experimental.model.LongEntrySnapshot;
import org.junit.Assert;
import org.junit.Test;

public class LongContainerStorageContractTest {
    private static final ItemDescriptor ITEM = new ItemDescriptor("t", "c", new byte[] {1});
    private static final EntryKey KEY = new EntryKey("n", "k");
    private static final LongContainerSnapshot EMPTY = new LongContainerSnapshot(Collections.<LongEntrySnapshot>emptyList());

    @Test public void resultEnforcesStatusAndMovedInvariants() {
        new TransferResult(TransferStatus.COMPLETED, 2, EMPTY);
        new TransferResult(TransferStatus.PARTIAL, 1, EMPTY);
        new TransferResult(TransferStatus.NO_CHANGE, 0, EMPTY);
        new TransferResult(TransferStatus.NOT_FOUND, 0, EMPTY);
        try { new TransferResult(TransferStatus.COMPLETED, 0, EMPTY); Assert.fail(); } catch (IllegalArgumentException expected) { }
        try { new TransferResult(TransferStatus.NO_CHANGE, 1, EMPTY); Assert.fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void fakeBackendCoversExactUpToAndMissingContracts() {
        FakeStorage fake = new FakeStorage();
        TransferResult full = fake.insert(ITEM, 3, TransferMode.EXACT);
        Assert.assertEquals(TransferStatus.COMPLETED, full.status()); Assert.assertEquals(3, full.moved());
        Assert.assertEquals(1, full.snapshot().entries().size()); Assert.assertEquals(3, full.snapshot().entries().get(0).amount());
        TransferResult insufficient = fake.insert(ITEM, 4, TransferMode.EXACT);
        Assert.assertEquals(TransferStatus.NO_CHANGE, insufficient.status()); Assert.assertEquals(0, insufficient.moved());
        Assert.assertEquals(1, insufficient.snapshot().entries().size()); Assert.assertEquals(3, insufficient.snapshot().entries().get(0).amount());
        TransferResult partial = fake.extract(KEY, 5, TransferMode.UP_TO);
        Assert.assertEquals(TransferStatus.PARTIAL, partial.status()); Assert.assertEquals(3, partial.moved());
        Assert.assertTrue(partial.snapshot().entries().isEmpty());
        TransferResult upToFull = fake.insert(ITEM, 1, TransferMode.UP_TO);
        Assert.assertEquals(TransferStatus.COMPLETED, upToFull.status()); Assert.assertEquals(1, upToFull.moved());
        Assert.assertEquals(1, upToFull.snapshot().entries().size()); Assert.assertEquals(1, upToFull.snapshot().entries().get(0).amount());
        TransferResult upToPartial = fake.insert(ITEM, 5, TransferMode.UP_TO);
        Assert.assertEquals(TransferStatus.PARTIAL, upToPartial.status()); Assert.assertEquals(2, upToPartial.moved());
        Assert.assertEquals(1, upToPartial.snapshot().entries().size()); Assert.assertEquals(3, upToPartial.snapshot().entries().get(0).amount());
        TransferResult upToZero = fake.insert(ITEM, 1, TransferMode.UP_TO);
        Assert.assertEquals(TransferStatus.NO_CHANGE, upToZero.status()); Assert.assertEquals(0, upToZero.moved());
        Assert.assertEquals(1, upToZero.snapshot().entries().size()); Assert.assertEquals(3, upToZero.snapshot().entries().get(0).amount());
        TransferResult exactInsufficient = fake.extract(KEY, 4, TransferMode.EXACT);
        Assert.assertEquals(TransferStatus.NO_CHANGE, exactInsufficient.status()); Assert.assertEquals(0, exactInsufficient.moved());
        Assert.assertEquals(1, exactInsufficient.snapshot().entries().size()); Assert.assertEquals(3, exactInsufficient.snapshot().entries().get(0).amount());
        TransferResult zero = fake.extract(new EntryKey("n", "absent"), 1, TransferMode.UP_TO);
        Assert.assertEquals(TransferStatus.NOT_FOUND, zero.status()); Assert.assertEquals(0, zero.moved());
        Assert.assertEquals(1, zero.snapshot().entries().size()); Assert.assertEquals(3, zero.snapshot().entries().get(0).amount());
    }
    @Test public void fakeBackendRejectsNonPositiveRequests() {
        FakeStorage fake = new FakeStorage();
        EntryKey absent = new EntryKey("n", "absent");
        try { fake.insert(ITEM, 0, TransferMode.EXACT); Assert.fail(); } catch (IllegalArgumentException expected) { }
        try { fake.insert(ITEM, -1, TransferMode.UP_TO); Assert.fail(); } catch (IllegalArgumentException expected) { }
        try { fake.extract(absent, 0, TransferMode.EXACT); Assert.fail(); } catch (IllegalArgumentException expected) { }
        try { fake.extract(absent, -1, TransferMode.UP_TO); Assert.fail(); } catch (IllegalArgumentException expected) { }
    }

    private static final class FakeStorage implements LongContainerStorage {
        private long amount;
        public LongContainerSnapshot snapshot() { return amount == 0 ? EMPTY : new LongContainerSnapshot(Arrays.asList(new LongEntrySnapshot(KEY, ITEM, amount))); }
        public TransferResult insert(ItemDescriptor item, long requested, TransferMode mode) {
            if (requested <= 0) throw new IllegalArgumentException();
            long moved = mode == TransferMode.EXACT && requested > 3 - amount ? 0 : Math.min(requested, 3 - amount);
            amount += moved;
            return new TransferResult(moved == 0 ? TransferStatus.NO_CHANGE : moved == requested ? TransferStatus.COMPLETED : TransferStatus.PARTIAL, moved, snapshot());
        }
        public TransferResult extract(EntryKey key, long requested, TransferMode mode) {
            if (requested <= 0) throw new IllegalArgumentException();
            if (!KEY.equals(key)) return new TransferResult(TransferStatus.NOT_FOUND, 0, snapshot());
            long moved = mode == TransferMode.EXACT && requested > amount ? 0 : Math.min(requested, amount);
            amount -= moved;
            return new TransferResult(moved == 0 ? TransferStatus.NO_CHANGE : moved == requested ? TransferStatus.COMPLETED : TransferStatus.PARTIAL, moved, snapshot());
        }
    }
}
