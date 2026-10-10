package club.heiqi.uilib.testing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 复用输入/输出路径和资源读取，不预加载字体，也不解释任何生产资源语义。 */
public final class TestResources {
    private TestResources() {}

    public static Path argumentPath(String[] args, int index, String fallback) {
        return Paths.get(args.length > index ? args[index] : fallback);
    }

    public static Path outputDirectory(String[] args, int index, String fallback)
            throws IOException {
        return Files.createDirectories(argumentPath(args, index, fallback));
    }

    public static ByteBuffer bytes(Path path) throws IOException {
        return ByteBuffer.wrap(Files.readAllBytes(path));
    }

    public static ByteBuffer font(Path directory, String face) throws IOException {
        return bytes(directory.resolve(face + ".ttf"));
    }

    public static ByteBuffer resource(ClassLoader loader, String name) throws IOException {
        try (InputStream input = loader.getResourceAsStream(name)) {
            if (input == null) throw new IOException("测试资源不存在: " + name);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1; ) output.write(buffer, 0, count);
            return ByteBuffer.wrap(output.toByteArray());
        }
    }
}
