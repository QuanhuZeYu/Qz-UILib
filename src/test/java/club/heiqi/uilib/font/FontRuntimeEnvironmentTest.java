package club.heiqi.uilib.font;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ClientProxy;
import club.heiqi.uilib.CommonProxy;
import club.heiqi.uilib.config.modern.ModernConfigBootstrap;
import club.heiqi.uilib.util.LaunchSide;
import club.heiqi.uilib.font.glyph.GlyphGenerationDispatcher;
import cpw.mods.fml.relauncher.Side;

/**
 * issue #71（纯服务端环境因找不到字体而崩启动）的回归锁。
 *
 * <p>本类锁定三件互相独立的事：</p>
 * <ol>
 *   <li>字体<b>渲染</b>骨架的引导只在客户端启动侧发生：专用服务端一次 generation candidate 都不该
 *       构建，也不该新建字体线程。</li>
 *   <li>环境级「没有可用字体」在 CPU-only 测量入口转成一次性的可读 {@link IllegalStateException}，
 *       不重复枚举、不把 AWT 原生异常穿透进业务调用栈。</li>
 *   <li>真实字体缺陷不得被降级成「环境没有字体」；launch side 判定不到时不得当作服务端。</li>
 * </ol>
 */
public class FontRuntimeEnvironmentTest {

    /** 真实崩溃里的 AWT 原文（issue #71 附件 crash-2026-08-27_05.11.56-server.txt）。 */
    private static final String FONTCONFIG_HEAD_NULL =
            "Fontconfig head is null, check your fonts or fonts configuration";

    @Test
    public void dedicatedServerMustNotBootstrapFontRenderRuntime() {
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher();
        CountingCandidateFactory factory = new CountingCandidateFactory(
                DefaultFontGenerationCandidateFactory.INSTANCE);
        FontService service = newFontService(factory, dispatcher, Side.SERVER);
        try {
            service.initialize();
            service.initialize();
            Assert.assertEquals("专用服务端不得构建 generation candidate，那一步会枚举系统字体"
                    + "（issue #71 的崩溃点）；实际构建次数：", 0, factory.preparations.get());
            Assert.assertFalse("专用服务端不得把字体渲染骨架标记为已初始化", service.isInitialized());
            Assert.assertFalse("专用服务端不得初始化字形 worker（QzFontWorker 只能由它创建）",
                    dispatcher.isInitialized());
        } finally {
            service.shutdown();
        }
    }

