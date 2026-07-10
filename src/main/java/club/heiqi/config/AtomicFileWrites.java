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
import java.util.concurrent.atomic.AtomicReference;

/**
 * 同目录临时文件完整写入后 replace 目标，降低半截写风险。
 *
 * <p><b>优先</b> {@link StandardCopyOption#ATOMIC_MOVE}；平台不支持时退回
 * 完整 temp 的 {@code REPLACE_EXISTING} move（<b>非严格原子</b>，但仍是整文件替换而非截断覆写）。
 * 失败时尽力删除 temp；若 replace 未发生则目标文件保持原字节。</p>
 *
 * <h3>测试缝（package 可控）</h3>
 * <p>{@link #installFaultInjector} 可在 temp 写成功后、move 前注入失败，用于确定性覆盖
 * IO_FAILED 且 expected/Authority/draft/event 零推进。生产路径 injector 为 null。</p>
 */
public final class AtomicFileWrites {

    /**
     * 包内测试故障注入：在 temp 完整写入后、move 前调用；抛 IOException 则目标不变。
     */
    public interface FaultInjector {
        /**
         * @param target 目标文件
         * @param temp   已写满的临时文件
         * @throws IOException 模拟 move 失败等
         */
        void beforeMove(File target, Path temp) throws IOException;
    }

    private static final AtomicReference<FaultInjector> FAULT_INJECTOR =
            new AtomicReference<FaultInjector>(null);

    private AtomicFileWrites() {
    }

    /**
     * 安装故障注入器（仅测试；用完必须 {@link #clearFaultInjector}）。
     *
     * @param injector 注入器，null 清除
     */
    public static void installFaultInjector(FaultInjector injector) {
        FAULT_INJECTOR.set(injector);
    }

    /** 清除故障注入器。 */
    public static void clearFaultInjector() {
        FAULT_INJECTOR.set(null);
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
            FaultInjector injector = FAULT_INJECTOR.get();
            if (injector != null) {
                injector.beforeMove(target, temp);
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
