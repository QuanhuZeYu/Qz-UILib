package club.heiqi.uilib.mixin.late.ae2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import club.heiqi.uilib.font.config.FontConfig;

/**
 * 直接调用生产 handler；Operation fake 只记录宿主列表操作，不创建 GL 上下文或另建字体绘制路径。
 * 宿主夹具仅表示 AE renderDynamic 的 dirty 分支顺序：写 false、begin、屏幕提交、end，否则命中列表。
 * 这些是 handler 行为回归，不能替代 Mixin 在实际 AE 字节码上的应用验证。
 */
public class MixinAbstractPartMonitorTest {

    private boolean previousReplaceOrigin;

    @Before
    public void saveConfig() {
        previousReplaceOrigin = FontConfig.replaceOrigin;
    }

    @After
    public void restoreConfig() {
        FontConfig.replaceOrigin = previousReplaceOrigin;
    }

    @Test
    public void disabledReplacementPreservesCleanCacheHits() throws Exception {
        FontConfig.replaceOrigin = false;
        Monitor monitor = new Monitor(false);

        monitor.render();
        monitor.render();

        assertEquals(Arrays.asList("cache", "cache"), monitor.events);
        assertFalse(monitor.dirty());
        assertEquals(0, monitor.fieldWrites);
    }

    @Test
    public void disabledReplacementCompilesDirtyListOnceThenHitsCache() throws Exception {
        FontConfig.replaceOrigin = false;
        Monitor monitor = new Monitor(true);

        monitor.render();
        monitor.render();

        assertEquals(Arrays.asList("begin", "screen", "end", "cache"), monitor.events);
        assertFalse(monitor.dirty());
        assertEquals(1, monitor.fieldWrites);
    }

    @Test
    public void enabledReplacementRendersEveryFrameWithoutCompilingOrReplayingLists() throws Exception {
        FontConfig.replaceOrigin = true;
        Monitor monitor = new Monitor(false);

        monitor.render();
        assertTrue("HEAD 必须使已缓存的监控器进入屏幕提交分支", monitor.dirty());
        monitor.render();
        assertTrue("宿主写 false 后仍须保持 dirty", monitor.dirty());
        monitor.render();

        assertEquals(Arrays.asList("screen", "screen", "screen"), monitor.events);
        assertEquals(3, monitor.fieldWrites);
        assertTrue(monitor.dirty());
    }

    @Test
    public void disablingAfterDirectRenderingRecompilesBeforeCacheCanBeUsed() throws Exception {
        Monitor monitor = new Monitor(true);
        FontConfig.replaceOrigin = false;
        monitor.render();
        monitor.render();
        assertEquals(Arrays.asList("begin", "screen", "end", "cache"), monitor.events);
        monitor.events.clear();

        FontConfig.replaceOrigin = true;
        monitor.render();
        monitor.render();
        FontConfig.replaceOrigin = false;
        monitor.render();
        assertFalse(monitor.dirty());
        monitor.render();

        assertEquals(Arrays.asList("screen", "screen", "begin", "screen", "end", "cache"), monitor.events);
    }

    @Test
    public void configChangesDuringCallCannotSplitBeginAndEndOrChangeDirtyDecision() throws Exception {
        for (boolean enabledAtHead : new boolean[] { false, true }) {
            for (FlipAt flipAt : FlipAt.values()) {
                Monitor monitor = new Monitor(true);
                FontConfig.replaceOrigin = enabledAtHead;
                monitor.render(flipAt);

                String context = "enabledAtHead=" + enabledAtHead + ", flipAt=" + flipAt;
                assertEquals(context, enabledAtHead, monitor.dirty());
                assertEquals(context, enabledAtHead ? Collections.singletonList("screen")
                        : Arrays.asList("begin", "screen", "end"), monitor.events);
                assertFalse(context, monitor.listOpen);

                // 下一次 HEAD 才采用新值；开启到关闭必须先编译，关闭到开启必须直接提交。
                monitor.events.clear();
                monitor.render();
                assertEquals(context, enabledAtHead ? Arrays.asList("begin", "screen", "end")
                        : Collections.singletonList("screen"), monitor.events);
                assertEquals(context, !enabledAtHead, monitor.dirty());
            }
        }
    }

