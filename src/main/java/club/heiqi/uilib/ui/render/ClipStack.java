package club.heiqi.uilib.ui.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.ui.base.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.base.values.UiSurfaceStyle;

/**
 * UI 渲染裁剪栈。
 *
 * <p>负责维护矩形裁剪交集、圆角裁剪区域快照，并把快照回放到 OpenGL scissor/stencil 状态。</p>
 *
 * <p>宿主 scissor/stencil 基线由 {@link #installHostBaseline} 在上下文创建时安装，
 * 生命周期内保持不变。首层 {@link #push} 与该基线求交；栈空时 {@link #applyCurrent}
 * <strong>幂等</strong>恢复同一基线（不消费、不清空）。静态 {@link #applySnapshot}/
 * {@link #clearState} 仍表示「强制清空」，供 FBO 与 deferred 回放使用。</p>
 *
 * <p>宿主 stencil 恢复范围：enable、func、ref、value mask、write mask、fail/zfail/zpass op。
 * 不恢复 front/back 分离状态（本项目固定管线路径只用统一 stencil 状态）。</p>
 */
final class ClipStack {

    private static final ThreadLocal<IntBuffer> SCISSOR_BOX_BUFFER = new ThreadLocal<IntBuffer>() {
        @Override
        protected IntBuffer initialValue() {
            return ByteBuffer.allocateDirect(16 * Integer.BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asIntBuffer();
        }
    };

    /** 可替换的 GL 操作面；生产为 {@link RealClipGlOps}，测试可注入记录器。 */
    private static volatile ClipGlOps glOps = RealClipGlOps.INSTANCE;

    private final Deque<ClipState> entries = new ArrayDeque<ClipState>();

    /**
     * 进入 uilib 时安装的宿主 scissor/stencil 基线；上下文生命周期内保留。
     * 默认 {@link HostClipBaseline#disabled()}，避免未安装时误 clear。
     */
    private HostClipBaseline hostBaseline = HostClipBaseline.disabled();

    /**
     * 为 false 时跳过实例路径上的 GL 写（仅测试用，默认 true）。
     * 静态 clear/applySnapshot 仍走 {@link #glOps}。
     */
    boolean glOperationsEnabled = true;

    /**
     * 安装 GL 操作面（测试用）；生产勿调用。
     *
     * @param ops 操作面；{@code null} 恢复生产实现
     */
    static void setGlOpsForTest(ClipGlOps ops) {
        glOps = ops == null ? RealClipGlOps.INSTANCE : ops;
    }

    /**
     * 当前 GL 操作面（测试钩子）。
     *
     * @return 操作面
     */
    static ClipGlOps getGlOpsForTest() {
        return glOps;
    }

    /**
     * 压入一层 UI 裁剪区域，并与父层（及首层时的宿主 scissor）求交。
     *
     * <p>不在此捕获宿主基线；基线须在上下文创建时已 {@link #installHostBaseline}。</p>
     *
     * @param left 左侧坐标（UI，左上原点）
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param cornerRadii 圆角
     */
    void push(int left, int top, int right, int bottom, int screenWidth, int screenHeight,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
        int[] parentClip = entries.isEmpty() ? null : entries.peek().clipRect;
        int[] hostUiRect = null;
        if (parentClip == null && hostBaseline.scissorEnabled) {
            hostUiRect = hostBaseline.toUiRect(screenHeight);
        }

        int[] resolved = resolvePushedClipRect(left, top, right, bottom, screenWidth, screenHeight,
                parentClip, hostUiRect);
        entries.push(new ClipState(resolved,
                UiBorderRadiusResolver.scaleToFit(cornerRadii, resolved[2] - resolved[0],
                        resolved[3] - resolved[1])));
    }

    void pop() {
        if (!entries.isEmpty()) {
            entries.pop();
        }
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * 将当前栈顶（或宿主基线）应用到 GL。
     *
     * <p>栈非空时应用栈顶快照；栈空时<strong>幂等</strong>恢复已安装的宿主基线
     * （多次空栈 apply 结果相同，不消费基线，也不走 {@link #clearState()}）。</p>
     *
     * @param screenHeight 屏幕高度
     */
    void applyCurrent(int screenHeight) {
        if (entries.isEmpty()) {
            restoreHostBaseline();
            return;
        }
        if (!glOperationsEnabled) {
            return;
        }
        applySnapshot(copySnapshot(), screenHeight);
    }

    ClipSnapshot copySnapshot() {
        if (entries.isEmpty()) {
            return null;
        }

        int[] clip = entries.peek().clipRect;
        List<RoundedClipRegion> roundedClipRegions = new ArrayList<RoundedClipRegion>();
        Iterator<ClipState> iterator = entries.descendingIterator();
        while (iterator.hasNext()) {
            ClipState clipState = iterator.next();
            if (!UiRoundedRectGeometry.hasAnyCornerRadius(clipState.cornerRadii)) {
                continue;
            }
            int[] clipRect = clipState.clipRect;
            roundedClipRegions.add(new RoundedClipRegion(clipRect[0], clipRect[1], clipRect[2], clipRect[3],
                    clipState.cornerRadii));
        }
        return new ClipSnapshot(new int[] { clip[0], clip[1], clip[2], clip[3] },
                Collections.unmodifiableList(roundedClipRegions));
    }

    /**
     * 将裁剪快照回放到 OpenGL；{@code null} 表示强制清空当前裁切（不恢复宿主基线）。
     *
     * @param clipSnapshot 裁剪快照
     * @param screenHeight 屏幕高度
     */
    static void applySnapshot(ClipSnapshot clipSnapshot, int screenHeight) {
        if (clipSnapshot == null || clipSnapshot.getClipRect() == null) {
            clearState();
            return;
        }

        int[] clipRect = clipSnapshot.getClipRect();
        int width = Math.max(0, clipRect[2] - clipRect[0]);
        int height = Math.max(0, clipRect[3] - clipRect[1]);
        ClipGlOps ops = glOps;
        ops.enable(GL11.GL_SCISSOR_TEST);
        ops.scissor(clipRect[0], screenHeight - clipRect[3], width, height);

        List<RoundedClipRegion> roundedClipRegions = clipSnapshot.getRoundedClipRegions();
        if (roundedClipRegions.isEmpty()) {
            ops.disable(GL11.GL_STENCIL_TEST);
            ops.stencilMask(0xFF);
            return;
        }

        rebuildRoundedClipMask(roundedClipRegions, ops);
    }

    /**
     * 强制清空当前 OpenGL 裁切状态（关闭 scissor/stencil）。
     *
     * <p>供 FBO/deferred 回放使用；不等于恢复宿主进入 uilib 前的基线。</p>
     */
    static void clearState() {
        ClipGlOps ops = glOps;
        ops.disable(GL11.GL_SCISSOR_TEST);
        ops.disable(GL11.GL_STENCIL_TEST);
        ops.stencilMask(0xFF);
        ops.colorMask(true, true, true, true);
        ops.depthMask(true);
    }

    /**
     * 将 GL scissor box（左下角原点）转换为 UI 矩形（左上角原点）{@code [left, top, right, bottom]}。
     *
     * @param glX GL scissor X
     * @param glY GL scissor Y（自底部向上）
     * @param glWidth 宽度
     * @param glHeight 高度
     * @param screenHeight 屏幕高度
     * @return UI 矩形
     */
    static int[] glScissorBoxToUiRect(int glX, int glY, int glWidth, int glHeight, int screenHeight) {
        int safeWidth = Math.max(0, glWidth);
        int safeHeight = Math.max(0, glHeight);
        int left = glX;
        int right = glX + safeWidth;
        int top = screenHeight - (glY + safeHeight);
        int bottom = screenHeight - glY;
        return new int[] { left, top, right, bottom };
    }

    /**
     * 计算压入一层后的有效 UI 裁剪矩形（纯函数，可单测）。
     *
     * <p>先钳到屏幕，再与父层求交；首层（无父层）且存在宿主 UI 矩形时再与宿主求交。</p>
     *
     * @param left 请求左
     * @param top 请求上
     * @param right 请求右
     * @param bottom 请求下
     * @param screenWidth 屏幕宽
     * @param screenHeight 屏幕高
     * @param parentClipRect 父层 {@code [L,T,R,B]}，栈空时为 {@code null}
     * @param hostBaselineUiRect 宿主 scissor 的 UI 矩形，无外部 scissor 或非首层时为 {@code null}
     * @return 有效裁剪矩形 {@code [left, top, right, bottom]}
     */
    static int[] resolvePushedClipRect(int left, int top, int right, int bottom, int screenWidth,
            int screenHeight, int[] parentClipRect, int[] hostBaselineUiRect) {
        int clipLeft = Math.max(0, Math.min(left, right));
        int clipTop = Math.max(0, Math.min(top, bottom));
        int clipRight = Math.min(screenWidth, Math.max(left, right));
        int clipBottom = Math.min(screenHeight, Math.max(top, bottom));

        if (parentClipRect != null) {
            clipLeft = Math.max(clipLeft, parentClipRect[0]);
            clipTop = Math.max(clipTop, parentClipRect[1]);
            clipRight = Math.min(clipRight, parentClipRect[2]);
            clipBottom = Math.min(clipBottom, parentClipRect[3]);
        } else if (hostBaselineUiRect != null) {
            clipLeft = Math.max(clipLeft, hostBaselineUiRect[0]);
            clipTop = Math.max(clipTop, hostBaselineUiRect[1]);
            clipRight = Math.min(clipRight, hostBaselineUiRect[2]);
            clipBottom = Math.min(clipBottom, hostBaselineUiRect[3]);
        }

        if (clipRight < clipLeft) {
            clipRight = clipLeft;
        }
        if (clipBottom < clipTop) {
            clipBottom = clipTop;
        }
        return new int[] { clipLeft, clipTop, clipRight, clipBottom };
    }

    /**
     * 安装宿主裁切基线（上下文生命周期内保留，可多次调用覆盖）。
     *
     * @param baseline 宿主基线；{@code null} 视为 disabled
     */
    void installHostBaseline(HostClipBaseline baseline) {
        this.hostBaseline = baseline == null ? HostClipBaseline.disabled() : baseline;
    }

    /**
     * 从当前 OpenGL 状态捕获宿主 scissor/stencil 基线（不写回、不改栈）。
     *
     * <p>应在 uilib 开始绘制前、主 framebuffer 上调用。无 GL 上下文时返回 disabled。
     * 仅捕获预期的无 GL / 链接失败类错误，不吞 VM 级 {@link Error}（除链接相关）。</p>
     *
     * @return 捕获的基线
     */
    static HostClipBaseline captureCurrentHostBaseline() {
        try {
            return captureCurrentHostBaseline(glOps);
        } catch (RuntimeException ignored) {
            // 纯 JVM 测试 / 无当前 GL 上下文
            return HostClipBaseline.disabled();
        } catch (UnsatisfiedLinkError ignored) {
            return HostClipBaseline.disabled();
        } catch (NoClassDefFoundError ignored) {
            return HostClipBaseline.disabled();
        }
    }

    /**
     * 使用指定 ops 捕获基线（测试可注入记录器）。
     *
     * @param ops GL 操作面
     * @return 捕获的基线
     */
    static HostClipBaseline captureCurrentHostBaseline(ClipGlOps ops) {
        boolean scissorEnabled = ops.isEnabled(GL11.GL_SCISSOR_TEST);
        int glX = 0;
        int glY = 0;
        int glWidth = 0;
        int glHeight = 0;
        if (scissorEnabled) {
            IntBuffer box = SCISSOR_BOX_BUFFER.get();
            box.clear();
            ops.getIntegers(GL11.GL_SCISSOR_BOX, box);
            glX = box.get(0);
            glY = box.get(1);
            glWidth = box.get(2);
            glHeight = box.get(3);
        }

        boolean stencilEnabled = ops.isEnabled(GL11.GL_STENCIL_TEST);
        int stencilFunc = ops.getInteger(GL11.GL_STENCIL_FUNC);
        int stencilRef = ops.getInteger(GL11.GL_STENCIL_REF);
        int stencilValueMask = ops.getInteger(GL11.GL_STENCIL_VALUE_MASK);
        int stencilWriteMask = ops.getInteger(GL11.GL_STENCIL_WRITEMASK);
        int stencilFail = ops.getInteger(GL11.GL_STENCIL_FAIL);
        int stencilZFail = ops.getInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
        int stencilZPass = ops.getInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);

        return new HostClipBaseline(scissorEnabled, glX, glY, glWidth, glHeight, stencilEnabled,
                stencilFunc, stencilRef, stencilValueMask, stencilWriteMask,
                stencilFail, stencilZFail, stencilZPass);
    }

    /**
     * 查看已安装的宿主基线（测试钩子）。
     *
     * @return 当前宿主基线
     */
    HostClipBaseline peekHostBaselineForTest() {
        return hostBaseline;
    }

    /**
     * 查看栈顶裁剪矩形（测试钩子）；栈空返回 {@code null}。
     *
     * @return 栈顶 {@code [L,T,R,B]} 副本
     */
    int[] peekClipRectForTest() {
        if (entries.isEmpty()) {
            return null;
        }
        int[] clip = entries.peek().clipRect;
        return new int[] { clip[0], clip[1], clip[2], clip[3] };
    }

    /**
     * 空栈恢复宿主基线：不消费、不清空 {@link #hostBaseline}，保证多次 apply 幂等。
     */
    private void restoreHostBaseline() {
        if (!glOperationsEnabled) {
            return;
        }
        hostBaseline.applyToGl(glOps);
    }

    private static void rebuildRoundedClipMask(List<RoundedClipRegion> roundedClipRegions, ClipGlOps ops) {
        ops.enable(GL11.GL_STENCIL_TEST);
        ops.stencilMask(0xFF);
        // clear / 几何绘制仍走 GL11：圆角 mask 重建依赖固定管线绘制，非测试替身关注点
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        ops.colorMask(false, false, false, false);
        ops.depthMask(false);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        for (int index = 0; index < roundedClipRegions.size(); index++) {
            ops.stencilFunc(GL11.GL_EQUAL, index, 0xFF);
            ops.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);
            RoundedClipRegion clipRegion = roundedClipRegions.get(index);
            UiRoundedRectGeometry.drawRoundedRectGeometry(clipRegion.getLeft(), clipRegion.getTop(),
                    clipRegion.getRight(), clipRegion.getBottom(), clipRegion.getCornerRadii(), true,
                    UiSurfaceStyle.CORNER_ALL);
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        ops.colorMask(true, true, true, true);
        ops.depthMask(true);
        ops.stencilMask(0x00);
        ops.stencilFunc(GL11.GL_EQUAL, roundedClipRegions.size(), 0xFF);
        ops.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
    }

    /**
     * 进入 uilib 时捕获/安装的宿主裁切基线。
     */
    static final class HostClipBaseline {

        private final boolean scissorEnabled;
        private final int glX;
        private final int glY;
        private final int glWidth;
        private final int glHeight;
        private final boolean stencilEnabled;
        private final int stencilFunc;
        private final int stencilRef;
        private final int stencilValueMask;
        private final int stencilWriteMask;
        private final int stencilFail;
        private final int stencilZFail;
        private final int stencilZPass;

        HostClipBaseline(boolean scissorEnabled, int glX, int glY, int glWidth, int glHeight,
                boolean stencilEnabled) {
            this(scissorEnabled, glX, glY, glWidth, glHeight, stencilEnabled,
                    GL11.GL_ALWAYS, 0, 0xFF, 0xFF, GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        }

        HostClipBaseline(boolean scissorEnabled, int glX, int glY, int glWidth, int glHeight,
                boolean stencilEnabled, int stencilFunc, int stencilRef, int stencilValueMask,
                int stencilWriteMask, int stencilFail, int stencilZFail, int stencilZPass) {
            this.scissorEnabled = scissorEnabled;
            this.glX = glX;
            this.glY = glY;
            this.glWidth = glWidth;
            this.glHeight = glHeight;
            this.stencilEnabled = stencilEnabled;
            this.stencilFunc = stencilFunc;
            this.stencilRef = stencilRef;
            this.stencilValueMask = stencilValueMask;
            this.stencilWriteMask = stencilWriteMask;
            this.stencilFail = stencilFail;
            this.stencilZFail = stencilZFail;
            this.stencilZPass = stencilZPass;
        }

        static HostClipBaseline disabled() {
            return new HostClipBaseline(false, 0, 0, 0, 0, false);
        }

        boolean isScissorEnabled() {
            return scissorEnabled;
        }

        boolean isStencilEnabled() {
            return stencilEnabled;
        }

        int getGlX() {
            return glX;
        }

        int getGlY() {
            return glY;
        }

        int getGlWidth() {
            return glWidth;
        }

        int getGlHeight() {
            return glHeight;
        }

        int getStencilFunc() {
            return stencilFunc;
        }

        int getStencilRef() {
            return stencilRef;
        }

        int getStencilValueMask() {
            return stencilValueMask;
        }

        int getStencilWriteMask() {
            return stencilWriteMask;
        }

        /**
         * 将本基线的 GL scissor 转为 UI 矩形。
         *
         * @param screenHeight 屏幕高度
         * @return UI 矩形
         */
        int[] toUiRect(int screenHeight) {
            return glScissorBoxToUiRect(glX, glY, glWidth, glHeight, screenHeight);
        }

        /**
         * 把宿主基线写回 OpenGL（使用当前全局 {@link ClipGlOps}）。
         */
        void applyToGl() {
            applyToGl(glOps);
        }

        /**
         * 把宿主基线写回指定 ops。
         *
         * @param ops GL 操作面
         */
        void applyToGl(ClipGlOps ops) {
            if (scissorEnabled) {
                ops.enable(GL11.GL_SCISSOR_TEST);
                ops.scissor(glX, glY, Math.max(0, glWidth), Math.max(0, glHeight));
            } else {
                ops.disable(GL11.GL_SCISSOR_TEST);
            }
            if (stencilEnabled) {
                ops.enable(GL11.GL_STENCIL_TEST);
            } else {
                ops.disable(GL11.GL_STENCIL_TEST);
            }
            ops.stencilFunc(stencilFunc, stencilRef, stencilValueMask);
            ops.stencilOp(stencilFail, stencilZFail, stencilZPass);
            ops.stencilMask(stencilWriteMask);
            ops.colorMask(true, true, true, true);
            ops.depthMask(true);
        }
    }

    /**
     * 单层裁剪状态快照。
     */
    private static final class ClipState {

        private final int[] clipRect;
        private final UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii;

        private ClipState(int[] clipRect, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
            this.clipRect = clipRect;
            this.cornerRadii = cornerRadii;
        }
    }
}
