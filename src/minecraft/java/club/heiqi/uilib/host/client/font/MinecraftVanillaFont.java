package club.heiqi.uilib.host.client.font;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.lwjgl.opengl.Display;
import club.heiqi.uilib.host.client.font.compat.OptionalFontHostBridge;
import club.heiqi.uilib.host.client.extension.MinecraftHostExtensions;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.host.client.font.context.LwjglFontContextCapture;
import club.heiqi.uilib.host.client.font.context.RasterPolicy;
import club.heiqi.uilib.host.client.font.context.StandardFontContext;
import club.heiqi.uilib.host.client.font.vanilla.VanillaTextService;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.RasterSampling;

/** MC 标准文字入口适配：布局使用共享字体服务，caller 的 GUI/世界变换在播放位置捕获。
 * 安装创建逻辑适配器，GPU 资源按需取得；文字与 scene 共用字体、worker、atlas 和 backend。 */
public final class MinecraftVanillaFont {
    private static final Map<FontRendererAccess, SettingsEntry> SETTINGS =
            new WeakHashMap<FontRendererAccess, SettingsEntry>();
    // 小字号轮廓从 32px 采样以保留顶部细节，布局 em/advance/基线由 Settings 决定。
    // 依据：微软雅黑“世界/取消”在 em11、GUI 密度 2 时，24px 首墨迹行较 32px 晚一物理像素。
    private static final RasterPolicy RASTER =
            new RasterPolicy(32, 48, 64, 96, 128, 192, 256, 384, 512);
    private static VanillaTextService text;
    private static LwjglFontContextCapture capture;
    private static OptionalFontHostBridge bridge;
    private static WorldFontSampling samplingFacts;
    private static Thread ownerThread;
    private static volatile boolean installed;

    private MinecraftVanillaFont() {}

    public static void install() {
        if (installed) throw new IllegalStateException("原版字体入口重复安装");
        text = new VanillaTextService(() -> MinecraftFontServices.shared().fonts());
        samplingFacts = new WorldFontSampling(RASTER, 4096);
        ownerThread = Thread.currentThread();
        installed = true;
    }

    // Forge Splash 使用独立线程与 context；volatile 发布安装结果，标准入口仅在游戏 owner 线程接管。
    public static boolean active() {
        return installed && Thread.currentThread() == ownerThread && VanillaFontOptions.enabled();
    }

    private static VanillaTextService.Settings settings(FontRendererAccess font, boolean shadow) {
        int height = font.qz$fontHeight();
        if (height <= 0) throw new IllegalArgumentException("标准FONT_HEIGHT须为正数");
        int[] palette = font.qz$palette();
        java.util.function.IntUnaryOperator mapping = MinecraftHostExtensions.textMapping();
        SettingsEntry entry = SETTINGS.get(font);
        if (entry == null
                || entry.height != height
                || entry.unicode != font.qz$unicode()
                || entry.bidi != font.qz$bidi()
                || entry.normal.textMapping != mapping
                || !Arrays.equals(entry.palette, palette)) {
            entry = new SettingsEntry(height, font.qz$unicode(), font.qz$bidi(), palette, mapping);
            SETTINGS.put(font, entry);
        }
        return shadow ? entry.shadow : entry.normal;
    }

    private static final class SettingsEntry {
        final int height;
        final boolean unicode, bidi;
        final int[] palette;
        final VanillaTextService.Settings normal, shadow;

        SettingsEntry(int height, boolean unicode, boolean bidi, int[] palette,
                java.util.function.IntUnaryOperator mapping) {
            if (palette == null || palette.length < 32)
                throw new IllegalArgumentException("标准字体须提供32色调色板");
            this.height = height;
            this.unicode = unicode;
            this.bidi = bidi;
            this.palette = palette.clone();
            int[] dark = palette.clone();
            for (int i = 0; i < 16; i++) dark[i] = palette[i + 16];
            // 原版替代按宿主FONT_HEIGHT设置em；测宽、裁剪与绘制共用此值，基线保留宿主行距。
            float em = height * (9f / 9f), baseline = height;
            normal = new VanillaTextService.Settings(em, height, baseline, bidi, unicode, palette, mapping);
            shadow = new VanillaTextService.Settings(em, height, baseline, bidi, unicode, dark, mapping);
        }
    }