    @Test
    public void fieldWrapperPreservesTrueWritesAndForwardsReceiverExactlyOnce() throws Exception {
        for (boolean enabled : new boolean[] { false, true }) {
            for (boolean requestedDirty : new boolean[] { false, true }) {
                FontConfig.replaceOrigin = enabled;
                Monitor monitor = new Monitor(false);
                monitor.beginRender();
                monitor.writeDirty(requestedDirty);

                assertEquals(enabled || requestedDirty, monitor.dirty());
                assertEquals(1, monitor.fieldWrites);
            }
        }
    }

    private enum FlipAt {
        AFTER_HEAD,
        AFTER_FIELD_WRITE,
        AFTER_BEGIN,
        AFTER_SCREEN
    }

    private static final class Monitor {

        private static final int LIST_ID = 73;
        private static final int COMPILE_AND_EXECUTE = 0x1301;

        private final MixinAbstractPartMonitor handler = new MixinAbstractPartMonitor() {};
        private final Field dirtyField = MixinAbstractPartMonitor.class.getDeclaredField("updateList");
        private final Method head = method("qzuilib$beginMonitorRender", CallbackInfo.class);
        private final Method write = method("qzuilib$keepDisplayListDirty", Object.class, boolean.class, Operation.class);
        private final Method begin = method("qzuilib$beginDisplayList", int.class, int.class, Operation.class);
        private final Method end = method("qzuilib$endDisplayList", Operation.class);
        private final List<String> events = new ArrayList<>();
        private int fieldWrites;
        private boolean listOpen;

        private Monitor(boolean dirty) throws Exception {
            dirtyField.setAccessible(true);
            dirtyField.setBoolean(handler, dirty);
        }

        private boolean dirty() throws Exception {
            return dirtyField.getBoolean(handler);
        }

        private void beginRender() throws Exception {
            CallbackInfo callback = new CallbackInfo("renderDynamic", false);
            head.invoke(handler, callback);
            assertFalse("HEAD 不能取消宿主渲染", callback.isCancelled());
        }

        private void writeDirty(boolean value) throws Exception {
            Operation<Void> original = args -> {
                assertEquals("PUTFIELD 接收 receiver 和 boolean", 2, args.length);
                assertSame("字段包装必须转交原 receiver", handler, args[0]);
                assertTrue(args[1] instanceof Boolean);
                fieldWrites++;
                try {
                    dirtyField.setBoolean(args[0], (Boolean) args[1]);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
                return null;
            };
            write.invoke(handler, handler, value, original);
        }

        private void render() throws Exception {
            render(null);
        }

        private void render(FlipAt flipAt) throws Exception {
            beginRender();
            flip(flipAt, FlipAt.AFTER_HEAD);
            if (dirty()) {
                writeDirty(false);
                flip(flipAt, FlipAt.AFTER_FIELD_WRITE);
                Operation<Void> originalBegin = args -> {
                    assertArrayEquals(new Object[] { LIST_ID, COMPILE_AND_EXECUTE }, args);
                    assertFalse("不能嵌套 glNewList", listOpen);
                    listOpen = true;
                    events.add("begin");
                    return null;
                };
                begin.invoke(handler, LIST_ID, COMPILE_AND_EXECUTE, originalBegin);
                flip(flipAt, FlipAt.AFTER_BEGIN);
                events.add("screen");
                flip(flipAt, FlipAt.AFTER_SCREEN);
                Operation<Void> originalEnd = args -> {
                    assertEquals("glEndList 不接收参数", 0, args.length);
                    assertTrue("glEndList 必须对应此前的 glNewList", listOpen);
                    listOpen = false;
                    events.add("end");
                    return null;
                };
                end.invoke(handler, originalEnd);
            } else {
                events.add("cache");
            }
            assertFalse("离开 renderDynamic 后不得留下未结束的列表", listOpen);
        }

        private static void flip(FlipAt selected, FlipAt current) {
            if (selected == current) {
                FontConfig.replaceOrigin = !FontConfig.replaceOrigin;
            }
        }

        private static Method method(String name, Class<?>... parameters) throws Exception {
            Method method = MixinAbstractPartMonitor.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        }
    }
}