    /**
     * 与上一个用例只差 launch side 判定：证明拦住的是侧别，而不是夹具本身跑不起来。
     * 删掉 {@code FontService#initialize()} 里的侧别判断后，这两个用例不可能同时保持绿色。
     */
    @Test
    public void clientLaunchSideStillBootstrapsFontRenderRuntime() {
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher();
        CountingCandidateFactory factory = new CountingCandidateFactory(
                DefaultFontGenerationCandidateFactory.INSTANCE);
        FontService service = newFontService(factory, dispatcher, Side.CLIENT);
        try {
            service.initialize();
            Assert.assertTrue("客户端必须照常构建 generation candidate", factory.preparations.get() >= 1);
            Assert.assertTrue("客户端字体渲染骨架必须完成初始化", service.isInitialized());
            Assert.assertTrue("客户端必须初始化字形 worker", dispatcher.isInitialized());
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void renderBootstrapGateDependsOnlyOnLaunchSide() {
        Assert.assertTrue("CLIENT 必须允许引导字体渲染骨架",
                FontRuntimeEnvironment.forLaunchSide(Side.CLIENT).allowsRenderBootstrap());
        Assert.assertFalse("SERVER 必须拒绝引导字体渲染骨架",
                FontRuntimeEnvironment.forLaunchSide(Side.SERVER).allowsRenderBootstrap());
        FontRuntimeEnvironment unknown = FontRuntimeEnvironment.forLaunchSide(null);
        Assert.assertTrue("判定不到 launch side（单元测试、离线工具）不得按服务端处理",
                unknown.allowsRenderBootstrap());
        Assert.assertTrue("未知侧别必须在文案里说明原因", unknown.describeLaunchSide().contains("非 FML"));
        Assert.assertTrue("非 FML 宿主里生产实例必须允许引导，否则整套字体测试连带失效",
                FontRuntimeEnvironment.LAUNCH.allowsRenderBootstrap());
    }

    @Test
    public void fontlessEnvironmentMustFailReadableAndMustNotRetryEnumeration() {
        CountingCandidateFactory factory = new CountingCandidateFactory(new RuntimeException(FONTCONFIG_HEAD_NULL));
        FontService service = newFontService(factory, new GlyphGenerationDispatcher(), Side.SERVER);

        IllegalStateException first = expectLayoutRuntimeFailure(service);
        Assert.assertTrue("失败文案必须给出补救动作，实际：" + first.getMessage(),
                first.getMessage().contains("没有可用字体"));
        Assert.assertTrue("失败文案必须点名 fontconfig，实际：" + first.getMessage(),
                first.getMessage().contains("fontconfig"));
        Assert.assertNotNull("必须保留原始异常作为 cause", first.getCause());

        IllegalStateException second = expectLayoutRuntimeFailure(service);
        Assert.assertEquals("同一环境结论必须复用同一文案", first.getMessage(), second.getMessage());
        Assert.assertEquals("环境级失败不得重复枚举系统字体", 1, factory.preparations.get());
        Assert.assertFalse("不可用的测量运行时不得被标记为就绪", readLayoutRuntimeReady(service));
    }

    /** 反向锁：不是所有 candidate 失败都算环境问题，真实缺陷必须原样抛出并保持可重试。 */
    @Test
    public void realFontDefectMustNotBeDowngradedToEnvironmentFailure() {
        IllegalStateException defect = new IllegalStateException("字体资源文件数量超过上限: 256");
        CountingCandidateFactory factory = new CountingCandidateFactory(defect);
        FontService service = newFontService(factory, new GlyphGenerationDispatcher(), Side.SERVER);

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                service.ensureLayoutRuntimeReady();
                Assert.fail("真实字体缺陷必须抛出，第 " + attempt + " 次调用没有抛出");
            } catch (IllegalStateException exception) {
                Assert.assertSame("真实字体缺陷必须原样抛出，不得降级成环境没有字体", defect, exception);
            }
        }
        Assert.assertEquals("真实缺陷不得被缓存成永久不可用，必须允许重试", 2, factory.preparations.get());
    }

    @Test
    public void classifierRecognisesOnlyAwtFontManagerFailures() {
        Assert.assertFalse("null 不是环境级失败", FontRuntimeEnvironment.isFontSubsystemUnavailable(null));
        Assert.assertFalse("无关异常不得被判成环境级失败",
                FontRuntimeEnvironment.isFontSubsystemUnavailable(new IllegalArgumentException("配置项缺失")));
        Assert.assertTrue("AWT 原文必须识别为环境级失败",
                FontRuntimeEnvironment.isFontSubsystemUnavailable(new RuntimeException(FONTCONFIG_HEAD_NULL)));
        Assert.assertTrue("快照层的包装必须能透过 cause 链识别",
                FontRuntimeEnvironment.isFontSubsystemUnavailable(new IllegalStateException("无法枚举系统字体："
                        + "AWT 字体子系统初始化失败", new RuntimeException(FONTCONFIG_HEAD_NULL))));
        Assert.assertTrue("NoClassDefFoundError 只有文案没有 cause，也要按字体管理器类名识别",
                FontRuntimeEnvironment.isFontSubsystemUnavailable(
                        new NoClassDefFoundError("Could not initialize class sun.awt.X11FontManager")));
        Assert.assertTrue("ExceptionInInitializerError 包装的字体管理器失败同样要识别",
                FontRuntimeEnvironment.isFontSubsystemUnavailable(new ExceptionInInitializerError(
                        new RuntimeException(FONTCONFIG_HEAD_NULL))));
    }

