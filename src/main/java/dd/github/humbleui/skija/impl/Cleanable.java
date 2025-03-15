package io.github.humbleui.skija.impl;

import java.lang.ref.Reference;
import java.util.Objects;

public final class Cleanable {
    private static final Object CLEANER;
    private final Object cleanable;

    static {
        Object cleaner = null;
        try {
            // 尝试 Java 9+ 的 java.lang.ref.Cleaner
            Class<?> cleanerClass = Class.forName("java.lang.ref.Cleaner");
            cleaner = cleanerClass.getMethod("create").invoke(null);
        } catch (Throwable ex) {
            try {
                // 回退到 sun.misc.Cleaner
                Class<?> cleanerClass = Class.forName("sun.misc.Cleaner");
                cleaner = cleanerClass; // 标记使用旧版实现
            } catch (Throwable ex2) {
                throw new RuntimeException("No Cleaner implementation available");
            }
        }
        CLEANER = cleaner;
    }

    public static Cleanable register(Object obj, Runnable action) {
        Objects.requireNonNull(obj);
        Objects.requireNonNull(action);
        try {
            if (CLEANER instanceof Class) { // sun.misc.Cleaner 处理
                Object cleaner = ((Class<?>) CLEANER).getMethod("create", Object.class, Runnable.class)
                    .invoke(null, obj, action);
                return new Cleanable(cleaner);
            } else { // java.lang.ref.Cleaner 处理
                Object cleanable = CLEANER.getClass().getMethod("register", Object.class, Runnable.class)
                    .invoke(CLEANER, obj, action);
                return new Cleanable(cleanable);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to register cleaner", e);
        }
    }

    private Cleanable(Object cleanable) {
        this.cleanable = cleanable;
    }

    public void clean() {
        try {
            if (cleanable.getClass().getName().equals("sun.misc.Cleaner")) {
                cleanable.getClass().getMethod("clean").invoke(cleanable);
            } else {
                cleanable.getClass().getMethod("clean").invoke(cleanable);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean", e);
        }
    }
}
