package club.heiqi.uilib.ui.render;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.ui.base.cascade.UiBorderRadiusResolver;

/**
 * issue #63：宿主 scissor 基线与 clip 边界 deferred text flush 的回归测试。
 *
 * <p>纯 JVM 路径不调用真实 GL；坐标转换与求交走静态纯函数；GL 语义分叉通过
 * {@link ClipStack#setGlOpsForTest} 注入记录型 {@link ClipGlOps} 验证。</p>
 */
public class ClipStackHostBaselineTest {

    private static final int SCREEN_W = 800;
    private static final int SCREEN_H = 600;

    @After
    public void resetGlOps() {
        ClipStack.setGlOpsForTest(null);
    }

    /**
     * GL scissor box（左下原点）应正确转为 UI 矩形（左上原点）。
     */
    @Test
    public void shouldConvertGlScissorBoxToUiRectWithCorrectY() {
        // GL: x=100, y=50（自底向上）, w=200, h=100；屏高 600
        // UI top = 600 - (50+100) = 450；UI bottom = 600 - 50 = 550
        int[] ui = ClipStack.glScissorBoxToUiRect(100, 50, 200, 100, SCREEN_H);
        Assert.assertArrayEquals(new int[] { 100, 450, 300, 550 }, ui);
    }

    /**
     * 外部 scissor 启用时，首层 clip 应与宿主 UI 矩形求交（依赖已安装基线，非 push 时捕获）。
     */
    @Test
    public void shouldIntersectFirstClipWithHostScissorWhenEnabled() {
        ClipStack.HostClipBaseline host = new ClipStack.HostClipBaseline(
                true, 50, 100, 350, 400, false);

        ClipStack stack = new ClipStack();
        stack.glOperationsEnabled = false;
        stack.installHostBaseline(host);

        stack.push(0, 0, 800, 600, SCREEN_W, SCREEN_H,
                UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0));

