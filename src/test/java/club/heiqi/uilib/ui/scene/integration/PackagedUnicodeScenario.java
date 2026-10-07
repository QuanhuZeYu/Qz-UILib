package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Method;
import java.util.Locale;

/** 隔离加载真正shadow产物，防止Minecraft旧ICU与错误资源路径被开发classpath掩盖。 */
public final class PackagedUnicodeScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new PackagedUnicodeScenario().run(args); }

    @Override protected void runTests() throws Exception {
        String[] args = arguments();
        URLClassLoader loader = own(new URLClassLoader(new URL[] {new File(args[0]).toURI().toURL()}, null));
        scenario("发布包Bidi视觉run", () -> {
            String prefix = "club.heiqi.uilib.shadow.com.ibm.icu.";
            Class<?> bidiType = loader.loadClass(prefix + "text.Bidi");
            Object bidi = bidiType.getConstructor().newInstance();
            bidiType.getMethod("setPara", String.class, byte.class, byte[].class)
                .invoke(bidi, "abc العربية 123 हिन्दी ไทย 😀", (byte) 126, null);
            int runs = (Integer) bidiType.getMethod("countRuns").invoke(bidi);
            if (runs != 4) throw new AssertionError("发布包Bidi视觉run错误：" + runs);
        });
        Class<?> breaks = loader.loadClass("club.heiqi.uilib.shadow.com.ibm.icu.text.BreakIterator");
        Method setText = breaks.getMethod("setText", String.class);
        Method first = breaks.getMethod("first"), next = breaks.getMethod("next");
        scenario("发布包ZWJ完整字素", () -> {
            Object character = breaks.getMethod("getCharacterInstance", Locale.class).invoke(null, Locale.ROOT);
            setText.invoke(character, "👩‍💻");
            if ((Integer) first.invoke(character) != 0 || (Integer) next.invoke(character) != 5
                    || (Integer) next.invoke(character) != -1) throw new AssertionError("发布包ZWJ字素边界错误");
        });
        scenario("发布包Thai字典资源", () -> {
            Object line = breaks.getMethod("getLineInstance", Locale.class).invoke(null, new Locale("th"));
            setText.invoke(line, "ภาษาไทยภาษาไทย"); first.invoke(line);
            int[] expected = {4, 7, 11, 14, -1};
            for (int end : expected) if ((Integer) next.invoke(line) != end)
                throw new AssertionError("发布包泰文字典断点错误，资源可能未正确relocate");
        });
        scenario("发布包ICU名称隔离", () -> {
            try { loader.loadClass("com.ibm.icu.text.Bidi"); throw new AssertionError("发布包泄漏原ICU包名"); }
            catch (ClassNotFoundException expectedAbsent) { }
            System.out.println("发布包私有ICU：Bidi/ZWJ字素/泰文字典资源加载通过");
        });
    }
}
