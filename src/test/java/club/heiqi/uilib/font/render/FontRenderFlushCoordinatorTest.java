package club.heiqi.uilib.font.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link FontRenderFlushCoordinator} 回归测试。
 */
public class FontRenderFlushCoordinatorTest {

    /**
     * 验证字形批次与装饰线批次会被包进同一层状态保护边界。
     */
    @Test
    public void shouldFlushGlyphsAndDecorationsInsideSingleGuardScope() {
        FontRenderFlushCoordinator coordinator = new FontRenderFlushCoordinator();
        List<String> events = new ArrayList<String>();
        RecordingFontRenderStateExecutor stateGuard = new RecordingFontRenderStateExecutor(events);

        coordinator.flush(stateGuard, new Runnable() {
            @Override
            public void run() {
                events.add("glyph");
            }
        }, new Runnable() {
            @Override
            public void run() {
                events.add("decoration");
            }
        });

        Assert.assertEquals(Arrays.asList("guard-enter", "glyph", "decoration", "guard-exit"), events);
    }

    /**
     * 验证空状态保护器会被立即拒绝。
     */
    @Test(expected = NullPointerException.class)
    public void shouldRejectNullStateGuard() {
        new FontRenderFlushCoordinator().flush(null, new Runnable() {
            @Override
            public void run() {}
        }, new Runnable() {
            @Override
            public void run() {}
        });
    }

    /**
     * 验证测试可在无 OpenGL 上下文下覆盖调用边界。
     */
    private static final class RecordingFontRenderStateExecutor implements FontRenderStateExecutor {

        private final List<String> events;

        private RecordingFontRenderStateExecutor(List<String> events) {
            this.events = events;
        }

        @Override
        public void run(Runnable task) {
            events.add("guard-enter");
            try {
                task.run();
            } finally {
                events.add("guard-exit");
            }
        }
    }
}
