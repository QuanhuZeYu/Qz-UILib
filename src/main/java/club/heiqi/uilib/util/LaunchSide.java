package club.heiqi.uilib.util;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * 启动侧判定唯一权威：客户端 / 专用服务端 / 未知。
 *
 * <p>仓库内凡需要区分启动侧的地方都必须经本类，不得各自去读 FML 的 launch side。
 * 原因不是风格，而是可空性：{@code FMLLaunchHandler.side} 是普通静态字段，只在
 * {@code setupClient} / {@code setupServer} 里赋值，因此非 FML 宿主（单元测试 JVM、
 * 离线工具）读到的是 {@code null}，读它还可能整体触发类加载。</p>
 *
 * <p><b>未知不等于服务端。</b>把 null 当成服务端，会让测试环境里所有"仅客户端"的保护
 * 与逻辑静默失效；当成客户端只是少一层保护。所以本类对"是否服务端"采用
 * fail-open：只有明确读到 SERVER 才返回 true。</p>
 *
 * <p>本类只回答"启动在哪一侧"这一件事实。侧别之上、各子系统自己的策略
 * （例如字体渲染骨架是否允许 bootstrap）留在各自权威里判定，不下沉到本类。</p>
 */
public final class LaunchSide {

    /** 生产实例：侧别取 FML launch side，读不到即未知。 */
    public static final LaunchSide LAUNCH = forSide(readLaunchSide());

    private final Side side;

    private LaunchSide(Side side) {
        this.side = side;
    }

    /**
     * 构造指定侧别的判定实例，用于覆盖 CLIENT / SERVER / 未知三种取值。
     *
     * @param side 启动侧；null 表示非 FML 宿主
     * @return 判定实例
     */
    public static LaunchSide forSide(Side side) {
        return new LaunchSide(side);
    }

    /**
     * 原始侧别。
     *
     * @return FML 启动侧；非 FML 宿主为 null
     */
    public Side side() {
        return side;
    }

    /**
     * 是否为专用服务端：只有明确读到 SERVER 才成立，未知按非服务端处理。
     *
     * @return 专用服务端返回 true
     */
    public boolean isDedicatedServer() {
        return side == Side.SERVER;
    }

    /**
     * 侧别描述，用于日志与用户可见文案。
     *
     * @return 客户端 / 服务端时为侧别名，未知时为说明文案
     */
    public String describe() {
        return side == null ? "未知（非 FML 启动环境）" : side.name();
    }

    private static Side readLaunchSide() {
        try {
            return FMLLaunchHandler.side();
        } catch (Throwable ignored) {
            // 非 FML 宿主没有 launch side，不猜测。
            return null;
        }
    }
}