        Assert.assertArrayEquals(new int[] { 50, 100, 400, 500 }, stack.peekClipRectForTest());
    }

    /**
     * 外部 scissor 禁用时，首层 clip 只受屏幕边界约束。
     */
    @Test
    public void shouldNotIntersectHostWhenScissorDisabled() {
        ClipStack stack = new ClipStack();
        stack.glOperationsEnabled = false;
        stack.installHostBaseline(ClipStack.HostClipBaseline.disabled());

        stack.push(10, 20, 300, 400, SCREEN_W, SCREEN_H,
                UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0));

        Assert.assertArrayEquals(new int[] { 10, 20, 300, 400 }, stack.peekClipRectForTest());
    }

    /**
     * 纯函数：有父层时只与父层求交，忽略宿主矩形。
     */
    @Test
    public void shouldIntersectParentNotHostWhenNested() {
        int[] parent = new int[] { 100, 100, 500, 500 };
        int[] host = new int[] { 0, 0, 200, 200 };
        int[] resolved = ClipStack.resolvePushedClipRect(0, 0, 800, 600, SCREEN_W, SCREEN_H,
                parent, host);
        Assert.assertArrayEquals(new int[] { 100, 100, 500, 500 }, resolved);
    }

    /**
     * 纯函数：首层 + 宿主启用时与宿主求交。
     */
    @Test
    public void shouldResolveFirstClipAgainstHostBaselineUiRect() {
        int[] host = new int[] { 80, 90, 280, 290 };
        int[] resolved = ClipStack.resolvePushedClipRect(0, 0, 400, 400, SCREEN_W, SCREEN_H,
                null, host);
        Assert.assertArrayEquals(new int[] { 80, 90, 280, 290 }, resolved);
    }

    /**
     * 纯函数：首层 + 无宿主时不额外收紧。
     */
    @Test
    public void shouldResolveFirstClipWithoutHostUnchanged() {
        int[] resolved = ClipStack.resolvePushedClipRect(12, 34, 56, 78, SCREEN_W, SCREEN_H,
                null, null);
        Assert.assertArrayEquals(new int[] { 12, 34, 56, 78 }, resolved);
    }

    /**
     * P0-1：空栈 applyCurrent 幂等恢复同一基线，不消费、不因二次 apply 丢失。
     */
    @Test
    public void shouldKeepHostBaselineAcrossMultipleEmptyApplyCurrent() {
        ClipStack.HostClipBaseline host = new ClipStack.HostClipBaseline(
                true, 10, 20, 100, 80, false);
        ClipStack stack = new ClipStack();
        stack.glOperationsEnabled = false;
        stack.installHostBaseline(host);

        stack.push(0, 0, 50, 50, SCREEN_W, SCREEN_H,
                UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0));
        stack.pop();

        Assert.assertTrue(stack.isEmpty());
        stack.applyCurrent(SCREEN_H);
        Assert.assertSame(host, stack.peekHostBaselineForTest());
        stack.applyCurrent(SCREEN_H);
        Assert.assertSame(host, stack.peekHostBaselineForTest());
        Assert.assertTrue(stack.peekHostBaselineForTest().isScissorEnabled());
        Assert.assertEquals(10, stack.peekHostBaselineForTest().getGlX());
    }

    /**
     * P0-2：首层求交使用构造/安装时的基线，不依赖 push 时再捕获。
     */
    @Test
    public void shouldUseInstalledBaselineAtPushWithoutRecapture() {
        FlushCountingContext context = new FlushCountingContext();
        context.clipStack.glOperationsEnabled = false;

        ClipStack.HostClipBaseline host = new ClipStack.HostClipBaseline(
                true, 50, 100, 350, 400, false);
        context.clipStack.installHostBaseline(host);

        Assert.assertSame(host, context.clipStack.peekHostBaselineForTest());
        context.pushClip(0, 0, 800, 600);
        Assert.assertSame(host, context.clipStack.peekHostBaselineForTest());
        Assert.assertArrayEquals(new int[] { 50, 100, 400, 500 },
                context.clipStack.peekClipRectForTest());
    }

    /**
     * 语义分叉：静态 applySnapshot(null)/clearState 走 clear；实例空栈 applyCurrent 走 restore baseline。
     */
    @Test
    public void shouldDistinguishStaticClearFromInstanceRestoreBaseline() {
        RecordingClipGlOps recorder = new RecordingClipGlOps();
        ClipStack.setGlOpsForTest(recorder);

        ClipStack.HostClipBaseline host = new ClipStack.HostClipBaseline(
                true, 40, 50, 120, 90, true,
                GL11.GL_EQUAL, 2, 0x0F, 0x7F,
                GL11.GL_KEEP, GL11.GL_INCR, GL11.GL_REPLACE);

        // 1) 静态 null snapshot → clearState
        recorder.calls.clear();
        ClipStack.applySnapshot(null, SCREEN_H);
        Assert.assertTrue("静态 null 应 disable scissor",
                recorder.calls.contains("disable:" + GL11.GL_SCISSOR_TEST));
        Assert.assertTrue("静态 null 应 disable stencil",
                recorder.calls.contains("disable:" + GL11.GL_STENCIL_TEST));
        Assert.assertTrue("静态 clear 应 stencilMask(0xFF)",
                recorder.calls.contains("stencilMask:255"));
        Assert.assertFalse("静态 clear 不得 scissor 写宿主 box",
                recorder.hasScissor(40, 50, 120, 90));

        // 2) 实例空栈 applyCurrent → restore baseline（含完整 stencil）
        ClipStack stack = new ClipStack();
        stack.glOperationsEnabled = true;
        stack.installHostBaseline(host);
        recorder.calls.clear();
        stack.applyCurrent(SCREEN_H);

        Assert.assertTrue("实例空栈应 enable 宿主 scissor",
                recorder.calls.contains("enable:" + GL11.GL_SCISSOR_TEST));
        Assert.assertTrue("实例空栈应写回宿主 scissor box",
                recorder.hasScissor(40, 50, 120, 90));
        Assert.assertTrue("实例空栈应 enable 宿主 stencil",
                recorder.calls.contains("enable:" + GL11.GL_STENCIL_TEST));
        Assert.assertTrue("应恢复 stencilFunc",
                recorder.calls.contains("stencilFunc:" + GL11.GL_EQUAL + ",2," + 0x0F));
        Assert.assertTrue("应恢复 stencilOp",
                recorder.calls.contains("stencilOp:" + GL11.GL_KEEP + "," + GL11.GL_INCR + ","
                        + GL11.GL_REPLACE));
        Assert.assertTrue("应恢复捕获的 write mask 而非固定 0xFF",
                recorder.calls.contains("stencilMask:" + 0x7F));
        Assert.assertFalse("restore 不得走 clear 式无条件 disable scissor 作为唯一动作",
                recorder.calls.equals(java.util.Arrays.asList(
                        "disable:" + GL11.GL_SCISSOR_TEST,
                        "disable:" + GL11.GL_STENCIL_TEST,
                        "stencilMask:255",
                        "colorMask:true,true,true,true",
                        "depthMask:true")));
    }

    /**
     * 捕获应读出完整 stencil 状态并写回。
     */
    @Test
    public void shouldCaptureAndRestoreFullStencilState() {
        RecordingClipGlOps recorder = new RecordingClipGlOps();
        recorder.scissorEnabled = false;
        recorder.stencilEnabled = true;
        recorder.stencilFunc = GL11.GL_NOTEQUAL;
        recorder.stencilRef = 3;
        recorder.stencilValueMask = 0xAB;
        recorder.stencilWriteMask = 0xCD;
        recorder.stencilFail = GL11.GL_ZERO;
        recorder.stencilZFail = GL11.GL_REPLACE;
        recorder.stencilZPass = GL11.GL_INCR;
        ClipStack.setGlOpsForTest(recorder);

        ClipStack.HostClipBaseline captured = ClipStack.captureCurrentHostBaseline(recorder);
        Assert.assertTrue(captured.isStencilEnabled());
        Assert.assertEquals(GL11.GL_NOTEQUAL, captured.getStencilFunc());
        Assert.assertEquals(3, captured.getStencilRef());
        Assert.assertEquals(0xAB, captured.getStencilValueMask());
        Assert.assertEquals(0xCD, captured.getStencilWriteMask());

        recorder.calls.clear();
        captured.applyToGl(recorder);
        Assert.assertTrue(recorder.calls.contains("enable:" + GL11.GL_STENCIL_TEST));
        Assert.assertTrue(recorder.calls.contains(
                "stencilFunc:" + GL11.GL_NOTEQUAL + ",3," + 0xAB));
        Assert.assertTrue(recorder.calls.contains(
                "stencilOp:" + GL11.GL_ZERO + "," + GL11.GL_REPLACE + "," + GL11.GL_INCR));
        Assert.assertTrue(recorder.calls.contains("stencilMask:" + 0xCD));
    }

    /**
     * pushClip / popClip 改变 clip 前应 flush deferred text batch。
     */
    @Test
    public void shouldFlushDeferredTextBatchBeforeClipChanges() {
        FlushCountingContext context = new FlushCountingContext();
        context.clipStack.glOperationsEnabled = false;

        Assert.assertEquals(0, context.flushCount);
        context.pushClip(0, 0, 100, 100);
        Assert.assertEquals("pushClip 前应 flush", 1, context.flushCount);
        context.popClip();
        Assert.assertEquals("popClip 前应再 flush", 2, context.flushCount);
    }

    /**
     * 计数 flush 调用的渲染上下文替身。
     */
    private static final class FlushCountingContext extends UiRenderContext {

        private int flushCount;

        private FlushCountingContext() {
            super(SCREEN_W, SCREEN_H, 0, 0, 0.0F);
        }

        @Override
        public void flushDeferredTextBatch() {
            flushCount++;
        }
    }

    /**
     * 记录 GL 调用的测试替身，并可回放捕获用查询值。
     */
    private static final class RecordingClipGlOps implements ClipGlOps {

        private final List<String> calls = new ArrayList<String>();
        private boolean scissorEnabled;
        private boolean stencilEnabled;
        private int stencilFunc = GL11.GL_ALWAYS;
        private int stencilRef;
        private int stencilValueMask = 0xFF;
        private int stencilWriteMask = 0xFF;
        private int stencilFail = GL11.GL_KEEP;
        private int stencilZFail = GL11.GL_KEEP;
        private int stencilZPass = GL11.GL_KEEP;
        private final int[] scissorBox = new int[] { 0, 0, 0, 0 };

        private boolean hasScissor(int x, int y, int w, int h) {
            return calls.contains("scissor:" + x + "," + y + "," + w + "," + h);
        }

        @Override
        public boolean isEnabled(int cap) {
            calls.add("isEnabled:" + cap);
            if (cap == GL11.GL_SCISSOR_TEST) {
                return scissorEnabled;
            }
            if (cap == GL11.GL_STENCIL_TEST) {
                return stencilEnabled;
            }
            return false;
        }

        @Override
        public void enable(int cap) {
            calls.add("enable:" + cap);
        }

        @Override
        public void disable(int cap) {
            calls.add("disable:" + cap);
        }

        @Override
        public void getIntegers(int pname, IntBuffer params) {
            calls.add("getIntegers:" + pname);
            if (pname == GL11.GL_SCISSOR_BOX) {
                params.put(0, scissorBox[0]);
                params.put(1, scissorBox[1]);
                params.put(2, scissorBox[2]);
                params.put(3, scissorBox[3]);
            }
        }

        @Override
        public int getInteger(int pname) {
            calls.add("getInteger:" + pname);
            if (pname == GL11.GL_STENCIL_FUNC) {
                return stencilFunc;
            }
            if (pname == GL11.GL_STENCIL_REF) {
                return stencilRef;
            }
            if (pname == GL11.GL_STENCIL_VALUE_MASK) {
                return stencilValueMask;
            }
            if (pname == GL11.GL_STENCIL_WRITEMASK) {
                return stencilWriteMask;
            }
            if (pname == GL11.GL_STENCIL_FAIL) {
                return stencilFail;
            }
            if (pname == GL11.GL_STENCIL_PASS_DEPTH_FAIL) {
                return stencilZFail;
            }
            if (pname == GL11.GL_STENCIL_PASS_DEPTH_PASS) {
                return stencilZPass;
            }
            return 0;
        }

        @Override
        public void scissor(int x, int y, int width, int height) {
            calls.add("scissor:" + x + "," + y + "," + width + "," + height);
        }

        @Override
        public void stencilFunc(int func, int ref, int mask) {
            calls.add("stencilFunc:" + func + "," + ref + "," + mask);
        }

        @Override
        public void stencilOp(int fail, int zfail, int zpass) {
            calls.add("stencilOp:" + fail + "," + zfail + "," + zpass);
        }

        @Override
        public void stencilMask(int mask) {
            calls.add("stencilMask:" + mask);
        }

        @Override
        public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
            calls.add("colorMask:" + red + "," + green + "," + blue + "," + alpha);
        }

        @Override
        public void depthMask(boolean flag) {
            calls.add("depthMask:" + flag);
        }
    }
}
