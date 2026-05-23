package club.heiqi.uilib.net.codec;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * 网络层使用的 varint/varlong 编码工具。
 */
public final class Varint {

    private Varint() {}

    /**
     * 写入无符号 int varint。
     *
     * @param out 输出目标
     * @param value 非负整数
     * @throws IOException 写入失败
     */
    public static void writeUnsignedInt(DataOutput out, int value) throws IOException {
        if (value < 0) {
            throw new IllegalArgumentException("value must be >= 0");
        }
        writeRawInt(out, value);
    }

    /**
     * 读取无符号 int varint。
     *
     * @param in 输入来源
     * @return 非负整数
     * @throws IOException 读取失败或 varint 超长
     */
    public static int readUnsignedInt(DataInput in) throws IOException {
        int value = 0;
        int shift = 0;
        for (int index = 0; index < 5; index++) {
            int part = in.readUnsignedByte();
            value |= (part & 127) << shift;
            if ((part & 128) == 0) {
                return value;
            }
            shift += 7;
        }
        throw new IOException("Varint too big");
    }

    /**
     * 写入带符号 int，内部使用 ZigZag。
     *
     * @param out 输出目标
     * @param value 整数
     * @throws IOException 写入失败
     */
    public static void writeSignedInt(DataOutput out, int value) throws IOException {
        writeRawInt(out, (value << 1) ^ (value >> 31));
    }

    /**
     * 读取带符号 int，内部使用 ZigZag。
     *
     * @param in 输入来源
     * @return 整数
     * @throws IOException 读取失败
     */
    public static int readSignedInt(DataInput in) throws IOException {
        int raw = readUnsignedInt(in);
        return (raw >>> 1) ^ -(raw & 1);
    }

    /**
     * 写入无符号 long varlong。
     *
     * @param out 输出目标
     * @param value 非负长整数
     * @throws IOException 写入失败
     */
    public static void writeUnsignedLong(DataOutput out, long value) throws IOException {
        if (value < 0L) {
            throw new IllegalArgumentException("value must be >= 0");
        }
        writeRawLong(out, value);
    }

    /**
     * 读取无符号 long varlong。
     *
     * @param in 输入来源
     * @return 非负长整数
     * @throws IOException 读取失败或 varlong 超长
     */
    public static long readUnsignedLong(DataInput in) throws IOException {
        long value = 0L;
        int shift = 0;
        for (int index = 0; index < 10; index++) {
            int part = in.readUnsignedByte();
            value |= (long) (part & 127) << shift;
            if ((part & 128) == 0) {
                return value;
            }
            shift += 7;
        }
        throw new IOException("Varlong too big");
    }

    /**
     * 写入带符号 long，内部使用 ZigZag。
     *
     * @param out 输出目标
     * @param value 长整数
     * @throws IOException 写入失败
     */
    public static void writeSignedLong(DataOutput out, long value) throws IOException {
        writeRawLong(out, (value << 1) ^ (value >> 63));
    }

    /**
     * 读取带符号 long，内部使用 ZigZag。
     *
     * @param in 输入来源
     * @return 长整数
     * @throws IOException 读取失败
     */
    public static long readSignedLong(DataInput in) throws IOException {
        long raw = readUnsignedLong(in);
        return (raw >>> 1) ^ -(raw & 1L);
    }

    /**
     * 计算无符号 int varint 字节数。
     *
     * @param value 非负整数
     * @return 字节数
     */
    public static int unsignedIntSize(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be >= 0");
        }
        int size = 1;
        while ((value & -128) != 0) {
            size++;
            value >>>= 7;
        }
        return size;
    }

    private static void writeRawInt(DataOutput out, int value) throws IOException {
        while ((value & -128) != 0) {
            out.writeByte((value & 127) | 128);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static void writeRawLong(DataOutput out, long value) throws IOException {
        while ((value & -128L) != 0L) {
            out.writeByte(((int) value & 127) | 128);
            value >>>= 7;
        }
        out.writeByte((int) value);
    }
}
