package club.heiqi.uilib.ui.scene.host.lwjgl;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** lwjgl3ify 文本桥注册事务与反射探测的故障注入测试。 */
public class SceneLwjgl3ifyTextBridgeTest {

    @Test
    public void availabilityUsesNoInitializationAndAnchorLoader() {
        FakeAdapter adapter = new FakeAdapter();
        Assert.assertTrue(SceneLwjgl3ifyTextBridge.isAvailable(adapter));
        Assert.assertEquals(Boolean.FALSE, adapter.initializeValues.get(0));
        Assert.assertSame(SceneLwjgl3ifyTextBridge.class.getClassLoader(), adapter.loaders.get(0));

        adapter.loadFailure = new ClassNotFoundException("missing");
        Assert.assertFalse(SceneLwjgl3ifyTextBridge.isAvailable(adapter));
        adapter.loadFailure = new SecurityException("denied");
        Assert.assertFalse(SceneLwjgl3ifyTextBridge.isAvailable(adapter));
        adapter.loadFailure = new NoClassDefFoundError("broken link");
        Assert.assertFalse(SceneLwjgl3ifyTextBridge.isAvailable(adapter));
    }

    @Test
    public void successfulLifecycleIsOrderedAndIdempotent() {
        FakeAdapter adapter = new FakeAdapter();
        SceneLwjgl3ifyTextBridge bridge = bridge(adapter);

        Assert.assertTrue(bridge.register());
        Assert.assertTrue(bridge.register());
        Assert.assertEquals(Arrays.asList("prepare", "addWeakKeyboardListener", "beginTextInput"), adapter.events);
        bridge.unregister();
        bridge.unregister();
        Assert.assertEquals(Arrays.asList("prepare", "addWeakKeyboardListener", "beginTextInput",
                "endTextInput", "removeWeakKeyboardListener"), adapter.events);
    }

    @Test
    public void incompleteRequiredPairsNeverAdd() {
        for (String missing : Arrays.asList("removeWeakKeyboardListener", "removeKeyboardListener",
                "beginTextInput", "endTextInput")) {
            FakeAdapter adapter = new FakeAdapter();
            if (missing.startsWith("remove")) {
                adapter.missing.add("removeWeakKeyboardListener");
                adapter.missing.add("removeKeyboardListener");
            } else {
                adapter.missing.add(missing);
            }
            Assert.assertFalse("missing=" + missing, bridge(adapter).register());
            Assert.assertEquals("missing=" + missing, 0, adapter.count("addWeakKeyboardListener")
                    + adapter.count("addKeyboardListener"));
        }
    }

    @Test
    public void incompleteWeakPairFallsBackToCompleteStrongPair() {
        FakeAdapter adapter = new FakeAdapter();
        adapter.missing.add("removeWeakKeyboardListener");
        SceneLwjgl3ifyTextBridge bridge = bridge(adapter);
        Assert.assertTrue(bridge.register());
        Assert.assertEquals(0, adapter.count("addWeakKeyboardListener"));
        Assert.assertEquals(1, adapter.count("addKeyboardListener"));
        bridge.unregister();
        Assert.assertEquals(1, adapter.count("removeKeyboardListener"));
    }

    @Test
    public void addFailureAfterSideEffectStillRemovesWithoutEnding() {
        FakeAdapter adapter = new FakeAdapter();
        adapter.failAfter.put("addWeakKeyboardListener", Integer.valueOf(1));
        Assert.assertFalse(bridge(adapter).register());
        Assert.assertEquals(Arrays.asList("prepare", "addWeakKeyboardListener", "removeWeakKeyboardListener"),
                adapter.events);
    }

    @Test
    public void beginFailureAfterSideEffectEndsThenRemoves() {
        FakeAdapter adapter = new FakeAdapter();
        adapter.failAfter.put("beginTextInput", Integer.valueOf(1));
        Assert.assertFalse(bridge(adapter).register());
        Assert.assertEquals(Arrays.asList("prepare", "addWeakKeyboardListener", "beginTextInput",
                "endTextInput", "removeWeakKeyboardListener"), adapter.events);
    }

