package club.heiqi.uilib.net.codec;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 网络反射 codec 的字段元数据。
 *
 * <p>默认使用 Java 字段名作为线协议字段名；需要兼容重命名时可以通过
 * {@link #name()} 固定协议名。{@link #since()} 预留给后续 schema 演化使用。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NetField {

    /**
     * 线协议字段名，空字符串表示沿用 Java 字段名。
     *
     * @return 线协议字段名
     */
    String name() default "";

    /**
     * 字段引入的 schema 版本。
     *
     * @return schema 版本号
     */
    int since() default 1;
}
