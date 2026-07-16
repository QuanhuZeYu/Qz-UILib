package club.heiqi.uilib.ui.image;

import java.util.Objects;

/**
 * 在运行时适配器边界为宿主图片委托施加不可绕过的 ItemStack 状态围栏。
 *
 * <p>包装器只调用 {@link HostImageRenderer#render}，因此 delegate 覆盖
 * {@link HostImageRenderer#renderGuarded} 也不能绕过真实围栏。</p>
 */
public final class GuardedHostImageRenderer implements HostImageRenderer {

    private final HostImageRenderer delegate;
    private final HostImageGlStateGuard itemStateGuard;

    /**
     * 使用生产 GL 状态围栏包装指定委托。
     *
     * @param delegate 宿主图片绘制委托
     */
    private GuardedHostImageRenderer(HostImageRenderer delegate) {
        this(delegate, new HostImageGlStateGuard());
    }

    /** 创建可注入完整状态围栏的测试实例。 */
    GuardedHostImageRenderer(HostImageRenderer delegate, HostImageGlStateGuard itemStateGuard) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.itemStateGuard = Objects.requireNonNull(itemStateGuard, "itemStateGuard");
    }

    /**
     * 幂等包装宿主图片委托，避免重复执行昂贵状态快照。
     *
     * @param renderer 待包装委托
     * @return 已受围栏保护的委托
     */
    public static HostImageRenderer wrap(HostImageRenderer renderer) {
        HostImageRenderer resolved = Objects.requireNonNull(renderer, "renderer");
        return resolved instanceof GuardedHostImageRenderer
                ? resolved
                : new GuardedHostImageRenderer(resolved);
    }

    @Override
    public void render(HostImageSource source, int left, int top, int right, int bottom) {
        HostImageRenderOutcome outcome = execute(source, left, top, right, bottom);
        if (!outcome.isRendered() || !outcome.isRecovered()) {
            throw renderFailure(outcome);
        }
    }

    /**
     * ItemStack 走完整围栏，其它图片保持旧委托的轻量异常隔离语义。
     */
    @Override
    public HostImageRenderOutcome renderGuarded(HostImageSource source, int left, int top, int right, int bottom) {
        return execute(source, left, top, right, bottom);
    }

    /** 执行唯一的围栏判定与委托调用，避免兼容入口之间递归或重复围栏。 */
    private HostImageRenderOutcome execute(HostImageSource source, int left, int top, int right, int bottom) {
        if (source != null && source.getKind() == HostImageSource.Kind.ITEM_STACK) {
            return itemStateGuard.run(() -> delegate.render(source, left, top, right, bottom));
        }
        try {
            delegate.render(source, left, top, right, bottom);
            return HostImageRenderOutcome.success();
        } catch (RuntimeException exception) {
            return HostImageRenderOutcome.failure("render", exception, true,
                    exception.getClass().getSimpleName());
        } catch (LinkageError error) {
            return HostImageRenderOutcome.failure("render", error, true, error.getClass().getSimpleName());
        }
    }

    /** 将 void 兼容入口无法表达的失败转换为带阶段与原始原因的明确异常。 */
    private static RuntimeException renderFailure(HostImageRenderOutcome outcome) {
        String message = "Host image render failed at stage " + outcome.getStage()
                + (outcome.getDetail() == null ? "" : ": " + outcome.getDetail());
        return new IllegalStateException(message, outcome.getFailure());
    }
}
