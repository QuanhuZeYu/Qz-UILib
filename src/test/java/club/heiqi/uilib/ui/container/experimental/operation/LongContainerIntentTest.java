package club.heiqi.uilib.ui.container.experimental.operation;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import club.heiqi.uilib.ui.container.experimental.model.EntryKey;
import org.junit.Assert;
import org.junit.Test;

public class LongContainerIntentTest {
    private final EntryKey key = new EntryKey("n", "v");
    @Test public void exposesExactlySevenFactoriesAndDepositHasNoKey() {
        LongContainerIntent[] intents = {LongContainerIntent.takeStack(key), LongContainerIntent.takeHalfStack(key), LongContainerIntent.depositAll(), LongContainerIntent.depositOne(), LongContainerIntent.quickExtract(key), LongContainerIntent.dropOne(key), LongContainerIntent.dropStack(key)};
        Assert.assertEquals(7, intents.length);
        for (LongContainerIntent intent : intents) {
            if (intent.kind() == LongContainerIntent.Kind.DEPOSIT_ALL || intent.kind() == LongContainerIntent.Kind.DEPOSIT_ONE) Assert.assertNull(intent.key());
            else Assert.assertNotNull(intent.key());
        }
        try { LongContainerIntent.dropOne(null); Assert.fail(); } catch (NullPointerException expected) { }
    }
    @Test public void hasValueSemantics() { Assert.assertEquals(LongContainerIntent.dropOne(key), LongContainerIntent.dropOne(new EntryKey("n", "v"))); Assert.assertNotEquals(LongContainerIntent.dropOne(key), LongContainerIntent.dropStack(key)); }
    @Test public void carriesOnlyKindAndOptionalKey() {
        Set<String> names = new HashSet<String>();
        for (Field field : LongContainerIntent.class.getDeclaredFields()) names.add(field.getName());
        Assert.assertEquals(new HashSet<String>(Arrays.asList("kind", "key")), names);
    }
}
