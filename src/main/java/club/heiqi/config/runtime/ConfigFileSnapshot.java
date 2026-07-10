package club.heiqi.config.runtime;

import club.heiqi.config.ConfigException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * 配置文件 canonical 路径上的一次不可变字节快照。
 *
 * <p>用于 bootstrap / save / flushRaw / reload 的磁盘 CAS 期望值与比较基准。
 * 相等性为<strong>精确字节等价</strong>（{@link Arrays#equals}），不依赖 mtime / 内容哈希。</p>
 *
 * <p>状态：</p>
 * <ul>
 *   <li>{@link State#MISSING}：canonical 路径不存在</li>
 *   <li>{@link State#REGULAR}：普通文件（含空文件）；持有完整原始字节</li>
 *   <li>{@link State#NON_REGULAR}：存在但不是普通文件（目录、特殊节点等）</li>
 * </ul>
 *
 * <p>同一 canonical 路径的不同 {@link File} 别名（相对/绝对解析后相同路径字符串）共享同一写域。
 * <b>不</b>实现 inode/硬链接身份：硬链接共享写域不保证。
 * 跨进程 compare→replace 窗口<strong>不是</strong> OS 级 CAS；本类只保证同 JVM classloader 内
 * 经静态 monitor 串行的参与式写前精确字节比较 + 原子替换语义（见 {@link Persistence}）。
 * 相同字节 A→B→A（ABA）明确允许。</p>
 */
public final class ConfigFileSnapshot {

    /** 快照状态 */
    public enum State {
        /** 路径不存在 */
        MISSING,
        /** 普通文件 */
        REGULAR,
        /** 存在但非普通文件 */
        NON_REGULAR
    }

    private final File canonicalFile;
    private final State state;
    private final byte[] bytes;

    private ConfigFileSnapshot(File canonicalFile, State state, byte[] bytes) {
        this.canonicalFile = canonicalFile;
        this.state = state;
        this.bytes = bytes == null ? new byte[0] : bytes;
    }

    /**
     * 解析 canonical 并一次读取不可变原始字节。
     *
     * @param file 配置文件（可为相对路径或别名）
     * @return 快照
     * @throws ConfigException canonical 解析失败或读盘 IO 失败
     */
    public static ConfigFileSnapshot capture(File file) throws ConfigException {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        File canonical;
        try {
            canonical = file.getCanonicalFile();
        } catch (IOException e) {
            throw new ConfigException("cannot resolve canonical config path: " + file, e);
        }
        if (!canonical.exists()) {
            return new ConfigFileSnapshot(canonical, State.MISSING, new byte[0]);
        }
        if (!canonical.isFile()) {
            return new ConfigFileSnapshot(canonical, State.NON_REGULAR, new byte[0]);
        }
        try {
            byte[] data = Files.readAllBytes(canonical.toPath());
            return new ConfigFileSnapshot(canonical, State.REGULAR, data);
        } catch (IOException e) {
            throw new ConfigException("cannot read config file: " + canonical, e);
        }
    }

    /**
     * 由已预制 UTF-8 文本构造 REGULAR 快照（写成功后更新 expected 用）。
     *
     * @param canonicalFile 已解析的 canonical 文件
     * @param utf8Text      完整 UTF-8 文本内容
     * @return REGULAR 快照（字节 = text 的 UTF-8）
     */
    public static ConfigFileSnapshot ofPreparedUtf8(File canonicalFile, String utf8Text) {
        if (canonicalFile == null) {
            throw new IllegalArgumentException("canonicalFile must not be null");
        }
        String text = utf8Text == null ? "" : utf8Text;
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        return new ConfigFileSnapshot(canonicalFile, State.REGULAR, data);
    }

    /**
     * @return canonical 文件（写域身份）
     */
    public File canonicalFile() {
        return canonicalFile;
    }

    /**
     * @return 快照状态
     */
    public State state() {
        return state;
    }

    /**
     * 原始字节防御副本。
     *
     * @return 新数组；MISSING/NON_REGULAR 为空数组
     */
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * 包内：直接访问内部字节（只读，调用方不得修改）。
     */
    byte[] rawBytes() {
        return bytes;
    }

    /**
     * 将原始字节解码为 UTF-8 文本（解析 Authority 用）。
     *
     * @return UTF-8 字符串；非 REGULAR 时为空串
     */
    public String utf8Text() {
        if (state != State.REGULAR || bytes.length == 0) {
            return "";
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 精确字节等价：状态相同且字节数组 {@link Arrays#equals}。
     *
     * <p>相同字节的文件重建视为等价；mtime 差异不影响结果。</p>
     *
     * @param other 另一快照
     * @return 精确等价时 true
     */
    public boolean exactBytesEqual(ConfigFileSnapshot other) {
        if (other == null) {
            return false;
        }
        if (this.state != other.state) {
            return false;
        }
        return Arrays.equals(this.bytes, other.bytes);
    }

    /**
     * 两路径是否解析为同一 canonical 写域。
     *
     * @param a 文件 a
     * @param b 文件 b
     * @return 同一 canonical 时 true；任一方解析失败时 false
     */
    public static boolean sameCanonicalWriteDomain(File a, File b) {
        if (a == null || b == null) {
            return false;
        }
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return false;
        }
    }
}
