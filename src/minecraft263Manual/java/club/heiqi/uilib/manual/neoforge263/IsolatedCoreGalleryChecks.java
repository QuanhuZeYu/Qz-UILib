package club.heiqi.uilib.manual.neoforge263;

import club.heiqi.uilib.testing.gallery.GalleryChecks;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/** 核心测试与正式 Core 同包；在独立运行域保持包内访问，不向 FML 模块平铺验证类。 */
public final class IsolatedCoreGalleryChecks implements GalleryChecks {
    private static final String ROOT = "/META-INF/uilib-verification/";
    private static final List<String> LIBRARIES = List.of("core.jar", "gallery.jar", "core-verification.jar");
    private final List<Check> directory;

    public IsolatedCoreGalleryChecks() {
        try (VerificationJars jars = new VerificationJars();
             URLClassLoader loader = new URLClassLoader(jars.extract(), null)) {
            Object service = service(loader);
            List<Check> entries = new ArrayList<>();
            for (Object entry : (List<?>) invoke(service, "checks")) {
                entries.add(new Check(text(entry, "id"), text(entry, "title"), text(entry, "description")));
            }
            directory = List.copyOf(entries);
        } catch (Exception failure) {
            throw new IllegalStateException("NeoForge 核心验证装配失败", failure);
        }
    }

    @Override public List<Check> checks() { return directory; }

    @Override public Result run(String id) {
        long begin = System.nanoTime();
        Result result = null;
        try (VerificationJars jars = new VerificationJars();
             URLClassLoader loader = new URLClassLoader(jars.extract(), null)) {
            Object actual = invoke(service(loader), "run", id);
            result = new Result(text(actual, "id"), actual.getClass().getField("elapsedNanos").getLong(actual),
                    text(actual, "details"), (Throwable) actual.getClass().getField("failure").get(actual));
        } catch (Throwable failure) {
            StringWriter trace = new StringWriter();
            failure.printStackTrace(new PrintWriter(trace));
            if (result == null) return new Result(id, System.nanoTime() - begin, "核心验证执行失败\n" + trace, failure);
            Throwable original = result.failure == null ? failure : result.failure;
            if (original != failure) original.addSuppressed(failure);
            return new Result(result.id, result.elapsedNanos, result.details + '\n' + trace, original);
        }
        return result;
    }

    private static Object service(ClassLoader loader) throws ClassNotFoundException, IOException {
        Class<?> contract = loader.loadClass(GalleryChecks.class.getName());
        var providers = ServiceLoader.load(contract, loader).iterator();
        if (!providers.hasNext()) throw new IOException("随包 core-verification 缺少 GalleryChecks 服务");
        return providers.next();
    }

    private static String text(Object value, String field) throws ReflectiveOperationException {
        return (String) value.getClass().getField(field).get(value);
    }

    private static Object invoke(Object service, String method, String... arguments) throws Exception {
        try {
            return arguments.length == 0 ? service.getClass().getMethod(method).invoke(service)
                    : service.getClass().getMethod(method, String.class).invoke(service, (Object[]) arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        }
    }

    private static final class VerificationJars implements AutoCloseable {
        private final Path directory = Files.createTempDirectory("qz-core-verification-");
        VerificationJars() throws IOException {}

        URL[] extract() throws IOException {
            URL[] urls = new URL[LIBRARIES.size()];
            for (int index = 0; index < LIBRARIES.size(); index++) {
                String name = LIBRARIES.get(index);
                Path jar = directory.resolve(name);
                try (InputStream input = IsolatedCoreGalleryChecks.class.getResourceAsStream(ROOT + name)) {
                    if (input == null) throw new IOException("缺少随包核心验证资源：" + ROOT + name);
                    Files.copy(input, jar);
                }
                urls[index] = jar.toUri().toURL();
            }
            return urls;
        }

        @Override public void close() throws IOException {
            IOException failure = null;
            List<Path> owned = new ArrayList<>();
            for (String name : LIBRARIES) owned.add(directory.resolve(name));
            owned.add(directory);
            for (Path path : owned) {
                try { Files.deleteIfExists(path); }
                catch (IOException next) {
                    if (failure == null) failure = next; else failure.addSuppressed(next);
                }
            }
            if (failure != null) throw failure;
        }
    }
}