    /**
     * 结构锁：字体引导入口必须留在客户端代理里，公共代理不得再引用 FontService。
     *
     * <p>字节码常量池比注释可靠——只要有人把 {@code FontService.getInstance().initialize()} 加回
     * {@code CommonProxy}，或在公共代码里新引 FontService，本用例立即失败。</p>
     */
    @Test
    public void fontBootstrapMustStayOutOfCommonProxyBytecode() throws IOException {
        String commonProxy = classFileConstants(CommonProxy.class);
        Assert.assertFalse("公共代理（= 专用服务端代理）不得引用 FontService：字体渲染骨架只在客户端引导",
                commonProxy.contains("FontService"));
        Assert.assertTrue("配置回灌必须仍在公共代理里", commonProxy.contains("ModernConfigBootstrap"));
        String clientProxy = classFileConstants(ClientProxy.class);
        Assert.assertTrue("字体引导必须落到客户端代理", clientProxy.contains("FontService"));
        Assert.assertFalse("服务端代理不得注册客户端 devtools 自检端点（常驻线程 + 13 个调试端点）",
                commonProxy.contains("NetRuntimeSelfChecks"));
        Assert.assertTrue("devtools 自检端点必须跟着它的客户端驱动一起注册",
                clientProxy.contains("NetRuntimeSelfChecks"));
    }

    /**
     * 结构锁：启动配置回灌不得为了问一句"要不要 reload"而创建 FontService 单例。
     *
     * <p>{@code INSTANCE} 是饿汉单例，构造链经 GlyphPageManager 建出按码点直索引表；实测一次
     * {@code getInstance()} 常驻约 150 MiB，且这些表全部只服务渲染（#71 同族审计 C1）。
     * 专用服务端要的答案是"本侧根本不 bootstrap"，那是静态判据，不该用这笔内存去换。</p>
     */
    @Test
    public void configBootstrapMustNotCreateFontServiceSingleton() throws IOException {
        Set<String> bootstrapRefs = referencedMethods(ModernConfigBootstrap.class);
        Assert.assertTrue("解析器自检：必须读得到真实的 FontService.requestReloadIfRenderRuntimeReady 引用，"
                        + "否则下面的反向断言恒真（解析器坏掉时也会看起来通过）",
                bootstrapRefs.contains(
                        "club/heiqi/uilib/font/FontService#requestReloadIfRenderRuntimeReady"));
        Assert.assertFalse("启动配置回灌不得触碰 FontService 单例：改回 getInstance() 会让专用服务端"
                + "为一句恒 false 的判断付出约 150 MiB 只服务渲染的字形表",
                bootstrapRefs.contains("club/heiqi/uilib/font/FontService#getInstance"));
    }

    /**
     * 侧别判据必须是静态可问的，且与启动侧权威同源——不允许各留一份判断。
     */
    @Test
    public void renderRuntimeSupportPredicateSharesLaunchSideAuthority() {
        Assert.assertEquals("FontService 静态侧判据必须与 LaunchSide 权威同结论",
                Boolean.valueOf(!LaunchSide.LAUNCH.isDedicatedServer()),
                Boolean.valueOf(FontService.isRenderRuntimeSupportedOnThisSide()));
        Assert.assertEquals("静态判据与注入式环境判定必须同源，不能各写一遍规则",
                Boolean.valueOf(FontRuntimeEnvironment.LAUNCH.allowsRenderBootstrap()),
                Boolean.valueOf(FontService.isRenderRuntimeSupportedOnThisSide()));
    }

    private static FontService newFontService(FontGenerationCandidateFactory factory,
            GlyphGenerationDispatcher dispatcher, Side launchSide) {
        return new FontService(new FontReloadSignal(0L, 0L, 0L, System::nanoTime), factory, dispatcher,
                DirectFontGenerationCandidateScheduler.INSTANCE, FontRuntimeEnvironment.forLaunchSide(launchSide));
    }

    private static IllegalStateException expectLayoutRuntimeFailure(FontService service) {
        try {
            service.ensureLayoutRuntimeReady();
        } catch (IllegalStateException exception) {
            return exception;
        }
        throw new AssertionError("无字体环境的测量运行时入口必须抛 IllegalStateException，实际正常返回");
    }

    private static boolean readLayoutRuntimeReady(FontService service) {
        try {
            Field field = FontService.class.getDeclaredField("layoutRuntimeReady");
            field.setAccessible(true);
            return ((AtomicBoolean) field.get(service)).get();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("无法读取 layoutRuntimeReady 字段", exception);
        }
    }

