package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.TestResources;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Properties;

/** 同一个正式包在Java8/25实际打开字体、整形及光栅，避免开发classpath掩盖多版本绑定。 */
public final class PackagedFontScenario extends TestApplication {
    private URLClassLoader loader;
    private Class<?> sessionType, faceType, directionType;
    private Object engine;
    private Properties initialProperties;

    @Override protected void setup() throws Exception {
        initialProperties = new Properties(); initialProperties.putAll(System.getProperties());
        loader = own(new URLClassLoader(new URL[] {new File(arguments()[0]).toURI().toURL()}, null));
        sessionType = loader.loadClass("club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession");
        faceType = loader.loadClass("club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace");
        directionType = loader.loadClass("club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeDirection");
        engine = call(loader.loadClass("club.heiqi.uilib.host.font.IsolatedFontEngine").getMethod("shared"), null);
        System.out.println("PACKAGED_FONT_RUNTIME Java=" + System.getProperty("java.version") + " jar=" + arguments()[0]);
    }

    @Override protected void teardown() {
        require(initialProperties.equals(System.getProperties()), "私有字体初始化或释放修改全局系统属性");
    }

    @Override protected void runTests() throws Exception {
        scenario("发布包阿拉伯整形及真实像素", () -> font("NotoSansArabic", "العربية", "RTL", "ar", false));
        scenario("发布包彩色emoji", () -> font("NotoColorEmoji", "😀", "LTR", "und", true));
        scenario("发布包完整ZWJ序列", () -> font("NotoColorEmoji", "👩‍💻", "LTR", "und", true));
    }

    private void font(String name, String text, String direction, String language, boolean color) throws Exception {
        Properties before = new Properties(); before.putAll(System.getProperties());
        Object session = call(loader.loadClass("club.heiqi.uilib.ui.scene.text.font.nativefont.FontEngine").getMethod("openSession"), engine);
        own((AutoCloseable) session);
        ByteBuffer source = TestResources.resource(loader, "assets/qz_uilib/fonts/noto/" + name + ".ttf");
        byte[] bytes = new byte[source.remaining()]; source.get(bytes);
        Object face = call(sessionType.getMethod("openFace", ByteBuffer.class, long.class, float.class), session, ByteBuffer.wrap(bytes), 0L, 32.3f);
        own((AutoCloseable) face);
        Arrays.fill(bytes, (byte) 0);
        Object dir = directionType.getField(direction).get(null);
        Object run = call(faceType.getMethod("shape", String.class, directionType, String.class), face, text, dir, language);
        int glyphs = 0, visible = 0; boolean colored = false;
        for (Object glyph : (Iterable<?>) run) {
            int id = glyph.getClass().getField("glyphId").getInt(glyph);
            int cluster = glyph.getClass().getField("cluster").getInt(glyph);
            require(id > 0 && cluster >= 0 && cluster < text.length(), "发布包glyph或源文簇无效");
            Object bitmap = call(faceType.getMethod("rasterize", int.class), face, id);
            byte[] pixels = (byte[]) call(bitmap.getClass().getMethod("pixels"), bitmap);
            for (int i = 0; i < pixels.length; i += 4) {
                int alpha = pixels[i + 3] & 255;
                if (alpha > 0) {
                    visible++;
                    colored |= pixels[i] != pixels[i + 1] || pixels[i + 1] != pixels[i + 2];
                }
            }
            glyphs++;
        }
        require(glyphs > 0 && visible > 0, "发布包字体无实际整形或可见像素");
        if (color) require(glyphs == 1 && colored, "发布包emoji拆分或丢失颜色");
        require(before.equals(System.getProperties()), "私有字体绑定修改全局系统属性");
        System.out.println("PACKAGED_FONT_PIXELS " + name + " glyphs=" + glyphs + " visible=" + visible + " colored=" + colored);
    }

    private static Object call(Method method, Object target, Object... args) throws Exception {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            throw failure;
        }
    }
    public static void main(String[] args) throws Exception { new PackagedFontScenario().run(args); }
}
