package club.heiqi.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 同目录临时文件完整写入后 replace 目标，降低半截写风险。
 *
 * <p><b>优先</b> {@link StandardCopyOption#ATOMIC_MOVE}；平台不支持时退回
 * 完整 temp 的 {@code REPLACE_EXISTING} move（<b>非严格原子</b>，但仍是整文件替换而非截断覆写）。
 * 失败时尽力删除 temp；若 replace 未发生则目标文件保持原字节。</p>
 */
public final class AtomicFileWrites {

    private AtomicFileWrites() {
    }

    /**
     * 将 UTF-8 文本原子写入目标文件。
     *
     * @param target 目标文件
     * @param text   完整内容
     * @throws IOException 写或替换失败
     */
    public static void writeUtf8Atomically(File target, String text) throws IOException {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (text == null) {
            text = "";
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs() && !parent.isDirectory()) {
                throw new IOException("cannot create parent directory: " + parent);
            }
        }
        Path targetPath = target.toPath();
        Path dir = parent != null ? parent.toPath() : targetPath.getParent();
        if (dir == null) {
            dir = new File(".").toPath();
        }
        Path temp = Files.createTempFile(dir, target.getName() + ".", ".tmp");
        try {
            FileOutputStream fos = new FileOutputStream(temp.toFile());
            try {
                OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                writer.write(text);
                writer.flush();
                writer.close();
            } finally {
                fos.close();
            }
            try {
                Files.move(temp, targetPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null; // moved
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
