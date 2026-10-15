package club.heiqi.uilib.ui.scene.text.font.nativefont;

import java.util.Objects;

/**
 * 一次行或段落整形拥有的完整 UTF-16 上下文；各 run 只借用其范围，不能截断连接上下文。
 * 创建、读取和关闭均限所属线程，调用方在本次整形结束时关闭，不放入布局或跨调用缓存。
 * 默认实现只持有 Java 字符串；适配可在同一生命周期内拥有编码资源，核心不接触其地址。
 */
public class ShapeContext implements AutoCloseable {
    private final Thread thread = Thread.currentThread();
    private String text;

    public ShapeContext(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    public String text() {
        checkThread();
        if (text == null) throw new IllegalStateException("整形上下文已关闭");
        return text;
    }

    private void checkThread() {
        if (Thread.currentThread() != thread)
            throw new IllegalStateException("整形上下文仅限创建线程使用");
    }

    @Override
    public void close() {
        checkThread();
        text = null;
    }
}