    @Test
    public void endFailureDoesNotBlockRemove() {
        FakeAdapter adapter = new FakeAdapter();
        SceneLwjgl3ifyTextBridge bridge = bridge(adapter);
        Assert.assertTrue(bridge.register());
        adapter.failAfter.put("endTextInput", Integer.valueOf(1));
        bridge.unregister();
        Assert.assertEquals(1, adapter.count("removeWeakKeyboardListener"));
    }

    @Test
    public void pendingRemoveRetriesAndBlocksNewAdd() {
        FakeAdapter adapter = new FakeAdapter();
        SceneLwjgl3ifyTextBridge bridge = bridge(adapter);
        Assert.assertTrue(bridge.register());
        adapter.failAfter.put("removeWeakKeyboardListener", Integer.valueOf(2));
        bridge.unregister();
        Assert.assertFalse(bridge.register());
        Assert.assertEquals(1, adapter.count("addWeakKeyboardListener"));
        bridge.unregister();
        Assert.assertEquals(3, adapter.count("removeWeakKeyboardListener"));
    }

    @Test
    public void completedRollbackAllowsRegistrationAgain() {
        FakeAdapter adapter = new FakeAdapter();
        adapter.failAfter.put("beginTextInput", Integer.valueOf(1));
        SceneLwjgl3ifyTextBridge bridge = bridge(adapter);
        Assert.assertFalse(bridge.register());
        Assert.assertTrue(bridge.register());
        Assert.assertEquals(2, adapter.count("addWeakKeyboardListener"));
    }

    private static SceneLwjgl3ifyTextBridge bridge(FakeAdapter adapter) {
        return new SceneLwjgl3ifyTextBridge(text -> { }, adapter);
    }

    /** 使用本测试类公开方法作为 Method token 的反射故障桩。 */
    private static final class FakeAdapter extends SceneLwjgl3ifyTextBridge.ReflectionAdapter {
        private final List<String> events = new ArrayList<String>();
        private final List<Boolean> initializeValues = new ArrayList<Boolean>();
        private final List<ClassLoader> loaders = new ArrayList<ClassLoader>();
        private final List<String> missing = new ArrayList<String>();
        private final Map<String, Integer> failAfter = new HashMap<String, Integer>();
        private Throwable loadFailure;

        @Override
        Class<?> loadClass(String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
            initializeValues.add(Boolean.valueOf(initialize));
            loaders.add(loader);
            if (loadFailure instanceof ClassNotFoundException) throw (ClassNotFoundException) loadFailure;
            if (loadFailure instanceof SecurityException) throw (SecurityException) loadFailure;
            if (loadFailure instanceof LinkageError) throw (LinkageError) loadFailure;
            return Fixture.class;
        }

        @Override
        Method getMethod(Class<?> owner, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
            if (missing.contains(name)) throw new NoSuchMethodException(name);
            return Fixture.class.getMethod(name);
        }

        @Override
        Object proxy(Class<?> listenerClass, InvocationHandler handler, ClassLoader fallbackLoader) {
            events.add("prepare");
            return new Object();
        }

        @Override
        Object invokeStatic(Method method, Object... arguments) throws ReflectiveOperationException {
            String name = method.getName();
            events.add(name);
            Integer remaining = failAfter.get(name);
            if (remaining != null && remaining.intValue() > 0) {
                failAfter.put(name, Integer.valueOf(remaining.intValue() - 1));
                throw new ReflectiveOperationException(name);
            }
            return null;
        }

        private int count(String event) {
            int result = 0;
            for (String value : events) if (event.equals(value)) result++;
            return result;
        }
    }

    /** 仅提供稳定的方法 token，不执行真实外部副作用。 */
    public static final class Fixture {
        public static void addWeakKeyboardListener() { }
        public static void removeWeakKeyboardListener() { }
        public static void addKeyboardListener() { }
        public static void removeKeyboardListener() { }
        public static void beginTextInput() { }
        public static void endTextInput() { }
    }
}
