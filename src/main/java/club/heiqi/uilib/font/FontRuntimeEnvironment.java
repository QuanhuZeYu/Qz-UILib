package club.heiqi.uilib.font;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * 字体子系统的启动环境判定：唯一权威。
 *
 * <p>字体系统有两个彼此独立的引导契约，因此也对应两条独立判定，调用方不得各自重实现：</p>
 * <ul>
 *   <li>{@link FontService#initialize()} 建立的是<b>渲染</b>骨架：字形 worker 线程、字符页管理
 *       与 GL 上传回调。只有客户端存在渲染上下文，专用服务端一律跳过。</li>
 *   <li>{@link FontService#ensureLayoutRuntimeReady()} 建立的是 <b>CPU-only 测量</b>运行时
 *       （设计契约见该方法 javadoc：不创建 atlas texture / worker / 批渲染器 / 着色器）。
 *       任意启动侧都允许使用，但当环境里没有可用系统字体时必须降级为可读失败，
 *       而不是把 AWT 的原生异常抛进 FML 生命周期或业务调用栈。</li>
 * </ul>
 *
 * <p>启动侧只用于第一条契约。侧别读不出来时（非 FML 宿主：单元测试、离线工具）一律按
 * 允许处理：判定不到不等于判定为服务端。</p>
 */
final class FontRuntimeEnvironment {

    /** JDK 字体管理器相关类名（取自真实崩溃栈）；只用于匹配文案，不做反射。 */
    private static final String[] FONT_MANAGER_CLASS_NAMES = {
            "sun.awt.FontConfiguration",
            "sun.awt.X11FontManager",
            "sun.awt.FcFontManager",
            "sun.font.SunFontManager",
            "sun.font.PlatformFontInfo",
            "sun.font.FontManagerFactory",
            "sun.java2d.SunGraphicsEnvironment",
            "sun.java2d.HeadlessGraphicsEnvironment",
    };

    /** AWT 在字体目录为空 / fontconfig head 为 null 时给出的固定原文。 */
    private static final String FONTCONFIG_HEAD_NULL = "Fontconfig head is null";

    /** 生产实例：启动侧取 FML 的 launch side。 */
    static final FontRuntimeEnvironment LAUNCH = new FontRuntimeEnvironment(currentLaunchSide());

    /**
     * 构造指定启动侧的判定实例，供单测覆盖 CLIENT / SERVER / 未知三种取值。
     *
     * @param launchSide 启动侧；null 表示非 FML 宿主
     * @return 判定实例
     */
    static FontRuntimeEnvironment forLaunchSide(Side launchSide) {
        return new FontRuntimeEnvironment(launchSide);
    }

    private final Side launchSide;

    private FontRuntimeEnvironment(Side launchSide) {
        this.launchSide = launchSide;
    }

    /**
     * 判断当前启动环境是否允许引导字体渲染骨架。
     *
     * @return 专用服务端返回 false；客户端与判定不到侧别的环境返回 true
     */
    boolean allowsRenderBootstrap() {
        return launchSide != Side.SERVER;
    }

    /**
     * 返回启动侧描述，用于日志与异常文案。
     *
     * @return 启动侧描述
     */
    String describeLaunchSide() {
        return launchSide == null ? "未知（非 FML 启动环境）" : launchSide.name();
    }

    /**
     * 判断异常链里是否有 JDK 字体子系统本身初始化失败。
     *
     * <p>只认字体子系统不可用这一类：字体文件损坏、数量超限、配置错误等仍按原样抛出，
     * 否则会把真实的产品缺陷降级成环境没有字体。</p>
     *
     * @param failure 待判异常；可为 null
     * @return 是否属于环境级字体不可用
     */
    static boolean isFontSubsystemUnavailable(Throwable failure) {
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth < 16) {
            String message = current.getMessage();
            if (message != null && (message.contains(FONTCONFIG_HEAD_NULL) || namesFontManagerClass(message))) {
                return true;
            }

            Throwable cause = current.getCause() == current ? null : current.getCause();
            if (cause == null) {
                return false;
            }
            current = cause;
            depth++;
        }
        return false;
    }

    /**
     * 生成环境级字体不可用的可读结论。
     *
     * @param environment 启动环境判定
     * @param failure 原始异常
     * @return 面向用户、含补救动作的说明
     */
    static String describeFontSubsystemUnavailable(FontRuntimeEnvironment environment, Throwable failure) {
        return "当前系统没有可用字体，Qz-UILib 的文本测量与字形渲染不可用（启动侧："
                + environment.describeLaunchSide() + "，原始异常：" + describeRoot(failure) + "）。"
                + "Linux 需安装 fontconfig 与至少一个字体包（Alpine: apk add fontconfig ttf-dejavu；"
                + "Debian/Ubuntu: fonts-dejavu-core），安装后重启进程即可恢复；"
                + "专用服务端不安装字体也可以正常启动，只是无法执行文本测量。";
    }

    /**
     * 按类名文案识别：ExceptionInInitializerError 与 NoClassDefFoundError 只有 message 没有可用 cause，
     * 但都会把出问题的字体管理器类名写进文案。
     */
    private static boolean namesFontManagerClass(String message) {
        for (String fontManagerClass : FONT_MANAGER_CLASS_NAMES) {
            if (message.contains(fontManagerClass)) {
                return true;
            }
        }
        return false;
    }

    private static String describeRoot(Throwable failure) {
        if (failure == null) {
            return "unknown";
        }
        Throwable root = failure;
        int depth = 0;
        while (root.getCause() != null && root.getCause() != root && depth < 16) {
            root = root.getCause();
            depth++;
        }
        return root.getClass().getName() + ": " + root.getMessage();
    }

    private static Side currentLaunchSide() {
        try {
            return FMLLaunchHandler.side();
        } catch (Throwable ignored) {
            // 非 FML 宿主（单元测试、离线工具）没有 launch side，不猜测。
            return null;
        }
    }
}