    public static int width(FontRendererAccess font, String source) {
        return text.width(source, settings(font, false));
    }

    public static int charWidth(FontRendererAccess font, char c) {
        return text.charWidth(c, settings(font, false));
    }

    public static String trim(FontRendererAccess font, String source, int width, boolean reverse) {
        return text.trim(source, width, reverse, settings(font, false));
    }

    public static List<String> list(FontRendererAccess font, String source, int width) {
        return text.list(source, width, settings(font, false));
    }

    public static String wrap(FontRendererAccess font, String source, int width) {
        return text.wrap(source, width, settings(font, false));
    }

    public static int sizeToWidth(FontRendererAccess font, String source, int width) {
        return text.sizeToWidth(source, width, settings(font, false));
    }

    public static int splitHeight(FontRendererAccess font, String source, int width) {
        return text.splitHeight(source, width, settings(font, false));
    }

    public static String bidiString(FontRendererAccess font, String source) {
        return text.bidiString(source, settings(font, false));
    }

    private static int normalizeColor(int color) {
        return (color & 0xfc000000) == 0 ? color | 0xff000000 : color;
    }

    private static int shadowColor(int color) {
        return color & 0xff000000 | (color & 0x00fcfcfc) >>> 2;
    }

    // 微软雅黑 GPU 对照的中文墨迹下沿位于 caller+10 附近；绘制原点上移 2 个 GUI 单位对齐原版框底。
    private static final int DRAW_Y_OFFSET = -2;

    private static PaintPlan plan(TextLayout layout, int x, int y) {
        return new PaintPlanBuilder()
                .add(
                        PaintCommand.text(
                                new LayoutBox(
                                        x,
                                        y + DRAW_Y_OFFSET,
                                        (int) Math.ceil(layout.advanceWidth),
                                        layout.height),
                                null,
                                layout))
                .build();
    }

    public static int draw(
            FontRendererAccess font, String source, int x, int y, int color, boolean shadow) {
        if (source == null) {
            font.qz$enableAlpha();
            return 0;
        }
        color = normalizeColor(color);
        VanillaTextService.Settings style = settings(font, false);
        if (source.isEmpty()) {
            font.qz$tailState(color);
            return x + (shadow ? 1 : 0);
        }
        TextLayout logical = text.layout(source, style, color, Float.MAX_VALUE, TextPolicy.NO_WRAP);
        font.qz$enableAlpha();
        FontRenderContext context = capture();
        PaintPlanBuilder plans = new PaintPlanBuilder();
        if (shadow)
            plans.add(
                    PaintCommand.text(
                            new LayoutBox(
                                    x + 1,
                                    y + 1 + DRAW_Y_OFFSET,
                                    (int) Math.ceil(logical.advanceWidth),
                                    logical.height),
                            null,
                            text.layout(
                                    source,
                                    settings(font, true),
                                    shadowColor(color),
                                    Float.MAX_VALUE,
                                    TextPolicy.NO_WRAP)));
        plans.add(
                PaintCommand.text(
                        new LayoutBox(
                                x,
                                y + DRAW_Y_OFFSET,
                                (int) Math.ceil(logical.advanceWidth),
                                logical.height),
                        null,
                        logical));
        PaintPlan plan = plans.build();
        WorldFontSampling facts = samplingFacts;
        hostBridge().submit(plan, context, () -> facts.forget(plan));
        font.qz$tailState(text.terminalColor(source, style, color));
        return (int) (x + logical.advanceWidth + (shadow ? 1 : 0));
    }

    public static void drawSplit(
            FontRendererAccess font, String source, int x, int y, int width, int color) {
        color = normalizeColor(color);
        VanillaTextService.Settings style = settings(font, false);
        TextLayout logical = text.layout(source, style, color, width, TextPolicy.NORMAL);
        font.qz$enableAlpha();
        PaintPlan plan = plan(logical, x, y);
        WorldFontSampling facts = samplingFacts;
        hostBridge().submit(plan, capture(), () -> facts.forget(plan));
        font.qz$tailState(text.terminalColor(source, style, color));
    }