    private static String classFileConstants(Class<?> type) throws IOException {
        return new String(classFileBytes(type), StandardCharsets.ISO_8859_1);
    }

    private static byte[] classFileBytes(Class<?> type) throws IOException {
        String resource = type.getName().replace('.', '/') + ".class";
        InputStream input = type.getClassLoader().getResourceAsStream(resource);
        Assert.assertNotNull("测试需要读到 " + resource + " 的字节码", input);
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        } finally {
            input.close();
        }
    }

    /**
     * 解析 class 常量池，返回该方法引用到的全部方法，形如 {@code owner#name}。
     *
     * <p>之所以不能在 class 字节里直接 contains("getInstance")：常量池把类名和方法名存成两条
     * 独立 UTF8，方法引用只是一对索引，子串匹配既会漏也会假阳性。未识别的 tag 一律抛异常，
     * 让解析器不可能"安静地"返回空集而把反向断言变成恒真。</p>
     */
    private static Set<String> referencedMethods(Class<?> type) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(classFileBytes(type)));
        in.readInt();
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        int[] classNames = new int[count];
        int[] refOwner = new int[count];
        int[] refNameAndType = new int[count];
        int[] nameAndTypeName = new int[count];
        List<Integer> methodRefs = new ArrayList<Integer>();
        for (int index = 1; index < count; index++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1: {
                    byte[] raw = new byte[in.readUnsignedShort()];
                    in.readFully(raw);
                    utf8[index] = new String(raw, StandardCharsets.UTF_8);
                    break;
                }
                case 3:
                case 4:
                    in.skipBytes(4);
                    break;
                case 5:
                case 6:
                    in.skipBytes(8);
                    index++;
                    break;
                case 7:
                    classNames[index] = in.readUnsignedShort();
                    break;
                case 8:
                    in.skipBytes(2);
                    break;
                case 9:
                    in.skipBytes(2);
                    in.skipBytes(2);
                    break;
                case 10:
                case 11:
                    refOwner[index] = in.readUnsignedShort();
                    refNameAndType[index] = in.readUnsignedShort();
                    methodRefs.add(Integer.valueOf(index));
                    break;
                case 12:
                    nameAndTypeName[index] = in.readUnsignedShort();
                    in.skipBytes(2);
                    break;
                case 15:
                    in.skipBytes(3);
                    break;
                case 16:
                    in.skipBytes(2);
                    break;
                case 17:
                    in.skipBytes(4);
                    break;
                case 18:
                    in.skipBytes(1);
                    in.skipBytes(2);
                    break;
                case 19:
                case 20:
                    in.skipBytes(2);
                    break;
                default:
                    throw new IllegalStateException("常量池出现解析器未覆盖的 tag " + tag
                            + "（索引 " + index + "），必须同步更新解析器，不能跳过");
            }
        }
        Set<String> methods = new LinkedHashSet<String>();
        for (Integer refIndex : methodRefs) {
            int entry = refIndex.intValue();
            String className = utf8[classNames[refOwner[entry]]];
            String methodName = utf8[nameAndTypeName[refNameAndType[entry]]];
            if (className != null && methodName != null) {
                methods.add(className + "#" + methodName);
            }
        }
        return methods;
    }

    /** 记录构建次数的 candidate 工厂；给了 failure 就固定抛出该异常实例。 */
    private static final class CountingCandidateFactory implements FontGenerationCandidateFactory {

        private final AtomicInteger preparations = new AtomicInteger();
        private final FontGenerationCandidateFactory delegate;
        private final RuntimeException failure;

        CountingCandidateFactory(FontGenerationCandidateFactory delegate) {
            this(delegate, null);
        }

        CountingCandidateFactory(RuntimeException failure) {
            this(null, failure);
        }

        private CountingCandidateFactory(FontGenerationCandidateFactory delegate, RuntimeException failure) {
            this.delegate = delegate;
            this.failure = failure;
        }

        @Override
        public FontGenerationCandidate prepare(FontGenerationRegistry fontRegistry,
                FontGenerationBuildRequest request) {
            preparations.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return delegate.prepare(fontRegistry, request);
        }
    }
}