    private static FontRenderContext capture() {
        ClientFontServices owner = MinecraftFontServices.shared();
        if (capture == null)
            capture =
                    new LwjglFontContextCapture(
                            owner.gl(),
                            MinecraftHostExtensions.matrices(
                                    MinecraftVanillaFont.class.getClassLoader(), owner.gl()));
        FontRenderContext context =
                capture.capture(Display.getHeight(), MinecraftFontServices.resourceRevision(), 0);
        return StandardFontContext.from(context);
    }

    private static OptionalFontHostBridge hostBridge() {
        if (bridge == null) {
            WorldFontSampling facts = samplingFacts;
            bridge =
                    OptionalFontHostBridge.create(
                            MinecraftVanillaFont.class.getClassLoader(),
                            MinecraftFontServices.shared().gl(),
                            new OptionalFontHostBridge.ReplaySink() {
                                @Override
                                public FontRenderContext capture() {
                                    return MinecraftVanillaFont.capture();
                                }

                                @Override
                                public void registerVisible(
                                        PaintPlan plan, FontRenderContext context) {
                                    try {
                                        ClientFontServices owner = MinecraftFontServices.shared();
                                        GlPaintPlanRenderer renderer = owner.sharedPlanRenderer();
                                        owner.registerVisibleDemand(
                                                renderer, sampled(plan, context, facts, true), context);
                                    } catch (RuntimeException | Error failure) {
                                        // 登记失败时立即退役本次采样事实。
                                        facts.forget(plan);
                                        throw failure;
                                    }
                                }

                                @Override
                                public void draw(PaintPlan plan, FontRenderContext context) {
                                    try {
                                        ClientFontServices owner = MinecraftFontServices.shared();
                                        GlPaintPlanRenderer renderer = owner.sharedPlanRenderer();
                                        try (GlPaintPlanRenderer.Prepared prepared =
                                                owner.prepare(
                                                        renderer, sampled(plan, context, facts, false), context)) {
                                            prepared.render(context);
                                        }
                                    } finally {
                                        // 合并计划保留原command引用，成员票据的清理也可重复执行。
                                        facts.forget(plan);
                                    }
                                }
                            },
                            4096);
        }
        return bridge;
    }

    /** 按播放位置的投影采样原逻辑计划，保留布局并生成当前 tick 的混淆字形。 */
    private static PaintPlan sampled(PaintPlan plan, FontRenderContext context,
            WorldFontSampling facts, boolean remember) {
        WorldFontSampling.Source source = WorldFontSampling.Source.capture(context);
        PaintPlanBuilder builder = new PaintPlanBuilder();
        long tick = System.nanoTime() / 50000000L;
        for (PaintCommand command : plan.commands()) {
            if (command.kind != PaintCommand.Kind.TEXT) {
                builder.add(command);
                continue;
            }
            TextLayout layout = command.text;
            RasterSampling sampling = facts.sample(command, context, source, remember);
            TextLayout sample = text.sample(layout, sampling, tick, layout.input.text.hashCode());
            builder.add(PaintCommand.text(command.box, null, sample));
        }
        return builder.build();
    }

    public static void beforePresent() {
        if (bridge != null && bridge.pending() != 0)
            throw new IllegalStateException("呈现之前仍有未消费的宿主字体票据：" + bridge.pending());
        if (samplingFacts != null) samplingFacts.endFrame();
    }

    static void fontResourcesChanged() {
        if (samplingFacts != null) samplingFacts.clear();
    }

    public static void close() {
        installed = false;
        try {
            if (bridge != null) bridge.close();
        } finally {
            // lease先沿旧bridge归还，再清空数值事实；旧列表的清理仍持有原facts实例。
            bridge = null;
            if (samplingFacts != null) samplingFacts.clear();
            samplingFacts = null;
            capture = null;
            text = null;
            SETTINGS.clear();
        }
    }
}
